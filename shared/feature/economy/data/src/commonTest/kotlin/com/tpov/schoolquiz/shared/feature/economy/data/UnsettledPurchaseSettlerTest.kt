package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingPurchase
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseVerification
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProduct
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.BillingRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.PurchaseVerifier
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.ServerBalanceRefresher
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.SettlePurchaseUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The recovery path: a purchase that was paid for and never credited is finished later.
 *
 * Covers the matrix rows the settler owns — died after paying, account switch, network returns —
 * plus the guarantee that one token is never settled twice at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UnsettledPurchaseSettlerTest {

    private val uid = MutableStateFlow<String?>(null)
    private val network = SettlerFakeNetworkMonitor(online = true)
    private val billing = SettlerFakeBillingRepository()
    private val verifier = SettlerFakePurchaseVerifier()
    private val refresher = SettlerFakeBalanceRefresher()

    private fun settler(scope: CoroutineScope) =
        UnsettledPurchaseSettler(
            currentUidFlow = { uid },
            networkMonitor = network,
            billing = billing,
            settlePurchase =
                SettlePurchaseUseCase(
                    billing = billing,
                    verifier = verifier,
                    balanceRefresher = refresher,
                ),
            scope = scope,
        )

    // Matrix: "Died after paying" — the next start with an account settles the leftover purchase
    // by the ordinary path.
    @Test
    fun `a purchase left over from a killed process is settled on the next start`() =
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val purchase = purchase("t-1")
            billing.deliver(purchase)
            verifier.credits("t-1", gold = 60L)

            val job = settler(scope).start()
            uid.value = "uid-A"
            advanceUntilIdle()

            assertEquals(listOf("t-1"), verifier.verifiedTokens)
            assertEquals(listOf("t-1"), billing.consumedTokens)
            assertEquals(1, refresher.calls)
            job.cancel()
        }

    /** A guest has nothing to settle, and asking the store on their behalf would be wrong. */
    @Test
    fun `no account settles nothing and does not even re-read the queue`() =
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            billing.deliver(purchase("t-1"))
            verifier.credits("t-1")

            val job = settler(scope).start()
            advanceUntilIdle()

            assertEquals(emptyList(), verifier.verifiedTokens)
            assertEquals(0, billing.refreshUnsettledCalls)
            job.cancel()
        }

    // Matrix: "Account switch" — A's subscription is dropped, B's queue is re-read and settled.
    @Test
    fun `switching account re-reads the queue and settles the new account's purchases`() =
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            uid.value = "uid-A"
            val job = settler(scope).start()
            advanceUntilIdle()
            val refreshesForA = billing.refreshUnsettledCalls

            // Into the store, not into the published queue: a purchase nobody has looked for yet.
            // It can only become visible because the switch made somebody ask the store again —
            // which is exactly the guarantee this row exists for.
            billing.store(purchase("t-b"))
            verifier.credits("t-b", gold = 150L)
            uid.value = "uid-B"
            advanceUntilIdle()

            assertEquals(listOf("t-b"), verifier.verifiedTokens)
            assertEquals(listOf("t-b"), billing.consumedTokens)
            assertTrue(
                billing.refreshUnsettledCalls > refreshesForA,
                "the new account never re-read the store, so it could not have found anything",
            )
            job.cancel()
        }

    // Matrix: "Network returns" — the queue is re-read first, then what is in it is settled.
    @Test
    fun `the network coming back re-reads the queue and settles what is in it`() =
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            network.online.value = false
            uid.value = "uid-A"
            billing.deliver(purchase("t-1"))
            verifier.credits("t-1")

            val job = settler(scope).start()
            advanceUntilIdle()
            assertEquals(0, billing.refreshUnsettledCalls)
            assertEquals(emptyList(), verifier.verifiedTokens)

            network.online.value = true
            advanceUntilIdle()

            assertTrue(billing.refreshUnsettledCalls >= 1, "the queue was never re-read")
            assertEquals(listOf("t-1"), verifier.verifiedTokens)
            assertEquals(listOf("t-1"), billing.consumedTokens)
            job.cancel()
        }

    /**
     * The buy flow and the settler are both live on every ordinary purchase, and the store hands
     * the same token to both within milliseconds. Settling it twice would verify it twice and race
     * two consumes; the guard that prevents it belongs to the settlement path they share, not to
     * either caller — a guard in only one of them is a guard the other walks past.
     */
    @Test
    fun `the same token presented by both callers at once is settled once`() =
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            uid.value = "uid-A"
            val settle =
                SettlePurchaseUseCase(
                    billing = billing,
                    verifier = verifier,
                    balanceRefresher = refresher,
                )
            val purchase = purchase("t-1")
            verifier.creditsWhenReleased("t-1")

            // The settler finds it while the buy flow is already settling it.
            val fromBuyFlow = scope.async { settle(purchase) }
            advanceUntilIdle()
            val fromSettler = scope.async { settle(purchase) }
            advanceUntilIdle()

            verifier.release("t-1")
            advanceUntilIdle()

            assertEquals(listOf("t-1"), verifier.verifiedTokens, "verified twice for one purchase")
            assertEquals(listOf("t-1"), billing.consumedTokens, "two consumes raced for one token")
            // Both callers still learn what the player is owed.
            assertEquals(fromBuyFlow.await().getOrNull(), fromSettler.await().getOrNull())
        }

    /** Once finished, the same token can be settled again — the guard defers, it does not blacklist. */
    @Test
    fun `a token is settleable again once its earlier settlement has finished`() =
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val settle =
                SettlePurchaseUseCase(
                    billing = billing,
                    verifier = verifier,
                    balanceRefresher = refresher,
                )
            verifier.credits("t-1")

            settle(purchase("t-1"))
            settle(purchase("t-1"))
            advanceUntilIdle()

            assertEquals(listOf("t-1", "t-1"), verifier.verifiedTokens)
        }

    /**
     * A settlement that could not reach the server is tried again on the settler's own clock.
     *
     * This is the guarantee the store's queue cannot provide: it conflates, so re-reading it when
     * nothing changed emits nothing at all. Before this clock existed, one failed attempt on a
     * stable, online device meant the purchase was never settled until the app restarted — and the
     * test that claimed otherwise only passed because it re-delivered a purchase with a different
     * order id, a value the real store never produces.
     */
    @Test
    fun `a failed settlement is retried without the queue changing at all`() =
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            uid.value = "uid-A"
            verifier.failsWith("t-1") { SyncFailure(SyncError.NoNetwork) }
            billing.store(purchase("t-1"))

            val job = settler(scope).start()
            advanceUntilIdle()

            // Nothing about the queue changed between these attempts.
            assertTrue(
                verifier.verifiedTokens.size > 1,
                "tried once and gave up: ${verifier.verifiedTokens}",
            )
            assertEquals(emptyList(), billing.consumedTokens)

            // And the attempts are bounded — a token the server keeps refusing must not become a
            // call generator. Something real has to happen for it to start over.
            val attemptsBeforeGivingUp = verifier.verifiedTokens.size
            advanceUntilIdle()
            assertEquals(attemptsBeforeGivingUp, verifier.verifiedTokens.size)

            verifier.credits("t-1")
            network.online.value = false
            advanceUntilIdle()
            network.online.value = true
            advanceUntilIdle()

            assertEquals(listOf("t-1"), billing.consumedTokens)
            job.cancel()
        }

    private fun purchase(
        token: String,
        orderId: String = "order-$token",
    ): BillingPurchase =
        BillingPurchase(
            productId = StoreProductId.GOLD_PACK_SMALL,
            purchaseToken = token,
            orderId = orderId,
            isAcknowledged = false,
            priceMicros = 0L,
            currency = "",
        )
}

private class SettlerFakeNetworkMonitor(online: Boolean) : NetworkMonitor {
    val online = MutableStateFlow(online)

    override fun observeOnline(): Flow<Boolean> = online.asStateFlow()

    override suspend fun isOnline(): Boolean = online.value
}

private class SettlerFakeBillingRepository : BillingRepository {
    private val unsettled = MutableStateFlow<List<BillingPurchase>>(emptyList())

    val consumedTokens = mutableListOf<String>()
    var refreshUnsettledCalls: Int = 0
        private set

    override suspend fun loadProducts(ids: Set<StoreProductId>): Result<List<StoreProduct>> =
        Result.success(emptyList())

    override fun observeUnsettledPurchases(): Flow<List<BillingPurchase>> = unsettled.asStateFlow()

    override suspend fun purchase(
        productId: StoreProductId,
        buyerId: String,
    ): BillingOutcome = BillingOutcome.Cancelled

    override suspend fun consume(purchaseToken: String): Result<Unit> {
        consumedTokens += purchaseToken
        // Out of the backlog too: the real store stops listing a purchase once it is consumed, and
        // a fake that kept it would have every refresh hand it back for settling again.
        backlog.removeAll { it.purchaseToken == purchaseToken }
        unsettled.value = unsettled.value.filterNot { it.purchaseToken == purchaseToken }
        return Result.success(Unit)
    }

    override suspend fun isAvailable(): Boolean = true

    /** When true, the store cannot be asked at all — a cold start before Play has bound. */
    var refreshFails: Boolean = false

    /**
     * Publishes the backlog, exactly as the real adapter does.
     *
     * Deliberately not a bare counter. With an inert refresh, a production adapter that stopped
     * re-reading the store would keep every test here green while the recovery this class exists
     * for was dead: a purchase becomes visible *because* the refresh ran.
     */
    override suspend fun refreshUnsettledPurchases(): Result<Unit> {
        refreshUnsettledCalls++
        if (refreshFails) return Result.failure(IllegalStateException("store could not be asked"))
        unsettled.value = backlog.toList()
        return Result.success(Unit)
    }

    /** What the store holds but has not published — visible only after a refresh. */
    private val backlog = mutableListOf<BillingPurchase>()

    /** A purchase that was paid for while nobody was looking. Surfaces on the next refresh. */
    fun store(vararg purchases: BillingPurchase) {
        backlog.clear()
        backlog += purchases
    }

    /** A live re-delivery: published immediately, and kept for later refreshes. */
    fun deliver(vararg purchases: BillingPurchase) {
        backlog.clear()
        backlog += purchases
        unsettled.value = purchases.toList()
    }
}

private class SettlerFakePurchaseVerifier : PurchaseVerifier {
    private val answers = mutableMapOf<String, () -> PurchaseVerification>()

    /** Tokens whose answer is withheld until [release], so a settlement can be held in flight. */
    private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

    val verifiedTokens = mutableListOf<String>()

    fun credits(
        token: String,
        gold: Long = 10L,
    ) {
        answers[token] = { credited(token, gold) }
    }

    fun creditsWhenReleased(
        token: String,
        gold: Long = 10L,
    ) {
        gates[token] = CompletableDeferred()
        answers[token] = { credited(token, gold) }
    }

    fun release(token: String) {
        gates.remove(token)?.complete(Unit)
    }

    fun failsWith(
        token: String,
        error: () -> Throwable,
    ) {
        answers[token] = { throw error() }
    }

    override suspend fun verify(
        purchaseToken: String,
        productId: StoreProductId,
    ): PurchaseVerification {
        verifiedTokens += purchaseToken
        gates[purchaseToken]?.await()
        val answer = answers[purchaseToken] ?: error("no scripted answer for $purchaseToken")
        return answer()
    }

    private fun credited(
        token: String,
        gold: Long,
    ): PurchaseVerification =
        PurchaseVerification.Credited(StoreProductId.GOLD_PACK_SMALL, gold, "settlement-$token")
}

private class SettlerFakeBalanceRefresher : ServerBalanceRefresher {
    var calls: Int = 0
        private set

    override suspend fun refresh(): Result<Unit> {
        calls++
        return Result.success(Unit)
    }
}
