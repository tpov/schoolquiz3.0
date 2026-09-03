package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.syncErrorOrNull
import com.tpov.schoolquiz.shared.feature.economy.domain.fake.FakeBillingRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.fake.FakeNetworkMonitor
import com.tpov.schoolquiz.shared.feature.economy.domain.fake.FakePurchaseVerifier
import com.tpov.schoolquiz.shared.feature.economy.domain.fake.FakeServerBalanceRefresher
import com.tpov.schoolquiz.shared.feature.economy.domain.fake.billingPurchase
import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The buy flow: two gates, then the store, then the one settlement path.
 *
 * Covers every matrix row whose behaviour is decided before or around the purchase itself.
 */
class BuyGoldPackUseCaseTest {

    private val journal = mutableListOf<String>()
    private val billing = FakeBillingRepository(journal = journal)
    private val verifier = FakePurchaseVerifier(journal = journal)
    private val refresher = FakeServerBalanceRefresher(journal = journal)
    private val network = FakeNetworkMonitor(online = true)
    private val uid = MutableStateFlow<String?>("uid-A")

    private val buy =
        BuyGoldPackUseCase(
            billing = billing,
            networkMonitor = network,
            settlePurchase =
                SettlePurchaseUseCase(
                    billing = billing,
                    verifier = verifier,
                    balanceRefresher = refresher,
                ),
            currentUidFlow = { uid },
            // Deliberately not the identity: the store must receive the *tag*, never the raw
            // account id. A recognisable stand-in is what lets the assertion below tell the two
            // apart — the hash itself is pinned by `BuyerTagTest`, and cannot be computed here
            // because common code has no digest.
            buyerTag = { "tag($it)" },
        )

    // Matrix: "Buy, happy path" — end to end, and the buyer tag is the uid handed to the store.
    @Test
    fun `a bought pack is verified, consumed and the balance re-read, in that order`() =
        runTest {
            val purchase = billingPurchase(token = "t-1", productId = StoreProductId.GOLD_PACK_MEDIUM)
            billing.outcome = BillingOutcome.Purchased(purchase)
            verifier.credits("t-1", goldGranted = 60L, productId = StoreProductId.GOLD_PACK_MEDIUM)

            val result = buy(StoreProductId.GOLD_PACK_MEDIUM)

            assertEquals(PurchaseOutcome.Credited(60L), result.getOrNull())
            // The tag, not the account id: handing Play the raw uid is what would make the server
            // refuse every real purchase as belonging to somebody else.
            assertEquals(listOf(StoreProductId.GOLD_PACK_MEDIUM to "tag(uid-A)"), billing.purchaseCalls)
            assertEquals(listOf("verify", "consume", "refresh"), journal)
        }

    // Matrix: "Offline before buying" — refused before Play is opened, with a named reason, and
    // nothing queued anywhere.
    @Test
    fun `offline refuses with NoNetwork and never opens the store`() =
        runTest {
            network.online.value = false

            val result = buy(StoreProductId.GOLD_PACK_SMALL)

            assertTrue(result.isFailure)
            assertEquals(SyncError.NoNetwork, result.syncErrorOrNull())
            assertEquals(emptyList(), billing.purchaseCalls)
            assertEquals(emptyList(), verifier.verifiedTokens)
        }

    // Matrix: "No account" — Play is never opened.
    @Test
    fun `no signed-in account refuses before the store is opened`() =
        runTest {
            uid.value = null

            val result = buy(StoreProductId.GOLD_PACK_SMALL)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(emptyList(), billing.purchaseCalls)
        }

    /**
     * The uid is read at the moment of buying, not held. A tag captured at construction time would
     * follow the previous account after a switch.
     */
    @Test
    fun `the buyer tag follows the account that is signed in when the purchase starts`() =
        runTest {
            billing.outcome = BillingOutcome.Cancelled
            buy(StoreProductId.GOLD_PACK_SMALL)
            uid.value = "uid-B"
            buy(StoreProductId.GOLD_PACK_SMALL)

            assertEquals(listOf("tag(uid-A)", "tag(uid-B)"), billing.purchaseCalls.map { it.second })
        }

    // Matrix: "Play pending" — the verifier is never called.
    @Test
    fun `a pending store answer never reaches the verifier`() =
        runTest {
            billing.outcome = BillingOutcome.Pending(StoreProductId.GOLD_PACK_SMALL)

            val result = buy(StoreProductId.GOLD_PACK_SMALL)

            assertEquals(PurchaseOutcome.Pending, result.getOrNull())
            assertEquals(emptyList(), verifier.verifiedTokens)
            assertEquals(emptyList(), billing.consumedTokens)
        }

    @Test
    fun `a cancelled purchase is an ending, not a failure`() =
        runTest {
            billing.outcome = BillingOutcome.Cancelled

            val result = buy(StoreProductId.GOLD_PACK_SMALL)

            assertEquals(PurchaseOutcome.Cancelled, result.getOrNull())
            assertEquals(emptyList(), verifier.verifiedTokens)
        }

    // Matrix: "Play already owned", first half — a named purchase is settled, because "already
    // owned" for a consumable means an earlier attempt never finished.
    @Test
    fun `already owned with a named purchase is settled rather than refused`() =
        runTest {
            val purchase = billingPurchase(token = "t-old")
            billing.outcome = BillingOutcome.AlreadyOwned(purchase)
            verifier.credits("t-old", goldGranted = 10L)

            val result = buy(StoreProductId.GOLD_PACK_SMALL)

            assertEquals(PurchaseOutcome.Credited(10L), result.getOrNull())
            assertEquals(listOf("t-old"), billing.consumedTokens)
        }

    // Matrix: "Play already owned", second half — no purchase named, so the queue is re-read and
    // the caller is told why.
    @Test
    fun `already owned without a purchase re-reads the queue and fails with a named reason`() =
        runTest {
            billing.outcome = BillingOutcome.AlreadyOwned(null)

            val result = buy(StoreProductId.GOLD_PACK_SMALL)

            assertTrue(result.isFailure)
            assertEquals(1, billing.refreshUnsettledCalls)
            val message = result.exceptionOrNull()?.message
            assertTrue(!message.isNullOrBlank(), "Expected a named reason, got $message")
        }

    @Test
    fun `a store that is unavailable fails without consuming anything`() =
        runTest {
            billing.outcome = BillingOutcome.Unavailable("no Play Services")

            val result = buy(StoreProductId.GOLD_PACK_SMALL)

            assertTrue(result.isFailure)
            assertNull(result.getOrNull())
            assertEquals(emptyList(), billing.consumedTokens)
        }
}
