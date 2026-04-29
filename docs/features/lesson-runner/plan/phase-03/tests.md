---
phase: phase-03
role: test-dev
---

# Phase 03 — Tests

> Integration tests для repository round-trip. Используют Walking Skeleton fakes или in-memory Room. Не дублируют domain JVM tests.

## Pattern Invariants

- `LessonAttemptRepositoryImpl` тесты — использовать `FakeLessonAttemptDao` (in-memory backing) или in-memory Room, НЕ Mockito
- Walking Skeleton fakes (`FakeLessonAttemptRepository` и др.) — в `commonTest` domain; эти тесты — в `data/commonTest` или `data/androidInstrumentedTest`
- Provider тесты — JVM (нет Android dep)

## Test Locations

| Test | Location |
|------|----------|
| Repository round-trip | `shared/feature/lesson-runner/data/src/commonTest/` (fake DAOs) или `androidInstrumentedTest` (in-memory Room) |
| Provider unit | `shared/feature/lesson-runner/data/src/commonTest/` |
| Mapper unit | `shared/feature/lesson-runner/data/src/commonTest/` |

---

## Scenario Group A — Repository Integration

### IT-01 (Phase-03 proxy) `lessonAttemptRepository_save_thenObserve`

- **Given:** `LessonAttemptRepositoryImpl` с `FakeLessonAttemptDao`; `Attempt(id=AttemptId("a1"), userId="u1", lessonId=LessonId("l1"), mode=EASY, codeAnswer=CodeAnswer("9"), percentScore=PercentScore(100), completedAt=1000L, lessonVersion=2L)`
- **When:** `repository.save(attempt)` then `repository.observeByLesson("u1", LessonId("l1")).take(1).toList()`
- **Then:** `Result.success`; observed list contains `attempt` with matching fields

### IT-07 (proxy) `lessonAttemptRepository_noWritesDuringPlaythrough`

- **Given:** `FakeLessonAttemptDao` with `upsertCallCount = 0`
- **When:** simulate 3 questions answered without calling `repository.save()`
- **Then:** `upsertCallCount == 0` after 3 answers (only `RepositoryImpl.save()` triggers `dao.upsert()`)

### IT-08 (proxy) `lessonRatingRepository_submit_thenHasSubmitted`

- **Given:** `LessonRatingRepositoryImpl` с `FakeLessonRatingLocalDao`; `LessonRating(...)` for ("u1", LessonId("l1"))
- **When:** `repository.submit(rating)` then `repository.hasSubmitted("u1", LessonId("l1")).take(1).first()`
- **Then:** `Result.success`; `hasSubmitted == true` (`Flow<Boolean>` — per `06-api-contract.md:666`)

### IT-08b `lessonRatingRepository_hasSubmitted_differentPair`

- **Given:** submit for ("u1", "l1") via `repository.submit(rating)`
- **When:** `repository.hasSubmitted("u1", LessonId("l2")).take(1).first()`
- **Then:** `false` (`Flow<Boolean>`)

---

## Scenario Group B — Mappers

### Map-01 `attemptMapper_toEntity_isHardEasy`

- **Given:** `Attempt(mode = Difficulty.EASY, ...)`
- **When:** `attempt.toEntity()`
- **Then:** `entity.isHard == 0`

### Map-02 `attemptMapper_toEntity_isHardHard`

- **Given:** `Attempt(mode = Difficulty.HARD, ...)`
- **When:** `attempt.toEntity()`
- **Then:** `entity.isHard == 1`

### Map-03 `attemptMapper_toEntity_attemptIdValue`

- **Given:** `Attempt(id = AttemptId("uuid-123"), ...)`
- **When:** `attempt.toEntity()`
- **Then:** `entity.attemptId == "uuid-123"` (uses `.value` per ADR-LR-12)

### Map-04 `attemptMapper_entityToDomain_roundtrip`

- **Given:** `LessonAttemptEntity(isHard=1, attemptId="abc", codeAnswer="19", percentScore=85, ...)`
- **When:** `entity.toDomain()`
- **Then:** `domain.mode == HARD`; `domain.id.value == "abc"`; `domain.codeAnswer.raw == "19"`; `domain.percentScore.raw == 85`

---

## Scenario Group C — Providers

### Prov-01 `defaultAttemptIdProvider_returnsNonEmpty`

- **Given:** `DefaultAttemptIdProvider()`
- **When:** `provider.next()`
- **Then:** `AttemptId.value.isNotBlank() == true`

### Prov-02 `defaultAttemptIdProvider_returnsUniqueIds`

- **Given:** `DefaultAttemptIdProvider()`
- **When:** call `next()` twice
- **Then:** two different `AttemptId.value` strings

### Prov-03 `defaultRatingIdProvider_deterministic`

- **Given:** `DefaultRatingIdProvider()`; same inputs: `userId="u1"`, `lessonId=LessonId("l1")`
- **When:** call `provide("u1", LessonId("l1"))` twice
- **Then:** both results have same `RatingId.value`

### Prov-04 `defaultRatingIdProvider_differentInputs_differentIds`

- **Given:** two pairs: ("u1","l1") and ("u1","l2")
- **When:** `provide` for each
- **Then:** different `RatingId.value`

### Prov-05 `defaultRandomSeedProvider_returnsLong`

- **Given:** `DefaultRandomSeedProvider()`
- **When:** `next()`
- **Then:** returns non-zero Long (System.currentTimeMillis())

---

## Fake Blueprints

### `FakeLessonAttemptDao`

Для testing `LessonAttemptRepositoryImpl` без in-memory Room:

- In-memory `MutableList<LessonAttemptEntity>` as backing store
- `upsert(entity): Long` — append to list (replace by attemptId if exists); track `upsertCallCount`; return row index as Long (`>= 0` = success)
- `observeByLesson(userId, lessonId)` — `MutableStateFlow(list.filter { it.userId==userId && it.lessonId==lessonId })`; update on each `upsert`
- `observeAllByUser(userId)` — filter by userId; `MutableStateFlow` updated on upsert

### `FakeLessonRatingLocalDao`

- In-memory `MutableSet<Pair<String, String>>` for (userId, lessonId) pairs
- `upsert(entity): Long` — add to set (REPLACE semantics — idempotent); return `0L`
- `hasSubmitted(userId, lessonId): Flow<Boolean>` — `MutableStateFlow(set.contains(pair))`; updated after each `upsert` call

---

## Validation Commands

```bash
./gradlew :shared:feature:lesson-runner:data:jvmTest --no-configuration-cache
./gradlew detekt ktlintCheck --no-configuration-cache
# No Android imports in commonMain:
rg "^import (android|androidx)\." shared/feature/lesson-runner/data/src/commonMain -g "*.kt"
```
