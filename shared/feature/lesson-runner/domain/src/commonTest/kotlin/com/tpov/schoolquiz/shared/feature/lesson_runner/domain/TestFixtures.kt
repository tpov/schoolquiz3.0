package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.question.domain.model.Question
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState

// ── Option / Blank / Candidate helpers ───────────────────────────────────────

fun optId(s: String) = OptionId(s)
fun blankId(s: String) = BlankId(s)
fun candId(s: String) = CandidateId(s)

// ── QuestionContent builders ──────────────────────────────────────────────────

fun singleChoiceContent(
    correctId: String = "A",
    optionIds: List<String> = listOf("A", "B", "C", "D"),
    text: String = "Question text",
    difficulty: Difficulty = Difficulty.EASY,
    imageUrl: String? = null,
): QuestionContent.SingleChoice = QuestionContent.SingleChoice(
    id = "q1",
    difficulty = difficulty,
    text = text,
    imageUrl = imageUrl,
    options = optionIds.map { QuestionContent.Option(optId(it), "Option $it") },
    correctOptionId = optId(correctId),
)

fun multipleChoiceContent(
    correctIds: Set<String>,
    allOptionIds: List<String>,
    text: String = "Question text",
    difficulty: Difficulty = Difficulty.EASY,
): QuestionContent.MultipleChoice = QuestionContent.MultipleChoice(
    id = "q1",
    difficulty = difficulty,
    text = text,
    imageUrl = null,
    options = allOptionIds.map { QuestionContent.Option(optId(it), "Option $it") },
    correctOptionIds = correctIds.map { optId(it) }.toSet(),
)

fun orderingContent(
    itemIds: List<String> = listOf("A", "B", "C", "D"),
    text: String = "Question text",
    difficulty: Difficulty = Difficulty.EASY,
): QuestionContent.Ordering = QuestionContent.Ordering(
    id = "q1",
    difficulty = difficulty,
    text = text,
    imageUrl = null,
    items = itemIds.map { QuestionContent.OrderItem(optId(it), "Item $it") },
)

fun fillBlankContent(
    blankCorrectPairs: List<Pair<String, String>>,
    candidateIds: List<String>,
    text: String = "Fill the blank",
    difficulty: Difficulty = Difficulty.EASY,
): QuestionContent.FillBlank = QuestionContent.FillBlank(
    id = "q1",
    difficulty = difficulty,
    text = text,
    imageUrl = null,
    blanks = blankCorrectPairs.map { (blankId, candidateId) ->
        QuestionContent.Blank(blankId(blankId), candId(candidateId))
    },
    candidates = candidateIds.map { QuestionContent.Candidate(candId(it), "Word $it") },
)

// ── Lesson / Question builders ────────────────────────────────────────────────

fun makeLesson(
    id: String = "lesson1",
    version: Long = 5L,
    themeId: String = "theme1",
) = Lesson(
    id = LessonId(id),
    themeId = ThemeId(themeId),
    title = "Test Lesson",
    order = 0,
    version = version,
    contentsVersion = 1L,
    lastModifiedAt = 1000L,
)

fun makeQuestion(
    id: String,
    lessonId: String = "lesson1",
    order: Int = 0,
    payload: String = "payload_$id",
    language: String = "en",
    difficulty: Difficulty = Difficulty.EASY,
) = Question(
    id = QuestionId(id),
    lessonId = LessonId(lessonId),
    text = "Question $id",
    payload = payload,
    language = language,
    order = order,
    version = 1L,
    lastModifiedAt = 1000L,
)

// ── RunnerQuestion builder ────────────────────────────────────────────────────

fun makeRunnerQuestion(
    id: String = "q1",
    order: Int = 0,
    codeAnswerIndex: Int = 0,
    content: QuestionContent = singleChoiceContent(),
) = RunnerQuestion.Valid(
    sourceId = QuestionId(id),
    order = order,
    codeAnswerIndex = codeAnswerIndex,
    content = content,
)

// ── RunnerState.Ready builder ─────────────────────────────────────────────────

fun makeReadyState(
    userId: String = "user1",
    lessonId: String = "lesson1",
    lessonVersion: Long = 5L,
    mode: Difficulty = Difficulty.EASY,
    playOrder: List<RunnerQuestion.Valid> = listOf(makeRunnerQuestion()),
    eligibleSize: Int = playOrder.size,
    indexInPool: Int = 0,
    codeAnswer: CodeAnswer = CodeAnswer("0".repeat(eligibleSize)),
    deadlineMs: Long = 9_000_000L,
    seed: Long = 12345L,
    isPaused: Boolean = false,
    questionStartedAtMs: Long = 0L,
) = RunnerState.Ready(
    userId = userId,
    lessonId = LessonId(lessonId),
    lessonVersion = lessonVersion,
    mode = mode,
    playOrder = playOrder,
    eligibleSize = eligibleSize,
    indexInPool = indexInPool,
    codeAnswer = codeAnswer,
    deadlineMs = deadlineMs,
    seed = seed,
    currentDraftAnswer = null,
    isPaused = isPaused,
    questionStartedAtMs = questionStartedAtMs,
)

// ── Attempt builder ───────────────────────────────────────────────────────────

fun makeAttempt(
    userId: String = "user1",
    lessonId: String = "lesson1",
    lessonVersion: Long = 5L,
    mode: Difficulty = Difficulty.EASY,
    codeAnswer: String = "9",
    percentScore: Int = 100,
    completedAt: Long = 1_000_000L,
) = Attempt(
    id = AttemptId("attempt-$userId-$lessonId"),
    userId = userId,
    lessonId = LessonId(lessonId),
    lessonVersion = lessonVersion,
    mode = mode,
    completedAt = completedAt,
    codeAnswer = CodeAnswer(codeAnswer),
    percentScore = PercentScore(percentScore),
)
