package com.tpov.schoolquiz.di

import android.app.Application
import com.tpov.common.data.RepositoryQuestionDetailImpl
import com.tpov.common.data.RepositoryQuestionImpl
import com.tpov.common.data.RepositoryQuizImpl
import com.tpov.common.data.dao.QuestionDao
import com.tpov.common.data.dao.QuestionDetailDao
import com.tpov.common.data.dao.QuizDao
import com.tpov.common.domain.repository.RepositoryQuestion
import com.tpov.common.domain.repository.RepositoryQuestionDetail
import com.tpov.common.domain.repository.RepositoryQuiz
import com.tpov.schoolquiz.data.RepositoryChatImpl
import com.tpov.schoolquiz.data.RepositoryDataImpl
import com.tpov.schoolquiz.data.RepositoryPlayersImpl
import com.tpov.schoolquiz.data.RepositoryProfileImpl
import com.tpov.schoolquiz.data.database.ChatDao
import com.tpov.schoolquiz.data.database.DataDao
import com.tpov.schoolquiz.data.database.PlayersDao
import com.tpov.schoolquiz.data.database.ProfileDao
import com.tpov.schoolquiz.data.database.QuizDatabase
import com.tpov.schoolquiz.domain.repository.RepositoryChat
import com.tpov.schoolquiz.domain.repository.RepositoryData
import com.tpov.schoolquiz.domain.repository.RepositoryPlayers
import com.tpov.schoolquiz.domain.repository.RepositoryProfile
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