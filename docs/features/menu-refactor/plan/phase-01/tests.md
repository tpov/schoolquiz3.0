---
phase: 01
role: test-dev
---

# Phase 01 — Test Tasks

## Pattern Invariants

- Framework: `kotlin.test` в KMP commonTest; `kotlin.test.Test`, `assertEquals`, `assertTrue`, `assertFailsWith`
- NO Turbine — только `.value`, `.take(N).toList()` для Flow testing
- Fakes для DAO/Repository — не MockK (project convention)
- Тест-dev НЕ модифицирует production code
- Удалённые тесты (overlay-related) удаляются atomically вместе с production файлами в backend-dev задаче

---

## Walking Skeleton Cleanup — Удалить test файлы

Следующие тест-файлы удаляются (вместе с production файлами в рамках backend-dev tasks). Все три файла перечислены в `04-testing.md §2.1` как обязательные к удалению:

| Файл | Причина удаления |
|------|-----------------|
| `shared/feature/qualification/domain/src/commonTest/.../dev_mode/model/LocalDeveloperOverrideTest.kt` | `LocalDeveloperOverride` удалена (ADR-HLA-02 revert) |
| `shared/feature/qualification/domain/src/commonTest/.../dev_mode/logic/EffectiveDeveloperLevelTest.kt` | `EffectiveDeveloperLevel` удалена (merge logic не нужна) |
| `shared/feature/qualification/domain/src/commonTest/.../dev_mode/fake/FakeLocalDeveloperOverrideRepositoryTest.kt` | overlay репозиторий удалён — фейк репозитория удаляется вместе с ним |

Проверить наличие всех трёх файлов в `shared/feature/qualification/domain/src/commonTest/` и удалить. После удаления: `grep -rn "LocalDeveloperOverride\|EffectiveDeveloperLevel\|FakeLocalDeveloperOverrideRepository" shared/feature/qualification/domain/` → 0 matches.

---

## Walking Skeleton Preservation — Переместить тест QualificationLevel

**Задача:** MOVE `QualificationLevelTest.kt`

- **From:** `shared/feature/qualification/domain/src/commonTest/kotlin/.../model/QualificationLevelTest.kt`
- **To:** `shared/core/foundation/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/foundation/QualificationLevelTest.kt`
- Обновить package declaration: `package com.tpov.schoolquiz.shared.core.foundation`
- Все 14 тестов (QL-01..QL-14) должны оставаться зелёными без изменений логики

---

## Сценарии: QualificationLevel (QL-01..QL-14)

**Файл:** `shared/core/foundation/src/commonTest/.../QualificationLevelTest.kt`
**Source:** `04-testing.md §3.1`

Все 14 сценариев перемещены без изменений; верифицировать что модуль `core:foundation` запускает их:

- QL-01: given `LEVEL_1`, when `.points`, then `100`
- QL-02: given `LEVEL_2`, when `.points`, then `200`
- QL-03: given `LEVEL_3`, when `.points`, then `300`
- QL-04: given `LEVEL_1`, when `isReachedBy(99)`, then `false`
- QL-05: given `LEVEL_1`, when `isReachedBy(100)`, then `true`
- QL-06: given `LEVEL_1`, when `isReachedBy(500)`, then `true`
- QL-07: given `LEVEL_2`, when `isReachedBy(100)`, then `false`
- QL-08: given `LEVEL_2`, when `isReachedBy(200)`, then `true`
- QL-09: given `LEVEL_3`, when `isReachedBy(200)`, then `false`
- QL-10: given `LEVEL_3`, when `isReachedBy(300)`, then `true`
- QL-11: given `LEVEL_1`, when `isReachedBy(-1)`, then `false` (negative guard)
- QL-12: given `LEVEL_1`, when `isReachedBy(0)`, then `false`
- QL-13: given `QualificationLevel`, when `.entries.size`, then `3`
- QL-14: given `QualificationLevel`, when `.entries.map { it.points }`, then `[100, 200, 300]` (order check)

---

## Сценарии: RegisterTap FSM (DM-01..DM-10)

**Файл:** `shared/feature/qualification/domain/src/commonTest/.../dev_mode/logic/RegisterTapTest.kt`
**Source:** `04-testing.md §3.2.1`

Walking Skeleton preserved. Обновить call sites: параметр `currentEffectiveDeveloperLevel` → `currentDeveloperLevel`. Логика тестов не меняется:

- DM-01: given `progress.count=0`, when `registerTap(_, _, any)`, then `NoChange(count=1)`
- DM-02: given `count=1, elapsed=100ms`, when tap, then `NoChange(count=2)`
- DM-03: given `count=5, elapsed=600ms`, when tap, then `Reset`
- DM-04: given `count=3, elapsed=500ms`, when tap, then `NoChange` (граница включена)
- DM-05: given `count=3, elapsed=501ms`, when tap, then `Reset`
- DM-06: given `count=8`, when tap in time, then `NoChange(count=9)`
- DM-07: given `count=9, developer=0`, when 10-й tap, then `Activated`
- DM-08: given `count=9, developer=100`, when 10-й tap, then `AlreadyDev`
- DM-09: given `count=9, developer=99`, when 10-й tap, then `Activated` (порог exclusive: 99 < 100)
- DM-10: given `count=9, elapsed=600ms`, when tap, then `Reset`

---

## Сценарии: ActivateDevModeUseCase (DM-11..DM-16) — REWRITE

**Файл:** `shared/feature/qualification/domain/src/commonTest/.../dev_mode/use_case/ActivateDevModeUseCaseTest.kt`
**Source:** `04-testing.md §3.2.2`

Полная перепись — убрать `overlayRepo`, заменить на lambda fakes:

**Fake setup** для тестов:
```
var onDevModeActivatedCallCount = 0
var developerLevelToReturn = 0
val useCase = ActivateDevModeUseCase(
    readCurrentDeveloperLevel = { developerLevelToReturn },
    onDevModeActivated = { onDevModeActivatedCallCount++ }
)
```

- DM-11: given 10-й тап с `developer=0`, when `invoke(TapProgress(count=9,...), nowMillis)`, then `onDevModeActivated` вызван 1 раз
- DM-12: given 5-й тап, when `invoke`, then `onDevModeActivated` NOT called, `onDevModeActivatedCallCount == 0`
- DM-13: given `developerLevelToReturn=100` (AlreadyDev), when 10-й тап, then `onDevModeActivated` NOT called
- DM-14: given любой input, when `invoke`, then return value == result from `registerTap`
- DM-15: given any input, when `invoke` called N times, then `readCurrentDeveloperLevel` called N times
- DM-16: given `onDevModeActivated` с delay (suspend coroutine delay), when `invoke`, then returns without hanging

**runTest / coroutines setup:**

```
// Использовать runTest из kotlinx-coroutines-test
// Для DM-16 — verify suspend lambda работает корректно через coroutineScope
```

---

## Сценарии: AppDatabase Migration (instrumented — не JVM)

**Файл:** `shared/core/persistence/src/androidTest/.../AppDatabaseMigrationTest.kt`
**Source:** `04-testing.md §5.1`
**Framework:** `MigrationTestHelper` (instrumented, нужен Android device/emulator)

- `version1_schema_valid`: given `MigrationTestHelper` creates DB version 1, when check tables, then `user_stats` и `catalogs` таблицы существуют
- `version1_user_stats_insert_query`: given empty DB, when `upsert(UserStatsEntity(...))` + `findByUid(uid)`, then entity returned
- `version1_catalog_insert_query`: given empty DB, when `insertAll([CatalogEntity(...)])` + `observeAll().take(1)`, then list not empty

**Note:** Instrumented тесты запускаются через `./gradlew :shared:core:persistence:connectedAndroidTest`. Требуют connected device. Могут быть отложены до Phase 08 (Integration Tests) если нет device в CI.

---

## Edge Cases Priority

| Сценарий | Priority | Why |
|----------|----------|-----|
| `isReachedBy(-1)` → false (QL-11) | P0 | Negative values должны быть отклонены |
| `registerTap` boundary 500ms (DM-04, DM-05) | P0 | Off-by-one в timing = spec violation |
| `AlreadyDev` guard (DM-08, DM-13) | P0 | Prevents double-write to Room |
| `ActivateDevModeUseCase` lambda called once per invoke (DM-15) | P1 | Stale closure detection |

---

## Validation

| Команда | Ожидаемый результат |
|---------|---------------------|
| `./gradlew :shared:core:foundation:jvmTest --no-configuration-cache` | 14 тестов GREEN |
| `./gradlew :shared:feature:qualification:domain:jvmTest --no-configuration-cache` | DM-01..DM-16 GREEN |
| `grep -rn "FakeLocalDeveloperOverrideRepository\|LocalDeveloperOverride\|EffectiveDeveloperLevel" shared/feature/qualification/domain/src/commonTest/` | 0 matches |
