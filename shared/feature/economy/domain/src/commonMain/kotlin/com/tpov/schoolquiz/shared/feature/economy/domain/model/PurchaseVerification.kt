package com.tpov.schoolquiz.shared.feature.economy.domain.model

/**
 * What the server answered about a purchase token.
 *
 * Only two answers exist, and neither of them is "the client may now grant something": [Credited]
 * means the server has **already** moved the money, so the device's job is to consume the purchase
 * in the store and re-read the balance; [Pending] means the payment has not settled yet and the
 * token must stay in the store's queue.
 *
 * A refusal is not a branch here — it arrives as [PurchaseRefusedException], because a refusal
 * carries a reason the caller has to keep intact and must never be mistaken for a settled purchase.
 */
sealed interface PurchaseVerification {
    /**
     * The server credited the account for this token.
     *
     * [goldGranted] is what the server says it granted, and it is for the receipt and the log only:
     * the balance is re-read from the server afterwards, never computed by adding this number to a
     * local one (SYNC-AD-25).
     */
    data class Credited(
        val productId: StoreProductId,
        val goldGranted: Long,
        val settlementId: String,
    ) : PurchaseVerification

    /** The payment has not settled yet. Nothing was credited, nothing may be consumed. */
    data class Pending(val productId: StoreProductId) : PurchaseVerification
}

/**
 * Why the server refused to settle a purchase, as a code rather than a sentence.
 *
 * The server sends these in `HttpsError.details.reasonCode`; the English `message` next to them is
 * for the log, not for a branch and not for the screen. Matching on text would break the moment a
 * message is reworded or the interface is translated, which is exactly why the code exists.
 *
 * The eleven named entries mirror `REASON_CODES` in `functions/purchase-verification.js` one for
 * one. [UNKNOWN] covers a code this build has not heard of yet — a newer server refusing for a
 * newer reason is still a refusal, and treating it as one is safer than treating it as a success.
 */
enum class PurchaseRefusalCode {
    TOKEN_UNKNOWN,
    SKU_MISMATCH,
    PURCHASE_CANCELED,
    PURCHASE_STATE_UNKNOWN,
    CONSUMED_WITHOUT_SETTLEMENT,
    SKU_NOT_SOLD,
    TOKEN_OWNED_BY_ANOTHER_ACCOUNT,
    SETTLEMENT_NOT_CREDITED,
    CONSUMPTION_STATE_UNKNOWN,
    QUANTITY_UNREADABLE,
    REQUEST_INVALID,

    /** The server named a reason this build does not know. Still a refusal. */
    UNKNOWN,
    ;

    companion object {
        /** Reads the wire value the server put in `details.reasonCode`. */
        fun fromWire(value: String?): PurchaseRefusalCode =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

/**
 * The server looked at the purchase and refused it.
 *
 * Distinct from a network failure on purpose: a refusal is an answer, so retrying it changes
 * nothing, while a failure to reach the server leaves the purchase unsettled and worth retrying.
 * Nothing is consumed in either case, but only the second one keeps hoping.
 */
class PurchaseRefusedException(
    val code: PurchaseRefusalCode,
    message: String,
) : Exception(message)
