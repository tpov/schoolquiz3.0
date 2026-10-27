@file:Suppress("MaxLineLength")

package com.tpov.schoolquiz.android.feature.quest.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeAuthRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeCatalogRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeLessonRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeQuestRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeSectionRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeThemeRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.buildCatalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.model.QuestType
import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxFailureReason
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxOpening
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxOpeningException
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxReward
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimException
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimFailureReason
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimStatus
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PendingGiftBoxOpening
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftBoxLocalStreakStore
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftBoxRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftClaimRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftClaimRequest
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.LocalBoxStreak
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.ClaimPremiumDailyGiftUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.OpenGiftBoxUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.SyncBoxStreakUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.ObserveCurrentProfileUseCase
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for [DefaultHomeQuestsComponent].
 *
 * Tests are traced to AC#21 (catalog list in home state) and AC#22 (archived excluded).
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 * Plan: docs/features/home-and-my-quests/plan/phase-05/tests.md §2
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultHomeQuestsComponentTest {
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private lateinit var lifecycle: LifecycleRegistry

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        if (::lifecycle.isInitialized) {
            lifecycle.stop()
            lifecycle.destroy()
        }
        Dispatchers.resetMain()
    }

    private fun testCtx(): DefaultComponentContext {
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
        return DefaultComponentContext(lifecycle)
    }

    private val day = 24L * 60 * 60 * 1000
    private val now = MutableStateFlow(10_000L * day)
    private val online = MutableStateFlow(true)

    private fun buildComponent(
        catalogRepo: FakeCatalogRepository = FakeCatalogRepository(),
        profileRepo: FakeProfileRepository = FakeProfileRepository(),
        giftBoxRepo: FakeGiftBoxRepository = FakeGiftBoxRepository(),
        authRepo: FakeAuthRepository = FakeAuthRepository(),
        claimRepo: FakeGiftClaimRepository = FakeGiftClaimRepository(),
        onCatalogDrillDown: (CatalogId, String) -> Unit = { _, _ -> },
        onResumeLesson: (LessonId) -> Unit = {},
    ): DefaultHomeQuestsComponent {
        val claims = GiftClaimPort(claimRepo, profileRepo)
        return DefaultHomeQuestsComponent(
            componentContext = testCtx(),
            observeCatalogs = ObserveCatalogsUseCase(catalogRepo),
            observeProfile = ObserveCurrentProfileUseCase(profileRepo),
            openGiftBoxUseCase = OpenGiftBoxUseCase(giftBoxRepo),
            syncBoxStreak =
                SyncBoxStreakUseCase(
                    claims = claims,
                    profileRepository = profileRepo,
                    localStreak = FakeLocalStreakStore(),
                    network = onlineMonitor(),
                    currentUidFlow = { flowOf("A") },
                    clock = { now.value },
                ),
            claimPremiumDailyGift = ClaimPremiumDailyGiftUseCase(claims),
            networkMonitor = onlineMonitor(),
            onCatalogDrillDown = onCatalogDrillDown,
            onResumeLesson = onResumeLesson,
            attemptRepository = FakeLessonAttemptRepository(),
            lessonRepository = FakeLessonRepository(),
            themeRepository = FakeThemeRepository(),
            sectionRepository = FakeSectionRepository(),
            questRepository = FakeQuestRepository(),
            authRepository = authRepo,
            mainContext = testDispatcher,
            nowMs = { now.value },
        )
    }

    private fun onlineMonitor() =
        object : NetworkMonitor {
            override fun observeOnline(): Flow<Boolean> = online

            override suspend fun isOnline() = online.value
        }

    /** Общий порт заявок: и репозиторий в use-cases, и счётчик того, что нажал экран. */
    private class GiftClaimPort(
        private val fake: FakeGiftClaimRepository,
        private val profileRepo: FakeProfileRepository,
    ) : GiftClaimRepository by fake {
        override suspend fun submit(request: GiftClaimRequest): Result<GiftClaimOutcome> {
            fake.submits += request.kind
            return Result.success(outcome(request.kind))
        }

        private fun outcome(kind: GiftClaimKind) =
            GiftClaimOutcome(
                kind,
                if (kind == GiftClaimKind.PREMIUM_DAILY) GiftClaimStatus.CLAIMED else GiftClaimStatus.NO_CHANGE,
                replayed = false,
                boxCount = profileRepo.state.value.boxCount + 1,
                boxStreakDays = profileRepo.state.value.boxStreakDays,
                nextBoxAtMs = profileRepo.state.value.nextBoxAtMs,
                nextBonusBoxAtMs = profileRepo.state.value.nextBonusBoxAtMs,
                earnedBoxes = 1,
                premiumUntilMs = profileRepo.state.value.premiumUntilMs,
            )
    }

    private class FakeGiftClaimRepository : GiftClaimRepository {
        val submits = mutableListOf<GiftClaimKind>()
        val resumes = mutableListOf<GiftClaimKind>()

        // Лежащие намерения отдельны по виду — как строки (owner_uid, kind) в магазине.
        val pendingKinds = MutableStateFlow<Set<GiftClaimKind>>(emptySet())

        /** Сколько следующих resume упадут, оставив намерение лежать (транспортный отказ). */
        var resumeFailures = 0
        val resumeAttempts = mutableListOf<GiftClaimKind>()

        override suspend fun submit(request: GiftClaimRequest) = Result.success(
            GiftClaimOutcome(request.kind, GiftClaimStatus.CLAIMED, false, 1, 2, 3, 4, 1, 5),
        )

        // Лежащее намерение возобновляется по-настоящему: исход разрешает его и снимает pending —
        // null только при отсутствии намерения, как в репозитории.
        override suspend fun resumePending(kind: GiftClaimKind): Result<GiftClaimOutcome>? {
            if (kind !in pendingKinds.value) return null
            resumeAttempts += kind
            if (resumeFailures > 0) {
                resumeFailures--
                return Result.failure(
                    GiftClaimException(GiftClaimFailureReason.TRANSPORT, kind),
                )
            }
            resumes += kind
            pendingKinds.value = pendingKinds.value - kind
            return Result.success(
                GiftClaimOutcome(kind, GiftClaimStatus.CLAIMED, replayed = true, 1, 2, 3, 4, 1, 5),
            )
        }

        override fun observePending(kind: GiftClaimKind): Flow<Boolean> =
            pendingKinds.map { kind in it }.distinctUntilChanged()
    }

    private class FakeLocalStreakStore : GiftBoxLocalStreakStore {
        override suspend fun find(ownerUid: String): LocalBoxStreak? = null

        override suspend fun save(ownerUid: String, value: LocalBoxStreak) = Unit

        override suspend fun forget(ownerUid: String) = Unit
    }

    // ── AC#21 — catalogs from repository appear in state ─────────────────────

    @Test
    fun `when observeCatalogs emits then state catalogs updated`() =
        runTest {
            val catalogRepo = FakeCatalogRepository()
            catalogRepo.emit(
                listOf(
                    buildCatalog(id = "cat1", name = "Catalog 1"),
                    buildCatalog(id = "cat2", name = "Catalog 2"),
                ),
            )

            val component = buildComponent(catalogRepo = catalogRepo)

            val catalogs = component.state.value.catalogs
            assertEquals(2, catalogs.size, "both catalogs must appear in state")
            assertTrue(catalogs.any { it.id == CatalogId("cat1") })
            assertTrue(catalogs.any { it.id == CatalogId("cat2") })
        }

    // ── AC#22 — archived catalog not in state ────────────────────────────────

    @Test
    fun `when catalog is archived then it is not in state`() =
        runTest {
            val catalogRepo = FakeCatalogRepository()
            catalogRepo.emit(
                listOf(
                    buildCatalog(id = "active", archived = false),
                    buildCatalog(id = "archived", archived = true),
                ),
            )

            val component = buildComponent(catalogRepo = catalogRepo)

            val catalogs = component.state.value.catalogs
            assertEquals(1, catalogs.size, "archived catalog must be excluded")
            assertEquals(CatalogId("active"), catalogs.first().id)
        }

    @Test
    fun `course catalogs are not shown in home`() =
        runTest {
            val catalogRepo = FakeCatalogRepository()
            catalogRepo.emit(
                listOf(
                    buildCatalog(id = "school", name = "Школа"),
                    buildCatalog(id = "courses", name = "Курсы", questType = QuestType.COURSE),
                ),
            )

            val component = buildComponent(catalogRepo = catalogRepo)

            val catalogs = component.state.value.catalogs
            assertEquals(1, catalogs.size, "courses catalog is opened from archive, not home")
            assertEquals(CatalogId("school"), catalogs.first().id)
        }

    // ── Late catalog emission updates state reactively ────────────────────────

    @Test
    fun `when catalog emitted after construction then state updates`() =
        runTest {
            val catalogRepo = FakeCatalogRepository()
            val component = buildComponent(catalogRepo = catalogRepo)

            assertTrue(component.state.value.catalogs.isEmpty(), "initial state must be empty")

            catalogRepo.emit(listOf(buildCatalog(id = "cat1")))

            assertEquals(1, component.state.value.catalogs.size, "state must update on late emission")
            assertEquals(CatalogId("cat1"), component.state.value.catalogs.first().id)
        }

    // ── Empty catalog list → not stuck in loading ─────────────────────────────

    @Test
    fun `when catalog list is empty then state is empty and not loading`() =
        runTest {
            val component = buildComponent(catalogRepo = FakeCatalogRepository())

            val state = component.state.value
            assertTrue(state.catalogs.isEmpty(), "empty store → empty catalog list")
            assertFalse(state.isLoading, "must not be stuck in loading state on empty data")
        }

    // ── HC-U-01 — onCatalogClick invokes onCatalogDrillDown lambda ─────────────
    // Spec: docs/features/quizzes-screen/plan/phase-07/tests.md HC-U-01
    // GIVEN onCatalogClick(CatalogId("cat-1"), "Mathematics")
    // THEN lambda invoked with (CatalogId("cat-1"), "Mathematics")

    @Test
    fun `when onCatalogClick called then onCatalogDrillDown lambda invoked with correct catalogId and catalogName`() =
        runTest {
            var capturedId: CatalogId? = null
            var capturedName: String? = null

            val component =
                buildComponent(
                    onCatalogDrillDown = { id, name ->
                        capturedId = id
                        capturedName = name
                    },
                )

            component.onCatalogClick(CatalogId("cat-1"), "Mathematics")

            assertEquals(CatalogId("cat-1"), capturedId, "lambda must receive the clicked catalog id")
            assertEquals("Mathematics", capturedName, "lambda must receive the name passed from UI")
        }

    // ── HC-U-02 — blank name is forwarded as-is ────────────────────────────────
    // Spec: docs/features/quizzes-screen/plan/phase-07/tests.md HC-U-02
    // GIVEN onCatalogClick with blank name
    // THEN lambda invoked; capturedName == "" (screen resolves blank to localized fallback)

    @Test
    fun `when onCatalogClick called with blank name then catalogName is forwarded unchanged`() =
        runTest {
            var capturedId: CatalogId? = null
            var capturedName: String? = null

            val component =
                buildComponent(
                    onCatalogDrillDown = { id, name ->
                        capturedId = id
                        capturedName = name
                    },
                )

            component.onCatalogClick(CatalogId("cat-1"), "")

            assertEquals(CatalogId("cat-1"), capturedId, "lambda must receive the clicked id even if name is blank")
            assertEquals("", capturedName, "blank name must be forwarded; the screen resolves the fallback label")
        }

    // ── HC-U-03 — lambda is only side-effect, no double invocation ────────────
    // Spec: docs/features/quizzes-screen/plan/phase-07/tests.md HC-U-03
    // GIVEN component
    // WHEN onCatalogClick called once
    // THEN lambda invoked exactly once

    @Test
    fun `when onCatalogClick called once then lambda invoked exactly once`() =
        runTest {
            var invocationCount = 0

            val component =
                buildComponent(
                    onCatalogDrillDown = { _, _ -> invocationCount++ },
                )

            component.onCatalogClick(CatalogId("cat-1"), "Mathematics")

            assertEquals(1, invocationCount, "onCatalogDrillDown must be invoked exactly once per click")
        }

    @Test
    fun `when profile has boxes then gift box count appears in state`() =
        runTest {
            val profileRepo = FakeProfileRepository(UserProfile.offline().copy(boxCount = 2, boxStreakDays = 7))
            val component = buildComponent(profileRepo = profileRepo)

            assertEquals(2, component.state.value.giftBoxCount)
            assertEquals(7, component.state.value.giftBoxStreakDays)
        }

    @Test
    fun `when gift box opened then state shows reward and remaining count`() =
        runTest {
            val profileRepo = FakeProfileRepository(UserProfile.offline().copy(boxCount = 1))
            val giftBoxRepo =
                FakeGiftBoxRepository(
                    Result.success(
                        GiftBoxOpening(
                            reward = GiftBoxReward.Nolics(500),
                            profileSynced = true,
                            remainingBoxCount = 0,
                        ),
                    ),
                )
            val component = buildComponent(profileRepo = profileRepo, giftBoxRepo = giftBoxRepo)

            component.onGiftBoxFabClick()

            val opening = component.state.value.giftBoxOpening as HomeGiftBoxOpeningState.Opened
            assertEquals(GiftBoxReward.Nolics(500), opening.reward)
            assertEquals(0, opening.remainingBoxCount)
        }

    @Test
    fun `pending result is restored with zero boxes and only dismissal acknowledges it`() =
        runTest {
            val repo =
                FakeGiftBoxRepository(
                    Result.success(
                        GiftBoxOpening(GiftBoxReward.Nolics(500), true, 0, "opening-1", "A"),
                    ),
                )
            repo.pending.value = PendingGiftBoxOpening("opening-1", "A", true)
            val component = buildComponent(giftBoxRepo = repo, authRepo = FakeAuthRepository("A"))
            assertEquals(0, component.state.value.giftBoxCount)
            assertTrue(component.state.value.hasPendingGiftBoxOpening)
            assertTrue(component.state.value.giftBoxOpening is HomeGiftBoxOpeningState.Opened)
            assertEquals(0, repo.opens)
            assertEquals(emptyList(), repo.acknowledged)
            component.onGiftBoxDismiss()
            assertEquals(listOf("opening-1"), repo.acknowledged)
            assertFalse(component.state.value.hasPendingGiftBoxOpening)
        }

    @Test
    fun `dismissing uncertain result keeps recovery reachable at zero boxes`() =
        runTest {
            val repo =
                FakeGiftBoxRepository(
                    Result.failure(
                        GiftBoxOpeningException(GiftBoxFailureReason.PENDING, "opening-1", "A"),
                    ),
                )
            repo.pending.value = PendingGiftBoxOpening("opening-1", "A", false)
            val component = buildComponent(giftBoxRepo = repo, authRepo = FakeAuthRepository("A"))
            component.onGiftBoxDismiss()
            assertTrue(component.state.value.hasPendingGiftBoxOpening)
            assertEquals(null, component.state.value.giftBoxOpening)
            component.onGiftBoxFabClick()
            assertTrue(component.state.value.giftBoxOpening is HomeGiftBoxOpeningState.Failed)
            assertEquals(0, repo.opens)
        }

    // ── 7.4: кнопка «Получить подарок» — типы поверх серверных слов ──────────

    private fun premiumProfile(
        untilMs: Long,
        nextBonusAtMs: Long,
    ) = UserProfile.offline().copy(premiumUntilMs = untilMs, nextBonusBoxAtMs = nextBonusAtMs)

    @Test
    fun `premium gift is available when the term is active and the daily slot is free`() =
        runTest {
            val profileRepo =
                FakeProfileRepository(premiumProfile(untilMs = now.value + day, nextBonusAtMs = 0L))
            val component = buildComponent(profileRepo = profileRepo)

            assertEquals(HomePremiumGiftState.Available, component.state.value.premiumGift)
        }

    @Test
    fun `claimed slot reads as claimed today and expired term hides the button`() =
        runTest {
            val claimed =
                buildComponent(
                    profileRepo =
                        FakeProfileRepository(premiumProfile(untilMs = now.value + day, nextBonusAtMs = now.value + 1)),
                )
            assertEquals(HomePremiumGiftState.ClaimedToday, claimed.state.value.premiumGift)

            val hidden =
                buildComponent(
                    profileRepo = FakeProfileRepository(premiumProfile(untilMs = now.value - 1, nextBonusAtMs = 0L)),
                )
            assertEquals(HomePremiumGiftState.Hidden, hidden.state.value.premiumGift)
        }

    @Test
    fun `a pending premium intent left by process death resumes itself on entry`() =
        runTest {
            val claimRepo = FakeGiftClaimRepository()
            claimRepo.pendingKinds.value = setOf(GiftClaimKind.PREMIUM_DAILY)
            val component =
                buildComponent(
                    profileRepo = FakeProfileRepository(premiumProfile(untilMs = now.value + day, nextBonusAtMs = 0L)),
                    claimRepo = claimRepo,
                )
            testScheduler.advanceUntilIdle()

            assertEquals(
                listOf(GiftClaimKind.PREMIUM_DAILY),
                claimRepo.resumes,
                "перезапуск доигрывает лежащую заявку тем же operationId без всякого тапа",
            )
            assertTrue(!claimRepo.pendingKinds.value.contains(GiftClaimKind.PREMIUM_DAILY), "разрешённое намерение больше не числится лежащим")
            assertTrue(component.state.value.premiumGift !is HomePremiumGiftState.InFlight)
        }

    // ── 7.4 retro F2: re-arm лежащего намерения — Claiming конечен ────────────

    @Test
    fun `an offline premium resume waits for the network and settles on reconnect without a new submit`() =
        runTest {
            online.value = false
            val claimRepo = FakeGiftClaimRepository()
            claimRepo.pendingKinds.value = setOf(GiftClaimKind.PREMIUM_DAILY)
            val component =
                buildComponent(
                    profileRepo = FakeProfileRepository(premiumProfile(untilMs = now.value + day, nextBonusAtMs = 0L)),
                    claimRepo = claimRepo,
                )
            testScheduler.advanceUntilIdle()

            assertEquals(
                emptyList(),
                claimRepo.resumeAttempts,
                "без связи попытка не начинается вовсе — бессмысленного круга нет",
            )
            assertTrue(component.state.value.premiumGift is HomePremiumGiftState.InFlight)

            // Возврат на экран без связи тоже не жжёт попытку: отказ уехал бы TRANSPORT'ом вместо
            // честного «нет сети» (review patch: doOnResume re-arm гейтится наблюдённой сетью).
            lifecycle.stop()
            lifecycle.resume()
            testScheduler.advanceUntilIdle()
            assertEquals(
                emptyList(),
                claimRepo.resumeAttempts,
                "офлайн-возврат на экран не создаёт попытку",
            )

            online.value = true
            testScheduler.advanceUntilIdle()

            assertEquals(
                listOf(GiftClaimKind.PREMIUM_DAILY),
                claimRepo.resumes,
                "возвращение сети доигрывает лежащее намерение",
            )
            assertTrue(component.state.value.premiumGift !is HomePremiumGiftState.InFlight, "Claiming конечен")
            assertEquals(
                0,
                claimRepo.submits.count { it == GiftClaimKind.PREMIUM_DAILY },
                "повтор идёт лежащим намерением — новый operationId не минтуется",
            )
        }

    @Test
    fun `a failed premium resume is re-armed by returning to the screen`() =
        runTest {
            // Сеть есть, но ответ не пришёл (таймаут): pending остаётся, новых эмиссий нет —
            // доиграть обязан возврат на экран (doOnResume), не только смена сети.
            val claimRepo = FakeGiftClaimRepository()
            claimRepo.resumeFailures = 1
            claimRepo.pendingKinds.value = setOf(GiftClaimKind.PREMIUM_DAILY)
            val component =
                buildComponent(
                    profileRepo = FakeProfileRepository(premiumProfile(untilMs = now.value + day, nextBonusAtMs = 0L)),
                    claimRepo = claimRepo,
                )
            testScheduler.advanceUntilIdle()

            assertEquals(1, claimRepo.resumeAttempts.size, "первая попытка упала транспортом")
            assertTrue(component.state.value.premiumGift is HomePremiumGiftState.InFlight)

            lifecycle.stop()
            lifecycle.resume()
            testScheduler.advanceUntilIdle()

            assertEquals(
                listOf(GiftClaimKind.PREMIUM_DAILY),
                claimRepo.resumes,
                "возврат на экран доигрывает намерение после неудачи",
            )
            assertTrue(component.state.value.premiumGift !is HomePremiumGiftState.InFlight)
        }

    @Test
    fun `tap from available state sends exactly one premium claim`() =
        runTest {
            val claimRepo = FakeGiftClaimRepository()
            val component =
                buildComponent(
                    profileRepo = FakeProfileRepository(premiumProfile(untilMs = now.value + day, nextBonusAtMs = 0L)),
                    claimRepo = claimRepo,
                )
            testScheduler.advanceUntilIdle()

            component.onPremiumGiftClaim()
            testScheduler.advanceUntilIdle()
            val premium = { claimRepo.submits.count { it == GiftClaimKind.PREMIUM_DAILY } }
            assertEquals(1, premium(), "входная синхронизации серии здесь не в счёт — только явная заявка")

            // После CLAIMED профиль перечитан репозиторием; здесь слово ещё прежнее, поэтому
            // повторный тап из доступного состояния уезжает тем же намерением — второй операции
            // не создаёт ни клиент (одно активное намерение), ни сервер (забор по operationId).
            component.onPremiumGiftClaim()
            testScheduler.advanceUntilIdle()
            assertEquals(2, premium())
        }

    @Test
    fun `hidden button never claims even on a stale tap`() =
        runTest {
            val claimRepo = FakeGiftClaimRepository()
            val component =
                buildComponent(
                    profileRepo = FakeProfileRepository(premiumProfile(untilMs = now.value - 1, nextBonusAtMs = 0L)),
                    claimRepo = claimRepo,
                )
            testScheduler.advanceUntilIdle()

            component.onPremiumGiftClaim()
            testScheduler.advanceUntilIdle()

            assertEquals(
                0,
                claimRepo.submits.count { it == GiftClaimKind.PREMIUM_DAILY },
                "нет премиума — нет и заявки, даже если входная синхронизация серии отработала",
            )
        }

    @Test
    fun `returning to the screen re-runs the entry streak sync`() =
        runTest {
            val claimRepo = FakeGiftClaimRepository()
            val component = buildComponent(claimRepo = claimRepo)
            testScheduler.advanceUntilIdle()
            val afterFirstEntry = claimRepo.submits.size

            lifecycle.stop()
            lifecycle.resume()
            testScheduler.advanceUntilIdle()

            assertTrue(
                claimRepo.submits.size > afterFirstEntry,
                "каждый вход на главный экран начинается с синхронизации серии",
            )
        }

    @Test
    fun `component destruction never acknowledges an undisplayed or shown result`() =
        runTest {
            val repo =
                FakeGiftBoxRepository(
                    Result.success(
                        GiftBoxOpening(GiftBoxReward.Nolics(500), true, 0, "opening-1", "A"),
                    ),
                )
            repo.pending.value = PendingGiftBoxOpening("opening-1", "A", true)
            buildComponent(giftBoxRepo = repo, authRepo = FakeAuthRepository("A"))
            lifecycle.stop()
            lifecycle.destroy()
            assertEquals(emptyList(), repo.acknowledged)
            assertEquals("opening-1", repo.pending.value?.openingId)
        }
}

