package com.tpov.schoolquiz.platform.firebase.arena

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.network.mutationResponseJson
import com.tpov.schoolquiz.platform.firebase.network.syncErrorForCode
import com.tpov.schoolquiz.platform.firebase.network.toSyncError
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaOwnerGate
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketStatusRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancelResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryTicket
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketCancelRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketListRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatusObservation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/**
 * Explicit calls only, including a question-free status port. The caller owns durable journalling.
 * This adapter never creates IDs, writes a local wallet/cache, automatically retries or acknowledges a terminal ticket.
 * Mutation expectedOwnerUid is checked by the callable BEFORE financial effects; session epochs also
 * reject stale reads and A -> B -> A responses. Neither check replaces the other.
 */
class FirebaseArenaTicketRemoteDataSource internal constructor(
    private val exchange: ArenaCallableExchange,
    private val gate: ArenaOwnerGate,
    private val sessions: OutboxSessionProvider,
    private val networkMonitor: NetworkMonitor,
    private val dispatcher: CoroutineDispatcher,
    private val parser: ArenaTicketWireParser = ArenaTicketWireParser(::arenaSha256),
) : ArenaTicketRemoteDataSource, ArenaTicketStatusRemoteDataSource {
    constructor(
        functions: FirebaseFunctions,
        sessions: OutboxSessionProvider,
        networkMonitor: NetworkMonitor,
        dispatcher: CoroutineDispatcher,
        gate: ArenaOwnerGate,
    ) : this(
        exchange = firebaseArenaCallableExchange(functions),
        gate = gate,
        sessions = sessions,
        networkMonitor = networkMonitor,
        dispatcher = dispatcher,
    )

    override suspend fun prepare(request: ArenaPrepareRequest): Result<ArenaPrepareResponse> =
        currentSession(request.ownerUid) { prepare(it, request) }

    override suspend fun prepare(
        expected: OutboxSession,
        request: ArenaPrepareRequest,
    ): Result<ArenaPrepareResponse> {
        val payload =
            buildMap<String, Any?> {
                put("expectedOwnerUid", request.ownerUid)
                put("requestId", request.requestId)
                put("difficulty", request.difficulty.name)
                when (request) {
                    is ArenaPrepareRequest.Random -> put("mode", "RANDOM")
                    is ArenaPrepareRequest.Selected -> {
                        put("mode", "SELECTED")
                        put("lessonId", request.lessonId)
                        put("maxPriceNolics", request.maxPriceNolics)
                    }
                }
            }
        return execute(
            expected,
            request.ownerUid,
            PREPARE,
            payload,
            { parser.prepare(it, request) },
            knownFailure = { signal ->
                when (signal) {
                    ArenaCallableSignal.RETRY_REGEN -> ArenaPrepareResponse.RetryRegeneration(request.requestId)
                    ArenaCallableSignal.PRICE_CHANGED ->
                        if (request is ArenaPrepareRequest.Selected) ArenaPrepareResponse.PriceChanged else null
                    else -> null
                }
            },
        )
    }

    override suspend fun list(request: ArenaTicketListRequest): Result<ArenaTicketPage> =
        currentSession(request.ownerUid) { list(it, request) }

    override suspend fun list(
        expected: OutboxSession,
        request: ArenaTicketListRequest,
    ): Result<ArenaTicketPage> =
        execute(
            expected,
            request.ownerUid,
            LIST,
            buildMap {
                put("pageSize", request.pageSize)
                request.afterTicketId?.let { put("afterTicketId", it) }
            },
            { parser.list(it, request) },
        )

    override suspend fun get(request: ArenaTicketGetRequest): Result<ArenaEntryTicket> =
        currentSession(request.ownerUid) { get(it, request) }

    override suspend fun get(
        expected: OutboxSession,
        request: ArenaTicketGetRequest,
    ): Result<ArenaEntryTicket> =
        execute(
            expected,
            request.ownerUid,
            GET,
            mapOf("ticketId" to request.ticketId),
            { parser.get(it, request) },
        )

    override suspend fun status(request: ArenaTicketGetRequest): Result<ArenaTicketStatusObservation> =
        currentSession(request.ownerUid) { status(it, request) }

    override suspend fun status(
        expected: OutboxSession,
        request: ArenaTicketGetRequest,
    ): Result<ArenaTicketStatusObservation> =
        execute(
            expected,
            request.ownerUid,
            STATUS,
            mapOf("ticketId" to request.ticketId),
            { parser.status(it, request) },
        )

    override suspend fun cancel(request: ArenaTicketCancelRequest): Result<ArenaCancelResponse> =
        currentSession(request.ticket.ownerUid) { cancel(it, request) }

    override suspend fun cancel(
        expected: OutboxSession,
        request: ArenaTicketCancelRequest,
    ): Result<ArenaCancelResponse> =
        execute(
            expected,
            request.ticket.ownerUid,
            CANCEL,
            mapOf(
                "expectedOwnerUid" to request.ticket.ownerUid,
                "ticketId" to request.ticket.ticketId,
                "requestId" to request.requestId,
            ),
            { parser.cancel(it, request) },
            knownFailure = { signal ->
                if (signal == ArenaCallableSignal.RETRY_REGEN) {
                    ArenaCancelResponse.RetryRegeneration(request.ticket.ticketId, request.requestId)
                } else {
                    null
                }
            },
        )

    /** Plain API captures before its first dispatcher suspension, not when execution resumes. */
    private suspend fun <T> currentSession(
        ownerUid: String,
        block: suspend (OutboxSession) -> Result<T>,
    ): Result<T> {
        coroutineContext.ensureActive()
        val session = sessions.current()
        return when {
            session == null -> Result.failure(ArenaRemoteFailure(ArenaRemoteFailureKind.ACCOUNT_UNAVAILABLE))
            session.ownerUid != ownerUid -> accountChanged()
            else -> block(session)
        }
    }

    private suspend fun <T> execute(
        session: OutboxSession,
        ownerUid: String,
        name: String,
        payload: Map<String, Any?>,
        decode: (String) -> T,
        knownFailure: (ArenaCallableSignal) -> T? = { null },
    ): Result<T> {
        coroutineContext.ensureActive()
        if (session.ownerUid != ownerUid || sessions.current() != session) return accountChanged()
        return withContext(dispatcher) {
            try {
                requireSession(session)
                val online = networkMonitor.isOnline()
                requireSession(session)
                if (!online) return@withContext Result.failure(SyncFailure(SyncError.NoNetwork))
                coroutineContext.ensureActive()
                val pending =
                    gate.withOwner(session) {
                        coroutineContext.ensureActive()
                        requireSession(session)
                        exchange.start(name, payload)
                    }
                // SDK initiation is guarded; the network wait must not block account switch or queue passes.
                val reply = pending.await()
                coroutineContext.ensureActive()
                requireSession(session)
                val wire = responseWire(reply)
                val decoded = decode(wire)
                requireSession(session)
                Result.success(decoded)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                coroutineContext.ensureActive()
                if (sessions.current() != session) {
                    return@withContext Result.failure(ArenaRemoteFailure(ArenaRemoteFailureKind.ACCOUNT_CHANGED))
                }
                when (e) {
                    is ArenaRemoteFailure -> Result.failure(e)
                    is ArenaCallableException -> {
                        val signal = arenaCallableSignal(e.code, e.details)
                        if (signal == ArenaCallableSignal.ACCOUNT_CHANGED) {
                            Result.failure(ArenaRemoteFailure(ArenaRemoteFailureKind.ACCOUNT_CHANGED))
                        } else {
                            val value = knownFailure(signal)
                            if (value != null) {
                                Result.success(value)
                            } else {
                                Result.failure(
                                    SyncFailure(
                                        syncErrorForCode(
                                            e.code,
                                            message = null,
                                            details = e.details,
                                            causedByIo = e.cause?.cause is IOException,
                                        ),
                                        e,
                                    ),
                                )
                            }
                        }
                    }
                    else -> Result.failure(SyncFailure(e.toSyncError(), e))
                }
            }
        }
    }

    private fun responseWire(reply: Any?): String =
        try {
            mutationResponseJson(reply)
        } catch (_: IllegalArgumentException) {
            throw ArenaRemoteFailure(ArenaRemoteFailureKind.MALFORMED_RESPONSE)
        } catch (_: IllegalStateException) {
            throw ArenaRemoteFailure(ArenaRemoteFailureKind.MALFORMED_RESPONSE)
        }

    private fun <T> accountChanged(): Result<T> =
        Result.failure(ArenaRemoteFailure(ArenaRemoteFailureKind.ACCOUNT_CHANGED))

    private fun requireSession(expected: OutboxSession) {
        if (sessions.current() != expected) throw ArenaRemoteFailure(ArenaRemoteFailureKind.ACCOUNT_CHANGED)
    }

    internal companion object {
        const val PREPARE = "prepareArenaEntry"
        const val LIST = "listArenaEntryTickets"
        const val GET = "fetchArenaEntryTicket"
        const val STATUS = "fetchArenaEntryTicketStatus"
        const val CANCEL = "cancelArenaEntry"
    }
}

/** SDK metadata, never message-text parsing. Internal seam keeps JVM tests free of Firebase startup. */
internal class ArenaCallableException(val code: String, val details: Any?, cause: Throwable? = null) : Exception(cause)

internal enum class ArenaCallableSignal { RETRY_REGEN, PRICE_CHANGED, ACCOUNT_CHANGED, OTHER }

internal fun arenaCallableSignal(
    code: String,
    details: Any?,
): ArenaCallableSignal {
    val reason = (details as? Map<*, *>)?.get("reason") as? String
    return when {
        code == "UNAVAILABLE" && reason == "RETRY_REGEN" -> ArenaCallableSignal.RETRY_REGEN
        code == "FAILED_PRECONDITION" && reason == "PRICE_CHANGED" -> ArenaCallableSignal.PRICE_CHANGED
        code == "PERMISSION_DENIED" && reason == "ACCOUNT_CHANGED" -> ArenaCallableSignal.ACCOUNT_CHANGED
        else -> ArenaCallableSignal.OTHER
    }
}

@Suppress("MagicNumber") // Unsigned-byte mask and hexadecimal radix.
internal fun arenaSha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }
