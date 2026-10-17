package com.tpov.schoolquiz.platform.firebase.tournament

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.network.mutationResponseJson
import com.tpov.schoolquiz.platform.firebase.network.toSyncError
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.MalformedWire
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentEventWireParser
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentMeetingWireParser
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentSessionWireParser
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentDownloadRecord
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentDownloadRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventListRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventPage
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventRead
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventReadRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingPage
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingReadRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentOperateRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentOperationView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrepareReply
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrepareRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrizeRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentRemoteFailure
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentRemoteFailureKind
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionRead
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionReadRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.remote.TournamentEventRemoteDataSource
import com.tpov.schoolquiz.shared.feature.tournament.domain.remote.TournamentMeetingRemoteDataSource
import com.tpov.schoolquiz.shared.feature.tournament.domain.remote.TournamentOperationRemoteDataSource
import com.tpov.schoolquiz.shared.feature.tournament.domain.remote.TournamentSessionReadRemoteDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/**
 * Явные вызовы соревнования (история 5.9), транспортный образец Arena: состав
 * [TournamentCallableExchange] переиспользуется, не обобщается. Мутаций сессии здесь нет —
 * ANSWER/ADVANCE/READY_ACK едут из очереди; identity/повтор держат operationId и ключ очереди,
 * а не транспорт. Владелец captured до отправки и перепроверяется на каждом возврате: ответ
 * чужой смены (A -> B -> A включительно) не принимается; локальных записей, авто-повторов и
 * признания терминальных состояний здесь нет.
 */
