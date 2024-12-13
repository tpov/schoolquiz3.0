package com.tpov.common.di

import android.app.Application
import androidx.room.Room
import com.tpov.common.data.database.CommonDatabase
import com.tpov.common.data.database.QuestionDao
import com.tpov.common.data.database.QuizDao
import com.tpov.common.data.database.StructureCategoryDataDao
import com.tpov.common.data.database.StructureRatingDataDao
import dagger.Module
import dagger.Provides

@Module
class DatabaseModuleCommon {

    @Provides
    fun provideCommonDatabase(application: Application): CommonDatabase {
        return Room.databaseBuilder(
            application,
            CommonDatabase::class.java,
            "CommonData.db"
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideStructureRatingDataDao(database: CommonDatabase): StructureRatingDataDao {
        return database.getStructureRatingDataDao()
    }

    @Provides
    fun provideStructureCategoryDataDao(database: CommonDatabase): StructureCategoryDataDao {
        return database.getStructureCategoryDataDao()
    }

    @Provides
    fun provideQuizDao(database: CommonDatabase): QuizDao {
        return database.getQuizDao()
    }

    @Provides
    fun provideQuestionDao(database: CommonDatabase): QuestionDao {
        return database.getQuestionDao()
    }
}