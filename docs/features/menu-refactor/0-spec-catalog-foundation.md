---
date: 2026-04-19
feature: menu-refactor / catalog-foundation
type: new-feature
commit: 7c52c200
parent: 0-spec.md
---

# Sub-spec: Catalog Foundation

Cross-feature справочник "каталогов" — плоский список именованных категорий (Опросы, Курсы, Игры, Школа), подгружаемых из Firebase. Каждый квест принадлежит одному каталогу. UI-представление варьируется: spinner на "Мои квесты", grid-with-pictures на "Домашние квесты". Изменения каталогов — только серверные, клиент read-only.

## Source

- Пользовательский запрос:
  > "начал с экрана мои квесты, посмотри на легаси там есть разные каталоги, курсы, опросы и т д, есть что-то об этом?"
- Уточнения в диалоге:
  > "каталог, каталоги будут встречаться на многих экранах, на данном этапе в пункте домашние квесты и мои квесты"
  > "я думаю просто сделать спиннер"
  > "это будет отдельная сущность"
  > "каталоги одни и те же на всех экранах"
  > "но у каталога есть свои квесты в легаси это субкатегоии, прмеры каталогов опросы, курсы, игры, школа"
  > "они есть на многих экранах и все одинаковы, они кстати подгружаются с фаербейза, забыл сказать"
  > "содержимое то есть квесты разные"
  > "спиннет это жругие экраны, в данном случае мои квесты, а на домашние там в два ряда с картинками"
  > "при первом открытии, все остальное через ворк менеджер, для разрботчика добавь кнопку синхронизация в меню"
  > "сортировку не надо, без разницы пока что"
  > "только оригинальный язык"

- Type: new-feature

## Requirements

### Functional Requirements

