---
date: 2026-04-18
feature: app-shell-menu
reviewer: architect-reviewer
pass: 2 (verification)
documents-reviewed: [04-testing.md, 06-api-contract.md, 08-storage-model.md]
reference-pack: [0-spec.md, 01-architecture.md, 03-decisions.md, docs/invariants.md]
based-on: docs/features/app-shell-menu/review/architect-review-04-06-08.md (pass 1)
---

# Architect Review: Pass 2 — Verification of Pass 1 Findings

## Summary

**Verdict: PASS**
**Pass 1 findings: 3 HIGH + 3 MEDIUM + 1 LOW — всего 7**
**Closed: 7/7 (все findings из pass 1)**
**New findings: 1 LOW (ADR-COMP-07 naming drift в 03-decisions.md vs canonical constructor)**

Все семь findings из pass 1 закрыты в обновлённых документах. `03-decisions.md ADR-COMP-06` тоже обновлён до двухаргументного API с `MutableMap<Tab, ScrollToTopHook>` — потенциальный MEDIUM-NEW из pass-1 Positive Notes устранён. Единственное новое наблюдение: `ADR-COMP-07` в `03-decisions.md:207,210` использует имена параметров `initializeUseCase` / `onTabRetapUseCase`, тогда как canonical constructor в `01-architecture.md:368,371` и `06-api-contract.md:271,273` используют `initUseCase` / `retapUseCase`. Расхождение только в Koin call-site snippet в ADR, не в определении класса — runtime impact отсутствует, но может ввести в заблуждение backend-dev при именовании параметров. Это LOW.

Domain Walking Skeleton остаётся чистым по всем 4 grep checks. Нет новых HIGH или BLOCKER. Фаза-01 может стартовать.

---

## Grep Checklist Results

### Check 1 — Domain purity (`domain-models.md`)

**Path scanned**: `shared/feature/app-shell/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/app_shell/domain/`

| Sub-check | Команда | Результат |
|-----------|---------|-----------|
| Android imports in domain (excl. Parcelable/annotation) | `grep -rE "^import (android\|androidx)\."` | **CLEAN — 0 matches** |
| SDK types in domain (Firebase, Room, Retrofit, etc.) | `grep -rE "^import (io\.livekit\|com\.google\.firebase\|retrofit2\|okhttp3\|androidx\.room\|...)"` | **CLEAN — 0 matches** |
| Context/Uri/Bundle/View as params/fields | `grep -rE "\b(Context\|Uri\|Bundle\|Intent\|View\|Activity\|Fragment)\s*[:,)]"` | **CLEAN — 0 matches** |
| DI annotations in domain | `grep -rE "@(Inject\|Provides\|Module\|Singleton\|HiltAndroidApp)"` | **CLEAN — 0 matches** |

### Check 2 — Activity/Fragment discipline (`use-cases.md`)

**Path scanned**: `apps/android-next/src/main/` (единственный существующий файл: `MainActivity.kt`)

| Sub-check | Результат |
|-----------|-----------|
| Activity/Fragment вызывает provider/store/manager/service методы | **CLEAN — 0 matches** |
| Activity/Fragment использует Repository как поле | **CLEAN — 0 matches** |
| Activity/Fragment инжектит UseCase напрямую | **CLEAN — 0 matches** |

Note: `android/feature/app-shell/presentation/` Kotlin sources не существуют — phase-01 не начата. Проверка по существующему коду.

### Check 3 — Cross-module boundaries (`clean-architecture.md`)

**Paths scanned**: `shared/feature/app-shell/domain/`, `shared/feature/app-shell/data/`, `android/feature/app-shell/presentation/`

| Sub-check | Результат |
|-----------|-----------|
| app-shell domain импортирует другие feature-модули (quiz, minigame, economy, internet, local, qualification) | **CLEAN — 0 matches** |
| app-shell data/presentation импортирует другие feature-модули | **CLEAN — 0 matches; Kotlin sources в data/presentation отсутствуют** |

