package com.tpov.logger_compiler_plugin

import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isStatic
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/**
 * Интерфейс, определяющий методы логирования, которые должны быть реализованы
 */
interface LogProvider {
    val logVSymbol: IrSimpleFunctionSymbol
    val logDSymbol: IrSimpleFunctionSymbol
    val logISymbol: IrSimpleFunctionSymbol
    val logWSymbol: IrSimpleFunctionSymbol
    val logESymbol: IrSimpleFunctionSymbol
    val logWtfSymbol: IrSimpleFunctionSymbol

    companion object {
        /**
         * Фабричный метод для создания соответствующего провайдера логов
         */
        fun create(pluginContext: IrPluginContext): LogProvider {
            return if (isAndroidProject(pluginContext)) {
                try {
                    AndroidLogProvider(pluginContext)
                } catch (e: Exception) {
                    println("AndroidLogProvider недоступен: ${e.message}")
                    println("Используем JavaLogProvider")
                    JavaLogProvider(pluginContext)
                }
            } else {
                JavaLogProvider(pluginContext)
            }
        }

        /**
         * Определяем, является ли проект Android проектом
         */
        private fun isAndroidProject(pluginContext: IrPluginContext): Boolean {
            return try {
                // Проверяем наличие android классов через ClassId
                val androidLogClassId = ClassId.topLevel(FqName("android.util.Log"))
                val androidContextClassId = ClassId.topLevel(FqName("android.content.Context"))

                // Если найден хотя бы один Android класс - это Android проект
                pluginContext.referenceClass(androidLogClassId) != null ||
                    pluginContext.referenceClass(androidContextClassId) != null
            } catch (e: Exception) {
                false
            }
        }
    }
}

/**
 * Реализация LogProvider для Android-среды
 */
@OptIn(FirIncompatiblePluginAPI::class)
class AndroidLogProvider(pluginContext: IrPluginContext) : LogProvider {

    private val logClass = run {
        // Используем ClassId для правильного поиска Android классов
        val androidLogClassId = ClassId.topLevel(FqName("android.util.Log"))
        pluginContext.referenceClass(androidLogClassId)
            ?: throw IllegalStateException("Cannot find android.util.Log class")
    }

    override val logVSymbol = findLogMethod("v")
        ?: throw IllegalStateException("Cannot find Log.v function")

    override val logDSymbol = findLogMethod("d")
        ?: throw IllegalStateException("Cannot find Log.d function")

    override val logISymbol = findLogMethod("i")
        ?: throw IllegalStateException("Cannot find Log.i function")

    override val logWSymbol = findLogMethod("w")
        ?: throw IllegalStateException("Cannot find Log.w function")

    override val logESymbol = findLogMethod("e")
        ?: throw IllegalStateException("Cannot find Log.e function")

    override val logWtfSymbol = findLogMethod("wtf")
        ?: throw IllegalStateException("Cannot find Log.wtf function")

    /**
     * Ищет метод логирования с правильной сигнатурой
     */
    private fun findLogMethod(methodName: String): IrSimpleFunctionSymbol? {
        return logClass.owner.functions.firstOrNull { function ->
            function.name.asString() == methodName &&
                function.valueParameters.size == 2 &&
                function.isStatic
        }?.symbol
    }
}

/**
 * Реализация LogProvider для Java/Kotlin-среды, использующая System.out
 */
@OptIn(FirIncompatiblePluginAPI::class)
class JavaLogProvider(pluginContext: IrPluginContext) : LogProvider {

    private val printlnMethod = run {
        // Ищем функцию println через правильный FqName
        pluginContext.referenceFunctions(FqName("kotlin.io.println"))
            .firstOrNull { it.owner.valueParameters.size == 1 }
            ?: throw IllegalStateException("Cannot find kotlin.io.println function")
    }

    // В Java/Kotlin среде все уровни логирования используют один и тот же println
    override val logVSymbol: IrSimpleFunctionSymbol = printlnMethod
    override val logDSymbol: IrSimpleFunctionSymbol = printlnMethod
    override val logISymbol: IrSimpleFunctionSymbol = printlnMethod
    override val logWSymbol: IrSimpleFunctionSymbol = printlnMethod
    override val logESymbol: IrSimpleFunctionSymbol = printlnMethod
    override val logWtfSymbol: IrSimpleFunctionSymbol = printlnMethod
}
