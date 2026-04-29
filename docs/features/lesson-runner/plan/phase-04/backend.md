---
phase: phase-04
role: backend-dev
---

# Phase 04 — Backend Tasks

## Pattern Invariants

- `Channel<RunnerEvent>(capacity = Channel.BUFFERED)` + `receiveAsFlow()` — per `DefaultRootComponent.kt:113-114`
- `instanceKeeper.getOrCreate("runner_state_holder") { RunnerStateHolder(...) }` — Decompose 3.1.0 API
- `lifecycle.doOnStop {}` / `lifecycle.doOnResume {}` — Essenty API (НЕ Activity lifecycle callbacks)
- `lifecycle.doOnDestroy { _events.close() }` — cleanup channel on destroy
- RepositoryImpl не в presentation (layer boundary); component получает use cases через constructor

---

## Create `LessonRunnerRootComponent` interface in `android/feature/lesson-runner/presentation`

> ADR-LR-16 fix: interface живёт в `lesson-runner/presentation`, НЕ в `android/core/navigation`. Старый план создавал circular Gradle dep: `core/navigation → lesson-runner/presentation` (для RunnerUiState/RunnerEvent) + `lesson-runner/presentation → core/navigation` (для interface impl). Cycle = build failure. Canonical path — `06-api-contract.md:284`.

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt`
- **Тип:** interface
- **Сигнатура:** `interface LessonRunnerRootComponent`
- **Вход:** N/A — interface definition
- **Поведение / Выход:**
  - `val uiState: StateFlow<RunnerUiState>`
  - `val events: Flow<RunnerEvent>` — `receiveAsFlow()` pattern per `DefaultRootComponent.kt:114`
  - `fun onAnswer(answer: UserAnswerDraft)` — submit answer
  - `fun onTimeout()` — timer expired
  - `fun onContinue()` — dialog "Продолжить" tapped
  - `fun onExit()` — dialog "Выйти" tapped
  - `fun onCrossButtonTap()` — крестик tapped (shows exit confirm dialog)
  - `fun onCrossConfirmed()` — confirm exit
  - `fun onCrossCancelled()` — cancel exit dialog
  - `fun onSubmitRating(rating: Int)` — rating submitted (1, 2 or 3)
  - `fun onFinish()` — Завершить button; emits `RunnerEvent.NavigateBack` → `LessonRunnerScreen` LaunchedEffect → `onNavigateBack()` → `popCurrentChild()`
  - `fun onBack()` — InitFailed / SaveFailed back tap; emits `RunnerEvent.NavigateBack` (terminal state back path per `06-api-contract.md:306`)
- **Edge cases:**
  - `android/core/navigation/` НЕ затронут этой фичей — никаких новых Kotlin файлов там не создаётся (ADR-LR-16)
  - `UserAnswerDraft` — domain sealed class из `shared/feature/lesson-runner/domain/`; допустим в presentation interface (presentation знает domain)
  - `StackNavigation<QuizzesConfig>` НЕ в конструкторе — navigation A2 hybrid: component emits `RunnerEvent.NavigateBack`; `LessonRunnerScreen` calls `onNavigateBack()` callback from `QuizzesScreen`
- **Depends on:** `RunnerUiState`, `RunnerEvent` (created in this phase), `UserAnswerDraft` (domain)
- **Canonical reference:** `06-api-contract.md:284`
- **Rationale:** Interface живёт там где его typed state (`RunnerUiState`, `RunnerEvent`) — нет circular dep. ADR-LR-16 принят 2026-04-27 для устранения Gradle cycle (Codex plan-round-1 blocker 1 resolved).

---

## Create `LessonRunnerComponentFactory` in `android/feature/lesson-runner/presentation`

> ADR-LR-16 fix: factory живёт в `lesson-runner/presentation`, НЕ в `android/core/navigation`. Canonical path — `06-api-contract.md:354`.

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt`
- **Тип:** fun interface
- **Сигнатура:** `fun interface LessonRunnerComponentFactory { fun create(componentContext: ComponentContext, lessonId: LessonId, mode: Difficulty): LessonRunnerRootComponent }`
- **Вход:** `componentContext: ComponentContext`, `lessonId: LessonId`, `mode: Difficulty`
- **Поведение / Выход:**
  - `fun interface` — single abstract method, lambda-compatible
  - Return type `LessonRunnerRootComponent` (interface from lesson-runner/presentation)
  - **Binding owner: phase-07 composition root** — `single<LessonRunnerComponentFactory> { LessonRunnerComponentFactory { ctx, lessonId, mode -> getKoin().get(parametersOf(ctx, lessonId, mode)) } }` регистрируется в `apps/android-next/.../AppApplication.kt` или dedicated module добавляемом в `startKoin { modules(...) }`. Phase-06 НЕ владеет binding'ом — phase-06 только потребляет `get<LessonRunnerComponentFactory>()` в `DefaultQuizzesComponent`. См. `phase-07/backend.md` factory registration task.
  - `DefaultQuizzesComponent.createChild` calls `factory.create(ctx, LessonId(config.lessonId), config.mode)`
