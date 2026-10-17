package com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.feature.app_shell.presentation.screen.EventsScreenComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.PlaceholderTournamentAdmissionComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.PlaceholderTournamentBrowserComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.PlaceholderTournamentOrganizerComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.PlaceholderTournamentQualificationComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentAdmissionComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentBrowserComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentOrganizerComponent
import com.tpov.schoolquiz.android.feature.tournament.presentation.component.TournamentQualificationComponent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.EventsConfig

interface EventsTabComponent {
    val childStack: Value<ChildStack<EventsConfig, EventsScreenComponent>>
}

/**
 * Стек событий: реальные фабрики маршрутов соревнования на своих конфигурациях (история 5.10),
 * заглушка — всем остальным. Фабрики приходят снаружи с заглушками по умолчанию (образец
 * quest-authoring): непривязанный слой не собирается и не притворяется живым.
 */
class DefaultEventsTabComponent(
    componentContext: ComponentContext,
    navigation: StackNavigation<EventsConfig>,
    organizerFactory: (
        ComponentContext,
    ) -> TournamentOrganizerComponent = { PlaceholderTournamentOrganizerComponent() },
    browserFactory: (ComponentContext) -> TournamentBrowserComponent = { PlaceholderTournamentBrowserComponent() },
    admissionFactory: (
        ComponentContext,
    ) -> TournamentAdmissionComponent = { PlaceholderTournamentAdmissionComponent() },
    qualificationFactory: (
        ComponentContext,
    ) -> TournamentQualificationComponent = { PlaceholderTournamentQualificationComponent() },
) : EventsTabComponent, ComponentContext by componentContext {
    override val childStack: Value<ChildStack<EventsConfig, EventsScreenComponent>> =
        childStack(
            source = navigation,
            serializer = null,
            initialConfiguration = EventsConfig.QualifierTournamentRoot,
            handleBackButton = false,
            key = "EventsStack",
            childFactory = { config, context ->
                when (config) {
                    // Просмотр и допуск живут рядом на одной конфигурации: допуск открывает
                    // выбор из детали просмотра, старт — маршрут участия на своей конфигурации.
                    EventsConfig.ActiveEventsRoot ->
                        EventsScreenComponent.Browser(
                            component = browserFactory(context),
                            admission = admissionFactory(context),
                        )
                    EventsConfig.QualifierTournamentRoot ->
                        EventsScreenComponent.OrganizerCreate(organizerFactory(context))
                    EventsConfig.QualifierTournamentParticipantsRoot ->
                        EventsScreenComponent.Qualification(qualificationFactory(context))
                    else -> EventsScreenComponent.Placeholder(config)
                }
            },
        )
}
