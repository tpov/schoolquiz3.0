package com.tpov.schoolquiz.platform.firebase.arena

import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.tpov.schoolquiz.platform.firebase.auth.FakeMutationReceiptStore
import com.tpov.schoolquiz.platform.firebase.network.fake.EmptyOutbox
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.MutationTransport
import com.tpov.schoolquiz.shared.core.outbox.NoLocalEffect
import com.tpov.schoolquiz.shared.core.outbox.OutboxEngine
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.OutboxArenaOwnerGate
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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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
        // Re-pinned 2026-10-20: pins lagged behind arena epic work committed after the
        // 2026-09-07 fixture generation (c5895b65, 51fb2614, bc4b8627, 8dc782ea, 34fe664b —
        // eligible-universe extraction and settlement fixes). Wire payloads unchanged: every
        // fixture-payload test stayed green, so only the recorded SHAs were re-pinned,
        // mirroring the 2.13 functions-contract-drift precedent. Test-only.
        // Re-pinned 2026-10-23: arena-tickets.js drifted in the epic-8 session (45f7e9c8, private
        // quizzes) without a re-pin — that session's gate did not include :platform:firebase.
        // Wire payloads unchanged again: 333 tests, only the SHA assertion failed. Test-only.
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

    @Test
    fun plainCallsCaptureBeforeTheFirstDispatcherSuspension() =
        runTest {
            repeat(4) { operation ->
                val f = Fake(this)
                val result = async(start = CoroutineStart.UNDISPATCHED) { plain(f, operation) }
                f.session = OutboxSession("other", 2)
                f.session = OutboxSession("player", 3)
                failure(result.await(), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
                assertTrue(f.calls.isEmpty())
                assertEquals(0, f.networkReads)
            }
        }

    @Test
    fun allBoundCallsRejectAnOldEpochBeforePrimitiveEntryOnStandardAndImmediateDispatchers() =
        runTest {
            for (immediate in listOf(false, true)) {
                repeat(4) { operation ->
                    val f = Fake(this, immediate)
                    val original = requireNotNull(f.session)
                    f.session = OutboxSession("other", 2)
                    f.session = OutboxSession("player", 3)
                    failure(bound(f, operation, original), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
                    assertEquals(0, f.networkReads)
                    assertTrue(f.calls.isEmpty())
                }
            }
        }

    @Test
    fun allBoundCallsRetainTheOriginalEpochThroughConnectivitySuspension() =
        runTest {
            for (immediate in listOf(false, true)) {
                repeat(4) { operation ->
                    val f = Fake(this, immediate)
                    val original = requireNotNull(f.session)
                    f.beforeOnline = {
                        f.session = OutboxSession("other", 2)
                        f.session = OutboxSession("player", 3)
                    }
                    failure(bound(f, operation, original), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
                    assertTrue(f.calls.isEmpty())
                }
            }
        }

    @Test
    fun stalledReplyDoesNotHoldTheActualOwnerGateAndCannotBeAcceptedAfterSwitch() =
        runTest {
            for (immediate in listOf(false, true)) {
                val f = Fake(this, immediate)
                val original = requireNotNull(f.session)
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                f.beforeReply = {
                    entered.complete(Unit)
                    release.await()
                }
                val call = async { f.remote.prepare(original, selected) }
                entered.await()
                withTimeout(1000) {
                    f.engine.withoutPasses {
                        f.session = OutboxSession("other", 2)
                        f.session = OutboxSession("player", 3)
                    }
                }
                release.complete(Unit)
                failure(call.await(), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
                assertEquals(1, f.calls.size)
            }
        }

    @Test
    fun controlledSwitchCannotEnterBetweenGuardAndSynchronousTaskStart() =
        runTest {
            for (immediate in listOf(false, true)) {
                val f = Fake(this, immediate)
                val original = requireNotNull(f.session)
                var switch: Deferred<Unit>? = null
                var startObservedOriginal = false
                f.beforeStart = {
                    switch =
                        async(start = CoroutineStart.UNDISPATCHED) {
                            f.engine.withoutPasses { f.session = OutboxSession("player", 3) }
                        }
                    startObservedOriginal = f.session == original && switch?.isCompleted == false
                }
                f.beforeReply = { requireNotNull(switch).await() }
                failure(f.remote.prepare(original, selected), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
                assertTrue(startObservedOriginal)
                assertEquals(1, f.calls.size)
            }
        }

    @Test
    fun cancellationWhileWaitingForTheStartGateCreatesNoSdkTask() =
        runTest {
            val f = Fake(this)
            val held = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val ownerOperation =
                async {
                    f.engine.withoutPasses {
                        held.complete(Unit)
                        release.await()
                    }
                }
            held.await()
            val checking = CompletableDeferred<Unit>()
            f.beforeOnline = { checking.complete(Unit) }
            val call = async { f.remote.prepare(requireNotNull(f.session), selected) }
            checking.await()
            call.cancelAndJoin()
            release.complete(Unit)
            ownerOperation.await()
            assertTrue(call.isCancelled)
            assertTrue(f.calls.isEmpty())
        }

    @Test
    fun freshExplicitBoundRetryUsesOriginalConsentAfterAnUncertainOldEpochReply() =
        runTest {
            val f = Fake(this)
            val original = requireNotNull(f.session)
            f.beforeReply = {
                f.session = OutboxSession("player", 3)
                throw IOException("lost reply")
            }
            failure(f.remote.prepare(original, selected), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
            f.beforeReply = {}
            failure(f.remote.prepare(original, selected), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
            f.remote.prepare(requireNotNull(f.session), selected).getOrThrow()
            assertEquals(2, f.calls.size)
            assertEquals(f.calls.first(), f.calls.last())
            assertEquals(12, f.calls.last().second["maxPriceNolics"])
            assertEquals("client-selected", f.calls.last().second["requestId"])
        }

    @Test
    fun mockedSdkMetadataAtBothTaskStartAndAwaitKeepsExactStructuredCodeAndDetails() =
        runTest {
            for (atStart in listOf(true, false)) {
                val functions = mockk<FirebaseFunctions>()
                val reference = mockk<HttpsCallableReference>(relaxed = true)
                every { functions.getHttpsCallable("prepareArenaEntry") } returns reference
                val details = mapOf("reason" to "RETRY_REGEN")
                val sdkError = mockk<FirebaseFunctionsException>(relaxed = true)
                every { sdkError.code } returns FirebaseFunctionsException.Code.UNAVAILABLE
                every { sdkError.details } returns details
                if (atStart) {
                    every { reference.call(any()) } throws sdkError
                } else {
                    every { reference.call(any()) } returns Tasks.forException(sdkError)
                }
                val actual =
                    try {
                        firebaseArenaCallableExchange(functions).start("prepareArenaEntry", emptyMap()).await()
                        throw AssertionError("SDK failure must escape as structured metadata")
                    } catch (error: ArenaCallableException) {
                        error
                    }
                assertEquals("UNAVAILABLE", actual.code)
                assertEquals(details, actual.details)
                assertTrue(actual.cause === sdkError || actual.cause?.cause === sdkError)
            }
        }

    private suspend fun plain(
        f: Fake,
        operation: Int,
    ): Result<*> =
        when (operation) {
            0 -> f.remote.prepare(selected)
            1 -> f.remote.list(ArenaTicketListRequest("player"))
            2 -> f.remote.get(getRequest())
            else -> f.remote.cancel(cancelRequest())
        }

    private suspend fun bound(
        f: Fake,
        operation: Int,
        expected: OutboxSession,
    ): Result<*> =
        when (operation) {
            0 -> f.remote.prepare(expected, selected)
            1 -> f.remote.list(expected, ArenaTicketListRequest("player"))
            2 -> f.remote.get(expected, getRequest())
            else -> f.remote.cancel(expected, cancelRequest())
        }

    private fun cancelRequest() =
        ArenaTicketCancelRequest(
            ArenaTicketWireParser(
                ::arenaSha256,
            ).get(fixture.getValue("recoveredSelected").toString(), getRequest()).snapshot.header,
            "same-cancel",
        )

    private fun TestScope.fake(): Fake = Fake(this)

    private inner class Fake(scope: TestScope, immediate: Boolean = false) {
        var session: OutboxSession? = OutboxSession("player", 1)
        var online = true
        var networkReads = 0
        var reply: Any? = fixture.getValue("issuedSelected").toSdk()
        var beforeOnline: suspend () -> Unit = {}
        var beforeReply: suspend () -> Unit = {}
        var beforeStart: () -> Unit = {}
        val sessions = OutboxSessionProvider { session }
        val engine = arenaSessionTestEngine(sessions)
        val gate = OutboxArenaOwnerGate(sessions, engine)
        val calls = mutableListOf<Pair<String, Map<String, Any?>>>()
        val remote =
            FirebaseArenaTicketRemoteDataSource(
                exchange =
                    ArenaCallableExchange { name, payload ->
                        beforeStart()
                        calls += name to payload.toMap()
                        ArenaPendingCall {
                            beforeReply()
                            reply
                        }
                    },
                gate = gate,
                sessions = sessions,
                networkMonitor =
                    object : NetworkMonitor {
                        override fun observeOnline(): Flow<Boolean> = flowOf(online)

                        override suspend fun isOnline(): Boolean {
                            networkReads++
                            beforeOnline()
                            return online
                        }
                    },
                dispatcher =
                    if (immediate) {
                        kotlinx.coroutines.test.UnconfinedTestDispatcher(
                            scope.testScheduler,
                        )
                    } else {
                        StandardTestDispatcher(scope.testScheduler)
                    },
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

/** Reuse existing platform fakes; the gate/engine are real and no test starts an outbox pass. */
internal fun arenaSessionTestEngine(
    sessions: OutboxSessionProvider,
    transport: MutationTransport = MutationTransport { error("A test must not start a settlement sender") },
): OutboxEngine =
    OutboxEngine(
        store = EmptyOutbox(),
        transport = transport,
        clock = { 0L },
        onQuarantined = NoLocalEffect(),
        receipts = FakeMutationReceiptStore(),
        sessions = sessions,
    )
