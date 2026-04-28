package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.di

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
import org.koin.dsl.module

/**
 * Koin domain module for lesson-runner.
 *
 * Clock: data module registers single<Clock> { Clock.System }.
 * Providers: data module registers single<AttemptIdProvider>, single<RandomSeedProvider>, single<RatingIdProvider>.
 * LessonRunnerDomainKoinAdapter (data/androidMain) bridges providers → use case function types.
 * This module is NOT registered in production — use lessonRunnerDomainKoinAdapter instead (ADR-LR-09 C1).
 */
val lessonRunnerDomainModule = module {
    factory {
        StartLessonAttemptUseCase(
            questionRepository = get(),
            lessonRepository = get(),
            parser = get(),
            authRepository = get(),
            clock = get(),
            randomSeedProvider = get(),
        )
    }
    factory {
        CompleteAttemptUseCase(
            attemptRepository = get(),
            ratingRepository = get(),
            clock = get(),
            attemptIdProvider = get(),
        )
    }
    factory {
        AbortAttemptUseCase(
            attemptRepository = get(),
            clock = get(),
            attemptIdProvider = get(),
        )
    }
    factory {
        SubmitLessonRatingUseCase(
            ratingRepository = get(),
            lessonRepository = get(),
            clock = get(),
            ratingIdProvider = get(),
        )
    }
}
