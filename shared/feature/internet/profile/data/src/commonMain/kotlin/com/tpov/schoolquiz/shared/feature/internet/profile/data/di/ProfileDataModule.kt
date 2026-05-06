package com.tpov.schoolquiz.shared.feature.internet.profile.data.di

import com.tpov.schoolquiz.shared.feature.internet.profile.data.ProfileLocalDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.data.ProfileRepositoryImpl
import com.tpov.schoolquiz.shared.feature.internet.profile.data.RoomProfileLocalDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.module.Module
import org.koin.dsl.module

@Suppress("unused")
fun profileDataModule(currentUidFlow: () -> Flow<String?> = { flowOf(null) }): Module =
    module {
        single<ProfileLocalDataSource> { RoomProfileLocalDataSource(get()) }
        single<ProfileRepository> {
            ProfileRepositoryImpl(
                local = get(),
                remote = get(),
                currentUidFlow = currentUidFlow,
            )
        }
    }
