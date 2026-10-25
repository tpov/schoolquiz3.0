package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.feature.economy.domain.fake.FakeBillingRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProduct
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreShelf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoadStoreShelfUseCaseTest {
    private val billing = FakeBillingRepository()
    private val loadShelf = LoadStoreShelfUseCase(billing)

    @Test
    fun `given the store quoted every product when the shelf is loaded then it is ready with them`() = runTest {
        billing.productAnswer = Result.success(listOf(product(StoreProductId.GOLD_PACK_SMALL)))

        val shelf = loadShelf()

        assertTrue(shelf is StoreShelf.Ready, "ответ магазина — готовая полка")
        assertEquals(listOf(StoreProductId.GOLD_PACK_SMALL), (shelf as StoreShelf.Ready).products.map { it.id })
    }

    @Test
    fun `given the store answered with nothing when the shelf is loaded then nothing is configured`() = runTest {
        billing.productAnswer = Result.success(emptyList())

        assertEquals(StoreShelf.NotConfigured, loadShelf())
    }

    @Test
    fun `given the store could not be asked when the shelf is loaded then it is unreachable`() = runTest {
        billing.productAnswer = Result.failure(IllegalStateException("Billing service unavailable"))

        assertEquals(StoreShelf.Unreachable, loadShelf())
    }

    @Test
    fun `given the store answers with a row without a price when the shelf is loaded then the shelf stays ready`() = runTest {
        // Подписка без покупаемого оффера — строка без цены, а не недостающий SKU: ответ остаётся
        // успехом, полка — готовой, а безценная строка лежит на ней рядом с ценами остальных.
        billing.productAnswer =
            Result.success(
                listOf(unpricedPremium(), product(StoreProductId.GOLD_PACK_SMALL)),
            )

        val shelf = loadShelf()

        assertTrue(shelf is StoreShelf.Ready, "строка без цены не роняет полку")
        assertEquals(
            listOf(StoreProductId.PREMIUM_30D, StoreProductId.GOLD_PACK_SMALL),
            (shelf as StoreShelf.Ready).products.map { it.id },
        )
    }

    @Test
    fun `the shelf asks the store about every product it knows`() = runTest {
        loadShelf()

        assertEquals(1, billing.productRequests.size)
        assertEquals(StoreProductId.entries.toSet(), billing.productRequests.single().toSet())
    }

    private fun product(id: StoreProductId) =
        StoreProduct(
            id = id,
            title = "Немного золота",
            description = "Проверка",
            formattedPrice = "1,99 ₴",
            priceMicros = 1_990_000L,
            currency = "UAH",
        )

    /** Ответ магазина о подписке без цены: Play знает товар, но купить его не предлагает. */
    private fun unpricedPremium() =
        StoreProduct(
            id = StoreProductId.PREMIUM_30D,
            title = "Premium",
            description = "Подписка",
            formattedPrice = null,
            priceMicros = 0L,
            currency = "",
        )
}
