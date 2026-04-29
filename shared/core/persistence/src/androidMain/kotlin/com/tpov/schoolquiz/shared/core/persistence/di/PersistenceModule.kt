package com.tpov.schoolquiz.shared.core.persistence.di

import androidx.room.Room
import com.tpov.schoolquiz.shared.core.persistence.AppDatabase
import com.tpov.schoolquiz.shared.core.persistence.CatalogDao
import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptDao
import com.tpov.schoolquiz.shared.core.persistence.LessonDao
import com.tpov.schoolquiz.shared.core.persistence.LessonRatingLocalDao
import com.tpov.schoolquiz.shared.core.persistence.QuestionDao
import com.tpov.schoolquiz.shared.core.persistence.QuestDao
import com.tpov.schoolquiz.shared.core.persistence.SectionDao
import com.tpov.schoolquiz.shared.core.persistence.StringSetConverter
import com.tpov.schoolquiz.shared.core.persistence.ThemeDao
import com.tpov.schoolquiz.shared.core.persistence.TopParticipantListConverter
import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_3_4
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_4_5
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_5_6
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val persistenceModule = module {
    single<AppDatabase> {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "schoolquiz.db",
        )
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
}
