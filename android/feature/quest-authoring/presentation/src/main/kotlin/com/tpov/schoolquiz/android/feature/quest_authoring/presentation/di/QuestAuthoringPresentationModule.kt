package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.AuthorLifecycleComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.CuratorComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.DecisionsComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.DefaultAuthorLifecycleComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.DefaultCuratorComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.DefaultDecisionsComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.DefaultInboxComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.DefaultQuestCreateComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.InboxComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.QuestCreateComponent
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
                observeQuestDraftConflict = get(),
                resolveQuestDraftConflict = get(),
                questionContentParser = get(),
                questRepository = get(),
                sectionRepository = get(),
                themeRepository = get(),
                lessonRepository = get(),
                navigator = navigator,
                closePrivateQuizLink = get(),
                timestampProvider = get(),
            )
        }

        factory<DecisionsComponent> { (ctx: ComponentContext) ->
            DefaultDecisionsComponent(
                componentContext = ctx,
                fetchDecisions = get(),
                markDecision = get(),
                questionContentParser = get(),
            )
        }

        factory<CuratorComponent> { (ctx: ComponentContext) ->
            DefaultCuratorComponent(
                componentContext = ctx,
                authRepository = get(),
                listCurator = get(),
                listHistory = get(),
                transitionLesson = get(),
                idProvider = get(),
            )
        }

        factory<AuthorLifecycleComponent> { (ctx: ComponentContext) ->
            DefaultAuthorLifecycleComponent(
                componentContext = ctx,
                authRepository = get(),
                listOwned = get(),
                listHistory = get(),
                listRoyalty = get(),
            )
        }

        factory<InboxComponent> { (ctx: ComponentContext) ->
            DefaultInboxComponent(
                componentContext = ctx,
                authRepository = get(),
                listInboxItems = get(),
            )
        }
    }
