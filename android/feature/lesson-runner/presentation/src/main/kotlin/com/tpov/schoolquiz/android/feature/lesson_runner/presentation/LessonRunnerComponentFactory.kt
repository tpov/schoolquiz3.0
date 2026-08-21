package com.tpov.schoolquiz.android.feature.lesson_runner.presentation

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode

fun interface LessonRunnerComponentFactory {
    fun create(
        componentContext: ComponentContext,
        lessonId: LessonId,
        mode: Difficulty,
        // No default: a functional interface cannot declare one. Callers pass the mode explicitly,
        // which also keeps it visible at every navigation site.
        sessionMode: SessionMode,
    ): LessonRunnerRootComponent
}
