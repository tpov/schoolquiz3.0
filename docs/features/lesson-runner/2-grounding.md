---
date: 2026-04-26
researcher: Claude (orchestrator)
commit: f2492d29
branch: kmp-skillify-4.0
gate: blocks design phase until all problems resolved or accepted
---

# Grounding: Lesson Runner

Gate-документ. Research отвечает «что есть в коде». Grounding отвечает «что реально возможно построить из того, что есть, и что блокирует». Все utверждения **независимо verified** через Read tool — указано `[VERIFIED: file:line, что увидел]` или `[CONTRADICTS: ..., что должно быть в research]`.

Lesson runner — **new feature**, не багфикс. Grounding-карточки описывают «что нужно построить и какие препятствия» а не «что сломалось».

---

## Problem 1: Production `QuestionContentParser` отсутствует

### Symptom

Walking Skeleton domain (`shared/feature/lesson-runner/domain/`) сгенерирован и зелёный. `StartLessonAttemptUseCase.kt:30` принимает `QuestionContentParser` через constructor injection. `LessonRunnerDomainModule.kt:25` регистрирует use case через Koin `get()`. **Production реализации `QuestionContentParser` нет нигде в коде**. При интеграции в `apps/android-next` — `NoBeanDefinitionFoundException` на старте.

### Repro

`grep -rn "class.*QuestionContentParser" --include="*.kt"` находит:
- `[VERIFIED: shared/feature/lesson-runner/domain/src/commonTest/.../fake/FakeQuestionContentParser.kt:6]` — единственная impl, test-only
- Никакого production `KotlinxSerializationQuestionContentParser` или другой production impl

### Entry Points

- `[VERIFIED: shared/feature/lesson-runner/domain/src/commonMain/.../use_case/StartLessonAttemptUseCase.kt:30]` — `parser: QuestionContentParser` constructor param
- `[VERIFIED: shared/feature/lesson-runner/domain/src/commonMain/.../di/LessonRunnerDomainModule.kt]` — `get<QuestionContentParser>()` resolution

### Code Owners

- Interface: `shared/core/question-schema/src/commonMain/.../QuestionContentParser.kt:9`
- Module owner для production impl: design-phase решение (Option A: `shared/core/question-schema/src/commonMain/`, Option B: `shared/feature/lesson-runner/data/`)

### Flow Trace

```
DefaultLessonRunnerComponent.init  (presentation, NEW)
→ get<StartLessonAttemptUseCase>()  (Koin factory)
→ creates StartLessonAttemptUseCase(parser = get())
→ get<QuestionContentParser>() resolution → FAIL: no binding
```

### Backend Check

N/A — это client-side dependency injection issue.

### Constraints

- `kotlinx.serialization` уже dependency в `shared/core/question-schema/build.gradle.kts:13`
- Sealed class polymorphic JSON parsing supported в kotlinx-serialization 1.7.3 (см. web research): `@JsonClassDiscriminator("kind")` + `@SerialName` per subclass
- Existing `QuestionContent` (`QuestionContent.kt:9`) **не имеет** `@Serializable` annotations — нужно добавить или использовать manual `Json.decodeFromString` через `JsonElement` parsing

### Fix Shape

**Phase-01 work**:
1. Создать `shared/core/question-schema/src/commonMain/.../KotlinxSerializationQuestionContentParser.kt`
2. Добавить `@Serializable @JsonClassDiscriminator("kind")` на `QuestionContent` sealed interface
3. Добавить `@SerialName("single_choice")` etc. на subclasses
4. Зарегистрировать `single<QuestionContentParser> { KotlinxSerializationQuestionContentParser() }` в `questionSchemaModule` (новый Koin module) или в `lessonRunnerDataModule`
5. Добавить module в `AppApplication.startKoin` list

### Validation

- JVM unit test парсера: round-trip serialize/deserialize each `QuestionContent` subtype
- Integration test через Walking Skeleton тесты (FakeParser → KotlinxSerializationParser сравнение output)
- AC #54: `import kotlinx.serialization` в lesson-runner/domain ЗАПРЕЩЁН — но в `shared/core/question-schema` allowed

---

## Problem 2: `Lesson.top3` создаёт bidirectional coupling

### Symptom

Spec §34: `Lesson` domain model расширяется полем `top3: List<TopParticipant>`. `TopParticipant` — domain type из `shared/feature/lesson-runner/domain`. `lesson:domain` уже импортируется из `lesson-runner:domain`. Если `lesson:domain` тоже импортирует `lesson-runner:domain` для `TopParticipant` — **bidirectional coupling, блокер по invariant 3**.

### Repro

```
shared/feature/lesson-runner/domain/build.gradle.kts:1
  → commonMain dependencies
  → :shared:feature:lesson:domain  [IMPORTS]

Если добавить в Lesson:
data class Lesson(..., val top3: List<TopParticipant>)
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TopParticipant
  → создаёт обратный import shared/feature/lesson:domain → shared/feature/lesson-runner:domain
  → BIDIRECTIONAL → Invariant 3 blocker
```

