---
date: 2026-04-26
authors: architect-component
feature: lesson-runner
---

# 04 Testing Strategy: Lesson Runner

## Test Layers

| Layer | Framework | Location | Fakes |
|-------|-----------|----------|-------|
| Domain JVM (Walking Skeleton) | JUnit4 + coroutines-test | `shared/feature/lesson-runner/domain/src/commonTest/` | FakeLessonRepo, FakeQuestionRepo, FakeAuthRepo (inline) |
| Data (Room) | JUnit4 + Room in-memory | `shared/feature/lesson-runner/data/src/androidInstrumentedTest/` | in-memory Room |
| Presentation unit | JUnit4 + coroutines-test + `DefaultComponentContext(LifecycleRegistry())` | `android/feature/lesson-runner/presentation/src/test/` | Fake use cases + repos |
| Compose UI | ComposeTestRule + AndroidJUnit4 | `android/feature/lesson-runner/presentation/src/androidTest/` | `RunFakeComponent` |
| Integration | JUnit4 + Room in-memory + coroutines-test | `android/feature/lesson-runner/presentation/src/test/` | FakeRoom + FakeComponentContext |
| Migration | MigrationTestHelper + AndroidJUnit4 | `shared/core/persistence/src/androidInstrumentedTest/` | — |

## Test ID Ranges

| Range | Scope |
|-------|-------|
| DT-01..DT-89 | Domain JVM (Walking Skeleton, existing) |
| PT-01..PT-45 | Presentation unit |
| CT-01..CT-31 | Compose UI / instrumented |
| IT-01..IT-09g | Integration |
| MT-01..MT-07 | Migration |

---

## Fake Blueprints

### Existing fakes (Walking Skeleton — не создавать повторно)

Следующие 7 fakes **уже существуют** в `shared/feature/lesson-runner/domain/src/commonTest/kotlin/.../fake/` (C10):
- `FakeLessonAttemptRepository` — in-memory backing с MutableStateFlow
- `FakeLessonRatingRepository` — submit/hasSubmitted stubs
- `FakeAuthRepository` — currentUserId stub
- `FakeLessonRepository` — getById stub
- `FakeQuestionRepository` — observeByLesson stub
- `FakeQuestionContentParser` — parse result stub
- `FakeClock` — fixed timestamp

Phase-01 **использует** эти fakes, не переписывает.

### New fakes (создать в presentation src/test/)

```kotlin
// Presentation-layer fakes для unit tests (PT-*)
class FakeStartLessonAttemptUseCase {
    var result: RunnerState = RunnerState.Loading
    var callCount = 0
    suspend operator fun invoke(lessonId: LessonId, mode: Difficulty): RunnerState {
        callCount++; return result
    }
}

class FakeCompleteAttemptUseCase(
    var result: RunnerState = RunnerState.Completed(/* fixture */),
) {
    var callCount = 0
    suspend operator fun invoke(state: RunnerState.Ready): RunnerState {
        callCount++; return result
    }
}

class FakeAbortAttemptUseCase(
    var result: RunnerState = RunnerState.Aborted(/* fixture */),
) {
    var callCount = 0
    suspend operator fun invoke(state: RunnerState.Ready): RunnerState {
        callCount++; return result
    }
}

class FakeSubmitLessonRatingUseCase {
    var result: Result<Unit> = Result.success(Unit)
    var callCount = 0
    suspend operator fun invoke(userId: String, lessonId: LessonId, rating: Int): Result<Unit> {
        callCount++; return result
    }
}

// RunFakeComponent for Compose tests (CT-*)
class RunFakeComponent(
    private val _uiState: MutableStateFlow<RunnerUiState>,
    private val _events: Channel<RunnerEvent> = Channel(Channel.BUFFERED),
) : LessonRunnerRootComponent {
    override val uiState: StateFlow<RunnerUiState> = _uiState
    override val events: Flow<RunnerEvent> = _events.receiveAsFlow()
    var lastAnswer: UserAnswerDraft? = null
    override fun onAnswer(answer: UserAnswerDraft) { lastAnswer = answer }
    override fun onTimeout() {}
    override fun onContinue() {}
    override fun onExit() {}
    override fun onCrossButtonTap() {}
    override fun onCrossConfirmed() {}
    override fun onCrossCancelled() {}
    override fun onSubmitRating(rating: Int) {}
    override fun onFinish() {}
}
```

---

## Presentation Unit Tests (PT-*)

All in `android/feature/lesson-runner/presentation/src/test/`.

