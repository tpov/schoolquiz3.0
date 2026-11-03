---
phase: phase-05
role: test-dev
---

# Phase 05 — Tests

> CT-01..CT-30 — Compose UI instrumented tests. All in `android/feature/lesson-runner/presentation/src/androidTest/`. Use `RunFakeComponent`.

## Pattern Invariants

- `RunFakeComponent` — единственный test double для `LessonRunnerRootComponent`
- `ComposeTestRule.setContent { LessonRunnerScreen(component = fakeComponent, onNavigateBack = {}, onSegmentClick = {}) }` — canonical render call per `06-api-contract.md:319`
- State control: `fakeComponent._uiState.value = targetState` then assert
- `ActivityScenario` для FLAG_SECURE tests (CT-11..CT-13, CT-29) — Window access required

## Test Locations

| Test | Location |
|------|----------|
| CT-* Compose UI | `android/feature/lesson-runner/presentation/src/androidTest/` |

---

## CT-* Compose UI Test Scenarios (CT-01..CT-30)

Full list per `04-testing.md §Compose UI Tests`. All render calls use canonical signature `LessonRunnerScreen(component, onNavigateBack = {}, onSegmentClick = {})` per `06-api-contract.md:319`. Key scenarios:

### CT-01 `questionScreen_easy_mode_renders`

- **Given:** `RunFakeComponent(_uiState = MutableStateFlow(RunnerUiState.Question(isHard=false, ...)))`
- **When:** `composeTestRule.setContent { LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {}) }`
- **Then:** question content visible; progress indicator present

### CT-30 `navigateBack_event_invokes_onNavigateBack_callback`

- **Given:** `RunFakeComponent(_events = Channel<RunnerEvent>(BUFFERED)); var navCalled = false`
- **When:** `composeTestRule.setContent { LessonRunnerScreen(fakeComponent, onNavigateBack = { navCalled = true }, onSegmentClick = {}) }`; then `fakeComponent._events.trySend(RunnerEvent.NavigateBack)`; `composeTestRule.waitForIdle()`
- **Then:** `navCalled == true` (LaunchedEffect collected NavigateBack and invoked callback)
- **AC coverage:** AC-5, AC-34

### CT-02 `questionScreen_hard_mode_errorBackground`

- **Given:** `isHard = true`
- **When:** render
- **Then:** HARD background color applied (colorScheme.errorContainer or error surface)

### CT-03 `exitConfirmDialog_showExitConfirmDialog_true`

- **Given:** `RunnerUiState.Question(showExitConfirmDialog = true, ...)`
- **When:** render
- **Then:** `onNodeWithText("Уверены?").assertIsDisplayed()`

### CT-04 `resultState_resultScreen_visible`

- **Given:** `RunnerUiState.Result(...)`
- **When:** render
- **Then:** percent text visible; stars visible; Завершить button present

### CT-05 `finishButton_present_on_result`

- **Given:** Result state
- **When:** render
- **Then:** `onNodeWithText("Завершить").assertExists()`

### CT-10 `timer_expired_onTimeout_invoked`

- **Given:** `deadlineMs = System.currentTimeMillis() - 100` (already past)
- **When:** render + `advanceUntilIdle` or wait
- **Then:** `fakeComponent.onTimeoutCallCount > 0`

### CT-11 `hard_mode_flagSecure_set`

- **Given:** `ActivityScenario<TestActivity>`; Question state with `isHard=true`
- **When:** render `LessonRunnerScreen`
- **Then:** `window.attributes.flags AND FLAG_SECURE != 0`

### CT-12 `exit_hard_mode_flagSecure_cleared`

- **Given:** `isHard=true` → state changed to Loading (exit)
- **When:** `_uiState.value = RunnerUiState.Loading`
- **Then:** `FLAG_SECURE` not set (`onDispose` triggered)

### CT-13 `easy_mode_no_flagSecure`

- **Given:** `isHard=false`
- **When:** render
- **Then:** `FLAG_SECURE` NOT set

