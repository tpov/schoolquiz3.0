package com.tpov.schoolquiz.di

import android.app.Application
import com.tpov.schoolquiz.presentation.DaggerWorkerFactory
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class])
interface ApplicationComponent {

    fun inject(application: Application)
    fun workerFactory(): DaggerWorkerFactory

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance application: Application): ApplicationComponent
    }
}