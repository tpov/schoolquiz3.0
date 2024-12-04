package com.tpov.logger_processor

import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
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
import kotlin.collections.set

class LoggerIrGenerationExtension : IrGenerationExtension {
    private val loggerAnnotationFqName = FqName("com.tpov.log_api.logger.Logger")
    private val callGraph = mutableMapOf<String, MutableSet<String>>()
    private val functionDepths = mutableMapOf<String, Int>()
    private var asyncContextLevel = 0
    private val asyncContexts = mutableMapOf<String, Int>()
    private val asyncCallChains = mutableMapOf<String, String>()
    private val currentAsyncContext = mutableMapOf<String, String>()
    private val asyncStack = mutableListOf<AsyncContext>()

    companion object {
        private const val TAG = "LoggerTag"
        private const val ASYNC_INDENT = "            " // 10 spaces
        private const val REGULAR_INDENT = "|    "
    }

    @OptIn(FirIncompatiblePluginAPI::class)
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val logClass = pluginContext.referenceClass(FqName("android.util.Log"))
            ?: throw IllegalStateException("Cannot find android.util.Log class")

        val logDSymbol = logClass.owner.functions.firstOrNull {
            it.name.asString() == "d" && it.valueParameters.size == 2
        }?.symbol ?: throw IllegalStateException("Cannot find Log.d function")

        val logISymbol = logClass.owner.functions.firstOrNull {
            it.name.asString() == "i" && it.valueParameters.size == 2
        }?.symbol ?: throw IllegalStateException("Cannot find Log.i function")

