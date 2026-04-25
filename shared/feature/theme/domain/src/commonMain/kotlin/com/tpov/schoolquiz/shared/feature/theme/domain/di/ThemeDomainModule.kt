package com.tpov.schoolquiz.shared.feature.theme.domain.di

import com.tpov.schoolquiz.shared.feature.theme.domain.use_case.SyncThemesUseCase
import org.koin.dsl.module

val themeDomainModule = module {
    factory { SyncThemesUseCase(get()) }
}
