package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.exam

import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamSittingRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamTermsWire
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.examAckVerdict
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamAckOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamFetchOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamRefusal
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamStartOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamSubmitOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Логика экзаменационного прогона — состояние без Decompose и без Compose, проверяемое на JVM.
 *
 * Контракт протокола, который держит класс:
 *
 * - **Старт** строит ключ операции на каждое НАМЕРЕНИЕ (не на попытку): потерянный ответ
 *   восстанавливается тем же ключом, новое намерение — новым. Восстановление открытого сидинга
 *   ничего не стоит и возвращает исходные дедлайны.
 * - **ACK** закрепляет дедлайн первым подтверждением; дубликат возвращает закреплённое; после
 *   закрепления отсчёт ведётся от серверного дедлайна, не от локальных часов.
 * - **Ответ** привязан к редакции вопроса и телу; повтор того же тела — та же квитанция.
 * - **Результат** читается, а не считается: проценты, звёзды, проход и совет рисуются как есть.
 */
class ExamRunStateMachine(
    private val remote: ExamSittingRemoteDataSource,
    private val newOperationId: () -> String,
) {
    private val state = MutableStateFlow<ExamRunView>(
        ExamRunView.Entry(
            mode = ExamMode.THEME_TEST,
            assessmentId = "",
            entryPrice = 0,
            termsShown = false,
            termsAccepted = false,
            requiredTermsRevision = 0,
            refusal = null,
            busy = false,
        ),
    )
    val view: StateFlow<ExamRunView> = state

    var sittingId: String? = null
        private set

    /** operationId текущего НАМЕРЕНИЯ старта: переживает потерю ответа, меняется на новый старт. */
    var startOperationId: String = newOperationId()
        private set

    fun showEntry(mode: ExamMode, assessmentId: String, entryPrice: Int, requiredTermsRevision: Long) {
        state.value = ExamRunView.Entry(
            mode = mode,
            assessmentId = assessmentId,
            entryPrice = entryPrice,
            termsShown = state.value.let { it is ExamRunView.Entry && it.termsShown },
            termsAccepted = state.value.let { it is ExamRunView.Entry && it.termsAccepted },
            requiredTermsRevision = requiredTermsRevision,
            refusal = null,
            busy = false,
        )
    }

    fun acceptTerms(shown: Boolean, acceptedRevision: Long) {
        val entry = state.value as? ExamRunView.Entry ?: return
        state.value = entry.copy(termsShown = shown, termsAccepted = shown && acceptedRevision >= entry.requiredTermsRevision)
    }

    /** Старт или восстановление: тот же operationId, пока намерение не сменено. */
    suspend fun start(mode: ExamMode, assessmentId: String) {
        val entry = state.value as? ExamRunView.Entry ?: return
        state.value = entry.copy(busy = true)
        val terms = ExamTermsWire(
            shown = entry.termsShown,
            acceptedRevision = if (entry.termsAccepted) entry.requiredTermsRevision else 0,
        )
        val outcome = when (mode) {
            ExamMode.THEME_TEST ->
                remote.startThemeTest(startOperationId, ACCOUNT_EPOCH, assessmentId, terms)
            ExamMode.FINAL_EXAM ->
                remote.startFinalExam(startOperationId, ACCOUNT_EPOCH, assessmentId, terms)
        }.getOrElse { failure ->
            state.value = entry.copy(
                busy = false,
                refusal = ExamRefusal.Unavailable(failure.javaClass.simpleName.uppercase()),
            )
            return
        }
        when (outcome) {
            is ExamStartOutcome.Started -> {
                sittingId = outcome.sitting.sittingId
                state.value = runningOf(outcome.sitting)
            }
            is ExamStartOutcome.Recovered -> {
                sittingId = outcome.sitting.sittingId
                state.value = runningOf(outcome.sitting)
            }
            is ExamStartOutcome.Refused -> state.value = entry.copy(busy = false, refusal = outcome.refusal)
        }
    }

    /** Смена намерения: следующий старт — новый ключ операции. */
    fun renewStartIntent() {
        startOperationId = newOperationId()
    }

    private fun runningOf(sitting: com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamSittingProjection): ExamRunView.Running {
        val question = sitting.nextQuestion
        return ExamRunView.Running(
            sitting = sitting,
            question = question,
            awaitingAck = question != null && question.ack == null,
            answerDraft = "",
            busy = false,
        )
    }

    /**
     * Черновик ответа — ЛОКАЛЬНОЕ состояние ввода: каждый keystroke в транспорт не уходит
     * (первый частичный ответ побеждал бы на сервере), отправка — только кнопкой.
     */
    fun setAnswerDraft(draft: String) {
        val running = state.value as? ExamRunView.Running ?: return
        state.value = running.copy(answerDraft = draft)
    }

    /** Первый ACK или дубликат: дедлайн возвращает сервер, локально ничего не закрепляется. */
    suspend fun acknowledgeCurrent() {
        val running = state.value as? ExamRunView.Running ?: return
        val question = running.question ?: return
        val id = sittingId ?: return
        // Вердикт зеркала: после закрепления ACK не зовётся вовсе.
        val decision = examAckVerdict(
            established = question.ack,
            nowMs = question.serverNowMs,
            boundForQuestion = question.awaitingAck?.windowDeadlineMs,
            answerSeconds = question.answerSeconds,
        )
        if (decision is com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.ExamAckDecision.Duplicate) return
        state.value = running.copy(busy = true)
        when (
            val outcome = remote.acknowledgeQuestion(id, question.index, newOperationId())
                .getOrElse {
                    state.value = running.copy(busy = false)
                    return
                }
        ) {
            is ExamAckOutcome.Acknowledged -> {
                val refreshed = outcome.ack.let { ack ->
                    running.question?.copy(ack = ack, awaitingAck = null)
                }
                state.value = running.copy(busy = false, question = refreshed, awaitingAck = false)
            }
            is ExamAckOutcome.Refused -> state.value = running.copy(busy = false)
        }
    }

    /** Ответ: тело и редакция связываются сервером; повтор возвращает ту же квитанцию. */
    suspend fun submitAnswer(answerJson: String) {
        val running = state.value as? ExamRunView.Running ?: return
        val question = running.question ?: return
        val id = sittingId ?: return
        state.value = running.copy(busy = true, answerDraft = answerJson)
        when (
            val outcome = remote.submitAnswer(id, question.index, question.revision, answerJson, newOperationId())
                .getOrElse {
                    state.value = running.copy(busy = false)
                    return
                }
        ) {
            is ExamSubmitOutcome.Submitted -> state.value = runningOf(outcome.sitting)
            is ExamSubmitOutcome.Refused -> state.value = running.copy(busy = false)
        }
    }

    /** Раннее закрытие: передние вызовы не терминализируют — worker закрывает. */
    suspend fun requestFinish() {
        val id = sittingId ?: return
        state.value = ExamRunView.AwaitingClosure(sittingId = id, busy = true)
        remote.finishSitting(id)
    }

    /** Чтение результата: до закрытия worker'ом — явное ожидание, не выдуманный итог. */
    suspend fun fetchResult() {
        val id = sittingId ?: return
        when (val outcome = remote.fetchSittingResult(id).getOrElse { return }) {
            is ExamFetchOutcome.Open -> {
                val current = state.value
                if (current is ExamRunView.AwaitingClosure) {
                    state.value = current.copy(busy = false)
                }
            }
            is ExamFetchOutcome.Terminal -> state.value = ExamRunView.Finished(outcome.result)
        }
    }

    private companion object {
        /** Epoch аккаунта в теле старта: A→B→A отбивается сервером, клиент называет свой. */
        const val ACCOUNT_EPOCH = 0L
    }
}
