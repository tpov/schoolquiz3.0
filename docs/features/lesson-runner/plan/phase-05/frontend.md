---
phase: phase-05
role: frontend-dev
---

# Phase 05 — Frontend Tasks

## Pattern Invariants

- Все цвета через `MaterialTheme.colorScheme.*` — НИКОГДА `Color(0xFF...)`
- Каждый Composable ОБЯЗАН иметь `@Preview` annotation
- Compose screens получают `LessonRunnerRootComponent` (interface), не `DefaultLessonRunnerRootComponent`
- `component.events.collect` ОБЯЗАН быть в `LaunchedEffect(component)` — per `AppShellScreen.kt:131-147` precedent (keyed on component instance for correct re-subscription on component change)
- Ordering controls: up/down `IconButton` (не drag library)
- FLAG_SECURE: `DisposableEffect(enabled)` approach — НЕ Activity override
- `BlockingResumeDialog`: `DialogProperties(dismissOnBackPress=false, dismissOnClickOutside=false, usePlatformDefaultWidth=false)` — ОБЯЗАТЕЛЬНО
- ~~**FLAG_SECURE derivation**: `RunnerUiState.Result` НЕ имеет поля `isHard` или `mode`. HARD mode для result screen определяется через `state.attempt.mode == Difficulty.HARD`. ОБЯЗАТЕЛЬНО использовать `state.attempt.mode`.~~ **Superseded by ADR-LR-19**: `RunnerUiState.Result` использует flat projection (Phase-04 security). Используй `state.mode == Difficulty.HARD` напрямую.

---

## Create `LessonRunnerScreen`

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/LessonRunnerScreen.kt`
- **Тип:** Composable fun
- **Сигнатура:** `@Composable fun LessonRunnerScreen(component: LessonRunnerRootComponent, onNavigateBack: () -> Unit, onSegmentClick: (Int) -> Unit)`
- **Вход:** `component: LessonRunnerRootComponent`, `onNavigateBack: () -> Unit` (from `QuizzesScreen`), `onSegmentClick: (Int) -> Unit` (breadcrumb nav)
- **Поведение / Выход:**
  - Collect `component.uiState` via `collectAsState()`
  - `rememberFlagSecure(enabled = ...)` — enable in HARD mode during question AND result phase. **Superseded by ADR-LR-19**: use `state.mode == Difficulty.HARD` (flat field) for `RunnerUiState.Result`, not `state.attempt.mode`.
  - `LaunchedEffect(component) { component.events.collect { event -> when(event) { is RunnerEvent.SaveAttemptFailed -> snackbarHostState.showSnackbar(...); RunnerEvent.SaveRatingFailed -> snackbarHostState.showSnackbar(...); RunnerEvent.NavigateBack -> onNavigateBack() } } }` — NavigateBack calls the Compose callback (A2 hybrid; per `07-events.md:67`)
  - `Scaffold { SnackbarHost(snackbarHostState) }`
  - `when(state)`:
    - `Loading` → `CircularProgressIndicator`
    - `InitFailed` → empty state text + back button calling `component.onBack()`
    - `Question` → `QuestionProgressHeader` + question content + `CrossButton` + `BlockingResumeDialog` if `isPaused`; `ExitConfirmDialog` if `showExitConfirmDialog`
    - `Result` → `ResultContent`
  - HARD background: `if (state is Question && state.isHard) MaterialTheme.colorScheme.errorContainer` as surface color
- **Edge cases:**
  - FLAG_SECURE toggle on mode change (EASY to HARD possible? — No, mode set at start; still key on `isHard` field)
  - `SnackbarHostState` via `remember`
  - Process kill while HARD mode: FLAG_SECURE cleared by `onDispose` automatically
  - `RunnerEvent.NavigateBack` → calls `onNavigateBack()` which is `{ component.popCurrentChild() }` from `QuizzesScreen` — component never holds `StackNavigation`
- **Depends on:** `LessonRunnerRootComponent`, `RunnerUiState`, `RunnerEvent`, all sub-composables
- **Canonical reference:** `06-api-contract.md:319`, `07-events.md:67`
- **Rationale:** Root screen; navigation A2 hybrid — callback from parent; delegates rendering to sub-composables per state type

---

## Create `rememberFlagSecure` (within `LessonRunnerScreen.kt` or separate util file)

- **Файл:** same file or `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/FlagSecureEffect.kt`
- **Тип:** Composable fun
- **Сигнатура:** `@Composable fun rememberFlagSecure(enabled: Boolean)`
- **Вход:** `enabled: Boolean`
- **Поведение / Выход:**
  - `DisposableEffect(enabled)` — keyed on `enabled`
  - `val window = (LocalContext.current as? Activity)?.window ?: return@DisposableEffect onDispose {}`
  - `if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)`
  - `onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }`
  - When `enabled` changes false→true: `onDispose` clears, new effect adds (keyed on `enabled`)
- **Edge cases:**
  - `LocalContext.current` не `Activity` → `?: return` (safe in non-Activity host)
  - Rotation: `DisposableEffect` reruns → FLAG_SECURE re-applied correctly (CT-29)
- **Depends on:** `LocalContext`, `WindowManager`
- **Canonical reference:** `2-grounding.md §Problem 8 Fix Shape 1`
- **Rationale:** Grounding Problem 8 solution; new pattern for project; contains в presentation scope

---

## Create `QuestionProgressHeader`

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/QuestionProgressHeader.kt`
- **Тип:** Composable fun
- **Сигнатура:** `@Composable fun QuestionProgressHeader(indexInPool: Int, totalInPool: Int, deadlineMs: Long, isPaused: Boolean, isHard: Boolean, onCrossClick: () -> Unit)`
- **Вход:** progress state + callbacks
- **Поведение / Выход:**
  - Progress text: `"${indexInPool + 1} / $totalInPool"` (1-indexed display)
  - Timer display: `LaunchedEffect(indexInPool, deadlineMs) { while (isActive) { val remaining = (deadlineMs - System.currentTimeMillis()).coerceAtLeast(0); if (remaining == 0L) break; delay(100L) } }` — update `remainingMs` MutableState
  - Timer text: `"${remainingMs / 1000}s"` или formatted
  - If `isPaused` → timer display stops (LaunchedEffect exits when `isPaused` true — add `if (isPaused) return@LaunchedEffect`)
  - `CrossButton(onClick = onCrossClick)` — left side или right top corner
  - HARD mode indicator: `isHard` → colorScheme.error или errorContainer tint on timer
