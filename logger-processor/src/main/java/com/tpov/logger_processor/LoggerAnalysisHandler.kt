package com.tpov.logger_processor

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment


class ModuleCollector : IrGenerationExtension {
    val modules = mutableListOf<IrModuleFragment>()

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        modules.add(moduleFragment)
    }
}

class LoggerTransformer() : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {

    }
}