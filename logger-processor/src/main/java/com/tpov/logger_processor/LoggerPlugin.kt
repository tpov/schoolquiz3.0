package com.tpov.logger_processor

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

@OptIn(ExperimentalCompilerApi::class)
class LoggerPlugin : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = false

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        messageCollector.report(
            CompilerMessageSeverity.INFO,
            "LoggerPlugin: registerExtensions called"
        )
        IrGenerationExtension.registerExtension(IrGenerationExtensionReader())
        IrGenerationExtension.registerExtension(IrGenerationExtensionWriter())

    }
}
