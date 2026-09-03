package com.tpov.schoolquiz.shared.feature.economy.domain.fake

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingPurchase
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseVerification
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProduct
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.BillingRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.PurchaseVerifier
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.ServerBalanceRefresher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A purchase as Play would hand it over, with only the fields the settlement path reads. */
fun billingPurchase(
    token: String = "token-1",
    productId: StoreProductId = StoreProductId.GOLD_PACK_SMALL,
): BillingPurchase =
    BillingPurchase(
        productId = productId,
        purchaseToken = token,
        orderId = "order-$token",
        isAcknowledged = false,
        priceMicros = 0L,
        currency = "",
    )

/**
 * The store, scripted.
 *
 * Records what was consumed and in what order relative to everything else, because the order is the
 * invariant these tests exist to protect.
 */
class FakeBillingRepository(
    private val unsettled: MutableStateFlow<List<BillingPurchase>> = MutableStateFlow(emptyList()),
    private val journal: MutableList<String> = mutableListOf(),
) : BillingRepository {

    /** What [purchase] answers. Set per test. */
    var outcome: BillingOutcome = BillingOutcome.Cancelled

    /** When true, [consume] fails — the "credited but not consumed" row of the matrix. */
    var consumeFails: Boolean = false

    /**
     * When true, [consume] *throws* instead of returning a failure.
     *
     * A separate switch because the two are different code paths and only one of them was ever
     * exercised: the store adapter re-reads its own queue inside its success path, so a consume
     * that worked can still throw on the way back, and a settlement that treats that as a failed
     * purchase tells a player whose money moved that nothing happened.
     */
    var consumeThrows: Boolean = false

    /** When true, [refreshUnsettledPurchases] reports that the store could not be asked. */
    var refreshFails: Boolean = false

    val purchaseCalls = mutableListOf<Pair<StoreProductId, String>>()
    val consumedTokens = mutableListOf<String>()
    var refreshUnsettledCalls: Int = 0
        private set

    override suspend fun loadProducts(ids: Set<StoreProductId>): Result<List<StoreProduct>> =
        Result.success(emptyList())

    override fun observeUnsettledPurchases(): Flow<List<BillingPurchase>> = unsettled.asStateFlow()

    override suspend fun purchase(
        productId: StoreProductId,
        buyerId: String,
    ): BillingOutcome {
        purchaseCalls += productId to buyerId
        return outcome
    }

    override suspend fun consume(purchaseToken: String): Result<Unit> {
        consumedTokens += purchaseToken
        journal += "consume"
        if (consumeThrows) throw IllegalStateException("consume threw")
        return if (consumeFails) {
            Result.failure(IllegalStateException("consume failed"))
        } else {
            unsettled.value = unsettled.value.filterNot { it.purchaseToken == purchaseToken }
            Result.success(Unit)
        }
    }

    override suspend fun isAvailable(): Boolean = true

    /**
     * Publishes the backlog into the queue, exactly as the real adapter does.
     *
     * Not a counter: with an inert refresh, a production delegate that did nothing at all would
     * keep every settler test green while the recovery path — the whole point of the story — was
     * dead. Here a purchase becomes visible *because* the refresh ran.
     */
    override suspend fun refreshUnsettledPurchases(): Result<Unit> {
        refreshUnsettledCalls++
        if (refreshFails) return Result.failure(IllegalStateException("store could not be asked"))
        unsettled.value = backlog.toList()
        return Result.success(Unit)
    }

    /** What the store holds but has not published yet — visible only after a refresh. */
    private val backlog = mutableListOf<BillingPurchase>()

    /** Puts purchases into the store's backlog, as a payment would. */
    fun store(vararg purchases: BillingPurchase) {
        backlog.clear()
        backlog += purchases
    }

    /** Puts purchases straight into the published queue, as a live re-delivery would. */
    fun deliver(vararg purchases: BillingPurchase) {
        backlog.clear()
        backlog += purchases
        unsettled.value = purchases.toList()
    }
}

/** The server's answer, scripted per token. */
class FakePurchaseVerifier(
    private val journal: MutableList<String> = mutableListOf(),
) : PurchaseVerifier {
    private val answers = mutableMapOf<String, () -> PurchaseVerification>()

    val verifiedTokens = mutableListOf<Pair<String, StoreProductId>>()

    fun credits(
        token: String,
        goldGranted: Long = 10L,
        productId: StoreProductId = StoreProductId.GOLD_PACK_SMALL,
        settlementId: String = "settlement-$token",
    ) {
        answers[token] = { PurchaseVerification.Credited(productId, goldGranted, settlementId) }
    }

    fun pends(
        token: String,
        productId: StoreProductId = StoreProductId.GOLD_PACK_SMALL,
    ) {
        answers[token] = { PurchaseVerification.Pending(productId) }
    }

    fun fails(
        token: String,
        error: () -> Throwable,
    ) {
        answers[token] = { throw error() }
    }

    override suspend fun verify(
        purchaseToken: String,
        productId: StoreProductId,
    ): PurchaseVerification {
        verifiedTokens += purchaseToken to productId
        journal += "verify"
        val answer =
            answers[purchaseToken]
                ?: error("FakePurchaseVerifier has no scripted answer for $purchaseToken")
        return answer()
    }
}

/** Connectivity as a switch the test flips. */
class FakeNetworkMonitor(online: Boolean = true) : NetworkMonitor {
    val online = MutableStateFlow(online)

    override fun observeOnline(): Flow<Boolean> = online.asStateFlow()

    override suspend fun isOnline(): Boolean = online.value
}

/** Counts the pulls, so a test can assert the balance was re-read rather than computed. */
class FakeServerBalanceRefresher(
    private val journal: MutableList<String> = mutableListOf(),
) : ServerBalanceRefresher {
    var calls: Int = 0
        private set

    var fails: Boolean = false

    override suspend fun refresh(): Result<Unit> {
        calls++
        journal += "refresh"
        return if (fails) Result.failure(IllegalStateException("refresh failed")) else Result.success(Unit)
    }
}
