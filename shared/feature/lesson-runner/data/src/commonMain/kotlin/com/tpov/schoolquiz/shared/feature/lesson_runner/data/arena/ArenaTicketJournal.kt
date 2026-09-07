package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.persistence.ArenaCheckpointBinding
import com.tpov.schoolquiz.shared.core.persistence.ArenaCheckpointValidator
import com.tpov.schoolquiz.shared.core.persistence.ArenaCommandEntity
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketRecordEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancelResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaInitialCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketListRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UnscoredTerminalIntent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/**
 * Explicit one-shot operations only. No scheduler, default consent, automatic purchase, ledger,
 * quota restoration, live hierarchy lookup or second settlement transport is hidden here.
 * Callers capture a session at user intent time and keep that lease through every suspension.
 * The constructor keeps every required port explicit; no optional dependency or fallback is safe here.
 */
@Suppress("LongParameterList")
class ArenaTicketJournal(
    private val storage: ArenaJournalStorage,
    private val remote: ArenaTicketRemoteDataSource,
    private val gate: ArenaOwnerGate,
    private val network: NetworkMonitor,
    private val codec: ArenaJournalCodec,
    private val checkpoints: ArenaJournalCheckpointCodec,
    private val nowMs: () -> Long,
    private val dispatcher: CoroutineDispatcher,
) {
    fun captureSession(owner: String): OutboxSession = gate.capture(owner)

    suspend fun read(
        session: OutboxSession,
        ticketId: String,
    ): Result<ArenaCachedTicket> =
        attempt {
            gate.withOwner(session) { state(requiredTicket(session, ticketId)) }
        }

    /** Online explicit consent is committed before dispatch. Existing identity cannot change ceiling. */
    suspend fun recordPrepareConsent(
        session: OutboxSession,
        request: ArenaPrepareRequest,
    ): Result<Unit> =
        attempt {
            require(session.ownerUid == request.ownerUid)
            gate.withOwner(session) { Unit }
            val online = network.isOnline()
            gate.withOwner(session) { Unit }
            if (!online) throw SyncFailure(SyncError.NoNetwork)
            gate.withOwner(session) { storage.saveConsent(codec.consent(request, nowMs())) }
        }

    suspend fun dispatchPrepare(
        session: OutboxSession,
        requestId: String,
    ): Result<ArenaCommandDispatch> =
        attempt {
            val cmd = gate.withOwner(session) { requiredCommand(session, "PREPARE", requestId) }
            if (cmd.state == "ACKNOWLEDGED") return@attempt ArenaCommandDispatch.ALREADY_RECORDED
            if (cmd.state != "PENDING") throw ArenaJournalAttention(cmd.lastFailure ?: "ATTENTION")
            val request = codec.prepare(cmd)
            val result = exchange(session, cmd) { remote.prepare(session, request).getOrThrow() }
            gate.withOwner(session) {
                when (result) {
                    is ArenaPrepareResponse.Ticket -> {
                        storage.acknowledge(codec.preparedRecord(result.ticket, request, nowMs()), "PREPARE", requestId)
                        ArenaCommandDispatch.RECORDED
                    }
                    is ArenaPrepareResponse.RetryRegeneration -> {
                        require(result.requestId == requestId)
                        storage.failure(session.ownerUid, "PREPARE", requestId, "RETRY_REGEN", false)
                        ArenaCommandDispatch.RETRY_REGEN
                    }
                    ArenaPrepareResponse.PriceChanged -> {
                        storage.failure(session.ownerUid, "PREPARE", requestId, "PRICE_CHANGED", true)
                        ArenaCommandDispatch.PRICE_CHANGED
                    }
                }
            }
        }

    /** Header observations only. Missing from this page is never interpreted as cancellation. */
    suspend fun recoverPage(
        session: OutboxSession,
        afterTicketId: String? = null,
        pageSize: Int = 20,
    ): Result<ArenaTicketPage> =
        attempt {
            gate.withOwner(session) { Unit }
            val page =
                readRemote(
                    session,
                ) {
                    remote.list(
                        session,
                        ArenaTicketListRequest(session.ownerUid, afterTicketId, pageSize),
                    ).getOrThrow()
                }
            gate.withOwner(session) {
                page.tickets.forEach { header ->
                    coroutineContext.ensureActive()
                    require(header.ownerUid == session.ownerUid)
                    storage.observe(codec.record(header, nowMs()))
                }
            }
            page
        }

    suspend fun recoverTicket(
        session: OutboxSession,
        ticketId: String,
    ): Result<ArenaCachedTicket> =
        attempt {
            gate.withOwner(session) { Unit }
            val result =
                readRemote(
                    session,
                ) { remote.get(session, ArenaTicketGetRequest(session.ownerUid, ticketId)).getOrThrow() }
            gate.withOwner(session) {
                require(
                    result.snapshot.header.ownerUid == session.ownerUid && result.snapshot.header.ticketId == ticketId,
                )
                val observation = codec.record(result, nowMs())
                storage.observe(observation)
                val prepare = storage.command(session.ownerUid, "PREPARE", observation.requestId)
                if (prepare != null) {
                    storage.acknowledge(
                        codec.preparedRecord(result, codec.prepare(prepare), nowMs()),
                        "PREPARE",
                        prepare.commandId,
                    )
                }
                val settlement = storage.ticketCommand(session.ownerUid, ticketId, "SETTLE")
                if (settlement != null && observation.serverStatus == "SETTLED") {
                    storage.acknowledge(observation, "SETTLE", settlement.commandId)
                } else if (settlement != null && observation.serverStatus == "CANCELLED") {
                    // Never forge a generic mutation receipt or silently remove another sender's queue row.
                    storage.failure(session.ownerUid, "SETTLE", settlement.commandId, "TICKET_CANCELLED", true)
                }
                state(requiredTicket(session, ticketId))
            }
        }

    suspend fun claimForLaunch(
        session: OutboxSession,
        ticketId: String,
        initial: ArenaInitialCheckpoint,
    ): Result<ArenaLaunchClaim> =
        attempt {
            gate.withOwner(session) {
                val row = requiredTicket(session, ticketId)
                val cached = state(row)
                if (cached !is ArenaCachedTicket.Available) return@withOwner ArenaLaunchClaim.NotFresh(cached)
                val candidate = checkpoints.initial(cached.snapshot, requireNotNull(row.snapshotSha256), initial)
                if (storage.claim(session.ownerUid, ticketId, candidate, checkpointValidator(row))) {
                    ArenaLaunchClaim.Claimed(cached.snapshot)
                } else {
                    ArenaLaunchClaim.NotFresh(state(requiredTicket(session, ticketId)))
                }
            }
        }

    suspend fun checkpoint(
        session: OutboxSession,
        ticketId: String,
    ): Result<ArenaSavedCheckpoint> =
        attempt {
            gate.withOwner(session) {
                val row = requiredTicket(session, ticketId)
                val saved = row.checkpointJson ?: throw ArenaJournalAttention("NO_DURABLE_CHECKPOINT")
                require(row.checkpointRevision >= 0)
                val snapshot = codec.decode(row).snapshot
                val hash = requireNotNull(row.snapshotSha256)
                checkpoints.validate(snapshot, hash, saved, saved, null)
                ArenaSavedCheckpoint(snapshot, hash, saved, row.checkpointRevision)
            }
        }

    suspend fun updateCheckpoint(
        session: OutboxSession,
        ticketId: String,
        attemptId: String,
        expectedRevision: Long,
        candidateJson: String,
    ): Result<Boolean> =
        attempt {
            gate.withOwner(session) {
                val row = requiredTicket(session, ticketId)
                storage.saveCheckpoint(
                    session.ownerUid,
                    ticketId,
                    attemptId,
                    expectedRevision,
                    candidateJson,
                    checkpointValidator(row),
                )
            }
        }

    /** May be recorded offline; only the explicit dispatch performs a server cancellation. */
    suspend fun requestCancel(
        session: OutboxSession,
        ticketId: String,
        requestId: String,
    ): Result<Unit> =
        attempt {
            gate.withOwner(
                session,
            ) { storage.chooseCancel(codec.cancel(requiredTicket(session, ticketId), requestId, nowMs())) }
        }

    suspend fun dispatchCancel(
        session: OutboxSession,
        ticketId: String,
    ): Result<ArenaCommandDispatch> =
        attempt {
            val pair =
                gate.withOwner(session) {
                    val row = requiredTicket(session, ticketId)
                    row to (
                        storage.ticketCommand(
                            session.ownerUid,
                            ticketId,
                            "CANCEL",
                        ) ?: throw ArenaJournalAttention("NO_CANCEL_INTENT")
                    )
                }
            val (row, cmd) = pair
            if (cmd.state == "ACKNOWLEDGED") return@attempt ArenaCommandDispatch.ALREADY_RECORDED
            if (cmd.state != "PENDING") throw ArenaJournalAttention(cmd.lastFailure ?: "ATTENTION")
            val result =
                exchange(session, cmd) { remote.cancel(session, codec.cancellationRequest(row, cmd)).getOrThrow() }
            gate.withOwner(session) {
                if (result is ArenaCancelResponse.RetryRegeneration) {
                    require(result.ticketId == ticketId && result.requestId == cmd.commandId)
                    storage.failure(session.ownerUid, "CANCEL", cmd.commandId, "RETRY_REGEN", false)
                    ArenaCommandDispatch.RETRY_REGEN
                } else {
                    storage.acknowledge(codec.terminal(row, result, nowMs()), "CANCEL", cmd.commandId)
                    ArenaCommandDispatch.RECORDED
                }
            }
        }

    suspend fun freezeCompletion(
        session: OutboxSession,
        ticketId: String,
        intent: UnscoredTerminalIntent,
    ): Result<Unit> =
        attempt {
            gate.withOwner(session) {
                val row = requiredTicket(session, ticketId)
                val retained = storage.ticketCommand(session.ownerUid, ticketId, "SETTLE")
                if (retained != null) {
                    val replay = codec.completionReplay(row, retained, intent)
                    storage.confirmSettlementReplay(replay, codec.outbox(replay))
                } else {
                    val cmd = codec.completion(row, intent)
                    storage.freezeSettlement(cmd, codec.outbox(cmd), checkpointValidator(row))
                }
            }
        }

    /** Explicit same-owner recovery after switch erased the ordinary queue. Never creates a new attempt ID. */
    suspend fun restorePendingSettlement(
        session: OutboxSession,
        ticketId: String,
    ): Result<Unit> =
        attempt {
            gate.withOwner(session) {
                val cmd =
                    storage.ticketCommand(
                        session.ownerUid,
                        ticketId,
                        "SETTLE",
                    ) ?: throw ArenaJournalAttention("NO_SETTLEMENT_INTENT")
                if (cmd.state == "PENDING") storage.restoreSettlement(session.ownerUid, ticketId, codec.outbox(cmd))
            }
        }

    suspend fun evictInactiveSnapshot(
        session: OutboxSession,
        ticketId: String,
    ): Result<Boolean> =
        attempt {
            gate.withOwner(session) { storage.evictSnapshot(session.ownerUid, ticketId) }
        }

    private suspend fun requiredTicket(
        session: OutboxSession,
        ticketId: String,
    ) = storage.ticket(session.ownerUid, ticketId) ?: throw ArenaJournalAttention("UNKNOWN_TICKET")

    private suspend fun requiredCommand(
        session: OutboxSession,
        kind: String,
        id: String,
    ) = storage.command(session.ownerUid, kind, id) ?: throw ArenaJournalAttention("NO_ORIGINAL_CONSENT")

    private fun checkpointValidator(row: ArenaTicketRecordEntity): ArenaCheckpointValidator {
        val snapshot = codec.decode(row).snapshot
        val hash = requireNotNull(row.snapshotSha256)
        val expected = ArenaCheckpointBinding(row.ownerUid, row.ticketId, row.attemptId, hash)
        return ArenaCheckpointValidator { binding, previous, candidate, completion ->
            require(binding == expected)
            checkpoints.validate(snapshot, hash, previous, candidate, completion)
        }
    }

    private fun state(row: ArenaTicketRecordEntity): ArenaCachedTicket {
        val header = codec.header(row)
        return when {
            row.serverStatus != "RESERVED" -> ArenaCachedTicket.Terminal(header)
            row.decision != "NONE" -> ArenaCachedTicket.DecisionPending(header, row.decision)
            else -> reservedState(row, header)
        }
    }

    private fun reservedState(
        row: ArenaTicketRecordEntity,
        header: ArenaTicketHeader,
    ): ArenaCachedTicket {
        val snapshot =
            try {
                codec.decode(row).snapshot
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure) {
                null
            }
        return when {
            snapshot == null -> ArenaCachedTicket.SnapshotUnavailable(header)
            row.localUse == "LAUNCHED" -> ArenaCachedTicket.ResumeOrReconcile(snapshot)
            else -> ArenaCachedTicket.Available(snapshot)
        }
    }

    // Transport/storage implementations may throw platform exceptions; cancellation must escape unchanged.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> exchange(
        session: OutboxSession,
        cmd: ArenaCommandEntity,
        request: suspend () -> T,
    ): T =
        try {
            gate.withOwner(session) { Unit }
            request().also { gate.withOwner(session) { Unit } }
        } catch (cancelled: CancellationException) {
            throw cancelled // Persisted PENDING remains; cancellation is not a confirmed server cancellation.
        } catch (error: Exception) {
            gate.withOwner(
                session,
            ) { storage.failure(session.ownerUid, cmd.commandKind, cmd.commandId, "EXCHANGE_FAILED", false) }
            throw error
        }

    // Recheck the owner even for an unknown transport exception, without consuming coroutine cancellation.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> readRemote(
        session: OutboxSession,
        request: suspend () -> T,
    ): T =
        try {
            gate.withOwner(session) { Unit }
            request().also { gate.withOwner(session) { Unit } }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            gate.withOwner(session) { Unit }
            throw error
        }

    // The public Result boundary preserves implementation errors; cancellation remains structured control flow.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        withContext(dispatcher) {
            try {
                Result.success(block())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
}
