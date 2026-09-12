package com.tpov.schoolquiz.apps.android_next.di

import android.util.Log
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import com.tpov.schoolquiz.platform.android_services.economy.PreferencesEconomyConstantsStore
import com.tpov.schoolquiz.platform.android_services.network.AndroidNetworkMonitor
import com.tpov.schoolquiz.platform.android_services.sync.SyncPreferences
import com.tpov.schoolquiz.platform.android_services.sync.SyncWorkerFactory
import com.tpov.schoolquiz.platform.android_services.sync.WorkManagerSyncScheduler
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.outbox.AccountSwitchGuard
import com.tpov.schoolquiz.shared.core.outbox.MutationReceiptStore
import com.tpov.schoolquiz.shared.core.outbox.MutationTransport
import com.tpov.schoolquiz.shared.core.outbox.OutboxEngine
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxQuarantineRouter
import com.tpov.schoolquiz.shared.core.outbox.OutboxRemainderSource
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.core.outbox.OwnerTraces
import com.tpov.schoolquiz.shared.core.outbox.QuarantineListener
import com.tpov.schoolquiz.shared.core.persistence.MutationReceiptDao
import com.tpov.schoolquiz.shared.core.persistence.OutboxDao
import com.tpov.schoolquiz.shared.core.persistence.OwnerRowsDao
import com.tpov.schoolquiz.shared.core.persistence.RoomMutationReceiptStore
import com.tpov.schoolquiz.shared.core.persistence.RoomOutboxStore
import com.tpov.schoolquiz.shared.core.persistence.RoomOwnerTraces
import com.tpov.schoolquiz.shared.core.persistence.RoomSyncRunTraceStore
import com.tpov.schoolquiz.shared.core.persistence.RoomSyncStateRepository
import com.tpov.schoolquiz.shared.core.persistence.SyncRunTraceDao
import com.tpov.schoolquiz.shared.core.persistence.SyncStateDao
import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncListOrchestrator
import com.tpov.schoolquiz.shared.core.sync.DefaultSyncStatusRepository
import com.tpov.schoolquiz.shared.core.sync.ForceResync
import com.tpov.schoolquiz.shared.core.sync.JournalReadPolicy
import com.tpov.schoolquiz.shared.core.sync.LessonContentSyncOrchestrator
import com.tpov.schoolquiz.shared.core.sync.OutboxSyncable
import com.tpov.schoolquiz.shared.core.sync.SyncGate
import com.tpov.schoolquiz.shared.core.sync.SyncRun
import com.tpov.schoolquiz.shared.core.sync.SyncRunTraceStore
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
import com.tpov.schoolquiz.shared.core.sync.SyncStateRepository
import com.tpov.schoolquiz.shared.core.sync.SyncStatusRepository
import com.tpov.schoolquiz.shared.core.sync.Syncable
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.PendingLocalCharges
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import com.tpov.schoolquiz.shared.feature.economy.data.EconomyConstantsStore
import com.tpov.schoolquiz.shared.feature.economy.data.EconomyConstantsSync
import com.tpov.schoolquiz.shared.feature.internet.profile.data.outbox.ListingCancelQuarantine
import com.tpov.schoolquiz.shared.feature.internet.profile.data.sync.ProfileBootstrapSync
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import com.tpov.schoolquiz.shared.feature.lesson.data.outbox.LessonUnlockOutbox
import com.tpov.schoolquiz.shared.feature.lesson.data.outbox.LessonUnlockQuarantineRollback
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.LessonResultQuarantineRollback
import com.tpov.schoolquiz.shared.feature.quest.data.outbox.QuestShelfOutbox
import com.tpov.schoolquiz.shared.feature.quest.data.outbox.QuestShelfQuarantineMark
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox.QuestArenaQuarantineRollback
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox.ReviewActionQuarantine
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.QuestArenaOutcomeSync
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.QuestPrivateSync
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.ReviewAssignmentSync
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.privateQuestSyncCursorId
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.reviewAssignmentSyncCursorId
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** The account's own state — the only thing the rarer profile cadence refreshes. */
class ProfileSyncables(val value: List<Syncable>)

