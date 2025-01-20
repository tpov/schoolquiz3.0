package com.tpov.common.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tpov.common.data.manager.Converters
import com.tpov.common.data.model.local.QuestionDetailEntity
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.StructureDataEntity
import com.tpov.common.data.model.remote.StructureEditData

@Database(
    entities = [QuestionDetailEntity::class, QuestionEntity::class, StructureDataEntity::class, StructureEditData::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class CommonDatabase : RoomDatabase() {
    abstract fun getQuestionDao(): QuestionDao
    abstract fun getQuestionDetailDao(): QuestionDetailDao
    abstract fun getStructureEditDataDao(): StructureEditDataDao
    abstract fun getStructureDataDao(): StructureDataDao
}