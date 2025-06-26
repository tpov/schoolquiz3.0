package com.tpov.schoolquiz.di

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tpov.common.presentation.utils.ViewModelFactory
import com.tpov.schoolquiz.presentation.create.CreateQuizViewModel
import com.tpov.schoolquiz.presentation.edit.EditQuizViewModel
import com.tpov.schoolquiz.presentation.main.MainViewModel
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import kotlin.reflect.KClass

@Module
abstract class ViewModelModule {

    @Binds
    abstract fun bindViewModelFactory(factory: ViewModelFactory): ViewModelProvider.Factory

    @Binds
    @IntoMap
    @ViewModelKey(MainViewModel::class)
    abstract fun bindMainViewModel(viewModel: MainViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(EditQuizViewModel::class)
    abstract fun bindCreateQuizViewModel(viewModel: EditQuizViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(CreateQuizViewModel::class)
    abstract fun bindCreateQuizViewModel2(viewModel: CreateQuizViewModel): ViewModel

    companion object {
        @Provides
        fun provideSavedStateHandle(): SavedStateHandle {
            return SavedStateHandle()
        }
    }
}

@MustBeDocumented
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MapKey
annotation class ViewModelKey(val value: KClass<out ViewModel>)
