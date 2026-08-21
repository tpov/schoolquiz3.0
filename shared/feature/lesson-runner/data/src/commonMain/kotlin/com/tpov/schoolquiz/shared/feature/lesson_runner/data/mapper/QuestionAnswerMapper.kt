package com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper

import com.tpov.schoolquiz.shared.core.persistence.QuestionAnswerEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestionRepetitionEntity
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AnsweredQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RepetitionState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
import kotlinx.serialization.json.Json

/** Shared instance: answers are written on every completed lesson. */
private val answerJson = Json { encodeDefaults = true }

fun AnsweredQuestion.toEntity(attempt: Attempt): QuestionAnswerEntity =
    QuestionAnswerEntity(
        attemptId = attempt.id.value,
        questionId = questionId.value,
        userId = attempt.userId,
        lessonId = attempt.lessonId.value,
        lessonVersion = attempt.lessonVersion,
        isHard = if (attempt.mode == Difficulty.HARD) 1 else 0,
        codeAnswerIndex = codeAnswerIndex,
        score = score.raw,
        answerPayload = answerJson.encodeToString(UserAnswer.serializer(), answer),
        answeredAtMs = answeredAtMs,
        durationMs = durationMs,
        wasTimeout = if (wasTimeout) 1 else 0,
    )

fun RepetitionState.toEntity(
    userId: String,
    questionId: String,
    lessonId: String,
): QuestionRepetitionEntity =
    QuestionRepetitionEntity(
        userId = userId,
        questionId = questionId,
        lessonId = lessonId,
        intervalDays = intervalDays,
        easeFactorMilli = easeFactorMilli,
        repetitions = repetitions,
        lastAnsweredAtMs = lastAnsweredAtMs,
        nextReviewAtMs = nextReviewAtMs,
    )

fun QuestionRepetitionEntity.toDomain(): RepetitionState =
    RepetitionState(
        intervalDays = intervalDays,
        easeFactorMilli = easeFactorMilli,
        repetitions = repetitions,
        lastAnsweredAtMs = lastAnsweredAtMs,
        nextReviewAtMs = nextReviewAtMs,
    )
