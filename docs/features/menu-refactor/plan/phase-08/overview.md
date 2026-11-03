---
phase: 08
name: Integration Tests + Firebase Rules
complexity: simple
---

# Phase 08: Integration Tests + Firebase Rules

## Goal

Завершить тестовое покрытие через instrumented DAO tests и journey integration tests. Добавить Firebase Security Rules для `catalogs` collection. После этой фазы весь implementation stack готов для end-to-end тестирования.

## Scope

- CREATE `AppDatabaseMigrationTest` (instrumented) — schema validation v1
- CREATE `UserStatsDaoTest` (instrumented) — DAO boundary tests (4 scenarios)
- CREATE `CatalogDaoTest` (instrumented) — DAO boundary tests (CF-06..10)
- UPDATE `firestore.rules` — добавить `catalogs` read block (authenticated users)
- CREATE journey integration tests (5 catalog + dev-mode auto-deactivation):
  - `CatalogFirstFetchIntegrationTest`
  - `CatalogWarmCacheIntegrationTest`
  - `CatalogOfflineEmptyIntegrationTest`
  - `SyncDeactivatesDevModeIntegrationTest`

**Note:** `DevModeActivationIntegrationTest`, `SyncNowFlowIntegrationTest`, `DesignCatalogRenderConditionTest` — созданы в Phase 07. Не дублировать.

## Layer

infrastructure (instrumented tests + Firebase security rules)

## Role Inputs

- `backend.md` — firebase rules update
- `frontend.md` — none
- `tests.md`

## Dependencies

phases_ref: [phase-01, phase-04, phase-05, phase-06, phase-07]
- Phase 01: `AppDatabase` schema + `UserStatsDao` + `CatalogDao` implementations
- Phase 04: `UserStatsRepositoryImpl` + `FakeUserStatsDao` (created in phase-04 tests)
- Phase 05: `CatalogRepositoryImpl` + `FakeXxx` fakes
- Phase 06: `SyncWorker`
- Phase 07: `DefaultRootComponent` integration complete

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 4: нет Firebase Rules для catalogs | backend-dev | `firestore.rules` | Firebase security model | добавить `catalogs` read block | firebase deploy + security check |
| Problem 2: нет тестов для sync pipeline | test-dev | `shared/core/persistence/androidTest/` | ADR-HLA-03 (central DB) | AppDatabaseMigrationTest + DAO tests | `connectedDebugAndroidTest` GREEN |
| Problem 5: CatalogRepository integration не тестировалась | test-dev | `shared/core/catalog/data/` | Phase 05 fakes | journey integration tests | JVM test GREEN |

## New Files

- `shared/core/persistence/src/androidTest/kotlin/.../AppDatabaseMigrationTest.kt`
- `shared/core/persistence/src/androidTest/kotlin/.../UserStatsDaoTest.kt`
- `shared/core/persistence/src/androidTest/kotlin/.../CatalogDaoTest.kt`
- `shared/core/catalog/data/src/commonTest/kotlin/.../CatalogFirstFetchIntegrationTest.kt`
- `shared/core/catalog/data/src/commonTest/kotlin/.../CatalogWarmCacheIntegrationTest.kt`
- `shared/core/catalog/data/src/commonTest/kotlin/.../CatalogOfflineEmptyIntegrationTest.kt`
- `shared/feature/app-shell/data/src/commonTest/kotlin/.../SyncDeactivatesDevModeIntegrationTest.kt`

## Modified Files

- `firestore.rules` — добавить `catalogs` read block

## Deleted Files

none

## Acceptance Criteria

- [ ] `AppDatabaseMigrationTest` — 3 сценария: schema v1 valid + user_stats insert + catalogs insert
- [ ] `UserStatsDaoTest` — 4 сценария: upsert+observe, updateDeveloperLevel targeted, upsert перезаписывает developerLevel, null uid flow
- [ ] `CatalogDaoTest` (CF-06..10) — 5 сценариев: insertAll+observeAll, replaceAll атомарная, replaceAll удаляет старые, observeAll sort ASC, findById null
- [ ] Journey tests CF-like: `CatalogFirstFetchIntegrationTest` (empty Room → fetch → replaceAll), `CatalogWarmCacheIntegrationTest` (pre-populated → observeAll без Firestore), `CatalogOfflineEmptyIntegrationTest` (empty Room + Firestore error → emptyList no crash)
- [ ] `SyncDeactivatesDevModeIntegrationTest`: `refreshProfile()` с Firestore returning developer=0 → Room `developerLevel=0` → Flow эмитит developer=0 (перезапись локального 100)
- [ ] `firestore.rules` содержит `catalogs` read rule для `request.auth != null`
- [ ] Все instrumented тесты проходят на connected device/emulator

## Pattern Invariants

- Instrumented тесты используют `Room.inMemoryDatabaseBuilder().allowMainThreadQueries()` — не production DB
- `MigrationTestHelper` для migration tests — стандартный способ проверки schema (per `.claude/rules/testing.md`)
- JVM integration tests используют фейки, не Android API — в `commonTest`
- `test-dev` НЕ модифицирует production code

## State Matrix Coverage

Phase 08 не добавляет production code — только тесты. Покрывает оставшиеся сценарии из `04-testing.md`: CF-06..10 (DAO boundary), AppDatabaseMigrationTest, US-тесты, journey tests.

## Domain Contract Coverage

N/A (только тесты, production domain не меняется)

## Tests Required

Phase 08 = целиком тестовая. Все файлы — тесты. Production code не меняется.

## Validation

| Команда | Ожидаемый результат |
|---------|---------------------|
| `./gradlew :shared:core:catalog:data:jvmTest --no-configuration-cache` | GREEN — CF-11..23 + journey integration tests |
| `./gradlew :shared:feature:app-shell:data:jvmTest --no-configuration-cache` | GREEN — US-01..08 + SyncDeactivatesDevModeIntegrationTest |
| `./gradlew :shared:core:persistence:connectedDebugAndroidTest` | GREEN — AppDatabaseMigrationTest + UserStatsDaoTest + CatalogDaoTest |
| `./gradlew :apps:android-next:assembleDebug --no-configuration-cache` | GREEN (production code не менялась в Phase 08) |

## Handoff Notes

После Phase 08 фича `menu-refactor` полностью реализована и протестирована:
- Все 77 spec-traced сценариев покрыты
- Walking Skeleton reuse / deletion выполнен (Phase 01)
- Firebase Rules обновлены
- Presentation integration готова (Phase 07)
- End-to-end тестирование возможно
