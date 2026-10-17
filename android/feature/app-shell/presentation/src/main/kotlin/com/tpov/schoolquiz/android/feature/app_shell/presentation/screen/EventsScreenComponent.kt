package com.tpov.schoolquiz.android.feature.app_shell.presentation.screen

import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentAdmissionComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentAgreementComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentBracketComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentBrowserComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentMatchPlayComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentOrganizerComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentQualificationComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentResultsComponent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.EventsConfig

/**
 * Дети вкладки событий (истории 5.10 и 5.11): реальные маршруты соревнования на своих
 * конфигурациях и [Placeholder] для всех остальных — конфигурация без живого слоя честно
 * заглушкой и зовётся.
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

    /**
     * Чемпионат (WorldChampionshipRoot, история 5.11): сетка, согласование слота, игра матча
     * и итоги — четыре живых слоя на одной конфигурации, как просмотр с допуском в 5.10.
     */
    data class Championship(
        val bracket: TournamentBracketComponent,
        val agreement: TournamentAgreementComponent,
        val matchPlay: TournamentMatchPlayComponent,
        val results: TournamentResultsComponent,
    ) : EventsScreenComponent
}
