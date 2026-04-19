package com.tpov.setting.di

import android.app.Application
import android.content.Context
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [SettingModule::class])
interface SettingComponent {

    fun provideContext(): Context

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance application: Application): SettingComponent
    }
}