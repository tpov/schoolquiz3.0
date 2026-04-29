---
date: 2026-04-27
authors: architect-high-level (§LR-1..§LR-7), architect-component (§LR-8..N)
feature: lesson-runner
---

# API Contract: Lesson Runner

Canonical signatures — единственный источник правды. `01-architecture.md` и `02-behavior.md` ссылаются на этот файл описательно.

Walking Skeleton types (Repository interfaces, Use Cases, RunnerState, Attempt, etc.) — canonical в `0-spec.md §Feature Domain Contract`. Этот файл документирует **только types вводимые design phase** плюс Firestore/Room contracts.

---

<!-- HL_SECTION_START: §LR-1..§LR-7 (architect-high-level writes here) -->

## §LR-1 QuizzesConfig.LessonRunner — новый вариант sealed class

**File**: `android/feature/quizzes-screen/presentation/.../config/QuizzesConfig.kt`

Добавляется в существующий `@Serializable sealed class QuizzesConfig` (см. `docs/features/quizzes-screen/06-api-contract.md §10`).

```kotlin
@Serializable
data class LessonRunner(
    val lessonId: String,        // LessonId.value — raw String (XxxId не @Serializable, per QS ADR-QS-02)
    val mode: Difficulty,        // требует @Serializable на Difficulty (ADR-LR-06)
    val titles: List<String>,    // полный breadcrumb путь включая lesson title последним
) : QuizzesConfig()
```

**Titles accumulation** (extends pattern из `docs/features/quizzes-screen/06-api-contract.md §10`):
```
LessonListComponent.onLessonClick(lessonItem)
  → pushNew(QuizzesConfig.LessonRunner(
        lessonId  = lessonItem.id,
        mode      = if (isHardChecked && hardUnlocked) HARD else EASY,
        titles    = config.titles + [lessonItem.title],
    ))
```

**Удаляется**: `QuizzesConfig.LessonPlaceholder` (ADR-LR-07) — compile-error-safe, все exhaustive `when` ветви обновляются атомарно.

---

## §LR-2 QuizzesChild.LessonRunner — новый вариант sealed interface

**File**: `android/feature/quizzes-screen/presentation/.../component/QuizzesChild.kt`

```kotlin
data class LessonRunner(val component: LessonRunnerRootComponent) : QuizzesChild
```

**Удаляется**: `QuizzesChild.LessonPlaceholder` (ADR-LR-07).

`LessonRunnerRootComponent` interface живёт в `android/feature/lesson-runner/presentation/` (ADR-LR-16). `val component: LessonRunnerRootComponent` импортируется из `lesson-runner/presentation`, не из `core/navigation`. Canonical signature — `06-api-contract.md §LR-9`.

---

## §LR-3 Firestore Collection: `lesson_attempts/{attemptId}`

Canonical schema. Пишется cascade sync infrastructure (out of scope этой фичи). Room является единственным local store; sync читает Room и публикует в Firestore.

```
Collection: lesson_attempts
Document ID: attemptId (UUID, = Attempt.id.value после rename raw→value)

Fields:
  userId:       String      — Firebase Auth UID (= Attempt.userId)
  lessonId:     String      — LessonId.value (= Attempt.lessonId.value)
  lessonVersion: Long       — snapshot на старте попытки (= Attempt.lessonVersion)
  hardQuestion: Boolean     — true = HARD mode (= Attempt.mode == Difficulty.HARD)
                              [USER DECIDED: поле "hardQuestion", не "hardQuiz"]
  completedAt:  Timestamp   — serverTimestamp при write (derived from Attempt.completedAt for client-set reference)
  codeAnswer:   String      — (= Attempt.codeAnswer.raw)
  percentScore: Int         — 0..100 (= Attempt.percentScore.raw)
  lastModifiedAt: Timestamp — serverTimestamp при write (cascade sync cursor field)
  version:      Long        — 1 (immutable после создания, = 1 всегда)
```

