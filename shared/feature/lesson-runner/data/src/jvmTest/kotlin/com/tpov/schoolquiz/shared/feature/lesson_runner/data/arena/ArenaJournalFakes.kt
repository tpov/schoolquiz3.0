package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.persistence.ARENA_RESOLVED_CANCELLED
import com.tpov.schoolquiz.shared.core.persistence.ArenaCancellationValidator
import com.tpov.schoolquiz.shared.core.persistence.ArenaCheckpointBinding
import com.tpov.schoolquiz.shared.core.persistence.ArenaCheckpointValidator
import com.tpov.schoolquiz.shared.core.persistence.ArenaCommandEntity
import com.tpov.schoolquiz.shared.core.persistence.ArenaJournalConflict
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketRecordEntity
import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptIntentEntity
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
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    val boundSessions = mutableListOf<OutboxSession>()
    var beforeBoundCall: suspend (OutboxSession) -> Unit = {}
    val prepares = mutableListOf<ArenaPrepareRequest>()
    val cancellations = mutableListOf<ArenaTicketCancelRequest>()

    override suspend fun prepare(request: ArenaPrepareRequest): Result<ArenaPrepareResponse> =
        error(
            "Plain path is forbidden for journal tests",
        )

    override suspend fun prepare(
        expected: OutboxSession,
        request: ArenaPrepareRequest,
    ): Result<ArenaPrepareResponse> {
        require(expected.ownerUid == request.ownerUid)
        boundSessions += expected
        beforeBoundCall(expected)
        prepares += request
        return prepareAction(request)
    }

    override suspend fun cancel(request: ArenaTicketCancelRequest): Result<ArenaCancelResponse> =
        error(
            "Plain path is forbidden for journal tests",
        )

    override suspend fun cancel(
        expected: OutboxSession,
        request: ArenaTicketCancelRequest,
    ): Result<ArenaCancelResponse> {
        require(expected.ownerUid == request.ticket.ownerUid)
        boundSessions += expected
        beforeBoundCall(expected)
        cancellations += request
        return cancelAction(request)
    }

    override suspend fun get(request: ArenaTicketGetRequest): Result<ArenaEntryTicket> =
        error("Plain path is forbidden for journal tests")

    override suspend fun get(
        expected: OutboxSession,
        request: ArenaTicketGetRequest,
    ): Result<ArenaEntryTicket> {
        require(expected.ownerUid == request.ownerUid)
        boundSessions += expected
        beforeBoundCall(expected)
        return getAction(request)
    }

    override suspend fun list(request: ArenaTicketListRequest): Result<ArenaTicketPage> =
        error("Plain path is forbidden for journal tests")

    override suspend fun list(
        expected: OutboxSession,
        request: ArenaTicketListRequest,
    ): Result<ArenaTicketPage> {
        require(expected.ownerUid == request.ownerUid)
        boundSessions += expected
        beforeBoundCall(expected)
        return listAction(request)
    }
}

/** Coordinator fake only. SQL atomicity/uniqueness/migration are tested independently with real Room. */
internal class JournalStorageFake : ArenaJournalStorage {
    var ticketPageReads = 0
    var preparePageReads = 0
    val localPageFetchLimits = mutableListOf<Int>()

    override suspend fun ticketPage(
        owner: String,
        pageSize: Int,
        cursor: ArenaLocalTicketCursor?,
    ): ArenaLocalTicketPage {
        val read = localTicketPageRead(owner, pageSize, cursor)
        ticketPageReads++
        localPageFetchLimits += read.fetchLimit
        val rows =
            tickets.values.filter { it.ownerUid == read.ownerUid }
                .filter { isAfterArenaLocalId(it.ticketId, read.afterId) }
                .sortedWith(Comparator { left, right -> compareArenaSqliteBinary(left.ticketId, right.ticketId) })
                .take(read.fetchLimit)
        return localTicketPage(read, rows)
    }

    override suspend fun pendingPreparePage(
        owner: String,
        pageSize: Int,
        cursor: ArenaLocalPrepareCursor?,
    ): ArenaLocalPreparePage {
        val read = localPreparePageRead(owner, pageSize, cursor)
        preparePageReads++
        localPageFetchLimits += read.fetchLimit
        val rows =
            commands.values.filter { it.ownerUid == read.ownerUid && it.commandKind == "PREPARE" }
                .filter { it.state in setOf("PENDING", "ATTENTION") }
                .filter { isAfterArenaLocalId(it.commandId, read.afterId) }
                .sortedWith(Comparator { left, right -> compareArenaSqliteBinary(left.commandId, right.commandId) })
                .take(read.fetchLimit)
        return localPreparePage(read, rows)
    }

