---
date: 2026-04-18
feature: app-shell-menu
reviewer: architect-reviewer
documents-reviewed: [04-testing.md, 06-api-contract.md, 08-storage-model.md]
reference-pack: [0-spec.md, 01-architecture.md, 02-behavior.md, 03-decisions.md, docs/invariants.md]
---

# Architect Review: 04-testing.md / 06-api-contract.md / 08-storage-model.md

## Summary

**Verdict: CONTESTED**
**Counts: 0 BLOCKER, 3 HIGH, 3 MEDIUM, 1 LOW**

All five mandatory grep checks completed. Walking Skeleton domain is clean. Three HIGH findings require resolution before phase-01 implementation starts: two are inconsistent `DefaultRootComponent` constructor shapes across documents (one with `handleBackUseCase` injected when it must not be, one with missing `observeUseCase`), and one is a `ScrollToTopRegistry` API shape mismatch that will produce compile errors. None of the HIGHs introduce incorrect business rules, but any implementor following the contradicted document will write wrong wiring code that fails at runtime or compile-time.

---

## Grep Checklist Results

### Check 1 — Domain purity (`domain-models.md`)

**Path scanned**: `shared/feature/app-shell/domain/src/commonMain/kotlin/`

| Sub-check | Result |
|-----------|--------|
| Android imports in domain (excl. Parcelable/annotation) | CLEAN — 0 matches |
| SDK types in domain (Firebase, Room, Retrofit, etc.) | CLEAN — 0 matches |
| Context/Uri/Bundle/View as params/fields | CLEAN — 0 matches |
| DI annotations (@Inject, @Provides, @Module) | CLEAN — 0 matches |

### Check 2 — Activity/Fragment discipline (`use-cases.md`)

**Path scanned**: `apps/android-next/src/main/java/` (one `MainActivity.kt` found)

| Sub-check | Result |
|-----------|--------|
| Activity/Fragment calling provider/store/manager/service methods | CLEAN — 0 matches |
| Activity/Fragment using Repository as field | CLEAN — 0 matches |
| Activity/Fragment injecting UseCase directly | CLEAN — 0 matches |

Note: `MainActivity.kt` does not yet contain the wiring described in design documents. Check is against existing code only.

### Check 3 — Cross-module boundaries (`clean-architecture.md`)

**Paths scanned**: `shared/feature/app-shell/data/`, `android/feature/app-shell/presentation/`

| Sub-check | Result |
|-----------|--------|
| app-shell feature importing other feature modules directly | CLEAN — 0 matches; modules have no Kotlin sources yet |

### Check 4 — Android lifecycle safety (`lifecycle.md`)

**Path scanned**: `apps/android-next/` Activity/Fragment files

| Sub-check | Result |
|-----------|--------|
| Kill-like actions in onDestroy without isFinishing check | CLEAN — 0 matches |
| Kill-intent in onDestroy | CLEAN — 0 matches |

### Check 5 — DI exclusive binding (`di-patterns.md`)

**Path scanned**: `shared/feature/app-shell/`, `android/feature/app-shell/`

| Sub-check | Result |
|-----------|--------|
| Classes with @Inject constructor | CLEAN — 0 matches |
| @Provides/@Binds for same types | CLEAN — 0 matches |

**N/A note**: Project uses Koin, not Hilt/Dagger. The Dagger-specific `@Inject constructor` + `@Provides` dual-binding rule does not apply. Koin-equivalent (duplicate `single { }` for same type across modules) was checked implicitly through module comparison below.

---

## Findings

### HIGH-1: `handleBackUseCase` injected into `DefaultRootComponent` in 06-api-contract and 04-testing, but explicitly excluded by 01-architecture and ADR-COMP-07

**Severity**: high
**Category**: DI wiring / constructor shape inconsistency
**Location**: `06-api-contract.md:267-276`, `04-testing.md:120-128`

**Evidence**:

`06-api-contract.md:267-276` shows:
```kotlin
single<RootComponent> { (context: ComponentContext) ->
    DefaultRootComponent(
        componentContext = context,
        initializeUseCase = get(),
        navigateUseCase = get(),
        handleBackUseCase = get(),      // <-- present
        onTabRetapUseCase = get(),
        observeStateUseCase = get(),
    )
}
```

