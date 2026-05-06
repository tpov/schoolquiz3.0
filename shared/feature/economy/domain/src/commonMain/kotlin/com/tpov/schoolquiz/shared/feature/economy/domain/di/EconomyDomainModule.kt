package com.tpov.schoolquiz.shared.feature.economy.domain.di

import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.GetReferralProgramUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.GetShopCatalogUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.ObserveEconomyBalanceUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.OpenGiftBoxUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.PurchaseShopItemUseCase
import org.koin.dsl.module

val economyDomainModule = module {
    factory { ObserveEconomyBalanceUseCase(get()) }
    factory { GetShopCatalogUseCase() }
    factory { PurchaseShopItemUseCase(get()) }
    factory { GetReferralProgramUseCase(get()) }
    factory { OpenGiftBoxUseCase(get()) }
}