- **Edge cases:**
  - Timer key `(indexInPool, deadlineMs)` — both в key → LaunchedEffect restarts on new question (PT-20, AC-26)
  - `deadlineMs = 0` degenerate case → `coerceAtLeast(0)` → 0 → onTimeout already called
  - `isPaused == true` → LaunchedEffect должен не вызывать onTimeout (timer stopped)
- **Depends on:** Compose, MaterialTheme
- **Canonical reference:** `0-spec.md §11`, `02-behavior.md`, AC-24..AC-27
- **Rationale:** Reusable header; timer logic here (not in full LessonRunnerScreen) for clarity

---

## Create `CrossButton`

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/CrossButton.kt`
- **Тип:** Composable fun
- **Сигнатура:** `@Composable fun CrossButton(onClick: () -> Unit, modifier: Modifier = Modifier)`
- **Вход:** `onClick`, optional `modifier`
- **Поведение / Выход:**
  - `IconButton(onClick = onClick)` with `Icons.Default.Close` icon
  - `contentDescription = "Выйти из урока"` (accessibility)
- **Edge cases:** N/A — simple button
- **Depends on:** Material Icons
- **Canonical reference:** internal (no api-contract entry)
- **Rationale:** Extracts reusable exit button; `@Preview` included

---

## Create `SingleChoiceContent`

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/SingleChoiceContent.kt`
- **Тип:** Composable fun
- **Сигнатура:** `@Composable fun SingleChoiceContent(state: QuestionUiState.SingleChoice, onOptionSelected: (String) -> Unit)`
- **Вход:** state with `options`, `selectedOptionId`, `questionText`, `imageUrl`
- **Поведение / Выход:**
  - Question text: `MaterialTheme.typography.titleMedium` centered
  - Image (if `hasImage && imageUrl != null`): `AsyncImage` (Coil) with loading/error placeholder
  - Options layout: `options.size <= 5` → single column; `6..8` → two columns (LazyVerticalGrid или custom grid)
  - Each option: `Button(onClick = { onOptionSelected(option.id) }, enabled = selectedOptionId == null)` — tap locks (immediate commit)
  - Selected visual: `Button` with `filled` style if `selectedOptionId == option.id`