- **Edge cases:**
  - Signature: `lessonId: LessonId` (НЕ `String`) — factory принимает domain type; `DefaultQuizzesComponent` конвертирует `config.lessonId: String` → `LessonId(config.lessonId)` при вызове factory
  - `mode: Difficulty` — после Phase-01 `@Serializable` fix
  - `titles: List<String>` — НЕ включён в factory params; `DefaultLessonRunnerRootComponent` получает breadcrumb info через `config.titles` при необходимости; упрощение против old plan (titles — для display only)
- **Depends on:** `LessonRunnerRootComponent`, `LessonId` (domain), `Difficulty` (core/question-schema), Decompose `ComponentContext`
- **Canonical reference:** `06-api-contract.md:354`
- **Rationale:** Factory в `lesson-runner/presentation` — одностороннее направление `quizzes-screen/presentation → lesson-runner/presentation` (ADR-LR-16, ADR-LR-17); `android/core/navigation/` не затронут (Open Question 1 RESOLVED)

---

## Create `android/feature/lesson-runner/presentation` build script

- **Файл:** `android/feature/lesson-runner/presentation/build.gradle.kts`
- **Тип:** build script
- **Сигнатура:** Android library module
- **Вход:** N/A
- **Поведение / Выход:**
  - `android { }` library plugin
  - `dependencies`: `:shared:feature:lesson-runner:domain`, `:android:core:designsystem`, Decompose, Essenty, Koin Android, Compose (for Phase-05 Compose files)
  - НЕ добавлять `:android:core:navigation` в dependencies — `LessonRunnerRootComponent` и `LessonRunnerComponentFactory` живут в этом модуле, не в core/navigation (ADR-LR-16)
  - `testImplementation`: JUnit4, coroutines-test
  - Pattern: аналогично `android/feature/quizzes-screen/presentation/build.gradle.kts`
- **Depends on:** existing build catalog
- **Canonical reference:** internal
- **Rationale:** Новый Android feature module; deps per ADR-LR-16 (no core/navigation dep)

---

