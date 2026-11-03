---
phase: phase-04
role: frontend-dev
---

# Phase-04: Frontend Tasks — Decompose Integration Layer

## 1. android/feature/app-shell/presentation/build.gradle.kts

```kotlin
plugins {
    id("schoolquiz.android.compose.library")
}

android {
    namespace = "com.tpov.schoolquiz.android.feature.app_shell.presentation"
}

dependencies {
    implementation(project(":shared:feature:app-shell:domain"))
    implementation(project(":android:core:navigation"))
    implementation(project(":android:core:designsystem"))

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.ui)
    implementation(libs.bundles.decompose)
    implementation(libs.bundles.koin.android)
    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.bundles.androidx.lifecycle.compose)
    implementation(libs.bundles.androidx.ui.base)

    testImplementation(libs.junit4)
    testImplementation(libs.bundles.testing.unit)
}
```

## 2. LocalTabComponent.kt

```kotlin
package com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.TabConfig.LocalConfig
import com.tpov.schoolquiz.android.feature.app_shell.presentation.screen.LocalScreenComponent

interface LocalTabComponent {
    val childStack: Value<ChildStack<LocalConfig, LocalScreenComponent>>
}

class DefaultLocalTabComponent(
    componentContext: ComponentContext,
    navigation: StackNavigation<LocalConfig>,
) : LocalTabComponent, ComponentContext by componentContext {

    override val childStack: Value<ChildStack<LocalConfig, LocalScreenComponent>> =
        childStack(
            source = navigation,
            serializer = null,                                   // ADR-LEAD-01: state-saving deferred
            initialConfiguration = LocalConfig.MyQuestsRoot,
            handleBackButton = false,                            // back managed by DefaultRootComponent
            key = "LocalStack",
            childFactory = { config, _ -> LocalScreenComponent.Placeholder(config) },
        )
}
```

Аналогично создать:
- `InternetTabComponent.kt` (key = "InternetStack", initial = InternetConfig.QualificationsRoot)
- `EventsTabComponent.kt` (key = "EventsStack", initial = EventsConfig.EmptyRoot)
- `ShopTabComponent.kt` (key = "ShopStack", initial = ShopConfig.ShopRoot)

## 3. Screen Components (4 файла)

```kotlin
// LocalScreenComponent.kt
package com.tpov.schoolquiz.android.feature.app_shell.presentation.screen

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.TabConfig.LocalConfig

sealed interface LocalScreenComponent {
    data class Placeholder(val config: LocalConfig) : LocalScreenComponent
}
```

Аналогично: `InternetScreenComponent`, `EventsScreenComponent`, `ShopScreenComponent`.

## 4. NavigatorImpl.kt

```kotlin
package com.tpov.schoolquiz.android.feature.app_shell.presentation.component

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.RootComponent

/**
 * Delegates Navigator.goTo() → RootComponent.onDestination().
 * Created inside DefaultRootComponent.init{} — not a separate Koin binding.
 */
class NavigatorImpl(
    private val rootComponent: RootComponent,
) : Navigator {
    override fun goTo(destination: Destination) {
        rootComponent.onDestination(destination)
    }
}
```

## 5. DefaultRootComponent.kt

