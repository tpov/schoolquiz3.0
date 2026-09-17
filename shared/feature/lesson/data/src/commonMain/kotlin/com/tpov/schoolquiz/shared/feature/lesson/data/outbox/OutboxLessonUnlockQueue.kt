package com.tpov.schoolquiz.shared.feature.lesson.data.outbox

import com.tpov.schoolquiz.shared.core.persistence.LessonUnlockLocalResult
import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
import com.tpov.schoolquiz.shared.core.persistence.toOutboxEntity
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonUnlockOutcome
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonUnlockQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/**
 * Разблокировка урока: списание, открытие и запись очереди — одной транзакцией (AD-23).
 *
 * Прямого вызова callable больше нет: без сети он отвечал отказом вместо отложенного действия.
 * Урок при этом открывается сразу — решение владельца от 2026-09-03: нолики мягкая валюта, и
 * локальная половина для неё разрешена. Пока её не было, игрок платил и смотрел на закрытую
 * дверь до ближайшей синхронизации профиля.
 *
 * Своего замка здесь нет и не нужно: «посмотреть баланс → списать → положить строку» целиком
 * лежит внутри транзакции Room, и два быстрых нажатия расходятся на ней, а не рядом с ней.
 */
class OutboxLessonUnlockQueue(
    private val userStats: UserStatsDao,
    private val clock: Clock = Clock.System,
) : LessonUnlockQueue {
    override suspend fun queueUnlock(
        ownerUid: String,
        lessonId: LessonId,
        kindWireName: String,
        priceNolics: Int,
    ): Result<LessonUnlockOutcome> =
        try {
            require(priceNolics >= 0) { "priceNolics must be non-negative, was: $priceNolics" }
            val row =
                LessonUnlockOutbox.record(
                    ownerUid = ownerUid,
                    lessonId = lessonId,
                    kindWireName = kindWireName,
                    chargedNolics = priceNolics.toLong(),
                    createdAtMs = clock.now().toEpochMilliseconds(),
                ).toOutboxEntity()
            val local =
                userStats.unlockLessonWithOutbox(
                    uid = ownerUid,
                    unlockKey = LessonUnlockOutbox.unlockKey(kindWireName, lessonId),
                    priceNolics = priceNolics.toLong(),
                    outboxRow = row,
                )
            Result.success(local.toOutcome(priceNolics))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    override fun observeRefusedUnlocks(ownerUid: String): Flow<Set<LessonId>> =
        userStats
            .observeQuarantinedEntityRefs(ownerUid, LessonUnlockOutbox.OPERATION)
            .map { refs -> refs.mapNotNull(LessonUnlockOutbox::lessonIdOf).toSet() }
            .distinctUntilChanged()
}

/** Исход транзакции на языке домена: о таблицах фича наружу не рассказывает. */
private fun LessonUnlockLocalResult.toOutcome(priceNolics: Int): LessonUnlockOutcome =
    when (this) {
        LessonUnlockLocalResult.Opened -> LessonUnlockOutcome.Opened
        LessonUnlockLocalResult.AlreadyOpen -> LessonUnlockOutcome.AlreadyOpen
        LessonUnlockLocalResult.NoProfile -> LessonUnlockOutcome.NoProfile
        is LessonUnlockLocalResult.NotEnoughNolics ->
            LessonUnlockOutcome.NotEnoughNolics(
                priceNolics = priceNolics,
                balanceNolics = balanceNolics,
            )
    }
