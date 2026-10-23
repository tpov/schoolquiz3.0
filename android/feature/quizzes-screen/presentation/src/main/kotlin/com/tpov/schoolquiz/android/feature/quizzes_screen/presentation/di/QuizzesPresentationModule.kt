package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryComponentFactory
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryDependencies
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaLessonCatalogComponentFactory
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultArenaEntryComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultArenaLessonCatalogComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultLessonEarningsComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.LessonEarningsComponentFactory
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.PrivateQuestItem
import com.tpov.schoolquiz.shared.core.sync.LessonContentSyncOrchestrator
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.PrivateQuizSettings
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveQuestDraftSummariesUseCase
import com.tpov.schoolquiz.shared.feature.question.domain.use_case.ObserveLessonUnlockPricesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
                privateQuizRunnerFactory = get(),
                lessonEarningsFactory =
                    LessonEarningsComponentFactory { ctx, lessonId, title ->
                        DefaultLessonEarningsComponent(
                            componentContext = ctx,
                            lessonId = lessonId,
                            title = title,
                            observeOwnHistory = get(),
                        )
                    },
                observePrivateQuests = { uid ->
                    // Приватная категория (AD-29, история 8.4): сохранённые приватные квизы
                    // владельца; статусы, у которых уже есть замороженный набор на сервере.
                    get<ObserveQuestDraftSummariesUseCase>()(uid)
                        .map { summaries ->
                            summaries
                                .filter { it.catalogId.value == PrivateQuizSettings.PRIVATE_QUIZ_CATALOG_ID }
                                .filter { it.status == QuestDraftStatus.SYNCED || it.status == QuestDraftStatus.REVIEW_QUEUED }
                                .sortedByDescending { it.updatedAtMs }
                                .map { summary ->
                                    PrivateQuestItem(
                                        questId = summary.id.value,
                                        title = summary.title,
                                        questionCount = summary.questionCount,
                                    )
                                }
                        }
                },
                arenaCatalogFactory = get<ArenaLessonCatalogComponentFactory>(),
                // Keep catalogue construction independent of runner resolution; no fallback on explicit launch.
                arenaRunnerFactory =
                    ArenaRunnerComponentFactory { child, request ->
                        get<ArenaRunnerComponentFactory>().create(child, request)
                    },
                questContentSync = contentSync?.let { it::syncQuestContent } ?: { missingSync() },
                lessonContentSync = contentSync?.let { it::syncLessonContent } ?: { missingSync() },
                observeLessonUnlockPrices = get<ObserveLessonUnlockPricesUseCase>()::invoke,
            )
        }
    }
