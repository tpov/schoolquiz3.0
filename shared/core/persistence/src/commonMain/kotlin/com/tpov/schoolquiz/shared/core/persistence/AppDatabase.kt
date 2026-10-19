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
        SyncRunTraceEntity::class,
        MutationReceiptEntity::class,
        LessonAttemptIntentEntity::class,
        ArenaTicketRecordEntity::class,
        ArenaCommandEntity::class,
        ArenaFrozenRatingIntentEntity::class,
        GiftClaimIntentEntity::class,
        GiftBoxLocalStreakEntity::class,
    ],
    // Every version has an explicit non-destructive migration and a generated Room schema.
    version = 17,
    exportSchema = true,
)
@TypeConverters(StringSetConverter::class, TopParticipantListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun arenaTicketDao(): ArenaTicketDao

    abstract fun arenaFrozenRatingDao(): ArenaFrozenRatingDao

    abstract fun mutationReceiptDao(): MutationReceiptDao

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

    abstract fun giftClaimIntentDao(): GiftClaimIntentDao

    abstract fun giftBoxLocalStreakDao(): GiftBoxLocalStreakDao

    abstract fun questionAnswerDao(): QuestionAnswerDao

    abstract fun questionRepetitionDao(): QuestionRepetitionDao

    abstract fun outboxDao(): OutboxDao

    /**
     * След прогона синхронизации — по строке на владельца и вид прогона (AD-14).
     *
     * Вид в ключе с версии 10: расписаний несколько, и общая строка означала, что удачный
     * профильный прогон снимает ветвь упавшего контентного.
     */
    abstract fun syncRunTraceDao(): SyncRunTraceDao

    /** Строки прежнего владельца — вторая операция смены аккаунта (AD-8). Схемы не касается. */
    abstract fun ownerRowsDao(): OwnerRowsDao
}