- **Edge cases:**
  - `options.size == 1` — degenerate (shouldn't happen per spec §9 "2..8")
  - Disabled после tap (selectedOptionId set) → component auto-advances
  - Long option text → `text` wraps within Button
- **Depends on:** `QuestionUiState.SingleChoice`, Coil, Material3
- **Canonical reference:** `0-spec.md §9`, AC-6..AC-9
- **Rationale:** Spec §9: ≤5 one row, 6-8 two rows

---

## Create `MultipleChoiceContent`

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/MultipleChoiceContent.kt`
- **Тип:** Composable fun
- **Сигнатура:** `@Composable fun MultipleChoiceContent(state: QuestionUiState.MultipleChoice, onOptionToggled: (String) -> Unit, onSubmit: () -> Unit)`
- **Вход:** state with `options`, `selectedIds`
- **Поведение / Выход:**
  - Question text + optional image
  - Options: `Checkbox(checked = option.id in selectedIds)` per option row
  - Layout: ≤5 single column, 6-8 two columns (same rule as SingleChoice)
  - Bottom: `Button("Ответить", onClick = onSubmit)` — enabled always (at least tap without selection = all wrong)
- **Edge cases:**
  - No checkboxes selected → submit → Jaccard = 0.0 → score '1'
  - All checked → Jaccard = correct_picked/total if some wrong
- **Depends on:** `QuestionUiState.MultipleChoice`, Material3
- **Canonical reference:** `0-spec.md §9`, AC-7, AC-10..AC-12
- **Rationale:** Explicit submit button; Jaccard scoring in domain

---

## Create `OrderingContent`

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/OrderingContent.kt`
- **Тип:** Composable fun
- **Сигнатура:** `@Composable fun OrderingContent(state: QuestionUiState.Ordering, onMoveUp: (Int) -> Unit, onMoveDown: (Int) -> Unit, onSubmit: () -> Unit)`
- **Вход:** state with `items` (current order), move callbacks, submit
- **Поведение / Выход:**
  - Question text + optional image
  - Items list: each item has `text` + `IconButton(Icons.Default.KeyboardArrowUp, onMoveUp(index))` + `IconButton(Icons.Default.KeyboardArrowDown, onMoveDown(index))`
  - First item: up arrow disabled (`alpha = 0.3f` or `enabled = false`)
  - Last item: down arrow disabled
  - Bottom: `Button("Готово", onClick = onSubmit)`
  - Accessibility: up/down buttons have `contentDescription`
- **Edge cases:**
  - Single item list — both arrows disabled (edge case per spec "2..8")
  - `onMoveUp(0)` — top item; disabled arrow prevents call
- **Depends on:** `QuestionUiState.Ordering`, Material Icons
- **Canonical reference:** `0-spec.md §9`, AC-8
- **Rationale:** Accessibility-friendly (keyboard nav possible); 0 external deps

---

## Create `FillBlankContent`

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/FillBlankContent.kt`
- **Тип:** Composable fun
- **Сигнатура:** `@Composable fun FillBlankContent(state: QuestionUiState.FillBlank, onCandidateSelected: (candidateText: String) -> Unit, onBlankCleared: (blankIndex: Int) -> Unit, onSubmit: () -> Unit)`
- **Вход:** state with `templateParts`, `filledValues`, image, candidates inferred from state
- **Поведение / Выход:**
  - Template display: `FlowRow` или manual layout; `TemplatePart.Text` → plain text; `TemplatePart.Blank(index)` → `Button(filledValues[index] ?: "___", onClick = onBlankCleared(index))`
  - Candidates grid: 5 candidates → one row; 10 → two rows of 5 (per spec §9)
  - Each candidate: `Button(text, onClick = onCandidateSelected(text), enabled = text not already used)` — consumed candidates visually dimmed
  - Bottom: `Button("Готово", onClick = onSubmit)`
- **Edge cases:**
  - Blank already filled → tap blank → `onBlankCleared` returns candidate to pool
  - All blanks filled → submit auto (or explicit button)
  - Candidates exhausted but blank not filled — degenerate; shouldn't happen per spec
- **Depends on:** `QuestionUiState.FillBlank`, Material3
- **Canonical reference:** `0-spec.md §9`, AC-9
- **Rationale:** Complex fill-blank interaction; `TemplatePart` already parsed by mapper

---

## Create `BlockingResumeDialog`

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/BlockingResumeDialog.kt`
- **Тип:** Composable fun
- **Сигнатура:** `@Composable fun BlockingResumeDialog(onContinue: () -> Unit, onExit: () -> Unit)`
- **Вход:** callbacks only
- **Поведение / Выход:**
  - `Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false))`
  - Fullscreen `Card` content
  - Text: "Продолжить прохождение?"
  - `Button("Продолжить", onClick = onContinue)`
  - `OutlinedButton("Выйти", onClick = onExit)`
- **Edge cases:**
  - `usePlatformDefaultWidth = false` → dialog fills screen; размер управляется через `fillMaxSize` modifier в Card
  - `dismissOnBackPress = false` → System back press does NOT dismiss (critical for anti-cheat spec §14)
- **Depends on:** Material3 Dialog, DialogProperties
- **Canonical reference:** `2-grounding.md §Problem 8 Fix Shape 2`, `0-spec.md §15`
- **Rationale:** Anti-task-switcher dialog; must be non-dismissable per spec

---

## Create `ExitConfirmDialog`

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/ExitConfirmDialog.kt`
- **Тип:** Composable fun
- **Сигнатура:** `@Composable fun ExitConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit)`
- **Вход:** callbacks
- **Поведение / Выход:**
  - Standard `AlertDialog` (dismissable unlike BlockingResumeDialog)
  - Title: "Уверены? Прогресс попытки потеряется"
  - `TextButton("Выйти", onClick = onConfirm)` — destructive
  - `TextButton("Отмена", onClick = onDismiss)`
- **Edge cases:**
  - `onDismissRequest = onDismiss` — clicking outside dismisses (normal dialog)
- **Depends on:** Material3 AlertDialog
- **Canonical reference:** `0-spec.md §18`, AC-3
- **Rationale:** Standard confirm dialog for cross-button exit

---

## Create `ResultContent`

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/ResultContent.kt`
- **Тип:** Composable fun
- **Сигнатура:** `@Composable fun ResultContent(state: RunnerUiState.Result, onSubmitRating: (Int) -> Unit, onFinish: () -> Unit)`
- **Вход:** `RunnerUiState.Result`, callbacks
- **Поведение / Выход:**
  - 1. `Text("${state.percentScore.raw}%")` — `MaterialTheme.typography.displayLarge`, center. **Superseded by ADR-LR-19**: flat field `state.percentScore`, not `state.attempt.percentScore`.
  - 2. Subtitle text based on attempt:
    - First perfect EASY (detect via `state.mode == EASY && state.hardUnlocked && state.percentScore.raw == 100`) → "Поздравляем! Сложные вопросы доступны". **Superseded by ADR-LR-19**: flat fields.
    - First perfect HARD → "100% сложные! Вы прошли урок полностью"
    - Else → "Урок завершён"
  - 3. `StarRating(rating = state.bestStarsRawTenths / 10f)` — existing designsystem component. **Superseded by ADR-LR-19**: flat field `state.bestStarsRawTenths`.
  - 4. Stats: `"Попыток: ${state.userAttemptCount} | Средний %: ${state.userAveragePercentScore}"`
  - 5. `if (state.showRatingPrompt) RatingPromptSection(onSubmitRating = onSubmitRating)`
  - 6. `if (state.top3.isNotEmpty()) Top3Section(state.top3)`
  - 7. `Button("Завершить", onClick = onFinish)`
  - `if (state.saveWarning) Text("⚠ Результат не сохранён", color = MaterialTheme.colorScheme.error)`
