package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.core.persistence.GiftBoxLocalStreakDao
import com.tpov.schoolquiz.shared.core.persistence.GiftBoxLocalStreakEntity
import com.tpov.schoolquiz.shared.feature.economy.domain.logic.GiftBoxStreak
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftBoxLocalStreakStore
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.LocalBoxStreak

class RoomGiftBoxLocalStreakStore(
    private val dao: GiftBoxLocalStreakDao,
) : GiftBoxLocalStreakStore {
    override suspend fun find(ownerUid: String): LocalBoxStreak? = dao.find(ownerUid)?.toLocal()

    override suspend fun save(
        ownerUid: String,
        value: LocalBoxStreak,
    ) {
        dao.upsert(value.toEntity(ownerUid))
    }

    override suspend fun forget(ownerUid: String) {
        dao.delete(ownerUid)
    }

    private fun GiftBoxLocalStreakEntity.toLocal(): LocalBoxStreak {
        val base = GiftBoxStreak(baseBoxCount, baseStreakDays, baseNextBoxAtMs)
        return LocalBoxStreak(base, GiftBoxStreak(boxCount, streakDays, nextBoxAtMs), updatedAtMs)
    }

    private fun LocalBoxStreak.toEntity(ownerUid: String) =
        GiftBoxLocalStreakEntity(
            ownerUid = ownerUid,
            baseBoxCount = base.boxCount,
            baseStreakDays = base.streakDays,
            baseNextBoxAtMs = base.nextBoxAtMs,
            boxCount = counted.boxCount,
            streakDays = counted.streakDays,
            nextBoxAtMs = counted.nextBoxAtMs,
            updatedAtMs = updatedAtMs,
        )
}