class FirebaseTournamentRemoteDataSource internal constructor(
    private val exchange: TournamentCallableExchange,
    private val sessions: OutboxSessionProvider,
    private val networkMonitor: NetworkMonitor,
    private val dispatcher: CoroutineDispatcher,
    private val eventParser: TournamentEventWireParser,
    private val meetingParser: TournamentMeetingWireParser,
    private val sessionParser: TournamentSessionWireParser,
) : TournamentEventRemoteDataSource,
    TournamentMeetingRemoteDataSource,
    TournamentSessionReadRemoteDataSource,
    TournamentOperationRemoteDataSource {
    constructor(
        functions: FirebaseFunctions,
        sessions: OutboxSessionProvider,
        networkMonitor: NetworkMonitor,
        dispatcher: CoroutineDispatcher,
        eventParser: TournamentEventWireParser,
        meetingParser: TournamentMeetingWireParser,
        sessionParser: TournamentSessionWireParser,
    ) : this(
        exchange = firebaseTournamentCallableExchange(functions),
        sessions = sessions,
        networkMonitor = networkMonitor,
        dispatcher = dispatcher,
        eventParser = eventParser,
        meetingParser = meetingParser,
        sessionParser = sessionParser,
    )

    override suspend fun readEvent(request: TournamentEventReadRequest): Result<TournamentEventRead> =
        currentSession(request.ownerUid) { session ->
            execute(session, request.ownerUid, READ_EVENT, mapOf("eventId" to request.eventId)) { wire ->
                eventParser.parseEventRead(wire)
            }
        }

    override suspend fun listEvents(request: TournamentEventListRequest): Result<TournamentEventPage> =
        currentSession(request.ownerUid) { session ->
            execute(
                session,
                request.ownerUid,
                LIST_EVENTS,
                buildMap {
                    request.afterEventId?.let { put("afterEventId", it) }
                    put("pageSize", request.pageSize)
                },
            ) { wire ->
                eventParser.parseEventPage(wire)
            }
        }

    override suspend fun readMeeting(request: TournamentMeetingReadRequest): Result<TournamentMeetingPage> =
        currentSession(request.ownerUid) { session ->
            execute(
                session,
                request.ownerUid,
                READ_MEETING,
                buildMap {
                    put("eventId", request.eventId)
                    put("matchId", request.matchId)
                    request.afterOfferId?.let { put("afterOfferId", it) }
                    request.afterConfirmationId?.let { put("afterConfirmationId", it) }
                    put("pageSize", request.pageSize)
                },
            ) { wire ->
                meetingParser.parseMeetingPage(wire)
            }
        }

    override suspend fun readSession(request: TournamentSessionReadRequest): Result<TournamentSessionRead> =
        currentSession(request.ownerUid) { session ->
            execute(session, request.ownerUid, READ_SESSION, mapOf("sessionId" to request.sessionId)) { wire ->
                sessionParser.parseOperationView(wire)
            }
        }

    override suspend fun prepare(request: TournamentPrepareRequest): Result<TournamentPrepareReply> =
        currentSession(request.ownerUid) { session ->
            execute(
                session,
                request.ownerUid,
                PREPARE,
                mapOf(
                    "requestId" to request.requestId,
                    "source" to mapOf("kind" to request.source.kind, "id" to request.source.id),
                ),
            ) { wire ->
                sessionParser.parsePrepareReply(wire)
            }
        }

    override suspend fun operate(request: TournamentOperateRequest): Result<TournamentOperationView> =
        currentSession(request.ownerUid) { session ->
            execute(session, request.ownerUid, OPERATE, operatePayload(request)) { wire ->
                sessionParser.parseOperationView(wire)
            }
        }

    override suspend fun recordDownload(request: TournamentDownloadRequest): Result<TournamentDownloadRecord> =
        currentSession(request.ownerUid) { session ->
            execute(
                session,
                request.ownerUid,
                RECORD_DOWNLOAD,
                mapOf("operationId" to request.operationId, "courseId" to request.courseId),
            ) { wire ->
                sessionParser.parseDownloadRecord(wire)
            }
        }

    /** Тело операции — ровно allowlist вызываемого; ownerUid телом не ездит (выводится из Auth). */
    private fun operatePayload(request: TournamentOperateRequest): Map<String, Any?> =
        buildMap {
            put("operationId", request.operationId)
            put("eventId", request.eventId)
            when (request) {
                is TournamentOperateRequest.Create -> {
                    put("kind", "CREATE")
                    put(
                        "source",
                        mapOf("kind" to request.source.kind, "id" to request.source.id, "snapshotId" to request.snapshotId),
                    )
                    put("startsAtMs", request.startsAtMs)
                    put("prizes", prizeList(request.prizeRows))
                    put("fundedBoxes", request.fundedBoxes)
                }
                is TournamentOperateRequest.StartQualification -> {
                    put("kind", "START_QUALIFICATION")
                    put("roundNumber", request.roundNumber)
                    put("attemptId", request.attemptId)
                    request.linkToken?.let { put("linkToken", it) }
                }
                is TournamentOperateRequest.Propose -> {
                    put("kind", "PROPOSE")
                    put("matchId", request.matchId)
                    put("slotStartsAtMs", request.slotStartsAtMs)
                }
                is TournamentOperateRequest.Confirm -> {
                    put("kind", "CONFIRM")
                    put("matchId", request.matchId)
                    put("slotStartsAtMs", request.slotStartsAtMs)
                }
                is TournamentOperateRequest.Ready -> {
                    put("kind", "READY")
                    put("matchId", request.matchId)
                }
            }
        }

    /** Новые события несут только канонический список v1; legacy-форма клиентом не создаётся. */
    private fun prizeList(rows: List<TournamentPrizeRow>): Map<String, Any?> =
        mapOf(
            "listVersion" to 1,
            "rows" to rows.map { mapOf("place" to it.place, "amountBoxes" to it.amountBoxes) },
        )

    /** Plain API captures before its first dispatcher suspension, not when execution resumes. */
    private suspend fun <T> currentSession(
        ownerUid: String,
        block: suspend (OutboxSession) -> Result<T>,
    ): Result<T> {
        coroutineContext.ensureActive()
        val session = sessions.current()
        return when {
            session == null -> Result.failure(TournamentRemoteFailure(TournamentRemoteFailureKind.ACCOUNT_UNAVAILABLE))
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
                val pending = exchange.start(name, payload)
                // SDK initiation is guarded; the network wait must not block account switch.
                val reply = pending.await()
                coroutineContext.ensureActive()
                requireSession(session)
                val decoded = decode(responseWire(reply))
                requireSession(session)
                Result.success(decoded)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                coroutineContext.ensureActive()
                if (sessions.current() != session) {
                    return@withContext Result.failure(TournamentRemoteFailure(TournamentRemoteFailureKind.ACCOUNT_CHANGED))
                }
                when (e) {
                    is TournamentRemoteFailure -> Result.failure(e)
                    is MalformedWire -> Result.failure(TournamentRemoteFailure(TournamentRemoteFailureKind.MALFORMED_RESPONSE))
                    is TournamentCallableException ->
                        Result.failure(
                            SyncFailure(
                                // Два уровня причины — форма обмена выше: TournamentCallableException
                                // несёт cause = FirebaseFunctionsException, чей cause — транспортная
                                // IOException; глубже хвоста нет, по типу защищённо классифицируется
                                // ровно эта цепочка (AD-15: код и детали, не текст).
                                tournamentSyncError(e.code, e.details, message = null, causedByIo = e.cause?.cause is IOException),
                                e,
                            ),
                        )
                    else -> Result.failure(SyncFailure(e.toSyncError(), e))
                }
            }
        }
    }

    private fun responseWire(reply: Any?): String =
        try {
            mutationResponseJson(reply)
        } catch (_: IllegalArgumentException) {
            throw TournamentRemoteFailure(TournamentRemoteFailureKind.MALFORMED_RESPONSE)
        } catch (_: IllegalStateException) {
            throw TournamentRemoteFailure(TournamentRemoteFailureKind.MALFORMED_RESPONSE)
        }

    private fun <T> accountChanged(): Result<T> =
        Result.failure(TournamentRemoteFailure(TournamentRemoteFailureKind.ACCOUNT_CHANGED))

    private fun requireSession(expected: OutboxSession) {
        if (sessions.current() != expected) throw TournamentRemoteFailure(TournamentRemoteFailureKind.ACCOUNT_CHANGED)
    }

    internal companion object {
        const val PREPARE = "prepareCourseTournamentBank"
        const val OPERATE = "submitCourseTournamentOperation"
        const val READ_EVENT = "fetchCourseTournamentEvent"
        const val READ_SESSION = "fetchCourseTournamentSession"
        const val READ_MEETING = "fetchCourseTournamentMeeting"
        const val LIST_EVENTS = "fetchCourseTournamentEvents"
        const val RECORD_DOWNLOAD = "recordCourseTournamentDownload"
    }
}
