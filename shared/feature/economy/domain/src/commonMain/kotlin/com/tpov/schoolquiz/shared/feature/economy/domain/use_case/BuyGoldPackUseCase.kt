package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingPurchase
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.BillingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Buys a gold pack: opens the store, then settles whatever the store answers.
 *
 * Two gates come before Play is opened at all, and both refuse rather than queue. Gold is money:
 * every movement of it is authorised by the server, online, and a purchase that cannot be verified
 * must not become a deferred mutation that settles later out of the player's sight (ADM-5). The
 * offline refusal is named ([SyncError.NoNetwork]) so the interface can say "you need a connection"
 * instead of spinning until a timeout.
 *
 * Everything after the store's answer is [SettlePurchaseUseCase] — the same code the background
 * settler runs, so a purchase interrupted here is finished there without a second implementation.
 */
class BuyGoldPackUseCase(
    private val billing: BillingRepository,
    private val networkMonitor: NetworkMonitor,
    private val settlePurchase: SettlePurchaseUseCase,
    private val currentUidFlow: () -> Flow<String?>,
    /**
     * Turns the account id into the value the store carries and the server compares against.
     *
     * A port rather than a call inside the store adapter, so the tag that actually reaches the
     * store is visible to a test. The adapter cannot be tested on that point at all — the store's
     * flow parameters expose no getter — and a tag that silently stopped being hashed would refuse
     * every real purchase while every suite stayed green.
     */
    private val buyerTag: (String) -> String,
) {

    suspend operator fun invoke(productId: StoreProductId): Result<PurchaseOutcome> {
        // Read once, at the moment of buying. Not held in a field: the account can change, and a
        // stale uid would tag the purchase for somebody who is no longer signed in.
        val uid = currentUidFlow().first()
        if (uid.isNullOrBlank()) {
            return Result.failure(IllegalStateException(NO_ACCOUNT))
        }
        if (!networkMonitor.isOnline()) {
            return Result.failure(SyncFailure(SyncError.NoNetwork))
        }

        return when (val outcome = billing.purchase(productId, buyerTag(uid))) {
            is BillingOutcome.Purchased -> settleBought(productId, outcome.purchase)

            // Play took the payment method but the money has not moved yet. Nothing to settle; the
            // settler will finish it when Play re-delivers the purchase as purchased.
            is BillingOutcome.Pending -> Result.success(PurchaseOutcome.Pending)

            BillingOutcome.Cancelled -> Result.success(PurchaseOutcome.Cancelled)

            // What Play answers for a consumable bought and never consumed — i.e. an earlier
            // attempt that died before the server credited it. Settling it is the recovery, not an
            // error. When Play declines to say *which* purchase, re-reading the queue is what makes
            // it visible to the settler.
            is BillingOutcome.AlreadyOwned ->
                outcome.purchase?.let { settlePurchase(it) }
                    ?: run {
                        billing.refreshUnsettledPurchases()
                        Result.failure(IllegalStateException(ALREADY_OWNED_UNIDENTIFIED))
                    }

            is BillingOutcome.Unavailable -> Result.failure(IllegalStateException(outcome.reason))

            is BillingOutcome.Failed ->
                Result.failure(IllegalStateException("${outcome.code}: ${outcome.message}"))
        }
    }

    /**
     * Settles what the store handed back, but only reports it when it is what was asked for.
     *
     * The store's purchase callback is process-wide and carries no request id, so the purchase that
     * arrives while this call is waiting may be an older unconsumed one that the store chose to
     * re-deliver at that moment. It still deserves settling — it is paid for — but reporting it as
     * this call's outcome would tell the player they received the pack they just bought while
     * crediting a different one.
     */
    private suspend fun settleBought(
        requested: StoreProductId,
        purchase: BillingPurchase,
    ): Result<PurchaseOutcome> {
        val settlement = settlePurchase(purchase)
        if (purchase.productId == requested) return settlement
        return Result.failure(IllegalStateException(DELIVERED_ANOTHER_PRODUCT))
    }

    private companion object {
        const val NO_ACCOUNT = "No signed-in account to attribute the purchase to"

        /**
         * Named rather than silent: the purchase was settled, so nothing is lost, but the caller
         * must not show the amount as if it were the pack the player chose.
         */
        const val DELIVERED_ANOTHER_PRODUCT =
            "The store delivered a different product than the one being bought; " +
                "it was settled, and the requested pack is still waiting"

        /**
         * Named rather than blank: "already owned, and Play did not say which purchase" is a real
         * state with a real recovery (the settler picks it up from the refreshed queue), and a
         * caller staring at an empty message cannot tell that from a crash.
         */
        const val ALREADY_OWNED_UNIDENTIFIED =
            "The store reports this product is already owned but did not name the purchase; " +
                "it was re-queued for settlement"
    }
}
