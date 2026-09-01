package com.tpov.schoolquiz.shared.core.scoring

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Заявка — не транзакция: клиент пишет, что знает, а поднимает позицию только оплата. */
class ChargeClaimMaskTest {

    @Test
    fun `given a paid skip then the position becomes fully correct`() {
        val settled = settleClaims(ChargeClaimMask("..P."), CodeAnswer("9905"), standardAvailable = 0, plasmaAvailable = 1)

        assertEquals("9995", settled.codeAnswer.raw)
        assertEquals(listOf(2), settled.paid)
        assertEquals(1, settled.plasmaChargesPaid)
    }

    @Test
    fun `given no plasma then the skip stays unanswered, which is what it was`() {
        // Неоплаченная заявка не наказывает второй раз: вопрос просто засчитан как неотвеченный.
        val settled = settleClaims(ChargeClaimMask("..P."), CodeAnswer("9905"), standardAvailable = 0, plasmaAvailable = 0)

        assertEquals("9905", settled.codeAnswer.raw)
        assertEquals(listOf(2), settled.unpaid)
        assertEquals(0, settled.plasmaChargesPaid)
    }

    @Test
    fun `given fewer charges than claims then the earliest claims are the ones paid`() {
        // Частичная оплата платит за самые ранние заявки, а не за произвольное подмножество.
        val settled = settleClaims(ChargeClaimMask("P.PP"), CodeAnswer("0000"), standardAvailable = 0, plasmaAvailable = 2)

        assertEquals(listOf(0, 2), settled.paid)
        assertEquals(listOf(3), settled.unpaid)
        assertEquals("9090", settled.codeAnswer.raw)
    }

    @Test
    fun `given the order the questions were asked then that order decides who gets paid`() {
        val settled =
            settleClaims(
                ChargeClaimMask("P.PP"),
                CodeAnswer("0000"),
                standardAvailable = 0,
                plasmaAvailable = 1,
                order = listOf(3, 2, 0),
            )

        assertEquals(listOf(3), settled.paid)
        assertEquals("0009", settled.codeAnswer.raw)
    }

    @Test
    fun `given a hint then payment is taken but the digit is what the player answered`() {
        // Подсказка показала ответ; цифра — то, что игрок после этого ответил. Поднимать нечего.
        val settled = settleClaims(ChargeClaimMask("S..."), CodeAnswer("7999"), standardAvailable = 3, plasmaAvailable = 0)

        assertEquals("7999", settled.codeAnswer.raw)
        assertEquals(1, settled.standardChargesPaid)
    }

    @Test
    fun `given a client that wrote 9 under its own skip then the mask is refused`() {
        // Клиент заявляет ответ, которого не давал, — и маска делает это различимым.
        assertEquals(
            ClaimMaskFault.SKIP_ON_ANSWERED,
            ChargeClaimMask("P...").validateAgainst(CodeAnswer("9111"), Difficulty.HARD),
        )
    }

    @Test
    fun `given a claim of the wrong kind for the difficulty then the mask is refused`() {
        // Обычный заряд не касается сложного вопроса; плазма не платит за лёгкий (CAP-1).
        assertEquals(ClaimMaskFault.WRONG_DIFFICULTY, ChargeClaimMask("S...").validateAgainst(CodeAnswer("0111"), Difficulty.HARD))
        assertEquals(ClaimMaskFault.WRONG_DIFFICULTY, ChargeClaimMask("P...").validateAgainst(CodeAnswer("0111"), Difficulty.EASY))
    }

    @Test
    fun `given a mask of another length then it is refused`() {
        assertEquals(ClaimMaskFault.LENGTH_MISMATCH, ChargeClaimMask("P..").validateAgainst(CodeAnswer("0111"), Difficulty.HARD))
    }

    @Test
    fun `given an honest mask then it passes`() {
        assertNull(ChargeClaimMask("P.P.").validateAgainst(CodeAnswer("0905"), Difficulty.HARD))
        assertNull(ChargeClaimMask("S..S").validateAgainst(CodeAnswer("7995"), Difficulty.EASY))
        assertNull(ChargeClaimMask.none(4).validateAgainst(CodeAnswer("1234"), Difficulty.EASY))
    }

    @Test
    fun `given a character outside the alphabet then the mask cannot even be built`() {
        assertFailsWith<IllegalArgumentException> { ChargeClaimMask("P.X.") }
    }

    @Test
    fun `given no claims then nothing is paid and nothing changes`() {
        val settled = settleClaims(ChargeClaimMask.none(3), CodeAnswer("123"), standardAvailable = 5, plasmaAvailable = 5)

        assertEquals("123", settled.codeAnswer.raw)
        assertEquals(0, settled.standardChargesPaid + settled.plasmaChargesPaid)
    }
}