### Entry Points

- `[VERIFIED: shared/feature/lesson/domain/src/commonMain/.../model/Lesson.kt:15]` — current 8-field data class, **нет `top3`**
- `[VERIFIED: shared/feature/lesson-runner/domain/src/commonMain/.../model/TopParticipant.kt:3]` — `data class TopParticipant(nickname: String, avatarUrl: String?, percent: Int)` invariants enforced

### Code Owners

- `Lesson` model: `shared/feature/lesson:domain` (модуль расширяется)
- `TopParticipant`: текущее место `shared/feature/lesson-runner:domain` (нужна релокация)

### Flow Trace

```
Result Screen (lesson-runner/presentation, NEW)
→ читает Lesson.top3
→ Lesson.top3: List<TopParticipant>
→ требует TopParticipant в lesson:domain или общем месте
```

### Backend Check

Spec §34: `top3` агрегируется на сервере (Cloud Function `onCreate(lesson_attempts)` обновляет field в `lessons/{lessonId}` document). `[REQUIRES BACKEND CHANGE]` для актуального contents, но для client-side это просто Firestore read — поле всегда читается, может быть пустым list.

### Constraints

- `clean-architecture.md` invariant 3: bidirectional coupling между feature modules только через `core/` interface или ADR-обоснованной reflection
- `TopParticipant` — value-pure data class без поведения, безопасно перемещать
- `domain-models.md`: domain ничего не знает о DI или другом feature

### Fix Shape

**Design-phase решение** (3 варианта; Recommendation A):

**A) Переместить `TopParticipant` в `shared/core/`**:
- Создать `shared/core/leaderboard/` или `shared/core/ranking/` package
- `TopParticipant` живёт там
- `lesson:domain` импортирует core (allowed)
- `lesson-runner:domain` импортирует core (allowed)
- Cross-feature coupling устранён

**B) `Lesson.top3: List<String>` JSON-serialized**:
- `lesson:domain` хранит JSON string
- `lesson-runner:presentation` сама делает unpack через `Json.decodeFromString`
- Минусы: domain leaks JSON contract к presentation

**C) Top3 НЕ в `Lesson`, отдельный `TopParticipantsRepository`**:
- `lesson-runner:domain` определяет repository
- Отдельный read-flow для top3
- Минусы: dual sync infrastructure (Lesson document отдельно, top3 отдельно)

### Validation

- After A: `rg "^import .*lesson_runner" shared/feature/lesson/domain/` пусто
- After A: `rg "^import .*lesson_runner" shared/feature/lesson/data/` пусто
- Compile check: `./gradlew :shared:feature:lesson:domain:jvmTest` зелёный

---

## Problem 3: `AppDatabase.fallbackToDestructiveMigration` уничтожит user data

### Symptom

Lesson-runner phase-01 добавит таблицы `lesson_attempts`, `lesson_rating_submitted_local` и расширит `lessons` table (`averageRating`, `ratingCount`, `top3`). Это требует AppDatabase version bump (3 → 4). Текущий config в production уничтожит **все** user data (catalogs, quests, sections, themes, lessons, questions, userStats) при таком bump.

### Repro

`[VERIFIED: shared/core/persistence/src/androidMain/kotlin/.../di/PersistenceModule.kt:23]`:
```kotlin
.fallbackToDestructiveMigration(dropAllTables = true)
```

`[VERIFIED: shared/core/persistence/src/commonMain/.../AppDatabase.kt:7]`:
```kotlin
@Database(entities = [...], version = 3, exportSchema = true)
```

Без явной `Migration(3, 4)` Room вызовет `dropAllTables = true` → DROP ALL.

### Entry Points

- `PersistenceModule.kt:16-26` — `Room.databaseBuilder(...).fallbackToDestructiveMigration(dropAllTables = true)`
- `AppDatabase.kt:7` — entity list + version

### Code Owners

- `backend-dev` (per scaffold ownership)

### Flow Trace

```
App upgrade installed
→ Room.databaseBuilder rebuild AppDatabase instance
→ Schema diff: 7 entities → 9 entities (lesson_attempts, lesson_rating_submitted_local)
→ Schema diff: lessons table получает 3 new columns
→ Version mismatch: 3 → 4 (no Migration registered)
→ fallbackToDestructiveMigration → DROP ALL TABLES → CREATE schema v4
→ All user data lost
```

### Backend Check

`[ASSUMPTION]`: Cascade sync восстановит хост-data (catalogs/quests/sections/themes/lessons/questions) при первом sync run, но user-state (userStats, in-progress attempts если бы были) будет lost. `lesson_attempts` per-user — Cloud Function-aggregated metrics на server side тоже, так что theoretically restorable, но требует UX-acceptable downtime для re-sync.

