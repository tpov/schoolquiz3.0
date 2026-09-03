package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.network.syncErrorOrNull
import com.tpov.schoolquiz.shared.feature.economy.domain.fake.FakeBillingRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.fake.FakePurchaseVerifier
import com.tpov.schoolquiz.shared.feature.economy.domain.fake.FakeServerBalanceRefresher
import com.tpov.schoolquiz.shared.feature.economy.domain.fake.billingPurchase
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseRefusalCode
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseRefusedException
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The settlement path: verify → consume → refresh, and never any other order.
 *
 * Every row of the story's matrix that ends inside this use case has a test here.
 */
class SettlePurchaseUseCaseTest {

    private val journal = mutableListOf<String>()
    private val billing = FakeBillingRepository(journal = journal)
    private val verifier = FakePurchaseVerifier(journal = journal)
    private val refresher = FakeServerBalanceRefresher(journal = journal)

    private val settle =
        SettlePurchaseUseCase(
            billing = billing,
            verifier = verifier,
            balanceRefresher = refresher,
        )

    // Matrix: "Buy, happy path" — the settlement half.
    @Test
    fun `credited purchase is verified once, consumed once and the balance re-read once`() =
        runTest {
            val purchase = billingPurchase(token = "t-1")
            verifier.credits("t-1", goldGranted = 60L)

            val result = settle(purchase)

            assertEquals(PurchaseOutcome.Credited(60L), result.getOrNull())
            assertEquals(listOf("t-1" to StoreProductId.GOLD_PACK_SMALL), verifier.verifiedTokens)
            assertEquals(listOf("t-1"), billing.consumedTokens)
            assertEquals(1, refresher.calls)
            assertEquals(listOf("verify", "consume", "refresh"), journal)
        }

    // Matrix: "Server replays" — a token the server has already settled answers CREDITED again and
    // the client behaves identically. This is what makes a killed process recoverable.
    @Test
    fun `a replayed token settles exactly like the first time`() =
        runTest {
            val purchase = billingPurchase(token = "t-1")
            verifier.credits("t-1", goldGranted = 60L)

            val first = settle(purchase)
            journal.clear()
            val second = settle(purchase)

            assertEquals(first.getOrNull(), second.getOrNull())
            assertEquals(listOf("verify", "consume", "refresh"), journal)
        }

    // Matrix: "Server pending" — nothing consumed, not an error, purchase stays in Play's queue.
    @Test
    fun `pending settles nothing and consumes nothing`() =
        runTest {
            verifier.pends("t-1")

            val result = settle(billingPurchase(token = "t-1"))

            assertEquals(PurchaseOutcome.Pending, result.getOrNull())
            assertEquals(emptyList(), billing.consumedTokens)
            assertEquals(0, refresher.calls)
        }

    // Matrix: "Offline at verify" — no consume, the failure reaches the caller as NoNetwork, and
    // the purchase is left for the settler.
    @Test
    fun `a verifier that cannot reach the server consumes nothing and reports NoNetwork`() =
        runTest {
            verifier.fails("t-1") { SyncFailure(SyncError.NoNetwork) }

            val result = settle(billingPurchase(token = "t-1"))

            assertTrue(result.isFailure)
            assertEquals(SyncError.NoNetwork, result.syncErrorOrNull())
            assertEquals(emptyList(), billing.consumedTokens)
            assertEquals(0, refresher.calls)
        }

    // Matrix: "Server refuses" — a success carrying the code, nothing consumed, code intact.
    @Test
    fun `a refusal is a success carrying the code and consumes nothing`() =
        runTest {
            verifier.fails("t-1") {
                PurchaseRefusedException(PurchaseRefusalCode.SKU_NOT_SOLD, "sku is not sold")
            }

            val result = settle(billingPurchase(token = "t-1"))

            assertEquals(
                PurchaseOutcome.Refused(PurchaseRefusalCode.SKU_NOT_SOLD),
                result.getOrNull(),
            )
            assertEquals(emptyList(), billing.consumedTokens)
            assertEquals(0, refresher.calls)
        }

    // Matrix: "Consume fails" — refresh still runs, Credited is still returned, and the token stays
    // in Play's queue so the next delivery replays it.
    @Test
    fun `a failed consume still refreshes the balance and still reports Credited`() =
        runTest {
            verifier.credits("t-1", goldGranted = 150L)
            billing.consumeFails = true
            val purchase = billingPurchase(token = "t-1")
            billing.deliver(purchase)

            val result = settle(purchase)

            assertEquals(PurchaseOutcome.Credited(150L), result.getOrNull())
            assertEquals(listOf("t-1"), billing.consumedTokens)
            assertEquals(1, refresher.calls)
            assertEquals(listOf("verify", "consume", "refresh"), journal)
        }

    /**
     * A refusal must never be mistaken for a settled purchase, whichever code arrives. A server
     * that grows a twelfth reason still refuses; the unknown code is the safe reading.
     */
    @Test
    fun `an unrecognised refusal code still refuses and still consumes nothing`() =
        runTest {
            verifier.fails("t-1") {
                PurchaseRefusedException(
                    PurchaseRefusalCode.fromWire("SOMETHING_THE_CLIENT_HAS_NOT_HEARD_OF"),
                    "refused",
                )
            }

            val result = settle(billingPurchase(token = "t-1"))

            assertEquals(PurchaseOutcome.Refused(PurchaseRefusalCode.UNKNOWN), result.getOrNull())
            assertEquals(emptyList(), billing.consumedTokens)
        }
}