    val tickets = mutableMapOf<Pair<String, String>, ArenaTicketRecordEntity>()
    val commands = mutableMapOf<Triple<String, String, String>, ArenaCommandEntity>()
    val outbox = mutableMapOf<String, OutboxEntity>()
    val firstIntents = mutableMapOf<String, LessonAttemptIntentEntity>()

    // A receipt for this mutation blocks resolution regardless of its owner or opaque result body.
    val acknowledgedMutations = mutableSetOf<String>()
    val scoredAttempts = mutableSetOf<String>()
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
        if (cmd.state == ARENA_RESOLVED_CANCELLED) throw ArenaJournalConflict()
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
        if (old.state != "ACKNOWLEDGED" && old.state != ARENA_RESOLVED_CANCELLED) {
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
        val unused = row.localUse == "UNUSED" && row.checkpointJson == null && row.checkpointRevision == 0L
        if (!row.canWriteFakeCheckpoint() || !unused) return false
        row.validateFakeCheckpoint(checkpoint, null, validator)
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
        require(expectedRevision >= 0 && expectedRevision < Long.MAX_VALUE)
        val key = owner to ticket
        val row = tickets[key]
        val previous = row?.checkpointJson
        val identityMatches = row != null && row.attemptId == attemptId && row.checkpointRevision == expectedRevision
        val active = row != null && row.canWriteFakeCheckpoint() && row.localUse == "LAUNCHED"
        return if (row != null && previous != null && identityMatches && active) {
            row.validateFakeCheckpoint(candidate, previous, validator)
            tickets[key] = row.copy(checkpointJson = candidate, checkpointRevision = expectedRevision + 1)
            true
        } else {
            false
        }
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
        if (!ticket.canWriteFakeCheckpoint() || ticket.localUse != "LAUNCHED") throw ArenaJournalConflict()
        ticket.validateFakeCheckpoint(
            requireNotNull(ticket.checkpointJson),
            ticket.checkpointJson,
            validator,
            row.payload,
        )
        commands[Triple(command.ownerUid, "SETTLE", command.commandId)] = command
        firstIntents[ticket.attemptId] = row.frozenIntent(ticket.attemptId)
        outbox[row.mutationId] = row
        tickets[key] = ticket.copy(decision = "SETTLE")
    }

    override suspend fun restoreSettlement(
        owner: String,
        ticket: String,
        row: OutboxEntity,
    ) {
        val cmd = ticketCommand(owner, ticket, "SETTLE") ?: error("missing body")
        if (tickets.getValue(owner to ticket).serverStatus != "RESERVED" || cmd.state != "PENDING") return
        require(cmd.bodyJson == row.payload && cmd.ownerUid == row.ownerUid)
        outbox.putIfAbsent(row.mutationId, row)
    }

