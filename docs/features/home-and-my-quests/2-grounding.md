---
date: 2026-04-22
researcher: Claude
commit: 7c52c200
branch: kmp-skillify-4.0
---

# Grounding: Home Quests & My Quests + Cascading Catalog Sync

## Verification Protocol

Каждый claim из `1-research.md` проверен **независимо** через Read tool — открыты исходные файлы, прочитан реальный код. Формат: `[VERIFIED: file:line]` с цитатой или `[CONTRADICTS: …]` если research расходится с реальностью.

Critical claims проверены ниже:

- **`CatalogEntity` 4 поля без version/contentsVersion/archived/lastModifiedAt** — `[VERIFIED: CatalogEntity.kt:7-12]` — `data class CatalogEntity(@PrimaryKey val id, name, picturePath, pictureUrl)`. Только 4 поля.
- **`PersistenceModule` без `fallbackToDestructiveMigration()`** — `[VERIFIED: PersistenceModule.kt:11-16]` — `Room.databaseBuilder(...).build()` напрямую, никаких migration вызовов.
- **`AppDatabase` version=1, 2 entities** — `[VERIFIED: AppDatabase.kt:6-10]` — `@Database(entities = [UserStatsEntity, CatalogEntity], version = 1, exportSchema = true)`.
- **`UserStatsRepository` — 4 метода, нет `currentAuthUid()`** — `[VERIFIED: UserStatsRepository.kt:15-43]` — interface содержит `observeStats`, `currentStats`, `setLocalDeveloperLevel`, `refreshProfile`. Нет `currentAuthUid`.
- **`UserStats` model — нет `uid` поля** — `[VERIFIED: UserStats.kt:11-22]` — 11 полей: `nickname, avatarUrl, hasPremium, streakDays, stars, nolics, standardHearts, goldHearts, gold, currentSkill, qualification`. Нет `uid`.
- **`LocalConfig` — 5 children, нет `QuestCreateRoot`** — `[VERIFIED: TabConfig.kt:22-30]` — `MyQuestsRoot, HomeQuestsRoot, SettingsRoot, DesignCatalogRoot, EmptyRoot`. Нет `QuestCreateRoot`.
- **`Destination` — 6 children, нет `Push(config)`** — `[VERIFIED: Destination.kt:9-35]` — `Back, SwitchTab(tab), SelectSection(section), OpenDrawer, CloseDrawer, OpenDesignCatalog`. Нет `Push`.
- **`Navigator.goTo(destination: Destination)` принимает Destination, не LocalConfig** — `[VERIFIED: Navigator.kt:14-16]` — `interface Navigator { fun goTo(destination: Destination) }`. Spec `Navigator.goTo(LocalConfig.QuestCreateRoot)` — compile error.
- **`CatalogRepositoryImpl.refreshFromRemote()` full-replace без cursor** — `[VERIFIED: CatalogRepositoryImpl.kt:24-46]` — `remote.fetchAll()` → entities → `local.replaceAll(entities)`. Никаких cursor параметров. Prefix-guard `path.startsWith("catalog-pictures/")` на строке 32.
- **`CatalogSpinner` существует, не используется в production** — `[VERIFIED: CatalogSpinner.kt:34]` — `fun CatalogSpinner(items, selectedId, onSelectionChanged, modifier)`; Grep по всему проекту — единственный hit на сам файл (Preview), нет ни одного callsite.
- **Coil 3.1.0 в libs.versions.toml** — `[VERIFIED: libs.versions.toml:44]` — `coil3 = "3.1.0"`. Spec ссылается на 3.4.0.
- **`UserStatsRepositoryImpl` содержит `LOCAL_UID = "_local"` + private `currentUidFlow`** — `[VERIFIED: UserStatsRepositoryImpl.kt:22-28, :74-76]` — `private val currentUidFlow: () -> Flow<String?>`, `companion object { const val LOCAL_UID: String = "_local" }`, `effectiveUidFlow` маппит null → LOCAL_UID.

Все verified — research findings consistent с кодом. Нет `[CONTRADICTS]`.

---

## Problem 1: Catalog data layer — full-replace вместо delta-sync

### Symptom

Spec требует delta-sync через cursor (`lastModifiedAt > cursor`), early-exit по `contentsVersion`, version-guarded upsert. Реальный код делает полный `fetchAll() → replaceAll()` каждый раз, не имеет cursor-механизма, не обрабатывает delete (`archived=true`).

### Entry Points (EXHAUSTIVE)