### CT-14 `paused_timer_not_ticking`

- **Given:** `isPaused=true`; `deadlineMs = now + 30000`
- **When:** render + wait 500ms
- **Then:** timer display not decreased (LaunchedEffect paused)

### CT-15 `paused_blockingDialog_displayed`

- **Given:** `isPaused=true`
- **When:** render
- **Then:** `onNodeWithText("Продолжить прохождение?").assertIsDisplayed()`

### CT-16 `continue_button_calls_onContinue`

- **Given:** blocking dialog displayed
- **When:** `onNodeWithText("Продолжить").performClick()`
- **Then:** `fakeComponent.onContinueCallCount == 1`

### CT-17 `exit_button_calls_onExit`

- **Given:** blocking dialog displayed
- **When:** `onNodeWithText("Выйти").performClick()`
- **Then:** `fakeComponent.onExitCallCount == 1`

### CT-18 `ratingPrompt_showRatingPrompt_true_visible`

- **Given:** Result state with `showRatingPrompt=true`
- **When:** render
- **Then:** `onNodeWithText("Оцените урок").assertIsDisplayed()`

### CT-19 `ratingPrompt_false_absent`

- **Given:** `showRatingPrompt=false`
- **When:** render
- **Then:** rating prompt NOT displayed

### CT-20 `top3_nonEmpty_sectionVisible`

- **Given:** `top3 = listOf(TopParticipant("Alice", null, 90))`
- **When:** render
- **Then:** "Alice" visible; "90%" visible

### CT-21 `top3_nullAvatarUrl_placeholder_rendered`

- **Given:** `TopParticipant("Bob", avatarUrl=null, 80)`
- **When:** render
- **Then:** no crash; placeholder icon or AccountCircle rendered

### CT-22 `bestStarsRawTenths_15_starRating_1_5`

- **Given:** LessonItemUi with `bestStarsRawTenths=15` (Phase-06 test — verify StarRating(1.5f) rendered in LessonItemCard)
- **Note:** This CT may belong to Phase-06; verify AC-47 coverage there

### CT-23 `hardUnlocked_false_checkbox_absent`

- **Given:** LessonItemUi with `hardUnlocked=false`
- **When:** render LessonItemCard (Phase-06)
- **Note:** Phase-06 scope; verify CT-23 in Phase-06 tests

### CT-24 `hardUnlocked_true_checkbox_visible`

- Note: Phase-06 scope

### CT-25 `initFailed_emptyPool_text_displayed`

- **Given:** `RunnerUiState.InitFailed(reason=EmptyPool)`
- **When:** render
- **Then:** "В уроке пока нет вопросов" text visible

### CT-26 `initFailed_noValidQuestions`

- **Given:** `RunnerUiState.InitFailed(reason=NoValidQuestions)`
- **When:** render
- **Then:** empty state text visible

### CT-27 `saveWarning_true_indicator_shown`

- **Given:** `RunnerUiState.Result(saveWarning=true, ...)`
- **When:** render
- **Then:** warning indicator visible (error color text or icon)

### CT-28 `saveRatingFailed_event_snackbar_shown`

- **Given:** `fakeComponent._events.send(RunnerEvent.SaveRatingFailed)`
- **When:** render + `advanceUntilIdle`
- **Then:** Snackbar with "Не удалось отправить оценку" visible

### CT-29 `hardMode_activityRecreate_flagSecure_remains`

- **Given:** `ActivityScenario`; `isHard=true` render
- **When:** `scenario.recreate()` (rotation simulation)
- **Then:** `FLAG_SECURE` still set after recreation

---

## Validation Commands

```bash
# Compose instrumented tests (requires emulator)
./gradlew :android:feature:lesson-runner:presentation:connectedAndroidTest --no-configuration-cache
# No hardcoded colors:
rg "Color\(0x" android/feature/lesson-runner/presentation/src/main -g "*.kt"
```
