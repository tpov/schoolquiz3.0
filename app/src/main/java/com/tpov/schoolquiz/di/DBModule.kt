package com.tpov.schoolquiz.di

import android.app.Application
import android.content.Context
import com.tpov.schoolquiz.data.RepositoryDBImpl
import com.tpov.schoolquiz.data.RepositoryFBImpl
import com.tpov.schoolquiz.data.database.QuizDao
import com.tpov.schoolquiz.data.database.QuizDatabase
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import com.tpov.schoolquiz.domain.repository.RepositoryFB
import dagger.Binds
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Singleton

@Module
interface DBModule {

    @InternalCoroutinesApi
    @Binds
    fun bindRepositoryDB(impl: RepositoryDBImpl): RepositoryDB

    @InternalCoroutinesApi
    @Singleton
    @Binds
    fun bindRepositoryFB(impl: RepositoryFBImpl): RepositoryFB

    companion object {

        @InternalCoroutinesApi
        @Provides
        fun provideGetQuizDao(
            application: Application        //Нужно добавить в граф зависимостей
        ): QuizDao {
            return QuizDatabase.getDatabase(application).getQuizDao()
        }
    }
}