| ID | Scenario | AC |
|----|----------|----|
| PT-01 | EASY: Loading → Ready; no FLAG_SECURE signal | AC-1 |
| PT-02 | HARD: Loading → Ready; isHard=true in uiState | AC-2 |
| PT-03 | onCrossConfirmed() → AbortUseCase called → Aborted state | AC-3 |
| PT-04 | All questions answered → CompleteUseCase called once | AC-4 |
| PT-05 | Completed state → onFinish() → emit RunnerEvent.NavigateBack | AC-5 |
| PT-10 | onTimeout() → autoAnswerOnTimeout → state updated | AC-15, AC-26 |
| PT-11 | EASY 100% → Completed(Stars(20)) | AC-17 |
| PT-12 | EASY 50% → Completed(Stars(10)); hardUnlocked=false | AC-18 |
| PT-13 | HARD 80% → Completed(Stars(28)) | AC-19 |
| PT-14 | HARD 100% → Completed(Stars(30)) | AC-20 |
| PT-15 | No attempts → LessonItemUi(bestStarsRawTenths=0, hardUnlocked=false) | AC-21 |
| PT-16 | EASY perfect attempt → LessonItemUi.hardUnlocked=true | AC-22 |
| PT-17 | EASY imperfect → hardUnlocked=false even if rawTenths=20 | AC-23 |
| PT-18 | charsCount=165 EASY → deadlineMs = now+30000 | AC-24 |
| PT-19 | charsCount=165 HARD → deadlineMs = now+20000 | AC-25 |
| PT-20 | Timer key changes on next question → LaunchedEffect restarts | AC-26 |
| PT-21 | charsCount=10 → timer=5s (floor) | AC-27 |
| PT-22 | lifecycle.doOnStop → state.isPaused=true | AC-31 |
| PT-23 | isPaused=true → Question.isPaused=true in uiState | AC-32 |
| PT-24 | onContinue() → isPaused=false; same indexInPool | AC-33 |
| PT-25 | onExit() → AbortUseCase → Aborted → emit RunnerEvent.NavigateBack | AC-34 |
| PT-26 | FakeLessonAttemptRepository.savedAttempts.size == 1 after complete | AC-37, AC-40 |
| PT-27 | Abort after 3 answers: codeAnswer[0..2] scored, [3..19] = '1', rest '0' | AC-38 |
| PT-28 | attempt.lessonVersion == lesson.version snapshot | AC-39 |
| PT-29 | allShown9=true && !hasSubmitted → showRatingPrompt=true | AC-41 |
| PT-30 | hasSubmitted=true → showRatingPrompt=false | AC-42 |
| PT-31 | allShown9=false → showRatingPrompt=false | AC-43 |
| PT-32 | onSubmitRating(2) → FakeLessonRatingRepository.submit called once | AC-44 |
| PT-33 | Lesson.top3 non-empty → Result.top3 not empty | AC-45 |
| PT-34 | bestStars.rawTenths=15 → bestStarsRawTenths=15 in LessonItemUi | AC-47 |
| PT-35 | hardUnlocked=false → isHardChecked ignores toggle visually | AC-48 |
| PT-36 | hardUnlocked=true → isHardChecked toggleable | AC-49 |
| PT-37 | StartUseCase returns InitFailed(EmptyPool) → uiState=InitFailed | AC-50 |
| PT-38 | StartUseCase returns InitFailed(NoValidQuestions) → uiState=InitFailed | AC-51 |
| PT-39 | Parser filters one invalid → attempt continues with valid-only | AC-52 |
| PT-40 | CompleteUseCase returns SaveFailed → uiState.Result.saveWarning=true + event emitted | AC-52a |
| PT-41 | FakeLessonRatingRepository.submitResult=failure → SaveRatingFailed event | AC-52b |
| PT-42 | StartUseCase returns InitFailed(RedactedNotSupported) → uiState=InitFailed with that reason | E2.7 |
| PT-43 | `InitFailureReason.messageRes` maps each of the five reasons to its own string, none shared | E2.7 |

---

## Compose UI Tests (CT-*)

All in `android/feature/lesson-runner/presentation/src/androidTest/`.
Uses `RunFakeComponent` with controlled `MutableStateFlow<RunnerUiState>`.

