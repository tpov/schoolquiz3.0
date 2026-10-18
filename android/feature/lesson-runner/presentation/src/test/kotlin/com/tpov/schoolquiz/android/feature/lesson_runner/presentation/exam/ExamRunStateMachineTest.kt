package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.exam

import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamFinishVerdict
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamSittingRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamTermsWire
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamTransportFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamTransportFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamAckOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamFetchOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamRefusal
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamResultProjection
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamSittingProjection
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamSittingRules
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamSittingState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamShownDifficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamStartOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamSubmitOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamQuestionProjection
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamAnswerReceipt
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Логика экзаменационного прогона без Compose: старт/восстановление одним намерением, ACK,
 * ответ, ожидание закрытия и чтение серверного результата без пересчёта.
 */
class ExamRunStateMachineTest {

    @Test
    fun `given a lost response then the same intent recovers the same sitting without a new start`() = runTest {
        val fake = FakeRemote()
        val machine = ExamRunStateMachine(fake) { "op-1" }
        machine.showEntry(ExamMode.THEME_TEST, "th0", entryPrice = 100, requiredTermsRevision = 1)
        machine.acceptTerms(shown = true, acceptedRevision = 1)
        machine.start(ExamMode.THEME_TEST, "th0")
        assertTrue(fake.startCalls == 1)

        // Потерянный ответ: тот же intent — сервер вернул бы восстановление.
        fake.nextStartOutcome = recoveredOutcome()
        machine.start(ExamMode.THEME_TEST, "th0")
        assertEquals("тот же ключ операции на то же намерение", "op-1", fake.lastStartOperationId)
        assertTrue(machine.view.value is ExamRunView.Running)
    }

    @Test
    fun `given a new intent then the next start carries a fresh operation id`() = runTest {
        var counter = 0
        val machine = ExamRunStateMachine(FakeRemote()) { "op-${++counter}" }
        assertEquals("op-1", machine.startOperationId)
        assertTrue("до всякого старта машина первозданна", machine.isPristine)
        machine.renewStartIntent()
        assertEquals("op-2", machine.startOperationId)
    }

    @Test
    fun `given a submit refusal then the reason surfaces in the view and clears on success`() = runTest {
        val fake = FakeRemote()
        fake.nextSubmitOutcome = ExamSubmitOutcome.Refused("ACK_REQUIRED")
        val machine = ExamRunStateMachine(fake) { "op" }
        machine.showEntry(ExamMode.THEME_TEST, "th0", 100, 1)
        machine.acceptTerms(true, 1)
        machine.start(ExamMode.THEME_TEST, "th0")
        machine.submitAnswer("""{"type":"single-choice","selected":"ri-0"}""")
        val refused = machine.view.value as ExamRunView.Running
        assertEquals("причина отказа видна, не тихая петля", "ACK_REQUIRED", refused.refusal)
        fake.nextSubmitOutcome = ExamSubmitOutcome.Submitted(
            receipt = ExamAnswerReceipt("r", "s1", 0, 2, 1),
            duplicate = false,
            sitting = sittingProjection(index = 1, withAck = false),
        )
        machine.acknowledgeCurrent()
        machine.submitAnswer("""{"type":"single-choice","selected":"ri-0"}""")
        val running = machine.view.value as ExamRunView.Running
        assertEquals("успех чистит причину", null, running.refusal)
    }

    @Test
    fun `given a typed refusal then the entry shows it and nothing runs`() = runTest {
        val fake = FakeRemote()
        fake.nextStartOutcome = ExamStartOutcome.Refused(ExamRefusal.Cooldown(availableAtMs = 5))
        val machine = ExamRunStateMachine(fake) { "op" }
        machine.showEntry(ExamMode.FINAL_EXAM, "c", 200, 1)
        machine.acceptTerms(true, 1)
        machine.start(ExamMode.FINAL_EXAM, "c")
        val entry = machine.view.value as ExamRunView.Entry
        assertEquals(ExamRefusal.Cooldown(5), entry.refusal)
    }

    @Test
    fun `given an established ack then the duplicate is never re-requested`() = runTest {
        val fake = FakeRemote()
        val machine = ExamRunStateMachine(fake) { "op" }
        machine.showEntry(ExamMode.THEME_TEST, "th0", 100, 1)
        machine.acceptTerms(true, 1)
        machine.start(ExamMode.THEME_TEST, "th0")

        machine.acknowledgeCurrent()
        assertEquals(1, fake.ackCalls)
        // Повтор ACK при уже закреплённом дедлайне не уходит в транспорт: зеркало отсекает.
        machine.acknowledgeCurrent()
        assertEquals(1, fake.ackCalls)
    }

