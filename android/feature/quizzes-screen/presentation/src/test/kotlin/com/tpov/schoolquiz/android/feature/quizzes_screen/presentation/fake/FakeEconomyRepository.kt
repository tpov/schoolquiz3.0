package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.LessonUnlockKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ReferralProgram
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopPurchaseResult
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory economy: unlocks are whatever the test put in, and buying adds one. */
class FakeEconomyRepository(
    initial: EconomyResourceBalance = EconomyResourceBalance(),
) : EconomyRepository {

    private val state = MutableStateFlow(initial)
    val unlockCalls = mutableListOf<Pair<String, LessonUnlockKind>>()

    /** When set, every unlock fails with this — the "not enough nolics" path. */
    var unlockFailure: Throwable? = null

    /** Held open so a test can have two purchases in flight at once. */
    var unlockGate: CompletableDeferred<Unit>? = null

    fun emit(balance: EconomyResourceBalance) {
        state.value = balance
    }

    override fun observeBalance(): Flow<EconomyResourceBalance> = state

    override suspend fun currentBalance(): EconomyResourceBalance = state.value

    override suspend fun purchase(itemId: ShopItemId): Result<ShopPurchaseResult> =
        Result.failure(UnsupportedOperationException("not used in these tests"))

    override suspend fun unlockLesson(
        lessonId: String,
        kind: LessonUnlockKind,
    ): Result<EconomyResourceBalance> {
        unlockCalls += lessonId to kind
        unlockGate?.await()
        unlockFailure?.let { return Result.failure(it) }
        val updated = state.value.copy(
            lessonUnlocks = state.value.lessonUnlocks + kind.keyFor(lessonId),
        )
        state.value = updated
        return Result.success(updated)
    }

    override suspend fun referralProgram(): ReferralProgram = ReferralProgram("", emptyList())
}
