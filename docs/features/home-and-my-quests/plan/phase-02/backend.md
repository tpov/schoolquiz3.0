---
phase: 02
role: backend-dev
---

# Phase-02 Backend Tasks

Walking Skeleton domain interfaces существуют и не переписываются. Все задачи — data layer integration поверх domain interfaces.

---

## Pattern Invariants

- НИКОГДА не модифицировать файлы в `shared/feature/*/domain/` — Walking Skeleton ownership (Invariant #6)
- Все `RepositoryImpl` классы принимают `syncStateRepo: SyncStateRepository` через constructor injection
- Паттерн cursor advancement: `setCursor` вызывается ТОЛЬКО после `Result.success` каждого step
- `QuestRepositoryImpl` — два независимых Firestore запроса (не sequential), merge/dedupe по id клиент-сайд
- Koin bindings: один подход per class (`single<Interface> { Impl(get(), get()) }`) — нет дублирования
- Scaffold ownership: build.gradle.kts, settings.gradle.kts, AppApplication.kt — ТОЛЬКО backend-dev

---

## 1. Update CatalogRemoteDataSource interface

- **Файл:** `shared/core/catalog/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogRemoteDataSource.kt`
- **Тип:** interface
- **Сигнатура:** `interface CatalogRemoteDataSource { suspend fun fetchChangedSince(cursor: Long): List<CatalogDto> }`
- **Вход:** `cursor: Long` — Unix millis (0L = first sync, returns все)
- **Поведение / Выход:**
  - Заменяет `fetchAll()` → `fetchChangedSince(cursor: Long)`
  - Возвращает все каталоги где `lastModifiedAt > cursor`
- **Edge cases:**
  - cursor=0L → Firestore query WHERE lastModifiedAt > 0 → возвращает всё (первый sync)
  - Пустой результат → не вызывать setCursor (no docs = no cursor advance)
- **Depends on:** CatalogDto (с 4 новыми полями — phase-01)
- **Canonical reference:** `06-api-contract.md` §9

---

## 2. Update FirebaseCatalogRemoteDataSource

- **Файл:** `platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/catalog/FirebaseCatalogRemoteDataSource.kt`
- **Тип:** class
- **Сигнатура:** `class FirebaseCatalogRemoteDataSource(firestore: FirebaseFirestore) : CatalogRemoteDataSource`
- **Вход:** `cursor: Long`
- **Поведение / Выход:**
  - Firestore query: `firestore.collection("catalogs").whereGreaterThan("lastModifiedAt", Timestamp(cursor/1000, ((cursor%1000)*1000000).toInt())).get().await()`
  - Или через `whereLessThan` нет — `whereGreaterThan` с Timestamp conversion
  - Маппит каждый DocumentSnapshot через `FirestoreCatalogDtoMapper` (обновлён в phase-01)
  - Возвращает `List<CatalogDto>` (все документы с lastModifiedAt > cursor)
- **Edge cases:**
  - cursor=0 → Timestamp(0, 0) → все документы с lastModifiedAt > epoch start
  - Firestore Timestamp → Long: `snapshot.getTimestamp("lastModifiedAt")?.toDate()?.time ?: 0L`
  - Pagination (>500 docs): для MVP без pagination (acceptable per spec)
- **Depends on:** FirestoreCatalogDtoMapper (phase-01 updated), Firestore SDK
- **Canonical reference:** `06-api-contract.md` §9; `2-grounding.md` Problem 1 Flow Trace
- **Rationale:** P1 fix — full-replace замена на cursor-based query

---

## 3. Rewrite CatalogRepositoryImpl.refreshFromRemote()

- **Файл:** `shared/core/catalog/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogRepositoryImpl.kt`
- **Тип:** class
- **Сигнатура:** `class CatalogRepositoryImpl(local: CatalogLocalDataSource, remote: CatalogRemoteDataSource, storageUrlResolver: StorageUrlResolver, syncStateRepo: SyncStateRepository) : CatalogRepository, Syncable`
- **Вход:** добавить `syncStateRepo: SyncStateRepository` в constructor
- **Поведение / Выход:**
  - `refreshFromRemote()`:
    1. `val cursor = syncStateRepo.getCursor("catalogs")`
    2. `val dtos = remote.fetchChangedSince(cursor)`
    3. Для каждого dto: resolve URL, создать entity
    4. Если `dto.archived == true`: `local.deleteById(dto.id)`; иначе `local.upsertByIdIfNewerVersion(entity)`
    5. При success: `val newCursor = max(cursor, dtos.maxOfOrNull { it.lastModifiedAt } ?: cursor)` + `syncStateRepo.setCursor("catalogs", newCursor)` только если dtos.isNotEmpty()
    6. Return `Result.success(Unit)`
  - При CancellationException: rethrow (не оборачивать)
  - При других Exception: return `Result.failure(e)` (NO cursor advance)
  - `observeAll()` — без изменений (delegated to local.observeAll())
  - `getById()` — без изменений
- **Edge cases:**
  - dto.archived = false + local absent → upsert (INSERT) — Matrix 1.2
  - dto.archived = true + local absent → SKIP (don't try to delete nonexistent) — Matrix 1.1
  - dto.archived = false + dto.version <= local.version → DAO ignores (atomic check) — Matrix 1.5/1.6
  - dtos empty → no cursor advance (cursor stays unchanged)
- **Depends on:** CatalogLocalDataSource (phase-01 with upsert/delete), SyncStateRepository (phase-01/phase-02)
- **Canonical reference:** `06-api-contract.md` §2.1

---

## 4. Add SyncStateRepository binding to SyncModule

- **Файл:** `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt`
- **Тип:** Koin module update
- **Сигнатура:** `val syncModule = module { ... }` с добавленным SyncStateRepository binding
- **Вход:** существующий syncModule без SyncStateRepository
- **Поведение / Выход:**
  - Добавить: `single<SyncStateRepository> { InMemorySyncStateRepository() }`
  - Importы: `com.tpov.schoolquiz.shared.core.sync.SyncStateRepository`, `com.tpov.schoolquiz.shared.core.sync.InMemorySyncStateRepository`
  - List<Syncable> пока НЕ меняется (это делается в phase-03 при добавлении CascadingSyncOrchestrator)
- **Edge cases:**
  - `InMemorySyncStateRepository` уже создан в walking skeleton — просто добавить Koin binding
- **Depends on:** existing `shared/core/sync` module
- **Canonical reference:** `06-api-contract.md` §13 syncModule
- **Rationale:** P4 fix — SyncStateRepository существует но не подключён через DI; CatalogRepositoryImpl (task #3) требует инжекции

---

## 5. Update CatalogDataModule — inject SyncStateRepository

- **Файл:** `shared/core/catalog/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/di/CatalogDataModule.kt`
- **Тип:** Koin module update
- **Сигнатура:** `val catalogDataModule = module { ... }` — обновлённый
- **Вход:** существующий module
- **Поведение / Выход:**
  - Обновить `single<CatalogRepository>` binding — добавить `syncStateRepo = get()` parameter
  - `single<CatalogLocalDataSource> { CatalogLocalDataSourceImpl(get<AppDatabase>().catalogDao()) }` — без изменений
- **Depends on:** SyncStateRepository в Koin graph (task #4)
- **Canonical reference:** internal (no api-contract entry)

---

## 6. Create Quest Data Module

### 6a. Create build.gradle.kts for quest/data

- **Файл:** `shared/feature/quest/data/build.gradle.kts`
- **Тип:** build script
- **Сигнатура:** KMP library convention plugin + dependencies on quest/domain + persistence + sync + koin
- **Поведение / Выход:**
  - `id("schoolquiz.kmp.library")` + `id("com.google.devtools.ksp")`
  - dependencies: `implementation(project(":shared:feature:quest:domain"))`, `implementation(project(":shared:core:persistence"))`, `implementation(project(":shared:core:sync"))`, `implementation(libs.koin.core)`
  - add `"kspJvm"` for room if needed
- **Canonical reference:** internal — follow `:shared:core:catalog:data` build.gradle.kts pattern

### 6b. Create QuestRemoteDataSource interface

- **Файл:** `shared/feature/quest/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/quest/data/QuestRemoteDataSource.kt`
- **Тип:** interface
- **Сигнатура:** `interface QuestRemoteDataSource`
- **Вход/Поведение / Выход:**
  - `suspend fun fetchOwnChanged(authorUid: String, catalogIds: Set<String>, cursor: Long): List<QuestDto>` — Query A (own quests by authorUid + catalogId)
  - `suspend fun fetchPublicChanged(shelves: Set<String>, cursor: Long): List<QuestDto>` — Query B (public by visibleOn)
- **Edge cases:**
  - `catalogIds` batch ≤30 (Firestore in-filter limit) — caller (Orchestrator) splits into chunks ≤30 before calling
  - Both methods may return same quest id → deduplication in RepositoryImpl
- **Canonical reference:** `06-api-contract.md` §9

### 6c. Create QuestDto

- **Файл:** `shared/feature/quest/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/quest/data/dto/QuestDto.kt`
- **Тип:** data class
- **Сигнатура:** `data class QuestDto(val id: String, val catalogId: String, val authorUid: String, val title: String, val picturePath: String?, val visibleOn: List<String>, val averageRating: Double?, val averageRatingCount: Int, val version: Long, val contentsVersion: Long, val lastModifiedAt: Long, val archived: Boolean)`
- **Edge cases:** `averageRating` — `Double?` в Firestore, конвертируется в `Float?` в domain через mapper
- **Canonical reference:** `06-api-contract.md` §8.2

### 6d. Create QuestDtoMapper

- **Файл:** `shared/feature/quest/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/quest/data/mapper/QuestDtoMapper.kt`
- **Тип:** object с extension functions
- **Сигнатура:** `object QuestDtoMapper { fun QuestDto.toEntity(pictureUrl: String?): QuestEntity }`
- **Поведение / Выход:**
  - `visibleOn: List<String>` → `Set<String>` (toSet())
  - `averageRating: Double?` → `Float?` (.toFloat())
  - `pictureUrl` — параметр (resolved by repository before calling mapper)
- **Canonical reference:** internal (no api-contract entry)

### 6e. Create QuestMapper

- **Файл:** `shared/feature/quest/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/quest/data/mapper/QuestMapper.kt`
- **Тип:** object с extension functions
- **Сигнатура:** `object QuestMapper { fun QuestEntity.toDomain(): Quest }`
- **Поведение / Выход:** маппит все 13 полей QuestEntity → Quest domain object; `authorUid`, `catalogId` → через value classes `CatalogId(value)`
- **Canonical reference:** `06-api-contract.md` §1.2

### 6f. Create QuestLocalDataSource interface + impl

- **Файл:** `shared/feature/quest/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/quest/data/QuestLocalDataSource.kt`
- **Тип:** interface + class
- **Сигнатура:** `interface QuestLocalDataSource`; `class QuestLocalDataSourceImpl(dao: QuestDao) : QuestLocalDataSource`
- **Поведение / Выход:**
  - `fun observeMyQuests(authorUid: String, catalogId: String?): Flow<List<QuestEntity>>` — dispatch to `dao.observeMyQuests` или `observeMyQuestsInCatalog` по null check
  - `suspend fun upsertByIdIfNewerVersion(entity: QuestEntity)` — delegate to DAO (распаковать поля)
  - `suspend fun deleteById(id: String)` — delegate to DAO
  - `suspend fun findById(id: String): QuestEntity?`
- **Depends on:** QuestDao (phase-01 task #8)
- **Canonical reference:** internal (no api-contract entry)

### 6g. Create QuestRepositoryImpl

- **Файл:** `shared/feature/quest/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/quest/data/QuestRepositoryImpl.kt`
- **Тип:** class
- **Сигнатура:** `class QuestRepositoryImpl(local: QuestLocalDataSource, remote: QuestRemoteDataSource, urlResolver: StorageUrlResolver, syncStateRepo: SyncStateRepository) : QuestRepository`
- **Вход:** 4 constructor params
- **Поведение / Выход:**
  - `observeMyQuests(authorUid, catalogId?)` — delegates to local; maps entity→domain
  - `observeByShelf(shelf)` — delegates to local `observeByShelf`
  - `getById(id)` — delegates to local
  - `refreshFromRemote(currentUserUid, availableShelves, catalogIdsToSync, cursor)`:
    1. Query A (if currentUserUid != null): `remote.fetchOwnChanged(authorUid, catalogIdsToSync.map{it.value}.toSet(), cursor)`
    2. Query B: `remote.fetchPublicChanged(availableShelves, cursor)` — filter client-side: `dto.catalogId in catalogIdsToSync.map{it.value}`
    3. Merge A + B: `(resultA + resultB).distinctBy { it.id }` (deduplication)
    4. Для каждого dto: resolve URL, create entity; if `dto.archived || dto.visibleOn.isEmpty()` → `local.deleteById(dto.id)`; else `local.upsertByIdIfNewerVersion(entity)`
    5. Collect changed quest IDs (where `dto.contentsVersion > local.contentsVersion` OR new insert): `changedIds`
    6. On success: advance cursor (setCursor via syncStateRepo handled by orchestrator in phase-03)
    7. Return `Result.success(changedIds)`
- **Edge cases:**
  - `currentUserUid == null` → Query A skipped entirely (guest mode — ADR-CMP-49)
  - Query B returns quests from ALL catalogs; client filters `dto.catalogId in catalogIdsToSync`
  - Empty catalogIdsToSync → skip both queries, return empty result
  - deduplication by `dto.id` — last write wins if A and B return same id (identical data from server, safe)
- **Depends on:** QuestLocalDataSource, QuestRemoteDataSource, StorageUrlResolver, SyncStateRepository
- **Canonical reference:** `06-api-contract.md` §2.2; `06-api-contract.md` §9

### 6h. Create QuestDataModule (Koin)

- **Файл:** `shared/feature/quest/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/quest/data/di/QuestDataModule.kt`
- **Тип:** Koin module
- **Сигнатура:** `val questDataModule = module { ... }`
- **Поведение / Выход:**
  - `single<QuestLocalDataSource> { QuestLocalDataSourceImpl(get<AppDatabase>().questDao()) }`
  - `single<QuestRepository> { QuestRepositoryImpl(local=get(), remote=get(), urlResolver=get(named("storageUrlResolver")), syncStateRepo=get()) }`
- **Edge cases:**
  - `named("storageUrlResolver")` — проверить что Firebase module регистрирует StorageUrlResolver с этим qualifier (существующий паттерн из CatalogDataModule)
- **Canonical reference:** `06-api-contract.md` §13 questDataModule

---

## 7. Create Firebase Quest Module

### 7a. FirestoreQuestDtoMapper

- **Файл:** `platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/quest/FirestoreQuestDtoMapper.kt`
- **Тип:** extension function
- **Сигнатура:** `fun DocumentSnapshot.toQuestDto(): QuestDto`
- **Поведение / Выход:**
  - Читать id: `snapshot.id`
  - `catalogId`: `getString("catalogId") ?: ""`
  - `authorUid`: `getString("authorUid") ?: ""`
  - `title`: `getString("title") ?: ""`
  - `picturePath`: `getString("picturePath")`
  - `visibleOn`: `get("visibleOn") as? List<String> ?: emptyList()`
  - `averageRating`: `getDouble("averageRating")`
  - `averageRatingCount`: `getLong("averageRatingCount")?.toInt() ?: 0`
  - `version`: `getLong("version") ?: 1L`
  - `contentsVersion`: `getLong("contentsVersion") ?: 0L`
  - `lastModifiedAt`: `getTimestamp("lastModifiedAt")?.toDate()?.time ?: 0L`
  - `archived`: `getBoolean("archived") ?: false`
- **Canonical reference:** `06-api-contract.md` §10 Document Schemas

### 7b. FirebaseQuestRemoteDataSource

- **Файл:** `platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/quest/FirebaseQuestRemoteDataSource.kt`
- **Тип:** class
- **Сигнатура:** `class FirebaseQuestRemoteDataSource(firestore: FirebaseFirestore) : QuestRemoteDataSource`
- **Поведение / Выход:**
  - `fetchOwnChanged(authorUid, catalogIds, cursor)`:
    - `firestore.collection("quests").whereEqualTo("authorUid", authorUid).whereIn("catalogId", catalogIds.toList()).whereGreaterThan("lastModifiedAt", cursor.toTimestamp()).get().await()`
    - Map каждый snapshot via `toQuestDto()`
  - `fetchPublicChanged(shelves, cursor)`:
    - `firestore.collection("quests").whereArrayContainsAny("visibleOn", shelves.toList()).whereGreaterThan("lastModifiedAt", cursor.toTimestamp()).get().await()`
    - Map snapshots
- **Edge cases:**
  - `catalogIds.size > 30` — caller (Orchestrator) splits; FirebaseQuestRemoteDataSource не делает split сам
  - Cursor → Timestamp: helper `fun Long.toTimestamp() = Timestamp(this/1000, ((this%1000)*1_000_000).toInt())`
  - Firestore `whereIn` требует non-empty list — если catalogIds.isEmpty() → return emptyList() early
- **Canonical reference:** `06-api-contract.md` §10 Composite Indexes

### 7c. FirebaseQuestModule (Koin)

- **Файл:** `platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/di/FirebaseQuestModule.kt`
- **Тип:** Koin module
- **Сигнатура:** `val firebaseQuestModule = module { single<QuestRemoteDataSource> { FirebaseQuestRemoteDataSource(get()) } }`
- **Canonical reference:** `06-api-contract.md` §13 firebaseQuestModule

---

## 8. Create Section/Theme/Lesson Data Modules

Pattern идентичен Quest data module (задачи #6a-#6h) с заменами:

**Section:**
- parentIdField: `questId: String`
- refreshMethod: `refreshByParents(questIds: Set<QuestId>, cursor: Long): Result<Set<SectionId>>`
- Remote interface: `fetchChangedByParents(questIds: Set<String>, cursor: Long): List<SectionDto>`
- Dto: `SectionDto(id, questId, title, order, version, contentsVersion, lastModifiedAt, archived)` — нет picturePath/visibleOn/averageRating
- Firestore query: `where("questId", "in", questIds.toList()).whereGreaterThan("lastModifiedAt", cursor.toTimestamp())`
- LocalDataSource: `observeByQuest(questId: QuestId)`, `upsertByIdIfNewerVersion`, `deleteById`

**Theme:** parentIdField `sectionId: SectionId` → query `where("sectionId", "in", sectionIds)`

**Lesson:** parentIdField `themeId: ThemeId` → query `where("themeId", "in", themeIds)`

Файлы для Section (пример, Theme/Lesson аналогично):
- `shared/feature/section/data/build.gradle.kts`
- `shared/feature/section/data/src/commonMain/.../SectionLocalDataSource.kt`
- `shared/feature/section/data/src/commonMain/.../SectionLocalDataSourceImpl.kt`
- `shared/feature/section/data/src/commonMain/.../SectionRemoteDataSource.kt`
- `shared/feature/section/data/src/commonMain/.../dto/SectionDto.kt`
- `shared/feature/section/data/src/commonMain/.../mapper/SectionDtoMapper.kt`
- `shared/feature/section/data/src/commonMain/.../mapper/SectionMapper.kt`
- `shared/feature/section/data/src/commonMain/.../SectionRepositoryImpl.kt`
- `shared/feature/section/data/src/commonMain/.../di/SectionDataModule.kt`
- `platform/firebase/src/.../section/FirebaseSectionRemoteDataSource.kt`
- `platform/firebase/src/.../section/FirestoreSectionDtoMapper.kt`
- `platform/firebase/src/.../di/FirebaseSectionModule.kt`

**Canonical reference:** `06-api-contract.md` §2.3 SectionRepository; `08-storage-model.md` §SectionEntity/SectionDao

---

## 9. Create Question Data Module

Question — leaf (нет contentsVersion, нет cascade trigger):
- `refreshByParents(lessonIds: Set<LessonId>, cursor: Long): Result<Unit>` (not `Result<Set<...>>`)
- Dto: `QuestionDto(id, lessonId, text, payload, language, order, version, lastModifiedAt, archived)` — нет contentsVersion
- Firestore query: `where("lessonId", "in", lessonIds.toList()).whereGreaterThan("lastModifiedAt", cursor.toTimestamp())`
- RepositoryImpl.refreshByParents: upsert all (no cascade further), return Result.success(Unit)

**Canonical reference:** `06-api-contract.md` §2.4, §8.4, §9

---

## 10. Register new modules in settings.gradle.kts + AppApplication.kt

### settings.gradle.kts additions

- **Файл:** `settings.gradle.kts`
- **Тип:** build script
- **Сигнатура:** добавить строки include для 5 новых data модулей
- **Поведение / Выход:**
  - `include(":shared:feature:quest:data")`
  - `include(":shared:feature:section:data")`
  - `include(":shared:feature:theme:data")`
  - `include(":shared:feature:lesson:data")`
  - `include(":shared:feature:question:data")`
  - Firebase sub-modules если нужно (проверить структуру platform/firebase — монолитный или разделён)

### AppApplication.kt additions

- **Файл:** `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt`
- **Тип:** Application class update
- **Сигнатура:** добавить импорты + modules в startKoin
- **Поведение / Выход:**
  - Добавить `questDomainModule` (если имеет Koin module — check walking skeleton)
  - Добавить `questDataModule`, `sectionDomainModule`, `sectionDataModule`, ...
  - Добавить `firebaseQuestModule`, `firebaseSectionModule`, `firebaseThemeModule`, `firebaseLessonModule`, `firebaseQuestionModule`
- **Canonical reference:** `06-api-contract.md` §13
