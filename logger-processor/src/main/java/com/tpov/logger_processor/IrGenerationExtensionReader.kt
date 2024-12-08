package com.tpov.logger_processor

import com.tpov.logger_processor.Core.loggerAnnotationFqName
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid

class IrGenerationExtensionReader : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        buildCallGraph(moduleFragment)
    }

    private fun buildCallGraph(moduleFragment: IrModuleFragment) {
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
                    CreateAsyncData().initFunction(declaration)
                    super.visitFunction(declaration)
                } else super.visitFunction(declaration)
            }
        }, null)
    }

    companion object {
        private const val TAG = "LoggerTag"
        private const val THREAD_INDENT_SIZE = 50
        private const val REGULAR_INDENT = "|    "
    }
}