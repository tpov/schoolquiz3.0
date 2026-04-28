package com.tpov.schoolquiz.shared.feature.lesson_runner.data.di

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
import org.koin.dsl.module

val lessonRunnerDomainKoinAdapter = module {
    factory<StartLessonAttemptUseCase> {
        StartLessonAttemptUseCase(
            questionRepository = get(),
            lessonRepository = get(),
            parser = get(),
            authRepository = get(),
            clock = get(),
            randomSeedProvider = get<RandomSeedProvider>()::next,
        )
    }
    factory<CompleteAttemptUseCase> {
        CompleteAttemptUseCase(
            attemptRepository = get(),
            ratingRepository = get(),
            clock = get(),
            attemptIdProvider = get<AttemptIdProvider>()::next,
        )
    }
    factory<AbortAttemptUseCase> {
        AbortAttemptUseCase(
            attemptRepository = get(),
            clock = get(),
            attemptIdProvider = get<AttemptIdProvider>()::next,
        )
    }
    factory<SubmitLessonRatingUseCase> {
        SubmitLessonRatingUseCase(
            ratingRepository = get(),
            lessonRepository = get(),
            clock = get(),
            ratingIdProvider = get<RatingIdProvider>()::provide,
        )
    }
}
