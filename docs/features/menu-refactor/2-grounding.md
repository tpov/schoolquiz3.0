---
date: 2026-04-20
researcher: Claude
commit: 7c52c200
branch: kmp-skillify-4.0
---

# Grounding: Menu Refactor

Этот документ — gate перед переходом к design. Research отвечает "что есть в коде". Grounding отвечает "что сломается, если мы это изменим, и что реально возможно".

## User Decisions Resolved (2026-04-20)

Все open questions закрыты пользователем. Ниже секции `Fix Shape` для каждой Problem обновлены с выбранным вариантом.

| # | Decision |
|---|---|
| 1 | `QualificationLevel` → `shared/core/foundation/` (move) |
| 2 | Revert codex fix #2 — прямая запись `developer=100` в local Room, нет overlay entity |
| 3 | `RootComponent.onSyncNow()` + `RootEvent.SyncStarted` |
| 4 | Quest остаётся в catalog/domain как TEMPORARY |
| 5 | Создать SyncWorker + `refreshProfile()` infrastructure |
| 6 | Room (central AppDatabase) — dev mode persists между рестартами |
| 7 | Coil 3.4.0 |
| 8 | Kotlin 1.9.22 verified |
| 9 | Sync periodicity 1/2/3/4/7/14/30 дней, default 1 день |
| 10 | HandleBackUseCase cleanup отложен |

## Independent Verification Protocol

Для каждого utверждения из `1-research.md` lead (Claude) сам прочитал соответствующий файл через `Read` tool и подтвердил реальное содержимое кода. Ниже таблица — отдельные VERIFIED/CONTRADICTS маркеры по key claims.

### Verified claims

- [VERIFIED: прочитал `shared/feature/app-shell/domain/src/commonMain/kotlin/.../model/DrawerSection.kt:38`, подтверждаю: `data object MyCourses : LocalSection` с `requiredRoles = emptyMap()`]
- [VERIFIED: прочитал `shared/feature/app-shell/domain/src/commonMain/kotlin/.../model/DrawerSection.kt:100-103`, подтверждаю: 4 magic numbers `100` — `Role.TESTER to 100, Role.MODERATOR to 100, Role.ADMIN to 100, Role.DEVELOPER to 100` в `EventsSection.ActiveEvents.requiredRoles`]
- [VERIFIED: прочитал `shared/feature/app-shell/domain/src/commonMain/kotlin/.../logic/Visibility.kt:50-53`, подтверждаю: `isVisible` — AND-only, нет OR-bypass для DEVELOPER]
- [VERIFIED: прочитал `Visibility.kt:67-89`, подтверждаю: `visibleSections(Tab.LOCAL)` возвращает `[MyQuests, MyCourses, Settings]` — MyCourses на позиции 2, не 1]
- [VERIFIED: прочитал `Visibility.kt:106-118`, подтверждаю: `rootOf()` содержит `DrawerSection.LocalSection.MyCourses -> LocalConfig.MyCoursesRoot` на строке 108]
- [VERIFIED: прочитал `Visibility.kt:142-144`, подтверждаю: `visibleFooterActions(isDebugBuild: Boolean): List<DrawerFooterAction>` — 1 параметр, нет stats/overlay]
- [VERIFIED: прочитал `shared/feature/app-shell/domain/src/commonMain/kotlin/.../model/DrawerFooterAction.kt:14-20`, подтверждаю: sealed interface с 2 членами — `DesignCatalog` и `About`. SyncNow отсутствует]
- [VERIFIED: прочитал `android/feature/app-shell/presentation/src/main/kotlin/.../ui/drawer/DrawerFooter.kt:32-78`, подтверждаю: принимает `navigator, isDebugBuild, versionName` — нет UserStats; `Text("v$versionName")` на строке 59 без `Modifier.clickable` или tap tracking]
- [VERIFIED: прочитал `android/feature/app-shell/presentation/src/main/kotlin/.../ui/AppShellScreen.kt:129-167`, подтверждаю: `Scaffold(topBar, bottomBar)` без `snackbarHost` параметра]
- [VERIFIED: прочитал `AppShellScreen.kt:248-266`, подтверждаю: `LocalTabContent` проверяет `screen.config == LocalConfig.DesignCatalogRoot && isDebugBuild` — не учитывает overlay]
- [VERIFIED: прочитал `shared/feature/qualification/domain/src/commonMain/kotlin/.../model/QualificationLevel.kt:12-24`, подтверждаю: Walking Skeleton полный, `enum class QualificationLevel(val points: Int)` с LEVEL_1/2/3 + `isReachedBy` extension на строке 24]
- [VERIFIED: прочитал `shared/core/catalog/domain/src/commonMain/kotlin/.../model/Quest.kt:1-39`, подтверждаю: KDoc явно помечает класс как TEMPORARY — "Final Quest domain model lives in shared/feature/quiz/domain/"]
- [VERIFIED: прочитал `settings.gradle.kts:26`, подтверждаю: `include(":shared:core:catalog:domain")` — включён. `:shared:core:catalog:data` не включён (проверил весь блок 25-58)]
- [VERIFIED: прочитал `shared/feature/app-shell/domain/build.gradle.kts:1-19`, подтверждаю: зависимости — только `kotlinx.coroutines.core` + `kotlinx.coroutines.test`. НЕТ `implementation(project(":shared:feature:qualification:domain"))`]
- [VERIFIED: прочитал `shared/feature/qualification/domain/build.gradle.kts:1-8`, подтверждаю: файл не содержит никакого `kotlin { sourceSets { ... } }` блока — нет deps на coroutines/datetime вообще]

