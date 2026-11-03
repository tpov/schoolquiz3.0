# Аудит синхронизации: документы, легаси, текущий код

Что синхронизация в приложении делает сегодня, что про неё написано в `docs/architecture/0004-sync-contract.md`,
и где эти две вещи не совпадают. Плюс то, что не написано нигде и живёт только в коде.

Написано так, чтобы следующий человек — или агент — начинал с верной карты. Каждое утверждение
привязано к `файл:строка`; там, где проверка была частичной, это сказано прямо.

Дата среза: 2026-08-30, ветка `noir/result-screen`, HEAD `342c7c2c`.

---

## 1. Карта: где что лежит

| Слой | Модуль | Содержимое |
|---|---|---|
| Контракты | `shared/core/sync` | `Syncable`, `SyncScheduler`, `SyncFrequency`, `SyncStateRepository`, оба оркестратора, `CatalogSyncChange` |
| Курсоры | `shared/core/persistence` | `RoomSyncStateRepository`, `SyncStateDao`, `LessonResultSyncOutboxEntity/Dao` |
| Пер-фичевый sync | `shared/feature/*/data/**/sync` | `LessonResultSync`, `QuestPrivateSync`, `ReviewAssignmentSync`, `QuestArenaSubmissionSync`, `ProfileBootstrapSync` |
| Firestore-адаптеры | `platform/firebase/**/sync` | `FirebaseCatalogSyncChangeRemoteDataSource`, `FirebaseLessonContentSyncChangeRemoteDataSource` |
| Планировщик | `platform/android-services/**/sync` | `SyncWorker`, `ProfileSyncWorker`, `WorkManagerSyncScheduler`, `SyncWorkerFactory`, `SyncPreferences` |
| Сборка графа | `apps/android-next/**/di/SyncModule.kt` | порядок syncables, обе каденции |
| Сервер | `functions/index.js` | все callable, запись `sync_changes`, начисления |
| Сервер (заявленный) | `server/workers/sync` | пусто: `.gitkeep` + `build.gradle.kts` |

---

## 2. Что синхронизация делает сегодня

### 2.1 Загрузка с сервера

Один канал: pull по журналу изменений с курсором в Room. Реального real-time нет.

| Что тянется | Кто тянет | Ключ курсора | Источник |
|---|---|---|---|
| Каталоги, квесты, секции, темы, уроки, вопросы | `CatalogSyncListOrchestrator` | `catalog_sync:{catalogId}` | `catalogs/{id}/sync_changes` |
| Вопросы урока по требованию | `LessonContentSyncOrchestrator` | `lesson_content:{lessonId}` | `lesson_content/{id}/sync_changes` |
| Приватные квесты автора | `QuestPrivateSync` | `private_quests:{uid}` | `private/{uid}/sync_changes` |
| Задания на ревью | `ReviewAssignmentSync` | `review_assignments:{uid}` | `admin/review/sync_changes` |
| Профиль | `ProfileBootstrapSync` | — | `ensureUserProfile` |
| Статистика игрока | `UserStatsRepositoryImpl` | — | `users/{uid}` |

Механика одна и та же: `whereGreaterThan("changedAtMs", cursor).orderBy("changedAtMs")`
([FirebaseCatalogSyncChangeRemoteDataSource.kt:21](platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/sync/FirebaseCatalogSyncChangeRemoteDataSource.kt:21)),
записи группируются по типу, для каждого типа зовётся `refreshByIds`, курсор двигается на
`max(changedAtMs)` — и только после успешного применения
([CatalogSyncListOrchestrator.kt:85-86](shared/core/sync/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/sync/CatalogSyncListOrchestrator.kt:85)).

`refreshByIds` — это и есть механизм удаления: id, который сервер не вернул, удаляется локально
([QuestRepositoryImpl.kt:47-49](shared/feature/quest/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/quest/data/QuestRepositoryImpl.kt:47)),
а пустой `visibleOn` означает «убрать с устройства»
([QuestRepositoryImpl.kt:128-131](shared/feature/quest/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/quest/data/QuestRepositoryImpl.kt:128)).

