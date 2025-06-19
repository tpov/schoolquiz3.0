package com.tpov.logger_compiler_plugin

import com.tpov.logger_compiler_plugin.Core.getFunctionFullName
import com.tpov.logger_compiler_plugin.Core.loggerAnnotationFqName
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockBodyImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid

class IrGenerationExtensionWriter : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val callTreeBuilder = IrGenerationExtensionReader.lastCallTreeBuilder
        if (callTreeBuilder == null) {
            return
        }

        val collectedCallData = callTreeBuilder.callTreeData

        moduleFragment.accept(object : IrElementVisitorVoid {
            override fun visitElement(element: org.jetbrains.kotlin.ir.IrElement) {
                element.acceptChildren(this, null)
            }

            override fun visitFunction(declaration: IrFunction) {
                val parentClass = declaration.parent as? IrClass
                if (parentClass?.hasAnnotation(loggerAnnotationFqName) == true && declaration.body != null) {
                    val functionSymbol = declaration.symbol
                    val contexts = collectedCallData[functionSymbol]

                    if (contexts != null && contexts.isNotEmpty()) {
                        generateLogsForFunction(declaration, contexts, pluginContext)
                    }
                }
                super.visitFunction(declaration)
            }
        }, null)
    }

    private fun generateLogsForFunction(
        function: IrFunction,
        contexts: List<CallContextData>,
        pluginContext: IrPluginContext
    ) {
        val logProvider = try {
            AndroidLogProvider(pluginContext)
        } catch (e: IllegalStateException) {
            LogProvider.create(pluginContext)
        }

        val functionName = function.name.toString()
        // Получаем имена параметров функции
        val parameterNames = function.valueParameters
            .filter { it.index >= 0 } // Отфильтровываем специальные параметры (например, $this)
            .joinToString(", ") { it.name.asString() }


        val logCalls = contexts.mapNotNull { contextData ->
            val threadIndent = " ".repeat(THREAD_COLUMN_WIDTH * contextData.threadId)
            val callIndent = "|   ".repeat(contextData.depth)

            // val pathString = contextData.path.joinToString(" -> ") { pathSymbol -> // Закомментировано
            //     Core.getFunctionFullName(pathSymbol.owner)
            // }

            val logMessage = buildString {
                append(threadIndent)
                if (contextData.depth > 0 || (contextData.isAsyncRoot && contextData.threadId > 0) ) {
                    append(callIndent)
                }
                if (contextData.isAsyncRoot) append("*")
                append("[T${contextData.threadId}] ")
                append(functionName)
                if (parameterNames.isNotEmpty()) { // Добавляем параметры, если они есть
                    append("($parameterNames)")
                }
                // append(" (Path: $pathString)") // Путь все еще закомментирован
            }

            val logSymbol = logProvider.logDSymbol // Уровень по умолчанию Debug

            if (logProvider is JavaLogProvider) {
                IrCallImpl(
                    SYNTHETIC_OFFSET, SYNTHETIC_OFFSET,
                    pluginContext.irBuiltIns.unitType,
                    logSymbol,
                    typeArgumentsCount = 0,
                    valueArgumentsCount = 1
                ).apply {
                    putValueArgument(0, IrConstImpl.string(SYNTHETIC_OFFSET, SYNTHETIC_OFFSET, pluginContext.irBuiltIns.stringType, "[LG] $logMessage"))
                }
            } else {
                IrCallImpl(
                    SYNTHETIC_OFFSET, SYNTHETIC_OFFSET,
                    pluginContext.irBuiltIns.unitType,
                    logSymbol,
                    typeArgumentsCount = 0,
                    valueArgumentsCount = 2
                ).apply {
                    putValueArgument(0, IrConstImpl.string(SYNTHETIC_OFFSET, SYNTHETIC_OFFSET, pluginContext.irBuiltIns.stringType, "LoggerPlugin"))
                    putValueArgument(1, IrConstImpl.string(SYNTHETIC_OFFSET, SYNTHETIC_OFFSET, pluginContext.irBuiltIns.stringType, logMessage))
                }
            }
        }

        if (logCalls.isNotEmpty()) {
            function.body = function.body?.let { originalBody ->
                IrBlockBodyImpl(
                    originalBody.startOffset,
                    originalBody.endOffset,
                    logCalls + originalBody.statements
                )
            }
        }
    }

    companion object {
        private const val THREAD_COLUMN_WIDTH = 25
    }
}
