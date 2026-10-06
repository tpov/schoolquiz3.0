package com.tpov.schoolquiz.shared.feature.economy.domain.model

/**
 * Платные условия, с которыми игрок согласился (CAP-16, клиентская половина AD-5).
 *
 * Проекция [EconomyConstants], оставляющая в себе ровно то, за что берётся плата и что потому
 * требует согласия: прейскурант попыток и правила обоих видов зарядов — их лестницы цен, валюты и
 * платность аккаунта. Неплатные поля таблицы (допуск на расхождение часов, запись аудита) согласия
 * не требуют: игрок не соглашался ни на что, что его кошелёк касается.
 *
 * [version] в записи согласия остаётся — «accepted terms revisions фиксируются в операции» (AD-5),
 * — но решающим для согласия не является: согласие сверяется по платным полям, а не по номеру
 * версии, иначе правка неплатной ручки заставляла бы переспрашивать согласие по неизменным ценам.
 *
 * Экземпляр — снимок на момент согласия: живёт в хранилище согласия и переживает приезд новой
 * таблицы, чтобы сверка «захваченные против текущих» имела с чем сверяться.
 */
data class PayableTerms(
    val version: Long,
    val activityPrices: Map<ActivityKind, Int>,
    val standard: ChargeRules,
    val plasma: ChargeRules,
) {
    init {
        require(version >= 0L) { "PayableTerms.version must be non-negative, got $version" }
        require(activityPrices.keys.containsAll(ActivityKind.entries.toSet())) {
            "платные условия обязаны назвать цену каждому виду; нет цены у " +
                (ActivityKind.entries.toSet() - activityPrices.keys)
        }
        require(activityPrices.values.all { it >= 0 }) { "цена не может быть отрицательной" }
    }

    fun rulesFor(kind: ChargeKind): ChargeRules =
        when (kind) {
            ChargeKind.STANDARD -> standard
            ChargeKind.PLASMA -> plasma
        }

    fun priceOf(activity: ActivityKind): Int = activityPrices.getValue(activity)

    companion object {
        /** Платные условия таблицы на текущий момент. */
        fun of(constants: EconomyConstants): PayableTerms =
            PayableTerms(
                version = constants.version,
                activityPrices = constants.activityPrices,
                standard = constants.standard,
                plasma = constants.plasma,
            )

        /** Согласие с загрузочной копией — тоже согласие: игрок видел эти цены первыми. */
        val BOOTSTRAP: PayableTerms = of(EconomyConstants.BOOTSTRAP)
    }
}