### Check 4 — Android lifecycle safety (`lifecycle.md`)

**Path scanned**: `apps/android-next/src/main/` Activity/Fragment files

| Sub-check | Результат |
|-----------|-----------|
| Kill-like actions в onDestroy без isFinishing check | **CLEAN — 0 matches** |
| Kill-intent в onDestroy | **CLEAN — 0 matches** |

### Check 5 — DI exclusive binding (`di-patterns.md`)

**N/A**: Проект использует Koin, не Hilt/Dagger. Правило `@Inject constructor` + `@Provides` dual-binding не применяется. Koin-специфичная проверка (дублирующие `single { }` для одного типа) — module files в feature dirs не созданы (pre-phase-01). Дизайн-уровневая проверка через документы: `UserStatsRepository` — ровно один `single` в `appShellDataModule`; `UserStatsDataSource` — ровно один `single` в `firebasePlatformModule`; `RootComponent` — `factory`, не `single`.

| Sub-check | Результат |
|-----------|-----------|
| Классы с `@Inject constructor` | **CLEAN — 0 matches (Koin project)** |
| `@Provides`/`@Binds` cross-check | **N/A — Koin, не Hilt/Dagger** |
| Koin duplicate `single { }` для одного типа (design-level) | **CLEAN — дублей не обнаружено** |

---

## Findings Verification (по каждому finding из pass 1)

### HIGH-1: handleBackUseCase в constructor — CLOSED

**Проверялось**: `06-api-contract.md` Koin factory (строки 266–285), `04-testing.md createComponent()` (строки 119–129), `01-architecture.md` конструктор (строки 363–375).

**Evidence закрытия**:

- `06-api-contract.md:274`: `// handleBackUseCase NOT injected — production back via navigateUseCase(state, Destination.Back) per ADR-COMP-07` — аргумент отсутствует в factory вызове. `HandleBackUseCase()` зарегистрирован отдельно как `factory { HandleBackUseCase() }` с комментарием `// domain tests only — NOT wired into DefaultRootComponent`.
- `04-testing.md:127`: `// handleBackUseCase NOT injected — production back via NavigateUseCase(state, Destination.Back) per ADR-COMP-07` — аргумент отсутствует в `createComponent()`.
- `01-architecture.md:373`: аналогичный комментарий, параметра нет в конструкторе.

Все три документа согласованы: `handleBackUseCase` не передаётся в `DefaultRootComponent`.

**Verdict: CLOSED**

---

### HIGH-2: observeUseCase missing + userStatsRepository direct — CLOSED

**Проверялось**: `04-testing.md:119–129`, `06-api-contract.md:266–277`, `01-architecture.md:363–375`.

**Evidence закрытия**:

- `04-testing.md:124`: `observeUseCase = ObserveAppShellStateUseCase(fakeRepo)` — присутствует.
- `04-testing.md:128`: `// userStatsRepository NOT a direct dep — injected inside InitializeAppShellUseCase + ObserveAppShellStateUseCase` — прямой аргумент удалён.
- `06-api-contract.md:272`: `observeUseCase = get()` — присутствует.
- `06-api-contract.md:275`: `// userStatsRepository NOT a direct dep — ...` — прямого аргумента нет.
- `01-architecture.md:363–375`: canonical constructor содержит ровно 5 параметров: `componentContext`, `initUseCase`, `navigateUseCase`, `observeUseCase`, `retapUseCase`; `userStatsRepository` не в конструкторе.

Все три документа согласованы с canonical shape из `01-architecture.md:363–375`.

**Verdict: CLOSED**

---

### HIGH-3: ScrollToTopRegistry API shape — CLOSED

**Проверялось**: `04-testing.md:148–158`, `06-api-contract.md:333–337`, `01-architecture.md:488–499`, `03-decisions.md:171–179`.

**Evidence закрытия**:

- `04-testing.md:154–158`: тест использует `registry.register(tab, hook1)`, `registry.register(tab, hook2)`, `registry.unregister(tab, hook1)`, `assertSame(hook2, registry.current(tab))` — двухаргументный API, `current(tab)` как функция.
- `06-api-contract.md:334–336`: `fun register(tab: Tab, hook: ScrollToTopHook)`, `fun unregister(tab: Tab, hook: ScrollToTopHook)`, `fun current(tab: Tab): ScrollToTopHook?`.
- `01-architecture.md:492–499`: implementation body двухаргументный с `hooks = mutableMapOf<Tab, ScrollToTopHook>()`.
- `03-decisions.md:170–179` (ADR-COMP-06 Decision block): **обновлён** — содержит `private val hooks = mutableMapOf<Tab, ScrollToTopHook>()`, `fun register(tab: Tab, hook: ScrollToTopHook)`, `fun unregister(tab: Tab, hook: ScrollToTopHook)`, `fun current(tab: Tab): ScrollToTopHook?`. Старый `_currentHook` одноаргументный снипет заменён.

Все четыре документа согласованы на двухаргументном API.

**Verdict: CLOSED**

---

### MEDIUM-1: MutableValue → MutableStateFlow — CLOSED

**Проверялось**: `08-storage-model.md:11–18`.

**Evidence закрытия**:

- `08-storage-model.md:11`: «хранится in-memory в `MutableStateFlow<AppShellState>` (pure coroutines) внутри `DefaultRootComponent`» — `MutableValue` не упоминается.
- `08-storage-model.md:17` (таблица): `MutableStateFlow<AppShellState>` с аннотацией `(kotlinx.coroutines — не Decompose Value<>; см. ADR-0011)` — явное исключение Decompose зафиксировано.

**Verdict: CLOSED**

---

### MEDIUM-2: UserStatsDataSource location — CLOSED

**Проверялось**: `06-api-contract.md:257–295`.

**Evidence закрытия**:

- `06-api-contract.md:258`: `// UserStatsDataSource lives in shared/core/stats/ — binding in firebasePlatformModule (per OQ-COMP-5 resolution)`.
- `06-api-contract.md:259`: `single<UserStatsRepository> { UserStatsRepositoryImpl(get()) }` — `appShellDataModule` содержит только `UserStatsRepository` binding; `UserStatsDataSource` отсутствует.
- `06-api-contract.md:292` (`firebasePlatformModule`): `single<UserStatsDataSource> { FirebaseUserStatsDataSource(Firebase.firestore) }` — binding в правильном модуле.

**Verdict: CLOSED**

---

### MEDIUM-3: RootComponent single vs factory — CLOSED

**Проверялось**: `06-api-contract.md:267`.

**Evidence закрытия**:

- `06-api-contract.md:267`: `factory<RootComponent> { (context: ComponentContext) ->  // factory, не single — per ADR-COMP-07 (Activity-scoped ComponentContext)` — явный `factory`, не `single`.

**Verdict: CLOSED**

---

### LOW-1: ADR-LEAD-02 adaptation note в Scope table — CLOSED

**Проверялось**: `04-testing.md:17` (Domain row в Scope table).

**Evidence закрытия**:

- `04-testing.md:17`: Domain row содержит: «JVM (`jvmTest` task) — НЕ трогаем, **кроме**: phase-01 backend-dev адаптирует 9 тестов в `ObserveAppShellStateUseCaseTest.kt` per ADR-LEAD-02 (signature change `invoke(initialState)` → `invoke { initialState }`)» — явная ссылка на ADR-LEAD-02, число тестов и паттерн изменения указаны.

**Verdict: CLOSED**

---

## New Findings

### LOW-NEW-1: ADR-COMP-07 (`03-decisions.md:207,210`) использует имена параметров `initializeUseCase` / `onTabRetapUseCase`, расходящиеся с canonical `initUseCase` / `retapUseCase`

**Severity**: low
**Location**: `docs/features/app-shell-menu/03-decisions.md:207,210`

