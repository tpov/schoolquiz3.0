---
phase: 04
role: backend-dev
---

# Phase 04 — Backend Tasks

## Pattern Invariants

- `UserStatsRepositoryImpl` живёт в data layer — импортирует domain types, но НЕ presentation
- `observeStats()` читает из Room (не Firebase напрямую) — Firebase только через `refreshProfile()`
- `setLocalDeveloperLevel` использует ONLY `updateDeveloperLevel` DAO method — НЕ `upsert`
- Mapper chain: `RawUserStats` (flat) ↔ `UserStatsEntity` (flat) ↔ `UserStats` (nested Qualification) — per `08-storage-model.md §7`
- Koin: exclusive binding — один `single<UserStatsRepository>` без дублирования
- `authProvider: () -> String?` — lambda injection для uid (testability)

---

## 1. VERIFY / ADD FirebaseUserStatsDataSource.fetchOnce()

**REQUIRES verify перед implementation:**
Открыть `platform/firebase/src/main/kotlin/.../FirebaseUserStatsDataSource.kt` и проверить наличие `suspend fun fetchOnce(): RawUserStats`.

**Файл:** `platform/firebase/src/main/kotlin/.../FirebaseUserStatsDataSource.kt`
- **Тип:** `suspend fun` (добавление к existing class)
- **Сигнатура:** `suspend fun fetchOnce(uid: String): RawUserStats`
- **Вход:** `uid: String` — Firebase Auth uid
- **Поведение / Выход:**
  - Firestore one-shot: `firestore.collection("users").document(uid).get().await()`
  - Map `DocumentSnapshot` → `RawUserStats` (используя existing mapping logic)
  - `RawUserStats.Companion.EMPTY` при пустом snapshot
- **Edge cases:**
  - Network error → exception propagates → `refreshProfile()` returns `Result.failure`
  - Document не существует → `RawUserStats.EMPTY` (все нули)
- **Depends on:** ничего (existing Firebase setup)
- **Canonical reference:** `06-api-contract.md §5` (REQUIRES verify note)
- **Rationale:** `refreshProfile()` нуждается в one-shot fetch, а не в snapshot listener (который для realtime обновлений).

---

## 2. CREATE UserStatsMapper

**Файл:** `shared/feature/app-shell/data/src/commonMain/kotlin/.../mapper/UserStatsMapper.kt`
- **Тип:** extension functions (file-level)
- **Сигнатура:** `fun UserStatsEntity.toDomain(): UserStats` и `fun RawUserStats.toEntity(uid: String): UserStatsEntity`
- **Вход:**
  - `toDomain()`: `UserStatsEntity` с flat qualification fields
  - `toEntity()`: `RawUserStats` (flat fields) + `uid: String`
- **Поведение / Выход:**
  - `toDomain()`: maps flat `testerLevel..developerLevel` → `Qualification(tester=testerLevel, ...)` nested object
  - `toEntity()`: maps flat fields 1:1 (RawUserStats уже flat per `08-storage-model.md §7 VERIFIED note`)
- **Edge cases:**
  - Null `avatarUrl` — preserved as null
  - Все qualification fields Int >= 0 (no clamping in mapper — domain validates separately)
- **Depends on:** `UserStatsEntity`, `UserStats`, `Qualification`, `RawUserStats`
- **Canonical reference:** `06-api-contract.md §5`, `08-storage-model.md §7.1`
- **Rationale:** Mapper chain per clean architecture — entity ↔ domain mapping в data layer.

---

## 3. REWRITE UserStatsRepositoryImpl

**Файл:** `shared/feature/app-shell/data/src/commonMain/kotlin/.../UserStatsRepositoryImpl.kt`
- **Тип:** `class` implementing `UserStatsRepository` + `Syncable`
- **Сигнатура:** `class UserStatsRepositoryImpl(private val remoteDataSource: UserStatsDataSource, private val userStatsDao: UserStatsDao, private val currentUid: () -> String?) : UserStatsRepository, Syncable`
- **Вход:** constructor params
- **Поведение / Выход:**
  - `observeStats()`: `currentUid() ?: return emptyFlow()` → `userStatsDao.observeByUid(uid).map { it?.toDomain() ?: UserStats.guest() }`
  - `currentStats()`: `userStatsDao.findByUid(currentUid() ?: "")?.toDomain() ?: UserStats.guest()`
  - `setLocalDeveloperLevel(value)`: `val uid = currentUid() ?: return` → `userStatsDao.updateDeveloperLevel(uid, value)`
  - `refreshProfile()`: `val uid = currentUid() ?: return Result.failure(...)` → `remoteDataSource.fetchOnce(uid)` → `userStatsDao.upsert(raw.toEntity(uid))` → `Result.success(Unit)`
  - `sync()` (Syncable): делегирует к `refreshProfile()`
- **Edge cases:**
  - `currentUid()=null` в каждом методе — ранний return (no-op или failure)
  - `refreshProfile()` сетевая ошибка → `Result.failure(exception)` (не crash)
  - `observeStats()` — Flow не завершается при ошибке, только при cancellation
- **Depends on:** `UserStatsMapper`, `UserStatsDao`, `UserStatsDataSource`, `Syncable` interface
- **Canonical reference:** `06-api-contract.md §5`
- **Rationale:** ADR-HLA-02 + ADR-HLA-04 — Room-first data access + Syncable для SyncWorker.

---

## 4. UPDATE app-shell:data/build.gradle.kts

**Файл:** `shared/feature/app-shell/data/build.gradle.kts`
- **Тип:** build config
- **Сигнатура:** добавить `implementation(project(":shared:core:persistence"))` + `implementation(project(":shared:core:sync"))`
- **Вход:** текущий файл
- **Поведение / Выход:** `UserStatsDao`, `UserStatsEntity` доступны + `Syncable` interface
- **Edge cases:** не дублировать уже существующие deps
- **Depends on:** Phase 01 (persistence + sync modules created)
- **Canonical reference:** internal (no api-contract entry)
- **Rationale:** новые deps для Room integration + Syncable implementation.

---

## 5. UPDATE appShellDataModule — Koin binding

**Файл:** `shared/feature/app-shell/data/di/AppShellDataModule.kt`
- **Тип:** Koin module update
- **Сигнатура:** обновить `single<UserStatsRepository>` с новыми constructor params
- **Вход:** существующий модуль
- **Поведение / Выход:**
  - `single<UserStatsRepository> { UserStatsRepositoryImpl(remoteDataSource=get(), userStatsDao=get(), currentUid={ FirebaseAuth.getInstance().currentUser?.uid }) }`
  - `UserStatsRepositoryImpl` также биндится как `Syncable` — либо `single<Syncable> { get<UserStatsRepository>() as Syncable }` или через `bind()` DSL
- **Edge cases:**
  - `UserStatsRepositoryImpl` реализует и `UserStatsRepository` и `Syncable` — нужно оба binding
  - Exclusive binding rule: нет `@Inject constructor` + `@Provides` одновременно (Koin не использует аннотации, но аналог — не создавать два `single` для одного instance)
- **Depends on:** шаги 1, 3 + Phase 01 (AppDatabase/Dao bindings в persistenceModule)
- **Canonical reference:** `06-api-contract.md §12`
- **Rationale:** Koin composition root должен отражать новый constructor.
