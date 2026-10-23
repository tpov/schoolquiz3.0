package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toQuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
import com.tpov.schoolquiz.shared.core.question_schema.QuestionDisplay
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizAnswerCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizEntryRefusal
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizOwnResult
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizRun
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizRunQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizSubmission
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** Экран приватного прогона: одно из этих состояний на экране, третьего не показывается. */
sealed interface PrivateQuizRunUiState {
    /** Дверь проверяется: ссылка/лимит решает сервер. */
    data object DoorChecking : PrivateQuizRunUiState

    /** Именованный отказ двери (AD-21): текст — по причине, не по чтению сообщения. */
    data class Refused(val reason: PrivateQuizEntryRefusal) : PrivateQuizRunUiState

    /** Текущий вопрос с его исходным сроком. */
    data class Question(
        val question: QuestionUiState,
        val indexInPool: Int,
        val totalInPool: Int,
        val deadlineMs: Long,
        val currentDraft: UserAnswerDraft?,
    ) : PrivateQuizRunUiState

    /** Финиш: подача поставлена в очередь, результат читается. */
    data object Submitting : PrivateQuizRunUiState

    /** Подача в очереди ждёт сети: результат появится, когда очередь дойдёт (ручное обновление). */
    data object ResultPending : PrivateQuizRunUiState

    /** Терминал прочитан: показывает либо подтверждение, либо собственный результат (AD-29). */
    data class Finished(val result: PrivateQuizOwnResult) : PrivateQuizRunUiState

    /** Транспортная/техническая неудача чтения результата — повторяемо вручную. */
    data class ReadFailed(val retryable: Boolean = true) : PrivateQuizRunUiState
}

/**
 * Машина прогона приватного квиза (AD-29/AD-31, эпик 8, история 8.4).
 *
 * Что она делает: дверь (`startPrivateQuizAttempt` — единственная) → один непрерывный заход по
 * замороженному набору → подача в общую очередь (`private_quiz.SUBMIT_ATTEMPT`, ключ — startId) →
 * чтение собственного результата по ТЕКУЩЕЙ настройке showResult.
 *
 * Время. Часы вопроса — исходные: срок вопроса i выводится из расписания от `startedAtMs`
 * (HARD-время вопроса + окно AD-31 между вопросами), срезается серверным `deadlineMs` попытки.
 * Ни ответ, ни reconnect, ни повтор не перезапускают часы (AD-31); просроченный вопрос
 * закрывается как показанный-без-ответа, и попытка продолжается следующим вопросом в общем
 * бюджете. Никаких чекпоинтов и отложенного продолжения: один заход (AD-29), техническое
 * восстановление живёт в InstanceKeeper экрана и за его пределами попытка честно закрывается
 * серверным тайм-аутом.
 *
 * Бесплатность (8.5): машина не зовёт ни одной платной формы — ни зарядов, ни подсказок, ни
 * согласия с условиями; терминал пишет только подачу попытки, книгу движений не трогает.
 */
