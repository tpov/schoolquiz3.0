package com.tpov.schoolquiz.platform.firebase.tournament

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentEventWireParser
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentMeetingWireParser
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentSessionWireParser
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentDownloadRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventListRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventReadRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingReadRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentOperateRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrizeRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentRemoteFailure
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentRemoteFailureKind
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionReadRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Транспорт соревнования на подставном обмене (образец арены): тела вызовов — ровно allowlist
 * сервера, отказы читаются структурной причиной, забор смены аккаунта действует до и после сети,
 * испорченный провод — типовой отказ. Firebase SDK не поднимается.
 */
class FirebaseTournamentRemoteDataSourceTest {
    private val fixture = loadFixture()

    @Test
    fun `calls carry exactly the callable allowlist and read their projections`() =
        runTest {
            val f = fake(this)
            val read = f.remote.readEvent(TournamentEventReadRequest("player", "event_fixture_00000001")).getOrThrow()
            assertEquals(FirebaseTournamentRemoteDataSource.READ_EVENT, f.calls[0].first)
            assertEquals(mapOf("eventId" to "event_fixture_00000001"), f.calls[0].second)
            assertEquals("QUALIFICATION", read.event.status)
            assertEquals(10L, read.event.prizes.maximumLiabilityBoxes)

            f.reply = fixture.getValue("listEventsFirst").toSdk()
            val page = f.remote.listEvents(TournamentEventListRequest("player", pageSize = 25)).getOrThrow()
            assertEquals(FirebaseTournamentRemoteDataSource.LIST_EVENTS, f.calls.last().first)
            assertEquals(mapOf("pageSize" to 25), f.calls.last().second)
            assertTrue(page.hasMore)
            f.reply = fixture.getValue("listEventsLast").toSdk()
            f.remote.listEvents(TournamentEventListRequest("player", page.nextEventId, 25)).getOrThrow()
            assertEquals(mapOf("afterEventId" to page.nextEventId, "pageSize" to 25), f.calls.last().second)

            f.reply = fixture.getValue("readSessionStarted").toSdk()
            f.remote.readSession(TournamentSessionReadRequest("player", "q_" + "a".repeat(64))).getOrThrow()
            assertEquals(FirebaseTournamentRemoteDataSource.READ_SESSION, f.calls.last().first)

            f.reply = fixture.getValue("meetingPage").toSdk()
            f.remote.readMeeting(
                TournamentMeetingReadRequest("player", "event_fixture_00000001", "match-pending-1", pageSize = 25),
            ).getOrThrow()
            assertEquals(FirebaseTournamentRemoteDataSource.READ_MEETING, f.calls.last().first)
            assertEquals(
                mapOf("eventId" to "event_fixture_00000001", "matchId" to "match-pending-1", "pageSize" to 25),
                f.calls.last().second,
            )
        }

    @Test
    fun `operation bodies never carry ownerUid and create freezes the v1 prize list`() =
        runTest {
            val f = fake(this)
            f.reply = fixture.getValue("startView").toSdk()
            f.remote.operate(
                TournamentOperateRequest.Create(
                    ownerUid = "player",
                    operationId = "client_create_00000001",
                    eventId = "event_fixture_00000001",
                    source = com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSourceRef("COURSE", "course"),
                    snapshotId = "bank_" + "5".repeat(64),
                    startsAtMs = 1788739200000L,
                    prizeRows = listOf(TournamentPrizeRow(1, 5), TournamentPrizeRow(2, 3)),
                    fundedBoxes = 12,
                ),
            ).getOrThrow()
            assertEquals(FirebaseTournamentRemoteDataSource.OPERATE, f.calls.last().first)
            val expectedPayload =
                mapOf(
                    "operationId" to "client_create_00000001",
                    "eventId" to "event_fixture_00000001",
                    "kind" to "CREATE",
                    "source" to mapOf("kind" to "COURSE", "id" to "course", "snapshotId" to "bank_" + "5".repeat(64)),
                    "startsAtMs" to 1788739200000L,
                    "prizes" to mapOf("listVersion" to 1, "rows" to listOf(mapOf("place" to 1, "amountBoxes" to 5L), mapOf("place" to 2, "amountBoxes" to 3L))),
                    "fundedBoxes" to 12L,
                )
            // Пров OC плоско канонизирует числа; сравниваем те же детерминированные JSON-формы.
            assertEquals(
                com.tpov.schoolquiz.platform.firebase.network.mutationResponseJson(expectedPayload),
                com.tpov.schoolquiz.platform.firebase.network.mutationResponseJson(f.calls.last().second),
            )
            f.remote.operate(
                TournamentOperateRequest.Ready("player", "client_ready_0000001", "event_fixture_00000001", "match-pending-1"),
            ).getOrThrow()
            assertEquals(
                mapOf(
                    "operationId" to "client_ready_0000001",
                    "eventId" to "event_fixture_00000001",
                    "kind" to "READY",
                    "matchId" to "match-pending-1",
                ),
                f.calls.last().second,
            )
        }

