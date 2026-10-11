package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyConstants
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PremiumGoldOffer
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopCurrency
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProduct
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.shopItem
import com.tpov.schoolquiz.shared.feature.economy.domain.model.storeProductId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetShopCatalogUseCaseTest {
    private val catalog = GetShopCatalogUseCase().execute(EconomyResourceBalance.guest())

    /**
     * Every id reaches the shelf.
     *
     * The catalogue is a hand-written list, so a new [ShopItemId] can be added, wired to a screen
     * and still never appear — the failure looks like a missing feature rather than a missing line.
     */
    @Test
    fun everyShopItemIdAppearsInTheCatalog() {
        assertEquals(ShopItemId.entries.toSet(), catalog.map { it.id }.toSet())
    }

    @Test
    fun catalogHasNoDuplicates() {
        assertEquals(catalog.size, catalog.map { it.id }.distinct().size)
    }

    /** The two rows that open a screen instead of buying something: free, and always open. */
    @Test
    fun doorsAreFreeAndAvailable() {
        listOf(ShopItemId.REFERRAL_PROGRAM, ShopItemId.NICKNAME_MARKET).forEach { id ->
            val item = catalog.single { it.id == id }
            assertTrue(item.isAvailable, "$id must stay reachable")
            val price = assertNotNull(item.price, "$id must carry a price to render")
            assertEquals(ShopCurrency.FREE, price.currency)
            assertEquals(0L, price.amount)
        }
    }

    /**
     * The catalogue is data, not copy: every word the reader sees is picked by the screen from
     * [ShopItemId], so no wording may travel inside the domain model.
     */
    @Test
    fun catalogCarriesNoDisplayStrings() {
        catalog.forEach { item ->
            assertEquals("", item.title, "${item.id} must not carry a title")
            assertEquals("", item.description, "${item.id} must not carry a description")
            assertNull(item.unavailableReason, "${item.id} must not carry an availability reason")
        }
    }

    /** Цены и потолки — из таблицы, которой владеет сервер, а не из чисел в сборке. */
    @Test
    fun slotsArePricedByTheTableLadderAndStopAtItsCeiling() {
        val useCase = GetShopCatalogUseCase()

        val first = useCase.execute(EconomyResourceBalance.guest().copy(standardHearts = 0))
            .single { it.id == ShopItemId.STANDARD_HEART_SLOT }
        val fifth = useCase.execute(EconomyResourceBalance.guest().copy(standardHearts = 4))
            .single { it.id == ShopItemId.STANDARD_HEART_SLOT }
        val past = useCase.execute(EconomyResourceBalance.guest().copy(standardHearts = 7))
            .single { it.id == ShopItemId.STANDARD_HEART_SLOT }
        val full = useCase.execute(EconomyResourceBalance.guest().copy(standardHearts = 10))
            .single { it.id == ShopItemId.STANDARD_HEART_SLOT }

        assertEquals(1_000L, first.price?.amount)
        assertEquals(20_000L, fifth.price?.amount)
        // Лестница короче потолка — последняя ступень повторяется, а не обнуляется.
        assertEquals(20_000L, past.price?.amount)
        assertTrue(past.isAvailable, "семь из десяти — ещё можно купить")
        assertTrue(!full.isAvailable, "десять из десяти — потолок")
    }

    @Test
    fun theLineUnderTheTitleKnowsTheNextRungWithoutHoldingTheLadder() {
        // Экран печатает «следующий будет стоить …» — и лестница для этого живёт в таблице, а не в нём.
        val useCase = GetShopCatalogUseCase()
        val one = useCase.execute(EconomyResourceBalance.guest().copy(standardHearts = 1))
            .single { it.id == ShopItemId.STANDARD_HEART_SLOT }
        val last = useCase.execute(EconomyResourceBalance.guest().copy(standardHearts = 9))
            .single { it.id == ShopItemId.STANDARD_HEART_SLOT }

        assertEquals(5_000L, one.nextPrice?.amount)
        assertEquals(null, last.nextPrice, "после десятого слота следующего нет")
    }

    @Test
    fun plasmaClimbsItsOwnLadderInGold() {
        // `1, 2, 3` золотом вместо плоских десяти: все три стоят шесть, один раз.
        val useCase = GetShopCatalogUseCase()
        val prices = (0..2).map { owned ->
            useCase.execute(EconomyResourceBalance.guest().copy(goldHearts = owned))
                .single { it.id == ShopItemId.GOLD_HEART }
                .price
        }

        assertEquals(listOf(1L, 2L, 3L), prices.map { it?.amount })
        assertTrue(prices.all { it?.currency == ShopCurrency.GOLD })
        val third = useCase.execute(EconomyResourceBalance.guest().copy(goldHearts = 3))
            .single { it.id == ShopItemId.GOLD_HEART }
        assertTrue(!third.isAvailable, "три из трёх — потолок")
    }

    @Test
    fun aTableThatArrivedFromTheServerOverridesTheBuild() {
        // Смысл серверной таблицы: оператор поднял потолок и цену — витрина показывает новое без
        // релиза.
        val raised = EconomyConstants.BOOTSTRAP.copy(
            standard = EconomyConstants.BOOTSTRAP.standard.copy(maxOwned = 12, priceLadder = listOf(500L)),
        )
        val useCase = GetShopCatalogUseCase(constants = { raised })

        val item = useCase.execute(EconomyResourceBalance.guest().copy(standardHearts = 10))
            .single { it.id == ShopItemId.STANDARD_HEART_SLOT }

        assertEquals(500L, item.price?.amount)
        assertTrue(item.isAvailable, "десять из двенадцати — ещё можно")
    }

    @Test
    fun packsAreSoldWithTheStoreOwnWordsAndPrice() {
        // Цена — строка, отформатированная магазином: полка показывает её как есть и не знает,
        // сколько золота за ней стоит (NFR3, NFR4).
        val store =
            listOf(StoreProductId.GOLD_PACK_SMALL, StoreProductId.GOLD_PACK_MEDIUM, StoreProductId.GOLD_PACK_LARGE)
                .map { product(it) }
        val useCase = GetShopCatalogUseCase()

        val packs =
            useCase.execute(EconomyResourceBalance.guest(), store)
                .filter { it.storeProduct != null }

        assertEquals(
            listOf(StoreProductId.GOLD_PACK_SMALL, StoreProductId.GOLD_PACK_MEDIUM, StoreProductId.GOLD_PACK_LARGE),
            packs.map { it.storeProduct?.id },
        )
        assertTrue(packs.all { it.isAvailable }, "названный магазином пак продаётся")
        assertTrue(
            // Каждая запись магазина продаётся своей строкой полки: пара ищется по id, а не позицией.
            store.all { record ->
                packs.single { it.id == record.id.shopItem() }.storeProduct?.formattedPrice == record.formattedPrice
            },
            "строка цены доезжает без изменений",
        )
        assertTrue(packs.all { it.price?.currency == ShopCurrency.EXTERNAL }, "EXTERNAL — разметка, не число")
    }

    @Test
    fun packsStayOnTheShelfLockedWhileTheStoreHasNotAnswered() {
        val useCase = GetShopCatalogUseCase()

        val packs =
            useCase.execute(EconomyResourceBalance.guest(), emptyList())
                .filter { it.id.storeProductId() != null }

        assertEquals(StoreProductId.entries.size, packs.size, "паки остаются записями полки")
        assertTrue(packs.all { !it.isAvailable }, "без ответа магазина пак закрыт")
        assertTrue(packs.all { it.storeProduct == null }, "и не притворяется названным")
    }

    @Test
    fun aPartiallyAnsweringStoreLeavesTheUnquotedPacksLocked() {
        val useCase = GetShopCatalogUseCase()

        val packs = useCase.execute(EconomyResourceBalance.guest(), listOf(product(StoreProductId.GOLD_PACK_SMALL)))
            .filter { it.id.storeProductId() != null }

        // Порядок полки: премиум (2.1) идёт первым, за ним паки. Магазин назвал только пак S.
        assertEquals(
            listOf(false, true, false, false),
            packs.map { it.isAvailable },
            "магазин назвал только тот товар, который продаёт",
        )
    }

    @Test
    fun theServerGoldOfferPricesThePremiumRowAndSellsIt() {
        // История 2.2: цена живёт в таблице сервера и меняется без релиза; ровно одно предложение
        // делает строку продаваемой за золото, каким бы ни ответил магазин.
        val useCase =
            GetShopCatalogUseCase(
                constants = {
                    EconomyConstants.BOOTSTRAP.copy(
                        premiumGoldOffers =
                            listOf(PremiumGoldOffer(sku = "premium_30d", days = 30, goldPrice = 500L)),
                    )
                },
            )

        val item = useCase.execute(EconomyResourceBalance.guest(), listOf(product(StoreProductId.PREMIUM_30D)))
            .single { it.id == ShopItemId.PREMIUM }

        assertEquals(ShopCurrency.GOLD, item.price?.currency)
        assertEquals(500L, item.price?.amount)
        assertTrue(item.isAvailable, "сервер назвал цену — строка продаётся, даже если магазин молчит")
        assertNotNull(item.storeProduct, "ответ магазина едет рядом, а не вместо золотой цены")
    }

    @Test
    fun withoutASingleGoldOfferThePremiumRowBelongsToTheStoreAlone() {
        // Ноль предложений — премиум золотом не продаётся; два — оператор не определился, и полка
        // не выбирает за него. Оба случая оставляют правила истории 2.1: продаёт только магазин.
        for (offers in listOf(emptyList(), listOf(one(), two()))) {
            val useCase = GetShopCatalogUseCase(constants = { EconomyConstants.BOOTSTRAP.copy(premiumGoldOffers = offers) })

            val item = useCase.execute(EconomyResourceBalance.guest(), listOf(product(StoreProductId.PREMIUM_30D)))
                .single { it.id == ShopItemId.PREMIUM }

            assertEquals(ShopCurrency.EXTERNAL, item.price?.currency, "offers: $offers")
            assertTrue(item.isAvailable, "магазин ответил — строка открыта его путём")
        }

        val silent = GetShopCatalogUseCase(constants = { EconomyConstants.BOOTSTRAP })
            .execute(EconomyResourceBalance.guest(), emptyList())
            .single { it.id == ShopItemId.PREMIUM }
        assertTrue(!silent.isAvailable, "никто не назвал цену — строка закрыта")
    }

    private fun one() = PremiumGoldOffer(sku = "premium_30d", days = 30, goldPrice = 500L)

    private fun two() = PremiumGoldOffer(sku = "premium_365d", days = 365, goldPrice = 5000L)

    private fun product(id: StoreProductId) =
        StoreProduct(
            id = id,
            title = "Немного золота",
            description = "Проверка",
            formattedPrice = "1,99 ₴",
            priceMicros = 1_990_000L,
            currency = "UAH",
        )
}