    override suspend fun resolveCancelledSettlement(
        command: ArenaCommandEntity,
        firstIntent: LessonAttemptIntentEntity,
        observation: ArenaTicketRecordEntity,
        validator: ArenaCancellationValidator,
    ) {
        val key = command.ownerUid to requireNotNull(command.ticketId)
        val row = tickets[key] ?: throw ArenaJournalConflict()
        val retained = ticketCommand(key.first, key.second, "SETTLE") ?: throw ArenaJournalConflict()
        val marker = firstIntents[row.attemptId]
        val pending = outbox[firstIntent.mutationId]
        validateFakeCancellation(row, retained, command, firstIntent, observation)
        require(marker == null || marker == firstIntent)
        require(pending == null || pending.frozenIntent(row.attemptId) == firstIntent)
        require(firstIntent.mutationId !in acknowledgedMutations && row.attemptId !in scoredAttempts)
        require(pending == null || pending.state in setOf("WAITING", "WAITING_PRECONDITION"))
        validator.validate(row, retained, firstIntent, observation)
        if (retained.state == ARENA_RESOLVED_CANCELLED) {
            require(row.serverStatus == "CANCELLED" && pending == null)
            return
        }
        // All checks (including the required proof validator) precede any backing-state mutation.
        if (marker == null) firstIntents[row.attemptId] = firstIntent
        tickets[key] =
            row.copy(
                serverStatus = "CANCELLED",
                terminalJson = observation.terminalJson,
                observedAtMs = maxOf(row.observedAtMs, observation.observedAtMs),
            )
        commands[
            Triple(
                retained.ownerUid,
                "SETTLE",
                retained.commandId,
            ),
        ] = retained.copy(state = ARENA_RESOLVED_CANCELLED)
        if (pending != null) outbox.remove(pending.mutationId)
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
    ArenaCheckpointBinding(
        ownerUid,
        ticketId,
        attemptId,
        requireNotNull(snapshotSha256),
        requestId,
        termsJson,
        requireNotNull(snapshotJson),
    )

/** Same first-write guards as the Room DAO, not a substitute for the required typed proof validator. */
private fun ArenaTicketRecordEntity.canWriteFakeCheckpoint(): Boolean {
    val active = serverStatus == "RESERVED" && decision == "NONE" && terminalJson == null
    val hasSnapshot = snapshotJson != null && snapshotSha256 != null
    return active && hasSnapshot && observedAtMs >= 0 && checkpointRevision >= 0
}

private fun ArenaTicketRecordEntity.validateFakeCheckpoint(
    candidate: String,
    previous: String?,
    validator: ArenaCheckpointValidator,
    completion: String? = null,
) {
    require(candidate.encodeToByteArray().size in 1..1_048_576)
    require(checkpointRevision >= 0)
    validator.validate(binding(), previous, candidate, completion)
}

/** SQLite BINARY compares unsigned UTF-8 bytes, not Kotlin's UTF-16 ordering for supplementary IDs. */
private fun isAfterArenaLocalId(
    id: String,
    anchor: String?,
): Boolean = anchor == null || compareArenaSqliteBinary(id, anchor) > 0

private fun compareArenaSqliteBinary(
    left: String,
    right: String,
): Int {
    val a = left.encodeToByteArray()
    val b = right.encodeToByteArray()
    for (index in 0 until minOf(a.size, b.size)) {
        val order = (a[index].toInt() and 0xff).compareTo(b[index].toInt() and 0xff)
        if (order != 0) return order
    }
    return a.size.compareTo(b.size)
}

private fun OutboxEntity.frozenIntent(attempt: String) =
    LessonAttemptIntentEntity(attempt, ownerUid, mutationId, operation, payload, entityRef, expectedVersion)

private fun validateFakeCancellation(
    row: ArenaTicketRecordEntity,
    retained: ArenaCommandEntity,
    command: ArenaCommandEntity,
    intent: LessonAttemptIntentEntity,
    observation: ArenaTicketRecordEntity,
) {
    require(retained == command.copy(state = retained.state, lastFailure = retained.lastFailure))
    require(retained.ownerUid == row.ownerUid && retained.ticketId == row.ticketId && retained.commandKind == "SETTLE")
    require(retained.commandId == row.attemptId && retained.attemptId == row.attemptId)
    require(retained.state in setOf("PENDING", "ATTENTION", ARENA_RESOLVED_CANCELLED))
    require(row.decision == "SETTLE" && row.localUse == "LAUNCHED")
    require(intent.ownerUid == row.ownerUid && intent.attemptId == row.attemptId && intent.payload == retained.bodyJson)
    require(intent.operation == OutboxOperations.SUBMIT_ATTEMPT)
    require(intent.mutationId == OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, row.attemptId))
    require(intent.entityRef == "lesson_runner:attempt:${row.attemptId}" && intent.expectedVersion == null)
    validateFakeCancellationObservation(row, observation)
}

private fun validateFakeCancellationObservation(
    row: ArenaTicketRecordEntity,
    observation: ArenaTicketRecordEntity,
) {
    require(observation.ownerUid == row.ownerUid && observation.ticketId == row.ticketId)
    require(observation.attemptId == row.attemptId && observation.requestId == row.requestId)
    require(observation.termsJson == row.termsJson && observation.serverStatus == "CANCELLED")
    require(observation.snapshotJson == null && observation.snapshotSha256 == null)
    require(observation.localUse == "UNUSED" && observation.decision == "NONE")
    require(observation.checkpointJson == null && observation.checkpointRevision == 0L && observation.observedAtMs >= 0)
    require(!observation.terminalJson.isNullOrBlank())
    when (row.serverStatus) {
        "RESERVED" -> require(row.terminalJson == null)
        "CANCELLED" -> require(row.terminalJson == observation.terminalJson)
        else -> throw ArenaJournalConflict()
    }
}

/** Keeps the canonical fake's new transactional constraint observable, independently of Room tests. */
class JournalCancellationStorageFakeTest {
    private val ticket =
        ArenaTicketRecordEntity(
            "A", "ticket", "attempt", "request", "terms", "bank", "sha", "RESERVED", null,
            "LAUNCHED", "SETTLE", 10, "checkpoint", 2,
        )
    private val command =
        ArenaCommandEntity("A", "SETTLE", "attempt", "ticket", "attempt", "body", "hash", "PENDING", null, 20)
    private val pending =
        OutboxEntity(
            mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, "attempt"), ownerUid = "A",
            operation = OutboxOperations.SUBMIT_ATTEMPT, payload = "body", entityRef = "lesson_runner:attempt:attempt",
            expectedVersion = null, state = "WAITING", attemptCount = 2,
            nextRetryAtMs = 900, lastError = "retry", createdAtMs = 20,
        )
    private val first = pending.frozenIntent("attempt")
    private val observation =
        ticket.copy(
            snapshotJson = null, snapshotSha256 = null, serverStatus = "CANCELLED", terminalJson = "bound-proof",
            localUse = "UNUSED", decision = "NONE", observedAtMs = 30, checkpointJson = null, checkpointRevision = 0,
        )
    private val validator =
        ArenaCancellationValidator { actual, retained, marker, proof ->
            require(actual.ticketId == "ticket" && retained.bodyJson == "body" && marker == first)
            require(proof.terminalJson == "bound-proof")
        }

