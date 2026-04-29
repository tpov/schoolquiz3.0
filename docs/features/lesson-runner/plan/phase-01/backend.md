---
phase: phase-01
role: backend-dev
---

# Phase 01 — Backend Tasks

> Walking Skeleton domain (`shared/feature/lesson-runner/domain/`) — NOT modify (кроме AttemptId/RatingId rename per ADR-LR-12 и import update TopParticipant per ADR-LR-05). Только wrap/rename/import update. Use cases, RunnerState, RunnerLogic, repository interfaces, 7 existing fakes — без изменений.

## Pattern Invariants

- Koin module naming convention: `val <featureName>Module = module { ... }` per `appShellDataModule` pattern
- New Gradle modules ДОЛЖНЫ быть включены в `settings.gradle.kts` через `include(":shared:core:leaderboard")` — backend-dev owned
- `@ProvidedTypeConverter` pattern: НЕ в этой фазе (фаза-02); здесь только core types
- `@Serializable` на `QuestionContent` sealed interface ДОЛЖЕН использовать default discriminator (поле `"type"`) без `@JsonClassDiscriminator` annotation — per ADR-LR-08
- Все новые Koin modules в `androidMain` source set, не в `commonMain`

---

## Create `shared/core/leaderboard` module

### Create `build.gradle.kts` for leaderboard

- **Файл:** `shared/core/leaderboard/build.gradle.kts`
- **Тип:** build script (Gradle KTS)
- **Сигнатура:** KMP multiplatform module, commonMain only
- **Вход:** N/A — build script
- **Поведение / Выход:**
  - KMP module с `commonMain` source set
  - `kotlinx-serialization-json` dependency (для `@Serializable` на `TopParticipant`)
  - Без Android target (pure Kotlin common)
  - Pattern соответствует другим `shared/core/*/build.gradle.kts` в проекте
- **Edge cases:**
  - Убедиться что version catalog entry для `kotlinx-serialization-json` уже существует в `libs.versions.toml` (он там есть — `question-schema` его использует)
- **Depends on:** `libs.versions.toml` (existing), Kotlin multiplatform plugin (existing)
- **Canonical reference:** internal (no api-contract entry)
- **Rationale:** Новый минимальный core module; структура аналогична `shared/core/question-schema/build.gradle.kts`

---

### Create `TopParticipant`

- **Файл:** `shared/core/leaderboard/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/leaderboard/TopParticipant.kt`
- **Тип:** data class
- **Сигнатура:** `@Serializable data class TopParticipant(val nickname: String, val avatarUrl: String?, val percent: Int)`
- **Вход:** `nickname: String`, `avatarUrl: String?` (null = нет аватарки), `percent: Int` (0..100)
- **Поведение / Выход:**
  - Immutable value type
  - `@Serializable` — обязательно для `TopParticipantListConverter` (Room JSON) и Firestore mapping
  - `percent` семантически = `percentScore` значение (0..100)
  - `avatarUrl == null` → UI рендерит placeholder icon (`Icons.Default.AccountCircle` per spec §9 Delegated)
- **Edge cases:**
  - `percent` не validated в domain (сервер агрегирует); Range 0..100 документируется как contract, не enforced через require
  - Пустой `nickname` технически допустим (сервер может прислать; UI отображает как есть)
- **Depends on:** `kotlinx.serialization` (via leaderboard/build.gradle.kts)
- **Canonical reference:** `06-api-contract.md:112` (§LR-5a)
- **Rationale:** Перемещён из `lesson-runner/domain` (ADR-LR-05) для устранения bidirectional coupling с `lesson:domain`

---

## Modify `shared/core/question-schema`

### Modify `QuestionContent` — add `@Serializable`

- **Файл:** `shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/QuestionContent.kt`
- **Тип:** sealed interface (existing — modify)
- **Сигнатура:** `@Serializable sealed interface QuestionContent` с subclasses `SingleChoice`, `MultipleChoice`, `Ordering`, `FillBlank`
- **Вход:** N/A — modify existing type
- **Поведение / Выход:**
  - Добавить `@Serializable` на sealed interface
  - Добавить `@SerialName("SingleChoice")` на subclass `SingleChoice`
  - Добавить `@SerialName("MultipleChoice")` на subclass `MultipleChoice`
  - Добавить `@SerialName("Ordering")` на subclass `Ordering`
  - Добавить `@SerialName("FillBlank")` на subclass `FillBlank`
  - Discriminator field: default `"type"` (без `@JsonClassDiscriminator`)
  - Subclass fields (`options`, `items`, `blanks`, `candidates`, `correctOptionId` и т.д.) — без изменений
