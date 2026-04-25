package com.tpov.schoolquiz.shared.feature.lesson.domain.di

import com.tpov.schoolquiz.shared.feature.lesson.domain.use_case.SyncLessonsUseCase
import org.koin.dsl.module

val lessonDomainModule = module {
    factory { SyncLessonsUseCase(get()) }
}
