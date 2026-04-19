package com.tpov.schoolquiz.android.feature.app_shell.presentation.component.tab

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.feature.app_shell.presentation.screen.ShopScreenComponent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.ShopConfig

interface ShopTabComponent {
    val childStack: Value<ChildStack<ShopConfig, ShopScreenComponent>>
}

class DefaultShopTabComponent(
    componentContext: ComponentContext,
    navigation: StackNavigation<ShopConfig>,
) : ShopTabComponent, ComponentContext by componentContext {
    override val childStack: Value<ChildStack<ShopConfig, ShopScreenComponent>> =
        childStack(
            source = navigation,
            serializer = null,
            initialConfiguration = ShopConfig.ShopRoot,
            handleBackButton = false,
            key = "ShopStack",
            childFactory = { config, _ -> ShopScreenComponent.Placeholder(config) },
        )
}
