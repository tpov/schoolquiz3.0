package com.tpov.schoolquiz.di

import android.app.Application
import androidx.room.Room
import com.tpov.common.data.database.CommonDatabase
import com.tpov.common.data.database.QuestionDao
import com.tpov.common.data.database.QuestionDetailDao
import com.tpov.common.data.database.QuizDao
import com.tpov.common.data.database.StructureCategoryDataDao
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
    @Singleton
    fun provideCommonDatabase(application: Application): CommonDatabase {
        return Room.databaseBuilder(
            application,
            CommonDatabase::class.java,
            "CommonData.db"
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
    fun provideStructureRatingDataDao(database: CommonDatabase): StructureRatingDataDao {
        return database.getStructureRatingDataDao()
    }
    @Provides
    @Singleton
    fun provideStructureCategoryDataDao(database: CommonDatabase): StructureCategoryDataDao {
        return database.getStructureCategoryDataDao()
    }
}
