package com.tpov.logger_compiler_plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * Главный класс плагина компилятора Kotlin.
 * Этот плагин регистрирует расширения для работы с IR кодом.
 * 
 * @see CompilerPluginRegistrar - базовый класс для всех плагинов компилятора Kotlin
 */
@OptIn(ExperimentalCompilerApi::class)
class LoggerPlugin : CompilerPluginRegistrar() {
    // ВАЖНО: Мы не можем поддерживать K2 компилятор (Kotlin 2.0)
    // потому что наш код использует API, которые не совместимы с K2
    // Если вы хотите использовать Kotlin 2.0, необходимо переписать плагин
    // с использованием новых API для K2 компилятора
    override val supportsK2: Boolean = false

    /**
     * Регистрирует расширения компилятора.
     * Эта функция вызывается компилятором Kotlin во время компиляции проекта.
     * 
     * @param configuration - конфигурация компилятора, содержит настройки и параметры
     */
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        // Получаем коллектор сообщений для вывода информации во время компиляции
        val messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        
        // Выводим информационное сообщение, что наш плагин активирован
        messageCollector.report(
            CompilerMessageSeverity.INFO,
            "LoggerPlugin: registerExtensions called"
        )
        
        // Регистрируем расширения для чтения и записи IR кода
        // IR (Intermediate Representation) - промежуточное представление кода в компиляторе
        IrGenerationExtension.registerExtension(IrGenerationExtensionReader())
        IrGenerationExtension.registerExtension(IrGenerationExtensionWriter())
    }
}