- **Edge cases:**
  - Если subclasses не `data class` — может потребоваться `@Serializable` на каждой отдельно; compiler guide
  - `hasImage: Boolean` и `imageUrl: String?` — если существуют, добавить `@SerialName` соответственно
  - Не добавлять `@JsonClassDiscriminator` — использовать default `"type"` ключ per ADR-LR-08
- **Depends on:** `kotlinx-serialization-json` (существует в build.gradle.kts:13)
- **Canonical reference:** ADR-LR-08
- **Rationale:** Необходимо для `KotlinxSerializationQuestionContentParser`; core module разрешает serialization annotations (не feature domain)

---

### Modify `Difficulty` — add `@Serializable`

- **Файл:** `shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/Difficulty.kt`
- **Тип:** enum class (existing — modify)
- **Сигнатура:** `@Serializable enum class Difficulty { EASY, HARD }`
- **Вход:** N/A — 1-line annotation addition
- **Поведение / Выход:**
  - Backward-compatible change: existing consumers не ломаются
  - Разрешает `QuizzesConfig.LessonRunner(mode: Difficulty)` сохранение в Decompose StateKeeper (ADR-LR-06)
- **Edge cases:**
  - Проверить что `kotlinx.serialization` уже в classpath (да, через `question-schema/build.gradle.kts:13`)
- **Depends on:** `kotlinx-serialization-core` (через существующий dep)
- **Canonical reference:** `ADR-LR-06`, `06-api-contract.md:17` (§LR-1)
- **Rationale:** 1-line fix для Grounding Problem 5 (process-death restore crash)

---

### Create `KotlinxSerializationQuestionContentParser`

- **Файл:** `shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/KotlinxSerializationQuestionContentParser.kt`
- **Тип:** class
- **Сигнатура:** `class KotlinxSerializationQuestionContentParser : QuestionContentParser`
- **Вход:** N/A — stateless, no constructor params
- **Поведение / Выход:**
  - Реализует `QuestionContentParser.parse(payload: String): Result<QuestionContent>`
  - Использует `Json { ignoreUnknownKeys = true }` для forward-compatibility
  - `Json.decodeFromString<QuestionContent>(payload)` → `Result.success(content)` при успехе
  - Catch `SerializationException` → `Result.failure(e)`
  - Catch `IllegalArgumentException` → `Result.failure(e)` (bad JSON)
  - Stateless — один instance на приложение (Koin `single`)
- **Edge cases:**
  - `payload` пустая строка → `SerializationException` caught → `Result.failure`
  - `payload` с неизвестным `"type"` → `SerializationException` (polymorphic fallback missing) → `Result.failure`
  - `ignoreUnknownKeys = true` — защита от будущих schema extensions
  - Не логировать PII в payload при failure (только exception message)
- **Depends on:** `QuestionContent` (existing sealed interface + `@Serializable`), `QuestionContentParser` (existing interface), `kotlinx-serialization-json`
- **Canonical reference:** `ADR-LR-08`
- **Rationale:** Production impl отсутствовала (Grounding Problem 1); location в core per ADR-LR-08 для переиспользования другими фичами

---

### Create `QuestionSchemaModule` (Koin)

- **Файл:** `shared/core/question-schema/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/di/QuestionSchemaModule.kt`
- **Тип:** object / top-level val
- **Сигнатура:** `val questionSchemaModule = module { single<QuestionContentParser> { KotlinxSerializationQuestionContentParser() } }`
- **Вход:** N/A — Koin module declaration
- **Поведение / Выход:**
  - `single<QuestionContentParser>` — один инстанс на приложение
  - Регистрирует `KotlinxSerializationQuestionContentParser` как production impl
  - Добавляется в `AppApplication.startKoin` в Phase-07 (composition root)
- **Edge cases:**
  - `androidMain` source set — Koin зависит на Android SDK; commonMain не подходит
  - Нет других bindings в этом module (он minimal)
- **Depends on:** `KotlinxSerializationQuestionContentParser` (новый), Koin (через существующий dep в question-schema или добавить)
- **Canonical reference:** `06-api-contract.md:494` (§LR-13 questionSchemaModule)
- **Rationale:** Отдельный module per ADR-LR-08 (не в `lessonRunnerDataModule`) для переиспользования