- `SyncWorker.performSync()` → `syncable.sync()` для каждого `Syncable` (`SyncWorker.kt:24-29`)
- `CatalogRepositoryImpl.sync()` → `refreshFromRemote()` (`CatalogRepositoryImpl.kt:51`)
- `CatalogRepositoryImpl.refreshFromRemote()` (`CatalogRepositoryImpl.kt:24`)
- Manual trigger: `DefaultRootComponent.onSyncNow()` → `enqueueUniqueWork(WORK_NAME_MANUAL, REPLACE)` (`DefaultRootComponent.kt:231`)
- Cold start: `AppApplication.onCreate()` → `enqueueUniquePeriodicWork(WORK_NAME_PERIODIC, KEEP, 1d)` + `enqueueUniqueWork(WORK_NAME_BOOTSTRAP, KEEP)` (`AppApplication.kt:60, :76`)

### Code Owners

- `CatalogRepositoryImpl.kt:13` — owner всей delta-sync логики после изменений
- `CatalogRemoteDataSource.kt:3` — interface, требует расширения сигнатуры (`fetchChangedSince(cursor: Long)`)
- `FirebaseCatalogRemoteDataSource.kt:13` — Firestore query implementation
- `CatalogLocalDataSource.kt:7` — interface, нужны `upsertById`/`deleteById` методы
- `CatalogDao.kt:11` — DAO, добавить `upsertByIdIfNewerVersion`, `deleteById`
- `CatalogEntity.kt:6` — Entity, добавить 4 поля
- `CatalogDto.kt:3` — DTO, добавить 4 поля
- `CatalogMapper.kt:7-12,14-19` — оба mapper'а
- `CatalogDtoMapper.kt:6-11` — DTO→Entity mapper
- `FirestoreCatalogDtoMapper.kt:6-12` — DocumentSnapshot reading
- `SyncStateRepository.kt:16` (interface, не подключён) — для cursor management

### Flow Trace

`SyncWorker.kt:24` → `CatalogRepositoryImpl.sync():51` → `refreshFromRemote():24` → `CatalogRemoteDataSource.fetchAll()` → `FirebaseCatalogRemoteDataSource.kt:13` (`firestore.collection("catalogs").get().await()`) → `FirestoreCatalogDtoMapper.kt:6` (читает только `name, picturePath`) → `CatalogDtoMapper.kt:6` → `CatalogEntity` (4 поля) → `storageUrlResolver(path)` → `CatalogLocalDataSourceImpl` → `CatalogDao.replaceAll():26` (`@Transaction { deleteAll(); insertAll(entities) }`) → Room → `observeAll() Flow:13` → `CatalogMapper.kt:7` → `Catalog` (с дефолтами: `version=1, contentsVersion=0, lastModifiedAt=0, archived=false`)

### Backend / Contract Check

- Firestore: текущая коллекция `catalogs` имеет только `name, picturePath`. Spec требует расширения схемы документа полями `version, contentsVersion, lastModifiedAt, archived`. **`[REQUIRES BACKEND CHANGE]`** — admin/server должен заполнить эти поля во всех документах перед deployment phase-01. Spec FR#17: pre-production destructive — admin удаляет и заливает заново.
- Firestore composite index: `catalogs: lastModifiedAt ASC` (single-field, auto-created). Spec `0-spec.md:239` — confirmed.
- Server invariant A (upward propagation): не реализовано. Spec FR#14 (`0-spec.md:81`): "Cloud Function / admin-tool вне этой фичи". `[REQUIRES BACKEND CHANGE]` за пределами phase-01.

### Constraints

