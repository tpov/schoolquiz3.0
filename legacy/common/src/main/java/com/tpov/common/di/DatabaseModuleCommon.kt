package com.tpov.common.di

import android.app.Application
import androidx.room.Room
import com.tpov.common.data.database.CommonDatabase
import com.tpov.common.data.database.QuestionDao
import com.tpov.common.data.database.QuestionDetailDao
import com.tpov.common.data.database.StructureDataDao
import com.tpov.common.data.database.StructureEditDataDao
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
    fun provideQuestionDetailDao(database: CommonDatabase): QuestionDetailDao {
        return database.getQuestionDetailDao()
    }
    @Provides
    fun provideStructureEditDataDao(database: CommonDatabase): StructureEditDataDao {
        return database.getStructureEditDataDao()
    }
    @Provides
    fun provideStructureDataDao(database: CommonDatabase): StructureDataDao {
        return database.getStructureDataDao()
    }
    
    @Provides
    fun provideQuestionDao(database: CommonDatabase): QuestionDao {
        return database.getQuestionDao()
    }
}