    @Test
    fun `given a submitted answer then the view follows the server projection`() = runTest {
        val fake = FakeRemote()
        val machine = ExamRunStateMachine(fake) { "op" }
        machine.showEntry(ExamMode.THEME_TEST, "th0", 100, 1)
        machine.acceptTerms(true, 1)
        machine.start(ExamMode.THEME_TEST, "th0")
        machine.acknowledgeCurrent()
        machine.submitAnswer("""{"type":"single-choice","selected":"ri-0"}""")
        val running = machine.view.value as ExamRunView.Running
        assertEquals("серверная проекция решает следующий вопрос", 1, running.sitting.currentIndex)
    }

    @Test
    fun `given finish requested then the result poll renders the server values verbatim`() = runTest {
        val fake = FakeRemote()
        val machine = ExamRunStateMachine(fake) { "op" }
        machine.showEntry(ExamMode.THEME_TEST, "th0", 100, 1)
        machine.acceptTerms(true, 1)
        machine.start(ExamMode.THEME_TEST, "th0")
        machine.requestFinish()
        assertTrue(machine.view.value is ExamRunView.AwaitingClosure)

        // Открыто: worker ещё не закрыл — явное ожидание, не выдуманный итог.
        machine.fetchResult()
        assertTrue(machine.view.value is ExamRunView.AwaitingClosure)

        fake.nextFetchOutcome = ExamFetchOutcome.Terminal(SERVER_RESULT)
        machine.fetchResult()
        val finished = machine.view.value as ExamRunView.Finished
        assertEquals("результат отрисовывается как есть", SERVER_RESULT, finished.result)
    }

    @Test
    fun `given a transport failure on submit then the draft edited during the request survives`() = runTest {
        val fake = FakeRemote()
        fake.nextSubmitFailure = ExamTransportFailure(ExamTransportFailureKind.NO_NETWORK)
        val machine = ExamRunStateMachine(fake) { "op" }
        machine.showEntry(ExamMode.THEME_TEST, "th0", 100, 1)
        machine.acceptTerms(true, 1)
        machine.start(ExamMode.THEME_TEST, "th0")
        machine.acknowledgeCurrent()
        // Правка во время запроса: отказ читает свежее состояние, а не снимок до запроса.
        fake.onSubmit = { machine.setAnswerDraft("""{"edited":true}""") }
        machine.submitAnswer("""{"type":"single-choice","selected":"ri-0"}""")
        val running = machine.view.value as ExamRunView.Running
        assertEquals("черновик не откатывается отказом", """{"edited":true}""", running.answerDraft)
        assertEquals("вид транспорта виден вместо тихой петли", "NO_NETWORK", running.refusal)
    }

    @Test
    fun `given a transport failure on finish then the spinner stops and the kind surfaces`() = runTest {
        val fake = FakeRemote()
        fake.nextFinishFailure = ExamTransportFailure(ExamTransportFailureKind.NO_NETWORK)
        val machine = ExamRunStateMachine(fake) { "op" }
        machine.showEntry(ExamMode.THEME_TEST, "th0", 100, 1)
        machine.acceptTerms(true, 1)
        machine.start(ExamMode.THEME_TEST, "th0")
        machine.requestFinish()
        val awaiting = machine.view.value as ExamRunView.AwaitingClosure
        assertEquals("вечного спиннера нет", false, awaiting.busy)
        assertEquals("причина транспортной неудачи видна", "NO_NETWORK", awaiting.refusal)
    }

    @Test
    fun `given a transport failure on start then the terms toggled during the request survive`() = runTest {
        val fake = FakeRemote()
        fake.nextStartFailure = ExamTransportFailure(ExamTransportFailureKind.NO_NETWORK)
        val machine = ExamRunStateMachine(fake) { "op" }
        machine.showEntry(ExamMode.THEME_TEST, "th0", 100, 1)
        machine.acceptTerms(true, 1)
        // Пользователь снимает согласие, пока запрос ещё в полёте: отказ не откатывает его.
        fake.onStart = { machine.acceptTerms(shown = true, acceptedRevision = 0) }
        machine.start(ExamMode.THEME_TEST, "th0")
        val entry = machine.view.value as ExamRunView.Entry
        assertTrue("переключение условий во время запроса не откатывается", !entry.termsAccepted)
        assertEquals(false, entry.busy)
    }