**lastModifiedAt strategy**: cascade sync writer добавляет `FieldValue.serverTimestamp()` отдельно от domain `Attempt.completedAt` (Open Q #10, Recommendation B из research). Это защищает от device clock drift при cursor sync. `Attempt.completedAt` — client Unix millis timestamp (для domain ordering); `lastModifiedAt` — server timestamp (для sync cursor).

**Immutability**: document создаётся один раз (write-only с клиента). Нет update/delete operations с клиента. Cloud Function (out of scope) читает документы для агрегации `Lesson.top3`.

---

## §LR-4 Firestore Collection: `lesson_ratings/{ratingId}`

Canonical schema. Write-only с клиента.

```
Collection: lesson_ratings
Document ID: ratingId = sha256("$userId:$lessonId")
             (deterministic; Cloud Function dedupe through document ID collision)

Fields:
  userId:       String      — Firebase Auth UID
  lessonId:     String      — LessonId.value
  lessonVersion: Long       — snapshot на момент submit (analytics, не uniqueness key)
  rating:       Int         — 1, 2, или 3 (integer stars)
  ratedAt:      Timestamp   — serverTimestamp при write
  lastModifiedAt: Timestamp — serverTimestamp при write
  version:      Long        — 1 (immutable)
```

**Uniqueness**: `ratingId = sha256(userId:lessonId)` обеспечивает uniqueness per (userId, lessonId). Если пользователь попробует создать повторно (race condition или retry) — Cloud Function видит существующий document и игнорирует (document ID collision = implicit dedup). Нет update semantics.

**lessonVersion** в payload — для server analytics (понять какую версию оценивали). Не участвует в ключе — один раз оценил урок всегда, независимо от версии (см. `0-spec.md Business Rule 24`).

---

## §LR-5a TopParticipant — canonical type

**File**: `shared/core/leaderboard/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/leaderboard/TopParticipant.kt`

```kotlin
@Serializable
data class TopParticipant(
    val nickname: String,
    val avatarUrl: String?,
    val percent: Int,          // 0..100 — percentScore значение
)
```

Используется в `Lesson.top3: List<TopParticipant>` (§LR-5) и в result screen presentation. `@Serializable` — обязательно для `TopParticipantListConverter` (Room JSON) и Firestore mapping. Допустимо в `shared/core/leaderboard/` (core module, не feature). Перемещён из `lesson-runner:domain` (ADR-LR-05).

---

## §LR-5 Расширение `Lesson` (Firestore + domain)

**Domain file**: `shared/feature/lesson/domain/src/commonMain/.../model/Lesson.kt`

Существующие поля (`id`, `themeId`, `title`, `order`, `version`, `contentsVersion`, `lastModifiedAt`, `archived`) — без изменений. Добавляются:

```
+ averageRating: Float?    — средняя оценка (1.0..3.0). null пока нет ни одной оценки.
                            Naming: align с Quest.averageRating (non-null pattern: use Float? nullable)
+ ratingCount:  Int = 0    — количество оценок. Non-nullable, default 0.
                            Align с Quest.averageRatingCount pattern (ADR-LR-15, user-approved 2026-04-26).
+ top3:         List<TopParticipant>  — ≤ 3 топ-участника. emptyList() если нет данных.
                                        TopParticipant из shared/core/leaderboard/ (ADR-LR-05)
```

**Impact cascade**:

| Файл | Изменение |
|------|-----------|
| `Lesson.kt` | + 3 поля |
| `LessonEntity.kt` | + `averageRating REAL`, `ratingCount INTEGER NOT NULL DEFAULT 0`, `top3 TEXT` (JSON) |
| `LessonMapper.toDomain()` | + map 3 new fields |
| `LessonDtoMapper.toEntity()` | + map 3 new fields, backward compat defaults |
| `LessonDto.kt` | + 3 optional Firestore fields |
| `FirestoreLessonDtoMapper.kt` | + defaults для missing Firestore fields |
| `FakeLessonRepository.kt` (×3 copies) | Nullable defaults → no compile breakage |

**Naming decision**: `ratingCount: Int = 0` (non-nullable, default 0) — align с Quest.averageRatingCount pattern. Spec amendment applied в `0-spec.md §34` (user-approved 2026-04-26, ADR-LR-15).

---

## §LR-6 Firestore Security Rules Contract

**File**: `firestore.rules` — добавить правила для новых collections (out of scope этой фичи, но контракт фиксируется):

```javascript
// lesson_attempts: read-own + create-own + immutable (no update/delete)
match /lesson_attempts/{attemptId} {
  allow read: if request.auth != null
              && resource.data.userId == request.auth.uid;
  allow create: if request.auth != null
                && request.resource.data.userId == request.auth.uid
                && request.resource.data.version == 1;
  allow update, delete: if false;  // immutable after creation
}

// lesson_ratings: read-own + create-own + immutable
match /lesson_ratings/{ratingId} {
  allow read: if request.auth != null
              && resource.data.userId == request.auth.uid;
  allow create: if request.auth != null
                && request.resource.data.userId == request.auth.uid
                && request.resource.data.rating in [1, 2, 3]
                && request.resource.data.version == 1;
  allow update, delete: if false;
}

// lessons: read public + write Cloud Function only (no client write для top3/averageRating)
// (existing rules расширяются, не заменяются)
```

**Deployment blocker**: без этих rules производственный deployment невозможен (Firestore deny-by-default). Контракт фиксируется для server team как отдельная задача.

---

## §LR-7 Cloud Function Triggers Contract (out of scope, контракт для server team)

Эти Cloud Functions **out of scope** для этой фичи. Фиксируется контракт.

### CF-1: `onCreate(lesson_ratings)` → recompute averageRating + ratingCount

```
Trigger: onCreate на collection lesson_ratings
Input: new lesson_rating document
Process:
  1. read ALL lesson_ratings WHERE lessonId == doc.lessonId
  2. compute averageRating = sum(rating) / count
  3. compute ratingCount = count
  4. update lessons/{lessonId}: { averageRating, ratingCount }
Note: для текущей lessonVersion или all-time — server policy (не наша фича)
```

**Client graceful handling без CF**: `Lesson.averageRating == null` → не показывать на result screen.

### CF-2: `onCreate(lesson_attempts)` → recompute top3

```
Trigger: onCreate на collection lesson_attempts
Input: new lesson_attempt document
Process:
  1. read lesson_attempts WHERE lessonId == doc.lessonId
                            AND lessonVersion == doc.lessonVersion
  2. GROUP BY userId → take MAX(percentScore) per user
  3. SORT BY percentScore DESC, take TOP 3
  4. для каждого entry: lookup users/{userId}.nickname + avatarUrl
  5. update lessons/{lessonId}: { top3: [{nickname, avatarUrl, percent}, ...] }
```

**Client graceful handling без CF**: `Lesson.top3.isEmpty()` → Top3 секция скрыта на result screen.

### Composite Firestore Index

```
Collection: lesson_attempts
Index: (userId ASC, lastModifiedAt ASC)
Reason: cascade sync query — where('userId','==',uid).orderBy('lastModifiedAt','>')
Status: manual creation required (не auto-created Firestore)
```

---

## §LR-8 Cross-module Entry Points — DefaultLessonListComponent Update

**File**: `android/feature/quizzes-screen/presentation/.../component/DefaultLessonListComponent.kt`

После phase-01, constructor расширяется двумя новыми зависимостями:

```kotlin
class DefaultLessonListComponent(
    componentContext: ComponentContext,
    config: QuizzesConfig.LessonList,               // existing pattern (ADR-QS-13)
    private val lessonRepository: LessonRepository,  // existing
    private val lessonAttemptRepository: LessonAttemptRepository,  // NEW (ADR-QS-16)
    private val authRepository: AuthRepository,      // NEW (для userId)
    private val navigation: StackNavigation<QuizzesConfig>,  // existing
) : ComponentContext by componentContext, LessonListComponent
```

`LessonAttemptRepository` — из `shared/feature/lesson-runner/domain/` (cross-feature import, ADR-QS-16).

**DefaultQuizzesComponent update**: получает `lessonAttemptRepository` и `authRepository` в конструктор → передаёт в `childFactory` при создании `DefaultLessonListComponent`.

**quizzesPresentationModule** (Koin) update:
```kotlin
factory<QuizzesComponent> { (ctx: ComponentContext) ->
    DefaultQuizzesComponent(
        componentContext = ctx,
        questRepository = get(),
        sectionRepository = get(),
        themeRepository = get(),
        lessonRepository = get(),
        lessonAttemptRepository = get(),  // NEW
        authRepository = get(),           // NEW
    )
}
```

Full canonical `DefaultQuizzesComponent` signature — architect-component зона.

<!-- HL_SECTION_END -->

---

<!-- CMP_SECTION_START: §LR-9..N (architect-component writes here) -->

## §LR-9 LessonRunnerRootComponent — interface

**File**: `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt`

**Location rationale** (ADR-LR-16): interface декларирует `uiState: StateFlow<RunnerUiState>` и `events: Flow<RunnerEvent>`, оба типа принадлежат `lesson-runner/presentation`. Хранить interface в `core/navigation` создаёт cycle: `core/navigation → lesson-runner/presentation` (для state types) + `lesson-runner/presentation → core/navigation` (для interface). Cycle = Gradle build failure. Interface живёт там же где его typed state.

**ВАЖНО**: interface НЕ живёт в `android/core/navigation/`. Любая ссылка в других docs на `core/navigation/LessonRunnerRootComponent` должна быть обновлена.

```kotlin
interface LessonRunnerRootComponent {
    val uiState: StateFlow<RunnerUiState>
    val events: Flow<RunnerEvent>          // receiveAsFlow() pattern

    fun onAnswer(answer: UserAnswerDraft)
    fun onTimeout()
    fun onContinue()
    fun onExit()
    fun onCrossButtonTap()
    fun onCrossConfirmed()
    fun onCrossCancelled()
    fun onSubmitRating(rating: Int)
    fun onFinish()
    fun onBack()                           // InitFailed/SaveFailed → emit RunnerEvent.NavigateBack
}
```

**Implementation note** (DefaultLessonRunnerRootComponent):
```kotlin
private val _events = Channel<RunnerEvent>(Channel.BUFFERED)
override val events: Flow<RunnerEvent> = _events.receiveAsFlow()
```
Pattern per `DefaultRootComponent.kt:113-114`.

---

### LessonRunnerScreen — Compose signature (navigation contract A2)

**Navigation decision** (ADR-LR-17): Component не вызывает `navigation.pop()` напрямую (нет `StackNavigation` в конструкторе). Навигация назад реализована через Compose callback. `QuizzesScreen` передаёт `onNavigateBack = { component.popCurrentChild() }` при рендере. Component сигнализирует готовность к выходу через механизм в своей зоне (arch-cmp уточняет: state `Dismissed` или dedicated flow — canonical в CMP_SECTION `06-api-contract.md`). `LessonRunnerScreen` реагирует на сигнал вызовом callback.

```kotlin
@Composable
fun LessonRunnerScreen(
    component: LessonRunnerRootComponent,
    onNavigateBack: () -> Unit,
    onSegmentClick: (Int) -> Unit,
)
```

**QuizzesScreen rendering** (в `quizzes-screen/presentation`):
```kotlin
is QuizzesChild.LessonRunner ->
    LessonRunnerScreen(
        component = active.component,
        onNavigateBack = { component.popCurrentChild() },
        onSegmentClick = component::popToLevel,
    )
```

**QuizzesComponent interface** — добавить:
```kotlin
fun popCurrentChild()  // DefaultQuizzesComponent: navigation.pop()
```

**Notes**:
- `events: Flow<RunnerEvent>` — `ReceiveChannel` НЕ используется (C5: `DefaultRootComponent.kt:114` precedent). canonical в `07-events.md`
- `uiState: StateFlow<RunnerUiState>` — StateFlow (not `Value<T>`). `DefaultRootComponent` использует `MutableStateFlow` internal + `asStateFlow()` (интерфейс `RootComponent.kt:21` объявляет `Flow<AppShellState>`). Lesson-runner выбирает `StateFlow` для немедленного доступа к `.value` без suspension — dominant pattern проекта.
- `UserAnswerDraft` — domain sealed class (`shared/feature/lesson-runner/domain/src/.../model/UserAnswerDraft.kt`)

---

## §LR-9a LessonRunnerComponentFactory — factory contract

**Location decision** (ADR-LR-16): Factory interface живёт в `android/feature/lesson-runner/presentation/`, НЕ в `android/core/navigation/`.

**File**: `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt`

```kotlin
fun interface LessonRunnerComponentFactory {
    fun create(
        componentContext: ComponentContext,
        lessonId: LessonId,
        mode: Difficulty,
    ): LessonRunnerRootComponent
}
```

**Rationale**: `quizzes-screen/presentation` импортирует factory из `lesson-runner/presentation` (одностороннее направление). Альтернативный вариант — factory в `core/navigation` — потребовал бы `core/navigation → lesson-runner/presentation` зависимость для return type `LessonRunnerRootComponent`. Это нарушает `clean-architecture.md`: core не зависит от product features. Текущее решение: `quizzes-screen/presentation → lesson-runner/presentation` — одна ориентация, допустима при наличии ADR.

**`android/core/navigation/` остаётся пустым** в рамках этой фичи (нет Kotlin source files). Factory в `core/navigation` добавляется только если появится второй consumer вне `quizzes-screen` — это отдельное ADR решение.

**Koin wiring** (в `apps/android-next/AppApplication.kt`, composition root):
```kotlin
single<LessonRunnerComponentFactory> {
    LessonRunnerComponentFactory { ctx, lessonId, mode ->
        getKoin().get(parametersOf(ctx, lessonId, mode))
    }
}
```
Фактическое создание `DefaultLessonRunnerRootComponent` делегируется `lessonRunnerPresentationModule` (§LR-15).

---

## §LR-10 RunnerUiState — presentation sealed interface

**File**: `android/feature/lesson-runner/presentation/src/main/kotlin/.../state/RunnerUiState.kt`

```kotlin
sealed interface RunnerUiState {
    data object Loading : RunnerUiState

    data class InitFailed(
        val reason: InitFailureReason,
    ) : RunnerUiState

    data class Question(
        val questionUiState: QuestionUiState,
        val indexInPool: Int,
        val totalInPool: Int,
        val deadlineMs: Long,
        val isPaused: Boolean,
        val isHard: Boolean,
        val showExitConfirmDialog: Boolean,
    ) : RunnerUiState

    // **Superseded by ADR-LR-19**: actual Result uses flat projection per Phase-04 security review.
    // PII fields (userId, codeAnswer, attemptId) from Attempt must not appear in public StateFlow.
    // See 03-decisions.md ADR-LR-19. Actual implementation in RunnerUiState.kt:24-37.
    data class Result(
        val attempt: Attempt,
        val lessonAverageRating: Float?,
        val lessonRatingCount: Int,
        val top3: List<TopParticipant>,
        val userAttemptCount: Int,
        val userAveragePercentScore: Int,
        val showRatingPrompt: Boolean,
        val saveWarning: Boolean,
    ) : RunnerUiState
}
```

---

## §LR-11 QuestionUiState — per-type sealed interface

**File**: `android/feature/lesson-runner/presentation/src/main/kotlin/.../state/QuestionUiState.kt`

```kotlin
sealed interface QuestionUiState {
    val questionText: String
    val hasImage: Boolean

    data class SingleChoice(
        override val questionText: String,
        override val hasImage: Boolean,
        val imageUrl: String?,
        val options: List<OptionUi>,
        val selectedOptionId: String?,
    ) : QuestionUiState

    data class MultipleChoice(
        override val questionText: String,
        override val hasImage: Boolean,
        val imageUrl: String?,
        val options: List<OptionUi>,
        val selectedIds: Set<String>,
    ) : QuestionUiState

    data class Ordering(
        override val questionText: String,
        override val hasImage: Boolean,
        val imageUrl: String?,
        val items: List<OptionUi>,
    ) : QuestionUiState

    data class FillBlank(
        override val questionText: String,
        override val hasImage: Boolean,
        val imageUrl: String?,
        val templateParts: List<TemplatePart>,
        val filledValues: Map<Int, String>,
    ) : QuestionUiState
}

data class OptionUi(val id: String, val text: String)

sealed interface TemplatePart {
    data class Text(val content: String) : TemplatePart
    data class Blank(val index: Int, val placeholder: String) : TemplatePart
}
```

---

## §LR-12 LessonItemUi — карточка урока с progress

**File**: `android/feature/quizzes-screen/presentation/src/main/kotlin/.../uistate/LessonItemUi.kt`

```kotlin
data class LessonItemUi(
    val id: String,
    val title: String,
    val orderLabel: String? = null,     // nullable — matches HierarchyItemUi.kt:6 pattern
    val subtitleCount: String? = null,  // nullable — matches HierarchyItemUi.kt:7 pattern
    val bestStarsRawTenths: Int = 0,    // 0..30; StarRating(rating = raw/10f); "rawTenths" per Stars(rawTenths) domain
    val hardUnlocked: Boolean = false,
    val isHardChecked: Boolean = false, // ephemeral UI state; NOT persisted
)
```

`isHardChecked` — отслеживается в component как `MutableStateFlow<Set<String>>` (набор lessonId), не в Room.

---

## §LR-13 lessonRunnerDataModule — canonical Koin module

**File**: `shared/feature/lesson-runner/data/src/androidMain/kotlin/.../di/LessonRunnerDataModule.kt`

```kotlin
val lessonRunnerDataModule = module {
    single<AttemptIdProvider> { DefaultAttemptIdProvider() }
    single<RandomSeedProvider> { DefaultRandomSeedProvider() }
    single<RatingIdProvider> { DefaultRatingIdProvider() }

    single<LessonAttemptRepository> {
        LessonAttemptRepositoryImpl(attemptDao = get())
    }
    single<LessonRatingRepository> {
        LessonRatingRepositoryImpl(ratingLocalDao = get())
    }
}
```

`attemptDao` / `ratingLocalDao` — provided by existing `persistenceModule`.

**`QuestionContentParser` binding**: НЕ в `lessonRunnerDataModule`. Живёт в `questionSchemaModule` (новый Koin module в `shared/core/question-schema/src/androidMain/.../di/`):

```kotlin
val questionSchemaModule = module {
    single<QuestionContentParser> { KotlinxSerializationQuestionContentParser() }
}
```

`lessonRunnerDomainKoinAdapter` resolves `get<QuestionContentParser>()` транзитивно через `questionSchemaModule` (per ADR-LR-08: parser — shared core infrastructure, not feature-specific). `questionSchemaModule` добавляется в `AppApplication.kt` вместе с остальными modules.

---

## §LR-13a Provider interfaces — domain layer

**File**: `shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/`

```kotlin
interface AttemptIdProvider {
    fun next(): AttemptId
}

interface RandomSeedProvider {
    fun next(): Long
}

interface RatingIdProvider {
    fun provide(userId: String, lessonId: LessonId): RatingId
}
```

**Location rationale** (ADR-LR-09): Provider interfaces в domain — domain use cases зависят от них через constructor injection. Default implementations (`DefaultAttemptIdProvider`, `DefaultRandomSeedProvider`, `DefaultRatingIdProvider`) живут в `shared/feature/lesson-runner/data/src/androidMain/…/provider/`.

---

## §LR-14 lessonRunnerDomainKoinAdapter — domain use case wiring

**File**: `shared/feature/lesson-runner/data/src/androidMain/kotlin/.../di/LessonRunnerDomainKoinAdapter.kt`

Bridges wrapper interfaces (ADR-LR-09) to domain `() -> T` function types. Живёт в **data** (C1 fix: domain не должен содержать Koin adapter):

```kotlin
val lessonRunnerDomainKoinAdapter = module {
    factory<StartLessonAttemptUseCase> {
        StartLessonAttemptUseCase(
            questionRepository = get(),
            lessonRepository = get(),
            parser = get(),
            authRepository = get(),
            clock = get(),
            randomSeedProvider = get<RandomSeedProvider>()::next,
        )
    }
    factory<CompleteAttemptUseCase> {
        CompleteAttemptUseCase(
            attemptRepository = get(),
            ratingRepository = get(),
            clock = get(),
            attemptIdProvider = get<AttemptIdProvider>()::next,
        )
    }
    factory<AbortAttemptUseCase> {
        AbortAttemptUseCase(
            attemptRepository = get(),
            clock = get(),
            attemptIdProvider = get<AttemptIdProvider>()::next,
        )
    }
    factory<SubmitLessonRatingUseCase> {
        SubmitLessonRatingUseCase(
            ratingRepository = get(),
            lessonRepository = get(),
            ratingIdProvider = get<RatingIdProvider>()::provide,
            clock = get(),
        )
    }
}
```

**Constructor params** verified against Walking Skeleton (`shared/feature/lesson-runner/domain/.../use_case/*.kt`):
- `CompleteAttemptUseCase`: `attemptRepository`, `ratingRepository`, `clock`, `attemptIdProvider: () -> AttemptId`
- `AbortAttemptUseCase`: `attemptRepository`, `clock`, `attemptIdProvider: () -> AttemptId`
- `SubmitLessonRatingUseCase`: `ratingRepository`, `lessonRepository`, `clock`, `ratingIdProvider: (String, LessonId) -> RatingId`

Method references (`::next`, `::provide`) provide compile-time binding to interface signatures — no lambda indirection.

---

## §LR-15 lessonRunnerPresentationModule — canonical Koin module

**File**: `android/feature/lesson-runner/presentation/src/main/kotlin/.../di/LessonRunnerPresentationModule.kt`

```kotlin
val lessonRunnerPresentationModule = module {
    factory { (ctx: ComponentContext, lessonId: LessonId, mode: Difficulty) ->
        DefaultLessonRunnerRootComponent(
            componentContext = ctx,
            lessonId = lessonId,
            mode = mode,
            startAttemptUseCase = get(),
            completeAttemptUseCase = get(),
            abortAttemptUseCase = get(),
            submitRatingUseCase = get(),
            lessonRepository = get(),
            attemptRepository = get(),
            clock = get(),
        ) as LessonRunnerRootComponent
    }
}
```

**AppApplication.kt** — добавить в `startKoin { modules(...) }`:
```kotlin
questionSchemaModule,           // NEW: parser (shared/core/question-schema)
lessonRunnerDataModule,
lessonRunnerDomainKoinAdapter,
lessonRunnerPresentationModule,
```

---

## §LR-16 Room DAOs — canonical signatures

### LessonAttemptDao

**File**: `shared/core/persistence/src/commonMain/kotlin/.../dao/LessonAttemptDao.kt`

```kotlin
@Dao
interface LessonAttemptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LessonAttemptEntity): Long

    @Query("SELECT * FROM lesson_attempts WHERE user_id = :userId AND lesson_id = :lessonId")
    fun observeByLesson(userId: String, lessonId: String): Flow<List<LessonAttemptEntity>>

    @Query("SELECT * FROM lesson_attempts WHERE user_id = :userId")
    fun observeAllByUser(userId: String): Flow<List<LessonAttemptEntity>>
}
```

### LessonRatingLocalDao

**File**: `shared/core/persistence/src/commonMain/kotlin/.../dao/LessonRatingLocalDao.kt`

```kotlin
@Dao
interface LessonRatingLocalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LessonRatingSubmittedLocalEntity): Long

    @Query("SELECT COUNT(*) > 0 FROM lesson_rating_submitted_local WHERE user_id = :userId AND lesson_id = :lessonId")
    fun hasSubmitted(userId: String, lessonId: String): Flow<Boolean>
}
```

---

## §LR-17 Room Entities — canonical schema

### LessonAttemptEntity

```kotlin
@Entity(
    tableName = "lesson_attempts",
    indices = [Index("user_id"), Index("lesson_id")],
)
data class LessonAttemptEntity(
    @PrimaryKey @ColumnInfo(name = "attempt_id") val attemptId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    @ColumnInfo(name = "lesson_version") val lessonVersion: Long,
    @ColumnInfo(name = "is_hard") val isHard: Boolean,
    @ColumnInfo(name = "code_answer") val codeAnswer: String,
    @ColumnInfo(name = "percent_score") val percentScore: Int,
    @ColumnInfo(name = "completed_at") val completedAt: Long,
)
```

### LessonRatingSubmittedLocalEntity

```kotlin
@Entity(
    tableName = "lesson_rating_submitted_local",
    primaryKeys = ["user_id", "lesson_id"],
)
data class LessonRatingSubmittedLocalEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    @ColumnInfo(name = "submitted_at") val submittedAt: Long,
)
```

---

## §LR-18 TypeConverters — canonical

### DifficultyConverter

```kotlin
@ProvidedTypeConverter
class DifficultyConverter {
    @TypeConverter fun toDb(value: Difficulty): String = value.name
    @TypeConverter fun fromDb(value: String): Difficulty = Difficulty.valueOf(value)
}
```

### TopParticipantListConverter

```kotlin
@ProvidedTypeConverter
class TopParticipantListConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun toDb(list: List<TopParticipant>): String =
        json.encodeToString(ListSerializer(TopParticipant.serializer()), list)

    @TypeConverter
    fun fromDb(value: String): List<TopParticipant> =
        try { json.decodeFromString(ListSerializer(TopParticipant.serializer()), value) }
        catch (e: SerializationException) { emptyList() }
}
```

**Requirement**: `TopParticipant` (в `shared/core/leaderboard/`) должен быть `@Serializable` (ADR-LR-05).

<!-- CMP_SECTION_END -->
