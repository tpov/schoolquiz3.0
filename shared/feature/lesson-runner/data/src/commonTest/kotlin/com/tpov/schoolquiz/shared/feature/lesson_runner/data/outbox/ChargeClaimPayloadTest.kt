package com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox

import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Заявка едет в строке очереди, а не в таблице попыток.
 *
 * Строка пишется в той же транзакции, что и сама попытка, поэтому заявка так же переживает
 * перезапуск — а места в схеме под неё не нужно: локально её никто не читает, платит по ней сервер.
 */
class ChargeClaimPayloadTest {

    @Test
    fun `a run with claims carries them beside the digits, not inside them`() {
        val mask = ChargeClaimMask.none(4).with(1, ChargeClaimMask.Claim.STANDARD_HINT)

        // Ключ отдельный, и `codeAnswer` остаётся цифрами — его разбор и зеркало на сервере целы.
        assertEquals("S", mask.raw.substring(1, 2))
        assertTrue(mask.hasClaims)
        assertEquals(4, mask.raw.length)
    }

    @Test
    fun `a run without claims has nothing to send`() {
        // Попытка без заявок обязана выглядеть на сервере ровно так, как выглядела до маски.
        assertTrue(!ChargeClaimMask.none(4).hasClaims)
    }
}
