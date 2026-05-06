package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.selectSubset
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
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
 * Failures (no throw): InitFailed(AuthRequired | LessonNotFound | EmptyPool | NoValidQuestions).
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
    suspend operator fun invoke(lessonId: LessonId, mode: Difficulty): RunnerState {
        val userId = authRepository.currentUid()
            ?: return RunnerState.InitFailed(InitFailureReason.AuthRequired)

        val lesson = lessonRepository.getById(lessonId)
            ?: return RunnerState.InitFailed(InitFailureReason.LessonNotFound)

        val questions = questionRepository.observeByLesson(lessonId).first()
        val activeQuestions = questions
            .filter { !it.archived }
            .dedupeTranslatedVariants()

        val valids = activeQuestions.mapNotNull { q ->
            parser.parse(
                payload = q.payload,
                fallbackId = q.id.value,
                fallbackText = q.text,
                fallbackDifficulty = mode,
            ).getOrNull()?.let { content ->
                RunnerQuestion.Valid(
                    sourceId = q.id,
                    order = q.order,
                    codeAnswerIndex = -1,
                    content = content,
                )
            }
        }

        // Priority 1: active questions exist but all payloads are invalid
        if (activeQuestions.isNotEmpty() && valids.isEmpty()) {
            return RunnerState.InitFailed(InitFailureReason.NoValidQuestions)
        }

        // Priority 2: no eligible questions for selected difficulty
        val eligible = valids.filter { it.content.difficulty == mode }
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
        val firstDuration = computeTimer(playOrder.first().content, mode, timerCoefficients)
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
