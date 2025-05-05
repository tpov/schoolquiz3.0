package com.tpov.common

import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.presentation.model.PathStructure

open class ExceptionHandler(
    val beforeException: (String) -> Unit,
    val afterException: () -> Unit,
    val interactor: Interactor
) {

    inline fun <reified T> handleException(
        message: String,
        interactorAction: () -> Unit
    ): T {
        beforeException(message)
        interactorAction()
        interactor.sendErrorToRemote()
        afterException()
        return getDefaultValue()
    }

    inline fun <reified T> getDefaultValue(): T = when (T::class) {
        Int::class -> -1 as T
        java.lang.Integer::class -> -1 as T
        String::class -> "" as T
        Boolean::class -> false as T
        java.lang.Boolean::class -> false as T
        Float::class -> -1f as T
        java.lang.Float::class -> -1f as T
        Double::class -> -1.0 as T
        java.lang.Double::class -> -1.0 as T
        Long::class -> -1L as T
        java.lang.Long::class -> -1L as T
        List::class -> emptyList<Any>() as T
        Set::class -> emptySet<Any>() as T
        Map::class -> emptyMap<Any, Any>() as T
        StructureDataLocal::class -> StructureDataLocal() as T
        PathStructure::class -> PathStructure(-1, -1, -1, -1, -1) as T
        Unit::class -> Unit as T
        else -> null as T
    }

}