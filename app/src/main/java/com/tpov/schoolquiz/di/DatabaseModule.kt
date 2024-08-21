package com.tpov.schoolquiz.di

import android.app.Application
import androidx.room.Room
import com.tpov.common.data.database.QuestionDao
import com.tpov.common.data.database.QuestionDetailDao
import com.tpov.common.data.database.QuizDao
import com.tpov.common.data.database.StructureRatingDataDao
import com.tpov.schoolquiz.data.database.MainDatabase
import com.tpov.schoolquiz.data.database.ProfileDao
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(application: Application): MainDatabase {
        return Room.databaseBuilder(
            application,
            MainDatabase::class.java,
            "MainData.db"
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideQuizDao(database: MainDatabase): QuizDao {
        return database.getQuizDao()
    }

    @Provides
    fun provideQuestionDao(database: MainDatabase): QuestionDao {
        return database.getQuestionDao()
    }

    @Provides
    fun provideQuestionDetailDao(database: MainDatabase): QuestionDetailDao {
        return database.getQuestionDetailDao()
    }

    @Provides
    fun provideProfileDao(database: MainDatabase): ProfileDao {
        return database.getProfileDao()
    }
    @Provides
    fun provideStructureRatingDataDao(database: MainDatabase): StructureRatingDataDao {
        return database.getStructureRatingDataDao()
    }
}