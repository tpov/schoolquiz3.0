package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.chargeClaimsOrEmpty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.claimCharge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Трата заряда — заявка, и она обязана дожить до отправки.
 *
 * До сих пор трата жила только в памяти экрана: число уменьшалось, на сервер не уезжало ничего, и
 * подсказка была бесплатной всегда.
 */
class ChargeClaimRecordingTest {

    private fun ready(
        mode: Difficulty = Difficulty.EASY,
        index: Int = 0,
    ) = makeReadyState(
        mode = mode,
        playOrder = List(3) { makeRunnerQuestion(id = "q$it", order = it, codeAnswerIndex = it) },
        indexInPool = index,
    )

    @Test
    fun `a run with no claims carries an empty mask, not a missing one`() {
        // Сервер сверяет длины: маска короче `codeAnswer` — испорченный payload, а не «без заявок».
        val state = ready()

        assertEquals(state.codeAnswer.raw.length, state.chargeClaimsOrEmpty().raw.length)
        assertEquals(false, state.chargeClaimsOrEmpty().hasClaims)
    }

    @Test
    fun `a hint claims the position of the question it was asked on`() {
        val state = claimCharge(ready(index = 1), ChargeClaimMask.Claim.STANDARD_HINT)

        val expectedIndex = state.playOrder[1].codeAnswerIndex
        assertEquals(ChargeClaimMask.STANDARD, state.chargeClaimsOrEmpty().raw[expectedIndex])
        assertEquals(1, state.chargeClaimsOrEmpty().count(ChargeClaimMask.STANDARD))
    }

    @Test
    fun `two hints on two questions claim two positions`() {
        val first = claimCharge(ready(index = 0), ChargeClaimMask.Claim.STANDARD_HINT)
        val second = claimCharge(first.copy(indexInPool = 1), ChargeClaimMask.Claim.STANDARD_HINT)

        assertEquals(2, second.chargeClaimsOrEmpty().count(ChargeClaimMask.STANDARD))
    }

    @Test
    fun `the kind follows the difficulty of the attempt, not the caller`() {
        // Заявить пропуск там, где ответ был показан, — соврать серверу: он засчитает вопрос верным.
        assertFailsWith<IllegalArgumentException> {
            claimCharge(ready(mode = Difficulty.EASY), ChargeClaimMask.Claim.PLASMA_SKIP)
        }
        assertFailsWith<IllegalArgumentException> {
            claimCharge(ready(mode = Difficulty.HARD), ChargeClaimMask.Claim.STANDARD_HINT)
        }
    }

    @Test
    fun `a claim on a hard attempt is a skip`() {
        val state = claimCharge(ready(mode = Difficulty.HARD), ChargeClaimMask.Claim.PLASMA_SKIP)

        assertEquals(1, state.chargeClaimsOrEmpty().count(ChargeClaimMask.PLASMA))
    }
}
