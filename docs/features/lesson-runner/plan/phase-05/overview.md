---
phase: phase-05
name: Compose UI — LessonRunnerScreen
tag: complex
date: 2026-04-27
---

# Phase 05 — Compose UI (LessonRunnerScreen)

## Goal

Создать полный Compose UI для gameplay loop: `LessonRunnerScreen` со всеми sub-composables (4 типа вопросов, progress header, dialogs, result screen, rating prompt, top3 section). Реализовать 4 новых Compose паттерна для проекта: FLAG_SECURE, blocking resume dialog, timer countdown, ordering controls. После фазы фича визуально complete.

## Scope

- `LessonRunnerScreen` (root composable)
- `QuestionProgressHeader` — индикатор прогресса + таймер
- `CrossButton` — кнопка выхода
- `SingleChoiceContent`, `MultipleChoiceContent`, `OrderingContent`, `FillBlankContent`
- `BlockingResumeDialog`, `ExitConfirmDialog`
- `ResultContent` — percent, stars, stats, top3, rating prompt, finish button
- `RatingPromptSection`
- `rememberFlagSecure` DisposableEffect
- `RunFakeComponent` для Compose tests

## Role Inputs

- `backend.md` — No
- `frontend.md` — Yes
- `tests.md` — Yes

## Layer

`android/feature/lesson-runner/presentation` (UI added to existing presentation module from Phase-04)

## Review Tags

`architecture-review` (no hardcoded colors), `concurrency-review` (LaunchedEffect timer, Channel event collection)

## State Matrix Coverage

Matrix 6 (onStop/onResume dialog) — UI side: `isPaused == true` → `BlockingResumeDialog`. Matrix 7 (Timer) — UI side: `deadlineMs` → `LaunchedEffect` countdown display.

## Domain Contract Coverage

AC 1-5, 28-30, 31-34, 41-52b (UI rendering coverage).

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 8: New patterns (FLAG_SECURE, block-on-resume, timer, drag) | `frontend-dev` | `LessonRunnerScreen.kt` | `BrandComponentsInvariantsTest` — no hardcoded colors; Material 3 BOM 2024.09.02; `DialogProperties(dismissOnBackPress=false)` | Implement per `2-grounding.md §Problem 8 Fix Shape` patterns | CT-11..CT-17; CT-29 |

## Files

### New Files

- `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/LessonRunnerScreen.kt`
- `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/QuestionProgressHeader.kt`
- `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/CrossButton.kt`
- `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/SingleChoiceContent.kt`
- `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/MultipleChoiceContent.kt`
- `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/OrderingContent.kt`
- `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/FillBlankContent.kt`
- `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/BlockingResumeDialog.kt`
- `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/ExitConfirmDialog.kt`
- `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/ResultContent.kt`
- `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/RatingPromptSection.kt`
- `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/Top3Section.kt`
- `android/feature/lesson-runner/presentation/src/androidTest/kotlin/.../fake/RunFakeComponent.kt`

### Modified Files

None (Phase-04 module)

### Deleted Files

None

## Dependencies

- Phase-04 (component interface + state types)
- Phase-05 может идти параллельно с Phase-06

## Criteria for Acceptance

1. `BrandComponentsInvariantsTest` зелёный (нет hardcoded `Color(0xFF...)`)
2. CT-01..CT-30: все 30 Compose UI tests зелёные
3. `rememberFlagSecure(enabled = isHard)` применяется в `LessonRunnerScreen` (CT-11, CT-12, CT-13)
4. `BlockingResumeDialog` с `DialogProperties(dismissOnBackPress=false, dismissOnClickOutside=false, usePlatformDefaultWidth=false)` (CT-15)
5. Timer `LaunchedEffect(indexInPool, deadlineMs)` с `delay(100L)` loop (CT-10)
6. `Ordering` — up/down `IconButton` (без external drag library)
7. Каждый Composable имеет `@Preview`

## Tests Required