- Lifecycle: `SyncWorker` живёт в WorkManager, не привязан к Activity lifecycle. Process death → `InMemorySyncStateRepository` теряет cursors, следующий sync → cursor=0 → full re-sync (приемлемо MVP)
- In-memory state: `SyncStateRepository` — `MutableStateFlow<Map>` + `Mutex`. Не persistent
- DB/Storage: schema bump `1→2` + `fallbackToDestructiveMigration()` (NFR#6) — pre-production допустимо
- Offline/Online: `SyncWorker.Constraints(NetworkType.CONNECTED)` (`AppApplication.kt:67, :83`, `DefaultRootComponent.kt:233`); при отсутствии сети worker не запускается, retry через WorkManager backoff

### Code Path Divergence

Spec предполагает один cascading sync flow для всех trigger'ов (cold start / periodic / manual). Реальный код имеет 3 enqueue location'а (AppApplication periodic + bootstrap, DefaultRootComponent manual), но они используют одну и ту же `SyncWorker`. Расхождения нет.

### Fix Shape (минимально реализуемое)

- **Client-only fix**:
  1. Расширить `CatalogEntity` + `CatalogDto` 4 полями
  2. Bump `AppDatabase.version → 2` + добавить `fallbackToDestructiveMigration()` в `PersistenceModule`
  3. Обновить `CatalogMapper`/`CatalogDtoMapper`/`FirestoreCatalogDtoMapper`
  4. Заменить `CatalogDao.replaceAll` → `upsertByIdIfNewerVersion(entity)` + `deleteById(id)` методы
  5. Расширить `CatalogRemoteDataSource.fetchChangedSince(cursor: Long)` + impl
  6. Изменить `CatalogRepositoryImpl.refreshFromRemote()`: cursor через `SyncStateRepository.getCursor("catalogs")`, после успеха `setCursor(...)`
  7. Подключить `SyncStateRepository` через Koin (новый `syncStateModule` или extend `syncModule`)
- **Requires backend**: server-side admin tool должен заполнить новые поля в Firestore документах. Cloud Function для invariant A propagation — out-of-scope phase-01
- **Follow-up**: миграция на persistent `RoomSyncStateRepository` — отдельная "sync rollback" фича

### Validation

- Manual: запустить `:apps:android-next:installDebug`, нажать SyncNow в дев-режиме, убедиться что Room обновляется с новыми полями
- Tests: `CatalogRepositoryImplTest` обновить — заменить `replaceAll` assertions на upsert/delete; добавить cursor advance scenarios. Spec scenarios CF-11..CF-18, и новые: AC#7-8 (`0-spec.md:934-935`), AC#41-42 (`0-spec.md:983-984`)
- Success criteria: `./gradlew :shared:core:catalog:data:jvmTest` зелёный + `:shared:core:persistence:connectedAndroidTest` зелёный + `AppDatabaseMigrationTest` обновлён для version 2

---

## Problem 2: Quest data layer + 4 каскадных уровня — НЕ существуют

### Symptom

Spec требует 5 новых data-модулей (`shared/feature/{quest,section,theme,lesson,question}/data`) с Repository impls, DAO, DTO, Firebase remote sources, Koin modules. Только domain существует.

### Entry Points (EXHAUSTIVE)

- ViewModel `MyQuestsViewModel` (NEW) → `ObserveMyQuestsUseCase.invoke(authorUid, catalogId?)` → `QuestRepository.observeMyQuests` (`feature/quest/domain/.../QuestRepository.kt:39`)
- `SyncWorker.performSync` → новые `Syncable` impls для quest/section/theme/lesson/question (NEW)
- Cascade orchestration entry: новый `CascadingSyncOrchestrator.run()` или extension `SyncWorker` (DECISION REQUIRED)

### Code Owners (после реализации)

- `shared/feature/quest/data/.../QuestRepositoryImpl.kt` (NEW)
- `shared/feature/quest/data/.../QuestLocalDataSource.kt` (NEW)
- `shared/feature/quest/data/.../QuestRemoteDataSource.kt` (NEW)
- `shared/feature/quest/data/.../QuestDto.kt` (NEW)
- `shared/feature/quest/data/.../mapper/Quest{Dto,}Mapper.kt` (NEW)
- `shared/feature/quest/data/.../di/QuestDataModule.kt` (NEW)
- `shared/core/persistence/.../QuestEntity.kt` (NEW) + `QuestDao.kt` (NEW)
- `platform/firebase/.../FirebaseQuestRemoteDataSource.kt` + `FirestoreQuestDtoMapper.kt` + `FirebaseQuestModule.kt` (NEW)
- (То же для section, theme, lesson, question — 5 stacks total)

### Flow Trace

После реализации:
- `MyQuestsViewModel.init { effectiveUid = userStatsRepo.currentAuthUid() ?: return }` → `combine(catalogIdFlow, observeMyQuests(uid, catalogId))` → emit UI state
- Sync flow: `SyncWorker → CatalogStep → if catalog.cv > local.cv: QuestStep(catalogIds) → ... → QuestionStep(lessonIds)`. Каждый step через `SyncStateRepository.getCursor(collectionId)`

### Backend / Contract Check

- Firestore collections (5 new): `quests, sections, themes, lessons, questions` — должны быть созданы admin-ом. **`[REQUIRES BACKEND CHANGE]`**. Spec `0-spec.md:222-238` детализирует schema
- 5 composite indexes: `quests(authorUid, catalogId, lastModifiedAt)`, `quests(visibleOn, lastModifiedAt)`, `sections(questId, lastModifiedAt)`, `themes(sectionId, lastModifiedAt)`, `lessons(themeId, lastModifiedAt)`, `questions(lessonId, lastModifiedAt)`. **Требуют ручной регистрации в Firebase Console**
- Firebase Security Rules: spec `0-spec.md:241-273` определяет canonical block. Должен быть deployed admin-ом перед client release. **`[REQUIRES BACKEND CHANGE]`**
- Firestore query split (Query A + B) для quests — confirmed корректный workaround по web research (`array-contains-any + in` всё ещё forbidden в одном query)

### Constraints

- Lifecycle: 5 новых `Syncable` запускаются sequentially — fail-fast на любом ломает каскад
- DB/Storage: 5 новых Room tables + TypeConverter для `Quest.visibleOn: Set<String>` (нет `@TypeConverter` в проекте сейчас)
- Offline: каждый level falls back на cached Room data при отсутствии сети
- KSP: проверить нужен ли `kspJvm` для Room codegen на JVM target

### Code Path Divergence

Spec FR#14 описывает 6 sequential steps в одном orchestrator. Альтернативные реализации:
- (a) Один class `CascadingSyncOrchestrator implements Syncable` — 6 internal steps
- (b) 6 отдельных `Syncable` в `syncModule` list, state передаётся через `SyncStateRepository`
- (c) Расширить `SyncWorker.performSync` с conditional dispatch

Решение — design phase.

### Fix Shape

- **Client-only fix**:
  1. Создать 5 data модулей (Repository impls, DAO, DTO, mappers)
  2. Создать 5 firebase remote modules в `platform/firebase/`
  3. Создать 5 Koin modules + регистрация в `AppApplication`
  4. Расширить `AppDatabase` 5 новыми entities + TypeConverter для `Set<String>` + bump version
  5. Создать orchestrator (один из вариантов выше) + добавить в `syncModule.kt:14-17`
  6. Подключить `SyncStateRepository` через Koin
- **Requires backend**: server создаёт 5 Firestore collections + composite indexes + security rules + populates seed data
- **Follow-up**: persistent `RoomSyncStateRepository` (отдельная фича)

### Validation

- Tests: 5 новых contract test suites по образцу `CatalogRepositoryImplTest`. Spec scenarios 41-49 (`0-spec.md:830-842`) для cascade edges
- Manual: SyncNow → проверить Room через Database Inspector что 5 tables заполнены
- Success: все JVM tests + integration test scenario 58 (`0-spec.md:1009`) — placeholder for cross-module integration в phase-01

---

## Problem 3: MyQuestsScreen + ViewModel + UID source — отсутствуют

### Symptom

Spec FR#21 требует новый `MyQuestsScreen` (LazyColumn QuestCard + CatalogSpinner + FAB), `MyQuestsViewModel`, навигация на `LocalConfig.QuestCreateRoot` (FAB). Реальный код:
- `LocalConfig.QuestCreateRoot` не существует
- `Navigator.goTo(LocalConfig)` — compile error
- `MyQuestsScreen` Composable не существует
- `android/feature/quest/presentation` — пустой scaffold
- `UserStatsRepository.currentAuthUid()` — метод не существует
- Текущий `MyQuestsRoot` рендерит `CatalogGridSection` (то же что HomeQuestsRoot — `AppShellScreen.kt:307-309`)

### Entry Points (EXHAUSTIVE)

- `AppShellScreen.kt:301-311` — `LocalTabContent` when-блок (текущий routing)
- `AppShellScreen.kt:319-329` — `CatalogGridSection` (Composable, инжектит `CatalogRepository` напрямую)
- `Labels.kt:85-95` — exhaustive when для `TabConfig.displayName` (затронут добавлением `QuestCreateRoot`)
- `DefaultLocalTabComponent.kt:23` — `initialConfiguration = LocalConfig.MyQuestsRoot`
- `DefaultRootComponent.kt:189-196` — `onDestination(destination)` обрабатывает navigation
- FAB onClick: новый callback в `MyQuestsScreen` → `Navigator.goTo(...)` (НЕТ метода для push)
- `AppApplication.kt:41-44` — `authUidFlow = callbackFlow<String?>` уже создан, может быть переиспользован

### Code Owners (после реализации)

- `LocalConfig.kt` (TabConfig.kt:22-30) — добавить `QuestCreateRoot`
- `Destination.kt:9-35` — добавить `data class Push(config: TabConfig)` или alternative
- `Navigator.kt:14-16` или `RootComponent.kt` — новый push API
- `Labels.kt:85-95` — exhaustive when update
- `AppShellScreen.kt:307-311` — when branch update (`QuestCreateRoot → UnderConstructionScreen("Создание квеста в разработке")`, `MyQuestsRoot → MyQuestsScreen`)
- `android/feature/quest/presentation/.../MyQuestsScreen.kt` (NEW)
- `android/feature/quest/presentation/.../MyQuestsViewModel.kt` (NEW)
- `android/feature/quest/presentation/.../di/QuestPresentationModule.kt` (NEW)
- `UserStatsRepository.kt:15` или `AuthRepository.kt` (NEW) — метод для получения UID

### Flow Trace

**FAB click flow (планируемый)**:
- User → `MyQuestsScreen.onCreateQuestClick()` → `Navigator.goTo(NEW_DESTINATION)` → `DefaultRootComponent.onDestination()` → `AppShellTransitions.navigate()` → `setLocalStack(NavStack.push(QuestCreateRoot))` → `syncStack(...)` → Decompose `nav.navigate()` → `AppShellScreen.LocalTabContent` rerender → `UnderConstructionScreen("Создание квеста в разработке")`
- Back: `AppShellScreen.kt:127` `BackCallback` → `Destination.Back` → `NavStack.pop()` → возврат на `MyQuestsRoot`

**UID acquisition flow (планируемый)**:
- `MyQuestsViewModel.init { ... }` → `userStatsRepo.currentAuthUid()` (NEW) → `String?` → если null → `MyQuestsUiState(isGuest = true)`; если non-null → `combine(catalogFlow, observeMyQuests(uid, catalogId))`

### Backend / Contract Check

- `Quest.authorUid: String` security rule: `request.auth.uid == resource.data.authorUid` — клиент не может запросить чужие quests. Spec `0-spec.md:248-254`. **`[REQUIRES BACKEND CHANGE]`** для security rules deployment
- `quests` query: `where('authorUid', '==', currentUid).where('catalogId', 'in', [...])` (Query A) и `where('visibleOn', 'array-contains-any', shelves)` (Query B) — два независимых запроса, merge клиентом

### Constraints

- Lifecycle: `MyQuestsViewModel` — Decompose Component-based (не AndroidX ViewModel?). Нужно определить scope (presentation level)
- In-memory state: `StateFlow<CatalogId?>` для spinner selection — теряется при process death (приемлемо)
- Offline: empty list при отсутствии cached данных + cached данных при offline — invariant offline-first
- Min touch target ≥ 48dp для FAB и QuestCard (Material3 a11y)

### Code Path Divergence

`AppShellScreen.kt:307-309` обрабатывает `HomeQuestsRoot, MyQuestsRoot → CatalogGridSection` одинаково. Spec требует разделить:
- `HomeQuestsRoot → CatalogGridSection` (с обновлённой типографикой `titleMedium bold` вместо `bodySmall`)
- `MyQuestsRoot → MyQuestsScreen` (новый Composable + ViewModel)

Pre-existing pattern — `CatalogGridSection` использует `koinInject<CatalogRepository>()` напрямую (нарушение `use-cases.md`). `MyQuestsScreen` design phase должен решить — следовать тому же anti-pattern или использовать `MyQuestsViewModel` (рекомендуется второй).

### Fix Shape (FINALIZED 2026-04-22 — Decisions #41, #42)

- **Client-only fix**:
  1. Добавить `LocalConfig.QuestCreateRoot` в `TabConfig.kt:22-30`
  2. Добавить `Destination.OpenQuestCreate` (data object) в `Destination.kt:9-35` — по аналогии с существующим `OpenDesignCatalog`
  3. Расширить `RootComponent.onDestination` (или `AppShellTransitions.navigate()` case) обработкой `Destination.OpenQuestCreate` → `setLocalStack(NavStack.push(LocalConfig.QuestCreateRoot))`
  4. Обновить `Labels.kt:85-95` exhaustive when для `QuestCreateRoot → "Создание квеста"`
  5. Обновить `AppShellScreen.LocalTabContent:301-311` when для `QuestCreateRoot → UnderConstructionScreen("Создание квеста в разработке")` и `MyQuestsRoot → MyQuestsScreen(ViewModel)`
  6. Создать `android/feature/quest/presentation` модуль (зарегистрировать в settings.gradle)
  7. Создать `MyQuestsScreen` + `MyQuestsViewModel(authRepo, observeMyQuests, observeCatalogs, navigator)` + `QuestPresentationModule`
  8. **Decision #42**: создать `AuthRepository` interface в `shared/feature/app-shell/domain/repository/` (✅ DONE — Walking Skeleton dop'd) + `AuthRepositoryImpl` в data-layer (phase-01)
  9. `MyQuestsScreen` FAB callback: `onCreateQuestClick = { navigator.goTo(Destination.OpenQuestCreate) }`
- **Requires backend**: deploy Firebase Security Rules для quests collection
- **Follow-up**: создание квеста UI — отдельная фича

### Validation

- Manual: открыть "Мои квесты" → spinner + LazyColumn (или empty state) + FAB; tap FAB → UnderConstructionScreen; back → возврат
- Tests: `MyQuestsViewModelTest` (guest case + happy path + spinner selection); `LocalConfigTest` для нового QuestCreateRoot; UI snapshot tests если применимо
- Success: все compose preview тесты зелёные + `BrandComponentsInvariantsTest` зелёный для нового `QuestCard`/`StarRating`

---

## Problem 4: SyncStateRepository — создан, не подключён

### Symptom

Spec требует `SyncStateRepository` для управления курсорами и pending cascades. Interface + `InMemorySyncStateRepository` уже созданы и протестированы. Но нет Koin binding'а, не используется в `SyncWorker`.

### Entry Points (EXHAUSTIVE)

- Подключение через DI: `apps/android-next/.../di/SyncModule.kt:12` (расширить или создать новый `syncStateModule`)
- Использование в orchestrator: новый `CascadingSyncOrchestrator` (или другой подход — см. Problem 2)
- `SyncWorker.performSync()` — если каскад делает sync internally, передать `SyncStateRepository` через `SyncWorkerFactory`

### Code Owners

- `shared/core/sync/.../SyncStateRepository.kt:16` — interface (уже есть)
- `shared/core/sync/.../InMemorySyncStateRepository.kt:18` — impl (уже есть)
- `apps/android-next/.../di/SyncModule.kt` — добавить `single<SyncStateRepository> { InMemorySyncStateRepository() }`
- Новый orchestrator class — owner usage

### Flow Trace (планируемый)

`SyncWorker.doWork()` → `orchestrator.run(syncStateRepo)` → для каждого collection:
1. `cursor = syncStateRepo.getCursor("catalogs")` (default 0)
2. `dtos = remote.fetchChangedSince(cursor)`
3. для каждого DTO: upsert in Room (Matrix 1)
4. `newCursor = max(cursor, max(dto.lastModifiedAt))`
5. при success всего step → `syncStateRepo.setCursor("catalogs", newCursor)`
6. если catalog.cv > local.cv → recurse в quest step (с questsCursor)

### Backend / Contract Check

- N/A — purely client-side concern

### Constraints

- In-memory state: `MutableStateFlow<Map>` + `Mutex` (line 18). При process death всё теряется → следующий sync начнёт с 0
- Phase-01 acceptable per spec (`0-spec.md:57`): "При kill процесса теряются. Следующий sync стартует с cursor=0 для всех collections → тянет заново, но upsert-by-id идемпотентен → нет дубликатов"

### Fix Shape

- **Client-only fix**: добавить Koin binding в `SyncModule.kt` или создать отдельный `syncStateModule`. ~5 строк кода.
- **Follow-up**: `RoomSyncStateRepository` — отдельная "sync rollback" фича

### Validation

- Tests: уже есть `InMemorySyncStateRepositoryTest` — добавить integration с orchestrator
- Manual: SyncNow дважды подряд → второй раз должен быть incremental (cursor advanced)
- Success: AC#41-44 (`0-spec.md:983-986`) зелёные

---

## Problem 5: Coil 3.1.0 vs 3.4.0 spec расхождение

### Symptom

`libs.versions.toml:44` — `coil3 = "3.1.0"`. Spec `0-spec.md:1028` ссылается на ADR-HLA-06 "Coil 3.4.0".

### Entry Points

- `gradle/libs.versions.toml:44` — единственное место version
- `android/core/designsystem/build.gradle.kts` — Coil dependency (не verified в этом research, но работает)

### Code Owners

- `backend-dev` — owner всех `*.gradle.kts` и `libs.versions.toml` (per scaffold ownership rule)

### Flow Trace

Не применимо — это версия dependency.

### Backend / Contract Check

Не применимо.

### Constraints

- Coil 3 breaking changes: `AsyncImagePainter.state → StateFlow`, `modelEqualityDelegate` removed (web research)
- `?v={version}` URL pattern работает в обеих версиях (URL-based cache key)

### Fix Shape (FINALIZED 2026-04-22 — Decision #43)

- **Selected Option A**: bump `gradle/libs.versions.toml:44` 3.1.0 → 3.4.0 (consistent с ADR-HLA-06)
- Breaking changes (web research): `AsyncImagePainter.state` → StateFlow, `modelEqualityDelegate` removed, last write timestamp not added to file cache key. Текущее использование (`CatalogGrid.kt:71` — простой `AsyncImage(model = url)`) breaking changes не задевают.
- Owner: `backend-dev` (scaffold ownership rule).

### Validation

- `./gradlew :android:core:designsystem:assembleDebug` зелёный после bump
- `BrandComponentsInvariantsTest` зелёный
- Visual smoke test через Preview/Snapshot

---

## Problem 6: Quiz module placeholder cleanup

### Symptom

`shared/feature/quiz/{domain,data}` зарегистрированы в `settings.gradle.kts:47-48`, но содержат только `.gitkeep`. `android/feature/quiz/presentation` (`settings.gradle.kts:71`) — то же. Placeholder в `shared/core/catalog/domain/model/Quest.kt:31` ссылается на `quiz/`, реальный код в `quest/`.

### Entry Points

- `settings.gradle.kts:47-48,71` — module registrations
- `shared/core/catalog/domain/model/Quest.kt:31` — TEMPORARY placeholder
- `shared/core/catalog/domain/repository/QuestRepository.kt:19` — placeholder interface
- `shared/core/catalog/domain/use_case/CreateQuestUseCase.kt:28` — uses placeholder
- `shared/core/catalog/domain/.../fake/FakeQuestRepository.kt:13` — placeholder fake
- `shared/core/catalog/domain/.../di/CatalogDomainModule.kt:8` — комментарий "removed pending real binding"

### Code Owners

- `backend-dev` — settings.gradle.kts, build files
- Author of placeholder — kdoc comments указывают на временность

### Flow Trace

Placeholder Quest используется только в:
- `CreateQuestUseCase.invoke(quest)` (`CreateQuestUseCase.kt:28`)
- `QuestCatalogLinkTest.kt:7-8` (test for placeholder)
- `FakeQuestRepository.save(quest)` (`FakeQuestRepository.kt:13`)

После удаления — данные тесты ломаются и должны быть удалены вместе.

### Backend / Contract Check

Не применимо.

### Constraints

- Compile dependency: `settings.gradle.kts:47-48` registration не вредит — модули пустые. Удаление безопасно.
- Walking Skeleton ссылается на `quest/` (не `quiz/`) — placeholder KDoc устарел.

### Fix Shape (FINALIZED 2026-04-22 — Decision #44)

- **Selected Option A**: cleanup в scope phase-01.
  - Удалить `shared/feature/quiz/domain` — убрать `include(":shared:feature:quiz:domain")` из `settings.gradle.kts:47`
  - Удалить `shared/feature/quiz/data` — убрать строку `:48`
  - Удалить `android/feature/quiz/presentation` — убрать строку `:71`
  - Удалить файлы из `shared/core/catalog/domain`:
    - `model/Quest.kt` (placeholder + duplicate QuestId)
    - `repository/QuestRepository.kt` (placeholder с одним методом save)
    - `use_case/CreateQuestUseCase.kt`
    - `commonTest/.../fake/FakeQuestRepository.kt`
    - `commonTest/.../QuestCatalogLinkTest.kt`
  - В `CatalogDomainModule.kt:8` убрать комментарий "CreateQuestUseCase removed: ..."
- Канонический `Quest` остаётся в `shared/feature/quest/domain/model/Quest.kt:30` (Walking Skeleton).
- Owner: `backend-dev` (scaffold files: settings.gradle.kts) + `domain-dev` (domain Quest cleanup).

### Validation

- `./gradlew assemble` зелёный после удаления
- Все JVM/Android tests зелёные (`QuestCatalogLinkTest` удалить)

---

## Problem 7: BrandComponentsInvariantsTest — coverage для новых components

### Symptom

`android/core/designsystem/src/test/.../BrandComponentsInvariantsTest.kt:24-65` сканирует все `.kt` файлы в `components/` через `walkTopDown()`. Требует:
- `@Preview` annotation в каждом файле (`:54-65`)
- Запрещает `Color(0xFF...)` hardcoded (`:24-35`)

Новые `QuestCard.kt`, `StarRating.kt` обязаны соответствовать. Иначе `:android:core:designsystem:test` упадёт.

### Entry Points

- `BrandComponentsInvariantsTest.kt:24` (color check)
- `BrandComponentsInvariantsTest.kt:54` (preview check)

### Code Owners

- `frontend-dev` — owners новых components
- Тест проверяет invariant on commit time

### Flow Trace

При создании `QuestCard.kt`:
- Должен использовать `MaterialTheme.colorScheme.primary` (для `GoogleBlue`) вместо `Color(0xFF4285F4)`
- Должен иметь хотя бы один `@Preview` Composable

### Backend / Contract Check

Не применимо.

### Constraints

- StarRating spec `0-spec.md:128`: "Звёзды: 3 штуки, синий цвет (`#4285F4` из брендовой палитры ADR-0010)" — `GoogleBlue = Color(0xFF4285F4)` уже в `Color.kt:13` как brand token. Использовать `MaterialTheme.colorScheme.primary` (который binds к `GoogleBlue`)

### Fix Shape

При реализации:
- `QuestCard.kt` — `@Preview` для empty/rated/unrated/long-title (4 preview composables) + использовать `MaterialTheme.colorScheme` для colors
- `StarRating.kt` — `@Preview` для 0, 0.5, 1.5, 2.7, 3.0, null (6 preview composables) + `MaterialTheme.colorScheme.primary`

### Validation

- `./gradlew :android:core:designsystem:test` зелёный после добавления preview tests

---

## Problem 8: Two QuestRepository interfaces — RESOLVED by Decision #44

### Status: AUTO-RESOLVED 2026-04-22

После Decision #44 (Problem 6 cleanup) placeholder `QuestRepository` в `shared/core/catalog/domain/repository/` удаляется. Единственный `QuestRepository` остаётся в `shared/feature/quest/domain/repository/QuestRepository.kt:23` (Walking Skeleton). Никаких naming conflicts. DI binding однозначен.

### Validation

- Compile проверка после cleanup: `./gradlew :shared:core:catalog:domain:compileKotlinJvm` зелёный
- `single<QuestRepository>` в `questDataModule` ссылается только на `feature.quest.domain.repository.QuestRepository`

---

## Invariant Conflicts

Проверка против `docs/invariants.md`:

| Invariant | Owner затронут? | Conflict? |
|-----------|-----------------|-----------|
| 1. Domain layer purity | YES — все 5 новых domain modules + extension catalog domain | NO conflict — Walking Skeleton соблюдает (но `CatalogDomainModule.kt:4` импортирует Koin DSL — pre-existing edge case) |
| 2. Activity/Fragment calls only ViewModel | YES — `MyQuestsScreen` Composable + Activity/Fragment-equivalent (Composable) | RISK: spec предполагает `MyQuestsViewModel` (compliant); pre-existing violation в `AppShellScreen.CatalogGridSection` (`AppShellScreen.kt:319` — `koinInject<CatalogRepository>()`) — **не вводится** этой фичей, но HomeQuestsRoot после полировки всё ещё использует тот же anti-pattern |
| 3. No bidirectional coupling | YES — 5 новых features импортируют через цепочку | NO conflict — все imports один-направленные. Cross-feature scanner подтвердил |
| 4. onDestroy not for business cleanup | NO — фича не добавляет Activity, только Compose + ViewModel | N/A |
| 5. DI exclusive binding | YES — новые Koin modules | RISK: `as Syncable` cast в `syncModule.kt:16-17` — не compile-safe, повторяется для новых repositories |
| 6. Walking Skeleton ownership | YES — domain skeleton уже сгенерирован spec phase | COMPLIANT — phase-01 будет integration-mode (data + presentation), не rewrite domain |
| 7. Scaffold file ownership | YES — phase-01 затронет `build.gradle.kts`, `settings.gradle.kts`, `libs.versions.toml`, `AndroidManifest.xml` | COMPLIANT — backend-dev owner |

Pre-existing violations не блокируют phase-01, но должны быть зафиксированы в design `03-decisions.md`.

---

## Summary of Backend Dependencies

Phase-01 implementation требует следующих backend changes (out-of-scope spec, но зависимости):

1. Firestore: создание 5 новых collections (quests, sections, themes, lessons, questions) + расширение catalogs документов 4 полями
2. Firestore: 5 composite indexes ручная регистрация в Firebase Console
3. Firebase Security Rules: deploy canonical block из `0-spec.md:241-273`
4. Server-side admin tool / Cloud Function для invariant A (upward propagation) и invariant B (downward cascade) — без них cascade sync не получит свежие nested entities

Без этих backend changes — client будет работать (offline-first), но never receive каскадные обновления. Документировать как hard dependency в `03-decisions.md` или `06-api-contract.md` design phase.
