package com.tpov.schoolquiz.apps.android_next.di

import android.util.Log
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import com.tpov.schoolquiz.platform.android_services.network.AndroidNetworkMonitor
import com.tpov.schoolquiz.platform.android_services.sync.SyncPreferences
import com.tpov.schoolquiz.platform.android_services.sync.SyncWorkerFactory
import com.tpov.schoolquiz.platform.android_services.sync.WorkManagerSyncScheduler
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.outbox.AccountSwitchGuard
import com.tpov.schoolquiz.shared.core.outbox.MutationTransport
import com.tpov.schoolquiz.shared.core.outbox.NoLocalEffect
import com.tpov.schoolquiz.shared.core.outbox.OutboxEngine
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxQuarantineRouter
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.core.outbox.QuarantineListener
import com.tpov.schoolquiz.shared.core.persistence.OutboxDao
import com.tpov.schoolquiz.shared.core.persistence.RoomOutboxStore
import com.tpov.schoolquiz.shared.core.persistence.RoomSyncStateRepository
import com.tpov.schoolquiz.shared.core.persistence.SyncStateDao
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncListOrchestrator
import com.tpov.schoolquiz.shared.core.sync.ForceResync
import com.tpov.schoolquiz.shared.core.sync.InMemorySyncStatusRepository
import com.tpov.schoolquiz.shared.core.sync.LessonContentSyncOrchestrator
import com.tpov.schoolquiz.shared.core.sync.OutboxSyncable
import com.tpov.schoolquiz.shared.core.sync.SyncGate
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
import com.tpov.schoolquiz.shared.core.sync.SyncStateRepository
import com.tpov.schoolquiz.shared.core.sync.SyncStatusRepository
import com.tpov.schoolquiz.shared.core.sync.Syncable
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.data.sync.ProfileBootstrapSync
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.LessonResultQuarantineRollback
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox.QuestArenaQuarantineRollback
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.QuestArenaOutcomeSync
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.QuestPrivateSync
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.ReviewAssignmentSync
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** The account's own state — the only thing the rarer profile cadence refreshes. */
class ProfileSyncables(val value: List<Syncable>)

val syncModule =
    module {
        // Знание о связи платформенное, но нужно и записи, и чтению, поэтому живёт в
        // composition root рядом с планировщиком, а не в модуле фичи (AD-10).
        single<NetworkMonitor> { AndroidNetworkMonitor(androidContext()) }
        single<SyncStateRepository> { RoomSyncStateRepository(get<SyncStateDao>()) }

        // Очередь отложенных действий. Движок и таблица живут в composition root, а описание
        // самих операций — в модулях своих фич (AD-10).
        single<OutboxStore> { RoomOutboxStore(get<OutboxDao>()) }
        single<QuarantineListener> {
            OutboxQuarantineRouter(
                handlers =
                    mapOf(
                        // У разблокировки урока локальной половины нет: серверно-защищённые поля
                        // локально не меняются (AD-25), поэтому откатывать нечего. Сказано явно,
                        // чтобы не спутать с забытым обработчиком.
                        OutboxOperations.UNLOCK_LESSON to NoLocalEffect(),
                        // У остальных трёх локальная половина есть, и реакция на карантин одна:
                        // откат (AD-28). Решение принимает владеющая фича, ядро только зовёт.
                        OutboxOperations.SUBMIT_ATTEMPT to get<LessonResultQuarantineRollback>().attempts,
                        OutboxOperations.SUBMIT_RATING to get<LessonResultQuarantineRollback>().ratings,
                        OutboxOperations.SUBMIT_ARENA to get<QuestArenaQuarantineRollback>(),
                    ),
                // Операция без обработчика — молчаливое расхождение, которое AD-28 запрещает.
                onUnhandled =
                    QuarantineListener { record ->
                        Log.w(
                            "Outbox",
                            "Запись ушла в карантин, а обработчика у операции ${record.operation} нет: " +
                                "локальное состояние могло разойтись с сервером. Причина: ${record.lastError}",
                        )
                    },
            )
        }
        single<OutboxEngine> {
            OutboxEngine(
                store = get<OutboxStore>(),
                transport = get<MutationTransport>(),
                clock = { System.currentTimeMillis() },
                onQuarantined = get<QuarantineListener>(),
            )
        }
        // Слить очередь до смены аккаунта: после переключения прежнего uid уже не узнать (AD-8).
        // Последнее средство от разъехавшегося курсора: сегодня это лечится только переустановкой
        // приложения (AD-30). Читающая сторона — контентный оркестратор, очередь не трогается.
        // Одни ворота на всё приложение: сброс курсоров и обычный проход не пересекаются.
        single<SyncGate> { SyncGate() }
        single<ForceResync> {
            ForceResync(
                syncStateRepo = get<SyncStateRepository>(),
                readSide = get<CatalogSyncListOrchestrator>(),
                gate = get<SyncGate>(),
                // Иначе «Перечитать всё» — кнопка, после которой ничего видимого не происходит.
                status = get<SyncStatusRepository>(),
                clock = { System.currentTimeMillis() },
            )
        }
        single<AccountSwitchGuard> { AccountSwitchGuard(get<OutboxEngine>(), get<OutboxStore>()) }
        // Состояние синхронизации наружу (AD-14). Auth-scoped: счётчики принадлежат uid, и после
        // смены аккаунта чужие числа на экране остаться не должны.
        single<SyncStatusRepository> {
            InMemorySyncStatusRepository(
                store = get<OutboxStore>(),
                currentUidFlow = get<AuthRepository>().observeUid(),
            )
        }
        single<OutboxSyncable> {
            OutboxSyncable(
                engine = get<OutboxEngine>(),
                currentUidProvider = { get<AuthRepository>().currentUid() },
                status = get<SyncStatusRepository>(),
                clock = { System.currentTimeMillis() },
            )
        }
        single<WorkManager> { WorkManager.getInstance(androidContext()) }
        single<SyncScheduler> { WorkManagerSyncScheduler(get<WorkManager>()) }
        single<SyncPreferences> { SyncPreferences(androidContext()) }
        single<WorkerFactory> { SyncWorkerFactory(get(), get<ProfileSyncables>().value) }
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
                gate = get<SyncGate>(),
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
        single<QuestArenaOutcomeSync> {
            QuestArenaOutcomeSync(local = get(), remote = get(), timestampProvider = get())
        }
        single<ProfileSyncables> {
            ProfileSyncables(listOf(get<ProfileBootstrapSync>(), get<UserStatsRepository>() as Syncable))
        }

        single<List<Syncable>> {
            val profileSync = get<ProfileBootstrapSync>()
            listOf(
                profileSync,
                // Прохождения, оценки и заявки на арену уезжают общей очередью, своих
                // отправителей у них больше нет.
                get<OutboxSyncable>(),
                // Refresh profile-backed local tables after result outbox pushes server rewards.
                profileSync,
                get<UserStatsRepository>() as Syncable,
                get<QuestPrivateSync>(),
                // Вердикт рецензентов движок принести не может: это чтение, а не отправка.
                get<QuestArenaOutcomeSync>(),
                get<ReviewAssignmentSync>(),
                get<CatalogSyncListOrchestrator>(),
            )
        }
    }
