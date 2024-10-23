package com.tpov.schoolquiz.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tpov.schoolquiz.presentation.create_quiz.CreateQuizViewModel
import com.tpov.schoolquiz.presentation.main.MainViewModel
import com.tpov.schoolquiz.presentation.main.ViewModelFactory
import dagger.Binds
import dagger.MapKey
import dagger.Module
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
    @ViewModelKey(CreateQuizViewModel::class)
    abstract fun bindCreateQuizViewModel(viewModel: CreateQuizViewModel): ViewModel

}
@MustBeDocumented
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MapKey
annotation class ViewModelKey(val value: KClass<out ViewModel>)
