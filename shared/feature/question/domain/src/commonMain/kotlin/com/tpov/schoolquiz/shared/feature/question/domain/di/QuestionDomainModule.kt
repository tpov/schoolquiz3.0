package com.tpov.schoolquiz.shared.feature.question.domain.di

import com.tpov.schoolquiz.shared.feature.question.domain.use_case.SyncQuestionsUseCase
import org.koin.dsl.module

val questionDomainModule = module {
    factory { SyncQuestionsUseCase(get()) }
}