Запросы по id разбиты на чанки по 10 — лимит Firestore `whereIn` соблюдён
([FirestoreBatchFetch.kt:7](platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/util/FirestoreBatchFetch.kt:7)).

### 2.2 Отправка на сервер

Оффлайн-очередь есть ровно у трёх вещей:

| Что | Таблица | Кто отправляет | Идемпотентность на сервере |
|---|---|---|---|
| Прохождения уроков | `lesson_result_attempt_outbox` | `LessonResultSync` | да — по `attemptId`, повтор не оплачивается ([functions/index.js:363](functions/index.js:363)) |
| Оценки квестов | `quest_rating_outbox` | `LessonResultSync` | да — `set(..., merge)` по `ratingId` и хэшу uid ([functions/index.js:473](functions/index.js:473)) |
| Заявки квеста на арену | `quest_arena_submission` | `QuestArenaSubmissionSync` | не проверял |

Всё остальное — прямой вызов callable из репозитория, без очереди и без ретрая:
`claimNickname`, `buyLogo`, `applyShopPurchase`, `unlockLesson`, `openGiftBox`, `submitReviewAction`,
`setPublicQuestShelf`, `listNicknameForSale`, `buyListedNickname` и далее по `functions/index.js`.

### 2.3 Расписание

`SyncFrequency`: `MANUAL`, `ON_LAUNCH`, `DAILY`, `EVERY_3_DAYS`, `WEEKLY`; по умолчанию `DAILY`
([SyncPreferences.kt:31](platform/android-services/src/main/kotlin/com/tpov/schoolquiz/platform/android_services/sync/SyncPreferences.kt:31)).
Две независимые каденции — контент и профиль — обе перечитываются на каждом `onCreate`
([MainActivity.kt:42-45](apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:42)).
Ручной sync — из шторки ([DefaultRootComponent.kt:356](android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:356)).

`SyncWorker.performSync` намеренно не fail-fast: шаги независимы, один упавший не отменяет
остальные ([SyncWorker.kt:34-42](platform/android-services/src/main/kotlin/com/tpov/schoolquiz/platform/android_services/sync/SyncWorker.kt:34)).

---

## 3. Расхождения: ADR-0004 против кода

### D-1. Очереди исходящих мутаций не существует

**ADR**: правило 3 — «Все локальные мутации проходят через `OutgoingMutation` очередь. Прямой вызов
Firestore API из UI-слоя запрещён», плюс полная схема записи с `operation`, `payload`,
`expectedVersion`, `attemptCount`, `nextRetryAt`.

**Код**: `OutgoingMutation` и `MutationOp` не встречаются нигде — ноль совпадений по всему репозиторию.
Есть три узких очереди из §2.2, каждая под свой тип события, со своей таблицей и своим кодом отправки.

**Последствие**: правило нарушено не точечно, а системно. Покупка, ник, разблокировка урока,
действие ревьюера — всё это теряется без сети: нет очереди, нет повтора, нет следа. Оффлайн-first
заявлен для всего приложения, а обеспечен для прохождений и оценок.

### D-2. Оптимистичной блокировки нет, и `version` пишет клиент

**ADR**: «клиент шлёт мутацию с `expectedVersion`… мутация отклоняется → диалог "кто-то изменил это"»;
и `version: Long` — «инкрементируется сервером при каждом изменении».

**Код**: `expectedVersion` не существует. При публикации версию задаёт клиент — сервер пишет
`version: request.localRevision` и `contentsVersion: request.localRevision` во все узлы дерева
([functions/index.js:2290-2292](functions/index.js:2290)).

**Последствие**: два устройства одного автора перезаписывают друг друга молча. Диалог из ADR
построить не на чем — ни поля, ни серверной проверки.