    @Test
    fun `download record sends the identity pair and surfaces the replay flag`() =
        runTest {
            val f = fake(this)
            f.reply = fixture.getValue("downloadFirst").toSdk()
            val first = f.remote.recordDownload(TournamentDownloadRequest("player", "client_dl_0000001", "course")).getOrThrow()
            assertEquals(FirebaseTournamentRemoteDataSource.RECORD_DOWNLOAD, f.calls.last().first)
            assertEquals(mapOf("operationId" to "client_dl_0000001", "courseId" to "course"), f.calls.last().second)
            assertTrue(!first.replayed)
            f.reply = fixture.getValue("downloadReplay").toSdk()
            assertTrue(f.remote.recordDownload(TournamentDownloadRequest("player", "client_dl_0000002", "course")).getOrThrow().replayed)
        }

    @Test
    fun `structured reasons map onto typed sync errors and never onto message text`() =
        runTest {
            val refusals =
                listOf(
                    Triple("PERMISSION_DENIED", "PERMISSION_DENIED", SyncError.Refused("PERMISSION_DENIED")),
                    Triple("ALREADY_EXISTS", "CONFLICTING_REPLAY", SyncError.Refused("CONFLICTING_REPLAY")),
                    Triple("ALREADY_EXISTS", "PREPARATION_REUSE", SyncError.Refused("PREPARATION_REUSE")),
                    // L2: типизированные отказы допуска теперь живут на живом проводе — сервер
                    // (`fail(admission.reason, admission.reason)` + `courseTournamentFailure`)
                    // отвечает failed-precondition с details.reason, а не permission-denied.
                    Triple("FAILED_PRECONDITION", "NO_ADMISSION_BASIS", SyncError.Refused("NO_ADMISSION_BASIS")),
                    Triple("FAILED_PRECONDITION", "OUTSIDE_QUALIFICATION_WINDOW", SyncError.Refused("OUTSIDE_QUALIFICATION_WINDOW")),
                    Triple("FAILED_PRECONDITION", "QUESTION_ALREADY_CLOSED", SyncError.Refused("QUESTION_ALREADY_CLOSED")),
                    Triple("FAILED_PRECONDITION", "ANSWER_PENDING", SyncError.Refused("ANSWER_PENDING")),
                    Triple("ALREADY_EXISTS", "STALE_PREPARATION", SyncError.Refused("STALE_PREPARATION")),
                    // Подготовка и CREATE отказывают поимённо и окончательно.
                    Triple("FAILED_PRECONDITION", "SOURCE_NOT_ELIGIBLE", SyncError.Refused("SOURCE_NOT_ELIGIBLE")),
                    Triple("FAILED_PRECONDITION", "RESOURCE_EXHAUSTED", SyncError.Refused("RESOURCE_EXHAUSTED")),
                    Triple("RESOURCE_EXHAUSTED", "SOURCE_TOO_LARGE", SyncError.Refused("SOURCE_TOO_LARGE")),
                    // Сервер занят или снапшот требует починки: повторяемо, но не «сети нет».
                    Triple("UNAVAILABLE", "SCORING_UNAVAILABLE", SyncError.Unknown()),
                    Triple("UNAVAILABLE", "INCONSISTENT_SESSION", SyncError.Unknown()),
                    // Неизвестная причина будущего сервера — типовое «неизвестно», не крах.
                    Triple("ALREADY_EXISTS", "FUTURE_REASON", SyncError.Unknown()),
                )
            for ((code, reason, expected) in refusals) {
                val f = fake(this)
                f.beforeReply = { throw TournamentCallableException(code, mapOf("reason" to reason)) }
                val failure = f.remote.readEvent(TournamentEventReadRequest("player", "event_fixture_00000001"))
                assertEquals("reason $reason", expected, (failure.exceptionOrNull() as SyncFailure).error)
            }
        }

