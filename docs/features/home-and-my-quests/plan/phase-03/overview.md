---
phase: 03
name: CascadingSyncOrchestrator + SyncLevel
complexity: complex
date: 2026-04-23
---

# Phase-03: CascadingSyncOrchestrator + SyncLevel

## Goal

Создать `CascadingSyncOrchestrator` — центральный компонент каскадной синхронизации — и заменить им текущий список `Syncable` в `SyncModule`:
- `CascadingSyncOrchestrator` (9 params) с recursive `syncCascade(level, parentIds)`
- `SyncLevel` enum с `collectionId` + `next` computed properties
- Обновить `SyncModule.kt` — заменить `get<CatalogRepository>() as Syncable` на `get<CascadingSyncOrchestrator>()`
- Финальная интеграция курсора для всех 6 уровней
- `CascadingSyncOrchestratorTest` + `CascadeSyncIntegrationTest`

## Scope

`shared/core/sync` (CascadingSyncOrchestrator + SyncLevel), `apps/android-next/di/SyncModule.kt` (update List<Syncable>).

## Layer

data + infrastructure (orchestration)

## Role Inputs

- `backend.md` — backend-dev
- `tests.md` — test-dev

## Review Tags

- `concurrency-review` (6-level recursive cascade with Flow, suspend calls, cursor management across coroutine boundaries; Result propagation; early-exit logic)
- `architecture-review` (CascadingSyncOrchestrator зависит от 6 domain interfaces + SyncStateRepository + AuthRepository + UserStatsRepository — ensure no bidirectional coupling)

---

## Options Considered

| Критерий | Option A — recursive CascadingSyncOrchestrator + SyncLevel enum (recommended) | Option B — 6 separate Syncable impls | Option C — extend SyncWorker |
|----------|--------------------------------------------------------------------------------|--------------------------------------|------------------------------|
| Type safety | enum exhaustive `when` — compile-safe | Runtime-ordered list | No type safety |
| Testability | single test class covers all 6 levels | 6 separate tests, temporal coupling | Android Worker hard to test |
| Cursor management | centralized in orchestrator | distributed, temporal coupling risk | scattered in Worker |
| Adding new level | +1 enum value → compile error at `when` sites | +1 Syncable + manual ordering | SRP violation |
| Coupling | domain interfaces only | same | god object |

**Recommended: Option A (ADR-CMP-49)**