    private companion object {
        val SERVER_RESULT = ExamResultProjection(
            sittingId = "s1",
            mode = ExamMode.THEME_TEST,
            assessmentId = "th0",
            resultVersion = 1,
            percent = 59,
            passed = false,
            starsTenths = 18,
            advice = listOf("DEVELOPING", "BACK_TO_LESSONS", "THEME_RESULT"),
            closedAtMs = 1_000L,
            nextAvailableAtMs = 1_000L + 24 * 3_600 * 1_000,
        )
    }

    private class FakeRemote : ExamSittingRemoteDataSource {
        @Suppress("PropertyName")
        var nextSubmitOutcome: ExamSubmitOutcome =
            ExamSubmitOutcome.Submitted(
                receipt = ExamAnswerReceipt("r", "s1", 0, 2, 1),
                duplicate = false,
                sitting = sittingProjection(index = 1, withAck = false),
            )

        // Крючки «правка во время запроса» и типизированные транспортные отказы.
        var onStart: (() -> Unit)? = null
        var onSubmit: (() -> Unit)? = null
        var nextStartFailure: ExamTransportFailure? = null
        var nextSubmitFailure: ExamTransportFailure? = null
        var nextFinishFailure: ExamTransportFailure? = null

        var startCalls = 0
        var ackCalls = 0
        var lastStartOperationId: String? = null
        var nextStartOutcome: ExamStartOutcome = startedOutcome()
        var nextFetchOutcome: ExamFetchOutcome = ExamFetchOutcome.Open("s1", ExamSittingState.FINISH_REQUESTED)

        override suspend fun startThemeTest(
            operationId: String,
            accountEpoch: Long,
            themeId: String,
            terms: ExamTermsWire,
        ): Result<ExamStartOutcome> {
            startCalls += 1
            lastStartOperationId = operationId
            onStart?.invoke()
            nextStartFailure?.let { return Result.failure(it) }
            return Result.success(nextStartOutcome)
        }

        override suspend fun startFinalExam(
            operationId: String,
            accountEpoch: Long,
            courseId: String,
            terms: ExamTermsWire,
        ): Result<ExamStartOutcome> {
            startCalls += 1
            lastStartOperationId = operationId
            onStart?.invoke()
            nextStartFailure?.let { return Result.failure(it) }
            return Result.success(nextStartOutcome)
        }

        override suspend fun acknowledgeQuestion(
            sittingId: String,
            questionIndex: Int,
            operationId: String,
        ): Result<ExamAckOutcome> {
            ackCalls += 1
            return Result.success(
                ExamAckOutcome.Acknowledged(
                    ack = com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamAck(
                        ackedAtMs = 1,
                        answerDeadlineMs = 31_000,
                    ),
                    duplicate = false,
                ),
            )
        }

        override suspend fun submitAnswer(
            sittingId: String,
            questionIndex: Int,
            revision: Long,
            answerPayload: String,
            operationId: String,
        ): Result<ExamSubmitOutcome> {
            onSubmit?.invoke()
            nextSubmitFailure?.let { return Result.failure(it) }
            return Result.success(nextSubmitOutcome)
        }

        override suspend fun finishSitting(sittingId: String): Result<ExamFinishVerdict> {
            nextFinishFailure?.let { return Result.failure(it) }
            return Result.success(ExamFinishVerdict("FINISH_REQUESTED", alreadyRequested = false, alreadyClosed = false))
        }

        override suspend fun fetchSittingResult(sittingId: String): Result<ExamFetchOutcome> =
            Result.success(nextFetchOutcome)
    }
}

private fun startedOutcome(): ExamStartOutcome.Started = ExamStartOutcome.Started(
    debited = 100,
    sitting = sittingProjection(index = 0, withAck = false),
)

private fun recoveredOutcome(): ExamStartOutcome.Recovered = ExamStartOutcome.Recovered(
    sitting = sittingProjection(index = 0, withAck = true),
)

private fun sittingProjection(index: Int, withAck: Boolean): ExamSittingProjection =
    ExamSittingProjection(
        sittingId = "s1",
        mode = ExamMode.THEME_TEST,
        assessmentId = "th0",
        state = ExamSittingState.OPEN,
        currentIndex = index,
        totalQuestions = 20,
        rules = ExamSittingRules(60, 60),
        nextQuestion = ExamQuestionProjection(
            index = index,
            questionId = "e0",
            revision = 2,
            shownDifficulty = ExamShownDifficulty.EASY,
            answerSeconds = 30,
            publicPayload = "{}",
            ack = if (withAck) {
                com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamAck(
                    ackedAtMs = 1,
                    answerDeadlineMs = 31,
                )
            } else {
                null
            },
            awaitingAck = if (withAck) null else {
                com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamAwaitingAck(
                    windowDeadlineMs = 1,
                )
            },
            serverNowMs = 1,
        ),
    )