### D-3. Каскад `contentsVersion` был реализован и заменён журналом — документ этого не заметил

**ADR**, Amendment 2026-04-21: рекурсивный `syncLevel(...)`, спуск на уровень ниже только там, где
вырос `contentsVersion`, early-exit на любом уровне.

**История**: каскад действительно построили — `CascadingSyncOrchestrator` (205 строк) плюс
`SyncLevel`, `CascadingSyncOrchestratorTest` и `CascadeSyncIntegrationTest` (464 строки тестов),
коммит `5140ae3b` от 2026-04-25. Через пять дней, в `1cb9f366` от 2026-04-30 («Finalize sync list
architecture»), всё это удалено и заменено на `CatalogSyncListOrchestrator`. **Решение о замене
принято в коде и никогда не попало в ADR** — Amendment так и стоит как действующий.

**Код**: клиент читает плоский журнал `sync_changes` и спуска по дереву не делает вовсе. До клиента
`contentsVersion` доезжает только у каталога ([CatalogDto.kt:8](shared/core/catalog/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogDto.kt:8))
и ни в одном решении не участвует. Сервер при этом честно пишет его для quest, section, theme, lesson
([functions/index.js:2292](functions/index.js:2292), [:2302](functions/index.js:2302), [:2316](functions/index.js:2316), [:2329](functions/index.js:2329)).

**Последствие**: поле поддерживается вхолостую с обеих сторон, а главный документ описывает алгоритм,
которого в приложении нет. Это самое дорогое расхождение в списке: любой, кто откроет ADR как карту,
пойдёт не туда.

### D-4. Tombstones не существует

**ADR**: `deleted: Boolean`, retention 30+ дней, серверный janitor.

**Код**: `deleted` нет. Удаление выражается двумя другими способами — флагом `archived` на документе
и отсутствием id в ответе `refreshByIds` (см. §2.1). Оба работают, оба нигде не описаны.

**Последствие**: правило ADR 4 про retention применять не к чему. Реальный контракт удаления
(«сервер не вернул — значит удали», «пустой `visibleOn` — значит удали») живёт только в коде и
ломается молча.

### D-5. `Syncable` в коде — не тот контракт, что в ADR

**ADR**: правило 1 — «Любая новая сущность, подлежащая синку, реализует `Syncable` (id + version +
updatedAt + deleted)», плюс `SyncStrategy` на сущность.

**Код**: `Syncable` — это `suspend fun sync(): Result<Unit>`
([Syncable.kt](shared/core/sync/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/sync/Syncable.kt)),
то есть интерфейс *джоба*, а не сущности. `SyncStrategy` не существует.

**Последствие**: правило 1 в текущем коде буквально невыполнимо — реализовать `Syncable` сущностью
означает дать ей метод `sync()`.

### D-6. Из трёх каналов pull работает один

**ADR**: Firestore listeners (< 1 сек), pull при старте и возврате из фона, FCM (< 10 сек) для
критических событий.

**Код**:
- Listeners — две точки, обе вне контракта sync: `FirebaseUserStatsDataSource.kt:46`,
  `FirebaseLessonCommentRepository.kt:23`. Как канал в `shared/core/sync` их нет.
- FCM — ноль. Ни `FirebaseMessagingService`, ни зависимости, ни записи в манифесте.
- Pull при возврате из фона — нет. Есть pull на `onCreate` при `ON_LAUNCH` и периодический воркер.

**Последствие**: «серверные автопромо полок видны юзеру за секунды» — обещание, которое сегодня
выполняется в лучшем случае раз в сутки, а при `WEEKLY` — раз в неделю. Критические события
(сертификат, оффер квалификации) не доезжают вовсе, что прямо задевает ADR-0006 и ADR-0007.

### D-7. Ретраев с backoff нет

**ADR**: 1s → 2s → 4s → … → 1 час, `attemptCount` и `nextRetryAt` в записи очереди.

