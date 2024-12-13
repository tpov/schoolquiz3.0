package com.tpov.common.di

import android.app.Application
import android.content.Context
import com.tpov.common.data.database.QuestionDao
import com.tpov.common.data.database.QuizDao
import com.tpov.common.data.database.StructureCategoryDataDao
import com.tpov.common.data.database.StructureRatingDataDao
import com.tpov.common.presentation.quiz.QuizFragment
import dagger.BindsInstance
import dagger.Component
import kotlinx.coroutines.InternalCoroutinesApi

@Component(modules = [CommonModule::class, ViewModelModule::class, DatabaseModuleCommon::class, FirebaseModule::class])
interface CommonComponent {
    fun provideContext(): Context

    fun provideStructureRatingDataDao(): StructureRatingDataDao

    fun provideStructureCategoryDataDao(): StructureCategoryDataDao
    fun provideQuizDao(): QuizDao
    fun provideQuestionDao(): QuestionDao
    @OptIn(InternalCoroutinesApi::class)
    fun inject(mainFragment: QuizFragment)

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance application: Application): CommonComponent
    }
}