`04-testing.md:120-128` `createComponent()` shows:
```kotlin
DefaultRootComponent(
    componentContext = TestComponentContext(),
    initializeUseCase = InitializeAppShellUseCase(fakeRepo),
    navigateUseCase = NavigateUseCase(),
    handleBackUseCase = HandleBackUseCase(),    // <-- present
    onTabRetapUseCase = OnTabRetapUseCase(),
    userStatsRepository = fakeRepo,
)
```

`01-architecture.md:363-373` canonical constructor:
```kotlin
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val initUseCase: InitializeAppShellUseCase,
    private val navigateUseCase: NavigateUseCase,
    private val observeUseCase: ObserveAppShellStateUseCase,
    private val retapUseCase: OnTabRetapUseCase,
    private val userStatsRepository: UserStatsRepository,
) // NO handleBackUseCase
```

`03-decisions.md ADR-COMP-07` Koin factory (explicit note): `// handleBackUseCase: NOT injected — back via NavigateUseCase(state, Destination.Back)`.

`01-architecture.md` Koin graph node `UC3` annotation: `"[domain tests only — NOT wired into DefaultRootComponent; production back path: NavigateUseCase(state, Destination.Back)]"`.

**Why this matters**: ADR-COMP-07 and 01-architecture are explicit and deliberate: `HandleBackUseCase` is wired **only for test isolation** in domain tests. Production back goes through `NavigateUseCase(state, Destination.Back)`. If an implementor follows `06-api-contract` or `04-testing`, they inject a use case that should not be in the constructor, creating a dead parameter and a confusing DI graph. More importantly, the `DefaultRootComponent` class described in 01-architecture will not compile with the constructor shape in 06-api-contract.

**Proposed fix**: Update `06-api-contract.md:267-276` and `04-testing.md:120-128` to remove `handleBackUseCase` from the constructor call sites. The `04-testing` setup should also add `observeStateUseCase` (see HIGH-2).

---

### HIGH-2: `observeStateUseCase` / `observeUseCase` missing from `04-testing` `DefaultRootComponent` construction, `userStatsRepository` present when it should be absent per ADR-COMP-07

**Severity**: high
**Category**: DI wiring / constructor shape inconsistency
**Location**: `04-testing.md:119-129`, `03-decisions.md:197-207`

**Evidence**:

`04-testing.md:119-129` `createComponent()` has no `ObserveAppShellStateUseCase` argument and passes `userStatsRepository = fakeRepo` directly.

`01-architecture.md:363-373` constructor requires `observeUseCase: ObserveAppShellStateUseCase` and `userStatsRepository: UserStatsRepository` as separate parameters.

`03-decisions.md ADR-COMP-07:197-207` Koin factory omits `userStatsRepository` entirely: `observeUseCase = get()` covers stats observation through the use case; `userStatsRepository` is not a direct constructor param in that ADR version.

`01-architecture.md:411` `init {}` comment (ADR-LEAD-02): `observeUseCase { _state.value }.catch { ... }.collect { ... }` — ObserveAppShellStateUseCase is critical to the stats-update pipeline.

**Why this matters**: A `DefaultRootComponent` constructed without `ObserveAppShellStateUseCase` will not subscribe to live stats updates at all. Any test using the `04-testing.md` `createComponent()` factory will not exercise the ADR-LEAD-02 provider-lambda path, meaning the stale-closure fix is untested at the integration level. This is the most important phase-01 integration behaviour and the test setup does not cover it.

**Proposed fix**: `04-testing.md createComponent()` must include `observeStateUseCase = ObserveAppShellStateUseCase(fakeRepo)` (or equivalent). The `userStatsRepository = fakeRepo` direct injection should be removed if the canonical constructor (01-arch) does not expose it — or retained only if 01-arch is authoritative and ADR-COMP-07 is the one that must be updated to match.

**Open Question for lead**: The `userStatsRepository` parameter discrepancy between `01-arch` (includes it) and `ADR-COMP-07` (omits it) must be resolved into a single canonical constructor shape before phase-01 starts. Both documents cannot be correct simultaneously.

---

### HIGH-3: `ScrollToTopRegistry` API shape in `04-testing` is incompatible with `06-api-contract` and `01-architecture`

**Severity**: high
**Category**: API surface mismatch / compile-time failure
**Location**: `04-testing.md:147-158`, `06-api-contract.md:332-335`, `01-architecture.md:488-498`

**Evidence**:

`06-api-contract.md:332-335`:
```kotlin
class ScrollToTopRegistry {
    fun register(tab: Tab, hook: ScrollToTopHook)
    fun unregister(tab: Tab, hook: ScrollToTopHook)
    fun current(tab: Tab): ScrollToTopHook?
}
```

`01-architecture.md:488-498` implementation:
```kotlin
fun register(tab: Tab, hook: ScrollToTopHook) { hooks[tab] = hook }
fun unregister(tab: Tab, hook: ScrollToTopHook) { if (hooks[tab] === hook) hooks.remove(tab) }
fun current(tab: Tab): ScrollToTopHook? = hooks[tab]
```

`04-testing.md:147-158` crossfade test:
```kotlin
val registry = ScrollToTopRegistry()
registry.register(hook1)           // <-- no Tab param
registry.register(hook2)
registry.unregister(hook1)         // <-- no Tab param
assertSame(hook2, registry.currentHook)   // <-- property, not fun current(tab)
```

The 04-testing test calls `register(hook)` with **one argument** and accesses `registry.currentHook` as a property. The authoritative API (01-arch + 06-api-contract) requires **two arguments** `register(tab, hook)` and uses `current(tab)` as a function. These are incompatible method signatures: a test written against the 04-testing shape will fail to compile against the 01-arch/06-api-contract implementation.

