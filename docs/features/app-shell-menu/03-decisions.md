---
date: 2026-04-18
feature: app-shell-menu
authors: [architect-component]
---

# Component-Level Decisions: App Shell Menu

Документ содержит ADR уровня компонентов (C4 L3). High-level ADR (`01-architecture.md` ADR-0001–ADR-0010) — зона architect-high-level.

---

## ADR-COMP-01: Fix ObserveAppShellStateUseCase stale closure via provider lambda (User-Approved Walking Skeleton delta)

**Status**: Accepted (User-Approved Domain Signature Change, 2026-04-18)

**Context**:
`ObserveAppShellStateUseCase.invoke(initialState)` реализован как:
```kotlin
userStatsRepository.observeStats().map { stats -> initialState.copy(userStats = stats) }
```
Параметр `initialState` захватывается в closure в момент подписки. После навигационного перехода каждый stats emit перезаписывает текущую навигационную позицию старым `initialState` (stale closure bug). `01-architecture.md` документирует это как "stale navigation" проблему.

Изначально предлагался вариант "прямой collect без UC" — но user отверг его: он выбрасывает use case из production path, нарушая spec NFR который называет `ObserveAppShellStateUseCase` runtime updater-ом.

**Decision**:
Изменить сигнатуру Walking Skeleton use case (delta):
```kotlin
// shared/feature/app-shell/domain/.../use_case/ObserveAppShellStateUseCase.kt
operator fun invoke(currentStateProvider: () -> AppShellState): Flow<AppShellState> =
    observeStats().map { stats -> currentStateProvider().copy(userStats = stats) }
```
`DefaultRootComponent` передаёт provider читающий `_state.value` в момент каждого emit:
```kotlin
scope.launch {
    observeStatsUC(currentStateProvider = { _state.value })
        .catch { emit(AppShellState.fallback(UserStats.guest())) }
        .collect { newState -> _state.update { newState } }
}
```
`currentStateProvider()` вызывается в момент каждого stats emit — всегда читает актуальный `_state`, stale navigation невозможна.

**Phase-01 scope** (зона backend-dev):
- Обновить `ObserveAppShellStateUseCase.kt`: `invoke(initialState)` → `invoke(currentStateProvider: () -> AppShellState)`
- Обновить 9 тестов в `ObserveAppShellStateUseCaseTest.kt`: изменить сигнатуру вызова и добавить тест "navigation state preserved across stats emits"
- `DefaultRootComponent` использует UC через provider lambda (не прямой collect)

**Alternatives Considered**:

| Вариант | Причина отказа |
|---------|---------------|
| Прямой `observeStats().collect { stats -> _state.update { it.copy(userStats = stats) } }` без UC | User отверг: выбрасывает `ObserveAppShellStateUseCase` из production path; нарушает spec, который называет UC runtime updater-ом |
| Переписать UC принимая `StateFlow<AppShellState>` | Dependency direction inversion: use case принимал бы presentation-owned `MutableStateFlow<AppShellState>` как аргумент — domain зависел бы от presentation state container; нарушение `clean-architecture.md` dependency flow (`presentation → domain`, не наоборот). Примечание: `StateFlow` — kotlinx.coroutines, не androidx; `domain-models.md` его не запрещает. |
| `ObserveAppShellStateUseCase(initialState)` как есть | Stale closure: навигационные переходы перезатираются при каждом stats emit — исходная проблема |

---

## ADR-COMP-02: Decompose StateKeeper — `serializer = null` для дочерних ChildStack

