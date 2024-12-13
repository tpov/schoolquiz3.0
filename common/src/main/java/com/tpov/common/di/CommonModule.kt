package com.tpov.common.di

import android.app.Application
import android.content.Context
import dagger.Module
import dagger.Provides

@Module(includes = [ViewModelModule::class])
class CommonModule {

    @Provides
    fun provideContext(application: Application): Context {
        return application.applicationContext
    }
}