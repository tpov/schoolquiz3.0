package com.tpov.schoolquiz.platform.firebase.push

import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Кому сейчас принадлежит токен устройства.
 *
 * Отдельная ветвь для гостя, а не `emptyFlow()`: пустой поток неотличим от «ещё не узнали»
 * (инвариант 8, NFR8), а «нет адресата» — наблюдаемое состояние, по которому ничего не пишется.
 */
sealed interface TokenRecipient {
    data object Nobody : TokenRecipient

    data class Account(
        val uid: String,
        val token: String,
    ) : TokenRecipient
}

/**
 * Держит документ `users/{uid}/devices/{token}` в соответствии с текущим аккаунтом (Story 10.2).
 *
 * Построено на `observeUid()` через `flatMapLatest`: `uid` не хранится в поле, при смене аккаунта
 * поток пересоздаётся, при появлении `uid` в том же процессе токен дописывается сам. Время берётся
 * из общего источника, а не из вызова часов в адаптере (AD-9, NFR3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceTokenRegistrar(
    private val authRepository: AuthRepository,
    private val tokenSource: DeviceTokenSource,
    private val store: DeviceTokenStore,
    private val nowMs: () -> Long,
    private val scope: CoroutineScope,
) : DeviceTokenRelease {
    fun recipients(): Flow<TokenRecipient> =
        authRepository.observeUid()
            .flatMapLatest { uid ->
                if (uid.isNullOrBlank()) {
                    flowOf(TokenRecipient.Nobody)
                } else {
                    tokenSource.observeToken().map { token -> TokenRecipient.Account(uid, token) }
                }
            }
            .distinctUntilChanged()

    /** Запускает регистрацию на всё время жизни процесса. */
    fun start(): Job =
        scope.launch {
            recipients().collect { recipient ->
                if (recipient is TokenRecipient.Account) {
                    // Нет сети — документ допишется при следующей выдаче токена или входе; падать
                    // из-за этого нельзя.
                    runCatching { store.register(recipient.uid, recipient.token, nowMs()) }
                }
            }
        }

    override suspend fun release(uid: String) {
        if (uid.isBlank()) return
        val token = runCatching { tokenSource.currentToken() }.getOrNull() ?: return
        runCatching { store.detach(uid, token) }
    }

    override suspend fun reclaim(uid: String) {
        if (uid.isBlank()) return
        val token = runCatching { tokenSource.currentToken() }.getOrNull() ?: return
        // Тот же документ, что писал бы поток регистрации; сам поток его не перепишет — адресат
        // с его точки зрения не менялся (`distinctUntilChanged`).
        runCatching { store.register(uid, token, nowMs()) }
    }
}