**Why this matters**: The crossfade overlap test in `04-testing` is the only test that verifies the identity-aware unregister behaviour (spec FR #9 / ADR-COMP-06 correctness). If this test is written with the wrong API shape, it will not compile, and the critical crossfade race condition will remain unverified.

**Proposed fix**: Update `04-testing.md:147-158` to use the two-argument API consistent with 06-api-contract and 01-architecture. All four test cases in the ScrollToTopRegistry section need the `tab: Tab` parameter added. Also update the assertion to `assertSame(hook2, registry.current(activeTab))`.

---

### MEDIUM-1: `08-storage-model.md` documents `_state` type as `MutableValue<AppShellState>` (Decompose), but `01-architecture.md` L3 specifies `MutableStateFlow<AppShellState>`

**Severity**: medium
**Category**: Type surface inconsistency / stale doc
**Location**: `08-storage-model.md:17`, `01-architecture.md:378`

**Evidence**:

`08-storage-model.md:11,17`:
> Вся навигационная state хранится in-memory в `MutableValue<AppShellState>` внутри `DefaultRootComponent`.
> `AppShellState | MutableValue<AppShellState>` (Decompose)

`01-architecture.md:378`:
> `_state | MutableStateFlow<AppShellState> | Pure coroutines; override val appShellState = _state.asStateFlow()`

`ADR-0011` (in `01-architecture.md`) explicitly chose `Flow<AppShellState>` (not `Value<>`) for the `RootComponent` interface to keep domain pure from Decompose. `DefaultRootComponent._state` consequently uses `MutableStateFlow`, not `MutableValue`.

**Why this matters**: Storage model is the reference document for how data is held in memory. If it documents `MutableValue<AppShellState>`, a reader will believe there is a Decompose dependency in the state container, which contradicts ADR-0011 (domain purity) and the actual implementation direction. This causes confusion but does not block compilation as long as 01-arch is followed.

**Proposed fix**: Update `08-storage-model.md:17` row to read `MutableStateFlow<AppShellState> (kotlinx.coroutines)` and remove the `(Decompose)` annotation. Add note: "Pure coroutines — not Decompose `Value<>`. See ADR-0011."

---

### MEDIUM-2: `06-api-contract.md` `appShellDataModule` places `UserStatsDataSource` in `shared/feature/app-shell/data`, but `03-decisions.md` OQ-COMP-5 resolves it to `shared/core/stats/`

**Severity**: medium
**Category**: Module boundary inconsistency / OQ-COMP-5 not reflected
**Location**: `06-api-contract.md:257-260`, `03-decisions.md` OQ-COMP-5 row, `01-architecture.md` ADR-0011 OQ-COMP-5 section

**Evidence**:

`06-api-contract.md:257-260`:
```kotlin
val appShellDataModule = module {
    single<UserStatsDataSource> { FirebaseUserStatsDataSource(get()) }
    single<UserStatsRepository> { UserStatsRepositoryImpl(get()) }
}
```
This places the `UserStatsDataSource` binding inside `appShellDataModule` (which lives in `shared/feature/app-shell/data`).

`03-decisions.md` OQ-COMP-5: `RESOLVED — UserStatsDataSource переносится в shared/core/stats/`.

`01-architecture.md` ADR-0011 OQ-COMP-5: "Рекомендация high-level: `UserStatsDataSource` переносится в `shared/core/stats/`. Это делает `platform/firebase` зависимым от `shared/core/` — допустимо per ADR-0001."

If `UserStatsDataSource` moves to `shared/core/stats/`, the `appShellDataModule` should NOT bind it (it would be in a `coreStatsModule` or `firebasePlatformModule` that depends on `shared/core/stats/`, not on `shared/feature/app-shell/data`).

**Why this matters**: If phase-01 implements `appShellDataModule` per `06-api-contract`, the `UserStatsDataSource` interface will be in the wrong module, violating the resolved ADR-0001 boundary. The `platform/firebase` module would then depend on a feature module, which is the exact violation OQ-COMP-5 was resolving.

**Proposed fix**: Update `06-api-contract.md` to reflect the OQ-COMP-5 resolution: remove `single<UserStatsDataSource>` from `appShellDataModule`; document that `UserStatsDataSource` lives in `shared/core/stats/` and the binding is in `firebasePlatformModule`. Also update the `appShellDataModule` description at `06-api-contract.md:254`.

---

### MEDIUM-3: `06-api-contract.md` registers `RootComponent` as `single` but `ADR-COMP-07` explicitly requires `factory`

**Severity**: medium
**Category**: DI scope mismatch — potential lifecycle bug
**Location**: `06-api-contract.md:267`, `03-decisions.md ADR-COMP-07`

**Evidence**:

`06-api-contract.md:267`: `single<RootComponent> { (context: ComponentContext) -> DefaultRootComponent(...) }`

`03-decisions.md ADR-COMP-07`: Decision is `factory { (ctx: ComponentContext) -> ... }`, rationale: "singleton would be shared between Activities at configuration change — ComponentContext lifetime mismatch."

`01-architecture.md` Koin graph node: `"factory(ComponentContext): DefaultRootComponent"`.

**Why this matters**: A `single<RootComponent>` binding means the first `get<DefaultRootComponent>(parametersOf(ctx))` call creates an instance with a specific `ComponentContext` (tied to the Activity lifecycle). On a subsequent call (e.g., after process death restart without Koin `stopKoin()`), the old `ComponentContext`-captured instance would be returned. This is the exact memory-leak / stale-lifecycle scenario ADR-COMP-07 ruled out. In practice, Koin `factory` with `parametersOf` is the correct pattern for Activity-scoped Decompose components.

**Proposed fix**: Change `06-api-contract.md:267` from `single<RootComponent>` to `factory<RootComponent>` (or remove the `single` alias entirely, keeping only the explicit `factory { ... }` form consistent with ADR-COMP-07).

---

### LOW-1: `04-testing.md` `ObserveAppShellStateUseCase` tests do not note the ADR-LEAD-02 signature delta; test coverage table claims Walking Skeleton is "не дублировать" but does not reference ADR-LEAD-02 adaptation requirement

**Severity**: low
**Category**: Documentation completeness / phase-01 awareness gap
**Location**: `04-testing.md:61-72`, `01-architecture.md` Phase-01 Integration Notes

**Evidence**:

`01-architecture.md` Phase-01 Integration Notes table:
> `ObserveAppShellStateUseCaseTest.kt`: adapt 9 existing tests: call site `invoke(initialState)` → `invoke { initialState }`; verify stale closure is absent.

`04-testing.md:61-72` covers domain tests as "не дублировать" without mentioning that 9 existing Walking Skeleton tests must be adapted in phase-01 per ADR-LEAD-02.

Walking Skeleton (`ObserveAppShellStateUseCase.kt:29`) still uses `invoke(initialState: AppShellState)` — the old signature. The 13 test files in `commonTest` include `ObserveAppShellStateUseCaseTest.kt` with 9 tests calling the old API. These will break when phase-01 changes the signature per ADR-LEAD-02.

**Why this matters**: A `test-dev` reading only `04-testing.md` will not know that Walking Skeleton tests need updating in phase-01. The scope section says "НЕ трогаем" for domain tests but this conflicts with the explicit ADR-LEAD-02 mandate. Without this note, the Walking Skeleton may be broken by phase-01 without intent.

**Proposed fix**: Add a note in `04-testing.md` Scope table, Domain row: "НЕ трогаем — кроме: phase-01 backend-dev адаптирует 9 тестов в `ObserveAppShellStateUseCaseTest.kt` per ADR-LEAD-02 (signature `invoke(initialState)` → `invoke { initialState }`)."

---

## Deviations from Design

### Deviation A — `DefaultRootComponent` constructor canonical shape is inconsistent across design documents

The authoritative shape is in `01-architecture.md:363-373` (L3 class diagram, constructor block). Three other documents contain differing shapes:

| Document | handleBackUseCase | observeUseCase | userStatsRepository | Note |
|----------|-------------------|----------------|---------------------|------|
| `01-architecture.md` L3 | absent | present (observeUseCase) | present | **Canonical** |
| `03-decisions.md` ADR-COMP-07 | absent | present (observeUseCase) | absent | Agrees on UC, disagrees on repo |
| `06-api-contract.md` | **present** (wrong) | present (observeStateUseCase) | absent | Two errors vs canonical |
| `04-testing.md` createComponent | **present** (wrong) | absent (missing) | present | Two errors vs canonical |

Until these are reconciled, no document alone is a safe source of truth for backend-dev.

### Deviation B — `08-storage-model.md` not updated after ADR-0011 changed `_state` type from `MutableValue` to `MutableStateFlow`

`08-storage-model.md` was written before ADR-0011 finalized the interface/implementation split. ADR-0011 moved `DefaultRootComponent._state` to `MutableStateFlow<AppShellState>` to keep domain free of Decompose. The storage doc still shows `MutableValue<AppShellState> (Decompose)`, which contradicts the final design.

### Deviation C — `06-api-contract.md` `appShellDataModule` not updated with OQ-COMP-5 resolution

`03-decisions.md` records OQ-COMP-5 as RESOLVED with `UserStatsDataSource` moving to `shared/core/stats/`. `06-api-contract.md` `appShellDataModule` still shows `single<UserStatsDataSource> { FirebaseUserStatsDataSource(get()) }` inside the data module, which would violate the ADR-0001 module boundary once OQ-COMP-5 is implemented.

---

## Positive Notes

- Domain Walking Skeleton is architecturally clean: 0 Android imports, 0 SDK types, 0 DI annotations across all 26 production files and 13 test files. Invariant #1 fully satisfied at code level.
- `FakeUserStatsRepository` in `04-testing.md` matches the canonical project fake convention: `MutableStateFlow` backing, call tracking, no test-framework dependencies. Consistent with `.claude/rules/testing.md`.
- `08-storage-model.md` correctly and explicitly documents that no Room entities, no `@Entity`, no `@Dao`, no migrations exist for this feature. ADR-LEAD-01 (`serializer=null`) is reflected accurately with the correct process-death fallback flow described.
- `06-api-contract.md` `Navigator` interface shape and `Destination` sealed shape are consistent with the Walking Skeleton `Destination.kt` source (verified: all 6 variants present with matching signatures at `shared/feature/app-shell/domain/.../model/Destination.kt:9-35`).
- The `UserStatsRepository` contract in `06-api-contract.md` (`observeStats(): Flow<UserStats>` + `suspend currentStats(): UserStats`) matches the Walking Skeleton `UserStatsRepository.kt` exactly (verified at `repository/UserStatsRepository.kt:15-31`). Invariant #3 cross-feature isolation is correctly maintained: `Navigator` + `Destination` remain the sole cross-feature surface.

---

## Remaining Architectural Debt

1. **OQ-COMP-4** (Koin parametrized factory DI entry point convention) is still open in `03-decisions.md`. `06-api-contract.md:296-302` assumes `get<DefaultRootComponent>(parametersOf(ctx))` in `MainActivity`. If the project-level DI convention differs, this will need update.
2. **`TestComponentContext`** required by `04-testing.md:132` — "Verify availability in research or create inline fake" is unresolved. This is a phase-01 blocker for test-dev, not a design doc issue, but it should be resolved in the plan before implementation.
3. **Walking Skeleton test count claim**: `0-spec.md:807` states "alle 229 domain tests" and the actual count of `@Test` annotations in `commonTest` is confirmed as 229. However, `ObserveAppShellStateUseCase.kt:29` still uses the old `invoke(initialState)` signature. If phase-01 changes the signature per ADR-LEAD-02 without also updating the 9 existing tests atomically, the build will break. The Phase-01 Integration Notes table in `01-architecture.md` accounts for this but `04-testing.md` does not.
