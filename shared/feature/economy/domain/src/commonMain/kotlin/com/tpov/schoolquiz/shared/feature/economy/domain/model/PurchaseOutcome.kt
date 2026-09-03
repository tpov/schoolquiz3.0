package com.tpov.schoolquiz.shared.feature.economy.domain.model

/**
 * How a whole buy-and-settle attempt ended, in terms a caller can act on.
 *
 * This is the store's answer *and* the server's answer folded into one: [BillingOutcome] alone
 * cannot say whether the money reached the account, and the server's answer alone cannot say
 * whether the player ever reached the payment sheet.
 *
 * Everything here is a non-error ending. Failing to reach the server, or having no account at all,
 * is a `Result.failure` instead — those are states worth retrying, and a retryable state must not
 * be mistaken for a decision.
 */
sealed interface PurchaseOutcome {
    /**
     * The server credited the account and the purchase was settled.
     *
     * [goldGranted] is what the server reported granting. It is for the receipt, not for arithmetic:
     * the balance shown afterwards is the one re-read from the server.
     */
    data class Credited(val goldGranted: Long) : PurchaseOutcome

    /**
     * The payment has not settled yet — the store or the server says so.
     *
     * Not an error: cash-at-a-kiosk and some carrier billing legitimately take hours. The purchase
     * stays in the store's queue and the settler finishes it later, without the player doing
     * anything.
     */
    data object Pending : PurchaseOutcome

    /** The player closed the payment sheet. Nothing happened, and nothing needs explaining. */
    data object Cancelled : PurchaseOutcome

    /**
     * The server refused, naming [code].
     *
     * A success of the call and a refusal of the purchase: the client got a clear answer, so there
     * is nothing to retry. The code travels intact so the interface can say why in the player's own
     * language instead of showing the server's English.
     */
    data class Refused(val code: PurchaseRefusalCode) : PurchaseOutcome
}
