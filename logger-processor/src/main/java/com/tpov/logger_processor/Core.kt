package com.tpov.logger_processor

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.name.FqName

object Core {

    var maxCountThread: Int = 0
    val asyncList: HashMap<String, Pair<Int, Int>> = hashMapOf()

    val asyncFunctionNames = listOf(
        "launch", "async", "withContext", "runBlocking",
        "withTimeout", "withTimeoutOrNull", "awaitAll", "joinAll",
        "flowOn", "onEach", "collect", "launchIn",
        "send", "receive", "consumeEach", "actor"
    )

    val loggerAnnotationFqName = FqName("com.tpov.log_api.logger.Logger")

    fun isRootFunction(functionName: String): Boolean {
        return !functionName.contains("->")
    }

    fun getFunctionFullName(function: IrFunction): String {
        val parentClass = function.parent as? IrClass
        val packageName = parentClass?.packageFqName?.asString() ?: "unknown"
        val className = parentClass?.name?.asString() ?: "unknown"
        return "$packageName.${className}_${function.name}"
    }

}