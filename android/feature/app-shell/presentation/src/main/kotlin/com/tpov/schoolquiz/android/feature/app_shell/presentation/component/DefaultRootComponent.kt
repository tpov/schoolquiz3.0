package com.tpov.schoolquiz.android.feature.app_shell.presentation.component

import android.util.Log
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.backhandler.BackHandler
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab.DefaultEventsTabComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab.DefaultInternetTabComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab.DefaultLocalTabComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab.DefaultShopTabComponent
import com.tpov.schoolquiz.android.feature.economy.presentation.component.PlaceholderShopComponent
import com.tpov.schoolquiz.android.feature.economy.presentation.component.ShopComponent
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.component.PlaceholderProfileComponent
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.component.ProfileComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.PlaceholderQuestCreateComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.PlaceholderReviewQueueComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.QuestCreateComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.ReviewQueueComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.foundation.QualificationLevel
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
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
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.NavStack
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.TransitionResult
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.model.TournamentOverview
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.repository.TournamentLeaderboardRepository
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.use_case.FetchTournamentOverviewUseCase
import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.model.TapProgress
import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.model.TapResult
import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.use_case.ActivateDevModeUseCase
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
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

private val PUBLIC_SHELF_MANAGER_DEVELOPER_LEVEL = QualificationLevel.LEVEL_1.points + 1

sealed interface TournamentOverviewLoadState {
    val overview: TournamentOverview?

    data object Idle : TournamentOverviewLoadState {
        override val overview: TournamentOverview? = null
    }

    data class Loading(
        override val overview: TournamentOverview?,
    ) : TournamentOverviewLoadState

    data class Content(
        override val overview: TournamentOverview,
    ) : TournamentOverviewLoadState

    data class Error(
        val message: String?,
        override val overview: TournamentOverview?,
    ) : TournamentOverviewLoadState
}

