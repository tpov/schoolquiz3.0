package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyReceiptsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyReceipt
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.LessonUnlockKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ReferralProgram
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Квитанции доходят до репозитория как есть: источник заперт на auth платформы (гость получает
 * пустое там, где начислений и не бывает), репозиторий передаёт поток и лимит без пересчёта.
 */
class EconomyRepositoryReceiptsTest {

    private class FakeReceipts : EconomyReceiptsRemoteDataSource {
        var askedLimit: Int? = null
        override fun observeReceipts(limit: Int): Flow<List<EconomyReceipt>> {
            askedLimit = limit
            return flowOf(
                listOf(
                    EconomyReceipt(
                        id = "season_leaderboard_2026-Q1", kind = "SEASON_LEADERBOARD",
                        messageKey = "receipt.season_leaderboard",
                        params = mapOf("rank" to "1", "boxes" to "500"),
                        amount = 500, currency = "BOXES", createdAtMs = 1,
                    ),
                    EconomyReceipt(
                        id = "referral_bonus", kind = "REFERRAL_BONUS",
                        messageKey = "receipt.referral_bonus",
                        params = mapOf("invitees" to "6", "each" to "10", "boxes" to "50"),
                        amount = 50, currency = "BOXES", createdAtMs = 2,
                    ),
                ),
            )
        }
    }

    private class EmptyLocal : EconomyLocalDataSource {
        override fun observe(uid: String) = flowOf(null)
        override suspend fun find(uid: String) = null
        override suspend fun upsert(uid: String, balance: EconomyResourceBalance) = Unit
    }

    private class EmptyRemote : com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyRemoteDataSource {
        override suspend fun purchase(request: com.tpov.schoolquiz.shared.feature.economy.data.remote.ShopPurchaseRequest) =
            EconomyResourceBalance.guest()

        override suspend fun unlockLesson(request: com.tpov.schoolquiz.shared.feature.economy.data.remote.LessonUnlockRequest) =
            EconomyResourceBalance.guest()
    }

    private fun repository(receipts: FakeReceipts, uid: String? = "owner"): EconomyRepository =
        EconomyRepositoryImpl(
            local = EmptyLocal(),
            remote = EmptyRemote(),
            receipts = receipts,
            currentUidFlow = { flowOf(uid) },
        )

    @Test
    fun `receipts flow through and the default page limit travels`() = runTest {
        val receipts = FakeReceipts()
        val observed = repository(receipts).observeReceipts().first()
        assertEquals(EconomyRepository.RECEIPTS_PAGE_LIMIT, receipts.askedLimit)
        assertEquals(2, observed.size)
        assertEquals("receipt.referral_bonus", observed[1].messageKey)
    }

    @Test
    fun `a custom limit travels`() = runTest {
        val receipts = FakeReceipts()
        repository(receipts).observeReceipts(5).first()
        assertEquals(5, receipts.askedLimit)
    }

    @Test
    fun `a guest reads nothing and never reaches the source`() = runTest {
        val receipts = FakeReceipts()
        val observed = repository(receipts, uid = null).observeReceipts().first()
        assertEquals(emptyList(), observed)
        assertEquals(null, receipts.askedLimit)
    }
}
