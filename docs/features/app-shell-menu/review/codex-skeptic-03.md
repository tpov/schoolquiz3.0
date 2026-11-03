# Codex Skeptic Review — 03-decisions.md
**Date**: 2026-04-18  
**Lens**: "Решения обоснованы? Альтернативы рассмотрены? Что сломается если решение неверно?"  
**Verdict**: CONTESTED — 2 HIGH, 3 MEDIUM, 1 LOW

---

## Summary

Основная логика всех 7 ADR верна. Но документ содержит два фактических разрыва которые блокируют phase-01:  
1. Phantom spec reference в ADR-COMP-02 — "spec FR #23" не существует в `0-spec.md` (grep: no matches).  
2. ADR-COMP-07 Koin factory snapshot устарел: содержит `handleBackUseCase = get()` и `userStatsRepository = get()` из старого дизайна, хотя `01-architecture.md:579` явно говорит UC3 excluded и ObserveAppShellStateUseCase введён вместо прямого repo.

Если phase-01 backend-dev следует ADR-COMP-07 буква-в-букву, DefaultRootComponent получит неправильный набор зависимостей.

---

## Findings

### 1 · HIGH · SpecContradiction
**Location**: `03-decisions.md:69` — ADR-COMP-02, Decision section  
**Evidence**: ADR-COMP-02 утверждает: «При process death `DefaultRootComponent.init {}` выполняет `initUseCase()` заново — получает ActualState из UserStats. Навигационные позиции внутри tab сбрасываются до default — **допустимо по spec (spec FR #23 упоминает только top-level tab restoration)**».

Grep `0-spec.md` на `FR #23` — 0 совпадений. Spec содержит FR #1–#20 (progressive unlock упоминается как FR #20 в `0-spec.md:685`). FR #23 не существует.

**What breaks if wrong**: Если sub-stack restoration на cold start требуется по реальным FR (не FR #23), то `serializer = null` нарушает реальный spec. Justification для ADR-COMP-02 висит на несуществующей ссылке. Реальное обоснование (пользователь явно одобрил deviation, зафиксировано в ADR-LEAD-01) есть — но spec reference должна быть удалена или заменена на корректную.

**Fix required**: В ADR-COMP-02 заменить `(spec FR #23 упоминает только top-level tab restoration)` на `(User-Approved Deviation per ADR-LEAD-01; spec не имеет FR #23)`.

---

### 2 · HIGH · StaleSnapshot
**Location**: `03-decisions.md:195-207` — ADR-COMP-07, Decision section (Koin factory sample)  
**Evidence**: Koin factory показывает:
```kotlin
factory { (ctx: ComponentContext) ->
    DefaultRootComponent(
        componentContext = ctx,
        initializeUseCase = get(),
        navigateUseCase = get(),
        handleBackUseCase = get(),        // ← UC3 excluded in 01-architecture.md:579
        onTabRetapUseCase = get(),
        userStatsRepository = get(),      // ← replaced by observeUseCase per ADR-LEAD-02
    )
}
```
Но `01-architecture.md:575-579` явно говорит:
- `UC_OBS["factory: ObserveAppShellStateUseCase(get())"]` — добавлен
- `UC3 [HandleBackUseCase] ... NOT wired into DefaultRootComponent; production back path: NavigateUseCase(state, Destination.Back)` — исключён
- `DefaultRootComponent ← UC1, UC2, UC_OBS, UC4` — без UC3 и userStatsRepository

**What breaks if wrong**: Phase-01 backend-dev добавляет `handleBackUseCase` в конструктор (лишняя зависимость, не используется) и не добавляет `observeUseCase` (production stats updates не работают). Koin graph не предоставит `HandleBackUseCase` в COMP factory — runtime inject failure.

**Fix required**: Обновить ADR-COMP-07 factory sample в соответствии с `01-architecture.md`:
```kotlin
factory { (ctx: ComponentContext) ->
    DefaultRootComponent(
        componentContext = ctx,
        initializeUseCase = get(),
        navigateUseCase = get(),
        observeUseCase = get(),           // ← ADR-LEAD-02
        onTabRetapUseCase = get(),
        // handleBackUseCase: NOT injected — back via NavigateUseCase(state, Destination.Back)
        // userStatsRepository: NOT direct — delegated through observeUseCase
    )
}
```

---

### 3 · MEDIUM · ModelMismatch
**Location**: `03-decisions.md:99-102` — ADR-COMP-03, Alternatives Considered, Alt A  
**Evidence**: Rejected alternative «`RootComponent interface` в `domain/commonMain`» отвергнут с reasoning: «Interface содержит `Value<AppShellState>` (Decompose type) — domain зависел бы от Decompose. Нарушение `domain-models.md`».

Но ADR-0011 в `01-architecture.md:756-778` **реализует именно эту альтернативу** с исправлением: `interface RootComponent` в domain/commonMain, только `StateFlow<AppShellState>` (не `Value<>`). Rejection reason был валиден для Value<>, но ADR-0011 решил проблему через StateFlow — то, что ADR-COMP-03 называет "нарушением", уже исправлено.

**What breaks**: Читатель ADR-COMP-03 думает что interface в domain невозможен, хотя именно так и сделано по ADR-0011. ADR-COMP-03 status остаётся "SPEC AMBIGUITY — requires ADR-0011 from high-level", хотя ADR-0011 уже принят.

**Fix required**: Обновить ADR-COMP-03 status: "Accepted — resolved by ADR-0011 (interface RootComponent в domain с StateFlow<AppShellState>; DefaultRootComponent в presentation)". Добавить примечание в Alternatives: "Alt A переработана в ADR-0011: `StateFlow<AppShellState>` вместо `Value<>` снимает Decompose-зависимость в domain".

---

### 4 · MEDIUM · IncorrectReasoning
**Location**: `03-decisions.md:52-53` — ADR-COMP-01, Alternatives Considered, строка "Переписать UC принимая `StateFlow<AppShellState>`"  
**Evidence**: Rejection reason: «Нарушает domain purity — `StateFlow` это presentation concern; `domain-models.md` запрещает `androidx.*` в domain».

Это фактически неверно. `StateFlow` — это `kotlinx.coroutines.flow.StateFlow`, тот же package что и `Flow<AppShellState>` который domain уже использует. `domain-models.md` запрещает `android.*`, `androidx.*`, third-party SDK — не kotlinx.coroutines.

Корректная причина отказа: dependency direction inversion — use case принял бы `StateFlow` созданный и управляемый presentation layer; domain стал бы зависеть от presentation-owned mutable state container, что нарушает dependency flow `presentation → domain`.

**What breaks**: Будущий reviewer может решить что `StateFlow` запрещён в domain и создать `Flow` обёртку там, где она не нужна. Или наоборот — увидит что StateFlow уже есть в domain (return type `Flow`) и решит что rejection неправильный.

**Fix required**: Исправить причину отказа: «Dependency direction inversion: use case принимал бы presentation-owned `MutableStateFlow<AppShellState>` как аргумент — domain зависит от presentation state container, нарушение `clean-architecture.md` dependency flow».

---

### 5 · MEDIUM · StaleOpenQuestion
**Location**: `03-decisions.md:218-225` — Open Questions таблица  
**Evidence**: OQ-COMP-5 («`platform/firebase` зависит на `data/commonMain` для `UserStatsDataSource` interface — нарушает ли это ADR-0001?») показан без статуса в таблице. Однако `01-architecture.md:803-808` (ADR-0011 раздел "OQ-COMP-5 resolution") явно отвечает: `UserStatsDataSource` переносится в `shared/core/stats/`.

OQ-COMP-1 (Navigator Path A vs B) тоже resolved в ADR-0011 (Path A, domain interface) — но также остаётся без статуса в таблице.

**What breaks**: Phase-01 backend-dev видит открытые вопросы и не знает что они уже resolved — может задать их повторно или реализовать неправильно (оставит `UserStatsDataSource` в `data/` вместо `shared/core/stats/`).

**Fix required**: Обновить Open Questions таблицу:
- OQ-COMP-1: "RESOLVED — Path A, domain interface. See ADR-0011 (01-architecture.md)."
- OQ-COMP-3: "RESOLVED — ADR-0011 (01-architecture.md). Interface в domain, DefaultRootComponent в presentation."
- OQ-COMP-5: "RESOLVED — `UserStatsDataSource` в `shared/core/stats/`. See ADR-0011 OQ-COMP-5 section."
- OQ-COMP-4: оставить open (project DI convention — requires lead decision)

---

### 6 · LOW · MissingInvariant
**Location**: `03-decisions.md:162-183` — ADR-COMP-06, ScrollToTopRegistry  
**Evidence**: `_currentHook` — mutable field без явного thread-safety контракта. ADR объясняет identity-check (`===`) для Crossfade overlap, но не документирует что `register/unregister` вызываются только с Main thread (Compose Main thread invariant).

**What breaks**: Если тестировщик или другой feature вызовет `unregister()` из IO coroutine (технически возможно через `LaunchedEffect` + `withContext`), возможен race condition на `_currentHook`. Маловероятно в Compose, но недокументировано.

**Fix required** (LOW — может быть добавлен комментарий в implementation, не обязательно в ADR): «`register`/`unregister` вызываются исключительно из Compose composition (Main thread). Synchronization не требуется.»

---

## Positive Notes

- **ADR-COMP-01 core decision** верна: provider lambda `() -> AppShellState` правильно решает stale closure; `observeStats().map { currentStateProvider().copy(userStats = stats) }` не захватывает старый state.
- **ADR-COMP-02 rationale** (без phantom FR #23) обоснован: Walking Skeleton без `@Serializable`, MVP не требует sub-stack process death recovery, пользователь явно одобрил (ADR-LEAD-01).
- **ADR-COMP-04 (Navigator в domain)** корректен: 3-line pure interface, Spec NFR #3 compliant, не создаёт cross-module coupling.
- **ADR-COMP-05 синхронизация `@Serializable` + plugin + `serializer=serializer<C>()`** — умное решение: "одновременно все три" гарантирует что dead code annotation не накапливается.
- **ADR-COMP-07 core reasoning** (factory не single для ComponentContext) верен: `single` с Activity-bound ComponentContext = memory leak + broken BackDispatcher.
- Все 7 ADR содержат раздел "Alternatives Considered" с минимум 1 отвергнутым вариантом — требование из `CLAUDE.md` выполнено.

---

## Required Fixes Before phase-01

| # | ADR | Fix | Priority |
|---|-----|-----|----------|
| 1 | COMP-02 | Удалить `spec FR #23` — phantom reference | HIGH |
| 2 | COMP-07 | Обновить Koin factory sample: observeUseCase вместо handleBackUseCase+userStatsRepository | HIGH |
| 3 | COMP-03 | Обновить status → "Resolved by ADR-0011"; исправить Alt A rejection | MEDIUM |
| 4 | COMP-01 | Исправить причину отказа альтернативы StateFlow | MEDIUM |
| 5 | OQ table | Закрыть OQ-COMP-1, OQ-COMP-3, OQ-COMP-5 (resolved by ADR-0011) | MEDIUM |
| 6 | COMP-06 | Добавить Main thread invariant comment в implementation | LOW |