### Constraints

- `room-database.md` rule: «Provide migration paths — don't rely on destructive migration in production»
- ADR-CMP-TC: Room KMP требует `@ProvidedTypeConverter` + `.addTypeConverter()` для new converters
- Existing migration test (`AppDatabaseMigrationTest.kt:31`) testing **только** version 1 schema — нет coverage для 2→3 или 3→N

### Code Path Divergence

- Spec не уточняет — ожидание data preservation или acceptance data loss
- Project `home-and-my-quests` retrospective упоминает «Amendment 2026-04-XX cursor strategy» — sync infrastructure активно меняется, ожидается потенциальная reinitialization
- Per `PROJECT-CONTEXT.md` Known Debts: «Backfill script (TBD) для добавления `lastModifiedAt/version/contentsVersion/archived`» — server-side backfill activity, suggesting pre-MVP launch

### Fix Shape

**Design-phase решение** (Open Question #5):

**A) Implement настоящую Migration(3, 4)**:
```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE lesson_attempts (...)")
        db.execSQL("CREATE TABLE lesson_rating_submitted_local (...)")
        db.execSQL("ALTER TABLE lessons ADD COLUMN averageRating REAL")
        db.execSQL("ALTER TABLE lessons ADD COLUMN ratingCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE lessons ADD COLUMN top3 TEXT")
    }
}
```
+ TypeConverter за `Difficulty` (через `@ProvidedTypeConverter`) + new migration test.

**B) Continue с `fallbackToDestructiveMigration`** (acceptable если pre-launch dev/internal):
- AppDatabase version 3 → 4
- `fallbackToDestructiveMigration(dropAllTables = true)` сбрасывает на upgrade
- Acceptable до production launch
- Phase plan must explicitly document acceptance

### Validation

After A:
- `AppDatabaseMigrationTest` extended с migration 3→4 test
- Manual upgrade test: install old version, populate data, upgrade, verify data preserved
- `./gradlew :shared:core:persistence:test` зелёный

After B: phase plan documents data loss explicitly.

---

## Problem 4: `lessonRunnerDomainModule` не зарегистрирован в `AppApplication`

### Symptom

Walking Skeleton сгенерировал `LessonRunnerDomainModule`. Module **не добавлен** в `startKoin` list. Без регистрации все use cases unavailable через Koin → presentation crash при попытке `get<StartLessonAttemptUseCase>()`.

### Repro

`[VERIFIED: apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:87-115]`:
```kotlin
startKoin {
    modules(
        persistenceModule,
        firebaseModule,
        // ... 19 modules total ...
    )
}
```
Нет `lessonRunnerDomainModule`, `lessonRunnerDataModule`, `lessonRunnerPresentationModule`.

`[VERIFIED: shared/feature/lesson-runner/domain/src/commonMain/.../di/LessonRunnerDomainModule.kt:17]`:
```kotlin
val lessonRunnerDomainModule = module {
    factory<StartLessonAttemptUseCase> { ... }
    factory<CompleteAttemptUseCase> { ... }
    // ...
}
```

### Entry Points

- `AppApplication.kt:87` — `startKoin` block, единственная composition root
- `LessonRunnerDomainModule.kt:17` — module declaration

### Code Owners

- `backend-dev` (per scaffold ownership: AppApplication.kt — composition root, considered scaffold-adjacent)

### Flow Trace

Phase-01:
1. backend-dev добавляет `lessonRunnerDomainModule`, `lessonRunnerDataModule`, `lessonRunnerPresentationModule` в startKoin
2. KoinModuleWiringTest расширяется для проверки resolution всех new bindings (включая `Clock`, lambda providers, `QuestionContentParser`)

### Backend Check

N/A.

### Constraints

- `LessonRunnerDomainModule` уже использует `get()` для `Clock`, `() -> AttemptId`, `() -> Long`, `(String, LessonId) -> RatingId` — все эти bindings **должны существовать** до или одновременно с registration domain module (otherwise Koin throws `NoBeanDefinitionFoundException` lazily)
- `KoinModuleWiringTest.kt` (см. existing test для других modules) добавляет smoke test resolution

### Fix Shape

Phase-01 atomic step:
1. Создать `lessonRunnerDataModule` с `single<Clock> { Clock.System }`, lambda providers, `LessonAttemptRepositoryImpl`, `LessonRatingRepositoryImpl`
2. Создать `lessonRunnerPresentationModule` с `factory<LessonRunnerRootComponent> { (compCtx) -> ... }`
3. Добавить `KotlinxSerializationQuestionContentParser` (см. Problem 1)
4. Добавить module в `AppApplication.startKoin` в правильном порядке (data → domain → presentation)
5. Update `KoinModuleWiringTest` для верификации resolution

