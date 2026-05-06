package com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.feature.app_shell.presentation.screen.InternetScreenComponent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.InternetConfig

interface InternetTabComponent {
    val childStack: Value<ChildStack<InternetConfig, InternetScreenComponent>>
}

class DefaultInternetTabComponent(
    componentContext: ComponentContext,
    navigation: StackNavigation<InternetConfig>,
) : InternetTabComponent, ComponentContext by componentContext {
    override val childStack: Value<ChildStack<InternetConfig, InternetScreenComponent>> =
        childStack(
            source = navigation,
            serializer = null,
            initialConfiguration = InternetConfig.ProfileRoot,
            handleBackButton = false,
            key = "InternetStack",
            childFactory = { config, _ -> InternetScreenComponent.Placeholder(config) },
        )
}