private class FakeProfileRepository(
    initialProfile: UserProfile = UserProfile.offline(),
) : ProfileRepository {
    val state = MutableStateFlow(initialProfile)

    override fun observeCurrentProfile(): Flow<UserProfile> = state

    override suspend fun currentProfile(): UserProfile = state.value

    override suspend fun ensureCurrentProfile(): Result<UserProfile> = Result.success(state.value)

    override suspend fun updateNickname(nickname: String): Result<UserProfile> =
        Result.success(state.value.copy(nickname = nickname))
}

private class FakeGiftBoxRepository(
    private val result: Result<GiftBoxOpening> =
        Result.failure(IllegalStateException("No gift boxes available")),
) : GiftBoxRepository {
    val pending = MutableStateFlow<PendingGiftBoxOpening?>(null)
    val acknowledged = mutableListOf<String>()
    var opens = 0

    override suspend fun openGiftBox(): Result<GiftBoxOpening> {
        opens++
        return result
    }

    override fun observePendingOpening(): Flow<PendingGiftBoxOpening?> = pending

    override suspend fun resumePendingOpening(): Result<GiftBoxOpening>? = if (pending.value == null) null else result

    override suspend fun acknowledgeOpening(openingId: String): Result<Boolean> {
        val saved = pending.value ?: return Result.success(false)
        if (!saved.receiptAvailable || saved.openingId != openingId) return Result.success(false)
        acknowledged += openingId
        pending.value = null
        return Result.success(true)
    }
}
