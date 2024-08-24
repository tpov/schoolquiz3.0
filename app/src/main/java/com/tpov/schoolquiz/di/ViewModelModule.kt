package com.tpov.schoolquiz.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tpov.schoolquiz.presentation.main.MainViewModel
import com.tpov.schoolquiz.presentation.main.ViewModelFactory
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

@Module
abstract class ViewModelModule {

    @Binds
    abstract fun bindViewModelFactory(factory: ViewModelFactory): ViewModelProvider.Factory

    @Binds
    @IntoMap
    @ViewModelKey(MainViewModel::class)
    abstract fun bindMainViewModel(viewModel: MainViewModel): ViewModel
}