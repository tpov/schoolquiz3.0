package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.PrivateQuizDoorRefusal
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.PrivateQuizRunRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizAttemptReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizAttemptStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizEntryRefusal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Провод приватного прогона (AD-29, история 8.4): строгий разбор и типизированные отказы.
 *
 * Держатся три вещи: чужая форма отказывает (а не превращается в пустой прогон), скрытый
 * счёт доезжает как ОТСУТСТВИЕ полей (никаких нулей), и каждый код двери ложится на своё
 * типизированное состояние входа.
 */
class PrivateQuizRunWireContractTest {

    private fun runReply() =
        mapOf(
            "startId" to "start-000000001",
            "startedAtMs" to 1_000L,
            "deadlineMs" to 61_000L,
            "questions" to
                listOf(
                    mapOf("id" to "q-1", "order" to 0L, "payload" to "{\"type\":\"SingleChoiceRedacted\"}"),
                    mapOf("id" to "q-2", "order" to 1L, "payload" to "{}"),
                ),
        )

    @Test
    fun run_parsesTheFrozenSetStrictly() {
        val run = PrivateQuizRunWireParser.run(runReply(), "start-000000001")
        assertEquals("start-000000001", run.startId)
        assertEquals(61_000L, run.deadlineMs)
        assertEquals(2, run.questions.size)
        assertEquals("q-1", run.questions[0].id)
    }

    @Test
    fun run_refusesAReplyForAnotherStart() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                PrivateQuizRunWireParser.run(runReply(), "start-other-0001")
            }
        assertTrue(error.message!!.contains("belongs to start"))
    }

    @Test
    fun ownResult_completionOnly_arrivesWithoutScoreFields_andStaysThatWay() {
        val result =
            PrivateQuizRunWireParser.ownResult(
                mapOf("status" to "terminal", "reason" to "finished", "finishedAtMs" to 5_000L),
                "start-000000001",
            )
        assertEquals(PrivateQuizAttemptStatus.TERMINAL, result.attemptStatus)
        assertEquals(PrivateQuizAttemptReason.FINISHED, result.reason)
        assertEquals(false, result.showsScore, "сервер скрыл счёт — клиент не рисует нули")
        assertNull(result.percentScore)
        assertNull(result.questionScores)
    }

    @Test
    fun ownResult_withScore_carriesCountsAndDigits() {
        val result =
            PrivateQuizRunWireParser.ownResult(
                mapOf(
                    "status" to "terminal",
                    "reason" to "timeout",
                    "finishedAtMs" to 6_000L,
                    "percentScore" to 0L,
                    "correctCount" to 0L,
                    "questionCount" to 2L,
                    "questionScores" to listOf(1L, 1L),
                ),
                "start-000000001",
            )
        assertEquals(PrivateQuizAttemptReason.TIMEOUT, result.reason)
        assertTrue(result.showsScore)
        assertEquals(0, result.percentScore, "ноль подтверждённый — честный ноль, не подстановка")
        assertEquals(listOf(1, 1), result.questionScores)
    }

    @Test
    fun ownResult_running_hasNoReason() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                PrivateQuizRunWireParser.ownResult(
                    mapOf("status" to "running", "reason" to "finished"),
                    "start-000000001",
                )
            }
        assertTrue(error.message!!.contains("carries no reason"))
    }

    @Test
    fun doorRefusals_mapToTypedEntryStates() = runTest {
        val cases =
            mapOf(
                "LINK_DISABLED" to PrivateQuizEntryRefusal.LINK_DISABLED,
                "LINK_EXPIRED" to PrivateQuizEntryRefusal.LINK_EXPIRED,
                "ATTEMPT_LIMIT_REACHED" to PrivateQuizEntryRefusal.ATTEMPT_LIMIT_REACHED,
                "NOT_FOUND" to PrivateQuizEntryRefusal.NOT_FOUND,
                "RUN_CONTENT_CHANGED" to PrivateQuizEntryRefusal.RUN_CONTENT_CHANGED,
                "PRIVATE_ATTEMPT_TERMINAL" to PrivateQuizEntryRefusal.ATTEMPT_TERMINAL,
                null to PrivateQuizEntryRefusal.UNAVAILABLE,
                "SOMETHING_NEW" to PrivateQuizEntryRefusal.UNAVAILABLE,
            )
        cases.forEach { (reason, expected) ->
            val repository = PrivateQuizRunRepositoryImpl(remote = RefusingDataSource(reason))
            val entry = repository.start("owner", "catalog", "quest", "start-000000001")
            assertEquals(
                PrivateQuizEntry.Refused(expected),
                entry,
                "код двери \"$reason\" обязан ложиться на своё состояние входа",
            )
        }
    }

    @Test
    fun transportFailure_isUnavailable_notADoorRefusal() = runTest {
        val repository = PrivateQuizRunRepositoryImpl(remote = ThrowingDataSource(RuntimeException("offline")))
        val entry = repository.start("owner", "catalog", "quest", "start-000000001")
        assertEquals(PrivateQuizEntry.Refused(PrivateQuizEntryRefusal.UNAVAILABLE), entry)
    }

    @Test
    fun readyEntry_carriesTheParsedRun() = runTest {
        val repository = PrivateQuizRunRepositoryImpl(remote = OkDataSource(runReply(), runReply()))
        val entry = repository.start("owner", "catalog", "quest", "start-000000001")
        assertEquals(true, entry is PrivateQuizEntry.Ready)
        assertEquals(2, (entry as PrivateQuizEntry.Ready).run.questions.size)
    }

    private class RefusingDataSource(
        private val reason: String?,
    ) : PrivateQuizRunRemoteDataSource {
        override suspend fun start(
            ownerUid: String,
            catalogId: String,
            questId: String,
            startId: String,
        ): Any? = throw PrivateQuizDoorRefusal(reason)

        override suspend fun fetchRun(
            ownerUid: String,
            catalogId: String,
            questId: String,
            startId: String,
        ): Any? = throw IllegalStateException("not reached")

        override suspend fun readResult(
            ownerUid: String,
            catalogId: String,
            questId: String,
            startId: String,
        ): Any? = throw IllegalStateException("not reached")
    }

    private class ThrowingDataSource(
        private val error: Exception,
    ) : PrivateQuizRunRemoteDataSource {
        override suspend fun start(
            ownerUid: String,
            catalogId: String,
            questId: String,
            startId: String,
        ): Any? = throw error

        override suspend fun fetchRun(
            ownerUid: String,
            catalogId: String,
            questId: String,
            startId: String,
        ): Any? = throw error

        override suspend fun readResult(
            ownerUid: String,
            catalogId: String,
            questId: String,
            startId: String,
        ): Any? = throw error
    }

    private class OkDataSource(
        private val startReply: Any?,
        private val runReply: Any?,
    ) : PrivateQuizRunRemoteDataSource {
        override suspend fun start(
            ownerUid: String,
            catalogId: String,
            questId: String,
            startId: String,
        ): Any? = startReply

        override suspend fun fetchRun(
            ownerUid: String,
            catalogId: String,
            questId: String,
            startId: String,
        ): Any? = runReply

        override suspend fun readResult(
            ownerUid: String,
            catalogId: String,
            questId: String,
            startId: String,
        ): Any? = throw IllegalStateException("not reached")
    }
}
