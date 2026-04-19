---
date: 2026-04-18
researcher: Claude
commit: 35aeae89
branch: SchoolQuiz4.0
---

# Research: App Shell Menu (Bottom Navigation + Side Drawer)

## Summary

Фича `app-shell-menu` создаётся в **greenfield**-состоянии модульной KMP-сборки. `settings.gradle.kts:21-90` подтверждает `// layered-scaffold:start/end` маркер и включение всех 5 целевых модулей: `:shared:feature:app-shell:{domain,data}`, `:android:feature:app-shell:presentation`, `:android:core:{navigation,designsystem}`. Walking Skeleton (Variant Y) уже сгенерирован в `shared/feature/app-shell/domain/` — **26 production + 13 test Kotlin-файлов**, последний testrun (build/test-results/jvmTest/2026-04-17T19:38:47Z) записал **229 tests, 0 failures, 0 errors, 0 skipped** в 12 JUnit-suites. Domain purity инвариант подтверждён: нет `android.*`, `androidx.*`, Firebase/Retrofit/Room, DI-аннотаций (`@Inject`/`@Provides`) в `commonMain/`. Остальные 4 модуля — чистый scaffold (`.gitkeep`).

`apps/android-next` — stub-entrypoint: `MainActivity` рендерит только `TextView`, Application class отсутствует, `startKoin{}` не вызывается. `build.gradle.kts:15-19` подключает только `bundles.androidx.ui.base` — нет Compose BOM, Decompose, Koin, не подключён ни один feature-модуль. Convention-plugins (`AndroidApplicationConventionPlugin.kt:38-40`, `AndroidLibraryConventionPlugin.kt:38-40`, `KmpLibraryConventionPlugin.kt:8-42`) **не включают Compose** (только `viewBinding = true`) и **не применяют `kotlin-serialization` plugin** — это scaffold-work для phase-01 backend-dev.

Все необходимые library-aliases объявлены в `gradle/libs.versions.toml` (Decompose 3.1.0, Essenty 2.1.0, Compose BOM 2024.09.02, Compose compiler 1.5.10, Material Icons Extended через `compose-material-icons-extended`, kotlinx-serialization-json 1.6.3, Koin 3.5.6, Koin-Android, Koin-AndroidX-Compose 1.1.5). Bundle `koin-android` и `decompose` готовы. Version catalog полностью совместим со spec.

Cross-feature scan подтвердил **Invariant #3 compliance by absence** — нет ни одного cross-feature Kotlin-import (все feature-модули кроме app-shell/domain пусты). `app-shell/domain` не импортирует ничего из других features (только `kotlinx.coroutines.core`), что сохраняет правильное направление зависимости для shell-фичи.

Выявлены **2 delta относительно spec**, требующие внимания в design/plan фазе (не blockers для research):
1. `Navigator` interface из spec FR #16 **отсутствует в domain-коде** (только упоминается в KDoc `Destination.kt:6-7`). Domain exposes use cases напрямую.
2. `@Serializable` на `TabConfig` из spec NFR #2 **намеренно отложен** с комментарием `TabConfig.kt:15-16` («serialization is a data-layer concern; design phase will add kotlinx-serialization in the Decompose integration layer»).

Обе delta зафиксированы в `2-grounding.md` как open questions для design.

## Architecture Overview

### Модульная структура (подтверждена в `settings.gradle.kts:21-90`)

```
apps/android-next/                              -> схема: только AppCompatActivity + TextView stub
├─ MainActivity.kt:8                              -> AppCompatActivity, onCreate → setContentView(TextView)
└─ build.gradle.kts:15                            -> bundles.androidx.ui.base only

shared/feature/app-shell/
├─ domain/                                        -> Walking Skeleton Variant Y (229 tests green)
│   ├─ build.gradle.kts:1                           -> schoolquiz.kmp.library (androidTarget + jvm)
│   ├─ src/commonMain/kotlin/.../domain/
│   │   ├─ model/                                   -> 12 файлов (Tab, Destination, DrawerSection, TabConfig, UserStats, Role, Title, Qualification, BadgeContent, DrawerFooterAction, DeepLink, RetapOutcome, RootEvent)
│   │   ├─ state/                                   -> 4 файла (AppShellState, TabState, NavStack, TransitionResult)
│   │   ├─ logic/                                   -> 3 файла (Visibility.kt, AppShellFactory.kt, AppShellTransitions.kt)
│   │   ├─ use_case/                                -> 5 use cases
│   │   └─ repository/UserStatsRepository.kt:15     -> interface; Flow<UserStats> observeStats() + suspend currentStats()
│   └─ src/commonTest/kotlin/.../
│       ├─ fake/FakeUserStatsRepository.kt:18       -> in-memory MutableStateFlow<UserStats>
│       └─ <12 test suites, 229 tests green>
├─ data/                                          -> пусто (.gitkeep во всех source sets)
│   └─ build.gradle.kts:1                           -> schoolquiz.kmp.library, нет deps на domain

android/
├─ core/navigation/                               -> пусто (только .gitkeep)
│   └─ build.gradle.kts:9-11                        -> bundles.decompose подключён
├─ core/designsystem/                             -> пусто
│   └─ build.gradle.kts:9                           -> bundles.androidx.ui.base only; Compose BOM НЕ подключён
└─ feature/app-shell/presentation/                -> пусто
    └─ build.gradle.kts:9-12                        -> bundles.androidx.ui.base + bundles.androidx.lifecycle; нет domain deps
```

