package com.tpov.schoolquiz.shared.feature.economy.domain.logic

import com.tpov.schoolquiz.shared.feature.economy.domain.model.ActivityKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ChargeKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyConstants
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PayableTerms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Согласие на платные условия: когда старого согласия достаточно, а когда игрок должен согласиться
 * заново (CAP-16, клиентская половина AD-5).
 */
class TermsConsentGateTest {

    @Test
    fun `given no consent captured then fresh consent is required and no change can be named`() {
        val verdict = TermsConsentGate.evaluate(captured = null, current = EconomyConstants.BOOTSTRAP)

        val required = assertIs<TermsConsentVerdict.Required>(verdict)
        assertEquals(PayableTerms.of(EconomyConstants.BOOTSTRAP), required.to)
        assertNull(required.change, "первое согласие не с чем сравнивать — называть изменение нечем")
    }

    @Test
    fun `given consent captured on the very same terms then it is sufficient`() {
        val bootstrap = EconomyConstants.BOOTSTRAP

        val verdict = TermsConsentGate.evaluate(captured = PayableTerms.of(bootstrap), current = bootstrap)

        assertEquals(TermsConsentVerdict.Sufficient, verdict)
    }

    @Test
    fun `given consent captured on the bootstrap copy then it covers a republished table with the same prices`() {
        // Сервер опубликовал те же значения как версию 1 (первый призыв загрузочной копии в
        // настоящую таблицу): цены, с которыми игрок согласился, не изменились — согласие в силе,
        // номер версии согласия не решает.
        val republished = EconomyConstants.BOOTSTRAP.copy(version = 1L)
        val verdict = TermsConsentGate.evaluate(captured = PayableTerms.BOOTSTRAP, current = republished)

        assertEquals(TermsConsentVerdict.Sufficient, verdict)
    }

    @Test
    fun `given only the version and non-payable knobs changed then consent stays sufficient`() {
        // Сервер поправил допуск на часы и аудит — кошелёк игрока это не касается. Переспрос по
        // неплатной правке приучал бы соглашаться не глядя.
        val captured = PayableTerms.of(EconomyConstants.BOOTSTRAP)
        val current =
            EconomyConstants.BOOTSTRAP.copy(clockSkewToleranceMs = 30_000L, auditEnabled = false, version = 9L)

        assertEquals(TermsConsentVerdict.Sufficient, TermsConsentGate.evaluate(captured, current))
    }

    @Test
    fun `given a price rose then fresh consent is required and the exact change is named`() {
        val captured = PayableTerms.of(EconomyConstants.BOOTSTRAP)
        val current =
            EconomyConstants.BOOTSTRAP.copy(
                version = 3L,
                activityPrices = EconomyConstants.BOOTSTRAP_PRICES + (ActivityKind.TOURNAMENT to 900),
            )

        val required = assertIs<TermsConsentVerdict.Required>(TermsConsentGate.evaluate(captured, current))
        val change = requireNotNull(required.change)
        assertEquals(mapOf(ActivityKind.TOURNAMENT to PriceChange(from = 500, to = 900)), change.priceChanges)
        assertTrue(change.chargeRuleChanges.isEmpty())
        assertEquals(captured, change.from)
        assertEquals(PayableTerms.of(current), change.to)
    }

    @Test
    fun `given a charge rule changed then fresh consent is required even with every price intact`() {
        // Плазменный стал восстанавливаться вдвое быстрее за премиум: продаётся ускорение, значит
        // платное условие изменилось, хоть прейскурант попыток и не тронулся.
        val captured = PayableTerms.of(EconomyConstants.BOOTSTRAP)
        val current =
            EconomyConstants.BOOTSTRAP.copy(
                version = 4L,
                plasma = EconomyConstants.BOOTSTRAP.plasma.copy(premiumRegenDivisor = 2),
            )

        val required = assertIs<TermsConsentVerdict.Required>(TermsConsentGate.evaluate(captured, current))
        val change = requireNotNull(required.change)
        assertEquals(
            mapOf(ChargeKind.PLASMA to ChargeRulesChange(captured.plasma, required.to.plasma)),
            change.chargeRuleChanges,
        )
        assertTrue(change.priceChanges.isEmpty())
    }

    @Test
    fun `given a price dropped to zero then fresh consent is still required`() {
        // Ноль — тоже условие: «попытка даром» — это изменение, а не отсутствие цены.
        val captured = PayableTerms.of(EconomyConstants.BOOTSTRAP)
        val current =
            EconomyConstants.BOOTSTRAP.copy(
                version = 5L,
                activityPrices = EconomyConstants.BOOTSTRAP_PRICES + (ActivityKind.ARENA to 0),
            )

        val required = assertIs<TermsConsentVerdict.Required>(TermsConsentGate.evaluate(captured, current))
        assertEquals(PriceChange(from = 50, to = 0), requireNotNull(required.change).priceChanges[ActivityKind.ARENA])
    }
}
