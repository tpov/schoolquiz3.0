package com.tpov.schoolquiz.android.feature.app_shell.presentation.component

import android.util.Log
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab.DefaultEventsTabComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab.DefaultInternetTabComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab.DefaultLocalTabComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab.DefaultShopTabComponent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.EventsConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.InternetConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RetapOutcome
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.ShopConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Decompose implementation of [RootComponent].
 *
 * Wires all navigation use cases + UserStats observation.
 * Created via Koin factory(ComponentContext) per ADR-COMP-07.
 *
 * Coroutine scope is tied to component lifecycle via [lifecycle.doOnDestroy] — cancelled on
 * every Activity destroy (including rotation). Each new component starts with a clean scope,
 * preventing orphaned coroutines from accumulating across configuration changes.
 */
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val initUseCase: InitializeAppShellUseCase,
    private val navigateUseCase: NavigateUseCase,
    private val observeUseCase: ObserveAppShellStateUseCase,
    private val retapUseCase: OnTabRetapUseCase,
) : RootComponent, ComponentContext by componentContext {
    private val _appShellState = MutableStateFlow(AppShellState.fallback(UserStats.guest()))
    override val appShellState: Flow<AppShellState> = _appShellState.asStateFlow()

    @Volatile private var initDone = false

    /**
     * `pendingStats` applies only if non-guest. Trade-off: legitimate guest pre-init emission
     * (uid=null scenario) is absorbed by init state — this is acceptable because init also
     * produces guest stats from the same datasource. When auth changes post-init, observer
     * emits the new non-guest stats via normal (non-buffered) path.
     *
     * Alternative: accept all pre-init emissions → breaks cold start race test where cached
     * stale guest would override fresher init snapshot. See cross-phase review round 3.
     */
    @Volatile private var pendingStats: UserStats? = null

    /**
     * Single-consumer event channel. Only one collector should be active at a time.
     * The channel is BUFFERED (capacity=64) — events are never dropped under normal navigation load.
     */
    private val _events = Channel<RootEvent>(Channel.BUFFERED)
    override val events: Flow<RootEvent> = _events.receiveAsFlow()

    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + Dispatchers.Main.immediate)

    private val localNavigation = StackNavigation<LocalConfig>()
    private val internetNavigation = StackNavigation<InternetConfig>()
    private val eventsNavigation = StackNavigation<EventsConfig>()
    private val shopNavigation = StackNavigation<ShopConfig>()

    internal val localTabComponent = DefaultLocalTabComponent(childContext("LocalTab"), localNavigation)
    internal val internetTabComponent = DefaultInternetTabComponent(childContext("InternetTab"), internetNavigation)
    internal val eventsTabComponent = DefaultEventsTabComponent(childContext("EventsTab"), eventsNavigation)
    internal val shopTabComponent = DefaultShopTabComponent(childContext("ShopTab"), shopNavigation)

    /**
     * Navigator for programmatic destination changes.
     * Lifetime: tied to this component instance (Activity-scoped). Do not retain beyond Activity.
     */
    internal val navigator: Navigator = NavigatorImpl(this)

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }

        // Essenty BackCallback — not Jetpack BackHandler.
        // isEnabled = true always: Back at root no-ops via domain FSM (state machine absorbs it).
        // defaultComponentContext() auto-connects to Activity.onBackPressedDispatcher.
        backHandler.register(
            BackCallback(isEnabled = true) {
                onDestination(Destination.Back)
            },
        )

        // Cold start: merge initialState into current.
        // If user hasn't navigated (current == guest fallback): apply full initialState.
        // If user navigated before init completed: preserve their navigation, merge only userStats.
        // initDone gate ensures the observer cannot race-update state before this point.
        // initDone is set AFTER state update so observer never sees flag=true on stale state.
        scope.launch {
            val initialState =
                runCatching { initUseCase() }
                    .getOrElse { AppShellState.fallback(UserStats.guest()) }
            if (!initDone) {
                val before = _appShellState.value
                val merged =
                    if (before == AppShellState.fallback(UserStats.guest())) {
                        initialState
                    } else {
                        before.copy(userStats = initialState.userStats)
                    }
                // Apply pendingStats only if observer delivered a non-guest user before init:
                // initial observer emission == UserStats.guest() (cached value, same as init fallback).
                // A real-time update (logged-in user) arrives as non-guest and should win over init's cache.
                val finalStats = pendingStats?.takeIf { it != UserStats.guest() } ?: merged.userStats
                _appShellState.update { merged.copy(userStats = finalStats) }
                initDone = true
                val after = _appShellState.value
                syncStack(before.localState.stack, after.localState.stack, localNavigation)
                syncStack(before.internetState.stack, after.internetState.stack, internetNavigation)
                syncStack(before.eventsState.stack, after.eventsState.stack, eventsNavigation)
                syncStack(before.shopState.stack, after.shopState.stack, shopNavigation)
            }
        }

        // Stats observer: reconnects after normal Flow completion (logout) or error.
        // Observer only updates userStats after initDone — nav state is owned by init.
        scope.launch {
            while (isActive) {
                try {
                    observeUseCase { _appShellState.value }.collect { newState ->
                        if (!initDone) {
                            pendingStats = newState.userStats
                        } else {
                            _appShellState.update { it.copy(userStats = newState.userStats) }
                        }
                    }
                    delay(1_000)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    Log.w(TAG, "observeStats error (${t::class.simpleName}) — retrying in 5s")
                    delay(5_000)
                }
            }
        }
    }

    override fun onDestination(destination: Destination) {
        val current = _appShellState.value
        val result = navigateUseCase(current, destination)
        applyResult(current, result)
        if (result.events.isNotEmpty()) {
            scope.launch { result.events.forEach { _events.send(it) } }
        }
    }

    override fun onActiveTabRetap(tab: Tab): RetapOutcome {
        val old = _appShellState.value
        val (newState, outcome) = retapUseCase(old, tab)
        applyResult(old, TransitionResult(newState))
        return outcome
    }

    override fun onDeepLink(deepLink: DeepLink) {
        // MVP stub — no URL patterns registered yet.
        // Future: validate DeepLink.scheme and origin before processing (reject unknown schemes).
    }

    private fun applyResult(
        old: AppShellState,
        result: TransitionResult,
    ) {
        val new = result.newState
        _appShellState.update { new }
        syncStack(old.localState.stack, new.localState.stack, localNavigation)
        syncStack(old.internetState.stack, new.internetState.stack, internetNavigation)
        syncStack(old.eventsState.stack, new.eventsState.stack, eventsNavigation)
        syncStack(old.shopState.stack, new.shopState.stack, shopNavigation)
    }

    /**
     * Syncs a Decompose StackNavigation with the domain NavStack.
     *
     * NavStack.backStack[0] = oldest; active = top. Decompose: last entry = active.
     * Uses navigate() directly to avoid reified constraint of replaceAll(vararg C).
     */
    private fun <C : Any> syncStack(
        old: NavStack<C>,
        new: NavStack<C>,
        nav: StackNavigation<C>,
    ) {
        if (old == new) return
        val all = new.backStack + new.active
        nav.navigate(transformer = { all }, onComplete = { _, _ -> })
    }

    private companion object {
        private const val TAG = "DefaultRootComponent"
    }
}
