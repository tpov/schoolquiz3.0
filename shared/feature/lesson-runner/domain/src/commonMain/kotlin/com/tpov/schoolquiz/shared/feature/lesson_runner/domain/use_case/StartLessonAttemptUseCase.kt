package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
import com.tpov.schoolquiz.shared.core.question_schema.QuestionDisplay
import com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.selectSubset
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.question.domain.model.Question
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Builds [RunnerState.Ready] from a lesson and mode selection.
 *
 * Happy path: auth → lesson snapshot → question snapshot → parse + filter →
 *   selectSubset → assign codeAnswerIndex → RunnerState.Ready.
 *
 * Failures (no throw): InitFailed(AuthRequired | LessonNotFound | EmptyPool |
 *   RedactedNotSupported | NoValidQuestions).
 */
class StartLessonAttemptUseCase(
    private val questionRepository: QuestionRepository,
    private val lessonRepository: LessonRepository,
    private val parser: QuestionContentParser,
    private val authRepository: AuthRepository,
    private val clock: Clock,
    private val randomSeedProvider: () -> Long,
    private val timerCoefficients: TimerCoefficients = TimerCoefficients.Default,
    private val preparationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(
        lessonId: LessonId,
        mode: Difficulty,
        sessionMode: SessionMode = SessionMode.LEARNING,
    ): RunnerState {
        val userId =
            authRepository.currentUid()
                ?: return RunnerState.InitFailed(InitFailureReason.AuthRequired)

        val lesson =
            lessonRepository.getById(lessonId)
                ?: return RunnerState.InitFailed(InitFailureReason.LessonNotFound)

        val questions = questionRepository.observeByLesson(lessonId).first()
        val preparation = withContext(preparationDispatcher) { prepareQuestionPool(questions, mode, parser) }
        val indexed =
            when (preparation) {
                is QuestionPoolPreparation.Failed -> return RunnerState.InitFailed(preparation.reason)
                is QuestionPoolPreparation.Ready -> preparation.indexedQuestions
            }
        val eligibleSize = indexed.size

        // Random subset (deterministic via seed)
        val seed = randomSeedProvider()
        val (playOrder, initialCodeAnswer) =
            withContext(preparationDispatcher) {
                // Random play order: selectSubset already shuffles via seed, keep the random order
                // for question presentation. codeAnswerIndex preserves stable code position.
                selectSubset(indexed, POOL_SIZE, seed) to CodeAnswer("0".repeat(eligibleSize))
            }

        val nowMs = clock.now().toEpochMilliseconds()
        val firstDuration = computeTimer(playOrder.first().content, mode, timerCoefficients, sessionMode)
        val deadlineMs = nowMs + firstDuration.seconds * 1000L

        return RunnerState.Ready(
            userId = userId,
            lessonId = lessonId,
            lessonVersion = lesson.version,
            mode = mode,
            playOrder = playOrder,
            eligibleSize = eligibleSize,
            indexInPool = 0,
            codeAnswer = initialCodeAnswer,
            deadlineMs = deadlineMs,
            seed = seed,
            currentDraftAnswer = null,
            isPaused = false,
            sessionMode = sessionMode,
            questionStartedAtMs = nowMs,
        )
    }

    companion object {
        const val POOL_SIZE = 20
    }
}

private sealed interface QuestionPoolPreparation {
    data class Ready(val indexedQuestions: List<RunnerQuestion.Valid>) : QuestionPoolPreparation

    data class Failed(val reason: InitFailureReason) : QuestionPoolPreparation
}

