---
phase: phase-04
name: Presentation Component
tag: complex
date: 2026-04-27
---

# Phase 04 — Presentation Component (lesson-runner/presentation NEW MODULE)

## Goal

Создать новый Android module `android/feature/lesson-runner/presentation/` с `LessonRunnerRootComponent` interface и `LessonRunnerComponentFactory` fun interface (оба в `lesson-runner/presentation` per ADR-LR-16), `DefaultLessonRunnerRootComponent` Decompose component, UI state types, event channel, и Koin `lessonRunnerPresentationModule`. После фазы gameplay-loop component готов к подключению Compose UI (Phase-05) и composition root (Phase-07).

**ADR-LR-16 applies**: `LessonRunnerRootComponent` interface и `LessonRunnerComponentFactory` живут в `android/feature/lesson-runner/presentation/`, НЕ в `android/core/navigation/`. Open Question 1 (README:101) — RESOLVED.

## Scope

- Новый module `android/feature/lesson-runner/presentation/`
- `LessonRunnerRootComponent` interface + `LessonRunnerComponentFactory` fun interface — оба NEW FILES в `lesson-runner/presentation` (НЕ в core/navigation)
- `DefaultLessonRunnerRootComponent` (Decompose, instanceKeeper для `RunnerStateHolder`)
- Sealed `RunnerUiState`, `QuestionUiState`, `OptionUi`, `TemplatePart`
- Sealed `RunnerEvent` + Channel pattern
- Domain RunnerState → UI state mapper
- `lessonRunnerPresentationModule` (Koin factory)
- Presentation unit tests PT-01..PT-41

## Role Inputs

- `backend.md` — Yes
- `frontend.md` — No (UI Compose — Phase-05)
- `tests.md` — Yes

## Layer

`android/feature/lesson-runner/presentation` (new module; contains both interface and implementation). `android/core/navigation` — не затронут (ADR-LR-16).

## Review Tags

`architecture-review` (новый модуль, cross-feature Producer/Consumer boundary), `concurrency-review` (StateFlow + Channel, lifecycle.doOnStop/doOnResume, instanceKeeper, coroutineScope in component)

## State Matrix Coverage

Matrix rows: M1 (Score per type) — через `RunnerLogic.evaluateAnswer` calls. M2 (Stars) — через `RunnerLogic.computeStars`. M3 (bestStars/hardUnlocked) — через computed state. M4 (когда писать) — через `CompleteAttemptUseCase`. M5 (Rating prompt) — через `ratingPrompt` flag. M6 (onStop/onResume) — через `lifecycle.doOnStop`/`doOnResume`. M7 (Timer formula) — через `RunnerLogic.computeTimer`. M8 (Pool selection) — через `StartLessonAttemptUseCase`.

## Domain Contract Coverage

Фаза реализует presentation boundary для Walking Skeleton domain:
- `StartLessonAttemptUseCase` → `RunnerUiState.Question`
- `CompleteAttemptUseCase` → `RunnerUiState.Result`
- `AbortAttemptUseCase` → navigation pop
- `SubmitLessonRatingUseCase` → `RunnerEvent.SaveRatingFailed` on failure

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 4: `lessonRunnerDomainModule` не зарегистрирован | `backend-dev` | `AppApplication.kt:87` | `lessonRunnerPresentationModule` создаётся здесь; registration Phase-07 | Создать `lessonRunnerPresentationModule` с factory | IT-09e resolved в Phase-07 |
| Problem 8: New patterns (FLAG_SECURE, block-on-resume, timer, drag) | `frontend-dev` в Phase-05; Component side здесь | `DefaultLessonRunnerRootComponent.init` — `lifecycle.doOnStop`/`doOnResume` | `lifecycle.doOnStop/doOnResume` — Essenty API; `instanceKeeper` — Decompose 3.1.0 | Component реализует `onPaused()`, `onContinue()`, `onExit()` для dialog control; timer logic через `deadlineMs` в state | PT-22..PT-25 |

## Files

### New Files

