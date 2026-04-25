---
phase: 06
name: Sync Infrastructure
complexity: simple
---

# Phase 06: Sync Infrastructure

## Goal

Создать `SyncWorker` в `platform:android-services`, зарегистрировать periodic и manual sync в `AppApplication.onCreate()`, обновить `syncModule` Koin с `List<Syncable>` провайдером, обновить `platform:android-services/build.gradle.kts` с WorkManager dependency.

## Scope

- CREATE `SyncWorker` в `platform:android-services`
- UPDATE `platform:android-services/build.gradle.kts` — добавить WorkManager + `core:sync` deps
- UPDATE `AppApplication.kt` — добавить `syncModule` + периодическое расписание WorkManager
- UPDATE `AppApplication.kt` — добавить `WorkManager.enqueueUniquePeriodicWork(...)` в `onCreate()`
- Убедиться что `UserStatsRepositoryImpl` и `CatalogRepositoryImpl` реализуют `Syncable`

## Layer

infrastructure (WorkManager + sync pipeline)

## Role Inputs

- `backend.md`
- `frontend.md` — none
- `tests.md`

## Dependencies

phases_ref: [phase-04, phase-05]
- Phase 04: `UserStatsRepositoryImpl` implements `Syncable`
- Phase 05: `CatalogRepositoryImpl` implements `Syncable`
- Phase 01: `Syncable` interface в `core:sync`

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 2: нет SyncWorker/refreshProfile в новом app | backend-dev | `platform:android-services/` (пустой) | ADR-HLA-04 (Syncable topology) | CREATE SyncWorker + periodic schedule | app sync раз в день; SyncNow вручную |
| Problem 2: WorkManager не подключён | backend-dev | `libs.versions.toml:25`, `platform:android-services/build.gradle.kts` | WorkManager 2.9.1 | добавить dep + wire Koin | `:apps:android-next:assembleDebug` green |

## New Files

- `platform/android-services/src/main/.../sync/SyncWorker.kt`
- `platform/android-services/di/SyncModule.kt`

## Modified Files

- `platform/android-services/build.gradle.kts` — добавить WorkManager + `core:sync` deps
- `apps/android-next/src/main/.../AppApplication.kt` — добавить `syncModule` + periodic schedule
- `shared/feature/app-shell/data/src/commonMain/.../UserStatsRepositoryImpl.kt` — проверить `sync()` реализацию (Syncable)
- `shared/core/catalog/data/src/commonMain/.../CatalogRepositoryImpl.kt` — проверить `sync()` реализацию (Syncable)

## Deleted Files

none

## Acceptance Criteria

- [ ] `SyncWorker.doWork()` итерирует по `List<Syncable>`, вызывает `sync()` на каждом, возвращает `Result.success()` если все успешны
- [ ] На sync failure — `Result.retry()` (не `failure` — WorkManager retry policy)
- [ ] `AppApplication.onCreate()` содержит `enqueueUniquePeriodicWork(WORK_NAME_PERIODIC, KEEP, PeriodicWorkRequest(1L, DAYS))` с `NetworkType.CONNECTED`
- [ ] `SyncWorker.WORK_NAME_PERIODIC = "periodic_sync"`, `WORK_NAME_MANUAL = "manual_sync"` — константы
- [ ] `syncModule` содержит `single<WorkManager>` + `single<List<Syncable>>` с `UserStatsRepositoryImpl` + `CatalogRepositoryImpl`
- [ ] Stateful fields в `SyncWorker`: нет — stateless worker

## Pattern Invariants

- `SyncWorker` зависит ТОЛЬКО от `core:sync` (Syncable interface), нет `feature:app-shell:domain`, нет `core:catalog:domain`
- `platform:android-services` — Android-only (нет KMP), OK импортировать WorkManager
- `List<Syncable>` из Koin — порядок: UserStatsRepositoryImpl, CatalogRepositoryImpl
- Каждый `Syncable.sync()` failure → `Result.retry()`, не `Result.failure()` — WorkManager будет повторять

## Tests Required

- `SyncWorkerTest` (JVM или instrumented): given all Syncables succeed, when `doWork()`, then `Result.success()`
- `SyncWorkerTest`: given first Syncable fails, when `doWork()`, then `Result.retry()`
- Edge case: empty `List<Syncable>` → `Result.success()`

**Note:** WorkManager testing в JVM — `TestListenableWorkerBuilder<SyncWorker>` требует Android context → instrumented. Упрощённый JVM тест: mock `List<Syncable>` + verify delegation pattern.

## Validation

| Команда | Ожидаемый результат |
|---------|---------------------|
| `./gradlew :apps:android-next:assembleDebug --no-configuration-cache` | GREEN — `SyncWorker` зарегистрирован в Koin, no DI errors |
| `./gradlew :platform:android-services:test --no-configuration-cache` | GREEN — SyncWorker unit test (FakeSyncable) |

## Handoff Notes

После Phase 06 разблокирована Phase 07 (Presentation) — `WorkManager` доступен для `DefaultRootComponent.onSyncNow()`.