**Rationale:** ADR-CMP-49 (accepted by User Decision #49). Centralized cursor management, compile-safe level ordering, single testable unit.

**Rejected Option B:** 6 отдельных Syncable создают скрытую temporal coupling — порядок в `listOf(...)` критичен, нет compile-time enforcement. `as Syncable` cast — pre-existing tech debt; умножение на 6 увеличивает риск.

**Rejected Option C:** SRP violation — SyncWorker god object, тестирование Android Worker сложнее.

---

## Traceability

| Problem (from 2-grounding.md) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|-------------------------------|-----------|-------------|-----------------|-------------|-----------|
| P2: Orchestration entry point не существует | `shared/core/sync` (NEW orchestrator) | `SyncWorker → List<Syncable>` | 9-param constructor per 06-api-contract.md:341; cursor via SyncStateRepository | Create CascadingSyncOrchestrator; update SyncModule | `./gradlew :shared:core:sync:jvmTest` |
| P4: SyncStateRepository не подключён (cursor management) | `SyncModule.kt` (done phase-02 for binding), orchestrator usage | Orchestrator.sync() reads/writes cursor per step | cursor only advanced on success | CascadingSyncOrchestrator uses getCursor/setCursor per step | `CascadingSyncOrchestratorTest` cursor tests |

---

## State Matrix Coverage

**Matrix 3 (Cascade recurse predicate):**
- Row 3.1 (absent + cv > 0 → RECURSE): в CascadingSyncOrchestrator `changedParentIds` calculation
- Row 3.2 (absent + cv == 0 → STOP): early exit in cascade decision
- Row 3.3 (upserted + cv > local → RECURSE): cv comparison in changed ids
- Row 3.4 (upserted + cv == local → STOP): not included in changedParentIds
- EDGE 3.5-3.8: covered in CascadingSyncOrchestratorTest

**Matrix 4:**
- Row 4.1 (baseline availableShelves={"home","arena"}): hard-coded in orchestrator
- EDGE 4.5 (baseline qualification): verified in CascadingSyncOrchestratorTest
- EDGE 4.6 (guest uid=null → Query A skipped): covered in CascadingSyncOrchestratorTest

---

## New Files

| File | Module |
|------|--------|
| `shared/core/sync/src/commonMain/kotlin/.../CascadingSyncOrchestrator.kt` | sync |
| `shared/core/sync/src/commonMain/kotlin/.../SyncLevel.kt` | sync |
| `shared/core/sync/src/commonTest/.../CascadingSyncOrchestratorTest.kt` | sync |
| `shared/core/sync/src/commonTest/.../CascadeSyncIntegrationTest.kt` | sync |
| `shared/core/sync/src/commonTest/fake/FakeSyncStateRepository.kt` | sync — created in phase-02/tests.md; phase-03 reuses |
| `shared/core/sync/src/commonTest/fake/FakeCatalogRepository.kt` (if not exists) | sync |

## Modified Files

| File | Change |
|------|--------|
| `apps/android-next/src/main/.../di/SyncModule.kt` | Replace `get<CatalogRepository>() as Syncable` with `get<CascadingSyncOrchestrator>()`; add `single<CascadingSyncOrchestrator> { ... }` (no named qualifier) |

## Deleted Files

None.

---

## Dependencies

- Phase-02 MUST BE COMPLETE (all 5 Repository interfaces implemented + SyncStateRepository in Koin)
- `AuthRepository` interface — exists in Walking Skeleton (shared/feature/app-shell/domain)
- `AuthRepositoryImpl` — exists (phase-04 adds to Koin; for phase-03 orchestrator, `get<AuthRepository>()` must be available — **BLOCKER**: if phase-04 not done, Koin graph fails at runtime)
  - Resolution: phase-03 can use `AuthRepository` in code but Koin registration of `AuthRepositoryImpl` happens in phase-04 `AppShellDataModule`. Lead должен убедиться что phase-04 backend task (AuthRepository Koin binding) выполнен перед integration test на device.
  - JVM tests (CascadingSyncOrchestratorTest) используют `FakeAuthRepository` → no Koin dependency needed for test

---

## Acceptance Criteria (phase-03 scope)

- AC#10: catalog cv unchanged → quests sync skipped — `CascadingSyncOrchestratorTest` green
- AC#11: quest cv grew → sections pulled — `CascadingSyncOrchestratorTest` green
- AC#16: SyncWorker runs cascading steps (verified via SyncModule update + manual test)
- AC#17: SyncWorker network fail → Result.retry() — `CascadingSyncOrchestratorTest.when_step_fails_then_returns_failure`
- AC#50: batch > 30 parent ids — `CascadingSyncOrchestratorTest`
- AC#54: catalog step succeeds + quest step fails → catalogsCursor advanced, questsCursor not advanced
- AC#55: retry with same cursor → idempotent
- AC#56: process death → full resync is idempotent
- AC#57: after successful retry → cursor advances

---

## Tests Required

```
CascadingSyncOrchestratorTest:
  - when_catalog_cv_unchanged_then_quests_not_fetched (AC#10, scenario 28)
  - when_catalog_cv_grew_then_quests_fetched (AC#10, scenario 29)
  - when_quest_cv_grew_then_sections_fetched (AC#11)
  - when_no_changed_quests_then_sections_not_fetched (Matrix 3.7)
  - when_step_2_fails_then_questsCursor_not_advanced (AC#54)
  - when_step_1_succeeds_then_catalogsCursor_advanced (AC#54 positive)
  - when_31_parent_ids_then_batched_into_two_fetches (AC#50)
  - when_uid_null_then_query_A_skipped (EDGE 4.6)
  - when_sync_runs_then_availableShelves_equals_home_and_arena (Matrix 4.1)
  - when_step_fails_then_result_failure (AC#17)

CascadeSyncIntegrationTest:
  - process_death_then_full_resync_idempotent (AC#56)
  - when_retry_after_partial_fail_then_cursor_advances (AC#57)
  - full_cascade_6_levels_with_fakes (AC#19 integration)
```

---

## Pattern Invariants

- `syncCascade` ДОЛЖЕН быть `internal` — только `sync()` публичный (enforces single entry point through Syncable)
- `CascadingSyncOrchestrator` ДОЛЖЕН регистрироваться через `single<CascadingSyncOrchestrator> { ... }` (без named qualifier); включается в `List<Syncable>` через `get<CascadingSyncOrchestrator>()` — per `06-api-contract.md:787-797` SSoT
- Cursor advancement: каждый level вызывает `getCursor` в начале и `setCursor` ТОЛЬКО при success
- Batch splitting: если `parentIds.size > 30`, orchestrator разбивает на chunks ≤30 перед вызовом `refreshByParents`
- `availableShelves` — hard-coded `setOf("home", "arena")` в MVP (не dynamic from UserStats — future OQ-CMP-SHELVES)
- SyncModule ДОЛЖЕН содержать `single<CascadingSyncOrchestrator> { CascadingSyncOrchestrator(...) }` (без named qualifier) + `get<CascadingSyncOrchestrator>()` в `List<Syncable>` — per `06-api-contract.md:787-797` SSoT

---

## Validation

```bash
# JVM tests — no Koin dependency, run before phase-04 is complete
./gradlew :shared:core:sync:jvmTest

# Full app build — ONLY after phase-04 backend task #1 (AuthRepository Koin binding in AppShellDataModule)
# Without phase-04 complete: Koin graph fail at runtime (get<AuthRepository>() DefinitionNotFoundException)
./gradlew allTests
./gradlew assemble
```

---

## Handoff Notes

- `SyncModule.kt` изменение: убрать `get<CatalogRepository>() as Syncable` — CatalogRepository больше не является direct Syncable в List. Теперь CatalogRepositoryImpl вызывается ЧЕРЕЗ CascadingSyncOrchestrator.
- `UserStatsRepository` передаётся как 9-й параметр в CascadingSyncOrchestrator (для будущих dynamic shelves). MVP использует hardcoded shelves, но dependency injection уже настраивается.
- Phase-03 JVM тесты не требуют Koin — чистый unit test с fakes. Интеграционный тест с реальным SyncWorker (AC#16) — manual smoke.
