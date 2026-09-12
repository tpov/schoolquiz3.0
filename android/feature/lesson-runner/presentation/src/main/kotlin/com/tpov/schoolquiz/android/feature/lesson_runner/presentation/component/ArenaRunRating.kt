package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaFrozenRatingState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaFrozenRatingRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** Immutable frozen result input; a different attempt must never reuse a local rating observation. */
internal data class ArenaVerifiedResult(
    val stamp: ArenaSessionStamp,
    val ticketId: String,
    val attempt: AttemptIdentity,
    val settlement: ServerAttemptSettlement,
) {
    val identity: ArenaVerifiedResultIdentity
        get() = ArenaVerifiedResultIdentity(stamp, ticketId, attempt)
}

/** The retained key excludes mutable receipt fields but includes the authentication generation. */
internal data class ArenaVerifiedResultIdentity(
    val stamp: ArenaSessionStamp,
    val ticketId: String,
    val attempt: AttemptIdentity,
)

/** Local durability and server acceptance remain distinct states from the frozen-rating port. */
internal data class ArenaRunRatingState(
    val local: ArenaFrozenRatingState = ArenaFrozenRatingState.Unavailable,
    val submitting: Boolean = false,
    val syncCoolingDown: Boolean = false,
    val submitFailed: Boolean = false,
    val observationFailed: Boolean = false,
) {
    val canSubmit: Boolean
        get() = local is ArenaFrozenRatingState.Pending && !submitting

    val canSync: Boolean
        get() = local is ArenaFrozenRatingState.Queued && !submitting && !syncCoolingDown
}

/**
 * Retained owner for frozen Arena rating state. The future result host supplies only a
 * server-confirmed result and calls [onSessionChanged] from its retained session observer.
 */
internal class ArenaRunRating(
    private val ratings: ArenaFrozenRatingRepository,
    private val sessions: ArenaSessionProvider,
    private val sync: SyncScheduler,
    mainContext: CoroutineContext,
    private val queuedSyncCooldownMs: Long = QUEUED_SYNC_COOLDOWN_MS,
) : InstanceKeeper.Instance {
    private val scope = CoroutineScope(mainContext + SupervisorJob())
    private val mutableState = MutableStateFlow(ArenaRunRatingState())
    val state = mutableState.asStateFlow()

    private var target: ArenaVerifiedResultIdentity? = null
    private var observation: Job? = null
    private var submission: Job? = null
    private var syncCooldown: Job? = null
    private var destroyed = false

    /** Rebinding is idempotent for the same verified frozen identity. */
    fun bind(result: ArenaVerifiedResult?) {
        if (destroyed) return
        val next = result?.takeIf(::isEligible)?.identity
        if (target != next) {
            clearTarget()
            target = next
            next?.let(::observe)
        }
    }

    /** The retained host calls this on every authentication event, including same-UID epoch changes. */
    fun onSessionChanged() {
        target?.takeIf { sessions.current() != it.stamp }?.let { clearTarget() }
    }

    /** The first in-flight intent is deliberately left to the repository's durable idempotency. */
    fun submit(rating: Int) {
        val current = target ?: return
        if (rating !in ARENA_RATING_RANGE || !isCurrent(current) || !state.value.canSubmit) return

        mutableState.value = state.value.copy(submitting = true, submitFailed = false)
        submission =
            scope.launch {
                try {
                    ratings.submit(current.stamp, current.ticketId, rating).fold(
                        onSuccess = { local -> publishSuccess(current, local) },
                        onFailure = { error ->
                            if (error is CancellationException) throw error
                            publishFailure(current)
                        },
                    )
                } catch (cancelled: CancellationException) {
                    clearSubmitting(current)
                    throw cancelled
                }
            }
    }

    /** Explicit only: a queued Arena rating may request the app's existing manual sync worker. */
    fun requestSync() {
        val current = target ?: return
        if (isCurrent(current) && state.value.canSync) {
            mutableState.value = state.value.copy(syncCoolingDown = true)
            sync.enqueueManualSync()
            syncCooldown =
                scope.launch {
                    delay(queuedSyncCooldownMs)
                    if (isCurrent(current)) {
                        mutableState.value = state.value.copy(syncCoolingDown = false)
                    }
                }
        }
    }

    private fun observe(current: ArenaVerifiedResultIdentity) {
        observation =
            scope.launch {
                flow { emitAll(ratings.observe(current.stamp, current.ticketId)) }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        publishObservationFailure(current)
                    }.collect { local -> publishObserved(current, local) }
            }
    }

    private fun publishObserved(
        current: ArenaVerifiedResultIdentity,
        local: ArenaFrozenRatingState,
    ) {
        if (isCurrent(current)) {
            val previous = state.value
            val shown = previous.local.preserveAccepted(local)
            mutableState.value =
                previous.copy(
                    local = shown,
                    // The retry notice belongs only to the state that still offers a retry control.
                    submitFailed = previous.submitFailed && shown is ArenaFrozenRatingState.Pending,
                    observationFailed = false,
                )
        }
    }

    private fun publishSuccess(
        current: ArenaVerifiedResultIdentity,
        local: ArenaFrozenRatingState,
    ) {
        if (isCurrent(current)) {
            val previous = state.value
            mutableState.value =
                previous.copy(
                    local = previous.local.preserveAccepted(local),
                    submitting = false,
                    submitFailed = false,
                    observationFailed = false,
                )
        }
    }

    private fun publishObservationFailure(current: ArenaVerifiedResultIdentity) {
        if (isCurrent(current)) {
            mutableState.value =
                state.value.copy(
                    local = ArenaFrozenRatingState.Unavailable,
                    // A closed observation supersedes an earlier submit failure: no retry is offered here.
                    submitFailed = false,
                    observationFailed = true,
                )
        }
    }

    private fun publishFailure(current: ArenaVerifiedResultIdentity) {
        if (isCurrent(current)) {
            mutableState.value = state.value.copy(submitting = false, submitFailed = true)
        }
    }

    private fun clearSubmitting(current: ArenaVerifiedResultIdentity) {
        if (isCurrent(current)) {
            mutableState.value = state.value.copy(submitting = false)
        }
    }

    private fun isEligible(result: ArenaVerifiedResult): Boolean =
        result.settlement.isConfirmedCompletion &&
            result.hasSafeIdentity() &&
            sessions.current() == result.stamp

    private fun isCurrent(current: ArenaVerifiedResultIdentity): Boolean =
        !destroyed && target == current && sessions.current() == current.stamp

    private fun clearTarget() {
        target = null
        observation?.cancel()
        observation = null
        submission?.cancel()
        submission = null
        syncCooldown?.cancel()
        syncCooldown = null
        mutableState.value = ArenaRunRatingState()
    }

    override fun onDestroy() {
        destroyed = true
        clearTarget()
        scope.cancel()
    }
}

private fun ArenaVerifiedResult.hasSafeIdentity(): Boolean = ticketId.isNotBlank() && attempt.userId == stamp.ownerUid

private fun ArenaFrozenRatingState.preserveAccepted(incoming: ArenaFrozenRatingState): ArenaFrozenRatingState =
    if (this is ArenaFrozenRatingState.Accepted && incoming !is ArenaFrozenRatingState.Accepted) this else incoming

private const val MIN_ARENA_RATING = 1
private const val MAX_ARENA_RATING = 3
private val ARENA_RATING_RANGE = MIN_ARENA_RATING..MAX_ARENA_RATING
private const val QUEUED_SYNC_COOLDOWN_MS = 500L
