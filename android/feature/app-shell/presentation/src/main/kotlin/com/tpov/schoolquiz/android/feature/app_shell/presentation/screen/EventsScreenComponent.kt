package com.tpov.schoolquiz.android.feature.app_shell.presentation.screen

import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentAdmissionComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentBrowserComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentOrganizerComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentQualificationComponent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.EventsConfig

/**
 * Дети вкладки событий (история 5.10): реальные маршруты соревнования на своих конфигурациях и
 * [Placeholder] для всех остальных — конфигурация без живого слоя честно заглушкой и зовётся.
 */
sealed interface EventsScreenComponent {
    data class Placeholder(val config: EventsConfig) : EventsScreenComponent

    /** Просмотр и допуск (ActiveEventsRoot): пейджинг + деталь чтения; допуск открывает выбор. */
    data class Browser(
        val component: TournamentBrowserComponent,
        val admission: TournamentAdmissionComponent,
    ) : EventsScreenComponent

    /** Создание организатором (QualifierTournamentRoot). */
    data class OrganizerCreate(val component: TournamentOrganizerComponent) : EventsScreenComponent

    /** Участие в квалификации (QualifierTournamentParticipantsRoot). */
    data class Qualification(val component: TournamentQualificationComponent) : EventsScreenComponent
}
