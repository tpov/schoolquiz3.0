package com.tpov.schoolquiz.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import com.tpov.network.network.AutorisationViewModel
import com.tpov.network.network.chat.ChatViewModel
import com.tpov.network.network.event.EventViewModel
import com.tpov.network.network.profile.ProfileViewModel
import com.tpov.schoolquiz.presentation.main.MainActivityViewModel
import com.tpov.common.presentation.question.QuestionViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import kotlinx.coroutines.InternalCoroutinesApi

@Module
interface ViewModelModule {
    @Binds
    fun bindContext(application: Application): Context
    @InternalCoroutinesApi
    @Binds
    @IntoMap
    @ViewModelKey(QuestionViewModel::class)
    fun bindQuestionViewModel(viewModel: QuestionViewModel): ViewModel

    @InternalCoroutinesApi
    @Binds
    @IntoMap
    @ViewModelKey(MainActivityViewModel::class)
    fun bindMainViewModel(viewModel: MainActivityViewModel): ViewModel


    @InternalCoroutinesApi
    @Binds
    @IntoMap
    @ViewModelKey(com.tpov.network.network.profile.ProfileViewModel::class)
    fun bindProfileViewModel(viewModel: com.tpov.network.network.profile.ProfileViewModel): ViewModel
    @InternalCoroutinesApi
    @Binds
    @IntoMap
    @ViewModelKey(com.tpov.network.network.AutorisationViewModel::class)
    fun bindAutorisationViewModel(viewModel: com.tpov.network.network.AutorisationViewModel): ViewModel
    @InternalCoroutinesApi
    @Binds
    @IntoMap
    @ViewModelKey(com.tpov.network.network.chat.ChatViewModel::class)
    fun bindChatViewModel(viewModel: com.tpov.network.network.chat.ChatViewModel): ViewModel
    @InternalCoroutinesApi
    @Binds
    @IntoMap
    @ViewModelKey(com.tpov.network.network.event.EventViewModel::class)
    fun bindEventViewModel(viewModel: com.tpov.network.network.event.EventViewModel): ViewModel

}