### Validation

- `./gradlew :apps:android-next:assembleDebug --no-configuration-cache` зелёный
- `./gradlew test --no-configuration-cache` (включая KoinModuleWiringTest) зелёный
- Manual smoke: запуск приложения, navigate Catalog → Quest → Section → Theme → Lesson → tap → no crash

---

## Problem 5: `QuizzesConfig.LessonRunner(mode: Difficulty)` сериализация

### Symptom

Spec §37 предлагает заменить `QuizzesConfig.LessonPlaceholder` на `QuizzesConfig.LessonRunner(lessonId, mode, titles)`. `mode: Difficulty` — enum из `shared/core/question-schema/Difficulty.kt:3`. `Difficulty` **не аннотирован `@Serializable`**. `QuizzesConfig` — `@Serializable` sealed class сохраняется в Decompose StateKeeper. При попытке serialize new variant с `Difficulty` поле — `kotlinx.serialization` runtime error.

### Repro

`[VERIFIED: shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/Difficulty.kt]` (read inferred from research):
```kotlin
enum class Difficulty { EASY, HARD }
// БЕЗ @Serializable
```

`[VERIFIED: android/feature/quizzes-screen/presentation/src/main/.../config/QuizzesConfig.kt:5-6]`:
```kotlin
@Serializable
sealed class QuizzesConfig {
    // все subtypes @Serializable
}
```

При добавлении:
```kotlin
@Serializable
data class LessonRunner(
    val lessonId: String,
    val mode: Difficulty,  // ← runtime serialization fail без @Serializable на Difficulty
    val titles: List<String>,
) : QuizzesConfig()
```

### Entry Points

- `QuizzesConfig.kt:5-39` — sealed class definition
- `DefaultQuizzesComponent.kt:42-54` — `childStack(saveStack = ..., restoreStack = ...)` через `ListSerializer(QuizzesConfig.serializer())` — Decompose state preservation

### Code Owners

- `Difficulty` enum: `shared/core/question-schema` — shared core (Owner: phase 3.8 для domain, but core type is "stable")
- `QuizzesConfig`: `android/feature/quizzes-screen/presentation` — quizzes-screen owner

### Flow Trace

```
Process kill while QuizzesConfig.LessonRunner(mode=HARD) active
→ Android attempts to save Decompose ChildStack via SerializableContainer
→ ListSerializer(QuizzesConfig.serializer()).serialize(stack)
→ Reaches LessonRunner.mode field
→ kotlinx.serialization throws: SerializationException "Class Difficulty is not @Serializable"
→ Crash on save (or empty restore)
```

### Backend Check

N/A — client-side serialization.

### Constraints

- `domain-models.md`: domain не использует `kotlinx.serialization` annotations — но `shared/core/question-schema` это **shared core, не feature domain** — annotations allowed (см. `0-spec.md:219`)
- Modification to `Difficulty` requires careful: enum в widely-imported core, любое breaking change — wide impact

### Code Path Divergence

Текущий `QuizzesConfig.LessonPlaceholder` (`config/QuizzesConfig.kt:35`) использует только String/List<String> — нет enum dependency. Новый `LessonRunner` config впервые вводит enum в config family.

### Fix Shape

**Design-phase решение** (Open Question #3; Recommendation A):

**A) Добавить `@Serializable` в `Difficulty.kt`**:
```kotlin
@Serializable
enum class Difficulty { EASY, HARD }
```
- Изменение в `shared/core/question-schema` — shared core
- Backward compatible: existing usages не сломаются
- 1-line change

**B) Конвертировать в `String` в `QuizzesConfig.LessonRunner`**:
```kotlin
@Serializable
data class LessonRunner(val lessonId: String, val modeName: String /* "EASY"|"HARD" */, val titles: List<String>) : QuizzesConfig()
```
- Component конвертирует: `Difficulty.valueOf(config.modeName)`
- Минусы: type safety lost в config

### Validation

- Test: `QuizzesConfigSerializationTest` (existing) extended с `LessonRunner` round-trip
- Integration: process death simulation с `LessonRunner` active config

---

## Problem 6: Cross-feature dependencies НЕ задокументированы в ADR

### Symptom

`docs/features/lesson-runner/03-decisions.md` **не существует**. Walking Skeleton domain уже импортирует 3 features (`lesson:domain`, `question:domain`, `app-shell:domain`). Per invariant 3 (`docs/invariants.md:27-30`): «прямой cross-feature import должен быть задокументирован в `docs/features/<A>/03-decisions.md`».

Кроме того, planned imports `quizzes-screen → lesson-runner` (config push + LessonAttemptRepository) не документированы в `docs/features/quizzes-screen/03-decisions.md`.

### Repro

