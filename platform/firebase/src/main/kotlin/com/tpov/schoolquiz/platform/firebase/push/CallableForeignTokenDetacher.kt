package com.tpov.schoolquiz.platform.firebase.push

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Зовёт `detachDeviceToken` — единственную точку, откуда клиент просит снять чужой токен
 * (Story 10.3, 10.5).
 *
 * Уходит в фон в области процесса: сервис приёма живёт секунды, а вызов может ждать ответа до
 * таймаута. Неудача глотается — следующее чужое сообщение попросит снова.
 */
class CallableForeignTokenDetacher(
    private val functions: FirebaseFunctions,
    private val tokenSource: DeviceTokenSource,
    private val scope: CoroutineScope,
) : ForeignTokenDetacher {
    override fun detach(targetUid: String) {
        scope.launch {
            val token = runCatching { tokenSource.currentToken() }.getOrNull() ?: return@launch
            runCatching {
                functions
                    .getHttpsCallable(DETACH_DEVICE_TOKEN)
                    .withAppTimeout()
                    .call(mapOf(TOKEN to token, TARGET_UID to targetUid))
                    .await()
            }
        }
    }

    companion object {
        const val DETACH_DEVICE_TOKEN = "detachDeviceToken"
        const val TOKEN = "token"
        const val TARGET_UID = "targetUid"
    }
}
