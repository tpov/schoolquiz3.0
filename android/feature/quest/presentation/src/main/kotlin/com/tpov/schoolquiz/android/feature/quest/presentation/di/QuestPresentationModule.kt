package com.tpov.schoolquiz.android.feature.quest.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.quest.presentation.DefaultHomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.DefaultMyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator
import org.koin.dsl.module

val questPresentationModule = module {
    factory<MyQuestsComponent> { (ctx: ComponentContext, nav: Navigator, onQuestDrillDown: (QuestDisplayItem) -> Unit) ->
        DefaultMyQuestsComponent(
            componentContext = ctx,
            authRepo = get(),
            observeMyQuests = get(),
            observeCatalogs = get(),
            navigator = nav,
            onQuestDrillDown = onQuestDrillDown,
        )
    }
    factory<HomeQuestsComponent> { (ctx: ComponentContext, onCatalogDrillDown: (CatalogId, String) -> Unit) ->
        DefaultHomeQuestsComponent(
            componentContext = ctx,
            observeCatalogs = get(),
            onCatalogDrillDown = onCatalogDrillDown,
        )
    }
}
