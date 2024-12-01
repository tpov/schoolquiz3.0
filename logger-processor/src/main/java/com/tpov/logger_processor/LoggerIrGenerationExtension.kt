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
    private val loggerAnnotationFqName = FqName("com.tpov.log_api.logger.Logger")
    private val processedFunctions = mutableSetOf<String>()
    private val callGraph = mutableMapOf<String, MutableSet<String>>() // Для отслеживания вызовов
    private val functionDepths = mutableMapOf<String, Int>() // Для хранения глубины каждой функции

    companion object {
        private const val TAG = "LoggerTag"
        private const val DEBUG = true

        private fun getIndent(depth: Int): String {
            return "|    ".repeat(maxOf(depth, 0))
        }
    }

    @OptIn(FirIncompatiblePluginAPI::class)
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val logClass = pluginContext.referenceClass(FqName("android.util.Log"))
            ?: throw IllegalStateException("Cannot find android.util.Log class")

        val logDSymbol = logClass.owner.functions.firstOrNull {
            it.name.asString() == "d" && it.valueParameters.size == 2
        }?.symbol ?: throw IllegalStateException("Cannot find Log.d function")

        // Сначала строим граф вызовов
        buildCallGraph(moduleFragment)

        // Затем вычисляем глубины для всех функций
        calculateFunctionDepths()

        // И только потом инструментируем код
        instrumentFunctions(moduleFragment, pluginContext, logDSymbol)
    }

    private fun buildCallGraph(moduleFragment: IrModuleFragment) {
        moduleFragment.accept(object : IrElementVisitorVoid {
            private var currentFunction: String? = null

            override fun visitElement(element: IrElement) {
                element.acceptChildren(this, null)
            }

            override fun visitFunction(declaration: IrFunction) {
                val parentClass = declaration.parent as? IrClass
                if (parentClass?.annotations?.any { it.type.classFqName == loggerAnnotationFqName } == true) {
                    val previousFunction = currentFunction
                    currentFunction = "${parentClass.hashCode()}_${declaration.name}"

                    // Инициализируем множество вызовов для текущей функции
                    callGraph.putIfAbsent(currentFunction!!, mutableSetOf())

                    // Если есть родительская функция, добавляем связь
                    previousFunction?.let {
                        callGraph[it]?.add(currentFunction!!)
                    }

                    super.visitFunction(declaration)
                    currentFunction = previousFunction
                } else {
                    super.visitFunction(declaration)
                }
            }

            override fun visitCall(expression: IrCall) {
                val calledFunction = expression.symbol.owner
                val calledClass = calledFunction.parent as? IrClass

                if (currentFunction != null &&
                    calledClass?.annotations?.any { it.type.classFqName == loggerAnnotationFqName } == true) {
                    val calledFunctionId = "${calledClass.hashCode()}_${calledFunction.name}"
                    callGraph[currentFunction]?.add(calledFunctionId)
                }

                super.visitCall(expression)
            }
        }, null)
    }

    private fun calculateFunctionDepths() {
        // Находим корневые функции (те, которые никто не вызывает)
        val rootFunctions = callGraph.keys.toMutableSet()
        callGraph.values.forEach { calledFunctions ->
            rootFunctions.removeAll(calledFunctions)
        }

        // Для каждой корневой функции вычисляем глубины
        rootFunctions.forEach { rootFunction ->
            calculateDepthDFS(rootFunction, 0, mutableSetOf())
        }
    }

    private fun calculateDepthDFS(functionId: String, depth: Int, visited: MutableSet<String>) {
        if (functionId in visited) return
        visited.add(functionId)

        // Обновляем глубину функции на максимальное значение
        functionDepths[functionId] = maxOf(functionDepths[functionId] ?: 0, depth)

        // Рекурсивно обрабатываем все вызываемые функции
        callGraph[functionId]?.forEach { calledFunction ->
            calculateDepthDFS(calledFunction, depth + 1, visited)
        }
    }

    private fun instrumentFunctions(
        moduleFragment: IrModuleFragment,
        context: IrPluginContext,
        logSymbol: IrSimpleFunctionSymbol
    ) {
        moduleFragment.accept(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildren(this, null)
            }

            override fun visitFunction(declaration: IrFunction) {
                val parentClass = declaration.parent as? IrClass
                if (parentClass != null &&
                    parentClass.annotations.any { it.type.classFqName == loggerAnnotationFqName }) {
                    val functionId = "${parentClass.hashCode()}_${declaration.name}"
                    if (!processedFunctions.contains(functionId)) {
                        processedFunctions.add(functionId)
                        val depth = functionDepths[functionId] ?: 0
                        instrumentFunction(declaration, context, logSymbol, parentClass, depth)
                    }
                }
                super.visitFunction(declaration)
            }
        }, null)
    }

    private fun instrumentFunction(
        declaration: IrFunction,
        context: IrPluginContext,
        logSymbol: IrSimpleFunctionSymbol,
        parentClass: IrClass,
        depth: Int
    ) {
        val functionName = declaration.name.toString()
        val returnTypeString = declaration.returnType.toString()

        when (val body = declaration.body) {
            is IrBlockBody -> {
                val statements = mutableListOf<IrStatement>()

                statements.add(createLogStatement(
                    context = context,
                    logSymbol = logSymbol,
                    prefix = "-->",
                    functionName = functionName,
                    depth = depth,
                    className = parentClass.name.toString(),
                    returnType = returnTypeString
                ))

                statements.addAll(body.statements)

                statements.add(createLogStatement(
                    context = context,
                    logSymbol = logSymbol,
                    prefix = "<--",
                    functionName = functionName,
                    depth = depth,
                    className = parentClass.name.toString(),
                    returnType = returnTypeString
                ))

                declaration.body = IrBlockBodyImpl(
                    startOffset = body.startOffset,
                    endOffset = body.endOffset,
                    statements = statements
                )
            }

            is IrExpressionBody -> {
                val statements = mutableListOf<IrStatement>()

                statements.add(createLogStatement(
                    context = context,
                    logSymbol = logSymbol,
                    prefix = "-->",
                    functionName = functionName,
                    depth = depth,
                    className = parentClass.name.toString(),
                    returnType = returnTypeString
                ))

                statements.add(body.expression)

                statements.add(createLogStatement(
                    context = context,
                    logSymbol = logSymbol,
                    prefix = "<--",
                    functionName = functionName,
                    depth = depth,
                    className = parentClass.name.toString(),
                    returnType = returnTypeString
                ))

                declaration.body = IrBlockBodyImpl(
                    startOffset = body.startOffset,
                    endOffset = body.endOffset,
                    statements = statements
                )
            }
        }
    }

    private fun createLogStatement(
        context: IrPluginContext,
        logSymbol: IrSimpleFunctionSymbol,
        prefix: String,
        functionName: String,
        depth: Int,
        className: String,
        returnType: String
    ): IrCall {
        val indent = getIndent(depth)
        val message = "$indent$prefix $functionName [$className] [$returnType] [depth:${depth + 1}]"

        return IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = context.irBuiltIns.unitType,
            symbol = logSymbol,
            typeArgumentsCount = 0,
            valueArgumentsCount = 2
        ).apply {
            putValueArgument(0, IrConstImpl.string(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                context.irBuiltIns.stringType,
                TAG
            ))
            putValueArgument(1, IrConstImpl.string(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                context.irBuiltIns.stringType,
                message
            ))
        }
    }
}