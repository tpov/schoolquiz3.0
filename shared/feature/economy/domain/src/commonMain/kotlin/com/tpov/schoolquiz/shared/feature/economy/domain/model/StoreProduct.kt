package com.tpov.schoolquiz.shared.feature.economy.domain.model

/**
 * A real-money product, as the store knows it.
 *
 * The SKU is the only thing the client owns. **How much gold a pack grants is deliberately not
 * here**: gold is a monetary balance, the server settles it per operation, and a second copy of
 * the amount on the device would be exactly the duplicated source the economy table forbids.
 * The client says "this SKU was bought"; the server decides what that is worth.
 */
enum class StoreProductId(val playSku: String) {
    GOLD_PACK_SMALL("gold_pack_small"),
    GOLD_PACK_MEDIUM("gold_pack_medium"),
    GOLD_PACK_LARGE("gold_pack_large"),
    ;

    companion object {
        fun fromSku(sku: String): StoreProductId? = entries.firstOrNull { it.playSku == sku }
    }
}

/**
 * What the store reports for a product, at the price this particular user would actually pay.
 *
 * [formattedPrice] is rendered by the store in the user's own currency and locale, so it is what
 * the UI shows. Never format a price from [priceMicros] on the device — regional pricing means a
 * hardcoded hryvnia figure is wrong for most of the world and wrong in Ukraine the moment the
 * price tier changes.
 */
data class StoreProduct(
    val id: StoreProductId,
    val title: String,
    val description: String,
    val formattedPrice: String,
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
