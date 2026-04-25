package com.tpov.schoolquiz.android.feature.quest.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.quest.presentation.DefaultHomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.DefaultMyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator
import org.koin.dsl.module

/**
 * Koin module for quest presentation layer.
 *
 * Navigator is NOT a Koin singleton (see AppShellPresentationModule comment).
 * It must be passed via parametersOf at the call site (AppShellScreen).
 *
 * Open Question OQ-NAV-1: 06-api-contract.md §13 assumes Navigator bound via get() as singleton.
 * Current workaround: Navigator passed via parametersOf(ctx, nav) from AppShellScreen.
 * Resolution: add Navigator single{} in AppShellPresentationModule when rootComponent is available.
 *
 * Call site (AppShellScreen):
 *   koin.get<MyQuestsComponent> { parametersOf(childCtx, rootComponent.navigator) }
 *   koin.get<HomeQuestsComponent> { parametersOf(childCtx) }
 */
val questPresentationModule = module {
    factory<MyQuestsComponent> { (ctx: ComponentContext, nav: Navigator) ->
        DefaultMyQuestsComponent(
            componentContext = ctx,
            authRepo = get(),
            observeMyQuests = get(),
            observeCatalogs = get(),
            navigator = nav,
        )
    }
    factory<HomeQuestsComponent> { (ctx: ComponentContext) ->
        DefaultHomeQuestsComponent(
            componentContext = ctx,
            observeCatalogs = get(),
        )
    }
}
