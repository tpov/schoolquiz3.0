package com.tpov.schoolquiz.platform.billing

import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProduct
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId

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
 * Оффер без фаз цены не открывается и не ценится: offerToken у него есть, а покупать из него
 * нечего, поэтому такой оффер пропускается, как будто его не было. `null` — покупать нечем:
 * Play отдал пустой список офферов, либо у каждого из них пустой список фаз, и ни открыть покупку
 * (нужен offerToken живого оффера), ни показать цену из неё нельзя.
 */
internal fun preferredSubscriptionOffer(offers: List<SubscriptionOfferShape>): SubscriptionOfferShape? {
    val openable = offers.filter { offer -> offer.phases.isNotEmpty() }
    if (openable.isEmpty()) return null
    return openable.firstOrNull { offer -> offer.phases.firstOrNull()?.isFree == true } ?: openable.first()
}

/**
 * Строка подписки на полке: цена — последняя фаза выбранного оффера, той, что станет рекуррентным
 * платежом. Без покупаемого оффера (список пуст или у офферов нет фаз цены) строка остаётся на
 * полке без цены: Play товар знает, купить его не предлагает, — выпавшая строка читалась бы полкой
 * как недостающий SKU и роняла бы весь запрос (см. productsOrFailure).
 */
internal fun subscriptionStoreProduct(
    id: StoreProductId,
    title: String,
    description: String,
    offers: List<SubscriptionOfferShape>,
): StoreProduct {
    val phase = preferredSubscriptionOffer(offers)?.phases?.lastOrNull()
    return StoreProduct(
        id = id,
        title = title,
        description = description,
        formattedPrice = phase?.formattedPrice,
        priceMicros = phase?.priceMicros ?: 0L,
        currency = phase?.currency ?: "",
    )
}
