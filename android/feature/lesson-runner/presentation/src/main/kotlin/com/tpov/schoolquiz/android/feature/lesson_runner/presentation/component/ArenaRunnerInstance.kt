package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaRunnerRequest
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonComment
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.util.UUID
import kotlin.coroutines.CoroutineContext

/** Compatibility shell only; all question writes require the separate frame-token API. */
internal class ArenaRunnerInstance(
    private val request: ArenaRunnerRequest,
    services: ArenaRunServices,
    clock: Clock,
    mainContext: CoroutineContext,
) : InstanceKeeper.Instance, LessonRunnerRootComponent {
    private val scope = CoroutineScope(mainContext + SupervisorJob())
    val host = ArenaRunHost(request, services, clock, mainContext)
    private val channel = Channel<RunnerEvent>(Channel.BUFFERED)
    override val events = channel.receiveAsFlow()
    override val comments = MutableStateFlow<List<LessonComment>>(emptyList()).asStateFlow()
    override val uiState =
        host.view.map { (it as? ArenaRunnerView.Question)?.question ?: RunnerUiState.Loading }
            .stateIn(scope, SharingStarted.Eagerly, RunnerUiState.Loading)
    override val isHardMode get() = (host.view.value as? ArenaRunnerView.Question)?.question?.isHard == true
    private var cancelId: String? = null
    private var leaveAfterCancellation = false

    init {
        scope.launch {
            host.view.collect { frame ->
                val attention = frame as? ArenaRunnerView.Attention
                if (attention?.status == ArenaRunnerStatus.ACCOUNT_CHANGED) leaveAfterCancellation = false
                val cancellationSaved = attention?.status == ArenaRunnerStatus.PENDING_CANCEL && !attention.busy
                if (leaveAfterCancellation && cancellationSaved) {
                    leaveAfterCancellation = false
                    // Entry owns later RETRY_CANCEL dispatch; this screen only saved the durable intent.
                    channel.trySend(RunnerEvent.NavigateBack)
                }
            }
        }
    }

    fun requireRoute(other: ArenaRunnerRequest) {
        require(request.ownerUid == other.ownerUid && request.ticketId == other.ticketId)
    }

    // These legacy callbacks carry no revision. Fail closed rather than sampling a newer frame on their behalf.
    override fun onAnswer(answer: UserAnswerDraft) = Unit

    override fun onDraftChanged(draft: UserAnswerDraft) = Unit

    override fun onTimeout() = Unit

    override fun onContinue() = Unit

    override fun hintRequested() = false

    override fun onExit() = onCrossButtonTap()

    override fun onCrossButtonTap() {
        if (host.view.value is ArenaRunnerView.Question) {
            if (cancelId == null) cancelId = UUID.randomUUID().toString()
            host.requestExit()
        } else {
            onBack()
        }
    }

    override fun onCrossConfirmed() {
        val id = cancelId ?: return
        leaveAfterCancellation = true
        host.confirmExit(id)
    }

    override fun onCrossCancelled() {
        host.dismissExit()
        cancelId = null
        leaveAfterCancellation = false
    }

    override fun onSyncResult() = host.sync()

    override fun onBack() {
        val frame = host.view.value
        if (frame is ArenaRunnerView.Question) {
            onCrossButtonTap()
        } else if (canNavigateBack(frame)) {
            channel.trySend(RunnerEvent.NavigateBack)
        }
    }

    private fun canNavigateBack(frame: ArenaRunnerView): Boolean =
        when (frame) {
            is ArenaRunnerView.Result -> true
            is ArenaRunnerView.Attention -> !frame.busy && frame.status !in SAVING_STATUSES
            else -> false
        }

    override fun onFinish() = onBack()

    override fun onRunAgain() = onBack()

    override fun onNextLesson() = onBack()

    // The frozen-rating port is intentionally not wired until its owner/receipt transaction is available.
    override fun onSubmitRating(rating: Int) = Unit

    override fun onPostComment(text: String) = Unit

    override fun onDestroy() {
        scope.cancel()
        host.onDestroy()
        channel.close()
    }

    private companion object {
        val SAVING_STATUSES = setOf(ArenaRunnerStatus.LOADING, ArenaRunnerStatus.COMPLETING)
    }
}
