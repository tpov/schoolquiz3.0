package com.tpov.schoolquiz.platform.billing

import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingPurchase
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProduct
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.BillingRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Google Play Billing, behind the domain contract.
 *
 * Three things here are load-bearing and easy to get wrong:
 *
 * 1. **Nothing is granted on the device.** A purchase produces a token; the token goes to the
 *    server, the server verifies it against the Play Developer API and credits gold. The client
 *    consumes the purchase only after the server says it credited. Crediting locally is the
 *    standard in-app-purchase fraud, and consuming before crediting loses a paying user's money.
 *
 * 2. **The durable queue is Play's, not ours.** `queryPurchasesAsync` returns every purchase the
 *    store has recorded and nobody has consumed. That is what makes "pay, then the process dies,
 *    then get the gold" work without a local table to keep in step.
 *
 * 3. **Pending is a third state.** Play returns PENDING for payment methods that settle later.
 *    It is neither success nor failure and must grant nothing.
 */
class PlayBillingRepository(
    context: Context,
    private val activityHolder: CurrentActivityHolder,
    private val scope: CoroutineScope,
) : BillingRepository {
    private val unsettled = MutableStateFlow<List<BillingPurchase>>(emptyList())

    /**
     * Completes the suspended [purchase] call when Play reports back.
     *
     * [purchaseMutex] keeps two flows from overlapping — Play delivers results to one process-wide
     * listener with no request id — but the listener runs on Play's thread, not the caller's, so
     * the handoff itself is atomic rather than mutex-guarded. A plain field would let the listener
     * read a stale null and leave `purchase()` suspended forever *while still holding the mutex*,
     * which would make every later purchase in that process hang too.
     */
    private val pendingPurchase = AtomicReference<CompletableDeferred<BillingOutcome>?>(null)
    private val purchaseMutex = Mutex()

    private val purchasesListener =
        PurchasesUpdatedListener { result, purchases ->
            val outcome = toOutcome(result, purchases)
            pendingPurchase.getAndSet(null)?.complete(outcome)
            if (!purchases.isNullOrEmpty()) {
                scope.launch { refreshUnsettled() }
            }
        }

    private val client: BillingClient =
        BillingClient.newBuilder(context)
            .setListener(purchasesListener)
            // Play reconnects on its own from Billing 8. Without it every dropped connection had
            // to be caught and retried by hand, and a retry racing an in-flight call is exactly
            // where a purchase goes missing.
            .enableAutoServiceReconnection()
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .build()

    private val connectionMutex = Mutex()

    /**
     * Opens the first connection. Reconnects after a drop are Play's job now
     * (`enableAutoServiceReconnection`), so this only covers the cold start.
     */
    private suspend fun ensureConnected(): Boolean =
        connectionMutex.withLock {
            if (client.isReady) return true
            suspendCoroutine { continuation ->
                client.startConnection(
                    object : BillingClientStateListener {
                        private var resumed = false

                        override fun onBillingSetupFinished(result: BillingResult) {
                            if (resumed) return
                            resumed = true
                            continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                        }

                        override fun onBillingServiceDisconnected() {
                            if (resumed) return
                            resumed = true
                            continuation.resume(false)
                        }
                    },
                )
            }
        }

    override suspend fun isAvailable(): Boolean = ensureConnected()

    override suspend fun loadProducts(ids: Set<StoreProductId>): Result<List<StoreProduct>> {
        if (ids.isEmpty()) return Result.success(emptyList())
        if (!ensureConnected()) {
            return Result.failure(IllegalStateException("Billing service unavailable"))
        }
        val params =
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    ids.map { id ->
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(id.playSku)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    },
                )
                .build()

        val response = client.queryProductDetails(params)
        if (response.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return Result.failure(
                IllegalStateException("queryProductDetails: ${response.billingResult.debugMessage}"),
            )
        }
        val products = response.productDetailsList.orEmpty().mapNotNull { it.toStoreProduct() }
        return productsOrFailure(ids, products)
    }

    override fun observeUnsettledPurchases(): Flow<List<BillingPurchase>> = unsettled.asStateFlow()

    /**
     * @param buyerId the buyer tag the server will compare against, already hashed by the caller.
     *   Not the raw uid: Play stores this value, shows it in the console and puts it in exported
     *   reports.
     */
    override suspend fun purchase(
        productId: StoreProductId,
        buyerId: String,
    ): BillingOutcome {
        if (!ensureConnected()) return BillingOutcome.Unavailable("Billing service unavailable")

        val activity =
            activityHolder.activity
                ?: return BillingOutcome.Unavailable("No foreground activity to host the purchase flow")

        val details =
            loadProductDetails(productId)
                ?: return BillingOutcome.Unavailable("Product ${productId.playSku} not found in the store")

        return purchaseMutex.withLock {
            val deferred = CompletableDeferred<BillingOutcome>()
            pendingPurchase.set(deferred)

            val flowParams =
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(details)
                                .build(),
                        ),
                    )
                    // Binds the purchase to the account that is paying. Play returns it to the
                    // server as `obfuscatedExternalAccountId`, which is how a token presented by
                    // somebody else is refused instead of credited.
                    //
                    // Passed through, not computed here: the value has to match what the server
                    // computes byte for byte, and `BillingFlowParams` has no getter, so a value
                    // built at this line could never be read back by a test. Hashing happens where
                    // a test can see it (`BuyerTag`, wired in `billingModule`).
                    .setObfuscatedAccountId(buyerId)
                    .build()

            val launch = client.launchBillingFlow(activity, flowParams)
            if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
                pendingPurchase.compareAndSet(deferred, null)
                BillingOutcome.Failed(
                    code = launch.responseCode.toString(),
                    message = launch.debugMessage.orEmpty(),
                )
            } else {
                deferred.await()
            }
        }
    }

    override suspend fun consume(purchaseToken: String): Result<Unit> {
        if (!ensureConnected()) {
            return Result.failure(IllegalStateException("Billing service unavailable"))
        }
        val params = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()
        val result = client.consumePurchase(params)
        return if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            refreshUnsettled()
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("consume: ${result.billingResult.debugMessage}"))
        }
    }

    override suspend fun refreshUnsettledPurchases(): Result<Unit> = refreshUnsettled()

    /**
     * Re-reads what the store still holds.
     *
     * Call after every purchase and every consume, and once at startup — this is how a purchase
     * that was paid for but never credited comes back for another settlement attempt.
     *
     * Failing to ask is reported rather than swallowed. Both failures here are ordinary and
     * temporary — the store's process is not bound yet on a cold start, or it answers with a
     * transport error — but a caller that cannot tell them from "the queue is empty" will read a
     * stale queue and decide there is nothing to settle, which is how a paid purchase goes
     * unnoticed for a whole session.
     */
    suspend fun refreshUnsettled(): Result<Unit> {
        if (!ensureConnected()) {
            return Result.failure(IllegalStateException("Billing service unavailable"))
        }
        val params =
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return Result.failure(
                IllegalStateException("queryPurchases: ${result.billingResult.debugMessage}"),
            )
        }
        val purchased = result.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        // Sorted, because the queue is compared by equality to decide whether anything changed and
        // the store promises no order: the same set arriving shuffled would read as a change and
        // send the settler round again against Play for nothing.
        val known = purchased.mapNotNull { it.toBillingPurchase() }.sortedBy { it.purchaseToken }
        // A paid, unconsumed purchase whose SKU this build does not know — a pack added to the
        // console for a newer version, or a renamed one — is dropped by the mapping. Keep the drop;
        // lose the silence, because an unsettleable purchase that nothing can even count is a
        // player who paid and sees nothing at all.
        val unknown = purchased.size - known.size
        if (unknown > 0) {
            Log.w(TAG, "$unknown unconsumed purchase(s) name a product this build does not know")
        }
        unsettled.value = known
        return Result.success(Unit)
    }

    private suspend fun loadProductDetails(productId: StoreProductId): ProductDetails? {
        val params =
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId.playSku)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build(),
                    ),
                )
                .build()
        val response = client.queryProductDetails(params)
        if (response.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return null
        return response.productDetailsList.orEmpty().firstOrNull { it.productId == productId.playSku }
    }

    private fun toOutcome(
        result: BillingResult,
        purchases: List<Purchase>?,
    ): BillingOutcome =
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases?.firstOrNull()
                when {
                    purchase == null -> BillingOutcome.Failed("OK_NO_PURCHASE", "OK with no purchase")
                    purchase.purchaseState == Purchase.PurchaseState.PENDING ->
                        purchase.firstProductId()
                            ?.let { BillingOutcome.Pending(it) }
                            ?: BillingOutcome.Failed("UNKNOWN_SKU", "Pending purchase of an unknown product")
                    else ->
                        purchase.toBillingPurchase()
                            ?.let { BillingOutcome.Purchased(it) }
                            ?: BillingOutcome.Failed("UNKNOWN_SKU", "Purchase of an unknown product")
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> BillingOutcome.Cancelled

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                BillingOutcome.AlreadyOwned(purchases?.firstOrNull()?.toBillingPurchase())

            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
            ->
                BillingOutcome.Unavailable(result.debugMessage.orEmpty())

            else ->
                BillingOutcome.Failed(
                    code = result.responseCode.toString(),
                    message = result.debugMessage.orEmpty(),
                )
        }

    private fun Purchase.firstProductId(): StoreProductId? =
        products.firstNotNullOfOrNull { StoreProductId.fromSku(it) }

    /**
     * One store purchase becomes at most one [BillingPurchase] — the first product this build knows.
     *
     * A store purchase can name several products, but a token is settled against a single SKU: the
     * server asks Play about one product per token and records one settlement for it. Mapping the
     * token to several rows would promise a credit for the second product that nothing can ever
     * deliver — it would be verified, find the token already settled, and replay the first
     * product's answer. Refusing to split is the honest shape; a purchase naming more than one
     * known product is reported rather than silently halved.
     *
     * Price is not on [Purchase] — Play does not put it there. It stays 0 here and the server
     * reads the real amount from the Play Developer API when it verifies the token, which is the
     * only place the number can be trusted anyway.
     */
    private fun Purchase.toBillingPurchase(): BillingPurchase? {
        val known = products.mapNotNull { StoreProductId.fromSku(it) }
        if (known.size > 1) {
            Log.w(TAG, "a purchase names ${known.size} known products; settling only the first")
        }
        val id = known.firstOrNull() ?: return null
        return BillingPurchase(
            productId = id,
            purchaseToken = purchaseToken,
            orderId = orderId,
            isAcknowledged = isAcknowledged,
            priceMicros = 0L,
            currency = "",
        )
    }

    private fun ProductDetails.toStoreProduct(): StoreProduct? {
        val id = StoreProductId.fromSku(productId) ?: return null
        val offer = oneTimePurchaseOfferDetails ?: return null
        return StoreProduct(
            id = id,
            title = title,
            description = description,
            formattedPrice = offer.formattedPrice,
            priceMicros = offer.priceAmountMicros,
            currency = offer.priceCurrencyCode,
        )
    }

    private companion object {
        const val TAG = "PlayBilling"
    }
}

/**
 * Decides what a store answer of "here is what I know about those products" is worth.
 *
 * A SKU asked for and not returned is not an empty shelf — it is a product that exists in the code
 * and not in Play Console, and the shelf cannot show a price for it. Failing names the mistake;
 * succeeding with a short list hides it behind a shelf that is quietly missing an item.
 *
 * The check is per SKU rather than "did anything at all come back". Three packs with two of them
 * unconfigured is the ordinary way this goes wrong — a half-finished console — and a rule that only
 * fires when *every* product is missing is silent in exactly that case.
 *
 * Free function over plain values so it can be tested without the Play SDK, which does not
 * initialise in a JVM test.
 */
internal fun productsOrFailure(
    requested: Set<StoreProductId>,
    returned: List<StoreProduct>,
): Result<List<StoreProduct>> {
    val missing = requested - returned.map { it.id }.toSet()
    if (missing.isEmpty()) return Result.success(returned)
    return Result.failure(
        IllegalStateException(
            "Products not available in the store: " + missing.joinToString { it.playSku },
        ),
    )
}