**Problem**: Koin factory snippet в `ADR-COMP-07`:
```kotlin
DefaultRootComponent(
    componentContext = ctx,
    initializeUseCase = get(),    // line 207
    navigateUseCase = get(),
    observeUseCase = get(),
    onTabRetapUseCase = get(),    // line 210
    ...
)
```

Canonical constructor в `01-architecture.md:368,371`:
```kotlin
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val initUseCase: InitializeAppShellUseCase,        // line 368
    private val navigateUseCase: NavigateUseCase,
    private val observeUseCase: ObserveAppShellStateUseCase,
    private val retapUseCase: OnTabRetapUseCase,               // line 371
)
```

`06-api-contract.md:271,273` согласован с canonical: `initUseCase = get()`, `retapUseCase = get()`.

**Почему важно**: Расхождение только в Koin call-site snippet в ADR, не в определении класса — runtime impact отсутствует. Однако backend-dev, читающий ADR-COMP-07, может выбрать имена параметров `initializeUseCase` / `onTabRetapUseCase` при написании `DefaultRootComponent` конструктора. Это приведёт к несоответствию с `06-api-contract.md` и с named argument вызовами из Koin factory в том же документе. Минимальное влияние: Koin factory использует `get()` без named args, поэтому call-site компилируется при любом имени параметра.

**Предлагаемое исправление**: Обновить `03-decisions.md:207,210` для согласованности с canonical:
- `initializeUseCase = get()` → `initUseCase = get()`
- `onTabRetapUseCase = get()` → `retapUseCase = get()`

---

## Remaining Architectural Debt (неблокирующий)

1. **`ObserveAppShellStateUseCase.kt:29`** (Walking Skeleton) сохраняет старую сигнатуру `invoke(initialState: AppShellState)`. ADR-LEAD-02 предписывает изменить на `invoke(currentStateProvider: () -> AppShellState)` в phase-01. Это ожидаемое состояние до начала фазы — не finding review, зафиксировано в `01-architecture.md` Phase-01 Integration Notes и в `04-testing.md:17`.

2. **OQ-COMP-4** (`03-decisions.md`) остаётся открытым: `get<DefaultRootComponent>(parametersOf(ctx))` в `MainActivity` vs проектная DI-конвенция. Не блокирует design documents, блокирует только implementation plan.

3. **Navigator Koin binding** (`06-api-contract.md:278`): `single<Navigator> { NavigatorImpl(get<RootComponent>()) }` вызывает `get<RootComponent>()` без `parametersOf` для parametrized factory. По `01-architecture.md:383` `NavigatorImpl` создаётся в `DefaultRootComponent.init {}` как `NavigatorImpl(this)`, а не через Koin. Противоречие между двумя подходами — кандидат для уточнения в plan фазы.

---

## Positive Notes

- `03-decisions.md ADR-COMP-06` обновлён полностью: двухаргументный API с `MutableMap<Tab, ScrollToTopHook>` совпадает с `01-architecture.md` и `06-api-contract.md`. Потенциальный MEDIUM из pass-1 observation устранён превентивно.
- `04-testing.md createComponent()` (строки 119–129) точно соответствует canonical constructor из `01-architecture.md:363–375`: ровно 5 аргументов, `ObserveAppShellStateUseCase(fakeRepo)` присутствует, `userStatsRepository` прямого поля нет, `handleBackUseCase` отсутствует с пояснительным комментарием.
- `08-storage-model.md` исправлен с явным упоминанием ADR-0011 и явным `(не Decompose Value<>)` — документ больше не вводит в заблуждение относительно Decompose зависимости в state container.
- `06-api-contract.md appShellDataModule` теперь содержит только `UserStatsRepository` binding; `UserStatsDataSource` binding корректно перенесён в `firebasePlatformModule` — модульная граница ADR-0001 соблюдена.
- Domain Walking Skeleton остаётся чистым по всем 4 grep checks (Checks 1a–1d): 0 Android imports, 0 SDK types, 0 Android framework types в параметрах, 0 DI аннотаций. Invariant #1 полностью соблюдён.