| ID | Scenario | AC |
|----|----------|----|
| CT-01 | Question screen renders in EASY mode | AC-1 |
| CT-02 | isHard=true → HARD background visible | AC-2 |
| CT-03 | showExitConfirmDialog=true → confirm dialog present | AC-3 |
| CT-04 | Result state → result screen visible | AC-4 |
| CT-05 | Finish button present on result screen | AC-5 |
| CT-10 | deadlineMs expired → onTimeout callback invoked | AC-26 |
| CT-11 | HARD mode → FLAG_SECURE set on window | AC-28 |
| CT-12 | Exit HARD mode composition → FLAG_SECURE cleared | AC-29 |
| CT-13 | EASY mode → FLAG_SECURE absent | AC-30 |
| CT-14 | isPaused=true → timer not ticking | AC-31 |
| CT-15 | isPaused=true → blocking dialog displayed | AC-32 |
| CT-16 | «Продолжить» tap → onContinue() called | AC-33 |
| CT-17 | «Выйти» tap → onExit() called | AC-34 |
| CT-18 | showRatingPrompt=true → rating prompt visible | AC-41 |
| CT-19 | showRatingPrompt=false → rating prompt absent | AC-42 |
| CT-20 | top3 non-empty → Top3 section visible | AC-45 |
| CT-21 | top3 entry avatarUrl=null → placeholder rendered, no crash | AC-46 |
| CT-22 | bestStarsRawTenths=15 → StarRating(1.5f) rendered | AC-47 |
| CT-23 | hardUnlocked=false → HARD checkbox absent | AC-48 |
| CT-24 | hardUnlocked=true → HARD checkbox visible | AC-49 |
| CT-25 | InitFailed(EmptyPool) → empty state text displayed | AC-50 |
| CT-26 | InitFailed(NoValidQuestions) → empty state | AC-51 |
| CT-27 | saveWarning=true → warning indicator on result screen | AC-52a |
| CT-28 | SaveRatingFailed event → Snackbar shown | AC-52b |
| CT-29 | FLAG_SECURE rotation: HARD mode + ActivityScenario.recreate() → FLAG_SECURE remains set | AC-28, AC-29 |
| CT-30 | RunFakeComponent._events.trySend(NavigateBack) → LessonRunnerScreen calls onNavigateBack() lambda | AC-5, AC-34 |
| CT-31 | InitFailed(RedactedNotSupported) → redacted message displayed, NoValidQuestions message absent | E2.7 |

---

## Integration Tests (IT-*)

| ID | Scenario | AC |
|----|----------|----|
| IT-01 | save(attempt) → Room → observeByLesson returns it | AC-16, AC-37 |
| IT-02 | Rotation: new DefaultLessonRunnerRootComponent reuses RunnerStateHolder | AC-35 |
| IT-03 | Negative: stateHolder.onDestroy clears state (not called on rotation) | AC-36 |
| IT-04 | Complete: FakeLessonAttemptRepository.savedAttempts.size==1 | AC-40 |
| IT-05 | Abort after 3 of 50 eligible: codeAnswer.length==50, digits[0..2] scored | AC-38 |
| IT-06 | Lesson.version changes mid-run → attempt.lessonVersion == initial | AC-39 |
| IT-07 | No writes during 3-question playthrough (only at complete) | AC-40 |
| IT-08 | Submit rating → lesson_rating_submitted_local row exists in Room | AC-44 |
| IT-09a | `lessonRunnerDataModule`: `LessonAttemptRepository` resolved without exception | AC-53 |
| IT-09b | `lessonRunnerDataModule`: `LessonRatingRepository` resolved without exception | AC-53 |
| IT-09c | `lessonRunnerDataModule`: `AttemptIdProvider`, `RandomSeedProvider`, `RatingIdProvider` — каждый resolved | AC-53 |
| IT-09d | `lessonRunnerDomainKoinAdapter`: `CompleteAttemptUseCase`, `AbortAttemptUseCase`, `SubmitLessonRatingUseCase` — каждый resolved | AC-53 |
| IT-09e | `lessonRunnerPresentationModule`: `DefaultLessonRunnerRootComponent` resolved с parametersOf(ctx, lessonId, Difficulty.EASY) | AC-53 |
| IT-09f | `questionSchemaModule`: `get<QuestionContentParser>()` resolves to `KotlinxSerializationQuestionContentParser` (не null, не другой тип) | AC-53 |
| IT-09g | `AppDatabase` builder: `DifficultyConverter` + `TopParticipantListConverter` оба registered через `addTypeConverter` (Room не падает при query) | AC-53 |

---

## Migration Tests (MT-*)

Location: `shared/core/persistence/src/androidInstrumentedTest/` (extend `AppDatabaseMigrationTest`).
Full template in `08-storage-model.md`.

| ID | Verifies |
|----|----------|
| MT-01 | lesson_attempts table created with all 8 columns |
| MT-02 | lesson_rating_submitted_local created with compound PK |
| MT-03 | lessons.average_rating=NULL, rating_count=0, top3='[]' after migration |
| MT-04 | ALL 7 existing tables preserved after migration: user_stats, catalogs, quests, sections, themes, lessons, questions — insert row pre-migration, verify row exists post-migration |
| MT-05 | DifficultyConverter roundtrip: EASY → "EASY" → EASY |
| MT-06 | Production build config: `fallbackToDestructiveMigration` absent from AppDatabase builder (grep check: `rg "fallbackToDestructive" shared/core/persistence/src/androidMain` — empty in prod flavor) |
| MT-07 | `TopParticipantListConverter` roundtrip: `listOf(TopParticipant("Alice", null, 90))` → `toDb()` → `fromDb()` → original list; `fromDb("")` → `emptyList()` (no crash) |