- **Edge cases:**
  - `state.top3.isEmpty()` → Top3Section hidden (spec AC-45)
  - `state.showRatingPrompt == false` → RatingPromptSection hidden (spec AC-42)
  - `state.saveWarning == true` → warning indicator visible (CT-27)
  - Stars `StarRating` is existing `android/core/designsystem` component; accepts `rating: Float = rawTenths/10f`
- **Depends on:** `RunnerUiState.Result`, `StarRating` (designsystem), `RatingPromptSection`, `Top3Section`
- **Canonical reference:** `0-spec.md §30`, `06-api-contract.md:386`
- **Rationale:** Per spec §30 ordered content list

---

## Create `RatingPromptSection`

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/RatingPromptSection.kt`
- **Тип:** Composable fun
- **Сигнатура:** `@Composable fun RatingPromptSection(onSubmitRating: (Int) -> Unit)`
- **Вход:** callback
- **Поведение / Выход:**
  - Title: "Оцените урок"
  - Three `IconButton` for 1/2/3 full stars
  - Tap → `onSubmitRating(selectedRating)` — one-time interaction (component prevents re-submit)
  - Selected star highlighted
- **Edge cases:**
  - Stars 1/2/3 (integer, not fractional) — per spec §30.5
  - After selection: `onSubmitRating` called; component may hide prompt via state change
- **Depends on:** Material Icons (stars)
- **Canonical reference:** `0-spec.md §30.5`, AC-41..44
- **Rationale:** Isolated rating UI; only shown when `showRatingPrompt == true`

---

## Create `Top3Section`

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/.../ui/Top3Section.kt`
- **Тип:** Composable fun
- **Сигнатура:** `@Composable fun Top3Section(top3: List<TopParticipant>)`
- **Вход:** `top3: List<TopParticipant>` (size ≤ 3)
- **Поведение / Выход:**
  - Section title: "Лучшие участники"
  - Each entry: `AsyncImage(model = participant.avatarUrl, placeholder = Icons.Default.AccountCircle)` + `Text(participant.nickname)` + `Text("${participant.percent}%")`
  - `avatarUrl == null` → `fallback = Icons.Default.AccountCircle` per spec AC-46
  - List hidden when `top3.isEmpty()` (caller should check before rendering)
