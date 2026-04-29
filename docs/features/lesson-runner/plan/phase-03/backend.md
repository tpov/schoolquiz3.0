---
phase: phase-03
role: backend-dev
---

# Phase 03 — Backend Tasks

## Pattern Invariants

- `LessonAttemptRepositoryImpl` ДОЛЖЕН делать ровно 1 `dao.upsert()` per attempt (Matrix 4 invariant; `OnConflictStrategy.REPLACE` per `06-api-contract.md:641`)
- `LessonRatingRepositoryImpl` ДОЛЖЕН использовать `OnConflictStrategy.REPLACE` семантику — idempotent submit (per `06-api-contract.md:662`)
- Providers в `androidMain` source set — UUID и System.currentTimeMillis() — Android/JVM specific
- `lessonRunnerDomainKoinAdapter` живёт в `data/androidMain` — НИКОГДА в `domain` (ADR-LR-09 C1 fix)
- RepositoryImpl конструкторы принимают DAO interfaces, НЕ конкретные Room классы

---

## Create `shared/feature/lesson-runner/data` build script

- **Файл:** `shared/feature/lesson-runner/data/build.gradle.kts`
- **Тип:** build script
- **Сигнатура:** KMP multiplatform module; `commonMain` + `androidMain` source sets
- **Вход:** N/A
- **Поведение / Выход:**
  - `commonMain` dependencies: `:shared:feature:lesson-runner:domain`, `:shared:core:persistence`
  - `androidMain` dependencies: Koin Android (для Koin modules в androidMain)
  - `jvmTest` dependencies: JUnit4, coroutines-test
  - Pattern: аналогично `shared/feature/quest/data/build.gradle.kts` (существующий)
- **Edge cases:**
  - Проверить что `:shared:core:persistence` правильно exposed (api vs implementation)
  - `commonTest` — для mapper unit tests (нет Android dep)
- **Depends on:** `:shared:feature:lesson-runner:domain` (Phase-01 output), `:shared:core:persistence` (Phase-02 output)
- **Canonical reference:** internal (no api-contract entry)
- **Rationale:** Новый data module; структура аналогична другим feature data modules в проекте

---

## Create `LessonAttemptRepositoryImpl`

- **Файл:** `shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt`
- **Тип:** class
- **Сигнатура:** `class LessonAttemptRepositoryImpl(private val attemptDao: LessonAttemptDao) : LessonAttemptRepository`
- **Вход:** `attemptDao: LessonAttemptDao` (constructor injection)
- **Поведение / Выход:**
  - `override suspend fun save(attempt: Attempt): Result<Unit>` — `attemptDao.upsert(attempt.toEntity())` в try/catch → если `rowId > 0` → `Result.success`; exception → `Result.failure`
  - `override fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>>` — `attemptDao.observeByLesson(userId, lessonId.value).map { list -> list.map { it.toDomain() } }`
  - `override fun observeAllByUser(userId: String): Flow<List<Attempt>>` — аналогично
  - Stateless: нет in-memory fields, нет MutableState
- **Edge cases:**
  - `save` при IO error → `Result.failure(IOException)` — presentation показывает SaveAttemptFailed event
  - `upsert` возвращает `Long` (row id); проверять `>= 0` для success; `-1` при IGNORE конфликте не применяется (REPLACE strategy)
  - `observe*` flows — Room автоматически emits on change; нет manual invalidation нужен
  - `attempt.toEntity()` использует `LessonAttemptMapper`
- **Depends on:** `LessonAttemptRepository` (domain interface), `LessonAttemptDao` (Phase-02, `06-api-contract.md:645` upsert), `LessonAttemptMapper`
- **Canonical reference:** `06-api-contract.md:494` (LessonAttemptRepositoryImpl in lessonRunnerDataModule)
- **Rationale:** Production Room-backed impl; `upsert` (REPLACE) соответствует canonical DAO per `06-api-contract.md:645`; Walking Skeleton `FakeLessonAttemptRepository` остаётся для tests

