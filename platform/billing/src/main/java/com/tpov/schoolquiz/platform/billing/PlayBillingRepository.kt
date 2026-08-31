package com.tpov.schoolquiz.platform.billing

import android.content.Context
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
     * Guarded by [purchaseMutex] so two overlapping flows cannot resolve each other's
     * continuation — Play delivers results to one process-wide listener with no request id.
     */
    private var pendingPurchase: CompletableDeferred<BillingOutcome>? = null
    private val purchaseMutex = Mutex()

    private val purchasesListener =
        PurchasesUpdatedListener { result, purchases ->
            val outcome = toOutcome(result, purchases)
            pendingPurchase?.complete(outcome)
            pendingPurchase = null
            if (!purchases.isNullOrEmpty()) {
                scope.launch { refreshUnsettled() }
            }
        }

    private val client: BillingClient =
        BillingClient.newBuilder(context)
            .setListener(purchasesListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .build()

    private val connectionMutex = Mutex()

    /** Connects if needed. Play drops the connection freely, so every call goes through here. */
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
        return Result.success(products)
    }

    override fun observeUnsettledPurchases(): Flow<List<BillingPurchase>> = unsettled.asStateFlow()

    override suspend fun purchase(productId: StoreProductId): BillingOutcome {
        if (!ensureConnected()) return BillingOutcome.Unavailable("Billing service unavailable")

        val activity =
            activityHolder.activity
                ?: return BillingOutcome.Unavailable("No foreground activity to host the purchase flow")

        val details =
            loadProductDetails(productId)
                ?: return BillingOutcome.Unavailable("Product ${productId.playSku} not found in the store")

        return purchaseMutex.withLock {
            val deferred = CompletableDeferred<BillingOutcome>()
            pendingPurchase = deferred

            val flowParams =
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(details)
                                .build(),
                        ),
                    )
                    .build()

            val launch = client.launchBillingFlow(activity, flowParams)
            if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
                pendingPurchase = null
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

    /**
     * Re-reads what the store still holds.
     *
     * Call after every purchase and every consume, and once at startup — this is how a purchase
     * that was paid for but never credited comes back for another settlement attempt.
     */
    suspend fun refreshUnsettled() {
        if (!ensureConnected()) return
        val params =
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return
        unsettled.value =
            result.purchasesList
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .flatMap { it.toBillingPurchases() }
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
                        purchase.toBillingPurchases().firstOrNull()
                            ?.let { BillingOutcome.Purchased(it) }
                            ?: BillingOutcome.Failed("UNKNOWN_SKU", "Purchase of an unknown product")
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> BillingOutcome.Cancelled

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                BillingOutcome.AlreadyOwned(purchases?.firstOrNull()?.toBillingPurchases()?.firstOrNull())

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
     * One Play purchase can carry several products, so it maps to a list.
     *
     * Price is not on [Purchase] — Play does not put it there. It stays 0 here and the server
     * reads the real amount from the Play Developer API when it verifies the token, which is the
     * only place the number can be trusted anyway.
     */
    private fun Purchase.toBillingPurchases(): List<BillingPurchase> =
        products.mapNotNull { sku ->
            StoreProductId.fromSku(sku)?.let { id ->
                BillingPurchase(
                    productId = id,
                    purchaseToken = purchaseToken,
                    orderId = orderId,
                    isAcknowledged = isAcknowledged,
                    priceMicros = 0L,
                    currency = "",
                )
            }
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
}
