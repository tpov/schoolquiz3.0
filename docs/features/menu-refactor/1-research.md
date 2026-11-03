---
date: 2026-04-20
researcher: Claude
commit: 7c52c200
branch: kmp-skillify-4.0
---

# Research: Menu Refactor (Master)

Фича объединяет 4 sub-spec:
- `qualification-levels` — enum `QualificationLevel` + замена magic `100`
- `dev-mode` — 10-tap unlock + **прямая запись `developer=100` в local Room** + superqualification (**revert codex fix #2 per user decision 2026-04-20**)
- `home-quests` — rename `MyCourses → HomeQuests` + reorder LOCAL
- `catalog-foundation` — `Catalog` сущность + Firebase + UI компоненты + WorkManager sync

## User Decisions (Resolved 2026-04-20)

Open Questions #1-#10 закрыты пользователем через 2 раунда `AskUserQuestion`:

| # | Question | Decision | Impact |
|---|---|---|---|
| 1 | `QualificationLevel` location | **shared/core/foundation/** (move из qualification:domain) | Avoid cross-feature import. Walking Skeleton file `QualificationLevel.kt` + test переезжают. |
| 2 | Superqualification overlay propagation | **Нет overlay** — прямая запись `developer=100` в local Room (Option B, ранее отвергнут codex fix #2) | Revert codex fix #2. Walking Skeleton dev_mode частично переписывается: удалены `LocalDeveloperOverride`, `DeveloperLevelStats`, `EffectiveDeveloperLevel`, `LocalDeveloperOverrideRepository`. Добавляется local Room cache для UserStats. |
| 3 | SyncNow trigger | **Method + event в RootComponent** (`onSyncNow()` + `RootEvent.SyncStarted`) | Симметрично существующему паттерну. Snackbar через существующий events Flow. |
| 4 | Quest placeholder location | **Остаётся в catalog/domain как TEMPORARY** до quiz-feature | Scope не раздувается. Migration — будущая quiz-lifecycle spec. |
| 5 | Deactivation event (нет refreshProfile в новом app) | **Создать SyncWorker + refreshProfile() инфраструктуру** | Полноценный sync pipeline как в legacy. WorkManager periodic + manual. |
| 6 | Storage для dev mode state | **Room** (same infrastructure как для UserStats целиком). Dev mode persists между рестартами — "квалификации все хранятся в модели Room" | Central `AppDatabase` создаётся в phase-01. Нет отдельного DataStore для overlay. |
| 7 | Image loader | **Coil 3.4.0** (`io.coil-kt.coil3`) | Self-decided (Coil recommended для Compose KMP 2026). |
| 8 | Kotlin version | **1.9.22** (достаточно для `.entries`) | Self-verified в `libs.versions.toml`. |
| 9 | Sync periodicity | **Legacy values 1/2/3/4/7/14/30 дней** (user-configurable). MVP default = 1 день | Settings screen для выбора — follow-up фича. |
| 10 | HandleBackUseCase dead code | **Отложено** (не блокер menu-refactor) | Cleanup в отдельной фиче. |

**Spec файлы обновлены** (помечено `[UPDATED IN RESEARCH 2026-04-20]`):
- `0-spec.md` — User Decisions #15-#22, Cross-cutting Invariants (revert codex fix #2), ADR-0006/ADR-0004 updates.
- `0-spec-qualification-levels.md` — new location (`shared/core/foundation/`).
- `0-spec-dev-mode.md` — revert codex fix #2, переписанная модель без overlay; Walking Skeleton частично переделывается.
- `0-spec-catalog-foundation.md` — SyncNow trigger architecture + sync periodicity.

## Summary

**Walking Skeletons сгенерированы и валидны.** Три из четырёх sub-spec уже имеют Walking Skeleton domain код, сгенерированный на spec-фазе (Phase 3.8):
- `shared/feature/qualification/domain/` — `QualificationLevel` (14 тестов) + `dev_mode/` package (32+ тестов). Все зелёные на JVM.
- `shared/core/catalog/domain/` — `Catalog`, `CatalogId`, `Quest` (TEMP), `CatalogRepository`, `CreateQuestUseCase` (27 тестов зелёных).
- `home-quests` — Walking Skeleton не нужен (rename-only refactor).

Phase-01 в implement должен **интегрировать** этот skeleton, не переписывать.

**Критичные инфраструктурные gap-ы в new-stack, которые phase-01+ должен закрыть:**
1. `shared/core/sync/` — пустой stub (нет `Syncable` contract).
2. `shared/core/persistence/` — пустой stub (нет `AppDatabase`, нет Room usage в new code).
3. `shared/core/preferences/` — пустой stub (нет DataStore usage).
4. `shared/feature/qualification/data/` — пустой stub (нет `LocalDeveloperOverrideRepositoryImpl`).
5. `shared/feature/internet/profile/` — пустой (нет profile sync use case).
6. `shared/feature/quiz/domain/` — пустой; `Quest` placeholder живёт в `shared/core/catalog/domain/model/Quest.kt` (помечен TEMP).
7. `:shared:core:catalog:data` не зарегистрирован в `settings.gradle.kts:26` (только `:domain`).
8. WorkManager не декларирован ни в одном модуле new-stack (`libs.versions.toml:25` есть version, но ни одна `build.gradle.kts` не подтягивает `androidx-work-runtime-ktx`).
9. Room 2.7 декларирован (`libs.versions.toml:27`), но `ksp` plugin не подключён ни к одному new-stack модулю.
10. Image-loading library (Coil/Glide) не выбрана и не декларирована — ни в `libs.versions.toml`, ни в одном `build.gradle.kts`.
11. `AppShellScreen.kt` — нет `SnackbarHostState` в `Scaffold`. Для snackbar-feedback dev-mode нужно добавить.
12. Новый `apps/android-next` **не имеет SyncWorker / `refreshProfile()`** — профиль приходит реактивно через Firestore snapshot listener. Spec dev-mode FR #6 требует deactivation overlay "после успешного refreshProfile()" — event-point нужно определить заново (Open Question).

**Critical architectural decision needed перед design.** Spec `qualification-levels` AC #4 требует заменить magic `100` в `DrawerSection.kt` на `QualificationLevel.LEVEL_1.points`, но `shared/feature/app-shell/domain/build.gradle.kts` не объявляет зависимость на `:shared:feature:qualification:domain`. Добавление прямого import между feature-modules нарушит `.claude/rules/clean-architecture.md` (запрет cross-feature direct import). Три варианта — перенос `QualificationLevel` в `shared/core/foundation`, дублирование константы, либо явное ADR-обоснование cross-feature import. См. Open Question #1.

**Cross-feature couplings отсутствуют в текущем коде.** Все production imports однонаправленные (`app-shell:data → core:stats`, `platform:firebase → core:stats`). Bidirectional coupling = 0. Reflection cross-feature calls = 0.

## Architecture Overview

### Module map (затронутые модули)

| Зона | Модуль | State |
|------|--------|-------|
| shared/feature | `:shared:feature:app-shell:domain` | Реализован (27+ файлов, 15+ тестов) — предыдущая фича `app-shell-menu` |
| shared/feature | `:shared:feature:app-shell:data` | Реализован (`UserStatsRepositoryImpl` + Koin) |
| shared/feature | `:shared:feature:qualification:domain` | Walking Skeleton (enum + dev_mode package, 45+ тестов) |
| shared/feature | `:shared:feature:qualification:data` | Пустой stub (нет Kotlin files) |
| shared/feature | `:shared:feature:internet:profile:domain` | Пустой stub |
| shared/feature | `:shared:feature:quiz:domain` | Пустой stub |
| shared/core | `:shared:core:catalog:domain` | Walking Skeleton (Catalog/Quest/Use cases/Fakes, 27 тестов) |
| shared/core | `:shared:core:catalog:data` | **НЕ включён** в settings.gradle.kts |
| shared/core | `:shared:core:sync` | Пустой stub |
| shared/core | `:shared:core:persistence` | Пустой stub |
| shared/core | `:shared:core:preferences` | Пустой stub |
| shared/core | `:shared:core:stats` | Реализован (`RawUserStats`, `UserStatsDataSource`) |
| android/feature | `:android:feature:app-shell:presentation` | Реализован (DefaultRootComponent, AppShellScreen, DrawerFooter) |
| android/core | `:android:core:designsystem` | Реализован (SchoolQuizTheme, Brand* компоненты, DesignCatalogScreen) |
| platform | `:platform:firebase` | Реализован (FirebaseUserStatsDataSource, FirebaseInitializer) |
| platform | `:platform:android-services` | Пустой stub |
| apps | `:apps:android-next` | Реализован (MainActivity, AppApplication с Koin) |

### Domain types (существующие в `app-shell:domain`)

- `DrawerSection` sealed interface: `LocalSection` (MyQuests, **MyCourses**, Settings), `InternetSection` (Arena, Catalog, Qualifications, Profile, Social, Leaderboard), `EventsSection` (ActiveEvents, Minigames). Каждая имеет `tab: Tab` + `requiredRoles: Map<Role, Int>`. File: `DrawerSection.kt:18`.
- `TabConfig` sealed hierarchy: `LocalConfig` (MyQuestsRoot, **MyCoursesRoot**, SettingsRoot, DesignCatalogRoot, EmptyRoot), `InternetConfig`, `EventsConfig`, `ShopConfig`. File: `TabConfig.kt:9`.
- `DrawerFooterAction` sealed interface: `DesignCatalog`, `About` (2 элемента). File: `DrawerFooterAction.kt:14`.
- `UserStats`: `currentSkill: Int`, `qualification: Qualification`. File: `UserStats.kt:11`.
- `Qualification`: 6 полей (`tester`, `moderator`, `sponsor`, `translator`, `admin`, `developer` — все `Int ≥ 0`). Фабрика `Qualification.zero()`. File: `Qualification.kt:12`. **Это фактический аналог `UserQualifications` из ADR-0006** (само имя `UserQualifications` нигде в коде не встречается).
- `Role` enum: `USER, TESTER, MODERATOR, SPONSOR, TRANSLATOR, ADMIN, DEVELOPER`. File: `Role.kt:12`.
- `AppShellState`: `activeTab, localState, internetState, eventsState, shopState, isDrawerOpen, userStats`. File: `AppShellState.kt:24`. **НЕТ поля `LocalDeveloperOverride`** — overlay придётся либо добавлять в `AppShellState`, либо вести отдельный `StateFlow`.

### Visibility logic

`shared/feature/app-shell/domain/src/commonMain/kotlin/.../logic/Visibility.kt`:
- `isVisible(section: DrawerSection, stats: UserStats): Boolean` (line 50) — AND semantics: `section.requiredRoles.all { (role, min) -> actualLevel(role, stats) >= min }`. **Нет OR-bypass для DEVELOPER.**
- `visibleSections(tab: Tab, stats: UserStats): List<DrawerSection>` (line 67) — захардкоженный порядок per-tab; `Tab.SHOP → emptyList()`.
  - Текущий LOCAL: `[MyQuests, MyCourses, Settings].filter(isVisible)` — home-quests spec меняет на `[HomeQuests, MyQuests, Settings]`.
- `rootOf(section): TabConfig` (line 100) — 11-entry exhaustive when.
- `visibleFooterActions(isDebugBuild: Boolean): List<DrawerFooterAction>` (line 142) — возвращает `[DesignCatalog, About]` в debug, `[About]` в release. **Не принимает stats/overlay.** Dev-mode spec меняет signature на `(isDebugBuild, stats, overlay)`.

### Navigation architecture

- **Decompose + Single-Activity** (ADR-0008). Нет ViewModel; роль играет `DefaultRootComponent : ComponentContext`.
- `DefaultRootComponent.kt:56` — holds `MutableStateFlow<AppShellState>`, 4x `StackNavigation<C>` (local/internet/events/shop), 4x tab components. Koin factory `factory { (ctx) -> DefaultRootComponent(...) }`.
- `AppShellScreen.kt:77` — принимает `rootComponent`, `appVersionName`, `isDebugBuild`. Использует `Scaffold(topBar, bottomBar)` **без `snackbarHost` слота**.
- `Destination.kt:9` sealed interface: `Back, SwitchTab(tab), SelectSection(section), OpenDrawer, CloseDrawer, OpenDesignCatalog`. **Нет `SyncNow`, нет `ActivateDevMode`** — добавление потребует нового архитектурного решения (Open Question).
- `Navigator.kt:14` — `fun goTo(destination: Destination)`. `NavigatorImpl.kt:14` делегирует в `rootComponent.onDestination(d)`.

### Storage status

- **Firebase**: `platform/firebase/` — настроен с Firestore, Auth, Storage, Functions, AppCheck (PlayIntegrity). Один Koin single `UserStatsDataSource`. Коллекция `users/{uid}` — реализована. Коллекция `catalogs/*` — **не существует**. `firestore.rules` — только `users` и `quizzes` collections. Нет `Storage` resolve-pattern в коде.
- **Room**: `libs.versions.toml:27` декларирует Room 2.7 (KMP-ready), но `ksp` plugin не применяется, ни одна `@Entity`/`@Database` не существует в new-stack. AppDatabase отсутствует.
- **DataStore**: не используется ни в одном модуле. `shared/core/preferences/` — пустой stub.
- **WorkManager**: `libs.versions.toml:25` декларирует 2.9.1, но ни одна `build.gradle.kts` не подтягивает `androidx-work-runtime-ktx`. Sync в new app — реактивно через Firestore `addSnapshotListener` (`FirebaseUserStatsDataSource.kt:28`).

## Existing Patterns

### Koin DI (ADR-0009)

Паттерн per-feature module, один `val` на feature, regsitered в `AppApplication.kt:17`:
```kotlin
startKoin { modules(firebaseModule, appShellDataModule, appShellPresentationModule) }
```

- `appShellDataModule` (file `shared/feature/app-shell/data/di/AppShellDataModule.kt:7`): `single<UserStatsRepository> { UserStatsRepositoryImpl(get()) }`.
- `appShellPresentationModule` (file `android/feature/app-shell/presentation/di/AppShellPresentationModule.kt:16`):
  ```kotlin
  factory { (ctx: ComponentContext) -> DefaultRootComponent(ctx, get(), get(), get(), get()) }
  factory { InitializeAppShellUseCase(get()) }
  factory { NavigateUseCase() }
  factory { OnTabRetapUseCase() }
  factory { ObserveAppShellStateUseCase(get()) }
  ```
- `firebaseModule` (file `platform/firebase/src/main/kotlin/.../di/FirebaseModule.kt:9`): `single<UserStatsDataSource> { FirebaseUserStatsDataSource(...) }`.

**Нет DI модуля для `qualification:domain`/`qualification:data`, `catalog:domain`** — нужно создать в phase-01.

### Flow-based reactive data

Pattern: `Flow<T>` от DataSource → map через Repository → consume в Decompose component.

```
FirebaseUserStatsDataSource.observeRaw() (callbackFlow + addSnapshotListener)
  → UserStatsRepositoryImpl.observeStats() (.map { toDomain() }.retryWhen { AuthUidChanged })
  → ObserveAppShellStateUseCase.invoke(currentStateProvider)
  → DefaultRootComponent._appShellState.update { it.copy(userStats = ...) }
  → AppShellScreen.rootComponent.appShellState.collectAsStateWithLifecycle()
```

**Retry-on-auth-change pattern** (`UserStatsRepositoryImpl.kt:24`): `.retryWhen { cause, _ -> cause is AuthUidChanged }`.

**Stale-closure mitigation** (`ObserveAppShellStateUseCase.kt:20`): через `currentStateProvider: () -> AppShellState` — per ADR-COMP-01 из предыдущей фичи `app-shell-menu/03-decisions.md`.

### Testing conventions

- **Framework**: `kotlin.test` (`kotlin.test.Test`, `assertEquals`, `assertTrue`, `assertFalse`, `assertFailsWith`).
- **Source set**: `commonTest` (KMP common, JVM execution). `jvmTest/kotlin/` содержит только `.gitkeep`.
- **Naming**: backtick style — `` `LEVEL_1 points equals 100`() ``.
- **Fakes**: есть два `FakeUserStatsRepository` (`domain/fake/` и `android/presentation/fake/`) — небольшое дублирование. `FakeCatalogRepository` и `FakeQuestRepository` в catalog skeleton. `FakeLocalDeveloperOverrideRepository` в qualification skeleton.
- **NO Turbine / Hilt test** — только `.toList()`, `.take()`, `.value` inspection для Flow testing.

### Compose UI patterns

- **Dark-only Material3 Theme** (`SchoolQuizTheme.kt:6`) — брендовая палитра per ADR-0010.
- **Brand components** (`android/core/designsystem/components/`): `BrandPrimaryButton`, `BrandSecondaryButton`, `BrandCard`, `BrandProgressBar`, `BrandCircleIconButton`, `BrandDrawerItem`, `BrandNavBarItem`, `CategoryIcon`.
- **DesignCatalogScreen** (`android/core/designsystem/catalog/DesignCatalogScreen.kt`) — уже существует, рендерится в `AppShellScreen.kt:255` условно `isDebugBuild` (release → "Недоступно" placeholder).
- **ExposedDropdownMenuBox**, **LazyVerticalGrid**, **AsyncImage** — нигде не используются, будут новыми в catalog-foundation.

### Scroll-to-top registry (`android/feature/app-shell/presentation/ui/scroll/`)

Уже существующий pattern `ScrollToTopRegistry` + `CompositionLocal` — провайдится в `AppShellScreen.kt`.

## Integration Points

### Firebase

- **Init**: `platform/firebase/.../FirebaseInitializer.kt:8` — `initializeFirebaseSecurity(app)` вызывает `FirebaseApp.initializeApp(app)` + AppCheck PlayIntegrity. Вызывается из `AppApplication.kt:14`.
- **Firestore SDK** — используется **официальный Firebase Android SDK** (`firebase-firestore-ktx`), не KMP GitLiveApp SDK. `platform/firebase` — `schoolquiz.android.library` plugin (не KMP).
- **Firebase Storage** — задекларирован (`platform/firebase/build.gradle.kts:14`), но не используется в коде. Нет resolve-pattern `storageRef.downloadUrl`.
- **Snapshot pattern** (`FirebaseUserStatsDataSource.kt:28`): `callbackFlow { addSnapshotListener; awaitClose { listener.remove() } }` — reusable pattern для catalog collection.

### Sync

Новый app (`apps/android-next`) **не имеет SyncWorker/WorkManager**. Sync = Firestore реальный time через snapshot listener. Для `refreshFromRemote()` (catalog) + `refreshProfile()` hook (dev-mode deactivation) нужны design решения. Legacy reference: `legacy/app/src/main/.../SyncWorker.kt:39` — `CoroutineWorker` + `syncProfile → syncSettings → syncQuizData`. В новой архитектуре аналога нет.

### User stats propagation

Trace от source к UI:
```
platform/firebase/FirebaseUserStatsDataSource.kt:28
  → shared/feature/app-shell/data/UserStatsRepositoryImpl.kt:21
  → shared/feature/app-shell/domain/use_case/ObserveAppShellStateUseCase.kt:31
  → android/.../component/DefaultRootComponent.kt:153 (_appShellState.update)
  → android/.../ui/AppShellScreen.kt:83 (collectAsStateWithLifecycle)
  → DrawerContent (line 119, passes userStats to DrawerHeader)
  → **DrawerFooter** (line 45 of DrawerContent.kt) — НЕ получает userStats сейчас
```

**Missing link**: `DrawerFooter.kt:34` принимает только `navigator, isDebugBuild, versionName`. Для dev-mode + SyncNow visibility нужно пробросить `userStats` и `overlay`.

## Detailed Findings

### 1. Qualification-levels sub-spec

**Walking Skeleton state** (`shared/feature/qualification/domain/`):

| Файл | Строка | Описание |
|------|--------|----------|
| `model/QualificationLevel.kt` | 12 | `enum class QualificationLevel(val points: Int) { LEVEL_1(100), LEVEL_2(200), LEVEL_3(300) }` |
| `model/QualificationLevel.kt` | 24 | `fun QualificationLevel.isReachedBy(points: Int): Boolean = points >= this.points` |
| `commonTest/.../QualificationLevelTest.kt` | 20 | 14 тестов, kotlin.test framework — покрывает все Domain Test Scenarios 1-14 из spec |

Scenario 13 использует `QualificationLevel.entries.size` → требует Kotlin 1.9+ (подтверждение версии — отсутствующий info).

**Magic number `100` occurrences** (production только):

| File | Lines | Context |
|------|-------|---------|
| `shared/feature/app-shell/domain/model/DrawerSection.kt` | 100, 101, 102, 103 | `Role.TESTER to 100`, `Role.MODERATOR to 100`, `Role.ADMIN to 100`, `Role.DEVELOPER to 100` в `EventsSection.ActiveEvents.requiredRoles` |

В тестах app-shell `VisibilityTest.kt:161-390` — `100` дублируется как assertion values (не production logic). Замена production ≠ замена тестов (assertion может остаться с литералом 100).

**Missing infrastructure**:
- Нет DI модуля для `qualification:domain`.
- `shared/feature/qualification/domain/build.gradle.kts` — пусто (нет coroutines, нет datetime).
- `shared/feature/app-shell/domain/build.gradle.kts` не объявляет dependency на `:shared:feature:qualification:domain`.

**User terminology mismatch**: spec упоминает `UserQualifications`, реальный тип — `Qualification` (`shared/feature/app-shell/domain/model/Qualification.kt:12`). ADR-0006 update должен использовать `Qualification`, не `UserQualifications`.

### 2. Dev-mode sub-spec

**Walking Skeleton state** (`shared/feature/qualification/domain/.../dev_mode/`):

| Компонент | Файл | Примечание |
|-----------|------|-----------|
| `TapProgress(count, lastTapAtMillis: Long?)` | `model/TapProgress.kt:19` | Использует `Long` (epoch millis), не `Instant` — нет `kotlinx-datetime` dep |
| `LocalDeveloperOverride(active, activatedAtMillis: Long?)` | `model/LocalDeveloperOverride.kt:21` | Two-way invariant enforced |
| `DeveloperLevelStats(developer: Int)` | `model/DeveloperLevelStats.kt:18` | Локальный контракт для `effectiveDeveloperLevel` без cross-module import |
| `TapResult` sealed | `model/TapResult.kt:17` | 4 варианта (NoChange/Activated/AlreadyDev/Reset), все с newProgress |
| `registerTap(...)` | `logic/RegisterTap.kt:34` | Pure FSM function — implements spec State Matrix |
| `effectiveDeveloperLevel(stats, overlay): Int` | `logic/EffectiveDeveloperLevel.kt:22` | Pure merge function |
| `LocalDeveloperOverrideRepository` interface | `repository/LocalDeveloperOverrideRepository.kt:32` | Синхронный (нет `suspend`, нет `Flow`) — есть Open Question в KDoc lines 19-27 про promotion |
| `ActivateDevModeUseCase(overlayRepo, readDeveloperLevel: () -> Int)` | `use_case/ActivateDevModeUseCase.kt:37` | Lambda-based reading → stale-closure safe |
| Fakes | `commonTest/.../fake/FakeLocalDeveloperOverrideRepository.kt` | Готов для integration |

Domain tests — 32+ покрывают Domain Test Scenarios 1-32 из spec.

**Critical missing infrastructure for phase-01**:
1. `shared/feature/qualification/data/` — нет Kotlin файлов, нет `LocalDeveloperOverrideRepositoryImpl`.
2. Нет `kotlinx-coroutines-core` в `shared/feature/qualification/domain/build.gradle.kts` — интерфейс остаётся синхронным пока backend-dev не добавит.
3. Нет DataStore / Room / Preferences setup.
4. `AppShellState` не содержит `LocalDeveloperOverride` — для UI reactive updates нужен архитектурный выбор.
5. Нет `refreshProfile()` event в новом app — spec FR #6 "auto-deactivate на success" неактуален без нового event point.
6. Нет `SnackbarHostState` в AppShellScreen — нужно добавить для FR #3 feedback.

**`visibleFooterActions` signature change impact** (breaking change):
- Definition: `Visibility.kt:142` — currently `(isDebugBuild: Boolean): List<DrawerFooterAction>`.
- Call sites:
  - `DrawerFooter.kt:38` — passes only `isDebugBuild`.
  - `DrawerFooterActionTest.kt:26,34,38` — 8 тестов.
  - `DrawerFooterMapperTest.kt:17,26,35,36` — 3+ теста.
- После spec change на `(isDebugBuild, stats, overlay)` — все call sites ломаются одновременно (compile check).

**Superqualification bypass in `isVisible()`** — архитектурное решение (см. Open Question #2):
- Option A: Добавить `overlay: LocalDeveloperOverride` как параметр `isVisible` → создаёт cross-module coupling `app-shell:domain → qualification:domain`.
- Option B: Pre-merge overlay в `UserStats.qualification.developer` в presentation/data boundary → ломает invariant "UserStats.qualification = server value".
- Option C: Добавить `effectiveDeveloperLevel: Int` в `AppShellState` → чистый, domain остаётся decoupled, overlay не утекает в domain.

**`DesignCatalogRoot` render condition** (`AppShellScreen.kt:255`):
```kotlin
if (screen.config == LocalConfig.DesignCatalogRoot && isDebugBuild) {
    DesignCatalogScreen(...)
} else {
    UnderConstructionScreen("Недоступно")
}
```
Spec FR #8 Codex fix #3 требует: `isDebugBuild || effectiveDeveloperLevel >= LEVEL_1.points`.

### 3. Home-quests sub-spec

**Exhaustive rename checklist** (детали от research-home-quests агента):

**Production — 8 lines в 5 files**:

| File | Line | Old → New |
|------|------|-----------|
| `DrawerSection.kt` | 38 | `data object MyCourses : LocalSection` → `data object HomeQuests : LocalSection` |
| `TabConfig.kt` | 24 | `data object MyCoursesRoot : LocalConfig` → `data object HomeQuestsRoot : LocalConfig` |
| `Visibility.kt` | 70 | `MyCourses,` в `visibleSections(Tab.LOCAL)` position 2 → `HomeQuests,` position **1** (reorder) |
| `Visibility.kt` | 108 | `DrawerSection.LocalSection.MyCourses -> LocalConfig.MyCoursesRoot` (rootOf) → `HomeQuests -> HomeQuestsRoot` |
| `AppShellTransitions.kt` | 31 | `MyCourses -> NavStack(MyCoursesRoot)` → `HomeQuests -> NavStack(HomeQuestsRoot)` |
| `Labels.kt` | 52 | `MyCourses -> "Мои курсы"` → `HomeQuests -> "Домашние квесты"` |
| `Labels.kt` | 68 | `MyCourses -> Icons.Default.Book` → `HomeQuests -> Icons.Default.Home` |
| `Labels.kt` | 88 | `MyCoursesRoot -> "Мои курсы"` → `HomeQuestsRoot -> "Домашние квесты"` |

**`Icons.Default.Book`** остаётся в imports (`Labels.kt:5`) — используется в строках 67 (MyQuests) и 71 (InternetSection.Catalog).

**Tests — 26 lines в 6 files**:
- `NavStackTest.kt` (9 lines: 26, 27, 34, 47, 55, 65, 68, 74, 77)
- `PrimaryUserJourneyTest.kt` (1 line: 132)
- `OnTabRetapUseCaseTest.kt` (5 lines: 51, 62, 71[comment], 74, 89)
- `AppShellTransitionsTest.kt` (3 lines: 160, 175, 190)
- `VisibilityTest.kt` (7 lines: 192[backtick name], 198[list reorder], 326[backtick name], 327, 336[backtick name], 341, 432)
- `DefaultRootComponentTest.kt` (1 commented-out line: 402, hygiene update)

**Absent assumptions** (нет deep links, нет DI hardcoding):
- Нет матчей для `my-courses`, `my_courses`, `myCourses` в `AndroidManifest.xml`, XML resources, Kotlin.
- `LocalConfig.MyCoursesRoot` не появляется ни в одном Koin `module { }` block.
- `DrawerFooterMapperTest.kt` и `AppShellScreenTest.kt`, `DrawerSectionListTest.kt` не содержат references на `MyCourses`.

**Order change in Visibility.kt:68-72** — это behavior change: `VisibilityTest.kt:192-202` (scenario 22) assertion `[MyQuests, MyCourses, Settings]` → `[HomeQuests, MyQuests, Settings]`.

### 4. Catalog-foundation sub-spec

**Walking Skeleton state** (`shared/core/catalog/domain/`):

| File | Path | Tests |
|------|------|-------|
| `Catalog`, `CatalogId` | `model/` | `CatalogTest.kt` — 14 tests (construction invariants, equality) |
| `Quest(id, catalogId, title)` TEMP | `model/Quest.kt:9-39` | `QuestCatalogLinkTest.kt` — 4 tests |
| `CatalogRepository` | `repository/` | `CatalogRepositoryContractTest.kt` — 9 tests |
| `QuestRepository` TEMP | `repository/` | (same) |
| `ObserveCatalogsUseCase`, `CreateQuestUseCase` | `use_case/` | (same) |
| `FakeCatalogRepository`, `FakeQuestRepository` | `commonTest/.../fake/` | ready for integration |

Total: 27 тестов JVM зелёные, покрывают scenarios 1-23 from spec.

**Missing for phase-01**:
1. `:shared:core:catalog:data` module — **не включён** в `settings.gradle.kts`. Phase-01 создаёт этот sub-module.
2. `CatalogEntity` + `CatalogDao` + `@Database` — не существует. Вся Room infrastructure с нуля.
3. `CatalogRemoteDataSource` (Firestore) + mapper `DocumentSnapshot → Catalog` — не существует.
4. Coil (или Glide) — **не декларирован** в `libs.versions.toml` и нигде не используется. Image-loading с нуля.
5. `CatalogSpinner`, `CatalogGrid`, `CatalogGridItem` в `android/core/designsystem/` — не существуют.
6. Firestore rules для `catalogs/*` — в `firestore.rules` нет этого collection.
7. Firebase Storage resolve pattern — нет в коде.
8. WorkManager — не подключён ни к одному модулю.

**DrawerFooterAction.SyncNow addition**:
- Adds to `shared/feature/app-shell/domain/model/DrawerFooterAction.kt:14` sealed set → breaks exhaustive `when` in `DrawerFooter.kt:49-57` и `Labels.kt:114-119`.
- Breaks `DrawerFooterActionTest.kt:69-82` "exactly two variants" assertion.

**SyncNow trigger architecture** (Open Question #3):
- `Destination` sealed hierarchy не содержит `SyncNow` entry. Нажатие не может быть `navigator.goTo(...)` — это side-effect, не navigation.
- Варианты: (A) Добавить `Destination.SyncNow` + обрабатывать в `DefaultRootComponent.onDestination()` (но это не navigation). (B) Callback `onSyncNow: () -> Unit` в `DrawerFooter` composable. (C) Inject `CatalogRepository` в presentation и вызывать прямо из composable.

**Quest placeholder ownership** (Open Question #4):
- `shared/core/catalog/domain/model/Quest.kt:9-39` помечен TEMPORARY в KDoc.
- `shared/feature/quiz/domain/` — пустой stub.
- Плана миграции в quiz-domain нет.

### 5. App-shell cross-cutting findings

**No ViewModel pattern**. Архитектура использует Decompose `DefaultRootComponent : ComponentContext`. `MutableStateFlow<AppShellState>` — одна source of truth в `DefaultRootComponent.kt:63`.

Impact на spec wording: spec dev-mode FR "вызвать ViewModel action" нужно читать как "вызвать метод компонента" или добавить event channel.

**HandleBackUseCase dead code** (`use_case/HandleBackUseCase.kt:19`) — есть тесты, нет Koin binding, не вызывается в `DefaultRootComponent`. Phase-01 dev-mode не должна использовать его.

**FakeUserStatsRepository duplication** — два экземпляра (`domain/fake/` и `presentation/fake/`). Низкий risk, но если dev-mode Tests понадобятся — пусть используют `domain/fake/`.

**`Scaffold` missing `snackbarHost`** (`AppShellScreen.kt:129-141`). Нужно добавить `SnackbarHostState` для dev-mode feedback + catalog SyncNow feedback.

**Decompose `serializer = null`** (ADR-COMP-02) — на все `childStack`, process death не сохраняет navigation state. Это не мешает menu-refactor, но контекст важен.

### 6. Cross-feature dependency scan

**Зависимости build.gradle.kts** (выборка релевантного):

```
:shared:feature:app-shell:data    → :shared:feature:app-shell:domain + :shared:core:stats
:android:feature:app-shell:presentation → :shared:feature:app-shell:domain + :android:core:navigation + :android:core:designsystem
:platform:firebase                → :shared:core:stats
:apps:android-next                → все (composition root)
:shared:core:catalog:domain       → kotlinx.coroutines.core (только)
:shared:feature:qualification:domain → ничего (только plugin)
:shared:feature:qualification:data → ничего (пусто)
:shared:feature:quiz:domain       → ничего (пусто)
```

**Cross-feature imports в коде**: 0. Bidirectional coupling: 0. Reflection calls: 0.

**Planned new dependencies for menu-refactor** (требуют добавления в `build.gradle.kts`, ownership — `backend-dev`):

| Direction | Ratio | Notes |
|-----------|-------|-------|
| `app-shell:domain → qualification:domain` | **BLOCKED by rule** (per `clean-architecture.md` cross-module table) | Direct feature→feature import запрещён. Нужно решение (Open Question #1) |
| `app-shell:data → qualification:data` | OK if isolated (data layer) | Low risk; но требует DI wiring для `LocalDeveloperOverrideRepository` |
| `app-shell:presentation → qualification:domain` | OK (presentation → domain) | Для tap handler использующего `ActivateDevModeUseCase` |
| `app-shell:presentation → catalog:domain` | OK | Для `SyncNow` trigger если использовать option C |
| `catalog:data → firebase` | OK (data → platform) | Standard |

**Shared SDK map**:
- Firebase — только `platform:firebase`. Other features не знают о Firebase напрямую.
- Room — нет usage в new-stack.
- WorkManager — нет usage в new-stack.
- Coroutines/Flow — везде где нужно.
- Koin — composition root + per-feature modules.

### 7. Web research findings

**Firebase Kotlin SDK vs official Android SDK**:
- Проект использует **официальный Firebase Android SDK** (`firebase-firestore-ktx` итд) через `platform/firebase` как `android.library`. Не GitLiveApp KMP SDK.
- `FirebaseBoM` новая версия (34+) **удалила `-ktx` suffix** — при обновлении зависимостей это breaking change.
- `StorageReference.downloadUrl` возвращает `Task<Uri>` в Android SDK — нужен `.await()` из `kotlinx-coroutines-play-services`.
- Known offline issue: `suspend fun` writes/updates to Firestore могут зависать при offline (GitLiveApp issue #518) — mitigation через race + Flow snapshot. Для catalog use case (read-only public data) этот issue не критичен.

**Coil 3 for Compose (2026)**:
- Latest: `io.coil-kt.coil3` **3.4.0** (February 2026). Gradle coordinates изменились vs Coil 2.
- `AsyncImage(model, contentDescription, imageLoader, ...)` — signature стабильна.
- Firebase Storage custom fetcher — **нет official** (FireCoil unmaintained). Нужно написать `Fetcher.Factory<StorageReference>` samостоятельно.
- Known issue: custom fetcher может re-fetch на recomposition (Coil issue #2551). Mitigation: resolve URL в repository + pass HTTPS URL в `AsyncImage` (не `StorageReference`).

**Material3 Compose**:
- `material3:1.4.0` latest. `ExposedDropdownMenuBox` — `@ExperimentalMaterial3Api`.
- `Modifier.menuAnchor()` parameterless deprecated с 1.3+ — use `menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)` для read-only spinner.
- `LazyVerticalGrid(columns = GridCells.Fixed(2))` + `PaddingValues` + `Arrangement.spacedBy(...)` — стандартный pattern.

**WorkManager + CoroutineWorker**:
- `enqueueUniquePeriodicWork(policy = KEEP)` для scheduled sync; `enqueueUniqueWork(policy = REPLACE)` для manual SyncNow trigger.
- Android 14 target: foreground service types обязательны (`dataSync` type — будет deprecated в Android 15). Для catalog sync (background без foreground service) проблема не применима.
- Testing: `TestListenableWorkerBuilder<SyncWorker>(context)`.

**Android 7-tap dev options reference**:
- AOSP: `TAPS_TO_BE_A_DEVELOPER = 7`, countdown toast начиная с tap 3.
- **NO tap timeout** в AOSP — spec `500ms reset` — project-specific design choice (user decision #10).
- Snackbar (Material3) — правильный mechanism в Compose context.

**Koin**:
- Koin 4.2.0 latest. ABI break от 4.1.x (NoSuchMethodError для transitively compiled libs).
- Project's libs.versions.toml нужно проверить — возможны transitively mixed versions.

**Decompose**:
- 3.5.0 latest. `Child#key: String` breaking change.
- Deep linking pattern: `handleDeepLink` extension в `RootComponent`.

## Cross-Feature Interactions

### Dependency Graph (before menu-refactor)

| Feature A | → | Feature B | Mechanism | File:line | Documented in ADR? |
|-----------|---|-----------|-----------|-----------|---------------------|
| app-shell:data | → | core:stats | `project(...)` | `app-shell/data/build.gradle.kts:13` | Yes (app-shell-menu ADR-COMP-05) |
| app-shell:presentation | → | app-shell:domain | `project(...)` | `presentation/build.gradle.kts:10` | Yes (ADR-0001) |
| app-shell:presentation | → | core:navigation | `project(...)` | `presentation/build.gradle.kts:11` | Yes (ADR-COMP-07) |
| platform:firebase | → | core:stats | `project(...)` | `firebase/build.gradle.kts:10` | Yes (ADR-COMP-05) |

### Bidirectional Coupling Risks

**None detected** — все feature-to-feature dependencies однонаправленные, через `data:` и `presentation:` слои.

### Planned new couplings для menu-refactor

| Feature A | → | Feature B | Mechanism | Documented? | Risk |
|-----------|---|-----------|-----------|-------------|------|
| app-shell:domain | → | qualification:domain | `import QualificationLevel` | **NO** | HIGH — нарушает `clean-architecture.md` запрет на cross-feature direct import |
| app-shell:data (new) | → | qualification:data (new impl) | `project(...)` + DI wiring | Needed | Low |
| app-shell:presentation | → | qualification:domain | `ActivateDevModeUseCase` injection | Needed | OK (presentation → domain allowed) |
| app-shell:presentation | → | core:catalog:domain | `CatalogRepository.refreshFromRemote()` for SyncNow | Needed | OK |
| core:catalog:data (new) | → | platform:firebase | Firestore + Storage access | Needed | OK |

### Shared SDK Across Features

| SDK | Used by (modules) | Recommended pattern (from Web research) | Current integration |
|-----|---|---|---|
| Firebase | `platform:firebase` | Single adapter module (correct) | Per-feature isolation via `UserStatsDataSource` interface |
| Firestore snapshot | `platform:firebase` | `callbackFlow + addSnapshotListener` | Implemented in `FirebaseUserStatsDataSource.kt:28` — reusable для catalog |
| Firebase Storage | (not yet used) | KMP: suspend `getDownloadUrl()`; Android: `.downloadUrl.await()` | **NEW** for catalog picturePath resolve |
| Coil 3 | (not yet used) | `AsyncImage` + custom Fetcher OR pre-resolve URL in repository | **NEW** for CatalogGrid |
| Koin | `:apps:android-next` + feature modules | Per-feature module, one `val` per feature | Pattern established |
| Room | (not yet used) | KMP 2.7+ with `ksp` plugin per module | **NEW** — нет existing AppDatabase |
| WorkManager | (not yet used) | `CoroutineWorker` + `enqueueUnique*Work` | **NEW** для catalog sync |
| Decompose | `android:core:navigation` + `app-shell:presentation` | Single-Activity + sealed config + `childStack(serializer=...)` | Pattern established |

### Undocumented Patterns

- **`clean-architecture.md` запрещает direct import между feature-modules.** Spec qualification-levels AC #4 создаёт такой import (`app-shell:domain → qualification:domain`). Нужно либо новое ADR в `menu-refactor/03-decisions.md`, либо архитектурный переосмысление (option: move `QualificationLevel` в `core/`).
- **SyncNow trigger** не вписывается в existing `Destination` sealed interface (не navigation). Нужно решение.

## State Matrix Validation

Проверка spec state matrices против реального кода.

### Qualification-levels Domain Test Scenarios

Все 14 сценариев spec `0-spec-qualification-levels.md:117-133` **покрыты** в `QualificationLevelTest.kt`. Нет пропущенных условий.

### Dev-mode Domain Test Scenarios

Все 32 сценария spec `0-spec-dev-mode.md:222-272` **покрыты** в тестах Walking Skeleton. Нет пропущенных условий.

**Несостыковки матрицы vs код**:
- Spec использует `Instant` (`kotlinx-datetime`); код использует `Long` (millis). Функционально эквивалентно, но KDoc `LocalDeveloperOverride.kt:15-18` явно отмечает это как Open Question — промоция нужна.

### Home-quests Domain Test Scenarios

Scenarios 1-7 spec `0-spec-home-quests.md:118-127` — **не покрыты** (Walking Skeleton не нужен для rename-only refactor). Проверка будет через обновление existing тестов (`VisibilityTest.kt` и др.) — 26 lines в 6 files.

### Catalog-foundation Domain Test Scenarios

Все 23 сценария spec `0-spec-catalog-foundation.md:318-347` **покрыты** (27 тестов JVM зелёные).

### Domain Contract Mismatches

**DrawerFooterAction sealed set**:
- Spec `0-spec-dev-mode.md:307-313` определяет финальный sealed set: `{DesignCatalog, SyncNow, About}` с порядком `[DesignCatalog?, SyncNow?, About]`.
- Код `DrawerFooterAction.kt:14` содержит `{DesignCatalog, About}`.
- **Match after catalog-foundation** implemented (SyncNow добавляется). Не match до.

**visibleFooterActions signature**:
- Spec signature: `visibleFooterActions(isDebugBuild, stats, overlay)` (`0-spec-dev-mode.md:34`).
- Код: `visibleFooterActions(isDebugBuild)` (`Visibility.kt:142`).
- **Требует signature change + обновление 4 call sites + 3+ tests**.

**isVisible semantics**:
- Spec: OR-bypass для `effectiveDeveloperLevel >= LEVEL_1.points` (superqualification).
- Код: только AND semantics (`Visibility.kt:50-52`).
- **Требует extension logic + архитектурное решение на overlay propagation** (Open Question #2).

**`AppShellState` contents**:
- Spec implies overlay reactive state доступен для UI (`0-spec-dev-mode.md:146-147` — UI перерисовывается).
- Код: `AppShellState.kt:24` содержит только `userStats: UserStats`, нет overlay.
- **Требует добавления поля (`effectiveDeveloperLevel: Int` или `overlay: LocalDeveloperOverride`) или отдельного StateFlow**.

## Conditional Documents Needed

Помимо стандартных `01-architecture.md` / `02-behavior.md` / `03-decisions.md` / `04-testing.md`, для этой фичи нужны:

| Document | Reason |
|----------|--------|
| `05-prior-art.md` | Есть legacy `SyncWorker.kt` + `StructureDataLocal.kt` для inspiration, + AOSP `BuildNumberPreferenceController` reference |
| `06-api-contract.md` | `DrawerFooterAction` sealed set extension, new `visibleFooterActions` signature, `isVisible` extension, `CatalogRepository` public API — single source of truth |
| `07-events.md` | Dev-mode Snackbar event pipe, SyncNow Snackbar, overlay deactivation trigger — event flow нестандартный |
| `08-storage-model.md` | Catalog Room entity + DAO + migration, `LocalDeveloperOverride` persistence decision (DataStore vs Room), Firestore catalog collection schema |

`09-security-model.md` — optional, если design решит что firestore.rules для catalog заслуживает отдельного обсуждения.

Walking Skeleton готов к phase-01 **интеграции** для всех трёх sub-spec с domain code. Для `home-quests` Walking Skeleton не был нужен.

## Open Questions

1. **`app-shell:domain → qualification:domain` coupling для `QualificationLevel.LEVEL_1.points`**. Spec `qualification-levels` AC #4 требует замены magic `100` в `DrawerSection.kt:100-103` на `QualificationLevel.LEVEL_1.points`. Это создаёт прямой feature→feature import, запрещённый `.claude/rules/clean-architecture.md`. Три варианта:
   - (A) Перенести `QualificationLevel` enum в `shared/core/foundation` (или новый `shared/core/qualification-model` module). Чистое решение per rule, но меняет scope spec (требует разрешение пользователя или design decision).
   - (B) Дублировать константу `100` в `app-shell:domain` с комментарием-ссылкой на `QualificationLevel.LEVEL_1`. Нарушает spec AC #4 "no magic numbers".
   - (C) Разрешить конкретный cross-feature import через ADR в `menu-refactor/03-decisions.md` с обоснованием (shared value class не создаёт coupling business logic). Нужен explicit user approval.

2. **Superqualification overlay propagation in `isVisible()`**. Три варианта:
   - (A) `isVisible(section, stats, overlay)` — cross-module import (same issue as #1).
   - (B) Pre-merge overlay в `UserStats.qualification.developer` на presentation boundary — ломает invariant "UserStats = server state".
   - (C) Расширить `AppShellState` новым полем `effectiveDeveloperLevel: Int`, computed в `DefaultRootComponent` при merge UserStats + overlay. `isVisible(section, stats, effectiveDeveloperLevel)` — чистое domain API, overlay не утекает. **Рекомендация — это option C, самое чистое**.

3. **`DrawerFooterAction.SyncNow` trigger mechanism**. `Destination` sealed interface не поддерживает non-navigation actions. Варианты:
   - (A) Добавить `Destination.SyncNow` + обрабатывать side-effect в `DefaultRootComponent.onDestination`. Semantically misleading (не navigation).
   - (B) Callback `onSyncNow: () -> Unit` в `DrawerFooter` composable — более локальное решение.
   - (C) Inject `CatalogRepository` напрямую в Composable через Koin `koinInject()` — прямой вызов `repository.refreshFromRemote()` и event для Snackbar.
   - (D) Добавить новый RootEvent/Method в `RootComponent` interface — `fun onSyncNow()` + `events: Flow<RootEvent>` для feedback.

4. **Quest placeholder ownership**. `Quest.kt` живёт в `shared/core/catalog/domain/model/` помеченный TEMPORARY. `shared/feature/quiz/domain/` пуст. План миграции не определён. Open:
   - Оставить Quest в catalog до реализации quiz-feature, с migration step в quiz-lifecycle spec.
   - Или уже переместить в `shared/feature/quiz/domain/` как часть catalog-foundation phase-01.

5. **`refreshProfile()` deactivation event в новом app**. Spec dev-mode FR #6 требует deactivate overlay "после `refreshProfile()` с `Result.success`". Новый app имеет только Firestore snapshot listener, не explicit `refreshProfile()`. Варианты:
   - (A) Каждый Firestore snapshot emission = implicit "refresh success" → deactivate overlay. Слабый контракт (может deactivate без реальной полной синхронизации).
   - (B) Добавить explicit sync worker в новый app как часть catalog-foundation, привязать deactivation overlay к завершению SyncWorker → consistent с legacy pattern. Scope creep для dev-mode.
   - (C) Оставить deactivation только через explicit manual call (например SyncNow button) — но это не "сам отключится при синхронизации" как в spec.

6. **DataStore vs Room для `LocalDeveloperOverride`**. Spec FR #10 говорит TBD. Поскольку `shared/core/preferences` и `shared/core/persistence` оба пусты — phase-01 выбирает один:
   - DataStore (`preferencesDataStore` + `Preferences` keys) — проще для 2-field overlay.
   - Room entity — консистентно с будущим `LocalDatabase`, но избыточно для 2 полей.

7. **Image loader choice**. Spec делегирует research → ничего не выбрано → phase-01 выбирает Coil 3 (recommended для Compose 2026). Подтвердить.

8. **Kotlin version check**. `QualificationLevelTest.kt` Scenario 13 использует `.entries.size` (Kotlin 1.9+ API). Нужно проверить `libs.versions.toml` kotlin version.

9. **WorkManager needed for first release?** Spec catalog-foundation требует WorkManager для periodic sync. New app пока не имеет ни одной background job. Варианты:
   - (A) Реализовать WorkManager в catalog-foundation phase-01 (scope creep — WorkManager infrastructure сама по себе).
   - (B) Отложить periodic sync, оставить только manual SyncNow + "on first open" pull в catalog MVP.

10. **`HandleBackUseCase` dead code** — не задача menu-refactor, но стоит зафиксировать: либо подключить в phase-01 через DI, либо удалить. Не блокер для menu-refactor.

## Constraints (from existing code and PROJECT_STRUCTURE.md)

1. **KMP**: `shared/*` модули должны оставаться KMP-compatible (androidTarget + jvm). `commonMain` + `commonTest` для всего shared кода без Android-specific API.
2. **Domain layer purity** (invariant #1): `shared/*/domain/` без Android/SDK/DI аннотаций. `kotlinx.coroutines`, `kotlinx.datetime` — OK.
3. **Koin DI** (ADR-0009): per-feature modules, registered в `AppApplication.startKoin { modules(...) }`.
4. **Scaffold ownership** (invariant #7): `build.gradle.kts`, `libs.versions.toml`, `settings.gradle.kts` — только `backend-dev`.
5. **Decompose + Single Activity** (ADR-0008): `MainActivity` single, navigation через sealed config.
6. **Material3 + Dark-only** (ADR-0010): все Compose UI dark theme, брендовая палитра.
7. **No `Hilt`, no `Dagger`** (ADR-0009): только Koin.
8. **Walking Skeleton ownership** (invariant #6): phase-01 не переписывает уже сгенерированный domain код. Renaming (MyCourses → HomeQuests в existing code) допустимо; rewriting `QualificationLevel` или `Catalog` — не допустимо.
9. **No Hilt test runner**, fakes используются вместо mocks для repositories (per `.claude/rules/testing.md`).
10. **`isReturnDefaultValues = true`** не применимо — нет Android unit tests в KMP.

## Summary for broadcast (design phase)

Cross-feature dependency summary для `menu-refactor`:
- **Impacts**: `app-shell:domain`, `app-shell:data`, `app-shell:presentation`, `qualification:domain` (Walking Skeleton), `qualification:data` (empty), `catalog:domain` (Walking Skeleton), `catalog:data` (not yet exists), `platform:firebase`, `core:stats`, `core:sync` (empty), `core:persistence` (empty), `core:preferences` (empty).
- **New dependencies required**: `app-shell → qualification` (direction TBD per Open Question #1), `app-shell:presentation → catalog:domain` (для SyncNow), `catalog:data → firebase`.
- **Bidirectional risks**: 0. No existing cross-feature coupling in new-stack.
- **Shared SDK**: Firebase (isolated in platform), Room/WorkManager/Coil/DataStore — **новые** инфраструктуры, нет существующих в new-stack.
- **Undocumented patterns**: `app-shell → qualification` direct import (см. Open Question #1); SyncNow trigger не навигационный — нужен design decision; `LocalDeveloperOverride` propagation — рекомендация Option C (effectiveDeveloperLevel в AppShellState).

Полные детали — в секциях выше. Design phase должна начаться с resolution Open Questions #1-#5 перед архитектурными решениями. Open Questions #6-#10 могут быть resolved в дизайне.