**Status**: Accepted (User-Approved Deviation from spec NFR #2, 2026-04-18)

**Context**:
Decompose `childStack(serializer = serializer<C>(), ...)` включает process death recovery через StateKeeper. Это требует что конфигурационный тип `C` является `@Serializable`. При десериализации failure (schema mismatch после обновления) — runtime exception или corrupted state.

Codex Realist pass 2 flagged spec conflict: spec NFR #2 требует state-saving, `serializer = null` отключает его. User explicitly approved deviation.

**Decision**:
- `DefaultRootComponent` использует `serializer = null` для всех 4 дочерних ChildStack (`localNav`, `internetNav`, `eventsNav`, `shopNav`). Process death для sub-stacks не сохраняется.
- При process death `DefaultRootComponent.init {}` выполняет `initUseCase()` заново — получает ActualState из UserStats. Навигационные позиции внутри tab сбрасываются до default — допустимо (User-Approved Deviation per ADR-LEAD-01; spec не имеет FR #23, точной нормы top-level tab restoration достаточно для MVP).
- Fallback: если initUseCase() throws → `fallbackState()` = `AppShellState.default(guest())`.

**Alternatives Considered**:

| Вариант | Причина отказа |
|---------|---------------|
| Полная @Serializable сериализация всех TabConfig + NavStack | Каждое изменение TabConfig sealed hierarchy = potential deserialization break при apk update; spec не требует точного восстановления sub-stack на cold start after death |
| StateKeeper с версионированием (custom serializer + version field) | Значительная сложность; нет прецедента в проекте (`.claude/rules/testing.md` не описывает migration test для Decompose state) |

---

## ADR-COMP-03: RootComponent — presentation layer, не domain

**Status**: Accepted — Resolved by ADR-0011 (interface RootComponent в domain/commonMain с `StateFlow<AppShellState>`; `DefaultRootComponent` в presentation)

**Context**:
`0-spec.md` NFR #1 упоминает "RootComponent в domain/commonMain". Walking Skeleton (229 тестов) сгенерирован в `shared/feature/app-shell/domain/` без RootComponent — только pure domain logic.

**Decision**:
`DefaultRootComponent` располагается в `android/feature/app-shell/presentation/`. Причины:
1. `DefaultRootComponent` принимает `ComponentContext` (Decompose API) — Android/KMP framework зависимость.
2. `domain-models.md` запрещает `androidx.*` и third-party SDK types в domain signatures.
3. Walking Skeleton контракт (Invariant #6) явно говорит что phase-01 — интеграция, не перезапись.
4. Decompose не является "pure Kotlin" — содержит platform-specific threading.

SPEC AMBIGUITY зафиксирована в `01-architecture.md` OQ-COMP-3. Требует подтверждения от architect-high-level через ADR-0011.

**Alternatives Considered**:

| Вариант | Причина отказа |
|---------|---------------|
| `RootComponent interface` в `domain/commonMain` с `Value<AppShellState>` | Interface содержал `Value<AppShellState>` (Decompose type) — domain зависел бы от Decompose. Нарушение `domain-models.md`. **Переработано в ADR-0011**: `StateFlow<AppShellState>` (kotlinx.coroutines) снимает Decompose-зависимость в domain — именно это решение принято. |
| `RootComponent interface` в `shared/feature/app-shell/commonMain` (не domain) | Возможно, но создаёт дополнительный слой абстракции без явной пользы; OQ-COMP-3 требует решения high-level |

---

## ADR-COMP-04: Navigator interface — добавить в domain (OQ#1 Path A)

**Status**: Accepted

**Context**:
Walking Skeleton (229 тестов зелёных) не содержит `Navigator` interface — это delta из `1-research.md`. `AppShellTransitions` не нуждается в Navigator (pure functions). Но `DefaultRootComponent` и Compose UI должны иметь общий type.

**Decision**:
```kotlin
// shared/feature/app-shell/domain/src/commonMain/.../domain/navigation/Navigator.kt
interface Navigator {
    fun goTo(destination: Destination)
}
```
Размещение в domain/commonMain позволяет:
- `NavigatorImpl` (presentation) реализует интерфейс
- Compose UI получает `Navigator` через DI или как параметр — без зависимости на `DefaultRootComponent`
- Use cases не используют Navigator — интерфейс только для navigation dispatch

**Alternatives Considered**:

| Вариант | Причина отказа |
|---------|---------------|
| Navigator как typealias `(Destination) -> Unit` | Менее явное именование; не расширяемо (нельзя добавить `canGoTo(d): Boolean` позже без breaking change) |
| Navigator в presentation layer | UI не может зависеть на presentation напрямую без interface в общем месте; cross-module coupling нарушение `clean-architecture.md` |

---

## ADR-COMP-05: @Serializable на TabConfig — deferred в MVP

**Status**: Accepted (for future state-saving, deferred in MVP)

**Context**:
Decompose `childStack(serializer = serializer<C>())` требует что `C` является `@Serializable`. `TabConfig` sealed hierarchy живёт в domain. ADR-COMP-02 решил что MVP использует `serializer = null` для всех child stacks. Annotation `@Serializable` на `TabConfig` при `serializer = null` — dead code: kotlinx-serialization plugin генерирует serializer который нигде не вызывается.

Codex Realist pass 2 зафиксировал: spec и design противоречат друг другу — 01-architecture требует `@Serializable`, а 02-behavior говорит `serializer = null`. Пока `serializer = null` (ADR-COMP-02) — `@Serializable` annotation не несёт пользы и только усложняет domain.

**Decision**:
**НЕ добавлять** `@Serializable` на `TabConfig` и связанные sealed classes в MVP. `kotlin-serialization` plugin в `build.gradle.kts` не добавляется на этом этапе.

Когда future feature реализует process death recovery (ADR-COMP-02 revision) — тогда одновременно добавляются:
1. `@Serializable` на `TabConfig` hierarchy (domain delta)
2. `serializer = serializer<C>()` в childStack вызовах (presentation)
3. `kotlin-serialization` plugin (build.gradle.kts, зона backend-dev)

Это гарантирует что annotation и механизм сохранения активны одновременно, а не независимо.

**Alternatives Considered**:

| Вариант | Причина отказа |
|---------|---------------|
| Добавить `@Serializable` сейчас при `serializer = null` | Dead code: annotation генерирует serializer который никогда не вызывается; усложняет domain без пользы для MVP |
| Custom Decompose serializer в presentation (без изменения domain) | Дублирует domain структуру в presentation; при изменении domain нужно менять два места |

---

## ADR-COMP-06: ScrollToTopRegistry — identity-aware unregister (`===`)

**Status**: Accepted

**Context**:
Compose `Crossfade(300ms)` при смене конфигурации child stack создаёт overlap: входящий экран регистрирует хук до того как исходящий экран вызывает `onDispose`. Если использовать простой `currentHook = null` при любом unregister — новый хук может быть затёрт.

**Decision**:
```kotlin
private val hooks = mutableMapOf<Tab, ScrollToTopHook>()

fun register(tab: Tab, hook: ScrollToTopHook) { hooks[tab] = hook }

fun unregister(tab: Tab, hook: ScrollToTopHook) {
    if (hooks[tab] === hook) hooks.remove(tab)  // === : reference equality
}

fun current(tab: Tab): ScrollToTopHook? = hooks[tab]
```
Registry хранит hook per-tab (`MutableMap<Tab, ScrollToTopHook>`). Identity check `===` гарантирует что только исходящий экран (чей instance зарегистрирован) обнуляет registry для своего tab. Входящий экран после регистрации `register(tab, hook2)` не будет затёрт при `unregister(tab, hook1)` исходящего.

**Main thread invariant**: `register` и `unregister` вызываются исключительно из Compose composition (Main thread — `DisposableEffect` / `LaunchedEffect` в composable). Дополнительная синхронизация `hooks` не требуется; вызов из IO coroutine (`withContext(Dispatchers.IO)`) запрещён.

**Alternatives Considered**:

| Вариант | Причина отказа |
|---------|---------------|
| `currentHook = null` без identity check | Race condition: входящий экран регистрируется → исходящий unregisters → `currentHook = null` → re-tap на новом экране не работает |
| Stack-based registry (push/pop) | Избыточно для данного use case — только один экран активен одновременно после завершения Crossfade |
| `DisposableEffect(navStack)` с key зависящим от nav position | Перерегистрирует при каждом nav change; сложнее lifecycle |

---

## ADR-COMP-07: Koin `factory` для DefaultRootComponent (не `single`)

**Status**: Accepted

**Context**:
`DefaultRootComponent` принимает `ComponentContext` — объект привязанный к lifecycle Activity. Если зарегистрировать как `single`, один instance будет shared между Activities при configuration change (что невозможно при правильной Decompose setup, но паттерн остаётся опасным).

**Decision**:
```kotlin
factory { (ctx: ComponentContext) ->
    DefaultRootComponent(
        componentContext = ctx,
        initUseCase = get(),
        navigateUseCase = get(),
        observeUseCase = get(),
        retapUseCase = get(),
        userStatsRepository = get(),
        workManager = get(),
    )
}
```
`parametersOf(componentContext)` передаётся из `MainActivity.onCreate()`.

**Alternatives Considered**:

| Вариант | Причина отказа |
|---------|---------------|
| `single` с ComponentContext захватом на App lifecycle | ComponentContext с App lifecycle не имеет BackHandler — back не работает. Activity-scope ComponentContext в singleton = memory leak при смене конфигурации |
| `viewModel { DefaultRootComponent(...) }` (Koin ViewModel scope) | Decompose использует собственный lifecycle management; `AndroidViewModel` не нужен и создаёт двойной lifecycle tracking |

---

## ADR-COMP-08b: app-shell/presentation → qualification/domain (one-directional, dev-mode)

**Status**: ACCEPTED (phase-04, decision owner: backend-dev)

**Context**:
`DefaultRootComponent` импортирует `TapProgress`, `TapResult`, `ActivateDevModeUseCase` из `shared/feature/qualification/domain` для реализации version-tap dev mode активации. Этот cross-feature import одно-направленный, но не был задокументирован в ADR. `clean-architecture.md` требует ADR для cross-feature импортов вне cascade chain (ADR-HMQ-06 покрывает только `shared/core` cascade, не app-shell→qualification).

**Decision**:
Явно разрешён одно-направленный import `app-shell/presentation → qualification/domain`. Обратный импорт (`qualification → app-shell`) — blocker.

**Rationale**:
- `ActivateDevModeUseCase` использует callback-based DI (`readCurrentDeveloperLevel`, `onDevModeActivated`) — не содержит ссылок на app-shell типы
- Связность строго одно-направленная (presentation → domain другой feature)
- Dev mode активация — забота presentation layer; qualification owning the use case правильно с точки зрения domain responsibility
- Нет циклической зависимости между features

**Review check**:
```bash
grep -rE "^import .*\.feature\.app_shell\." shared/feature/qualification/ --include="*.kt"
```
Non-empty output = blocker.

**Alternatives Considered**:

| Вариант | Причина отказа |
|---------|---------------|
| Перенести `ActivateDevModeUseCase` в `shared/core` | Оверинжиниринг — use case используется только в одном месте; core предназначен для shared infrastructure |
| Callback-only API без прямого импорта qualification types | Потребовало бы wrapper/adapter в app-shell domain — усложнение без пользы при строго одно-направленной связности |

---

## ADR-COMP-08: ktlint не применяется к KMP-модулям (Walking Skeleton compat)

**Status**: Accepted (phase-01, decision owner: backend-dev)

**Context**:
Walking Skeleton domain code (`AppShellTransitions.kt`, `AppShellFactory.kt` и commonTest файлы), сгенерированный domain-designer-ом на spec-фазе, нарушает ktlint правила (`multiline-expression-wrapping`, `blank-line-before-declaration`, и др.). Согласно Walking Skeleton awareness rule, эти файлы не подлежат модификации в phase-01.

**Decision**:
`KmpLibraryConventionPlugin` применяет только `detekt` (не `ktlint`) для модулей `:shared:feature:app-shell:domain`, `:shared:feature:app-shell:data`, `:shared:core:stats`. Android-only модули (`:platform:firebase`, `:apps:android-next`, `:android:*`) получают оба инструмента (detekt + ktlint). Aggregate `ktlintCheck` task в root `build.gradle.kts` включает только Android modules.

**Consequence**:
AC10 "detekt + ktlint работают на active modules" применяется ко всем Android modules. KMP modules имеют только detekt coverage до момента когда Walking Skeleton code будет приведён к стандарту в отдельной задаче.

---

## Open Questions (component-level)

| ID | Вопрос | Статус |
|----|--------|--------|
| OQ-COMP-1 | Navigator interface: домен (Path A) vs presentation (Path B)? | **RESOLVED** — Path A, `interface Navigator` в domain/commonMain. See ADR-0011 (`01-architecture.md`). |
| OQ-COMP-3 | RootComponent placement: spec NFR #1 vs domain purity — формальное подтверждение | **RESOLVED** — ADR-0011: `interface RootComponent` в domain/commonMain (StateFlow), `DefaultRootComponent` в presentation. See `01-architecture.md`. |
| OQ-COMP-4 | Koin параметризованный factory: `get<DefaultRootComponent>(parametersOf(ctx))` в MainActivity — spec требования к DI entry point? | **OPEN** — requires lead decision on project DI convention |
| OQ-COMP-5 | `platform/firebase` зависит на `data/commonMain` для `UserStatsDataSource` — нарушает ли это ADR-0001? | **RESOLVED** — `UserStatsDataSource` переносится в `shared/core/stats/`. See ADR-0011 OQ-COMP-5 section (`01-architecture.md`). |
| OQ-COMP-6 | `appShellPresentationModule` в `AppApplication.startKoin` — намеренно закомментирован в phase-01. | **RESOLVED** — `appShellPresentationModule` создаётся в phase-04 (Decompose integration). В phase-01 закомментирован намеренно: `apps/android-next/AppApplication.kt` строка 17. |
