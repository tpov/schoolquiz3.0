package com.tpov.common.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tpov.common.presentation.quiz.QuizActivityViewModel
import com.tpov.common.presentation.utils.ViewModelFactory
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.multibindings.IntoMap
import kotlinx.coroutines.InternalCoroutinesApi
import kotlin.reflect.KClass

@Module
abstract class ViewModelModule {

    @Binds
    abstract fun bindViewModelFactory(factory: ViewModelFactory): ViewModelProvider.Factory

    @OptIn(InternalCoroutinesApi::class)
    @Binds
    @IntoMap
    @ViewModelKey(QuizActivityViewModel::class)
    abstract fun bindQuizActivityViewModel(viewModel: QuizActivityViewModel): ViewModel

}

@MustBeDocumented
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MapKey
annotation class ViewModelKey(val value: KClass<out ViewModel>)