- **Edge cases:**
  - `avatarUrl` HTTPS URL vs local resource: Coil handles both
  - Nickname might be long → `maxLines = 1; overflow = Ellipsis`
- **Depends on:** `TopParticipant` (core/leaderboard), Coil `AsyncImage`
- **Canonical reference:** `0-spec.md §30.6`, AC-45, AC-46
- **Rationale:** Server-aggregated; empty when CF not implemented (graceful hidden)

---

## Create `RunFakeComponent` (for tests)

- **Файл:** `android/feature/lesson-runner/presentation/src/androidTest/kotlin/.../fake/RunFakeComponent.kt`
- **Тип:** class
- **Сигнатура:** `class RunFakeComponent(private val _uiState: MutableStateFlow<RunnerUiState>, private val _events: Channel<RunnerEvent> = Channel(Channel.BUFFERED)) : LessonRunnerRootComponent`
- **Вход:** controllable state + events
- **Поведение / Выход:**
  - `override val uiState: StateFlow<RunnerUiState> = _uiState`
  - `override val events: Flow<RunnerEvent> = _events.receiveAsFlow()`
  - All `fun on*()` methods: default no-op; `lastAnswer`, `lastRating` tracking fields for assertion
  - Per `04-testing.md §Fake Blueprints`
- **Edge cases:**
  - `_uiState.value = newState` from test → Compose recompose
- **Depends on:** `LessonRunnerRootComponent`
- **Canonical reference:** `04-testing.md §Fake Blueprints`
- **Rationale:** Controlled test double for CT-* Compose tests
