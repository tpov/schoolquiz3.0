package com.tpov.schoolquiz.shared.feature.economy.domain.model

/**
 * A real-money product, as the store knows it.
 *
 * The SKU is the only thing the client owns. **How much gold a pack grants is deliberately not
 * here**: gold is a monetary balance, the server settles it per operation, and a second copy of
 * the amount on the device would be exactly the duplicated source the economy table forbids.
 * The client says "this SKU was bought"; the server decides what that is worth.
 */
enum class StoreProductId(
    val playSku: String,
    /**
     * Подписка не гасится, а подтверждается: погашенный токен подписки Play не продлевает, и
     * активная подписка после погашения исчезла бы из очереди вместе со своим продлением.
     * Расчёт выбирает погашение/подтверждение по этому признаку.
     */
    val isSubscription: Boolean = false,
) {
    GOLD_PACK_SMALL("gold_pack_small"),
    GOLD_PACK_MEDIUM("gold_pack_medium"),
    GOLD_PACK_LARGE("gold_pack_large"),

    /** Единственная подписка финальной модели: месячное автопродление + триал (решение владельца). */
    PREMIUM_30D("premium_30d", isSubscription = true),
    ;

    companion object {
        fun fromSku(sku: String): StoreProductId? = entries.firstOrNull { it.playSku == sku }
    }
}

/** The shelf record this store product is sold through — the one «запись ↔ Play SKU» pair. */
fun StoreProductId.shopItem(): ShopItemId =
    when (this) {
        StoreProductId.GOLD_PACK_SMALL -> ShopItemId.GOLD_PACK_SMALL
        StoreProductId.GOLD_PACK_MEDIUM -> ShopItemId.GOLD_PACK_MEDIUM
        StoreProductId.GOLD_PACK_LARGE -> ShopItemId.GOLD_PACK_LARGE
        StoreProductId.PREMIUM_30D -> ShopItemId.PREMIUM
    }

/**
 * The store product behind a shelf record, or null for everything the store does not sell.
 *
 * Both directions live in this one file so the next paid product extends exactly two `when`s and
 * nowhere else. The entries are spelled out qualified: both enums have identically named entries in
 * one package, and an unqualified name here is a coin flip. The `when` stays exhaustive rather than
 * ending in `else`: a new paid record without a store product must fail compilation here, not
 * surface as a row nobody can ever buy.
 */
fun ShopItemId.storeProductId(): StoreProductId? =
    when (this) {
        ShopItemId.GOLD_PACK_SMALL -> StoreProductId.GOLD_PACK_SMALL
        ShopItemId.GOLD_PACK_MEDIUM -> StoreProductId.GOLD_PACK_MEDIUM
        ShopItemId.GOLD_PACK_LARGE -> StoreProductId.GOLD_PACK_LARGE
        ShopItemId.PREMIUM -> StoreProductId.PREMIUM_30D
        ShopItemId.STANDARD_HEART_SLOT,
        ShopItemId.GOLD_HEART,
        ShopItemId.QUIZ_SLOT,
        ShopItemId.AD_REWARD_BOX,
        ShopItemId.DONATE_GOOGLE_PLAY,
        ShopItemId.REFERRAL_PROGRAM,
        ShopItemId.NICKNAME_MARKET,
        -> null
    }

/**
 * What the store reports for a product, at the price this particular user would actually pay.
 *
 * [formattedPrice] is rendered by the store in the user's own currency and locale, so it is what
 * the UI shows. Never format a price from [priceMicros] on the device — regional pricing means a
 * hardcoded hryvnia figure is wrong for most of the world and wrong in Ukraine the moment the
 * price tier changes.
 *
 * A `null` [formattedPrice] is a row the store knows but does not sell: the subscription is there,
 * but no offer with pricing phases answers for it. The row stays on the shelf — dropped, it would
 * fail the whole query as a missing SKU — and the UI says it cannot be bought. [priceMicros] and
 * [currency] mean something only next to a price; without one they are placeholders, like on
 * [BillingPurchase], which Play sends without a price at all.
 */
data class StoreProduct(
    val id: StoreProductId,
    val title: String,
    val description: String,
    val formattedPrice: String?,
    val priceMicros: Long,
    val currency: String,
)

/**
 * A purchase the store has recorded and the server has not yet settled.
 *
 * [purchaseToken] is the only thing that proves the purchase, and it is what the server verifies
 * against the Play Developer API. It is not a secret the client may act on: crediting gold from
 * a token the device alone has seen is the classic in-app-purchase fraud.
 */
data class BillingPurchase(
    val productId: StoreProductId,
    val purchaseToken: String,
    val orderId: String?,
    val isAcknowledged: Boolean,
    val priceMicros: Long,
    val currency: String,
)

/**
 * How a purchase attempt ended.
 *
 * [Pending] is not a failure and not a success: Play returns it for payment methods that settle
 * later (cash at a kiosk, some carrier billing). Nothing may be granted until it becomes
 * [Purchased], and treating it as either outcome is a real source of both lost money and
 * free grants.
 */
sealed interface BillingOutcome {
    data class Purchased(val purchase: BillingPurchase) : BillingOutcome

    data class Pending(val productId: StoreProductId) : BillingOutcome

    data object Cancelled : BillingOutcome

    /** The store says this account already owns it — usually an earlier purchase never consumed. */
    data class AlreadyOwned(val purchase: BillingPurchase?) : BillingOutcome

    /** Billing is not usable here at all: no Play Services, unsupported country, no product. */
    data class Unavailable(val reason: String) : BillingOutcome

    data class Failed(val code: String, val message: String) : BillingOutcome
}
