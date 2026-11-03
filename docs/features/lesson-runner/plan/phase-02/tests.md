---
phase: phase-02
role: test-dev
---

# Phase 02 — Tests

> MT-01..MT-07 — instrumented migration tests (нужен эмулятор). DAO tests — in-memory Room (instrumented). Mapper tests — JVM.

## Pattern Invariants

- Migration tests используют `MigrationTestHelper` + `AndroidJUnit4` — per `08-storage-model.md §Migration Test Template`
- DAO tests используют Room in-memory database (`Room.inMemoryDatabaseBuilder(...)`) — per `testing.md` conventions
- Нет мокирования Room (in-memory вместо mocks)
- `test-dev` не модифицирует production code

## Test Locations

| Test | Location |
|------|----------|
| MT-* (migration) | `shared/core/persistence/src/androidInstrumentedTest/` |
| DAO tests | `shared/core/persistence/src/androidInstrumentedTest/` |
| TypeConverter unit | `shared/core/persistence/src/androidTest/` или `jvmTest` |
| LessonMapper unit | `shared/feature/lesson/data/src/commonTest/` |

---

## Migration Tests (MT-01..MT-07)

Template из `08-storage-model.md §Migration Test Template`. Все используют `MigrationTestHelper`.

### MT-01 `migrate3to4_lessonAttemptsCreated`

- **Given:** database version 3 (no lesson_attempts)
- **When:** `MIGRATION_3_4.migrate()`
- **Then:** `lesson_attempts` table exists; columns: `attempt_id, user_id, lesson_id, lesson_version, is_hard, code_answer, percent_score, completed_at`

### MT-02 `migrate3to4_lessonRatingSubmittedLocalCreated`

- **Given:** database version 3
- **When:** migrate
- **Then:** `lesson_rating_submitted_local` table exists; compound PK `(user_id, lesson_id)`; column `submitted_at` exists

### MT-03 `migrate3to4_existingLessonsNewColumnsDefaultValues`

- **Given:** v3 database with one `lessons` row inserted
- **When:** migrate to v4
- **Then:** lesson row preserved; `average_rating IS NULL`, `rating_count = 0`, `top3 = '[]'`

### MT-04 `migrate3to4_allExistingTablesPreserved`

- **Given:** v3 database; insert one row in each of 7 tables: `user_stats, catalogs, quests, sections, themes, lessons, questions`
- **When:** migrate to v4
- **Then:** all 7 rows still exist after migration (no data loss)

### MT-05 `difficultyConverter_roundtrip`

- **Given:** `DifficultyConverter`; `Difficulty.EASY`
- **When:** `toDb(EASY)` → `fromDb(...)`
- **Then:** `Difficulty.EASY`; for `HARD`: same

### MT-06 `productionBuildConfig_noFallbackToDestructiveMigration`

- **Given:** production `AppDatabase` builder code
- **When:** grep `rg "fallbackToDestructive" shared/core/persistence/src/androidMain -g "*.kt"`
- **Then:** empty result (production build does not use fallback)

### MT-07 `topParticipantListConverter_roundtrip`

- **Given:** `TopParticipantListConverter`
- **When:** `toDb(listOf(TopParticipant("Alice", null, 90)))` → `fromDb(result)`
- **Then:** `listOf(TopParticipant("Alice", null, 90))` (round-trip)

### MT-07b `topParticipantListConverter_emptyString_nocrash`

- **Given:** `TopParticipantListConverter`
- **When:** `fromDb("")`
- **Then:** `emptyList()` — no exception

### MT-07c `topParticipantListConverter_malformedJson_nocrash`

- **Given:** `fromDb("not-json")`
- **Then:** `emptyList()` — no crash

---

## DAO Tests

### DAO-01 `lessonAttemptDao_upsert_thenObserveByLesson`

- **Given:** in-memory Room v4; `LessonAttemptEntity(attemptId="a1", userId="u1", lessonId="l1", ...)`
- **When:** `dao.upsert(entity)` then `dao.observeByLesson("u1", "l1").take(1).toList()`
- **Then:** returns list with 1 entity, `attemptId == "a1"`

### DAO-02 `lessonAttemptDao_observeAllByUser_filtersCorrectly`

- **Given:** 2 attempts for user "u1", 1 for "u2"; each inserted via `dao.upsert(entity)`
- **When:** `dao.observeAllByUser("u1").take(1).toList()`
- **Then:** 2 results for "u1", 0 from "u2"

### DAO-03 `lessonRatingLocalDao_upsert_hasSubmitted`

- **Given:** `LessonRatingSubmittedLocalEntity(userId="u1", lessonId="l1", submittedAt=1000L)`
- **When:** `dao.upsert(entity)` then `dao.hasSubmitted("u1", "l1").take(1).first()`
- **Then:** returns `true` (`Flow<Boolean>` — per `06-api-contract.md:666`; `upsert` uses `OnConflictStrategy.REPLACE` per `06-api-contract.md:662`)

### DAO-04 `lessonRatingLocalDao_hasSubmitted_differentUser`

- **Given:** entity for ("u1", "l1") inserted via `dao.upsert(entity)`
- **When:** `dao.hasSubmitted("u2", "l1").take(1).first()`
- **Then:** returns `false` (`Flow<Boolean>`)

### DAO-05 `lessonRatingLocalDao_upsert_idempotent`

- **Given:** insert same entity twice via `dao.upsert(entity)` (`OnConflictStrategy.REPLACE`)
- **When:** second upsert
- **Then:** no exception; `dao.hasSubmitted("u1", "l1").take(1).first() == true`

---

## Mapper Tests

### Mapper-01 `lessonMapper_toDomain_newFieldsMapped`

- **Given:** `LessonEntity(..., averageRating=2.5f, ratingCount=10, top3="[]")`
- **When:** `entity.toDomain()`
- **Then:** `Lesson.averageRating == 2.5f`, `Lesson.ratingCount == 10`, `Lesson.top3 == emptyList()`

### Mapper-02 `lessonMapper_toDomain_top3Parsed`

- **Given:** `LessonEntity(..., top3 = """[{"nickname":"Bob","avatarUrl":null,"percent":85}]""")`
- **When:** `entity.toDomain()`
- **Then:** `Lesson.top3 == listOf(TopParticipant("Bob", null, 85))`

### Mapper-03 `lessonDtoMapper_backwardCompat_missingTop3Field`

- **Given:** `LessonDto(...)` без `top3` (null)
- **When:** `dto.toEntity()`
- **Then:** `LessonEntity.top3 == "[]"`; no NullPointerException

### Mapper-04 `lessonDtoMapper_backwardCompat_missingRatingCount`

- **Given:** `LessonDto(ratingCount=null)`
- **When:** `dto.toEntity()`
- **Then:** `LessonEntity.ratingCount == 0`

---

## Validation Commands

```bash
# Migration instrumented tests (requires emulator/device)
./gradlew :shared:core:persistence:connectedAndroidTest --no-configuration-cache

# Lesson data mapper JVM tests
./gradlew :shared:feature:lesson:data:jvmTest --no-configuration-cache

# Persistence fallback check (must be empty)
rg "fallbackToDestructive" shared/core/persistence/src/androidMain -g "*.kt"
# Expected: empty (or debug-only commented block)
```
