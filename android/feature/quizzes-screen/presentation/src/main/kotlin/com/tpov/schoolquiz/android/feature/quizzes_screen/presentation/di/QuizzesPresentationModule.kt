package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryComponentFactory
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryDependencies
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaLessonCatalogComponentFactory
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultArenaEntryComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultArenaLessonCatalogComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
import com.tpov.schoolquiz.shared.core.sync.LessonContentSyncOrchestrator
import com.tpov.schoolquiz.shared.feature.question.domain.use_case.ObserveLessonUnlockPricesUseCase
import org.koin.dsl.module

val quizzesPresentationModule =
    module {
        factory {
            ArenaEntryComponentFactory { ctx ->
                DefaultArenaEntryComponent(ctx, ArenaEntryDependencies(get(), get(), get(), get()))
            }
        }
        factory {
            // Required dependencies, lazily resolved only when the catalogue child is created.
            ArenaLessonCatalogComponentFactory { ctx ->
                DefaultArenaLessonCatalogComponent(
                    ctx,
                    repository = get(),
                    sessions = get(),
                    authRepository = get(),
                    entryFactory = get(),
                    sessionEvents = get(),
                )
            }
        }
        factory<QuizzesComponent> { (ctx: ComponentContext) ->
            // Оркестратор регистрируется в syncModule и в приложении есть всегда; отсутствует он
            // только в тестах проводки. Раньше на этот случай подставлялась заглушка,
            // возвращавшая success, — и квест помечался «скачан», хотя не скачивалось ничего.
            val contentSync = runCatching { get<LessonContentSyncOrchestrator>() }.getOrNull()
            val missingSync: () -> Result<Unit> = {
                Result.failure(IllegalStateException("LessonContentSyncOrchestrator is not registered"))
            }
            DefaultQuizzesComponent(
                componentContext = ctx,
                questRepository = get(),
                sectionRepository = get(),
                themeRepository = get(),
                lessonRepository = get(),
                lessonAttemptRepository = get(),
                authRepository = get(),
                economyRepository = get(),
                queueLessonUnlock = get(),
                questionRepository = get(),
                catalogRepository = get(),
                setPublicQuestShelf = get(),
                retirePublicQuest = get(),
                lessonRunnerFactory = get<LessonRunnerComponentFactory>(),
                arenaCatalogFactory = get<ArenaLessonCatalogComponentFactory>(),
                questContentSync = contentSync?.let { it::syncQuestContent } ?: { missingSync() },
                lessonContentSync = contentSync?.let { it::syncLessonContent } ?: { missingSync() },
                observeLessonUnlockPrices = get<ObserveLessonUnlockPricesUseCase>()::invoke,
            )
        }
    }
