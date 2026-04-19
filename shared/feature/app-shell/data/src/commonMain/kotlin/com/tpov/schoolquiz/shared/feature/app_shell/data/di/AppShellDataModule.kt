package com.tpov.schoolquiz.shared.feature.app_shell.data.di

import com.tpov.schoolquiz.shared.feature.app_shell.data.UserStatsRepositoryImpl
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import org.koin.dsl.module

val appShellDataModule =
    module {
        single<UserStatsRepository> { UserStatsRepositoryImpl(get()) }
    }
