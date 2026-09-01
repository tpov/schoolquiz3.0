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
        LessonAttemptEntity::class,
        LessonRatingSubmittedLocalEntity::class,
        SyncStateEntity::class,
        QuestDraftEntity::class,
        DraftSectionEntity::class,
        DraftThemeEntity::class,
        DraftLessonEntity::class,
        DraftQuestionEntity::class,
        ReviewAssignmentEntity::class,
        ReviewAssignmentQuestionEntity::class,
        UserProfileEntity::class,
        QuestionAnswerEntity::class,
        QuestionRepetitionEntity::class,
        OutboxEntity::class,
    ],
    // Bumped whenever the schema changes. Destructive fallback only fires on a version change —
    // leaving the number alone makes Room compare identity hashes and crash instead.
    version = 6,
    exportSchema = true,
)
@TypeConverters(StringSetConverter::class, TopParticipantListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userStatsDao(): UserStatsDao
    abstract fun catalogDao(): CatalogDao
    abstract fun questDao(): QuestDao
    abstract fun sectionDao(): SectionDao
    abstract fun themeDao(): ThemeDao
    abstract fun lessonDao(): LessonDao
    abstract fun questionDao(): QuestionDao
    abstract fun lessonAttemptDao(): LessonAttemptDao
    abstract fun lessonRatingLocalDao(): LessonRatingLocalDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun questAuthoringDao(): QuestAuthoringDao
    abstract fun reviewAssignmentDao(): ReviewAssignmentDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun questionAnswerDao(): QuestionAnswerDao
    abstract fun questionRepetitionDao(): QuestionRepetitionDao

    abstract fun outboxDao(): OutboxDao
}
