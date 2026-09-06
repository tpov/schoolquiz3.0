package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.CoroutineContext

class RunnerStateHolder(
    mainContext: CoroutineContext = Dispatchers.Main.immediate,
) : InstanceKeeper.Instance {
    val scope = CoroutineScope(SupervisorJob() + mainContext)
    val uiState: MutableStateFlow<RunnerUiState> = MutableStateFlow(RunnerUiState.Loading)
    var domainState: RunnerState = RunnerState.Loading
    /** Shared by retained components: only one Complete/Abort may save a started attempt. */
    val terminalSaveMutex = Mutex()

    // Set to true in doOnDestroy (Decompose lifecycle). Cleared by InstanceKeeper.onDestroy
    // (navigation pop / true process death). If still true when new component init runs,
    // means config change — restore pre-stop snapshot instead of applying the pause.
    var pendingConfigChangeRestore: Boolean = false
    var snapshotBeforeStop: Pair<RunnerState, RunnerUiState>? = null

    /** Last partial answer typed/selected on current question — survives rotation via InstanceKeeper. */
    var currentDraftAnswer: UserAnswerDraft? = null

    /**
     * Local mirror of the life budget for this session, in hearts. Read from the profile at
     * start, spent by the hint; the server stays the authority and is never written here.
     */
    var livesRemainingHearts: Int? = null

    /**
     * Подписка на пометку очереди для показанного прохождения. Живёт ровно столько, сколько на
     * экране держится его результат: новая попытка спрашивает про свою запись, а не про прежнюю.
     */
    var syncMarkJob: Job? = null
    var settlementJob: Job? = null

    /** Fresh attempt on the same lesson (Run again) — keeps the scope and the life mirror. */
    fun resetForRestart() {
        syncMarkJob?.cancel()
        syncMarkJob = null
        settlementJob?.cancel()
        settlementJob = null
        pendingConfigChangeRestore = false
        snapshotBeforeStop = null
        currentDraftAnswer = null
        domainState = RunnerState.Loading
        uiState.value = RunnerUiState.Loading
    }

    override fun onDestroy() {
        // Called on navigation pop or process death — NOT on config change.
        pendingConfigChangeRestore = false
        snapshotBeforeStop = null
        currentDraftAnswer = null
        livesRemainingHearts = null
        syncMarkJob = null
        scope.cancel()
        settlementJob = null
        uiState.value = RunnerUiState.Loading
        domainState = RunnerState.Loading
    }
}