### Notes

Отдельно: `DrawerSection.InternetSection.Catalog` (line 62 в DrawerSection.kt) — это existing nav section для browsing каталогов на INTERNET tab, **не путать** с `shared/core/catalog/domain/model/Catalog` из catalog-foundation sub-spec. Обе существуют и означают разное. При design/implement нужно быть осторожным с import statements — `import .../app_shell/domain/model/DrawerSection.InternetSection.Catalog` vs `import .../core/catalog/domain/model/Catalog`. Предупреждение не было в research-отчётах.

## Problem 1: qualification-levels — rename magic numbers in DrawerSection

### Symptom
Spec требует заменить магические числа `100` в `DrawerSection.EventsSection.ActiveEvents.requiredRoles` на `QualificationLevel.LEVEL_1.points`. Простой refactor, но создаёт прямой cross-feature import между `app-shell:domain` и `qualification:domain`, запрещённый `.claude/rules/clean-architecture.md`.

### Repro
1. Открыть `shared/feature/app-shell/domain/src/commonMain/kotlin/.../model/DrawerSection.kt`.
2. Увидеть строки 100-103 с литералами `100`.
3. Добавить `import com.tpov.schoolquiz.shared.feature.qualification.domain.model.QualificationLevel` и заменить литералы.
4. Попытаться скомпилировать — **fails** (нет зависимости в `build.gradle.kts`).
5. Добавить `implementation(project(":shared:feature:qualification:domain"))` в `shared/feature/app-shell/domain/build.gradle.kts` — компилируется, но нарушает clean-architecture rule.

### Entry Points (EXHAUSTIVE)
- `DrawerSection.EventsSection.ActiveEvents.requiredRoles` — computed property, line 98 definition, line 100-103 values.
- Consumers `DrawerSection.*.requiredRoles`:
  - `Visibility.isVisible()` — `Visibility.kt:50-52` (reads Map entries)
  - `VisibilityTest.kt:387-390` — test assertion против hardcoded `100`
  - `Labels.kt:60, 76` — UI rendering (не читает `requiredRoles`, только displayName)
  - `AppShellTransitions.kt:47` — `rootEventsStackForSection(section)` (не читает `requiredRoles`)

