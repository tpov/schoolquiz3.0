---
phase: phase-07
name: Composition Root & Smoke
date: 2026-04-27
---

# Phase 07 — Composition Root & Smoke

## Goal

Зарегистрировать все новые Koin modules в `AppApplication.startKoin`, написать KoinModuleWiringTest (IT-09a..IT-09g), убедиться что `assembleDebug` зелёный. После фазы фича полностью интегрирована в production binary.

## Scope

- `apps/android-next/.../AppApplication.kt`: добавить `questionSchemaModule`, `lessonRunnerDataModule`, `lessonRunnerDomainKoinAdapter`, `lessonRunnerPresentationModule` в `startKoin` в правильном порядке
- `KoinModuleWiringTest` (или расширение существующего): resolve checks IT-09a..IT-09g
- Smoke validation: `assembleDebug` зелёный + manual nav check

## Role Inputs

- `backend.md` — Yes
- `frontend.md` — No
- `tests.md` — Yes

## Layer

`apps/android-next` (composition root — scaffold ownership: backend-dev)

## Review Tags

`architecture-review` (Koin module registration order; no duplicate bindings)

## State Matrix Coverage

Нет прямого gameplay matrix coverage. Фаза closure для Grounding Problem 4 (missing module registration).

## Domain Contract Coverage

IT-09a..IT-09g — проверяют что ВСЕ domain contracts (repository impls, use cases, providers, parser) resolve через Koin.

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 4: `lessonRunnerDomainModule` не зарегистрирован | `backend-dev` (scaffold ownership) | `AppApplication.kt:87` — startKoin block | Порядок: data → domain adapter → presentation; `questionSchemaModule` до `lessonRunnerDomainKoinAdapter` | Добавить 4 modules в startKoin в правильном порядке | IT-09a..IT-09g; `./gradlew :apps:android-next:assembleDebug` |

## Files

### New Files

- `apps/android-next/src/test/kotlin/.../KoinModuleWiringTest.kt` (или extend existing)

### Modified Files

- `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt` — startKoin modules list

### Deleted Files

None

## Dependencies

- Phase-01 (`questionSchemaModule`)
- Phase-02 (`persistenceModule` updated — already registered)
- Phase-03 (`lessonRunnerDataModule`, `lessonRunnerDomainKoinAdapter`)
- Phase-04 (`lessonRunnerPresentationModule`)
- Phase-05, Phase-06 (все файлы compile-зелёные)

## Criteria for Acceptance

1. `./gradlew :apps:android-next:assembleDebug --no-configuration-cache` — зелёный (no crash, no KoinException)
2. IT-09a: `LessonAttemptRepository` resolves
3. IT-09b: `LessonRatingRepository` resolves
4. IT-09c: `AttemptIdProvider`, `RandomSeedProvider`, `RatingIdProvider` resolves
5. IT-09d: `CompleteAttemptUseCase`, `AbortAttemptUseCase`, `SubmitLessonRatingUseCase` resolves
6. IT-09e: `DefaultLessonRunnerRootComponent` resolved с `parametersOf(ctx, LessonId("l1"), Difficulty.EASY)`
7. IT-09f: `get<QuestionContentParser>()` resolves to `KotlinxSerializationQuestionContentParser`
8. IT-09g: `AppDatabase` builder — `DifficultyConverter` + `TopParticipantListConverter` registered (Room не падает при query)
9. IT-09h: `get<LessonRunnerComponentFactory>()` resolves without exception (composition root binding; NEW BLOCKER B)
10. Manual smoke: navigate Catalog → Quest → Section → Theme → Lesson → tap → no crash → question screen visible

## Tests Required