class PrivateQuizRunMachine(
    private val request: com.tpov.schoolquiz.android.feature.lesson_runner.presentation.PrivateQuizRunnerRequest,
    private val startDoor: suspend (String?) -> PrivateQuizEntry,
    private val submit: suspend (PrivateQuizSubmission) -> Result<Unit>,
    private val readResult: suspend () -> Result<PrivateQuizOwnResult>,
    private val parser: QuestionContentParser,
    private val clock: Clock,
    private val scope: CoroutineScope,
) : InstanceKeeper.Instance {
    private val _state = MutableStateFlow<PrivateQuizRunUiState>(PrivateQuizRunUiState.DoorChecking)
    val state: StateFlow<PrivateQuizRunUiState> = _state.asStateFlow()

    private var run: PrivateQuizRun? = null
    private var schedule: List<QuestionSlot> = emptyList()
    private val drafts = mutableMapOf<Int, UserAnswerDraft>()
    private val answers = mutableMapOf<Int, UserAnswerDraft?>()
    private var reachedIndex = -1
    private var doorJob: Job? = null

    /** Открыть дверь; [existingStartId] — повтор того же намерения (AD-6), не новый вход. */
    fun openDoor(existingStartId: String? = null) {
        if (doorJob?.isActive == true) return
        _state.value = PrivateQuizRunUiState.DoorChecking
        doorJob =
            scope.launch {
                when (val entry = startDoor(existingStartId)) {
                    is PrivateQuizEntry.Ready -> beginRun(entry.run)
                    is PrivateQuizEntry.Refused -> _state.value = PrivateQuizRunUiState.Refused(entry.reason)
                }
            }
    }

    /** Выбрать вариант(ы) / черновик ответа — до нажатия «Ответить». */
    fun onDraftChanged(draft: UserAnswerDraft) {
        val index = reachedIndex
        if (index >= 0) drafts[index] = draft
        val current = _state.value as? PrivateQuizRunUiState.Question ?: return
        _state.value = current.copy(currentDraft = draft)
    }

    /** «Ответить»: ответ принят, окно вопроса закрыто принятым ответом (AD-31). */
    fun onAnswer() {
        val index = reachedIndex
        if (index >= 0) answers[index] = drafts[index]
        advance()
    }

    /** Истёк срок вопроса: закрывается показанным-без-ответа, попытка идёт дальше (AD-31). */
    fun onTimeout() {
        val index = reachedIndex
        if (index >= 0) answers[index] = drafts[index]?.takeIf { false }
        advance()
    }

    private fun beginRun(run: PrivateQuizRun) {
        this.run = run
        // Расписание строится по префиксам, а не по недостроенному списку: старт вопроса i —
        // сумма времён всех предыдущих плюс их окна (AD-31).
        val slots = ArrayList<QuestionSlot>(run.questions.size)
        var cumulativeMs = 0L
        for ((index, question) in run.questions.withIndex()) {
            val start = run.startedAtMs + cumulativeMs + index * NEXT_QUESTION_WINDOW_MS
            val slot =
                buildSlot(run, question, index, start)
                    ?: return // ReadFailed уже поднят: нечитаемый набор не показывается.
            slots += slot
            cumulativeMs += slot.durationMs
        }
        schedule = slots
        reachedIndex = -1
        advance()
    }

    private fun buildSlot(
        run: PrivateQuizRun,
        question: PrivateQuizRunQuestion,
        index: Int,
        startMs: Long,
    ): QuestionSlot? {
        val display =
            parser
                .parseForDisplay(
                    payload = question.payload,
                    fallbackId = question.id,
                    fallbackText = question.payload,
                    fallbackDifficulty = com.tpov.schoolquiz.shared.core.question_schema.Difficulty.HARD,
                )
                .getOrNull()
        if (display == null) {
            // Нечитаемый вопрос выданного набора — наша дыра на клиенте: попытка не показывается
            // и не «показанной без ответа», а честно отказывает чтением (повтор за дверью).
            _state.value = PrivateQuizRunUiState.ReadFailed(retryable = false)
            return null
        }
        val seconds =
            computeTimer(display, com.tpov.schoolquiz.shared.core.question_schema.Difficulty.HARD, TimerCoefficients.Default)
                .seconds
        return QuestionSlot(
            questionId = question.id,
            codeAnswerIndex = index,
            display = display,
            startMs = startMs,
            durationMs = seconds * 1000L,
        )
    }

    /**
     * Идти к первому незакрытому вопросу расписания; пропуская просроченные как показанные-
     * без-ответа. За серверным сроком попытки — подача: всё, что было показано, едет «без
     * ответа», и терминал закрывает попытку (продолжения не существует, AD-29).
     */
    private fun advance() {
        val run = run ?: return
        val now = clock.now().toEpochMilliseconds()
        while (reachedIndex + 1 < schedule.size) {
            val next = reachedIndex + 1
            val slot = schedule[next]
            val deadline = minOf(slot.startMs + slot.durationMs, run.deadlineMs)
            if (now >= deadline && next < schedule.size - 1) {
                // Просрочен и НЕ последний: закрывается показанным-без-ответа (черновик ввода
                // ответом не становится —expiry закрывает вопрос без ответа, AD-31), попытка
                // идёт дальше по тому же расписанию.
                answers[next] = null
                reachedIndex = next
                continue
            }
            reachedIndex = next
            _state.value =
                PrivateQuizRunUiState.Question(
                    question = slot.display.toQuestionUiState(draft = drafts[next]),
                    indexInPool = next,
                    totalInPool = schedule.size,
                    deadlineMs = deadline,
                    currentDraft = drafts[next],
                )
            return
        }
        // Всё, что было показано, показано — подача.
        finish()
    }

    private fun finish() {
        _state.value = PrivateQuizRunUiState.Submitting
        scope.launch {
            val served =
                schedule.take(reachedIndex + 1).map { slot ->
                    PrivateQuizSubmission.Served(questionId = slot.questionId, codeAnswerIndex = slot.codeAnswerIndex)
                }
            val submitted =
                answers.entries
                    .sortedBy { it.key }
                    .map { (index, draft) ->
                        val slot = schedule[index]
                        PrivateQuizSubmission.Answer(
                            questionId = slot.questionId,
                            codeAnswerIndex = slot.codeAnswerIndex,
                            answerPayload = draft?.let(PrivateQuizAnswerCodec::answerPayload),
                            answeredAtMs = clock.now().toEpochMilliseconds(),
                        )
                    }
            submit(
                PrivateQuizSubmission(served = served, answers = submitted),
            ).onFailure { error ->
                // Очередь не приняла намерение (нет хранилища) — повтор вручную; тело не потеряно.
                _state.value = PrivateQuizRunUiState.ReadFailed(retryable = error.message?.contains("OutboxStore") != true)
                return@launch
            }
            awaitResult(tries = RESULT_READ_TRIES)
        }
    }

    /** Терминал появляется, когда очередь доставила подачу; чтение повторяется ограниченно. */
    private suspend fun awaitResult(tries: Int) {
        repeat(tries) { attempt ->
            val result = readResult()
            val own = result.getOrNull()
            if (own != null && own.attemptStatus == com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizAttemptStatus.TERMINAL) {
                _state.value = PrivateQuizRunUiState.Finished(own)
                return
            }
            if (attempt == tries - 1) {
                _state.value = PrivateQuizRunUiState.ResultPending
                return
            }
            delay(RESULT_READ_PAUSE_MS)
        }
    }

    /** Ручное обновление из [PrivateQuizRunUiState.ResultPending]. */
    fun onRefreshResult() {
        if (_state.value !is PrivateQuizRunUiState.ResultPending && _state.value !is PrivateQuizRunUiState.ReadFailed) return
        _state.value = PrivateQuizRunUiState.Submitting
        scope.launch { awaitResult(tries = RESULT_READ_TRIES) }
    }

    private data class QuestionSlot(
        val questionId: String,
        val codeAnswerIndex: Int,
        val display: QuestionDisplay,
        val startMs: Long,
        val durationMs: Long,
    )

    override fun onDestroy() {
        scope.cancel()
    }

    private companion object {
        /** Окно загрузки следующего вопроса (AD-31) — общая серверная константа, 60 секунд. */
        const val NEXT_QUESTION_WINDOW_MS: Long = 60_000L
        const val RESULT_READ_TRIES: Int = 4
        const val RESULT_READ_PAUSE_MS: Long = 1_500L
    }
}
