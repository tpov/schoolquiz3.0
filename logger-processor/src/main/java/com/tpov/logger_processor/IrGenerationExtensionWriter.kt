package com.tpov.logger_processor

import com.tpov.logger_processor.Core.loggerAnnotationFqName
import com.tpov.logger_processor.ReadCodeUtils.getPathWithRootFunction
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
import org.jetbrains.kotlin.name.FqName

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

    @OptIn(FirIncompatiblePluginAPI::class)
    private fun IrFunction.generateLogs(
        pathList: Set<String>,
        pluginContext: IrPluginContext
    ) {
        val logAndroid = LogAndroid(pluginContext)
        val functionName = "${name}"

        val logCalls = pathList.map { path ->
            val spaces = " ".repeat(path.count { it == '-' } * 50)
            val logStatement = "%s[Thread %d] Entering function: %s%n".format(spaces, Thread.currentThread().id, functionName)
            val logLevel = DetectArchLayer.getIdLayer(this)
            val logSymbol = when (logLevel) {
                1 -> logAndroid.logVSymbol
                2 -> logAndroid.logDSymbol
                3 -> logAndroid.logISymbol
                4 -> logAndroid.logWSymbol
                else -> logAndroid.logESymbol
            }

            IrCallImpl(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                pluginContext.irBuiltIns.unitType,
                logSymbol,
                typeArgumentsCount = 0,
                valueArgumentsCount = 2
            ).apply {
                putValueArgument(0, IrConstImpl.string(UNDEFINED_OFFSET, UNDEFINED_OFFSET, pluginContext.irBuiltIns.stringType, "MyTag"))
                putValueArgument(1, IrConstImpl.string(UNDEFINED_OFFSET, UNDEFINED_OFFSET, pluginContext.irBuiltIns.stringType, logStatement))
            }
        }

        val pathCondition = pathList.mapIndexed { index, path ->
            val condition = IrCallImpl(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                pluginContext.irBuiltIns.booleanType,
                pluginContext.irBuiltIns.eqeqSymbol,
                typeArgumentsCount = 0,
                valueArgumentsCount = 2
            ).apply {
                putValueArgument(0, IrConstImpl.string(UNDEFINED_OFFSET, UNDEFINED_OFFSET, pluginContext.irBuiltIns.stringType, path))
                putValueArgument(1, IrCallImpl(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    pluginContext.irBuiltIns.stringType,
                    pluginContext.referenceFunctions(FqName("com.tpov.logger_processor.ReadCodeUtils.getPathToThisFunction")).single(),
                    typeArgumentsCount = 0,
                    valueArgumentsCount = 0
                ))
            }
            if (index == 0) condition else null
        }.filterNotNull()

        val whenStatement = IrWhenImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            pluginContext.irBuiltIns.unitType,
            IrStatementOrigin.IF
        ).apply {
            branches.addAll(pathCondition.mapIndexed { index, condition ->
                IrBranchImpl(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    condition,
                    logCalls[index]
                )
            })
        }

        body = body?.let { IrBlockBodyImpl(it.startOffset, it.endOffset, listOf(whenStatement) + it.statements) }
    }

    companion object {
        private const val TAG = "LoggerTag"
        private const val THREAD_INDENT_SIZE = 50
        private const val REGULAR_INDENT = "|    "
    }
}