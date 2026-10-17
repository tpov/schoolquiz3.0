package com.tpov.schoolquiz.platform.firebase.tournament

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import kotlinx.coroutines.tasks.await

/** Only synchronous SDK Task creation belongs inside the session gate. */
internal fun interface TournamentCallableExchange {
    fun start(
        name: String,
        payload: Map<String, Any?>,
    ): TournamentPendingCall
}

/** Await outside the gate. Cancellation does not imply that the already-created SDK Task had no effect. */
internal fun interface TournamentPendingCall {
    suspend fun await(): Any?
}

internal fun firebaseTournamentCallableExchange(functions: FirebaseFunctions): TournamentCallableExchange =
    TournamentCallableExchange { name, payload ->
        val task =
            try {
                functions.getHttpsCallable(name).withAppTimeout().call(payload)
            } catch (error: FirebaseFunctionsException) {
                throw TournamentCallableException(error.code.name, error.details, error)
            }
        TournamentPendingCall {
            try {
                task.await().data
            } catch (error: FirebaseFunctionsException) {
                throw TournamentCallableException(error.code.name, error.details, error)
            }
        }
    }

/**
 * SDK-метаданные отказа вызова: имя кода и структурные детали, никогда message/cause.
 * Причина сервера читается из `details.reason` — и только из неё; текст сообщения не разобрается
 * нигде (AD-15). Internal-шов держит JVM-тесты свободными от поднятия Firebase.
 */
internal class TournamentCallableException(
    val code: String,
    val details: Any?,
    cause: Throwable? = null,
) : Exception(cause)
