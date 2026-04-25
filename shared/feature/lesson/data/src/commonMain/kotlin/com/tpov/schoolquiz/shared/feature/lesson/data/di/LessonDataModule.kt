package com.tpov.schoolquiz.shared.feature.lesson.data.di

import com.tpov.schoolquiz.shared.feature.lesson.data.LessonLocalDataSource
import com.tpov.schoolquiz.shared.feature.lesson.data.LessonLocalDataSourceImpl
import com.tpov.schoolquiz.shared.feature.lesson.data.LessonRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import org.koin.dsl.module

val lessonDataModule = module {
    single<LessonLocalDataSource> { LessonLocalDataSourceImpl(get()) }
    single<LessonRepository> { LessonRepositoryImpl(local = get(), remote = get()) }
}