- `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt` — interface (ADR-LR-16; NOT in core/navigation)
- `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt` — fun interface (ADR-LR-16; NOT in core/navigation)
- `android/feature/lesson-runner/presentation/build.gradle.kts`
- `android/feature/lesson-runner/presentation/src/main/kotlin/.../state/RunnerUiState.kt`
- `android/feature/lesson-runner/presentation/src/main/kotlin/.../state/QuestionUiState.kt`
- `android/feature/lesson-runner/presentation/src/main/kotlin/.../event/RunnerEvent.kt`
- `android/feature/lesson-runner/presentation/src/main/kotlin/.../component/DefaultLessonRunnerRootComponent.kt`
- `android/feature/lesson-runner/presentation/src/main/kotlin/.../mapper/RunnerStateMapper.kt`
- `android/feature/lesson-runner/presentation/src/main/kotlin/.../di/LessonRunnerPresentationModule.kt`
- `android/feature/lesson-runner/presentation/src/test/kotlin/.../fake/FakeStartLessonAttemptUseCase.kt`
- `android/feature/lesson-runner/presentation/src/test/kotlin/.../fake/FakeCompleteAttemptUseCase.kt`
- `android/feature/lesson-runner/presentation/src/test/kotlin/.../fake/FakeAbortAttemptUseCase.kt`
- `android/feature/lesson-runner/presentation/src/test/kotlin/.../fake/FakeSubmitLessonRatingUseCase.kt`

### Modified Files

- `settings.gradle.kts` — include `:android:feature:lesson-runner:presentation`

### Deleted Files

None

## Dependencies

- Phase-01 (domain types)
- Phase-03 (data module — но presentation НЕ импортирует data напрямую; только через domain interfaces via Koin)
- Phase-04 может идти параллельно с Phase-02/03

## Criteria for Acceptance

1. `android/feature/lesson-runner/presentation/LessonRunnerRootComponent` interface compile зелёный; `rg "interface LessonRunnerRootComponent" android/feature/lesson-runner/presentation/src` — 1 match; `rg "interface LessonRunnerRootComponent" android/core/navigation` — empty (ADR-LR-16 validation)
2. `DefaultLessonRunnerRootComponent` с `instanceKeeper` — rotation test IT-02 (component reuses RunnerStateHolder)
3. `lifecycle.doOnStop { component.onPaused() }` → `state.isPaused == true` (PT-22)
4. `onContinue()` → `state.isPaused == false` (PT-24)
5. `onExit()` → `AbortAttemptUseCase` called → navigation pop signal (PT-25)
6. `CompleteAttemptUseCase` called exactly once after all questions answered (PT-04, PT-26)
7. `RunnerEvent.SaveAttemptFailed` emitted when save fails (PT-40)
8. `RunnerEvent.SaveRatingFailed` emitted when rating submit fails (PT-41)
9. PT-01..PT-41: все 41 presentation unit tests зелёные
10. **Stateful field reset**: `RunnerStateHolder.onDestroy()` очищает state (IT-03); НЕ вызывается при rotation (IT-02)

## Tests Required

- `component_initialState_isLoading`: given новый `DefaultLessonRunnerRootComponent` создан, when инициализация, then `uiState.value == RunnerUiState.Loading`
- `component_rotation_reusesStateHolder`: given component создан с `instanceKeeper`, when configuration change (component recreated с тем же instanceKeeper), then `RunnerStateHolder` — тот же объект (IT-02)
- `component_onStop_setsIsPaused`: given component в `RunnerState.Ready`, when `lifecycle.doOnStop` triggered, then `uiState.value` is `RunnerUiState.Question(isPaused=true)` (PT-22)
- `component_onContinue_clearsPaused`: given `isPaused=true`, when `onContinue()` called, then `uiState.value` is `RunnerUiState.Question(isPaused=false)` (PT-24)
- `component_onExit_callsAbortUseCase`: given component in Ready state, when `onExit()` called, then `FakeAbortAttemptUseCase.invocationCount == 1` (PT-25)
- `component_completeUseCase_calledOnceAfterLastQuestion`: given N questions pool, when last answer submitted, then `FakeCompleteAttemptUseCase.invocationCount == 1` (PT-04, PT-26)
- `component_saveFailure_emitsSaveAttemptFailedEvent`: given `FakeLessonAttemptRepository` configured to throw, when `CompleteAttemptUseCase` executed, then `events.first() is RunnerEvent.SaveAttemptFailed` (PT-40)
- `component_ratingFailure_emitsSaveRatingFailedEvent`: given `FakeLessonRatingRepository` configured to throw, when `onSubmitRating(3)` called, then `events.first() is RunnerEvent.SaveRatingFailed` (PT-41)
- PT-01..PT-41: полный список в `04-testing.md §Presentation Unit Tests`; test-dev пишет параллельно с backend-dev

