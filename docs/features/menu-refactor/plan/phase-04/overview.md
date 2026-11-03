---
phase: 04
name: UserStats Data Layer
complexity: simple
---

# Phase 04: UserStats Data Layer

## Goal

Реализовать `UserStatsRepositoryImpl` с Room + Firestore integration: `observeStats()` читает из Room DAO, `setLocalDeveloperLevel()` делает targeted UPDATE, `refreshProfile()` читает из Firestore и делает full UPSERT в Room. Создать `UserStatsMapper` (Entity ↔ Domain). Обновить Koin `appShellDataModule`.

## Scope

- REWRITE `UserStatsRepositoryImpl` — добавить `UserStatsDao` injection + реализовать 4 методы
- CREATE `UserStatsMapper.kt` — `UserStatsEntity.toDomain()` + `RawUserStats.toEntity(uid)`
- VERIFY `FirebaseUserStatsDataSource` имеет `suspend fun fetchOnce(): RawUserStats` — если нет, создать
- UPDATE `appShellDataModule` Koin — добавить `UserStatsDao` и `currentUid` lambda params

## Layer

data (repository implementation)

## Role Inputs

- `backend.md`
- `frontend.md` — none
- `tests.md`

## Dependencies

phases_ref: [phase-01, phase-03]
- Phase 01: `UserStatsDao`, `UserStatsEntity` в `core:persistence` должны существовать
- Phase 03: `UserStatsRepository` interface должен иметь `setLocalDeveloperLevel` + `refreshProfile`

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 2: нет Room integration в UserStatsRepositoryImpl | backend-dev | `UserStatsRepositoryImpl.kt:21` | ADR-HLA-02 (direct write), ADR-HLA-03 (central DB) | добавить DAO injection + реализовать методы | US-01..08 green |
| Problem 2: нет fetchOnce() в FirebaseUserStatsDataSource | backend-dev | `FirebaseUserStatsDataSource.kt:28` | REQUIRES verify | проверить наличие и добавить если нет | `refreshProfile()` работает без timeout |
| Problem 2: Koin binding не обновлён для нового constructor | backend-dev | `AppShellDataModule.kt:7` | exclusive binding rule | обновить single<> с новыми params | app стартует без DI error |

## New Files

- `shared/feature/app-shell/data/src/commonMain/.../mapper/UserStatsMapper.kt`

## Modified Files

- `shared/feature/app-shell/data/src/commonMain/.../UserStatsRepositoryImpl.kt` — full rewrite
- `shared/feature/app-shell/data/di/AppShellDataModule.kt` — обновить Koin binding
- `shared/feature/app-shell/data/build.gradle.kts` — добавить `core:persistence` dep
- `platform/firebase/src/.../FirebaseUserStatsDataSource.kt` — добавить `fetchOnce()` если нет (REQUIRES verify)

## Deleted Files

none

## Acceptance Criteria

- [ ] `UserStatsRepositoryImpl.observeStats()` читает из `UserStatsDao.observeByUid(uid)`, не из Firebase напрямую
- [ ] `UserStatsRepositoryImpl.setLocalDeveloperLevel(100)` вызывает `UserStatsDao.updateDeveloperLevel(uid, 100)` — NOT `upsert`
- [ ] `UserStatsRepositoryImpl.refreshProfile()` вызывает `firebaseDataSource.fetchOnce()` → `upsert(rawUserStats.toEntity(uid))` — перезаписывает ВСЕ поля включая developerLevel
- [ ] `UserStatsMapper.UserStatsEntity.toDomain()` — 16+ полей корректно маппятся
- [ ] `UserStatsMapper.RawUserStats.toEntity(uid)` — flat qualification fields корректно маппятся
- [ ] US-01..US-08 зелёные
- [ ] `./gradlew :shared:feature:app-shell:data:jvmTest` GREEN

## State Matrix Coverage

Sync Contract (из `08-storage-model.md §10`):
- `refreshProfile()` → full UPSERT перезаписывает developer_level (US-08 — dev mode auto-deactivation)
- `setLocalDeveloperLevel()` → targeted UPDATE только developer_level (US-04)

## Pattern Invariants

- `UserStatsRepositoryImpl` — НЕ вызывает Firebase напрямую для observeStats (только через Room Flow)
- `setLocalDeveloperLevel` использует `UserStatsDao.updateDeveloperLevel` (TARGETED UPDATE), НЕ `upsert` (full replace)
- `refreshProfile()` использует `upsert` (full replace) — перезаписывает клиентский developer=100 server'ским developer=0
- `currentUid` lambda инжектируется, не hardcoded в implementation (testability)
- Новые stateful fields в `UserStatsRepositoryImpl`: нет MutableState/StateFlow — stateless impl

## Tests Required

Параллельно:

- US-01: given `currentUid()=null`, when `observeStats()`, then emptyFlow (no emissions)
- US-02: given uid exists, Room entity=null, when `observeStats().take(1)`, then `UserStats.EMPTY` (или `UserStats.guest()`)
- US-03: given entity with all 16 fields, when `toDomain()`, then all fields mapped correctly (round-trip)
- US-04: given uid exists, when `setLocalDeveloperLevel(100)`, then `updateDeveloperLevelCalls == 1` (NOT upsert)
- US-05: given `currentUid()=null`, when `setLocalDeveloperLevel(100)`, then DAO not called
- US-06: given Firebase returns RawUserStats, when `refreshProfile()`, then `upsert` called with entity
- US-07: given `currentUid()=null`, when `refreshProfile()`, then `Result.failure` (no Firebase call)
- US-08: given entity with developerLevel=100 in Room, when `refreshProfile()` with Firebase(developer=0), then Room entity has developerLevel=0

**Fakes:** `FakeUserStatsDao` (из `04-testing.md §4.1`), `FakeFirebaseUserStatsDataSource` (из `04-testing.md §4.4`)

## Validation

| Команда | Ожидаемый результат |
|---------|---------------------|
| `./gradlew :shared:feature:app-shell:data:jvmTest --no-configuration-cache` | GREEN — US-01..08 |
| `./gradlew :apps:android-next:assembleDebug --no-configuration-cache` | GREEN — нет DI errors (UserStatsRepositoryImpl в Koin) |

## Handoff Notes

После Phase 04 разблокирована Phase 06 (Sync Infrastructure) — `UserStatsRepositoryImpl.sync()` может быть добавлен как Syncable wrapper.
