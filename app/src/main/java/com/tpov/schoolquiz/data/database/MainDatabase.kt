package com.tpov.schoolquiz.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tpov.common.data.custom.Converters
import com.tpov.common.data.database.QuestionDao
import com.tpov.common.data.database.QuestionDetailDao
import com.tpov.common.data.database.QuizDao
import com.tpov.common.data.model.local.QuestionDetailEntity
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import kotlinx.coroutines.InternalCoroutinesApi

@Database(
    entities = [QuestionDetailEntity::class, QuestionEntity::class, QuizEntity::class, ProfileEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MainDatabase : RoomDatabase() {
    abstract fun getQuizDao(): QuizDao
    abstract fun getQuestionDao(): QuestionDao
    abstract fun getQuestionDetailDao(): QuestionDetailDao
    abstract fun getProfileDao(): ProfileDao

    companion object {
        @Volatile
        private var INSTANCE: MainDatabase? = null

        @InternalCoroutinesApi
        fun getDatabase(context: Context): MainDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext, MainDatabase::class.java, "MainData.db"
                ).fallbackToDestructiveMigration().allowMainThreadQueries().build()
                INSTANCE = instance
                instance
            }
        }
    }
}