private suspend fun prepareQuestionPool(
    questions: List<Question>,
    mode: Difficulty,
    parser: QuestionContentParser,
): QuestionPoolPreparation {
    val coroutineContext = currentCoroutineContext()
    coroutineContext.ensureActive()
    val activeQuestions = questions.filter { !it.archived }.dedupeTranslatedVariants()
    coroutineContext.ensureActive()

    val parsed = ArrayList<ParsedQuestion>(activeQuestions.size)
    activeQuestions.forEachIndexed { index, question ->
        if (index % PREPARATION_CANCELLATION_CHECK_INTERVAL == 0) coroutineContext.ensureActive()
        parsed += parser.readForRunner(question, mode)
    }
    coroutineContext.ensureActive()

    val valids = parsed.filterIsInstance<ParsedQuestion.Playable>().map { it.question }

    // Only redacted questions that could plausibly have been played at this difficulty count.
    val redactedForMode =
        parsed
            .filterIsInstance<ParsedQuestion.Redacted>()
            .count { it.difficulty == mode || it.difficulty == null }

    // Preserve failure priority: broken payloads are more actionable than a genuinely empty pool.
    if (activeQuestions.isNotEmpty() && valids.isEmpty() && redactedForMode == 0) {
        return QuestionPoolPreparation.Failed(InitFailureReason.NoValidQuestions)
    }

    val eligible = valids.filter { it.content.difficulty == mode }
    if (eligible.isEmpty() && redactedForMode > 0) {
        return QuestionPoolPreparation.Failed(InitFailureReason.RedactedNotSupported)
    }
    if (eligible.isEmpty()) {
        return QuestionPoolPreparation.Failed(InitFailureReason.EmptyPool)
    }

    // Assign codeAnswerIndex per sorted eligible position (full pool, not subset).
    val sorted = eligible.sortedWith(compareBy({ it.order }, { it.sourceId.value }))
    coroutineContext.ensureActive()
    val indexed = sorted.mapIndexed { index, question -> question.copy(codeAnswerIndex = index) }
    coroutineContext.ensureActive()
    return QuestionPoolPreparation.Ready(indexed)
}

private const val PREPARATION_CANCELLATION_CHECK_INTERVAL = 64

private fun List<Question>.dedupeTranslatedVariants() =
    groupBy { it.id.value.canonicalQuestionId() }
        .values
        .map { variants ->
            variants.minWith(
                compareBy<Question>(
                    { if (it.id.value == it.id.value.canonicalQuestionId()) 0 else 1 },
                    { it.order },
                    { it.id.value },
                ),
            )
        }

private fun String.canonicalQuestionId(): String {
    val separatorIndex = lastIndexOf("__")
    if (separatorIndex <= 0 || separatorIndex >= lastIndex - 1) return this
    val suffix = substring(separatorIndex + 2)
    val isLanguageSuffix = suffix.length in 2..8 && suffix.all { it.isLetter() || it == '-' }
    return if (isLanguageSuffix) substring(0, separatorIndex) else this
}

/**
 * What one stored question turned out to be, once read through the entry point that understands
 * both hierarchies.
 *
 * `parse` would have refused a redacted payload and `mapNotNull` would have dropped the refusal,
 * which is exactly how a redacted lesson came to report itself as broken bytes. Naming the three
 * outcomes is what lets the caller tell them apart.
 */
private sealed interface ParsedQuestion {
    /** An ordinary question, with its answer key, ready for the pool. */
    data class Playable(val question: RunnerQuestion.Valid) : ParsedQuestion

    /**
     * A question whose answer key was removed. [difficulty] is nullable because the wire form is a
     * free string that may be absent, empty, or a name the enum has no case for — all three are
     * shapes `question-redaction.js` really emits. See [QuestionDisplay.difficultyOrNull].
     */
    data class Redacted(val difficulty: Difficulty?) : ParsedQuestion

    /** The payload could not be read at all. */
    data object Unreadable : ParsedQuestion
}

/**
 * Reads one question and says which of the three it is.
 *
 * [QuestionDisplay] is deliberately not sealed, so the `when` below cannot be exhaustive and the
 * `else` is a real branch rather than a formality. Anything arriving there is a third implementor
 * this code has never been told about; letting it fall silently into "the payloads are broken"
 * would hide it behind the very message this slice exists to stop overloading, so it fails loudly.
 */
private fun QuestionContentParser.readForRunner(
    question: Question,
    mode: Difficulty,
): ParsedQuestion {
    val display =
        parseForDisplay(
            payload = question.payload,
            fallbackId = question.id.value,
            fallbackText = question.text,
            fallbackDifficulty = mode,
        ).getOrNull()

    return when (display) {
        null -> ParsedQuestion.Unreadable
        is QuestionContent ->
            ParsedQuestion.Playable(
                RunnerQuestion.Valid(
                    sourceId = question.id,
                    order = question.order,
                    codeAnswerIndex = -1,
                    content = display,
                ),
            )
        is RedactedQuestionContent -> ParsedQuestion.Redacted(display.difficultyOrNull)
        else ->
            error(
                "QuestionContentParser returned an unrecognised QuestionDisplay: ${display::class.simpleName}",
            )
    }
}
