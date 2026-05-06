package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
import com.tpov.schoolquiz.shared.feature.economy.data.mapper.mergeWithBalance
import com.tpov.schoolquiz.shared.feature.economy.data.mapper.toBalance
import com.tpov.schoolquiz.shared.feature.economy.data.mapper.toNewUserStatsEntity
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface EconomyLocalDataSource {
    fun observe(uid: String): Flow<EconomyResourceBalance?>

    suspend fun find(uid: String): EconomyResourceBalance?

    suspend fun upsert(uid: String, balance: EconomyResourceBalance)
}

class RoomEconomyLocalDataSource(
    private val userStatsDao: UserStatsDao,
) : EconomyLocalDataSource {
    override fun observe(uid: String): Flow<EconomyResourceBalance?> =
        userStatsDao.observeByUid(uid).map { entity -> entity?.toBalance() }

    override suspend fun find(uid: String): EconomyResourceBalance? =
        userStatsDao.findByUid(uid)?.toBalance()

    override suspend fun upsert(uid: String, balance: EconomyResourceBalance) {
        val current = userStatsDao.findByUid(uid)
        userStatsDao.upsert(current?.mergeWithBalance(balance) ?: balance.toNewUserStatsEntity(uid))
    }
}
