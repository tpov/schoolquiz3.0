package com.tpov.schoolquiz.android.feature.app_shell.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
import com.tpov.schoolquiz.android.feature.economy.presentation.component.PlaceholderShopComponent
import com.tpov.schoolquiz.android.feature.economy.presentation.component.ShopComponent
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.component.PlaceholderProfileComponent
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.component.ProfileComponent
import com.tpov.schoolquiz.android.feature.internet.social.presentation.component.PlaceholderPollsComponent
import com.tpov.schoolquiz.android.feature.internet.social.presentation.component.PollsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.AuthorLifecycleComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.CuratorComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.InboxComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.PlaceholderAuthorLifecycleComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.PlaceholderCuratorComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.PlaceholderInboxComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.PlaceholderQuestCreateComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.PlaceholderReviewQueueComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.QuestCreateComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.ReviewQueueComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.PlaceholderTournamentAdmissionComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.PlaceholderTournamentBrowserComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.PlaceholderTournamentOrganizerComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.PlaceholderTournamentQualificationComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentAdmissionComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentBrowserComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentOrganizerComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentQualificationComponent
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
                fetchTournamentOverview = get(),
                userStatsRepository = get(),
                syncScheduler = get(),
                syncStatusRepository = get(),
                myQuestsFactory = { compCtx, nav, onQuestDrillDown ->
                    koin.get(MyQuestsComponent::class, parameters = { parametersOf(compCtx, nav, onQuestDrillDown) })
                },
                homeQuestsFactory = { compCtx, onCatalogDrillDown, onResumeLesson ->
                    koin.get(
                        HomeQuestsComponent::class,
                        parameters = { parametersOf(compCtx, onCatalogDrillDown, onResumeLesson) },
                    )
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
                curatorFactory = { compCtx ->
                    runCatching<CuratorComponent> {
                        koin.get(CuratorComponent::class, parameters = { parametersOf(compCtx) })
                    }.getOrElse {
                        PlaceholderCuratorComponent()
                    }
                },
                authorLifecycleFactory = { compCtx ->
                    runCatching<AuthorLifecycleComponent> {
                        koin.get(AuthorLifecycleComponent::class, parameters = { parametersOf(compCtx) })
                    }.getOrElse {
                        PlaceholderAuthorLifecycleComponent()
                    }
                },
                inboxFactory = { compCtx ->
                    runCatching<InboxComponent> {
                        koin.get(InboxComponent::class, parameters = { parametersOf(compCtx) })
                    }.getOrElse {
                        PlaceholderInboxComponent()
                    }
                },
                pollsFactory = { compCtx ->
                    runCatching<PollsComponent> {
                        koin.get(PollsComponent::class, parameters = { parametersOf(compCtx) })
                    }.getOrElse {
                        PlaceholderPollsComponent()
                    }
                },
                profileFactory = { compCtx ->
                    runCatching<ProfileComponent> {
                        koin.get(ProfileComponent::class, parameters = { parametersOf(compCtx) })
                    }.getOrElse {
                        PlaceholderProfileComponent()
                    }
                },
                shopFactory = { compCtx ->
                    runCatching<ShopComponent> {
                        koin.get(ShopComponent::class, parameters = { parametersOf(compCtx) })
                    }.getOrElse {
                        PlaceholderShopComponent()
                    }
                },
                tournamentOrganizerFactory = { compCtx ->
                    runCatching<TournamentOrganizerComponent> {
                        koin.get(TournamentOrganizerComponent::class, parameters = { parametersOf(compCtx) })
                    }.getOrElse {
                        PlaceholderTournamentOrganizerComponent()
                    }
                },
                tournamentBrowserFactory = { compCtx ->
                    runCatching<TournamentBrowserComponent> {
                        koin.get(TournamentBrowserComponent::class, parameters = { parametersOf(compCtx) })
                    }.getOrElse {
                        PlaceholderTournamentBrowserComponent()
                    }
                },
                tournamentAdmissionFactory = { compCtx ->
                    runCatching<TournamentAdmissionComponent> {
                        koin.get(TournamentAdmissionComponent::class, parameters = { parametersOf(compCtx) })
                    }.getOrElse {
                        PlaceholderTournamentAdmissionComponent()
                    }
                },
                tournamentQualificationFactory = { compCtx ->
                    runCatching<TournamentQualificationComponent> {
                        koin.get(TournamentQualificationComponent::class, parameters = { parametersOf(compCtx) })
                    }.getOrElse {
                        PlaceholderTournamentQualificationComponent()
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
