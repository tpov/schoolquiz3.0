---
date: 2026-04-26
researcher: Claude (orchestrator) + 9 sub-agents
commit: f2492d29
branch: kmp-skillify-4.0
spec: docs/features/lesson-runner/0-spec.md
---

# Research: Lesson Runner — экран прохождения урока

## Summary

Walking Skeleton domain модуля `shared/feature/lesson-runner/domain/` **полностью сгенерирован** (Phase 3.8): 25 production файлов (модели, state, logic, repository interfaces, use cases, DI module) + ~89 JVM тестов в `commonTest`. Domain Contract из spec реализован корректно: `evaluateAnswer` имеет non-inverted scoring (correct → Score(9), wrong → Score(1)), `computeStars` integer math, `allShownAnswersAre9` string-based detection.

Phase-01 implementation потребует создать **с нуля** два новых модуля: `shared/feature/lesson-runner/data/` (Room adapters, repository impls) и `android/feature/lesson-runner/presentation/` (Decompose Components + Compose UI). Также требуется production реализация `KotlinxSerializationQuestionContentParser` (interface уже есть в `shared/core/question-schema/`; impl нет).

5 архитектурных блокеров и 10 open questions для design phase обнаружены. Главный риск — `top3: List<TopParticipant>` создаёт bidirectional coupling `lesson:domain ↔ lesson-runner:domain` если `Lesson` модель напрямую содержит `top3`; требуется ADR-решение (вариант: переместить `TopParticipant` в `shared/core/`).

## Architecture Overview

### Текущая структура модулей (затронутые)

| Module | Path | Status |
|--------|------|--------|
| `shared/feature/lesson-runner/domain` | `shared/feature/lesson-runner/domain/` | **Существует** (Walking Skeleton зелёный) |
| `shared/feature/lesson-runner/data` | `shared/feature/lesson-runner/data/` | **Не существует** — создаётся в phase-01 |
| `android/feature/lesson-runner/presentation` | `android/feature/lesson-runner/presentation/` | **Не существует** — создаётся в phase-01 |
| `shared/core/question-schema` | `shared/core/question-schema/src/commonMain/` | Существует (sealed `QuestionContent`, `QuestionContentParser` interface) |
| `shared/feature/lesson/domain` | `shared/feature/lesson/domain/` | Существует, требует расширения (`averageRating`, `ratingCount`, `top3`) |
| `android/feature/quizzes-screen/presentation` | `android/feature/quizzes-screen/presentation/` | Существует, требует замены `QuizzesConfig.LessonPlaceholder` на `LessonRunner` |
| `shared/feature/app-shell/domain` | `shared/feature/app-shell/domain/` | Существует, `AuthRepository` готов к использованию |
| `shared/core/persistence` | `shared/core/persistence/src/commonMain/AppDatabase.kt:7` | Существует, version=3, нет `lesson_attempts`/`lesson_ratings` |
| `shared/core/sync` | `shared/core/sync/src/commonMain/CascadingSyncOrchestrator.kt:37` | Существует, **нерасширяем для orthogonal collections** без structural change |

### Layer dependency graph (planned)

```
android/feature/lesson-runner/presentation  (NEW)
  ├─→ shared/feature/lesson-runner/domain  (EXISTS, Walking Skeleton)
  ├─→ shared/feature/lesson/domain  (EXISTS, needs extension)
  ├─→ shared/feature/question/domain  (EXISTS)
  ├─→ shared/core/question-schema  (EXISTS)
  ├─→ android/core/designsystem  (EXISTS)
  └─→ android/core/navigation  (EXISTS)

shared/feature/lesson-runner/data  (NEW)
  ├─→ shared/feature/lesson-runner/domain
  ├─→ shared/core/persistence
  └─→ platform/firebase  (для Firestore mapping → cascade sync infra)

android/feature/quizzes-screen/presentation  (MODIFIED)
  └─→ android/feature/lesson-runner/presentation  (NEW: push LessonRunner config)
  └─→ shared/feature/lesson-runner/domain  (NEW: для bestStars/hardUnlocked в LessonListComponent)
```

## Existing Patterns

### 1. Decompose Component canonical pattern (для LessonRunnerRootComponent)

```kotlin
class DefaultXxxComponent(
    componentContext: ComponentContext,
    /* domain deps */,
    coroutineContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : XxxComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + coroutineContext)

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
    }
}
```

Образцы: `DefaultLessonListComponent.kt:23` (`android/feature/quizzes-screen/presentation/`), `DefaultRootComponent.kt:74` (`android/feature/app-shell/presentation/`).

### 2. instanceKeeper для retain across rotation (единственный пример)

`android/feature/quest/presentation/.../DefaultMyQuestsComponent.kt:59`:
```kotlin
instanceKeeper.getOrCreate { SelectedCatalogHolder() }
// где
private class SelectedCatalogHolder : InstanceKeeper.Instance {
    val flow = MutableStateFlow<CatalogId?>(null)
    override fun onDestroy() = Unit
}
```

Decompose 3.1.0 (`gradle/libs.versions.toml:??`) — `retainedInstance{}` delegate **недоступен** (только с 3.2.0+). Для lesson-runner: `RunnerStateHolder : InstanceKeeper.Instance`, инкапсулирует `MutableStateFlow<RunnerState>` + seed + deadline.

### 3. ChildStack push/pop

`DefaultLessonListComponent.kt:55-62` push pattern:
```kotlin
navigation.pushNew(QuizzesConfig.LessonPlaceholder(lessonId, lessonTitle, titles))
```

`DefaultQuizzesComponent.kt:38-55` `childStack(...)` factory с `saveStack`/`restoreStack` через `SerializableContainer` + `ListSerializer(QuizzesConfig.serializer())`.

### 4. Sealed Configuration с kotlinx.serialization (`@Serializable`, не Parcelable)

`QuizzesConfig.kt:5` — `@Serializable sealed class QuizzesConfig` с `@Serializable` на каждом subtype. Все поля — примитивы (String, List<String>).