    private fun storage() =
        JournalStorageFake().apply {
            tickets["A" to "ticket"] = ticket
            commands[Triple("A", "SETTLE", "attempt")] = command
            outbox[first.mutationId] = pending
        }

    private fun snapshot(storage: JournalStorageFake) =
        listOf(storage.tickets.toMap(), storage.commands.toMap(), storage.outbox.toMap(), storage.firstIntents.toMap())

    @Test
    fun fakeResolutionRetainsFirstBodyAndDoesNotResendAfterCleanupOrLateFailure() =
        runTest {
            val storage = storage()
            storage.resolveCancelledSettlement(command, first, observation, validator)
            assertEquals(first, storage.firstIntents["attempt"])
            assertNull(storage.outbox[first.mutationId])
            storage.firstIntents.clear() // Ordinary switch can erase legacy tables but not the Arena journal.
            val before = snapshot(storage)
            storage.resolveCancelledSettlement(command, first, observation.copy(observedAtMs = 900), validator)
            storage.failure("A", "SETTLE", "attempt", "TICKET_CANCELLED", true)
            storage.restoreSettlement("A", "ticket", pending)
            assertEquals(before, snapshot(storage))
            assertTrue(runCatching { storage.acknowledge(observation, "SETTLE", "attempt") }.isFailure)
            assertEquals(before, snapshot(storage))
        }

    @Test
    fun fakeRejectionPreservesAllBackingStateForMarkerQueueAckAndProofConflicts() =
        runTest {
            val changes: List<(JournalStorageFake) -> Unit> =
                listOf(
                    { it.firstIntents["attempt"] = first.copy(payload = "other") },
                    { it.outbox[first.mutationId] = pending.copy(ownerUid = "B") },
                    { it.outbox[first.mutationId] = pending.copy(payload = "other") },
                    { it.outbox[first.mutationId] = pending.copy(state = "QUARANTINED") },
                    { it.acknowledgedMutations += first.mutationId },
                    { it.scoredAttempts += "attempt" },
                )
            for (change in changes) {
                val storage = storage().also(change)
                val before = snapshot(storage)
                assertTrue(
                    runCatching {
                        storage.resolveCancelledSettlement(
                            command,
                            first,
                            observation,
                            validator,
                        )
                    }.isFailure,
                )
                assertEquals(before, snapshot(storage))
            }
            val storage = storage()
            val before = snapshot(storage)
            assertTrue(
                runCatching {
                    storage.resolveCancelledSettlement(
                        command,
                        first,
                        observation.copy(terminalJson = "unproven"),
                        validator,
                    )
                }.isFailure,
            )
            assertEquals(before, snapshot(storage))
        }
}

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

