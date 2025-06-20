package com.tpov.shop.di

import android.app.Application
import android.content.Context
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [ShopModule::class])
interface ShopComponent {

    fun provideContext(): Context

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance application: Application): ShopComponent
    }
}