package com.tpov.schoolquiz.shared.feature.economy.data.di

import com.tpov.schoolquiz.shared.feature.economy.data.EconomyLocalDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.EconomyRepositoryImpl
import com.tpov.schoolquiz.shared.feature.economy.data.GiftBoxRepositoryImpl
import com.tpov.schoolquiz.shared.feature.economy.data.RoomEconomyLocalDataSource
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftBoxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.module.Module
import org.koin.dsl.module

fun economyDataModule(currentUidFlow: () -> Flow<String?> = { flowOf(null) }): Module =
    module {
        single<EconomyLocalDataSource> { RoomEconomyLocalDataSource(get()) }
        single<EconomyRepository> {
            EconomyRepositoryImpl(
                local = get(),
                remote = get(),
                currentUidFlow = currentUidFlow,
            )
        }
        single<GiftBoxRepository> {
            GiftBoxRepositoryImpl(
                remote = get(),
                profileRepository = get(),
            )
        }
    }