### Code Owners
- `shared/feature/app-shell/domain/model/DrawerSection.kt` — app-shell domain (owner: domain-designer / backend-dev)
- `shared/feature/qualification/domain/model/QualificationLevel.kt` — qualification domain (owner: domain-designer — skeleton already generated)
- `shared/feature/app-shell/domain/build.gradle.kts` — scaffold file (owner: backend-dev per invariant #7)

### Flow Trace
```
DrawerSection.kt:98-104 (declaration)
  → Visibility.kt:50-52 (isVisible reads requiredRoles as Map<Role, Int>)
  → DefaultRootComponent.kt:? (UI uses via ObserveAppShellStateUseCase → AppShellState.visibleSections)
  → AppShellScreen.kt → DrawerSectionList.kt (renders filtered list)
```

### Backend / Contract Check
- REST API: N/A (no server contract, pure UI visibility).
- WebSocket: N/A.
- Push payload: N/A.
- Нет backend change требуемого.

### Constraints
- Lifecycle: pure domain function, нет lifecycle привязки.
- In-memory state: rebuilds при каждом emit `UserStats`.
- DB/Storage: N/A.
- Offline/Online: не меняется.

### Code Path Divergence
Только один путь чтения `requiredRoles` — через `isVisible`. Divergence rivisible не ожидается.

### Fix Shape (выбрано: Option A — USER DECISION 2026-04-20)

**Option A: Перенести `QualificationLevel` в `shared/core/foundation`** ✅ ВЫБРАНО

- Переместить файл `QualificationLevel.kt` из `shared/feature/qualification/domain/src/commonMain/kotlin/.../model/QualificationLevel.kt` в `shared/core/foundation/src/commonMain/kotlin/.../QualificationLevel.kt`.
- Переместить `QualificationLevelTest.kt` соответственно.
- Обновить package declaration: `package com.tpov.schoolquiz.shared.core.foundation`.
- Добавить `implementation(project(":shared:core:foundation"))` в:
  - `shared/feature/app-shell/domain/build.gradle.kts` (для `DrawerSection.kt`)
  - `shared/feature/qualification/domain/build.gradle.kts` (для `RegisterTap.kt` default param)
- `app-shell:domain` не получает прямой зависимости на feature-module. `core/foundation` — shared infrastructure, разрешён per `.claude/rules/clean-architecture.md`.
- Walking Skeleton: `QualificationLevel.kt` + test перемещаются. Остальной `qualification:domain` (`dev_mode/` package — `TapProgress`, `TapResult`, `RegisterTap`, `ActivateDevModeUseCase`) **частично переделывается** (см. Problem 2).
- Import в `DrawerSection.kt`: `import com.tpov.schoolquiz.shared.core.foundation.QualificationLevel`.

**Options B/C — rejected** (documentation ниже для audit).

Option B (hardcode) отклонён — нарушает spec AC #4.
Option C (ADR для cross-feature) отклонён — создавал бы прецедент размывания clean-architecture.

### Validation
- Ручной сценарий: после replacement — `./gradlew :shared:feature:app-shell:domain:jvmTest` + `:shared:feature:qualification:domain:jvmTest` зелёные.
- Compilation check: все 4 строки 100-103 использовать `QualificationLevel.LEVEL_1.points` (если Option A или C).
- Grep проверка: `grep -rn "to 100" shared/feature/app-shell/domain/` → 0 matches (в production code).
- Critical: `VisibilityTest.kt:387-390` assertion `assertEquals(100, roles[Role.TESTER])` — runtime value равен 100 в обоих options, тесты пройдут без изменений.

## Problem 2: dev-mode — 10-tap registration в DrawerFooter

### Symptom
Spec требует добавить tap tracking на `Text("v$versionName")` в `DrawerFooter.kt:59`. Сейчас этот `Text` — чистый display, без click handler. Также нужен snackbar feedback, но `AppShellScreen.Scaffold` не имеет `snackbarHost` слота.

### Repro
1. Запустить app в release build, developer=0.
2. Открыть drawer, увидеть `v$versionName` внизу.
3. Тапнуть 10 раз — **ничего не происходит** (Text не clickable).
4. Нужно добавить `Modifier.pointerInput` / `Modifier.clickable` + ViewModel-style handler.

### Entry Points (EXHAUSTIVE)
- `DrawerFooter.kt:58-63` — `Text("v$versionName")` без event handler.
- Caller: `DrawerContent.kt:45` → `DrawerFooter(navigator, isDebugBuild, versionName)`.
- Caller цепочка: `AppShellScreen.kt:117 → DrawerContent → DrawerFooter`. `AppShellScreen` получает `rootComponent` с `navigator` и `userStats` из state (state.userStats на строке 83).
- Для feedback: нужен `SnackbarHostState` — **нет в коде**, добавляется впервые.

### Code Owners
- `DrawerFooter.kt` — presentation (owner: frontend-dev)
- `DrawerContent.kt` — presentation (owner: frontend-dev)
- `AppShellScreen.kt` — presentation (owner: frontend-dev)
- `DefaultRootComponent.kt` — presentation (owner: frontend-dev) — если выбран event pipe через компонент
- `shared/feature/qualification/data/` — новый `LocalDeveloperOverrideRepositoryImpl` (owner: backend-dev)

### Flow Trace
```
User tap on Text("v$versionName") (DrawerFooter.kt:59)
  → tap handler (NEW — Composable local state or pass-through)
  → ActivateDevModeUseCase.invoke(progress, nowMillis) (qualification:domain — Walking Skeleton ready)
  → if Activated: overlayRepo.activate(now) (LocalDeveloperOverrideRepositoryImpl — NEW)
  → UI update through:
     (a) AppShellState gets new field effectiveDeveloperLevel (rec. Option C), OR
     (b) DefaultRootComponent gets new StateFlow<LocalDeveloperOverride>
  → Snackbar "Режим разработчика включён" via SnackbarHostState (NEW)
```

### Backend / Contract Check
- REST API: не используется.
- WebSocket: не используется.
- Push payload: не используется.
- `LocalDeveloperOverride` — **client-only state**, никуда не отправляется.
- Нет backend change требуемого.

### Constraints
- Lifecycle: `TapProgress` живёт в Composable state (in-memory) — теряется при recomposition / background. Это приемлемо per spec Journey 5. `LocalDeveloperOverride` — persisted в DataStore/Room.
- In-memory state: `TapProgress(count, lastTapAtMillis)` — сбрасывается при process death (ok).
- DB/Storage: `LocalDeveloperOverride` требует DataStore или Room — **оба отсутствуют** в new-stack.
- Offline/Online: активация работает offline (client-only).

### Code Path Divergence
Нет — только один path для 10-tap activation.

### Fix Shape (USER DECISION 2026-04-20 — revert codex fix #2, simple model)

**Ключевое изменение**: Walking Skeleton dev_mode package **частично переделывается** — удаляются overlay-файлы, добавляется local Room cache для UserStats целиком.

Phase-01 (integration + Room infrastructure):

**1. Backend-dev — Walking Skeleton cleanup (qualification:domain):**
   - **Удалить**: `model/LocalDeveloperOverride.kt`, `model/DeveloperLevelStats.kt`, `logic/EffectiveDeveloperLevel.kt`, `repository/LocalDeveloperOverrideRepository.kt` + их тесты (`LocalDeveloperOverrideTest`, `EffectiveDeveloperLevelTest`).
   - **Удалить** `FakeLocalDeveloperOverrideRepository`.
   - **Оставить и переименовать параметр** в `logic/RegisterTap.kt`: `currentEffectiveDeveloperLevel` → `currentDeveloperLevel`.
   - **Обновить** `use_case/ActivateDevModeUseCase.kt`: принимать `UserStatsRepository` вместо `overlayRepo` + `readDeveloperLevel`. При `Activated` вызывать `userStatsRepository.setLocalDeveloperLevel(100)`.
   - **Обновить** тесты (`ActivateDevModeUseCaseTest`) — через `FakeUserStatsRepository`.
   - Добавить `implementation(libs.kotlinx.coroutines.core)` в `shared/feature/qualification/domain/build.gradle.kts`.
   - Добавить `implementation(project(":shared:core:foundation"))` для import `QualificationLevel`.

**2. Backend-dev — Room infrastructure (new):**
   - Создать `:shared:core:persistence:data` sub-module (или добавить Room в `shared/core/persistence/`) с `AppDatabase`.
   - Подключить ksp plugin.
   - `UserStatsEntity` (Room `@Entity`) — все поля `UserStats` + 6 qualification levels.
   - `UserStatsDao` — CRUD + `observe()`.
   - Room migration (первая версия).
   - Koin `persistenceModule` с `single<AppDatabase> { }` + `single<UserStatsDao>`.

**3. Backend-dev — Sync infrastructure (new):**
   - `SyncWorker` (WorkManager `CoroutineWorker`) — `refreshProfile()`: Firestore `users/{uid}` get → mapper → Room write.
   - Periodic work: `PeriodicWorkRequest` (24h default, legacy user-configurable 1/2/3/4/7/14/30 дней — settings в follow-up).
   - Manual work: `OneTimeWorkRequest` через `enqueueUniqueWork("manual_sync", REPLACE, ...)`.
   - Koin `syncModule` + `WorkManager` provider.
   - Schedule periodic в `AppApplication.onCreate`.

**4. Backend-dev — app-shell domain updates:**
   - Import `QualificationLevel` из `shared/core/foundation` в `Visibility.kt` и `DrawerSection.kt`.
   - Update `isVisible(section, stats)` — добавить superqualification check: `stats.qualification.developer >= QualificationLevel.LEVEL_1.points` as OR-bypass.
   - Change `visibleFooterActions(isDebugBuild)` → `visibleFooterActions(isDebugBuild, stats: UserStats)`. `DesignCatalog` видим если `isDebugBuild || stats.qualification.developer >= LEVEL_1.points`. `SyncNow` (от catalog-foundation) — same condition.
   - Update `AppShellState` — не требует новых полей (всё в существующем `userStats`).

**5. Backend-dev — UserStatsRepository update:**
   - `shared/feature/app-shell/data/UserStatsRepositoryImpl.kt`:
     - `observeStats()`: читать из Room Dao (Flow), не напрямую с Firestore.
     - `refreshProfile()`: Firestore fetch → Room write (вызывается из SyncWorker).
     - Add `setLocalDeveloperLevel(value: Int)`: UPDATE `developer_level` column where uid=current.
   - `FirebaseUserStatsDataSource` продолжает existing snapshot listener — но вместо direct emit он теперь пишет в Room (через sync pipeline).

**6. Frontend-dev:**
   - `AppShellScreen.kt`:
     - Add `val snackbarHostState = remember { SnackbarHostState() }` + `Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) })`.
     - `LaunchedEffect` для collect `rootComponent.events` → on `RootEvent.SyncStarted` → `snackbarHostState.showSnackbar("Синхронизация запущена")`.
     - Line 255: update DesignCatalogRoot condition: `isDebugBuild || state.userStats.qualification.developer >= QualificationLevel.LEVEL_1.points`.
   - `DrawerContent.kt`: pass `userStats` в `DrawerFooter` (уже есть в scope, нужно просто передать).
   - `DrawerFooter.kt`:
     - New params: `userStats: UserStats, onActivateDevMode: () -> Unit`.
     - `visibleFooterActions(isDebugBuild, userStats)` — new signature.
     - `Text("v$versionName")` обернуть в `Modifier.clickable` — local `tapProgress: MutableState<TapProgress>`. Click → `registerTap(...)` + если `Activated` → `onActivateDevMode()` → snackbar.
     - `when (action)` добавить branch `SyncNow -> navigator.goTo(Destination.SyncNow)` (или через callback — design phase уточнит).
   - `DefaultRootComponent.kt`:
     - Add `fun onSyncNow()` — enqueue manual sync work + emit `RootEvent.SyncStarted`.
     - Add `fun onActivateDevMode()` — вызов `ActivateDevModeUseCase`. При result=`Activated` emit `RootEvent.DevModeActivated` (или другое название) для snackbar.

**7. Firebase-dev:**
   - Seed Firestore `catalogs` collection (admin through Firebase Console) — part of catalog-foundation.
   - `firestore.rules` — add `catalogs/{catalogId}` block.

**8. Test-dev:**
   - `RegisterTapTest` (existing, update param rename)
   - `ActivateDevModeUseCaseTest` (rewrite — через FakeUserStatsRepository)
   - `UserStatsRepositoryImplTest` (new — Room roundtrip, setLocalDeveloperLevel, refresh overwrites)
   - `VisibilityTest` (update — superqualification scenarios 19-25)
   - `DrawerFooterActionTest` (update — expect 3 variants after SyncNow added)
   - `DrawerFooterMapperTest` (update — new signature)
   - `SyncWorkerTest` (new — TestListenableWorkerBuilder)
   - **Delete**: `LocalDeveloperOverrideTest.kt`, `EffectiveDeveloperLevelTest.kt`, `FakeLocalDeveloperOverrideRepository.kt`.

**Follow-up (post-phase-01):**
- Settings screen для sync frequency (1/2/3/4/7/14/30 дней).
- `HandleBackUseCase` cleanup (dead code).
- Ranking/stars в CatalogGrid (spec out-of-scope).

### Validation
- Ручной: release build, 10 тапов на `v$versionName` → snackbar "Режим разработчика включён" → все пункты drawer с requiredRoles видны → SyncNow и DesignCatalog в footer видны.
- Restart app → overlay persists → dev mode остаётся активным (для DataStore/Room).
- Тесты: `RegisterTapTest`, `ActivateDevModeUseCaseTest`, `EffectiveDeveloperLevelTest` — уже все зелёные (Walking Skeleton). Integration: `VisibilityTest` расширенный, new `DevModeIntegrationTest`.

## Problem 3: home-quests — rename MyCourses → HomeQuests + reorder

### Symptom
Spec требует переименовать `DrawerSection.LocalSection.MyCourses` → `HomeQuests` + `LocalConfig.MyCoursesRoot` → `HomeQuestsRoot` + обновить displayName/icon + поставить HomeQuests первым в `visibleSections(Tab.LOCAL)`. Pure rename-refactor + reorder.

### Repro
N/A (не баг, refactor task).

### Entry Points (EXHAUSTIVE)
Production — 5 files / 8 lines (see research-home-quests full checklist). Tests — 6 files / 26 lines. Exhaustive list — в `1-research.md` секция `3. Home-quests sub-spec`.

### Code Owners
- `shared/feature/app-shell/domain/` — domain (owner: backend-dev, domain-designer если изменение rules, но здесь просто rename)
- `android/feature/app-shell/presentation/` — UI (owner: frontend-dev)
- Tests — owner: test-dev

### Flow Trace
Rename + reorder — локальные изменения. Дерево вызовов не меняется, меняются symbol names и declaration order.

### Backend / Contract Check
- REST API: N/A.
- WebSocket: N/A.
- Persistence: нет persisted serialized `MyCourses` string (подтверждено grep — нет `my_courses` в XML resources, AndroidManifest, Kotlin).
- Нет backend changes.

### Constraints
- Lifecycle: нет.
- In-memory state: нет (enum/sealed objects — compile-time).
- DB/Storage: нет.
- Offline/Online: не меняется.

### Code Path Divergence
Нет — single rename + reorder.

### Fix Shape (минимально реализуемое решение)
**Client-only fix** — pure Kotlin rename.

Шаги:
1. Backend-dev: rename 8 production lines в 5 files (atomic — все за один commit).
2. Test-dev: update 26 test lines в 6 files.
3. Frontend-dev: `Labels.kt:68` изменить `Icons.Default.Book` → `Icons.Default.Home`. Import `Icons.Default.Home` на line 8 уже есть.
4. Hygiene: обновить комментарии в `DrawerSection.kt:32-45` — метки `Row 1/2/3` после reorder (home-quests будет Row 1, MyQuests → Row 2).
5. `DefaultRootComponentTest.kt:402` — commented line обновить для consistency.
6. Compile check: `./gradlew assembleDebug` + `./gradlew test` должны быть зелёные.
7. Grep: `grep -rn "MyCourses\|MyCoursesRoot\|Мои курсы" shared/ android/` должно вернуть 0 matches (вне docs/features/).

### Validation
- Ручной: open drawer на LOCAL tab → видно `[Домашние квесты, Мои квесты, Настройки]` (в этом порядке) → клик на "Домашние квесты" → placeholder screen (как был MyCoursesRoot).
- Compile проверка: `./gradlew assembleDebug`.
- Test: `./gradlew :shared:feature:app-shell:domain:jvmTest` + `:android:feature:app-shell:presentation:test`.
- Grep: post-rename верификация — 0 matches `MyCourses*`.

## Problem 4: catalog-foundation — Catalog persistence + UI + sync

### Symptom
Spec требует создать полный catalog stack: Firebase Firestore collection, Room local cache, `CatalogRepository` impl, WorkManager sync, Material3 UI компоненты, + добавить `DrawerFooterAction.SyncNow`. В новом коде — **0 infrastructure** (Room, Coil, WorkManager — не используются нигде).

### Repro
N/A (new feature).

### Entry Points (EXHAUSTIVE)
**Walking Skeleton domain** (ready for integration):
- `shared/core/catalog/domain/src/commonMain/kotlin/.../model/Catalog.kt` — invariants готовы
- `shared/core/catalog/domain/src/commonMain/kotlin/.../model/CatalogId.kt` — готов
- `shared/core/catalog/domain/src/commonMain/kotlin/.../repository/CatalogRepository.kt` — interface готов
- `shared/core/catalog/domain/src/commonMain/kotlin/.../use_case/ObserveCatalogsUseCase.kt` — готов
- Fakes в `commonTest/` — готовы для integration tests

**Missing entry points (phase-01+ creates)**:
- `:shared:core:catalog:data` module — не включён в `settings.gradle.kts:26` → backend-dev добавить
- `CatalogEntity` + `CatalogDao` + `@Database` — с нуля → backend-dev
- `CatalogRemoteDataSource` (Firestore) + mapper — с нуля → backend-dev
- `CatalogRepositoryImpl` — с нуля → backend-dev
- `CatalogSpinner` / `CatalogGrid` / `CatalogGridItem` в `android/core/designsystem/` — с нуля → frontend-dev
- `DrawerFooterAction.SyncNow` member — frontend-dev (после backend-dev добавления в sealed)
- Koin `catalogDomainModule` + `catalogDataModule` — backend-dev
- Firestore rules для `catalogs/*` — backend-dev (через firestore.rules)
- WorkManager `SyncWorker` — backend-dev (или firebase-dev)

### Code Owners
- `shared/core/catalog/` — core (owner: backend-dev; domain уже готов как Walking Skeleton)
- `android/core/designsystem/` — design system (owner: frontend-dev)
- `shared/feature/app-shell/domain/model/DrawerFooterAction.kt` — add SyncNow (owner: backend-dev)
- `platform/firebase/` — Firestore + Storage access (owner: firebase-dev)
- `firestore.rules` — backend/firebase-dev (owner: infrastructure)
- `libs.versions.toml` — `backend-dev` (invariant #7)
- `build.gradle.kts`, `settings.gradle.kts` — `backend-dev`

### Flow Trace
```
First app open / SyncNow clicked
  → WorkManager enqueue CatalogSyncWorker
  → CatalogRemoteDataSource.fetch() (Firestore .get().await())
  → map DocumentSnapshot → Catalog (domain)
  → CatalogLocalDataSource.insertAll() (Room DAO)
  → CatalogRepositoryImpl.observeAll() emits updated list (Flow<List<Catalog>>)
  → ViewModel/Screen (future specs) consumes via ObserveCatalogsUseCase
  → UI: CatalogSpinner / CatalogGrid + AsyncImage resolving picturePath → Storage downloadUrl
```

### Backend / Contract Check
- **Firestore schema**: `catalogs/{catalogId}` — **нет сейчас**. Нужно: admin-tool для seeding. Spec предлагает schema `{name, picturePath, order, createdAt, updatedAt}`. Backend-dev нужно fill.
- **Firestore rules**: добавить `match /catalogs/{catalogId} { allow read: if true; allow write: if request.auth.uid has admin }` в `firestore.rules`.
- **Firebase Storage**: bucket `catalog-pictures/*.jpg/png/webp` — **нет сейчас**, нужно seed admin-ом.
- **Firebase Storage ACL**: public read — нужно настроить в Firebase Console (или Storage rules).
- Клиент не пишет в `catalogs/*` — инвариант.

### Constraints
- Lifecycle: ViewModel-scoped для `ObserveCatalogsUseCase`.
- In-memory state: ограничен только Flow observation.
- DB/Storage:
  - Room: **никакого AppDatabase нет**. Варианты — (a) создать центральный `AppDatabase` в `shared/core/persistence/` с catalog + будущих entities; (b) отдельный `CatalogDatabase` в `shared/core/catalog/data/`. Option (a) scales лучше.
  - Firebase Storage для pictures: resolve URL либо в repository (`StorageReference.downloadUrl.await()`) либо в Coil custom Fetcher (см. Open Question #4 в research).
- Offline/Online: first-launch offline → empty state UI; cached работает offline.
- Sync: WorkManager constraint `NetworkType.CONNECTED`.

### Code Path Divergence — RESOLVED 2026-04-20

Выбран **Path D (USER DECISION)**: `RootComponent.onSyncNow()` method + `RootEvent.SyncStarted` event.

- `DrawerFooter` → `rootComponent.onSyncNow()` (через pass-through from AppShellScreen).
- `DefaultRootComponent.onSyncNow()`: `workManager.enqueueUniqueWork("manual_sync", REPLACE, oneTimeSyncWorkRequest)` → `_events.trySend(RootEvent.SyncStarted)`.
- `AppShellScreen` collect-ит `events` Flow → `RootEvent.SyncStarted` → `snackbarHostState.showSnackbar("Синхронизация запущена")`.

Также Q5 decision: **создан полный SyncWorker infrastructure** (не reject of WorkManager scope). Periodic 1 день default.

### Fix Shape (минимально реализуемое решение)

Phase-01 infrastructure setup:

1. Backend-dev:
   - Добавить в `libs.versions.toml`: `coil3`, `androidx-work-runtime-ktx` (already declared), Room alias-ы (declared, но не используются).
   - Добавить `include(":shared:core:catalog:data")` в `settings.gradle.kts`.
   - Создать `shared/core/catalog/data/build.gradle.kts` с ksp plugin + Room + coroutines.
   - Создать `CatalogEntity`, `CatalogDao`, `CatalogLocalDataSource`.
   - Создать `CatalogRemoteDataSource` в `platform/firebase/` (firebase-dev ownership).
   - Создать `CatalogRepositoryImpl` в `shared/core/catalog/data/`.
   - `catalogDomainModule`, `catalogDataModule` Koin, добавить в `AppApplication.kt`.
   - Sync worker (see `platform/android-services/` или `shared/core/sync/`) — design phase решит где.
   - Добавить `DrawerFooterAction.SyncNow` в sealed set → breaks existing `when`-expressions → нужно обновить одновременно.
   - `firestore.rules` — добавить `catalogs` block.

2. Firebase-dev:
   - Настроить Firestore collection `catalogs` с admin-seed.
   - Firebase Storage bucket `catalog-pictures/` c public read.

3. Frontend-dev:
   - `CatalogSpinner` в `android/core/designsystem/components/` — `ExposedDropdownMenuBox` с `menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)` (Material3 1.3+ API).
   - `CatalogGrid` + `CatalogGridItem` — `LazyVerticalGrid(GridCells.Fixed(2))`.
   - `AsyncImage` из Coil 3 с custom Fetcher OR pre-resolved HTTPS URL.
   - `DrawerFooter.kt:49-57` — обновить `when` для `SyncNow` → trigger механизм (design phase).
   - `Labels.kt:114-119` — добавить branch для `SyncNow.displayName = "Синхронизация"` (или "Sync now").

4. Test-dev:
   - Domain tests: уже в Walking Skeleton (27 тестов).
   - Data tests: `CatalogEntityMapperTest`, `CatalogLocalDataSourceTest` (Room in-memory), `CatalogRemoteDataSourceTest` (MockWebServer или Firebase emulator).
   - Repository integration: `CatalogRepositoryImplTest` (with fakes).
   - UI: Compose preview tests for `CatalogSpinner`, `CatalogGrid`.
   - `DrawerFooterActionTest.kt:69-82` — обновить `assertEquals(3, all.size)` + добавить `SyncNow` в expected list.

**Requires backend**: Firestore seed + Storage seed (через admin-tool или manual Firebase Console).

**Follow-up (post-phase-01)**:
- Orphan quest handling (spec Open Question).
- Periodic sync periodicity (spec не определяет; рекомендация — 24h).
- Image caching tuning.

### Validation
- Ручной:
  - First open (offline) → empty state UI.
  - First open (online) → fetch → grid с 4 каталогами (surveys/courses/games/school) в порядке по `id.value ASC`.
  - Dev mode → SyncNow видим → click → snackbar "Синхронизация запущена" → cache обновлён.
  - Release + developer=0 → SyncNow не видим.
- Тесты: `./gradlew :shared:core:catalog:domain:jvmTest` (27 tests) + `:shared:core:catalog:data:test` (data tests) + `:android:core:designsystem:test` (UI).
- firestore.rules: client write → permission denied; admin write (qualifications.admin >= 100) → success.
- Grep: `grep -rn "pictureUrl" shared/core/catalog/` → 0 matches (domain purity — picturePath only).

## Problem 5: visibleFooterActions signature change breaks consumers

### Symptom
`Visibility.visibleFooterActions(isDebugBuild)` — текущая signature. Spec dev-mode требует `(isDebugBuild, stats, overlay)`. Catalog-foundation adds `SyncNow` в sealed. Два atomic changes в одной функции.

### Repro
1. Изменить sig `visibleFooterActions` → compile errors в 3+ call sites.
2. Добавить `SyncNow` в `DrawerFooterAction` sealed → compile errors в `when (action)` exhaustive checks в `DrawerFooter.kt:49-54` и `Labels.kt:114-119`.
3. `DrawerFooterActionTest.kt:69-82` test с `assertEquals(2, all.size)` → fails.

### Entry Points (EXHAUSTIVE)
- `Visibility.kt:142-144` — function definition.
- `DrawerFooter.kt:38` — 1-arg call.
- `DrawerFooter.kt:49-57` — `when (action)` 2-branch exhaustive.
- `Labels.kt:114-119` — `when (this)` 2-branch for displayName.
- `DrawerFooterActionTest.kt:69-82` — test "exactly two variants".
- `DrawerFooterMapperTest.kt:17,26,35,36` — 3+ tests с 1-arg calls.

### Code Owners
- `Visibility.kt` — domain (backend-dev).
- `DrawerFooter.kt` — presentation (frontend-dev).
- `Labels.kt` — presentation (frontend-dev).
- Tests — test-dev.

### Flow Trace
```
AppShellScreen (state.userStats, isDebugBuild, overlay? from rootComponent)
  → DrawerContent(userStats, isDebugBuild, ...)
  → DrawerFooter(navigator, isDebugBuild, versionName, userStats?, overlay?)
  → visibleFooterActions(isDebugBuild, stats, overlay) — NEW signature
  → filtered list
```

### Backend / Contract Check
N/A.

### Constraints
- Lifecycle: pure function, нет.
- DB/Storage: нет.
- Offline/Online: нет.

### Code Path Divergence
Нет — единый path.

### Fix Shape (минимально реализуемое решение)
**Client-only fix** — atomic coordinated commit:

1. Backend-dev в одном commit:
   - Расширить sig `visibleFooterActions(isDebugBuild, effectiveDeveloperLevel)` (per Option C из Problem 2).
   - Добавить `SyncNow` в `DrawerFooterAction` sealed.
   - Обновить function body для возврата `[DesignCatalog?, SyncNow?, About]` в правильном порядке.

2. Frontend-dev в одном commit (после backend-dev):
   - Обновить `DrawerFooter.kt:38` call с новыми args (через проброс из `DrawerContent`).
   - Обновить `DrawerFooter.kt:49-57` `when (action)` — добавить `SyncNow` branch c handler (trigger via design-chosen path).
   - Обновить `Labels.kt:114-119` — добавить `SyncNow.displayName`.

3. Test-dev в одном commit:
   - Обновить `DrawerFooterActionTest.kt` — expect 3 variants.
   - Обновить `DrawerFooterMapperTest.kt` — new args.
   - Обновить `VisibilityTest.kt` — новые scenarios для визибильности SyncNow.

### Validation
- Compile: `./gradlew assembleDebug` зелёный.
- Tests: все obновлённые тесты зелёные.
- Ручной: все 4 состояния (debug/release × developer=0/100) дают правильный output (см. `0-spec-dev-mode.md:329-335` Full matrix).

## Invariant Conflicts

### Conflict 1: Cross-feature direct import (Problem 1)

**Invariant**: `No direct import между feature-модулями` (`docs/invariants.md:26-31`, rule #3 + `.claude/rules/clean-architecture.md`).

**Planned change**: `app-shell:domain → qualification:domain` direct import `QualificationLevel`.

**Resolution options**: см. Problem 1 Fix Shape — A/B/C. Option A (move to `core/foundation`) — чище, но меняет spec scope.

### Conflict 2: Walking Skeleton ownership (Problem 2)

**Invariant**: `Walking Skeleton ownership — domain код не переписывается в downstream фазах` (`docs/invariants.md:49-55`, rule #6).

**Planned change**: `LocalDeveloperOverrideRepository` interface promotion с synchronous `fun` → `suspend fun` + `Flow<LocalDeveloperOverride>`.

**Resolution**: Spec dev-mode Walking Skeleton `LocalDeveloperOverrideRepository.kt` KDoc явно отмечает это как Open Question (lines 19-27). Promotion не считается "rewriting", это "renaming classes in design phase — допустимо" per invariant #6 constraint. **OK** — не conflict.

### Conflict 3: No invariants violated by home-quests (Problem 3)

Rename + reorder — не затрагивает ни один invariant. **No conflict**.

### Conflict 4: Scaffold file ownership (Problem 4 + 5)

**Invariant**: `Файлы build.gradle.kts, libs.versions.toml, settings.gradle.kts меняет только backend-dev` (`docs/invariants.md:57-63`, rule #7).

**Planned changes**:
- `settings.gradle.kts` — добавить `:shared:core:catalog:data`.
- `libs.versions.toml` — Coil 3, WorkManager (библиотеки уже декларированы, нужно проверить), Room (надо activate ksp).
- Several `build.gradle.kts` — зависимости qualification→catalog→app-shell.

**Resolution**: эти изменения делает `backend-dev`. Frontend-dev / test-dev / firebase-dev шлют `SendMessage` lead-у, не редактируют сами. **No conflict при соблюдении rule**.

## Summary

5 проблем идентифицированы, все — интеграционные. Основное изменение по сравнению с изначальной spec: **Walking Skeleton dev_mode package частично переписывается** (revert codex fix #2) — это допустимо по invariant #6 "Renaming классов в design phase допустимо; изменение business rules — architectural mismatch, эскалация". Эскалация произошла (user approved).

### Все 10 Open Questions resolved 2026-04-20

| # | Decision | Impacts |
|---|---|---|
| 1 | QualificationLevel → shared/core/foundation/ | Walking Skeleton move + build.gradle.kts deps |
| 2 | Revert codex fix #2 — прямая запись developer=100 | Walking Skeleton dev_mode частично переписывается |
| 3 | RootComponent.onSyncNow() + RootEvent.SyncStarted | Новый method + event в RootComponent interface |
| 4 | Quest placeholder остаётся в catalog/domain как TEMP | Без migration в quiz-feature сейчас |
| 5 | Создать SyncWorker + refreshProfile() infrastructure | Полноценный WorkManager + Room |
| 6 | Room как storage (не DataStore) | Central AppDatabase; dev mode persists |
| 7 | Coil 3.4.0 | Image loading для CatalogGrid |
| 8 | Kotlin 1.9.22 verified | `.entries` API доступен |
| 9 | Sync periodicity 1/2/3/4/7/14/30 дней, default 1 день | Settings screen — follow-up |
| 10 | HandleBackUseCase cleanup отложен | Не блокер |

Дальше: `/feature-design menu-refactor` — design phase создаёт `01-architecture.md` + conditional docs (`06-api-contract.md`, `07-events.md`, `08-storage-model.md`). Все open questions закрыты, design может начаться без дополнительных user вопросов.
