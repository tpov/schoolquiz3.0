package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.persistence.ArenaCancellationValidator
import com.tpov.schoolquiz.shared.core.persistence.ArenaCheckpointValidator
import com.tpov.schoolquiz.shared.core.persistence.ArenaCommandEntity
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketDao
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketRecordEntity
import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptIntentEntity
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity

/**
 * Narrow fakeable transactional storage boundary, not a second delivery engine.
 * These operations belong to one atomic journal boundary; splitting ports would not reduce its contract.
 */
@Suppress("TooManyFunctions")
interface ArenaJournalStorage {
    /** Data-layer raw pages only; caller supplies the same owner-generation gate and maps rows for UI. */
    suspend fun ticketPage(
        owner: String,
        pageSize: Int,
        cursor: ArenaLocalTicketCursor? = null,
    ): ArenaLocalTicketPage

    suspend fun pendingPreparePage(
        owner: String,
        pageSize: Int,
        cursor: ArenaLocalPrepareCursor? = null,
    ): ArenaLocalPreparePage

    suspend fun ticket(
        owner: String,
        ticket: String,
    ): ArenaTicketRecordEntity?

    suspend fun command(
        owner: String,
        kind: String,
        id: String,
    ): ArenaCommandEntity?

    suspend fun ticketCommand(
        owner: String,
        ticket: String,
        kind: String,
    ): ArenaCommandEntity?

    suspend fun saveConsent(command: ArenaCommandEntity)

    suspend fun observe(row: ArenaTicketRecordEntity)

    suspend fun acknowledge(
        row: ArenaTicketRecordEntity,
        kind: String,
        id: String,
    )

    suspend fun failure(
        owner: String,
        kind: String,
        id: String,
        reason: String,
        attention: Boolean,
    )

    suspend fun claim(
        owner: String,
        ticket: String,
        checkpoint: String,
        validator: ArenaCheckpointValidator,
    ): Boolean

    suspend fun saveCheckpoint(
        owner: String,
        ticket: String,
        attemptId: String,
        expectedRevision: Long,
        candidate: String,
        validator: ArenaCheckpointValidator,
    ): Boolean

    suspend fun chooseCancel(command: ArenaCommandEntity)

    suspend fun freezeSettlement(
        command: ArenaCommandEntity,
        row: OutboxEntity,
        validator: ArenaCheckpointValidator,
    )

    suspend fun confirmSettlementReplay(
        command: ArenaCommandEntity,
        row: OutboxEntity,
    )

    suspend fun restoreSettlement(
        owner: String,
        ticket: String,
        row: OutboxEntity,
    )

    /** Requires the caller's captured owner-generation gate, with delivery passes excluded. */
    suspend fun resolveCancelledSettlement(
        command: ArenaCommandEntity,
        firstIntent: LessonAttemptIntentEntity,
        observation: ArenaTicketRecordEntity,
        validator: ArenaCancellationValidator,
    )

    suspend fun evictSnapshot(
        owner: String,
        ticket: String,
    ): Boolean
}

class RoomArenaJournalStorage(private val dao: ArenaTicketDao) : ArenaJournalStorage {
    override suspend fun ticketPage(
        owner: String,
        pageSize: Int,
        cursor: ArenaLocalTicketCursor?,
    ): ArenaLocalTicketPage {
        val read = localTicketPageRead(owner, pageSize, cursor)
        val rows = dao.selectLocalTickets(read.ownerUid, read.afterId, read.fetchLimit)
        return localTicketPage(read, rows)
    }

    override suspend fun pendingPreparePage(
        owner: String,
        pageSize: Int,
        cursor: ArenaLocalPrepareCursor?,
    ): ArenaLocalPreparePage {
        val read = localPreparePageRead(owner, pageSize, cursor)
        val rows = dao.selectLocalPendingPrepares(read.ownerUid, read.afterId, read.fetchLimit)
        return localPreparePage(read, rows)
    }

    override suspend fun ticket(
        owner: String,
        ticket: String,
    ) = dao.ticket(owner, ticket)

    override suspend fun command(
        owner: String,
        kind: String,
        id: String,
    ) = dao.command(owner, kind, id)

    override suspend fun ticketCommand(
        owner: String,
        ticket: String,
        kind: String,
    ) = dao.ticketCommand(
        owner,
        ticket,
        kind,
    )

    override suspend fun saveConsent(command: ArenaCommandEntity) = dao.saveConsent(command)

    override suspend fun observe(row: ArenaTicketRecordEntity) = dao.observe(row)

    override suspend fun acknowledge(
        row: ArenaTicketRecordEntity,
        kind: String,
        id: String,
    ) = dao.acknowledgeObservation(
        row,
        kind,
        id,
    )

    override suspend fun failure(
        owner: String,
        kind: String,
        id: String,
        reason: String,
        attention: Boolean,
    ) = dao.recordFailure(owner, kind, id, reason, attention)

    override suspend fun claim(
        owner: String,
        ticket: String,
        checkpoint: String,
        validator: ArenaCheckpointValidator,
    ) = dao.claim(owner, ticket, checkpoint, validator)

    override suspend fun saveCheckpoint(
        owner: String,
        ticket: String,
        attemptId: String,
        expectedRevision: Long,
        candidate: String,
        validator: ArenaCheckpointValidator,
    ) = dao.saveCheckpoint(owner, ticket, attemptId, expectedRevision, candidate, validator)

    override suspend fun chooseCancel(command: ArenaCommandEntity) = dao.chooseCancel(command)

    override suspend fun freezeSettlement(
        command: ArenaCommandEntity,
        row: OutboxEntity,
        validator: ArenaCheckpointValidator,
    ) = dao.freezeSettlement(
        command,
        row,
        validator,
    )

    override suspend fun restoreSettlement(
        owner: String,
        ticket: String,
        row: OutboxEntity,
    ) = dao.restoreSettlement(
        owner,
        ticket,
        row,
    )

    override suspend fun confirmSettlementReplay(
        command: ArenaCommandEntity,
        row: OutboxEntity,
    ) = dao.confirmSettlementReplay(command, row)

    override suspend fun resolveCancelledSettlement(
        command: ArenaCommandEntity,
        firstIntent: LessonAttemptIntentEntity,
        observation: ArenaTicketRecordEntity,
        validator: ArenaCancellationValidator,
    ) = dao.resolveCancelledSettlement(command, firstIntent, observation, validator)

    override suspend fun evictSnapshot(
        owner: String,
        ticket: String,
    ) = dao.evictSnapshot(owner, ticket)
}
