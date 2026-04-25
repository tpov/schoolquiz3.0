package com.tpov.schoolquiz.platform.firebase.di

import com.tpov.schoolquiz.platform.firebase.lesson.FirebaseLessonRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson.data.LessonRemoteDataSource
import org.koin.dsl.module

val firebaseLessonModule = module {
    single<LessonRemoteDataSource> { FirebaseLessonRemoteDataSource(get()) }
}
