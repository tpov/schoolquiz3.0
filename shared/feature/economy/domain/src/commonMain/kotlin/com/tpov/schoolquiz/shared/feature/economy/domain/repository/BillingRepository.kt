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

    /** Launches the store's purchase flow and suspends until it resolves one way or another. */
    suspend fun purchase(productId: StoreProductId): BillingOutcome

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
