package com.tpov.logger_processor

import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockBodyImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.name.FqName

class LoggerIrGenerationExtension : IrGenerationExtension {
    // Defines the FqName for the Logger annotation
    private val loggerAnnotationFqName = FqName("com.tpov.log_api.logger.Logger")

    // Set to track processed functions to avoid duplicates
    private val processedFunctions = mutableSetOf<String>()

    // Map to store call stack for each class
    private val callStackByClass = mutableMapOf<IrClass, MutableList<String>>()

    companion object {
        // Helper function to generate indentation based on call depth
        private fun getIndent(depth: Int): String {
            return "|    ".repeat(depth)
        }
    }

    @OptIn(FirIncompatiblePluginAPI::class)
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        // Get reference to android.util.Log class
        val logClass = pluginContext.referenceClass(FqName("android.util.Log"))
            ?: throw IllegalStateException("Cannot find android.util.Log class")

        // Get symbol for the Log.d(String, String) function
        val logDSymbol = logClass.owner.functions.firstOrNull {
            it.name.asString() == "d" && it.valueParameters.size == 2
        }?.symbol ?: throw IllegalStateException("Cannot find Log.d function")

        // Collect annotated classes
        val annotatedClasses = mutableSetOf<IrClass>()
        moduleFragment.accept(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildren(this, null)
            }

            override fun visitClass(declaration: IrClass) {
                // Check if class has @Logger annotation
                if (declaration.annotations.any { it.type.classFqName == loggerAnnotationFqName }) {
                    annotatedClasses.add(declaration)
                    callStackByClass[declaration] = mutableListOf() // Initialize call stack for class
                }
                super.visitClass(declaration)
            }
        }, null)

        // Process functions in annotated classes
        moduleFragment.accept(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildren(this, null)
            }

            override fun visitFunction(declaration: IrFunction) {
                val parentClass = declaration.parent as? IrClass
                if (parentClass != null && annotatedClasses.contains(parentClass)) {
                    val functionId = "${parentClass.hashCode()}_${declaration.name}"
                    if (!processedFunctions.contains(functionId)) {
                        processedFunctions.add(functionId)
                        instrumentFunction(declaration, pluginContext, logDSymbol, parentClass) // Instrument function
                    }
                }
                super.visitFunction(declaration)
            }
        }, null)
    }

    // Creates a Log.d statement IrCall
    private fun createLogStatement(
        context: IrPluginContext,
        logSymbol: IrSimpleFunctionSymbol,
        tag: String,
        prefix: String,
        functionName: String,
        depth: Int
    ): IrCall {
        val indent = getIndent(depth)
        val message = "$indent$prefix $functionName [depth:${depth + 1}]"
        return IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = context.irBuiltIns.unitType,
            symbol = logSymbol,
            typeArgumentsCount = 0,
            valueArgumentsCount = 2
        ).apply {
            putValueArgument(0, IrConstImpl.string(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.stringType, tag))
            putValueArgument(1, IrConstImpl.string(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.stringType, message))
        }
    }

    private fun instrumentFunction(
        declaration: IrFunction,
        context: IrPluginContext,
        logSymbol: IrSimpleFunctionSymbol,
        parentClass: IrClass
    ) {
        val functionName = declaration.name.toString()
        val functionId = "${parentClass.hashCode()}_$functionName"
        val callStack = callStackByClass[parentClass] ?: mutableListOf()

        // Check for recursive calls to avoid infinite loops
        if (callStack.contains(functionId)) {
            return
        }

        callStack.add(functionId) // Push to call stack
        val currentDepth = callStack.size - 1

        when (val body = declaration.body) {
            is IrBlockBody -> {
                val statements = mutableListOf<IrStatement>()

                // Add function entry log statement
                statements.add(createLogStatement(context, logSymbol, "LoggerTag", "-->", functionName, currentDepth))

                // Process existing function body statements
                for (statement in body.statements) {
                    statements.add(statement)

                    // Process nested function calls recursively
                    if (statement is IrCall) {
                        val calledFunction = statement.symbol.owner
                        if (calledFunction is IrFunction) {
                            val calledClass = calledFunction.parent as? IrClass
                            if (calledClass != null &&
                                calledClass.annotations.any { it.type.classFqName == loggerAnnotationFqName }) {
                                val calledFunctionId = "${calledClass.hashCode()}_${calledFunction.name}"
                                if (!processedFunctions.contains(calledFunctionId)) {
                                    processedFunctions.add(calledFunctionId)
                                    instrumentFunction(calledFunction, context, logSymbol, calledClass)
                                }
                            }
                        }
                    }
                }

                // Add function exit log statement
                statements.add(createLogStatement(context, logSymbol, "LoggerTag", "<--", functionName, currentDepth))

                // Replace original function body with instrumented one
                declaration.body = IrBlockBodyImpl(
                    startOffset = body.startOffset,
                    endOffset = body.endOffset,
                    statements = statements
                )
            }
            is IrExpressionBody -> {
                val statements = mutableListOf<IrStatement>()
                statements.add(createLogStatement(context, logSymbol, "LoggerTag", "-->", functionName, currentDepth))
                statements.add(body.expression)
                statements.add(createLogStatement(context, logSymbol, "LoggerTag", "<--", functionName, currentDepth))

                // Replace original expression body with block body containing log statements
                declaration.body = IrBlockBodyImpl(
                    startOffset = body.startOffset,
                    endOffset = body.endOffset,
                    statements = statements
                )
            }
        }

        callStack.removeLast() // Pop from call stack
    }
}