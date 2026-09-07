package com.tpov.schoolquiz.platform.firebase.arena

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseArenaTicketRemoteDataSourceTest {
    private val repo =
        generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, FIXTURE_PATH).isFile }
    private val fixture = Json.parseToJsonElement(File(repo, FIXTURE_PATH).readText()).jsonObject
    private val selected = ArenaPrepareRequest.Selected("player", "client-selected", Difficulty.EASY, "lesson", 12)

    @Test
    fun selectedAlwaysSendsOwnerAssertionAndExactOriginalConsentWithoutRetryMutation() =
        runTest {
            val f = fake()
            val first = f.remote.prepare(selected).getOrThrow()
            val second = f.remote.prepare(selected).getOrThrow()
            assertEquals(first, second)
            assertEquals(2, f.calls.size)
            val expected =
                mapOf(
                    "expectedOwnerUid" to "player",
                    "requestId" to "client-selected",
                    "difficulty" to "EASY",
                    "mode" to "SELECTED",
                    "lessonId" to "lesson",
                    "maxPriceNolics" to 12,
                )
            assertEquals(FirebaseArenaTicketRemoteDataSource.PREPARE, f.calls[0].first)
            assertEquals(expected, f.calls[0].second)
            assertEquals(f.calls[0], f.calls[1])
        }

    @Test
    fun randomNeverInventsLessonOrCeilingAndReadonlyRecoveryHasNoAuthorityFields() =
        runTest {
            val f = fake()
            f.reply = fixture.getValue("issuedRandom").toSdk()
            f.remote.prepare(ArenaPrepareRequest.Random("player", "client-random", Difficulty.EASY)).getOrThrow()
            assertEquals(setOf("expectedOwnerUid", "requestId", "difficulty", "mode"), f.calls.last().second.keys)
            f.reply = fixture.getValue("listFirst").toSdk()
            val page = f.remote.list(ArenaTicketListRequest("player", pageSize = 2)).getOrThrow()
            assertEquals(FirebaseArenaTicketRemoteDataSource.LIST, f.calls.last().first)
            assertEquals(mapOf("pageSize" to 2), f.calls.last().second)
            f.reply = fixture.getValue("listLast").toSdk()
            f.remote.list(ArenaTicketListRequest("player", page.nextTicketId, 2)).getOrThrow()
            assertEquals(mapOf("pageSize" to 2, "afterTicketId" to page.nextTicketId), f.calls.last().second)
            f.reply = fixture.getValue("recoveredSelected").toSdk()
            f.remote.get(getRequest()).getOrThrow()
            assertEquals(FirebaseArenaTicketRemoteDataSource.GET, f.calls.last().first)
            assertEquals(mapOf("ticketId" to getRequest().ticketId), f.calls.last().second)
        }

    @Test
    fun cancelBindsOwnerAndOriginalRequestAndReadsTerminalWithoutNewIntent() =
        runTest {
            val f = fake()
            val header = (f.remote.prepare(selected).getOrThrow() as ArenaPrepareResponse.Ticket).ticket.snapshot.header
            f.reply = fixture.getValue("cancelled").toSdk()
            val request = ArenaTicketCancelRequest(header, "cancel-client-selected")
            assertTrue(f.remote.cancel(request).getOrThrow() is ArenaCancelResponse.Cancelled)
            assertEquals(FirebaseArenaTicketRemoteDataSource.CANCEL, f.calls.last().first)
            assertEquals(
                mapOf(
                    "expectedOwnerUid" to "player",
                    "ticketId" to header.ticketId,
                    "requestId" to request.requestId,
                ),
                f.calls.last().second,
            )
            f.reply = fixture.getValue("recoveredCancelled").toSdk()
            assertTrue(f.remote.get(getRequest()).getOrThrow() is ArenaEntryTicket.Cancelled)
            assertEquals(3, f.calls.size)
        }

    @Test
    fun anonymousMissingOrDifferentOwnerCannotSend() =
        runTest {
            val f = fake()
            f.session = null
            failure(f.remote.prepare(selected), ArenaRemoteFailureKind.ACCOUNT_UNAVAILABLE)
            f.session = OutboxSession("other", 2)
            failure(f.remote.prepare(selected), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
            assertTrue(f.calls.isEmpty())
        }

    @Test
    fun networkSuspendAccountChangeIsCaughtBeforeSending() =
        runTest {
            val f = fake()
            f.beforeOnline = { f.session = OutboxSession("other", 2) }
            failure(f.remote.prepare(selected), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
            assertTrue(f.calls.isEmpty())
        }

    @Test
    fun responseOfOldOwnerAndSameOwnerNewGenerationCannotBeAccepted() =
        runTest {
            for (changed in listOf(OutboxSession("other", 2), OutboxSession("player", 3))) {
                val f = fake()
                f.beforeReply = { f.session = changed }
                failure(f.remote.prepare(selected), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
                assertEquals(1, f.calls.size)
            }
        }

    @Test
    fun staleErrorOrReadonlyResponseAlsoFailsTheGenerationGuard() =
        runTest {
            val f = fake()
            f.beforeReply = {
                f.session = OutboxSession("player", 3)
                throw ArenaCallableException("FAILED_PRECONDITION", mapOf("reason" to "PRICE_CHANGED"))
            }
            failure(f.remote.prepare(selected), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
            f.session = OutboxSession("player", 4)
            f.beforeReply = { f.session = OutboxSession("other", 5) }
            f.reply = fixture.getValue("recoveredSelected").toSdk()
            failure(f.remote.get(getRequest()), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
        }

    @Test
    fun noNetworkDoesNotSendAndLostReplyRetriesOnlyTheOriginalImmutableCommand() =
        runTest {
            val f = fake()
            f.online = false
            assertEquals(SyncError.NoNetwork, (f.remote.prepare(selected).exceptionOrNull() as SyncFailure).error)
            assertTrue(f.calls.isEmpty())
            f.online = true
            f.beforeReply = { throw IOException("lost reply") }
            assertTrue(f.remote.prepare(selected).isFailure)
            f.beforeReply = {}
            f.remote.prepare(selected).getOrThrow()
            assertEquals(2, f.calls.size)
            assertEquals(f.calls.first(), f.calls.last())
        }

    @Test
    fun coroutineCancellationIsRethrownEvenIfOwnerAlsoChanges() =
        runTest {
            val f = fake()
            val cancelled = CancellationException("cancelled test")
            f.beforeReply = {
                f.session = OutboxSession("other", 2)
                throw cancelled
            }
            try {
                f.remote.prepare(selected)
                throw AssertionError("Cancellation must propagate")
            } catch (actual: CancellationException) {
                // JVM debug stacktrace recovery may copy at withContext and retain the original as cause.
                assertEquals(cancelled.message, actual.message)
                assertTrue(actual === cancelled || actual.cause === cancelled)
            }
        }

    @Test
    fun sdkDoubleIntegersAreCanonicalizedButNanAndMissingNumbersAreNotDefaults() =
        runTest {
            val f = fake()
            f.reply = fixture.getValue("issuedSelected").toSdk(allNumbersAsDouble = true)
            assertTrue(f.remote.prepare(selected).isSuccess)
            val root = (f.reply as Map<*, *>).toMutableMap()
            val ticket = (root["ticket"] as Map<*, *>).toMutableMap()
            ticket["lifeCost"] = Double.NaN
            root["ticket"] = ticket
            f.reply = root
            failure(f.remote.prepare(selected), ArenaRemoteFailureKind.MALFORMED_RESPONSE)
            ticket.remove("lifeCost")
            failure(f.remote.prepare(selected), ArenaRemoteFailureKind.MALFORMED_RESPONSE)
        }

    @Test
    fun exactSdkCodesAndStructuredReasonsProduceTypedSignalsOnly() =
        runTest {
            val f = fake()
            f.beforeReply = { throw ArenaCallableException("UNAVAILABLE", mapOf("reason" to "RETRY_REGEN")) }
            assertEquals(
                ArenaPrepareResponse.RetryRegeneration(selected.requestId),
                f.remote.prepare(selected).getOrThrow(),
            )
            val header =
                ArenaTicketWireParser(::arenaSha256).get(
                    fixture.getValue("recoveredSelected").toString(),
                    getRequest(),
                ).snapshot.header
            val cancel = ArenaTicketCancelRequest(header, "same-cancel")
            assertEquals(
                ArenaCancelResponse.RetryRegeneration(header.ticketId, "same-cancel"),
                f.remote.cancel(cancel).getOrThrow(),
            )
            f.beforeReply = { throw ArenaCallableException("FAILED_PRECONDITION", mapOf("reason" to "PRICE_CHANGED")) }
            assertEquals(ArenaPrepareResponse.PriceChanged, f.remote.prepare(selected).getOrThrow())
            assertTrue(f.remote.cancel(cancel).isFailure)
            f.beforeReply = { throw ArenaCallableException("PERMISSION_DENIED", mapOf("reason" to "ACCOUNT_CHANGED")) }
            failure(f.remote.prepare(selected), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
            for (details in listOf("RETRY_REGEN", mapOf("code" to "RETRY_REGEN"), mapOf("reason" to 1))) {
                assertEquals(ArenaCallableSignal.OTHER, arenaCallableSignal("UNAVAILABLE", details))
            }
            assertEquals(ArenaCallableSignal.OTHER, arenaCallableSignal("INTERNAL", mapOf("reason" to "RETRY_REGEN")))
            assertEquals(
                ArenaCallableSignal.OTHER,
                arenaCallableSignal("UNAVAILABLE", mapOf("reason" to "PRICE_CHANGED")),
            )
            assertEquals(
                ArenaCallableSignal.OTHER,
                arenaCallableSignal("FAILED_PRECONDITION", mapOf("reason" to "ACCOUNT_CHANGED")),
            )
        }

    @Test
    fun fixtureHashesAndCallableNamesAreBoundToActualServerSources() {
        for ((input, expected) in fixture.getValue("hashes").jsonObject) {
            assertEquals(expected.jsonPrimitive.content, arenaSha256(input))
        }
        val sources = fixture.getValue("sourceSha256").jsonObject
        // Pure producers must be regenerated on drift; unrelated index changes do not invalidate payload fixtures.
        for (name in listOf(
            "arena-tickets.js",
            "arena-ticket-recovery.js",
            "arena-policy.js",
            "question-redaction.js",
            "arena-entry-runtime.js",
            "arena-public-question.js",
        )) {
            assertEquals(
                "Regenerate actual Arena fixtures for $name",
                sources.getValue(name).jsonPrimitive.content,
                arenaSha256(File(repo, "functions/$name").readText()),
            )
        }
        val index = File(repo, "functions/index.js").readText()
        val names =
            listOf(
                FirebaseArenaTicketRemoteDataSource.PREPARE,
                FirebaseArenaTicketRemoteDataSource.LIST,
                FirebaseArenaTicketRemoteDataSource.GET,
                FirebaseArenaTicketRemoteDataSource.CANCEL,
            )
        assertEquals(4, names.distinct().size)
        names.forEach { name -> assertTrue("Missing actual callable $name", index.contains("exports.$name = onCall(")) }
        assertFalse(names.any { "settle" in it })
    }

    private fun TestScope.fake(): Fake = Fake(this)

    private inner class Fake(scope: TestScope) {
        var session: OutboxSession? = OutboxSession("player", 1)
        var online = true
        var reply: Any? = fixture.getValue("issuedSelected").toSdk()
        var beforeOnline: suspend () -> Unit = {}
        var beforeReply: suspend () -> Unit = {}
        val calls = mutableListOf<Pair<String, Map<String, Any?>>>()
        val remote =
            FirebaseArenaTicketRemoteDataSource(
                call = { name, payload ->
                    calls += name to payload.toMap()
                    beforeReply()
                    reply
                },
                sessions = OutboxSessionProvider { session },
                networkMonitor =
                    object : NetworkMonitor {
                        override fun observeOnline(): Flow<Boolean> = flowOf(online)

                        override suspend fun isOnline(): Boolean {
                            beforeOnline()
                            return online
                        }
                    },
                dispatcher = StandardTestDispatcher(scope.testScheduler),
            )
    }

    private fun failure(
        result: Result<*>,
        kind: ArenaRemoteFailureKind,
    ) {
        assertEquals(kind, (result.exceptionOrNull() as ArenaRemoteFailure).kind)
    }

    private fun getRequest(): ArenaTicketGetRequest =
        ArenaTicketGetRequest(
            "player",
            fixture.getValue("issuedSelected").jsonObject.getValue("ticket").jsonObject
                .getValue("ticketId").jsonPrimitive.content,
        )

    private fun JsonElement.toSdk(allNumbersAsDouble: Boolean = false): Any? =
        when (this) {
            JsonNull -> null
            is JsonObject -> mapValues { it.value.toSdk(allNumbersAsDouble) }
            is JsonArray -> map { it.toSdk(allNumbersAsDouble) }
            is JsonPrimitive ->
                when {
                    isString -> content
                    booleanOrNull != null -> booleanOrNull
                    allNumbersAsDouble -> doubleOrNull!!
                    else -> longOrNull ?: doubleOrNull!!
                }
        }

    private companion object {
        const val FIXTURE_PATH =
            "shared/feature/lesson-runner/data/src/jvmTest/resources/arena-ticket-client-fixtures.json"
    }
}
