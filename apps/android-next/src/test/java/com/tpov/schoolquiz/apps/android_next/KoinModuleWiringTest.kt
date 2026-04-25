package com.tpov.schoolquiz.apps.android_next

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.di.appShellPresentationModule
import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import com.tpov.schoolquiz.shared.core.stats.RawUserStats
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import com.tpov.schoolquiz.shared.feature.app_shell.data.AuthRepositoryImpl
import com.tpov.schoolquiz.shared.feature.app_shell.data.UserStatsRepositoryImpl
import com.tpov.schoolquiz.shared.feature.app_shell.data.di.appShellDataModule
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import androidx.work.WorkManager
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import org.mockito.Mockito.mock
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.test.KoinTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Smoke tests: Koin module graph resolves without errors.
 *
 * D2 coverage: firebaseModule replaced by testDataSourceModule + appShellDataModule.
 * H5 fix (phase-01): appShellPresentationModule created in phase-04.
 * Phase-07: full-stack wiring (all 3 modules) verified here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KoinModuleWiringTest : KoinTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    private val testDataSourceModule = module {
        single<UserStatsDataSource> {
            object : UserStatsDataSource {
                override fun observeRaw() = emptyFlow<RawUserStats>()
                override suspend fun fetchRaw() = RawUserStats()
            }
        }
    }

    private val testDaoModule = module {
        single<UserStatsDao> {
            object : UserStatsDao {
                override fun observeByUid(uid: String) = emptyFlow<UserStatsEntity?>()
                override suspend fun findByUid(uid: String): UserStatsEntity? = null
                override suspend fun upsert(entity: UserStatsEntity) {}
                override suspend fun updateDeveloperLevel(uid: String, value: Int) {}
            }
        }
    }

    private val testWorkManagerModule = module {
        single<WorkManager> { mock(WorkManager::class.java) }
    }

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        stopKoin()
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------
    // Phase-01 D2: appShellDataModule resolves UserStatsRepository
    // -----------------------------------------------------------------------

    @Test
    fun `D2 appShellDataModule resolves UserStatsRepository given UserStatsDataSource`() {
        startKoin {
            modules(testDataSourceModule, testDaoModule, appShellDataModule())
        }

        val repo = getKoin().get<UserStatsRepository>()
        assertNotNull(repo)
    }

    @Test
    fun `D2 UserStatsRepository resolves as UserStatsRepositoryImpl`() {
        startKoin {
            modules(testDataSourceModule, testDaoModule, appShellDataModule())
        }

        val repo = getKoin().get<UserStatsRepository>()
        assertNotNull(repo)
        assertTrue(repo is UserStatsRepositoryImpl)
    }

    // -----------------------------------------------------------------------
    // Phase-01 (home-and-my-quests Decision #42 + Codex Round 3 B3): AuthRepository
    // -----------------------------------------------------------------------

    @Test
    fun `appShellDataModule resolves AuthRepository given currentUidFlow`() {
        startKoin {
            modules(testDataSourceModule, testDaoModule, appShellDataModule())
        }

        val repo = getKoin().get<AuthRepository>()
        assertNotNull(repo)
        assertTrue(repo is AuthRepositoryImpl)
    }

    @Test
    fun `appShellDataModule binds AuthRepository as AuthRepositoryImpl with shared currentUidFlow`() = runTest {
        val uidSource = MutableStateFlow<String?>("user-A")
        startKoin {
            modules(testDataSourceModule, testDaoModule, appShellDataModule { uidSource.asStateFlow() })
        }

        val auth = getKoin().get<AuthRepository>()
        // currentUid() returns the real Firebase UID without LOCAL_UID substitution
        assertEquals("user-A", auth.currentUid())

        // observeUid() reflects the same source as UserStatsRepositoryImpl.currentUidFlow
        uidSource.value = null
        assertNull(auth.currentUid())
    }

    // -----------------------------------------------------------------------
    // Phase-07 H5: full-stack wiring — all 3 modules
    // GIVEN testDataSourceModule + appShellDataModule + appShellPresentationModule
    // WHEN get<DefaultRootComponent> with parametersOf(ComponentContext)
    // THEN component resolves without MissingDefinitionException
    // -----------------------------------------------------------------------

    @Test
    fun `full stack wiring DefaultRootComponent resolvable with parametersOf`() {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val testCtx = DefaultComponentContext(lifecycle)

        startKoin {
            modules(testDataSourceModule, testDaoModule, testWorkManagerModule, appShellDataModule(), appShellPresentationModule)
        }

        try {
            val component = getKoin().get<DefaultRootComponent> { parametersOf(testCtx) }
            assertNotNull(component)
        } finally {
            lifecycle.stop()
            lifecycle.destroy()
        }
    }

    // -----------------------------------------------------------------------
    // Phase-07: system back event at LOCAL root
    // Spec: back_on_LOCAL_root_emits_system_back (Journey 4 step 3)
    // GIVEN DefaultRootComponent at LOCAL root (backStack empty, drawer closed)
    // WHEN onDestination(Back)
    // THEN events flow emits RootEvent.SystemBack
    // -----------------------------------------------------------------------

    @Test
    fun `system back event emitted at LOCAL root`() = runTest {
        val fakeStats = MutableStateFlow(UserStats.guest())
        val fakeRepo = object : UserStatsRepository {
            override fun observeStats(): Flow<UserStats> = fakeStats.asStateFlow()
            override suspend fun currentStats(): UserStats = fakeStats.value
            override suspend fun setLocalDeveloperLevel(value: Int) = Unit
            override suspend fun refreshProfile(): Result<Unit> = Result.success(Unit)
        }
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val testCtx = DefaultComponentContext(lifecycle)

        val component = DefaultRootComponent(
            componentContext = testCtx,
            initUseCase = InitializeAppShellUseCase(fakeRepo),
            navigateUseCase = NavigateUseCase(),
            observeUseCase = ObserveAppShellStateUseCase(fakeRepo),
            retapUseCase = OnTabRetapUseCase(),
            userStatsRepository = fakeRepo,
            workManager = mock(WorkManager::class.java),
        )

        val events = mutableListOf<RootEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            component.events.toList(events)
        }

        component.onDestination(Destination.Back)

        job.cancel()
        lifecycle.stop()
        lifecycle.destroy()
        assertTrue(events.any { it == RootEvent.SystemBack }, "Expected SystemBack in $events")
    }

    // -----------------------------------------------------------------------
    // Phase-07: deep link stub — no exception
    // Spec: deep_link_stub_no_crash (overview.md Tests Required)
    // GIVEN DefaultRootComponent (MVP stub — no URL patterns registered)
    // WHEN onDeepLink(DeepLink("schoolquiz://test"))
    // THEN no exception thrown
    // -----------------------------------------------------------------------

    @Test
    fun `deep link stub no crash`() {
        val fakeStats = MutableStateFlow(UserStats.guest())
        val fakeRepo = object : UserStatsRepository {
            override fun observeStats(): Flow<UserStats> = fakeStats.asStateFlow()
            override suspend fun currentStats(): UserStats = fakeStats.value
            override suspend fun setLocalDeveloperLevel(value: Int) = Unit
            override suspend fun refreshProfile(): Result<Unit> = Result.success(Unit)
        }
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val testCtx = DefaultComponentContext(lifecycle)

        val component = DefaultRootComponent(
            componentContext = testCtx,
            initUseCase = InitializeAppShellUseCase(fakeRepo),
            navigateUseCase = NavigateUseCase(),
            observeUseCase = ObserveAppShellStateUseCase(fakeRepo),
            retapUseCase = OnTabRetapUseCase(),
            userStatsRepository = fakeRepo,
            workManager = mock(WorkManager::class.java),
        )

        component.onDeepLink(DeepLink("schoolquiz://test"))

        lifecycle.stop()
        lifecycle.destroy()
    }
}
