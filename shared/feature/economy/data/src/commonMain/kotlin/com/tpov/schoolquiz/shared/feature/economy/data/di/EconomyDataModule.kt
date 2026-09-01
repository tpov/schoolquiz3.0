package com.tpov.schoolquiz.shared.feature.economy.data.di

import com.tpov.schoolquiz.shared.feature.economy.data.EconomyConstantsRepositoryImpl
import com.tpov.schoolquiz.shared.feature.economy.data.EconomyConstantsSync
import com.tpov.schoolquiz.shared.feature.economy.data.EconomyLocalDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.EconomyRepositoryImpl
import com.tpov.schoolquiz.shared.feature.economy.data.GiftBoxRepositoryImpl
import com.tpov.schoolquiz.shared.feature.economy.data.RoomEconomyLocalDataSource
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyConstantsRepository
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
        // Таблица настроек одна на всех и от аккаунта не зависит: цены не меняются от того,
        // кто вошёл. Поэтому не auth-scoped и переживает смену аккаунта целиком.
        single<EconomyConstantsRepository> {
            EconomyConstantsRepositoryImpl(remote = get(), store = get())
        }
        single { EconomyConstantsSync(get()) }
        single<GiftBoxRepository> {
            GiftBoxRepositoryImpl(
                remote = get(),
                profileRepository = get(),
            )
        }
    }
