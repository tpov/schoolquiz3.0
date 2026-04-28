package com.tpov.schoolquiz.shared.feature.lesson_runner.data.di

import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultAttemptIdProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRandomSeedProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRatingIdProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonAttemptRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonRatingRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
import kotlinx.datetime.Clock
import org.koin.dsl.module

val lessonRunnerDataModule = module {
    single<AttemptIdProvider> { DefaultAttemptIdProvider() }
    single<RandomSeedProvider> { DefaultRandomSeedProvider() }
    single<RatingIdProvider> { DefaultRatingIdProvider() }
    single<Clock> { Clock.System }
    single<LessonAttemptRepository> { LessonAttemptRepositoryImpl(attemptDao = get()) }
    single<LessonRatingRepository> { LessonRatingRepositoryImpl(ratingLocalDao = get()) }
}
