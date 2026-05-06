package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
import com.tpov.schoolquiz.shared.core.sync.LessonContentSyncOrchestrator
import org.koin.dsl.module

val quizzesPresentationModule =
    module {
        factory<QuizzesComponent> { (ctx: ComponentContext) ->
            val contentSync = runCatching { get<LessonContentSyncOrchestrator>() }.getOrNull()
            DefaultQuizzesComponent(
                componentContext = ctx,
                questRepository = get(),
                sectionRepository = get(),
                themeRepository = get(),
                lessonRepository = get(),
                lessonAttemptRepository = get(),
                authRepository = get(),
                questionRepository = get(),
                catalogRepository = get(),
                setPublicQuestShelf = get(),
                lessonRunnerFactory = get<LessonRunnerComponentFactory>(),
                questContentSync = contentSync?.let { it::syncQuestContent } ?: { Result.success(Unit) },
                lessonContentSync = contentSync?.let { it::syncLessonContent } ?: { Result.success(Unit) },
            )
        }
    }