### 5. StarRating (existing, уже поддерживает fractional)

`android/core/designsystem/.../StarRating.kt:99` — `StarRating(rating: Float?, modifier, size: Dp = 18.dp)`. Поддерживает 0.0..3.0 шаг 0.1 нативно. Spec формула `bestStars.rawTenths / 10f` — **совместима без изменений**.

### 6. HierarchyItemCard (existing, уже принимает rating)

`android/core/designsystem/.../HierarchyItemCard.kt:34`:
```kotlin
fun HierarchyItemCard(
    title: String,
    orderLabel: String? = null,
    subtitleCount: String? = null,
    rating: Float? = null,        // УЖЕ есть — для bestStars
    ratingCount: Int? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    modifier: Modifier = Modifier,
)
```

**Нет Checkbox slot**. Добавление checkbox для HARD mode требует расширения API или нового компонента (см. Open Question #7).

### 7. AuthRepository (existing, готов к использованию)

`shared/feature/app-shell/domain/.../AuthRepository.kt:21`:
- `suspend fun currentUid(): String?` (line 31)
- `fun observeUid(): Flow<String?>` (line 43)

Реализация `AuthRepositoryImpl.kt:21` — wrap `currentUidFlow: () -> Flow<String?>` lambda; `currentUid()` → `currentUidFlow().first()`.

### 8. Coil 3 (existing, без custom ImageLoader)

`gradle/libs.versions.toml:44` — `coil3 = "3.4.0"`. Зависимости в `android/core/designsystem/build.gradle.kts:13-14`. Custom `ImageLoader` отсутствует — Coil 3 automatic singleton с default disk cache.

URL guard в существующих компонентах:
- `QuestCard.kt:72-73` — strict: `scheme=="https" && host=="firebasestorage.googleapis.com"`
- `CatalogGrid.kt:78` — soft: `startsWith("https://")`

### 9. Snackbar (existing pattern для feedback)

`AppShellScreen.kt:104,131-147` — `SnackbarHostState` + `Channel<Event>` (BUFFERED). Нет `Toast` в KMP/Android коде. Spec упоминает «toast» — фактически использовать Snackbar или Channel-based event.

## Integration Points

### Entry point из quizzes-screen (точка замены)

`android/feature/quizzes-screen/presentation/.../DefaultLessonListComponent.kt:55-63`:
```kotlin
override fun onLessonClick(lesson: HierarchyItemUi) {
    navigation.pushNew(
        QuizzesConfig.LessonPlaceholder(
            lessonId = lesson.id,
            lessonTitle = lesson.title,
            titles = titles + listOf(lesson.title),
        ),
    )
}
```

`DefaultQuizzesComponent.kt:117-138` — exhaustive `when` над `QuizzesConfig` для `createChild`. `QuizzesScreen.kt:35-46` — exhaustive `when` над `QuizzesChild`. Замена/добавление `LessonRunner` варианта затрагивает **3 exhaustive when ветви** + 1 push call site. Compile-error безопасность.

### Lesson read snapshot path

`StartLessonAttemptUseCase.kt:36-43`:
```
authRepository.currentUid()      // null → InitFailed(AuthRequired)
↓
lessonRepository.getById(lessonId)  // null → InitFailed(LessonNotFound) — fix lesson.version snapshot
↓
questionRepository.observeByLesson(lessonId).first()  // one-shot snapshot
↓
parser.parse(question.payload)  // QuestionContentParser — IMPL НЕ СУЩЕСТВУЕТ
↓
filter !archived, filter difficulty, sortedBy(order, id)
↓
selectSubset(eligible, 20, seed)
↓
RunnerState.Ready(...)
```

### Cascade sync — orthogonal collections

`shared/core/sync/.../CascadingSyncOrchestrator.kt:37` — orchestrator с 6-уровневой иерархией (Catalog→Quest→Section→Theme→Lesson→Question). `lesson_attempts`, `lesson_ratings` — orthogonal (per-user, не per-tree).

Existing precedent для non-hierarchy syncable: `apps/android-next/.../SyncModule.kt:38`:
```kotlin
single { listOf<Syncable>(get<UserStatsRepository>() as Syncable, get<CascadingSyncOrchestrator>()) }
```

`UserStatsRepository as Syncable` — отдельный путь синхронизации. Lesson-runner sync (out of scope) добавит `LessonAttemptRepository as Syncable` или отдельный `LessonAttemptSyncable` impl.

`SyncStateRepository.getCursor(collectionId: String)` принимает любой String — новые ключи `"lesson_attempts"`, `"lesson_ratings"` работают без изменений `SyncLevel` enum.

## Detailed Findings

### 1. Walking Skeleton (lesson-runner/domain)

- **Location**: `shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/`
- **Production files** (25):
  - `model/`: `Attempt.kt:10`, `AttemptId.kt:4`, `LessonRating.kt:10`, `RatingId.kt:4`, `TopParticipant.kt:3`, `Stars.kt:8`, `CodeAnswer.kt:9` (+ extension `allShownAnswersAre9` line 20), `PercentScore.kt:9`, `Score.kt:7`, `RunnerQuestion.kt`, `UserAnswer.kt`, `UserAnswerDraft.kt:9` (с комментарием про instanceKeeper), `TimerCoefficients.kt`, `TimerDuration.kt`
  - `state/RunnerState.kt:18` — sealed `Loading | InitFailed | Ready | Completed | Aborted | SaveFailed`. `Ready.isPaused: Boolean` (line 47), `Ready.userId: String` (snapshot)
  - `logic/RunnerLogic.kt:25-237` — pure functions: `submitAnswer`, `autoAnswerOnTimeout`, `evaluateAnswer` (correct, не inverted), `computeStars`, `computeBestStars`, `computeHardUnlocked`, `computeTimer`, `selectSubset`, `buildCodeAnswerOnAbort`, `computePercentScore`
  - `repository/`: `LessonAttemptRepository.kt:7`, `LessonRatingRepository.kt:7`
  - `use_case/`: `StartLessonAttemptUseCase.kt:27`, `CompleteAttemptUseCase.kt:22`, `AbortAttemptUseCase.kt:20`, `SubmitLessonRatingUseCase.kt`
  - `di/LessonRunnerDomainModule.kt:17` — Koin module (но НЕ зарегистрирован в `AppApplication`)
- **Test files** (~89 scenarios в 14 файлах в `commonTest/`): `ScoreFormulaTest`, `StarsComputeTest`, `BestStarsHardUnlockedTest`, `TimerComputeTest`, `CodeAnswerTest`, `SelectSubsetTest`, `AutoAnswerTest`, `SaveAttemptTest`, `FailureSemanticsTest`, `EdgeCasesTest`, `StateMachineTest`, `CodeAnswerConstructionTest`, `LessonVersionTest`, `RatingPromptTest`, `PercentScoreComputeTest`
- **Dependencies в `build.gradle.kts:1`**:
  - commonMain: `:shared:core:question-schema`, `:shared:feature:lesson:domain`, `:shared:feature:question:domain`, `:shared:feature:app-shell:domain`, `kotlinx.datetime`, `koin.core`
  - commonTest: `:shared:feature:theme:domain` (только тесты)

### 2. QuestionContent sealed (question-schema)

- **Location**: `shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/QuestionContent.kt:9`
- **Содержание**: sealed interface с 4 вариантами (`SingleChoice`, `MultipleChoice`, `Ordering`, `FillBlank`). Каждый имеет `id: String`, `difficulty: Difficulty`, `text: String`, `imageUrl: String?`. Validation invariants в `init { require(...) }`:
  - `SingleChoice.options.size in 2..8`, `correctOptionId ∈ options.ids` (lines 36-40)
  - `MultipleChoice.options.size in 2..8`, `correctOptionIds.size >= 2` (lines 60-65)
  - `Ordering.items.size in 2..8` (line 83)
  - `FillBlank.blanks.size in 1..3`, `candidates.size == 5 || candidates.size == 10` (lines 102-113)
- **build.gradle.kts:13** — kotlinx.serialization уже зависимость в commonMain (разрешена для shared core).
- **Difficulty enum**: `shared/core/question-schema/src/commonMain/.../Difficulty.kt:3` — `enum class Difficulty { EASY, HARD }`. **НЕ `@Serializable`** — нужно проверить нужна ли аннотация для `QuizzesConfig.LessonRunner(mode: Difficulty)` сериализации.
- **timeLimitSec missing**: ADR-0003 описывает `timeLimitSec: Int` как поле каждого варианта. В реальном `QuestionContent` поля **нет**. Spec уже зафиксировал ADR Amendment C для игнорирования этого поля.

### 3. QuestionContentParser (interface есть, impl НЕТ)

- **Location**: `shared/core/question-schema/src/commonMain/.../QuestionContentParser.kt:9`
- **Signature**: `interface QuestionContentParser { fun parse(payload: String): Result<QuestionContent> }`
- **Production impl**: **НЕ СУЩЕСТВУЕТ**. `KotlinxSerializationQuestionContentParser` нет ни в `shared/core/question-schema/src/commonMain/`, ни в `jvmMain/`, ни в `androidMain/`. Только `FakeQuestionContentParser` в `shared/feature/lesson-runner/domain/src/commonTest/.../fake/FakeQuestionContentParser.kt:6`.
- **Koin binding**: `LessonRunnerDomainModule.kt:25` использует `parser = get()` — **NoBeanDefinitionFoundException** при runtime resolution. **Блокер #1 для phase-01**.

### 4. Lesson model (требует расширения)

- **Location**: `shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/model/Lesson.kt:15`
- **Текущие поля** (8): `id: LessonId`, `themeId: ThemeId`, `title: String`, `order: Int`, `version: Long`, `contentsVersion: Long`, `lastModifiedAt: Long`, `archived: Boolean = false`
- **Spec §34 требует добавить**: `averageRating: Float?`, `ratingCount: Int?`, `top3: List<TopParticipant>`
- **Cascade impact** при добавлении полей:
  - `LessonEntity` — Room schema (`shared/core/persistence/.../LessonEntity.kt:8`) — нужны новые columns + TypeConverter для `top3` (нет существующего конвертера для `List<TopParticipant>`)
  - `LessonDtoMapper.toEntity()`, `LessonMapper.toDomain()` — обновление chain
  - `LessonDto` (`shared/feature/lesson/data/.../dto/LessonDto.kt:3`) — новые поля
  - `FirestoreLessonDtoMapper.kt:6` — backward compat для отсутствующих Firestore полей (default values)
  - 3+ test fakes конструируют `Lesson(...)` напрямую: `FakeLessonRepository.kt` (lesson-runner/domain commonTest), `FakeLessonRepository.kt` (quizzes-screen presentation test), `QuizzesRootIntegrationTest.kt:293,321,349`. Nullable defaults минимизируют compile breakage.
- **Quest precedent для naming**: `Quest.averageRating: Float?`, `Quest.averageRatingCount: Int` (`shared/feature/quest/domain/.../model/Quest.kt:62,69`). Spec пишет `ratingCount: Int?`, Quest pattern — `averageRatingCount: Int` (non-nullable). Naming inconsistency (Open Question #4).

### 5. TopParticipant — bidirectional coupling risk

- **Location**: `shared/feature/lesson-runner/domain/src/commonMain/.../model/TopParticipant.kt:3`
- **Описание**: `data class TopParticipant(nickname: String, avatarUrl: String?, percent: Int)`. Invariants: `nickname.isNotBlank()`, `percent in 0..100`.
- **Risk**: Если `Lesson.top3: List<TopParticipant>` — то `lesson:domain` импортирует `lesson-runner:domain`. Lesson-runner уже импортирует `lesson:domain` → **bidirectional coupling блокер по invariant 3**.
- **Резолюция (для design phase)**:
  - **A**: переместить `TopParticipant` в `shared/core/` (лучшее расположение TBD)
  - **B**: использовать `List<String>` сериализованную форму в `Lesson` + распаковывать в presentation
  - **C**: НЕ хранить `top3` в `Lesson`, читать отдельно через свой repository
- **Block #2 для design phase**.

### 6. AppDatabase + migration risk

- **Location**: `shared/core/persistence/src/commonMain/.../AppDatabase.kt:7` — version=3, 7 entities, нет `LessonAttemptEntity`/`LessonRatingEntity`
- **Migration strategy**: `shared/core/persistence/src/androidMain/.../di/PersistenceModule.kt:23` — `.fallbackToDestructiveMigration(dropAllTables = true)`. Любое version-bump без явной `Migration(3, 4)` **уничтожит ВСЕ user data** (catalogs, quests, sections, themes, lessons, questions, userStats).
- **Required migrations** для phase-01:
  - `CREATE TABLE lesson_attempts (...)` (компенсация всех полей `Attempt`)
  - `CREATE TABLE lesson_rating_submitted_local (...)` (compound PK `(userId, lessonId)`)
  - `ALTER TABLE lessons ADD COLUMN averageRating REAL` (nullable)
  - `ALTER TABLE lessons ADD COLUMN ratingCount INTEGER` (default 0?)
  - `ALTER TABLE lessons ADD COLUMN top3 TEXT` (JSON-serialized List<TopParticipant>)
- **TypeConverter gap**: Existing only `StringSetConverter` (`AppDatabase.kt:20`). Нужен:
  - `EnumConverter` для `Difficulty` в `LessonAttemptEntity.mode` (или storing as String "EASY"/"HARD")
  - `TopParticipantListConverter` для `LessonEntity.top3` (JSON via kotlinx.serialization)
  - Per ADR-CMP-TC, Room KMP требует `@ProvidedTypeConverter` + `.addTypeConverter()` (как `StringSetConverter` сделан)
- **AppDatabaseMigrationTest**: `AppDatabaseMigrationTest.kt:31` тестирует **только** version 1 → нет coverage для 2→3, 3→4
- **Block #3 для phase-01**.

### 7. Koin composition root

- **Location**: `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:87`
- **Текущие 21 module** (порядок): `persistenceModule`, `firebaseModule`, `firebaseCatalogModule`, ..., `appShellDataModule { sharedAuthUidFlow }`, `appShellPresentationModule`, `questPresentationModule`, `quizzesPresentationModule`, `catalogDataModule`, `catalogDomainModule`, ..., `syncModule`
- **Отсутствует** в `startKoin`: `lessonRunnerDomainModule`, `lessonRunnerDataModule`, `lessonRunnerPresentationModule`. **Блокер #4 для phase-01**.
- **Lambda type bindings** для `() -> AttemptId`, `() -> Long` (randomSeedProvider), `(String, LessonId) -> RatingId` — нет существующего precedent в production. Spec явно: «Phase-01 решит strategy». Существующий близкий pattern — `appShellDataModule(currentUidFlow: () -> Flow<String?>)` как **параметр функции module** (не Koin binding) — `AppShellDataModule.kt:15`.
- **Clock binding**: нигде не зарегистрирован. `CascadingSyncOrchestrator.kt:68` использует `Clock.System.now()` напрямую. Lesson-runner будет **первым** consumer Koin-injected `Clock`.

### 8. quizzes-screen integration (точка замены)

- `QuizzesConfig` (`config/QuizzesConfig.kt:5`) — `@Serializable sealed class` с 6 вариантами. **`Difficulty` НЕ `@Serializable`** → нужно либо добавить аннотацию (изменение core), либо в `QuizzesConfig.LessonRunner` использовать `String`/`Int` для mode.
- `QuizzesChild` (`component/QuizzesChild.kt:14`) — sealed interface 6 вариантов. Exhaustive `when` в `QuizzesScreen.kt:35-46`.
- `DefaultQuizzesComponent.createChild` (`:117-138`) — exhaustive `when`. `popToLevel` (`:84-107`) использует `cfg.titles.size` — новый `LessonRunner` config обязан содержать `titles: List<String>`.
- `DefaultLessonListComponent.kt:55` — единственный push site. Замена тривиальная.
- `LessonPlaceholderComponent` interface + `DefaultLessonPlaceholderComponent` + `LessonPlaceholderScreen` + `LessonPlaceholderUiState` + `FakeLessonPlaceholderComponent` + `LessonPlaceholderScreenTest` — все становятся dead code при полной замене. Spec §37 делегирует решение design phase.

### 9. HierarchyItemCard — нет Checkbox slot

- `android/core/designsystem/.../HierarchyItemCard.kt:34-43` — нет slot для Checkbox или дополнительного контрола.
- `HierarchyItemUi.kt:3-8` — поля `id, title, orderLabel?, subtitleCount?`. Нет `bestStars` или `hardUnlocked`.
- Spec AC #47, #49 требует:
  - `StarRating(rating = bestStars.rawTenths / 10f)` — параметр `rating: Float?` уже совместим.
  - HARD checkbox visible когда `hardUnlocked == true` — slot отсутствует.
- **Решение для design** (Open Question #7):
  - **A**: Расширить `HierarchyItemCard` API (затронет все 5 drill-down screens)
  - **B**: Создать новый `LessonItemCard` специфичный
  - **C**: Использовать generic content slot lambda

### 10. LessonAttemptRepository в quizzes-screen

`DefaultLessonListComponent` сейчас в `quizzes-screen/presentation` получает только `LessonRepository` через constructor (`DefaultLessonListComponent.kt:26`). Чтобы показать `bestStars` + `hardUnlocked` на карточке урока, компонент должен:
- Получить `LessonAttemptRepository` из `lesson-runner:domain`
- Combine `lessonRepository.observeByTheme(themeId)` + `attemptRepository.observeAllByUser(userId)` per lesson

Это **новый cross-feature import**: `quizzes-screen/presentation` → `lesson-runner/domain`. Не bidirectional (lesson-runner не импортирует quizzes-screen), но требует **ADR в `docs/features/lesson-runner/03-decisions.md`** (файл не существует, design phase создаёт).

### 11. Lifecycle hooks (новые patterns для проекта)

Поиск по production коду android/ модулей:
- `lifecycle.doOnStop` — **0 использований**
- `lifecycle.doOnResume` — **0 использований**
- `lifecycle.doOnStart` — **0 использований**
- `lifecycle.doOnDestroy` — 7 использований (только `componentJob.cancel()`)

Spec FR#14 (`onStop` → auto-random fill) и FR#15 (`onResume` → блокирующий dialog) требуют **ввести новый pattern** через Essenty `Lifecycle.doOnStop {}` / `Lifecycle.doOnResume {}` extensions. Доступны в `essenty = "2.1.0"` (на boundary с issue #627 — verify).

### 12. FLAG_SECURE / Block-on-resume / Timer / Drag — все новые patterns

| Feature | Existing pattern? | Реализация (по Web Research) |
|---------|-------------------|------------------------------|
| FLAG_SECURE в Compose | Нет (legacy QuestionActivity использует FLAG_KEEP_SCREEN_ON, не FLAG_SECURE) | `DisposableEffect(Unit)` + `LocalContext.current.findWindow()` + `addFlags`/`clearFlags` (Coil docs / community established pattern) |
| Block-on-resume fullscreen dialog | Нет | `Dialog(properties = DialogProperties(dismissOnBackPress=false, dismissOnClickOutside=false, usePlatformDefaultWidth=false))` с opaque fullscreen `Card` |
| Timer countdown в Compose | Нет (нет `delay()` loop) | `LaunchedEffect(Unit) { while (isActive) { delay(100); ... } }` + monotonic `SystemClock.uptimeMillis()` для drift compensation |
| Drag-and-drop Ordering | Нет | Recommended: up/down `IconButton` (accessible, 0 deps); альтернатива — `sh.calvin.reorderable:reorderable:3.1.0` |
| Toast/Snackbar | Snackbar via Channel events (`AppShellScreen.kt:104, 131-147`) | Использовать Channel-based feedback в Component event sink |

### 13. Cross-feature dependency map

| From → To | Mechanism | Direction | ADR? |
|-----------|-----------|-----------|------|
| lesson-runner/domain → shared/core/question-schema | direct import | one-way | implicit (in scope) |
| lesson-runner/domain → lesson:domain | direct import (`LessonId`, `LessonRepository`) | one-way | **MISSING** — `03-decisions.md` для lesson-runner не существует |
| lesson-runner/domain → question:domain | direct import (`QuestionRepository`) | one-way | **MISSING** |
| lesson-runner/domain → app-shell:domain | direct import (`AuthRepository`) | one-way | **MISSING** |
| android/feature/lesson-runner/presentation → lesson-runner:domain | direct import | planned, one-way | trivial (layer-crossing) |
| android/feature/quizzes-screen/presentation → android/feature/lesson-runner/presentation | push `QuizzesConfig.LessonRunner` | planned, one-way | **MUST DOCUMENT** в quizzes-screen/03-decisions.md |
| android/feature/quizzes-screen/presentation → lesson-runner:domain | для `bestStars`/`hardUnlocked` (new) | planned, one-way | **MUST DOCUMENT** |
| android/feature/lesson-runner/presentation → quizzes-screen | **MUST be NONE** | — | — |

**Verified zero existing imports** между lesson-runner и quizzes-screen (lesson-runner модули presentation/data ещё не существуют). При создании modulей все imports должны быть one-way (lesson-runner ← quizzes-screen для push, lesson-runner ← lesson:domain для repository, etc.).

**Existing undocumented domain hierarchy imports** (one-way, не блокеры): `question:domain → lesson:domain`, `lesson:domain → theme:domain`, `section:domain → quest:domain`. Установленный pattern, не bidirectional.

### 14. Walking Skeleton naming inconsistency

| Class | Field | Spec line ref |
|-------|-------|---------------|
| `LessonId` | `value: String` | `model/LessonId.kt:12` |
| `QuestionId` | `value: String` | `model/QuestionId.kt:12` |
| `AttemptId` | `raw: String` | `model/AttemptId.kt:4` |
| `RatingId` | `raw: String` | `model/RatingId.kt:4` |

Spec line 100 пишет `sourceId.raw`, но `StartLessonAttemptUseCase.kt:69` использует `sourceId.value` — компилируется и работает. Это **опечатка в spec**, не code defect. Не блокер; design phase решит унификацию имени поля (для consistency можно переименовать `AttemptId.raw` → `value`).

### 15. Server-side gaps (out of scope, но контракт)

- `firestore.rules:1-73` — нет правил для `lesson_attempts` или `lesson_ratings`. **Deployment blocker** для production sync.
- `server/functions/src/main/kotlin/.gitkeep` — нет реализаций Cloud Functions. Spec-required:
  - `onCreate(lesson_ratings)` — пересчёт `Lesson.averageRating` + `ratingCount`
  - `onCreate(lesson_attempts)` — пересчёт `Lesson.top3`
  Без CF: ratings/attempts пишутся локально, но `Lesson.averageRating`/`top3` всегда null/empty. AC #45 явно говорит "top3 — закешированный server snapshot, текущая попытка ещё не там".
- **Composite Firestore index** для `lesson_attempts`: query `where('userId','==',uid).where('lastModifiedAt','>',cursor)` требует manual `(userId ASC, lastModifiedAt ASC)` индекс. Не auto-created.
- **`lastModifiedAt` в `Attempt`**: domain имеет `completedAt: Long`, но не `lastModifiedAt`. Spec §32 требует `lastModifiedAt: serverTimestamp` в Firestore document. Решения:
  - А) Derive `lastModifiedAt = completedAt` на клиенте
  - B) Sync writer добавляет `FieldValue.serverTimestamp()` отдельно от domain `completedAt`
  Open Question для design.

## Conditional Documents Needed

| Document | Reason |
|----------|--------|
| `02-behavior.md` | mandatory — описывает gameplay loop (per-question state machine, timer, auto-random, dialogs) |
| `03-decisions.md` | mandatory — ADR для (a) cross-feature imports lesson-runner/domain → 3 features, (b) `LessonPlaceholder` replacement strategy, (c) `TopParticipant` location, (d) Koin lambda binding strategy, (e) `Difficulty` serializable, (f) `Lesson.top3` storage, (g) HierarchyItemCard extension |
| `04-data-flow.md` | mandatory — push-replace `LessonPlaceholder` → `LessonRunner`; data flow `Lesson` → `LessonListComponent` (с attempts) → tap → `LessonRunner` |
| `05-state-management.md` | required — `RunnerState` уже sealed; описывает instanceKeeper hold + onStop/onResume transitions; FLAG_SECURE toggle binding с state |
| `06-api-contract.md` | required — Firestore collection `lesson_attempts`, `lesson_ratings`, расширение `lessons` document; security rules; CF triggers (out-of-scope, контракт) |
| `07-events.md` | optional — Channel `<RunnerEvent>` для save errors / submit rating errors → Snackbar в presentation |
| `08-storage-model.md` | required — Room migrations 3→4, `lesson_attempts` table schema, `lesson_rating_submitted_local` compound PK, TypeConverter для `Difficulty`, `top3` |

## Constraints

### Кодовая база

1. **`fallbackToDestructiveMigration(dropAllTables = true)` в production** — `PersistenceModule.kt:23`. Без явной Migration теряются user data при upgrade. Реальное ограничение для phase-01.
2. **`ProvidedTypeConverter` requirement (ADR-CMP-TC)** — Room KMP требует `@ProvidedTypeConverter` + `.addTypeConverter()` в builder. Pattern в `StringSetConverter` (`AppDatabase.kt:20` + `PersistenceModule.kt:24`).
3. **Sealed `QuizzesConfig` exhaustive matches** — добавление `LessonRunner` обязательно требует обновления `popToLevel` (`DefaultQuizzesComponent.kt:84-107`), `createChild` (`:117-138`), `QuizzesScreen` (`:35-46`) — атомарно или compile error.
4. **Domain layer purity** (invariant 1) — `lesson-runner/domain/src/commonMain/` не импортирует `android.*`, `androidx.*`, Firebase, Room, kotlinx.serialization. **Verified clean** (см. invariant check ниже).
5. **`@JvmInline value class` `value` vs `raw` field naming** — inconsistency в `LessonId`/`QuestionId` vs `AttemptId`/`RatingId`. Не блокер.

### Инвариант check (см. docs/invariants.md)

| Invariant | Импакт | Текущее состояние |
|-----------|--------|-------------------|
| 1. Domain layer purity | New `shared/feature/lesson-runner/domain/src/commonMain/` | **VERIFIED CLEAN**: единственный non-domain import — `org.koin.dsl.module` в `LessonRunnerDomainModule.kt:7`. Это установленный project pattern (LessonDomainModule, QuestionDomainModule, etc. помещают Koin module declarations в domain `di/`). Не нарушение — Koin module **не аннотация**. |
| 2. Presentation does not bypass domain | `android/feature/lesson-runner/presentation/` (новый) — обязан использовать UseCase / Repository через Component, без direct DAO | Phase-01 implementation responsibility |
| 3. No bidirectional coupling | quizzes-screen → lesson-runner one-way (push), lesson-runner НЕ импортирует quizzes-screen | **ВАЖНО**: `Lesson.top3: List<TopParticipant>` создаст bidirectional `lesson:domain ↔ lesson-runner:domain`. **Risk блокер** до резолюции (см. Open Question #2) |
| 4. onDestroy is not for business cleanup | FLAG_SECURE cleanup, save attempt — не должны быть в Activity `onDestroy` без guard | Phase-01: FLAG_SECURE через `DisposableEffect`; save через UseCase invoked from Component, не из lifecycle hook |
| 5. Koin binding uniqueness | Новые `LessonAttemptRepository`, `LessonRatingRepository`, etc. — один production binding per type | Phase-01: проверить `KoinModuleWiringTest` после добавления |
| 6. Walking Skeleton ownership | Domain сгенерирован Phase 3.8; Phase-01 интегрирует, не переписывает | **Verified готов** |
| 7. Scaffold file ownership | `build.gradle.kts` (3 новых модулей), `settings.gradle.kts`, AppManifest entries | backend-dev владеет, в Phase-01 |

## Open Questions

Для design phase. Каждый требует решения с ADR.

1. **Где регистрировать `KotlinxSerializationQuestionContentParser`?**
   - **A**: `shared/core/question-schema/src/commonMain/` (kotlinx.serialization уже dep) — single instance shared across features
   - **B**: `shared/feature/lesson-runner/data/` — следует pattern «impl в data layer»
   - Recommendation: A, т.к. parser — shared core infrastructure, не feature-specific data.

2. **Где разместить `TopParticipant`?**
   - **A**: переместить в `shared/core/<package>/` — solve bidirectional coupling
   - **B**: оставить в `lesson-runner/domain` + `Lesson.top3: List<String>` (JSON-serialized), unpack в presentation
   - **C**: НЕ хранить `top3` в `Lesson`, отдельный `TopParticipantsRepository`
   - Recommendation: A — `TopParticipant` универсальная концепция (ranking on shared content).

3. **`Difficulty` serializable для `QuizzesConfig.LessonRunner(mode: Difficulty)`?**
   - **A**: добавить `@Serializable` в `shared/core/question-schema/Difficulty.kt:3` — изменение core
   - **B**: использовать `String`/`Int` для mode в `QuizzesConfig` + конвертация в Component
   - Recommendation: A — `Difficulty` core type без поведения, `@Serializable` безопасно.

4. **`Lesson` rating fields naming consistency**:
   - Spec: `averageRating: Float?`, `ratingCount: Int?`
   - Quest precedent: `averageRating: Float?`, `averageRatingCount: Int` (non-nullable)
   - Recommendation: align с Quest pattern (non-nullable `Int = 0` default).

5. **AppDatabase migration strategy**:
   - **A**: написать настоящую Migration(3, 4) с CREATE TABLE + ALTER TABLE
   - **B**: продолжать с `fallbackToDestructiveMigration` до post-MVP
   - Recommendation: A для production-ready feature; B приемлемо если фича dev-только до launch.

6. **Koin lambda binding strategy** для `() -> AttemptId`, `() -> Long`, `(String, LessonId) -> RatingId`:
   - **A**: `single<() -> AttemptId> { { AttemptId(UUID.randomUUID().toString()) } }` — Koin function type binding (type erasure risk на JVM)
   - **B**: параметр функции module: `lessonRunnerDataModule(attemptIdProvider: () -> AttemptId, ...)` — pattern из `appShellDataModule`
   - **C**: wrapper interface `interface AttemptIdProvider { fun next(): AttemptId }` + `single<AttemptIdProvider>`
   - Recommendation: C — explicit, no type erasure issue, easily testable.

7. **HierarchyItemCard extension** для HARD checkbox:
   - **A**: добавить параметр `trailing: @Composable () -> Unit` в `HierarchyItemCard` — generic, затронет 5 screens
   - **B**: новый `LessonItemCard` в `android/core/designsystem/` или в `quizzes-screen/presentation`
   - Recommendation: B (LessonItemCard в quizzes-screen) — изоляция изменения, no impact на другие drill-levels.

8. **`LessonPlaceholder` replacement vs coexistence**:
   - **A**: полная замена → удалить `LessonPlaceholderComponent`, screen, test, fake
   - **B**: оставить как fallback config — но это dead code в normal flow
   - Recommendation: A — atomic replacement, save serializer compat не нужен (process-death state restore vs feature change).

9. **`DefaultLessonListComponent` ↔ `LessonAttemptRepository`**: где документируется новый cross-feature import quizzes-screen → lesson-runner:domain?
   - Recommendation: оба `docs/features/quizzes-screen/03-decisions.md` (consumer side) и `docs/features/lesson-runner/03-decisions.md` (producer side).

10. **`lastModifiedAt` в `Attempt` для Firestore sync**:
    - **A**: derive `lastModifiedAt = completedAt` на клиенте
    - **B**: sync writer добавляет `FieldValue.serverTimestamp()` separately
    - Recommendation: B — server-side timestamp единственный source of truth для cursor sync, защищён от device clock drift.

## Cross-Feature Interactions

### Dependency Graph

| From | → | To | Mechanism | File:line | ADR? |
|------|---|----|-----------|-----------|------|
| lesson-runner/domain | → | shared/core/question-schema | direct import: `QuestionContent`, `QuestionContentParser`, `Difficulty` | `RunnerLogic.kt:3-4`, `StartLessonAttemptUseCase.kt:30` | implicit (in scope §219) |
| lesson-runner/domain | → | lesson:domain | direct import: `LessonId`, `LessonRepository` | `Attempt.kt`, `StartLessonAttemptUseCase.kt:30` | **MISSING** |
| lesson-runner/domain | → | question:domain | direct import: `QuestionId`, `QuestionRepository`, `Question` | `RunnerQuestion.kt`, `StartLessonAttemptUseCase.kt:29` | **MISSING** |
| lesson-runner/domain | → | app-shell:domain | direct import: `AuthRepository` | `StartLessonAttemptUseCase.kt:30` | **MISSING** |
| android/feature/quizzes-screen/presentation | → | android/feature/lesson-runner/presentation | планируется: `QuizzesConfig.LessonRunner` push | (NEW) | **MUST CREATE** |
| android/feature/quizzes-screen/presentation | → | lesson-runner:domain | планируется: `LessonAttemptRepository` для bestStars/hardUnlocked | (NEW) | **MUST CREATE** |
| android/feature/lesson-runner/presentation | → | android/feature/quizzes-screen/presentation | **MUST be NONE** | — | — |

### Bidirectional Coupling Risks

- `lesson:domain ↔ lesson-runner:domain` — **БУДЕТ создан** при добавлении `Lesson.top3: List<TopParticipant>` (lesson-runner импортирует lesson:domain уже; обратный import создаст bidir). См. Open Question #2.
- Нет existing bidirectional coupling в проекте (verified scanner).

### Shared SDK Across Features

| SDK | Used by | Recommended pattern (Web Research) | Current integration |
|-----|---------|-------------------------------------|---------------------|
| Coil 3.4.0 | designsystem (5+ компонентов), spec — lesson-runner UI (top-3 avatars, question images) | `setSingletonImageLoaderFactory` once в Application; `DiskCache.maxSizePercent(0.02)`; `coil-network-okhttp` дополнительно к `coil-core` | **No custom ImageLoader** — Coil 3 automatic singleton. Default disk cache. Достаточно для MVP. |
| kotlinx.serialization 1.7.3 | question-schema (parser), quizzes-screen (config StateKeeper) | Sealed class: no `SerializersModule` нужен, `@SerialName` на subclasses; `@JsonClassDiscriminator("kind")` если subclass имеет `type` field | Existing: `QuizzesConfig` (`@Serializable sealed`); planned: `KotlinxSerializationQuestionContentParser` (новый) |
| Decompose 3.1.0 | navigation, app-shell, quest, quizzes-screen | `instanceKeeper.getOrCreate { Holder() }` ; `pushNew` requires `@OptIn(ExperimentalDecomposeApi::class)`; `retainedInstance{}` delegate **NOT available** в 3.1.0 | Existing pattern: `DefaultMyQuestsComponent.kt:59` |
| Koin 3.x | DI everywhere | `single<T>` for repositories, `factory<T>` for components; lambda type binding pattern не установлен | Existing: 21 modules в `AppApplication.kt:87`; `lessonRunnerDomainModule` НЕ зарегистрирован |
| Essenty 2.1.0 | Decompose lifecycle | `lifecycle.doOnStop`, `lifecycle.doOnResume` available но НЕ используются нигде в production. Issue #627 boundary — verify retained behavior on back nav | Existing: только `lifecycle.doOnDestroy { componentJob.cancel() }` (7 мест) |

### Undocumented Patterns (blockers для design phase)

1. `lesson-runner/domain` импортирует 3 features (`lesson:domain`, `question:domain`, `app-shell:domain`) — НЕ задокументированы в `docs/features/lesson-runner/03-decisions.md` (файл не существует, design создаст).
2. `quizzes-screen` ↔ `lesson-runner` planned imports — НЕ задокументированы в `docs/features/quizzes-screen/03-decisions.md`.
3. Domain hierarchy imports (`question:domain → lesson:domain`, `lesson:domain → theme:domain`, `section:domain → quest:domain`) — existing pattern, ни один не имеет ADR. Existing debt, не блокер для lesson-runner.

## Domain Contract Validation (State Matrix vs реальный код)

Walking Skeleton полностью покрывает все 8 матриц spec. Проверки выполнены против `RunnerLogic.kt`, `StartLessonAttemptUseCase.kt`, тестов в `commonTest/`.

### Matrix 1: Score 0-9 — VERIFIED

`RunnerLogic.evaluateAnswer():64`:
- SingleChoice: `if (selected == correctOptionId) Score(9) else Score(1)` ✅ (НЕ inverted, как в legacy `QuestionViewModel.kt:231` баг)
- MultipleChoice: Jaccard через `scoreDigit(correctPicked, correctPicked + wrongPicked + missed)` ✅
- Ordering: `matched_positions / total` через `scoreDigit` ✅; perm validation guard ✅
- FillBlank: `correctly_filled / total_blanks` через `scoreDigit` ✅; foreign IDs filter ✅
- Tested: `ScoreFormulaTest` (12 scenarios)

### Matrix 2: Stars formula — VERIFIED

`RunnerLogic.computeStars():108`:
- EASY: `(percentScore.raw * 20 + 50) / 100` ✅
- HARD: `20 + (percentScore.raw * 10 + 50) / 100` ✅
- Integer math (round-half-up via +50) ✅
- Tested: `StarsComputeTest` (9 scenarios per Matrix 2)

### Matrix 3: bestStars / hardUnlocked — VERIFIED

`computeBestStars(attempts):120` returns `Stars(0)` if empty, else max ✅
`computeHardUnlocked(attempts):129` — string-based via `codeAnswer.allShownAnswersAre9` ✅ (НЕ percentScore-based как требует Matrix 3 row 4)
- Tested: `BestStarsHardUnlockedTest`

### Matrix 4: When to write attempt — VERIFIED по domain

`CompleteAttemptUseCase`, `AbortAttemptUseCase` — domain-side write decisions implemented. Matrix rows про `onStop` / `Process kill` / `Configuration change` — это presentation responsibility (НЕ в domain).

### Matrix 5: Rating prompt — VERIFIED

`CompleteAttemptUseCase.kt`: `ratingPrompt = state.codeAnswer.allShownAnswersAre9 && !ratingRepository.hasSubmitted(state.userId, state.lessonId).first()` ✅
- Tested: `RatingPromptTest` (4 scenarios)

### Matrix 6: Sworn-fold (onResume / abort) — DOMAIN partial

Domain-level: `RunnerState.Ready.isPaused: Boolean` (`RunnerState.kt:47`) для onResume dialog flag. Логика onStop auto-fill — `autoAnswerOnTimeout` (`RunnerLogic.kt:54`) реализована. Matrix 6 rows про onStop/onResume — presentation использует domain functions.

### Matrix 7: Timer formula — VERIFIED

`RunnerLogic.computeTimer():?` принимает `(content: QuestionContent, mode: Difficulty, coefficients: TimerCoefficients): TimerDuration`. Tested: `TimerComputeTest`.

### Matrix 8: Pool selection — VERIFIED

`RunnerLogic.selectSubset(eligible, poolSize=20, seed):?` deterministic. Tested: `SelectSubsetTest`.

### Domain Test Scenarios coverage

Spec требует ~89 scenarios (sections 78-82, плюс 1-77 + edge cases + value object guards + failure semantics + subset determinism). Walking Skeleton commonTest содержит 14 файлов с равномерной декомпозицией. **Phase 3.8b verified zelёный** (по статусу README.md).

### Пропущенных условий не обнаружено

Domain Contract в spec **полностью соответствует** реализации Walking Skeleton. Никаких пропущенных flags / states / conditions.

### Несостыковки

- ADR-0003 `timeLimitSec` поле — отсутствует в реальном `QuestionContent`. Spec уже зафиксировал ADR Amendment C для игнорирования.
- Spec line 100: `sourceId.raw`. Code: `sourceId.value` (`QuestionId.value`). **Опечатка в spec**, не code defect.

### Domain Contract — готов для phase-01

**Verdict**: domain implementation в Walking Skeleton полностью реализует Feature Domain Contract из spec. Phase-01 backend-dev интегрирует через Room + sync (не переписывает domain). 0 architectural mismatches.

## Validation status готовности к design phase

- ✅ Domain Walking Skeleton зелёный (~89 tests pass per README.md)
- ✅ Feature Domain Contract из spec полностью реализован
- ✅ State Matrix 1-8 покрыты
- ✅ Cross-feature dependency map составлена
- ✅ Independent Verification Protocol выполнен для critical claims
- ⚠️ 5 блокеров для phase-01 (требуют решения в design phase): `KotlinxSerializationQuestionContentParser`, `TopParticipant` location, AppDatabase migration, Koin lambda strategy, ADR для cross-feature imports
- ⚠️ 10 open questions требуют решения в design phase
- ⚠️ Server-side gaps (firestore.rules, Cloud Functions) — out of scope, контракт зафиксирован

**Готов к /feature-design**.
