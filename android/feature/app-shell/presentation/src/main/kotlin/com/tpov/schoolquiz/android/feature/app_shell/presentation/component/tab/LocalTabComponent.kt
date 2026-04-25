package com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.feature.app_shell.presentation.screen.LocalScreenComponent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig

interface LocalTabComponent {
    val childStack: Value<ChildStack<LocalConfig, LocalScreenComponent>>
}

class DefaultLocalTabComponent(
    componentContext: ComponentContext,
    navigation: StackNavigation<LocalConfig>,
) : LocalTabComponent, ComponentContext by componentContext {
    override val childStack: Value<ChildStack<LocalConfig, LocalScreenComponent>> =
        childStack(
            source = navigation,
            serializer = null,
            initialConfiguration = LocalConfig.HomeQuestsRoot,
            handleBackButton = false,
            key = "LocalStack",
            childFactory = { config, _ -> LocalScreenComponent.Placeholder(config) },
        )
}
