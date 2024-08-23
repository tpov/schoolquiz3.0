package com.tpov.common.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tpov.common.data.custom.Converters
import com.tpov.common.data.model.local.QuestionDetailEntity
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureRatingDataEntity

@Database(
    entities = [QuestionDetailEntity::class, QuestionEntity::class, QuizEntity::class, StructureRatingDataEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class CommonDatabase : RoomDatabase() {
    abstract fun getQuizDao(): QuizDao
    abstract fun getQuestionDao(): QuestionDao
    abstract fun getQuestionDetailDao(): QuestionDetailDao
    abstract fun getStructureRatingDataDao(): StructureRatingDataDao

}