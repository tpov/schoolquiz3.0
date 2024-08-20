package com.tpov.common.di

import android.app.Application
import android.content.Context
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [CommonModule::class])
interface CommonComponent {

    fun provideContext(): Context

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance application: Application): CommonComponent
    }
}