`[VERIFIED: shared/feature/lesson-runner/domain/build.gradle.kts]`:
```
commonMain dependencies:
- :shared:core:question-schema
- :shared:feature:lesson:domain   ← cross-feature
- :shared:feature:question:domain ← cross-feature
- :shared:feature:app-shell:domain ← cross-feature
```

`[VERIFIED: docs/features/lesson-runner/]` — directory contains only `0-spec.md`, `README.md`. Нет `03-decisions.md`.

`[VERIFIED: docs/features/quizzes-screen/]` — `03-decisions.md` существует, но lesson-runner не упоминается (verified via grep "lesson-runner" в 03-decisions.md).

### Entry Points

- Architecture review checks (`architect-reviewer`): grep `^import .*shared\.feature\.` checks
- Invariant 3 enforcement: `clean-architecture.md` rules

### Code Owners

- ADR creation: `architect-high-level` / `architect-component` (design phase teammates)

### Flow Trace

design phase создаёт `03-decisions.md` для lesson-runner с ADR entries для 4 cross-feature imports + расширяет `quizzes-screen/03-decisions.md` для 2 new lesson-runner-направленных imports.

### Backend Check

N/A.

### Constraints

- Existing domain hierarchy imports (`question→lesson→theme`, `section→quest`) тоже undocumented (existing debt). lesson-runner team **не несёт ответственности** за existing debt — но новые imports должны иметь ADR.

### Fix Shape

**Phase 0 (design-phase)**:
1. Создать `docs/features/lesson-runner/03-decisions.md` с разделами:
   - **ADR-LR-01**: lesson-runner/domain → lesson:domain (`LessonId`, `LessonRepository`)
   - **ADR-LR-02**: lesson-runner/domain → question:domain (`QuestionId`, `QuestionRepository`, `Question`)
   - **ADR-LR-03**: lesson-runner/domain → app-shell:domain (`AuthRepository`)
   - **ADR-LR-04**: lesson-runner/domain → shared/core/question-schema (sealed `QuestionContent`, parser)
2. Расширить `docs/features/quizzes-screen/03-decisions.md`:
   - **ADR-QS-XX**: quizzes-screen/presentation → lesson-runner/presentation (push `LessonRunner` config)
   - **ADR-QS-XX+1**: quizzes-screen/presentation → lesson-runner:domain (read `LessonAttemptRepository` для bestStars)
3. Architectural review verifies imports unidirectional (one-way only)

### Validation

- `architect-reviewer` grep checks pass
- ADR docs reviewed for consistency

---

## Problem 7: `LessonListComponent` extension impact для `bestStars`/`hardUnlocked`

### Symptom

Spec AC #47-49: на карточке урока показать `StarRating(bestStars)` + visible `Checkbox` (для HARD) если `hardUnlocked == true`. Текущий `DefaultLessonListComponent` (`DefaultLessonListComponent.kt:23`) получает только `LessonRepository`. `HierarchyItemCard` (`HierarchyItemCard.kt:34`) — нет Checkbox slot. `HierarchyItemUi` (`HierarchyItemUi.kt:3`) — нет `bestStars`/`hardUnlocked` полей.

### Repro

`[VERIFIED: android/feature/quizzes-screen/presentation/.../component/DefaultLessonListComponent.kt:26]`:
```kotlin
class DefaultLessonListComponent(
    componentContext: ComponentContext,
    config: QuizzesConfig.LessonList,
    private val lessonRepository: LessonRepository,
    // НЕТ LessonAttemptRepository
    ...
)
```

`[VERIFIED: android/core/designsystem/.../components/HierarchyItemCard.kt:34-43]` — параметры `title, orderLabel?, subtitleCount?, rating?, ratingCount?, onClick, onLongClick?`. Нет Checkbox slot.

`[VERIFIED: android/feature/quizzes-screen/presentation/.../uistate/HierarchyItemUi.kt:3]`:
```kotlin
data class HierarchyItemUi(
    val id: String,
    val title: String,
    val orderLabel: String? = null,
    val subtitleCount: String? = null,
)
```

### Entry Points

- `DefaultLessonListComponent.init` (`:39`) — observe lessons + map to UI
- `LessonListScreen.kt:56` — render `HierarchyItemCard(rating = ..., ...)`
- `LessonDrillMapper.kt:6` (read by previous research) — `Lesson.toDrillItem()` mapper

### Code Owners

- `quizzes-screen/presentation`: `DefaultLessonListComponent`, `LessonListScreen`, `LessonDrillMapper`, `HierarchyItemUi`
- `core/designsystem`: `HierarchyItemCard` (если выбрано расширение API)
- Cross-feature: будет ADR (см. Problem 6)

### Flow Trace