- CT-01..CT-30 (полный список в `04-testing.md §Compose UI Tests`):
  - `ct01_lessonRunnerScreen_loading_shows_progressIndicator`: given `RunFakeComponent` with `uiState = Loading`, when `LessonRunnerScreen(component, onNavigateBack={}, onSegmentClick={})` rendered, then `CircularProgressIndicator` visible
  - `ct05_navigateBack_emitted_calls_onNavigateBack`: given `RunFakeComponent`, when component emits `RunnerEvent.NavigateBack`, then `onNavigateBack` lambda invoked (A2 hybrid)
  - `ct11_hardMode_flagSecure_set`: given HARD mode component, when `LessonRunnerScreen` rendered, then `FLAG_SECURE` flag applied to window (requires `ActivityScenario`)
  - `ct15_blockingResumeDialog_notDismissable`: given `isPaused=true`, when system back pressed, then dialog remains visible (`dismissOnBackPress=false`)
  - `ct10_timer_counts_down`: given `QuestionProgressHeader(deadlineMs=System.currentTimeMillis()+5000L)`, when 1 second elapses, then displayed seconds decrease
  - `ct29_rotation_flagSecure_reapplied`: given HARD mode, when rotation occurs, then `FLAG_SECURE` remains applied after `DisposableEffect` re-runs
- Instrumented tests с `ComposeTestRule + RunFakeComponent`

## Validation

```bash
# Compose tests (requires emulator)
./gradlew :android:feature:lesson-runner:presentation:connectedAndroidTest --no-configuration-cache
# No hardcoded colors:
rg "Color\(0x" android/feature/lesson-runner/presentation/src/main -g "*.kt"
# Expected: empty
# No Koin in Compose:
rg "getKoin\(\|koinInject\(\|inject<" android/feature/lesson-runner/presentation/src/main -g "**/ui/**/*.kt"
# Expected: empty
```

## Handoff Notes

После phase-05:
- Phase-06 интегрирует `LessonRunnerScreen` в `QuizzesScreen` exhaustive when

## Pattern Invariants

- Timer ОБЯЗАН использовать `LaunchedEffect(indexInPool, deadlineMs) { while (isActive) { delay(100L) } }` — per `2-grounding.md §Problem 8 Fix Shape 3`
- FLAG_SECURE ОБЯЗАН использовать `DisposableEffect(enabled)` — НЕ override Activity method — per `2-grounding.md §Problem 8 Fix Shape 1`
- `BlockingResumeDialog` ОБЯЗАН иметь `DialogProperties(dismissOnBackPress=false, dismissOnClickOutside=false, usePlatformDefaultWidth=false)` — per `2-grounding.md §Problem 8 Fix Shape 2`
- Все цвета через `MaterialTheme.colorScheme.*` — `BrandComponentsInvariantsTest` enforce
- Ordering ОБЯЗАН использовать up/down IconButton (не drag-and-drop library) — 0 external deps для accessibility
- SnackbarHost для `RunnerEvent` consumption ОБЯЗАН использовать `LaunchedEffect(Unit) { component.events.collect { ... } }`
- `RunFakeComponent` ОБЯЗАН реализовывать `LessonRunnerRootComponent` интерфейс (не mock)

## Options Considered

| Критерий | Option A: up/down IconButton для Ordering (recommended) | Option B: `sh.calvin.reorderable:reorderable` drag library | Option C: Compose built-in `Modifier.draggable` |
|----------|---------------------------------------------------------|------------------------------------------------------------|--------------------------------------------------|
| Accessibility | ✓ Встроенная (Button semantics) | Partial (drag = нет keyboard nav) | ✗ Нет accessibility |
| External deps | 0 | 1 новая lib (ADR нужен) | 0 |
| Complexity | Низкая | Средняя (lib integration) | Высокая (custom gesture) |
| Тестируемость | Высокая (Button tap test) | Средняя (gesture testing) | Низкая |
| Spec requirement | "drag-and-drop" per spec §9 — делегировано ("Delegated #12") | Closest to spec intent | Closest to spec intent |

**Recommended: Option A** (up/down buttons) — per `2-grounding.md §Problem 8 Fix Shape 4`

**Rationale:** Spec §9 помечает ordering как `[USER DECIDED]` layout, но конкретный механизм — "Delegated". Grounding Problem 8 Fix Shape рекомендует up/down buttons "для accessibility + 0 deps". Drag-and-drop требует ADR.

**Rejected Option B:** требует дополнительную ADR + новую зависимость; spec не mandates drag explicitly.

**Rejected Option C:** нет accessibility; custom gesture implementation fragile.