## Create `RunnerUiState`

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/.../state/RunnerUiState.kt`
- **Тип:** sealed interface
- **Сигнатура:** `sealed interface RunnerUiState`
- **Вход:** N/A — type definition
- **Поведение / Выход:**
  - `data object Loading : RunnerUiState`
  - `data class InitFailed(val reason: InitFailureReason) : RunnerUiState`
  - `data class Question(val questionUiState: QuestionUiState, val indexInPool: Int, val totalInPool: Int, val deadlineMs: Long, val isPaused: Boolean, val isHard: Boolean, val showExitConfirmDialog: Boolean) : RunnerUiState`
  - `data class Result(val attempt: Attempt, val lessonAverageRating: Float?, val lessonRatingCount: Int, val top3: List<TopParticipant>, val userAttemptCount: Int, val userAveragePercentScore: Int, val showRatingPrompt: Boolean, val saveWarning: Boolean) : RunnerUiState`
  - `enum class InitFailureReason { AuthRequired, LessonNotFound, EmptyPool, NoValidQuestions }`
  - Exact fields per `06-api-contract.md:386`
- **Edge cases:**
  - `Attempt` — domain type (из lesson-runner/domain); допустим в presentation state (presentation знает domain)
  - `TopParticipant` — из `shared/core/leaderboard` (Core, допустимо)
  - `deadlineMs: Long` — абсолютное Unix millis; UI вычитает `System.currentTimeMillis()` для display
- **Depends on:** `Attempt` (domain), `TopParticipant` (core/leaderboard), `QuestionUiState`
- **Canonical reference:** `06-api-contract.md:386`
- **Rationale:** Presentation layer state; mapped from domain `RunnerState` by component

---

## Create `QuestionUiState`

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/.../state/QuestionUiState.kt`
- **Тип:** sealed interface
- **Сигнатура:** `sealed interface QuestionUiState`
- **Вход:** N/A
- **Поведение / Выход:**
  - Per `06-api-contract.md:423` — точная сигнатура с `SingleChoice`, `MultipleChoice`, `Ordering`, `FillBlank`
  - `data class OptionUi(val id: String, val text: String)` — companion type
  - `sealed interface TemplatePart { data class Text(val content: String); data class Blank(val index: Int, val placeholder: String) }` — for FillBlank
  - Common properties: `questionText: String`, `hasImage: Boolean`
- **Edge cases:**
  - `selectedOptionId: String?` — nullable (nothing selected yet in SingleChoice)
  - `selectedIds: Set<String>` — empty Set for MultipleChoice initial state
  - `filledValues: Map<Int, String>` — empty Map for FillBlank initial state
  - `items: List<OptionUi>` для Ordering — current user order (mutable in component state)
- **Depends on:** N/A
- **Canonical reference:** `06-api-contract.md:423`
- **Rationale:** Per-question-type UI state; avoids mega-sealed with optional fields

---

