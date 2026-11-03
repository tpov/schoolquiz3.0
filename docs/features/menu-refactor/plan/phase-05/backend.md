---
phase: 05
role: backend-dev
---

# Phase 05 — Backend Tasks

## Pattern Invariants

- `CatalogLocalDataSource` interface и `CatalogRemoteDataSource` interface живут в `shared/core/catalog/data/` — NОТ в `platform/firebase`
- `FirebaseCatalogRemoteDataSource` живёт в `platform/firebase` — только реализация interface
- `storageUrlResolver: suspend (String) -> String` — лямбда в `CatalogRepositoryImpl`, инжектируется из Koin
- `CatalogRepositoryImpl.observeAll()` сортирует по `id.value ASC` (Codex fix #6 invariant)
- KMP-совместимость: `core:catalog:data` — KMP commonMain, нет Android imports
- `platform:firebase` — Android-only library, OK для Firebase SDK imports

---

## 1. CREATE CatalogLocalDataSource interface + impl

**Файл:** `shared/core/catalog/data/src/commonMain/.../CatalogLocalDataSource.kt`
- **Тип:** `interface` + `class CatalogLocalDataSourceImpl`
- **Сигнатура:** `interface CatalogLocalDataSource` + `class CatalogLocalDataSourceImpl(private val dao: CatalogDao) : CatalogLocalDataSource`
- **Вход:** `CatalogDao` (из `core:persistence`)
- **Поведение / Выход:**
  - `fun observeAll(): Flow<List<CatalogEntity>>` — delegates to `dao.observeAll()`
  - `suspend fun replaceAll(entities: List<CatalogEntity>)` — delegates to `dao.replaceAll(entities)` (атомарный @Transaction)
  - `suspend fun findById(id: String): CatalogEntity?` — delegates to `dao.findById(id)`
- **Edge cases:** empty list for `replaceAll` — valid (clears all catalogs)
- **Depends on:** `CatalogDao`, `CatalogEntity` (Phase 01)
- **Canonical reference:** `06-api-contract.md §9.1`
- **Rationale:** Abstraction layer between repository и DAO — testable с `FakeCatalogLocalDataSource`.

---

## 2. CREATE CatalogRemoteDataSource interface

**Файл:** `shared/core/catalog/data/src/commonMain/.../CatalogRemoteDataSource.kt`
- **Тип:** `interface`
- **Сигнатура:** `interface CatalogRemoteDataSource { suspend fun fetchAll(): List<CatalogDto> }`
- **Вход:** N/A
- **Поведение / Выход:**
  - `fetchAll()` — возвращает `List<CatalogDto>` (DTO — pure Kotlin data class в том же модуле `core:catalog:data/commonMain`, нет Firestore/Firebase типов)
  - Throws exception при network error (caller wraps в `Result`)
- **Edge cases:**
  - empty Firestore collection → empty list
  - Mapping `DocumentSnapshot → CatalogDto` выполняется внутри `FirebaseCatalogRemoteDataSource` (Firebase adapter), перед возвратом
  - `CatalogDto` — строго `(id, name, picturePath)` без `pictureUrl` (URL resolution — в `CatalogRepositoryImpl.refreshFromRemote()`)
- **Canonical reference:** `06-api-contract.md §9.2`
- **Rationale:** Dependency inversion — `catalog:data` (KMP commonMain) определяет interface и владеет `CatalogDto` как pure Kotlin type. `platform/firebase` импортирует `CatalogDto` из `core:catalog:data`, выполняет Firestore-specific mapping `DocumentSnapshot → CatalogDto`. Clean architecture: platform → core, never reversed.

---

## 3. CREATE CatalogMapper

**Файл:** `shared/core/catalog/data/src/commonMain/.../mapper/CatalogMapper.kt`
- **Тип:** extension functions
- **Сигнатура:** `fun CatalogEntity.toDomain(): Catalog` + `fun Catalog.toEntity(): CatalogEntity`
- **Вход:** `CatalogEntity` / `Catalog`
- **Поведение / Выход:**
  - `toDomain()`: `CatalogEntity(id, name, picturePath, pictureUrl)` → `Catalog(id=CatalogId(id), name=name, picturePath=picturePath)` — domain не содержит pictureUrl (ADR-HLA-07)
  - `toEntity()`: `Catalog(id, name, picturePath)` → `CatalogEntity(id=id.value, name=name, picturePath=picturePath, pictureUrl=null)` — pictureUrl null при создании, заполняется в `refreshFromRemote`
- **Edge cases:**
  - `picturePath=null` → preserved as null
  - `name` blank — не должно возникнуть (domain validates), но если происходит — entity.name = blank (no crash)
- **Depends on:** `CatalogEntity`, `Catalog`, `CatalogId`
- **Canonical reference:** `06-api-contract.md §9`, `08-storage-model.md §7.2`
- **Rationale:** mapper chain: Entity ↔ Domain per clean architecture.

---

## 4. CREATE CatalogDto + mappers (split core/platform)

**4a.** `shared/core/catalog/data/src/commonMain/.../CatalogDto.kt`
- **Тип:** `data class`
- **Сигнатура:** `data class CatalogDto(val id: String, val name: String, val picturePath: String?)`
- **Поведение / Выход:** pure Kotlin DTO — без Firebase/Firestore типов
- **Edge cases:** `picturePath` nullable — `null` для каталогов без картинки
- **Depends on:** ничего (pure data class)
- **Canonical reference:** `06-api-contract.md §9.2` + `08-storage-model.md §7.3`
- **Rationale:** DTO живёт в `core:catalog:data` (commonMain) — pure Kotlin, позволяет interface `CatalogRemoteDataSource.fetchAll()` возвращать `List<CatalogDto>` без зависимости на `platform/firebase`. Clean architecture.

**4b.** `shared/core/catalog/data/src/commonMain/.../mapper/CatalogDtoMapper.kt`
- **Тип:** extension function (pure Kotlin)
- **Сигнатура:** `fun CatalogDto.toEntity(): CatalogEntity`
- **Поведение / Выход:** `CatalogEntity(id=id, name=name, picturePath=picturePath, pictureUrl=null)` — `pictureUrl` заполняется в `CatalogRepositoryImpl.refreshFromRemote()` после URL resolve
- **Edge cases:** `picturePath=null` → `pictureUrl=null` preserved (CF-23)
- **Depends on:** `CatalogDto` (шаг 4a), `CatalogEntity` (Phase 01)
- **Canonical reference:** `06-api-contract.md §9.2` + `08-storage-model.md §7.3`
- **Rationale:** Pure Kotlin mapper (DTO → Entity) — живёт в `core:catalog:data`, нет Firebase зависимостей. Firebase-specific `DocumentSnapshot → CatalogDto` mapper — задача 4c.

**4c.** `platform/firebase/src/main/.../catalog/FirestoreCatalogDtoMapper.kt`
- **Тип:** extension function (Firebase-specific adapter)
- **Сигнатура:** `fun DocumentSnapshot.toCatalogDto(): CatalogDto?`
- **Вход:** Firestore `DocumentSnapshot`
- **Поведение / Выход:** `getString("name") ?: return null`; если `name.isBlank()` → `return null`; иначе `CatalogDto(id=id, name=name, picturePath=getString("picturePath"))`
- **Edge cases:**
  - blank name → returns null (CF-22)
  - null name field → returns null
  - `picturePath` field отсутствует в document → `null` preserved (CF-23)
- **Depends on:** `CatalogDto` (import из `core:catalog:data`), Firebase SDK `DocumentSnapshot`
- **Canonical reference:** `06-api-contract.md §10` + `08-storage-model.md §7.3`
- **Rationale:** Firebase adapter — переводит Firestore document в DTO. Живёт в `platform/firebase`, импортирует `CatalogDto` из `core:catalog:data`. Один-направленная зависимость `platform → core`.

---

## 5. CREATE FirebaseCatalogRemoteDataSource

**Файл:** `platform/firebase/src/main/.../catalog/FirebaseCatalogRemoteDataSource.kt`
- **Тип:** `class` implementing `CatalogRemoteDataSource`
- **Сигнатура:** `class FirebaseCatalogRemoteDataSource(private val firestore: FirebaseFirestore) : CatalogRemoteDataSource`
- **Вход:** `FirebaseFirestore` instance (from Koin)
- **Поведение / Выход:**
  - `override suspend fun fetchAll(): List<CatalogDto>` — возвращает `List<CatalogDto>` (pure Kotlin тип из `core:catalog:data`)
  - `firestore.collection("catalogs").get().await().documents.mapNotNull { it.toCatalogDto() }`
  - Mapping chain: `DocumentSnapshot → CatalogDto` (через extension из `FirestoreCatalogDtoMapper`); дальнейшее `CatalogDto → CatalogEntity` делает `CatalogRepositoryImpl` после URL resolve
  - Возвращает пустой список если нет документов
- **Edge cases:**
  - Firebase network error → exception propagates (caller `CatalogRepositoryImpl` wraps в `runCatching`)
  - Пустая коллекция → empty list
  - DocumentSnapshot с blank name → `toCatalogDto()` returns null → `mapNotNull` фильтрует
- **Depends on:** `CatalogRemoteDataSource` interface + `CatalogDto` (из `core:catalog:data`), `FirestoreCatalogDtoMapper` (шаг 4c), Firebase SDK
- **Canonical reference:** `06-api-contract.md §10`
- **Rationale:** `platform:firebase` знает о `DocumentSnapshot`, `FirebaseFirestore`. Маппинг `DocumentSnapshot → CatalogDto` выполняется здесь. Interface возвращает `List<CatalogDto>` — DTO живёт в core (pure KMP), platform лишь адаптирует Firestore к DTO. Clean architecture: `platform → core`.

---

## 6. CREATE CatalogRepositoryImpl

**Файл:** `shared/core/catalog/data/src/commonMain/.../CatalogRepositoryImpl.kt`
- **Тип:** `class` implementing `CatalogRepository` + `Syncable`
- **Сигнатура:** `class CatalogRepositoryImpl(private val local: CatalogLocalDataSource, private val remote: CatalogRemoteDataSource, private val storageUrlResolver: suspend (String) -> String) : CatalogRepository, Syncable`
- **Вход:** constructor params
- **Поведение / Выход:**
  - `observeAll()`: `local.observeAll().map { list -> list.map { it.toDomain() }.sortedBy { it.id.value } }`
  - `refreshFromRemote()`: `val dtos = remote.fetchAll()` (возвращает `List<CatalogDto>`) → для каждой dto: `val entity = dto.toEntity()` (pure mapper из шага 4b); если `entity.picturePath != null`: `entity.copy(pictureUrl = runCatching { storageUrlResolver(picturePath) }.getOrNull())` → `local.replaceAll(entities)` → `Result.success(Unit)`
  - `getById(id)`: `local.findById(id.value)?.toDomain()`
  - `sync()` (Syncable): делегирует к `refreshFromRemote()`
- **Edge cases:**
  - Firebase error → `Result.failure`
  - `storageUrlResolver` throws → `runCatching` → `pictureUrl=null` (graceful degradation, нет crash)
  - Empty remote list → `replaceAll(emptyList())` → удаляет все локальные каталоги
- **Depends on:** `CatalogLocalDataSource`, `CatalogRemoteDataSource`, `CatalogMapper`, `Syncable`
- **Canonical reference:** `06-api-contract.md §9.3`
- **Rationale:** ADR-HLA-07 (URL pre-resolve) + ADR-L3-04 (atomic replaceAll) + ADR-HLA-04 (Syncable).

---

## 7. CREATE catalogDataModule Koin

**Файл:** `shared/core/catalog/data/src/commonMain/kotlin/.../di/CatalogDataModule.kt` (canonical location per `06-api-contract.md §12`)
- **Тип:** Koin module
- **Сигнатура:** `val catalogDataModule = module { ... }`
- **Вход:** `core:persistence` bindings (`CatalogDao` через `persistenceModule`); `CatalogRemoteDataSource` + `storageUrlResolver` поставляются из `firebaseCatalogModule` (см. задача 7b)
- **Поведение / Выход:**
  - `single<CatalogLocalDataSource> { CatalogLocalDataSourceImpl(get()) }` — `get()` резолвит `CatalogDao` из `persistenceModule`
  - `single<CatalogRepository> { CatalogRepositoryImpl(get(), get(), storageUrlResolver = get(named("storageUrlResolver"))) }` — `get()` резолвит `CatalogLocalDataSource` + `CatalogRemoteDataSource` (из `firebaseCatalogModule`)
- **Edge cases:**
  - `CatalogRemoteDataSource` должен быть зарегистрирован в `firebaseCatalogModule` до использования `catalogDataModule` (порядок `modules(...)` в `AppApplication.startKoin` — см. задача 9)
  - `storageUrlResolver` — named binding, зарегистрирован в `firebaseCatalogModule` (задача 7b)
  - `shared:core:catalog:data/build.gradle.kts` должен иметь Koin core dep (`implementation(libs.koin.core)` — см. задача 10)
- **Depends on:** шаги 1-6, задача 10 (Koin core dep)
- **Canonical reference:** `06-api-contract.md §12`
- **Rationale:** Feature-own DI pattern — симметрично с existing `appShellDataModule` (в `feature:app-shell:data/di/`). `catalogDataModule` живёт в KMP commonMain; Koin core — multiplatform-совместим. Named binding `"storageUrlResolver"` разрешается через module композицию в `AppApplication`.

---

## 7b. CREATE firebaseCatalogModule Koin

**Файл:** `platform/firebase/src/main/kotlin/.../di/FirebaseCatalogModule.kt`
- **Тип:** Koin module
- **Сигнатура:** `val firebaseCatalogModule = module { ... }`
- **Вход:** `FirebaseFirestore` instance (из `firebaseModule`), `FirebaseStorage` instance
- **Поведение / Выход:**
  - `single<CatalogRemoteDataSource> { FirebaseCatalogRemoteDataSource(get<FirebaseFirestore>()) }`
  - `single<suspend (String) -> String>(named("storageUrlResolver")) { { path: String -> FirebaseStorage.getInstance().reference.child(path).downloadUrl.await().toString() } }`
  - `storageUrlResolver` лямбда — захватывает Firebase Storage reference; Android-only → корректно в `platform:firebase`
- **Edge cases:**
  - Named binding `"storageUrlResolver"` — должен совпадать с `get(named("storageUrlResolver"))` в `catalogDataModule`
  - `FirebaseStorage.getInstance()` — singleton; вызов безопасен внутри лямбды
  - Тип лямбды: `suspend (String) -> String` (non-null; `downloadUrl.await()` либо возвращает URI либо бросает exception, caller `CatalogRepositoryImpl.refreshFromRemote` wraps в `runCatching { ... }.getOrNull()` для graceful degradation на network error)
- **Depends on:** шаг 5 (`FirebaseCatalogRemoteDataSource`), Firebase SDK
- **Canonical reference:** `06-api-contract.md §12`
- **Rationale:** `platform:firebase` owns Firebase-specific DI — `CatalogRemoteDataSource` impl + `storageUrlResolver`. Разделение DI по принадлежности: platform module знает о Firebase, core/catalog/data не знает. Pattern: `shared:core:catalog:data` defines interface, `platform:firebase` provides impl + lambda.

---

## 8. CREATE catalogDomainModule Koin

**Файл:** `shared/core/catalog/domain/src/commonMain/.../di/CatalogDomainModule.kt`
- **Тип:** Koin module
- **Сигнатура:** `val catalogDomainModule = module { factory { ObserveCatalogsUseCase(get()) }; factory { CreateQuestUseCase(get(), get()) } }`
- **Вход:** `CatalogRepository` binding (из `catalogDataModule`)
- **Поведение / Выход:**
  - `factory { ObserveCatalogsUseCase(get()) }` — `get()` резолвит `CatalogRepository`
  - `factory { CreateQuestUseCase(get(), get()) }` — `get()` резолвит `CatalogRepository` + дополнительный dep (уточнить по Walking Skeleton `CreateQuestUseCase` constructor)
  - use cases из Walking Skeleton доступны для injection
- **Edge cases:**
  - `CatalogRepository` должен быть зарегистрирован (через `catalogDataModule`) до того как `catalogDomainModule` резолвит его
  - Если `shared:core:catalog:domain` не имеет Android-specific Koin code — файл добавляется в commonMain или в отдельный `di/` package в том же KMP модуле (допустимо, Koin — pure Kotlin)
  - `CreateQuestUseCase` конструктор — уточнить у Walking Skeleton (может иметь второй параметр `QuestRepository` или аналогичный)
- **Depends on:** задача 7, Walking Skeleton `ObserveCatalogsUseCase` + `CreateQuestUseCase`, задача 10 (Koin core dep в `catalog:domain/build.gradle.kts`)
- **Canonical reference:** `06-api-contract.md §12`
- **Rationale:** Domain layer owns DI регистрацию своих use cases. `catalogDomainModule` живёт в `catalog:domain`, а не в `catalog:data` — domain layer не зависит от data layer.

---

## 9. UPDATE AppApplication.kt — добавить новые Koin modules

**Файл:** `apps/android-next/src/main/.../AppApplication.kt`
- **Тип:** `Application` class update
- **Сигнатура:** добавить в `startKoin { modules(...) }`: `persistenceModule`, `firebaseCatalogModule`, `catalogDataModule`, `catalogDomainModule`
- **Вход:** существующий список modules
- **Поведение / Выход:** новые bindings доступны во всём приложении
- **Edge cases:**
  - Порядок важен: `persistenceModule` → `firebaseCatalogModule` → `catalogDataModule` → `catalogDomainModule`
  - `firebaseCatalogModule` (из `platform/firebase`) должен быть до `catalogDataModule` (его зависимость)
  - Если `firebaseModule` уже регистрирует `FirebaseFirestore` — `firebaseCatalogModule` зависит от него
- **Depends on:** задачи 7, 7b, 8 + Phase 01 (persistenceModule)
- **Canonical reference:** `06-api-contract.md §12`
- **Rationale:** Composition root — единственное место где все layer boundaries пересекаются.

---

## 10. UPDATE build.gradle.kts файлы

**Файлы:**
- `shared/core/catalog/data/build.gradle.kts`:
  - ADD `implementation(project(":shared:core:persistence"))` — для `CatalogDao`, `CatalogEntity`
  - ADD `implementation(project(":shared:core:sync"))` — для `Syncable` interface
  - ADD `implementation(project(":shared:core:catalog:domain"))` — для `CatalogRepository`, `Catalog`, `CatalogId`
  - НЕ добавлять `platform:firebase` — нарушит KMP чистоту (core → platform запрещено)
- `platform/firebase/build.gradle.kts`:
  - ADD `implementation(project(":shared:core:catalog:data"))` — для `CatalogRemoteDataSource` interface + `CatalogDto` (pure Kotlin)
  - ADD `implementation(project(":shared:core:catalog:domain"))` — для domain types если нужны в binding (`CatalogRepository` composition); добавить Firebase `FirebaseFirestore` binding в existing `firebaseModule` (либо расширяем `firebaseModule` с `single<FirebaseFirestore> { FirebaseFirestore.getInstance() }`, либо `firebaseCatalogModule` сам резолвит через `FirebaseFirestore.getInstance()` internal). Рекомендуется: extend `platform/firebase/src/main/.../di/FirebaseModule.kt` — добавить `single<FirebaseFirestore> { FirebaseFirestore.getInstance() }` binding; затем `firebaseCatalogModule` резолвит через `get<FirebaseFirestore>()`.
- `shared/core/catalog/domain/build.gradle.kts`:
  - ADD `implementation(libs.koin.core)` — для `CatalogDomainModule.kt` в commonMain (использует Koin DSL `module { factory { ... } }`)
- `shared/core/catalog/data/build.gradle.kts`:
  - ADD `implementation(libs.koin.core)` — для `CatalogDataModule.kt` в commonMain (Koin core — KMP-совместим, per ADR-0009 + existing appShellDataModule pattern)

- **Тип:** build config updates
- **Поведение / Выход:** все imports разрешаются, модули компилируются
- **Edge cases:**
  - Проверить circular dependency: `catalog:data → platform:firebase` — запрещено (BLOCKER); `platform:firebase → catalog:data` — OK (platform depends on core)
  - `catalog:data` — KMP module (kotlin("multiplatform")); `platform:firebase` — Android module (com.android.library)
  - `libs.koin.core` должен быть задекларирован в `libs.versions.toml` (проверить существующие appShellDataModule / firebaseModule — должно быть). Если ключ `koin` вместо `koin.core` — использовать существующий alias
  - `FirebaseFirestore` — класс из `com.google.firebase:firebase-firestore`; добавление binding в `firebaseModule` — одна строка, не требует новых deps (Firebase SDK уже в `platform/firebase/build.gradle.kts`)
- **Depends on:** Phase 01, existing Koin setup в проекте
- **Canonical reference:** internal (no api-contract entry), `06-api-contract.md §12` для module ownership
- **Rationale:** Module dependency direction: `platform:firebase → shared:core:catalog:data` (platform implements core interface). Koin core в commonMain — допустимо per Koin 4.x KMP support. `FirebaseFirestore` binding в `firebaseModule` централизует Firebase SDK access.
