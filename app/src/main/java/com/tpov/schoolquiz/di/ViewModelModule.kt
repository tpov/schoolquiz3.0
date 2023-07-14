package com.tpov.schoolquiz.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import com.tpov.schoolquiz.presentation.main.MainActivityViewModel
import com.tpov.schoolquiz.presentation.network.AutorisationViewModel
import com.tpov.schoolquiz.presentation.network.chat.ChatViewModel
import com.tpov.schoolquiz.presentation.network.event.EventViewModel
import com.tpov.schoolquiz.presentation.network.profile.ProfileViewModel
import com.tpov.schoolquiz.presentation.question.QuestionViewModel
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
    @ViewModelKey(ProfileViewModel::class)
    fun bindProfileViewModel(viewModel: ProfileViewModel): ViewModel
    @InternalCoroutinesApi
    @Binds
    @IntoMap
    @ViewModelKey(AutorisationViewModel::class)
    fun bindAutorisationViewModel(viewModel: AutorisationViewModel): ViewModel
    @InternalCoroutinesApi
    @Binds
    @IntoMap
    @ViewModelKey(ChatViewModel::class)
    fun bindChatViewModel(viewModel: ChatViewModel): ViewModel
    @InternalCoroutinesApi
    @Binds
    @IntoMap
    @ViewModelKey(EventViewModel::class)
    fun bindEventViewModel(viewModel: EventViewModel): ViewModel

}