```kotlin
package com.tpov.schoolquiz.android.feature.app_shell.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab.DefaultEventsTabComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab.DefaultInternetTabComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab.DefaultLocalTabComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab.DefaultShopTabComponent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RetapOutcome
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.TabConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.RootComponent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.NavStack
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.TransitionResult
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

/**
 * Decompose implementation of [RootComponent].
 *
 * Wires all navigation use cases + UserStats observation.
 * Created via Koin factory(ComponentContext) per ADR-COMP-07.
 */
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val initUseCase: InitializeAppShellUseCase,
    private val navigateUseCase: NavigateUseCase,
    private val observeUseCase: ObserveAppShellStateUseCase,
    private val retapUseCase: OnTabRetapUseCase,
) : RootComponent, ComponentContext by componentContext {

    private val _state = MutableStateFlow(AppShellState.fallback(UserStats.guest()))
    override val appShellState: Flow<AppShellState> = _state.asStateFlow()

    private val _events = Channel<RootEvent>(Channel.BUFFERED)
    override val events: Flow<RootEvent> = _events.receiveAsFlow()

    private val scope = coroutineScope(Dispatchers.Main.immediate)

    // Per-tab StackNavigation instances (lifecycle-independent; reset only on component destroy)
    private val localNavigation = StackNavigation<TabConfig.LocalConfig>()
    private val internetNavigation = StackNavigation<TabConfig.InternetConfig>()
    private val eventsNavigation = StackNavigation<TabConfig.EventsConfig>()
    private val shopNavigation = StackNavigation<TabConfig.ShopConfig>()

    val localTabComponent = DefaultLocalTabComponent(childContext("LocalTab"), localNavigation)
    val internetTabComponent = DefaultInternetTabComponent(childContext("InternetTab"), internetNavigation)
    val eventsTabComponent = DefaultEventsTabComponent(childContext("EventsTab"), eventsNavigation)
    val shopTabComponent = DefaultShopTabComponent(childContext("ShopTab"), shopNavigation)

    override val navigator: Navigator = NavigatorImpl(this)

    init {
        // Back handler: Essenty BackHandler (NOT Jetpack BackHandler).
        // defaultComponentContext() auto-connects to Activity.onBackPressedDispatcher.
        backHandler.register(BackCallback(isEnabled = true) {
            onDestination(Destination.Back)
        })

        // 1. Cold start: initialize default state from UserStatsRepository
        // InitializeAppShellUseCase.invoke() returns AppShellState (not TransitionResult).
        // Wrap into TransitionResult to call applyResult() which syncs Decompose stacks.
        scope.launch {
            try {
                val initialState: AppShellState = initUseCase()
                applyResult(AppShellState.fallback(UserStats.guest()), TransitionResult(initialState))
            } catch (e: Exception) {
                _state.update { AppShellState.fallback(UserStats.guest()) }
            }
        }

        // 2. Stats observer — ADR-LEAD-02: provider lambda avoids stale closure
        scope.launch {
            observeUseCase(currentStateProvider = { _state.value })
                .catch { emit(AppShellState.fallback(UserStats.guest())) }
                .collect { newState -> _state.update { newState } }
        }
    }

    override fun onDestination(destination: Destination) {
        val current = _state.value
        val result = navigateUseCase(current, destination)
        applyResult(current, result)
        result.events.forEach { event ->
            scope.launch { _events.send(event) }
        }
    }

    override fun onActiveTabRetap(tab: Tab): RetapOutcome {
        val (newState, outcome) = retapUseCase(_state.value, tab)
        _state.update { newState }
        return outcome
    }

    override fun onDeepLink(deepLink: DeepLink) {
        // MVP: stub — no URL patterns registered
    }

    // Sync Decompose StackNavigation with domain NavStack (called after every state update)
    private fun applyResult(old: AppShellState, result: TransitionResult) {
        val new = result.newState
        _state.update { new }
        syncStack(old.localState.navStack, new.localState.navStack, localNavigation)
        syncStack(old.internetState.navStack, new.internetState.navStack, internetNavigation)
        syncStack(old.eventsState.navStack, new.eventsState.navStack, eventsNavigation)
        syncStack(old.shopState.navStack, new.shopState.navStack, shopNavigation)
    }

    /**
     * Syncs a Decompose StackNavigation with the domain NavStack.
     * Calls replaceAll(vararg C) — Decompose 3.1.0 API.
     *
     * OQ-COMP-1: DEFERRED — verify replaceAll(vararg C) signature at implementation time.
     * Fallback if vararg form unavailable: replaceAll(list) overload.
     */
    private fun <C : Any> syncStack(
        old: NavStack<C>,
        new: NavStack<C>,
        nav: StackNavigation<C>,
    ) {
        if (old == new) return
        // NavStack.backStack[0] = oldest item; active = top
        // Decompose replaceAll: last entry = active
        val all = new.backStack + new.active
        nav.replaceAll(*all.toTypedArray())   // REQUIRES: verify replaceAll(vararg C) — OQ-COMP-1
    }
}
```

## 6. AppShellPresentationModule.kt

```kotlin
package com.tpov.schoolquiz.android.feature.app_shell.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.NavigatorImpl
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.RootComponent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.HandleBackUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/**
 * Koin module for presentation layer.
 * ADR-0009 Rule 1: one module val.
 * ADR-COMP-07: DefaultRootComponent as factory (Activity-scoped ComponentContext).
 */
val appShellPresentationModule = module {
    factory { (ctx: ComponentContext) ->
        DefaultRootComponent(
            componentContext = ctx,
            initUseCase = get(),
            navigateUseCase = get(),
            observeUseCase = get(),
            retapUseCase = get(),
            // handleBackUseCase: NOT injected — back via navigateUseCase per ADR-COMP-07
        )
    }

    // Navigator: created inside DefaultRootComponent.init{} — exposed via rootComponent.navigator
    // NOT a separate Koin binding to avoid MissingPropertyException when get<RootComponent>()
    // is called without parametersOf(ctx). See 06-api-contract.md:41.

    factory { InitializeAppShellUseCase(get()) }
    factory { NavigateUseCase() }
    factory { OnTabRetapUseCase() }
    factory { ObserveAppShellStateUseCase(get()) }
    factory { HandleBackUseCase() }   // domain tests only; NOT wired into DefaultRootComponent
}
```

## 7. Update AppApplication.kt

Добавить `appShellPresentationModule` в startKoin:
```kotlin
startKoin {
    androidContext(this@AppApplication)
    modules(
        firebaseModule,
        appShellDataModule,
        appShellPresentationModule,   // ADD: phase-04
    )
}
```

### Pattern Invariants

1. **serializer = null ОБЯЗАТЕЛЕН** во всех `childStack(...)` вызовах — ADR-LEAD-01. Нельзя добавить `serializer = serializer<C>()` без одновременного добавления `@Serializable` на Config (которое deferred).

2. **Essenty BackCallback, не Jetpack BackHandler** — `backHandler.register(BackCallback(...))` через Essenty. Jetpack `BackHandler {}` Composable НЕ используется в `DefaultRootComponent` — нарушает иерархию.

3. **coroutineScope(Dispatchers.Main.immediate)** через Essenty lifecycle binding. НЕ `CoroutineScope(Dispatchers.Main)` без lifecycle — утечка при destroy.

4. **_state.update** thread-safety: `MutableStateFlow.update` атомарен. `onDestination` и stats observer могут вызываться с разных coroutines — update безопасен.

5. **applyResult ПОСЛЕ _state.update**: `syncStack` вызывается после `_state.update { new }`. Порядок важен — Decompose navigation синхронна, domain state должен быть актуальным.

6. **NavigatorImpl создаётся в init{}, не Koin**: Koin `single<Navigator> { NavigatorImpl(get()) }` создаёт проблему — `get<RootComponent>()` без `parametersOf(ctx)` бросает `MissingPropertyException`. Navigator доступен через `rootComponent.navigator` — этот паттерн зафиксирован в `06-api-contract.md:41`.
