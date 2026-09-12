package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.ArenaRunResultServices
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.ArenaRunServices
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultArenaRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases
import com.tpov.schoolquiz.shared.core.analytics.AnalyticsTracker
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonCommentRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.GetResultAdviceUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

val lessonRunnerPresentationModule =
    module {
        single<ArenaRunnerComponentFactory> {
            val services =
                ArenaRunServices(
                    runs = get(),
                    entries = get(),
                    sessions = get(),
                    sessionEvents = get(),
                    wallet = get(),
                    result = ArenaRunResultServices(settlements = get(), ratings = get(), sync = get()),
                )
            val clock = get<kotlinx.datetime.Clock>()
            ArenaRunnerComponentFactory { context, request ->
                DefaultArenaRunnerRootComponent(context, request, services, clock)
            }
        }
        factory { parameters ->
            DefaultLessonRunnerRootComponent(
                componentContext = parameters.get<ComponentContext>(),
                lessonId = parameters.get<LessonId>(),
                mode = parameters.get<Difficulty>(),
                sessionMode = parameters.get<SessionMode>(),
                useCases =
                    LessonRunnerUseCases(
                        startAttempt = get<StartLessonAttemptUseCase>()::invoke,
                        completeAttempt = get<CompleteAttemptUseCase>()::invoke,
                        abortAttempt = get<AbortAttemptUseCase>()::invoke,
                        submitRating = get<SubmitLessonRatingUseCase>()::invoke,
                    ),
                lessonRepository = get(),
                attemptRepository = get(),
                attemptSyncMarks = get(),
                attemptSettlements = get(),
                syncScheduler = get(),
                profileRepository = get<ProfileRepository>(),
                commentRepository = get<LessonCommentRepository>(),
                getResultAdvice = GetResultAdviceUseCase(lessonRepository = get()),
                clock = get(),
                analytics = get<AnalyticsTracker>(),
            ) as LessonRunnerRootComponent
        }

        single<LessonRunnerComponentFactory> {
            LessonRunnerComponentFactory { ctx, lessonId, mode, sessionMode ->
                get { parametersOf(ctx, lessonId, mode, sessionMode) }
            }
        }
    }
