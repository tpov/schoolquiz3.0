package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.ArenaRunTransitions
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.advanceArenaFeedbackToPause
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.revealPreparedArenaQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.timeoutArenaQuestionToPause
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaEntryRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunCasOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.coroutines.cancellation.CancellationException

/** Retain this object with its scope across configuration changes. No ordinary runner use cases or sender. */
internal class ArenaRunCoordinator(
    private val stamp: ArenaSessionStamp,
    private val ticketId: String,
    private val runs: ArenaRunRepository,
    private val sessions: ArenaSessionProvider,
    private val scope: CoroutineScope,
    private val clock: Clock,
) {
    private val mutableState = MutableStateFlow(ArenaRunCoordinatorState())
    val state = mutableState.asStateFlow()
    private var active: Any? = null
    private var job: Job? = null
    private var started = false

    fun start(intent: ArenaRunStart) {
        if (started || !checkSession()) return
        started = true
        perform({ Unit }) {
            val current =
                when (intent) {
                    is ArenaRunStart.Fresh -> runs.freshClaim(stamp, ticketId, intent.mode).getOrThrow().current
                    ArenaRunStart.ResumeOnly -> runs.resumeOnly(stamp, ticketId).getOrThrow()
                }
            publish(current)
        }
    }

    /** Explicit recovery after an unknown write outcome is a read, never another fresh invocation. */
    fun reload() {
        if (!started) return
        perform({ Unit }) { publish(runs.resumeOnly(stamp, ticketId).getOrThrow()) }
    }

    fun token(): ArenaRunToken? {
        if (!checkSession()) return null
        val view = state.value
        val current = view.observed as? ArenaRunRead.Continuable
        return if (current == null || view.busy || view.notice != null) {
            null
        } else {
            ArenaRunToken(
                stamp,
                ticketId,
                current.checkpoint.attemptId,
                current.revision,
                current.checkpoint.indexInPool,
                current.checkpoint.phase,
            )
        }
    }

    /** All callbacks carry the token captured by the rendered frame, not a newly sampled token. */
    fun dispatch(
        token: ArenaRunToken,
        action: ArenaRunAction,
    ) {
        if (token != this.token()) return
        val current = state.value.observed as? ArenaRunRead.Continuable ?: return
        perform(
            { if (action is ArenaRunAction.BackgroundAt) action.nowMs else clock.now().toEpochMilliseconds() },
        ) { now ->
            val candidate =
                when (action) {
                    is ArenaRunAction.Draft -> ArenaRunTransitions.draft(current, action.answer)
                    is ArenaRunAction.Answer -> ArenaRunTransitions.answer(current, action.answer)
                    is ArenaRunAction.Hint -> ArenaRunTransitions.hint(current, action.answer)
                    ArenaRunAction.Continue -> ArenaRunTransitions.continueRun(current, now)
                    ArenaRunAction.Timeout -> ArenaRunTransitions.timeout(current, now)
                    ArenaRunAction.Background -> ArenaRunTransitions.background(current, now)
                    is ArenaRunAction.BackgroundAt -> ArenaRunTransitions.background(current, now)
                    ArenaRunAction.AdvanceToPause -> advanceArenaFeedbackToPause(current, now)
                    ArenaRunAction.TimeoutToPause -> timeoutArenaQuestionToPause(current, now)
                    ArenaRunAction.RevealPrepared -> revealPreparedArenaQuestion(current)
                }
            val result = runs.checkpointCAS(stamp, ticketId, current.revision, candidate).getOrThrow()
            publish(result.current, if (result is ArenaRunCasOutcome.Conflict) ArenaRunNotice.CONFLICT else null)
        }
    }

    /** Durable local final intent only. The resulting read remains pending until a genuine server receipt. */
    fun freeze() {
        val current = state.value.observed as? ArenaRunRead.Completing ?: return
        perform({ Unit }) {
            runs.finalize(stamp, current).getOrThrow()
            publish(runs.resumeOnly(stamp, ticketId).getOrThrow())
        }
    }

    /** User-confirmed local cancellation. Network dispatch is deliberately outside this serialized writer. */
    fun cancel(
        entries: ArenaEntryRepository,
        requestId: String,
    ) {
        val current = state.value.observed
        val cancellable =
            current is ArenaRunRead.Prepared || current is ArenaRunRead.Continuable ||
                current is ArenaRunRead.NeedsRecovery && current.header != null
        if (!cancellable) return
        perform({ Unit }) {
            entries.recordCancelIntent(stamp, ticketId, requestId).getOrThrow()
            publish(runs.resumeOnly(stamp, ticketId).getOrThrow())
        }
    }

    /** Called by the common epoch observer as well as before every interaction and publication. */
    fun checkSession(): Boolean {
        if (sessions.current() == stamp) return true
        active = null
        job?.cancel()
        job = null
        mutableState.value = ArenaRunCoordinatorState(notice = ArenaRunNotice.ACCOUNT_CHANGED)
        return false
    }

    private fun publish(
        current: ArenaRunRead,
        notice: ArenaRunNotice? = null,
    ) {
        if (!checkSession()) return
        val header =
            when (current) {
                is ArenaRunRead.Prepared -> current.header
                is ArenaRunRead.Continuable -> current.binding.snapshot.header
                is ArenaRunRead.Completing -> current.binding.snapshot.header
                is ArenaRunRead.DecisionPending -> current.header
                is ArenaRunRead.Terminal -> current.header
                is ArenaRunRead.NeedsRecovery -> current.header
                ArenaRunRead.LocalAbsent -> null
            }
        require(header == null || header.ownerUid == stamp.ownerUid && header.ticketId == ticketId)
        mutableState.value = state.value.copy(observed = current, notice = notice)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun <P> perform(
        capture: () -> P,
        block: suspend (P) -> Unit,
    ) {
        if (!checkSession() || active != null) return
        val operation = Any()
        active = operation // Before emission: reentrant collectors cannot start a competing write.
        val parameters =
            try {
                capture() // Timestamp belongs to this callback, before the launch/dispatcher suspension.
            } catch (cancelled: CancellationException) {
                captureFailed(operation)
                throw cancelled
            } catch (_: Exception) {
                captureFailed(operation)
                return
            }
        if (checkSession() && active === operation) {
            mutableState.value = state.value.copy(busy = true, notice = null)
            val pending = scope.launch(start = CoroutineStart.LAZY) { execute(operation) { block(parameters) } }
            job = pending
            // A cancelled parent may prevent the lazy body (and its finally) from starting at all.
            pending.invokeOnCompletion { captureFailed(operation) }
            pending.start()
        }
    }

    private fun captureFailed(operation: Any) {
        if (checkSession() && active === operation) {
            active = null
            job = null
            mutableState.value = state.value.copy(busy = false, notice = ArenaRunNotice.RELOAD_REQUIRED)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun execute(
        operation: Any,
        block: suspend () -> Unit,
    ) {
        try {
            if (checkSession()) block()
        } catch (cancelled: CancellationException) {
            if (checkSession() && active === operation) {
                mutableState.value = state.value.copy(notice = ArenaRunNotice.RELOAD_REQUIRED)
            }
            throw cancelled
        } catch (_: Exception) {
            if (checkSession() && active === operation) {
                mutableState.value = state.value.copy(notice = ArenaRunNotice.RELOAD_REQUIRED)
            }
        } finally {
            if (checkSession() && active === operation) {
                active = null
                job = null
                mutableState.value = state.value.copy(busy = false)
            }
        }
    }
}

internal data class ArenaRunCoordinatorState(
    val observed: ArenaRunRead? = null,
    val busy: Boolean = false,
    val notice: ArenaRunNotice? = null,
)

internal enum class ArenaRunNotice { RELOAD_REQUIRED, CONFLICT, ACCOUNT_CHANGED }

internal data class ArenaRunToken(
    val stamp: ArenaSessionStamp,
    val ticketId: String,
    val attemptId: String,
    val revision: Long,
    val index: Int,
    val phase: ArenaRunnerPhase,
)

internal sealed interface ArenaRunStart {
    data class Fresh(val mode: SessionMode) : ArenaRunStart

    data object ResumeOnly : ArenaRunStart
}

internal sealed interface ArenaRunAction {
    data class Draft(val answer: UserAnswerDraft) : ArenaRunAction

    data class Answer(val answer: UserAnswerDraft) : ArenaRunAction

    data class Hint(val answer: UserAnswerDraft) : ArenaRunAction

    data object Continue : ArenaRunAction

    data object Timeout : ArenaRunAction

    data object Background : ArenaRunAction

    data class BackgroundAt(val nowMs: Long) : ArenaRunAction

    data object AdvanceToPause : ArenaRunAction

    data object TimeoutToPause : ArenaRunAction

    data object RevealPrepared : ArenaRunAction
}
