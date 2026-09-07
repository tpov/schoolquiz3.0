package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaRunReadView
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaRunnerCheckpointCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaSavedCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaTicketJournal
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpointBinding
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRecoveryReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** Required concrete codec, original epoch and injected dispatcher; no network, writes or recapture. */
class ArenaRunRepositoryImpl(
    private val journal: ArenaTicketJournal,
    private val checkpoints: ArenaRunnerCheckpointCodec,
    private val sessions: ArenaSessionProvider,
    private val dispatcher: CoroutineDispatcher,
) : ArenaRunRepository {
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