**Код**: outbox хранит `last_error` и больше ничего
([LessonResultSyncOutboxEntity.kt:36](shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonResultSyncOutboxEntity.kt:36)).
Ретрай — это «следующий запуск воркера попробует снова», интервал задаёт WorkManager и настройка
игрока. `Result.retry()` отдаёт бэкофф WorkManager'у, но за весь пакет целиком.

### D-8. `server/workers/sync` пуст

ADR раздаёт модулю роль (периодические джобы, очистка tombstones, компакция). В репозитории —
`.gitkeep` и `build.gradle.kts`. Вся серверная синхронизация живёт в `functions/index.js`.
То же верно для `server/workers/rewards`, на который ссылаются ADR-0005 и ADR-0007.

### D-9. Журнал `sync_changes` не описан нигде, а свойства у него неочевидные

Это не «расхождение» в строгом смысле — про журнал в ADR нет ни слова. Но у него есть важное
свойство, которое стоит зафиксировать раньше, чем его случайно сломают.

Id документа — `${type}_${id}` ([functions/index.js:3922](functions/index.js:3922)), то есть журнал
хранит **последнее изменение на узел**, а не историю. Размер ограничен числом узлов, GC не нужен.
Именно поэтому tombstone-retention из ADR здесь не к месту.

А сид-скрипты пишут туда же по другой схеме — `${changedAtMs}-${type}-${id}`
([seed-hierarchy.js:100](scripts/seed-hierarchy.js:100), [seed-lesson-runner-samples.js:348](scripts/seed-lesson-runner-samples.js:348)),
то есть создают append-лог. Сид-данные растут неограниченно и ведут себя не как прод.

---

## 4. Расхождения: ADR-0004 против легаси

### L-1. Про пустой rollback ADR прав

`SettingLocalDBUseCase.rollbackStructureData` — пустое тело
([SettingLocalDBUseCase.kt:9-11](legacy/common/src/main/java/com/tpov/common/domain/usecase/SettingLocalDBUseCase.kt:9)).
При этом легаси-воркер зовёт его в трёх ветках обработки ошибок
([SyncWorker.kt:152](legacy/app/src/main/java/com/tpov/schoolquiz/presentation/SyncWorker.kt:152), :160, :167).
Обработка ошибок была декоративной.

### L-2. Формулировка про конфликты смешивает два разных механизма

ADR: «last-write-wins на основе `dataUpdate` timestamp'а и `version: Int`».

Фактически это два разных сравнения в разных местах. Структуры сравниваются **только по версии**:
`remoteVersion > localVersion` ([StructureDataUtils.kt:36-39](legacy/common/src/main/java/com/tpov/common/domain/utils/StructureDataUtils.kt:36)).
Таймстемпы `dataUpdateLocal/Global` участвуют только в списках инфо-изменений
([StructureDataExtention.kt:569](legacy/common/src/main/java/com/tpov/common/domain/usecase/StructureDataExtention.kt:569), :588).

### L-3. Двусторонность легаси, от которой отталкивается ADR, в рабочем пути не выполнялась

`updateRemoteQuestion` ходит по `changedListQuestionRemote`
([StructureDataExtention.kt:309](legacy/common/src/main/java/com/tpov/common/domain/usecase/StructureDataExtention.kt:309)).
Этот список наполняется единственным местом — `syncChangeListQuestionsRemote`, целиком обёрнутым в
`if (this.eventId == EventQuiz.QUIZ_BY_USER)` ([:209](legacy/common/src/main/java/com/tpov/common/domain/usecase/StructureDataExtention.kt:209)).
А воркер этот event пропускает первым же условием цикла:
`if (event == EventQuiz.QUIZ_BY_USER) continue` ([SyncWorker.kt:100-101](legacy/app/src/main/java/com/tpov/schoolquiz/presentation/SyncWorker.kt:100)).

То есть загрузка вопросов пользователя на сервер из фонового sync никогда не срабатывала.
Это стоит знать, прежде чем ссылаться на легаси как на образец двустороннего обмена.

