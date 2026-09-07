package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations

/**
 * Internal storage primitives; the data coordinator supplies the live owner gate.
 * One Room DAO owns the queries and transaction boundaries of this financial aggregate.
 */
@Suppress("TooManyFunctions")
@Dao
interface ArenaTicketDao {
    @Query("SELECT COUNT(*) FROM arena_ticket_records WHERE owner_uid = :owner AND snapshot_json IS NOT NULL")
    suspend fun snapshotCount(owner: String): Int

    @Query(
        "SELECT COUNT(*) FROM arena_command_journal WHERE owner_uid = :owner AND command_kind = 'PREPARE' " +
            "AND state = 'PENDING' AND command_id != :exceptRequest",
    )
    suspend fun pendingPrepareCount(
        owner: String,
        exceptRequest: String,
    ): Int

    @Query("SELECT * FROM arena_ticket_records WHERE owner_uid = :owner AND ticket_id = :ticket")
    suspend fun ticket(
        owner: String,
        ticket: String,
    ): ArenaTicketRecordEntity?

    @Query("SELECT * FROM arena_command_journal WHERE owner_uid = :owner AND command_kind = :kind AND command_id = :id")
    suspend fun command(
        owner: String,
        kind: String,
        id: String,
    ): ArenaCommandEntity?

    @Query(
        "SELECT * FROM arena_command_journal WHERE owner_uid = :owner AND ticket_id = :ticket AND command_kind = :kind",
    )
    suspend fun ticketCommand(
        owner: String,
        ticket: String,
        kind: String,
    ): ArenaCommandEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTicket(row: ArenaTicketRecordEntity)

