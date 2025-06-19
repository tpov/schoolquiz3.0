package com.tpov.logger_compiler_plugin

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.name.FqName

object Core {
    // Новая структура для хранения данных для IrGenerationExtensionWriter (возможно, временная)
    // Ключ - полное имя функции (String), значение - список пар (threadId, depth)
    val functionData = mutableMapOf<String, MutableList<Pair<Int, Int>>>()

    fun addFunctionData(functionFullName: String, threadId: Int, depth: Int) {
        functionData.computeIfAbsent(functionFullName) { mutableListOf() }.add(Pair(threadId, depth))
    }

    fun clearData() {
        functionData.clear()
    }

    val asyncFunctionNames = listOf(
        "launch", "async", "withContext", "runBlocking",
        "withTimeout", "withTimeoutOrNull", "awaitAll", "joinAll",
        "flowOn", "onEach", "collect", "launchIn",
        "send", "receive", "consumeEach", "actor"
        // Добавьте сюда другие функции, которые считаются началом асинхронного блока
    )

    val loggerAnnotationFqName = FqName("com.tpov.log_api.logger.Logger")

    // isRootFunction больше не нужен в таком виде, пути строятся динамически

    fun getFunctionFullName(function: IrFunction): String {
        val parentClass = function.parent as? IrClass
        val packageName = parentClass?.packageFqName?.asString() ?: function.packageFqName?.asString() ?: "unknown_package"
        val className = parentClass?.name?.asString()
        return if (className != null) {
            "$packageName.${className}_${function.name.asString()}"
        } else {
            // Для функций верхнего уровня или функций без явного класса-родителя (например, в файлах)
            "$packageName.${function.name.asString()}"
        }
    }
}