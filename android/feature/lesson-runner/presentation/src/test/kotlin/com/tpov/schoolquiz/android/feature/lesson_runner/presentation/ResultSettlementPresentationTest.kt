package com.tpov.schoolquiz.android.feature.lesson_runner.presentation

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.settlementStatusText
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptSyncMark
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptReward
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlementState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResultSettlementPresentationTest {
    private fun result(settlement: ServerAttemptSettlement) = RunnerUiState.Result(
        percentScore = PercentScore(88), mode = Difficulty.HARD, completedAt = 10L,
        hardUnlocked = true, bestStarsRawTenths = 28, currentAttemptStarsRawTenths = 28,
        lessonAverageRating = null, lessonRatingCount = 0, top3 = emptyList(), userAttemptCount = 1,
        userAveragePercentScore = 88, showRatingPrompt = true, saveWarning = false, serverSettlement = settlement,
    )

    private fun confirmed() = ServerAttemptSettlement(
        state = ServerAttemptSettlementState.SETTLED, receiptId = "receipt", codeAnswer = CodeAnswer("1"),
        percentScore = PercentScore(0), completed = true, settledAtMs = 20L,
        lifePointsSpent = 50L, plasmaPointsSpent = 100L, reward = ServerAttemptReward(0L, 0L),
        ownerVerified = true,
    )

    @Test
    fun waitingUnscorableLegacyRefusalAndAccountLossShowNoScoreOrReward() {
        val unknownStates = ServerAttemptSettlementState.entries.filter { it != ServerAttemptSettlementState.SETTLED }
        val wording = unknownStates.map { state ->
            val value = result(ServerAttemptSettlement(state))
            assertNull(value.displayedPercentScore)
            assertNull(value.displayedReward)
            assertFalse(value.serverSettlement.isConfirmedCompletion)
            settlementStatusText(value.serverSettlement)
        }
        assertEquals(unknownStates.size, wording.toSet().size, "Unknown states must not share a misleading generic label")
    }

    @Test
    fun serverZeroIsShownAndNotReplacedWithLocalEightyEightOrHardRewards() {
        val value = result(confirmed())
        assertTrue(value.serverSettlement.isConfirmedCompletion)
        assertEquals(0, value.displayedPercentScore?.raw)
        assertEquals(ServerAttemptReward(0L, 0L), value.displayedReward)
        assertEquals(R.string.runner_settlement_confirmed, settlementStatusText(value.serverSettlement))
    }

    @Test
    fun accountingOnlyHasNeutralLabelAndNoRating() {
        val value = result(confirmed().copy(completed = false))
        assertEquals(0, value.displayedPercentScore?.raw)
        assertFalse(value.canSubmitRating)
        assertFalse(value.serverSettlement.isConfirmedCompletion)
        assertEquals(R.string.runner_settlement_accounting_only, settlementStatusText(value.serverSettlement))
    }

    @Test
    fun malformedAdapterCannotCreateGreenOrDisplayNegativeUnsafeAmounts() {
        listOf(
            confirmed().copy(receiptId = null), confirmed().copy(percentScore = null),
            confirmed().copy(reward = ServerAttemptReward(-1L, 0L)),
            confirmed().copy(lifePointsSpent = 9_007_199_254_740_992L),
        ).forEach {
            val value = result(it)
            assertFalse(value.serverSettlement.isConfirmedCompletion)
            assertNull(value.displayedPercentScore)
            assertNull(value.displayedReward)
            assertFalse(value.canSubmitRating)
        }
    }

    @Test
    fun onlyConfirmedSavedOwnedNonQuarantinedCompletionCanRate() {
        val value = result(confirmed())
        assertTrue(value.canSubmitRating)
        assertFalse(value.copy(syncMark = AttemptSyncMark.QUARANTINED).canSubmitRating)
        assertFalse(value.copy(saveWarning = true).canSubmitRating)
        assertFalse(result(ServerAttemptSettlement(ServerAttemptSettlementState.PENDING, ownerVerified = true)).canSubmitRating)
        assertFalse(result(ServerAttemptSettlement(ServerAttemptSettlementState.PENDING)).canSubmitRating,
            "Before the current owner is observed, no rating may be queued")
    }
}
