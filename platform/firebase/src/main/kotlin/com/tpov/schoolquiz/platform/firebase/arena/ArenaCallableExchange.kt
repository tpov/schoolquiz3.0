package com.tpov.schoolquiz.platform.firebase.arena

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import kotlinx.coroutines.tasks.await

/** Only synchronous SDK Task creation belongs inside the shared owner gate. */
internal fun interface ArenaCallableExchange {
    fun start(
        name: String,
        payload: Map<String, Any?>,
    ): ArenaPendingCall
}

/** Await outside the gate. Cancellation does not imply that the already-created SDK Task had no effect. */
internal fun interface ArenaPendingCall {
    suspend fun await(): Any?
}

internal fun firebaseArenaCallableExchange(functions: FirebaseFunctions): ArenaCallableExchange =
    ArenaCallableExchange { name, payload ->
        val task =
            try {
                functions.getHttpsCallable(name).withAppTimeout().call(payload)
            } catch (error: FirebaseFunctionsException) {
                throw ArenaCallableException(error.code.name, error.details, error)
            }
        ArenaPendingCall {
            try {
                task.await().data
            } catch (error: FirebaseFunctionsException) {
                throw ArenaCallableException(error.code.name, error.details, error)
            }
        }
    }
