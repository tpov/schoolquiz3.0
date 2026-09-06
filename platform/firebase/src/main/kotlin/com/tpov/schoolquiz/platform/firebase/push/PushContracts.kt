package com.tpov.schoolquiz.platform.firebase.push

import kotlinx.coroutines.flow.Flow

/** Показывает уведомление в шторке. Реализация решает, можно ли показывать вообще. */
fun interface PushNotifier {
    fun notify(event: PushEvent)
}

/**
 * Просит сервер снять с этого устройства токен, записанный под чужим `uid` (Story 10.5).
 *
 * Не suspend и не бросает: вызов уходит в фон, а сервис приёма возвращает управление сразу.
 */
fun interface ForeignTokenDetacher {
    fun detach(targetUid: String)
}

/** Откуда берётся FCM-токен этого устройства. */
interface DeviceTokenSource {
    /** Актуальный токен и каждый последующий. Пока токена нет — ничего не эмитит. */
    fun observeToken(): Flow<String>

    suspend fun currentToken(): String?
}

/**
 * Документ `users/{uid}/devices/{token}` — пишется клиентом, читается сервером (AD-20).
 *
 * Сигнатуры без SDK-типов, чтобы поток регистрации проверялся фейком.
 */
interface DeviceTokenStore {
    /** Повторная запись того же токена перезаписывает тот же документ, второго не появляется. */
    suspend fun register(
        uid: String,
        token: String,
        updatedAtMs: Long,
    )

    suspend fun detach(
        uid: String,
        token: String,
    )
}

/**
 * Снять токен с аккаунта до того, как его сменят, и вернуть, если сменить не вышло (Story 10.5).
 *
 * После смены аутентификации правило владельца документ прежнего `uid` уже не отдаст — поэтому
 * снятие идёт до входа. Но вход может и не случиться: сеть, отозванный credential. Тогда прежний
 * аккаунт остаётся активным с уже снятым документом, а регистратор повторно его не запишет —
 * адресат с его точки зрения не менялся. Возврат закрывает эту дыру.
 */
interface DeviceTokenRelease {
    /** Не бросает: не удалось снять — не повод не пускать в другой аккаунт. */
    suspend fun release(uid: String)

    /** Не бросает: записывает текущий токен обратно под [uid], если снятие оказалось напрасным. */
    suspend fun reclaim(uid: String)
}
