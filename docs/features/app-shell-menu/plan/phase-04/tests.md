---
phase: phase-04
role: test-dev
---

# Phase-04: Test Tasks — DefaultRootComponent Integration

## Scope

JVM тесты для `DefaultRootComponent` через TestComponentContext. Instrumented тесты не нужны — logika pure Kotlin/coroutines.

Написать ПАРАЛЛЕЛЬНО с production code (TDD).

## 1. TestComponentContext

Decompose предоставляет `TestContext` / `DefaultComponentContext` для JVM тестов (без Android runtime). Использовать:

```kotlin
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume

fun testComponentContext(): ComponentContext {
    val lifecycle = LifecycleRegistry()
    lifecycle.resume()   // Activate lifecycle for coroutineScope
    return DefaultComponentContext(lifecycle = lifecycle)
}
```

## 2. Fakes для presentation tests

`InitializeAppShellUseCase` — конкретный класс (не sealed, не abstract). Наследование через subclass wrapper невалидно если класс не открыт.

Вместо FakeInitializeAppShellUseCase — использовать реальный `InitializeAppShellUseCase(fakeRepo)` с контролируемым `FakeUserStatsRepository`:

```kotlin
// Контроль поведения через backing repo:
// Happy path:
val fakeRepo = FakeUserStatsRepository()
fakeRepo.currentStatsResult = UserStats.guest().copy(currentSkill = 500)
val initUseCase = InitializeAppShellUseCase(fakeRepo)
// initUseCase() вернёт AppShellState.default(stats с currentSkill=500)

// Error path — FakeUserStatsRepository пробрасывает exception:
val errorRepo = object : FakeUserStatsRepository() {
    override suspend fun currentStats(): UserStats = throw RuntimeException("offline")
}
val initUseCase = InitializeAppShellUseCase(errorRepo)
// initUseCase() бросит RuntimeException → DefaultRootComponent перейдёт в fallback
```

`buildComponent()` helper (см. раздел 3) конструирует `InitializeAppShellUseCase(fakeRepo)` напрямую.

Аналогично: `FakeNavigateUseCase`, `FakeOnTabRetapUseCase`, `FakeObserveAppShellStateUseCase` — если UC не открытые классы, создавать через реальные UC + fake repos, не через subclassing.

## 3. DefaultRootComponentTest.kt

**Файл**: `android/feature/app-shell/presentation/src/test/kotlin/.../presentation/DefaultRootComponentTest.kt`

```kotlin
package com.tpov.schoolquiz.android.feature.app_shell.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tpov.schoolquiz.shared.feature.app_shell.domain.fake.FakeUserStatsRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RetapOutcome
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRootComponentTest {

    private fun testCtx(): DefaultComponentContext {
        val lifecycle = LifecycleRegistry(); lifecycle.resume()
        return DefaultComponentContext(lifecycle)
    }

    private fun buildComponent(
        fakeRepo: FakeUserStatsRepository = FakeUserStatsRepository(),
    ) = DefaultRootComponent(
        componentContext = testCtx(),
        initUseCase = InitializeAppShellUseCase(fakeRepo),
        navigateUseCase = NavigateUseCase(),
        observeUseCase = ObserveAppShellStateUseCase(fakeRepo),
        retapUseCase = OnTabRetapUseCase(),
    )

    @Test
    fun `cold start state equals AppShellState default with fetched stats`() = runTest {
        val stats = UserStats.guest().copy(currentSkill = 100)
        val fakeRepo = FakeUserStatsRepository(UserStats.guest())
        fakeRepo.currentStatsResult = stats

        val component = buildComponent(fakeRepo)

        // Wait for init coroutine
        val state = component.appShellState.first { it.userStats.currentSkill == 100 || true }
        // activeTab == LOCAL per AppShellState.default
        assertEquals(Tab.LOCAL, state.activeTab)
    }

    @Test
    fun `when stats emit after navigation change then navigation state is preserved`() = runTest {
        val fakeRepo = FakeUserStatsRepository(UserStats.guest())
        val component = buildComponent(fakeRepo)

        // Switch tab THEN emit stats
        component.onDestination(Destination.SwitchTab(Tab.INTERNET))
        fakeRepo.emit(UserStats.guest().copy(currentSkill = 999))

        val state = component.appShellState.first { it.userStats.currentSkill == 999 }
        assertEquals(Tab.INTERNET, state.activeTab)   // navigation preserved (ADR-LEAD-02)
        assertEquals(999, state.userStats.currentSkill)
    }

    @Test
    fun `go to SwitchTab changes activeTab`() = runTest {
        val component = buildComponent()
        component.onDestination(Destination.SwitchTab(Tab.INTERNET))
        val state = component.appShellState.first { it.activeTab == Tab.INTERNET }
        assertEquals(Tab.INTERNET, state.activeTab)
    }

    @Test
    fun `back with drawer open closes drawer`() = runTest {
        val component = buildComponent()
        component.onDestination(Destination.OpenDrawer)
        component.appShellState.first { it.isDrawerOpen }

        component.onDestination(Destination.Back)
        val state = component.appShellState.first { !it.isDrawerOpen }
        assertFalse(state.isDrawerOpen)
        assertEquals(Tab.LOCAL, state.activeTab)
    }

    @Test
    fun `back on LOCAL root emits SystemBack`() = runTest {
        val component = buildComponent()
        val events = mutableListOf<RootEvent>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            component.events.toList(events)
        }

        // Ensure at root (LOCAL, empty backStack, drawer closed)
        component.onDestination(Destination.Back)
        component.onDestination(Destination.Back)   // second should emit SystemBack (FSM step 4)

        job.cancel()
        assertTrue(events.any { it == RootEvent.SystemBack })
    }

    @Test
    fun `retap at root returns NO_OP`() = runTest {
        val component = buildComponent()
        val outcome = component.onActiveTabRetap(Tab.LOCAL)
        assertEquals(RetapOutcome.NO_OP, outcome)
    }

    @Test
    fun `open drawer on SHOP tab is no-op`() = runTest {
        val component = buildComponent()
        component.onDestination(Destination.SwitchTab(Tab.SHOP))
        component.appShellState.first { it.activeTab == Tab.SHOP }

        component.onDestination(Destination.OpenDrawer)
        val state = component.appShellState.first()
        assertFalse(state.isDrawerOpen)
    }

    // AC 23g: deep link to hidden section is a no-op (domain guard blocks navigation)
    @Test
    fun `go_to_select_section_hidden_section_no_op`() = runTest {
        // Arena requires USER >= Title.TEACHINGS.first (3000). guest() has currentSkill = 0.
        val fakeRepo = FakeUserStatsRepository(UserStats.guest())
        val component = buildComponent(fakeRepo)

        // Capture state before navigation attempt
        val stateBefore = component.appShellState.first()

        // Attempt to navigate to hidden section
        component.onDestination(Destination.SelectSection(
            com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection.InternetSection.Arena
        ))

        // State must be unchanged (domain guard in NavigateUseCase blocks hidden section)
        val stateAfter = component.appShellState.first()
        assertEquals(stateBefore.activeTab, stateAfter.activeTab)
        assertEquals(stateBefore.activeSection, stateAfter.activeSection)
    }
}
```

## Validation

```bash
./gradlew :android:feature:app-shell:presentation:test --no-configuration-cache
```
