package com.tpov.schoolquiz.shared.feature.theme.data.di

import com.tpov.schoolquiz.shared.feature.theme.data.ThemeLocalDataSource
import com.tpov.schoolquiz.shared.feature.theme.data.ThemeLocalDataSourceImpl
import com.tpov.schoolquiz.shared.feature.theme.data.ThemeRepositoryImpl
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import org.koin.dsl.module

val themeDataModule = module {
    single<ThemeLocalDataSource> { ThemeLocalDataSourceImpl(get()) }
    single<ThemeRepository> { ThemeRepositoryImpl(local = get(), remote = get()) }
}