---

## 5. Дефекты и риски в текущем коде

### B-1. Одно отвергнутое прохождение навсегда блокирует отправку оценок

`sync()` вызывает `syncAttempts()`, затем `syncRatings()`. `syncAttempts()` перебрасывает исключение
наверх ([LessonResultSync.kt:45](shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/sync/LessonResultSync.kt:45)),
поэтому `syncRatings()` не выполняется вообще.

**Сценарий**: одна запись, которую сервер устойчиво отвергает, — и ни одна оценка квеста больше не
уходит с устройства. Счётчика попыток нет, карантина нет, запись остаётся в выборке `pendingAttempts`
вечно.

### B-2. Курсоры не сбрасываются ничем и никогда

`RoomSyncStateRepository` умеет только `getCursor`/`setCursor`. Нет сброса при смене аккаунта, при
миграции схемы, при явном «полном ресинке». Единственный форс — хардкод для каталога `courses`, когда
полка archive пуста ([CatalogSyncListOrchestrator.kt:90-92](shared/core/sync/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/sync/CatalogSyncListOrchestrator.kt:90)).

**Сценарий**: локальная строка потерялась (миграция, сбой, ручная чистка), курсор остался впереди —
данные не вернутся никогда, кроме как переустановкой приложения. Для журнала «последнее изменение на
узел» это особенно неприятно: повторной записи в журнал не будет, пока автор не тронет узел.

### B-3. Outbox не привязан к аккаунту в момент отправки

`pendingAttempts` выбирает по `sent_at_ms IS NULL` без фильтра по `user_id`
([LessonResultSyncOutboxDao.kt:20-27](shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonResultSyncOutboxDao.kt:20)),
а сервер атрибутирует событие вызывающему uid — `normalizeLessonResultAttemptEvent(item, uid)`
([functions/index.js:316](functions/index.js:316)).

**Сценарий**: смена аккаунта при непустой очереди — прохождения игрока A начисляются игроку B.
Сегодня это латентный дефект, а не активный: продового пути выхода из аккаунта в приложении нет
(`signOut` встречается только в тестовых фейках и инструментальном тесте). Он станет активным ровно в
тот день, когда logout появится — и это же ровно инвариант 8 из `docs/invariants.md`.

### B-4. `ON_LAUNCH` для профиля запускает контентный sync, а не профильный

При `storedProfileFrequency == ON_LAUNCH` вызывается `enqueueManualSync()`
([MainActivity.kt:44-45](apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:44)),
который ставит `SyncWorker` со **всем** контентным списком, а не `ProfileSyncWorker`. Обратное тоже
верно: выбор `ON_LAUNCH` в пикере профиля не запускает ничего
([MainActivity.kt:76-79](apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:76)),
хотя контентный пикер немедленный sync запускает.

### B-5. Выборка изменений не ограничена

`fetchChangedSince` не ставит `limit` и не пагинируется. При полной перепубликации большого курса или
после `backfill-sync-changes.js` это один запрос на весь изменившийся набор, а затем
`refreshByIds` чанками по 10 — то есть сотни последовательных round-trip'ов внутри одного
`doWork()`. Не ошибка корректности, но потолок, о который упрутся именно на большом курсе.

### B-6. Курсор двигается строго по `max(changedAtMs)`, условие выборки строгое

Читается `changedAtMs > cursor`, курсор ставится в максимум прочитанного. Публикация пишет всем узлам
один и тот же `now` одним батчем, поэтому расщепление одной публикации маловероятно. Но защиты от
двух записей в одну миллисекунду из разных источников (админ-тул и публикация) нет — вторая будет
пропущена без следа. Риск, а не наблюдаемый баг; лечится курсором вида `(changedAtMs, docId)` или
перекрытием окна.

### B-7. `Result.retry()` возвращается за весь пакет