    @Update
    suspend fun updateTicket(row: ArenaTicketRecordEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCommand(row: ArenaCommandEntity)

    @Update
    suspend fun updateCommand(row: ArenaCommandEntity)

    @Query("SELECT * FROM outbox WHERE mutation_id = :id")
    suspend fun queued(id: String): OutboxEntity?

    @Query("SELECT * FROM lesson_attempt_intents WHERE attempt_id = :id")
    suspend fun intent(id: String): LessonAttemptIntentEntity?

    @Query(
        "SELECT EXISTS(SELECT 1 FROM lesson_attempts WHERE attempt_id = :id) OR " +
            "EXISTS(SELECT 1 FROM question_answers WHERE attempt_id = :id)",
    )
    suspend fun hasScoredRows(id: String): Boolean

    @Query("SELECT * FROM mutation_receipts WHERE owner_uid = :owner AND mutation_id = :id")
    suspend fun receipt(
        owner: String,
        id: String,
    ): MutationReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIntent(row: LessonAttemptIntentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOutbox(row: OutboxEntity)

    @Transaction
    suspend fun saveConsent(row: ArenaCommandEntity) {
        require(row.commandKind == ArenaJournalValues.PREPARE && row.state == ArenaJournalValues.PENDING)
        require(row.ticketId == null && row.attemptId == null && row.ownerUid.isNotBlank())
        val old = command(row.ownerUid, row.commandKind, row.commandId)
        if (old == null) {
            if (snapshotCount(row.ownerUid) + pendingPrepareCount(row.ownerUid, row.commandId) >= MAX_SNAPSHOTS) {
                throw ArenaJournalConflict("Arena cache capacity is reserved; recover or cancel existing tickets")
            }
            insertCommand(row)
        } else if (!old.sameIntent(row)) {
            throw ArenaJournalConflict()
        }
    }

    @Transaction
    suspend fun observe(row: ArenaTicketRecordEntity) {
        require(row.serverStatus in setOf("RESERVED", "CANCELLED", "SETTLED"))
        require(row.localUse == ArenaJournalValues.UNUSED && row.decision == ArenaJournalValues.NONE)
        require(row.checkpointJson == null && row.checkpointRevision == 0L)
        val old = ticket(row.ownerUid, row.ticketId)
        val incomingSnapshot =
            if (old?.snapshotJson == null && row.snapshotJson != null &&
                snapshotCount(row.ownerUid) + pendingPrepareCount(row.ownerUid, row.requestId) >= MAX_SNAPSHOTS
            ) {
                null
            } else {
                row.snapshotJson
            }
        if (old == null) {
            insertTicket(row.copy(snapshotJson = incomingSnapshot))
            return
        }
        updateTicket(mergeObservation(old, row, incomingSnapshot))
    }

    @Transaction
    suspend fun acknowledgeObservation(
        row: ArenaTicketRecordEntity,
        kind: String,
        id: String,
    ) {
        observe(row)
        val cmd = command(row.ownerUid, kind, id) ?: throw ArenaJournalConflict()
        validateObservationCommand(cmd, row, kind, id)
        updateCommand(cmd.copy(state = ArenaJournalValues.ACKNOWLEDGED, lastFailure = null))
    }

    @Transaction
    suspend fun recordFailure(
        owner: String,
        kind: String,
        id: String,
        failure: String,
        attention: Boolean,
    ) {
        val old = command(owner, kind, id) ?: throw ArenaJournalConflict()
        if (old.state == ArenaJournalValues.ACKNOWLEDGED) return
        updateCommand(old.copy(state = if (attention) "ATTENTION" else old.state, lastFailure = failure))
    }

    @Transaction
    suspend fun claim(
        owner: String,
        id: String,
        checkpoint: String,
        validator: ArenaCheckpointValidator,
    ): Boolean {
        val old = ticket(owner, id) ?: throw ArenaJournalConflict()
        if (!old.canClaim()) return false
        validateArenaCheckpoint(old, checkpoint, null, validator)
        updateTicket(old.copy(localUse = "LAUNCHED", checkpointJson = checkpoint, checkpointRevision = 0))
        return true
    }

    @Transaction
    suspend fun saveCheckpoint(
        owner: String,
        id: String,
        attemptId: String,
        expectedRevision: Long,
        candidate: String,
        validator: ArenaCheckpointValidator,
    ): Boolean {
        require(expectedRevision >= 0 && expectedRevision < Long.MAX_VALUE)
        val old = ticket(owner, id)
        val previous = old?.checkpointJson
        return if (old != null && previous != null && old.canSaveCheckpoint(attemptId, expectedRevision)) {
            validateArenaCheckpoint(old, candidate, previous, validator)
            updateTicket(old.copy(checkpointJson = candidate, checkpointRevision = expectedRevision + 1))
            true
        } else {
            false
        }
    }

    @Transaction
    suspend fun chooseCancel(cmd: ArenaCommandEntity) {
        require(cmd.commandKind == "CANCEL" && cmd.state == "PENDING")
        val old = ticket(cmd.ownerUid, requireNotNull(cmd.ticketId)) ?: throw ArenaJournalConflict()
        validateCancellationIdentity(old, cmd)
        val previous = ticketCommand(old.ownerUid, old.ticketId, "CANCEL")
        if (previous != null) {
            if (!previous.sameIntent(cmd)) throw ArenaJournalConflict()
            return
        }
        validateNewCancellation(old)
        insertCommand(cmd)
        updateTicket(old.copy(decision = "CANCEL"))
    }

    @Transaction
    suspend fun freezeSettlement(
        cmd: ArenaCommandEntity,
        row: OutboxEntity,
        validator: ArenaCheckpointValidator,
    ) {
        val old = ticket(cmd.ownerUid, requireNotNull(cmd.ticketId)) ?: throw ArenaJournalConflict()
        validateSettlement(old, cmd, row)
        val previous = ticketCommand(old.ownerUid, old.ticketId, "SETTLE")
        if (previous != null) {
            confirmSettlementReplay(cmd, row)
            return // Never reset retry metadata or re-enqueue an ACKed body from a second save.
        }
        validateNewSettlement(old, row)
        val finalCheckpoint = old.checkpointJson ?: throw ArenaJournalConflict("No durable runner checkpoint")
        validateArenaCheckpoint(old, finalCheckpoint, finalCheckpoint, validator, cmd.bodyJson)
        insertCommand(cmd)
        insertIntent(row.attemptIntent(old.attemptId))
        insertOutbox(row)
        updateTicket(old.copy(decision = "SETTLE"))
    }

    /** Read-only proof of an already frozen body, including after terminal snapshot eviction. */
    @Transaction
    suspend fun confirmSettlementReplay(
        cmd: ArenaCommandEntity,
        row: OutboxEntity,
    ) {
        val old = ticket(cmd.ownerUid, requireNotNull(cmd.ticketId)) ?: throw ArenaJournalConflict()
        validateSettlement(old, cmd, row)
        val previous = ticketCommand(old.ownerUid, old.ticketId, "SETTLE") ?: throw ArenaJournalConflict()
        validateSettlementReplay(old, previous, cmd)
        val frozen = row.attemptIntent(old.attemptId)
        val marker = intent(old.attemptId)
        val pending = queued(row.mutationId)
        validateRetainedIntent(marker, frozen)
        validateRetainedIntent(pending?.attemptIntent(old.attemptId), frozen)
    }

    /** Explicit reconciliation only. Retained journal is the proof; existing outbox is still the only sender. */
    @Transaction
    suspend fun restoreSettlement(
        owner: String,
        id: String,
        row: OutboxEntity,
    ) {
        val old = ticket(owner, id) ?: throw ArenaJournalConflict()
        val cmd = ticketCommand(owner, id, "SETTLE") ?: throw ArenaJournalConflict()
        if (old.serverStatus != "RESERVED" || cmd.state != "PENDING") return
        validateSettlement(old, cmd, row)
        validateRestorableSettlement(old)
        val frozen = row.attemptIntent(old.attemptId)
        val marker = intent(old.attemptId)
        validateRetainedIntent(marker, frozen)
        val pending = queued(row.mutationId)
        if (pending != null) {
            validateRetainedIntent(pending.attemptIntent(old.attemptId), frozen)
            if (pending.state == "QUARANTINED") {
                updateCommand(
                    cmd.copy(state = "ATTENTION", lastFailure = "QUARANTINED"),
                )
            }
        } else if (receipt(owner, row.mutationId) == null) {
            // A generic receipt means delivery happened; recovery GET supplies terminal meaning.
            if (marker == null) insertIntent(frozen)
            insertOutbox(row)
        }
    }

    @Transaction
    suspend fun evictSnapshot(
        owner: String,
        id: String,
    ): Boolean {
        val old = ticket(owner, id) ?: return false
        return if (old.serverStatus == "RESERVED" && (old.localUse == "LAUNCHED" || old.decision != "NONE")) {
            false
        } else {
            updateTicket(
                old.copy(
                    snapshotJson = null,
                    checkpointJson = if (old.serverStatus == "RESERVED") old.checkpointJson else null,
                ),
            ) // Fingerprint and financial/use markers remain.
            true
        }
    }
}

private const val MAX_SNAPSHOTS = 20

private fun mergeObservation(
    old: ArenaTicketRecordEntity,
    row: ArenaTicketRecordEntity,
    incomingSnapshot: String?,
): ArenaTicketRecordEntity {
    validateObservedIdentity(old, row)
    if (old.serverStatus != "RESERVED" && row.serverStatus != "RESERVED" && old.serverStatus != row.serverStatus) {
        throw ArenaJournalConflict("Contradictory server terminal states")
    }
    if (old.terminalJson != null && row.terminalJson != null && old.terminalJson != row.terminalJson) {
        throw ArenaJournalConflict("Contradictory permanent receipt")
    }
    return old.copy(
        snapshotJson = old.snapshotJson ?: incomingSnapshot,
        snapshotSha256 = old.snapshotSha256 ?: row.snapshotSha256,
        serverStatus = if (old.serverStatus != "RESERVED") old.serverStatus else row.serverStatus,
        terminalJson = old.terminalJson ?: row.terminalJson,
        observedAtMs = maxOf(old.observedAtMs, row.observedAtMs),
    )
}

private fun validateObservedIdentity(
    old: ArenaTicketRecordEntity,
    row: ArenaTicketRecordEntity,
) {
    if (old.attemptId != row.attemptId || old.requestId != row.requestId || old.termsJson != row.termsJson) {
        throw ArenaJournalConflict()
    }
    if (old.snapshotSha256 != null && row.snapshotSha256 != null && old.snapshotSha256 != row.snapshotSha256) {
        throw ArenaJournalConflict()
    }
}

private fun validateObservationCommand(
    cmd: ArenaCommandEntity,
    row: ArenaTicketRecordEntity,
    kind: String,
    id: String,
) {
    val ticketMatches = cmd.ticketId == null || cmd.ticketId == row.ticketId
    val prepareMatches = kind != "PREPARE" || row.requestId == id
    val attemptMatches = cmd.attemptId == null || cmd.attemptId == row.attemptId
    if (!ticketMatches || !prepareMatches || !attemptMatches) throw ArenaJournalConflict()
}

private fun ArenaTicketRecordEntity.canClaim(): Boolean {
    val available = serverStatus == "RESERVED" && decision == "NONE" && snapshotJson != null
    val unused = localUse == "UNUSED" && checkpointJson == null
    return available && unused
}

private fun ArenaTicketRecordEntity.canSaveCheckpoint(
    attempt: String,
    revision: Long,
): Boolean {
    val identityMatches = attemptId == attempt && checkpointRevision == revision
    val active = serverStatus == "RESERVED" && decision == "NONE" && localUse == "LAUNCHED"
    return identityMatches && active
}

private fun validateNewCancellation(ticket: ArenaTicketRecordEntity) {
    if (ticket.serverStatus != "RESERVED" || ticket.decision != "NONE") throw ArenaJournalConflict()
}

private fun validateCancellationIdentity(
    ticket: ArenaTicketRecordEntity,
    cmd: ArenaCommandEntity,
) {
    if (ticket.attemptId != cmd.attemptId) throw ArenaJournalConflict()
}

/** Called inside freezeSettlement's Room transaction, preserving the ordered short-circuit reads. */
private suspend fun ArenaTicketDao.validateNewSettlement(
    ticket: ArenaTicketRecordEntity,
    row: OutboxEntity,
) {
    if (ticket.serverStatus != "RESERVED" || ticket.localUse != "LAUNCHED" || ticket.decision != "NONE") {
        throw ArenaJournalConflict()
    }
    if (queued(row.mutationId) != null || intent(ticket.attemptId) != null || hasScoredRows(ticket.attemptId)) {
        throw ArenaJournalConflict()
    }
}

private fun validateSettlementReplay(
    ticket: ArenaTicketRecordEntity,
    previous: ArenaCommandEntity,
    cmd: ArenaCommandEntity,
) {
    if (ticket.decision != "SETTLE" || !previous.sameIntent(cmd)) throw ArenaJournalConflict()
}

private fun validateRetainedIntent(
    actual: LessonAttemptIntentEntity?,
    expected: LessonAttemptIntentEntity,
) {
    if (actual != null && actual != expected) throw ArenaJournalConflict()
}

/** Called inside restoreSettlement's Room transaction, before reading the first-intent marker. */
private suspend fun ArenaTicketDao.validateRestorableSettlement(ticket: ArenaTicketRecordEntity) {
    if (ticket.decision != "SETTLE" || hasScoredRows(ticket.attemptId)) throw ArenaJournalConflict()
}

private fun validateSettlement(
    ticket: ArenaTicketRecordEntity,
    cmd: ArenaCommandEntity,
    row: OutboxEntity,
) {
    validateSettlementCommand(ticket, cmd)
    validateSettlementDelivery(ticket, cmd, row)
}

private fun validateSettlementCommand(
    ticket: ArenaTicketRecordEntity,
    cmd: ArenaCommandEntity,
) {
    if (cmd.commandKind != "SETTLE" || cmd.state != "PENDING" || cmd.ownerUid != ticket.ownerUid) {
        throw ArenaJournalConflict()
    }
    if (cmd.attemptId != ticket.attemptId || cmd.commandId != ticket.attemptId) throw ArenaJournalConflict()
}

private fun validateSettlementDelivery(
    ticket: ArenaTicketRecordEntity,
    cmd: ArenaCommandEntity,
    row: OutboxEntity,
) {
    val ownerAndOperationMatch = row.ownerUid == ticket.ownerUid && row.operation == OutboxOperations.SUBMIT_ATTEMPT
    if (!ownerAndOperationMatch || row.payload != cmd.bodyJson || row.state != "WAITING") throw ArenaJournalConflict()
    // Keep the fallible mutationKey call after the earlier guards, as in the original condition.
    if (row.mutationId != OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, ticket.attemptId) ||
        row.entityRef != "lesson_runner:attempt:${ticket.attemptId}" || row.expectedVersion != null
    ) {
        throw ArenaJournalConflict()
    }
}