---

## Create `LessonRatingRepositoryImpl`

- **Файл:** `shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonRatingRepositoryImpl.kt`
- **Тип:** class
- **Сигнатура:** `class LessonRatingRepositoryImpl(private val ratingLocalDao: LessonRatingLocalDao) : LessonRatingRepository`
- **Вход:** `ratingLocalDao: LessonRatingLocalDao`
- **Поведение / Выход:**
  - `override suspend fun submit(rating: LessonRating): Result<Unit>` — `ratingLocalDao.upsert(rating.toEntity())` в try/catch → `Result.success/failure`
  - `override fun hasSubmitted(userId: String, lessonId: LessonId): Flow<Boolean>` — `ratingLocalDao.hasSubmitted(userId, lessonId.value)` — прямой pass-through `Flow<Boolean>` из DAO; НЕ `suspend fun ... : Boolean` (canonical per `06-api-contract.md:666`)
  - `LessonRating` domain type — из Walking Skeleton domain; `rating.toEntity()` через mapper
- **Edge cases:**
  - `hasSubmitted` возвращает `Flow<Boolean>` — `DefaultLessonRunnerRootComponent` собирает через `.first()` для одноразовой проверки при инициализации result screen; `showRatingPrompt = !hasSubmitted.first()`
  - `submit` idempotent: повторный вызов для same (userId, lessonId) → `REPLACE` conflict → строка обновляется → `Result.success`
  - `upsert` возвращает `Long` (row id); если `>= 0` → успех; exception catches IO errors
- **Depends on:** `LessonRatingRepository` (domain interface), `LessonRatingLocalDao` (Phase-02, `06-api-contract.md:663` upsert), `LessonRatingMapper`
- **Canonical reference:** `06-api-contract.md:494` (LessonRatingRepositoryImpl in lessonRunnerDataModule)
- **Rationale:** `hasSubmitted(): Flow<Boolean>` — canonical возврат DAO (не `suspend Int`); уточнён по `06-api-contract.md:635` (§LR-16 LessonRatingLocalDao); local dedup storage; Firestore write (out of scope) синхронизируется cascade sync infrastructure

---

## Create `LessonAttemptMapper`

- **Файл:** `shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt`
- **Тип:** extension functions (top-level)
- **Сигнатура:** `fun Attempt.toEntity(): LessonAttemptEntity` и `fun LessonAttemptEntity.toDomain(): Attempt`
- **Вход:** `Attempt` domain object ↔ `LessonAttemptEntity` Room entity
- **Поведение / Выход:**
  - `Attempt.toEntity()`:
    - `attemptId = attempt.id.value` (ADR-LR-12: `.value` не `.raw`)
    - `userId = attempt.userId`
    - `lessonId = attempt.lessonId.value`
    - `lessonVersion = attempt.lessonVersion`
    - `isHard = if (attempt.mode == Difficulty.HARD) 1 else 0`
    - `codeAnswer = attempt.codeAnswer.raw` (CodeAnswer.raw — НЕ переименован per ADR-LR-12)
    - `percentScore = attempt.percentScore.raw` (PercentScore.raw)
    - `completedAt = attempt.completedAt`
  - `LessonAttemptEntity.toDomain()`:
    - `id = AttemptId(entity.attemptId)`
    - `mode = if (entity.isHard != 0) Difficulty.HARD else Difficulty.EASY`
    - `codeAnswer = CodeAnswer(entity.codeAnswer)`
    - `percentScore = PercentScore(entity.percentScore)`
    - etc.
- **Edge cases:**
  - `CodeAnswer.raw` vs `PercentScore.raw` — эти поля НЕ переименованы (только AttemptId + RatingId per ADR-LR-12)
  - `isHard` stored as Int (0/1) → mapper конвертирует
