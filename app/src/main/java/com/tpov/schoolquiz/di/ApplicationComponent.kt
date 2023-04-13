package com.tpov.schoolquiz.di

import android.app.Application
import com.tpov.schoolquiz.presentation.mainactivity.FragmentMain
import com.tpov.schoolquiz.presentation.mainactivity.MainActivity
import com.tpov.schoolquiz.presentation.network.AutorisationFragment
import com.tpov.schoolquiz.presentation.network.event.ArenaFragment
import com.tpov.schoolquiz.presentation.network.chat.ChatFragment
import com.tpov.schoolquiz.presentation.network.chat.ChatViewModel
import com.tpov.schoolquiz.presentation.network.event.EventFragment
import com.tpov.schoolquiz.presentation.network.event.EventViewModel
import com.tpov.schoolquiz.presentation.network.event.translation.TranslateQuestionFragment
import com.tpov.schoolquiz.presentation.network.profile.ProfileFragment
import com.tpov.schoolquiz.presentation.question.QuestionActivity
import com.tpov.schoolquiz.presentation.question.QuestionListActivity
import com.tpov.schoolquiz.presentation.splashscreen.SplashScreen
import dagger.BindsInstance
import dagger.Component
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        DBModule::class,
        ViewModelModule::class
    ]
)
@InternalCoroutinesApi
interface ApplicationComponent {
    fun inject(activity: QuestionActivity)

    fun inject(activity: QuestionListActivity)

    fun inject(fragment: FragmentMain)
    fun inject(fragment: ArenaFragment)

    fun inject(activity: SplashScreen)

    fun inject(activity: MainActivity)

    fun inject(profileFragment: ProfileFragment)

    fun inject(autorisationFragment: AutorisationFragment)

     fun inject(chatFragment: ChatFragment)

    fun inject(chatViewModel: ChatViewModel)

    fun inject(eventViewModel: EventViewModel)

    fun inject(eventFragment: EventFragment)

    fun inject(translateQuestionFragment: TranslateQuestionFragment)


    @Component.Factory
    interface Factory {     //Граф зависимостей

        fun create(
            @BindsInstance application: Application
        ): ApplicationComponent
    }
}