### Domain types (source of truth для phase-01 integration)

- `Tab.kt:7` — `enum class Tab { LOCAL, INTERNET, EVENTS, SHOP }`
- `Destination.kt:9` — sealed interface с `Back`, `SwitchTab(Tab)`, `SelectSection(DrawerSection)`, `OpenDrawer`, `CloseDrawer`, `OpenDesignCatalog`
- `DrawerSection.kt:18` — sealed interface с `LocalSection` (MyQuests/MyCourses/Settings), `InternetSection` (Arena/Catalog/Qualifications/Profile/Social/Leaderboard), `EventsSection` (ActiveEvents/Minigames). Каждая секция декларирует `requiredRoles: Map<Role, Int>`.
- `TabConfig.kt:9` — sealed supertype + 4 per-tab config-hierarchies (`LocalConfig`, `InternetConfig`, `EventsConfig`, `ShopConfig`) с `EmptyRoot` sentinel в каждом кроме Shop (`TabConfig.kt:22-53`).
- `AppShellState.kt:24` — data class с `activeTab`, `localState/internetState/eventsState/shopState: TabState<C>`, `isDrawerOpen: Boolean`, `userStats: UserStats`. Computed `activeSection: DrawerSection?` `:34`, `isShopActive: Boolean` `:43`. Factories: `default(stats)` `:59`, `fallback(stats)` `:76`.

### Transition logic (pure)

- `AppShellTransitions.kt:69` — `navigate(state, destination)` — main dispatcher
- `AppShellTransitions.kt:90` — `onBack(state)` — 4-step FSM (drawer → pop → switchLocal → emit SystemBack)
- `AppShellTransitions.kt:148` — `onSwitchTab(state, target)` — сохраняет current tab state + restore target
- `AppShellTransitions.kt:171` — `onActiveTabRetap(state, tab)` — возвращает `Pair<state, RetapOutcome>`
- `AppShellTransitions.kt:229` — `onSelectSection(state, section)` — cross-tab auto-switch + closeDrawer
- `AppShellTransitions.kt:285` — `onOpenDrawer(state)` — guard Shop no-op
- `AppShellTransitions.kt:308` — `onOpenDesignCatalog(state)` — переводит в LOCAL/DesignCatalogRoot
- `AppShellTransitions.kt:330` — `onDeepLink(state, deepLink)` — MVP stub (@Suppress("UNUSED_PARAMETER"))

### Visibility/Factory helpers

`Visibility.kt:34-142` — `actualLevel(role, stats)`, `isVisible(section, stats)`, `visibleSections(tab, stats)`, `defaultSection(tab, stats)`, `rootOf(section): TabConfig`, `emptyRootFor(tab): TabConfig`, `visibleFooterActions(isDebugBuild)`.

`AppShellFactory.kt:28-74` — 4 typed factories: `initialLocalTabState(stats)`, `initialInternetTabState(stats)`, `initialEventsTabState(stats)`, `initialShopTabState()`.

## Existing Patterns

### Convention plugins (`buildSrc/src/main/kotlin/`)

- `KmpLibraryConventionPlugin.kt:8-42` — `androidTarget() + jvm()`, JVM 17 toolchain. Не применяет `kotlin-serialization` плагин.
- `AndroidLibraryConventionPlugin.kt:38-40` — `viewBinding = true`; **Compose НЕ включён**.
- `AndroidApplicationConventionPlugin.kt:38-40` — `viewBinding = true`; **Compose НЕ включён**.

### Library bundles (`gradle/libs.versions.toml`)

- `bundles.decompose = [decompose, decompose-extensions-compose, essenty-lifecycle, essenty-state-keeper]` — `libs.versions.toml:178`
- `bundles.koin-android = [koin-core, koin-android, koin-androidx-compose]` — `libs.versions.toml:179`
- `bundles.compose-ui` — включает `compose-material-icons-extended` (`libs.versions.toml:93`, `:176`)
- `bundles.androidx.ui.base` — `core-ktx + appcompat + material` (View-system-oriented)
- Detekt config `detekt.yml:3-15` — `maxIssues: 10`, `MaxLineLength: 120`; plugin заявлен в catalog (`:194`), но не применяется в convention-plugins (только в legacy).

### Koin pattern (из ADR-0009, не реализован в новом коде)

