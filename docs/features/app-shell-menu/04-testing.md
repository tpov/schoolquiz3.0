---
date: 2026-04-18
feature: app-shell-menu
authors: [architect-component]
---

# Test Strategy: App Shell Menu

Документ описывает тестовую стратегию для фичи app-shell-menu. Walking Skeleton уже имеет 229 JVM тестов в `shared/feature/app-shell/domain/` — они не дублируются. Phase-01 integration tests покрывают adapter layer и Koin wiring.

---

## Scope

| Слой | Файлы тестов | Тип |
|------|-------------|-----|
| Domain (Walking Skeleton) | `shared/feature/app-shell/domain/src/commonTest/` | JVM (`jvmTest` task) — НЕ трогаем, **кроме**: phase-01 backend-dev адаптирует 9 тестов в `ObserveAppShellStateUseCaseTest.kt` per ADR-LEAD-02 (signature change `invoke(initialState)` → `invoke { initialState }`) |
| Data adapter (KMP) | `shared/feature/app-shell/data/src/jvmTest/` | JVM unit (task `:shared:feature:app-shell:data:jvmTest` — per AC 25a) |
| Presentation integration | `android/feature/app-shell/presentation/src/test/` | JVM unit |
| Koin wiring | `apps/android-next/src/test/` | JVM unit (KoinTest) |
| UI (Compose) | `android/feature/app-shell/presentation/src/androidTest/` | Instrumented (если scope позволяет) |

---

## Fakes

### FakeUserStatsRepository

```kotlin
class FakeUserStatsRepository : UserStatsRepository {
    private val _stats = MutableStateFlow(UserStats.guest())
    var currentStatsResult: UserStats = UserStats.guest()
    var currentStatsCallCount = 0

    override fun observeStats(): Flow<UserStats> = _stats
    override suspend fun currentStats(): UserStats {
        currentStatsCallCount++
        return currentStatsResult
    }

    fun emit(stats: UserStats) { _stats.value = stats }
}
```

**Backing store**: `MutableStateFlow<UserStats>`
**Call tracking**: `currentStatsCallCount` для write methods

### FakeNavigator

```kotlin
class FakeNavigator : Navigator {
    val destinations = mutableListOf<Destination>()
    override fun goTo(destination: Destination) { destinations += destination }
}
```

---

## Test Categories & Coverage Mapping

### 1. Domain Walk — AppShellTransitions (уже покрыты Walking Skeleton)

> ⚠️ Не дублировать. Перечислено для reference.

| Тест | Файл:строка |
|------|------------|
| `onBack` — 4-step FSM | `AppShellTransitionsTest.kt` (domain test) |
| `onSwitchTab` — state save/restore | `AppShellTransitionsTest.kt` |
| `onSelectSection` — visibility guard | `AppShellTransitionsTest.kt` |
| `onOpenDrawer` — SHOP no-op | `AppShellTransitionsTest.kt` |
| `onActiveTabRetap` — POP_TO_ROOT vs NO_OP | `AppShellTransitionsTest.kt` |

### 2. Data Layer — UserStatsRepositoryImpl

Файл: `shared/feature/app-shell/data/src/test/.../UserStatsRepositoryImplTest.kt`

| Тест | Описание |
|------|----------|
| `when dataSource emits stats then repository emits mapped UserStats` | Flow mapping корректен |
| `when dataSource throws then repository emits guest()` | Error recovery через `.catch` |
| `currentStats returns dataSource value on first call` | Single-shot fetch |
| `currentStats offline returns guest()` | Offline fallback |

### 3. Presentation — DefaultRootComponent Integration

Файл: `android/feature/app-shell/presentation/src/test/.../DefaultRootComponentTest.kt`

| Тест | Описание |
|------|----------|
| `when init then state equals AppShellState default with fetched stats` | Cold start state |
| `when initUseCase throws then state fallback to guest` | InitUC failure fallback |
| `when observeStats emits then state userStats updated without navigation change` | ADR-COMP-01 verification |
| `when goTo SwitchTab then state activeTab changes` | Tab switch integration |
| `when goTo Back with drawer open then drawer closes` | Back FSM step 1 |
| `when goTo Back with non-empty backStack then pop` | Back FSM step 2 |
| `when goTo Back on LOCAL root then SystemBack event emitted` | Back FSM step 4 |
| `when onActiveTabRetap on LOCAL with backStack then pop to root` | Re-tap POP_TO_ROOT |
| `when onActiveTabRetap on LOCAL at root then NO_OP outcome` | Re-tap NO_OP (scroll-to-top — UI concern per ADR-COMP-06) |
| `when goTo SelectSection with cross-tab section then auto-switch tab` | Cross-tab section |
| `when goTo OpenDesignCatalog then LOCAL tab with DesignCatalogRoot pushed` | Debug catalog |

