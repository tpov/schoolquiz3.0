package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.network.syncErrorOrNull
import com.tpov.schoolquiz.shared.core.analytics.AnalyticsEvent
import com.tpov.schoolquiz.shared.feature.economy.domain.fake.FakeBillingRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.fake.RecordingAnalyticsTracker
import com.tpov.schoolquiz.shared.feature.economy.domain.fake.FakePurchaseVerifier
import com.tpov.schoolquiz.shared.feature.economy.domain.fake.FakeServerBalanceRefresher
import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingPurchase
import com.tpov.schoolquiz.shared.feature.economy.domain.fake.billingPurchase
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseRefusalCode
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseRefusedException
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import kotlinx.coroutines.launch
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

    private val analytics = RecordingAnalyticsTracker()

    private val settle =
        SettlePurchaseUseCase(
            billing = billing,
            verifier = verifier,
            balanceRefresher = refresher,
            analytics = analytics,
        )

    // Matrix: "Buy, happy path" — the settlement half.
    @Test
    fun `credited purchase is verified once, consumed once and the balance re-read once`() =
        runTest {
            val purchase = billingPurchase(token = "t-1")
            verifier.credits("t-1", goldGranted = 60L)

            val result = settle(purchase)

            assertEquals(PurchaseOutcome.Credited(StoreProductId.GOLD_PACK_SMALL, 60L), result.getOrNull())
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

    // Funnel (story 1.5): the completion event is the settlement's own, emitted on CREDITED with
    // the price the store took, and nowhere else.
    @Test
    fun `the credited settlement emits purchase completed with the store price`() =
        runTest {
            verifier.credits("t-1", goldGranted = 60L)

            // Цена не дефолтная нарочно: нули не отличили бы «перенёс цену покупки» от
            // «захардкодил ноль» — а это и есть NFR4, ради которого событие здесь.
            settle(billingPurchase(token = "t-1", priceMicros = 1_990_000L, currency = "UAH"))

            assertEquals(
                listOf<AnalyticsEvent>(
                    AnalyticsEvent.PurchaseCompleted(
                        productId = StoreProductId.GOLD_PACK_SMALL.playSku,
                        priceMicros = 1_990_000L,
                        currency = "UAH",
                    ),
                ),
                analytics.events,
            )
        }

    @Test
    fun `pending, refusals and failures emit no completion event`() =
        runTest {
            verifier.pends("t-pending")
            settle(billingPurchase(token = "t-pending"))
            verifier.fails("t-refused") { PurchaseRefusedException(PurchaseRefusalCode.SKU_NOT_SOLD, "not sold") }
            settle(billingPurchase(token = "t-refused"))
            verifier.fails("t-failed") { SyncFailure(SyncError.NoNetwork) }
            settle(billingPurchase(token = "t-failed"))

            assertTrue(analytics.events.isEmpty(), "не-начисления не являются завершением покупки")
        }

    // История 2.1: подписка гасится никогда — только подтверждается. Тот же путь расчёта, другой
    // жест магазину, выбираемый товаром, а не вызывающим.
    @Test
    fun `a premium subscription is acknowledged and never consumed`() =
        runTest {
            val purchase =
                BillingPurchase(
                    productId = StoreProductId.PREMIUM_30D,
                    purchaseToken = "sub-token-1",
                    orderId = "GPA.sub",
                    isAcknowledged = false,
                    priceMicros = 0L,
                    currency = "",
                )
            verifier.credits("sub-token-1", goldGranted = 0, productId = StoreProductId.PREMIUM_30D)

            val result = settle(purchase)

            assertEquals(PurchaseOutcome.Credited(StoreProductId.PREMIUM_30D, 0L), result.getOrNull())
            assertEquals(listOf("sub-token-1"), billing.acknowledgedTokens)
            assertTrue(billing.consumedTokens.isEmpty(), "подписка не гасится — погашенный токен не продлевается")
            // Тот же порядок, другой жест: расчёт — подтверждение — перечитывание.
            assertEquals(listOf("verify", "acknowledge", "refresh"), journal)
        }

    @Test
    fun `a failed acknowledgment stays survivable like a failed consume`() =
        runTest {
            val purchase =
                BillingPurchase(
                    productId = StoreProductId.PREMIUM_30D,
                    purchaseToken = "sub-token-2",
                    orderId = "GPA.sub2",
                    isAcknowledged = false,
                    priceMicros = 0L,
                    currency = "",
                )
            verifier.credits("sub-token-2", goldGranted = 0, productId = StoreProductId.PREMIUM_30D)
            billing.acknowledgeFails = true

            val result = settle(purchase)

            assertEquals(PurchaseOutcome.Credited(StoreProductId.PREMIUM_30D, 0L), result.getOrNull())
            assertEquals(1, refresher.calls, "баланс перечитан и при неудачном подтверждении")
        }

    @Test
    fun `a throwing acknowledgment stays survivable like a failed one`() =
        runTest {
            // Бросок — вторая форма из KDoc handBack: адаптер перечитывает очередь внутри успеха,
            // и работавший жест может упасть на обратном пути (умерший биндер). Расчёт уже был —
            // игроку отказом это быть не должно.
            val purchase =
                BillingPurchase(
                    productId = StoreProductId.PREMIUM_30D,
                    purchaseToken = "sub-token-2x",
                    orderId = "GPA.sub2x",
                    isAcknowledged = false,
                    priceMicros = 0L,
                    currency = "",
                )
            verifier.credits("sub-token-2x", goldGranted = 0, productId = StoreProductId.PREMIUM_30D)
            billing.acknowledgeThrows = true

            val result = settle(purchase)

            assertEquals(PurchaseOutcome.Credited(StoreProductId.PREMIUM_30D, 0L), result.getOrNull())
            assertEquals(1, refresher.calls, "баланс перечитан и при брошенном подтверждении")
        }

    @Test
    fun `a replayed token emits the completion only once`() =
        runTest {
            // Под runTest запуски исполняются по очереди: второй расчёт пере-проходит verify и
            // доходит до CREDITED — держит его множество рассчитанных токенов, не ожидание в полёте.
            verifier.credits("t-1", goldGranted = 60L)
            val purchase = billingPurchase(token = "t-1")

            val owner = launch { settle(purchase) }
            val waiter = launch { settle(purchase) }
            owner.join()
            waiter.join()

            assertEquals(1, analytics.events.size, "токен завершается один раз в воронке")
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
                PurchaseRefusedException(PurchaseRefusalCode.PREMIUM_QUANTITY_UNSUPPORTED, "one premium item only")
            }

            val result = settle(billingPurchase(token = "t-1"))

            assertEquals(
                PurchaseOutcome.Refused(PurchaseRefusalCode.PREMIUM_QUANTITY_UNSUPPORTED),
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

            assertEquals(PurchaseOutcome.Credited(StoreProductId.GOLD_PACK_SMALL, 150L), result.getOrNull())
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