/** Local-only page contract and canonical fake order; SQL behaviour is covered in the persistence module. */
class ArenaLocalPageStorageTest {
    private fun ticket(
        id: String,
        owner: String = "A",
        status: String = "RESERVED",
    ) = ArenaTicketRecordEntity(
        owner, id, "attempt-$id", "request-$id", "not parsed", null, null, status, "not parsed terminal",
        "LAUNCHED", "SETTLE", 100, "checkpoint unchanged", 9,
    )

    private fun prepare(
        id: String,
        owner: String = "A",
        state: String = "PENDING",
    ) = ArenaCommandEntity(owner, "PREPARE", id, null, null, "{not parsed", "body-hash", state, "failure", 100)

    private fun seed(
        storage: JournalStorageFake,
        ids: List<String>,
        owner: String = "A",
    ) {
        for (id in ids.reversed()) {
            storage.tickets[owner to id] = ticket(id, owner)
            storage.commands[Triple(owner, "PREPARE", id)] = prepare(id, owner)
        }
    }

    @Test
    fun invalidBoundsBlankOwnersAndForeignCursorsFailBeforeReading() =
        runTest {
            val storage = JournalStorageFake()
            for (size in listOf(Int.MIN_VALUE, -1, 0, 51, Int.MAX_VALUE)) {
                assertTrue(runCatching { storage.ticketPage("A", size) }.isFailure)
                assertTrue(runCatching { storage.pendingPreparePage("A", size) }.isFailure)
            }
            assertTrue(runCatching { storage.ticketPage(" ", 1) }.isFailure)
            assertTrue(runCatching { storage.pendingPreparePage("", 50) }.isFailure)
            assertEquals(0, storage.ticketPageReads)
            assertEquals(0, storage.preparePageReads)
            seed(storage, listOf("a", "b"))
            val tickets = storage.ticketPage("A", 1)
            val prepares = storage.pendingPreparePage("A", 1)
            val ticketCursor = assertNotNull(tickets.nextCursor)
            val prepareCursor = assertNotNull(prepares.nextCursor)
            assertTrue(runCatching { storage.ticketPage("B", 1, ticketCursor) }.isFailure)
            assertTrue(runCatching { storage.pendingPreparePage("B", 1, prepareCursor) }.isFailure)
            assertEquals(1, storage.ticketPageReads)
            assertEquals(1, storage.preparePageReads)
            assertEquals(listOf(2, 2), storage.localPageFetchLimits)
        }

    @Test
    fun lookaheadAnchorsLastVisibleRowAndContinuesBeyondFiftyWithoutDuplicatesOrWrites() =
        runTest {
            val storage = JournalStorageFake()
            val expected = (0 until 103).map { "id-" + it.toString().padStart(3, '0') }
            seed(storage, expected)
            seed(storage, expected, "B")
            val beforeTickets = storage.tickets.toMap()
            val beforeCommands = storage.commands.toMap()
            val seenTickets = mutableListOf<String>()
            val seenPrepares = mutableListOf<String>()
            var ticketCursor: ArenaLocalTicketCursor? = null
            var prepareCursor: ArenaLocalPrepareCursor? = null
            repeat(3) { pageIndex ->
                val tickets = storage.ticketPage("A", 50, ticketCursor)
                val prepares = storage.pendingPreparePage("A", 50, prepareCursor)
                assertTrue(tickets.rows.size <= 50 && prepares.rows.size <= 50)
                seenTickets += tickets.rows.map { it.ticketId }
                seenPrepares += prepares.rows.map { it.commandId }
                ticketCursor = tickets.nextCursor
                prepareCursor = prepares.nextCursor
                if (pageIndex == 0) {
                    assertEquals("id-049", tickets.nextCursor!!.afterTicketId)
                    assertEquals("id-049", prepares.nextCursor!!.afterCommandId)
                }
            }
            assertEquals(expected, seenTickets)
            assertEquals(expected, seenPrepares)
            assertEquals(103, seenTickets.toSet().size)
            assertEquals(103, seenPrepares.toSet().size)
            assertNull(ticketCursor)
            assertNull(prepareCursor)
            assertEquals(3, storage.ticketPageReads)
            assertEquals(3, storage.preparePageReads)
            assertEquals(List(6) { 51 }, storage.localPageFetchLimits)
            assertEquals(beforeTickets, storage.tickets)
            assertEquals(beforeCommands, storage.commands)
            assertTrue(
                storage.outbox.isEmpty() && storage.firstIntents.isEmpty() && storage.acknowledgedMutations.isEmpty(),
            )
        }

