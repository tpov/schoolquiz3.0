package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** Retained financial identity; terms exclude observed status. Never a local wallet ledger. */
@Entity(
    tableName = "arena_ticket_records",
    primaryKeys = ["owner_uid", "ticket_id"],
    indices = [Index(value = ["owner_uid", "attempt_id"], unique = true)],
)
data class ArenaTicketRecordEntity(
    @ColumnInfo(name = "owner_uid") val ownerUid: String,
    @ColumnInfo(name = "ticket_id") val ticketId: String,
    @ColumnInfo(name = "attempt_id") val attemptId: String,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "terms_json") val termsJson: String,
    @ColumnInfo(name = "snapshot_json") val snapshotJson: String?,
    @ColumnInfo(name = "snapshot_sha256") val snapshotSha256: String?,
    @ColumnInfo(name = "server_status") val serverStatus: String,
    @ColumnInfo(name = "terminal_json") val terminalJson: String?,
    @ColumnInfo(name = "local_use") val localUse: String,
    @ColumnInfo(name = "decision") val decision: String,
    @ColumnInfo(name = "observed_at_ms") val observedAtMs: Long,
    @ColumnInfo(name = "runner_checkpoint_json") val checkpointJson: String? = null,
    @ColumnInfo(name = "runner_checkpoint_revision") val checkpointRevision: Long = 0,
)

/** Separate values: LAUNCHED is not a financial terminal decision. */
object ArenaJournalValues {
    const val PREPARE = "PREPARE"
    const val CANCEL = "CANCEL"
    const val SETTLE = "SETTLE"
    const val PENDING = "PENDING"
    const val ACKNOWLEDGED = "ACKNOWLEDGED"
    const val ATTENTION = "ATTENTION"
    const val RESERVED = "RESERVED"
    const val CANCELLED = "CANCELLED"
    const val SETTLED = "SETTLED"
    const val UNUSED = "UNUSED"
    const val LAUNCHED = "LAUNCHED"
    const val NONE = "NONE"
}

class ArenaJournalConflict(message: String = "Conflicting Arena financial identity") : IllegalStateException(message)
