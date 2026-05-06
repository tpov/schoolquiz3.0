package com.tpov.schoolquiz.shared.core.persistence.di

import androidx.room.Room
import com.tpov.schoolquiz.shared.core.persistence.AppDatabase
import com.tpov.schoolquiz.shared.core.persistence.CatalogDao
import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptDao
import com.tpov.schoolquiz.shared.core.persistence.LessonDao
import com.tpov.schoolquiz.shared.core.persistence.LessonRatingLocalDao
import com.tpov.schoolquiz.shared.core.persistence.LessonResultSyncOutboxDao
import com.tpov.schoolquiz.shared.core.persistence.QuestionDao
import com.tpov.schoolquiz.shared.core.persistence.QuestArenaSubmissionDao
import com.tpov.schoolquiz.shared.core.persistence.QuestAuthoringDao
import com.tpov.schoolquiz.shared.core.persistence.QuestDao
import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentDao
import com.tpov.schoolquiz.shared.core.persistence.SectionDao
import com.tpov.schoolquiz.shared.core.persistence.StringSetConverter
import com.tpov.schoolquiz.shared.core.persistence.SyncStateDao
import com.tpov.schoolquiz.shared.core.persistence.ThemeDao
import com.tpov.schoolquiz.shared.core.persistence.TopParticipantListConverter
import com.tpov.schoolquiz.shared.core.persistence.UserProfileDao
import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_3_4
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_4_5
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_5_6
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_6_7
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_7_8
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_8_9
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_9_10
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_10_11
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_11_12
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_12_13
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_13_14
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_14_15
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_15_16
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_16_17
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val persistenceModule = module {
    single<AppDatabase> {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "schoolquiz.db",
        )
            .addMigrations(
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
            )
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
    }
    single<UserStatsDao> { get<AppDatabase>().userStatsDao() }
    single<CatalogDao> { get<AppDatabase>().catalogDao() }
    single<QuestDao> { get<AppDatabase>().questDao() }
    single<SectionDao> { get<AppDatabase>().sectionDao() }
    single<ThemeDao> { get<AppDatabase>().themeDao() }
    single<LessonDao> { get<AppDatabase>().lessonDao() }
    single<QuestionDao> { get<AppDatabase>().questionDao() }
    single<LessonAttemptDao> { get<AppDatabase>().lessonAttemptDao() }
    single<LessonRatingLocalDao> { get<AppDatabase>().lessonRatingLocalDao() }
    single<LessonResultSyncOutboxDao> { get<AppDatabase>().lessonResultSyncOutboxDao() }
    single<SyncStateDao> { get<AppDatabase>().syncStateDao() }
    single<QuestAuthoringDao> { get<AppDatabase>().questAuthoringDao() }
    single<QuestArenaSubmissionDao> { get<AppDatabase>().questArenaSubmissionDao() }
    single<ReviewAssignmentDao> { get<AppDatabase>().reviewAssignmentDao() }
    single<UserProfileDao> { get<AppDatabase>().userProfileDao() }
}
