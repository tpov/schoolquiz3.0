package com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.feature.app_shell.presentation.screen.EventsScreenComponent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.EventsConfig

interface EventsTabComponent {
    val childStack: Value<ChildStack<EventsConfig, EventsScreenComponent>>
}

class DefaultEventsTabComponent(
    componentContext: ComponentContext,
    navigation: StackNavigation<EventsConfig>,
) : EventsTabComponent, ComponentContext by componentContext {
    override val childStack: Value<ChildStack<EventsConfig, EventsScreenComponent>> =
        childStack(
            source = navigation,
            serializer = null,
            initialConfiguration = EventsConfig.EmptyRoot,
            handleBackButton = false,
            key = "EventsStack",
            childFactory = { config, _ -> EventsScreenComponent.Placeholder(config) },
        )
}
