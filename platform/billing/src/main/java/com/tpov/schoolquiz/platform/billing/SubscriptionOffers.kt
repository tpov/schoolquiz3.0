package com.tpov.schoolquiz.platform.billing

/**
 * Оффер подписки, сведённый к простым значениям.
 *
 * Типы Play SDK не поднимаются в JVM-тестах, поэтому выбор оффера живёт над этими фигурами: адаптер
 * магазина снимает с [ProductDetails][com.android.billingclient.api.ProductDetails] только то, что
 * влияет на выбор, а решение проверяется тестом на обычных списках.
 */
data class SubscriptionOfferShape(
    val offerToken: String,
    val phases: List<PricePhaseShape>,
)

/** Одна фаза цены оффера: триал — фаза с нулевой ценой, дальше — рекуррентная. */
data class PricePhaseShape(
    val priceMicros: Long,
    val formattedPrice: String,
    val currency: String,
) {
    val isFree: Boolean get() = priceMicros == 0L
}

/**
 * Оффер, с которым подписка открывается, и цена, которую полка показывает.
 *
 * Триал предпочитается всему остальному: у финальной модели он один (trial-3d), и игрок, которому
 * он положен, должен получить именно его, а не базовый план. Остальные офферы идут в порядке Play —
 * первый отвечает и за случай «триал этому игроку недоступен», и за отсутствие триала как такового.
 * Цена на полке — последняя фаза выбранного оффера: та, что станет рекуррентным платежом.
 *
 * `null` — подписка без офферов вовсе: Play отдал пустой список, и ни открыть покупку (нужен
 * offerToken), ни показать цену из неё нельзя.
 */
internal fun preferredSubscriptionOffer(offers: List<SubscriptionOfferShape>): SubscriptionOfferShape? {
    if (offers.isEmpty()) return null
    return offers.firstOrNull { offer -> offer.phases.firstOrNull()?.isFree == true } ?: offers.first()
}
