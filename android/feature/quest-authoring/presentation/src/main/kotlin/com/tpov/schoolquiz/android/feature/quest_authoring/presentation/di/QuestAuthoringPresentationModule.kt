package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.DefaultQuestCreateComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.DefaultReviewQueueComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.QuestCreateComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.ReviewQueueComponent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator
import org.koin.dsl.module

val questAuthoringPresentationModule =
    module {
        factory<QuestCreateComponent> { (ctx: ComponentContext, navigator: Navigator) ->
            DefaultQuestCreateComponent(
                componentContext = ctx,
                authRepository = get(),
                observeCatalogs = get(),
                createQuestDraft = get(),
                getActiveQuestDraft = get(),
                saveDraftQuestion = get(),
                submitQuestDraftToArena = get(),
                questionContentParser = get(),
                questRepository = get(),
                sectionRepository = get(),
                themeRepository = get(),
                lessonRepository = get(),
                navigator = navigator,
            )
        }

        factory<ReviewQueueComponent> { (ctx: ComponentContext) ->
            DefaultReviewQueueComponent(
                componentContext = ctx,
                authRepository = get(),
                observeReviewAssignments = get(),
                submitReviewAction = get(),
                questionContentParser = get(),
            )
        }
    }
