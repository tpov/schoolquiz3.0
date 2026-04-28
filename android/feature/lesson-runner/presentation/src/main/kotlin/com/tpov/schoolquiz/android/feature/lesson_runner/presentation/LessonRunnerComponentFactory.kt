package com.tpov.schoolquiz.android.feature.lesson_runner.presentation

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId

fun interface LessonRunnerComponentFactory {
    fun create(
        componentContext: ComponentContext,
        lessonId: LessonId,
        mode: Difficulty,
    ): LessonRunnerRootComponent
}
