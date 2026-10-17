package com.tpov.schoolquiz.android.feature.tournament.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.DefaultTournamentAdmissionComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.DefaultTournamentBrowserComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.DefaultTournamentOrganizerComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.DefaultTournamentQualificationComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentAdmissionComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentBrowserComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentOrganizerComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentQualificationComponent
import org.koin.dsl.module

/**
 * Сборка презентации соревнования (история 5.10): четыре маршрута над 5.9-слоем. Репозиторий и
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
    }
