package com.tpov.schoolquiz.shared.core.persistence.di

import androidx.room.Room
import com.tpov.schoolquiz.shared.core.persistence.AppDatabase
import com.tpov.schoolquiz.shared.core.persistence.CatalogDao
import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptDao
import com.tpov.schoolquiz.shared.core.persistence.LessonDao
import com.tpov.schoolquiz.shared.core.persistence.LessonRatingLocalDao
import com.tpov.schoolquiz.shared.core.persistence.QuestionAnswerDao
import com.tpov.schoolquiz.shared.core.persistence.QuestionRepetitionDao
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
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration1to2
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration2to3
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration3to4
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration4to5
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val persistenceModule = module {
    single<AppDatabase> {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "schoolquiz.db",
        )
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .addMigrations(Migration1to2, Migration2to3, Migration3to4, Migration4to5)
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
    single<QuestionAnswerDao> { get<AppDatabase>().questionAnswerDao() }
    single<QuestionRepetitionDao> { get<AppDatabase>().questionRepetitionDao() }
}
