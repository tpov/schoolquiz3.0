package com.tpov.logger_processor

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.name.FqName

object Core {

    var countThread: Int = 0
    var countAsync: Int = 0
    var maxCountThread: Int = 0
    val asyncList: HashMap<String, Pair<Int, Int>> = hashMapOf()

    val asyncFunctionNames = listOf(
        "launch", "async", "withContext", "runBlocking",
        "withTimeout", "withTimeoutOrNull", "awaitAll", "joinAll",
        "flowOn", "onEach", "collect", "launchIn",
        "send", "receive", "consumeEach", "actor"
    )

    val loggerAnnotationFqName = FqName("com.tpov.log_api.logger.Logger")
    val callGraph = mutableListOf<FunctionCall>()

    fun isRootFunction(declaration: IrFunction): Boolean {
        val isCalledFromLoggerClass = (declaration.symbol.owner.parent as? IrClass)?.annotations?.any {
            it.type.classFqName == loggerAnnotationFqName
        } ?: false

        return !isCalledFromLoggerClass
    }

    fun isRootFunction(pathList: List<String>) = pathList.size == 1

    fun getFunctionFullName(function: IrFunction): String {
        val parentClass = function.parent as? IrClass
        val packageName = parentClass?.packageFqName?.asString() ?: "unknown"
        val className = parentClass?.name?.asString() ?: "unknown"
        return "$packageName.${className}_${function.name}"
    }

}