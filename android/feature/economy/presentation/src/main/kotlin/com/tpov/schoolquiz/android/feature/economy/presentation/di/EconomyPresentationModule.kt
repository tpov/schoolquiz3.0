package com.tpov.schoolquiz.android.feature.economy.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.economy.presentation.component.DefaultShopComponent
import com.tpov.schoolquiz.android.feature.economy.presentation.component.ShopComponent
import org.koin.dsl.module

val economyPresentationModule =
    module {
        factory<ShopComponent> { (ctx: ComponentContext) ->
            DefaultShopComponent(
                componentContext = ctx,
                observeBalance = get(),
                getCatalog = get(),
                purchaseItem = get(),
                getReferralProgram = get(),
                nicknames = get(),
                logos = get(),
            )
        }
    }
