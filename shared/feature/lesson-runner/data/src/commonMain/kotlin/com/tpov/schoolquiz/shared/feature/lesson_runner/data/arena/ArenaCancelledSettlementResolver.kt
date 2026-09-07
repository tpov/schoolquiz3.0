package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.persistence.ArenaCommandEntity
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketRecordEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketStatusRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatusObservation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** These observations grant no launch, score, success ACK or local wallet authority. */
sealed interface ArenaCancellationResolution {
    data class Reserved(val observation: ArenaTicketStatusObservation.Reserved) : ArenaCancellationResolution

    data class SettledUnchanged(val observation: ArenaTicketStatusObservation.Settled) : ArenaCancellationResolution

    data class ResolvedCancelled(val observation: ArenaTicketStatusObservation.Cancelled) : ArenaCancellationResolution
}

/** Explicit one-shot reconciliation of an existing SETTLE only. Never a sender, poller or startup hook. */
class ArenaCancelledSettlementResolver(
    private val storage: ArenaJournalStorage,
    private val remote: ArenaTicketStatusRemoteDataSource,
    private val gate: ArenaOwnerGate,
    private val proof: ArenaCancelledSettlementProof,
    private val dispatcher: CoroutineDispatcher,
    private val nowMs: () -> Long,
) {
    // Platform/storage failures cross this Result boundary; structured cancellation must still escape.
    @Suppress("TooGenericExceptionCaught")
    suspend fun resolve(
        ownerUid: String,
        ticketId: String,
    ): Result<ArenaCancellationResolution> =
        try {
            val session = gate.capture(ownerUid) // Capture before even the dispatcher suspension: A -> B -> A is stale.
            resolve(session, ticketId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }

    /** The façade/journal supplies its original epoch; this path never captures a replacement. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun resolve(
        session: OutboxSession,
        ticketId: String,
    ): Result<ArenaCancellationResolution> =
        try {
            val request = ArenaTicketGetRequest(session.ownerUid, ticketId)
            withContext(dispatcher) {
                val (ticket, command) =
                    gate.withOwner(session) {
                        retained(session, ticketId).also { (row, cmd) -> proof.firstIntent(row, cmd) }
                    }
                val response = readStatus(session, request) // No owner mutex/withoutPasses is held across the network.
                val result =
                    gate.withOwner(session) {
                        val bound = proof.bind(response, request, ticket, command, nowMs())
                        val (currentTicket, currentCommand) = retained(session, ticketId)
                        bound.checkCurrent(currentTicket, currentCommand)
                        when (val verified = bound.response) {
                            is ArenaTicketStatusObservation.Reserved -> ArenaCancellationResolution.Reserved(verified)
                            is ArenaTicketStatusObservation.Settled ->
                                ArenaCancellationResolution.SettledUnchanged(verified)
                            is ArenaTicketStatusObservation.Cancelled -> {
                                storage.resolveCancelledSettlement(
                                    command,
                                    bound.firstIntent,
                                    bound.observation,
                                    bound,
                                )
                                ArenaCancellationResolution.ResolvedCancelled(verified)
                            }
                        }
                    }
                Result.success(result)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }

    private suspend fun retained(
        session: OutboxSession,
        ticketId: String,
    ): Pair<ArenaTicketRecordEntity, ArenaCommandEntity> {
        val ticket = storage.ticket(session.ownerUid, ticketId) ?: throw ArenaJournalAttention("UNKNOWN_TICKET")
        require(ticket.ownerUid == session.ownerUid && ticket.ticketId == ticketId)
        val command =
            storage.ticketCommand(session.ownerUid, ticketId, "SETTLE")
                ?: throw ArenaJournalAttention("NO_SETTLEMENT_INTENT")
        return ticket to command
    }

    @Suppress(
        "TooGenericExceptionCaught",
    ) // Recheck the captured epoch on failed as well as successful network replies.
    private suspend fun readStatus(
        session: OutboxSession,
        request: ArenaTicketGetRequest,
    ): ArenaTicketStatusObservation =
        try {
            remote.status(session, request).getOrThrow().also { gate.withOwner(session) { Unit } }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            gate.withOwner(session) { Unit }
            throw error
        }
}
