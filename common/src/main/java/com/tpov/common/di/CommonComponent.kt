package com.tpov.common.di

import android.app.Application
import android.content.Context
import com.tpov.common.data.database.QuestionDao
import com.tpov.common.data.database.StructureDataDao
import com.tpov.common.data.database.StructureEditDataDao
import com.tpov.common.presentation.question.QuestionActivity
import com.tpov.common.presentation.question.TranslateDialog
import com.tpov.common.presentation.quiz.QuizFragment
import dagger.BindsInstance
import dagger.Component
import kotlinx.coroutines.InternalCoroutinesApi

@Component(modules = [CommonModule::class, ViewModelModule::class, DatabaseModuleCommon::class, FirebaseModule::class])
interface CommonComponent {
    fun provideContext(): Context
    fun provideStructureEditDataDao(): StructureEditDataDao
    @OptIn(InternalCoroutinesApi::class)
    fun inject(activity: QuestionActivity)
    fun provideStructureDataDao(): StructureDataDao
    fun provideQuestionDao(): QuestionDao
    @OptIn(InternalCoroutinesApi::class)
    fun inject(mainFragment: QuizFragment)
    fun inject(translateDialog: TranslateDialog)

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance application: Application): CommonComponent
    }
}