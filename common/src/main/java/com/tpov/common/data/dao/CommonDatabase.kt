package com.tpov.common.data.dao

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tpov.common.data.custom.Converters
import com.tpov.common.data.model.local.QuestionDetailEntity
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import kotlinx.coroutines.InternalCoroutinesApi

@Database(
    entities = [QuestionDetailEntity::class, QuestionEntity::class, QuizEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class CommonDatabase : RoomDatabase() {
    abstract fun getQuizDao(): QuizDao
    abstract fun getQuestionDao(): QuestionDao
    abstract fun getQuestionDetailDao(): QuestionDetailDao

    companion object {
        @Volatile
        private var INSTANCE: CommonDatabase? = null

        @InternalCoroutinesApi
        fun getDatabase(context: Context): CommonDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext, CommonDatabase::class.java, "SchoolQuizCommon.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}