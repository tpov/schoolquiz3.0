package com.tpov.common.di

import android.app.Application
import com.tpov.common.data.RepositoryQuestionDetailImpl
import com.tpov.common.data.RepositoryQuestionImpl
import com.tpov.common.data.RepositoryQuizImpl
import com.tpov.common.data.dao.CommonDatabase
import com.tpov.common.data.dao.QuestionDao
import com.tpov.common.data.dao.QuestionDetailDao
import com.tpov.common.data.dao.QuizDao
import com.tpov.common.domain.repository.RepositoryQuestion
import com.tpov.common.domain.repository.RepositoryQuestionDetail
import com.tpov.common.domain.repository.RepositoryQuiz
import dagger.Binds
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Singleton

@Module
interface DBModule {

    @InternalCoroutinesApi
    @Singleton
    @Binds
    fun bindRepositoryQuestionDetail(impl: RepositoryQuestionDetailImpl): RepositoryQuestionDetail

    @InternalCoroutinesApi
    @Singleton
    @Binds
    fun bindRepositoryQuestion(impl: RepositoryQuestionImpl): RepositoryQuestion

    @InternalCoroutinesApi
    @Singleton
    @Binds
    fun bindRepositoryQuiz(impl: RepositoryQuizImpl): RepositoryQuiz

    companion object {

        @InternalCoroutinesApi
        @Provides
        fun provideGetQuizDao(application: Application): QuizDao {
            return CommonDatabase.getDatabase(application).getQuizDao()
        }

        @InternalCoroutinesApi
        @Provides
        fun provideGetQuestionDao(application: Application): QuestionDao {
            return CommonDatabase.getDatabase(application).getQuestionDao()
        }

        @InternalCoroutinesApi
        @Provides
        fun provideGetQuestionDetailDao(application: Application): QuestionDetailDao {
            return CommonDatabase.getDatabase(application).getQuestionDetailDao()
        }
    }
}