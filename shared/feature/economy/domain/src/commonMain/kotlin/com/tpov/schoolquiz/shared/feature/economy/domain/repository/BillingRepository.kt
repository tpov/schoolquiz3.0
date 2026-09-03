package com.tpov.schoolquiz.shared.feature.economy.domain.repository

import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingPurchase
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProduct
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import kotlinx.coroutines.flow.Flow

/**
 * The store, as the rest of the app is allowed to see it.
 *
 * No Play types cross this boundary — `BillingClient`, `ProductDetails` and `Purchase` stay in
 * `platform/billing`, and so does the Activity a purchase flow needs. That is why [purchase]
 * takes only a product id: the implementation holds the current Activity itself.
 */
interface BillingRepository {

    /**
     * Prices for the given products, from the store, in the user's own currency.
     *
     * Returns a failure rather than an empty list when the store cannot be reached, so a UI can
     * distinguish "no products configured" from "we could not ask".
     */
    suspend fun loadProducts(ids: Set<StoreProductId>): Result<List<StoreProduct>>

    /**
     * Purchases the store has recorded and nothing has consumed yet.
     *
     * This is the durable queue, and it lives in Play rather than in a local table on purpose: a
     * purchase that survives process death, reinstall, or a crash between paying and crediting is
     * re-delivered here on the next connection. Anything the server has not yet settled shows up
     * again, which is what makes the "pay, then die, then get your gold" path work.
     *
     * A [Flow] with multiple collectors is expected (shop screen and a background settler), so
     * implementations multicast rather than hand each collector a slice of the events.
     */
    fun observeUnsettledPurchases(): Flow<List<BillingPurchase>>

    /**
     * Launches the store's purchase flow and suspends until it resolves one way or another.
     *
     * [buyerId] tags the purchase with the account that is paying, so the server can refuse a token
     * presented by somebody else. It is an opaque, non-reversible identifier derived from the uid —
     * the store shows it in the console and it must not be a login, an e-mail or the uid itself.
     */
    suspend fun purchase(
        productId: StoreProductId,
        buyerId: String,
    ): BillingOutcome

    /**
     * Re-asks the store what it still holds, refreshing [observeUnsettledPurchases].
     *
     * Needed because the queue is Play's: after a cold start, after an account change, and after
     * the network comes back, the only way to learn about a purchase that was paid for and never
     * credited is to ask again.
     *
     * Returns a failure when the store could not be asked — the store's own process can be
     * unavailable for a moment, and that moment is often a cold start right after it updated
     * itself, which is exactly when an unsettled purchase is waiting. A silent no-op here would
     * leave the caller reading a stale, usually empty queue and concluding there is nothing to
     * settle, so the difference between "nothing to do" and "could not ask" has to survive.
     */
    suspend fun refreshUnsettledPurchases(): Result<Unit>

    /**
     * Tells the store the purchase has been granted, so the user can buy it again.
     *
     * Called **only after the server confirms it credited the account.** Consuming first and
     * crediting second loses the user's money whenever the second step fails, and Play will not
     * hand the token back once it is consumed.
     */
    suspend fun consume(purchaseToken: String): Result<Unit>

    /** Whether billing is usable on this device at all — no Play Services, no purchases. */
    suspend fun isAvailable(): Boolean
}
