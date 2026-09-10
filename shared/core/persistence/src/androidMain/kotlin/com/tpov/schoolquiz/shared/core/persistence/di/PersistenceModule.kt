package com.tpov.schoolquiz.shared.core.persistence.di

import androidx.room.Room
import com.tpov.schoolquiz.shared.core.persistence.AppDatabase
import com.tpov.schoolquiz.shared.core.persistence.ArenaFrozenRatingDao
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketDao
import com.tpov.schoolquiz.shared.core.persistence.CatalogDao
import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptDao
import com.tpov.schoolquiz.shared.core.persistence.LessonDao
import com.tpov.schoolquiz.shared.core.persistence.LessonRatingLocalDao
import com.tpov.schoolquiz.shared.core.persistence.MutationReceiptDao
import com.tpov.schoolquiz.shared.core.persistence.OutboxDao
import com.tpov.schoolquiz.shared.core.persistence.OwnerRowsDao
import com.tpov.schoolquiz.shared.core.persistence.QuestAuthoringDao
import com.tpov.schoolquiz.shared.core.persistence.QuestDao
import com.tpov.schoolquiz.shared.core.persistence.QuestionAnswerDao
import com.tpov.schoolquiz.shared.core.persistence.QuestionDao
import com.tpov.schoolquiz.shared.core.persistence.QuestionRepetitionDao
import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentDao
import com.tpov.schoolquiz.shared.core.persistence.SectionDao
import com.tpov.schoolquiz.shared.core.persistence.StringSetConverter
import com.tpov.schoolquiz.shared.core.persistence.SyncRunTraceDao
import com.tpov.schoolquiz.shared.core.persistence.SyncStateDao
import com.tpov.schoolquiz.shared.core.persistence.ThemeDao
import com.tpov.schoolquiz.shared.core.persistence.TopParticipantListConverter
import com.tpov.schoolquiz.shared.core.persistence.UserProfileDao
import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration10to11
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration11to12
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration12to13
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration13to14
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration1to2
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration2to3
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration3to4
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration4to5
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration5to6
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration6to7
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration7to8
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration8to9
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration9to10
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val persistenceModule =
    module {
        single<AppDatabase> {
            Room.databaseBuilder(
                androidContext(),
                AppDatabase::class.java,
                "schoolquiz.db",
            )
                .addTypeConverter(StringSetConverter())
                .addTypeConverter(TopParticipantListConverter())
                .addMigrations(
                    Migration1to2,
                    Migration2to3,
                    Migration3to4,
                    Migration4to5,
                    Migration5to6,
                    Migration6to7,
                    Migration7to8,
                    Migration8to9,
                    Migration9to10,
                    Migration10to11,
                    Migration11to12,
                    Migration12to13,
                    Migration13to14,
                )
                .build()
        }
        single<ArenaTicketDao> { get<AppDatabase>().arenaTicketDao() }
        single<ArenaFrozenRatingDao> { get<AppDatabase>().arenaFrozenRatingDao() }
        single<UserStatsDao> { get<AppDatabase>().userStatsDao() }
        single<CatalogDao> { get<AppDatabase>().catalogDao() }
        single<QuestDao> { get<AppDatabase>().questDao() }
        single<SectionDao> { get<AppDatabase>().sectionDao() }
        single<ThemeDao> { get<AppDatabase>().themeDao() }
        single<LessonDao> { get<AppDatabase>().lessonDao() }
        single<QuestionDao> { get<AppDatabase>().questionDao() }
        single<LessonAttemptDao> { get<AppDatabase>().lessonAttemptDao() }
        single<LessonRatingLocalDao> { get<AppDatabase>().lessonRatingLocalDao() }
        single<SyncStateDao> { get<AppDatabase>().syncStateDao() }
        single<SyncRunTraceDao> { get<AppDatabase>().syncRunTraceDao() }
        single<QuestAuthoringDao> { get<AppDatabase>().questAuthoringDao() }
        single<ReviewAssignmentDao> { get<AppDatabase>().reviewAssignmentDao() }
        single<UserProfileDao> { get<AppDatabase>().userProfileDao() }
        single<QuestionAnswerDao> { get<AppDatabase>().questionAnswerDao() }
        single<QuestionRepetitionDao> { get<AppDatabase>().questionRepetitionDao() }
        single<OutboxDao> { get<AppDatabase>().outboxDao() }
        single<MutationReceiptDao> { get<AppDatabase>().mutationReceiptDao() }
        single<OwnerRowsDao> { get<AppDatabase>().ownerRowsDao() }
    }