## Create `RunnerEvent`

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/.../event/RunnerEvent.kt`
- **Тип:** sealed interface
- **Сигнатура:** `sealed interface RunnerEvent`
- **Вход:** N/A
- **Поведение / Выход:**
  - `data class SaveAttemptFailed(val error: SaveError) : RunnerEvent`
  - `data object SaveRatingFailed : RunnerEvent`
  - `data object NavigateBack : RunnerEvent` — navigation A2 hybrid: emitted by `onFinish()`/`onExit()`/`onCrossConfirmed()`/`onBack()`; collected by `LessonRunnerScreen` LaunchedEffect → `onNavigateBack()` callback → host `popCurrentChild()`
  - `enum class SaveError { IoError, Unknown }`
  - Per `07-events.md:21` (NavigateBack canonical) + Event Types
- **Edge cases:**
  - `SaveAttemptFailed` при `LessonAttemptRepository.save()` → `Result.failure`
  - `SaveRatingFailed` при `LessonRatingRepository.submit()` → `Result.failure`
- **Depends on:** N/A
- **Canonical reference:** `07-events.md`
- **Rationale:** One-way error feedback per `07-events.md` pattern

---

## Create `DefaultLessonRunnerRootComponent`

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/.../component/DefaultLessonRunnerRootComponent.kt`
- **Тип:** class
- **Сигнатура:** `class DefaultLessonRunnerRootComponent(componentContext: ComponentContext, lessonId: LessonId, mode: Difficulty, startAttemptUseCase: StartLessonAttemptUseCase, completeAttemptUseCase: CompleteAttemptUseCase, abortAttemptUseCase: AbortAttemptUseCase, submitRatingUseCase: SubmitLessonRatingUseCase, lessonRepository: LessonRepository, attemptRepository: LessonAttemptRepository, clock: Clock) : ComponentContext by componentContext, LessonRunnerRootComponent`
- **Вход:** use cases (from Koin injection), repository deps, lessonId/mode (from factory call)
- **Замечание:** `StackNavigation<QuizzesConfig>` НЕ в конструкторе — `lesson-runner/presentation` не знает о QuizzesConfig (нарушало бы unidirectional dep). `onExit()` / `onFinish()` сигнализируют через `RunnerEvent.NavigateBack` (см. `07-events.md`), который `QuizzesScreen` слушает и вызывает `navigation.pop()` на своей стороне.
- **Поведение / Выход:**
  - `private val _state: MutableStateFlow<RunnerUiState> = MutableStateFlow(RunnerUiState.Loading)` в `RunnerStateHolder` через `instanceKeeper`
  - `override val uiState: StateFlow<RunnerUiState> = stateHolder.state.asStateFlow()`
  - `private val _events = Channel<RunnerEvent>(Channel.BUFFERED)`
  - `override val events: Flow<RunnerEvent> = _events.receiveAsFlow()`
  - `init { lifecycle.doOnDestroy { _events.close() }; lifecycle.doOnStop { onPaused() }; lifecycle.doOnResume { /* UI observes isPaused, shows dialog */ }; launch StartLessonAttemptUseCase }`
  - `onAnswer(answer)` → `RunnerLogic.submitAnswer(state, answer, nowMs)` → update state
  - `onTimeout()` → `RunnerLogic.autoAnswerOnTimeout(state, seed, nowMs)` → update; if last question → CompleteAttemptUseCase
  - `onContinue()` → `state.copy(isPaused = false)` → advance to next question
  - `onExit()` → `AbortAttemptUseCase(state)` → emit `RunnerEvent.NavigateBack`
  - `onCrossButtonTap()` → `state.copy(showExitConfirmDialog = true)`
  - `onCrossConfirmed()` → same as exit-via-abort; `AbortAttemptUseCase` → emit `RunnerEvent.NavigateBack`
  - `onCrossCancelled()` → `state.copy(showExitConfirmDialog = false)`
  - `onSubmitRating(rating)` → `SubmitLessonRatingUseCase(userId, lessonId, rating)` → failure → emit `SaveRatingFailed`
  - `onFinish()` → emit `RunnerEvent.NavigateBack` (A2 hybrid: consumer `LessonRunnerScreen` calls `onNavigateBack()`)
  - `onBack()` → emit `RunnerEvent.NavigateBack` (InitFailed/SaveFailed terminal back path per `06-api-contract.md:306`)
  - After `startAttemptUseCase` completes → `RunnerState.Ready` → update `_state` → `RunnerUiState.Question`
  - After `completeAttemptUseCase` → `RunnerState.Completed(attempt, ratingPrompt)` → read Lesson + stats → `RunnerUiState.Result`
  - All business logic in component coroutine scope (via `coroutineScope { }` or `lifecycle.doOnCreate { launch { ... } }`)
- **Edge cases:**
  - `RunnerState.InitFailed` → `RunnerUiState.InitFailed` (empty pool, auth failed, lesson not found)
  - `CompleteAttemptUseCase` returns `RunnerState.SaveFailed` → emit `SaveAttemptFailed` event + show `RunnerUiState.Result(saveWarning=true)`
  - Rotation: `instanceKeeper` → same `RunnerStateHolder` reused → no duplicate `StartLessonAttemptUseCase` call
  - `onPaused()`: если `state.is Ready` → `RunnerLogic.autoAnswerOnTimeout(...)` → `state.copy(isPaused=true, indexInPool++)`
  - `doOnResume` callback: component sets no flag; UI observes `state.isPaused == true` → shows dialog
- **Depends on:** `LessonRunnerRootComponent` (interface), all use cases (Walking Skeleton), `RunnerLogic` (Walking Skeleton), `RunnerUiState`, `RunnerStateMapper`, `RunnerEvent`
- **Canonical reference:** `06-api-contract.md:284`, `02-behavior.md DFD 2-4`
- **Rationale:** Central gameplay loop; instanceKeeper для rotation persistence per ADR (Options Considered)

---