- **Depends on:** `Attempt`, `AttemptId`, `LessonId`, `Difficulty`, `CodeAnswer`, `PercentScore` (из domain)
- **Canonical reference:** `08-storage-model.md §New Table: lesson_attempts`
- **Rationale:** Standard mapper chain per `domain-models.md`: Entity ↔ Mapper ↔ Domain

---

## Create `LessonRatingMapper`

- **Файл:** `shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonRatingMapper.kt`
- **Тип:** extension function
- **Сигнатура:** `fun LessonRating.toEntity(): LessonRatingSubmittedLocalEntity`
- **Вход:** `LessonRating` domain object
- **Поведение / Выход:**
  - `userId = rating.userId`
  - `lessonId = rating.lessonId.value`
  - `submittedAt = System.currentTimeMillis()` (или via clock if available — prefer Clock injection)
  - No `rating.value` field in entity (only submission flag; value stays in Firestore path)
- **Edge cases:**
  - Entity stores только dedup flag — rating Int значение НЕ хранится локально; оно идёт в Firestore (sync path, out of scope)
- **Depends on:** `LessonRating` (domain), `LessonRatingSubmittedLocalEntity` (Phase-02)
- **Canonical reference:** `08-storage-model.md §New Table: lesson_rating_submitted_local`
- **Rationale:** Minimal entity; only stores (userId, lessonId, submittedAt) for dedup

---

## Create `DefaultAttemptIdProvider`

- **Файл:** `shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultAttemptIdProvider.kt`
- **Тип:** class
- **Сигнатура:** `class DefaultAttemptIdProvider : AttemptIdProvider`
- **Вход:** N/A — stateless
- **Поведение / Выход:**
  - `override fun next(): AttemptId = AttemptId(UUID.randomUUID().toString())`
  - Каждый вызов — новый UUID v4
- **Edge cases:**
  - UUID uniqueness — Java UUID.randomUUID() достаточен для production use
- **Depends on:** `AttemptIdProvider` (domain interface, `06-api-contract.md:527`), `AttemptId` (domain value class)
- **Canonical reference:** `06-api-contract.md:527`
- **Rationale:** Platform-specific UUID generation в androidMain; interface в domain (ADR-LR-09)

---

## Create `DefaultRandomSeedProvider`

- **Файл:** `shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultRandomSeedProvider.kt`
- **Тип:** class
- **Сигнатура:** `class DefaultRandomSeedProvider : RandomSeedProvider`
- **Вход:** N/A — stateless
- **Поведение / Выход:**
  - `override fun next(): Long = System.currentTimeMillis()`
  - Seed для random subset selection; время starта попытки
- **Edge cases:**
  - Два вызова в одну миллисекунду вернут одинаковый seed → разные попытки в одну мс имеют одинаковый subset. Допустимо (крайне редко)
- **Depends on:** `RandomSeedProvider` (domain)
- **Canonical reference:** `06-api-contract.md:527`
- **Rationale:** System.currentTimeMillis() в androidMain; platform-specific

---

## Create `DefaultRatingIdProvider`

- **Файл:** `shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultRatingIdProvider.kt`
- **Тип:** class
- **Сигнатура:** `class DefaultRatingIdProvider : RatingIdProvider`
- **Вход:** N/A — stateless
- **Поведение / Выход:**
  - `override fun provide(userId: String, lessonId: LessonId): RatingId`
  - Computation: sha256 of `"$userId:${lessonId.value}"` → hex string → `RatingId(hexString)`
  - Детерминированный: те же входы → тот же ID
  - Совпадает с Firestore document ID schema per `06-api-contract.md:87`
- **Edge cases:**
  - sha256 стандартный; использовать `MessageDigest.getInstance("SHA-256")`
  - Hex encoding: lower-case hexadecimal (64 chars)
  - `userId` пустой — технически допустимо (не валидировать здесь; auth guard в UseCase)
- **Depends on:** `RatingIdProvider` (domain), `RatingId` (domain), `LessonId` (domain)
- **Canonical reference:** `06-api-contract.md:87`, `06-api-contract.md:527`
- **Rationale:** Детерминированный ID per spec §33: "sha256(userId:lessonId)" для Firestore dedup

