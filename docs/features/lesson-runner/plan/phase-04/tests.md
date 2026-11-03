---
phase: phase-04
role: test-dev
---

# Phase 04 — Tests

> PT-01..PT-41 — presentation unit tests. IT-02, IT-03 — integration tests. All in `android/feature/lesson-runner/presentation/src/test/`.

## Pattern Invariants

- `DefaultComponentContext(LifecycleRegistry())` для component construction in tests — per `testing.md`
- Fake use cases из `04-testing.md §Fake Blueprints` (FakeStartLessonAttemptUseCase и др.)
- Flow collection через `StateFlow.value` или `take(1).toList()` — нет Turbine
- `runTest` + `StandardTestDispatcher` для coroutines

## Test Locations

| Test | Location |
|------|----------|
| PT-* presentation unit | `android/feature/lesson-runner/presentation/src/test/` |
| IT-02, IT-03 integration | `android/feature/lesson-runner/presentation/src/test/` |
| RunFakeComponent + CT-* | `android/feature/lesson-runner/presentation/src/androidTest/` (Phase-05) |

---

## Fake Blueprints (create in `src/test/kotlin/.../fake/`)

### `FakeStartLessonAttemptUseCase`

Per `04-testing.md §Fake Blueprints`:
- `var result: RunnerState = RunnerState.Loading`
- `var callCount = 0`
- `suspend operator fun invoke(lessonId: LessonId, mode: Difficulty): RunnerState` — returns `result`, increments `callCount`

### `FakeCompleteAttemptUseCase`

- `var result: RunnerState = RunnerState.Completed(...)` (fixture attempt)
- `var callCount = 0`
- `suspend operator fun invoke(state: RunnerState.Ready): RunnerState`

### `FakeAbortAttemptUseCase`

- `var result: RunnerState = RunnerState.Aborted(...)`
- `var callCount = 0`
- `suspend operator fun invoke(state: RunnerState.Ready): RunnerState`

### `FakeSubmitLessonRatingUseCase`

- `var result: Result<Unit> = Result.success(Unit)`
- `var callCount = 0`
- `suspend operator fun invoke(userId: String, lessonId: LessonId, rating: Int): Result<Unit>`

---

## PT-* Presentation Unit Tests (PT-01..PT-41)

Full list per `04-testing.md §Presentation Unit Tests`. Key scenarios below — `test-dev` implements ALL 41:

### PT-01 `EASY_loading_to_ready_no_flagSecure`

- **Given:** component with EASY mode; FakeStartAttempt returns Ready state
- **When:** init completes
- **Then:** `uiState.value` is `RunnerUiState.Question`; `isHard == false`

### PT-02 `HARD_loading_to_ready_isHard_true`

- **Given:** HARD mode; FakeStartAttempt returns Ready
- **When:** init
- **Then:** `uiState.value` is `Question(isHard = true)`

### PT-03 `onCrossConfirmed_abortUseCase_called`

- **Given:** state = Ready; FakeAbortAttemptUseCase
- **When:** `component.onCrossConfirmed()`
- **Then:** `fakeAbortUseCase.callCount == 1`; uiState eventually Aborted

### PT-04 `allQuestionsAnswered_completeUseCase_called_once`

- **Given:** state = Ready with 1 question in pool; FakeCompleteAttemptUseCase
- **When:** `component.onAnswer(answer)` for last question
- **Then:** `fakeCompleteUseCase.callCount == 1`

### PT-05 `completedState_onFinish_popNavigation`

- **Given:** state = Completed; FakeNavigator
- **When:** `component.onFinish()`
- **Then:** navigation.pop() called

### PT-10 `onTimeout_autoAnswerOnTimeout_stateUpdated`

- **Given:** state = Ready(indexInPool=0)
- **When:** `component.onTimeout()`
- **Then:** `uiState.value` has `indexInPool > 0` (или Result if last)

### PT-22 `lifecycle_doOnStop_state_isPaused_true`

- **Given:** component initialized; state = Ready(isPaused=false)
- **When:** simulate `lifecycle.doOnStop` event (via LifecycleRegistry)
- **Then:** `uiState.value.isPaused == true` (eventually)

### PT-23 `isPaused_true_question_uiState_isPaused_true`

- **Given:** state with isPaused=true
- **When:** observe `uiState`
- **Then:** `(uiState.value as RunnerUiState.Question).isPaused == true`

### PT-24 `onContinue_isPaused_false_sameIndex`

