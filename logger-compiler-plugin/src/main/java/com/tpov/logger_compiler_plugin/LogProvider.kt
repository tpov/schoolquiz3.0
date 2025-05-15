package com.tpov.logger_compiler_plugin

import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.util.functions
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
            return try {
                // Пробуем создать Android логгер
                AndroidLogProvider(pluginContext)
            } catch (e: IllegalStateException) {
                // Если Android Logger недоступен, используем Java Logger
                JavaLogProvider(pluginContext)
            }
        }
    }
}

/**
 * Реализация LogProvider для Android-среды
 */
@OptIn(FirIncompatiblePluginAPI::class)
class AndroidLogProvider(pluginContext: IrPluginContext) : LogProvider {
    private val logClass = pluginContext.referenceClass(FqName("android.util.Log"))
        ?: throw IllegalStateException("Cannot find android.util.Log class")

    override val logVSymbol = logClass.owner.functions.firstOrNull {
        it.name.asString() == "v" && it.valueParameters.size == 2
    }?.symbol ?: throw IllegalStateException("Cannot find Log.v function")

    override val logDSymbol = logClass.owner.functions.firstOrNull {
        it.name.asString() == "d" && it.valueParameters.size == 2
    }?.symbol ?: throw IllegalStateException("Cannot find Log.d function")

    override val logISymbol = logClass.owner.functions.firstOrNull {
        it.name.asString() == "i" && it.valueParameters.size == 2
    }?.symbol ?: throw IllegalStateException("Cannot find Log.i function")

    override val logWSymbol = logClass.owner.functions.firstOrNull {
        it.name.asString() == "w" && it.valueParameters.size == 2
    }?.symbol ?: throw IllegalStateException("Cannot find Log.w function")

    override val logESymbol = logClass.owner.functions.firstOrNull {
        it.name.asString() == "e" && it.valueParameters.size == 2
    }?.symbol ?: throw IllegalStateException("Cannot find Log.e function")

    override val logWtfSymbol = logClass.owner.functions.firstOrNull {
        it.name.asString() == "wtf" && it.valueParameters.size == 2
    }?.symbol ?: throw IllegalStateException("Cannot find Log.wtf function")
}

/**
 * Реализация LogProvider для Java/Kotlin-среды, использующая System.out
 */
@OptIn(FirIncompatiblePluginAPI::class)
class JavaLogProvider(pluginContext: IrPluginContext) : LogProvider {
    private val printlnMethod = pluginContext.referenceFunctions(FqName("kotlin.io.println"))
        .firstOrNull { it.owner.valueParameters.size == 1 }
        ?: throw IllegalStateException("Cannot find kotlin.io.println function")

    // В Java/Kotlin среде все уровни логирования используют один и тот же println
    override val logVSymbol: IrSimpleFunctionSymbol = printlnMethod
    override val logDSymbol: IrSimpleFunctionSymbol = printlnMethod
    override val logISymbol: IrSimpleFunctionSymbol = printlnMethod
    override val logWSymbol: IrSimpleFunctionSymbol = printlnMethod
    override val logESymbol: IrSimpleFunctionSymbol = printlnMethod
    override val logWtfSymbol: IrSimpleFunctionSymbol = printlnMethod
} 