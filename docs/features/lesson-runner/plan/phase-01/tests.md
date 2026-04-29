---
phase: phase-01
role: test-dev
---

# Phase 01 — Tests

> Domain JVM тесты (DT-01..DT-89) уже зелёные (Walking Skeleton). НЕ дублировать. Фаза-01 тесты — integration тесты для новых production types + regression для rename.

## Pattern Invariants

- Тесты используют Walking Skeleton fakes: `FakeQuestionContentParser`, `FakeLessonRepository`, `FakeLessonAttemptRepository` — НЕ создавать дубликаты
- JVM tests в `commonTest` или `jvmTest` source sets, НЕ в `androidTest`
- Нет Turbine (per rules/testing.md); Flow tests через `.take(n).toList()` или `StateFlow.value`
- `test-dev` не модифицирует production code; если нужна модификация — Open Question в RESULT

## Test Locations

| Test | Location |
|------|----------|
| Parser round-trip | `shared/core/question-schema/src/commonTest/` |
| `AttemptId.value` regression | `shared/feature/lesson-runner/domain/src/commonTest/` (модифицировать существующие, не новые) |
| `Lesson` new fields | `shared/feature/lesson/domain/src/commonTest/` |
| Bidirectional coupling grep | validation command (не JUnit) |
| Domain purity grep | validation command (не JUnit) |

---

## Scenario Group A — Parser Round-trip

### A-01 `parser_roundtrip_singleChoice`

- **Given:** JSON строка `{"type":"SingleChoice","questionText":"Q?","options":[{"id":"1","text":"A"},{"id":"2","text":"B"}],"correctOptionId":"1","hasImage":false}`
- **When:** `KotlinxSerializationQuestionContentParser().parse(json)`
- **Then:** `Result.success` с `QuestionContent.SingleChoice`, `options.size == 2`, `correctOptionId == "1"`
- **AC:** Problem 1 fix validation

### A-02 `parser_roundtrip_multipleChoice`

- **Given:** JSON с `"type":"MultipleChoice"`, `correctOptionIds` list
- **When:** parse
- **Then:** `Result.success(QuestionContent.MultipleChoice)`, `correctOptionIds` корректны

### A-03 `parser_roundtrip_ordering`

- **Given:** JSON с `"type":"Ordering"`, `items` list
- **When:** parse
- **Then:** `Result.success(QuestionContent.Ordering)`, `items` order preserved

### A-04 `parser_roundtrip_fillBlank`

- **Given:** JSON с `"type":"FillBlank"`, `blanks`, `candidates`
- **When:** parse
- **Then:** `Result.success(QuestionContent.FillBlank)`, blank count matches

### A-05 `parser_unknownType_returnsFailure`

- **Given:** `{"type":"VideoQuestion","foo":"bar"}`
- **When:** parse
- **Then:** `Result.failure` (не crash, не NPE)

### A-06 `parser_emptyString_returnsFailure`

- **Given:** `""`
- **When:** parse
- **Then:** `Result.failure`

### A-07 `parser_malformedJson_returnsFailure`

- **Given:** `"not-json"`
- **When:** parse
- **Then:** `Result.failure`

### A-08 `parser_ignoresUnknownKeys`

- **Given:** JSON с корректным `"type":"SingleChoice"` + лишнее поле `"extraField":"ignored"`
- **When:** parse
- **Then:** `Result.success` (ignoreUnknownKeys работает)

---

## Scenario Group B — `Difficulty @Serializable`

### B-01 `difficulty_easy_serializable_roundtrip`

- **Given:** `Difficulty.EASY`
- **When:** `Json.encodeToString(Difficulty.serializer(), it)` → `Json.decodeFromString`
- **Then:** `Difficulty.EASY` (round-trip)

### B-02 `difficulty_hard_serializable_roundtrip`

- **Given:** `Difficulty.HARD`
- **When:** round-trip
- **Then:** `Difficulty.HARD`

---

## Scenario Group C — `AttemptId` rename regression

Эти тесты — модификация существующих Walking Skeleton тестов (не новые файлы). `test-dev` проверяет compile gate.

### C-01 `attemptId_value_field_accessible`

- **Given:** `val id = AttemptId("abc-123")`
- **When:** `id.value`
- **Then:** `"abc-123"` (`.raw` больше не существует — compile error если осталось)

### C-02 `ratingId_value_field_accessible`

- **Given:** `val id = RatingId("sha256hash")`
- **When:** `id.value`
- **Then:** `"sha256hash"`

---

## Scenario Group D — `Lesson` new fields

### D-01 `lesson_defaultFields_noRatingData`

- **Given:** `Lesson(id=..., themeId=..., title=..., ...existing required fields...)`
- **When:** access `averageRating`, `ratingCount`, `top3`
- **Then:** `averageRating == null`, `ratingCount == 0`, `top3 == emptyList()`

### D-02 `lesson_withTop3_notEmpty`

- **Given:** `Lesson(..., top3 = listOf(TopParticipant("Alice", null, 90)))`
- **When:** `lesson.top3`
- **Then:** `size == 1`, `first().nickname == "Alice"`, `first().percent == 90`

### D-03 `lesson_withAverageRating_nonNull`

- **Given:** `Lesson(..., averageRating = 2.5f, ratingCount = 10)`
- **When:** access fields
- **Then:** `averageRating == 2.5f`, `ratingCount == 10`

---

## Scenario Group E — `TopParticipant`

### E-01 `topParticipant_serializable_roundtrip`

- **Given:** `TopParticipant(nickname="Bob", avatarUrl="https://...", percent=85)`
- **When:** `Json.encodeToString` → `Json.decodeFromString<TopParticipant>`
- **Then:** decoded `== original`

### E-02 `topParticipant_nullAvatarUrl_roundtrip`

- **Given:** `TopParticipant(nickname="X", avatarUrl=null, percent=0)`
- **When:** round-trip
- **Then:** `avatarUrl == null` (не `"null"` строка)

---

## Fake Blueprints

Не создавать новые fakes в этой фазе. Walking Skeleton fakes (`FakeQuestionContentParser` и др.) используются как есть.

Для parser тестов (Group A) — прямое инстанцирование `KotlinxSerializationQuestionContentParser()` без fake.

---

## Validation Commands

```bash
# Parser tests (new)
./gradlew :shared:core:question-schema:jvmTest --no-configuration-cache

# Walking Skeleton regression after rename (89 tests должны быть зелёными)
./gradlew :shared:feature:lesson-runner:domain:jvmTest --no-configuration-cache

# Lesson domain tests
./gradlew :shared:feature:lesson:domain:jvmTest --no-configuration-cache

# Leaderboard module
./gradlew :shared:core:leaderboard:jvmTest --no-configuration-cache

# Bidirectional coupling check (must be empty)
rg "^import .*lesson_runner" shared/feature/lesson/domain/ -g "*.kt"

# Domain purity (must be empty — no serialization imports in lesson-runner/domain)
rg "^import (kotlinx\.serialization)" shared/feature/lesson-runner/domain/src/commonMain -g "*.kt"
```
