package com.tpov.schoolquiz.android.feature.app_shell.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.PlaceholderQuestCreateComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.PlaceholderReviewQueueComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.QuestCreateComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.ReviewQueueComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/**
 * Koin module for presentation layer.
 * ADR-0009 Rule 1: one module val.
 * ADR-COMP-07: DefaultRootComponent as factory (Activity-scoped ComponentContext).
 */
val appShellPresentationModule =
    module {
        factory { (ctx: ComponentContext) ->
            val koin = getKoin()
            DefaultRootComponent(
                componentContext = ctx,
                initUseCase = get(),
                navigateUseCase = get(),
                observeUseCase = get(),
                retapUseCase = get(),
                userStatsRepository = get(),
                syncScheduler = get(),
                myQuestsFactory = { compCtx, nav, onQuestDrillDown ->
                    koin.get(MyQuestsComponent::class, parameters = { parametersOf(compCtx, nav, onQuestDrillDown) })
                },
                homeQuestsFactory = { compCtx, onCatalogDrillDown ->
                    koin.get(HomeQuestsComponent::class, parameters = { parametersOf(compCtx, onCatalogDrillDown) })
                },
                questCreateFactory = { compCtx, nav ->
                    runCatching<QuestCreateComponent> {
                        koin.get(QuestCreateComponent::class, parameters = { parametersOf(compCtx, nav) })
                    }.getOrElse {
                        PlaceholderQuestCreateComponent(nav)
                    }
                },
                reviewQueueFactory = { compCtx ->
                    runCatching<ReviewQueueComponent> {
                        koin.get(ReviewQueueComponent::class, parameters = { parametersOf(compCtx) })
                    }.getOrElse {
                        PlaceholderReviewQueueComponent()
                    }
                },
                quizzesFactory = { compCtx ->
                    koin.get(QuizzesComponent::class, parameters = { parametersOf(compCtx) })
                },
            )
        }

        // Navigator exposed via rootComponent.navigator — NOT a separate Koin binding.
        // Reason: get<RootComponent>() without parametersOf(ctx) throws MissingPropertyException.
        // See 06-api-contract.md:41.

        factory { InitializeAppShellUseCase(get()) }
        factory { NavigateUseCase() }
        factory { OnTabRetapUseCase() }
        factory { ObserveAppShellStateUseCase(get()) }
    }
