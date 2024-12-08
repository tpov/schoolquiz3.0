package com.tpov.logger_processor

import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.name.FqName

class LogAndroid(pluginContext: IrPluginContext) {
    @OptIn(FirIncompatiblePluginAPI::class)
    val logClass = pluginContext.referenceClass(FqName("android.util.Log"))
        ?: throw IllegalStateException("Cannot find android.util.Log class")

    val logVSymbol = logClass.owner.functions.firstOrNull {
        it.name.asString() == "v" && it.valueParameters.size == 2
    }?.symbol ?: throw IllegalStateException("Cannot find Log.v function")

    // Debug - для отладочной информации
    val logDSymbol = logClass.owner.functions.firstOrNull {
        it.name.asString() == "d" && it.valueParameters.size == 2
    }?.symbol ?: throw IllegalStateException("Cannot find Log.d function")

    // Info - для информационных сообщений
    val logISymbol = logClass.owner.functions.firstOrNull {
        it.name.asString() == "i" && it.valueParameters.size == 2
    }?.symbol ?: throw IllegalStateException("Cannot find Log.i function")

    // Warning - для предупреждений
    val logWSymbol = logClass.owner.functions.firstOrNull {
        it.name.asString() == "w" && it.valueParameters.size == 2
    }?.symbol ?: throw IllegalStateException("Cannot find Log.w function")

    // Error - для ошибок
    val logESymbol = logClass.owner.functions.firstOrNull {
        it.name.asString() == "e" && it.valueParameters.size == 2
    }?.symbol ?: throw IllegalStateException("Cannot find Log.e function")

    // WTF - для критических ошибок (What a Terrible Failure)
    val logWtfSymbol = logClass.owner.functions.firstOrNull {
        it.name.asString() == "wtf" && it.valueParameters.size == 2
    }?.symbol ?: throw IllegalStateException("Cannot find Log.wtf function")
}