package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaLaunchClaim
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaRunReadView
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaRunnerCheckpointCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaSavedCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaTicketJournal
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.arenaCheckpointCompletion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaInitialCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpointBinding
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketSnapshot
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunCasOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunClaimOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunFinalizeOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRecoveryReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/** Strict local adapter. Original epoch and existing journal TXs; no network, new sender or wallet writes. */
class ArenaRunRepositoryImpl(
    private val journal: ArenaTicketJournal,
    private val checkpoints: ArenaRunnerCheckpointCodec,
    private val sessions: ArenaSessionProvider,
    private val dispatcher: CoroutineDispatcher,
    private val clock: Clock = Clock.System,
    private val seed: () -> Long = { Random.nextLong() },
) : ArenaRunRepository {
    override suspend fun resumeOnly(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Result<ArenaRunRead> = read(stamp, ticketId)

    override suspend fun freshClaim(
        stamp: ArenaSessionStamp,
        ticketId: String,
        sessionMode: SessionMode,
    ): Result<ArenaRunClaimOutcome> =
        write(
            stamp,
            ticketId,
            capture = { FreshIntent(sessionMode, clock.now().toEpochMilliseconds(), seed()) },
        ) { original, intent ->
            val claim = journal.claimForLaunch(original, ticketId) { snapshot -> intent.initial(snapshot) }.getOrThrow()
            requireCurrent(stamp)
            val current = observe(original, ticketId)
            if (claim is ArenaLaunchClaim.Claimed) {
                ArenaRunClaimOutcome.Claimed(current)
            } else {
                ArenaRunClaimOutcome.NotFresh(current)
            }
        }

    override suspend fun checkpointCAS(
        stamp: ArenaSessionStamp,
        ticketId: String,
        expectedRevision: Long,
        candidate: ArenaRunnerCheckpoint,
    ): Result<ArenaRunCasOutcome> =
        write(stamp, ticketId, capture = { Unit }) { original, _ ->
            require(expectedRevision in 0 until Long.MAX_VALUE)
            val current = observe(original, ticketId)
            val saved = current.saved()
            if (saved == null) {
                ArenaRunCasOutcome.Conflict(current)
            } else {
                val bytes = checkpoints.encode(candidate, saved.binding)
                if (saved.matches(bytes, expectedRevision)) {
                    ArenaRunCasOutcome.AlreadyCurrent(current)
                } else if (saved.revision != expectedRevision) {
                    ArenaRunCasOutcome.Conflict(current)
                } else {
                    val accepted =
                        journal.updateCheckpoint(
                            original,
                            ticketId,
                            candidate.attemptId,
                            expectedRevision,
                            bytes,
                        ).getOrThrow()
                    requireCurrent(stamp)
                    val observed = observe(original, ticketId)
                    when {
                        accepted -> ArenaRunCasOutcome.Saved(observed)
                        observed.saved()?.let {
                            it.binding == saved.binding && it.matches(bytes, expectedRevision)
                        } == true -> ArenaRunCasOutcome.AlreadyCurrent(observed)
                        else -> ArenaRunCasOutcome.Conflict(observed)
                    }
                }
            }
        }

    override suspend fun finalize(
        stamp: ArenaSessionStamp,
        completing: ArenaRunRead.Completing,
    ): Result<ArenaRunFinalizeOutcome> =
        write(
            stamp,
            completing.checkpoint.ticketId,
            capture = { Unit },
        ) { original, _ ->
            require(completing.revision >= 0)
            require(completing.binding.snapshot.header.ownerUid == stamp.ownerUid)
            require(completing.checkpoint.phase == ArenaRunnerPhase.COMPLETING)
            checkpoints.encode(completing.checkpoint, completing.binding)
            val intent = arenaCheckpointCompletion(completing.checkpoint, completing.binding)
            journal.freezeCompletion(original, completing.checkpoint.ticketId, intent).getOrThrow()
            ArenaRunFinalizeOutcome.FrozenLocally(completing.checkpoint.ticketId, completing.checkpoint.attemptId)
        }

    /** Capture explicit fresh timing/seed before the first suspension, then fence every return hop. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <P, T> write(
        stamp: ArenaSessionStamp,
        ticketId: String,
        capture: () -> P,
        action: suspend (OutboxSession, P) -> T,
    ): Result<T> =
        try {
            requireCurrent(stamp)
            validateRequest(stamp, ticketId)
            val parameters = capture()
            val original = OutboxSession(stamp.ownerUid, stamp.generation)
            val value =
                withContext(dispatcher) {
                    requireCurrent(stamp)
                    action(original, parameters).also { requireCurrent(stamp) }
                }
            requireCurrent(stamp)
            Result.success(value)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ArenaRunFailure) {
            Result.failure(if (sessions.current() != stamp) changed() else ArenaRunFailure(error.kind))
        } catch (_: Exception) {
            Result.failure(
                if (sessions.current() != stamp) {
                    changed()
                } else {
                    ArenaRunFailure(ArenaRunFailureKind.LOCAL_WRITE_FAILED)
                },
            )
        }

    private suspend fun observe(
        original: OutboxSession,
        ticketId: String,
    ): ArenaRunRead {
        val view = journal.readRunView(original, ticketId).getOrThrow()
        requireCurrent(ArenaSessionStamp(original.ownerUid, original.generation))
        return project(view)
    }

    private fun ArenaRunRead.saved(): SavedView? =
        when (this) {
            is ArenaRunRead.Continuable -> SavedView(binding, checkpoint, revision)
            is ArenaRunRead.Completing -> SavedView(binding, checkpoint, revision)
            else -> null
        }

    private fun SavedView.matches(
        bytes: String,
        expectedRevision: Long,
    ): Boolean =
        (revision == expectedRevision || revision == expectedRevision + 1) &&
            checkpoints.encode(checkpoint, binding) == bytes

    private data class SavedView(
        val binding: ArenaRunnerCheckpointBinding,
        val checkpoint: ArenaRunnerCheckpoint,
        val revision: Long,
    )

    private data class FreshIntent(val mode: SessionMode, val nowMs: Long, val seed: Long) {
        fun initial(snapshot: ArenaTicketSnapshot): ArenaInitialCheckpoint {
            val seconds =
                computeTimer(
                    snapshot.questions.first().display,
                    snapshot.header.difficulty,
                    TimerCoefficients.Default,
                    mode,
                ).seconds
            val durationMs = seconds * MILLIS_PER_SECOND
            require(nowMs in 0..Long.MAX_VALUE - durationMs)
            return ArenaInitialCheckpoint(mode, seed, nowMs, nowMs + durationMs)
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }

    // Unknown database/platform failures are sanitized here, after rechecking the initiating epoch.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun read(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Result<ArenaRunRead> {
        val original = OutboxSession(stamp.ownerUid, stamp.generation)
        val result =
            withContext(dispatcher) {
                try {
                    requireCurrent(stamp)
                    validateRequest(stamp, ticketId)
                    val view = journal.readRunView(original, ticketId).getOrThrow()
                    requireCurrent(stamp)
                    val result = project(view)
                    requireCurrent(stamp)
                    Result.success(result)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // Do not return a stale error (including one with a private message) after A→B→A.
                    Result.failure(if (sessions.current() != stamp) changed() else sanitize(error))
                }
            }
        // Include the dispatch back to the caller; a same-UID epoch can change on that hop too.
        return if (sessions.current() != stamp) Result.failure(changed()) else result
    }

    private fun project(view: ArenaRunReadView): ArenaRunRead =
        when (view) {
            ArenaRunReadView.LocalAbsent -> ArenaRunRead.LocalAbsent
            is ArenaRunReadView.Prepared -> ArenaRunRead.Prepared(view.header)
            is ArenaRunReadView.Saved -> projectCheckpoint(view.checkpoint)
            is ArenaRunReadView.DecisionPending -> ArenaRunRead.DecisionPending(view.header, view.decision)
            is ArenaRunReadView.Terminal -> ArenaRunRead.Terminal(view.header)
            is ArenaRunReadView.NeedsRecovery -> ArenaRunRead.NeedsRecovery(view.header, view.reason)
        }

    private fun projectCheckpoint(saved: ArenaSavedCheckpoint): ArenaRunRead {
        val binding = ArenaRunnerCheckpointBinding(saved.snapshot, saved.snapshotSha256)
        return try {
            val checkpoint = checkpoints.decode(saved.json, binding)
            if (checkpoint.phase == ArenaRunnerPhase.COMPLETING) {
                ArenaRunRead.Completing(binding, checkpoint, saved.revision)
            } else {
                ArenaRunRead.Continuable(binding, checkpoint, saved.revision)
            }
        } catch (_: IllegalArgumentException) {
            ArenaRunRead.NeedsRecovery(saved.snapshot.header, ArenaRunRecoveryReason.CHECKPOINT_INVALID)
        }
    }

    private fun validateRequest(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ) {
        try {
            ArenaTicketGetRequest(stamp.ownerUid, ticketId)
        } catch (_: IllegalArgumentException) {
            throw ArenaRunFailure(ArenaRunFailureKind.INVALID_REQUEST)
        }
    }

    private fun requireCurrent(stamp: ArenaSessionStamp) {
        if (sessions.current() != stamp) throw changed()
    }

    private fun changed() = ArenaRunFailure(ArenaRunFailureKind.ACCOUNT_CHANGED)

    private fun sanitize(error: Exception): ArenaRunFailure =
        when {
            error is ArenaRunFailure -> ArenaRunFailure(error.kind)
            error is ArenaRemoteFailure && error.kind == ArenaRemoteFailureKind.ACCOUNT_CHANGED -> changed()
            else -> ArenaRunFailure(ArenaRunFailureKind.LOCAL_READ_FAILED)
        }
}
