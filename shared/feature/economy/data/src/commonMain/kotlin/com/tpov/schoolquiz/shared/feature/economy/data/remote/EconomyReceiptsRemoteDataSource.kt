package com.tpov.schoolquiz.shared.feature.economy.data.remote

import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyReceipt
import kotlinx.coroutines.flow.Flow

/**
 * Чтение квитанций владельца (история 5.4).
 *
 * Правила разрешают игроку читать только свои `users/{uid}/receipts` и не разрешают писать:
 * квитанция — то, как «вам начислено 60 золота» доезжает до устройства. Свежие первыми; список
 * ограничен — история начислений не лента новостей.
 */
interface EconomyReceiptsRemoteDataSource {
    fun observeReceipts(limit: Int): Flow<List<EconomyReceipt>>
}
