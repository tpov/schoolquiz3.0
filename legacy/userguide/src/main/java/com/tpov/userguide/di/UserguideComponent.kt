package com.tpov.userguide.di

import android.app.Application
import android.content.Context
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [UserguideModule::class])
interface UserguideComponent {

    fun provideContext(): Context

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance application: Application): UserguideComponent
    }
}