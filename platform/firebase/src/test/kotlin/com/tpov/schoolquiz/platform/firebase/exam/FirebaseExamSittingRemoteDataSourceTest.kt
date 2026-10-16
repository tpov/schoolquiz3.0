@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.platform.firebase.exam

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamTermsWire
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamAckOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamFetchOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamStartOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamSubmitOutcome
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

/**
 * Проводная граница шести экзаменационных вызовов: тело запроса, строгий разбор ответа, сессия и
 * сеть. Тела ответов — ровно те формы, которые отдаёт `functions/exam-sitting.js`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseExamSittingRemoteDataSourceTest {

    private val repo =
        generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "functions/index.js").isFile }

    @Test
    fun startThemeTestSendsOperationIdentityAndParsesTheStartedSitting() =
        runTest {
            val f = Fake(this)
            f.reply = startedReply()
            val outcome = f.remote.startThemeTest(
                "op-1", 1, "th0",
                ExamTermsWire(shown = true, acceptedRevision = 1),
            ).getOrThrow()
            val started = assertIs<ExamStartOutcome.Started>(outcome)
            assertEquals(100, started.debited)
            assertEquals("sitting-1", started.sitting.sittingId)
            assertEquals(0, started.sitting.currentIndex)
            assertEquals(24, started.sitting.nextQuestion?.answerSeconds)
            assertEquals(
                listOf(
                    FirebaseExamSittingRemoteDataSource.START_THEME to
                        mapOf(
                            "operationId" to "op-1",
                            "accountEpoch" to 1L,
                            "themeId" to "th0",
                            "shownTerms" to true,
                            "acceptedTermsRevision" to 1L,
                        ),
                ),
                f.calls.toList(),
            )
        }

    @Test
    fun typedRefusalParsesIntoTheRefusalOutcomeWithoutAWireDrift() =
        runTest {
            val f = Fake(this)
            f.reply =
                mapOf(
                    "refused" to true,
                    "kind" to "Cooldown",
                    "availableAtMs" to 1_760_086_400_000,
                )
            val outcome =
                f.remote.startFinalExam(
                    "op-2", 1, "course",
                    ExamTermsWire(shown = true, acceptedRevision = 1),
                ).getOrThrow()
            val refused = assertIs<ExamStartOutcome.Refused>(outcome)
            val cooldown = assertIs<com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamRefusal.Cooldown>(refused.refusal)
            assertEquals(1_760_086_400_000, cooldown.availableAtMs)
            assertEquals(
                listOf(
                    FirebaseExamSittingRemoteDataSource.START_FINAL to
                        mapOf(
                            "operationId" to "op-2",
                            "accountEpoch" to 1L,
                            "courseId" to "course",
                            "shownTerms" to true,
                            "acceptedTermsRevision" to 1L,
                        ),
                ),
                f.calls.toList(),
            )
        }

    @Test
    fun ackSendsOnlyTheQuestionIdentityAndParsesTheEstablishedDeadline() =
        runTest {
            val f = Fake(this)
            f.reply =
                mapOf(
                    "acknowledged" to true,
                    "duplicate" to false,
                    "ack" to
                        mapOf("ackedAtMs" to 1_760_000_010_000L, "answerDeadlineMs" to 1_760_000_034_000L),
                    "index" to 0,
                    "questionId" to "e0",
                    "revision" to 2L,
                    "shownDifficulty" to "EASY",
                    "answerSeconds" to 30,
                    "publicPayload" to "{\"type\":\"SingleChoiceRedacted\"}",
                    "serverNowMs" to 1_760_000_010_000,
                )
            val outcome =
                f.remote.acknowledgeQuestion("sitting-1", 0, "ack-1").getOrThrow()
            val acknowledged = assertIs<ExamAckOutcome.Acknowledged>(outcome)
            assertEquals(1_760_000_034_000, acknowledged.ack.answerDeadlineMs)
            assertEquals(
                listOf(
                    FirebaseExamSittingRemoteDataSource.ACKNOWLEDGE to
                        mapOf(
                            "sittingId" to "sitting-1",
                            "questionIndex" to 0,
                            "operationId" to "ack-1",
                        ),
                ),
                f.calls.toList(),
            )
        }

    @Test
    fun submitSendsTheBoundAnswerAndParsesTheDurableReceipt() =
        runTest {
            val f = Fake(this)
            f.reply = submittedReply()
            val outcome = f.remote.submitAnswer(
                "sitting-1", 0, 2,
                "{\"type\":\"single-choice\",\"selected\":\"ri-0\"}",
                "ans-1",
            ).getOrThrow()
            val submitted = assertIs<ExamSubmitOutcome.Submitted>(outcome)
            assertEquals("exam-answer-receipt", submitted.receipt.receiptId)
            assertTrue(!submitted.duplicate)
            assertEquals(
                listOf(
                    FirebaseExamSittingRemoteDataSource.SUBMIT to
                        mapOf(
                            "sittingId" to "sitting-1",
                            "questionIndex" to 0,
                            "revision" to 2L,
                            "answer" to "{\"type\":\"single-choice\",\"selected\":\"ri-0\"}",
                            "operationId" to "ans-1",
                        ),
                ),
                f.calls.toList(),
            )
        }

    @Test
    fun fetchSittingResultParsesTheTerminalProjectionVerbatim() =
        runTest {
            val f = Fake(this)
            f.reply =
                mapOf(
                    "sittingId" to "sitting-1",
                    "state" to "CLOSED",
                    "open" to false,
                    "result" to
                        mapOf(
                            "schemaVersion" to 1,
                            "sittingId" to "sitting-1",
                            "mode" to "ThemeTest",
                            "assessmentId" to "th0",
                            "resultVersion" to 1,
                            "percent" to 60,
                            "passed" to true,
                            "starsTenths" to 18,
                            "advice" to listOf("SOLID", "THEME_PASSED", "THEME_RESULT"),
                            "closedAtMs" to 1_760_000_100_000L,
                            "nextAvailableAtMs" to 1_760_086_500_000L,
                        ),
                )
            val outcome = f.remote.fetchSittingResult("sitting-1").getOrThrow()
            val terminal = assertIs<ExamFetchOutcome.Terminal>(outcome)
            assertEquals(60, terminal.result.percent)
            assertEquals(18, terminal.result.starsTenths)
            assertEquals(true, terminal.result.passed)
        }

    @Test
    fun absentOrForeignSessionCannotCallTheExamSurface() =
        runTest {
            val f = Fake(this)
            f.session = null
            failed(
                f.remote.startThemeTest("op", 1, "t", ExamTermsWire(true, 1)),
                FirebaseExamSittingRemoteDataSource.ExamFailureKind.ACCOUNT_UNAVAILABLE,
            )
            assertTrue(f.calls.isEmpty())
        }

    @Test
    fun offlineFailsWithoutCallingFunctions() =
        runTest {
            val f = Fake(this)
            f.online = false
            assertEquals(
                SyncError.NoNetwork,
                (
                    f.remote.startThemeTest("op", 1, "t", ExamTermsWire(true, 1))
                        .exceptionOrNull() as SyncFailure
                ).error,
            )
            assertTrue(f.calls.isEmpty())
        }

    @Test
    fun sessionChangeDuringConnectivityIsRejectedBeforeAnyCall() =
        runTest {
            val f = Fake(this)
            val call =
                async(start = CoroutineStart.UNDISPATCHED) {
                    f.remote.startThemeTest("op", 1, "t", ExamTermsWire(true, 1))
                }
            f.session = OutboxSession("other", 2)
            f.session = OutboxSession("player", 3)
            failed(
                call.await(),
                FirebaseExamSittingRemoteDataSource.ExamFailureKind.ACCOUNT_CHANGED,
            )
            assertTrue(f.calls.isEmpty())
        }

    @Test
    fun malformedReplyIsATypedFailureNotAFact() =
        runTest {
            val f = Fake(this)
            f.reply = mapOf("schemaVersion" to 1, "sittingId" to "s1")
            failed(
                f.remote.fetchSittingResult("sitting-1"),
                FirebaseExamSittingRemoteDataSource.ExamFailureKind.MALFORMED_RESPONSE,
            )
        }

    @Test
    fun callableNamesRemainBoundToTheActualServerExports() {
        assertEquals("startThemeTest", FirebaseExamSittingRemoteDataSource.START_THEME)
        assertEquals("startFinalExam", FirebaseExamSittingRemoteDataSource.START_FINAL)
        assertEquals("acknowledgeQuestion", FirebaseExamSittingRemoteDataSource.ACKNOWLEDGE)
        assertEquals("submitAnswer", FirebaseExamSittingRemoteDataSource.SUBMIT)
        assertEquals("finishSitting", FirebaseExamSittingRemoteDataSource.FINISH)
        assertEquals("fetchSittingResult", FirebaseExamSittingRemoteDataSource.FETCH_RESULT)
        val index = File(repo, "functions/index.js").readText()
        for (name in
            listOf(
                FirebaseExamSittingRemoteDataSource.START_THEME,
                FirebaseExamSittingRemoteDataSource.START_FINAL,
                FirebaseExamSittingRemoteDataSource.ACKNOWLEDGE,
                FirebaseExamSittingRemoteDataSource.SUBMIT,
                FirebaseExamSittingRemoteDataSource.FINISH,
                FirebaseExamSittingRemoteDataSource.FETCH_RESULT,
            )
        ) {
            assertTrue(
                "server export $name is missing or not an onCall",
                index.contains("exports.$name = onCall("),
            )
        }
    }

    /** Тело ответа старта — ровно та форма, что собирает `publicSittingProjection`. */
    private fun startedReply(): Map<String, Any?> =
        mapOf(
            "recovered" to false,
            "debited" to 100,
            "lifePoints" to 9_900L,
            "schemaVersion" to 1,
            "sittingId" to "sitting-1",
            "mode" to "ThemeTest",
            "assessmentId" to "th0",
            "state" to "OPEN",
            "currentIndex" to 0,
            "totalQuestions" to 20,
            "rules" to
                mapOf(
                    "nextQuestionWindowSeconds" to 60,
                    "firstAckGapSeconds" to 60,
                    "timerRule" to "ALL_HARD_ORDINARY",
                    "timerRuleVersion" to 1,
                ),
            "nextQuestion" to
                mapOf(
                    "index" to 0,
                    "questionId" to "e0",
                    "revision" to 2L,
                    "shownDifficulty" to "EASY",
                    "answerSeconds" to 24,
                    "publicPayload" to "{\"type\":\"SingleChoiceRedacted\"}",
                    "ack" to
                        mapOf("awaitingAck" to true, "windowDeadlineMs" to 1_760_000_060_000L),
                    "serverNowMs" to 1_760_000_000_000,
                ),
            "serverNowMs" to 1_760_000_000_000,
        )

    private fun submittedReply(): Map<String, Any?> =
        mapOf(
            "accepted" to true,
            "duplicate" to false,
            "receipt" to
                mapOf(
                    "receiptId" to "exam-answer-receipt",
                    "sittingId" to "sitting-1",
                    "index" to 0,
                    "revision" to 2L,
                    "receivedAtMs" to 1_760_000_020_000L,
                ),
            "schemaVersion" to 1,
            "sittingId" to "sitting-1",
            "mode" to "ThemeTest",
            "assessmentId" to "th0",
            "state" to "OPEN",
            "currentIndex" to 1,
            "totalQuestions" to 20,
            "rules" to
                mapOf(
                    "nextQuestionWindowSeconds" to 60,
                    "firstAckGapSeconds" to 60,
                    "timerRule" to "ALL_HARD_ORDINARY",
                    "timerRuleVersion" to 1,
                ),
            "nextQuestion" to null,
            "serverNowMs" to 1_760_000_020_000,
        )

    private fun failed(
        result: Result<*>,
        kind: FirebaseExamSittingRemoteDataSource.ExamFailureKind? = null,
    ) {
        val failure = result.exceptionOrNull() as? FirebaseExamSittingRemoteDataSource.ExamFailure
        assertEquals(
            "expected $kind, got $result",
            kind?.name,
            failure?.kind?.name,
        )
    }

    private fun <T> assertIs(value: Any?): T = value as T

    private inner class Fake(scope: TestScope) {
        var session: OutboxSession? = OutboxSession("player", 1)
        var online = true
        var reply: Any? = startedReply()
        var beforeReply: suspend () -> Unit = {}
        val sessions = OutboxSessionProvider { session }
        val calls = mutableListOf<Pair<String, Map<String, Any?>>>()
        val remote: FirebaseExamSittingRemoteDataSource =
            FirebaseExamSittingRemoteDataSource(
                call = { name, payload ->
                    calls += name to payload
                    beforeReply()
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
