package com.tpov.schoolquiz.di

import android.app.Application
import androidx.room.Room
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
    fun provideProfileDao(database: MainDatabase): ProfileDao {
        return database.getProfileDao()
    }
}
