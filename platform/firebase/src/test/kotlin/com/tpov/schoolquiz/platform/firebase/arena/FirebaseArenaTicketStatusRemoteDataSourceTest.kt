package com.tpov.schoolquiz.platform.firebase.arena

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.OutboxArenaOwnerGate
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketStatusRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatusObservation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseArenaTicketStatusRemoteDataSourceTest {
    private val repo =
        generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, FIXTURE_PATH).isFile }
    private val fixtures = Json.parseToJsonElement(File(repo, FIXTURE_PATH).readText()).jsonObject

    @Test
    fun separateStatusPortSendsOnlyTicketIdAndNeverUsesOldGetOrCreatesAnIdentity() =
        runTest {
            val f = Fake(this)
            val request = request()
            val first = f.status.status(request).getOrThrow()
            val second = f.status.status(request).getOrThrow()
            assertTrue(first is ArenaTicketStatusObservation.Reserved)
            assertEquals(first, second)
            assertEquals(
                listOf(
                    FirebaseArenaTicketRemoteDataSource.STATUS to mapOf("ticketId" to request.ticketId),
                    FirebaseArenaTicketRemoteDataSource.STATUS to mapOf("ticketId" to request.ticketId),
                ),
                f.calls.toList(),
            )
        }

    @Test
    fun actualCancelledAndSettledReceiptsAreReturnedWithoutLoadingAnyQuestions() =
        runTest {
            val f = Fake(this)
            f.reply = projection("recoveredCancelled").toSdk()
            val cancelled = f.status.status(request("recoveredCancelled")).getOrThrow()
            assertTrue(cancelled is ArenaTicketStatusObservation.Cancelled)
            f.reply = projection("recoveredSettled").toSdk()
            val settled = f.status.status(request("recoveredSettled")).getOrThrow()
            assertTrue(settled is ArenaTicketStatusObservation.Settled)
            assertEquals(100.0, (settled as ArenaTicketStatusObservation.Settled).receipt.percentScore, 0.0)
            assertEquals(2, f.calls.size)
            assertTrue(f.calls.all { it.first == FirebaseArenaTicketRemoteDataSource.STATUS })
        }

    @Test
    fun absentOrForeignSessionCannotSendEvenAReadOnlyStatusRequest() =
        runTest {
            val f = Fake(this)
            f.session = null
            failure(f.status.status(request()), ArenaRemoteFailureKind.ACCOUNT_UNAVAILABLE)
            f.session = OutboxSession("other", 2)
            failure(f.status.status(request()), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
            assertEquals(0, f.networkReads)
            assertTrue(f.calls.isEmpty())
        }

    @Test
    fun offlineStatusFailsWithoutFabricatingAnObservationOrCallingFunctions() =
        runTest {
            val f = Fake(this)
            f.online = false
            assertEquals(SyncError.NoNetwork, (f.status.status(request()).exceptionOrNull() as SyncFailure).error)
            assertTrue(f.calls.isEmpty())
        }

    @Test
    fun ownerGenerationChangeDuringConnectivityCheckIsRejectedBeforeAwaitingFunctions() =
        runTest {
            val f = Fake(this)
            f.beforeOnline = { f.session = OutboxSession("player", 3) }
            failure(f.status.status(request()), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
            assertTrue(f.calls.isEmpty())
        }

    @Test
    fun lateSuccessAndLateErrorCannotCrossAtoBOrAtoBtoAOwnerEpochs() =
        runTest {
            for (changed in listOf(OutboxSession("other", 2), OutboxSession("player", 3))) {
                val success = Fake(this)
                success.beforeReply = { success.session = changed }
                failure(success.status.status(request()), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
                assertEquals(1, success.calls.size)
                val error = Fake(this)
                error.beforeReply = {
                    error.session = changed
                    throw ArenaCallableException("UNAVAILABLE", mapOf("reason" to "RETRY_REGEN"))
                }
                failure(error.status.status(request()), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
                assertEquals(1, error.calls.size)
            }
        }

    @Test
    fun sdkDoubleIntegersAreSupportedButNanAndMissingReceiptValuesAreNotZeros() =
        runTest {
            val f = Fake(this)
            f.reply = projection("recoveredSettled").toSdk(allNumbersAsDouble = true)
            assertTrue(f.status.status(request("recoveredSettled")).isSuccess)
            val root = (f.reply as Map<*, *>).toMutableMap()
            val ticket = (root["ticket"] as Map<*, *>).toMutableMap()
            val terminal = (ticket["terminal"] as Map<*, *>).toMutableMap()
            root["ticket"] = ticket
            ticket["terminal"] = terminal
            terminal["percentScore"] = Double.NaN
            f.reply = root
            failure(f.status.status(request("recoveredSettled")), ArenaRemoteFailureKind.MALFORMED_RESPONSE)
            terminal.remove("percentScore")
            failure(f.status.status(request("recoveredSettled")), ArenaRemoteFailureKind.MALFORMED_RESPONSE)
        }

    @Test
    fun notFoundUnavailableAndRetrySignalsRemainFailuresInsteadOfSyntheticTerminalObservations() =
        runTest {
            for ((code, details) in listOf(
                "NOT_FOUND" to null,
                "UNAVAILABLE" to null,
                "UNAVAILABLE" to mapOf("reason" to "RETRY_REGEN"),
                "FAILED_PRECONDITION" to mapOf("reason" to "PRICE_CHANGED"),
            )) {
                val f = Fake(this)
                f.beforeReply = { throw ArenaCallableException(code, details) }
                assertTrue(f.status.status(request()).exceptionOrNull() is SyncFailure)
                assertEquals(1, f.calls.size)
            }
        }

    @Test
    fun cancellationEscapesEvenWhenTheOwnerChangesDuringAwait() =
        runTest {
            val f = Fake(this)
            val original = CancellationException("status cancelled")
            f.beforeReply = {
                f.session = OutboxSession("other", 2)
                throw original
            }
            try {
                f.status.status(request())
                throw AssertionError("Cancellation must propagate")
            } catch (actual: CancellationException) {
                assertEquals(original::class, actual::class)
                assertEquals(original.message, actual.message)
                assertTrue(actual === original || actual.cause === original)
            }
        }

    @Test
    fun ordinaryTransportFailureDoesNotRetryOrReturnAnEmptyStatus() =
        runTest {
            val f = Fake(this)
            f.beforeReply = { throw IOException("lost reply") }
            assertTrue(f.status.status(request()).exceptionOrNull() is SyncFailure)
            assertEquals(1, f.calls.size)
        }

    @Test
    fun oldIssuedEnvelopeAndForeignResponseOwnerAreRejectedByTheBoundParser() =
        runTest {
            val f = Fake(this)
            f.reply = fixtures.getValue("recoveredSelected").toSdk()
            failure(f.status.status(request()), ArenaRemoteFailureKind.MALFORMED_RESPONSE)
            val root = projection("recoveredSelected")
            val ticket = root.getValue("ticket").jsonObject
            f.reply = JsonObject(mapOf("ticket" to JsonObject(ticket + ("uid" to JsonPrimitive("other"))))).toSdk()
            failure(f.status.status(request()), ArenaRemoteFailureKind.MALFORMED_RESPONSE)
        }

    @Test
    fun statusCallableNameIsGuardedAgainstTheActualServerExport() {
        val name = FirebaseArenaTicketRemoteDataSource.STATUS
        assertEquals("fetchArenaEntryTicketStatus", name)
        assertTrue(name != FirebaseArenaTicketRemoteDataSource.GET)
        val index = File(repo, "functions/index.js").readText()
        assertTrue(index.contains("exports." + name + " = onCall("))
    }

    @Test
    fun plainStatusCapturesBeforeDispatcherAndCannotRenewAnOldCallerEpoch() =
        runTest {
            val f = Fake(this)
            val call = async(start = CoroutineStart.UNDISPATCHED) { f.status.status(request()) }
            f.session = OutboxSession("other", 2)
            f.session = OutboxSession("player", 3)
            failure(call.await(), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
            assertEquals(0, f.networkReads)
            assertTrue(f.calls.isEmpty())
        }

    @Test
    fun boundStatusRejectsOldEpochBeforeEntryAndAfterConnectivityOnBothDispatchers() =
        runTest {
            for (immediate in listOf(false, true)) {
                for (duringNetwork in listOf(false, true)) {
                    val f = Fake(this, immediate)
                    val original = requireNotNull(f.session)
                    if (duringNetwork) {
                        f.beforeOnline = { f.session = OutboxSession("player", 3) }
                    } else {
                        f.session = OutboxSession("player", 3)
                    }
                    failure(f.status.status(original, request()), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
                    assertTrue(f.calls.isEmpty())
                    assertEquals(if (duringNetwork) 1 else 0, f.networkReads)
                }
            }
        }

    @Test
    fun boundStatusAwaitReleasesTheOwnerGateButItsLateTerminalCannotBecomeFreshProof() =
        runTest {
            for (immediate in listOf(false, true)) {
                val f = Fake(this, immediate)
                val original = requireNotNull(f.session)
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                f.reply = projection("recoveredCancelled").toSdk()
                f.beforeReply = {
                    entered.complete(Unit)
                    release.await()
                }
                val call = async { f.status.status(original, request("recoveredCancelled")) }
                entered.await()
                withTimeout(1000) {
                    f.engine.withoutPasses { f.session = OutboxSession("player", 3) }
                }
                release.complete(Unit)
                failure(call.await(), ArenaRemoteFailureKind.ACCOUNT_CHANGED)
                assertEquals(1, f.calls.size)
            }
        }

    private inner class Fake(scope: TestScope, immediate: Boolean = false) {
        var session: OutboxSession? = OutboxSession("player", 1)
        var online = true
        var networkReads = 0
        var reply: Any? = projection("recoveredSelected").toSdk()
        var beforeOnline: suspend () -> Unit = {}
        var beforeReply: suspend () -> Unit = {}
        var beforeStart: () -> Unit = {}
        val sessions = OutboxSessionProvider { session }
        val engine = arenaSessionTestEngine(sessions)
        val gate = OutboxArenaOwnerGate(sessions, engine)
        val calls = mutableListOf<Pair<String, Map<String, Any?>>>()
        val status: ArenaTicketStatusRemoteDataSource =
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
                        override fun observeOnline() = flowOf(online)

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

    private fun projection(name: String): JsonObject {
        val ticket = fixtures.getValue(name).jsonObject.getValue("ticket").jsonObject
        return JsonObject(mapOf("ticket" to JsonObject(ticket - setOf("questions", "requestHash"))))
    }

    private fun request(name: String = "recoveredSelected") =
        ArenaTicketGetRequest(
            "player",
            projection(name).getValue("ticket").jsonObject
                .getValue("ticketId").jsonPrimitive.content,
        )

    private fun failure(
        result: Result<*>,
        kind: ArenaRemoteFailureKind,
    ) {
        assertEquals(kind, (result.exceptionOrNull() as ArenaRemoteFailure).kind)
    }

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
