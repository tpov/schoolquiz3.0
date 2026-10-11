package com.tpov.schoolquiz.shared.feature.economy.domain.logic

import com.tpov.schoolquiz.shared.feature.economy.domain.model.ActivityKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ChargeKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ChargeRules
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyConstants
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PayableTerms

/**
 * Чем захваченные платные условия разошлись с текущими.
 *
 * Пустых расхождений здесь не бывает: экземпляр создаётся только когда есть что назвать. Клиент
 * показывает это игроку («цены изменились»), а не молча проводит старую операцию по новым ценам —
 * скрытая переоценка согласованной операции и есть то, что AD-5 запрещает.
 *
 * @property from условия, с которыми игрок согласился раньше.
 * @property to условия, которые действуют сейчас.
 * @property priceChanges прейскурант: вид — прежняя и новая цена попытки.
 * @property chargeRuleChanges заряды: вид — прежние и новые правила (лестница, валюта, платность).
 */
data class TermsChange(
    val from: PayableTerms,
    val to: PayableTerms,
    val priceChanges: Map<ActivityKind, PriceChange>,
    val chargeRuleChanges: Map<ChargeKind, ChargeRulesChange>,
)

/** @property from цена попытки, с которой игрок согласился. @property to цена сейчас. */
data class PriceChange(
    val from: Int,
    val to: Int,
)

/** @property from правила заряда, с которыми игрок согласился. @property to правила сейчас. */
data class ChargeRulesChange(
    val from: ChargeRules,
    val to: ChargeRules,
)

/** Вердикт согласия: можно ли вести платную операцию по захваченным условиям. */
sealed interface TermsConsentVerdict {
    /**
     * Захваченные условия совпадают с текущими по всем платным полям — игрок уже соглашался ровно
     * на эти цены, переспрашивать нечего.
     */
    data object Sufficient : TermsConsentVerdict

    /**
     * Требуется свежее согласие: платные условия разошлись (или согласия ещё не было вовсе).
     *
     * @property to условия, на которые игрока просят согласиться сейчас.
     * @property change что именно изменилось; `null`, когда прежнего согласия не было — называть
     *   изменение нечем, игрок соглашается впервые.
     */
    data class Required(
        val to: PayableTerms,
        val change: TermsChange?,
    ) : TermsConsentVerdict
}

/**
 * Клиентская половина CAP-16: изменившиеся платные условия требуют нового согласия.
 *
 * Сверяет захваченные платные условия операции/согласия с текущей таблицей. Решающим является
 * совпадение платных полей — прейскуранта и правил зарядов. Номер версии и неплатные ручки таблицы
 * ([EconomyConstants.clockSkewToleranceMs], [EconomyConstants.auditEnabled]) вердикт не меняют:
 * сервер вправе править их без перезапроса согласия, и переспрос по неплатной правке — не
 * осторожность, а ложная тревога, приучающая игрока соглашаться не глядя.
 *
 * Серверную половину AD-5 гейт не заменяет: новую mutation по-прежнему проверяет сервер
 * (blocked-история 2.8), здесь клиент отказывается проводить старое согласие поверх новых цен.
 */
object TermsConsentGate {
    /**
     * @param captured условия, с которыми игрок согласился; `null`, если согласия ещё не было.
     * @param current таблица, действующая на устройстве сейчас.
     */
    fun evaluate(
        captured: PayableTerms?,
        current: EconomyConstants,
    ): TermsConsentVerdict {
        val to = PayableTerms.of(current)
        if (captured == null) return TermsConsentVerdict.Required(to, change = null)

        val priceChanges =
            ActivityKind.entries
                .map { it to PriceChange(from = captured.priceOf(it), to = to.priceOf(it)) }
                .filter { (_, change) -> change.from != change.to }
                .toMap()
        val chargeRuleChanges =
            ChargeKind.entries
                .map { it to ChargeRulesChange(from = captured.rulesFor(it), to = to.rulesFor(it)) }
                .filter { (_, change) -> change.from != change.to }
                .toMap()

        return if (priceChanges.isEmpty() && chargeRuleChanges.isEmpty()) {
            TermsConsentVerdict.Sufficient
        } else {
            TermsConsentVerdict.Required(to, TermsChange(captured, to, priceChanges, chargeRuleChanges))
        }
    }
}
