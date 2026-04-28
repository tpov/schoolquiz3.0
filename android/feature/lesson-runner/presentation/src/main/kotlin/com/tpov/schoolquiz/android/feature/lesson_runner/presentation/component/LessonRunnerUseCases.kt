package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState

class LessonRunnerUseCases(
    val startAttempt: suspend (LessonId, Difficulty) -> RunnerState,
    val completeAttempt: suspend (RunnerState.Ready) -> RunnerState,
    val abortAttempt: suspend (RunnerState.Ready) -> RunnerState,
    val submitRating: suspend (String, LessonId, Int) -> Result<Unit>,
)
