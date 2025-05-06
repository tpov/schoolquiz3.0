package com.tpov.logger_compiler_plugin

import com.tpov.logger_compiler_plugin.Core.asyncList
import com.tpov.logger_compiler_plugin.Core.loggerAnnotationFqName
import com.tpov.logger_compiler_plugin.ReadCodeUtils.getPathWithRootFunction
import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockBodyImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrWhenImpl
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid

class IrGenerationExtensionWriter : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        buildCallGraph(moduleFragment, pluginContext)
    }

    private fun buildCallGraph(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.accept(object : IrElementVisitorVoid {
            private var currentClass: IrClass? = null

            override fun visitElement(element: IrElement) {
                element.acceptChildren(this, null)
            }

            override fun visitClass(declaration: IrClass) {
                currentClass = declaration
                super.visitClass(declaration)
            }

            override fun visitFunction(declaration: IrFunction) {
                val parentClass = declaration.parent as? IrClass
                if (parentClass?.annotations?.any { it.type.classFqName == loggerAnnotationFqName } == true) {
                    val pathList = getPathWithRootFunction(declaration)
                    declaration.generateLogs(pathList, pluginContext)
                    super.visitFunction(declaration)
                } else super.visitFunction(declaration)
            }
        }, null)
    }

    // IrGenerationExtensionWriter.kt
    @OptIn(FirIncompatiblePluginAPI::class)
    private fun IrFunction.generateLogs(
        pathList: Set<String>,
        pluginContext: IrPluginContext
    ) {
        val logAndroid = LogAndroid(pluginContext)
        val functionName = name.toString()

        val findFunAsyncList = asyncList
            .filter { it.key in pathList }
            .toList()

        val logCalls = findFunAsyncList.map { (path, threadInfo) ->
            val threadIndent = " ".repeat(THREAD_INDENT_SIZE * threadInfo.first)
            val callIndent = "|   ".repeat(getCountCals(Pair(path, threadInfo)))

            // Формируем сообщение лога
            val logMessage = buildString {
                append(threadIndent)
                append(callIndent)
                append("[Thread ${threadInfo}] ")
                append("Entering function: $functionName")
                append(" (Path: $path)")
                append(" (findFunAsyncList: $findFunAsyncList)")
            }

            val logLevel = DetectArchLayer.getIdLayer(this)
            val logSymbol = when (logLevel) {
                1 -> logAndroid.logVSymbol // Presentation layer - Verbose
                2 -> logAndroid.logDSymbol // ViewModel layer - Debug
                3 -> logAndroid.logISymbol // Domain layer - Info
                4 -> logAndroid.logWSymbol // Data layer - Warning
                else -> logAndroid.logESymbol // Unknown - Error
            }

            // Создаем вызов функции логирования
            IrCallImpl(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                pluginContext.irBuiltIns.unitType,
                logSymbol,
                typeArgumentsCount = 0,
                valueArgumentsCount = 2
            ).apply {
                putValueArgument(0, IrConstImpl.string(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    pluginContext.irBuiltIns.stringType,
                    "LoggerPlugin"
                ))
                putValueArgument(1, IrConstImpl.string(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    pluginContext.irBuiltIns.stringType,
                    logMessage
                ))
            }
        }

        // Создаем условные блоки для каждого пути
        val conditions = pathList.mapIndexed { index, path ->
            IrBranchImpl(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                // Условие: текущий путь совпадает с ожидаемым
                IrCallImpl(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    pluginContext.irBuiltIns.booleanType,
                    pluginContext.irBuiltIns.eqeqSymbol,
                    typeArgumentsCount = 0,
                    valueArgumentsCount = 2
                ).apply {
                    putValueArgument(0, IrConstImpl.string(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        pluginContext.irBuiltIns.stringType,
                        path
                    ))
                    putValueArgument(1, IrConstImpl.string(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        pluginContext.irBuiltIns.stringType,
                        path
                    ))
                },
                logCalls.getOrNull(index) ?: return@mapIndexed null
            )
        }.filterNotNull()

        // Создаем when-выражение для выбора правильного лога
        val whenStatement = IrWhenImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            pluginContext.irBuiltIns.unitType,
            IrStatementOrigin.IF
        ).apply {
            branches.addAll(conditions)
        }

        // Добавляем логирование в начало тела функции
        body = body?.let { originalBody ->
            IrBlockBodyImpl(
                originalBody.startOffset,
                originalBody.endOffset,
                listOf(whenStatement) + originalBody.statements
            )
        }
    }

    private fun getCountCals(findFunAsync: Pair<String, Pair<Int, Int>>): Int {
        val path = findFunAsync.first
        val parts = path.split("->")
        var calls = 0
        var countCalls = if (parts.size > 1) {
            parts.dropLast(1).joinToString("->")
        } else {
            path
        }

        var calledIdThread = asyncList[countCalls]

        while (findFunAsync.second.first == calledIdThread?.first
            && countCalls.split("->").size > 1) {

            calls++
            countCalls = if (parts.size > 1) {
                parts.dropLast(1).joinToString("->")
            } else {
                path
            }

            calledIdThread = asyncList[countCalls]
        }

        return calls
    }

    companion object {
        private const val TAG = "LoggerTag"
        private const val THREAD_INDENT_SIZE = 50
        private const val REGULAR_INDENT = "|    "
    }
}
