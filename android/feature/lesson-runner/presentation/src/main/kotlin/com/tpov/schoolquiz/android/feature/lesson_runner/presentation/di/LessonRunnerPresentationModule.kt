package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.GetResultAdviceUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

val lessonRunnerPresentationModule =
    module {
        factory { (ctx: ComponentContext, lessonId: LessonId, mode: Difficulty, sessionMode: SessionMode) ->
            DefaultLessonRunnerRootComponent(
                componentContext = ctx,
                lessonId = lessonId,
                mode = mode,
                sessionMode = sessionMode,
                useCases =
                    LessonRunnerUseCases(
                        startAttempt = get<StartLessonAttemptUseCase>()::invoke,
                        completeAttempt = get<CompleteAttemptUseCase>()::invoke,
                        abortAttempt = get<AbortAttemptUseCase>()::invoke,
                        submitRating = get<SubmitLessonRatingUseCase>()::invoke,
                    ),
                lessonRepository = get(),
                attemptRepository = get(),
                profileRepository = get<ProfileRepository>(),
                getResultAdvice = GetResultAdviceUseCase(lessonRepository = get()),
                clock = get(),
            ) as LessonRunnerRootComponent
        }

        single<LessonRunnerComponentFactory> {
            LessonRunnerComponentFactory { ctx, lessonId, mode, sessionMode ->
                get { parametersOf(ctx, lessonId, mode, sessionMode) }
            }
        }
    }
