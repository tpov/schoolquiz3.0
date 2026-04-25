---
phase: 06
role: backend-dev
---

# Phase 06 — Backend Tasks

## Pattern Invariants

- `SyncWorker` зависит ТОЛЬКО от `core:sync` (нет feature imports из platform)
- `platform:android-services` — Android-only библиотека, WorkManager Android API — OK
- `List<Syncable>` в Koin — порядок: UserStats, Catalog (UserStats первым, как в legacy)
- Periodic schedule: `ExistingPeriodicWorkPolicy.KEEP` — не перезапускать если уже запланировано

---

## 1. UPDATE platform:android-services/build.gradle.kts

**Файл:** `platform/android-services/build.gradle.kts`
- **Тип:** build config
- **Сигнатура:** добавить `implementation(libs.androidx.work.runtime.ktx)` + `implementation(project(":shared:core:sync"))` + WorkManager Koin factory deps
- **Вход:** текущий файл (пустой stub)
- **Поведение / Выход:** WorkManager API доступен + `Syncable` импортируется
- **Edge cases:** `libs.androidx.work.runtime.ktx` должен быть задекларирован в `libs.versions.toml` (уже есть per research, версия 2.9.1)
- **Depends on:** Phase 01 (core:sync module created)
- **Canonical reference:** internal
- **Rationale:** WorkManager = Android-only, живёт в platform module.

---

## 2. CREATE SyncWorker

**Файл:** `platform/android-services/src/main/kotlin/.../sync/SyncWorker.kt`
- **Тип:** `class` extending `CoroutineWorker`
- **Сигнатура:** `class SyncWorker(appContext: Context, workerParams: WorkerParameters, private val syncables: List<Syncable>) : CoroutineWorker(appContext, workerParams)`
- **Вход:** `List<Syncable>` via constructor injection (WorkManager custom factory needed — see notes)
- **Поведение / Выход:**
  - `override suspend fun doWork(): Result`
  - Итерирует по `syncables`: `for (syncable in syncables) { syncable.sync().onFailure { return Result.retry() } }`
  - Возвращает `Result.success()` если все прошли
  - Возвращает `Result.retry()` если любой failed
  - `companion object { const val WORK_NAME_PERIODIC = "periodic_sync"; const val WORK_NAME_MANUAL = "manual_sync"; val PERIODIC_INTERVAL = 1L to TimeUnit.DAYS }`
- **Edge cases:**
  - Пустой `syncables` → `Result.success()` (нет ops, всё ок)
  - Один из Syncables throws exception → `onFailure` catches → `Result.retry()`
- **Depends on:** `Syncable` (Phase 01), WorkManager API
- **Canonical reference:** `06-api-contract.md §11`
- **Rationale:** ADR-HLA-04 Topology B — single worker + List<Syncable>.

**WorkManager Factory note:** для Koin injection в `CoroutineWorker` constructor нужна кастомная `WorkerFactory`. Варианты:
- (A) Koin `KoinWorkerFactory` — если `koin-android-workmanager` доступен в libs
- (B) Захватить `List<Syncable>` через lambda/WorkerFactory implementation
- Проверить `libs.versions.toml` на наличие `koin-android-workmanager`; если нет — добавить или использовать approach B

---

## 3. CREATE syncModule Koin

**Файл:** `platform/android-services/di/SyncModule.kt` (или в `apps:android-next`)
- **Тип:** Koin module
- **Сигнатура:** `val syncModule = module { single<WorkManager> { WorkManager.getInstance(androidContext()) }; single<List<Syncable>> { listOf(get<UserStatsRepositoryImpl>(), get<CatalogRepositoryImpl>()) } }`
- **Вход:** `UserStatsRepositoryImpl` + `CatalogRepositoryImpl` биндинги из других модулей
- **Поведение / Выход:** `WorkManager` singleton + `List<Syncable>` для injection в SyncWorker
- **Edge cases:**
  - `UserStatsRepositoryImpl` тип нужен (не interface) для cast к Syncable — убедиться что в Koin есть binding на impl type
  - `CatalogRepositoryImpl` аналогично
- **Depends on:** Phase 04 (UserStatsRepositoryImpl), Phase 05 (CatalogRepositoryImpl)
- **Canonical reference:** `06-api-contract.md §11,12`
- **Rationale:** composition root для sync infrastructure.

---

## 4. UPDATE AppApplication.kt — periodic sync schedule

**Файл:** `apps/android-next/src/main/.../AppApplication.kt`
- **Тип:** `Application.onCreate()` update
- **Сигнатура:** добавить вызов `enqueueUniquePeriodicWork` + добавить `syncModule` в `startKoin { modules(...) }`
- **Вход:** существующий `AppApplication.kt`
- **Поведение / Выход:**
  - WorkManager периодически запускает `SyncWorker` 1 раз в сутки
  - Constraint: `NetworkType.CONNECTED`
  - Policy: `KEEP` — не перезаписывать если уже запланировано
  - `syncModule` доступен для DI
- **Edge cases:**
  - WorkManager должен быть инициализирован (или lazy via `WorkManager.getInstance(this)`)
  - `syncModule` добавляется после всех data modules (depends on UserStats + Catalog bindings)
- **Depends on:** шаги 1-3
- **Canonical reference:** `06-api-contract.md §12`
- **Rationale:** periodic sync — spec default 1 день.
