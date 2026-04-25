package com.tpov.schoolquiz.shared.feature.question.data.di

import com.tpov.schoolquiz.shared.feature.question.data.QuestionLocalDataSource
import com.tpov.schoolquiz.shared.feature.question.data.QuestionLocalDataSourceImpl
import com.tpov.schoolquiz.shared.feature.question.data.QuestionRepositoryImpl
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import org.koin.dsl.module

val questionDataModule = module {
    single<QuestionLocalDataSource> { QuestionLocalDataSourceImpl(get()) }
    single<QuestionRepository> { QuestionRepositoryImpl(local = get(), remote = get()) }
}