---

## State Matrix → Test Coverage

| Matrix | Test IDs |
|--------|----------|
| Matrix 1 (Score per type) | DT-01..DT-12, PT-10 |
| Matrix 2 (Stars formula) | DT-21..DT-29 |
| Matrix 3 (bestStars/hardUnlocked) | DT-30..DT-35a, PT-15..PT-17 |
| Matrix 4 (When to write) | DT-52..DT-54, PT-26..PT-28, IT-04..IT-07 |
| Matrix 5 (Rating prompt) | DT-48..DT-51, PT-29..PT-31, CT-18..CT-19 |
| Matrix 6 (onStop/onResume) | PT-22..PT-25, CT-14..CT-17, IT-02..IT-03 |
| Matrix 7 (Timer formula) | DT-36..DT-39b, PT-18..PT-21 |
| Matrix 8 (Pool selection) | DT-40..DT-47, DT-75..DT-77 |

---

## AC Coverage Map

| AC | Test IDs | Note |
|----|----------|------|
| 1 | PT-01, CT-01 | |
| 2 | PT-02, CT-02, CT-11 | |
| 3 | PT-03, CT-03 | |
| 4 | PT-04, CT-04 | |
| 5 | PT-05, CT-05, CT-30 | |
| 6 | DT-01 | |
| 7 | DT-02 | |
| 8 | DT-03 | |
| 9 | DT-04 | |
| 10 | DT-05 | |
| 11 | DT-06 | |
| 12 | DT-07 | |
| 13 | DT-08 | |
| 14 | DT-09 | |
| 15 | DT-10, PT-10 | |
| 16 | DT-13..DT-16, IT-01 | |
| 17 | DT-25, DT-34, PT-11 | |
| 18 | DT-23, DT-33, PT-12 | |
| 19 | DT-28, PT-13 | |
| 20 | DT-29, PT-14 | |
| 21 | DT-30, PT-15 | |
| 22 | DT-34, PT-16 | |
| 23 | DT-33, DT-35a, PT-17 | |
| 24 | DT-37, PT-18 | |
| 25 | DT-39a, PT-19 | |
| 26 | DT-40, PT-20, CT-10 | |
| 27 | DT-39b, PT-21 | |
| 28 | CT-11, CT-29 | |
| 29 | CT-12, CT-29 | |
| 30 | CT-13 | |
| 31 | PT-22, CT-14 | |
| 32 | PT-23, CT-15 | |
| 33 | PT-24, CT-16 | |
| 34 | PT-25, CT-17, CT-30 | |
| 35 | IT-02 | |
| 36 | IT-03 | |
| 37 | PT-26, IT-04 | |
| 38 | PT-27, IT-05 | |
| 39 | DT-55..DT-57, IT-06 | |
| 40 | PT-28, IT-07 | |
| 41 | DT-48..DT-50, PT-29, CT-18 | |
| 42 | DT-51, PT-30, CT-19 | |
| 43 | DT-48, PT-31 | |
| 44 | PT-32, IT-08 | |
| 45 | PT-33, CT-20 | |
| 46 | CT-21 | |
| 47 | PT-34, CT-22 | |
| 48 | PT-35, CT-23 | |
| 49 | PT-36, CT-24 | |
| 50 | PT-37, CT-25 | |
| 51 | PT-38, CT-26 | |
| 52 | DT-58..DT-61, PT-39 | |
| 52a | DT-72..DT-74, PT-40, CT-27 | |
| 52b | PT-41, CT-28 | |
| 53 | IT-09a..IT-09g, MT-07 | |
| 54 | static grep CI | `rg "^import (android|androidx)\." shared/feature/lesson-runner/domain` |
| 55 | static grep CI | `rg "getKoin\|koinInject\|inject<" android/feature/lesson-runner` |
| 56 | static grep CI | no bidirectional import |
| 57 | static grep CI | quizzes-screen imports lesson-runner, not reverse |
| 58 | static grep CI | `rg "@(Inject|HiltAndroidApp)"` |
| 59 | static grep CI | no direct Firestore write in lesson-runner |
| 60 | DT-01..DT-89 | Walking Skeleton domain tests |
| 61 | PT-01..PT-43 | |
| 62 | CT-01..CT-31 | |
| 63 | build gate | `./gradlew :shared:feature:lesson-runner:domain:jvmTest` |
| 64 | build gate | `./gradlew assemble` |
| 65 | build gate | `./gradlew test && ./gradlew allTests` |
