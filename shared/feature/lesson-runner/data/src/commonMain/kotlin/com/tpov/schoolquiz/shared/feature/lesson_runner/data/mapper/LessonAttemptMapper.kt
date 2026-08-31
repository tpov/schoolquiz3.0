package com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper

import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptEntity
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.PercentScore

fun Attempt.toEntity(): LessonAttemptEntity = LessonAttemptEntity(
    attemptId = id.value,
    userId = userId,
    lessonId = lessonId.value,
    lessonVersion = lessonVersion,
    isHard = if (mode == Difficulty.HARD) 1 else 0,
    codeAnswer = codeAnswer.raw,
    percentScore = percentScore.raw,
    completedAt = completedAt,
)

fun LessonAttemptEntity.toDomain(): Attempt = Attempt(
    id = AttemptId(attemptId),
    userId = userId,
    lessonId = LessonId(lessonId),
    lessonVersion = lessonVersion,
    mode = if (isHard != 0) Difficulty.HARD else Difficulty.EASY,
    completedAt = completedAt,
    codeAnswer = CodeAnswer(codeAnswer),
    percentScore = PercentScore(percentScore),
)