- `ADR-0009:44-66` — `single<X> { ... }` для singletons, `factory { (param) -> ... }` для Decompose factories.
- `ADR-0009:71-89` — `startKoin { androidContext(this); modules(...) }` в `MainActivity` (не в Application class).
- Каждый leaf-module owns exactly один Koin module: `appShellDataModule` (val) в `shared/feature/app-shell/data/src/commonMain/kotlin/di/`, `appShellPresentationModule` (val) в `android/feature/app-shell/presentation/src/main/kotlin/di/`.

### Walking Skeleton (domain)

Уже реализованный pattern — pure Kotlin + `kotlinx.coroutines.core` + in-memory fakes в `commonTest`. Repository interface в domain, impl в data. Тесты используют `MutableStateFlow<UserStats>` для fake state source.

## Integration Points

### Domain → Presentation (phase-01 work)

- `InitializeAppShellUseCase` (`InitializeAppShellUseCase.kt:17`) — constructor-inject `UserStatsRepository`; `invoke()` → `suspend AppShellState`. Вызывается при старте `MainActivity` / `RootComponent`.
- `NavigateUseCase.invoke(state, destination)` (`NavigateUseCase.kt:19`) → `TransitionResult(newState, events)`.
- `HandleBackUseCase.invoke(state)` (`HandleBackUseCase.kt:20`) → `TransitionResult`.
- `OnTabRetapUseCase.invoke(state, tab)` (`OnTabRetapUseCase.kt:22`) → `Pair<AppShellState, RetapOutcome>`.
- `ObserveAppShellStateUseCase(userStatsRepository).invoke(initialState)` (`ObserveAppShellStateUseCase.kt:30`) → `Flow<AppShellState>` с `.map { stats -> initialState.copy(userStats = stats) }` (сохраняет navigation + обновляет stats).

### Data → Domain binding (phase-01 work)

- `UserStatsRepository` interface в domain (`UserStatsRepository.kt:15`).
- Production impl в `shared/feature/app-shell/data/` (пусто сейчас) — Firebase-backed или Firebase+Room combo.
- Koin binding: `val appShellModule = module { single<UserStatsRepository> { FirebaseUserStatsRepository(...) } }`.

### Presentation → Android (phase-01 work)

- `MainActivity.onCreate` должен: `startKoin { androidContext(this@MainActivity); modules(appShellModule, ...) }` + создать `DefaultComponentContext(lifecycle, stateKeeper)` + `RootComponent` + `setContent { SchoolQuizTheme { AppShellScreen(rootComponent) } }`.
- Existing theme (`apps/android-next/src/main/res/values/themes.xml:3`): `Theme.MaterialComponents.DayNight.NoActionBar` — совместим с Compose + `AppCompatActivity`, но обёртка `MaterialTheme` / `SchoolQuizTheme` происходит в Compose-слое.
- AndroidManifest (`apps/android-next/src/main/AndroidManifest.xml:1-15`) — `<application>` без `android:name` ⇒ Application class не зарегистрирована.

### Decompose API (verified в web research)

- `StackNavigation<C>()` + extension-методы `push`/`pop`/`replaceAll`/`bringToFront`/`popTo` в `com.arkivanov.decompose.router.stack`.
- `childStack(source, serializer: KSerializer<C>?, initialConfiguration, handleBackButton, key, childFactory)` → `Value<ChildStack<C, T>>`.
- State-saving: `@Serializable` + `serializer = Config.serializer()`. `serializer = null` — отключает state-saving (стек сбрасывается при process death).
- **Зависимость**: для KMP state-saving требуется `kotlinx-serialization-json` (не только `-core`) — Decompose encodes state как JSON на iOS/JVM/Web (`:190` plugin alias + `:73` json library alias в catalog).
- **Multiple stacks (4 tab components)**: каждому `childStack(...)` нужен уникальный `key` параметр. Recommended pattern — `bringToFront(config)` для tab switching.
- **BackHandler**: Decompose использует Essenty `BackHandler` (из `ComponentContext`), не Jetpack `BackHandler` — Jetpack-вариант регистрируется в root dispatcher и всегда перехватывает, даже в child component.
- `Children(stack, animation, content)` из `decompose-extensions-compose` для рендеринга активного child.

### Material3 Compose (verified в web research)

