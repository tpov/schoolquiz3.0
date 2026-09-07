package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketRecordEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunDecision
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRecoveryReason

/** Data-only single-row view. No snapshot/checkpoint is attached to inactive or unused states. */
sealed interface ArenaRunReadView {
    data object LocalAbsent : ArenaRunReadView

    data class Prepared(val header: ArenaTicketHeader) : ArenaRunReadView

    data class Saved(val checkpoint: ArenaSavedCheckpoint) : ArenaRunReadView

    data class DecisionPending(val header: ArenaTicketHeader, val decision: ArenaRunDecision) : ArenaRunReadView

    data class Terminal(val header: ArenaTicketHeader) : ArenaRunReadView

    data class NeedsRecovery(val header: ArenaTicketHeader?, val reason: ArenaRunRecoveryReason) : ArenaRunReadView
}

/** Pure interpretation of exactly the row returned inside the journal's existing gate. */
internal fun arenaRunReadView(
    row: ArenaTicketRecordEntity?,
    session: OutboxSession,
    ticketId: String,
    codec: ArenaJournalCodec,
    checkpoints: ArenaJournalCheckpointCodec,
): ArenaRunReadView {
    if (row == null) return ArenaRunReadView.LocalAbsent
    val header =
        validRecordOrNull {
            require(row.ownerUid == session.ownerUid && row.ticketId == ticketId)
            requireClosedRunFlags(row)
            codec.header(row).also {
                require(codec.record(it, row.observedAtMs).termsJson == row.termsJson)
                require(it.status != ArenaTicketStatus.RESERVED || row.terminalJson == null)
            }
        }
    return when {
        header == null -> ArenaRunReadView.NeedsRecovery(null, ArenaRunRecoveryReason.INVALID_RECORD)
        header.status != ArenaTicketStatus.RESERVED -> ArenaRunReadView.Terminal(header)
        row.decision != "NONE" -> ArenaRunReadView.DecisionPending(header, ArenaRunDecision.valueOf(row.decision))
        else -> reservedRunReadView(row, header, codec, checkpoints)
    }
}

private fun requireClosedRunFlags(row: ArenaTicketRecordEntity) {
    require(row.serverStatus in setOf("RESERVED", "CANCELLED", "SETTLED"))
    require(row.localUse in setOf("UNUSED", "LAUNCHED"))
    require(row.decision in setOf("NONE", "CANCEL", "SETTLE"))
    require(row.decision != "SETTLE" || row.localUse == "LAUNCHED")
    require(row.checkpointRevision >= 0 && row.observedAtMs >= 0)
    if (row.localUse == "UNUSED" && row.decision == "NONE") {
        require(row.checkpointJson == null && row.checkpointRevision == 0L)
    }
}

private fun reservedRunReadView(
    row: ArenaTicketRecordEntity,
    header: ArenaTicketHeader,
    codec: ArenaJournalCodec,
    checkpoints: ArenaJournalCheckpointCodec,
): ArenaRunReadView {
    val snapshot = validRecordOrNull { codec.decode(row).snapshot }
    val saved = row.checkpointJson
    return when {
        snapshot == null -> ArenaRunReadView.NeedsRecovery(header, ArenaRunRecoveryReason.SNAPSHOT_UNAVAILABLE)
        row.localUse == "UNUSED" -> ArenaRunReadView.Prepared(header)
        saved == null -> ArenaRunReadView.NeedsRecovery(header, ArenaRunRecoveryReason.CHECKPOINT_UNAVAILABLE)
        else -> {
            val checkpoint =
                validRecordOrNull {
                    val hash = requireNotNull(row.snapshotSha256)
                    checkpoints.validate(snapshot, hash, saved, saved, null)
                    ArenaSavedCheckpoint(snapshot, hash, saved, row.checkpointRevision)
                }
            checkpoint?.let { ArenaRunReadView.Saved(it) }
                ?: ArenaRunReadView.NeedsRecovery(header, ArenaRunRecoveryReason.CHECKPOINT_INVALID)
        }
    }
}

/** Corrupt local data is recoverable. Unexpected implementation faults and cancellation escape. */
private inline fun <T> validRecordOrNull(block: () -> T): T? =
    try {
        block()
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: ArenaRemoteFailure) {
        null
    }
