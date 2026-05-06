package com.tpov.schoolquiz.apps.android_next.di

import androidx.work.WorkManager
import androidx.work.WorkerFactory
import com.tpov.schoolquiz.platform.android_services.sync.SyncWorkerFactory
import com.tpov.schoolquiz.platform.android_services.sync.WorkManagerSyncScheduler
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import com.tpov.schoolquiz.shared.core.persistence.RoomSyncStateRepository
import com.tpov.schoolquiz.shared.core.persistence.SyncStateDao
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncListOrchestrator
import com.tpov.schoolquiz.shared.core.sync.LessonContentSyncOrchestrator
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
import com.tpov.schoolquiz.shared.core.sync.SyncStateRepository
import com.tpov.schoolquiz.shared.core.sync.Syncable
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.data.sync.ProfileBootstrapSync
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.sync.LessonResultSync
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.QuestArenaSubmissionSync
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.QuestPrivateSync
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.ReviewAssignmentSync
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val syncModule =
    module {
        single<SyncStateRepository> { RoomSyncStateRepository(get<SyncStateDao>()) }
        single<WorkManager> { WorkManager.getInstance(androidContext()) }
        single<SyncScheduler> { WorkManagerSyncScheduler(get<WorkManager>()) }
        single<CatalogSyncListOrchestrator> {
            CatalogSyncListOrchestrator(
                catalogRepo = get<CatalogRepository>(),
                questRepo = get<QuestRepository>(),
                sectionRepo = get<SectionRepository>(),
                themeRepo = get<ThemeRepository>(),
                lessonRepo = get<LessonRepository>(),
                questionRepo = get<QuestionRepository>(),
                syncStateRepo = get<SyncStateRepository>(),
                syncChangeRemote = get(),
            )
        }
        single<LessonContentSyncOrchestrator> {
            LessonContentSyncOrchestrator(
                catalogSync = get<CatalogSyncListOrchestrator>(),
                lessonRepo = get<LessonRepository>(),
                themeRepo = get<ThemeRepository>(),
                sectionRepo = get<SectionRepository>(),
                questRepo = get<QuestRepository>(),
                questionRepo = get<QuestionRepository>(),
                syncStateRepo = get<SyncStateRepository>(),
                syncChangeRemote = get(),
            )
        }
        single<QuestPrivateSync> {
            QuestPrivateSync(
                local = get(),
                remote = get(),
                syncStateRepo = get<SyncStateRepository>(),
                currentUidProvider = { get<AuthRepository>().currentUid() },
            )
        }
        single<ReviewAssignmentSync> {
            ReviewAssignmentSync(
                local = get(),
                remote = get(),
                syncStateRepo = get<SyncStateRepository>(),
                currentUidProvider = { get<AuthRepository>().currentUid() },
            )
        }
        single<ProfileBootstrapSync> {
            ProfileBootstrapSync(
                repository = get<ProfileRepository>(),
                currentUidProvider = { get<AuthRepository>().currentUid() },
            )
        }
        single<LessonResultSync> {
            LessonResultSync(
                outboxDao = get(),
                remote = get(),
                nowMs = { System.currentTimeMillis() },
            )
        }
        single<List<Syncable>> {
            val profileSync = get<ProfileBootstrapSync>()
            listOf(
                profileSync,
                get<LessonResultSync>(),
                // Refresh profile-backed local tables after result outbox pushes server rewards.
                profileSync,
                get<UserStatsRepository>() as Syncable,
                get<QuestPrivateSync>(),
                get<QuestArenaSubmissionSync>(),
                get<ReviewAssignmentSync>(),
                get<CatalogSyncListOrchestrator>(),
            )
        }
        single<WorkerFactory> { SyncWorkerFactory(get()) }
    }
