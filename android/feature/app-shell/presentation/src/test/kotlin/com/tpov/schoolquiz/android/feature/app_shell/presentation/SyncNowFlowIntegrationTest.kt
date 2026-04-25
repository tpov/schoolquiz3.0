package com.tpov.schoolquiz.android.feature.app_shell.presentation

import androidx.work.ExistingWorkPolicy
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.fake.FakeUserStatsRepository
import com.tpov.schoolquiz.android.feature.app_shell.presentation.fake.FakeWorkManager
import com.tpov.schoolquiz.android.feature.app_shell.presentation.fake.StubHomeQuestsComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.fake.StubMyQuestsComponent
import com.tpov.schoolquiz.platform.android_services.sync.SyncWorker
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for [DefaultRootComponent.onSyncNow] → WorkManager + SyncStarted event.
 *
 * Covers SN-phase7-01..03 from docs/features/menu-refactor/plan/phase-07/tests.md.
 * Spec: 07-events.md L3.2 — SyncNow flow.
 *
 * Uses [FakeWorkManager] capturing fake — no Mockito verify() needed, just property assertions.
 *
 * Open Question (seam): [DefaultRootComponent.onSyncNow] creates [OneTimeWorkRequestBuilder]
 * internally. If WorkManager data classes fail in JVM tests, production code needs an injectable
 * WorkRequest factory seam. Report to lead if tests fail with Android runtime error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncNowFlowIntegrationTest {

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

    private fun buildComponent(
        fakeRepo: FakeUserStatsRepository = FakeUserStatsRepository(),
        fakeWorkManager: FakeWorkManager,
    ) = DefaultRootComponent(
        componentContext = testCtx(),
        initUseCase = InitializeAppShellUseCase(fakeRepo),
        navigateUseCase = NavigateUseCase(),
        observeUseCase = ObserveAppShellStateUseCase(fakeRepo),
        retapUseCase = OnTabRetapUseCase(),
        userStatsRepository = fakeRepo,
        workManager = fakeWorkManager.workManager,
        myQuestsFactory = { _, _ -> StubMyQuestsComponent },
        homeQuestsFactory = { _ -> StubHomeQuestsComponent },
    )

    // -----------------------------------------------------------------------
    // SN-phase7-01
    // GIVEN DefaultRootComponent with FakeWorkManager
    // WHEN onSyncNow()
    // THEN enqueueUniqueWork called once with WORK_NAME_MANUAL + ExistingWorkPolicy.REPLACE
    // -----------------------------------------------------------------------
    @Test
    fun `onSyncNow enqueues unique work with REPLACE policy`() = runTest(testDispatcher) {
        val fakeWorkManager = FakeWorkManager()
        val component = buildComponent(fakeWorkManager = fakeWorkManager)

        component.onSyncNow()

        assertEquals(1, fakeWorkManager.enqueueUniqueWorkCalls)
        assertEquals(SyncWorker.WORK_NAME_MANUAL, fakeWorkManager.lastWorkName)
        assertEquals(ExistingWorkPolicy.REPLACE, fakeWorkManager.lastPolicy)
    }

    // -----------------------------------------------------------------------
    // SN-phase7-02
    // GIVEN DefaultRootComponent with FakeWorkManager
    // WHEN onSyncNow()
    // THEN events flow contains RootEvent.SyncStarted
    // -----------------------------------------------------------------------
    @Test
    fun `onSyncNow emits SyncStarted event`() = runTest(testDispatcher) {
        val fakeWorkManager = FakeWorkManager()
        val component = buildComponent(fakeWorkManager = fakeWorkManager)
        val collectedEvents = mutableListOf<RootEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            component.events.toList(collectedEvents)
        }

        component.onSyncNow()

        job.cancel()
        assertTrue(collectedEvents.any { it == RootEvent.SyncStarted })
    }

    // -----------------------------------------------------------------------
    // SN-phase7-03
    // GIVEN DefaultRootComponent with FakeWorkManager
    // WHEN onSyncNow() called twice
    // THEN enqueueUniqueWorkCalls == 2 (REPLACE policy allows repeated enqueue)
    // -----------------------------------------------------------------------
    @Test
    fun `two consecutive onSyncNow calls both reach WorkManager`() = runTest(testDispatcher) {
        val fakeWorkManager = FakeWorkManager()
        val component = buildComponent(fakeWorkManager = fakeWorkManager)

        component.onSyncNow()
        component.onSyncNow()

        assertEquals(2, fakeWorkManager.enqueueUniqueWorkCalls)
    }
}
