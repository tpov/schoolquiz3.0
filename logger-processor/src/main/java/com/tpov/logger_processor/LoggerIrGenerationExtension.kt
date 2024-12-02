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
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockBodyImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.name.FqName
import kotlin.collections.MutableSet
import kotlin.collections.any
import kotlin.collections.forEach
import kotlin.collections.get
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.collections.set
import kotlin.collections.toMutableSet

class LoggerIrGenerationExtension : IrGenerationExtension {
    private val loggerAnnotationFqName = FqName("com.tpov.log_api.logger.Logger")
    private val callGraph = mutableMapOf<String, MutableSet<String>>()
    private val functionDepths = mutableMapOf<String, Int>()
    private var asyncContextLevel = 0
    private val asyncContexts = mutableMapOf<String, Int>()
    private val asyncCallChains = mutableMapOf<String, String>()
    private val currentAsyncContext = mutableMapOf<String, String>()

    companion object {
        private const val TAG = "LoggerTag"
        private const val ASYNC_INDENT = "          " // 10 spaces
        private const val REGULAR_INDENT = "|    "
    }

    @OptIn(FirIncompatiblePluginAPI::class)
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val logClass = pluginContext.referenceClass(FqName("android.util.Log"))
            ?: throw IllegalStateException("Cannot find android.util.Log class")

        val logDSymbol = logClass.owner.functions.firstOrNull {
            it.name.asString() == "d" && it.valueParameters.size == 2
        }?.symbol ?: throw IllegalStateException("Cannot find Log.d function")

        buildCallGraph(moduleFragment)
        calculateFunctionDepths()
        instrumentFunctions(moduleFragment, pluginContext, logDSymbol)
    }

    private fun buildCallGraph(moduleFragment: IrModuleFragment) {
        moduleFragment.accept(object : IrElementVisitorVoid {
            private var currentFunction: String? = null
            private var currentClass: IrClass? = null

            override fun visitElement(element: IrElement) {
                element.acceptChildren(this, null)
            }

            override fun visitClass(declaration: IrClass) {
                val previousClass = currentClass
                currentClass = declaration
                super.visitClass(declaration)
                currentClass = previousClass
            }

            override fun visitFunction(declaration: IrFunction) {
                val parentClass = declaration.parent as? IrClass
                if (parentClass?.annotations?.any { it.type.classFqName == loggerAnnotationFqName } == true) {
                    val previousFunction = currentFunction
                    currentFunction = "${parentClass.hashCode()}_${declaration.name}"

                    callGraph.putIfAbsent(currentFunction!!, mutableSetOf())

                    if (isAsyncOperation(declaration, previousFunction)) {
                        asyncContexts[declaration.name.asString()] = asyncContextLevel
                        currentAsyncContext[declaration.name.asString()] = previousFunction ?: ""
                    }

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
        val rootFunctions = callGraph.keys.toMutableSet()
        callGraph.values.forEach { calledFunctions ->
            rootFunctions.removeAll(calledFunctions)
        }

        rootFunctions.forEach { rootFunction ->
            calculateDepthDFS(rootFunction, 0, mutableSetOf())
        }
    }

    private fun calculateDepthDFS(functionId: String, depth: Int, visited: MutableSet<String>) {
        if (functionId in visited) return
        visited.add(functionId)

        functionDepths[functionId] = maxOf(functionDepths[functionId] ?: 0, depth)

        callGraph[functionId]?.forEach { calledFunction ->
            calculateDepthDFS(calledFunction, depth + 1, visited)
        }
    }

    private fun isAsyncOperation(declaration: IrFunction, parentFunction: String? = null): Boolean {
        val functionBody = declaration.body?.toString() ?: ""
        val functionName = declaration.name.asString()

        // Проверяем Flow и StateFlow
        val isFlow = functionBody.contains("collect") ||
                functionBody.contains("Flow") ||
                functionBody.contains(".observe") ||
                functionName.contains("collect", ignoreCase = true)

        // Проверяем корутины и скоупы
        val isCoroutine = functionBody.contains("launch") ||
                functionBody.contains("async") ||
                functionBody.contains("withContext") ||
                functionBody.contains("viewModelScope") ||
                functionBody.contains("lifecycleScope")

        // Проверяем таймеры и обработчики
        val isTimer = functionName.contains("timer", ignoreCase = true) ||
                functionBody.contains("TimerTask") ||
                functionBody.contains("schedule")

        if (isFlow || isCoroutine || isTimer) {
            asyncContextLevel++
            asyncContexts[functionName] = asyncContextLevel

            // Строим цепочку вызовов
            asyncCallChains[functionName] = when {
                functionBody.contains("lifecycleScope.launch") -> "lifecycleScope.launch -> $functionName"
                functionBody.contains("collect") -> "${parentFunction ?: "Unknown"} -> collect -> $functionName"
                else -> "${parentFunction ?: "Unknown"} -> $functionName"
            }

            return true
        }
        return false
    }

    private fun getIndent(depth: Int, functionName: String): String {
        val asyncLevel = asyncContexts[functionName] ?: 0
        return ASYNC_INDENT.repeat(asyncLevel) + REGULAR_INDENT.repeat(depth)
    }

    private fun getAsyncSource(functionName: String): String {
        return asyncCallChains[functionName]?.let { chain ->
            "[AsyncSource: $chain]"
        } ?: ""
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
                    val depth = functionDepths[functionId] ?: 0
                    instrumentFunction(declaration, context, logSymbol, parentClass, depth)
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
        when (val body = declaration.body) {
            is IrBlockBody -> {
                val newStatements = mutableListOf<IrStatement>()

                newStatements.add(createLogStatement(
                    context = context,
                    logSymbol = logSymbol,
                    prefix = "-->",
                    functionName = declaration.name.toString(),
                    depth = depth,
                    className = parentClass.name.toString()
                ))

                body.statements.forEach { statement ->
                    if (statement is IrReturn) {
                        newStatements.add(createLogStatement(
                            context = context,
                            logSymbol = logSymbol,
                            prefix = "<--",
                            functionName = declaration.name.toString(),
                            depth = depth,
                            className = parentClass.name.toString()
                        ))
                    }
                    newStatements.add(statement)
                }

                newStatements.add(createLogStatement(
                    context = context,
                    logSymbol = logSymbol,
                    prefix = "<--",
                    functionName = declaration.name.toString(),
                    depth = depth,
                    className = parentClass.name.toString()
                ))

                declaration.body = IrBlockBodyImpl(
                    startOffset = body.startOffset,
                    endOffset = body.endOffset,
                    statements = newStatements
                )
            }
            is IrExpressionBody -> {
                val statements = mutableListOf<IrStatement>()

                statements.add(createLogStatement(
                    context = context,
                    logSymbol = logSymbol,
                    prefix = "-->",
                    functionName = declaration.name.toString(),
                    depth = depth,
                    className = parentClass.name.toString()
                ))

                statements.add(body.expression)

                statements.add(createLogStatement(
                    context = context,
                    logSymbol = logSymbol,
                    prefix = "<--",
                    functionName = declaration.name.toString(),
                    depth = depth,
                    className = parentClass.name.toString()
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
        className: String
    ): IrCall {
        val indent = getIndent(depth, functionName)
        val asyncSource = getAsyncSource(functionName)
        val message = "$indent$prefix $functionName [$className] $asyncSource"

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