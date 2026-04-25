---
date: 2026-04-22
researcher: Claude
commit: 7c52c200
branch: kmp-skillify-4.0
---

# Research: Home Quests & My Quests + Cascading Catalog Sync

## Summary

Feature `home-and-my-quests` затрагивает 6-уровневую каскадную синхронизацию `Catalog → Quest → Section → Theme → Lesson → Question`, дизайн-полировку существующего экрана "Домашние квесты" и создание нового экрана "Мои квесты".

**Walking Skeleton уже сгенерирован** на spec-фазе:
- `shared/feature/{quest,section,theme,lesson,question}/domain` — все 5 модулей с моделями, repository interfaces, use cases, fakes и JVM тестами (включая `CascadeDecision.shouldRecurseIntoChildren` pure function)
- `shared/core/catalog/domain/model/Catalog.kt:21` — расширен полями `version, contentsVersion, lastModifiedAt, archived` с дефолтами
- `shared/core/sync/{SyncStateRepository,InMemorySyncStateRepository}` — interface + in-memory stub созданы и тестируются

**Что уже реализовано в production**:
- `CatalogRepository.observeAll()` + `refreshFromRemote()` через full-replace (`replaceAll = deleteAll + insertAll`)
- `CatalogSpinner` Compose-компонент (готов к переиспользованию, но нигде не используется)
- `CatalogGrid` + `CatalogGridSection` для существующего "Домашние квесты"
- `SyncWorker` + `SyncWorkerFactory` + `syncModule` с двумя `Syncable` (UserStats, Catalog) и манипуляцией через `DrawerFooterAction.SyncNow`
- `authUidFlow` в `AppApplication.kt:41` (callback flow над `FirebaseAuth.AuthStateListener`)
- `StorageUrlResolver` + `FakeCatalogUrlResolver` для catalog картинок

