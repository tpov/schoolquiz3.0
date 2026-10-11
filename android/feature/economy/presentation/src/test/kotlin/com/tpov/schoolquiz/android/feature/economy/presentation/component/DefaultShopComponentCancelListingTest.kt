package com.tpov.schoolquiz.android.feature.economy.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.LessonUnlockKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ReferralProgram
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopPurchaseResult
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.GetReferralProgramUseCase
import com.tpov.schoolquiz.shared.core.analytics.NoOpAnalyticsTracker
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.GetShopCatalogUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.LoadStoreShelfUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.ObserveEconomyBalanceUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.PurchaseShopItemUseCase
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Снятие лота из магазина (история 5.6): кладётся в очередь, строку не занимает, витрину локально
 * не правит, а карантин показывает на строке словами «уже продан».
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultShopComponentCancelListingTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)
    private lateinit var lifecycle: LifecycleRegistry

    private val nicknames = FakeNicknameRepository()
    private val logos = FakeLogoRepository()

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
    fun `given airplane mode when a name is taken down then the intent is queued and the row is not busy`() =
        runTest(scheduler) {
            val component = component()
            component.obtainEvent(ShopViewEvent.SelectTab(ShopTab.NICKNAMES))
            val stateBefore = component.state.value

            component.obtainEvent(ShopViewEvent.CancelNicknameListing("alpha"))

            assertEquals(listOf("alpha"), nicknames.cancelled, "намерение легло в очередь")
            val state = component.state.value
            assertNull(state.nicknames.processingNickname, "строка лота не помечена занятой: сервер не зовётся")
            assertNull(state.processingItemId)
            assertEquals(ShopMessage.ListingCancelQueued, state.message)
            assertEquals(
                stateBefore.nicknames.owned,
                state.nicknames.owned,
                "лот не убран локально — витрина обновится сама",
            )
            assertEquals(1, nicknames.ownedCalls, "витрина не перечитана только ради снятия")
        }

    @Test
    fun `given airplane mode when a logo is taken down then the intent is queued and the row is not busy`() =
        runTest(scheduler) {
            val component = component()
            component.obtainEvent(ShopViewEvent.SelectTab(ShopTab.NICKNAMES))

            component.obtainEvent(ShopViewEvent.CancelLogoListing("phoenix"))

            assertEquals(listOf("phoenix"), logos.cancelled)
            assertNull(component.state.value.nicknames.processingNickname)
            assertEquals(ShopMessage.ListingCancelQueued, component.state.value.message)
            assertTrue(
                component.state.value.nicknames.logoListingsByName.containsKey("phoenix"),
                "лот остаётся до обновления",
            )
        }

    @Test
    fun `given the removal was refused because the lot sold then the row reads already sold`() =
        runTest(scheduler) {
            val component = component()
            component.obtainEvent(ShopViewEvent.SelectTab(ShopTab.NICKNAMES))
            assertEquals(emptyMap(), component.state.value.nicknames.nicknameCancelSyncs)

            nicknames.syncs.value = mapOf("alpha" to ListingCancelSync.REFUSED)

            assertEquals(
                mapOf("alpha" to ListingCancelSync.REFUSED),
                component.state.value.nicknames.nicknameCancelSyncs,
                "пометка приходит из очереди по entity_ref, а не из локальной правки",
            )
            assertEquals(setOf("alpha"), nicknames.observed, "спрашивается только про свой лот, не про чужие")
        }

    @Test
    fun `given the logo removal was refused then the logo row reads already sold`() = runTest(scheduler) {
        val component = component()
        component.obtainEvent(ShopViewEvent.SelectTab(ShopTab.NICKNAMES))

        logos.syncs.value = mapOf("phoenix" to ListingCancelSync.REFUSED)

        assertEquals(mapOf("phoenix" to ListingCancelSync.REFUSED), component.state.value.nicknames.logoCancelSyncs)
        assertEquals(setOf("phoenix"), logos.observed, "не выставленный на продажу логотип очередь не спрашивают")
    }

    @Test
    fun `given a queued removal then the row reads queued until the window catches up`() = runTest(scheduler) {
        val component = component()
        component.obtainEvent(ShopViewEvent.SelectTab(ShopTab.NICKNAMES))

        nicknames.syncs.value = mapOf("alpha" to ListingCancelSync.QUEUED)
        assertEquals(ListingCancelSync.QUEUED, component.state.value.nicknames.nicknameCancelSyncs["alpha"])

        // Доехала: запись удалена, очередь про лот ничего не знает; сам лот уйдёт с витрины сам.
        nicknames.syncs.value = emptyMap()
        assertEquals(emptyMap(), component.state.value.nicknames.nicknameCancelSyncs)
    }

    @Test
    fun `given no account to own the intent then the shop says so and the row stays free`() = runTest(scheduler) {
        nicknames.failCancel = IllegalStateException("Снять лот с продажи может только вошедший в аккаунт")
        val component = component()

        component.obtainEvent(ShopViewEvent.CancelNicknameListing("alpha"))

        val state = component.state.value
        assertEquals(ShopMessage.Failure("Снять лот с продажи может только вошедший в аккаунт"), state.message)
        assertNull(state.nicknames.processingNickname)
    }

    private fun component(): DefaultShopComponent {
        lifecycle = LifecycleRegistry()
        val economy = FakeEconomyRepository()
        return DefaultShopComponent(
            componentContext = DefaultComponentContext(lifecycle),
            observeBalance = ObserveEconomyBalanceUseCase(economy),
            getCatalog = GetShopCatalogUseCase(),
            loadStoreShelf = LoadStoreShelfUseCase(StubStoreBilling()),
            purchaseItem = PurchaseShopItemUseCase(economy),
            buyGoldPack = inertBuyGoldPack(),
            analytics = NoOpAnalyticsTracker,
            logos = logos,
            profile = FakeProfileRepository(),
            getReferralProgram = GetReferralProgramUseCase(economy),
            nicknames = nicknames,
        ).also { lifecycle.resume() }
    }

    /** Один свой лот `alpha` на продаже и одно просто имя; что говорит очередь — задаёт тест. */
    private class FakeNicknameRepository : NicknameRepository {
        val cancelled = mutableListOf<String>()
        val observed = mutableSetOf<String>()
        val syncs = MutableStateFlow<Map<String, ListingCancelSync>>(emptyMap())
        var ownedCalls = 0
        var failCancel: Throwable? = null

        override suspend fun checkAvailability(nickname: String): NicknameAvailability = error("unused")

        override suspend fun owned(): List<OwnedNickname> {
            ownedCalls++
            return listOf(
                OwnedNickname("worn", active = true, generated = false),
                OwnedNickname("alpha", active = false, generated = false, listedPrice = 40L),
            )
        }

        override suspend fun setActive(nickname: String) = error("unused")

        override suspend fun claim(nickname: String): Long = error("unused")

        override suspend fun listings(limit: Int): List<NicknameListing> = emptyList()

        override suspend fun listForSale(nickname: String, price: Long) = error("unused")

        override suspend fun cancelListing(nickname: String) {
            failCancel?.let { throw it }
            cancelled += nickname
        }

        override fun observeCancelSync(nickname: String): Flow<ListingCancelSync?> {
            observed += nickname
            return syncs.map { it[nickname] }
        }

        override suspend fun buy(nickname: String): Long = error("unused")
    }

    /** Два своих логотипа, один из них (`phoenix`) выставлен; чужой лот `raven` тоже в окне. */
    private class FakeLogoRepository : LogoRepository {
        val cancelled = mutableListOf<String>()
        val observed = mutableSetOf<String>()
        val syncs = MutableStateFlow<Map<String, ListingCancelSync>>(emptyMap())

        override suspend fun catalog(): List<ProfileLogo> =
            listOf(
                ProfileLogo(name = "phoenix", price = 10L, owned = true, imageUrl = null),
                ProfileLogo(name = "wolf", price = 10L, owned = true, imageUrl = null),
                ProfileLogo(name = "raven", price = 10L, owned = false, imageUrl = null),
            )

        override suspend fun buy(logo: String): Long = error("unused")

        override suspend fun wear(logo: String): String = error("unused")

        override suspend fun listings(limit: Int): List<LogoListing> =
            listOf(
                LogoListing(logo = "phoenix", price = 25L, sellerNickname = "worn", listedAtMs = 1L),
                LogoListing(logo = "raven", price = 25L, sellerNickname = "someone", listedAtMs = 2L),
            )

        override suspend fun listForSale(logo: String, price: Long) = error("unused")

        override suspend fun cancelListing(logo: String) {
            cancelled += logo
        }

        override fun observeCancelSync(logo: String): Flow<ListingCancelSync?> {
            observed += logo
            return syncs.map { it[logo] }
        }

        override suspend fun buyListed(logo: String): Long = error("unused")
    }

    private class FakeProfileRepository : ProfileRepository {
        override fun observeCurrentProfile(): Flow<UserProfile> = emptyFlow()

        override suspend fun currentProfile(): UserProfile = error("unused")

        override suspend fun ensureCurrentProfile(): Result<UserProfile> =
            Result.failure(IllegalStateException("unused"))

        override suspend fun updateNickname(nickname: String): Result<UserProfile> =
            Result.failure(IllegalStateException("unused"))
    }

    private class FakeEconomyRepository : EconomyRepository {
        override fun observeBalance(): Flow<EconomyResourceBalance> = flowOf(EconomyResourceBalance.guest())

        override suspend fun currentBalance(): EconomyResourceBalance = EconomyResourceBalance.guest()

        override suspend fun purchase(itemId: ShopItemId): Result<ShopPurchaseResult> =
            Result.failure(IllegalStateException("unused"))

        override suspend fun unlockLesson(lessonId: String, kind: LessonUnlockKind): Result<EconomyResourceBalance> =
            Result.failure(IllegalStateException("unused"))

        override suspend fun referralProgram(): ReferralProgram = ReferralProgram(link = "", invitedUsers = emptyList())
    }
}