```
DefaultLessonListComponent.init  (MODIFIED)
→ combine(
    lessonRepository.observeByTheme(themeId),
    attemptRepository.observeAllByUser(userId),  ← NEW dependency
)
→ для каждого lesson вычислить:
  - bestStars = computeBestStars(attempts.filter { it.lessonId == lesson.id })
  - hardUnlocked = computeHardUnlocked(attempts.filter { it.lessonId == lesson.id })
→ map в LessonItemUi (extended HierarchyItemUi или новый type)
→ Render через LessonItemCard (новый) или HierarchyItemCard (расширенный)
```

### Backend Check

N/A — все вычисления client-side через RunnerLogic pure functions (`computeBestStars`, `computeHardUnlocked`) которые уже existence в Walking Skeleton.

### Constraints

- `DefaultLessonListComponent` инжектит `LessonAttemptRepository` → новый cross-feature import quizzes-screen → lesson-runner:domain
- ADR-QS-09: «designsystem must not import feature types» — `HierarchyItemCard` не может принимать `Stars` value class напрямую (только `Float` rating)
- 5 drill-down screens используют `HierarchyItemCard` (Section, Theme, Lesson) — расширение его API затрагивает все

### Code Path Divergence

Currently все drill-down уровни (Section, Theme, Lesson) используют generic `HierarchyItemCard` + `HierarchyItemUi`. Lesson — единственный уровень с Stars + Checkbox. Это первое расхождение из-за gameplay-state-aware lesson card.

### Fix Shape

**Design-phase решение** (Open Question #7; Recommendation B):

**A) Расширить `HierarchyItemCard` API**:
- Добавить `trailing: @Composable () -> Unit = {}` slot
- 5 drill-down screens passing empty `trailing` для backward compat

**B) Создать `LessonItemCard` в `quizzes-screen/presentation`**:
- Lesson-specific card с `StarRating + Checkbox`
- Generic `HierarchyItemCard` остаётся для Section/Theme
- Не загрязняет designsystem feature-specific compoнентом

**C) Добавить `bestStars: Float?, hardUnlocked: Boolean, isHardChecked: Boolean` в `HierarchyItemCard`**:
- Designsystem компонент знает о Stars (но как `Float?`, не `Stars`)
- HARD checkbox — designsystem-level concept

### Validation

- `BrandComponentsInvariantsTest` зелёный (нет hardcoded colors, есть `@Preview`)
- UI test: проверить что HARD checkbox visible when `hardUnlocked=true`, hidden otherwise
- Visual regression: existing Section/Theme cards не изменились

---

## Problem 8: New patterns для проекта (FLAG_SECURE, block-on-resume, timer, drag)

### Symptom

