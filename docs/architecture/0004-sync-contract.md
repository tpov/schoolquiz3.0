# ADR-0004: Sync-контракт между клиентом и сервером

## Status
Accepted — 2026-04-16 (базовые принципы зафиксированы; отдельные параметры помечены как TBD, уточняются по мере реализации)

## Context

В legacy синхронизация была **client-driven**: одна 8-этапная цепочка в `SyncInteractor` (см. `SyncStage`), last-write-wins на основе `dataUpdate` timestamp'а и `version: Int`. Запуск — `WorkManager.OneTimeWorkRequest` на старте приложения через `SyncWorker`. Блокировка сервера (`lockServer(event)` с 10 retry × 2 сек). Rollback `rollbackStructureData` заявлен, но реализация пустая. Delta sync через `ChangeVersionStructure`.

Что изменилось в новой архитектуре:

1. **Много серверной логики** (см. ADR-0005): автопромо полок, обработка `CompletionEffect`, выдача сертификатов, серверная защита квалификаций. Значит sync становится **двусторонним** — сервер сам двигает сущности, клиент должен это увидеть.
2. **Полки квестов** (ARENA → TOURNAMENT → HOME) меняются на сервере без участия клиента — нужен real-time или регулярный pull.
3. **Критические события** (оффер квалификации, новый сертификат) должны прилетать даже когда приложение закрыто.
4. Клиент может быть offline — изменения юзера (новый квест, прохождение) должны долго храниться локально и синкаться когда связь появится.

## Decision

### Основные принципы (наследуются из legacy + улучшения)

1. **Offline-first / write-local-first.** Все локальные изменения пишутся в Room/SQLDelight первым, sync — второй приоритет. Юзер никогда не ждёт сеть на UI-действиях.
2. **Delta sync.** Передаются только изменения, не весь датасет. Каждая сущность имеет `version: Long` и `updatedAt: Instant`.
3. **Per-entity timestamps + version** (а не «одна версия структуры» как в legacy). Каждый Quest, Question, Profile, Certificate, Qualification имеет свои `version`/`updatedAt`.
4. **Tombstones для удалений.** Вместо физического удаления запись помечается `deleted: true`. При следующем sync tombstone мэпится в peer'ов. Сбор мусора — отдельная серверная задача (retention > 30 дней).
5. **No hard rollback.** Если sync не удался — retry с exponential backoff; локальные изменения сохраняются в pending queue до восстановления. Принудительного отката работы юзера **не делаем** — это потеря данных.

### Двусторонний sync

**Локальное → удалённое (client push):**

- Изменения юзера (созданный квест, прохождение, правка) пишутся в local DB + в отдельную таблицу `OutgoingMutation` как операция (`CREATE | UPDATE | DELETE | COMPLETE_QUEST | ...`).
- Background worker (`SyncWorker` в `platform/android-services`) периодически флашит очередь на сервер.
- При успехе — удаляет мутацию из очереди; при ошибке — retry с exponential backoff (1s → 2s → 4s → ... до 1 часа), не теряет данные.
- Серверный handler (`server/functions` + `server/workers/sync`) принимает мутации, валидирует (квалификации, CompletionEffect, серверная защита), применяет атомарно.

**Удалённое → локальное (server push):**

Три канала, разные приоритеты:

| Канал | Использование | Задержка |
|---|---|---|
| **Firestore real-time listeners** | Активные экраны, где нужна мгновенная реакция (список квестов, профиль юзера, активный турнир). Подписка пока экран открыт. | < 1 сек |
| **Pull at startup + app foreground** | Некритичные сущности (каталог курсов, лидерборды). Синкаются при старте и при возврате из background'а. | До момента открытия |
| **FCM push** | Критические события: оффер квалификации, выдан сертификат, твой квест продвинулся в TOURNAMENT, сообщение от админа. Прилетает даже если приложение закрыто. | < 10 сек |

Листенеры регистрируются/снимаются на уровне presentation-слоя через контракт в `shared/core/sync`.

### Разрешение конфликтов

**Last-write-wins** на основе серверного `updatedAt` (как в legacy), с двумя улучшениями:

1. **Семантический мёрдж, где применимо.** Например, для `QuestChecks` (три параллельных флажка) клиент и сервер могут менять разные поля (`content`, `translation`, `logic`) — тогда мёрджим per-field вместо LWW всего объекта.
2. **Серверная валидация вместо доверия клиенту.** Поля, защищённые серверно (см. ADR-0006: квалификации; ADR-0007: сертификаты; ADR-0005: `CompletionEffect`), игнорируют клиентскую версию — только сервер пишет.

**Оптимистичная блокировка:** клиент шлёт мутацию с `expectedVersion`. Если на сервере уже `version > expectedVersion`, мутация отклоняется → клиент применяет серверную версию и показывает юзеру диалог «кто-то изменил это» (только для редких коллизий, большинство мутаций проходит).

### Схема полей синхронизируемых сущностей

Каждая сущность, подлежащая синку, имеет базовый набор:

```
id: String             // стабильный UUID, назначается клиентом при создании
version: Long          // инкрементируется сервером при каждом изменении
updatedAt: Instant     // серверное время последнего изменения
deleted: Boolean       // tombstone-флаг
createdBy: UserId      // кто создал (для защиты и логов)
```

Локальная мутация в очереди добавляет:

```
localId: Long           // PK в очереди
entityId: String        // id сущности
operation: MutationOp   // CREATE | UPDATE | DELETE | COMPLETE_QUEST | SUBMIT_RATING | ...
payload: JsonElement    // сериализованное изменение
expectedVersion: Long?  // для оптимистичной блокировки
attemptCount: Int       // для backoff
nextRetryAt: Instant
```

### Sync-этапы (упрощённые vs legacy)

Убираем жёсткую 8-этапную цепочку. Каждая **сущность** синкается независимо (per-entity sync):

1. Заказ серверных изменений — подписка на Firestore listener или pull-запрос с `since = lastUpdatedAt`.
2. Мёрдж серверных изменений в local DB (LWW / семантический мёрдж).
3. Флаш `OutgoingMutation` очереди — по одной мутации, с проверкой `expectedVersion`.
4. При конфликте — применяем серверную версию, инвалидируем клиентскую мутацию, поднимаем UI-уведомление если нужно.

Параллелизуется: профиль, квесты, квалификации, сертификаты синкаются независимо. Блокировка `lockServer(event)` из legacy отменяется — серверные транзакции атомарны сами по себе (Firestore transactions / server function с compare-and-swap).

### Роль модулей

- `shared/core/sync` — контракты: `Syncable` интерфейс (id + version + updatedAt + deleted), `MutationOp` enum, `OutgoingMutation` data class, `SyncStrategy` для стратегии каждой сущности.
- `shared/core/persistence` — общие примитивы local DB (как это реализовать в KMP — см. будущий ADR по persistence, выбор между Room/SQLDelight).
- `shared/feature/*/data` — репозитории каждой фичи, реализуют `SyncStrategy` для своих сущностей.
- `platform/firebase` — Firestore listeners, FCM push-handling.
- `platform/android-services` — `SyncWorker` на WorkManager, триггеры sync'а.
- `server/functions` — валидация и применение мутаций от клиента.
- `server/workers/sync` — периодические джобы (например, очистка старых tombstones, компакция истории).
- `server/workers/rewards` — применение `CompletionEffect` (выдача сертификатов, офферы квалификаций).

## Consequences

### Плюсы
- **Offline-first без потери данных.** Юзер может создавать квесты и проходить их без сети — всё улетит при восстановлении.
- **Реальная двусторонность.** Серверные автопромо полок становятся видны юзеру в секунды через Firestore listeners, а не только при рестарте.
- **Per-entity sync** устраняет главную проблему legacy — сбой на одном этапе ломал всю цепочку. Теперь профиль и квесты синкаются независимо.
- **Оптимистическая блокировка** защищает от серебряных ошибок "два устройства одного юзера" без жёстких лок-файлов.
- **FCM для критических событий** — юзер узнаёт о новом сертификате сразу, даже с закрытым приложением.

### Минусы
- **Инфраструктурная сложность выше legacy.** Listeners + pull + push — три канала, каждый со своей логикой подписки/unsubscribe.
- **FCM требует настройки** (сертификаты, конфиг платформы). Для iOS в будущем — APNs через FCM.
- **Tombstones + retention** — надо следить за объёмом данных. Серверный janitor раз в N дней удаляет tombstones старше 30 дней.
- **Оптимистическая блокировка может давать редкие «откаты»** — юзер попытался изменить, а кто-то его опередил. Ожидаемое поведение, но требует UI-обработки.

### Правила
1. Любая новая сущность, подлежащая синку, реализует `Syncable` контракт (id + version + updatedAt + deleted).
2. Клиент **никогда не пишет** поля, защищённые сервером (квалификации, сертификаты, promo полок). Даже локально — эти поля read-only для UI.
3. Все локальные мутации проходят через `OutgoingMutation` очередь. Прямой вызов Firestore API из UI-слоя запрещён.
4. Retention tombstones — минимум 30 дней, конфигурируется.
5. Для каждой фичи выбирается канал sync'а (listener / pull / push) в зависимости от требований UX и стоимости.

## Mapping из legacy

| Legacy | Новый эквивалент |
|---|---|
| `SyncInteractor` 8-step cycle | `SyncStrategy` per-entity, параллельно |
| `lockServer(event)` + `rollbackStructureData` | Оптимистическая блокировка + retry очередь |
| `dataUpdateGlobal / dataUpdateLocal` | `updatedAt` + `version` per-entity |
| `ChangeVersionStructure` (isCreate flag) | `OutgoingMutation` очередь с `MutationOp` |
| `SyncWorker` OneTime при старте | `SyncWorker` + Firestore listeners + FCM |
| `isDownload: Boolean` | Read-through cache в repository |
| `dataUpdateLocal == "-1"` (удаление) | `deleted: Boolean` tombstone |

## TBD (уточнения на этапе реализации)

- Конкретный backend: оставляем Firestore из legacy или мигрируем на свой API? Влияет на `platform/firebase` и `server/functions`.
- Библиотека local DB: Room (Android-only) или SQLDelight (KMP-ready). Отдельный ADR по `shared/core/persistence`.
- Формат `MutationOp.payload`: JSON (kotlinx-serialization) или Protobuf. JSON проще отлаживать, Protobuf компактнее.
- Retention tombstones: точное число дней.

## Notes

Контракты живут в `shared/core/sync/src/commonMain/kotlin/`. Реализации стратегий — в `shared/feature/*/data` (per-feature). Платформенная часть — `platform/firebase` (Firestore/FCM) и `platform/android-services` (WorkManager). Серверная — `server/functions`, `server/workers/sync`, `server/workers/rewards`.
