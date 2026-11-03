---
phase: 03
role: test-dev
---

# Phase-03 Test Tasks

All tests are pure JVM (commonTest). Fakes from Walking Skeleton + new FakeSyncStateRepository.

---

## Pattern Invariants

- Setup: все 9 fakes передаются в `CascadingSyncOrchestrator` constructor
- `FakeCatalogRepository` ДОЛЖЕН трекать: `refreshCalls`, `lastCursor`, `nextChangedIds: Set<CatalogId>`
- `FakeSyncStateRepository.setCursorCalls: MutableList<Pair<String, Long>>` — для проверки cursor advancement
- `FakeAuthRepository` из Walking Skeleton (`shared/feature/app-shell/domain/commonTest`)
- test-dev не модифицирует production code

---

## 1. Verify FakeSyncStateRepository (pre-created in phase-02)

- **Файл:** `shared/core/sync/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/sync/fake/FakeSyncStateRepository.kt`
- **Создаётся:** phase-02/tests.md task (Signature Card там). Phase-03 ИСПОЛЬЗУЕТ, не дублирует.
- **Verify:** файл существует с `setCursorCalls`, `resetAll()`, `getCursor`, `setCursor`.
- **Если отсутствует:** phase-02/tests.md должна быть выполнена первой — blocker.

---

## 2. Create FakeCatalogRepository (for sync tests)

- **Файл:** `shared/core/sync/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/sync/fake/FakeCatalogRepository.kt`
- **Тип:** class

**Key fields:**
- `var refreshCalls = 0`
- `var nextChangedIds: Set<CatalogId> = emptySet()` — IDs returned as "changed" by refreshFromRemote
- `var nextLocalCvMap: Map<CatalogId, Long> = emptyMap()` — что хранится локально (для cv comparison)
- `fun seedWithLocalCv(catalogId: String, localCv: Long)` — helper

---

## 3. CascadingSyncOrchestratorTest

- **Файл:** `shared/core/sync/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/sync/CascadingSyncOrchestratorTest.kt`