    @Test
    fun `missing reason falls back to the shared code table`() =
        runTest {
            val f = fake(this)
            f.beforeReply = { throw TournamentCallableException("PERMISSION_DENIED", mapOf("other" to 1)) }
            val failure = f.remote.readEvent(TournamentEventReadRequest("player", "event_fixture_00000001"))
            assertEquals(SyncError.Refused("PERMISSION_DENIED"), (failure.exceptionOrNull() as SyncFailure).error)
            val g = fake(this)
            g.beforeReply = { throw TournamentCallableException("UNAVAILABLE", null) }
            val unavailable = g.remote.readEvent(TournamentEventReadRequest("player", "event_fixture_00000001"))
            assertEquals(SyncError.NoNetwork, (unavailable.exceptionOrNull() as SyncFailure).error)
        }

    @Test
    fun `malformed wire and broken envelopes are typed decode failures`() =
        runTest {
            val f = fake(this)
            f.reply = mapOf("event" to mapOf("eventId" to "x"), "serverNow" to 1)
            failure(f.remote.readEvent(TournamentEventReadRequest("player", "event_fixture_00000001")), TournamentRemoteFailureKind.MALFORMED_RESPONSE)
            val g = fake(this)
            g.reply = "not-an-object"
            failure(g.remote.readEvent(TournamentEventReadRequest("player", "event_fixture_00000001")), TournamentRemoteFailureKind.MALFORMED_RESPONSE)
        }

    @Test
    fun `anonymous missing or different owner cannot send`() =
        runTest {
            val f = fake(this)
            f.session = null
            failure(f.remote.readEvent(TournamentEventReadRequest("player", "event_fixture_00000001")), TournamentRemoteFailureKind.ACCOUNT_UNAVAILABLE)
            f.session = OutboxSession("other", 2)
            failure(f.remote.readEvent(TournamentEventReadRequest("player", "event_fixture_00000001")), TournamentRemoteFailureKind.ACCOUNT_CHANGED)
            assertTrue(f.calls.isEmpty())
        }

    @Test
    fun `epoch change before during and after the call refuses the response`() =
        runTest {
            for (changed in listOf(OutboxSession("other", 2), OutboxSession("player", 3))) {
                val f = fake(this)
                f.beforeReply = { f.session = changed }
                failure(f.remote.readEvent(TournamentEventReadRequest("player", "event_fixture_00000001")), TournamentRemoteFailureKind.ACCOUNT_CHANGED)
                assertEquals(1, f.calls.size)
            }
        }

    @Test
    fun `no network does not send and lost replies leave the call unchanged`() =
        runTest {
            val f = fake(this)
            f.online = false
            assertEquals(SyncError.NoNetwork, (f.remote.readEvent(TournamentEventReadRequest("player", "e")).exceptionOrNull() as SyncFailure).error)
            assertTrue(f.calls.isEmpty())
            f.online = true
            f.beforeReply = { throw IOException("lost reply") }
            assertTrue(f.remote.readEvent(TournamentEventReadRequest("player", "e")).isFailure)
            f.beforeReply = { }
            f.reply = fixture.getValue("eventReadQualification").toSdk()
            f.remote.readEvent(TournamentEventReadRequest("player", "e")).getOrThrow()
            assertEquals(2, f.calls.size)
            assertEquals(f.calls.first(), f.calls.last())
        }

