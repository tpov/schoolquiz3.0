package com.tpov.schoolquiz.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tpov.schoolquiz.data.custom.Converters
import com.tpov.schoolquiz.data.database.entities.*
import kotlinx.coroutines.InternalCoroutinesApi

@Database(
    entities = [QuestionDetailEntity::class, PlayersEntity::class, QuestionEntity::class, QuizEntity::class, ProfileEntity::class, ChatEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class QuizDatabase : RoomDatabase() {
    abstract fun getQuizDao(): QuizDao
    abstract fun getQuestionDao(): QuestionDao
    abstract fun getQuestionDetailDao(): QuestionDetailDao
    abstract fun getProfileDao(): ProfileDao
    abstract fun getChatDao(): ChatDao
    abstract fun getDataDao(): DataDao
    abstract fun getPlayersDao(): PlayersDao

    companion object {
        @Volatile
        private var INSTANCE: QuizDatabase? = null

        @InternalCoroutinesApi
        fun getDatabase(context: Context): QuizDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext, QuizDatabase::class.java, "SchoolQuiz.db"
                ).fallbackToDestructiveMigration().allowMainThreadQueries().build()
                INSTANCE = instance
                instance
            }
        }
    }
}