private val noOpFetchTournamentOverviewUseCase =
    FetchTournamentOverviewUseCase(
        object : TournamentLeaderboardRepository {
            override suspend fun fetchOverview(
                tournamentId: String,
                limit: Int,
            ): Result<TournamentOverview> =
                Result.failure(IllegalStateException("Tournament leaderboard repository is not bound"))
        },
    )

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
@Suppress("LongParameterList", "TooGenericExceptionCaught")
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val initUseCase: InitializeAppShellUseCase,
    private val navigateUseCase: NavigateUseCase,
    private val observeUseCase: ObserveAppShellStateUseCase,
    private val retapUseCase: OnTabRetapUseCase,
    private val fetchTournamentOverview: FetchTournamentOverviewUseCase = noOpFetchTournamentOverviewUseCase,
    private val userStatsRepository: UserStatsRepository,
    private val syncScheduler: SyncScheduler,
    myQuestsFactory: (ComponentContext, Navigator, (QuestDisplayItem) -> Unit) -> MyQuestsComponent,
    homeQuestsFactory: (ComponentContext, (CatalogId, String) -> Unit) -> HomeQuestsComponent,
    questCreateFactory: (ComponentContext, Navigator) -> QuestCreateComponent = { _, nav ->
        PlaceholderQuestCreateComponent(nav)
    },
    reviewQueueFactory: (ComponentContext) -> ReviewQueueComponent = { PlaceholderReviewQueueComponent() },
    profileFactory: (ComponentContext) -> ProfileComponent = { PlaceholderProfileComponent() },
    shopFactory: (ComponentContext) -> ShopComponent = { PlaceholderShopComponent() },
    quizzesFactory: (ComponentContext) -> QuizzesComponent,
    // Injectable so tests stay on the test scheduler. A hardcoded Dispatchers.IO escapes
    // runTest's virtual clock: the scheduler goes idle, virtual time jumps ahead, and a
    // withTimeout expires before the real IO thread answers — which made the tournament
    // overview test fail only under load.
    private val ioContext: CoroutineContext = Dispatchers.IO,
) : RootComponent, ComponentContext by componentContext {
    private val _appShellState = MutableStateFlow(AppShellState.fallback(UserStats.guest()))
    private val _tournamentOverviewState =
        MutableStateFlow<Map<String, TournamentOverviewLoadState>>(emptyMap())
    private val tapProgressState = MutableStateFlow(TapProgress.initial)

    private val activateDevModeUseCase =
        ActivateDevModeUseCase(
            readCurrentDeveloperLevel = { _appShellState.value.userStats.qualification.developer },
            onDevModeActivated = { userStatsRepository.setLocalDeveloperLevel(PUBLIC_SHELF_MANAGER_DEVELOPER_LEVEL) },
        )
    override val appShellState: Flow<AppShellState> = _appShellState.asStateFlow()
    val tournamentOverviewState = _tournamentOverviewState.asStateFlow()

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

    // quizzesComponent MUST be created first — homeQuestsComponent and myQuestsComponent lambdas
    // capture it by reference. Decompose childContext keys are unique per parent context (duplicate-key
    // crash prevention on Compose recomposition).
    //
    // QuizzesComponent uses root's backHandler directly (not wrapped via childContext) so its
    // BackCallback(priority=100) is visible at the root BackHandler level per ADR-QS-12.
    // Decompose 3.x childContext wraps backHandler in DefaultChildBackHandler, which would expose
    // only a priority-0 bridge callback at root level, breaking back priority ordering.
    val quizzesComponent: QuizzesComponent = quizzesFactory(quizzesComponentContext())
    val homeQuestsComponent: HomeQuestsComponent =
        homeQuestsFactory(
            childContext("HomeQuestsContent"),
            { catId: CatalogId, name: String -> quizzesComponent.openQuestList(catId, name) },
        )
    val myQuestsComponent: MyQuestsComponent =
        myQuestsFactory(
            childContext("MyQuestsContent"),
            navigator,
            { quest: QuestDisplayItem ->
                val catalogName =
                    homeQuestsComponent.state.value.catalogs
                        .find { it.id == quest.catalogId }?.name.orEmpty()
                quizzesComponent.openSectionList(
                    quest.id,
                    listOf(
                        // Blank catalog name is forwarded as-is; the screen renders a localized fallback.
                        BreadcrumbRoot.Dynamic(catalogName),
                        BreadcrumbRoot.Dynamic(quest.title),
                    ),
                )
            },
        )
    val questCreateComponent: QuestCreateComponent =
        questCreateFactory(
            childContext("QuestCreateContent"),
            navigator,
        )
    val reviewQueueComponent: ReviewQueueComponent =
        reviewQueueFactory(
            childContext("ReviewQueueContent"),
        )
    val profileComponent: ProfileComponent =
        profileFactory(
            childContext("ProfileContent"),
        )
    val shopComponent: ShopComponent =
        shopFactory(
            childContext("ShopContent"),
        )

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
                    delay(OBSERVER_RECONNECT_DELAY_MILLIS)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "observeStats error — retrying in 5s", e)
                    delay(OBSERVER_RETRY_DELAY_MILLIS)
                }
            }
        }
    }

    override fun onDestination(destination: Destination) {
        val current = _appShellState.value
        val result = navigateUseCase(current, destination)
        applyResult(current, result)
        maybeRefreshTournamentOverview(destination)
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

    override fun onVersionTap(nowMillis: Long) {
        scope.launch {
            // Single-threaded coroutine launch via Main.immediate (no parallel tap processing
            // before first suspend point). FSM state transitions before ActivateDevModeUseCase's
            // suspend — so a racing tap coroutine reads the updated progress, not the stale one.
            val snapshot = tapProgressState.value
            when (val result = activateDevModeUseCase(snapshot, nowMillis)) {
                is TapResult.Activated -> {
                    tapProgressState.value = result.newProgress
                    sendEvent(RootEvent.DevModeActivated)
                }
                is TapResult.AlreadyDev -> {
                    tapProgressState.value = result.newProgress
                    sendEvent(RootEvent.DevModeAlreadyActive)
                }
                is TapResult.NoChange -> tapProgressState.value = result.newProgress
                is TapResult.Reset -> tapProgressState.value = result.newProgress
            }
        }
    }

    override fun onSyncNow() {
        syncScheduler.enqueueManualSync()
        sendEvent(RootEvent.SyncStarted)
    }

    private fun sendEvent(event: RootEvent) {
        if (_events.trySend(event).isFailure) {
            Log.w(TAG, "Event dropped (channel full): $event")
        }
    }

    private fun maybeRefreshTournamentOverview(destination: Destination) {
        when (destination) {
            is Destination.OpenTournamentLeaderboard -> refreshTournamentOverview(destination.event)
            is Destination.OpenTournamentParticipants -> refreshTournamentOverview(destination.event)
            else -> Unit
        }
    }

    private fun refreshTournamentOverview(event: DrawerSection.EventsSection) {
        val tournamentId = tournamentIdFor(event) ?: return
        val previous = _tournamentOverviewState.value[tournamentId]
        if (previous is TournamentOverviewLoadState.Loading) return
        _tournamentOverviewState.update {
            it + (tournamentId to TournamentOverviewLoadState.Loading(previous?.overview))
        }
        scope.launch {
            val result =
                withContext(ioContext) {
                    fetchTournamentOverview(tournamentId)
                }
            _tournamentOverviewState.update { current ->
                val next =
                    result.fold(
                        onSuccess = { TournamentOverviewLoadState.Content(it) },
                        onFailure = {
                            TournamentOverviewLoadState.Error(
                                message = it.message,
                                overview = previous?.overview,
                            )
                        },
                    )
                current + (tournamentId to next)
            }
        }
    }

    private fun tournamentIdFor(event: DrawerSection.EventsSection): String? =
        when (event) {
            DrawerSection.EventsSection.QualifierTournament -> QUALIFIER_TOURNAMENT_ID
            DrawerSection.EventsSection.WorldChampionship -> WORLD_CHAMPIONSHIP_ID
            else -> null
        }

    private fun applyResult(
        old: AppShellState,
        result: TransitionResult,
    ) {
        val new = result.newState
        _appShellState.update { new.copy(userStats = it.userStats) }
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

    // Returns a ComponentContext that uses the root's BackHandler directly, bypassing Decompose's
    // DefaultChildBackHandler wrapper introduced by childContext. stateKeeper/instanceKeeper are
    // still namespaced via childContext so state isolation is preserved.
    private fun quizzesComponentContext(): ComponentContext {
        val child = childContext("QuizzesContent")
        val rootBackHandler: BackHandler = backHandler
        return object : ComponentContext by child {
            override val backHandler: BackHandler get() = rootBackHandler
        }
    }

    private companion object {
        private const val TAG = "DefaultRootComponent"
        private const val OBSERVER_RECONNECT_DELAY_MILLIS = 1_000L
        private const val OBSERVER_RETRY_DELAY_MILLIS = 5_000L
        private const val QUALIFIER_TOURNAMENT_ID = "tournament"
        private const val WORLD_CHAMPIONSHIP_ID = "tournamentFinal"
    }
}