---

## Create Provider interfaces in `shared/feature/lesson-runner/domain`

> BLOCKER 4 fix: Walking Skeleton использует lambda types `() -> AttemptId` etc. в use case constructors. Phase-03 создаёт `DefaultAttemptIdProvider : AttemptIdProvider`. Интерфейсы должны существовать в domain ДО того как Default* импсы (androidMain phase-03) к ним обращаются. Добавляются в phase-01 как extension к domain (не modify existing Walking Skeleton logic).

### Create `AttemptIdProvider`

- **Файл:** `shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/AttemptIdProvider.kt`
- **Тип:** interface
- **Сигнатура:** `interface AttemptIdProvider { fun next(): AttemptId }`
- **Вход:** N/A — interface definition
- **Поведение / Выход:**
  - `fun next(): AttemptId` — генерация нового уникального AttemptId
  - Каждый вызов должен возвращать новый уникальный ID (invariant для impl, не enforced в interface)
- **Edge cases:**
  - Interface — без реализации; impl (`DefaultAttemptIdProvider`) в Phase-03 data/androidMain
- **Depends on:** `AttemptId` (domain value class, renamed in this phase)
- **Canonical reference:** `06-api-contract.md:496`
- **Rationale:** Walking Skeleton use cases требуют `() -> AttemptId` функциональный тип; wrapper interface позволяет Koin inject через `get<AttemptIdProvider>()::next` adapter (ADR-LR-09); domain-level interface = platform-neutral

---

### Create `RandomSeedProvider`

- **Файл:** `shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/RandomSeedProvider.kt`
- **Тип:** interface
- **Сигнатура:** `interface RandomSeedProvider { fun next(): Long }`
- **Вход:** N/A — interface definition
- **Поведение / Выход:**
  - `fun next(): Long` — генерация seed для детерминированного random subset selection
  - Seed используется в `StartLessonAttemptUseCase` для воспроизводимого shuffle
- **Edge cases:**
  - Impl (`DefaultRandomSeedProvider`) использует `System.currentTimeMillis()` в androidMain; commonMain ничего не знает о платформе
- **Depends on:** N/A
- **Canonical reference:** `06-api-contract.md:500`
- **Rationale:** Platform-agnostic interface; конкретная реализация Platform-specific (System.currentTimeMillis)

---

### Create `RatingIdProvider`

- **Файл:** `shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/RatingIdProvider.kt`
- **Тип:** interface
- **Сигнатура:** `interface RatingIdProvider { fun provide(userId: String, lessonId: LessonId): RatingId }`
- **Вход:** `userId: String` (Firebase UID), `lessonId: LessonId` (value object)
- **Поведение / Выход:**
  - `fun provide(userId: String, lessonId: LessonId): RatingId` — детерминированный ID из (userId, lessonId)
  - Детерминированность: те же входы → тот же ID; impl использует sha256
- **Edge cases:**
  - Interface не знает о sha256; это impl-детали `DefaultRatingIdProvider`
  - `lessonId: LessonId` — domain value object (после rename к `.value` в этой же фазе)
- **Depends on:** `RatingId` (domain value class, renamed in this phase), `LessonId` (domain)
- **Canonical reference:** `06-api-contract.md:504`
- **Rationale:** Детерминированный ID per spec §33; interface в domain позволяет use case (`SubmitLessonRatingUseCase`) принимать функциональный тип `(String, LessonId) -> RatingId` через adapter `get<RatingIdProvider>()::provide` (ADR-LR-09)

---

## Modify `shared/feature/lesson-runner/domain`

### Rename `AttemptId.raw → AttemptId.value`

- **Файл:** `shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/AttemptId.kt`
- **Тип:** value class (existing — modify)
- **Сигнатура:** `@JvmInline value class AttemptId(val value: String)`
- **Вход:** N/A — rename field
- **Поведение / Выход:**
  - Единственное изменение: `val raw: String` → `val value: String`
  - Compile-guided: IDE/compiler покажет все call sites `AttemptId(...).raw` → нужно заменить на `.value`
  - `init { require(value.isNotBlank()) { ... } }` — require остаётся без изменений (update message if it references "raw")
- **Edge cases:**
  - `CompleteAttemptUseCase.kt`, `AbortAttemptUseCase.kt` — `AttemptId(raw = ...)` → `AttemptId(value = ...)`
  - Все domain тесты `AttemptId("x").raw` → `.value`
  - `CodeAnswer.raw` — НЕ переименовывать (другое поле, не ID)
