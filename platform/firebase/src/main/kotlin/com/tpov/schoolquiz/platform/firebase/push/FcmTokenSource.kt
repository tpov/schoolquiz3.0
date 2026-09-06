package com.tpov.schoolquiz.platform.firebase.push

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart

/**
 * Токен FCM: последний известный плюс каждый новый из `onNewToken`.
 *
 * Первое чтение спрашивает токен у SDK; сервис приёма дописывает следующие через [publish].
 */
class FcmTokenSource(
    private val fetchToken: suspend () -> String?,
) : DeviceTokenSource {
    private val latest = MutableStateFlow<String?>(null)

    override fun observeToken(): Flow<String> =
        latest
            .onStart { if (latest.value == null) refresh() }
            .filterNotNull()

    override suspend fun currentToken(): String? = latest.value ?: refresh()

    fun publish(token: String) {
        if (token.isNotBlank()) latest.value = token
    }

    private suspend fun refresh(): String? {
        val token = runCatching { fetchToken() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (token != null) latest.value = token
        return token
    }
}
