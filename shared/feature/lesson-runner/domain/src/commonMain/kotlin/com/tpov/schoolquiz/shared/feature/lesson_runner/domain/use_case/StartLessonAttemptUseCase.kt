package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
import com.tpov.schoolquiz.shared.core.question_schema.QuestionDisplay
import com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.selectSubset
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.question.domain.model.Question
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import kotlinx.coroutines.flow.first
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
) {
    suspend operator fun invoke(
        lessonId: LessonId,
        mode: Difficulty,
        sessionMode: SessionMode = SessionMode.LEARNING,
    ): RunnerState {
        val userId = authRepository.currentUid()
            ?: return RunnerState.InitFailed(InitFailureReason.AuthRequired)

        val lesson = lessonRepository.getById(lessonId)
            ?: return RunnerState.InitFailed(InitFailureReason.LessonNotFound)

        val questions = questionRepository.observeByLesson(lessonId).first()
        val activeQuestions = questions
            .filter { !it.archived }
            .dedupeTranslatedVariants()

        val parsed = activeQuestions.map { q -> parser.readForRunner(q, mode) }

        val valids = parsed.filterIsInstance<ParsedQuestion.Playable>().map { it.question }

        // Only the redacted questions that could plausibly have been played at this difficulty.
        // A redacted question that definitely belongs to the other pool explains nothing about why
        // this one is empty, and counting it would make an easy-only lesson opened as hard blame
        // redaction for what is simply an absence of hard questions.
        val redactedForMode = parsed
            .filterIsInstance<ParsedQuestion.Redacted>()
            .count { it.difficulty == mode || it.difficulty == null }

        // Priority 1: active questions exist, none is playable, and none of the unplayable ones was
        // redacted into this pool — so the payloads really are broken. The `redactedForMode == 0`
        // guard is what makes a lesson mixing broken and redacted payloads report the redaction
        // instead: a removed answer key is the more specific and the more actionable fact.
        if (activeQuestions.isNotEmpty() && valids.isEmpty() && redactedForMode == 0) {
            return RunnerState.InitFailed(InitFailureReason.NoValidQuestions)
        }

        val eligible = valids.filter { it.content.difficulty == mode }

        // Priority 2: nothing left to play at this difficulty, and questions that might have filled
        // it were withheld. Sits above EmptyPool because "there are none for this difficulty" is
        // true but says nothing, when the reason there are none is that their answer keys were
        // removed.
        if (eligible.isEmpty() && redactedForMode > 0) {
            return RunnerState.InitFailed(InitFailureReason.RedactedNotSupported)
        }

        // Priority 3: no eligible questions for selected difficulty
        if (eligible.isEmpty()) {
            return RunnerState.InitFailed(InitFailureReason.EmptyPool)
        }

        // Assign codeAnswerIndex per sorted eligible position (full pool, not subset)
        val sorted = eligible.sortedWith(compareBy({ it.order }, { it.sourceId.value }))
        val indexed = sorted.mapIndexed { idx, rq -> rq.copy(codeAnswerIndex = idx) }
        val eligibleSize = indexed.size

        // Random subset (deterministic via seed)
        val seed = randomSeedProvider()
        val subset = selectSubset(indexed, POOL_SIZE, seed)

        // Random play order: selectSubset already shuffles via seed, keep the random order
        // for question presentation. codeAnswerIndex on each item preserves stable code position.
        val playOrder = subset

        val initialCodeAnswer = CodeAnswer("0".repeat(eligibleSize))

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
private fun QuestionContentParser.readForRunner(question: Question, mode: Difficulty): ParsedQuestion {
    val display = parseForDisplay(
        payload = question.payload,
        fallbackId = question.id.value,
        fallbackText = question.text,
        fallbackDifficulty = mode,
    ).getOrNull()

    return when (display) {
        null -> ParsedQuestion.Unreadable
        is QuestionContent -> ParsedQuestion.Playable(
            RunnerQuestion.Valid(
                sourceId = question.id,
                order = question.order,
                codeAnswerIndex = -1,
                content = display,
            ),
        )
        is RedactedQuestionContent -> ParsedQuestion.Redacted(display.difficultyOrNull)
        else -> error(
            "QuestionContentParser returned an unrecognised QuestionDisplay: ${display::class.simpleName}",
        )
    }
}
