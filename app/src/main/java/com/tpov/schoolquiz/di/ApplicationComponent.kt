package com.tpov.schoolquiz.di

import android.app.Application
import com.tpov.common.di.CommonComponent
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class])
interface ApplicationComponent {

    fun commonComponent(): CommonComponent.Factory
    fun inject(application: Application)

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance application: Application): ApplicationComponent
    }
}