**Setup:** Создать 9 fake полей через конструктор `CascadingSyncOrchestrator`. Использовать:
- `FakeCatalogRepository` (task #2 этой фазы) — `initialUid = null`
- `FakeQuestRepository`, `FakeSectionRepository`, `FakeThemeRepository`, `FakeLessonRepository`, `FakeQuestionRepository` — из Walking Skeleton в соответствующих `*/domain/commonTest/fake/`
- `FakeSyncStateRepository` (task #1 этой фазы)
- `FakeAuthRepository(initialUid = "test-uid")` — из app-shell Walking Skeleton
- `FakeUserStatsRepository` — из app-shell Walking Skeleton

Orchestrator конструируется с передачей всех 9 fakes через именованные параметры (порядок: catalogRepo, questRepo, sectionRepo, themeRepo, lessonRepo, questionRepo, syncStateRepo, authRepo, userStatsRepo). Каждый `@Test` использует свежий orchestrator или сбрасывает fakes через `resetAll()` / `beforeEach`.

**Сценарии:**

```
when_catalog_cv_unchanged_then_quests_not_fetched:
  GIVEN: fakeCatalogRepo.nextChangedIds = emptySet() (no catalogs with cv growth)
  WHEN: orchestrator.sync()
  THEN: fakeQuestRepo.refreshCalls == 0
  [AC#10, scenario 28]

when_catalog_cv_grew_then_quests_fetched:
  GIVEN: fakeCatalogRepo.nextChangedIds = setOf(CatalogId("c1"))
  WHEN: orchestrator.sync()
  THEN: fakeQuestRepo.refreshCalls == 1
  AND: fakeQuestRepo.lastRefreshCatalogIds == setOf(CatalogId("c1"))
  [AC#10, scenario 29]

when_quest_cv_grew_then_sections_fetched:
  GIVEN: catalog changed → quest changed with cv growth
  WHEN: orchestrator.sync()
  THEN: fakeSectionRepo.refreshCalls == 1
  [AC#11]

when_no_changed_quests_then_sections_not_fetched:
  GIVEN: catalog changed but fakeQuestRepo.setNextRefreshChanged(emptySet())
  WHEN: orchestrator.sync()
  THEN: fakeSectionRepo.refreshCalls == 0
  [Matrix 3.7 / EDGE 3.7]

when_uid_null_then_quest_refresh_called_with_null_uid:
  GIVEN: fakeAuth.signOut() → uid = null
  WHEN: orchestrator.sync() with catalog changed
  THEN: fakeQuestRepo.lastRefreshUid == null
  [EDGE 4.6 / ADR-CMP-49 guest mode]

when_sync_runs_then_availableShelves_equals_home_and_arena:
  GIVEN: catalog changed, any uid
  WHEN: orchestrator.sync()
  THEN: fakeQuestRepo.lastRefreshShelves == setOf("home", "arena")
  [Matrix 4.1 / 4.5]

when_step2_fails_then_questsCursor_not_advanced:
  GIVEN: fakeSyncState.setCursor("catalogs", 1000L)
  AND: catalog changed
  AND: fakeQuestRepo.setNextRefreshFailure(IOException("timeout"))
  WHEN: orchestrator.sync()
  THEN: result.isFailure == true
  AND: fakeSyncState.getCursor("quests") == 0L (not advanced)
  [AC#54]

when_step1_succeeds_then_catalogsCursor_advanced:
  GIVEN: catalog refreshFromRemote returns success
  AND: CatalogRepositoryImpl advances cursor internally (fakeSyncState.setCursor("catalogs", 500L) simulated)
  WHEN: orchestrator.sync()
  THEN: fakeSyncState.getCursor("catalogs") == 500L
  Note: CatalogRepositoryImpl manages its own cursor; orchestrator verifies it via fakeSyncState

when_31_parent_ids_then_batched_into_two_fetches:
  GIVEN: fakeQuestRepo.setNextRefreshChanged(ids of 31 quests)
  AND: fakeSectionRepo.captureRefreshBatches = true
  WHEN: orchestrator.sync()
  THEN: fakeSectionRepo.batchedRefreshCalls.size >= 2
  AND: each batch.size <= 30
  [AC#50]

when_step_fails_then_result_failure:
  GIVEN: fakeQuestRepo.setNextRefreshFailure(RuntimeException("test"))
  AND: catalog changed
  WHEN: orchestrator.sync()
  THEN: result.isFailure
  [AC#17]

when_cancellation_exception_rethrown:
  GIVEN: fakeQuestRepo throws CancellationException
  WHEN: orchestrator.sync()
  THEN: CancellationException propagates (not wrapped in Result.failure)
```

---

## 4. CascadeSyncIntegrationTest

- **Файл:** `shared/core/sync/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/sync/CascadeSyncIntegrationTest.kt`

```
process_death_then_full_resync_is_idempotent:
  GIVEN: orchestrator.sync() runs once, questRepo has some data seeded
  AND: fakeSyncState.resetAll() (simulate process death — all cursors to 0)
  WHEN: orchestrator.sync() runs again
  THEN: fakeQuestRepo.snapshot() contains same data (upsert idempotent)
  [AC#56]

retry_after_partial_fail_then_cursor_advances:
  GIVEN: first sync: catalog succeeds (cursor=500), quest fails (cursor stays 0)
  AND: second sync starts
  WHEN: second sync completes successfully
  THEN: fakeSyncState.getCursor("quests") > 0
  [AC#57]

full_cascade_6_levels_with_fakes:
  GIVEN: fakeAll repos seeded with changed data at all 6 levels
  WHEN: orchestrator.sync()
  THEN: all 6 repos had refreshCalls >= 1 (full cascade executed)
  [AC#19 integration level]

when_server_invariant_B_applied_then_client_pulls_descendants:
  GIVEN: quest.visibleOn changed → all descendants get fresh lastModifiedAt (simulated via fakes)
  WHEN: orchestrator.sync() with changedQuestIds having cv growth
  THEN: sectionRepo.refreshCalls == 1 AND themeRepo.refreshCalls == 1 ...
  [AC#58 — domain-level portion; cross-module integration test]

when_retry_with_in_memory_cursors_intact_then_step1_empty:
  GIVEN: first sync succeeded → catalogsCursor = 1000L
  AND: process still alive (fakeSyncState intact)
  WHEN: second sync runs with same fakeCatalogRepo returning empty (no new data)
  THEN: fakeQuestRepo.refreshCalls == 0 (no changed catalogs → no quest fetch)
  [AC#55]
```
