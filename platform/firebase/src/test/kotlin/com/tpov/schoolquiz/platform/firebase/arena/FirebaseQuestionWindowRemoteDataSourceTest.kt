@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.platform.firebase.arena

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionDeliveryVerdict
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowView
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseQuestionWindowRemoteDataSourceTest {
    private val repo =
        generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "functions/index.js").isFile }

    @Test
    fun beginWindowSendsTheTransitionBodyAndParsesTheCapturedConfig() =
        runTest {
            val f = Fake(this)
            f.reply =
                mapOf(
                    "schemaVersion" to 1L,
                    "windowId" to "win_1a2b3c4d5e6f",
                    "attemptId" to "attempt-7",
                    "completedQuestionIndex" to 2L,
                    "nextQuestionIndex" to 3L,
                    "state" to "OPEN",
                    "windowSeconds" to 60L,
                    "allocatedSeconds" to 24L,
                    "openedAtMs" to 1_760_000_000_000,
                    "expiresAtMs" to 1_760_000_060_000,
                )
            val view = f.remote.beginWindow(request()).getOrThrow()
            assertEquals("win_1a2b3c4d5e6f", view.windowId)
            assertEquals(60, view.windowSeconds)
            assertEquals(24, view.allocatedSeconds)
            assertEquals(
                listOf(
                    FirebaseQuestionWindowRemoteDataSource.BEGIN to
                        mapOf(
                            "attemptId" to "attempt-7",
                            "lessonId" to "lesson-1",
                            "lessonVersion" to 3L,
                            "completedQuestionIndex" to 2,
                            "nextQuestionId" to "q3",
                            "difficulty" to "HARD",
                        ),
                ),
                f.calls.toList(),
            )
        }

    @Test
    fun fetchNextSendsOnlyTheTransitionIdentityAndParsesTheVerdict() =
        runTest {
            val f = Fake(this)
            f.reply =
                mapOf(
                    "status" to "DELIVERED",
                    "windowId" to "win_1a2b3c4d5e6f",
                    "questionIndex" to 3L,
                    "questionId" to "q3",
                    "issuedAtMs" to 1_760_000_010_000,
                    "answerDeadlineMs" to 1_760_000_034_000,
                    "remainderMs" to 22_000L,
                    "serverNowMs" to 1_760_000_012_000,
                )
            val verdict = f.remote.fetchNext("attempt-7", 2).getOrThrow()
            val delivered = assertIs<QuestionDeliveryVerdict.Delivered>(verdict)
            assertEquals("q3", delivered.delivery.questionId)
            assertEquals(22_000, delivered.remainderMs)
            assertEquals(
                listOf(
                    FirebaseQuestionWindowRemoteDataSource.FETCH to
                        mapOf("attemptId" to "attempt-7", "completedQuestionIndex" to 2),
                ),
                f.calls.toList(),
            )
        }

    @Test
    fun absentOrForeignSessionCannotOpenAWindowOrFetchADelivery() =
        runTest {
            val f = Fake(this)
            f.session = null
            failed(f.remote.beginWindow(request()), QuestionWindowFailureKind.ACCOUNT_UNAVAILABLE)
            failed(f.remote.fetchNext("attempt-7", 2), QuestionWindowFailureKind.ACCOUNT_UNAVAILABLE)
            assertTrue(f.calls.isEmpty())
        }

    @Test
    fun offlineFailsWithoutCallingFunctions() =
        runTest {
            val f = Fake(this)
            f.online = false
            assertEquals(
                SyncError.NoNetwork,
                (f.remote.beginWindow(request()).exceptionOrNull() as SyncFailure).error,
            )
            assertTrue(f.calls.isEmpty())
        }

    @Test
    fun sessionChangeDuringConnectivityIsRejectedBeforeAnyCall() =
        runTest {
            val f = Fake(this)
            val call =
                async(start = CoroutineStart.UNDISPATCHED) { f.remote.beginWindow(request()) }
            f.session = OutboxSession("other", 2)
            f.session = OutboxSession("player", 3)
            failed(call.await(), QuestionWindowFailureKind.ACCOUNT_CHANGED)
            assertTrue(f.calls.isEmpty())
        }

    @Test
    fun lateSuccessOrLateErrorCannotCrossAnOwnerEpoch() =
        runTest {
            val success = Fake(this)
            success.beforeReply = { success.session = OutboxSession("other", 2) }
            failed(success.remote.beginWindow(request()), QuestionWindowFailureKind.ACCOUNT_CHANGED)
            assertEquals(1, success.calls.size)

            val error = Fake(this)
            error.beforeReply = {
                error.session = OutboxSession("other", 2)
                throw IOException("dropped")
            }
            failed(error.remote.fetchNext("attempt-7", 2), QuestionWindowFailureKind.ACCOUNT_CHANGED)
        }

    @Test
    fun malformedReplyIsATypedFailureNotAFact() =
        runTest {
            val f = Fake(this)
            f.reply = mapOf("schemaVersion" to 1L, "windowId" to "win_1")
            failed(f.remote.beginWindow(request()), QuestionWindowFailureKind.MALFORMED_RESPONSE)
        }

    @Test
    fun callableRefusalsMapByCodeIntoTypedWindowKinds() =
        runTest {
            // Маппинг callableFailure читает имя кода SDK, никогда текст: недоступный аккаунт
            // (UNAUTHENTICATED/PERMISSION_DENIED) — ACCOUNT_UNAVAILABLE, кривой запрос
            // (INVALID_ARGUMENT) — INVALID_REQUEST, остальной сервер — SERVER_FAILURE.
            val unauthenticated = Fake(this)
            unauthenticated.failure =
                FirebaseQuestionWindowRemoteDataSource.QuestionWindowCallableException("UNAUTHENTICATED", null)
            failed(unauthenticated.remote.beginWindow(request()), QuestionWindowFailureKind.ACCOUNT_UNAVAILABLE)

            val permission = Fake(this)
            permission.failure =
                FirebaseQuestionWindowRemoteDataSource.QuestionWindowCallableException("PERMISSION_DENIED", null)
            failed(permission.remote.fetchNext("attempt-7", 2), QuestionWindowFailureKind.ACCOUNT_UNAVAILABLE)

            val invalid = Fake(this)
            invalid.failure =
                FirebaseQuestionWindowRemoteDataSource.QuestionWindowCallableException("INVALID_ARGUMENT", null)
            failed(invalid.remote.beginWindow(request()), QuestionWindowFailureKind.INVALID_REQUEST)

            val internal = Fake(this)
            internal.failure =
                FirebaseQuestionWindowRemoteDataSource.QuestionWindowCallableException("INTERNAL", null)
            failed(internal.remote.beginWindow(request()), QuestionWindowFailureKind.SERVER_FAILURE)

            val deadline = Fake(this)
            deadline.failure =
                FirebaseQuestionWindowRemoteDataSource.QuestionWindowCallableException("DEADLINE_EXCEEDED", null)
            failed(deadline.remote.fetchNext("attempt-7", 2), QuestionWindowFailureKind.SERVER_FAILURE)
        }

    @Test
    fun callableNamesRemainBoundToTheActualServerExports() {
        assertEquals("beginLessonQuestionWindow", FirebaseQuestionWindowRemoteDataSource.BEGIN)
        assertEquals("fetchNextLessonQuestion", FirebaseQuestionWindowRemoteDataSource.FETCH)
        val index = File(repo, "functions/index.js").readText()
        assertTrue(index.contains("exports.${FirebaseQuestionWindowRemoteDataSource.BEGIN} = onCall("))
        assertTrue(index.contains("exports.${FirebaseQuestionWindowRemoteDataSource.FETCH} = onCall("))
    }

    private fun request(): QuestionWindowRequest =
        QuestionWindowRequest("attempt-7", "lesson-1", 3, 2, "q3", isHard = true)

    private fun failed(
        result: Result<*>,
        kind: QuestionWindowFailureKind,
    ) {
        val failure = result.exceptionOrNull() as? QuestionWindowFailure
        assertEquals("expected $kind, got $result", kind, failure?.kind)
    }

    private fun <T> assertIs(value: Any?): T = value as T

    private inner class Fake(scope: TestScope) {
        var session: OutboxSession? = OutboxSession("player", 1)
        var online = true
        var reply: Any? =
            mapOf(
                "status" to "WINDOW_EXPIRED",
                "windowId" to "win_1a2b3c4d5e6f",
                "closedAtMs" to 1_760_000_060_000,
                "serverNowMs" to 1_760_000_060_000,
            )
        var beforeReply: suspend () -> Unit = {}

        /** Когда выставлено, call бросает его вместо ответа: путь SDK-отказа без типов Firebase. */
        var failure: Throwable? = null
        val sessions = OutboxSessionProvider { session }
        val calls = mutableListOf<Pair<String, Map<String, Any?>>>()
        val remote: FirebaseQuestionWindowRemoteDataSource =
            FirebaseQuestionWindowRemoteDataSource(
                call = { name, payload ->
                    calls += name to payload
                    beforeReply()
                    failure?.let { throw it }
                    reply
                },
                sessions = sessions,
                networkMonitor =
                    object : NetworkMonitor {
                        override fun observeOnline() = throw UnsupportedOperationException()

                        override suspend fun isOnline(): Boolean = online
                    },
                dispatcher = StandardTestDispatcher(scope.testScheduler),
            )
    }
}
