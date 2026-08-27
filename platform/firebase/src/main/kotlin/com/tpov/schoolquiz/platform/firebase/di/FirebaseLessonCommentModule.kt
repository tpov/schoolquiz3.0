package com.tpov.schoolquiz.platform.firebase.di

import com.tpov.schoolquiz.platform.firebase.discussion.FirebaseLessonCommentRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonCommentRepository
import org.koin.dsl.module

val firebaseLessonCommentModule =
    module {
        single<LessonCommentRepository> { FirebaseLessonCommentRepository(get()) }
    }
