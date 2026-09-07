package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.persistence.ArenaJournalConflict
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaCancellationResolution
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaCancelledSettlementResolver
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaCommandDispatch
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaEntryLocalMapper
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaJournalAttention
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaJournalStorage
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaLocalPrepareCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaLocalTicketCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaOwnerGate
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaTicketJournal
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntriesCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntriesPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryCommandOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryResolution
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationsCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationsPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRecoveryCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRecoveryPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaEntryRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/** No constructor actions or owned scope. Journal methods acquire their own non-reentrant gate. */
class ArenaEntryRepositoryImpl(
    private val storage: ArenaJournalStorage,
    private val journal: ArenaTicketJournal,
    private val resolver: ArenaCancelledSettlementResolver,
    private val mapper: ArenaEntryLocalMapper,
    private val gate: ArenaOwnerGate,
    private val sessions: ArenaSessionProvider,
    private val dispatcher: CoroutineDispatcher,
) : ArenaEntryRepository {
    override suspend fun localTickets(
        stamp: ArenaSessionStamp,
        size: Int,
        cursor: ArenaEntriesCursor?,
    ): Result<ArenaEntriesPage> =
        attempt(stamp) { session ->
            valid(size in 1..MAX_LOCAL_SIZE)
            val after = ticketCursor(stamp, cursor)
            gate.withOwner(session) {
                val page = storage.ticketPage(session.ownerUid, size, after)
                ArenaEntriesPage(
                    page.rows.map { row ->
                        coroutineContext.ensureActive()
                        mapper.ticket(session.ownerUid, row)
                    },
                    page.nextCursor?.let { TicketCursor(stamp, it) },
                )
            }
        }

    override suspend fun pendingPreparations(
        stamp: ArenaSessionStamp,
        size: Int,
        cursor: ArenaPreparationsCursor?,
    ): Result<ArenaPreparationsPage> =
        attempt(stamp) { session ->
            valid(size in 1..MAX_LOCAL_SIZE)
            val after = prepareCursor(stamp, cursor)
            gate.withOwner(session) {
                val page = storage.pendingPreparePage(session.ownerUid, size, after)
                ArenaPreparationsPage(
                    page.rows.map { row ->
                        coroutineContext.ensureActive()
                        mapper.preparation(session.ownerUid, row)
                    },
                    page.nextCursor?.let { PrepareCursor(stamp, it) },
                )
            }
        }

    override suspend fun readTicket(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Result<ArenaLocalEntry?> =
        attempt(stamp) { session ->
            validTicket(stamp, ticketId)
            readLocal(session, ticketId)
        }

    override suspend fun recordPrepareConsent(
        stamp: ArenaSessionStamp,
        request: ArenaPrepareRequest,
    ): Result<Unit> =
        attempt(stamp) { session ->
            valid(request.ownerUid == stamp.ownerUid)
            journal.recordPrepareConsent(session, request).getOrThrow()
        }

    override suspend fun dispatchPrepare(
        stamp: ArenaSessionStamp,
        requestId: String,
    ): Result<ArenaEntryCommandOutcome> =
        attempt(stamp) { session ->
            validDocumentId(requestId)
            val result = journal.dispatchPrepare(session, requestId)
            if ((result.exceptionOrNull() as? ArenaJournalAttention)?.reason == "PRICE_CHANGED") {
                ArenaEntryCommandOutcome.PRICE_CHANGED_REQUIRES_CONSENT
            } else {
                outcome(result.getOrThrow())
            }
        }

    override suspend fun recoverPage(
        stamp: ArenaSessionStamp,
        size: Int,
        cursor: ArenaRecoveryCursor?,
    ): Result<ArenaRecoveryPage> =
        attempt(stamp) { session ->
            valid(size in 1..MAX_RECOVERY_SIZE)
            val after = recoveryCursor(stamp, cursor)
            // This journal may commit a valid prefix. An exception is not a page-wide rollback.
            val page = journal.recoverPage(session, after, size).getOrThrow()
            ArenaRecoveryPage(page.tickets.toList(), page.nextTicketId?.let { RecoveryCursor(stamp, it) })
        }

    override suspend fun recoverTicket(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Result<ArenaLocalEntry> =
        attempt(stamp) { session ->
            validTicket(stamp, ticketId)
            journal.recoverTicket(session, ticketId).getOrThrow()
            readLocal(session, ticketId) ?: throw ArenaEntryFailure(ArenaEntryFailureKind.NEEDS_RECOVERY)
        }

    override suspend fun recordCancelIntent(
        stamp: ArenaSessionStamp,
        ticketId: String,
        requestId: String,
    ): Result<Unit> =
        attempt(stamp) { session ->
            validTicket(stamp, ticketId)
            validDocumentId(requestId)
            journal.requestCancel(session, ticketId, requestId).getOrThrow()
        }

    override suspend fun dispatchCancel(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Result<ArenaEntryCommandOutcome> =
        attempt(stamp) { session ->
            validTicket(stamp, ticketId)
            outcome(journal.dispatchCancel(session, ticketId).getOrThrow())
        }

    override suspend fun reconcileCancelledSettlement(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Result<ArenaEntryResolution> =
        attempt(stamp) { session ->
            validTicket(stamp, ticketId)
            when (val result = resolver.resolve(session, ticketId).getOrThrow()) {
                is ArenaCancellationResolution.Reserved -> ArenaEntryResolution.StillReserved(result.observation)
                is ArenaCancellationResolution.SettledUnchanged ->
                    ArenaEntryResolution.SettledUnchanged(
                        result.observation,
                    )
                is ArenaCancellationResolution.ResolvedCancelled ->
                    ArenaEntryResolution.CancelledResolved(
                        result.observation,
                    )
            }
        }

    override suspend fun restorePendingSettlement(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Result<Unit> =
        attempt(stamp) { session ->
            validTicket(stamp, ticketId)
            journal.restorePendingSettlement(session, ticketId).getOrThrow()
        }

    private suspend fun readLocal(
        session: OutboxSession,
        ticketId: String,
    ): ArenaLocalEntry? =
        gate.withOwner(session) {
            storage.ticket(session.ownerUid, ticketId)?.also { require(it.ticketId == ticketId) }
                ?.let { mapper.ticket(session.ownerUid, it) }
        }

    // This layer removes implementation messages/causes; cancellation remains structured control flow.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> attempt(
        stamp: ArenaSessionStamp,
        block: suspend (OutboxSession) -> T,
    ): Result<T> {
        coroutineContext.ensureActive()
        val original = OutboxSession(stamp.ownerUid, stamp.generation) // Projection, NEVER recapture by UID.
        return try {
            requireSession(stamp)
            val value =
                withContext(dispatcher) {
                    requireSession(stamp)
                    val result = block(original)
                    coroutineContext.ensureActive()
                    requireSession(stamp)
                    result
                }
            coroutineContext.ensureActive()
            requireSession(stamp) // Also fence the dispatcher return to the caller, not just the IO block.
            Result.success(value)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            coroutineContext.ensureActive()
            val current = sessions.current()
            val failure =
                when {
                    current == null -> ArenaEntryFailureKind.ACCOUNT_UNAVAILABLE
                    current != stamp -> ArenaEntryFailureKind.ACCOUNT_CHANGED
                    else -> failureKind(error)
                }
            Result.failure(ArenaEntryFailure(failure))
        }
    }

    private fun requireSession(stamp: ArenaSessionStamp) {
        val current = sessions.current()
        when {
            current == null -> throw ArenaEntryFailure(ArenaEntryFailureKind.ACCOUNT_UNAVAILABLE)
            current != stamp -> throw ArenaEntryFailure(ArenaEntryFailureKind.ACCOUNT_CHANGED)
        }
    }

    private fun ticketCursor(
        stamp: ArenaSessionStamp,
        cursor: ArenaEntriesCursor?,
    ): ArenaLocalTicketCursor? {
        if (cursor == null) return null
        valid(cursor is TicketCursor && cursor.stamp == stamp)
        return (cursor as TicketCursor).raw
    }

    private fun prepareCursor(
        stamp: ArenaSessionStamp,
        cursor: ArenaPreparationsCursor?,
    ): ArenaLocalPrepareCursor? {
        if (cursor == null) return null
        valid(cursor is PrepareCursor && cursor.stamp == stamp)
        return (cursor as PrepareCursor).raw
    }

    private fun recoveryCursor(
        stamp: ArenaSessionStamp,
        cursor: ArenaRecoveryCursor?,
    ): String? {
        if (cursor == null) return null
        valid(cursor is RecoveryCursor && cursor.stamp == stamp)
        return (cursor as RecoveryCursor).afterTicketId
    }

    private fun validTicket(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ) {
        try {
            ArenaTicketGetRequest(stamp.ownerUid, ticketId)
        } catch (_: IllegalArgumentException) {
            throw ArenaEntryFailure(ArenaEntryFailureKind.INVALID_REQUEST)
        }
    }

    private fun validDocumentId(id: String) =
        valid(id.isNotBlank() && '/' !in id && id != "." && id != ".." && id.encodeToByteArray().size <= MAX_ID_BYTES)

    private fun valid(condition: Boolean) {
        if (!condition) throw ArenaEntryFailure(ArenaEntryFailureKind.INVALID_REQUEST)
    }

    private class TicketCursor(val stamp: ArenaSessionStamp, val raw: ArenaLocalTicketCursor) : ArenaEntriesCursor

    private class PrepareCursor(
        val stamp: ArenaSessionStamp,
        val raw: ArenaLocalPrepareCursor,
    ) : ArenaPreparationsCursor

    private class RecoveryCursor(val stamp: ArenaSessionStamp, val afterTicketId: String) : ArenaRecoveryCursor

    private companion object {
        const val MAX_LOCAL_SIZE = 50
        const val MAX_RECOVERY_SIZE = 20
        const val MAX_ID_BYTES = 1_500

        fun outcome(value: ArenaCommandDispatch): ArenaEntryCommandOutcome =
            when (value) {
                ArenaCommandDispatch.RECORDED -> ArenaEntryCommandOutcome.RECORDED
                ArenaCommandDispatch.ALREADY_RECORDED -> ArenaEntryCommandOutcome.ALREADY_RECORDED
                ArenaCommandDispatch.RETRY_REGEN -> ArenaEntryCommandOutcome.RETRY_SAME_REQUEST
                ArenaCommandDispatch.PRICE_CHANGED -> ArenaEntryCommandOutcome.PRICE_CHANGED_REQUIRES_CONSENT
            }

        fun failureKind(error: Exception): ArenaEntryFailureKind =
            when (error) {
                is ArenaEntryFailure -> error.kind
                is ArenaRemoteFailure ->
                    when (error.kind) {
                        ArenaRemoteFailureKind.ACCOUNT_UNAVAILABLE -> ArenaEntryFailureKind.ACCOUNT_UNAVAILABLE
                        ArenaRemoteFailureKind.ACCOUNT_CHANGED -> ArenaEntryFailureKind.ACCOUNT_CHANGED
                        ArenaRemoteFailureKind.MALFORMED_RESPONSE -> ArenaEntryFailureKind.NEEDS_RECOVERY
                    }
                is SyncFailure ->
                    when (error.error) {
                        SyncError.NoNetwork -> ArenaEntryFailureKind.NO_NETWORK
                        is SyncError.VersionConflict -> ArenaEntryFailureKind.CONFLICT
                        else -> ArenaEntryFailureKind.UNAVAILABLE
                    }
                is ArenaJournalConflict -> ArenaEntryFailureKind.CONFLICT
                is ArenaJournalAttention, is IllegalArgumentException -> ArenaEntryFailureKind.NEEDS_RECOVERY
                else -> ArenaEntryFailureKind.UNAVAILABLE
            }
    }
}
