package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState

class FakeStartLessonAttemptUseCase {
    var result: RunnerState = RunnerState.Loading
    var callCount = 0
    var lastSessionMode: SessionMode? = null
        private set

    suspend operator fun invoke(
        lessonId: LessonId,
        mode: Difficulty,
        sessionMode: SessionMode = SessionMode.LEARNING,
    ): RunnerState {
        callCount++
        lastSessionMode = sessionMode
        return result
    }
}