    @Test
    fun `coroutine cancellation is rethrown even if the owner also changes`() =
        runTest {
            val f = fake(this)
            val cancelled = CancellationException("cancelled test")
            f.beforeReply = {
                f.session = OutboxSession("other", 2)
                throw cancelled
            }
            try {
                f.remote.readEvent(TournamentEventReadRequest("player", "e"))
                throw AssertionError("Cancellation must propagate")
            } catch (actual: CancellationException) {
                assertEquals(cancelled.message, actual.message)
            }
        }

    @Test
    fun `callable names stay bound to the actual server sources`() {
        val index = File(findRepoRoot(), "functions/index.js").readText()
        for (name in
            listOf(
                FirebaseTournamentRemoteDataSource.PREPARE,
                FirebaseTournamentRemoteDataSource.OPERATE,
                FirebaseTournamentRemoteDataSource.READ_EVENT,
                FirebaseTournamentRemoteDataSource.READ_SESSION,
                FirebaseTournamentRemoteDataSource.READ_MEETING,
                FirebaseTournamentRemoteDataSource.LIST_EVENTS,
                FirebaseTournamentRemoteDataSource.RECORD_DOWNLOAD,
            )
        ) {
            assertTrue("missing callable $name", index.contains("exports.$name = onCall("))
        }
        // Мутации сессии в транспорте нет: приёмник очереди — единственный её адрес (AD-6).
        val source = File(findRepoRoot(), "functions/index.js").readText()
        assertTrue(source.contains("\"tournament.SESSION_OP\""))
    }

    private fun loadFixture(): JsonObject {
        val path = "shared/feature/tournament/data/src/jvmTest/resources/tournament-client-fixtures.json"
        return Json.parseToJsonElement(File(findRepoRoot(), path).readText()).jsonObject
    }

    private fun findRepoRoot(): File =
        generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }

    private inner class Fake(scope: kotlinx.coroutines.test.TestScope) {
        var session: OutboxSession? = OutboxSession("player", 1)
        var online = true
        var reply: Any? = fixture.getValue("eventReadQualification").toSdk()
        var beforeReply: suspend () -> Unit = {}
        val sessions = OutboxSessionProvider { session }
        val calls = mutableListOf<Pair<String, Map<String, Any?>>>()
        val remote =
            FirebaseTournamentRemoteDataSource(
                exchange =
                    TournamentCallableExchange { name, payload ->
                        calls += name to payload.toMap()
                        TournamentPendingCall {
                            beforeReply()
                            reply
                        }
                    },
                sessions = sessions,
                networkMonitor =
                    object : NetworkMonitor {
                        override fun observeOnline(): Flow<Boolean> = flowOf(online)

                        override suspend fun isOnline(): Boolean = online
                    },
                dispatcher = StandardTestDispatcher(scope.testScheduler),
                eventParser = TournamentEventWireParser(),
                meetingParser = TournamentMeetingWireParser(),
                sessionParser =
                    TournamentSessionWireParser(
                        events = TournamentEventWireParser(),
                        meetings = TournamentMeetingWireParser(),
                    ),
            )
    }

    private fun fake(scope: kotlinx.coroutines.test.TestScope): Fake = Fake(scope)

    private fun failure(
        result: Result<*>,
        kind: TournamentRemoteFailureKind,
    ) {
        assertEquals(kind, (result.exceptionOrNull() as TournamentRemoteFailure).kind)
    }

    private fun JsonElement.toSdk(): Any? =
        when (this) {
            JsonNull -> null
            is JsonObject -> mapValues { it.value.toSdk() }
            is JsonArray -> map { it.toSdk() }
            is JsonPrimitive ->
                when {
                    isString -> content
                    booleanOrNull != null -> booleanOrNull
                    else -> longOrNull ?: doubleOrNull!!
                }
        }
}