**Критические gap'ы между spec и production**:
1. `CatalogEntity` в Room **не содержит** новых полей — schema v1, mapper не маппит `version/contentsVersion/lastModifiedAt/archived` (BLOCKER для phase-01)
2. `CatalogDto` отсутствуют те же поля — `FirestoreCatalogDtoMapper` не читает их из документа
3. `PersistenceModule` **не вызывает** `fallbackToDestructiveMigration()` (NFR#6 BLOCKER)
4. `CatalogRepositoryImpl.refreshFromRemote()` использует full-replace вместо delta-sync через cursor
5. `Navigator.goTo(LocalConfig.QuestCreateRoot)` из spec — `Navigator.goTo(destination: Destination)` принимает `Destination`, не `LocalConfig` → spec предлагает несуществующий API
6. `UserStatsRepository.currentAuthUid()` — метод не существует; `UserStats` model не содержит `uid`; есть только private `currentUidFlow` лямбда внутри `UserStatsRepositoryImpl`
7. Coil **3.1.0** в `libs.versions.toml:44`, spec ссылается на 3.4.0 (ADR-HLA-06)
8. Data-layer для всех 5 новых feature-модулей **отсутствует** (только domain существует)
9. `?v={version}` cache-busting не реализован нигде
10. `shared/feature/quiz/{domain,data}` модули зарегистрированы в settings.gradle.kts (строки 47-48), но содержат только `.gitkeep` — placeholder в `core/catalog/domain` ссылается на `quiz/`, реальный код в `quest/`

## Architecture Overview

### Существующая module structure (затронутая фичей)

```
shared/
  core/
    catalog/{domain,data}     # extend (уже частично)
    persistence               # add 5 entities, bump schema, add destructive migration
    sync                      # SyncStateRepository уже создан, не подключён
    test                      # пустой scaffold (только .gitkeep)
  feature/
    quest/domain              # Walking Skeleton ✓
    quest/data                # MISSING (директория есть, файлов нет, не в settings.gradle)
    section/domain            # Walking Skeleton ✓
    section/data              # MISSING
    theme/domain              # Walking Skeleton ✓
    theme/data                # MISSING
    lesson/domain             # Walking Skeleton ✓
    lesson/data               # MISSING
    question/domain           # Walking Skeleton ✓
    question/data             # MISSING
    app-shell/{domain,data}   # extend (LocalConfig + UserStatsRepository)
    quiz/{domain,data}        # ПУСТЫЕ scaffold (settings.gradle:47-48), не нужны
android/
  feature/
    quest/presentation        # MISSING (директория без файлов, не в settings.gradle)
    app-shell/presentation    # AppShellScreen.kt — менять routing
  core/
    designsystem              # add QuestCard + StarRating, polish CatalogGrid
platform/
  firebase/                   # add FirebaseQuestRemoteDataSource + 4 ещё, FirebaseModule per-feature
  android-services/           # SyncWorker — extend cascading steps
apps/
  android-next                # AppApplication — add Koin modules, di/SyncModule update
```

### Dependency chain (verified)

```
question → lesson → theme → section → quest → catalog (core)
                                              ↑
                                    (CatalogId reused)
```

Dependency one-way, нет bidirectional coupling. (Cross-feature scanner findings)

## Existing Patterns

### Catalog data layer pattern (для Quest/Section/.../Question)

- **Repository impl** `CatalogRepositoryImpl.kt:13` implements `CatalogRepository, Syncable`
- **Local data source**: `CatalogLocalDataSource` interface + `CatalogLocalDataSourceImpl` (тонкая обертка над DAO)
- **Remote data source**: `CatalogRemoteDataSource` interface (`fetchAll(): List<CatalogDto>`) — реализуется в `platform/firebase/`
- **DTO**: `CatalogDto.kt:3` — pure Kotlin data class (3 поля)
- **Mapper chain**: `Dto → Entity → Domain`, `Domain → Entity` (`CatalogMapper.kt`, `CatalogDtoMapper.kt`)
- **Koin module**: `catalogDataModule` — `single<CatalogLocalDataSource>` + `single<CatalogRepository>`
- **Sync wiring**: `syncModule.kt:16` — `get<CatalogRepository>() as Syncable`

### Test fakes pattern (`commonTest/fake/`)

- **DAO fake**: `MutableStateFlow<List<Entity>>` + explicit call counters, `emit(entities)` test helper (`FakeCatalogDao.kt`)
- **DataSource fake**: backing `Result<List<Dto>>`, `fetchAllCalls: Int` counter (`FakeCatalogRemoteDataSource.kt`)
- **Repository fake (domain)**: `MutableStateFlow<Map<Id, Entity>>`, `seed()`, `simulateRemote*()`, `setNextRefreshFailure()`, `snapshot()` (`FakeCatalogRepository.kt:47`)
- **URL resolver fake**: `callCount` + `shouldThrow`, returns `"https://fake.example.com/$path"` (`FakeCatalogUrlResolver.kt:5`)
- **Test framework**: kotlin.test + coroutines-test (`runTest`, `UnconfinedTestDispatcher`); НЕ Turbine — используется `.toList()` через `coroutineScope + launch + cancel`

### Koin module pattern

- **Domain module**: `val xyzDomainModule = module { factory { XyzUseCase(get()) } }` (`CatalogDomainModule.kt:6`)
- **Data module**: `val xyzDataModule = module { single<LocalDataSource> { ... }; single<Repository> { ... } }` (`CatalogDataModule.kt:11`)
- **Firebase module** (`platform/firebase/.../FirebaseCatalogModule.kt:12`): `single<RemoteDataSource>`, `single<StorageUrlResolver>(named("storageUrlResolver"))` — qualifier для disambiguation
- **App-shell data module** — единственный модуль-функция: `fun appShellDataModule(currentUidFlow: () -> Flow<String?>): Module` (для прокидывания auth flow)

### Walking Skeleton domain pattern (уже применён)

- `Quest`, `Section`, `Theme`, `Lesson`, `Question` — `data class` с `init { require(...) }` invariants
- `*Id` value classes (`@JvmInline value class`) с `isNotBlank()` invariant
- Repository interface с `observe*` Flow methods + `suspend refresh*(...): Result<Unit>`
- Use cases — `operator fun invoke(...)` (delegate-style) или `suspend operator fun invoke(...)` для refresh
- Pure logic functions (`shouldRecurseIntoChildren`, `computeStarFills`)

## Integration Points

- **WorkManager**: `SyncWorker` + `SyncWorkerFactory` + Koin `WorkerFactory` binding в `syncModule`. Enqueue в 3 местах: cold start (`AppApplication.kt:60-86` — periodic 1d + bootstrap one-time), manual (`DefaultRootComponent.kt:231` — REPLACE policy)
- **Firebase Firestore**: `FirebaseFirestore.getInstance()` напрямую в `FirebaseModule.kt:11`, `FirebaseCatalogModule.kt:14`. KMP — Google Android SDK, не GitLive — пакет `com.google.firebase.firestore.*` (только `androidMain`)
- **Firebase Auth**: `FirebaseAuth.getInstance()` в `FirebaseModule.kt`, `FirebaseUserStatsDataSource.kt:18` private property `currentUid`. `AppApplication.kt:41` — `callbackFlow<String?>` через `AuthStateListener`
- **Firebase Storage**: `FirebaseCatalogModule.kt:16-19` — `FirebaseStorage.getInstance().reference.child(path).downloadUrl.await().toString()` без query params
- **Coil 3.1.0**: `coil3.compose.AsyncImage` в `CatalogGrid.kt:71`. URL передаётся как-есть — Coil deduplicates by full URL (incl. query params)
- **Decompose 3.x navigation**: `LocalConfig` sealed (5 children), `StackNavigation<LocalConfig>` через `DefaultLocalTabComponent.kt:23` (`initialConfiguration = LocalConfig.MyQuestsRoot`)
- **Domain contract DI**: Koin `module {}` DSL импортируется в `shared/core/catalog/domain/di/CatalogDomainModule.kt:4` — нарушение pure-domain если строго трактовать `domain-models.md`, но проект-wide convention принят (KMP DI module live в domain)

## Detailed Findings

### 1. Catalog data layer — full-replace, без delta-полей

- **Location**: `shared/core/catalog/data/src/commonMain/kotlin/.../CatalogRepositoryImpl.kt:24-46`
- **Description**: `refreshFromRemote()` делает `remote.fetchAll()` (полный `collection.get()`), для каждого DTO резолвит pictureUrl через `storageUrlResolver(path)` (только если `path.startsWith("catalog-pictures/")`), затем `local.replaceAll(entities)` — `@Transaction { deleteAll(); insertAll(entities) }`
- **Dependencies**:
  - `CatalogLocalDataSource.kt:7` (4 методов: observeAll, replaceAll, findById, отсутствуют upsertById/deleteById)
  - `CatalogRemoteDataSource.kt:3` — единственный метод `fetchAll(): List<CatalogDto>` без курсора
  - `FirebaseCatalogRemoteDataSource.kt:13` — `firestore.collection("catalogs").get().await()`, без `where('lastModifiedAt', '>', ...)` фильтра
  - `FirestoreCatalogDtoMapper.kt:6-12` — читает только `getString("name")`, `getString("picturePath")`; **не читает** `version`, `contentsVersion`, `lastModifiedAt`, `archived`
- **Data flow**: Firestore → DTO (3 поля) → toEntity() (4 поля) → resolveUrl → replaceAll → Room → observeAll() Flow → toDomain() → Catalog (с дефолтами для новых полей)

### 2. Room schema — version 1, без новых полей

- **Location**: `shared/core/persistence/src/commonMain/kotlin/.../AppDatabase.kt:6`
- **Description**: `@Database(entities = [UserStatsEntity, CatalogEntity], version = 1)`. Identity hash: `650a89038e431fac65a585cd39791c05` (`schemas/.../1.json`)
- **CatalogEntity** (`CatalogEntity.kt:6-25`): 4 поля — `id TEXT NOT NULL`, `name TEXT NOT NULL`, `picturePath TEXT`, `pictureUrl TEXT`. Init invariants: `pictureUrl == null || startsWith("https://")`; `picturePath` не начинается с `https://`/`http://`/`gs://`
- **CatalogDao** (`CatalogDao.kt:11-29`): `observeAll`, `findById`, `insertAll(REPLACE)`, `deleteAll`, `replaceAll(@Transaction)`. Отсутствуют: `upsertByIdIfNewerVersion`, `deleteById`
- **Type Converters**: 0 (нет `@TypeConverter` нигде)
- **PersistenceModule** (`PersistenceModule.kt:11-16`): `Room.databaseBuilder(..., "schoolquiz.db").build()` — **без** `fallbackToDestructiveMigration()`
- **KSP**: `build.gradle.kts:39` — `add("kspAndroid", ...)`. JVM target имеет `AppDatabase_Impl.kt` в build output — нужно подтвердить наличие `kspJvm` или artefact stale build

### 3. Walking Skeleton — все 5 feature domains готовы

- **Location**: `shared/feature/{quest,section,theme,lesson,question}/domain/`
- **Description**: Каждый модуль содержит: `model/{Entity}.kt` + `model/{Entity}Id.kt`, `repository/{Entity}Repository.kt`, `use_case/Sync{Entity}sUseCase.kt`, `commonTest/fake/Fake{Entity}Repository.kt`, JVM тесты
- **Quest** (`shared/feature/quest/domain/.../model/Quest.kt:30`): все 13 полей включая `authorUid`, `visibleOn: Set<String>`, `averageRating: Float?`, `version`, `contentsVersion`, `lastModifiedAt`, `archived`
- **Section/Theme/Lesson** — идентичная структура: `id, parentId, title, order, version, contentsVersion, lastModifiedAt, archived`
- **Question** (leaf, `Question.kt:23`) — без `contentsVersion`: `id, lessonId, text, payload, language, order, version, lastModifiedAt, archived`
- **CascadeDecision** (`shared/feature/quest/domain/.../logic/CascadeDecision.kt:46`) — pure function `shouldRecurseIntoChildren(dtoCV, localCV?)` реализует Matrix 3
- **StarRating** (`StarRating.kt:67`) — `computeStarFills(averageRating, stars=3, fractionsPerStar=10): StarRatingModel`, sealed `StarFill` (Empty, Full, Partial(tenths))
- **Tests**: 28 в QuestValueObjectsTest, 15 в QuestUseCaseTest, 12 в CascadeDecisionTest, 14 в StarRatingTest, аналогичные для остальных
- **Dependencies (verified в build.gradle.kts:11-14)**: section→quest→catalog (core), theme→section, lesson→theme, question→lesson — линейная цепочка, no bidirectional coupling

### 4. SyncStateRepository — interface + InMemoryStub созданы, не wired

- **Location**: `shared/core/sync/src/commonMain/kotlin/.../SyncStateRepository.kt:16` (interface), `InMemorySyncStateRepository.kt:18` (impl)
- **Description**: 5 методов — `getCursor(collectionId)`, `setCursor(collectionId, value)`, `markCascadeInProgress(parentId, parentType, pendingChildIds)`, `markCascadeCompleted(parentId, parentType)`, `getPendingCascades(): List<PendingCascade>`. KDoc явно ссылается на `docs/features/home-and-my-quests/0-spec.md § Sync State architecture seam`
- **Implementation**: `MutableStateFlow<Map<String, Long>>` для cursors + `Mutex` + `MutableStateFlow<Map<String, PendingCascade>>` для pending
- **Dependencies**: pure Kotlin, без Android/SDK
- **Used by**: только `InMemorySyncStateRepositoryTest`. Нет Koin binding'а, не подключён к `SyncWorker`

### 5. SyncWorker + manual sync trigger

- **Location**: `platform/android-services/src/main/kotlin/.../SyncWorker.kt:9`
- **Description**: `CoroutineWorker(appContext, workerParameters, syncables)`. `doWork()` → `performSync(syncables)` → for-loop с fail-fast (`syncable.sync().onFailure { return false }`) → `Result.success()` или `Result.retry()`. Нет каскадной orchestration логики
- **Constants**: `WORK_NAME_PERIODIC = "periodic_sync"`, `WORK_NAME_MANUAL = "manual_sync"`, `WORK_NAME_BOOTSTRAP = "bootstrap_sync"`, `PERIODIC_INTERVAL = 1L day`
- **Backoff strategy**: НЕ задана — WorkManager использует default exponential (30s init, 5min cap)
- **Dependencies**:
  - `Syncable.kt:3` — `suspend fun sync(): Result<Unit>` — единственный контракт
  - `SyncWorkerFactory.kt:13-20` — Custom factory, проверяет `workerClassName`
  - `syncModule.kt:14-17` — `single<List<Syncable>> { listOf(get<UserStatsRepository>() as Syncable, get<CatalogRepository>() as Syncable) }` — hardcoded, **as Syncable cast** (runtime ClassCastException risk)
- **Manual trigger** (`DefaultRootComponent.kt:231-237`): `OneTimeWorkRequest<SyncWorker>` + `Constraints(NetworkType.CONNECTED)` + `enqueueUniqueWork(WORK_NAME_MANUAL, REPLACE, request)` → `sendEvent(RootEvent.SyncStarted)`. **Нет** `RootEvent.SyncCompleted`
- **`SyncNow` visibility** (`Visibility.kt:152`): debug build OR `developer >= LEVEL_1.points` — в release не виден обычным пользователям

### 6. UserStats / Firebase Auth UID — currentAuthUid() метода нет

- **Location**: `shared/feature/app-shell/domain/.../UserStatsRepository.kt:15`
- **Description**: 4 метода — `observeStats(): Flow<UserStats>`, `currentStats(): UserStats`, `setLocalDeveloperLevel(value)`, `refreshProfile(): Result<Unit>`. Нет `currentAuthUid()`
- **UserStats model** (`UserStats.kt:11`): 11 полей (nickname, avatarUrl, stars, gold, qualification и др.), **нет `uid` поля**
- **UserStatsEntity** (`UserStatsEntity.kt:7-8`): `@PrimaryKey val uid: String` — Room хранит uid как PK, но Domain не экспонирует
- **UserStatsRepositoryImpl** (`UserStatsRepositoryImpl.kt:22`): принимает `currentUidFlow: () -> Flow<String?>` как private dependency. `LOCAL_UID = "_local"` fallback (`UserStatsRepositoryImpl.kt:75`). `effectiveUidFlow` маппит `null → "_local"` — но `currentAuthUid()` должен возвращать именно Firebase UID без подстановки
- **AppApplication.kt:41-44** — `authUidFlow = callbackFlow<String?>` над `FirebaseAuth.AuthStateListener`. Эмитит `a.currentUser?.uid`. Передаётся в `appShellDataModule { authUidFlow }`
- **GitLive firebase-kotlin-sdk** (web research): `Firebase.auth.currentUser?.uid` доступен в commonMain (v2.4.0). Текущий код использует Google Android SDK напрямую, не GitLive — Auth UID доступен только в androidMain
- **Impact**: 4 места `FakeUserStatsRepository` (commonTest, presentation/fake, 2 inline в `KoinModuleWiringTest`) сломаются при добавлении 5-го метода в interface

### 7. Navigation — LocalConfig.QuestCreateRoot несовместим с текущим Navigator

- **Location**: `shared/feature/app-shell/domain/.../navigation/TabConfig.kt:22-30` (LocalConfig sealed)
- **Description**: 5 sealed children — `MyQuestsRoot, HomeQuestsRoot, SettingsRoot, DesignCatalogRoot, EmptyRoot`. `QuestCreateRoot` отсутствует
- **Destination sealed** (`Destination.kt:9-35`): `Back`, `SwitchTab`, `SelectSection(section)`, `OpenDrawer`, `CloseDrawer`, `OpenDesignCatalog`. **Нет `Push(config: LocalConfig)`**
- **Navigator.goTo signature**: `Navigator.goTo(destination: Destination)` — принимает `Destination`, не `LocalConfig`
- **Spec gap**: `0-spec.md:617` пишет `Navigator.goTo(LocalConfig.QuestCreateRoot)` — это compile error без нового `Destination.Push` или другого механизма
- **AppShellScreen.LocalTabContent** (`AppShellScreen.kt:301-311`): when-блок exhaustive по `LocalConfig` через `Labels.kt:85-95` (TabConfig.displayName) — добавление `QuestCreateRoot` потребует обновления обоих файлов
- **DefaultLocalTabComponent** (`LocalTabComponent.kt:23`): `initialConfiguration = LocalConfig.MyQuestsRoot`. `AppShellState.default()` стартует с `HomeQuestsRoot` (Visibility.kt:75) — `syncStack()` согласует
- **Push mechanism** (verified): `NavStack.push()` (`NavStack.kt:25-28`) существует на domain-уровне. `syncStack()` (`DefaultRootComponent.kt:263-271`) применяет к Decompose. Нужен новый `Destination.Push(config: TabConfig)` или другой path

### 8. Designsystem — CatalogSpinner существует, Coil 3.1.0

- **Location**: `android/core/designsystem/src/main/kotlin/.../components/`
- **CatalogSpinner.kt:34** — `ExposedDropdownMenuBox` + "Все категории" pseudo-item, signature `(items: List<CatalogDisplayItem>, selectedId: CatalogId?, onSelectionChanged: (CatalogId?) -> Unit, modifier: Modifier)`. **Нигде не используется** в production коде — готов к интеграции
- **CatalogGrid.kt:37**: `LazyVerticalGrid(GridCells.Fixed(2))` + `CatalogGridItem`. Текущая типография `MaterialTheme.typography.bodySmall` (`:81`), padding 4dp карточки + 8dp/4dp текста. AsyncImage `aspectRatio(1f)` через Coil 3.1.0 (`coil3.compose.AsyncImage`). Defence-in-depth: `pictureUrl?.takeIf { startsWith("https://") }`. Corner radius — Material3 default `shapes.medium = 12dp`
- **Coil version**: `libs.versions.toml:44` → `coil3 = "3.1.0"`. Spec говорит "Coil 3.4.0 (ADR-HLA-06)" — расхождение
- **Brand colors** (`Color.kt`): `GoogleBlue = Color(0xFF4285F4)` (`:13`) — для StarRating
- **Theme**: dark-only `MaterialTheme` (`SchoolQuizTheme.kt:26`)
- **BrandComponentsInvariantsTest** (`/test/.../BrandComponentsInvariantsTest.kt:24-65`): сканирует все `.kt` в `components/` — требует `@Preview` (`:54-65`), запрещает hardcoded `Color(0xFF...)` (`:24-35`). Новые `QuestCard.kt`/`StarRating.kt` обязаны соответствовать
- **UnderConstructionScreen** (`android/feature/app-shell/presentation/.../UnderConstructionScreen.kt:29`) — `(title: String, icon: ImageVector = Icons.Default.Construction)`. Используется для FAB destination `QuestCreateRoot`
- **CatalogDisplayItem** (`android/core/designsystem/.../model/CatalogDisplayItem.kt:13`) + extension `Catalog.toDisplayItem()` — pattern для будущего `QuestDisplayItem`

### 9. Firebase Storage + StorageUrlResolver

- **Location**: `shared/core/catalog/data/src/commonMain/kotlin/.../StorageUrlResolver.kt:3`
- **Description**: `fun interface` с `suspend operator fun invoke(path: String): String` — принимает relative path, возвращает HTTPS URL
- **Firebase impl** (`platform/firebase/.../FirebaseCatalogModule.kt:16-19`): SAM-лямбда — `FirebaseStorage.getInstance().reference.child(path).downloadUrl.await().toString()`. Никаких query params (`?v=`) не добавляет
- **Koin binding**: `single<StorageUrlResolver>(named("storageUrlResolver"))` — единственный named qualifier в проекте
- **Used by**: `CatalogRepositoryImpl.kt:33` (только) через prefix-guard `path.startsWith("catalog-pictures/")` — иначе resolver не вызывается
- **Quest path convention** (KDoc `Quest.kt:17`): `"quest-pictures/q-uuid.jpg"` — документировано, но не реализовано
- **`?v={version}` cache-busting**: НЕ реализовано нигде. Web research подтверждает: Coil 3 использует full URL как disk cache key — `?v=N` query param даёт different cache key. Можно делать через `pictureUrl + "?v=$version"` в `*RepositoryImpl` или extending `StorageUrlResolver` signature

### 10. Quiz module placeholder vs real Quest

- **Location**: `shared/feature/quiz/{domain,data}` (registered `settings.gradle.kts:47-48`), `shared/core/catalog/domain/model/Quest.kt`
- **Description**:
  - `shared/feature/quiz/domain` — пустой scaffold (только `.gitkeep`); namespace `com.tpov.schoolquiz.shared.feature.quiz.domain`
  - `shared/feature/quiz/data` — пустой scaffold
  - `android/feature/quiz/presentation` — пустой scaffold (`settings.gradle.kts:71`)
  - `shared/core/catalog/domain/model/Quest.kt:31` — TEMPORARY placeholder `data class Quest(id, catalogId, title)` + `value class QuestId`. KDoc: "Final Quest domain model lives in `shared/feature/quiz/domain/`" — но реально находится в `shared/feature/quest/domain/model/Quest.kt:30`
  - `shared/core/catalog/domain/repository/QuestRepository.kt:19` — placeholder с одним методом `save`
  - `shared/core/catalog/domain/use_case/CreateQuestUseCase.kt:28` — использует placeholder Quest
  - `shared/core/catalog/domain/.../fake/FakeQuestRepository.kt:13` — fake для placeholder
  - `CatalogDomainModule.kt:8` комментарий: "CreateQuestUseCase removed: QuestRepository has no production binding. Re-enable when quest feature provides a real QuestRepository"
- **Two QuestRepository interfaces** в проекте:
  1. `shared/core/catalog/domain/repository/QuestRepository.kt:19` — placeholder, 1 метод `save`
  2. `shared/feature/quest/domain/repository/QuestRepository.kt:23` — реальный, 4 метода

### 11. Cross-feature dependencies (verified, no bidirectional)

- **Verified imports**:
  - `shared/feature/quest/domain → shared/core/catalog/domain` (CatalogId)
  - `shared/feature/section/domain → shared/feature/quest/domain` (QuestId)
  - `shared/feature/theme/domain → shared/feature/section/domain` (SectionId)
  - `shared/feature/lesson/domain → shared/feature/theme/domain` (ThemeId)
  - `shared/feature/question/domain → shared/feature/lesson/domain` (LessonId)
- **No bidirectional coupling** обнаружено
- **Firebase SDK usage** (только в `platform/firebase`): catalog (Firestore + Storage), user_stats (Firestore + Auth), AppApplication (Auth listener). Никакой `shared/*` или `android/*` модуль не импортирует Firebase SDK напрямую
- **Pre-existing violation**: `AppShellScreen.CatalogGridSection` (`AppShellScreen.kt:319-329`) инжектирует `CatalogRepository` через `koinInject()` напрямую в Composable, минуя ViewModel — нарушение `use-cases.md`. Pre-existing, не вводится фичей

### 12. Tests pattern (existing — для копирования)

- **CatalogRepositoryImplTest** (`commonTest/.../CatalogRepositoryImplTest.kt`) — 9 тестов CF-11..CF-18 через `FakeCatalogLocalDataSource + FakeCatalogRemoteDataSource + FakeCatalogUrlResolver`
- **Integration tests** (по journey): `CatalogFirstFetchIntegrationTest`, `CatalogWarmCacheIntegrationTest`, `CatalogOfflineEmptyIntegrationTest` — каждый journey покрыт отдельным файлом
- **Mapper tests**: `CatalogMapperTest.kt` — 4 round-trip тестов, без fakes, pure Kotlin
- **Domain contract tests** (`shared/core/catalog/domain/src/commonTest/.../CatalogRepositoryContractTest.kt`) — 14 тестов через `FakeCatalogRepository` + `ObserveCatalogsUseCase`
- **Convention**: catalog data использует `commonTest`, app-shell data использует `jvmTest` — разрыв. Spec `0-spec.md:511` требует следовать catalog → `commonTest`
- **Run command**: `:shared:core:catalog:data:jvmTest` (canonical из spec); convention plugin (`KmpLibraryConventionPlugin.kt:19`) добавляет `jvm()` target — `jvmTest` доступен для всех KMP modules

### 13. Coil 3.1.0 actual vs 3.4.0 spec

- **Location**: `gradle/libs.versions.toml:44`
- **Description**: `coil3 = "3.1.0"`. Spec `0-spec.md:1028` ссылается на ADR-HLA-06 "Coil 3.4.0"
- **Web research**: версия 3.1.0 имеет работающий cache-by-URL pattern. Critical bug `CacheControlCacheStrategy` в 3.0.2 — статус для 3.1.0 не подтверждён, но `?v=` URL-based pattern не затронут (это full-URL strategy, не header-based)
- **Coil 3 breaking changes** (web): `AsyncImagePainter.state` — `StateFlow` (`collectAsState()`); `modelEqualityDelegate` removed; `ImageRequest` с inline lambda триггерит recompose

### 14. Legacy reference — StructureDataLocal (5 уровней, не 6)

- **Location**: `legacy/common/src/main/java/com/tpov/common/data/model/local/StructureDataLocal.kt:9`
- **Description**: Дерево квизов в памяти, узлы хранят `children: MutableList<StructureDataLocal>?` (не flat). Иерархия — **5 уровней**, не 6: `event → category → subCategory → subsubCategory → quiz` (`PathStructure.kt:7`)
- **rollbackStructureData** (`SettingLocalDBUseCase.kt:9-11`) — **тело пустое**. Rollback не реализован в legacy. SyncWorker call sites: `:152, :159, :166`. Reference только для будущего `SyncStateRepository` rollback
- **Spec расхождение**: 6 уровней (Catalog→Quest→Section→Theme→Lesson→Question) vs legacy 5 уровней

## Cross-Feature Interactions

### Dependency Graph (verified imports)

| Feature A | → | Feature B | Mechanism | File:line | Documented in ADR? |
|-----------|---|-----------|-----------|-----------|---------------------|
| `shared/feature/quest/domain` | → | `shared/core/catalog/domain` | direct import (CatalogId) | `Quest.kt:3`, `build.gradle.kts:11` | Implicit (Walking Skeleton) |
| `shared/feature/section/domain` | → | `shared/feature/quest/domain` | direct import (QuestId) | `Section.kt:3`, `build.gradle.kts:11` | Implicit |
| `shared/feature/theme/domain` | → | `shared/feature/section/domain` | direct import (SectionId) | `Theme.kt:3`, `build.gradle.kts:11` | Implicit |
| `shared/feature/lesson/domain` | → | `shared/feature/theme/domain` | direct import (ThemeId) | `Lesson.kt:3`, `build.gradle.kts:11` | Implicit |
| `shared/feature/question/domain` | → | `shared/feature/lesson/domain` | direct import (LessonId) | `Question.kt:3`, `build.gradle.kts:11` | Implicit |
| `android/feature/app-shell/presentation` | → | `shared/core/catalog/domain` | direct import (CatalogRepository) | `AppShellScreen.kt:41` | Pre-existing |
| `platform/firebase` | → | `shared/core/catalog/data` | direct import (CatalogRemoteDataSource) | `FirebaseCatalogRemoteDataSource.kt:4` | Pre-existing |
| `apps/android-next` | → | `platform/firebase` | Firebase.getInstance + Koin module | `AppApplication.kt:18-19` | Pre-existing |

### Bidirectional Coupling Risks

**Не обнаружено**. Все verified imports — однонаправленные.

### Shared SDK Across Features

| SDK | Used by | Recommended pattern (web) | Current integration |
|-----|---------|--------------------------|---------------------|
| Firebase Firestore | platform/firebase (catalog + user-stats) | Single `FirebaseFirestore.getInstance()` per process — Firebase SDK is process-singleton | Direct `getInstance()` calls in `FirebaseModule.kt:11`, `FirebaseCatalogModule.kt:14` — для Quest нужно следовать тому же pattern |
| Firebase Auth | platform/firebase + AppApplication | Single instance + AuthStateListener | `AppApplication.kt:41` `callbackFlow<String?>` уже создан, использовать его |
| Firebase Storage | platform/firebase (catalog only) | Single `FirebaseStorage.getInstance()`, `StorageUrlResolver` Koin singleton | Один `named("storageUrlResolver")` — переиспользовать для quest |
| Coil 3 | android/core/designsystem | URL with `?v=$version` — different cache key, automatic refresh | Pattern не реализован — внедрить в `*RepositoryImpl` mapping |
| WorkManager | platform/android-services | `enqueueUniquePeriodicWork`/`enqueueUniqueWork` (already in use) + `setBackoffCriteria(EXPONENTIAL, 10s, MIN)` | Backoff не задан — добавить в `OneTimeWorkRequest` builders |

### Undocumented Patterns (no blockers)

- `as Syncable` runtime cast в `syncModule.kt:16-17` — нет compile-time гарантии. При добавлении новых `Syncable` (Quest/Section/Theme/Lesson/Question RepositoryImpls) тот же cast применим, но риск ClassCastException
- `koinInject()` для Repository в Composable (`AppShellScreen.CatalogGridSection`) — нарушение `use-cases.md`. Pre-existing, не вводится фичей. Spec design phase должен решить: создавать `HomeQuestsViewModel` или сохранить для совместимости

## State Matrix Validation

### Пропущенные условия (предложить пользователю добавить в матрицу)

- **`UserStats.guest()` state**: `UserStatsRepositoryImpl.kt:75` использует `LOCAL_UID = "_local"` fallback при null UID. State Matrix не описывает interaction между guest state и `MyQuestsScreen` через UID-based query. (Spec Journey 11 покрывает guest — empty state без login CTA, ViewModel не вызывает repo при uid=null. Это согласовано с реальным поведением)
- **`isChangingConfigurations` для `MyQuestsScreen`**: spec не упоминает (`MyQuestsScreen` — Composable, не Activity, lifecycle через ViewModel/Decompose). N/A для Compose, но Decompose `BackCallback` (`AppShellScreen.kt:127`) требует обработки.

### Несостыковки (матрица vs код)

- **Cursor field — `lastModifiedAt` vs `version`**: spec FR#14 (`0-spec.md:62`) — `where('lastModifiedAt', '>', catalogsCursor)`; spec Scope→Data layer (`0-spec.md:175`) — `where('version', '>', local.maxVersion)`. **Внутреннее противоречие в спеке**. `FakeCatalogRepository.refreshFromRemote()` (`shared/core/catalog/domain/.../fake/FakeCatalogRepository.kt:81`) реализует через `lastModifiedAt` — confirmed правильное направление. Spec нужно унифицировать (или один cursor для delta-pull, второй version для upsert monotonicity).
- **`CatalogRepository.observeAll()` фильтр `!archived`**: spec FR#20 (`0-spec.md:111`) требует фильтр; реальный код `CatalogRepositoryImpl.kt:20` — `local.observeAll().map { list -> list.map { it.toDomain() } }` без фильтра. `AppShellScreen.kt:322` тоже не фильтрует. State Matrix 1 говорит о DELETE при archived — но локально удаление работает только при sync delete, не при отображении.
- **`CatalogRepository.refreshFromRemote()` без cursor**: текущая signature `suspend fun refreshFromRemote(): Result<Unit>` (`CatalogRepository.kt:47`); State Matrix предполагает cursor. Либо менять signature interface, либо `*RepositoryImpl` читает cursor из `SyncStateRepository` внутренне.

### Непокрытые комбинации

- **`Catalog.archived = true` + `Quest.catalogId = removed_catalog`**: orphan Quest в Room. State Matrix 1 говорит "DELETE local" для catalog, но не описывает поведение orphan детей. Spec FR#22 (`0-spec.md:597`): "Все quests с `catalogId=old` становятся orphan ... остаются в Room, но показываются с placeholder caption 'Unknown'". Реальный код не реализует placeholder — UI не имеет JOIN catalog ↔ quest.
- **`Quest.visibleOn` filter при role change**: Matrix 4 описывает per-role visibility. Реальный код в `availableShelves` MVP = `{"home", "arena"}` hardcoded — нет реальной role-based composition.

### Domain Contract Mismatches

- **`Navigator.goTo(LocalConfig.QuestCreateRoot)` (spec FR#21, `0-spec.md:617`) vs Navigator API**: `Navigator.goTo(destination: Destination)` не принимает `LocalConfig`. Это compile error. Spec design должен решить: добавлять `Destination.Push(config: TabConfig)` или альтернативный метод в `RootComponent`.
- **`UserStatsRepository.currentAuthUid()` (spec FR#21, `0-spec.md:121`) vs interface**: метод не существует. Расхождение. Можно: (a) добавить method в interface; (b) расширить `UserStats.uid: String?`; (c) использовать `authUidFlow` напрямую через DI.
- **Coil 3.4.0 (spec) vs 3.1.0 (libs.versions.toml:44)**: расхождение версии. Возможно нужно bump в этой фиче или скорректировать ADR-HLA-06.
- **`shared/feature/quiz/{domain,data}` (registered, empty) vs реальный код в `shared/feature/quest/`**: spec говорит про новые модули `shared/feature/quest/...` — реально создано всё в `quest/`. Quiz — пустой dead scaffold. Placeholder Quest в `core/catalog/domain` ссылается на `quiz/`. Нужно решение: удалить `quiz/` или сохранить?

## Conditional Documents Needed

Spec уже содержит 11 conditional doc references в `README.md`. На основе research findings:

| Document | Needed? | Rationale |
|----------|---------|-----------|
| `01-architecture.md` | YES | новые 5 feature data модулей + cascading orchestrator + sync state seam |
| `02-behavior.md` | YES | 11 user journeys + cascading sync FSM + guest handling |
| `03-decisions.md` | YES | 35+18 решений + новые в research (`quiz/` cleanup, Coil version, Navigator.Push) |
| `04-testing.md` | YES | расширение существующих catalog tests + 5 новых contract test suites + integration tests + UI tests |
| `05-prior-art.md` | YES | reference legacy `SyncInteractor.rollbackStructureData` (пустой), 5-уровневая иерархия legacy |
| `06-api-contract.md` | YES | Firestore schema (6 collections) + composite indexes (5 indexes) + security rules (canonical block) |
| `07-events.md` | OPTIONAL | если cascading sync вводит новый telemetry/event log — иначе пропустить |
| `08-storage-model.md` | YES | Room schema bump (5 entities + CatalogEntity extension), TypeConverter для `Set<String>`, destructive migration justification |

Walking Skeleton ready для phase-01 implementation, но с критическими delta:
- `CatalogEntity` schema mismatch — нужно решить strategy (extend existing CatalogEntity или новый `CatalogEntityV2`?)
- `CatalogDto` field expansion — синхронизировать с Firestore mapper
- Cascading sync orchestration — новый класс или расширение `SyncWorker.performSync`?
- Backend cloud functions для invariant A/B — out-of-scope spec, но зависимость

## Constraints

### From PROJECT_STRUCTURE.md + ADRs (verified против real code)

- **KMP target**: `jvm() + androidTarget()` — convention plugin `KmpLibraryConventionPlugin.kt:19`. Все 5 новых feature data модулей должны следовать
- **Koin DI**: per-feature module — pattern verified в `CatalogDataModule`, `CatalogDomainModule`. ADR-0009
- **Material3 + Compose**: dark-only theme `SchoolQuizTheme.kt:26`. ADR-0010
- **Coil 3.1.0** (не 3.4.0): для AsyncImage в QuestCard. Расхождение с ADR-HLA-06
- **Decompose 3.x**: sealed `Config` pattern. Adding `LocalConfig.QuestCreateRoot` — 3 файла нужно изменить (LocalConfig.kt, AppShellScreen.kt when, Labels.kt when)
- **Firebase Android SDK** (не GitLive): Auth UID в androidMain only. Если нужен KMP-wide → миграция на GitLive 2.4.0 — out-of-scope

### From real code (additional)

- **`fallbackToDestructiveMigration()` отсутствует** в `PersistenceModule.kt:11-16` — должен быть добавлен
- **TypeConverter для `Set<String>`** для `Quest.visibleOn` отсутствует — `@TypeConverter` нужен в persistence модуле
- **`kspAndroid` only** в `shared/core/persistence/build.gradle.kts:39` — для KMP entities нужна верификация `kspJvm` (artefact в `build/generated/ksp/jvm/jvmMain/` существует, неизвестно как генерируется)
- **`BrandComponentsInvariantsTest`** проверяет `@Preview` для всех файлов в `components/` — `QuestCard.kt`, `StarRating.kt` обязаны иметь preview
- **`as Syncable` runtime cast** в `syncModule.kt:16-17` — расширение для quest/section/theme/lesson/question repositories требует тот же pattern

## Open Questions

1. ~~**`Navigator.goTo(LocalConfig.QuestCreateRoot)`**~~ — **CLOSED 2026-04-22 (Decision #41)**: добавляется `Destination.OpenQuestCreate` (data object, по аналогии с существующим `OpenDesignCatalog`). Push `LocalConfig.QuestCreateRoot` в локальный stack через `AppShellTransitions.navigate()` case.

2. ~~**`UserStatsRepository.currentAuthUid()`**~~ — **CLOSED 2026-04-22 (Decision #42)**: новый `AuthRepository` interface в `shared/feature/app-shell/domain/repository/`. Walking Skeleton дополнен (AuthRepository.kt + FakeAuthRepository + AuthRepositoryContractTest 6 тестов). `AuthRepositoryImpl` (phase-01) оборачивает существующий `authUidFlow` из `AppApplication.kt:41`.

3. ~~**Coil version**~~ — **CLOSED 2026-04-22 (Decision #43)**: bump `gradle/libs.versions.toml:44` 3.1.0 → 3.4.0. Текущее использование (`AsyncImage` в `CatalogGrid.kt:71`) breaking changes (StateFlow для AsyncImagePainter.state, removed modelEqualityDelegate) не задевают.

4. ~~**`shared/feature/quiz/{domain,data}` модули**~~ — **CLOSED 2026-04-22 (Decision #44)**: cleanup в scope phase-01. Удалить `quiz/` scaffold из settings.gradle.kts (строки 47-48, 71). Удалить placeholder Quest+QuestRepository+CreateQuestUseCase+FakeQuestRepository+QuestCatalogLinkTest из `shared/core/catalog/domain`. Канонический Quest остаётся в `shared/feature/quest/domain`.

5. ~~**Cursor field — `lastModifiedAt` или `version`**~~ — **CLOSED 2026-04-22**: используется `lastModifiedAt` (FR#14, FakeCatalogRepository уже реализует). Spec обновлён — Scope→Data layer (`0-spec.md`) теперь говорит `where('lastModifiedAt', '>', cursor)`. `version` остаётся для upsert monotonicity (Decision #15 superseded by #31, см. раздел Sync Algorithm).

---

> **Open Questions 6-15 — RESOLVED 2026-04-22**
>
> Все 10 design-level вопросов закрыты пользовательскими решениями (Decisions #49-55) после Codex Round 6. Каждый имеет статус CLOSED с reference на соответствующий Decision. Спойлер для design phase: structure orchestrator через recursion + sealed `SyncLevel` (#49); `?v=Quest.version` MVP с TODO для `pictureVersion` (#50); Decompose Component для обоих экранов (#51); DAO query фильтр archived (#52); extend CatalogEntity (#53); backend Cloud Function — documented hard dependency (#54); commonTest для data tests (#55).

6. ~~**`CatalogRepository.refreshFromRemote()` signature**~~ — **CLOSED 2026-04-22 (implied by Decision #49)**: cursor читается внутри `CascadingSyncOrchestrator` через `SyncStateRepository.getCursor("catalogs")` (variant b). Signature `CatalogRepository.refreshFromRemote(): Result<Unit>` остаётся без cursor параметра — breaking interface change не нужен. Существующие тесты `CatalogRepositoryImplTest` обновляются для нового delta-pull behavior, но сигнатура та же.

7. ~~**`CatalogRepository.observeAll()` filter `!archived`**~~ — **CLOSED 2026-04-22 (Decision #52)**: DAO query — `SELECT * FROM <table> WHERE archived = 0 ORDER BY ...`. Применяется ко всем 6 entities. Защита-инвариант: даже если sync race condition оставил archived row в Room — `observeAll()` его не вернёт. SQL filter эффективнее `.filter { }`.

8. ~~**`SyncWorker` cascading orchestration**~~ — **CLOSED 2026-04-22 (Decision #49)**: recursive `syncCascade(level: SyncLevel, parentIds: Set<String>)` функция с sealed `SyncLevel` (6 instances) и map `SyncLevel → repository`. Реализуется как один `CascadingSyncOrchestrator implements Syncable` в `shared/core/sync/`. Глубина 6 — без stack overflow. Чище и расширяемее чем 6 hardcoded Syncable.

9. ~~**`?v={version}` URL pattern**~~ — **PARTIALLY CLOSED 2026-04-22 (Decision #50)**: MVP использует `Quest.version` (всю сущность) — добавляется в `*RepositoryImpl` после `storageUrlResolver(path)` как `pictureUrl = resolved + "?v=$version"`. **TODO для отдельной задачи**: granular invalidation через `pictureVersion` поле (инкрементируется только при upload новой картинки) — оптимизация polish.

10. ~~**`shared/core/catalog/domain/model/Quest.kt` placeholder**~~ — **CLOSED 2026-04-22 (Decision #44)**: уже удалено вместе с `QuestRepository`, `CreateQuestUseCase`, `FakeQuestRepository`, `QuestCatalogLinkTest`. Канонический Quest — в `shared/feature/quest/domain/model/Quest.kt`.

11. ~~**`CatalogEntity` extension strategy**~~ — **CLOSED 2026-04-22 (Decision #53)**: extend existing — добавить 4 колонки в `CatalogEntity`, schema version 1→2, `fallbackToDestructiveMigration()` (consistent с Decision #26). Pre-production data loss приемлемо. 7 тестов с 4-arg constructor обновляются с named args.

12. ~~**Cloud Function для server invariant A/B propagation**~~ — **CLOSED 2026-04-22 (Decision #54)**: documented hard backend dependency в `06-api-contract.md` (design phase). Out-of-scope client implementation. Без серверной реализации клиент работает в degraded mode (catalog-level updates only). Backend track реализуется параллельно.

13. ~~**`SyncNow` visibility в release**~~ — **CLOSED 2026-04-22**: подтверждено dev-only (`Visibility.kt:152` — debug build OR `developer >= LEVEL_1.points`). Никаких изменений не требуется — фича phase-01 использует существующий dev-trigger.

14. ~~**`AppShellScreen.CatalogGridSection` direct repository injection**~~ — **CLOSED 2026-04-22 (Decision #51)**: рефакторим параллельно. Создаётся `HomeQuestsComponent` (Decompose Component, не AndroidX ViewModel) одновременно с `MyQuestsComponent`. `CatalogGridSection` Composable удаляется — заменяется `HomeQuestsScreen(component)`. Оба экрана консистентны, pre-existing нарушение `use-cases.md` закрыто.

15. ~~**`shared/feature/app-shell/data/jvmTest/` vs `commonTest/`**~~ — **CLOSED 2026-04-22 (Decision #55)**: `commonTest` — следовать catalog pattern. KMP-pure, тесты переиспользуются на Android и JVM targets. App-shell `jvmTest` — historical legacy, design phase может зафиксировать `commonTest` как proper convention.
