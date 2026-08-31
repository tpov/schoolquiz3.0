package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.LessonUnlockKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ReferralProgram
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopPurchaseResult
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory economy: unlocks are whatever the test put in, and buying adds one. */
class FakeEconomyRepository(
    initial: EconomyResourceBalance = EconomyResourceBalance(),
) : EconomyRepository {

    private val state = MutableStateFlow(initial)
    val unlockCalls = mutableListOf<Pair<String, LessonUnlockKind>>()

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
        val updated = state.value.copy(
            lessonUnlocks = state.value.lessonUnlocks + kind.keyFor(lessonId),
        )
        state.value = updated
        return Result.success(updated)
    }

    override suspend fun referralProgram(): ReferralProgram = ReferralProgram("", emptyList())
}
