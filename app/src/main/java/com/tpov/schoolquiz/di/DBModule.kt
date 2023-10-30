package com.tpov.schoolquiz.di

import android.app.Application
import com.tpov.schoolquiz.data.*
import com.tpov.schoolquiz.data.database.*
import com.tpov.schoolquiz.domain.repository.*
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
    fun bindRepositoryChat(impl: RepositoryChatImpl): RepositoryChat

    @InternalCoroutinesApi
    @Singleton
    @Binds
    fun bindRepositoryData(impl: RepositoryDataImpl): RepositoryData

    @InternalCoroutinesApi
    @Singleton
    @Binds
    fun bindRepositoryPlayers(impl: RepositoryPlayersImpl): RepositoryPlayers

    @InternalCoroutinesApi
    @Singleton
    @Binds
    fun bindRepositoryProfile(impl: RepositoryProfileImpl): RepositoryProfile

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
            return QuizDatabase.getDatabase(application).getQuizDao()
        }

        @InternalCoroutinesApi
        @Provides
        fun provideGetQuestionDao(application: Application): QuestionDao {
            return QuizDatabase.getDatabase(application).getQuestionDao()
        }

        @InternalCoroutinesApi
        @Provides
        fun provideGetQuestionDetailDao(application: Application): QuestionDetailDao {
            return QuizDatabase.getDatabase(application).getQuestionDetailDao()
        }

        @InternalCoroutinesApi
        @Provides
        fun provideGetChatDao(application: Application): ChatDao {
            return QuizDatabase.getDatabase(application).getChatDao()
        }

        @InternalCoroutinesApi
        @Provides
        fun provideGetProfileDao(application: Application): ProfileDao {
            return QuizDatabase.getDatabase(application).getProfileDao()
        }

        @InternalCoroutinesApi
        @Provides
        fun provideGetPlayersDao(application: Application): PlayersDao {
            return QuizDatabase.getDatabase(application).getPlayersDao()
        }

        @InternalCoroutinesApi
        @Provides
        fun provideGetDataDao(application: Application): DataDao {
            return QuizDatabase.getDatabase(application).getDataDao()
        }
    }
}