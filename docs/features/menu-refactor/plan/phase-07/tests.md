---
phase: 07
role: test-dev
---

# Phase 07 — Test Tasks

## Pattern Invariants

- Тесты для `DefaultRootComponent` — JVM unit tests (Decompose `TestComponentContext` без Android runtime)
- `FakeWorkManager` — не настоящий WorkManager; тест через captured calls (spy на `enqueueUniqueWork`)
- `FakeUserStatsDao` — уже должен существовать после Phase 04; если нет — создать по blueprint из `04-testing.md §4.1`
- Coroutines test: `runTest` + `StandardTestDispatcher`; `advanceUntilIdle()` для завершения `scope.launch`
- Turbine НЕ используется — Flow тестируется через `.take(1).toList()` или `channel.receive()`

---

## DevModeActivationIntegrationTest

**Файл:** `android/feature/app-shell/presentation/src/test/kotlin/.../DevModeActivationIntegrationTest.kt`

**Fake setup:**
```
FakeUserStatsRepository:
  - var setLocalDeveloperLevelCalls: Int = 0
  - var lastDeveloperLevel: Int = -1
  - override suspend fun setLocalDeveloperLevel(value: Int) { setLocalDeveloperLevelCalls++; lastDeveloperLevel = value }
  - override fun observeStats(): Flow<UserStats> = flowOf(UserStats.guest())
  - override suspend fun currentStats() = UserStats.guest()
  - override suspend fun refreshProfile() = Result.success(Unit)
```

**Сценарии:**

- DM-phase7-01: given `DefaultRootComponent` с `FakeUserStatsRepository`, when `onVersionTap(nowMillis)` × 10 (тапы < 500ms apart), then `fakeRepo.setLocalDeveloperLevelCalls == 1` + `fakeRepo.lastDeveloperLevel == 100` + `_events` содержит `RootEvent.DevModeActivated`
- DM-phase7-02: given `DefaultRootComponent` с developer уже 100 (stats через `FakeUserStatsRepository` что возвращает `developer=100`), when `onVersionTap()` × 1, then `fakeRepo.setLocalDeveloperLevelCalls == 0` + `_events` содержит `RootEvent.DevModeAlreadyActive`
- DM-phase7-03: given `_tapProgress` после 9 тапов (count=9), when `onVersionTap(nowMillis)` — 10-й тап, then `_tapProgress.value == TapProgress.initial` (сброс при Activated)
- DM-phase7-04: given `onVersionTap(now)`, then `onVersionTap(now + 1000)` (> 500ms reset threshold), then `_tapProgress.value.count == 1` (сброс прогресса при превышении интервала)

**Note:** `DefaultRootComponent` принимает `ComponentContext` — использовать `TestComponentContext()` из Decompose test utils.

---

## SyncNowFlowIntegrationTest

**Файл:** `android/feature/app-shell/presentation/src/test/kotlin/.../SyncNowFlowIntegrationTest.kt`

**Fake setup:** `FakeCapturingWorkManager` — фейк (не extends `WorkManager`; `WorkManager` — abstract класс, extends требует Android runtime). Реализовать как capturing wrapper: захватить calls через лямбды или через отдельный CapturingCallRecorder.
- `var enqueueUniqueWorkCalls: Int = 0`
- `var lastWorkName: String? = null`
- `var lastPolicy: ExistingWorkPolicy? = null`

Альтернатива: MockK relaxed mock (test-dev выбирает удобный подход для WM — FakeCapturingWorkManager или MockK relaxed; оба acceptable). Fixture note: если используется MockK — проверить что `relaxed = true` и что `enqueueUniqueWork` вызов корректно проверяется через capture или verify без inline code.

**Сценарии:**

- SN-phase7-01: test name `onSyncNow enqueues unique work with REPLACE policy`; given `DefaultRootComponent` с capturing WorkManager; when `onSyncNow()`; then `enqueueUniqueWork` был вызван один раз с аргументами `workName = SyncWorker.WORK_NAME_MANUAL` и `policy = ExistingWorkPolicy.REPLACE`
- SN-phase7-02: test name `onSyncNow emits SyncStarted event`; given `DefaultRootComponent` с capturing WorkManager; when `onSyncNow()`; then `rootComponent.events.take(1).toList()` содержит `RootEvent.SyncStarted`
- SN-phase7-03: test name `two consecutive onSyncNow calls both reach WorkManager`; given `DefaultRootComponent`; when `onSyncNow()` дважды; then `enqueueUniqueWorkCalls == 2` (REPLACE policy позволяет повторные вызовы)

---

## DesignCatalogRenderConditionTest

**Файл:** `shared/feature/app-shell/domain/src/commonTest/kotlin/.../VisibilityDesignCatalogTest.kt`

**Note:** Тест `visibleFooterActions()` функции (pure logic, JVM test в `app-shell:domain`):

**Сценарии:**

- DC-phase7-01: given `visibleFooterActions(isDebugBuild=false, stats=UserStats.guest())` (developer=0), then result == `[DrawerFooterAction.About]` (нет DesignCatalog, нет SyncNow)
- DC-phase7-02: given `visibleFooterActions(isDebugBuild=false, stats=statsWithDeveloper(100))`, then result contains `DrawerFooterAction.DesignCatalog` and `DrawerFooterAction.SyncNow`
- DC-phase7-03: given `visibleFooterActions(isDebugBuild=true, stats=UserStats.guest())` (developer=0, debug build), then result contains `DrawerFooterAction.DesignCatalog` and `DrawerFooterAction.SyncNow` (debug OR-bypass)
- DC-phase7-04: given `visibleFooterActions(isDebugBuild=false, stats=statsWithDeveloper(99))` (developer=99, below threshold), then result == `[DrawerFooterAction.About]`

**Helper:**
```
fun statsWithDeveloper(level: Int): UserStats = UserStats(
    qualification = Qualification(developer = level, /* other fields 0 */),
    /* other fields default */
)
```

---

## Validation

| Команда | Ожидаемый результат |
|---------|---------------------|
| `./gradlew :android:feature:app-shell:presentation:test --no-configuration-cache` | GREEN — DM-phase7-01..04, SN-phase7-01..03 |
| `./gradlew :shared:feature:app-shell:domain:jvmTest --no-configuration-cache` | GREEN — DC-phase7-01..04 (visibleFooterActions) |
| `./gradlew :android:core:designsystem:test --no-configuration-cache` | GREEN (no tests in phase-07 for designsystem; compile only) |
| `./gradlew :apps:android-next:assembleDebug --no-configuration-cache` | GREEN — все compile errors устранены |
