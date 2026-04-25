package com.tpov.schoolquiz.shared.feature.app_shell.data.di

import com.tpov.schoolquiz.shared.feature.app_shell.data.AuthRepositoryImpl
import com.tpov.schoolquiz.shared.feature.app_shell.data.UserStatsRepositoryImpl
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.module.Module
import org.koin.dsl.module

// SECURITY: default `= { flowOf(null) }` is test-only — silently enables permanent guest mode.
// Production callers MUST pass sharedAuthUidFlow. See security review phase-03.
@Suppress("unused") // default param used by KoinModuleWiringTest; production always passes explicit arg
fun appShellDataModule(currentUidFlow: () -> Flow<String?> = { flowOf(null) }): Module =
    module {
        single<UserStatsRepository> {
            UserStatsRepositoryImpl(
                remoteDataSource = get(),
                userStatsDao = get(),
                currentUidFlow = currentUidFlow,
            )
        }
        single<AuthRepository> {
            AuthRepositoryImpl(currentUidFlow = currentUidFlow)
        }
    }
