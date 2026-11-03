---
date: 2026-04-19
feature: menu-refactor / dev-mode
type: new-feature
commit: 7c52c200
parent: 0-spec.md
depends-on: 0-spec-qualification-levels.md
---

# Sub-spec: Developer Mode (10-tap unlock)

**[UPDATED IN RESEARCH 2026-04-20: revert codex fix #2 — simpler model per user decision]**

Локальный dev-mode в стиле Android "7-tap on Build number". 10 тапов по `v$versionName` в drawer footer → **прямая запись `UserStats.qualification.developer = QualificationLevel.LEVEL_1.points` (100) в local Room таблицу** (без отдельной `LocalDeveloperOverride` entity). DEVELOPER становится суперквалификацией: удовлетворяет любые `requiredRoles`. Сервер про это не знает. При следующем sync (WorkManager `SyncWorker.refreshProfile()`) — локальная Room row перезаписывается со Firestore → `developer=0` → dev mode автоматически выключается.

## Source

- "сделать режим разработчика, 10 раз по нажатию на версию приложения — становится 100 очков квалификации разработчика"
- "устанавливается поле разработчика на 100, а там уже все функции которые к нему привязаны они сами разблокируются, на данный момент это пункты в меню"
- "режим разработчика временно и только локально повышает очки разработчика"
- "он сам отключется при следующей синхронизации"
- "обновится юай, например отобразиться все пункты меню. и разработчик обозначает что он и тестировщик и т д, короче там добавь не (и разработчик 100) а (или разработчик 100)"
- "просто локально ставишь 100" / "никак, 100 очков и есть дев режим"

## Requirements

### Functional Requirements

1. **Trigger**: 10 последовательных тапов по `Text("v$versionName")` в `DrawerFooter` (`android/feature/app-shell/presentation/ui/drawer/DrawerFooter.kt:58-63`) — [USER DECIDED] "10 раз по нажатию на версию"
2. **Reset timeout**: счётчик тапов сбрасывается на 0, если пауза между тапами > 500 мс — [USER DECIDED] "500 мс (быстрый)"
3. **Feedback**: только на 10-м успешном тапе показать Snackbar "Режим разработчика включён" — [USER DECIDED] "Только на 10-м тапе"
4. **Эффект активации** [UPDATED 2026-04-20]: `ActivateDevModeUseCase` вызывает `UserStatsRepository.setLocalDeveloperLevel(100)` — обновляет `developer` поле в local Room `UserStatsEntity`. Нет отдельной overlay entity. Flow `observeStats()` эмитит обновлённый `UserStats` → UI реактивно перерисовывается — [USER DECIDED] "устанавливается поле разработчика на 100"
5. **Guard** — if already developer [UPDATED 2026-04-20]: если `stats.qualification.developer >= QualificationLevel.LEVEL_1.points` (читается напрямую из current `UserStats`) — 10-tap no-op + toast "Уже в режиме разработчика". Ничего не пишется в БД — [USER DECIDED]
6. **Deactivation** [UPDATED 2026-04-20]: local `UserStatsEntity.developer` перезаписывается server value автоматически при **успешном** `SyncWorker.refreshProfile()` (periodic WorkManager job + manual SyncNow). Ручного UI-toggle нет. При server `developer=0` → local тоже `0` → dev mode off — [USER DECIDED] "сам отключится при синхронизации"
7. **Superqualification rule** [UPDATED 2026-04-20]: `stats.qualification.developer >= QualificationLevel.LEVEL_1.points` — суперквалификация. `isVisible(section, stats)` возвращает `true` если либо (a) `stats.qualification.developer >= LEVEL_1.points`, либо (b) `section.requiredRoles.all { (role, min) -> actualLevel(role, stats) >= min }`. Overlay как отдельная концепция НЕТ — только `stats.qualification.developer` — [USER DECIDED] "или разработчик 100"
8. **DesignCatalog в footer + render path** [UPDATED 2026-04-20]: `visibleFooterActions` учитывает developer level. Signature: `visibleFooterActions(isDebugBuild: Boolean, stats: UserStats): List<DrawerFooterAction>` (без `overlay` — используется `stats.qualification.developer`). DesignCatalog виден если `isDebugBuild || stats.qualification.developer >= QualificationLevel.LEVEL_1.points`. Condition `canRenderDesignCatalog(isDebugBuild, stats) = isDebugBuild || stats.qualification.developer >= LEVEL_1.points` — [USER DECIDED] + Codex fix #3
9. **Side effect model** [UPDATED 2026-04-20]: Sync pipeline **перезаписывает** local `UserStatsEntity` целиком из Firestore. Единственное exception — **между sync-ами** client может временно записать `developer=100` для Dev Mode. Это explicit deviation от ADR-0006 "клиент не пишет в profile" для поля `developer` в **local Room cache** (не отправляется на сервер) — [USER DECIDED] + REVERT Codex fix #2
10. **UserStats local storage contract** [UPDATED 2026-04-20]:
    ```kotlin
    // Room entity — central storage для UserStats
    @Entity(tableName = "user_stats")
    data class UserStatsEntity(
        @PrimaryKey val uid: String,
        val currentSkill: Int,
        val nickname: String,
        val avatarUrl: String?,
        val streakDays: Int,
        val heartsBalance: Int,
        val starsBalance: Int,
        val nolicsBalance: Int,
        val goldBalance: Int,
        val premium: Boolean,
        // Qualification fields:
        val testerLevel: Int,
        val moderatorLevel: Int,
        val sponsorLevel: Int,
        val translatorLevel: Int,
        val adminLevel: Int,
        val developerLevel: Int,
    )

    // Repository API:
    interface UserStatsRepository {
        fun observeStats(): Flow<UserStats>              // reads from Room
        suspend fun refreshProfile(): Result<Unit>       // Firestore fetch → overwrite Room row
        suspend fun setLocalDeveloperLevel(value: Int)   // Dev Mode only — writes developer field locally
    }
    ```
    `setLocalDeveloperLevel` — **единственный** write method доступный клиенту для поля `developer`. Остальные поля — write-only через `refreshProfile()` (internal to repository implementation).

### Non-Functional Requirements

1. **Pure core**: счётчик тапов + decision rules — pure functions (testable без Android) — [DELEGATED: domain purity invariant]
2. **No leakage**: domain tap logic не зависит от `System.currentTimeMillis()` напрямую — принимает `now: Instant` как параметр — [DELEGATED: testability]
3. **Thread safety**: tap counter — either single-threaded (Composable state) or atomic — [DELEGATED: simpler UI-level state]
4. **Accessibility**: tap target размер ≥ 48×48dp (Material guideline) — [DELEGATED: standard a11y]

## Scope

### In Scope [UPDATED 2026-04-20 — Walking Skeleton частично переделывается]

**Domain (pure Kotlin, Walking Skeleton update):**
- `shared/feature/qualification/domain/.../dev_mode/`:
  - `model/TapProgress.kt` — `data class (count: Int, lastTapAtMillis: Long?)` — **остаётся** (Walking Skeleton)
  - `model/TapResult.kt` — sealed `TapResult { NoChange, Activated, AlreadyDev, Reset }` — **остаётся**
  - `logic/RegisterTap.kt` — pure function, signature:
    ```kotlin
    fun registerTap(
        progress: TapProgress,
        nowMillis: Long,
        currentDeveloperLevel: Int,  // was: currentEffectiveDeveloperLevel
        required: QualificationLevel = QualificationLevel.LEVEL_1,
        resetThresholdMillis: Long = 500,
        targetCount: Int = 10,
    ): TapResult
    ```
    **Остаётся**, переименован параметр с `currentEffectiveDeveloperLevel` на `currentDeveloperLevel` (overlay больше нет)
  - **УДАЛЕНО** [2026-04-20]:
    - `model/LocalDeveloperOverride.kt` — больше не нужен (revert codex fix #2)
    - `model/DeveloperLevelStats.kt` — был локальный контракт для overlay, больше не нужен
    - `logic/EffectiveDeveloperLevel.kt` — merge logic больше не нужна, читаем `stats.qualification.developer` напрямую
    - `repository/LocalDeveloperOverrideRepository.kt` — больше не нужен
  - **ОБНОВЛЁН** `use_case/ActivateDevModeUseCase.kt`:
    ```kotlin
    class ActivateDevModeUseCase(
        private val userStatsRepository: UserStatsRepository,
    ) {
        suspend operator fun invoke(
            progress: TapProgress,
            nowMillis: Long,
        ): TapResult {
            val currentDeveloper = userStatsRepository.observeStats().first().qualification.developer
            val result = registerTap(progress, nowMillis, currentDeveloper)
            if (result is TapResult.Activated) {
                userStatsRepository.setLocalDeveloperLevel(QualificationLevel.LEVEL_1.points)
            }
            return result
        }
    }
    ```
  - Move `QualificationLevel` + `QualificationLevelTest` → `shared/core/foundation/` (**см. 0-spec-qualification-levels.md**)

**Data (new infrastructure):**
- Room setup в `shared/core/persistence/` (central `AppDatabase`)
- `UserStatsEntity` + `UserStatsDao` + migration
- Обновление `UserStatsRepositoryImpl`:
  - `observeStats()` — читает из Room Flow (не напрямую с Firestore)
  - `setLocalDeveloperLevel(value: Int)` — writes `developer` поле в Room
  - Sync pipeline (см. ниже) пишет в Room целиком
- Koin `persistenceModule` (new) + update `appShellDataModule`

**Sync infrastructure (new):**
- `SyncWorker` (WorkManager `CoroutineWorker`) в `shared/core/sync/` или `platform/android-services/`:
  - `refreshProfile()` — Firestore fetch `users/{uid}` → `RawUserStats` → map to `UserStatsEntity` → write Room row
  - Schedule: periodic (default 1 день, legacy values 1/2/3/4/7/14/30)
  - Manual trigger: `DrawerFooterAction.SyncNow` → `RootComponent.onSyncNow()` → enqueue one-time WorkRequest
- `syncModule` Koin + enqueue в `AppApplication.onCreate`

**Presentation (updates):**
- Update `shared/feature/app-shell/domain/logic/Visibility.kt::isVisible`:
  ```kotlin
  fun isVisible(section: DrawerSection, stats: UserStats): Boolean =
      stats.qualification.developer >= QualificationLevel.LEVEL_1.points ||  // superqualification bypass
      section.requiredRoles.all { (role, min) -> actualLevel(role, stats) >= min }
  ```
  (Требует import `QualificationLevel` из `shared/core/foundation/`.)
- Update `visibleFooterActions` signature:
  ```kotlin
  fun visibleFooterActions(isDebugBuild: Boolean, stats: UserStats): List<DrawerFooterAction>
  ```
  DesignCatalog видим если `isDebugBuild || stats.qualification.developer >= LEVEL_1.points`. SyncNow видим если same condition (добавлено catalog-foundation).
- Update `RootComponent` interface:
  - Add `fun onSyncNow()` method
  - Add `RootEvent.SyncStarted` variant (новый sealed variant)
- Update `DefaultRootComponent`:
  - `onSyncNow()` → `workManager.enqueueUniqueWork("manual_sync", REPLACE, oneTimeSyncWorkRequest)` + emit `RootEvent.SyncStarted`
  - Inject `CatalogRepository` + `WorkManager` через Koin
  - Handle 10-tap path: add method `onActivateDevMode()` или через new `Destination` variant (design phase выбирает)
- Update `AppShellScreen`:
  - Add `SnackbarHostState` в Scaffold
  - Collect `rootComponent.events` для `RootEvent.SyncStarted` → show snackbar "Синхронизация запущена"
  - Collect event for dev mode activation → show snackbar "Режим разработчика включён"
- Update `DrawerContent.kt`: pass `userStats` к `DrawerFooter` (уже есть в props)
- Update `DrawerFooter.kt`:
  - New params: `userStats: UserStats` (+ existing `navigator`, `isDebugBuild`, `versionName`)
  - `Text("v$versionName")` обернуть в `Modifier.clickable` c tap tracking (local `mutableStateOf<TapProgress>`)
  - 10-th successful tap → `rootComponent.onActivateDevMode()` (или через Navigator.goTo — design decision)
  - `visibleFooterActions(isDebugBuild, userStats)` — новая signature

**Tests:**
- `RegisterTapTest` — 14+ scenarios (уже есть в Walking Skeleton, param rename)
- `ActivateDevModeUseCaseTest` — через `FakeUserStatsRepository` (переиспользуем `domain/fake/`)
- `VisibilityTest` — новые scenarios для superqualification (19-25 из spec)
- `DrawerFooterActionTest` — update assertion "3 variants" (включая SyncNow из catalog-foundation)
- `DrawerFooterMapperTest` — update signature calls
- `UserStatsRepositoryImplTest` — Room roundtrip, `setLocalDeveloperLevel` + overwrite on sync
- `SyncWorkerTest` — Worker testing через `TestListenableWorkerBuilder`

**Deleted from scope [2026-04-20]:**
- `FakeLocalDeveloperOverrideRepository` — больше не нужен
- `LocalDeveloperOverrideTest`, `EffectiveDeveloperLevelTest` — больше не нужны

### Explicitly Out of Scope
- UI toggle для отключения Dev Mode — "никак, сам отключится при синхронизации" [USER DECIDED]
- Progressive feedback (taps left) до 10-го — "Только на 10-м тапе" [USER DECIDED]
- Перенос legacy dev-пунктов меню (MENU_CHAT_BANNED, etc.) — "не раздувает фичу" [USER DECIDED]
- Отправка активации на сервер (analytics event) — out of scope
- Расширение для других квалификаций (admin mode, translator mode) — out of scope
- TTL локального Dev Mode (auto-disable через N дней) — "не отключается" [USER DECIDED]

## Search Criteria for Research

1. **Storage локального `UserQualifications`** — где хранится? Room entity (`ProfileEntity`?), DataStore, файл? В legacy — `legacy/app/src/main/.../database/entities/ProfileEntity.kt` содержит `developer: Int`. В новом проекте — research должен найти аналог в `shared/feature/qualification/data/` или `shared/core/persistence/`
2. **Sync pipeline** — как локальный `UserQualifications` синхронизируется с сервером? Что запускает sync? Какой механизм разрешения конфликтов (ADR-0004)? Критично: точно ли сервер **перезатирает** локальное значение, а не сливает через max()
3. **Koin DI модули для qualification** — где регистрируются repositories? `shared/feature/qualification/data/src/.../di/`?
4. **Текущее использование `isVisible` и `visibleFooterActions`** — все call sites; нужно обновить сигнатуру `visibleFooterActions`
5. **Как Snackbar host настроен в AppShellScreen** — где вешать event pipe ViewModel → snackbar
6. **UserStats как доходит до DrawerFooter** — AppShellScreen передаёт stats? Через ViewModel? (в `android/feature/app-shell/presentation/ui/AppShellScreen.kt`)
7. **Существующие tests `VisibilityTest` / `DrawerFooterMapperTest`** — `android/feature/app-shell/presentation/src/test/kotlin/.../DrawerFooterMapperTest.kt` — понять стиль тестов

### Обязательные search directions
- Найти ВСЕ вызовы `visibleFooterActions(isDebugBuild)` — нужно обновить после изменения signature
- Найти ВСЕ вызовы `isVisible(section, stats)` — убедиться что нет прямых звонков, которые сломаются при новой логике
- Найти где в DI регистрируется `UserStats` provider
- Найти где serializer/mapper `UserQualifications` Entity ↔ Domain

### Completeness check
- Grep `visibleFooterActions` — найти все call sites и тесты
- Grep `isVisible` — найти все call sites
- Grep `ProfileEntity.developer` или `qualification.developer` — найти storage path

## Primary User Journeys

1. **Happy path: первая активация**
   - Start: новый юзер открывает drawer, developer = 0 (server)
   - Trigger: 10 тапов по v$versionName с интервалом <500мс
   - State changes:
     - TapProgress: 0 → 1 → ... → 10
     - При 10: guard check passes (developer < 100) → repository.activateLocally() → локально developer = 100
   - Expected result: Snackbar "Режим разработчика включён"; drawer перерисовывается; `DrawerSection.EventsSection.ActiveEvents` и другие пункты с `DEVELOPER >= 100` или с superqualification now видимы; DesignCatalog виден в footer (если release build).
   - Decision: [USER DECIDED]

2. **Timeout reset**
   - Start: юзер тапает 3 раза, потом пауза 1 сек, потом ещё 3 тапа
   - Trigger: четвёртый тап после паузы (elapsed > 500мс)
   - State changes: TapProgress: 3 → 0 (reset) → 1
   - Expected result: dev mode НЕ активирован; счётчик начинается заново
   - Decision: [USER DECIDED] "500 мс"

3. **Already dev**
   - Start: юзер имеет server developer = 200 (настоящий разработчик)
   - Trigger: 10 тапов
   - State changes: TapProgress: 0 → 10, но на 10-м — guard check fails (current 200 >= 100)
   - Expected result: toast "Уже в режиме разработчика"; локальное developer НЕ пишется (остаётся 200); UI не меняется
   - Decision: [USER DECIDED]

4. **Automatic deactivation через sync**
   - Start: Dev Mode активирован (local developer = 100; server developer = 0)
   - Trigger: sync pipeline притягивает свежий `UserQualifications` с сервера
   - State changes: local developer = 100 → 0 (server value)
   - Expected result: UI перерисовывается; пункты меню с DEVELOPER >= 100 скрываются; DesignCatalog скрывается (в release)
   - Decision: [USER DECIDED] "сам отключится при синхронизации"

5. **Interrupted / background**
   - Start: юзер начал тапать, сделал 5 тапов
   - Trigger: Home button, app в background
   - State changes: TapProgress сбрасывается (held в memory / Composable state, lose on recomposition/background)
   - Expected result: после возврата — нужно тапать заново с нуля
   - Decision: [DELEGATED — Compose state не persists по умолчанию, это приемлемо для "secret" mode]

## Feature Domain Contract

### Terms / Entities / Value Constraints

- `TapProgress` — data class `(count: Int >= 0, lastTapAt: Instant?)`
- `TapResult` — sealed:
  - `NoChange(newProgress: TapProgress)` — tap registered, но ничего особенного
  - `Activated` — 10-й tap, guard passed, Dev Mode активирован
  - `AlreadyDev` — 10-й tap, guard failed (уже developer)
  - `Reset` — tap пришёл после timeout, счётчик сброшен на 1
- `targetCount` — default 10
- `resetThresholdMillis` — default 500
- `QualificationLevel.LEVEL_1.points` = 100 (reference)

### Business Rules / Invariants / Guards

1. `registerTap` — pure, idempotent per `(progress, now)` input
2. При `(now - progress.lastTapAt) > resetThresholdMillis` AND `progress.count > 0` → reset в count=1
3. При `progress.count + 1 == targetCount` AND current developer level < LEVEL_1.points → `Activated`
4. При `progress.count + 1 == targetCount` AND current developer level >= LEVEL_1.points → `AlreadyDev`
5. Функция `registerTap` НЕ пишет в repository — только возвращает намерение; запись инкапсулирована в use case
6. `isVisible(section, stats)` возвращает `true` если: `stats.qualification.developer >= QualificationLevel.LEVEL_1.points` OR `section.requiredRoles.all { ... }`
7. `visibleFooterActions(isDebugBuild, stats)` возвращает `DesignCatalog` если `isDebugBuild || stats.qualification.developer >= QualificationLevel.LEVEL_1.points`
8. Запись developer=100 в локальную БД — **один** тип local qualification override (для DEVELOPER только; другие поля не подвержены)

### State / Decision Rules (tap state machine — Codex fix #4 simplified)

После Activated/AlreadyDev счётчик немедленно сбрасывается на `(0, null)` — нет "terminal count=10" состояния. Новый tap начинает счёт с 1.

| Input state (progress) | `elapsed = now - progress.lastTapAt` | `currentEffectiveLevel` | Output (newProgress) | Result |
|---|---|---|---|---|
| `count=0, lastTap=null` | (N/A — first tap) | любой | `count=1, lastTap=now` | `NoChange` |
| `count=N (1≤N≤8)` | `≤ 500ms` | любой | `count=N+1, lastTap=now` | `NoChange` |
| `count=N (1≤N≤8)` | `> 500ms` | любой | `count=1, lastTap=now` | `Reset` |
| `count=9` | `≤ 500ms` | `< LEVEL_1.points` | `count=0, lastTap=null` | `Activated` |
| `count=9` | `≤ 500ms` | `≥ LEVEL_1.points` | `count=0, lastTap=null` | `AlreadyDev` |
| `count=9` | `> 500ms` | любой | `count=1, lastTap=now` | `Reset` |

**Note**: `count >= 10` состояние невозможно — `count=9` + успешный tap сразу переходит в `count=0` с result `Activated` или `AlreadyDev`. Это устраняет ambiguity из предыдущей версии.

### Error / Recovery Rules

- `DevModeRepository.activateLocally()` — suspend, может throw. Use case ловит exception → emits `Error` event (но это edge case; локальная запись редко fails)
- Если sync pipeline сбрасывает developer во время активации — race condition. Принимается: последняя запись побеждает. Side effect: юзер увидел snackbar, но dev mode "исчез". Минорный, не blocker

### Domain Test Scenarios (phase-01 source of truth)

**`registerTap` scenarios:**

1. GIVEN `TapProgress(0, null)` WHEN `registerTap(now=t0, currentEffectiveLevel=0)` THEN returns `NoChange(TapProgress(1, t0))`
2. GIVEN `TapProgress(9, t0)` WHEN `registerTap(now=t0+100ms, currentEffectiveLevel=0)` THEN returns `Activated(TapProgress(0, null))`
3. GIVEN `TapProgress(9, t0)` WHEN `registerTap(now=t0+100ms, currentEffectiveLevel=100)` THEN returns `AlreadyDev(TapProgress(0, null))`
4. GIVEN `TapProgress(9, t0)` WHEN `registerTap(now=t0+100ms, currentEffectiveLevel=200)` THEN returns `AlreadyDev(TapProgress(0, null))`
5. GIVEN `TapProgress(5, t0)` WHEN `registerTap(now=t0+501ms, currentEffectiveLevel=0)` THEN returns `Reset(TapProgress(1, t0+501ms))`
6. GIVEN `TapProgress(5, t0)` WHEN `registerTap(now=t0+500ms, currentEffectiveLevel=0)` (equal boundary) THEN returns `NoChange(TapProgress(6, t0+500ms))`
7. GIVEN `TapProgress(5, t0)` WHEN `registerTap(now=t0+499ms, currentEffectiveLevel=0)` THEN returns `NoChange(TapProgress(6, t0+499ms))`
8. GIVEN 10-tap sequence all intervals < 500ms AND currentEffectiveLevel=0 WHEN последний tap applied THEN returns `Activated(TapProgress(0, null))`
9. GIVEN post-Activated state `TapProgress(0, null)` WHEN `registerTap(now, currentEffectiveLevel=100)` (теперь юзер developer) THEN returns `NoChange(TapProgress(1, now))` (счёт рестартуется, но на 9+1=10 tap будет `AlreadyDev`)
10. GIVEN 10 tap sequence с одним интервалом >500ms AND currentEffectiveLevel=0 WHEN series processed THEN `Activated` НЕ достигается; в середине есть `Reset`

**`effectiveDeveloperLevel` scenarios:**

11. GIVEN `stats.developer=0`, `overlay.active=false` THEN `effectiveDeveloperLevel() == 0`
12. GIVEN `stats.developer=0`, `overlay.active=true` THEN `effectiveDeveloperLevel() == 100` (LEVEL_1)
13. GIVEN `stats.developer=200`, `overlay.active=false` THEN `effectiveDeveloperLevel() == 200` (server preserved)
14. GIVEN `stats.developer=200`, `overlay.active=true` THEN `effectiveDeveloperLevel() == 200` (max wins, overlay не понижает)
15. GIVEN `stats.developer=50`, `overlay.active=true` THEN `effectiveDeveloperLevel() == 100` (overlay raises above server)

**`ActivateDevModeUseCase` scenarios:**

16. GIVEN `FakeLocalDeveloperOverrideRepository.state = LocalDeveloperOverride(false, null)`, `stats.developer=0` WHEN invoked AND `registerTap` returns `Activated` THEN `overlay.activate(now)` called; state = `LocalDeveloperOverride(true, now)`
17. GIVEN `stats.developer=200`, overlay inactive WHEN invoked AND `registerTap` returns `AlreadyDev` THEN `overlay.activate(...)` НЕ вызывается; state unchanged
18. GIVEN `FakeLocalDeveloperOverrideRepository.state = LocalDeveloperOverride(true, t0)`, `stats.developer=0` WHEN invoked AND `registerTap` returns `AlreadyDev` (effective = 100 from overlay) THEN state unchanged

**`isVisible` scenarios (superqualification + normal + empty):**

19. GIVEN section requires `{TESTER=100, MODERATOR=100, ADMIN=100, DEVELOPER=100}` AND `stats.developer=100` (local overlay) AND tester=moderator=admin=0 WHEN isVisible THEN `true` (superqualification)
20. GIVEN section requires `{TESTER=100}` AND stats.tester=100 AND developer=0 WHEN isVisible THEN `true` (normal path)
21. GIVEN section requires `{TESTER=100}` AND stats.tester=0 AND developer=0 WHEN isVisible THEN `false`
22. GIVEN section requires `{TESTER=100}` AND stats.tester=0 AND developer=200 (real server dev) WHEN isVisible THEN `true` (superqualification bypass)
23. GIVEN section requires `{TESTER=100}` AND stats.tester=0 AND developer=99 (sub-threshold) WHEN isVisible THEN `false` (LEVEL_1 порог не достигнут)
24. **(Codex fix #4)** GIVEN section requires `{}` (empty requiredRoles) AND stats.developer=0 WHEN isVisible THEN `true` (empty all() = true, always visible)
25. GIVEN section requires `{}` (empty) AND stats.developer=100 WHEN isVisible THEN `true` (trivially; also superqualification bypass active, но результат same)

**`visibleFooterActions` scenarios (signature with `overlay`):**

26. GIVEN `isDebugBuild=false, stats.developer=0, overlay.active=false` WHEN `visibleFooterActions` THEN `[SyncNow? hidden, DesignCatalog hidden, About]` → list = `[About]` (SyncNow contributed catalog-foundation spec; DesignCatalog hidden)
27. GIVEN `isDebugBuild=false, stats.developer=0, overlay.active=true` WHEN `visibleFooterActions` THEN list includes `DesignCatalog` (effective level = 100)
28. GIVEN `isDebugBuild=true, stats.developer=0, overlay.active=false` WHEN `visibleFooterActions` THEN list includes `DesignCatalog` (debug bypass)
29. GIVEN `isDebugBuild=true, stats.developer=500, overlay.active=true` WHEN `visibleFooterActions` THEN list includes `DesignCatalog`

**`canRenderDesignCatalog` scenarios (Codex fix #3):**

30. GIVEN `isDebugBuild=false, stats.developer=0, overlay.active=false` WHEN `canRenderDesignCatalog` THEN `false` (рендер показывает "Недоступно")
31. GIVEN `isDebugBuild=false, stats.developer=0, overlay.active=true` WHEN `canRenderDesignCatalog` THEN `true` (рендер показывает Design Catalog UI)
32. GIVEN `isDebugBuild=true` (любой stats/overlay) WHEN `canRenderDesignCatalog` THEN `true`

## State Matrix

Dev Mode главная state matrix — решение `TapResult`:

| `progress.count` | `current developer level` | `(now - lastTap) vs 500ms` | Решение `registerTap` |
|---|---|---|---|
| 0 | любой | (первый tap — нет prev) | NoChange(count=1) |
| 1..8 | любой | ≤ 500ms | NoChange(count+1) |
| 1..8 | любой | > 500ms | Reset(count=1) |
| 9 | < LEVEL_1.points | ≤ 500ms | **Activated** |
| 9 | ≥ LEVEL_1.points | ≤ 500ms | **AlreadyDev** |
| 9 | любой | > 500ms | Reset(count=1) |

Visibility main matrix:

| Section requires | developer level | tester/other levels | Результат `isVisible` |
|---|---|---|---|
| `{D=100, T=100, M=100, A=100}` | ≥ LEVEL_1 | любые | **true** (superqualification) |
| `{D=100, T=100, M=100, A=100}` | < LEVEL_1 | все ≥ 100 | **true** (normal path all-satisfied) |
| `{D=100, T=100, M=100, A=100}` | < LEVEL_1 | не все ≥ 100 | **false** |
| `{T=100}` (без D) | ≥ LEVEL_1 | tester < 100 | **true** (superqualification) |
| `{T=100}` (без D) | < LEVEL_1 | tester ≥ 100 | **true** (normal) |
| `{T=100}` (без D) | < LEVEL_1 | tester < 100 | **false** |
| `{}` (empty) | любой | любой | **true** (always visible) |

## Footer Contract (Combined — Codex fix #7)

Эта таблица — **единый source of truth** для `visibleFooterActions` результата, включая вклад `SyncNow` от catalog-foundation (если эта фича уже implemented). dev-mode spec — **owner** `DrawerFooterAction` closed-set и порядка; catalog-foundation добавляет `SyncNow` через extension этого contract.

### Closed set DrawerFooterAction

Final list of actions (order = declaration order, всегда этот порядок в UI):

```kotlin
sealed interface DrawerFooterAction {
    data object DesignCatalog : DrawerFooterAction  // dev tool
    data object SyncNow : DrawerFooterAction        // dev tool (added by catalog-foundation)
    data object About : DrawerFooterAction          // always visible
}
```

### Visibility rules

| Action | Visible when |
|---|---|
| `DesignCatalog` | `isDebugBuild || effectiveDeveloperLevel(stats, overlay) >= LEVEL_1.points` |
| `SyncNow` | `isDebugBuild || effectiveDeveloperLevel(stats, overlay) >= LEVEL_1.points` (same condition — developer tools group) |
| `About` | always (no condition) |

### Output order

`visibleFooterActions(isDebugBuild, stats, overlay)` возвращает подмножество в **фиксированном** порядке: `[DesignCatalog?, SyncNow?, About]` (сохраняя declaration order, отфильтровав скрытые).

### Full matrix (4 состояния build × dev)

| `isDebugBuild` | `effectiveDeveloperLevel` | Output |
|---|---|---|
| `true` | любой | `[DesignCatalog, SyncNow, About]` |
| `false` | `>= LEVEL_1.points` (100+) | `[DesignCatalog, SyncNow, About]` |
| `false` | `< LEVEL_1.points` (0..99) | `[About]` |
| `true` (impossible — but для полноты) | `< LEVEL_1.points` | `[DesignCatalog, SyncNow, About]` (debug overrides) |

**Note**: `SyncNow` добавляется в `DrawerFooterAction` sealed interface в catalog-foundation spec. До реализации catalog-foundation — `SyncNow` нет в sealed set, output будет `[DesignCatalog?, About]`. После catalog-foundation — матрица выше актуальна полностью.

### Handler paths

| Action | Handler (ViewModel / Composable) |
|---|---|
| `DesignCatalog` | `navigator.goTo(Destination.OpenDesignCatalog)` → активирует `LocalConfig.DesignCatalogRoot` |
| `SyncNow` | `navigator.trigger(Destination.SyncNow)` → enqueue общий WorkManager sync job; показать snackbar |
| `About` | открыть local `AlertDialog` (as is) |

### Render rule для DesignCatalog screen (Codex fix #3)

`AppShellScreen.kt:255` сейчас показывает "Недоступно" для `LocalConfig.DesignCatalogRoot` в release build. Обновление:

```kotlin
// Псевдо-код — реальная реализация в implement phase
fun canRenderDesignCatalog(
    isDebugBuild: Boolean,
    stats: UserStats,
    overlay: LocalDeveloperOverride,
): Boolean =
    isDebugBuild || effectiveDeveloperLevel(stats, overlay) >= QualificationLevel.LEVEL_1.points
```

Если `false` → показать "Недоступно" (existing). Если `true` → рендерить DesignCatalog UI. Это условие **должно совпадать** с `visibleFooterActions` visibility для DesignCatalog — иначе footer показывает clickable item, но клик ведёт к "Недоступно".

## Delegated Decisions Summary

| # | Область | Решение | Обоснование | Risk |
|---|---|---|---|---|
| 1 | Pure domain core (RegisterTap) | Pure function with `Instant`/`Long`-based reset | Testability (no System.currentTimeMillis in domain) | low |
| 2 | Repository interface vs direct DAO | Repository pattern | Follow ADR-0006 + clean-architecture | low |
| 3 | Reset on 10-й tap | При `count=10` — при любом next tap count=0/1 | Avoids stuck state; users can re-activate via re-tap | low |
| 4 | Compose state for counter (not DataStore) | In-memory Composable state | Не важно сохранять счётчик между сессиями; activation persists в БД | low |
| 5 | No UI toggle to disable | Только через sync reset | User intent "не отключается" + simpler MVP | medium (user might be confused about how to disable; mitigated by toast на re-tap) |
| 6 | `visibleFooterActions(isDebugBuild, stats)` signature change | Add stats param | Required for superqualification logic; existing callers need update | low (caught by compiler) |
| 7 | Snackbar via AppShellScreen SnackbarHostState | Host-level pattern | Consistent с существующей M3 Compose архитектурой | low |
| 8 | Toast "Уже в режиме разработчика" на re-tap — механизм | Toast или Snackbar — TBD research/design | UX detail | low |
| 9 | Race condition (sync during tap activation) | Accept; last-write-wins | Edge case; minor UX impact | low |

## Acceptance Criteria

Primary journeys:

1. [ ] GIVEN release build, new user (developer=0 server, overlay.active=false) WHEN open drawer and tap 10 times on v$versionName with <500ms intervals THEN Snackbar "Режим разработчика включён" shown AND `overlay.active = true, activatedAt != null` AND `effectiveDeveloperLevel = 100` AND все пункты DrawerSection.EventsSection.ActiveEvents + DesignCatalog (footer) + SyncNow (footer, если catalog-foundation реализован) видны
2. [ ] GIVEN 5 taps done WHEN pause 600ms AND tap THEN счётчик сбрасывается (6-й tap = count=1); 10-th original tap НЕ достигается (требует ещё 9 тапов после reset)
3. [ ] GIVEN user has server developer=200 (real dev), overlay.active=false WHEN 10 taps done on version THEN toast "Уже в режиме разработчика" AND overlay.active остаётся false (НЕ пишется) AND UI не меняется
4. [ ] GIVEN dev mode активирован локально (overlay.active=true), server developer=0 WHEN sync pipeline вызывает `refreshProfile()` с `Result.success` THEN `overlay.deactivate()` вызывается → overlay.active=false AND `effectiveDeveloperLevel` возвращается к server value (0) AND UI re-renders (DesignCatalog скрывается в release; SyncNow скрывается; пункты меню с DEVELOPER=100 скрываются)
5. [ ] GIVEN debug build (любой stats/overlay) WHEN open drawer THEN DesignCatalog и SyncNow видны в footer
6. [ ] GIVEN release build, developer=0, overlay.active=false WHEN open drawer THEN DesignCatalog и SyncNow скрыты в footer; output = `[About]`
7. [ ] GIVEN section с `requiredRoles = {TESTER=100, MODERATOR=100, ADMIN=100, DEVELOPER=100}` AND overlay.active=true (effective developer=100) AND все остальные=0 WHEN isVisible() THEN `true` (superqualification bypass)
8. [ ] GIVEN section с `requiredRoles = {TESTER=100}` AND stats.tester=0 AND effective developer=0 WHEN isVisible() THEN `false`
9. [ ] GIVEN section с `requiredRoles = {}` (empty map) WHEN isVisible(anything) THEN `true` (always visible)
10. [ ] GIVEN pure `registerTap` + `effectiveDeveloperLevel` + `ActivateDevModeUseCase` + `isVisible` Domain Test Scenarios (all 32) WHEN run as `@Test` THEN все зелёные
11. [ ] GIVEN tap on v$versionName WHEN inspect Composable THEN tap target size ≥ 48dp (accessibility)
12. [ ] GIVEN `visibleFooterActions(isDebugBuild=false, stats.developer=0, overlay.active=true)` WHEN invoked THEN returns list в порядке declaration: `[DesignCatalog, (SyncNow если catalog-foundation реализован), About]`
13. [ ] **(Codex fix #3)** GIVEN release build, overlay.active=true WHEN клик на DesignCatalog в footer → открывается `LocalConfig.DesignCatalogRoot` → рендерится DesignCatalog UI (НЕ "Недоступно")
14. [ ] GIVEN release build, overlay.active=false, developer=0 WHEN навигация к `LocalConfig.DesignCatalogRoot` (deep link или прямой вызов) THEN показывается "Недоступно" (existing)
15. [ ] GIVEN sync pipeline contract WHEN inspect THEN `overlay.deactivate()` вызывается ТОЛЬКО после `refreshProfile()` с `Result.success` (не при network error, не до запроса)
16. [ ] GIVEN `LocalDeveloperOverride` persistence entity WHEN inspect THEN separate от `UserQualifications` / profile persistence (отдельная таблица Room или DataStore ключ); sync pipeline не пишет в это storage

## Invariant Check (from docs/invariants.md + new)

| Invariant | Impact | Decision |
|-----------|--------|----------|
| 1. Domain layer purity | Все pure domain files без Android/SDK/DI | preserve |
| 2. Activity/Fragment calls only ViewModel | DrawerFooter — Composable, tap handler вызывает ViewModel action | preserve |
| 3. No bidirectional coupling | `app-shell` depends on `qualification` (one-way) | preserve |
| 5. DI exclusive binding | DevModeRepository Koin-provided через factory, one approach | preserve |
| 6. Walking Skeleton ownership | Создаём domain skeleton для tap logic + use case + fake | preserve + generate in Phase 3.8 |
| NEW. Superqualification DEVELOPER | Добавляется правило в isVisible | add |
| NEW. Local-only qualification override | Запись developer=100 в локальную БД только; sync не отправляет | add |

## Constraints (from PROJECT_STRUCTURE.md + ADRs)

- Koin DI (ADR-0009) для `DevModeRepository` + `ActivateDevModeUseCase`
- Compose + Material3 (ADR-0010) — Snackbar через M3 SnackbarHost; tap target ≥ 48dp
- Single-Activity + Decompose (ADR-0008) — не затрагиваем navigation, только UI
- Sync pipeline (ADR-0004) — убеждаемся, что локальный override НЕ трекается для push на сервер
- Room или DataStore для UserQualifications — research определит, использовать существующий persistence
