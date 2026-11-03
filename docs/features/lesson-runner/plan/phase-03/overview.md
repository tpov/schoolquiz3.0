---
phase: phase-03
name: Data Layer — lesson-runner/data NEW MODULE
tag: complex
date: 2026-04-27
---

# Phase 03 — Data Layer (lesson-runner/data)

## Goal

Создать новый KMP module `shared/feature/lesson-runner/data/` с production реализациями repository interfaces, Provider implementations, и Koin DI adapter. После фазы Walking Skeleton domain может быть интегрирован в Android composition root — все domain use cases получают production dependencies.

## Scope

- Новый Gradle-модуль `shared/feature/lesson-runner/data/` (build.gradle.kts)
- `LessonAttemptRepositoryImpl` и `LessonRatingRepositoryImpl` — Room-backed
- `DefaultAttemptIdProvider`, `DefaultRandomSeedProvider`, `DefaultRatingIdProvider` в `androidMain`
- `lessonRunnerDataModule` (Koin) — providers + repository impls
- `lessonRunnerDomainKoinAdapter` (Koin) — bridges wrapper interfaces to domain function types (ADR-LR-09)
- Integration repository round-trip тесты

## Role Inputs

- `backend.md` — Yes
- `frontend.md` — No
- `tests.md` — Yes

## Layer

`shared/feature/lesson-runner/data` (новый data module)

## Review Tags

`architecture-review` (новый модуль, cross-layer imports), `concurrency-review` (Flow emissions, suspend functions, coroutineScope)

## State Matrix Coverage

Matrix 4 (когда писать attempt): Phase-03 реализует `LessonAttemptRepository.save()` — один write per attempt. Matrix тест IT-07 "No writes during 3-question playthrough" проверяется здесь.

## Domain Contract Coverage

Phase-03 реализует domain repository interfaces:
- `LessonAttemptRepository` (интеграция Room via `LessonAttemptDao`)
- `LessonRatingRepository` (интеграция Room via `LessonRatingLocalDao`)
- Provider interfaces per ADR-LR-09

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 1: Production `QuestionContentParser` отсутствует | `shared/feature/lesson-runner/data` — backend-dev | `LessonRunnerDomainModule.kt:25` — `get<QuestionContentParser>()` | Parser resolved через `questionSchemaModule` (Phase-01), не data module | `lessonRunnerDomainKoinAdapter` получает parser через `get<QuestionContentParser>()` (resolved from questionSchemaModule) | IT-09f resolved в Phase-07 smoke test |
| Problem 4: `lessonRunnerDomainModule` не зарегистрирован | `backend-dev` (composition root) | `AppApplication.kt:87` — startKoin block | `lessonRunnerDataModule` + `lessonRunnerDomainKoinAdapter` созданы в этой фазе; регистрация — Phase-07 | Создать оба Koin modules с canonical bindings | IT-09a..IT-09d resolved в Phase-07 |

## Files

### New Files

- `shared/feature/lesson-runner/data/build.gradle.kts`
- `shared/feature/lesson-runner/data/src/commonMain/kotlin/.../repository/LessonAttemptRepositoryImpl.kt`
- `shared/feature/lesson-runner/data/src/commonMain/kotlin/.../repository/LessonRatingRepositoryImpl.kt`
- `shared/feature/lesson-runner/data/src/commonMain/kotlin/.../mapper/LessonAttemptMapper.kt`
- `shared/feature/lesson-runner/data/src/commonMain/kotlin/.../mapper/LessonRatingMapper.kt`
- `shared/feature/lesson-runner/data/src/androidMain/kotlin/.../provider/DefaultAttemptIdProvider.kt`
- `shared/feature/lesson-runner/data/src/androidMain/kotlin/.../provider/DefaultRandomSeedProvider.kt`
- `shared/feature/lesson-runner/data/src/androidMain/kotlin/.../provider/DefaultRatingIdProvider.kt`
- `shared/feature/lesson-runner/data/src/androidMain/kotlin/.../di/LessonRunnerDataModule.kt`
- `shared/feature/lesson-runner/data/src/androidMain/kotlin/.../di/LessonRunnerDomainKoinAdapter.kt`

### Modified Files

- `settings.gradle.kts` — include `:shared:feature:lesson-runner:data`

### Deleted Files

None

## Dependencies

- Phase-01 (core types: `Difficulty`, `QuestionContent`, `TopParticipant`, `AttemptId.value`, `RatingId.value`)
- Phase-02 (persistence: `LessonAttemptDao`, `LessonRatingLocalDao` available via `persistenceModule`)
- Phase-03 НЕ зависит от Phase-04 (presentation) — может идти параллельно

