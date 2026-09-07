package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpointBinding
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader

/** One-shot local observation only. Keep the initiating stamp; a read is never a launch claim. */
fun interface ArenaRunRepository {
    suspend fun read(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Result<ArenaRunRead>
}

/** Only an already durably LAUNCHED, still undecided row can contain continuation data. */
sealed interface ArenaRunRead {
    data object LocalAbsent : ArenaRunRead

    data class Prepared(val header: ArenaTicketHeader) : ArenaRunRead

    data class Continuable(
        val binding: ArenaRunnerCheckpointBinding,
        val checkpoint: ArenaRunnerCheckpoint,
        val revision: Long,
    ) : ArenaRunRead

    /** Final checkpoint awaiting the existing atomic settlement freeze, not another question. */
    data class Completing(
        val binding: ArenaRunnerCheckpointBinding,
        val checkpoint: ArenaRunnerCheckpoint,
        val revision: Long,
    ) : ArenaRunRead

    data class DecisionPending(val header: ArenaTicketHeader, val decision: ArenaRunDecision) : ArenaRunRead

    /** Status metadata only, not a financial receipt or an acknowledgement. */
    data class Terminal(val header: ArenaTicketHeader) : ArenaRunRead

    /** A null header means even immutable ownership/terms could not be safely projected. */
    data class NeedsRecovery(val header: ArenaTicketHeader?, val reason: ArenaRunRecoveryReason) : ArenaRunRead
}

enum class ArenaRunDecision { CANCEL, SETTLE }

enum class ArenaRunRecoveryReason { INVALID_RECORD, SNAPSHOT_UNAVAILABLE, CHECKPOINT_UNAVAILABLE, CHECKPOINT_INVALID }

enum class ArenaRunFailureKind { ACCOUNT_CHANGED, INVALID_REQUEST, LOCAL_READ_FAILED }

/** Never carries a platform exception, raw JSON, private content or a forged score. */
class ArenaRunFailure(val kind: ArenaRunFailureKind) : Exception(kind.name)