/**
 * Состояние синхронизации наружу (AD-14) — единственная production-привязка контракта.
 *
 * Вынесена из [syncModule] отдельным модулем ради проверяемости, а не ради порядка. Остальной
 * [syncModule] без Android-контекста, WorkManager и живого Firebase не поднимается, и пока
 * привязка лежала внутри него, ни один JVM-тест не мог сказать, есть она вообще: корень собирал
 * экран, привязки не было, и вместо состояния очереди игрок получал вечное «синхронизации ещё не
 * было». Теперь `KoinModuleWiringTest` поднимает именно этот модуль, а не свою заглушку, —
 * пропадёт привязка, и тест сборки графа упадёт.
 *
 * Auth-scoped: и счётчики, и след принадлежат uid, и после смены аккаунта чужие числа на экране
 * остаться не должны (инвариант 8, AD-8).
 */
val syncStatusModule =
    module {
        single<SyncStatusRepository> {
            DefaultSyncStatusRepository(
                store = get<OutboxStore>(),
                trace = get<SyncRunTraceStore>(),
                currentUidFlow = get<AuthRepository>().observeUid(),
                // Пишущая сторона спрашивает uid тем же способом, что и остальная очередь: проход
                // случается и без живой подписки на состояние.
                currentUidProvider = { get<AuthRepository>().currentUid() },
            )
        }
        // Исход прогона — сюда же и ровно один раз за прогон (AD-14). Привязка стоит рядом с самим
        // контрактом, а не в [syncModule], по той же причине: так `KoinModuleWiringTest` поднимает
        // её без Android-контекста и WorkManager, и пропажа записывающей стороны падает тестом, а
        // не молчанием на экране.
        single<SyncRun> {
            SyncRun(
                status = get<SyncStatusRepository>(),
                clock = { System.currentTimeMillis() },
            )
        }
    }