- IT-09a..IT-09h (полный список в `phase-07/tests.md`):
  - `it09a_lessonAttemptRepository_resolves`: given all new modules registered, when `get<LessonAttemptRepository>()`, then no exception
  - `it09b_lessonRatingRepository_resolves`: given modules registered, when `get<LessonRatingRepository>()`, then resolves
  - `it09c_providers_resolve`: given `lessonRunnerDataModule` loaded, when `get<AttemptIdProvider>()` + `get<RandomSeedProvider>()` + `get<RatingIdProvider>()`, then each resolves to correct default impl
  - `it09d_useCases_resolve`: given `lessonRunnerDomainKoinAdapter` loaded, when `get<CompleteAttemptUseCase>()`, then resolves without exception
  - `it09e_lessonRunnerRootComponent_resolves`: given `lessonRunnerPresentationModule` loaded, when `get<LessonRunnerRootComponent>(parametersOf(ctx, LessonId("l1"), Difficulty.EASY))`, then resolves to `DefaultLessonRunnerRootComponent`
  - `it09f_questionContentParser_resolves`: given `questionSchemaModule` loaded, when `get<QuestionContentParser>()`, then resolves to `KotlinxSerializationQuestionContentParser`
  - `it09g_appDatabase_typeConverters_registered`: given `persistenceModule` with `addTypeConverter`, when DAO observed, then no Room TypeConverter exception
  - `it09h_lessonRunnerComponentFactory_resolves`: given `single<LessonRunnerComponentFactory>` in composition root, when `get<LessonRunnerComponentFactory>()`, then resolves without `NoBeanDefinitionFoundException` (NEW BLOCKER B validation)

## Validation

```bash
./gradlew :apps:android-next:assembleDebug --no-configuration-cache
./gradlew test --no-configuration-cache
./gradlew allTests --no-configuration-cache
./gradlew ciCheck --no-configuration-cache
# AC-55: No Activity/Compose Screen resolves Koin or imports UseCase/Repository/Dao directly.
# Restrict to screen/ files (Component/data layer legitimate consumers of UseCase/Repository).
rg "getKoin\(|koinInject\(|inject<" android/feature/lesson-runner/presentation/src/main -g "**/screen/**/*.kt"
# Expected: empty (Compose screens consume Component callbacks only, not Koin)
rg "^import .*\.(use_case|repository|dao)\." android/feature/lesson-runner/presentation/src/main -g "**/screen/**/*.kt"
# Expected: empty (screens don't import infrastructure; only Component types)
# AC-56: Reverse direction lesson-runner → quizzes-screen (must be empty):
rg "^import .*quizzes_screen" android/feature/lesson-runner -g "*.kt"
# Expected: empty (one-way dependency enforced)
# AC-58: No Hilt/Dagger annotations:
rg "@(Inject|Provides|Binds|Module|HiltAndroidApp|AndroidEntryPoint|HiltViewModel)" apps android shared platform -g "*.kt"
# Expected: empty
# Manual smoke:
# 1. Launch app on device/emulator
# 2. Navigate Catalog → Quest → Section → Theme → LessonList
# 3. Tap lesson → LessonRunnerScreen opens (no crash)
# 4. Answer questions → result screen
```

## Handoff Notes

После phase-07:
- Фича complete. Cascade sync infrastructure (out of scope) продолжает работу отдельно.

## Pattern Invariants

- Module registration ПОРЯДОК ОБЯЗАТЕЛЕН: `questionSchemaModule` → `lessonRunnerDataModule` → `lessonRunnerDomainKoinAdapter` → `lessonRunnerPresentationModule` — per Koin lazy resolution; dependency before dependent
- `KoinModuleWiringTest` ОБЯЗАН использовать `startKoin { modules(...) }` + `stopKoin()` в `@After` — isolate test
- НЕ добавлять `lessonRunnerDomainModule` (Walking Skeleton's own module) напрямую — `lessonRunnerDomainKoinAdapter` заменяет его для production Koin wiring (ADR-LR-09)
- Проверить что `persistenceModule` уже содержит `single { get<AppDatabase>().lessonAttemptDao() }` после Phase-02 (иначе добавить в Phase-07)