## Validation

```bash
./gradlew :android:feature:lesson-runner:presentation:test --no-configuration-cache
./gradlew detekt ktlintCheck --no-configuration-cache
# No data/persistence imports in presentation:
rg "^import .*(data|persistence|room|firebase)" android/feature/lesson-runner/presentation/src/main -g "*.kt"
# Expected: empty
# No direct Koin access from component (Koin is used in DI module only):
rg "getKoin\(\|koinInject\(\|inject<" android/feature/lesson-runner/presentation/src/main -g "*.kt"
# Expected: empty
```

## Handoff Notes

После phase-04:
- Phase-05 (Compose UI) создаёт `LessonRunnerScreen` принимающий `LessonRunnerRootComponent` (интерфейс)
- Phase-06 (quizzes-screen) интегрирует через `LessonRunnerComponentFactory`
- Phase-07 (composition root) регистрирует `lessonRunnerPresentationModule`

## Pattern Invariants

- `_events = Channel<RunnerEvent>(capacity = Channel.BUFFERED)` + `override val events = _events.receiveAsFlow()` — per `DefaultRootComponent.kt:113-114` precedent; НЕ expose `ReceiveChannel` напрямую
- `instanceKeeper.getOrCreate("runner_state_holder") { RunnerStateHolder(...) }` — Decompose 3.1.0 API; НЕ создавать RunnerStateHolder в component constructor без instanceKeeper
- `lifecycle.doOnStop { ... }` + `lifecycle.doOnResume { ... }` — Essenty API; НЕ override Activity lifecycle методы
- `_events.close()` в `lifecycle.doOnDestroy { ... }` — закрыть channel при destroy
- `uiState: StateFlow<RunnerUiState>` — НЕ `Value<T>` (Decompose) — `StateFlow` доминирует в проекте per `06-api-contract.md:284` (§LR-9)
- `RunnerLogic.*` pure functions — вызывать из component coroutine scope; НЕ вызывать из Compose
- Stateful fields в component (`_state: MutableStateFlow<RunnerUiState>`) ОБЯЗАНЫ сбрасываться при `RunnerStateHolder.onDestroy()` — AC acceptance criteria

## Options Considered

| Критерий | Option A: `instanceKeeper` для `RunnerStateHolder` (recommended) | Option B: `StateKeeper` (Decompose) для runner state | Option C: `ViewModel` (Android) |
|----------|-------------------------------------------------------------------|-------------------------------------------------------|----------------------------------|
| Config change (rotation) | ✓ State preserved | ✓ State preserved | ✓ State preserved |
| Process kill | State lost (spec §16: expected) | State restored (spec §16: не нужно) | State lost (без `SavedStateHandle`) |
| KMP compatibility | ✓ Decompose multiplatform | ✓ Decompose multiplatform | ✗ Android-only |
| Spec compliance | ✓ Process kill = state lost (§16) | ✗ Нарушает §16 "процесс kill = попытка теряется" | Нет (spec против ViewModel) |
| Project convention | ✓ Decompose Component pattern (PROJECT-CONTEXT.md) | N/A | ✗ против invariant 2 |

**Recommended: Option A** (per `0-spec.md §17`, spec §16, PROJECT-CONTEXT.md)

**Rationale:** `instanceKeeper` сохраняет in-memory state при rotation (не при process kill) — соответствует spec §17 (rotation preserves state) и §16 (process kill = lost). `StateKeeper` сохранял бы при process kill — нарушение §16.

**Rejected Option B:** `StateKeeper` = persistent across process death. Spec §16 явно: "process kill = попытка теряется". `StateKeeper` нарушает этот инвариант.

**Rejected Option C:** ViewModel — против invariant 2 проекта (Decompose Components only, PROJECT-CONTEXT.md).