1. Доменная сущность `Catalog(id: CatalogId, name: String, picturePath: String?)` в `shared/core/catalog/domain/src/commonMain/kotlin/` — [USER DECIDED] "отдельная сущность"
2. `CatalogId` — value class обёртывает String ID из Firestore — [DELEGATED: type safety]
3. Плоская модель (нет parent/children), нет иерархии — [USER DECIDED] "Плоские (без parent)"
4. Одноязычный `name: String` (MVP) — [USER DECIDED] "только оригинальный язык"
5. `picturePath: String?` — **относительный Firebase Storage path** (например `"catalog-pictures/surveys.jpg"`), хранится в самом Firestore документе Catalog. Nullable — не у каждого каталога есть картинка. Resolve URL делается клиентом через `FirebaseStorage.getReference(path).downloadUrl` — [USER DECIDED] "путь картинки в самом обьекте каталога который приходит с фаербейза" + "Относительный Storage path"
6. `Quest.catalogId: CatalogId` — обязательный (non-null) — [USER DECIDED] "Обязательный (non-null)"
7. **Стабильный порядок (Codex fix #6)**: "без custom сортировки" — [USER DECIDED] "сортировку не надо". Но для детерминизма (иначе Firestore/Room non-deterministic и тесты flaky) — сортировка по `Catalog.id.value` ASC на стороне клиента (после fetch). Это даёт стабильный порядок без добавления новых полей в domain и без order field в Firestore. Firestore `orderBy(FieldPath.documentId())` + local list sorted — consistent
8. Repository `CatalogRepository` в `shared/core/catalog/domain/repository/` — [DELEGATED: clean-architecture pattern]
   - `fun observeAll(): Flow<List<Catalog>>` — локальный кеш
   - `suspend fun refreshFromRemote(): Result<Unit>` — pull from Firestore
9. Синхронизация [UPDATED IN RESEARCH 2026-04-20]:
   - **Первая загрузка**: при первом запуске приложения / первом обращении к списку каталогов — pull из Firestore — [USER DECIDED] "при первом открытии"
   - **Периодическая**: один из шагов внутри общего `SyncWorker` (WorkManager `CoroutineWorker`). Periodicity — user-configurable (legacy values 1/2/3/4/7/14/30 дней), default 1 день — [USER DECIDED "посмотришь в легаси"]
   - **Manual trigger**: `DrawerFooterAction.SyncNow` в drawer footer, видна только при `isDebugBuild || stats.qualification.developer >= LEVEL_1.points` (после revert codex fix #2 — прямо из `UserStats.qualification.developer`, без overlay) — [USER DECIDED] "для разработчика добавь кнопку синхронизация в меню"
   - **Trigger architecture**: `RootComponent.onSyncNow()` method + `RootEvent.SyncStarted` event для Snackbar. DefaultRootComponent enqueue-ит `WorkManager.enqueueUniqueWork("manual_sync", REPLACE, oneTimeSyncWorkRequest)` + emits `RootEvent.SyncStarted`. UI (AppShellScreen) collect-ит events Flow для показа "Синхронизация запущена" — [USER DECIDED Q3]
10. UI-компоненты (**foundation-only — создаём компоненты, не интегрируем в screens** — Codex fix #8) в `android/core/designsystem/` — [DELEGATED: переиспользуемый компонент для cross-feature]:
    - `CatalogSpinner` — Material3 exposed dropdown menu; default selected = "Все категории" (псевдо-пункт)
    - `CatalogGrid` — 2-column LazyVerticalGrid; карточка = picture (Coil/Glide) + name; без rating/stars
    - Оба компонента — **standalone**, с Preview, принимают `List<Catalog>` + callbacks. Применение на конкретных экранах (MyQuests, HomeQuests, Arena, ...) — в **отдельных** future specs "screen integration"
11. `DrawerFooterAction.SyncNow` — новый footer action, добавляется в sealed set в `shared/feature/app-shell/domain/model/DrawerFooterAction.kt` (owned app-shell). Visibility rules зафиксированы в `menu-refactor/0-spec-dev-mode.md` "Footer Contract" (combined matrix). catalog-foundation — consumer contract, не owner — [USER DECIDED] + Codex fix #7

### Non-Functional Requirements

1. **Domain purity**: `Catalog` и `CatalogId` — pure Kotlin, без Android/SDK/DI аннотаций — [DELEGATED: invariant 1]
2. **KMP-совместимость**: `commonMain` source set — [DELEGATED: PROJECT_STRUCTURE §4]
3. **Изображения**: использовать существующую библиотеку загрузки (Coil/Glide, какая уже в проекте) — [DELEGATED: consistency, research определит]
4. **Offline fallback**: при отсутствии сети показывать last-known кеш — [DELEGATED: standard offline-first pattern ADR-0004]
5. **Loading state**: пока каталоги не загружены при первом запуске — spinner / skeleton — [DELEGATED: UX best practice]

## Scope

### In Scope
- Новый KMP-модуль `shared/core/catalog/` с submodules:
  - `domain` — `Catalog`, `CatalogId`, `CatalogRepository`, `ObserveCatalogsUseCase`
  - `data` — `CatalogRepositoryImpl`, `CatalogLocalDataSource` (Room), `CatalogRemoteDataSource` (Firestore), `CatalogEntity`, mappers
- **Quest delta contract (Codex fix #9)** — минимальное owned расширение quiz domain:
  - Если `shared/feature/quiz/domain/model/Quest.kt` **не существует** — создать minimal placeholder только с `id: QuestId, catalogId: CatalogId, title: String` (остальные поля — в последующих фичах); owner — catalog-foundation spec
  - Если существует — **добавить** non-null поле `catalogId: CatalogId` (не заменять existing model целиком)
  - Invariant: `catalogId` при создании quest проверяется через `CatalogRepository.getById(id) != null`. Если catalog не существует — throw `IllegalArgumentException("Unknown catalogId: $id")`
  - Use case `CreateQuestUseCase(questRepo, catalogRepo)` — thin wrapper: валидирует `catalogId`, сохраняет quest
  - Tests: `QuestCreateValidatesCatalogIdTest` (через fakes обоих repositories), `QuestCatalogIdNonNullTest` (domain init)
  - **Scope boundary**: catalog-foundation owns `catalogId` поле и create-time validation. Остальные quest concerns (QuestType, QuestPhase, PublicationShelf, CompletionEffect per ADR-0005) — **не** catalog-foundation owner; они добавляются quiz-lifecycle фичами отдельно
- UI-компоненты в `android/core/designsystem/`:
  - `CatalogSpinner.kt` + preview
  - `CatalogGrid.kt` + preview
  - `CatalogGridItem.kt` (single card)
- Koin DI модули для catalog (data + domain)
- Firebase integration:
  - Firestore rules (`firestore.rules`) — read public, write только admin-whitelist (см. ADR-0006)
  - Firestore collection `catalogs/{catalogId}` schema
  - Firebase Storage bucket `catalog-pictures/{catalogId}.jpg` — публичный read
- WorkManager integration:
  - Catalog sync как step в общем SyncWorker (либо reuse existing worker, либо create new)
  - Dev-mode manual trigger через footer action `DrawerFooterAction.SyncNow`
- Tests:
  - Domain: `CatalogTest`, `CatalogRepositoryContractTest` (через FakeCatalogRepository)
  - Data: `CatalogLocalDataSourceTest` (Room in-memory), `CatalogMapperTest`
  - UI: Compose preview + basic interaction tests для Spinner/Grid
- Firestore rules tests (если есть test harness)

### Explicitly Out of Scope
- Иерархия каталогов (parent/children) — [USER DECIDED] "плоские"
- Многоязычность — [USER DECIDED] "только оригинальный язык"; расширение через `Map<LangCode, String>` — future work
- Сортировка — [USER DECIDED] "без разницы пока что"
- Rating / stars / numQ counters (как в legacy StructureDataLocal) — [USER DECIDED] "MVP: только id + name + picture"
- Search vectors — legacy feature, не в MVP
- Migration story из legacy — [USER DECIDED] "проект pre-production"
- Client-side catalog CRUD — только admin-tools через серверные операции
- Caching tuning (TTL, LRU) — стандартная Room persistence + manual refresh; optimizations — future work
- **Применение `CatalogSpinner`/`CatalogGrid` на конкретных экранах** (MyQuests, HomeQuests, Arena, Validation) — это отдельные фичи "screen integration"; эта spec только создаёт building blocks + pipeline + domain + data. UI-integration — потом. (Codex fix #8 — scope сузил, убрал FR "Мои квесты используют Spinner" из in-scope)
- UI загрузки / ошибок на нестандартных экранах (каждый screen ViewModel сам решает) — out of scope
- Admin creation tool — отдельная фича (не для клиента приложения)

## User Decisions

| # | Question | Answer | Impact |
|---|----------|--------|-------|
| 1 | Каталог = таксономия или коллекция? | Плоская таксономия (subCategory level legacy) | Simple `Catalog(id, name)` без parent |
| 2 | Иерархия? | Плоские, без parent | Нет tree UI, простой spinner/grid |
| 3 | Курсы: QuestType.COURSE vs catalog="курсы"? | Ортогональные оси, оба остаются | ADR-0005 не меняется; convention catalog='курсы' ⇔ type=COURSE, но не hard rule |
| 4 | Quest.catalogId — обязательный? | Обязательный (non-null) | Domain invariant — при создании квеста catalog обязателен |
| 5 | Spinner default? | "Все категории" | Pseudo-item "All", clear semantics |
| 6 | Многоязычный? | Только оригинальный язык (MVP) | `name: String` (не Map) |
| 7 | Sync стратегия? | Первое открытие + общий WorkManager + dev кнопка | Catalog sync как step в общем workflow; dev-only manual trigger |
| 8 | Picture storage? | Firebase Storage + путь в Firestore (относительный) | `picturePath: String?`; URL резолвится клиентом через `StorageReference.downloadUrl` |
| 9 | Доп поля? | Только id + name + picture | Минимальная сущность |
| 10 | Dev sync кнопка где? | DrawerFooter (видна при dev mode) | Новый `DrawerFooterAction.SyncNow` |
| 11 | Legacy migration? | Не нужна (pre-production) | Firestore заполняется admin-ом с нуля |
| 12 | UI на "Мои квесты" vs "Домашние"? | Spinner на Мои, Grid на Домашние | Два компонента в designsystem |
| 13 | Сортировка? | Не нужна | Firestore order preserved |
| 14 | Rating на карточках Grid? | Нет в MVP | Без rating/stars; простой picture+name |

## Server-Side Context

### Firestore schema

**Collection**: `catalogs` (top-level)

**Document**: `{catalogId}` — например `catalogs/surveys`, `catalogs/courses`

**Fields**:
```json
{
  "name": "Опросы",
  "picturePath": "catalog-pictures/surveys.jpg",
  "order": 0,
  "createdAt": Timestamp,
  "updatedAt": Timestamp
}
```

- `picturePath` — относительный Storage path (не полный URL). Клиент резолвит в URL через `FirebaseStorage.getInstance().reference.child(picturePath).downloadUrl.await()`. **Domain** знает только про `picturePath` (relative); **URL** — infrastructure artifact, резолвится в image loader (Coil custom fetcher для Storage paths, или Glide `FirebaseImageLoader`), не в domain repository — **TBD design phase** (какой loader). Domain НЕ содержит поле `pictureUrl` (Codex fix #5).
- Fields `order`, `createdAt`, `updatedAt` — optional в MVP; `order` не используется т.к. сортировка TBD; timestamps — для будущего conflict resolution по ADR-0004.

### Firebase Storage schema

**Bucket**: default Firebase Storage bucket приложения

**Path convention**: `catalog-pictures/{catalogId}.{ext}` — `jpg`, `png`, `webp`. Точный path приходит из Firestore `Catalog.picturePath` field (клиент не хардкодит paths).

**ACL**: public read (для любого клиента, даже неавторизованного)

**Resolve flow**:
```
Catalog (Firestore) → picturePath = "catalog-pictures/surveys.jpg"
                   → FirebaseStorage.getReference("catalog-pictures/surveys.jpg")
                   → .downloadUrl → "https://firebasestorage.../?token=..."
                   → Coil/Glide load URL
```

### Auth / rules

`firestore.rules` дополнение:
```js
match /catalogs/{catalogId} {
  allow read: if true;                           // все могут читать
  allow write: if request.auth != null           // записывать только:
    && get(/databases/$(database)/documents/users/$(request.auth.uid)).data.qualifications.admin >= 100;
}
```

Validation server-side: `name != null && name != ""`, `picturePath` non-empty и не содержит `"https://"` / `"gs://"` prefix если non-null.

### Side effects

- При создании catalog админом — Firestore document создаётся; клиенты на следующем sync видят новый каталог
- При обновлении `picturePath` — клиенты на следующем sync тянут новую картинку (image loader резолвит свежий downloadUrl из Firebase Storage)
- При удалении (rare) — клиенты видят отсутствие документа; квесты с `catalogId` указывающим на удалённый каталог — становятся "orphan". **Orphan handling** — Open Question для research/design; variant (a) UI показывает "Unknown", variant (b) server-side validation не даёт удалять catalog с квестами.

### Server-Side Issues

Нет известных проблем, которые нельзя обойти на клиенте в MVP.

## Search Criteria for Research

1. **Существующий catalog-related код в новом проекте** — есть ли placeholder'ы:
   ```
   grep -rn "Catalog" shared/ android/ | grep -v legacy | grep -v build
   find shared/core -type d | grep -i catalog
   ```
2. **Существующий sync worker** — есть ли уже WorkManager sync в новом проекте?
   - `shared/core/sync/` — что там есть? contract `Syncable`?
   - `platform/android-services/` — WorkManager?
   - Legacy pattern для сравнения: `legacy/app/src/main/.../SyncWorker.kt` (profile + settings + quizData)
3. **Image loading library** — что уже используется?
   - `grep -rn "Coil\|Glide" android/ platform/`
   - Если ничего не используется — **выбор** Coil (recommended для Compose) или Glide (legacy-compatible)
4. **Firebase setup** — какие dependencies уже в `platform/firebase/`? Какие collections уже декларированы?
5. **`Quest` domain model** — есть ли файл `Quest.kt` в `shared/feature/quiz/domain/`? Если нет, нужно создать. Если есть — проверить текущую форму, добавить `catalogId` минимально инвазивно.
6. **Room database** — в `shared/core/persistence/` и `platform/firebase/` — какая конфигурация? Где singleton DB? Можно ли добавить `CatalogEntity` без конфликтов migration?
7. **Koin modules для features** — как структурированы `module { }` DSL файлы? Где appModule, где featureModule?
8. **Material3 dropdown / grid компоненты** — что уже использовали в designsystem?
   - `android/core/designsystem/` — что есть?
   - Использование `ExposedDropdownMenuBox` в проекте
9. **DrawerFooterAction паттерн** — читать `shared/.../domain/model/DrawerFooterAction.kt:14-20` для understanding как добавить новый action
10. **Legacy catalog data format** — `legacy/common/.../StructureDataLocal.kt` — для inspiration о том, что клиент ожидает

### Обязательные search directions
- Найти ВСЕ места в коде, где используется `"courses"`, `"surveys"`, `"games"`, `"school"` как строковые литералы — не должно быть после catalog-foundation (используется CatalogId)
- Найти существующий механизм подписки на Firestore updates (используется ли `snapshotListener` или просто `get()`)
- Найти ВСЕ screens которые планируются на использование catalog (research список: MyQuests, HomeQuests, Arena, Validation, возможно Tournament)
- Для каждой функции из Firestore integration — задокументировать полную сигнатуру и пример вызова

### Completeness check
- Compile check: после создания catalog module — `./gradlew :shared:core:catalog:domain:jvmTest` success
- Count of grep hits для magic strings каталогов — должно быть 0 в новом коде

## Primary User Journeys

1. **Happy path: первый запуск приложения**
   - Start: юзер впервые открывает приложение, нет локального кеша каталогов
   - Trigger: открывает экран "Домашние квесты" (или "Мои квесты")
   - State changes:
     - Screen ViewModel запрашивает `ObserveCatalogsUseCase.invoke()`
     - `CatalogRepository.observeAll()` возвращает empty Flow initially
     - Trigger async `refreshFromRemote()` → Firestore pull → локальный кеш заполняется → Flow emits list
     - UI: loading skeleton → catalog grid/spinner с данными
   - Expected result: юзер видит каталоги (опросы/курсы/игры/школа) с картинками (в grid) или в dropdown
   - Decision: [USER DECIDED] "при первом открытии"

2. **Happy path: возврат на экран (warm cache)**
   - Start: юзер вернулся на экран, кеш уже есть
   - Trigger: open screen
   - State changes: `observeAll()` сразу возвращает cached list
   - Expected result: мгновенный показ; фоновый refresh (при следующем WorkManager sync) обновит данные
   - Decision: [DELEGATED — offline-first pattern]

3. **Dev sync button flow**
   - Start: dev-mode активирован (`developer >= LEVEL_1.points` local)
   - Trigger: юзер кликает на `DrawerFooterAction.SyncNow` в drawer footer
   - State changes: enqueue общий WorkManager job → sync все entities (profile, settings, quizzes, **catalogs**)
   - Expected result: snackbar "Синхронизация запущена"; после completion — все кеши обновлены; UI реагирует на изменения в кешах через Flow
   - Decision: [USER DECIDED] "для разработчика добавь кнопку синхронизация"

4. **Offline first launch**
   - Start: юзер впервые запускает приложение без сети
   - Trigger: открывает экран
   - State changes:
     - `observeAll()` возвращает empty cache
     - `refreshFromRemote()` fails с network error
     - ViewModel показывает empty state "Нет каталогов, проверьте подключение"
   - Expected result: понятный empty state; при появлении сети — автоматический retry через WorkManager (при следующем scheduled sync)
   - Decision: [DELEGATED — стандартный offline-first pattern]

5. **Quest creation flow (interaction)**
   - Start: юзер создаёт новый квест, catalogs уже загружены
   - Trigger: юзер выбирает catalog в спиннере на экране создания
   - State changes: `quest.catalogId = selected.id`; save
   - Expected result: quest создан с привязкой к catalog; появляется в grid/spinner соответствующего каталога
   - Decision: [USER DECIDED] "обязательный"

6. **Admin updates catalog (remote)**
   - Start: админ через admin-tool обновил `catalogs/surveys.name` в Firestore
   - Trigger: клиент запускает next WorkManager sync (или dev-sync trigger)
   - State changes: Firestore pull → обновлённый catalog → локальный кеш → Flow emits
   - Expected result: UI автоматически обновляется с новым name (нет manual refresh)
   - Decision: [DELEGATED — standard sync pattern]

## Feature Domain Contract

### Terms / Entities / Value Constraints

- `CatalogId` — `@JvmInline value class CatalogId(val value: String)`. Invariant: `value.isNotBlank()`.
- `Catalog` — data class `(id: CatalogId, name: String, picturePath: String?)`.
  - Invariant: `name.isNotBlank()`
  - Invariant: `picturePath == null || picturePath.isNotBlank()`
  - Invariant: `picturePath` не должен начинаться с `"https://"` или `"gs://"` — это относительный path (если присутствует)
- `CatalogRepository` — interface:
  ```kotlin
  interface CatalogRepository {
      fun observeAll(): Flow<List<Catalog>>
      suspend fun refreshFromRemote(): Result<Unit>
      suspend fun getById(id: CatalogId): Catalog?   // для валидации Quest.catalogId
  }
  ```

### Business Rules / Invariants / Guards

1. `Catalog.id` уникален в пространстве всех каталогов
2. `Catalog.name` не пустой, trimmed
3. `Catalog.picturePath` — если не null, non-blank, не начинается с `"https://"` / `"http://"` / `"gs://"` (это relative Storage path)
4. `Quest.catalogId` — non-null; value должен существовать в `CatalogRepository` на момент создания квеста (валидация на клиенте + server-side)
5. Удаление catalog из Firestore → локально quest с ориглан catalogId видим в UI (показ "Unknown catalog" или fallback) — **Open Question для design**
6. Клиент НЕ пишет `catalogs/*` в Firestore (только admin через admin-tools)
7. Locally cached catalogs survive app restart (persisted via Room)

### State / Decision Rules

Single-state модель: Catalog имеет только `present` / `absent` (exists в кеше). Нет state-machine.

Sync-state для одного catalog:
- `Never fetched` (empty cache) → `Fetching` → `Cached` → `Stale (older than X)` → `Refetching` → `Cached`

Но в MVP sync-state не экспонируется — это внутренности WorkManager. Клиент видит просто `Flow<List<Catalog>>`.

### Error / Recovery Rules

- **Network error** на refresh: `Result.failure(NetworkError)`; Flow продолжает эмитить cached list
- **Firestore permission denied**: `Result.failure(AuthError)`; same behavior — cached list продолжается
- **Empty server response** (нет каталогов вообще): Flow emits `emptyList()`; UI показывает empty state
- **Orphan quest** (catalogId ссылается на несуществующий catalog): `getById(id) == null`; UI design решает (показать "Unknown" label); **Open Question**

### Domain Test Scenarios (phase-01 source of truth)

1. GIVEN empty cache WHEN `observeAll()` collected THEN emits `emptyList()` initially
2. GIVEN cached `[Catalog("surveys", "Опросы", null), Catalog("courses", "Курсы", "catalog-pictures/courses.jpg")]` WHEN `observeAll()` THEN emits exactly these 2 sorted by id ASC (`courses`, `surveys`)
3. GIVEN `Catalog(CatalogId("surveys"), "Опросы", null)` WHEN construct THEN no exception
4. GIVEN `Catalog(CatalogId(""), "Name", null)` WHEN construct THEN throws `IllegalArgumentException` (CatalogId value blank)
5. GIVEN `Catalog(CatalogId("id"), "", null)` WHEN construct THEN throws (name blank)
6. GIVEN `Catalog(CatalogId("id"), "Name", "")` WHEN construct THEN throws (blank picturePath)
7. GIVEN `Catalog(CatalogId("id"), "Name", "https://x")` WHEN construct THEN throws (должен быть relative path, не URL)
8. GIVEN `Catalog(CatalogId("id"), "Name", "gs://bucket/path")` WHEN construct THEN throws (должен быть relative path, не gs URI)
9. GIVEN `Catalog(CatalogId("id"), "Name", "catalog-pictures/surveys.jpg")` WHEN construct THEN no exception
10. GIVEN `Catalog(CatalogId("id"), "Name", null)` WHEN construct THEN no exception (null allowed)
11. GIVEN `FakeCatalogRepository` initialized with 3 catalogs WHEN `observeAll()` collected THEN emits list of 3
12. GIVEN `FakeCatalogRepository` WHEN `refreshFromRemote()` called AND simulated success AND new catalog added remotely THEN next emit from `observeAll()` contains new catalog
13. GIVEN `FakeCatalogRepository` WHEN `refreshFromRemote()` fails THEN `observeAll()` continues emitting cached list (no exception propagation)
14. GIVEN `FakeCatalogRepository` with `[Catalog(id=surveys), Catalog(id=courses)]` WHEN `getById(CatalogId("surveys"))` THEN returns non-null Catalog
15. GIVEN same WHEN `getById(CatalogId("unknown"))` THEN returns null
16. GIVEN `Catalog("surveys", "Опросы", null).equals(Catalog("surveys", "Опросы", null))` THEN `true` (data class equality)
17. GIVEN `Catalog("surveys", "Опросы", null).equals(Catalog("surveys", "Опросы", "catalog-pictures/x.jpg"))` THEN `false`

**Ordering / determinism (Codex fix #6):**

18. GIVEN `FakeCatalogRepository` with catalogs `[surveys, courses, school, games]` (inserted in this order) WHEN `observeAll()` collected THEN emits `[courses, games, school, surveys]` (sorted by `id.value` ASC)
19. GIVEN Firestore pull returns catalogs in non-deterministic order WHEN `refreshFromRemote` completes AND `observeAll()` collected THEN emits list sorted by `id.value` ASC (client-side sort guarantees stability)

**Quest delta contract (Codex fix #9):**

20. GIVEN `Quest(id=QuestId("q1"), catalogId=CatalogId(""), title="Test")` WHEN construct THEN throws (blank CatalogId)
21. GIVEN `CreateQuestUseCase` with `FakeCatalogRepository` containing `[surveys]` WHEN invoked with `Quest(id, catalogId=CatalogId("surveys"), title="Q")` THEN success; quest saved
22. GIVEN `CreateQuestUseCase` with `FakeCatalogRepository` containing `[surveys]` WHEN invoked with `Quest(id, catalogId=CatalogId("unknown"), title="Q")` THEN throws `IllegalArgumentException("Unknown catalogId: unknown")`; quest НЕ сохранён
23. GIVEN `Quest(id, catalogId=CatalogId("surveys"), title="Q")` WHEN read `catalogId` THEN `CatalogId("surveys")` (non-null guaranteed by type)

## State Matrix

N/A — catalog-foundation не содержит ветвистой логики (single-path CRUD через repository).

## Delegated Decisions Summary

| # | Область | Решение | Обоснование | Risk |
|---|---|---|---|---|
| 1 | `CatalogId` как value class | Type safety для ID | Standard Kotlin idiom, зёро overhead | low |
| 2 | Repository pattern | Clean architecture | Projects uses Koin-based DI; тестируемо через fakes | low |
| 3 | Image library: Coil vs Glide | Coil (recommended для Compose) если ничего не используется; иначе existing | Coil — первоклассный для Compose; Glide — legacy-compat | low |
| 4 | UI в designsystem | Cross-screen переиспользование | Avoids duplication; preview-testable | low |
| 5 | 2-col grid hardcoded | Простой MVP UI | Dynamic columns based on screen width — future work | low |
| 6 | `SyncNow` footer action depends on dev-mode | Dev-mode superqualification bypass | Согласованно с menu-refactor/dev-mode spec | low |
| 7 | Loading state design | Material skeleton loading | Standard pattern | low |
| 8 | Orphan quest handling | Open Question — решается в design | Может быть UI-level ("Unknown") либо server-level (не удалять catalog с квестами) | medium |
| 9 | Firestore schema: fields `name/picturePath/order/timestamps` | Minimal + future-proof | `order`/`timestamps` unused сейчас, но не ломают structure | low |
| 10 | Firebase Storage public read | No auth для images | Images — public asset; каталоги видны всем | low |
| 11 | Catalog sync как step в существующем SyncWorker | Reuse infrastructure | Легаси pattern; TBD research — есть ли уже worker | low |
| 12 | Koin `module { }` для catalog | Consistent с ADR-0009 | Per-feature module | low |
| 13 | Добавление `catalogId` в `Quest` | Non-null required | Depends on Quest existing or created | medium (if Quest not exist yet) |

## Acceptance Criteria

1. [ ] GIVEN `shared/core/catalog/domain/.../model/Catalog.kt` WHEN read THEN содержит data class `Catalog(id: CatalogId, name: String, picturePath: String?)` + `@JvmInline value class CatalogId(value: String)`. **Нет** поля `pictureUrl` в domain (Codex fix #5)
2. [ ] GIVEN все 23 Domain Test Scenarios WHEN run as `@Test` THEN зелёные
3. [ ] GIVEN `CatalogRepository` interface WHEN inspect THEN содержит `observeAll()`, `refreshFromRemote()`, `getById()`
4. [ ] GIVEN `FakeCatalogRepository` WHEN in-memory backed THEN behaves как spec contract
5. [ ] GIVEN `CatalogSpinner` в `android/core/designsystem/` WHEN render THEN использует M3 `ExposedDropdownMenuBox`; default selected "Все категории"
6. [ ] GIVEN `CatalogGrid` WHEN render THEN `LazyVerticalGrid(columns = GridCells.Fixed(2))`; карточки имеют `AsyncImage` (загружает picture через Firebase Storage resolve) + `Text(name)`; размер карточки 48dp+ для tap target
7. [ ] GIVEN Firestore collection `catalogs` WHEN seeded с 4 документами (surveys, courses, games, school) THEN `CatalogRepository.refreshFromRemote()` returns `Result.success` AND `observeAll()` emits list of 4 **отсортированных по `id.value` ASC** (Codex fix #6)
8. [ ] GIVEN dev mode active (`effectiveDeveloperLevel >= LEVEL_1.points` OR `isDebugBuild`) WHEN drawer открыт THEN `DrawerFooterAction.SyncNow` видим **в порядке Footer Contract matrix** (см. `menu-refactor/0-spec-dev-mode.md`)
9. [ ] GIVEN dev mode НЕ active AND `isDebugBuild == false` WHEN drawer открыт THEN `SyncNow` скрыт
10. [ ] GIVEN клик на `SyncNow` WHEN fired THEN WorkManager sync job enqueued; snackbar "Синхронизация запущена"
11. [ ] GIVEN общий sync WorkManager job completes WHEN inspect catalog state THEN локальный кеш содержит последнюю версию из Firestore
12. [ ] GIVEN offline (no network) AND empty cache WHEN open screen using CatalogGrid (preview / test harness) THEN empty state показан с сообщением "Нет каталогов, проверьте подключение"
13. [ ] GIVEN offline AND populated cache WHEN open screen THEN cached каталоги показаны (отсортированные по id ASC)
14. [ ] **(Codex fix #9)** GIVEN `Quest` domain model после фичи WHEN inspect THEN содержит поле `catalogId: CatalogId` (non-null); `CreateQuestUseCase` валидирует `catalogId` через `CatalogRepository.getById` перед save; throws если catalog отсутствует
15. [ ] GIVEN клиентское приложение WHEN попытка записать в `catalogs/*` в Firestore THEN permission denied (security rules)
16. [ ] GIVEN admin user (qualifications.admin >= 100) WHEN попытка записать в `catalogs/*` THEN success
17. [ ] **(Codex fix #5)** GIVEN grep `"pictureUrl"` по всему domain модулю (`shared/core/catalog/domain/`) WHEN run THEN 0 matches (domain НЕ содержит `pictureUrl`; резолв URL — artifact infrastructure layer)
18. [ ] **(Codex fix #8)** Foundation-only boundary: GIVEN integration с конкретным screen (например MyQuests using CatalogSpinner) WHEN inspect THEN это integration **не в этой spec** (не в AC, не в code); компоненты существуют в designsystem с Preview — integration происходит в future spec

## Invariant Check (from docs/invariants.md + new from menu-refactor)

| Invariant | Impact | Decision |
|-----------|--------|----------|
| 1. Domain layer purity | `Catalog`, `CatalogId` — pure Kotlin, no Android/SDK. Проверить grep | preserve |
| 2. Activity/Fragment calls only ViewModel | Screens используют ViewModel, которые вызывают UseCase | preserve |
| 3. No bidirectional coupling | `shared/core/catalog` — используется многими features; ВСЕ зависимости — one-way | preserve |
| 5. DI exclusive binding | Koin `module { }` — один подход (factory/single) per repository | preserve |
| 6. Walking Skeleton ownership | Генерируется в Phase 3.8 для catalog-foundation | preserve + generate |
| 7. Scaffold file ownership | Новый Gradle module `shared:core:catalog:domain` и `:data` — backend-dev owner for build.gradle.kts changes | preserve |
| NEW. Superqualification DEVELOPER (из menu-refactor) | SyncNow visible при `developer>=LEVEL_1` bypass | preserve (consistent) |

## Constraints (from PROJECT_STRUCTURE.md + ADRs)

- KMP `androidTarget + jvm` (ADR-0002) для shared/core/catalog
- Koin DI (ADR-0009) — per-feature module для catalog
- Compose + Material3 (ADR-0010) для UI components
- ADR-0004 sync contract — Catalog как `Syncable` (если contract существует в `shared/core/sync/`)
- ADR-0005 ортогональность — `QuestType.COURSE` ≠ `catalog="courses"` (не hard invariant)
- PROJECT_STRUCTURE §4 — catalog в `shared/core/` (NOT `shared/feature/`), т.к. используется в multiple features

## Dependencies on Other Features

- **menu-refactor/dev-mode** — для `DrawerFooterAction.SyncNow` видимости (superqualification + visibleFooterActions signature extension). catalog-foundation **extends** signature `visibleFooterActions`, добавляя `SyncNow` в list при dev mode. Нужен sync коnfiguration (catalog-foundation создаётся ПОСЛЕ или parallel с menu-refactor/dev-mode).

## Open Questions (для research / design)

1. **Orphan quest handling** — если catalog удалён, quest с этим catalogId что показывает? (UI "Unknown" vs server-side constraint)
2. **Existing sync worker** — research должен найти: есть ли уже WorkManager sync в новом проекте? Если нет — создаём новый; если есть — catalog sync добавляется как step
3. **Image library выбор** — Coil vs Glide; research проверит что уже используется
4. **Где резолвится picturePath → URL** — в repository layer (при fetch Catalog) или в image loader (Coil custom fetcher / Glide FirebaseImageLoader)? Design phase решит. Вариант loader-level лучше для кеширования. Вариант repository-level проще но больше Firestore reads.
5. **Catalog reorder UX** — если в будущем потребуется переупорядочивать каталоги — через `order` field в Firestore (уже заложено в schema), но admin tool пока не нужен
6. **Catalog `iconKey` alternative** — хранить имя icon из Material icons вместо path для каталогов без изображений? TBD в design