    @Test
    fun canonicalFakeUsesSqliteUtf8RatherThanUtf16AcrossNonAsciiPageBoundaries() =
        runTest {
            val storage = JournalStorageFake()
            val expected = listOf("a", "z", "é", "\uE000", "\uD800\uDC00", "\uD83D\uDE00")
            assertNotEquals(expected, expected.sorted())
            seed(storage, expected)
            val seenTickets = mutableListOf<String>()
            val seenPrepares = mutableListOf<String>()
            var ticketCursor: ArenaLocalTicketCursor? = null
            var prepareCursor: ArenaLocalPrepareCursor? = null
            repeat(expected.size) {
                val tickets = storage.ticketPage("A", 1, ticketCursor)
                val prepares = storage.pendingPreparePage("A", 1, prepareCursor)
                seenTickets += tickets.rows.single().ticketId
                seenPrepares += prepares.rows.single().commandId
                ticketCursor = tickets.nextCursor
                prepareCursor = prepares.nextCursor
            }
            assertEquals(expected, seenTickets)
            assertEquals(expected, seenPrepares)
            assertNull(ticketCursor)
            assertNull(prepareCursor)
        }

    @Test
    fun pendingKindAndStateFilterDoesNotHideTerminalTicketsOrParseOpaquePayloads() =
        runTest {
            val storage = JournalStorageFake()
            seed(storage, listOf("a", "b"))
            storage.tickets["A" to "a"] = ticket("a", status = "CANCELLED")
            storage.tickets["A" to "b"] = ticket("b", status = "SETTLED")
            storage.commands[Triple("A", "PREPARE", "b")] = prepare("b", state = "ATTENTION")
            for ((index, state) in listOf("ACKNOWLEDGED", "RESOLVED_CANCELLED", "QUARANTINED", "UNKNOWN").withIndex()) {
                storage.commands[Triple("A", "PREPARE", "skip-$index")] = prepare("skip-$index", state = state)
            }
            for (kind in listOf("CANCEL", "SETTLE", "UNKNOWN")) {
                storage.commands[Triple("A", kind, "a")] = prepare("a").copy(commandKind = kind)
            }
            seed(storage, listOf("a", "b"), "B")
            val beforeTickets = storage.tickets.toMap()
            val beforeCommands = storage.commands.toMap()
            val tickets = storage.ticketPage("A", 50)
            val prepares = storage.pendingPreparePage("A", 50)
            assertEquals(listOf("CANCELLED", "SETTLED"), tickets.rows.map { it.serverStatus })
            assertEquals(listOf("a", "b"), prepares.rows.map { it.commandId })
            assertEquals(listOf("PENDING", "ATTENTION"), prepares.rows.map { it.state })
            assertEquals(beforeTickets, storage.tickets)
            assertEquals(beforeCommands, storage.commands)
            assertTrue(storage.ticketPage("missing-owner", 50).rows.isEmpty())
            assertTrue(storage.pendingPreparePage("missing-owner", 50).rows.isEmpty())
        }

    @Test
    fun continuationSurvivesDeletedAnchorAndDoesNotClaimCompleteInventory() =
        runTest {
            val storage = JournalStorageFake()
            seed(storage, listOf("a", "b", "c"))
            val cursor = assertNotNull(storage.pendingPreparePage("A", 1).nextCursor)
            storage.commands.remove(Triple("A", "PREPARE", "a"))
            storage.commands[Triple("A", "PREPARE", "0-before")] = prepare("0-before")
            storage.commands[Triple("A", "PREPARE", "d")] = prepare("d")
            val next = storage.pendingPreparePage("A", 50, cursor)
            assertEquals(listOf("b", "c", "d"), next.rows.map { it.commandId })
            assertNull(next.nextCursor)
            assertEquals(
                listOf("0-before", "b", "c", "d"),
                storage.pendingPreparePage("A", 50).rows.map { it.commandId },
            )
        }
}
