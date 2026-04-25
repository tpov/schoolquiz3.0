package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        UserStatsEntity::class,
        CatalogEntity::class,
        QuestEntity::class,
        SectionEntity::class,
        ThemeEntity::class,
        LessonEntity::class,
        QuestionEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(StringSetConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userStatsDao(): UserStatsDao
    abstract fun catalogDao(): CatalogDao
    abstract fun questDao(): QuestDao
    abstract fun sectionDao(): SectionDao
    abstract fun themeDao(): ThemeDao
    abstract fun lessonDao(): LessonDao
    abstract fun questionDao(): QuestionDao
}