При частичном успехе успешные шаги выполняются заново. Для pull это безвредно, для push — опирается
на серверную идемпотентность, которая есть у прохождений и оценок (§2.2) и не проверена у заявок на арену.

---

## 6. Что легаси делал, а новый код не делает

| Легаси | Сегодня |
|---|---|
| Уведомление о завершении sync ([SyncWorker.kt:175](legacy/app/src/main/java/com/tpov/schoolquiz/presentation/SyncWorker.kt:175)) | Ничего — sync полностью безмолвен, кроме `Log.w` |
| `SyncStage` — наблюдаемая стадия процесса | Стадий нет, состояния наружу нет вовсе |
| Синхронизация настроек в профиль (`syncSettings`) | Не переносилось |
| Загрузка картинок структуры внутри sync (`fetchPictureStructure`) | URL резолвится лениво в репозитории, отдельного шага нет |
| Блокировка сервера на время sync | Убрана сознательно (так и записано в ADR) |

Строка «уведомление» здесь не ностальгия: сегодня у игрока нет **никакого** признака, что sync шёл,
шёл ли он вообще и когда был последний успешный. Это же делает недиагностируемыми B-1 и B-2.

---

## 7. Что должен закрыть SPEC

1. **Журнал изменений или каскад версий?** `sync_changes` работает и дёшев; каскад `contentsVersion`
   описан в ADR и наполовину оплачен на сервере. Одно из двух должно уйти.
2. **Кто назначает `version`?** Сегодня клиент. ADR говорит — сервер. От ответа зависит, возможна ли
   вообще оптимистичная блокировка.
3. **Одна очередь мутаций или три специализированных?** Обобщать `payload: JsonElement` с валидацией
   на сервере — или оставить типизированные очереди и завести четвёртую под покупки.
4. **Нужен ли FCM сейчас?** Без него ADR-0006 (офферы квалификаций) и ADR-0007 (сертификаты) не
   выполнимы в заявленной задержке. Это отдельный объём с настройкой платформы.
5. **Что игрок видит про sync?** Время последнего успеха, размер очереди, «не отправлено» на
   результате. Сегодня — ничего.
6. **Что происходит при смене аккаунта?** Сброс каких курсоров, судьба непустой очереди. Отвечать
   до того, как появится logout, а не после.
7. **Как sync уживается с server-authoritative сессией экзамена** из
   `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/` — E2 убирает
   ответы hard-вопросов с устройства, то есть у части контента меняется сам смысл «синхронизировать».

---

## 8. Что править в ADR-0004 после SPEC

Список правок, а не сами правки — они следуют из ответов §7.

- Amendment 2026-04-21 (каскад `contentsVersion`) — либо пометить как нереализованный и заменить
  описанием журнала `sync_changes`, либо оставить как план и явно сказать, что код ему не следует.
- Раздел «Схема полей» — привести к тому, что действительно пишется: `version`, `contentsVersion`,
  `lastModifiedAt`, `archived`, `visibleOn`. Убрать `deleted`, `createdBy` или обосновать их появление.
- Правило 1 (`Syncable` на сущность) — переписать под фактический смысл интерфейса или ввести
  отдельное имя для контракта сущности.
- Правило 3 (все мутации через очередь) — либо превратить в план с датой, либо честно сузить до
  прохождений и оценок. Сегодня оно читается как описание существующего положения, а это не так.
- Правило 4 (retention tombstones) — снять или переформулировать: журнал «последнее изменение на узел»
  в GC не нуждается.
- Таблица трёх каналов — отметить, что реализован один, и на что это влияет.
- Роли `server/workers/sync` и `server/workers/rewards` — отметить как незанятые; сказать, что
  фактический сервер это `functions/index.js`.
- Добавить то, чего в документе нет вовсе: схема id в `sync_changes`, семантика удаления через
  отсутствие id и пустой `visibleOn`, две каденции из настроек, порядок syncables в `SyncModule`
  и почему профиль в нём стоит дважды.
