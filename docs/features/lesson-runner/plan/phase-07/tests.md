---
phase: phase-07
role: test-dev
---

# Phase 07 — Tests

> IT-09a..IT-09g — Koin module wiring integration tests. In `apps/android-next/src/test/`.

## Pattern Invariants

- `KoinModuleWiringTest` использует `startKoin { modules(...all new modules...) }` + `@After stopKoin()`
- Тесты проверяют только resolve (не behavior)
- `test-dev` не модифицирует production code

---

## IT-09a..IT-09g Koin Module Wiring Tests

Location: `apps/android-next/src/test/kotlin/.../KoinModuleWiringTest.kt` (extend or create)

### IT-09a `lessonAttemptRepository_resolves`

- **Given:** all new modules registered in Koin
- **When:** `get<LessonAttemptRepository>()`
- **Then:** no `NoBeanDefinitionFoundException`; returns non-null instance

### IT-09b `lessonRatingRepository_resolves`

- **Given:** modules registered
- **When:** `get<LessonRatingRepository>()`
- **Then:** resolves without exception

### IT-09c `providers_resolve`

- **Given:** `lessonRunnerDataModule` loaded
- **When:** `get<AttemptIdProvider>()`, `get<RandomSeedProvider>()`, `get<RatingIdProvider>()`
- **Then:** each resolves to correct default impl type

### IT-09d `useCases_resolve`

- **Given:** `lessonRunnerDomainKoinAdapter` loaded
- **When:** `get<CompleteAttemptUseCase>()`, `get<AbortAttemptUseCase>()`, `get<SubmitLessonRatingUseCase>()`
- **Then:** each resolves without exception

### IT-09e `lessonRunnerRootComponent_resolves`

- **Given:** `lessonRunnerPresentationModule` loaded; `DefaultComponentContext`
- **When:** `get<LessonRunnerRootComponent>(parameters = parametersOf(ctx, LessonId("l1"), Difficulty.EASY))`
- **Then:** resolves to `DefaultLessonRunnerRootComponent` instance

### IT-09f `questionContentParser_resolves`

- **Given:** `questionSchemaModule` loaded
- **When:** `get<QuestionContentParser>()`
- **Then:** resolves to `KotlinxSerializationQuestionContentParser`

### IT-09g `appDatabase_typeConverters_registered`

- **Given:** `persistenceModule` loaded with `addTypeConverter` calls
- **When:** `get<AppDatabase>().lessonAttemptDao().observeByLesson("u","l").take(1).toList()` (in-memory Room or just check DAO resolve)
- **Then:** no Room exception about missing TypeConverter; `DifficultyConverter` and `TopParticipantListConverter` registered

### IT-09h `lessonRunnerComponentFactory_resolves`

- **Given:** `startKoin { modules(lessonRunnerPresentationModule, ...) }` with `single<LessonRunnerComponentFactory>` binding in composition root (per `06-api-contract.md:374`)
- **When:** `get<LessonRunnerComponentFactory>()`
- **Then:** resolves without `NoBeanDefinitionFoundException`; returns non-null factory instance (NEW BLOCKER B: factory binding in AppApplication.kt composition root)

---

## Validation Commands

```bash
./gradlew :apps:android-next:assembleDebug --no-configuration-cache
./gradlew test --no-configuration-cache
./gradlew allTests --no-configuration-cache
./gradlew ciCheck --no-configuration-cache
```
