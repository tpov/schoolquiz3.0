package com.tpov.schoolquiz.di

import android.app.Application
import com.tpov.common.di.DBModule
import com.tpov.common.presentation.question.QuestionActivity
import com.tpov.common.presentation.question.QuestionListActivity
import com.tpov.network.network.AutorisationFragment
import com.tpov.network.network.chat.ChatFragment
import com.tpov.network.network.chat.ChatViewModel
import com.tpov.network.network.event.EventFragment
import com.tpov.network.network.event.EventViewModel
import com.tpov.network.network.event.TranslateQuestionFragment
import com.tpov.network.network.profile.ProfileFragment
import com.tpov.schoolquiz.presentation.main.FragmentMain
import com.tpov.schoolquiz.presentation.main.MainActivity
import com.tpov.schoolquiz.presentation.shop.ShopFragment
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

    fun inject(activity: MainActivity)

    fun inject(profileFragment: com.tpov.network.network.profile.ProfileFragment)

    fun inject(autorisationFragment: com.tpov.network.network.AutorisationFragment)

    fun inject(chatFragment: com.tpov.network.network.chat.ChatFragment)

    fun inject(chatViewModel: com.tpov.network.network.chat.ChatViewModel)

    fun inject(eventViewModel: com.tpov.network.network.event.EventViewModel)

    fun inject(eventFragment: com.tpov.network.network.event.EventFragment)

    fun inject(shopFragment: ShopFragment)

    fun inject(translateQuestionFragment: com.tpov.network.network.event.TranslateQuestionFragment)


    @Component.Factory
    interface Factory {     //Граф зависимостей

        fun create(
            @BindsInstance application: Application
        ): ApplicationComponent
    }
}