package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.KotlinxSerializationQuestionContentParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizAttemptReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizAttemptStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizEntryRefusal
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizOwnResult
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizRun
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizRunQuestion
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.PrivateQuizRunnerRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizSubmission
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Машина прогона приватного квиза (AD-29/AD-31, история 8.4): дверь → вопросы по исходным
 * срокам → подача → результат. Здесь зажато то, что участнику обещано:
 *  - отказ двери — именованное состояние, попытка не начинается;
 *  - просроченный вопрос закрывается показанным-без-ответа, попытка идёт дальше (AD-31);
 *  - подача уходит ровно одна, по завершении; результат читается после доставки;
 *  - завершение без счёта (showResult=false) доезжает как подтверждение, без нулей (AD-21).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrivateQuizRunMachineTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val single = """
        {"type":"SingleChoiceRedacted","difficulty":"HARD","text":"2+2?","imageUrl":null,
         "options":[{"id":"a","text":"3"},{"id":"b","text":"4"}]}
    """.trimIndent()

    private fun run(vararg payloads: String, startedAtMs: Long = 0) =
        PrivateQuizRun(
            startId = "start-000000001",
            startedAtMs = startedAtMs,
            deadlineMs = startedAtMs + 600_000,
            questions = payloads.mapIndexed { index, payload -> PrivateQuizRunQuestion("q-$index", index, payload) },
        )

    private inner class Harness(
        val entry: PrivateQuizEntry,
        val submissions: MutableList<PrivateQuizSubmission> = mutableListOf(),
        var ownResult: PrivateQuizOwnResult? = null,
        var readFails: Int = 0,
        val nowMs: Long = 1_000L,
        var enqueueFails: Boolean = false,
    ) {
        var submitted = 0
        val startIds = mutableListOf<String?>()
        val machine =
            PrivateQuizRunMachine(
                request = PrivateQuizRunnerRequest("owner-1", "cat-1", "quest-1", "Приватный"),
                startDoor = { existing ->
                    startIds += existing
                    "start-000000001" to entry
                },
                submit = { startId, submission ->
                    startIds += startId
                    if (enqueueFails) {
                        Result.failure(IllegalStateException("queue refused"))
                    } else {
                        submitted += 1
                        submissions += submission
                        Result.success(Unit)
                    }
                },
                readResult = { _ ->
                    if (readFails > 0) {
                        readFails -= 1
                        Result.failure(IllegalStateException("not delivered yet"))
                    } else {
                        ownResult?.let { Result.success(it) }
                            ?: Result.failure(IllegalStateException("no result yet"))
                    }
                },
                parser = KotlinxSerializationQuestionContentParser(),
                clock = object : Clock {
                    override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
                },
                mainContext = dispatcher,
            )
    }

    @Test
    fun refusedDoor_landsAsTypedState_andNoAttemptBegins() = runTest(dispatcher) {
        val harness = Harness(PrivateQuizEntry.Refused(PrivateQuizEntryRefusal.ATTEMPT_LIMIT_REACHED))
        harness.machine.openDoor()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            PrivateQuizRunUiState.Refused(PrivateQuizEntryRefusal.ATTEMPT_LIMIT_REACHED),
            harness.machine.state.value,
        )
        assertEquals(0, harness.submitted, "отказ двери — попытки нет и подачи нет")
    }

    @Test
    fun answeredQuestions_areSubmittedOnce_withServedPositionsAndPayloads() = runTest(dispatcher) {
        val harness = Harness(PrivateQuizEntry.Ready(run(single, single)))
        harness.machine.openDoor()
        dispatcher.scheduler.advanceUntilIdle()

        val question = assertIs<PrivateQuizRunUiState.Question>(harness.machine.state.value)
        assertEquals(0, question.indexInPool)
        assertEquals(2, question.totalInPool)
        assertTrue(question.deadlineMs > 0, "у вопроса есть исходный срок: ${'$'}{question.deadlineMs}")

        harness.machine.onDraftChanged(UserAnswerDraft.SingleChoiceDraft(com.tpov.schoolquiz.shared.core.question_schema.OptionId("b")))
        harness.machine.onAnswer()
        dispatcher.scheduler.advanceUntilIdle()

        val second = assertIs<PrivateQuizRunUiState.Question>(harness.machine.state.value)
        assertEquals(1, second.indexInPool)
        harness.machine.onAnswer()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, harness.submitted, "подача одна на попытку")
        val submission = harness.submissions.single()
        assertEquals(listOf(0, 1), submission.served.map { it.codeAnswerIndex })
        assertEquals(2, submission.answers.size)
        assertTrue(submission.answers[0].answerPayload!!.contains("single-choice"))
    }

    @Test
    fun survivingMachine_ignoresReopen_afterTheRunResolved() = runTest(dispatcher) {
        // Поворот пересоздаёт компонент, но машина выживает в InstanceKeeper: повторный
        // openDoor по решённой двери — no-op, ключ и попытка не трогаются (AD-6).
        val harness = Harness(PrivateQuizEntry.Ready(run(single, single)))
        harness.machine.openDoor()
        dispatcher.scheduler.advanceUntilIdle()
        val before = harness.machine.startId

        harness.machine.openDoor()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(before, harness.machine.startId, "ключ не меняется повторным openDoor")
        assertEquals(1, harness.startIds.size, "дверь вызвана один раз")
    }

    @Test
    fun enqueueFailure_keepsTheFrozenSubmission_andRefreshReEnqueuesSameStartId() = runTest(dispatcher) {
        val completionOnly = PrivateQuizOwnResult(
            startId = "start-000000001",
            attemptStatus = PrivateQuizAttemptStatus.TERMINAL,
            reason = PrivateQuizAttemptReason.FINISHED,
            finishedAtMs = 9_000,
            percentScore = 50,
            correctCount = 1,
            questionCount = 2,
            questionScores = listOf(9, 1),
        )
        val harness = Harness(
            PrivateQuizEntry.Ready(run(single, single)),
            ownResult = completionOnly,
            enqueueFails = true,
        )
        harness.machine.openDoor()
        dispatcher.scheduler.advanceUntilIdle()
        harness.machine.onAnswer()
        harness.machine.onAnswer()
        dispatcher.scheduler.advanceUntilIdle()

        val failed = assertIs<PrivateQuizRunUiState.ReadFailed>(harness.machine.state.value)
        assertEquals(
            PrivateQuizRunUiState.ReadFailed.FailureSource.ENQUEUE,
            failed.source,
            "неудача — постановка, не чтение",
        )
        assertEquals(0, harness.submitted)

        // Сеть вернулась: очередь принимает — обновление переедет замороженной подачей.
        harness.enqueueFails = false
        harness.machine.onRefreshResult()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, harness.submitted, "обновление переедет замороженной подачей")
        // startIds: дверь (сначала null — ключ mint'ится внутри), подача, переотправка —
        // один и тот же ключ после первой двери (AD-6).
        assertTrue(
            harness.startIds == listOf(null, "start-000000001", "start-000000001"),
            "переотправка тем же ключом (AD-6): door + submit + resubmit",
        )
        val finished = assertIs<PrivateQuizRunUiState.Finished>(harness.machine.state.value)
        assertEquals(50, finished.result.percentScore)
    }

    @Test
    fun expiredQuestion_closesShownWithoutAnswer_andTheRunContinues() = runTest(dispatcher) {
        // Срок попытки истёк до первого вопроса: каждый вопрос закрывается показанным-без-ответа
        // и машина доходит до подачи по всему набору (AD-29: знаменатель весь квиз).
        val harness = Harness(PrivateQuizEntry.Ready(run(single, single)), nowMs = 600_000)
        harness.machine.openDoor()
        dispatcher.scheduler.advanceUntilIdle()
        // Последний (просроченный) вопрос показывается frame'ом: его закрывает onTimeout.
        harness.machine.onTimeout()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, harness.submitted)
        val submission = harness.submissions.single()
        assertEquals(2, submission.answers.size)
        assertEquals(
            listOf(null, null),
            submission.answers.map { it.answerPayload },
            "просроченные вопросы едут без ответа — и никогда оплаченно-верными (8.5)",
        )
        assertIs<PrivateQuizRunUiState.ResultPending>(harness.machine.state.value)
    }

    @Test
    fun finished_readsWithoutScore_first_thenTheTerminalArrives() = runTest(dispatcher) {
        val completionOnly = PrivateQuizOwnResult(
            startId = "start-000000001",
            attemptStatus = PrivateQuizAttemptStatus.TERMINAL,
            reason = PrivateQuizAttemptReason.FINISHED,
            finishedAtMs = 9_000,
            percentScore = null,
            correctCount = null,
            questionCount = null,
            questionScores = null,
        )
        val harness = Harness(
            PrivateQuizEntry.Ready(run(single)),
            ownResult = completionOnly,
            readFails = 1,
        )
        harness.machine.openDoor()
        dispatcher.scheduler.advanceUntilIdle()
        harness.machine.onDraftChanged(UserAnswerDraft.SingleChoiceDraft(com.tpov.schoolquiz.shared.core.question_schema.OptionId("b")))
        harness.machine.onAnswer()
        dispatcher.scheduler.advanceUntilIdle()
        dispatcher.scheduler.advanceTimeBy(5_000)
        dispatcher.scheduler.advanceUntilIdle()

        val finished = assertIs<PrivateQuizRunUiState.Finished>(harness.machine.state.value)
        assertEquals(false, finished.result.showsScore, "скрытый счёт — отсутствие чисел, не нули (AD-21)")
    }
}
