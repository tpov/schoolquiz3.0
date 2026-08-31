package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.PercentScore

object FakeAttemptFixtures {

    fun fixtureAttempt(
        id: String = "fixture-id",
        userId: String = "user1",
        lessonId: String = "lesson1",
        lessonVersion: Long = 5L,
        mode: Difficulty = Difficulty.EASY,
        codeAnswer: String = "9",
        percentScore: Int = 100,
        completedAt: Long = 1_000_000L,
    ) = Attempt(
        id = AttemptId(id),
        userId = userId,
        lessonId = LessonId(lessonId),
        lessonVersion = lessonVersion,
        mode = mode,
        completedAt = completedAt,
        codeAnswer = CodeAnswer(codeAnswer),
        percentScore = PercentScore(percentScore),
    )
}