## Create `RunnerStateMapper`

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/.../mapper/RunnerStateMapper.kt`
- **Тип:** top-level functions
- **Сигнатура:** `fun RunnerState.Ready.toQuestionUiState(): RunnerUiState.Question` и `fun QuestionContent.toQuestionUiState(...): QuestionUiState`
- **Вход:** domain `RunnerState` + domain `QuestionContent`
- **Поведение / Выход:**
  - Maps `RunnerState.Ready` → `RunnerUiState.Question(questionUiState, indexInPool, totalInPool, deadlineMs, isPaused, isHard, showExitConfirmDialog)`
  - `QuestionContent.SingleChoice` → `QuestionUiState.SingleChoice(options=[OptionUi(id,text),...], selectedOptionId=null initially)`
  - `QuestionContent.MultipleChoice` → `QuestionUiState.MultipleChoice(options=..., selectedIds=emptySet())`
  - `QuestionContent.Ordering` → `QuestionUiState.Ordering(items=OptionUi list in current order)`
  - `QuestionContent.FillBlank` → `QuestionUiState.FillBlank(templateParts=[Text/Blank...], filledValues=emptyMap())`
  - `RunnerState.Completed` → `RunnerUiState.Result(attempt, lessonAverageRating, top3, ...stats...)` (stats from separate Lesson read)
- **Edge cases:**
  - `QuestionContent` exhaustive `when` — compile check for all 4 types
  - FillBlank template parsing: разбить `questionText` по `___` маркерам → list of `TemplatePart.Text` и `TemplatePart.Blank`
  - `questionText` без `___` маркеров → единственный `Text` part (FillBlank degenerate case)
- **Depends on:** `RunnerState` (Walking Skeleton), `QuestionContent` (core/question-schema), `RunnerUiState`, `QuestionUiState`
- **Canonical reference:** `06-api-contract.md:386`, `06-api-contract.md:423`
- **Rationale:** Clean separation: component owns business logic, mapper owns UI state transformation

---

## Create `lessonRunnerPresentationModule`

- **Файл:** `android/feature/lesson-runner/presentation/src/main/kotlin/.../di/LessonRunnerPresentationModule.kt`
- **Тип:** top-level val (Koin module)
- **Сигнатура:** `val lessonRunnerPresentationModule = module { ... }`
- **Вход:** N/A
- **Поведение / Выход:**
  - `factory { (ctx: ComponentContext, lessonId: LessonId, mode: Difficulty) -> DefaultLessonRunnerRootComponent(componentContext = ctx, lessonId = lessonId, mode = mode, startAttemptUseCase = get(), completeAttemptUseCase = get(), abortAttemptUseCase = get(), submitRatingUseCase = get(), lessonRepository = get(), attemptRepository = get(), clock = get()) as LessonRunnerRootComponent }`
  - `parametersOf(ctx, lessonId, mode)` — per canonical `06-api-contract.md:602`
  - НЕ включает `navigation: StackNavigation<QuizzesConfig>` — `DefaultLessonRunnerRootComponent` не знает о QuizzesConfig; navigation происходит через `RunnerEvent.NavigateBack` (consumer side pop, A2 hybrid)
- **Edge cases:**
  - `as LessonRunnerRootComponent` — explicit cast; factory возвращает interface type
  - `LessonRunnerComponentFactory` lambda binding живёт в `AppApplication.kt` (Phase-07 composition root per `06-api-contract.md:374`); `lessonRunnerPresentationModule` — только `DefaultLessonRunnerRootComponent` factory
- **Depends on:** `DefaultLessonRunnerRootComponent`, use cases (lessonRunnerDomainKoinAdapter, Phase-03), `LessonRepository`, `LessonAttemptRepository`, `Clock`
- **Canonical reference:** `06-api-contract.md:602`
- **Rationale:** Navigation decoupled from component (ADR-LR-16): component не знает о StackNavigation; pop-сигнал через event channel (A2 hybrid per `07-events.md:13`); Open Question 2 RESOLVED
