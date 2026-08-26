package com.tpov.schoolquiz.android.feature.quest.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.quest.presentation.DefaultHomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.DefaultMyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import org.koin.dsl.module

val questPresentationModule =
    module {
        factory<MyQuestsComponent> {
                (ctx: ComponentContext, nav: Navigator, onQuestDrillDown: (QuestDisplayItem) -> Unit) ->
            DefaultMyQuestsComponent(
                componentContext = ctx,
                authRepo = get(),
                observeMyQuests = get(),
                observeDraftSummaries = get(),
                observeCatalogs = get(),
                navigator = nav,
                onQuestDrillDown = onQuestDrillDown,
            )
        }
        factory<HomeQuestsComponent> {
                (
                    ctx: ComponentContext, onCatalogDrillDown: (
                        CatalogId,
                        String,
                    ) -> Unit, onResumeLesson: (LessonId) -> Unit,
                ),
            ->
            DefaultHomeQuestsComponent(
                componentContext = ctx,
                observeCatalogs = get(),
                observeProfile = get(),
                openGiftBoxUseCase = get(),
                onCatalogDrillDown = onCatalogDrillDown,
                onResumeLesson = onResumeLesson,
                attemptRepository = get(),
                lessonRepository = get(),
                themeRepository = get(),
                sectionRepository = get(),
                questRepository = get(),
                authRepository = get(),
            )
        }
    }