val syncModule =
    module {
        includes(syncStatusModule)
        // Знание о связи платформенное, но нужно и записи, и чтению, поэтому живёт в
        // composition root рядом с планировщиком, а не в модуле фичи (AD-10).
        single<NetworkMonitor> { AndroidNetworkMonitor(androidContext()) }
        single<SyncStateRepository> { RoomSyncStateRepository(get<SyncStateDao>()) }

        // Очередь отложенных действий. Движок и таблица живут в composition root, а описание
        // самих операций — в модулях своих фич (AD-10).
        single { RoomOutboxStore(get<OutboxDao>()) }
        single<OutboxStore> { get<RoomOutboxStore>() }
        single<MutationReceiptStore> { RoomMutationReceiptStore(get<MutationReceiptDao>()) }
        // Остаток по всем пяти состояниям и список владельцев, оставивших строки, — вопрос смены
        // аккаунта (AD-8). Та же реализация, другой смысл: выборка «что поедет» на него не отвечает.
        single<OutboxRemainderSource> { get<RoomOutboxStore>() }
        single<QuarantineListener> {
            OutboxQuarantineRouter(
                handlers =
                    mapOf(
                        // У разблокировки урока локальная половина есть: владелец решил
                        // 2026-09-03, что нолики — мягкая валюта и урок открывается сразу, а
                        // AD-25 остаётся про настоящие серверные поля. Здесь стоял
                        // `NoLocalEffect`, и он молчал бы на карантин уже списанной покупки —
                        // урок остался бы открытым навсегда, а сервер за него не списал никогда.
                        // Имя берётся у владеющей фичи, у того же объекта, который эти записи и
                        // создаёт (NFR4).
                        LessonUnlockOutbox.OPERATION to get<LessonUnlockQuarantineRollback>(),
                        // У прохождения, оценки и заявки на арену локальная половина есть, и
                        // реакция на карантин та же: откат (AD-28). Решение принимает владеющая
                        // фича, ядро только зовёт.
                        OutboxOperations.SUBMIT_ATTEMPT to get<LessonResultQuarantineRollback>().attempts,
                        OutboxOperations.OPEN_GIFT_BOX to com.tpov.schoolquiz.shared.core.outbox.NoLocalEffect(),
                        OutboxOperations.SUBMIT_RATING to get<LessonResultQuarantineRollback>().ratings,
                        OutboxOperations.SUBMIT_ARENA to get<QuestArenaQuarantineRollback>(),
                        // Перенос квеста на полку: локальной половины нет (AD-25), последствие
                        // карантина — пометка на карточке по записи очереди. Обработчик объявлен
                        // в модуле фичи quest — той же, чьё имя стоит в операции (NFR4).
                        QuestShelfOutbox.OPERATION to get<QuestShelfQuarantineMark>(),
                        // Снятие лота: локальной половины нет (AD-25), пометку «уже продан» строка
                        // читает из очереди по entity_ref. Обработчик объявлен у фичи профиля.
                        OutboxOperations.CANCEL_NICKNAME_LISTING to get<ListingCancelQuarantine>(),
                        OutboxOperations.CANCEL_LOGO_LISTING to get<ListingCancelQuarantine>(),
                        // Решение ревьюера: локальной половины нет, пометку даёт сама запись очереди
                        // по entity_ref (AD-25, AD-28). Обработчик объявлен фичей, здесь только связь.
                        OutboxOperations.SUBMIT_REVIEW to get<ReviewActionQuarantine>(),
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
                receipts = get<MutationReceiptStore>(),
                sessions = get<OutboxSessionProvider>(),
            )
        }
        // Слить очередь до смены аккаунта: после переключения прежнего uid уже не узнать (AD-8).
        // Последнее средство от разъехавшегося курсора: сегодня это лечится только переустановкой
        // приложения (AD-30). Читающая сторона — контентный оркестратор, очередь не трогается.
        // Одни ворота на всё приложение: сброс курсоров и обычный проход не пересекаются.
        single<SyncGate> { SyncGate() }
        // Один переключатель строгости на все четыре журнала и на все четыре читателя (AD-11).
        // TOLERANT — пока backfill не привёл записанное к одной форме: старые формы разбираются,
        // непонятая запись считается и видна игроку числом. STRICT ставится ПОСЛЕ backfill и
        // ПОСЛЕ того, как обновившийся клиент уехал в прод (AD-13): в нём непонятая запись
        // останавливает проход, курсор остаётся на месте, и следующий проход читает ту же
        // страницу заново — вместо того чтобы выглядеть как «изменений нет».
        single<JournalReadPolicy> { JournalReadPolicy.TOLERANT }
        single<ForceResync> {
            ForceResync(
                syncStateRepo = get<SyncStateRepository>(),
                readSide = get<CatalogSyncListOrchestrator>(),
                // Те же ворота, что у читающей стороны: сброс курсоров и проход — одно неделимое
                // действие. Проход реентерабелен, поэтому вложенный `sync()` идёт насквозь.
                gate = get<SyncGate>(),
                // Исход решает тот же, кто решает его у планового прогона, и кладёт его под
                // собственным видом `RESYNC`. Пока ресинк писал успех сам, удавшееся
                // перечитывание каталога снимало ветвь упавшего планового прогона и ставило
                // поверх неё свежее время — очередь при этом стояла, а профиль не обновлялся.
                syncRun = get<SyncRun>(),
            )
        }
        single<EconomyConstantsStore> { PreferencesEconomyConstantsStore(androidContext()) }
        // Личные следы прежнего владельца вне очереди (AD-8): перечень закрыт и назван здесь —
        // ключи личных курсоров двух фич. Курсоры общего контента в него не входят.
        // Локальные траты, о которых сервер ещё не знает. Профиль вычитает это число из снимка,
        // иначе вернул бы игроку уже потраченные нолики: он потратил бы их второй раз, а откат
        // карантина добавил бы возврат поверх восстановленного баланса. Тело записи читает та
        // фича, которая его написала (AD-7); знать про обе стороны позволено только здесь.
        single<PendingLocalCharges> {
            val dao = get<UserStatsDao>()
            PendingLocalCharges { ownerUid ->
                dao
                    .undeliveredPayloads(ownerUid, LessonUnlockOutbox.OPERATION)
                    .sumOf { LessonUnlockOutbox.chargedNolicsOf(it) ?: 0L }
            }
        }
        single<OwnerTraces> {
            RoomOwnerTraces(get<OwnerRowsDao>()) { uid ->
                listOf(privateQuestSyncCursorId(uid), reviewAssignmentSyncCursorId(uid))
            }
        }
        single<AccountSwitchGuard> {
            AccountSwitchGuard(
                engine = get<OutboxEngine>(),
                store = get<OutboxStore>(),
                remainder = get<OutboxRemainderSource>(),
                traces = get<OwnerTraces>(),
            )
        }
        // След прогона в базе, а не в памяти процесса (история 4.2): «синхронизировались 20 минут
        // назад» — факт про аккаунт, и холодный старт его не отменяет. По строке на расписание:
        // содержимое, профиль и ручное «Перечитать всё» отвечают каждый за себя (AD-14).
        single<SyncRunTraceStore> { RoomSyncRunTraceStore(get<SyncRunTraceDao>()) }
        // Свой след очередь не пишет: успех — свойство всего прогона, и шаг, объявивший его сам,
        // стирал ветвь ошибки соседа по тому же прогону. Исход уезжает наверх результатом шага, а
        // записывает его [SyncRun] — один раз за прогон (AD-14).
        single<OutboxSyncable> {
            OutboxSyncable(
                engine = get<OutboxEngine>(),
                currentUidProvider = { get<AuthRepository>().currentUid() },
            )
        }
        single<WorkManager> { WorkManager.getInstance(androidContext()) }
        single<SyncScheduler> { WorkManagerSyncScheduler(get<WorkManager>()) }
        single<SyncPreferences> { SyncPreferences(androidContext()) }
        single<WorkerFactory> { SyncWorkerFactory(get(), get<ProfileSyncables>().value, get<SyncRun>()) }
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
                status = get<SyncStatusRepository>(),
                policy = get<JournalReadPolicy>(),
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
                // Иначе непонятые записи журнала урока пропадают так же молча, как до AD-11.
                status = get<SyncStatusRepository>(),
                policy = get<JournalReadPolicy>(),
            )
        }
        single<QuestPrivateSync> {
            QuestPrivateSync(
                local = get(),
                remote = get(),
                syncStateRepo = get<SyncStateRepository>(),
                currentUidProvider = { get<AuthRepository>().currentUid() },
                status = get<SyncStatusRepository>(),
                policy = get<JournalReadPolicy>(),
            )
        }
        single<ReviewAssignmentSync> {
            ReviewAssignmentSync(
                local = get(),
                remote = get(),
                syncStateRepo = get<SyncStateRepository>(),
                currentUidProvider = { get<AuthRepository>().currentUid() },
                status = get<SyncStatusRepository>(),
                policy = get<JournalReadPolicy>(),
            )
        }
        single<ProfileBootstrapSync> {
            ProfileBootstrapSync(
                repository = get<ProfileRepository>(),
                currentUidProvider = { get<AuthRepository>().currentUid() },
            )
        }
        single<QuestArenaOutcomeSync> {
            QuestArenaOutcomeSync(
                local = get(),
                remote = get(),
                timestampProvider = get(),
                currentUidProvider = { get<AuthRepository>().currentUid() },
            )
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
                // Таблица настроек экономики: цены и потолки должны меняться на сервере, без
                // релиза. Едет вместе с содержимым, а не с профилем, — она общая, не аккаунтная.
                object : Syncable {
                    private val step = get<EconomyConstantsSync>()

                    override suspend fun sync(): Result<Unit> = step.sync()
                },
                get<CatalogSyncListOrchestrator>(),
            )
        }
    }
