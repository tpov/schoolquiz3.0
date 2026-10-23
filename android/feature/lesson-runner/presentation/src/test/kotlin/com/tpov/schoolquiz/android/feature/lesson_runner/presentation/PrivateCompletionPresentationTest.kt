package com.tpov.schoolquiz.android.feature.lesson_runner.presentation

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlementState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Приватный прогон с выключенным показом результата (AD-29/UX-16, история 8.4).
 *
 * Сервер прислал только подтверждение завершения; клиент не рисует ни прочерков на месте
 * скрытых чисел, ни нулей: неизвестное — не ноль (AD-21). Флаг [RunnerUiState.Result.privateCompletionOnly]
 * — слово серверной красакции, а не UI-прятанье: до этого места счёт уже не доехал.
 */
class PrivateCompletionPresentationTest {

    private fun result(privateCompletionOnly: Boolean) =
        RunnerUiState.Result(
            percentScore = PercentScore(88),
            mode = Difficulty.HARD,
            completedAt = 10L,
            hardUnlocked = true,
            bestStarsRawTenths = 28,
            currentAttemptStarsRawTenths = 28,
            lessonAverageRating = null,
            lessonRatingCount = 0,
            top3 = emptyList(),
            userAttemptCount = 1,
            userAveragePercentScore = 88,
            showRatingPrompt = false,
            saveWarning = false,
            serverSettlement = ServerAttemptSettlement(ServerAttemptSettlementState.PENDING),
            privateCompletionOnly = privateCompletionOnly,
        )

    @Test
    fun privateCompletion_suppressesEveryDisplayedNumber() {
        val completionOnly = result(privateCompletionOnly = true)
        assertTrue(completionOnly.privateCompletionOnly)
        assertNull(completionOnly.displayedPercentScore, "счёт не подтверждён — процент не рисуется")
        assertNull(completionOnly.displayedReward, "награды у приватного прогона не существует")
        assertEquals(true, completionOnly.questionScores.isEmpty(), "по-вопросных цифр нет")
    }

    @Test
    fun ordinaryResult_keepsItsNumbers() {
        val ordinary = result(privateCompletionOnly = false)
        assertEquals(false, ordinary.privateCompletionOnly)
        assertEquals(false, ordinary.questionScores.isEmpty() && ordinary.percentScore == null)
    }
}
