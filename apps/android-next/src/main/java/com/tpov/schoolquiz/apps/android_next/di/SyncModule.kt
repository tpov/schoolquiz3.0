package com.tpov.schoolquiz.apps.android_next.di

import androidx.work.WorkManager
import androidx.work.WorkerFactory
import com.tpov.schoolquiz.platform.android_services.sync.SyncWorkerFactory
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import com.tpov.schoolquiz.shared.core.sync.CascadingSyncOrchestrator
import com.tpov.schoolquiz.shared.core.sync.InMemorySyncStateRepository
import com.tpov.schoolquiz.shared.core.sync.SyncStateRepository
import com.tpov.schoolquiz.shared.core.sync.Syncable
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val syncModule =
    module {
        single<SyncStateRepository> { InMemorySyncStateRepository() }
        single<WorkManager> { WorkManager.getInstance(androidContext()) }
        single<CascadingSyncOrchestrator> {
            CascadingSyncOrchestrator(
                catalogRepo = get<CatalogRepository>(),
                questRepo = get<QuestRepository>(),
                sectionRepo = get<SectionRepository>(),
                themeRepo = get<ThemeRepository>(),
                lessonRepo = get<LessonRepository>(),
                questionRepo = get<QuestionRepository>(),
                syncStateRepo = get<SyncStateRepository>(),
                authRepo = get<AuthRepository>(),
                userStatsRepo = get<UserStatsRepository>(),
            )
        }
        single<List<Syncable>> {
            listOf(
                get<UserStatsRepository>() as Syncable,
                get<CascadingSyncOrchestrator>(),
            )
        }
        single<WorkerFactory> { SyncWorkerFactory(get()) }
    }