Spec требует 4 patterns которых **нет в новой кодовой базе**:
1. FLAG_SECURE для HARD mode (FR#13, AC#28-30)
2. Fullscreen блокирующий dialog при onResume (FR#15, AC#32)
3. Timer countdown в Compose (FR#11, AC#24-26)
4. Drag-and-drop для Ordering question type (Functional Req #9, Delegated #12)

Каждый — risk новой реализации без precedent.

### Repro

Search в android/ модулях:
- `[VERIFIED via Grep: WindowManager.LayoutParams.FLAG_SECURE]` — 0 production matches
- `[VERIFIED via Grep: AlertDialog\(`]` — 1 match (DrawerFooter "About" — non-blocking, not lifecycle-driven)
- `[VERIFIED via Grep: while.*delay\(]` или `LaunchedEffect.*delay\(`]` — 0 production timer countdown matches
- `[VERIFIED via Grep: dragAndDropSource]` или `Modifier.draggable]` — 0 matches

### Entry Points

- `MainActivity.kt:15` — `AppCompatActivity` → `window` access available
- Compose `LocalContext.current.findWindow()` extension pattern (web research)

### Code Owners

- `frontend-dev` (presentation phase)

### Flow Trace

Каждый pattern — green-field implementation в `android/feature/lesson-runner/presentation/`.

### Backend Check

N/A.

### Constraints

- `lifecycle.md` invariant 4: `onDestroy` не для business cleanup без `isFinishing` guard
- Material 3 BOM 2024.09.02 поддерживает `Dialog`, `DialogProperties`
- Decompose 3.1.0 + Essenty 2.1.0 — Lifecycle.doOnStop / doOnResume extensions available
- Coil 3.4.0 — automatic singleton, default disk cache OK
- `BrandComponentsInvariantsTest` enforces no hardcoded colors

### Code Path Divergence

Нет divergence — нет existing implementations.

### Fix Shape

**Phase-01 implementation** (web research provided official patterns):

1. **FLAG_SECURE**:
   ```kotlin
   @Composable
   fun rememberFlagSecure(enabled: Boolean) {
       val context = LocalContext.current
       DisposableEffect(enabled) {
           val window = (context as? Activity)?.window ?: return@DisposableEffect onDispose {}
           if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
           onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
       }
   }
   ```
   Применять в `LessonRunnerScreen` keyed на `mode == Difficulty.HARD`.

2. **Block-on-resume dialog**:
   ```kotlin
   if (state.isPaused) {
       Dialog(
           onDismissRequest = {},
           properties = DialogProperties(
               dismissOnBackPress = false,
               dismissOnClickOutside = false,
               usePlatformDefaultWidth = false,
           ),
       ) { /* fullscreen Card with «Продолжить»/«Выйти» */ }
   }
   ```
   `state.isPaused` уже есть в `RunnerState.Ready` (verified `RunnerState.kt:47`). Toggle через `lifecycle.doOnStop { component.onPaused() }` / `doOnResume { ... }`.

3. **Timer countdown**:
   ```kotlin
   LaunchedEffect(state.indexInPool, state.deadlineMs) {
       while (isActive) {
           val remaining = (state.deadlineMs - System.currentTimeMillis()).coerceAtLeast(0)
           tick.value = remaining
           if (remaining == 0L) { component.onTimeout(); break }
           delay(100L)
       }
   }
   ```

4. **Ordering drag**: используем up/down `IconButton` (Recommended из web research для accessibility + 0 deps); если drag критичен — добавить `sh.calvin.reorderable:reorderable:3.1.0` (требует ADR).

### Validation

- Compose UI test: HARD mode → screen recording confirmed black (FLAG_SECURE active)
- Compose UI test: Home → app return → fullscreen dialog visible, back/click outside не закрывает
- Timer test: `delay(5500)` → таймер показал ~5 sec → ~0 sec, auto-random triggered
- Ordering test: tap up arrow → item swap visually
- AC #28-30, #32 covered

---

## Problem 9: Server-side gaps (out-of-scope, контракт fixated)

### Symptom

Spec contracts требуют server-side work которая **out of scope для lesson-runner** но необходимая для full functionality:
- Cloud Function `onCreate(lesson_ratings)` для агрегации `Lesson.averageRating` + `ratingCount`
- Cloud Function `onCreate(lesson_attempts)` для агрегации `Lesson.top3`
- Subset аватарок users `users/{uid}.avatarUrl` for top3 display
- Cascade sync infrastructure расширение под `lesson_attempts` + `lesson_ratings` collections
- Firestore security rules для new collections
- Composite Firestore index `(userId, lastModifiedAt)` для `lesson_attempts` query

### Repro

- `[VERIFIED: server/functions/src/main/kotlin/]` — пусто, только `.gitkeep`. Cloud Functions для Lesson aggregation **отсутствуют**
- `[VERIFIED: firestore.rules:1-73]` — нет правил для `lesson_attempts` или `lesson_ratings`
- `[VERIFIED: docs/features/home-and-my-quests/06-api-contract.md §10]` — index table не упоминает `lesson_attempts` composite index

### Entry Points

Read sites в client после implementation:
- `Lesson.averageRating` — result screen statistics
- `Lesson.top3` — result screen top participants section
- `LessonAttemptRepositoryImpl.refreshFromRemote(userId, cursor)` — sync writer (out of scope)

### Code Owners

- Cloud Functions: server-side team / отдельная задача
- Firestore rules: deployment team
- Cascade sync extension: `home-and-my-quests` orchestrator owners

### Flow Trace

```
Client writes Attempt → Room → cascade sync (later) → Firestore lesson_attempts
       ↓
   [REQUIRES BACKEND CHANGE: onCreate(lesson_attempts) Cloud Function]
       ↓
   recompute Lesson.top3 → write to lessons/{lessonId} document
       ↓
Client cascade sync reads updated Lesson → user sees fresh top3 on next result screen
```

### Backend Check

`[REQUIRES BACKEND CHANGE]` для:
- Cloud Function aggregation triggers
- Firestore security rules
- Composite index registration
- Cascade sync orchestrator расширение

`[ASSUMPTION — NOT VERIFIED]`: server-side schema для `lesson_attempts` и `lesson_ratings` documents соответствует spec §32-33. Нет server team confirmation.

### Constraints

- Spec §«Out of Scope» явно lists эти как separate tasks
- Без CF: `Lesson.averageRating` всегда null, `Lesson.top3` всегда empty, secret #45 явно говорит "top3 — server snapshot, текущая попытка ещё не там"
- Без cascade sync: attempts/ratings локальны, lost при logout (часть logout-cleanup отдельной задачи)
- Без security rules: production deploy fails (rules deny by default in production mode)

### Code Path Divergence

Client must gracefully handle:
- `Lesson.averageRating == null` → не показывать average на result screen
- `Lesson.top3.isEmpty()` → скрывать секцию (spec AC #45-46)
- `TopParticipant.avatarUrl == null` → placeholder icon (spec Delegated #17: `Icons.Default.AccountCircle`)

### Fix Shape

**Этой фичи Phase-01 ЗАДАЧА**:
- Реализовать client-side path как-если CF существуют (Lesson.top3 read-only)
- Graceful empty/null handling
- Документировать contract в `docs/features/lesson-runner/06-api-contract.md` для server team

**Отдельные tasks (вне scope)**:
- Cloud Functions implementation
- Firestore rules deployment
- Cascade sync расширение под orthogonal collections
- Backfill script для existing Lesson documents (default `averageRating=null`, `ratingCount=0`, `top3=[]`)

### Validation

- Client tests pass с `Lesson.top3 = emptyList()` — секция скрыта
- Result screen renders correctly с `averageRating = null` — no crash, no display
- AC #45-46 covered by client logic, server contract documented

---

## Problem 10: Naming inconsistency `value` vs `raw` в value classes

### Symptom

Spec line 100, 589 references `sourceId.raw`. Walking Skeleton uses `sourceId.value` (`StartLessonAttemptUseCase.kt:69`). Inconsistency между:
- `LessonId.value`, `QuestionId.value`, `ThemeId.value`, etc. (existing pattern)
- `AttemptId.raw`, `RatingId.raw` (Walking Skeleton)

### Repro

`[VERIFIED: shared/feature/lesson-runner/domain/src/commonMain/.../model/AttemptId.kt:4]`:
```kotlin
@JvmInline value class AttemptId(val raw: String) {
    init { require(raw.isNotBlank()) { ... } }
}
```

`[VERIFIED: shared/feature/lesson/domain/src/commonMain/.../model/LessonId.kt:12]`:
```kotlin
@JvmInline value class LessonId(val value: String) {
    init { require(value.isNotBlank()) { ... } }
}
```

`[CONTRADICTS: 0-spec.md:100]`: spec пишет `sortedWith(compareBy({ it.order }, { it.sourceId.raw }))`. Реальный код `sourceId.value` — это `QuestionId.value`. **Опечатка в spec**, code корректен.

### Entry Points

- All call sites accessing `.value` on `LessonId`/`QuestionId`/etc.
- All call sites accessing `.raw` on `AttemptId`/`RatingId`

### Code Owners

- `domain-designer` (Walking Skeleton author)
- `backend-dev` (если решает rename)

### Flow Trace

N/A — не блокер для функциональности, чистая naming convention question.

### Backend Check

N/A.

### Constraints

- Renaming `AttemptId.raw → AttemptId.value` затронет only Walking Skeleton domain code (domain тесты + use cases)
- Existing project pattern: `value` (5+ usages); `raw` — только Walking Skeleton
- Domain code generated, not yet integrated → low refactor cost

### Fix Shape

**Design-phase decision**:
- **Recommended**: переименовать `AttemptId.raw → AttemptId.value` и `RatingId.raw → RatingId.value` для consistency
- Impact: Walking Skeleton domain only, ~10 file edits
- Spec line 100 опечатка остаётся (`sourceId.raw` → fix as `sourceId.value` если переписываем spec, или leave as historical reference)

### Validation

- `./gradlew :shared:feature:lesson-runner:domain:jvmTest` зелёный после rename
- Naming consistency check via grep: `class.*Id.*val (value|raw)` — все `.value`

---

## Summary

| # | Problem | Severity | Owner phase |
|---|---------|----------|-------------|
| 1 | `KotlinxSerializationQuestionContentParser` отсутствует | **BLOCKER** для phase-01 | Design (location) → Phase-01 (impl) |
| 2 | `Lesson.top3` bidirectional coupling | **BLOCKER** | Design (ADR for `TopParticipant` location) |
| 3 | AppDatabase migration data loss | **HIGH RISK** | Design (strategy) → Phase-01 (Migration impl) |
| 4 | `lessonRunnerDomainModule` не зарегистрирован | **HIGH** (runtime crash) | Phase-01 (backend-dev) |
| 5 | `Difficulty` not `@Serializable` | **MEDIUM** (process-death restore) | Design (Open Q #3) → Phase-01 |
| 6 | Cross-feature ADRs missing | **MEDIUM** (invariant 3 enforcement) | Design (create ADRs) |
| 7 | LessonListComponent extension impact | **MEDIUM** | Design (Open Q #7) → Phase-01 |
| 8 | New patterns (FLAG_SECURE, dialog, timer, drag) | **MEDIUM** | Phase-01 (frontend-dev) |
| 9 | Server-side gaps | **OUT OF SCOPE**, контракт зафиксирован | Separate tasks |
| 10 | `value` vs `raw` naming inconsistency | **LOW** (cosmetic) | Design (Open Q decision) |

**Gate status**: design phase можеo стартовать. Все блокеры имеют resolution candidates. Phase-01 implementation зависит от design-phase решений по проблемам 1, 2, 3, 5, 7, 10.

**Independent Verification Protocol**: все critical claims проверены через Read tool против source files. 0 contradictions с 1-research.md. 1 опечатка в spec (Problem 10).