- **Depends on:** N/A
- **Canonical reference:** `ADR-LR-12`
- **Rationale:** Consistency с проектным convention (`LessonId.value`, `QuestionId.value` etc.)

---

### Rename `RatingId.raw → RatingId.value`

- **Файл:** `shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/RatingId.kt`
- **Тип:** value class (existing — modify)
- **Сигнатура:** `@JvmInline value class RatingId(val value: String)`
- **Вход:** N/A — rename field
- **Поведение / Выход:**
  - Единственное изменение: `val raw: String` → `val value: String`
  - `SubmitLessonRatingUseCase.kt` — `RatingId(raw = ...)` → `RatingId(value = ...)`
  - Compile-guided rename
- **Edge cases:**
  - Проверить `LessonRunnerDomainModule.kt` если есть lambda `(String, LessonId) -> RatingId { ... raw = ... }`
- **Depends on:** N/A
- **Canonical reference:** `ADR-LR-12`
- **Rationale:** Consistency; низкая стоимость rename до integration

---

### Delete local `TopParticipant.kt`, update import

- **Файл:** `shared/feature/lesson-runner/domain/src/commonMain/kotlin/.../model/TopParticipant.kt`
- **Тип:** DELETE
- **Сигнатура:** N/A — file deletion
- **Вход:** N/A
- **Поведение / Выход:**
  - Удалить файл
  - Добавить `:shared:core:leaderboard` в `lesson-runner/domain/build.gradle.kts` commonMain dependencies
  - Обновить все imports `TopParticipant` в lesson-runner/domain с local → `com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant`
  - Walking Skeleton tests, использующие `TopParticipant`, обновляют import
- **Edge cases:**
  - Проверить Walking Skeleton fakes — если `FakeLessonAttemptRepository` использует `TopParticipant` (маловероятно, но compile-guided)
- **Depends on:** `shared/core/leaderboard/TopParticipant.kt` (созданный выше)
- **Canonical reference:** `ADR-LR-05`
- **Rationale:** Устранение bidirectional coupling (Grounding Problem 2)

---

## Modify `shared/feature/lesson/domain`

### Modify `Lesson` — add 3 new fields

- **Файл:** `shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/model/Lesson.kt`
- **Тип:** data class (existing — modify)
- **Сигнатура:** `data class Lesson(...existing fields..., val averageRating: Float? = null, val ratingCount: Int = 0, val top3: List<TopParticipant> = emptyList())`
- **Вход:** все существующие поля плюс 3 новых с defaults
- **Поведение / Выход:**
  - `averageRating: Float? = null` — null пока нет ни одной оценки (server aggregated)
  - `ratingCount: Int = 0` — non-nullable, default 0 (ADR-LR-15, align с Quest.averageRatingCount)
  - `top3: List<TopParticipant> = emptyList()` — пуст до Cloud Function aggregation
  - Default values обеспечивают backward-compatibility в JVM tests (existing `Lesson(...)` constructors не сломаются)
- **Edge cases:**
  - Существующие `FakeLessonRepository` instances в `lesson:domain/commonTest` — compile-guided update (defaults покрывают большинство)
  - Если есть `copy()` call sites в `lesson:domain` тестах — compiler guide
- **Depends on:** `TopParticipant` из `shared/core/leaderboard` (добавить dep в `lesson/domain/build.gradle.kts`)
- **Canonical reference:** `06-api-contract.md:129` (§LR-5)
- **Rationale:** Spec §34; `Lesson.top3` нужен для result screen; `averageRating`/`ratingCount` — для server-side aggregation display

---

## Modify `settings.gradle.kts`

- **Файл:** `settings.gradle.kts`
- **Тип:** build script (existing — modify)
- **Сигнатура:** добавить `include(":shared:core:leaderboard")`
- **Вход:** N/A — 1-line include
- **Поведение / Выход:**
  - Gradle знает о новом module
  - Pattern: найти секцию `include(":shared:core:...")` и добавить строку рядом
- **Edge cases:**
  - Порядок `include` не важен для Gradle (alphabetical convention)
- **Depends on:** `shared/core/leaderboard/build.gradle.kts` (создан выше)
- **Canonical reference:** internal (scaffold ownership: backend-dev)
- **Rationale:** Новый module должен быть зарегистрирован в settings для разрешения Gradle dep graph
