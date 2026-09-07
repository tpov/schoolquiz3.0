@file:Suppress("MagicNumber") // Exact generated protocol and session-generation examples.

package com.tpov.schoolquiz.platform.firebase.arena

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseArenaLessonCatalogRemoteDataSourceTest {
    private val repo =
        generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, FIXTURE_PATH).isFile }
    private val fixture = Json.parseToJsonElement(File(repo, FIXTURE_PATH).readText()).jsonObject
    private val request = ArenaLessonCatalogRequest("player")

    @Test
    fun onlyExplicitReadPayloadLeavesTheAdapterIncludingPagination() =
        runTest {
            val f = Fake(this)
            f.reply = fixture.getValue("first").toSdk()
            val first = f.remote.list(request.copy(pageSize = 3)).getOrThrow()
            assertEquals(mapOf("pageSize" to 3), f.calls.single().second)
            f.reply = fixture.getValue("second").toSdk()
            f.remote.list(request.copy(pageSize = 3, cursor = first.nextCursor)).getOrThrow()
            assertEquals(mapOf("pageSize" to 3, "cursor" to first.nextCursor!!.token), f.calls.last().second)
            assertEquals(listOf("listArenaLessons", "listArenaLessons"), f.calls.map { it.first })
            assertEquals("player", first.ownerUid)
            // No wallet/cache/outbox/prepare capability is a dependency of this adapter.
            assertEquals(2, f.calls.size)
        }

    @Test
    fun missingOrDifferentAccountNeverCreatesAnonymousSessionOrSends() =
        runTest {
            val f = Fake(this)
            f.session = null
            failure(f.remote.list(request), ArenaLessonCatalogFailureKind.ACCOUNT_UNAVAILABLE)
            f.session = OutboxSession("other", 2)
            failure(f.remote.list(request), ArenaLessonCatalogFailureKind.ACCOUNT_CHANGED)
            assertTrue(f.calls.isEmpty())
        }

    @Test
    fun networkAwaitGenerationChangePreventsTheRead() =
        runTest {
            for (changed in listOf(OutboxSession("other", 2), OutboxSession("player", 3))) {
                val f = Fake(this)
                f.beforeOnline = { f.session = changed }
                failure(f.remote.list(request), ArenaLessonCatalogFailureKind.ACCOUNT_CHANGED)
                assertTrue(f.calls.isEmpty())
            }
        }

    @Test
    fun responseAfterAccountSwitchOrReturnToOriginalAccountIsNotAccepted() =
        runTest {
            for (changed in listOf(OutboxSession("other", 2), OutboxSession("player", 3))) {
                val f = Fake(this)
                f.beforeReply = { f.session = changed }
                failure(f.remote.list(request), ArenaLessonCatalogFailureKind.ACCOUNT_CHANGED)
                assertEquals(1, f.calls.size)
            }
        }

    @Test
    fun staleErrorsCannotChangeTheNewOwnerCatalogState() =
        runTest {
            val f = Fake(this)
            f.beforeReply = {
                f.session = OutboxSession("player", 3)
                throw ArenaCatalogCallableException("FAILED_PRECONDITION", mapOf("reason" to "CATALOG_CHANGED"))
            }
            failure(f.remote.list(request), ArenaLessonCatalogFailureKind.ACCOUNT_CHANGED)
            assertEquals(1, f.calls.size)
        }

    @Test
    fun cancellationPropagatesDuringNetworkOrResponseEvenWithOwnerChange() =
        runTest {
            for (duringNetwork in listOf(true, false)) {
                val f = Fake(this)
                val cancelled = CancellationException("test cancelled")
                val interrupt: suspend () -> Unit = {
                    f.session = OutboxSession("other", 2)
                    throw cancelled
                }
                if (duringNetwork) f.beforeOnline = interrupt else f.beforeReply = interrupt
                try {
                    f.remote.list(request)
                    throw AssertionError("Cancellation must propagate")
                } catch (actual: CancellationException) {
                    assertEquals(cancelled.message, actual.message)
                    assertTrue(actual === cancelled || actual.cause === cancelled)
                }
            }
        }

    @Test
    fun offlineAndLostReplyDoNotCauseAutomaticRetriesOrFallback() =
        runTest {
            val f = Fake(this)
            f.online = false
            assertEquals(SyncError.NoNetwork, (f.remote.list(request).exceptionOrNull() as SyncFailure).error)
            assertTrue(f.calls.isEmpty())
            f.online = true
            f.beforeReply = { throw IOException("private network diagnostic") }
            val failed = f.remote.list(request).exceptionOrNull() as SyncFailure
            assertEquals(SyncError.NoNetwork, failed.error)
            assertNull(failed.cause)
            assertEquals(1, f.calls.size)
            f.beforeReply = {}
            f.remote.list(request).getOrThrow()
            assertEquals(2, f.calls.size)
            assertEquals(f.calls.first(), f.calls.last())
        }

    @Test
    fun exactStructuredCodeReasonPairsMapToClosedTypedFailures() =
        runTest {
            val cases =
                listOf(
                    Triple("UNAUTHENTICATED", "UNAUTHENTICATED", ArenaLessonCatalogFailureKind.ACCOUNT_UNAVAILABLE),
                    Triple("INVALID_ARGUMENT", "INVALID_ARGUMENT", ArenaLessonCatalogFailureKind.INVALID_REQUEST),
                    Triple("FAILED_PRECONDITION", "CATALOG_CHANGED", ArenaLessonCatalogFailureKind.CATALOG_CHANGED),
                    Triple("RESOURCE_EXHAUSTED", "CATALOG_TOO_LARGE", ArenaLessonCatalogFailureKind.CATALOG_TOO_LARGE),
                    Triple("UNAVAILABLE", "CATALOG_INVALID", ArenaLessonCatalogFailureKind.CATALOG_INVALID),
                    Triple("INTERNAL", "SERVER_FAILURE", ArenaLessonCatalogFailureKind.SERVER_FAILURE),
                )
            for ((code, reason, expected) in cases) {
                val f = Fake(this)
                f.beforeReply = { throw ArenaCatalogCallableException(code, mapOf("reason" to reason)) }
                failure(f.remote.list(request), expected)
                assertEquals(1, f.calls.size)
            }
        }

    @Test
    fun malformedReasonsWrongCodeAndRawExceptionsNeverBecomePrivateMessages() =
        runTest {
            val f = Fake(this)
            for (details in listOf(
                null,
                "CATALOG_CHANGED",
                mapOf("code" to "CATALOG_CHANGED"),
                mapOf("reason" to 1),
                mapOf("reason" to "private/path"),
            )) {
                f.beforeReply = { throw ArenaCatalogCallableException("FAILED_PRECONDITION", details) }
                failure(f.remote.list(request), ArenaLessonCatalogFailureKind.SERVER_FAILURE)
            }
            f.beforeReply = { throw ArenaCatalogCallableException("INTERNAL", mapOf("reason" to "CATALOG_CHANGED")) }
            failure(f.remote.list(request), ArenaLessonCatalogFailureKind.SERVER_FAILURE)
            f.beforeReply = { error("private/reviewer/key") }
            failure(f.remote.list(request), ArenaLessonCatalogFailureKind.SERVER_FAILURE)
        }

    @Test
    fun sdkSafeIntegerDoublesAreAcceptedWithoutFloatScoreComparison() =
        runTest {
            val f = Fake(this)
            f.reply = fixture.getValue("full").toSdk(asDouble = true)
            val rows = f.remote.list(request).getOrThrow().lessons
            assertEquals("z-near", rows[1].lessonId)
            assertEquals("a-near", rows[2].lessonId)
            assertTrue(rows[1].rating.weightedScore > rows[2].rating.weightedScore)
        }

    @Test
    fun sdkUnrepresentableNumbersAndMissingAuthorityDoNotBecomeZero() =
        runTest {
            val f = Fake(this)
            for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.5, "0", null)) {
                val reply = (fixture.getValue("full").toSdk() as Map<*, *>).toMutableMap()
                reply["serverNowMs"] = bad
                f.reply = reply
                failure(f.remote.list(request), ArenaLessonCatalogFailureKind.MALFORMED_RESPONSE)
            }
            f.reply = (fixture.getValue("full").toSdk() as Map<*, *>).filterKeys { it != "serverNowMs" }
            failure(f.remote.list(request), ArenaLessonCatalogFailureKind.MALFORMED_RESPONSE)
            f.reply = listOf("not an SDK result object")
            failure(f.remote.list(request), ArenaLessonCatalogFailureKind.MALFORMED_RESPONSE)
        }

    @Test
    fun generatedPublicContractAndCallableNameRemainBoundToActualProducer() {
        val sources = fixture.getValue("sourceSha256").jsonObject
        // Pure protocol changes require actual regeneration. Additive unrelated index changes
        // do not invalidate payload fixtures; its generation-time SHA remains recorded.
        for (name in listOf("arena-lesson-catalog.js", "arena-policy.js")) {
            val bytes = MessageDigest.getInstance("SHA-256").digest(File(repo, "functions/$name").readBytes())
            val actual = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            assertEquals(sources.getValue(name).jsonPrimitive.content, actual)
        }
        assertEquals("listArenaLessons", FirebaseArenaLessonCatalogRemoteDataSource.LIST)
        assertTrue(
            File(
                repo,
                "functions/index.js",
            ).readText().contains("exports.${FirebaseArenaLessonCatalogRemoteDataSource.LIST} = onCall("),
        )
    }

    @Test
    fun catalogPlainReadCapturesBeforeDispatcherWithoutAddingAnyGateDependency() =
        runTest {
            val f = Fake(this)
            val call = async(start = CoroutineStart.UNDISPATCHED) { f.remote.list(request) }
            f.session = OutboxSession("other", 2)
            f.session = OutboxSession("player", 3)
            failure(call.await(), ArenaLessonCatalogFailureKind.ACCOUNT_CHANGED)
            assertTrue(f.calls.isEmpty())
        }

    @Test
    fun catalogAwaitedConnectivityRetainsEntryEpochOnStandardAndImmediateDispatchers() =
        runTest {
            for (immediate in listOf(false, true)) {
                val f = Fake(this, immediate)
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                f.beforeOnline = {
                    entered.complete(Unit)
                    release.await()
                }
                val call = async(start = CoroutineStart.UNDISPATCHED) { f.remote.list(request) }
                entered.await()
                f.session = OutboxSession("other", 2)
                f.session = OutboxSession("player", 3)
                release.complete(Unit)
                failure(call.await(), ArenaLessonCatalogFailureKind.ACCOUNT_CHANGED)
                assertTrue(f.calls.isEmpty())
            }
        }

    private inner class Fake(scope: TestScope, immediate: Boolean = false) {
        var session: OutboxSession? = OutboxSession("player", 1)
        var online = true
        var reply: Any? = fixture.getValue("full").toSdk()
        var beforeOnline: suspend () -> Unit = {}
        var beforeReply: suspend () -> Unit = {}
        val calls = mutableListOf<Pair<String, Map<String, Any?>>>()
        val remote =
            FirebaseArenaLessonCatalogRemoteDataSource(
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
        expected: ArenaLessonCatalogFailureKind,
    ) {
        val error = result.exceptionOrNull() as ArenaLessonCatalogFailure
        assertEquals(expected, error.kind)
        assertEquals(expected.name, error.message)
        assertNull(error.cause)
    }

    private fun JsonElement.toSdk(asDouble: Boolean = false): Any? =
        when (this) {
            JsonNull -> null
            is JsonObject -> mapValues { it.value.toSdk(asDouble) }
            is JsonArray -> map { it.toSdk(asDouble) }
            is JsonPrimitive ->
                when {
                    isString -> content
                    booleanOrNull != null -> booleanOrNull
                    asDouble -> requireNotNull(doubleOrNull)
                    else -> longOrNull ?: requireNotNull(doubleOrNull)
                }
        }

    private companion object {
        const val FIXTURE_PATH =
            "shared/feature/lesson-runner/data/src/jvmTest/resources/arena-catalog-client-fixtures.json"
    }
}