---

## Create `lessonRunnerDataModule`

- **Файл:** `shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt`
- **Тип:** top-level val (Koin module)
- **Сигнатура:** `val lessonRunnerDataModule = module { ... }`
- **Вход:** N/A — Koin module declaration
- **Поведение / Выход:**
  - `single<AttemptIdProvider> { DefaultAttemptIdProvider() }`
  - `single<RandomSeedProvider> { DefaultRandomSeedProvider() }`
  - `single<RatingIdProvider> { DefaultRatingIdProvider() }`
  - `single<Clock> { Clock.System }` — kotlinx.datetime Clock binding (required by `lessonRunnerDomainKoinAdapter` use case factories below); shared singleton instance
  - `single<LessonAttemptRepository> { LessonAttemptRepositoryImpl(attemptDao = get()) }`
  - `single<LessonRatingRepository> { LessonRatingRepositoryImpl(ratingLocalDao = get()) }`
  - `get()` для DAOs — resolved через `persistenceModule` (Phase-02)
  - `QuestionContentParser` binding — НЕ здесь; живёт в `questionSchemaModule` (ADR-LR-08)
- **Edge cases:**
  - Проверить что `LessonAttemptDao` и `LessonRatingLocalDao` exposed в `persistenceModule` через `single { get<AppDatabase>().lessonAttemptDao() }` (Phase-02 backend.md PersistenceModule)
- **Depends on:** все выше + `persistenceModule` (Phase-02)
- **Canonical reference:** `06-api-contract.md:494`
- **Rationale:** Production Koin wiring для data layer

---

## Create `lessonRunnerDomainKoinAdapter`

- **Файл:** `shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt`
- **Тип:** top-level val (Koin module)
- **Сигнатура:** `val lessonRunnerDomainKoinAdapter = module { ... }`
- **Вход:** N/A — Koin module
- **Поведение / Выход:**
  - `factory<StartLessonAttemptUseCase> { StartLessonAttemptUseCase(questionRepository = get(), lessonRepository = get(), parser = get(), authRepository = get(), clock = get(), randomSeedProvider = get<RandomSeedProvider>()::next) }`
  - `factory<CompleteAttemptUseCase> { CompleteAttemptUseCase(attemptRepository = get(), ratingRepository = get(), clock = get(), attemptIdProvider = get<AttemptIdProvider>()::next) }` — canonical per `06-api-contract.md:568`
  - `factory<AbortAttemptUseCase> { AbortAttemptUseCase(attemptRepository = get(), clock = get(), attemptIdProvider = get<AttemptIdProvider>()::next) }` — canonical per `06-api-contract.md:574`
  - `factory<SubmitLessonRatingUseCase> { SubmitLessonRatingUseCase(ratingRepository = get(), lessonRepository = get(), ratingIdProvider = get<RatingIdProvider>()::provide, clock = get()) }` — canonical per `06-api-contract.md:580`
  - Adapter pattern: `get<WrapperInterface>()::methodName` конвертирует interface в function type
  - `clock = get()` — `Clock` instance; `single<Clock> { Clock.System }` добавить в `lessonRunnerDataModule` (shared infrastructure)
  - `factory` (не `single`) для use cases — они создаются per-component instance
- **Edge cases:**
  - Параметры canonical per `06-api-contract.md:549` (lessonRunnerDomainKoinAdapter block start); НЕ адаптировать самостоятельно — compile-error = сигнал обновить канонический doc
  - `get<RatingIdProvider>()::provide` — method reference конвертирует `(String, LessonId) -> RatingId` function type
- **Depends on:** все use cases (Walking Skeleton domain), все providers + repos (lessonRunnerDataModule)
- **Canonical reference:** `06-api-contract.md:549`
- **Rationale:** Bridges Koin typed interfaces to domain function type expectations; живёт в data, не domain (ADR-LR-09 C1 fix)