**Setup pattern**:
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRootComponentTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeRepo = FakeUserStatsRepository()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createComponent(): DefaultRootComponent {
        return DefaultRootComponent(
            componentContext = TestComponentContext(),
            initUseCase = InitializeAppShellUseCase(fakeRepo),
            navigateUseCase = NavigateUseCase(),
            observeUseCase = ObserveAppShellStateUseCase(fakeRepo),
            retapUseCase = OnTabRetapUseCase(),
        )
        // handleBackUseCase NOT injected — production back via NavigateUseCase(state, Destination.Back) per ADR-COMP-07
        // userStatsRepository NOT a direct dep — injected inside InitializeAppShellUseCase + ObserveAppShellStateUseCase
    }
}
```

> REQUIRES: `TestComponentContext` — JVM stub for Decompose ComponentContext. Verify availability in research or create inline fake (`object TestComponentContext : ComponentContext { ... }` stubbing all members).

### 4. Presentation — ScrollToTopRegistry

Файл: `android/feature/app-shell/presentation/src/test/.../ScrollToTopRegistryTest.kt`

| Тест | Описание |
|------|----------|
| `when hook registered then current(tab) is that hook` | Basic register |
| `when hook unregistered with correct identity then current(tab) is null` | Identity unregister |
| `when hook2 registered then hook1 unregistered then current(tab) is hook2` | Crossfade overlap |
| `when hook1 registered then hook2 registered then hook1 unregistered then current(tab) is hook2` | Strict identity-only unregister |

**Crossfade overlap test** — ключевой:
```kotlin
@Test fun `when hook2 registered before hook1 unregistered then current is hook2`() {
    val registry = ScrollToTopRegistry()
    val tab = Tab.LOCAL
    val hook1 = ScrollToTopHook { /* no-op */ }
    val hook2 = ScrollToTopHook { /* no-op */ }

    registry.register(tab, hook1)
    registry.register(tab, hook2)    // входящий экран регистрируется (overwrites)
    registry.unregister(tab, hook1)  // исходящий экран dispose — identity check: hook2 остаётся

    assertSame(hook2, registry.current(tab))
}
```

### 5. Data — UserStats Mapper

Файл: `shared/feature/app-shell/data/src/test/.../UserStatsMapperTest.kt`

| Тест | Описание |
|------|----------|
| `maps all Firestore fields to UserStats correctly` | Round-trip |
| `maps missing optional fields to defaults` | Partial document |
| `maps negative skill values correctly` | Boundary: negative IDs rule |
| `guest() returned when uid is null` | Unauthenticated case |

### 6. Koin Module Verification

Файл: `app/src/test/.../AppShellKoinModuleTest.kt`

| Тест | Описание |
|------|----------|
| `verify appShellDataModule bindings` | `UserStatsRepository` → `UserStatsRepositoryImpl` |
| `verify appShellPresentationModule use case bindings` | All 4 use cases resolved |
| `verify firebaseModule UserStatsDataSource binding` | Firebase adapter resolved |

```kotlin
@Test fun `verify appShellDataModule bindings`() {
    val app = mockk<Application>(relaxed = true)
    val koin = startKoin {
        androidContext(app)
        modules(appShellDataModule, fakeFirebaseModule)
    }.koin
    koin.get<UserStatsRepository>()  // должен создаться без exception
    stopKoin()
}
```

---

## Edge Cases Checklist

| Edge Case | Covered By |
|-----------|-----------|
| `UserStats.guest()` — cold start без auth | `DefaultRootComponentTest`: init with guest |
| Offline (Firebase exception) | `UserStatsRepositoryImplTest`: catch emits guest |
| Process death recovery failure | `DefaultRootComponentTest`: fallbackState() |
| Negative skill values | `UserStatsMapperTest`: boundary |
| SHOP tab — no drawer open | Walking Skeleton AppShellTransitionsTest |
| Events tab with guest — EmptyRoot | Walking Skeleton VisibilityTest |
| Crossfade ScrollToTop overlap | `ScrollToTopRegistryTest`: identity check |
| `onActiveTabRetap` with `backStack.first()` | Walking Skeleton (domain); DefaultRootComponentTest: POP_TO_ROOT |
| Re-tap on non-active tab | N/A — NavBar only calls `onActiveTabRetap` for selectedItem tap |

---

## Coverage Mapping — Spec Obligations → Test Locations

**Audit requirement**: все spec obligations (30+7 AC, 45 domain scenarios + D1-D3, 5 FSM таблицы, 17 journeys) должны быть покрыты минимум одним test case.

### Acceptance Criteria Coverage (30+7 AC — `0-spec.md:742-820`)

| AC | Описание | Test Location |
|----|----------|---------------|
| AC 1 | Cold start: Local/MyQuests default | `DefaultRootComponentTest`: `when init then state equals AppShellState default with fetched stats` |
| AC 2 | Drawer hamburger open + header UserStats | `DefaultRootComponentTest`: drawer open + `AppShellScreenTest` (instrumented): header rendering |
| AC 3 | Shop — no hamburger, no edge swipe | Walking Skeleton `AppShellTransitionsTest`: onOpenDrawer SHOP no-op + `AppShellScreenTest`: hamburger hidden for SHOP |
| AC 4 | Drawer section switch — close + stack reset | `DefaultRootComponentTest`: goTo SelectSection — activeSection + drawer closed |
| AC 5 | Switch tab with `currentSkill>=3000` → Arena default | Walking Skeleton `VisibilityTest`: visibleSections(INTERNET, skill=3000) firstOrNull = Arena |
| AC 6 | Tab state preservation (Local→Internet→Local) | Walking Skeleton `AppShellTransitionsTest`: onSwitchTab preserves TabState |
| AC 7 | Re-tap with backStack — POP_TO_ROOT | `DefaultRootComponentTest`: `when onActiveTabRetap on LOCAL with backStack then pop to root` |
| AC 8 | Re-tap at root, scrolled — NO_OP + scroll-to-top UI | `DefaultRootComponentTest`: NO_OP outcome + `ScrollToTopRegistryTest`: crossfade identity |
| AC 9 | Re-tap at root, scrollOffset=0 — NO_OP no-op | `DefaultRootComponentTest`: NO_OP outcome + `AppShellScreenTest`: no scroll action |
| AC 10 | SystemBack closes drawer | `DefaultRootComponentTest`: `when goTo Back with drawer open then drawer closes` (FSM step 1) |
| AC 11 | SystemBack empty stack non-LOCAL → switchTab(LOCAL) | Walking Skeleton `HandleBackUseCaseTest` + `DefaultRootComponentTest`: switchTab(LOCAL) |
| AC 12 | SystemBack empty stack LOCAL → RootEvent.SystemBack | `DefaultRootComponentTest`: `when goTo Back on LOCAL root then SystemBack event emitted` |
| AC 13 | UnderConstructionScreen centered layout | `AppShellScreenTest` (instrumented): UI snapshot or Compose preview test |
| AC 14 | SchoolQuizTheme colors/shapes applied | `SchoolQuizThemeTest` (instrumented Compose): color assertion + shape assertion |
| AC 15 | Debug build — DesignCatalog footer action + navigate | `DefaultRootComponentTest`: goTo OpenDesignCatalog + `AppShellScreenTest`: footer rendering (BuildConfig.DEBUG=true) |
| AC 16 | Release build — no DesignCatalog; fallback UnderConstructionScreen | `AppShellScreenTest`: footer absent (BuildConfig.DEBUG=false) + fallback rendering test |
| AC 17 | Crossfade 300ms on transitions | `AppShellScreenTest` (instrumented): animation timing assertion |
| AC 18 | Drawer slide animation | `AppShellScreenTest` (instrumented): DrawerState animation |
| AC 19 | Full unlock visible — 3/6/2 sections | Walking Skeleton `VisibilityTest`: visibleSections with PLAYER + all quals=100 |
| AC 20 | badge: BadgeContent? in NavigationBarItem/NavigationDrawerItem | `AppShellScreenTest`: verify `badge` parameter present in API |
| AC 21 | Cross-tab deep link (Local → InternetSection.Profile) | `DefaultRootComponentTest`: `when goTo SelectSection with cross-tab section then auto-switch tab` |
| AC 22 | Tap active section — only closeDrawer | Walking Skeleton `AppShellTransitionsTest`: onSelectSection active row 3 |
| AC 23 | Programmatic `goTo(OpenDrawer)` non-Shop | `DefaultRootComponentTest`: isDrawerOpen=true on OpenDrawer |
| AC 23a | Guest LOCAL drawer — 3 visible sections | Walking Skeleton `VisibilityTest`: visibleSections(LOCAL, guest) |
| AC 23b | Guest INTERNET drawer — Qualifications, Profile | Walking Skeleton `VisibilityTest`: visibleSections(INTERNET, guest) |
| AC 23c | Guest EVENTS drawer — empty + footer + title fallback | Walking Skeleton `VisibilityTest`: visibleSections(EVENTS, guest)=[] + `AppShellScreenTest`: EmptyRoot rendering |
| AC 23d | currentSkill=3000 — 5 Internet sections (Social hidden) | Walking Skeleton `VisibilityTest`: visibleSections(INTERNET, skill=3000) |
| AC 23e | currentSkill=10000 — all 6 Internet sections | Walking Skeleton `VisibilityTest`: visibleSections(INTERNET, skill=10000) |
| AC 23f | Qualification roles ≥100 + skill=0 — only ActiveEvents | Walking Skeleton `VisibilityTest`: visibleSections(EVENTS, qualified) |
| AC 23g | Deep link hidden section — no-op guard | `DefaultRootComponentTest`: goTo SelectSection(hidden) domain guard |
| AC 24 | `:shared:feature:app-shell:domain:compileCommonMainKotlinMetadata` | Build gate (not a test per se — CI) |
| AC 25 | `:shared:feature:app-shell:domain:jvmTest` — 229 tests green | Walking Skeleton test suite (existing) + ADR-LEAD-02 adapted 9 tests |
| AC 25a | `:shared:feature:app-shell:data:jvmTest` — D1-D3 green | `UserStatsRepositoryImplTest` (D1-D3 mapping below) |
| AC 26 | Feature modules import Navigator only from domain | Compile gate + grep check: `android/feature/*/presentation/build.gradle.kts` no Decompose dep |
| AC 27 | Koin `get<UserStatsRepository>()` = production impl | `KoinModuleWiringTest` (apps/android-next/src/test) |
| AC 28 | `:apps:android-next:assembleDebug` BUILD SUCCESSFUL | Build gate (CI) |
| AC 29 | Manual APK launch — AppShellScreen visible | Manual smoke test (phase-01 acceptance) |
| AC 30 | detekt + ktlint pass | Build gate (if enabled per OQ#7) |

### State Matrix Coverage (5 FSM таблицы — `0-spec.md:609-679`)

| FSM | Cells | Test Location |
|-----|-------|---------------|
| Back-policy FSM (6 rows) | drawer open / pop / LOCAL SystemBack / switchTab(LOCAL) ×3 | Walking Skeleton `HandleBackUseCaseTest` (all 6 rows) + `DefaultRootComponentTest` integration smoke |
| Re-tap FSM (2 rows) | backStack empty → NO_OP; not empty → POP_TO_ROOT | Walking Skeleton `OnTabRetapUseCaseTest` (both rows) |
| Drawer visibility FSM (4 rows) | LOCAL/INTERNET/EVENTS hamburger=true; SHOP=false | `AppShellScreenTest` (instrumented, 4 cells) |
| Tab switch FSM (2 rows) | X→X: onActiveTabRetap; X→Y: save+switch+restore | Walking Skeleton `AppShellTransitionsTest` (both) |
| Section switch FSM (4 rows) | cross-tab / same-tab / same-section drawer open / same-section drawer closed | Walking Skeleton `AppShellTransitionsTest` (4 rows) |
| Section Visibility Rules (12 rows) | LOCAL×3 + INTERNET×6 + EVENTS×2 + negative (DesignCatalog) | Walking Skeleton `VisibilityTest` (all 12 rules via property-based tests) |

### Domain Test Scenarios Coverage (45 scenarios — `0-spec.md:505-597`)

**Scenarios 1-45**: все покрыты Walking Skeleton (229 tests green). Детальный mapping — `AppShellStateTest`, `AppShellTransitionsTest`, `VisibilityTest`, `HandleBackUseCaseTest`, `InitializeAppShellUseCaseTest`, `NavigateUseCaseTest`, `NavStackTest`, `ObserveAppShellStateUseCaseTest` (9 tests adapted per ADR-LEAD-02), `OnTabRetapUseCaseTest`, `PrimaryUserJourneyTest`, `UserStatsTest`, `DrawerFooterActionTest`. Каждый scenario trace-able по имени метода `when <given> then <expected>` к номеру scenario.

### Data-module Scenarios (D1-D3 — `0-spec.md:601-607`)

| Scenario | Test |
|----------|------|
| D1: unauth → guest() | `UserStatsRepositoryImplTest`: `when dataSource empty then currentStats returns guest` |
| D2: Koin singleton | `KoinModuleWiringTest`: `get<UserStatsRepository>() returns same instance` |
| D3: Firestore realtime update | `UserStatsRepositoryImplTest`: `when dataSource emits new stats then observeStats emits mapped` |

### Primary User Journeys Coverage (17 journeys — `0-spec.md:188-318`)

| Journey | Purpose | Test Location |
|---------|---------|---------------|
| 1. Cold start | Default state | `DefaultRootComponentTest`: cold start + Walking Skeleton `InitializeAppShellUseCaseTest` |
| 2. Drawer hamburger → section | Combined flow | `DefaultRootComponentTest`: compound test (drawer + SelectSection) |
| 3. Tab switch state preservation | State saving | Walking Skeleton `AppShellTransitionsTest` + `DefaultRootComponentTest` integration |
| 4. Back sequence (4 steps) | Back FSM | `DefaultRootComponentTest`: 4 separate tests per FSM row |
| 5. Re-tap active tab POP_TO_ROOT | Re-tap | `DefaultRootComponentTest` + Walking Skeleton `OnTabRetapUseCaseTest` |
| 6. Re-tap at root NO_OP + scroll | Re-tap UI | `DefaultRootComponentTest` + `ScrollToTopRegistryTest` |
| 7. Edge swipe open drawer | Gesture | `AppShellScreenTest` (instrumented): snapshotFlow sync |
| 8. Scrim close drawer | Gesture | `AppShellScreenTest` (instrumented): scrim click → CloseDrawer |
| 9. Swipe close drawer | Gesture | `AppShellScreenTest` (instrumented): swipe → CloseDrawer |
| 10. System back closes drawer | Back FSM step 1 | `DefaultRootComponentTest`: drawer open + goTo(Back) |
| 11. Programmatic drawer open | Deep link hook | `DefaultRootComponentTest`: goTo(OpenDrawer) |
| 12. Tap active section — closeDrawer | Edge | Walking Skeleton `AppShellTransitionsTest` row 3 |
| 13. Back на Shop root | Back FSM step 4 | `DefaultRootComponentTest`: SHOP + goTo(Back) |
| 14. Corrupted state fallback | Recovery | `DefaultRootComponentTest`: `when initUseCase throws then state fallback to guest` |
| 14b. Guest → Internet tab | Default section for unlocked | Walking Skeleton `VisibilityTest` + `DefaultRootComponentTest` |
| 14c. Guest → Events tab | Empty state | Walking Skeleton `VisibilityTest` + `DefaultRootComponentTest` |
| 15. Cross-tab deep link | Cross-tab | `DefaultRootComponentTest` + Walking Skeleton `AppShellTransitionsTest` |

---

## Rules

- JVM tests first — `DefaultRootComponent` и use cases не требуют Android runtime.
- `TestComponentContext` — inline fake, не Android-bound.
- Fakes для `UserStatsRepository` и `UserStatsDataSource` — project convention (`testing.md`).
- Flow тесты без Turbine: `.take(1).toList()`, `.value` на StateFlow, `UnconfinedTestDispatcher`.
- Coroutines time control: `StandardTestDispatcher` + `advanceUntilIdle()` для suspend init.
- `test-dev` agent добавляет тесты не меняя production код.
- TDD: тесты пишутся параллельно с production — не отдельной фазой.
