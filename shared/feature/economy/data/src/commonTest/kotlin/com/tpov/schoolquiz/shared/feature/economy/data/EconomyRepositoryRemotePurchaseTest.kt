package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyReceiptsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.LessonUnlockRequest
import com.tpov.schoolquiz.shared.feature.economy.data.remote.ShopPurchaseRequest
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyReceipt
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Покупка полки доезжает до сервера: гейт удалённых покупок — единственное, что стоит между тапом
 * и вызовом callable. Выпавший из белого списка элемент (например, PREMIUM из истории 2.2)
 * отшиб бы покупку на клиенте молчаливым «Пока недоступно» — и ни один другой тест бы не заметил.
 */
class EconomyRepositoryRemotePurchaseTest {

    private class RecordingRemote : EconomyRemoteDataSource {
        val purchases = mutableListOf<ShopPurchaseRequest>()
        override suspend fun purchase(request: ShopPurchaseRequest): EconomyResourceBalance {
            purchases += request
            return EconomyResourceBalance.guest()
        }

        override suspend fun unlockLesson(request: LessonUnlockRequest): EconomyResourceBalance =
            EconomyResourceBalance.guest()
    }

    private class EmptyLocal : EconomyLocalDataSource {
        override fun observe(uid: String) = flowOf(null)
        override suspend fun find(uid: String) = null
        override suspend fun upsert(uid: String, balance: EconomyResourceBalance) = Unit
    }

    private class EmptyReceipts : EconomyReceiptsRemoteDataSource {
        override fun observeReceipts(limit: Int): Flow<List<EconomyReceipt>> = flowOf(emptyList())
    }

    private fun repository(remote: EconomyRemoteDataSource): EconomyRepositoryImpl =
        EconomyRepositoryImpl(
            local = EmptyLocal(),
            remote = remote,
            receipts = EmptyReceipts(),
            currentUidFlow = { flowOf("owner") },
        )

    @Test
    fun `premium rides the remote purchase gate to the callable`() = runTest {
        val remote = RecordingRemote()

        val result = repository(remote).purchase(ShopItemId.PREMIUM)

        assertTrue(result.isSuccess, "премиум продаётся с сервера, а не «Пока недоступно»")
        assertEquals(listOf(ShopPurchaseRequest("PREMIUM")), remote.purchases)
    }

    @Test
    fun `an item off the remote list is refused before any network call`() = runTest {
        val remote = RecordingRemote()

        val result = repository(remote).purchase(ShopItemId.QUIZ_SLOT)

        assertTrue(result.isFailure, "не-серверный элемент не ходит на callable")
        assertEquals(emptyList<ShopPurchaseRequest>(), remote.purchases)
    }
}
