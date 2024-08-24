package com.tpov.schoolquiz.di

import android.app.Application
import com.tpov.schoolquiz.presentation.AppWorkerFactory
import com.tpov.schoolquiz.presentation.main.MainFragment
import com.tpov.schoolquiz.presentation.splashscreen.SplashScreen
import dagger.BindsInstance
import dagger.Component
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Singleton


@Singleton
@Component(modules = [AppModule::class, WorkerBindingModule::class, AssistedInjectModule::class, ViewModelModule::class])
interface ApplicationComponent {
    fun inject(application: Application)
    @OptIn(InternalCoroutinesApi::class)
    fun inject(splashScreen: SplashScreen)
    fun inject(mainFragment: MainFragment)
    fun workerFactory(): AppWorkerFactory

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance application: Application): ApplicationComponent
    }
}