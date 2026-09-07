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
    /** Raw bounded selectors: the data adapter validates page size and supplies at most 51 rows. */
    @Query(
        "SELECT * FROM arena_ticket_records WHERE owner_uid = :owner " +
            "AND (:afterTicketId IS NULL OR ticket_id COLLATE BINARY > :afterTicketId) " +
            "ORDER BY ticket_id COLLATE BINARY ASC LIMIT :limit",
    )
    suspend fun selectLocalTickets(
        owner: String,
        afterTicketId: String?,
        limit: Int,
    ): List<ArenaTicketRecordEntity>

    @Query(
        "SELECT * FROM arena_command_journal WHERE owner_uid = :owner AND command_kind = 'PREPARE' " +
            "AND state IN ('PENDING', 'ATTENTION') " +
            "AND (:afterCommandId IS NULL OR command_id COLLATE BINARY > :afterCommandId) " +
            "ORDER BY command_id COLLATE BINARY ASC LIMIT :limit",
    )
    suspend fun selectLocalPendingPrepares(
        owner: String,
        afterCommandId: String?,
        limit: Int,
    ): List<ArenaCommandEntity>

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

    @Query("SELECT EXISTS(SELECT 1 FROM mutation_receipts WHERE mutation_id = :id)")
    suspend fun hasAnyReceipt(id: String): Boolean

    /** Arena settlement always has expected_version NULL; all other immutable fields and state are bound. */
    @Query(
        "DELETE FROM outbox WHERE id = :id AND owner_uid = :owner AND mutation_id = :mutation " +
            "AND operation = :operation AND payload = :payload AND entity_ref IS :entityRef " +
            "AND expected_version IS NULL AND state = :state",
    )
    suspend fun deleteCancelledPending(
        id: Long,
        owner: String,
        mutation: String,
        operation: String,
        payload: String,
        entityRef: String?,
        state: String,
    ): Int

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
        val previous = command(row.ownerUid, kind, id)
        if (previous?.state == ARENA_RESOLVED_CANCELLED) throw ArenaJournalConflict("Cancellation is not a generic ACK")
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
        if (old.state == ArenaJournalValues.ACKNOWLEDGED || old.state == ARENA_RESOLVED_CANCELLED) return
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

    /**
     * Storage-only terminal resolution; caller must hold the same live owner-generation/delivery gate.
     * Genuine server cancellation, retained SETTLE tombstone and first intent are written before exact queue deletion.
     * All writes share one commit; none becomes visible independently.
     * No generic receipt, second sender, local balance/score update or local cancellation is synthesized.
     */
    @Transaction
    suspend fun resolveCancelledSettlement(
        command: ArenaCommandEntity,
        firstIntent: LessonAttemptIntentEntity,
        observation: ArenaTicketRecordEntity,
        validator: ArenaCancellationValidator,
    ) {
        val old = ticket(command.ownerUid, requireNotNull(command.ticketId)) ?: throw ArenaJournalConflict()
        val retained = ticketCommand(old.ownerUid, old.ticketId, "SETTLE") ?: throw ArenaJournalConflict()
        val marker = intent(old.attemptId)
        val pending = queued(firstIntent.mutationId)
        val hasAcknowledgment = hasAnyReceipt(firstIntent.mutationId)
        validateArenaCancellationIdentity(old, retained, command, firstIntent, observation)
        validateRetainedIntent(marker, firstIntent)
        validateRetainedIntent(pending?.attemptIntent(old.attemptId), firstIntent)
        requireUnacknowledgedCancellation(firstIntent, pending, hasAcknowledgment)
        validator.validate(old, retained, firstIntent, observation)
        if (validateArenaCancellationReplay(old, retained, pending)) return
        if (marker == null) insertIntent(firstIntent)
        updateTicket(mergeObservation(old, observation, null))
        updateCommand(retained.copy(state = ARENA_RESOLVED_CANCELLED))
        if (pending != null) deleteValidatedCancelledPending(pending)
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

private suspend fun ArenaTicketDao.requireUnacknowledgedCancellation(
    first: LessonAttemptIntentEntity,
    pending: OutboxEntity?,
    hasAcknowledgment: Boolean,
) {
    if (hasAcknowledgment || hasScoredRows(first.attemptId)) throw ArenaJournalConflict("Attempt has another outcome")
    if (pending != null && pending.state !in setOf("WAITING", "WAITING_PRECONDITION")) throw ArenaJournalConflict()
}

private suspend fun ArenaTicketDao.deleteValidatedCancelledPending(pending: OutboxEntity) {
    val count =
        deleteCancelledPending(
            pending.id,
            pending.ownerUid,
            pending.mutationId,
            pending.operation,
            pending.payload,
            pending.entityRef,
            pending.state,
        )
    if (count != 1) throw ArenaJournalConflict("Pending delivery changed before cancellation commit")
}

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
    val unused = localUse == "UNUSED" && checkpointJson == null && checkpointRevision == 0L
    return canWriteCheckpoint() && unused
}

/** New writes only; terminal replay and bank-independent cancellation deliberately do not use this guard. */
private fun ArenaTicketRecordEntity.canWriteCheckpoint(): Boolean {
    val active = serverStatus == "RESERVED" && decision == "NONE" && terminalJson == null
    val hasSnapshot = snapshotJson != null && snapshotSha256 != null
    val validClockAndRevision = observedAtMs >= 0 && checkpointRevision >= 0
    return active && hasSnapshot && validClockAndRevision
}

private fun ArenaTicketRecordEntity.canSaveCheckpoint(
    attempt: String,
    revision: Long,
): Boolean {
    val identityMatches = attemptId == attempt && checkpointRevision == revision
    val active = canWriteCheckpoint() && localUse == "LAUNCHED"
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
    if (!ticket.canWriteCheckpoint() || ticket.localUse != "LAUNCHED") {
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