- `ModalNavigationDrawer(drawerContent, drawerState, gesturesEnabled, scrimColor, content)` — suspend `drawerState.open()`/`close()` в `coroutineScope.launch`. **Material3 Scaffold НЕ имеет `drawerContent` slot** — паттерн: `ModalNavigationDrawer { Scaffold { ... } }`.
- `NavigationBarItem(selected, onClick, icon, label, badge)` — параметр `badge: (@Composable BoxScope.() -> Unit)?` подтверждён (spec FR #18 badges API).
- `TopAppBar(title, navigationIcon, actions, scrollBehavior)` + varианты `CenterAlignedTopAppBar`, `LargeTopAppBar`.
- Material Icons Extended: `androidx.compose.material:material-icons-extended` (без Material3 варианта; через Compose BOM без явной версии).

## Detailed Findings

### 1. Build infrastructure gaps (phase-01 backend-dev work)

- **Compose не в convention plugins** — `AndroidApplicationConventionPlugin.kt:38-40`, `AndroidLibraryConventionPlugin.kt:38-40` содержат только `viewBinding = true`. Ни один целевой модуль (`apps/android-next`, `android/feature/app-shell/presentation`, `android/core/designsystem`) не имеет `buildFeatures { compose = true }` + `composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }`.
- **`kotlin-serialization` plugin не применяется** в `KmpLibraryConventionPlugin.kt`. Plugin alias есть (`libs.versions.toml:190`), но применён только в `shared/core/question-schema/build.gradle.kts:3`. Для Decompose `@Serializable` state-saving plugin обязан быть применён в модуле, содержащем Config-иерархию.
- **`kotlinx-serialization-core` alias отсутствует** в catalog — только `-json`. Для Decompose достаточно `-json` (Decompose кодирует state как JSON).
- **`android/core/navigation/build.gradle.kts:9-11`** подключает `bundles.decompose`, но `schoolquiz.android.library` не активирует Compose — `decompose-extensions-compose` функции не скомпилируются без compose compiler.
- **`android/core/designsystem/build.gradle.kts:9`** — только `bundles.androidx.ui.base`. Compose BOM не подключён. ADR-0010 требует здесь `SchoolQuizTheme`, Material3 wrappers.
- **`android/feature/app-shell/presentation/build.gradle.kts:9-12`** — только `bundles.androidx.ui.base + bundles.androidx.lifecycle`. Нет domain/navigation/designsystem dependencies, нет Compose.
- **`shared/feature/app-shell/data/build.gradle.kts`** — пустой `dependencies { }`, нет deps на `:shared:feature:app-shell:domain` (нужно для реализации `UserStatsRepository`).
- **`apps/android-next/build.gradle.kts:15-19`** — только `bundles.androidx.ui.base`. Нет Decompose, Koin, Compose, нет deps ни на один из 5 модулей.
- **Detekt / Ktlint plugins не применяются** в convention-plugins (detekt classpath есть в `buildSrc/build.gradle.kts:15`, конфиг `detekt.yml:3-15` готов). Только legacy модули их используют.

### 2. Koin DI setup (не инициализирован)

- `apps/android-next/src/main/AndroidManifest.xml:1-15` — `<application>` без `android:name` — **Application class не зарегистрирована и не существует** в `apps/android-next`.
- `apps/android-next/src/main/java/.../MainActivity.kt:8-20` — только `AppCompatActivity` + `setContentView(TextView)`. **`startKoin{}` не вызывается**.
- Koin artifacts (`libs.versions.toml:119-121, 179`): `koin-core 3.5.6`, `koin-android 3.5.6`, `koin-androidx-compose 1.1.5`, `bundles.koin-android`. Подключены только в catalog, не используются в коде.
- ADR-0009 говорит startKoin в `MainActivity` (`docs/architecture/0009-dependency-injection.md:71`): `startKoin { androidContext(this@MainActivity); modules(appShellModule, ...) }`. Нет Application class — это соответствует ADR (Koin 3.x работает с `androidContext(Context)` в любом месте).
- Legacy-референс (не для переиспользования): `/home/Programming/Android/schoolquiz4.0/legacy/app/src/main/java/com/tpov/schoolquiz/MainApp.kt:16-48` — Dagger 2 с `DaggerApplicationComponent` + `DaggerCommonComponent`. Не связан с apps/android-next.

### 3. Walking Skeleton domain verification (229 tests green)

Полная проверка подтверждена через `build/test-results/jvmTest/` (last testrun 2026-04-17T19:38:47Z):

| Test Suite | Tests | Failures | Errors | Skipped |
|------------|-------|----------|--------|---------|
| `AppShellStateTest` | 29 | 0 | 0 | 0 |
| `AppShellTransitionsTest` | 74 | 0 | 0 | 0 |
| `DrawerFooterActionTest` | 8 | 0 | 0 | 0 |
| `HandleBackUseCaseTest` | 7 | 0 | 0 | 0 |
| `InitializeAppShellUseCaseTest` | 7 | 0 | 0 | 0 |
| `NavigateUseCaseTest` | 16 | 0 | 0 | 0 |
| `NavStackTest` | 8 | 0 | 0 | 0 |
| `ObserveAppShellStateUseCaseTest` | 9 | 0 | 0 | 0 |
| `OnTabRetapUseCaseTest` | 6 | 0 | 0 | 0 |
| `PrimaryUserJourneyTest` | 8 | 0 | 0 | 0 |
| `UserStatsTest` | 16 | 0 | 0 | 0 |
| `VisibilityTest` | 41 | 0 | 0 | 0 |
| **TOTAL** | **229** | **0** | **0** | **0** |

Domain purity grep-паттерны (из `.claude/rules/domain-models.md`) — **все три дали empty output** на `shared/feature/app-shell/domain/`:
- `^import (android|androidx)\.` — 0 matches
- `^import (io\.livekit|com\.google\.firebase|retrofit2|okhttp3|androidx\.room|com\.squareup\.moshi|kotlinx\.serialization)` — 0 matches
- `@(Inject|Provides|Module|Singleton|HiltAndroidApp)` — 0 matches

Инвариант #1 (domain layer purity) — **PASS**.

### 4. Legacy references (для naming + пороги)

`legacy/app/src/main/java/com/tpov/schoolquiz/presentation/main/MenuList.kt:29-158` содержит `allMenuItems: List<MenuItemRequirement>` — 17 записей с `Inset + titleRes + iconRes + requiredRoles`. Пороги вычислены через `TitleUserValue.skillTitleCount[Title.X]?.first()`:

| Menu Item | Role requirement | Legacy порог | Spec маппинг |
|-----------|------------------|--------------|--------------|
| `MENU_HOME_QUIZ` | — | 0 (always visible) | `LocalSection.MyQuests` → `emptyMap()` ✓ |
| `MENU_MY_QUIZ` | USER | 0 (`RECRUIT.first = 0`) | `LocalSection.MyCourses` → `emptyMap()` ✓ |
| `MENU_SETTING` | — | 0 | `LocalSection.Settings` → `emptyMap()` ✓ |
| `MENU_DOWNLOADS` | USER | 3000 (`TEACHINGS.first`) | — (не в scope spec) |
| `MENU_PROFILE` | — | 0 | `InternetSection.Profile` → `emptyMap()` ✓ |
| `MENU_MASSAGE` | USER | 10000 (`PLAYER.first`) | `EventsSection.Minigames` → `{USER to 10000}` ✓ |
| `MENU_CHAT_TOURNAMENT` | USER | 10000 | `InternetSection.Social` → `{USER to 10000}` ✓ |
| `MENU_EVENT` | TESTER=100, MODERATOR=100, DEVELOPER=100, ADMIN=100 (AND) | 100 (all 4 roles) | `EventsSection.ActiveEvents` → `{TESTER to 100, MODERATOR to 100, ADMIN to 100, DEVELOPER to 100}` ✓ |
| `MENU_LEADER` | USER | 3000 | `InternetSection.Leaderboard` → `{USER to 3000}` ✓ |
| `MENU_ARENA` | USER | 3000 | `InternetSection.Arena` → `{USER to 3000}` ✓ |

**Совпадение 1-в-1** с `Section Visibility Rules` таблицей в `0-spec.md:649-668`, кроме неиспользуемых legacy items (DOWNLOADS, USERS, NEWS, FRIEND, REPORT, CONTACT, CHAT_BANNED). Spec добавил новые sections (`Qualifications`, `Catalog` отдельно от Leaderboard) с `emptyMap()` / `{USER to 3000}`.

`Title` ranges в legacy `TitleUserValue.kt:7-18` совпадают 1-в-1 со spec `Title` enum (`Title.kt:11-29` в domain).

`Role` enum в legacy `MenuItemRequirement.kt:1` содержит 7 значений: `USER, TESTER, MODERATOR, SPONSOR, TRANSLATOR, ADMIN, DEVELOPER` — совпадает со spec (`Role.kt:12` в domain).

**Legacy quirks** (reference only — не копировать):
- ID коллизия `MENU_REPORT = 9 == MENU_CONTACT = 9` (`SetItemMenu.kt:25-26`) — в новом shell не воспроизводится (нет REPORT/CONTACT в spec scope).
- `setupMenu` hardcoded значения `Qualification(200,200,200,200,200,300)` + `currentSkill=500000` (`MainActivity.kt:302`) — dev-stub, не реальные user data.
- `MENU_MASSAGE` = `Inset.EVENT`, `MENU_EVENT` = `Inset.NETWORK` — наоборот от logical ожидания (spec присвоил корректно).

### 5. Existing scaffold (5 модулей)

**`shared/feature/app-shell/data`**:
- `build.gradle.kts:1-5` — `schoolquiz.kmp.library`, namespace `com.tpov.schoolquiz.shared.feature.app_shell.data`. Пустой `dependencies { }`.
- Все 5 source dirs (`androidMain`, `commonMain`, `jvmMain`, `commonTest`, `jvmTest`) — только `.gitkeep`.

**`android/feature/app-shell/presentation`**:
- `build.gradle.kts:1-13` — `schoolquiz.android.library`, namespace `com.tpov.schoolquiz.android.feature.app_shell.presentation`. Dependencies: только `bundles.androidx.ui.base + bundles.androidx.lifecycle`.
- `src/main/AndroidManifest.xml` — пустой manifest.
- Src: только `.gitkeep`.

**`android/core/navigation`**:
- `build.gradle.kts:1-12` — `schoolquiz.android.library`. Dependencies: `bundles.androidx.ui.base + bundles.decompose`.
- Src: только `.gitkeep`.

**`android/core/designsystem`**:
- `build.gradle.kts:1-10` — `schoolquiz.android.library`. Dependencies: только `bundles.androidx.ui.base`. **Compose BOM не подключён.**
- Src: только `.gitkeep`, пустая `res/`.

**`apps/android-next`**:
- `AndroidManifest.xml:1-15` — `<application>` без `android:name`; Activity `.MainActivity` с MAIN/LAUNCHER intent.
- `res/values/themes.xml:3`, `res/values-night/themes.xml:3` — оба `Theme.MaterialComponents.DayNight.NoActionBar`.
- `MainActivity.kt:8-20` — `AppCompatActivity`, `setContentView(TextView)`. Нет Compose, Koin, Decompose.
- `build.gradle.kts:15-19` — только `bundles.androidx.ui.base + junit4 + bundles.testing.instrumented`.
- `strings.xml` — отсутствует в `apps/android-next/src/main/res/values/`.

### 6. Version catalog completeness (`gradle/libs.versions.toml`)

| Tool | Version | Alias | Status |
|------|---------|-------|--------|
| AGP | 8.11.0 (`:3`) | — | ✓ spec-compatible |
| Kotlin | 1.9.22 (`:4`) | — | ✓ |
| KSP | 1.9.22-1.0.16 (`:5`) | — | ✓ |
| Detekt | 1.23.6 (`:6`) | `detekt` plugin (`:194`) | ✓ plugin alias, not applied |
| Ktlint | 12.1.0 (`:7`) | `ktlint` plugin (`:195`) | ✓ plugin alias, not applied |
| compileSdk | 34 (`:11`) | — | ✓ |
| minSdk | 26 (`:12`) | — | ✓ |
| targetSdk | 34 (`:13`) | — | ✓ |
| kotlinx-coroutines | 1.7.3 (`:16`) | — | ✓ |
| kotlinx-serialization | 1.6.3 (`:17`) | `kotlinx-serialization-json` (`:73`), `kotlin-serialization` plugin (`:190`) | ✓ json alias; **-core alias отсутствует** |
| Compose BOM | 2024.09.02 (`:33`) | `compose-bom` (`:85`) | ✓ |
| Compose compiler | 1.5.10 (`:34`) | — | ✓ alias, не применён |
| Decompose | 3.1.0 (`:37`) | `decompose` (`:113`), `decompose-extensions-compose` (`:114`), bundle (`:178`) | ✓ |
| Essenty | 2.1.0 (`:38`) | `essenty-lifecycle` (`:115`), `essenty-state-keeper` (`:116`) | ✓ |
| Koin | 3.5.6 (`:39`) | `koin-core` (`:119`), `koin-android` (`:120`), bundle (`:179`) | ✓ |
| Koin Compose | 1.1.5 (`:40`) | `koin-androidx-compose` (`:121`) | ✓ (Android-only вариант) |
| Material Icons Extended | Compose BOM | `compose-material-icons-extended` (`:93`), in bundle `compose-ui` (`:176`) | ✓ |

## Cross-Feature Interactions

### Dependency Graph (current state)

| Feature A | → | Feature B | Mechanism | File:line | Documented in ADR? |
|-----------|---|-----------|-----------|-----------|---------------------|
| (none — all other features empty scaffolds) | — | — | — | — | — |

Нет ни одного cross-feature Kotlin-import в текущем коде (подтверждено codebase-researcher cross-feature scanner).

### Expected future consumers (per spec + ADR)

| Future consumer | Imports from | Mechanism | Doc |
|-----------------|--------------|-----------|-----|
| `android/feature/<X>/presentation` | `shared/feature/app-shell/domain` (`Navigator`, `Destination`, типы DrawerSection) | direct Gradle dep + Kotlin import | `0-spec.md:43` (NFR #3) + `0-spec.md:34` (FR #16) |
| `android/feature/app-shell/presentation` | `shared/feature/app-shell/domain` (все use cases, `AppShellState`, `TransitionResult`) | direct Gradle dep | `0-spec.md:102-108` |
| `apps/android-next` | `android/feature/app-shell/presentation` + все feature presentations | Gradle deps + Koin module aggregation | `ADR-0009:70-89` |
| `shared/feature/app-shell/data` | `shared/feature/app-shell/domain` (`UserStatsRepository`) | direct (стандартный data→domain flow) | `ADR-0001:49-53` |

### Bidirectional Coupling Risks

Нет bidirectional coupling в текущем коде. Направление будущих зависимостей — **unidirectional** (features → app-shell), совместимо с Invariant #3.

### Shared SDK Across Features

| SDK | Version | Recommended pattern (from Context7/web research) | Current integration |
|-----|---------|--------------------------------------------------|---------------------|
| Decompose 3.x | 3.1.0 | Один `RootComponent` на app, child components через `childContext(key, lifecycle)` с unique keys для tab stacks; `BackHandler` от Essenty (не Jetpack) | Ещё не интегрирован |
| Compose Material3 | BOM 2024.09.02 | `ModalNavigationDrawer { Scaffold { ... } }` (Scaffold не имеет drawer slot в M3); `NavigationBarItem.badge: @Composable BoxScope.() -> Unit` для badges API | Ещё не интегрирован |
| Koin | 3.5.6 | `startKoin { androidContext(...); modules(...) }` в одном entry-point; `single<T>` per тип в owner-module; `koin-androidx-compose` — только Android target | Ещё не интегрирован |
| kotlinx-serialization | 1.6.3 | `kotlinx-serialization-json` (не `-core`) требуется для Decompose state-saving; plugin `org.jetbrains.kotlin.plugin.serialization` применяется на module-level | Только `shared/core/question-schema` применяет |
| Firebase | BOM 33.2.0 | Изолирован в `platform/firebase` per ADR-0001:36-37; feature data-modules консьюмят через adapter | `platform/firebase` — пустой scaffold |

### Undocumented Patterns

Отсутствуют (нет ни одного cross-feature import в коде, нет reflection calls).

## State Matrix Validation

Domain код реализует State Matrix 1-в-1. Проверка через Walking Skeleton tests (229 tests green):

### Back-policy FSM (`AppShellTransitions.onBack`, `:90-145`)

| Spec строка | Domain implementation | Test |
|-------------|-----------------------|------|
| 1. drawer open → closeDrawer | `onBack.kt:91-97` | `AppShellTransitionsTest` scenarios 7 |
| 2. backStack.isNotEmpty → pop | `onBack.kt:99-115` | scenario 8 |
| 3. LOCAL + empty stack → emit SystemBack | `onBack.kt:117-125` | scenario 10 |
| 4-6. не-LOCAL + empty stack → switchTab(LOCAL) | `onBack.kt:127-145` | scenarios 9, 15 |

### Re-tap active tab FSM (`AppShellTransitions.onActiveTabRetap`, `:171-227`)

| Spec строка | Domain implementation | Test |
|-------------|-----------------------|------|
| backStack.isNotEmpty → POP_TO_ROOT | `:175-199` (использует `backStack.first()` — стек-инверсия, not `active`) | scenario 6 |
| backStack.isEmpty → NO_OP | `:201-227` | scenario 5 |

### Drawer visibility FSM

| Spec | Domain | Test |
|------|--------|------|
| LOCAL/INTERNET/EVENTS → hamburgerVisible=true, edgeSwipeEnabled=true | `AppShellState.isShopActive` `:43` — UI-слой читает | AC 3 (phase-01) |
| SHOP → both false | `onOpenDrawer.kt:285-306` — no-op при Shop | scenario 11 |

### Section Visibility Rules (`0-spec.md:649-668`)

Все 12 строк таблицы реализованы в `DrawerSection.kt:29-112` как `requiredRoles` literal. Legacy source 1-в-1.

### Domain Contract Mismatches

**Mismatch 1**: Spec FR #16 (`0-spec.md:34`) требует `interface Navigator { fun goTo(destination: Destination) }` в `commonMain`. **В domain коде interface отсутствует** (только KDoc упоминание в `Destination.kt:6-7`). Использование — через use case classes напрямую.

**Mismatch 2**: Spec NFR #2 (`0-spec.md:44`) требует `@Serializable` на всех Config sealed. **Namerenno отложено** в domain (`TabConfig.kt:15-16` комментарий: «design phase will add kotlinx-serialization in the Decompose integration layer»). Phase-01 должен решить: добавить в domain или в обёрточном Decompose-integration слое.

Эти mismatches зафиксированы как open questions в `2-grounding.md`, не blockers для research.

### Пропущенные условия

Не найдено. Все условия из spec State Matrix реализованы в domain коде.

### Непокрытые комбинации

Не найдено. `AppShellTransitionsTest` покрывает 74 сценария, `VisibilityTest` — 41.

## Conditional Documents Needed

- `07-events.md` — **не нужен**: spec содержит только `RootEvent.SystemBack` (domain→UI), никаких cross-feature event buses.
- `08-storage-model.md` — **нужен минимальный**: производственный `UserStatsRepository` impl в phase-01 может использовать Firebase-backed или Firebase+Room combo. Если Room cache — требуется migration + DAO + Entity + mapper. Конкретика — design-фаза.
- `05-ui-states.md` — **нужен**: spec FR #12 (`UserStats` header), FR #13 (`UnderConstructionScreen`), FR #17 (`DesignCatalogScreen`), `AppShellScreen` Compose композиция, navigation bar / drawer / top bar UI states.
- `06-navigation.md` — **нужен**: Decompose-integration layer (RootComponent, AppShellComponent wrapping domain state, StackNavigation-per-tab, `@Serializable` Config strategy).
- `03-decisions.md` — **нужен** для ADR-обоснования: `Navigator`-interface location (domain vs android/core/navigation), `@Serializable` placement (domain vs integration layer).

## Constraints

- **Scaffold ownership** (Invariant #7): `build.gradle.kts`, `libs.versions.toml`, `settings.gradle.kts`, `AndroidManifest.xml` меняет только `backend-dev`. Phase-01 потребует модификаций:
  - `buildSrc/src/main/kotlin/AndroidApplicationConventionPlugin.kt` (добавить Compose) или per-module `build.gradle.kts` overrides
  - `buildSrc/src/main/kotlin/AndroidLibraryConventionPlugin.kt` (аналогично)
  - `buildSrc/src/main/kotlin/KmpLibraryConventionPlugin.kt` (возможно — `kotlin-serialization` plugin)
  - `apps/android-next/build.gradle.kts` (добавить все feature deps + Koin + Compose + Decompose)
  - `apps/android-next/src/main/AndroidManifest.xml` (возможно — регистрация Application class)
  - `android/core/designsystem/build.gradle.kts` (Compose BOM)
  - `android/core/navigation/build.gradle.kts` (добавить shared domain dep)
  - `android/feature/app-shell/presentation/build.gradle.kts` (все deps)
  - `shared/feature/app-shell/data/build.gradle.kts` (domain dep + Firebase)

- **Walking Skeleton ownership** (Invariant #6): domain код в `shared/feature/app-shell/domain/` **не переписывается** в design/plan. Phase-01 интегрирует, не rewrite. 2 delta (`Navigator`, `@Serializable`) — open questions для design-фазы, не для rewrite existing.

- **KMP scope**: `KmpLibraryConventionPlugin.kt:15-16` compiles только `androidTarget() + jvm()`. iOS targets не добавлены. Spec предусматривает KMP-compatibility (NFR #1) — домен уже pure KMP, iOS добавится позже без изменений в domain.

- **Detekt / Ktlint enforcement**: config готов (`detekt.yml`), но не применяется на новых модулях. Если Detekt maxIssues=10 — phase-01 должен включить plugin.

- **Firebase в KMP data-module**: `shared/feature/app-shell/data` — KMP (`androidTarget + jvm`). Firebase SDK — Android-only. Для production `UserStatsRepository` impl — либо использовать `androidMain` source set с Firebase, либо обернуть через `expect/actual`, либо делегировать в `platform/firebase` adapter. Решение — design-фаза.

- **`onActiveTabRetap` POP_TO_ROOT** (`AppShellTransitions.kt:183`) использует `backStack.first()` (oldest at index-0). `NavStack.pop()` использует `backStack.last()` (newest). Асимметрия корректна, но latent reading hazard для phase-01 integrators.

## Open Questions

1. **`Navigator` interface location + existence**: spec FR #16 (`0-spec.md:34`) требует `interface Navigator { fun goTo(destination: Destination) }` в `shared/feature/app-shell/domain/commonMain`. В коде interface отсутствует. Options:
   - (A) Добавить interface в domain (pure Kotlin) + реализовать в presentation через delegation к use cases. Фичи-модули импортируют только `Navigator` + `Destination`.
   - (B) Оставить как есть (feature modules импортируют use cases напрямую) — но это нарушит spec NFR #3 "feature-presentation модули импортируют только Navigator / Destination".
   Требует design-решения в phase-03 или ранее.

2. **`@Serializable` placement на Config-иерархии**: spec NFR #2 (`0-spec.md:44`) требует `@Serializable` на всех Config sealed. Domain код (`TabConfig.kt:15-16`) отложил это комментарием. Options:
   - (A) Добавить `@Serializable` прямо в domain + применить `kotlin-serialization` plugin в `shared/feature/app-shell/domain/build.gradle.kts`. Это нарушает domain purity rule? Нет — `kotlinx.serialization` разрешён в domain для API-boundary constants (см. `.claude/rules/domain-models.md:27` — хотя и запрещает `@Json` аннотации). Design-решение.
   - (B) Создать отдельную `@Serializable` mirror-иерархию в integration-layer (presentation/navigation), mapper domain→serializable. Больше кода, но чище domain.

3. **Koin `startKoin` location**: ADR-0009 говорит в `MainActivity`. Spec не уточняет. Создавать ли Application class? Без него `androidContext(applicationContext)` из Activity работает, но теряется early-init hooks (Firebase init, Crashlytics).

4. **Compose в convention plugins**: добавлять ли Compose в `AndroidApplicationConventionPlugin` + `AndroidLibraryConventionPlugin`, или создавать отдельный `schoolquiz.android.compose.library` plugin? Или — ad-hoc в каждом `build.gradle.kts`? Решение — design/plan.

5. **Firebase в KMP `data` module**: как интегрировать Firebase SDK (Android-only) в KMP-модуль `shared/feature/app-shell/data`? Через `androidMain` source set, `expect/actual`, или делегировать в `platform/firebase` adapter?

6. **`kotlinx-serialization-core` alias**: добавлять ли отдельный alias в `libs.versions.toml`? Для Decompose state-saving достаточно `-json`. Scaffold-решение.

7. **Detekt/Ktlint enforcement**: phase-01 должен активировать plugins в convention-plugins (spec Constraints требует)? Или это отдельный infrastructure task?