## Criteria for Acceptance

1. `shared/feature/lesson-runner/data/` compiles: `./gradlew :shared:feature:lesson-runner:data:jvmTest`
2. `LessonAttemptRepositoryImpl.save(attempt)` → `LessonAttemptDao.insert(entity)` → Room round-trip тест (IT-01 mock)
3. `LessonRatingRepositoryImpl.submit(rating)` → `LessonRatingLocalDao.insert(entity)` → IT-08 mock
4. `DefaultAttemptIdProvider.next()` возвращает UUID (непустой, уникальный per call)
5. `DefaultRatingIdProvider.provide(userId, lessonId)` → детерминированный sha256 hash
6. `lessonRunnerDataModule` объявлен; `lessonRunnerDomainKoinAdapter` объявлен
7. Нет прямых imports `android.*`, `androidx.*` в `commonMain` source set (domain purity для data если commonMain)
8. Stateful field reset: нет stateful полей в RepositoryImpl (они stateless); только DAO state в Room

## Tests Required

- `IT-01 proxy`: given `FakeLessonAttemptDao`, when `LessonAttemptRepositoryImpl.save(attempt)`, then `dao.insert` called once with correctly mapped entity
- `IT-08 proxy`: given `FakeLessonRatingLocalDao`, when `LessonRatingRepositoryImpl.submit(rating)`, then `dao.insert` called; `hasSubmitted` returns true
- `defaultAttemptIdProvider_returnsUniqueIds`: two consecutive `next()` calls → different IDs
- `defaultRatingIdProvider_deterministic`: same (userId, lessonId) → same RatingId.value
- `attemptMapper_domainToEntity`: given `Attempt` domain object, when `toEntity()`, then entity fields match (isHard, codeAnswer, percentScore, completedAt, lessonVersion)
- `attemptMapper_entityToDomain_roundtrip`: entity → domain → entity field check

## Validation

```bash
./gradlew :shared:feature:lesson-runner:data:jvmTest --no-configuration-cache
./gradlew detekt ktlintCheck --no-configuration-cache
# No Android imports in data/commonMain:
rg "^import (android|androidx)\." shared/feature/lesson-runner/data/src/commonMain -g "*.kt"
# Expected: empty
```

## Handoff Notes

После phase-03:
- Phase-07 (composition root) добавляет `lessonRunnerDataModule` + `lessonRunnerDomainKoinAdapter` в `AppApplication.startKoin`
- Phase-04 (presentation) получает `LessonAttemptRepository` через Koin injection — реализован здесь

## Pattern Invariants

- `LessonAttemptRepositoryImpl` ОБЯЗАН делать ровно 1 Room write per attempt (один `dao.insert`) — per spec §19, Matrix 4; тест IT-07 проверяет "no writes during playthrough"
- `LessonRatingRepositoryImpl.hasSubmitted` ОБЯЗАН делегировать `dao.hasSubmitted > 0` — не хранить local state
- `DefaultRatingIdProvider.provide` ОБЯЗАН использовать детерминированный sha256 hash (`"$userId:$lessonId"`) — per `06-api-contract.md:87` (§LR-4 document ID = sha256)
- `lessonRunnerDomainKoinAdapter` ОБЯЗАН жить в `data/androidMain` (не `domain`) — per ADR-LR-09 C1 fix
- Imports из `lesson-runner/domain` в `lesson-runner/data` разрешены (data → domain); обратное — запрещено

## Options Considered

| Критерий | Option A: wrapper interfaces в domain + adapter в data (recommended) | Option B: lambda function types напрямую в Koin | Option C: module parameters функция |
|----------|-----------------------------------------------------------------------|------------------------------------------------|--------------------------------------|
| Type erasure риск | Нет (named interfaces) | Высокий (все Function0<*> конфликтуют) | Нет |
| Testability | Высокая (fake реализации interfaces) | Низкая (lambda мокировать сложно) | Средняя |
| Переиспользование providers | Да (в core если нужно) | Нет | Нет |
| Coupling domain↔Koin | Нет (domain defines interface) | Да (domain зависит от Koin паттерна) | Нет |
| Complexity | Средняя | Низкая но хрупкая | Низкая |

**Recommended: Option A** (resolved в ADR-LR-09)

**Rejected Option B:** JVM type erasure: все `() -> X` имеют erasure `Function0<*>`, Koin без `named()` разрешает первый registered. Хрупко при росте числа providers.

**Rejected Option C:** Один consumer — ограничивает переиспользование; если `AttemptIdProvider` нужен другой фиче — нельзя переиспользовать.