        buildCallGraph(moduleFragment)
        calculateFunctionDepths()
        instrumentFunctions(moduleFragment, pluginContext, logDSymbol, logISymbol)
    }

    private fun buildCallGraph(moduleFragment: IrModuleFragment) {
        moduleFragment.accept(object : IrElementVisitorVoid {
            private var currentFunction: String? = null
            private var currentClass: IrClass? = null

            private fun isMainDispatcher(expression: IrCall): Boolean {
                return expression.valueArguments.any { arg ->
                    arg?.toString()?.contains("Dispatchers.Main") == true ||
                            arg?.toString()?.contains("MainCoroutineDispatcher") == true
                }
            }

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
                    currentFunction = if (declaration is IrConstructor) {
                        "${parentClass.name}_<init>"
                    } else {
                        "${parentClass.name}_${declaration.name}"
                    }

                    callGraph.putIfAbsent(currentFunction!!, mutableSetOf())

                    // Проверяем, выполняется ли функция в контексте существующей корутины
                    val isInCoroutineContext = asyncStack.any { it.isInsideCoroutine && !it.isMainThread }
                    if (isInCoroutineContext) {
                        asyncContexts[declaration.name.asString()] = asyncStack.last().depth
                        currentAsyncContext[declaration.name.asString()] = asyncStack.last().parentFunction ?: ""
                    }

                    // Анализируем тело функции на предмет новых корутин
                    declaration.body?.accept(object : IrElementVisitorVoid {
                        override fun visitElement(element: IrElement) {
                            element.acceptChildren(this, null)
                        }

                        override fun visitCall(expression: IrCall) {
                            val calledFunction = expression.symbol.owner
                            val calledFunctionName = calledFunction.name.asString()
                            val calledClass = calledFunction.parent as? IrClass
                            val calledClassName = calledClass?.name?.asString() ?: ""

                            when {
                                // Проверяем корутины
                                calledFunctionName in setOf("launch", "async", "withContext") -> {
                                    val isMainThread = isMainDispatcher(expression)

                                    if (!isMainThread) {
                                        asyncStack.add(AsyncContext().apply {
                                            isInsideCoroutine = true
                                            depth = asyncStack.size + 1
                                            parentFunction = currentFunction
                                            this.isMainThread = isMainThread
                                        })

                                        // Обновляем контекст для функций внутри корутины
                                        val functionName = declaration.name.asString()
                                        asyncContexts[functionName] = asyncStack.last().depth
                                        asyncCallChains[functionName] = when {
                                            calledClassName.contains("lifecycleScope") ->
                                                "lifecycleScope.${calledFunctionName} -> $functionName"
                                            else -> "${previousFunction ?: "Unknown"} -> ${calledFunctionName} -> $functionName"
                                        }

                                        expression.acceptChildren(this, null)
                                        asyncStack.removeLastOrNull()
                                    } else {
                                        // Для корутин на главном потоке просто обрабатываем содержимое
                                        expression.acceptChildren(this, null)
                                    }
                                }

                                // Проверяем Flow операции
                                calledFunctionName in setOf("collect", "collectLatest", "observe") ||
                                        calledClassName.contains("Flow") -> {
                                    if (!asyncStack.any { it.isInsideCoroutine && !it.isMainThread }) {
                                        asyncStack.add(AsyncContext().apply {
                                            isInsideCoroutine = true
                                            depth = asyncStack.size + 1
                                            parentFunction = currentFunction
                                            isMainThread = false
                                        })

                                        val functionName = declaration.name.asString()
                                        asyncContexts[functionName] = asyncStack.last().depth
                                        asyncCallChains[functionName] =
                                            "${previousFunction ?: "Unknown"} -> $calledFunctionName -> $functionName"
                                    }
                                }

                                // Проверяем Timer операции
                                calledClassName.contains("Timer") ||
                                        calledFunctionName.contains("schedule") -> {
                                    if (!asyncStack.any { it.isInsideCoroutine && !it.isMainThread }) {
                                        asyncStack.add(AsyncContext().apply {
                                            isInsideCoroutine = true
                                            depth = asyncStack.size + 1
                                            parentFunction = currentFunction
                                            isMainThread = false
                                        })

                                        val functionName = declaration.name.asString()
                                        asyncContexts[functionName] = asyncStack.last().depth
                                        asyncCallChains[functionName] =
                                            "${previousFunction ?: "Unknown"} -> Timer -> $functionName"
                                    }
                                }
                            }
                            super.visitCall(expression)
                        }
                    }, null)

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
                    val calledFunctionId = if (calledFunction is IrConstructor) {
                        "${calledClass.name}_<init>"
                    } else {
                        "${calledClass.name}_${calledFunction.name}"
                    }
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
        logDSymbol: IrSimpleFunctionSymbol,
        logISymbol: IrSimpleFunctionSymbol
    ) {
        moduleFragment.accept(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildren(this, null)
            }

            override fun visitFunction(declaration: IrFunction) {
                val parentClass = declaration.parent as? IrClass
                if (parentClass != null &&
                    parentClass.annotations.any { it.type.classFqName == loggerAnnotationFqName }) {
                    val functionId = if (declaration is IrConstructor) {
                        "${parentClass.name}_<init>"
                    } else {
                        "${parentClass.name}_${declaration.name}"
                    }
                    val depth = functionDepths[functionId] ?: 0
                    val logSymbol = if (parentClass.name.asString().contains("ViewModel"))
                        logISymbol else logDSymbol
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
        val body = declaration.body
        when (body) {
            is IrBlockBody -> {
                val newStatements = mutableListOf<IrStatement>()

                // Entry log
                newStatements.add(createLogStatement(
                    context,
                    logSymbol,
                    "-->",
                    declaration.name.asString(),
                    depth,
                    parentClass.name.asString()
                ))

                // Process existing statements
                body.statements.forEach { statement ->
                    if (statement is IrReturn) {
                        // Add exit log before return
                        newStatements.add(createLogStatement(
                            context,
                            logSymbol,
                            "<--",
                            declaration.name.asString(),
                            depth,
                            parentClass.name.asString()
                        ))
                    }
                    newStatements.add(statement)
                }

                // Add exit log if no return statement
                if (!body.statements.any { it is IrReturn }) {
                    newStatements.add(createLogStatement(
                        context,
                        logSymbol,
                        "<--",
                        declaration.name.asString(),
                        depth,
                        parentClass.name.asString()
                    ))
                }

                declaration.body = IrBlockBodyImpl(
                    body.startOffset,
                    body.endOffset,
                    newStatements
                )
            }

            is IrExpressionBody -> {
                // Similar handling for expression bodies
                val statements = mutableListOf<IrStatement>()

                statements.add(createLogStatement(
                    context,
                    logSymbol,
                    "-->",
                    declaration.name.asString(),
                    depth,
                    parentClass.name.asString()
                ))

                statements.add(body.expression)

                if (body.expression !is IrReturn) {
                    statements.add(createLogStatement(
                        context,
                        logSymbol,
                        "<--",
                        declaration.name.asString(),
                        depth,
                        parentClass.name.asString()
                    ))
                }

                declaration.body = IrBlockBodyImpl(
                    body.startOffset,
                    body.endOffset,
                    statements
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