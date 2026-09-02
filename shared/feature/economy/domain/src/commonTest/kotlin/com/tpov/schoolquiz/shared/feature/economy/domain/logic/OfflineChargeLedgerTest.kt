package com.tpov.schoolquiz.shared.feature.economy.domain.logic

import com.tpov.schoolquiz.shared.feature.economy.domain.model.ChargeKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ChargeRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Заряды, потраченные без связи, стоят на нуле, пока сервер их не учтёт. */
class OfflineChargeLedgerTest {

    private val standard = ChargeRules.STANDARD_BOOTSTRAP
    private val plasma = ChargeRules.PLASMA_BOOTSTRAP

    private fun ledger(
        standardServer: Int = 0,
        plasmaServer: Int = 0,
    ) = OfflineChargeLedger(ChargeLedgerLine(standardServer), ChargeLedgerLine(plasmaServer))

    private fun OfflineChargeLedger.claimed(kind: ChargeKind, rules: ChargeRules): OfflineChargeLedger =
        (claim(kind, rules) as ClaimOutcome.Accepted).ledger

    @Test
    fun `given three charges spent in a run then the shown balance is zero`() {
        var ledger = ledger(standardServer = 3)
        repeat(3) { ledger = ledger.claimed(ChargeKind.STANDARD, standard) }

        assertEquals(0, ledger.standard.available)
    }

    @Test
    fun `given a full regeneration period offline then the balance is still zero`() {
        // Сценарий из постановки: потратить три, переждать восстановление, потратить три ещё.
        // Вторая трата обязана быть недоступна на устройстве — иначе сервер видит шесть против трёх.
        var ledger = ledger(standardServer = 3)
        repeat(3) { ledger = ledger.claimed(ChargeKind.STANDARD, standard) }
        ledger = ledger.runEnded()

        val regenerated = ledger.regeneratedTo(ChargeKind.STANDARD, 3)

        assertEquals(0, regenerated.standard.available, "восстановление не доливает то, чего сервер ещё не списал")
        assertIs<ClaimOutcome.Refused>(regenerated.claim(ChargeKind.STANDARD, standard))
    }

    @Test
    fun `given the server settled then its word replaces everything`() {
        var ledger = ledger(standardServer = 3)
        repeat(3) { ledger = ledger.claimed(ChargeKind.STANDARD, standard) }
        ledger = ledger.runEnded()

        val after = ledger.settled(standardCharges = 1, plasmaCharges = 0)

        assertEquals(1, after.standard.available)
        assertFalse(after.hasUnsettledClaims)
    }

    @Test
    fun `given three plasma charges then all three may be skipped in one run`() {
        // Лимита на попытку нет — владелец отказался от него явно. Заявки идущей попытки — не долг:
        // расчёт бывает только в конце, и сервер учитывает попытку целиком.
        var ledger = ledger(plasmaServer = 3)
        repeat(3) { ledger = ledger.claimed(ChargeKind.PLASMA, plasma) }

        assertEquals(0, ledger.plasma.available)
        assertEquals(ClaimOutcome.Refused(ClaimRefusal.NOTHING_LEFT), ledger.claim(ChargeKind.PLASMA, plasma))
    }

    @Test
    fun `given an unsettled claim from a previous run then plasma is refused and says why`() {
        // Не «нет сети», а «сервер ещё не сказал своё слово». Вернётся после синхронизации.
        val ledger = ledger(plasmaServer = 3).claimed(ChargeKind.PLASMA, plasma).runEnded()

        assertEquals(ClaimOutcome.Refused(ClaimRefusal.WAITING_FOR_SETTLEMENT), ledger.claim(ChargeKind.PLASMA, plasma))
    }

    @Test
    fun `given an unsettled standard claim then plasma is refused too`() {
        // CAP-8 говорит об аккаунте, а не о плазме: любая неучтённая заявка запирает монетарный вид.
        val ledger = ledger(standardServer = 3, plasmaServer = 3).claimed(ChargeKind.STANDARD, standard).runEnded()

        assertEquals(ClaimOutcome.Refused(ClaimRefusal.WAITING_FOR_SETTLEMENT), ledger.claim(ChargeKind.PLASMA, plasma))
        assertIs<ClaimOutcome.Accepted>(ledger.claim(ChargeKind.STANDARD, standard), "а обычный заряд тратится и офлайн")
    }

    @Test
    fun `given an abandoned run then its claims cost nothing`() {
        // Что не потрачено в итоге, не обнуляется (CAP-15).
        val ledger = ledger(plasmaServer = 3).claimed(ChargeKind.PLASMA, plasma).runAbandoned()

        assertEquals(3, ledger.plasma.available)
        assertFalse(ledger.hasUnsettledClaims)
    }

    @Test
    fun `given nothing left then the refusal names that, not settlement`() {
        assertEquals(ClaimOutcome.Refused(ClaimRefusal.NOTHING_LEFT), ledger().claim(ChargeKind.STANDARD, standard))
    }

    @Test
    fun `given more claims than the server ever granted then available floors at zero`() {
        // Сервер мог оплатить меньше, чем заявлено; показанное всё равно не уходит в минус.
        assertEquals(0, ChargeLedgerLine(serverCharges = 1, unsettledClaims = 5).available)
    }

    @Test
    fun `given regeneration reports less than known then the known figure is kept`() {
        // Местный пересчёт не может отнять то, что сервер уже сказал.
        assertEquals(3, ledger(standardServer = 3).regeneratedTo(ChargeKind.STANDARD, 2).standard.serverCharges)
        assertTrue(OfflineChargeLedger.EMPTY.standard.available == 0)
    }
}
