package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimStatus
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftClaimRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftClaimRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Суточный бонус забирается только явным тапом, и повтор тапа не создаёт вторую операцию.
 */
class ClaimPremiumDailyGiftUseCaseTest {
    private val claims = FakeRepo()

    @Test fun freshTapSendsExactlyOnePremiumIntent() =
        runTest {
            val outcome = ClaimPremiumDailyGiftUseCase(claims).invoke().getOrThrow()

            assertEquals(GiftClaimKind.PREMIUM_DAILY, claims.submits.single().kind)
            assertEquals(GiftClaimStatus.CLAIMED, outcome.status)
        }

    @Test fun secondTapResumesTheSameIntentInsteadOfSubmittingAnother() =
        runTest {
            val useCase = ClaimPremiumDailyGiftUseCase(claims)
            claims.undecided = true
            useCase.invoke()
            assertEquals(1, claims.submits.size)

            claims.undecided = false
            useCase.invoke()

            assertEquals(1, claims.submits.size, "вторая заявка — тот же operationId, а не новая операция")
            assertEquals(2, claims.resumes, "каждый тап сначала спрашивает незавершённое")
            assertEquals(GiftClaimStatus.CLAIMED, useCase.invoke().getOrThrow().status)
        }

    @Test fun limitAndExpiryAreOutcomesRatherThanFailures() =
        runTest {
            claims.nextStatus = GiftClaimStatus.DAILY_LIMIT
            assertEquals(GiftClaimStatus.DAILY_LIMIT, ClaimPremiumDailyGiftUseCase(claims).invoke().getOrThrow().status)

            claims.nextStatus = GiftClaimStatus.PREMIUM_REQUIRED
            assertEquals(GiftClaimStatus.PREMIUM_REQUIRED, ClaimPremiumDailyGiftUseCase(claims).invoke().getOrThrow().status)
        }

    private class FakeRepo : GiftClaimRepository {
        val submits = mutableListOf<GiftClaimRequest>()
        var resumes = 0
        var undecided = false
        var nextStatus: GiftClaimStatus? = null
        private val pending = MutableStateFlow<GiftClaimOutcome?>(null)

        override suspend fun submit(request: GiftClaimRequest): Result<GiftClaimOutcome> {
            submits += request
            if (undecided) return Result.failure(IllegalStateException("no decision"))
            val outcome =
                GiftClaimOutcome(
                    request.kind, nextStatus ?: GiftClaimStatus.CLAIMED, false, 1, 2, 3, 4,
                    if (nextStatus == GiftClaimStatus.CLAIMED) 1 else 0, 5,
                )
            pending.value = null
            return Result.success(outcome)
        }

        override suspend fun resumePending(kind: GiftClaimKind): Result<GiftClaimOutcome>? {
            resumes++
            return when (val stored = pending.value) {
                null -> {
                    if (undecided) {
                        // Нерешённая заявка остаётся лежать — её и подхватит повтор.
                        pending.value = GiftClaimOutcome(kind, GiftClaimStatus.CLAIMED, false, 1, 2, 3, 4, 1, 5)
                    }
                    null
                }
                else -> Result.success(stored)
            }
        }

        override fun observePending(kind: GiftClaimKind): Flow<Boolean> = MutableStateFlow(false)
    }
}
