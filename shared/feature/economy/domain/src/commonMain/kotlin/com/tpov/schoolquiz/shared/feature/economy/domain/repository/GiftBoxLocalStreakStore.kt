package com.tpov.schoolquiz.shared.feature.economy.domain.repository

import com.tpov.schoolquiz.shared.feature.economy.domain.logic.GiftBoxStreak

/**
 * Местный счётчик серии между словами сервера.
 *
 * Пока связи нет, серия живёт здесь: по одному дню за визит на входе, с обрывом за пропущенные
 * сутки — и ни одной коробки содержимым, его решает сервер. [base] — слово сервера, от которого
 * счёт был посеян: расхождение с текущим словом означает, что сервер сказал новое, и счёт
 * пересеивается, а не складывается с чужим.
 */
interface GiftBoxLocalStreakStore {
    suspend fun find(ownerUid: String): LocalBoxStreak?

    suspend fun save(
        ownerUid: String,
        value: LocalBoxStreak,
    )

    suspend fun forget(ownerUid: String)
}

data class LocalBoxStreak(
    /** Последнее серверное слово, от которого посеян местный счёт. */
    val base: GiftBoxStreak,
    /** Сам местный счёт: серия и коробки, как их насчитало устройство. */
    val counted: GiftBoxStreak,
    val updatedAtMs: Long,
)
