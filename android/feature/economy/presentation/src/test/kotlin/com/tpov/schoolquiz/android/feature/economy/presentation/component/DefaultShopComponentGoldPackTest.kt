package com.tpov.schoolquiz.android.feature.economy.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.tpov.schoolquiz.shared.core.analytics.AnalyticsEvent
import com.tpov.schoolquiz.shared.core.analytics.AnalyticsTracker
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingPurchase
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyConstants
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyReceipt
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.LessonUnlockKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PremiumGoldOffer
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseRefusedException
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseRefusalCode
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseVerification
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ReferralProgram
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopCurrency
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopPurchaseResult
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProduct
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.BillingRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.PurchaseVerifier
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.ServerBalanceRefresher
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.BuyGoldPackUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.GetReferralProgramUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.GetShopCatalogUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.LoadStoreShelfUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.ObserveEconomyBalanceUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.ObserveEconomyReceiptsUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.PurchaseShopItemUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.SettlePurchaseUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ListingCancelSync
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LogoListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameAvailability
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnedNickname
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileLogo
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.LogoRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.NicknameRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Паки золота на полке (история 1.4): магазин отвечает за слова и цену, компонент — за то, чтобы
 * тап по паку шёл путём покупки 1.2, а каждый исход приходил к игроку своими словами.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultShopComponentGoldPackTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)
    private lateinit var lifecycle: LifecycleRegistry

    private val store = FakeStoreBilling()
    private val economy = FakeEconomyRepository()
    private val analytics = RecordingTracker()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        if (::lifecycle.isInitialized) lifecycle.destroy()
        Dispatchers.resetMain()
    }

    @Test
    fun `given the store quoted its products when the shop opens then the pack rows carry its words`() =
        runTest(scheduler) {
            store.productAnswer = Result.success(listOf(product(StoreProductId.GOLD_PACK_SMALL)))

            val component = component()

            val pack = component.state.value.items.single { it.id == ShopItemId.GOLD_PACK_SMALL }
            assertTrue(pack.isAvailable, "названный магазином пак продаётся")
            assertEquals("Немного золота", pack.storeProduct?.title, "название — слово магазина")
            assertEquals("1,99 ₴", pack.storeProduct?.formattedPrice, "цена — строка магазина без изменений")
            assertEquals(StoreShelfStatus.READY, component.state.value.storeShelf)
        }

    @Test
    fun `given the store could not be asked when the shop opens then the rows stay and explain`() =
        runTest(scheduler) {
            store.productAnswer = Result.failure(IllegalStateException("Billing service unavailable"))

            val component = component()

            val pack = component.state.value.items.single { it.id == ShopItemId.GOLD_PACK_SMALL }
            assertEquals(StoreShelfStatus.UNAVAILABLE, component.state.value.storeShelf)
            assertTrue(!pack.isAvailable, "без магазина пак не продаётся")
            assertTrue(
                component.state.value.items.any { it.id == ShopItemId.GOLD_PACK_LARGE },
                "полка не пустеет",
            )
        }

    @Test
    fun `given the store adapter threw when the shop opens then the shelf explains instead of crashing`() =
        runTest(scheduler) {
            // Адаптер Play бросает IllegalStateException на не-OK ответ queryProductDetails:
            // упавший вопрос магазина — строка с объяснением, а не рухнувший экран.
            store.loadError = IllegalStateException("queryProductDetails: SERVICE_UNAVAILABLE")

            val component = component()

            assertEquals(StoreShelfStatus.UNAVAILABLE, component.state.value.storeShelf)
            val pack = component.state.value.items.single { it.id == ShopItemId.GOLD_PACK_SMALL }
            assertTrue(!pack.isAvailable, "упавший магазин не продаёт")
            assertTrue(
                component.state.value.items.any { it.id == ShopItemId.GOLD_PACK_LARGE },
                "полка не пустеет",
            )
        }

    @Test
    fun `given the player is offline when the pack is tapped then the failure reaches the message line`() =
        runTest(scheduler) {
            val component = component(online = false)

            component.obtainEvent(ShopViewEvent.Purchase(ShopItemId.GOLD_PACK_SMALL))

            assertTrue(
                component.state.value.message is ShopMessage.Failure,
                "отказ до Play доходит словами ошибки, а не тишиной",
            )
            assertEquals(null, component.state.value.processingItemId)
            assertTrue(store.purchaseCalls.isEmpty(), "офлайн-покупка до Play не доходит")
            // Воронка считает и отказы: причины — машинный код, а не тишина и не фраза (AD-15).
            assertEquals(
                listOf(
                    AnalyticsEvent.ShopOpened,
                    AnalyticsEvent.PurchaseStarted("gold_pack_small"),
                    AnalyticsEvent.PurchaseFailed("gold_pack_small", "no_network"),
                ),
                analytics.events,
            )
        }

    @Test
    fun `given nobody is signed in when the pack is tapped then the funnel names no account`() =
        runTest(scheduler) {
            val component = component(signedIn = false)

            component.obtainEvent(ShopViewEvent.Purchase(ShopItemId.GOLD_PACK_SMALL))

            assertTrue(component.state.value.message is ShopMessage.Failure)
            assertTrue(store.purchaseCalls.isEmpty(), "без аккаунта Play не открывается")
            assertEquals(
                listOf(
                    AnalyticsEvent.ShopOpened,
                    AnalyticsEvent.PurchaseStarted("gold_pack_small"),
                    AnalyticsEvent.PurchaseFailed("gold_pack_small", "no_account"),
                ),
                analytics.events,
            )
        }

    @Test
    fun `given a credited purchase when the pack is tapped then the message names the server amount`() =
        runTest(scheduler) {
            store.outcome = BillingOutcome.Purchased(purchase())
            store.verifier.credited = PurchaseVerification.Credited(StoreProductId.GOLD_PACK_SMALL, 10, "s-1")
            val component = component()

            component.obtainEvent(ShopViewEvent.Purchase(ShopItemId.GOLD_PACK_SMALL))

            assertEquals(ShopMessage.GoldCredited(10), component.state.value.message)
            assertEquals(null, component.state.value.processingItemId)
            assertEquals(listOf(StoreProductId.GOLD_PACK_SMALL to "tag"), store.purchaseCalls)
            // Порядок воронки собирается сам: открытие — компонент, старт — компонент до Play,
            // завершение — расчёт, когда сервер уже подтвердил начисление.
            assertEquals(
                listOf(
                    AnalyticsEvent.ShopOpened,
                    AnalyticsEvent.PurchaseStarted("gold_pack_small"),
                    AnalyticsEvent.PurchaseCompleted("gold_pack_small", 1_990_000L, "UAH"),
                ),
                analytics.events,
            )
        }

    @Test
    fun `given the player closed the sheet when the pack is tapped then the message says cancelled`() =
        runTest(scheduler) {
            val component = component()

            component.obtainEvent(ShopViewEvent.Purchase(ShopItemId.GOLD_PACK_SMALL))

            assertEquals(ShopMessage.PurchaseCancelled, component.state.value.message)
            assertTrue(store.consumedTokens.isEmpty(), "отменённая покупка не гасится")
            assertEquals(
                listOf(
                    AnalyticsEvent.ShopOpened,
                    AnalyticsEvent.PurchaseStarted("gold_pack_small"),
                    AnalyticsEvent.PurchaseFailed("gold_pack_small", "cancelled"),
                ),
                analytics.events,
            )
        }

    @Test
    fun `given the server refused then the machine code reaches the message`() = runTest(scheduler) {
        store.outcome = BillingOutcome.Purchased(purchase())
        store.verifier.refusal = PurchaseRefusedException(PurchaseRefusalCode.SKU_NOT_SOLD, "not sold")
        val component = component()

        component.obtainEvent(ShopViewEvent.Purchase(ShopItemId.GOLD_PACK_SMALL))

        assertEquals(ShopMessage.PurchaseRefused("SKU_NOT_SOLD"), component.state.value.message)
        assertTrue(store.consumedTokens.isEmpty(), "отказ не гасит покупку")
        assertEquals(
            listOf(
                AnalyticsEvent.ShopOpened,
                AnalyticsEvent.PurchaseStarted("gold_pack_small"),
                AnalyticsEvent.PurchaseFailed("gold_pack_small", "SKU_NOT_SOLD"),
            ),
            analytics.events,
        )
    }

    @Test
    fun `given a pending payment then the message says it is processing`() = runTest(scheduler) {
        store.outcome = BillingOutcome.Pending(StoreProductId.GOLD_PACK_SMALL)
        val component = component()

        component.obtainEvent(ShopViewEvent.Purchase(ShopItemId.GOLD_PACK_SMALL))

        assertEquals(ShopMessage.PurchasePending, component.state.value.message)
        // Платёж не состоялся и не отказал: в воронке пока висит только старт.
        assertEquals(
            listOf(AnalyticsEvent.ShopOpened, AnalyticsEvent.PurchaseStarted("gold_pack_small")),
            analytics.events,
        )
    }

    @Test
    fun `given a pending subscription then the message does not promise gold`() = runTest(scheduler) {
        // Ожидающая подписка — не «золото придёт»: премиум активируется, когда подтвердит Play.
        store.outcome = BillingOutcome.Pending(StoreProductId.PREMIUM_30D)
        val component = component()

        component.obtainEvent(ShopViewEvent.Purchase(ShopItemId.PREMIUM))

        assertEquals(ShopMessage.PremiumPending, component.state.value.message)
        assertEquals(
            listOf(AnalyticsEvent.ShopOpened, AnalyticsEvent.PurchaseStarted("premium_30d")),
            analytics.events,
        )
    }

    @Test
    fun `given the subscription credited then the message says premium and the token is acknowledged`() =
        runTest(scheduler) {
            store.outcome =
                BillingOutcome.Purchased(
                    BillingPurchase(
                        productId = StoreProductId.PREMIUM_30D,
                        purchaseToken = "sub-1",
                        orderId = "GPA.sub",
                        isAcknowledged = false,
                        priceMicros = 0L,
                        currency = "UAH",
                    ),
                )
            store.verifier.credited = PurchaseVerification.Credited(StoreProductId.PREMIUM_30D, 0, "s-sub")
            val component = component()

            component.obtainEvent(ShopViewEvent.Purchase(ShopItemId.PREMIUM))

            assertEquals(ShopMessage.PremiumActivated, component.state.value.message)
            assertEquals(listOf("sub-1"), store.acknowledgedTokens, "подписка подтверждается, не гасится")
            assertTrue(store.consumedTokens.isEmpty())
        }

    @Test
    fun `given the server quoted gold when the premium row is tapped then the balance path sells it`() =
        runTest(scheduler) {
            economy.purchaseAnswer = {
                Result.success(ShopPurchaseResult(ShopItemId.PREMIUM, EconomyResourceBalance.guest(), "ok"))
            }
            val component =
                component(
                    EconomyConstants.BOOTSTRAP.copy(
                        premiumGoldOffers = listOf(PremiumGoldOffer(sku = "premium_30d", days = 30, goldPrice = 500L)),
                    ),
                )
            assertEquals(ShopCurrency.GOLD, component.state.value.items.single { it.id == ShopItemId.PREMIUM }.price?.currency)

            component.obtainEvent(ShopViewEvent.Purchase(ShopItemId.PREMIUM))

            assertEquals(ShopMessage.PremiumActivated, component.state.value.message)
            assertEquals(listOf(ShopItemId.PREMIUM), economy.purchaseCalls, "золото списывает сервер, а не магазин")
            assertTrue(store.purchaseCalls.isEmpty(), "золотая строка не открывает Play")
            assertTrue(store.acknowledgedTokens.isEmpty())
        }

    @Test
    fun `given the profile carries the premium words when they sync then the surface names them exactly`() =
        runTest(scheduler) {
            val component =
                component(
                    profileRepository =
                        FakeProfileRepository(
                            UserProfile.offline().copy(premiumUntilMs = 7_000L, nextBonusBoxAtMs = 9_000L),
                        ),
                )

            // Оба слова поимённо: перестановка полей местами здесь не проходит.
            assertEquals(7_000L, component.state.value.premium.activeUntilMs)
            assertEquals(9_000L, component.state.value.premium.dailyGiftAvailableAtMs)
        }

    private fun component(
        constants: EconomyConstants = EconomyConstants.BOOTSTRAP,
        online: Boolean = true,
        signedIn: Boolean = true,
        profileRepository: ProfileRepository = FakeProfileRepository(),
    ): DefaultShopComponent {
        lifecycle = LifecycleRegistry()
        return DefaultShopComponent(
            componentContext = DefaultComponentContext(lifecycle),
            observeBalance = ObserveEconomyBalanceUseCase(economy),
            observeReceipts = ObserveEconomyReceiptsUseCase(economy),
            getCatalog = GetShopCatalogUseCase(constants = { constants }),
            loadStoreShelf = LoadStoreShelfUseCase(store),
            purchaseItem = PurchaseShopItemUseCase(economy),
            analytics = analytics,
            buyGoldPack =
                BuyGoldPackUseCase(
                    billing = store,
                    networkMonitor = FakeNetworkMonitor(online),
                    settlePurchase =
                        SettlePurchaseUseCase(
                            billing = store,
                            verifier = store.verifier,
                            balanceRefresher = FakeBalanceRefresher(),
                            analytics = analytics,
                            log = { _, _ -> },
                        ),
                    currentUidFlow = { flowOf(if (signedIn) "uid-1" else null) },
                    buyerTag = { "tag" },
                ),
            logos = FakeLogoRepository(),
            profile = profileRepository,
            getReferralProgram = GetReferralProgramUseCase(economy),
            nicknames = FakeNicknameRepository(),
        ).also { lifecycle.resume() }
    }

    private fun purchase() =
        BillingPurchase(
            productId = StoreProductId.GOLD_PACK_SMALL,
            purchaseToken = "token-1",
            orderId = "order-1",
            isAcknowledged = false,
            priceMicros = 1_990_000L,
            currency = "UAH",
        )

    private fun product(id: StoreProductId) =
        StoreProduct(
            id = id,
            title = "Немного золота",
            description = "Проверка",
            formattedPrice = "1,99 ₴",
            priceMicros = 1_990_000L,
            currency = "UAH",
        )

    /** Память воронки этого теста: порядок событий важнее их числа. */
    private class RecordingTracker : AnalyticsTracker {
        val events = mutableListOf<AnalyticsEvent>()

        override fun track(event: AnalyticsEvent) {
            events += event
        }

        override fun setUserProperty(
            property: com.tpov.schoolquiz.shared.core.analytics.UserProperty,
            value: String,
        ) = Unit

        override fun setUserId(userId: String?) = Unit
    }

    /** Магазин, отвечающий тем, что задал тест: товары — из [productAnswer], покупки — из [outcome]. */
    private class FakeStoreBilling : BillingRepository {
        val verifier = FakeVerifier()
        var outcome: BillingOutcome = BillingOutcome.Cancelled
        var productAnswer: Result<List<StoreProduct>> = Result.success(emptyList())

        /** Заданный тестом бросок вместо ответа: адаптер Play так отвечает на не-OK queryProductDetails. */
        var loadError: Throwable? = null
        val purchaseCalls = mutableListOf<Pair<StoreProductId, String>>()
        val consumedTokens = mutableListOf<String>()
        val acknowledgedTokens = mutableListOf<String>()
        private val queue = MutableStateFlow<List<BillingPurchase>>(emptyList())

        override suspend fun loadProducts(ids: Set<StoreProductId>): Result<List<StoreProduct>> {
            loadError?.let { throw it }
            return productAnswer
        }

        override fun observeUnsettledPurchases(): Flow<List<BillingPurchase>> = queue

        override suspend fun purchase(
            productId: StoreProductId,
            buyerId: String,
        ): BillingOutcome {
            purchaseCalls += productId to buyerId
            return outcome
        }

        override suspend fun refreshUnsettledPurchases(): Result<Unit> = Result.success(Unit)

        override suspend fun consume(purchaseToken: String): Result<Unit> {
            consumedTokens += purchaseToken
            return Result.success(Unit)
        }

        override suspend fun acknowledge(purchaseToken: String): Result<Unit> {
            acknowledgedTokens += purchaseToken
            return Result.success(Unit)
        }

        override suspend fun isAvailable(): Boolean = true
    }

    private class FakeVerifier : PurchaseVerifier {
        var credited: PurchaseVerification? = null
        var refusal: PurchaseRefusedException? = null

        override suspend fun verify(
            purchaseToken: String,
            productId: StoreProductId,
        ): PurchaseVerification {
            refusal?.let { throw it }
            return credited ?: PurchaseVerification.Pending(productId)
        }
    }

    private class FakeBalanceRefresher : ServerBalanceRefresher {
        override suspend fun refresh(): Result<Unit> = Result.success(Unit)
    }

    private class FakeNetworkMonitor(
        private val online: Boolean = true,
    ) : NetworkMonitor {
        override fun observeOnline(): Flow<Boolean> = flowOf(online)

        override suspend fun isOnline(): Boolean = online
    }

    private class FakeNicknameRepository : NicknameRepository {
        override suspend fun checkAvailability(nickname: String): NicknameAvailability = error("unused")

        override suspend fun owned(): List<OwnedNickname> = emptyList()

        override suspend fun setActive(nickname: String) = error("unused")

        override suspend fun claim(nickname: String): Long = error("unused")

        override suspend fun listings(limit: Int): List<NicknameListing> = emptyList()

        override suspend fun listForSale(nickname: String, price: Long) = error("unused")

        override suspend fun cancelListing(nickname: String) = error("unused")

        override fun observeCancelSync(nickname: String): Flow<ListingCancelSync?> = flowOf(null)

        override suspend fun buy(nickname: String): Long = error("unused")
    }

    private class FakeLogoRepository : LogoRepository {
        override suspend fun catalog(): List<ProfileLogo> = emptyList()

        override suspend fun buy(logo: String): Long = error("unused")

        override suspend fun wear(logo: String): String = error("unused")

        override suspend fun listings(limit: Int): List<LogoListing> = emptyList()

        override suspend fun listForSale(logo: String, price: Long) = error("unused")

        override suspend fun cancelListing(logo: String) = error("unused")

        override fun observeCancelSync(logo: String): Flow<ListingCancelSync?> = flowOf(null)

        override suspend fun buyListed(logo: String): Long = error("unused")
    }

    private class FakeProfileRepository(
        initial: UserProfile = UserProfile.offline(),
    ) : ProfileRepository {
        private val state = MutableStateFlow(initial)

        override fun observeCurrentProfile(): Flow<UserProfile> = state

        override suspend fun currentProfile(): UserProfile = state.value

        override suspend fun ensureCurrentProfile(): Result<UserProfile> =
            Result.failure(IllegalStateException("unused"))

        override suspend fun updateNickname(nickname: String): Result<UserProfile> =
            Result.failure(IllegalStateException("unused"))
    }

    private class FakeEconomyRepository : EconomyRepository {
        override fun observeBalance(): Flow<EconomyResourceBalance> = flowOf(EconomyResourceBalance.guest())

        override suspend fun currentBalance(): EconomyResourceBalance = EconomyResourceBalance.guest()

        val purchaseCalls = mutableListOf<ShopItemId>()

        var purchaseAnswer: (ShopItemId) -> Result<ShopPurchaseResult> = { Result.failure(IllegalStateException("unused")) }

        override suspend fun purchase(itemId: ShopItemId): Result<ShopPurchaseResult> {
            purchaseCalls += itemId
            return purchaseAnswer(itemId)
        }

        override suspend fun unlockLesson(
            lessonId: String,
            kind: LessonUnlockKind,
        ): Result<EconomyResourceBalance> = Result.failure(IllegalStateException("unused"))

        override suspend fun referralProgram(): ReferralProgram = ReferralProgram(link = "", invitedUsers = emptyList())
        override fun observeReceipts(limit: Int) = flowOf(emptyList<EconomyReceipt>())
    }
}
