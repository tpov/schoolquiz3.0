package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.persistence.ArenaCheckpointBinding
import com.tpov.schoolquiz.shared.core.persistence.ArenaCheckpointValidator
import com.tpov.schoolquiz.shared.core.persistence.ArenaCommandEntity
import com.tpov.schoolquiz.shared.core.persistence.ArenaJournalConflict
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketRecordEntity
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancelResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryTicket
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaInitialCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketCancelRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketListRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class JournalGateFake : ArenaOwnerGate {
    var current = OutboxSession("player", 1)
    private val mutex = Mutex()

    override fun capture(owner: String): OutboxSession {
        if (current.ownerUid != owner) changed()
        return current
    }

    override suspend fun <T> withOwner(
        session: OutboxSession,
        block: suspend () -> T,
    ): T =
        mutex.withLock {
            if (current != session) changed()
            block().also { if (current != session) changed() }
        }

    suspend fun switch(owner: String) = mutex.withLock { current = OutboxSession(owner, current.generation + 1) }

    private fun changed(): Nothing = throw ArenaRemoteFailure(ArenaRemoteFailureKind.ACCOUNT_CHANGED)
}

internal class JournalRemoteFake : ArenaTicketRemoteDataSource {
    var prepareAction: suspend (ArenaPrepareRequest) -> Result<ArenaPrepareResponse> = { error("Unexpected prepare") }
    var cancelAction: suspend (ArenaTicketCancelRequest) -> Result<ArenaCancelResponse> = { error("Unexpected cancel") }
    var getAction: suspend (ArenaTicketGetRequest) -> Result<ArenaEntryTicket> = { error("Unexpected get") }
    var listAction: suspend (ArenaTicketListRequest) -> Result<ArenaTicketPage> = { error("Unexpected list") }
    val prepares = mutableListOf<ArenaPrepareRequest>()
    val cancellations = mutableListOf<ArenaTicketCancelRequest>()

    override suspend fun prepare(request: ArenaPrepareRequest): Result<ArenaPrepareResponse> {
        prepares += request
        return prepareAction(request)
    }

    override suspend fun cancel(request: ArenaTicketCancelRequest): Result<ArenaCancelResponse> {
        cancellations += request
        return cancelAction(request)
    }

    override suspend fun get(request: ArenaTicketGetRequest) = getAction(request)

    override suspend fun list(request: ArenaTicketListRequest) = listAction(request)
}

/** Coordinator fake only. SQL atomicity/uniqueness/migration are tested independently with real Room. */
internal class JournalStorageFake : ArenaJournalStorage {
    val tickets = mutableMapOf<Pair<String, String>, ArenaTicketRecordEntity>()
    val commands = mutableMapOf<Triple<String, String, String>, ArenaCommandEntity>()
    val outbox = mutableMapOf<String, OutboxEntity>()
    var failAcknowledgment = false

    override suspend fun ticket(
        owner: String,
        ticket: String,
    ) = tickets[owner to ticket]

    override suspend fun command(
        owner: String,
        kind: String,
        id: String,
    ) = commands[Triple(owner, kind, id)]

    override suspend fun ticketCommand(
        owner: String,
        ticket: String,
        kind: String,
    ) = commands.values.singleOrNull { it.ownerUid == owner && it.ticketId == ticket && it.commandKind == kind }

    override suspend fun saveConsent(command: ArenaCommandEntity) {
        val key = Triple(command.ownerUid, command.commandKind, command.commandId)
        val old = commands[key]
        if (old != null && (old.bodyJson != command.bodyJson || old.bodySha256 != command.bodySha256)) {
            throw ArenaJournalConflict()
        }
        if (old == null) commands[key] = command
    }

    override suspend fun observe(row: ArenaTicketRecordEntity) {
        val key = row.ownerUid to row.ticketId
        val old = tickets[key]
        if (old == null) {
            tickets[key] = row
            return
        }
        if (old.termsJson != row.termsJson) throw ArenaJournalConflict()
        tickets[key] =
            row.copy(
                snapshotJson = old.snapshotJson ?: row.snapshotJson,
                snapshotSha256 = old.snapshotSha256 ?: row.snapshotSha256,
                serverStatus = if (old.serverStatus == "RESERVED") row.serverStatus else old.serverStatus,
                terminalJson = old.terminalJson ?: row.terminalJson,
                localUse = old.localUse,
                decision = old.decision,
                checkpointJson = old.checkpointJson,
                checkpointRevision = old.checkpointRevision,
            )
    }

    override suspend fun acknowledge(
        row: ArenaTicketRecordEntity,
        kind: String,
        id: String,
    ) {
        if (failAcknowledgment) error("database unavailable")
        val key = Triple(row.ownerUid, kind, id)
        val cmd = commands.getValue(key)
        observe(row)
        commands[key] = cmd.copy(state = "ACKNOWLEDGED", lastFailure = null)
    }

    override suspend fun failure(
        owner: String,
        kind: String,
        id: String,
        reason: String,
        attention: Boolean,
    ) {
        val key = Triple(owner, kind, id)
        val old = commands.getValue(key)
        if (old.state != "ACKNOWLEDGED") {
            commands[key] =
                old.copy(
                    state = if (attention) "ATTENTION" else old.state,
                    lastFailure = reason,
                )
        }
    }

