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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

    /**
     * Техническая неудача с ясным источником: копия экрана и повтор зависят от того, ЧТО не
     * получилось — поставить попытку в очередь или прочитать результат.
     */
    data class ReadFailed(val source: FailureSource) : PrivateQuizRunUiState {
        enum class FailureSource { ENQUEUE, READ }
    }
}

/**
 * Машина прогона приватного квиза (AD-29/AD-31, эпик 8, история 8.4).
 *
 * Что она делает: дверь (`startPrivateQuizAttempt` — единственная) → один непрерывный заход по
 * замороженному набору → подача в общую очередь (`private_quiz.SUBMIT_ATTEMPT`, ключ — startId) →
 * чтение собственного результата по ТЕКУЩЕЙ настройке showResult.
 *
 * Чей ключ. startId живёт ЗДЕСЬ и назначается первой дверью: любая повторная дверь
 * (UNAVAILABLE, повтор после обрыва) едет с тем же ключом и не тратит вторую попытку лимита
 * (AD-6). Смена ключа означала бы двойной расход — поэтому повтор двери возможен только с тем
 * же startId.
 *
 * Чей охват. Машина сама владеет своим скоупом и живёт в InstanceKeeper экрана: поворот и фон
 * её не убивают (техническое восстановление в исходных дедлайнах); смерть процесса закрывает
 * попытку серверным тайм-аутом — продолжения не существует (AD-31).
 *
 * Время. Часы вопроса — исходные: срок вопроса i выводится из расписания от `startedAtMs`
 * (HARD-время вопроса + окно AD-31 между вопросами), срезается серверным `deadlineMs` попытки.
 * Ни ответ, ни reconnect, ни повтор не перезапускают часы (AD-31); просроченный вопрос
 * закрывается как показанный-без-ответа, и попытка продолжается следующим вопросом в общем
 * бюджете.
 *
 * Бесплатность (8.5): машина не зовёт ни одной платной формы — ни зарядов, ни подсказок, ни
 * согласия с условиями; терминал пишет только подачу попытки, книгу движений не трогает.
 */
class PrivateQuizRunMachine(
    private val request: com.tpov.schoolquiz.android.feature.lesson_runner.presentation.PrivateQuizRunnerRequest,
    private val startDoor: suspend (String?) -> Pair<String?, PrivateQuizEntry>,
    private val submit: suspend (startId: String, submission: PrivateQuizSubmission) -> Result<Unit>,
    private val readResult: suspend (startId: String) -> Result<PrivateQuizOwnResult>,
    private val parser: QuestionContentParser,
    private val clock: Clock,
    mainContext: CoroutineDispatcher,
) : InstanceKeeper.Instance {
    private val scope = CoroutineScope(SupervisorJob() + mainContext)

    private val _state = MutableStateFlow<PrivateQuizRunUiState>(PrivateQuizRunUiState.DoorChecking)
    val state: StateFlow<PrivateQuizRunUiState> = _state.asStateFlow()

    /** Ключ намерения; назначается первой дверью и больше не меняется (AD-6). */
    var startId: String? = null
        private set

    private var run: PrivateQuizRun? = null
    private var schedule: List<QuestionSlot> = emptyList()
    private val drafts = mutableMapOf<Int, UserAnswerDraft>()
    private val answers = mutableMapOf<Int, UserAnswerDraft?>()
    private var reachedIndex = -1
    private var doorJob: Job? = null

    /** Замороженная подача: переживает неудачную постановку в очередь и переедет тем же ключом. */
    private var pendingSubmission: PrivateQuizSubmission? = null

    /**
     * Открыть дверь. Повтор — только тем же [startId]: смена ключа тратила бы вторую попытку
     * лимита (AD-6). За пределами «дверь ещё не решена / отказ двери» — no-op: повторный вызов
     * с выжившей машиной (поворот экрана) дверь не перевпускает.
     */
    fun openDoor() {
        if (doorJob?.isActive == true) return
        when (_state.value) {
            is PrivateQuizRunUiState.DoorChecking, is PrivateQuizRunUiState.Refused -> Unit
            else -> return
        }
        doorJob =
            scope.launch {
                val (usedId, entry) = startDoor(startId)
                usedId?.let { startId = it }
                when (entry) {
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
        if (index >= 0) answers[index] = null
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
            _state.value = PrivateQuizRunUiState.ReadFailed(PrivateQuizRunUiState.ReadFailed.FailureSource.READ)
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
                // ответом не становится — expiry закрывает вопрос без ответа, AD-31), попытка
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
        scope.launch { doSubmit() }
    }

    private suspend fun doSubmit() {
        val startId = this.startId ?: return
        val served =
            schedule.take(reachedIndex + 1).map { slot ->
                PrivateQuizSubmission.Served(questionId = slot.questionId, codeAnswerIndex = slot.codeAnswerIndex)
            }
        val submission =
            pendingSubmission ?: run {
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
                PrivateQuizSubmission(served = served, answers = submitted).also { pendingSubmission = it }
            }
        submit(startId, submission)
            .onFailure {
                // Очередь не приняла намерение: тело заморожено и переедет тем же ключом
                // по кнопке обновления; результат без подачи не читается.
                _state.value = PrivateQuizRunUiState.ReadFailed(PrivateQuizRunUiState.ReadFailed.FailureSource.ENQUEUE)
                return
            }
        awaitResult(tries = RESULT_READ_TRIES)
    }

    /** Терминал появляется, когда очередь доставила подачу; чтение повторяется ограниченно. */
    private suspend fun awaitResult(tries: Int) {
        val startId = this.startId ?: return
        repeat(tries) { attempt ->
            val result = readResult(startId)
            val own = result.getOrNull()
            if (own != null && own.attemptStatus == com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizAttemptStatus.TERMINAL) {
                pendingSubmission = null
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

    /**
     * Ручное обновление из [PrivateQuizRunUiState.ResultPending] / [PrivateQuizRunUiState.ReadFailed]:
     * неудавшаяся постановка переезжает той же замороженной подачей и тем же ключом; неудавшееся
     * чтение — просто повтор чтения.
     */
    fun onRefreshResult() {
        if (doorJob?.isActive == true) return
        when (_state.value) {
            is PrivateQuizRunUiState.ReadFailed -> {
                _state.value = PrivateQuizRunUiState.Submitting
                doorJob =
                    scope.launch {
                        if (pendingSubmission != null) {
                            doSubmit()
                        } else {
                            awaitResult(tries = RESULT_READ_TRIES)
                        }
                    }
            }
            is PrivateQuizRunUiState.ResultPending -> {
                _state.value = PrivateQuizRunUiState.Submitting
                doorJob =
                    scope.launch { awaitResult(tries = RESULT_READ_TRIES) }
            }
            else -> Unit
        }
    }

    fun nowMs(): Long = clock.now().toEpochMilliseconds()

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
