package com.tpov.schoolquiz.platform.firebase.di

import com.tpov.schoolquiz.platform.firebase.question.FirebaseQuestionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.question.data.QuestionRemoteDataSource
import org.koin.dsl.module

val firebaseQuestionModule =
    module {
        single<QuestionRemoteDataSource> { FirebaseQuestionRemoteDataSource(get()) }
    }