    override suspend fun claim(
        owner: String,
        ticket: String,
        checkpoint: String,
        validator: ArenaCheckpointValidator,
    ): Boolean {
        val key = owner to ticket
        val row = tickets.getValue(key)
        if (row.serverStatus != "RESERVED" || row.decision != "NONE" ||
            row.localUse != "UNUSED" || row.snapshotJson == null || row.checkpointJson != null
        ) {
            return false
        }
        validator.validate(row.binding(), null, checkpoint, null)
        tickets[key] = row.copy(localUse = "LAUNCHED", checkpointJson = checkpoint)
        return true
    }

    override suspend fun saveCheckpoint(
        owner: String,
        ticket: String,
        attemptId: String,
        expectedRevision: Long,
        candidate: String,
        validator: ArenaCheckpointValidator,
    ): Boolean {
        val key = owner to ticket
        val row = tickets[key] ?: return false
        if (row.attemptId != attemptId || row.checkpointRevision != expectedRevision ||
            row.serverStatus != "RESERVED" || row.decision != "NONE" || row.localUse != "LAUNCHED"
        ) {
            return false
        }
        val previous = row.checkpointJson ?: return false
        validator.validate(row.binding(), previous, candidate, null)
        tickets[key] = row.copy(checkpointJson = candidate, checkpointRevision = expectedRevision + 1)
        return true
    }

    override suspend fun chooseCancel(command: ArenaCommandEntity) {
        val key = command.ownerUid to requireNotNull(command.ticketId)
        val row = tickets.getValue(key)
        val old = ticketCommand(command.ownerUid, key.second, "CANCEL")
        if (old != null) {
            if (old.commandId != command.commandId || old.bodyJson != command.bodyJson) throw ArenaJournalConflict()
            return
        }
        if (row.serverStatus != "RESERVED" || row.decision != "NONE") throw ArenaJournalConflict()
        commands[Triple(command.ownerUid, "CANCEL", command.commandId)] = command
        tickets[key] = row.copy(decision = "CANCEL")
    }

    override suspend fun freezeSettlement(
        command: ArenaCommandEntity,
        row: OutboxEntity,
        validator: ArenaCheckpointValidator,
    ) {
        val key = command.ownerUid to requireNotNull(command.ticketId)
        val ticket = tickets.getValue(key)
        if (ticket.localUse != "LAUNCHED" || ticket.decision != "NONE") throw ArenaJournalConflict()
        validator.validate(ticket.binding(), ticket.checkpointJson, requireNotNull(ticket.checkpointJson), row.payload)
        commands[Triple(command.ownerUid, "SETTLE", command.commandId)] = command
        outbox[row.mutationId] = row
        tickets[key] = ticket.copy(decision = "SETTLE")
    }

    override suspend fun restoreSettlement(
        owner: String,
        ticket: String,
        row: OutboxEntity,
    ) {
        val cmd = ticketCommand(owner, ticket, "SETTLE") ?: error("missing body")
        require(cmd.bodyJson == row.payload && cmd.ownerUid == row.ownerUid)
        outbox.putIfAbsent(row.mutationId, row)
    }

    override suspend fun confirmSettlementReplay(
        command: ArenaCommandEntity,
        row: OutboxEntity,
    ) {
        val ticket = tickets.getValue(command.ownerUid to requireNotNull(command.ticketId))
        val old = ticketCommand(command.ownerUid, ticket.ticketId, "SETTLE") ?: throw ArenaJournalConflict()
        require(ticket.decision == "SETTLE" && old.ownerUid == command.ownerUid)
        require(old.commandId == command.commandId && old.bodyJson == command.bodyJson)
        require(old.bodySha256 == command.bodySha256 && old.bodyJson == row.payload)
        outbox[row.mutationId]?.let { require(it.payload == row.payload && it.ownerUid == row.ownerUid) }
    }

    override suspend fun evictSnapshot(
        owner: String,
        ticket: String,
    ): Boolean {
        val key = owner to ticket
        val row = tickets.getValue(key)
        if (row.serverStatus == "RESERVED" && (row.localUse == "LAUNCHED" || row.decision != "NONE")) return false
        tickets[key] =
            row.copy(
                snapshotJson = null,
                checkpointJson = if (row.serverStatus == "RESERVED") row.checkpointJson else null,
            )
        return true
    }
}

private fun ArenaTicketRecordEntity.binding() =
    ArenaCheckpointBinding(ownerUid, ticketId, attemptId, requireNotNull(snapshotSha256))

/** Deliberately tiny test codec. Production must inject the separately tested typed runner codec. */
internal class JournalCheckpointFake : ArenaJournalCheckpointCodec {
    var reject = false

    override fun initial(
        snapshot: ArenaTicketSnapshot,
        snapshotSha256: String,
        request: ArenaInitialCheckpoint,
    ) = "0"

    override fun validate(
        snapshot: ArenaTicketSnapshot,
        snapshotSha256: String,
        previousJson: String?,
        candidateJson: String,
        completionPayloadJson: String?,
    ) {
        check(!reject)
        val index = candidateJson.toInt()
        require(index in 0..1)
        if (previousJson == null) require(index == 0) else require(index >= previousJson.toInt())
        if (completionPayloadJson != null) require(index == 1)
    }
}
