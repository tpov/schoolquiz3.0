package com.tpov.schoolquiz.shared.feature.economy.domain.logic

import com.tpov.schoolquiz.shared.feature.economy.domain.model.ChargeKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ChargeRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Заряды, потраченные без связи, стоят на нуле, пока сервер их не учтёт. */
class OfflineChargeLedgerTest {

    private val standard = ChargeRules.STANDARD_BOOTSTRAP
    private val plasma = ChargeRules.PLASMA_BOOTSTRAP

    private fun ledger(kind: ChargeKind, server: Int, unsettled: Int = 0) = OfflineChargeLedger(kind, server, unsettled)

    @Test
    fun `given three charges spent offline then the shown balance is zero`() {
        var ledger = ledger(ChargeKind.STANDARD, server = 3)
        repeat(3) { ledger = (ledger.claim(standard) as ClaimOutcome.Accepted).ledger }

        assertEquals(0, ledger.available)
        assertTrue(ledger.hasUnsettled)
    }

    @Test
    fun `given a full regeneration period offline then the balance is still zero`() {
        // Сценарий из постановки: потратить три, переждать восстановление, потратить три ещё.
        // Вторая трата обязана быть недоступна на устройстве — иначе сервер видит шесть против трёх.
        var ledger = ledger(ChargeKind.STANDARD, server = 3)
        repeat(3) { ledger = (ledger.claim(standard) as ClaimOutcome.Accepted).ledger }

        val regenerated = ledger.regeneratedTo(3)

        assertEquals(0, regenerated.available, "восстановление не доливает то, чего сервер ещё не списал")
        assertIs<ClaimOutcome.Refused>(regenerated.claim(standard))
    }

    @Test
    fun `given the server settled then its word replaces everything`() {
        var ledger = ledger(ChargeKind.STANDARD, server = 3)
        repeat(3) { ledger = (ledger.claim(standard) as ClaimOutcome.Accepted).ledger }

        val after = ledger.settled(serverCharges = 1)

        assertEquals(1, after.available)
        assertTrue(!after.hasUnsettled)
    }

    @Test
    fun `given an unsettled claim then plasma is refused and says why`() {
        // Не «нет сети», а «сервер ещё не сказал своё слово». Вернётся после синхронизации.
        val ledger = ledger(ChargeKind.PLASMA, server = 3, unsettled = 1)

        val outcome = ledger.claim(plasma)

        assertEquals(ClaimOutcome.Refused(ClaimRefusal.WAITING_FOR_SETTLEMENT), outcome)
    }

    @Test
    fun `given an unsettled claim then a standard charge is still spendable`() {
        // Обычный заряд — мотивационный: тратится офлайн, учитывается потом.
        val ledger = ledger(ChargeKind.STANDARD, server = 3, unsettled = 1)

        assertIs<ClaimOutcome.Accepted>(ledger.claim(standard))
    }

    @Test
    fun `given nothing left then the refusal names that, not settlement`() {
        val ledger = ledger(ChargeKind.STANDARD, server = 0)

        assertEquals(ClaimOutcome.Refused(ClaimRefusal.NOTHING_LEFT), ledger.claim(standard))
    }

    @Test
    fun `given more claims than the server ever granted then available floors at zero`() {
        // Сервер мог оплатить меньше, чем заявлено; показанное всё равно не уходит в минус.
        assertEquals(0, ledger(ChargeKind.STANDARD, server = 1, unsettled = 5).available)
    }

    @Test
    fun `given regeneration reports less than known then the known figure is kept`() {
        // Местный пересчёт не может отнять то, что сервер уже сказал.
        assertEquals(3, ledger(ChargeKind.STANDARD, server = 3).regeneratedTo(2).serverCharges)
    }
}