- **Given:** state = Ready(isPaused=true, indexInPool=3)
- **When:** `component.onContinue()`
- **Then:** `(uiState.value as Question).isPaused == false`; `indexInPool == 3` (dialog kiosk closed; same question index — уже авто-ответили на предыдущий)

### PT-25 `onExit_abortUseCase_called_popSignal`

- **Given:** state = Ready; FakeAbortAttemptUseCase
- **When:** `component.onExit()`
- **Then:** `fakeAbortUseCase.callCount == 1`; navigation popped

### PT-26 `complete_savedAttempts_size_one`

- **Given:** FakeLessonAttemptRepository; component completes all questions
- **When:** all questions answered → CompleteUseCase → save
- **Then:** `fakeLessonAttemptRepo.savedAttempts.size == 1`

### PT-27 `abort_after3_codeAnswer_positions_correct`

- **Given:** 20 eligible questions; user answers 3; then onExit()
- **When:** AbortAttemptUseCase
- **Then:** attempt.codeAnswer positions 0..2 scored (≥'1'); positions 3..19 = '1'; non-subset = '0'

### PT-28 `lessonVersion_snapshot_at_start`

- **Given:** lesson.version = 5 at start; version changes mid-run to 6
- **When:** attempt saved
- **Then:** `attempt.lessonVersion == 5`

### PT-29 `allShown9_not_submitted_showRatingPrompt_true`

- **Given:** attempt.codeAnswer.allShownAnswersAre9 = true; FakeLessonRatingRepo.hasSubmitted = false
- **When:** Result state computed
- **Then:** `Result.showRatingPrompt == true`

### PT-30 `hasSubmitted_showRatingPrompt_false`

- **Given:** FakeLessonRatingRepo.hasSubmitted = true
- **When:** Result state
- **Then:** `showRatingPrompt == false`

### PT-31 `allShown9_false_showRatingPrompt_false`

- **Given:** attempt.codeAnswer.allShownAnswersAre9 = false
- **When:** Result state
- **Then:** `showRatingPrompt == false`

### PT-32 `onSubmitRating_ratingUseCase_called_once`

- **Given:** FakeSubmitLessonRatingUseCase
- **When:** `component.onSubmitRating(2)`
- **Then:** `fakeSubmitUseCase.callCount == 1`; `fakeSubmitUseCase.lastRating == 2`

### PT-33..PT-41 (implement all per `04-testing.md` table)

Test-dev implements remaining PT-33..PT-41 scenarios per exact test IDs in `04-testing.md §Presentation Unit Tests`.

---

## Integration Tests

### IT-02 `rotation_component_reuses_runnerStateHolder`

- **Given:** component with FakeStartAttempt returning Ready; advance to indexInPool=3
- **When:** simulate rotation (LifecycleRegistry destroy + new LifecycleRegistry, same instanceKeeper)
- **Then:** new component instance has `uiState.value.indexInPool == 3` (state preserved)

### IT-03 `stateHolder_onDestroy_clears_state`

- **Given:** RunnerStateHolder with state=Ready
- **When:** `stateHolder.onDestroy()` called (not rotation)
- **Then:** internal state cleared (not used in new component without instanceKeeper)

### IT-04 `complete_fakeLessonAttemptRepo_savedAttempts_size_one`

- **Given:** FakeLessonAttemptRepository with in-memory list
- **When:** component completes all questions
- **Then:** `fakeLessonAttemptRepo.savedAttempts.size == 1`

### IT-05 `abort_after3_codeAnswer_length_matches_eligible`

- **Given:** 50 eligible questions (fake); answer 3; abort
- **When:** attempt saved
- **Then:** `attempt.codeAnswer.raw.length == 50`; `[0..2]` scored; rest '1'

### IT-06 `lesson_version_immutable_across_run`

- **Given:** FakeLessonRepository returns lesson.version = 5 at start
- **When:** mid-run lesson version changes to 7 (not possible in fake, but check snapshot)
- **Then:** `attempt.lessonVersion == 5`

### IT-07 `no_writes_during_3_question_playthrough`

- **Given:** FakeLessonAttemptRepository with insertCallCount; 3 questions answered
- **When:** answer 3 questions (not last)
- **Then:** `insertCallCount == 0` after answers (write only on complete)

### IT-08 `submit_rating_row_exists`

- **Given:** FakeLessonRatingRepository
- **When:** `component.onSubmitRating(3)` → use case → repo
- **Then:** `fakeRatingRepo.hasSubmitted("userId", lessonId) == true`

---

## Validation Commands

```bash
./gradlew :android:feature:lesson-runner:presentation:test --no-configuration-cache
./gradlew detekt ktlintCheck --no-configuration-cache
```
