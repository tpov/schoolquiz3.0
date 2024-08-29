package com.tpov.schoolquiz.di

import android.app.Application
import com.tpov.schoolquiz.presentation.AppWorkerFactory
import com.tpov.schoolquiz.presentation.create_quiz.CreateQuizActivity
import com.tpov.schoolquiz.presentation.main.MainActivity
import com.tpov.schoolquiz.presentation.main.MainFragment
import com.tpov.schoolquiz.presentation.splashscreen.SplashScreen
import dagger.BindsInstance
import dagger.Component
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Singleton


@OptIn(InternalCoroutinesApi::class)
@Singleton
@Component(modules = [AppModule::class, ViewModelModule::class, WorkerModule::class])
interface ApplicationComponent {
    fun inject(application: Application)
    fun inject(splashScreen: SplashScreen)
    fun inject(mainFragment: MainFragment)
    fun inject(mainActivity: MainActivity)
    fun inject(createQuizActivity: CreateQuizActivity)
    fun workerFactory(): AppWorkerFactory

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance application: Application): ApplicationComponent
    }
}