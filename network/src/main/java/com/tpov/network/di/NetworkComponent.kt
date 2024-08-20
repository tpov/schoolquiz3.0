package com.tpov.network.di

import com.tpov.common.di.CommonComponent
import dagger.Component


@Component(dependencies = [CommonComponent::class], modules = [NetworkModule::class])
interface NetworkComponent {

    fun inject()

    @Component.Factory
    interface Factory {
        fun create(commonComponent: CommonComponent): NetworkComponent
    }
}
