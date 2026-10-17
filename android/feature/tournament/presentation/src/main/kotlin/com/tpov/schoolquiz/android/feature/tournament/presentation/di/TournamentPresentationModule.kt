package com.tpov.schoolquiz.android.feature.tournament.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.DefaultTournamentAdmissionComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.DefaultTournamentAgreementComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.DefaultTournamentBracketComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.DefaultTournamentBrowserComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.DefaultTournamentMatchPlayComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.DefaultTournamentOrganizerComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.DefaultTournamentQualificationComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.DefaultTournamentResultsComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentAdmissionComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentAgreementComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentBracketComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentBrowserComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentMatchPlayComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentOrganizerComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentQualificationComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentResultsComponent
import org.koin.dsl.module

/**
 * Сборка презентации соревнования (истории 5.10 и 5.11): маршруты над 5.9-слоем. Репозиторий и
 * очередь приезжают готовыми из `tournamentDataModule`/`firebaseModule`; свои часы модуль не
 * держит — окна ведут серверные факты, а не DI.
 */
val tournamentPresentationModule =
    module {
        factory<TournamentOrganizerComponent> { (ctx: ComponentContext) ->
            DefaultTournamentOrganizerComponent(
                context = ctx,
                repository = get(),
                sessions = get(),
            )
        }
        factory<TournamentBrowserComponent> { (ctx: ComponentContext) ->
            DefaultTournamentBrowserComponent(
                context = ctx,
                repository = get(),
                sessions = get(),
            )
        }
        factory<TournamentAdmissionComponent> { (ctx: ComponentContext) ->
            DefaultTournamentAdmissionComponent(
                context = ctx,
                repository = get(),
                sessions = get(),
            )
        }
        factory<TournamentQualificationComponent> { (ctx: ComponentContext) ->
            DefaultTournamentQualificationComponent(
                context = ctx,
                repository = get(),
                outbox = get(),
                sessions = get(),
            )
        }
        factory<TournamentAgreementComponent> { (ctx: ComponentContext) ->
            DefaultTournamentAgreementComponent(
                context = ctx,
                repository = get(),
                sessions = get(),
            )
        }
        factory<TournamentMatchPlayComponent> { (ctx: ComponentContext) ->
            DefaultTournamentMatchPlayComponent(
                context = ctx,
                repository = get(),
                outbox = get(),
                sessions = get(),
            )
        }
        factory<TournamentBracketComponent> { (ctx: ComponentContext) ->
            DefaultTournamentBracketComponent(
                context = ctx,
                repository = get(),
                sessions = get(),
            )
        }
        factory<TournamentResultsComponent> { (ctx: ComponentContext) ->
            DefaultTournamentResultsComponent(
                context = ctx,
                repository = get(),
                sessions = get(),
            )
        }
    }
