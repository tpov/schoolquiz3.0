---
phase: phase-02
name: Persistence — Migration & Schema
tag: complex
date: 2026-04-27
---

# Phase 02 — Persistence (Migration & Schema)

## Goal

Реализовать Room schema v3→v4: новые таблицы `lesson_attempts` и `lesson_rating_submitted_local`, расширить `lessons` тремя новыми колонками, написать `Migration(3,4)`, добавить TypeConverters, обновить Lesson entity/mapper chain. После фазы persistence layer готов для Phase-03 (data repositories).

## Scope

- `shared/core/persistence/`: `AppDatabase` v4 + новые entities + новые DAOs + TypeConverters + `Migration(3,4)` + `PersistenceModule` update
- `shared/feature/lesson/data/`: `LessonEntity` + `LessonMapper` update (3 новых поля), `LessonDtoMapper`/`FirestoreLessonDtoMapper` backward-compat defaults
- Удаление `fallbackToDestructiveMigration` из production build
- Migration тесты MT-01..MT-07

## Role Inputs

- `backend.md` — Yes
- `frontend.md` — No
- `tests.md` — Yes

## Layer

`shared/core/persistence` (data layer modified), `shared/feature/lesson/data` (data layer modified)

## Review Tags

`architecture-review` (migration risk), `concurrency-review` (DAO suspend queries + Flow)

## State Matrix Coverage

Matrix rows: нет прямого gameplay coverage в этой фазе. Закладывает хранилище для Matrix 4 (когда писать attempt) — реализуется в Phase-03.

## Domain Contract Coverage

N/A для gameplay domain contract. Фаза реализует persistence contracts из `08-storage-model.md`.

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 3: AppDatabase migration data loss | `backend-dev` (scaffold ownership) | `PersistenceModule.kt:23` — `fallbackToDestructiveMigration(dropAllTables=true)`; `AppDatabase.kt:7` — version 3 | `room-database.md`: "Provide migration paths"; `@ProvidedTypeConverter` pattern required | Написать `Migration(3,4)` с полным SQL; добавить TypeConverters; удалить `fallbackToDestructiveMigration` из prod; bump version 3→4 | MT-01..MT-07; `./gradlew :shared:core:persistence:connectedAndroidTest` |

## Files

### New Files

- `shared/core/persistence/src/commonMain/kotlin/.../entity/LessonAttemptEntity.kt`
- `shared/core/persistence/src/commonMain/kotlin/.../entity/LessonRatingSubmittedLocalEntity.kt`
- `shared/core/persistence/src/commonMain/kotlin/.../dao/LessonAttemptDao.kt`
- `shared/core/persistence/src/commonMain/kotlin/.../dao/LessonRatingLocalDao.kt`
- `shared/core/persistence/src/androidMain/kotlin/.../converter/TopParticipantListConverter.kt`
- `shared/core/persistence/src/androidMain/kotlin/.../migrations/Migration3to4.kt`

### Modified Files

- `shared/core/persistence/src/commonMain/kotlin/.../AppDatabase.kt` — version 4, new entities, new DAOs, TypeConverters
- `shared/core/persistence/src/androidMain/kotlin/.../di/PersistenceModule.kt` — `addMigrations(MIGRATION_3_4)`, `addTypeConverter(×2)`, remove/replace `fallbackToDestructiveMigration`
- `shared/feature/lesson/data/src/commonMain/kotlin/.../entity/LessonEntity.kt` — 3 new columns
- `shared/feature/lesson/data/src/commonMain/kotlin/.../mapper/LessonMapper.kt` — map 3 new fields
- `shared/feature/lesson/data/src/commonMain/kotlin/.../dto/LessonDto.kt` — 3 optional Firestore fields
- `shared/feature/lesson/data/src/commonMain/kotlin/.../mapper/LessonDtoMapper.kt` — backward-compat defaults
- `shared/feature/lesson/data/src/commonMain/kotlin/.../mapper/FirestoreLessonDtoMapper.kt` — defaults for missing fields

### Deleted Files

- `shared/core/persistence/src/commonMain/kotlin/.../DifficultyConverter.kt` — per ADR-LR-18 (Room KMP rejects unused TypeConverters; mapper handles Difficulty↔Int)

## Dependencies

- Phase-01: нужны `TopParticipant` (для `TopParticipantListConverter`), `Lesson` с новыми полями, `Difficulty @Serializable`
- Phase-02 должна завершиться до Phase-03 (data repositories)

## Criteria for Acceptance

1. AppDatabase version == 4; `./gradlew :shared:core:persistence:connectedAndroidTest` зелёный
2. MT-01: `lesson_attempts` создана с 8 колонками
3. MT-02: `lesson_rating_submitted_local` с compound PK
4. MT-03: `lessons.average_rating=NULL`, `rating_count=0`, `top3='[]'` после migration
5. MT-04: все 7 существующих таблиц сохранены
6. MT-05: ~~`DifficultyConverter` round-trip~~ — **REMOVED (ADR-LR-18)**, converter удалён
7. MT-06: `fallbackToDestructiveMigration` отсутствует в production build config
8. MT-07: `TopParticipantListConverter` round-trip; `fromDb("")` → `emptyList()` без crash
9. `LessonMapper.toDomain()` mappes 3 новых поля
10. `LessonDtoMapper` — backward-compat defaults для старых Firestore documents без `top3`/`averageRating`

## Tests Required

- `MT-01..MT-07` (полный список в `phase-02/tests.md`):
  - `mt01_lessonAttempts_created`: given database v3, when `MIGRATION_3_4.migrate()`, then `lesson_attempts` table exists with 8 columns
  - `mt02_lessonRatingSubmittedLocal_created`: given database v3, when migrate, then `lesson_rating_submitted_local` with compound PK `(user_id, lesson_id)`
  - `mt03_existingLessons_newColumnsDefaultValues`: given v3 lessons row, when migrate to v4, then `average_rating IS NULL`, `rating_count=0`, `top3='[]'`
  - `mt04_allExistingTables_preserved`: given v3 with rows in 7 tables, when migrate, then all 7 rows preserved
  - ~~`mt05_difficultyConverter_roundtrip`~~ — **REMOVED (ADR-LR-18)**
  - `mt06_noFallbackToDestructive`: given production build config, when grep `fallbackToDestructive`, then empty result
  - `mt07_topParticipantListConverter_roundtrip`: given `TopParticipantListConverter`, when `toDb(list)` then `fromDb(result)`, then original list restored; when `fromDb("")`, then `emptyList()` with no crash
- `dao01_lessonAttemptDao_upsertAndObserve`: given in-memory Room v4 and `LessonAttemptEntity`, when `dao.upsert(entity)` then `observeByLesson(userId, lessonId).take(1)`, then list contains entity with matching `attemptId`
- `dao03_lessonRatingLocalDao_upsertAndHasSubmitted`: given `LessonRatingSubmittedLocalEntity`, when `dao.upsert(entity)` then `dao.hasSubmitted(userId, lessonId).take(1).first()`, then `true` (`Flow<Boolean>`)
- `mapper01_lessonMapper_newFields_roundtrip`: given `LessonEntity(averageRating=2.5f, ratingCount=10, top3='[...]')`, when `entity.toDomain()`, then `Lesson.averageRating==2.5f && ratingCount==10 && top3.size==1`
- ~~`difficultyConverter_easy_roundtrip`~~ — **REMOVED (ADR-LR-18)**
- `topParticipantListConverter_emptyString_nocrash`: given `TopParticipantListConverter`, when `fromDb("")`, then `emptyList()` without exception

## Validation

```bash
./gradlew :shared:core:persistence:connectedAndroidTest --no-configuration-cache
./gradlew :shared:feature:lesson:data:jvmTest --no-configuration-cache
./gradlew detekt ktlintCheck --no-configuration-cache
# Production build — no fallbackToDestructiveMigration:
rg "fallbackToDestructive" shared/core/persistence/src/androidMain -g "*.kt"
# Expected: empty or debug-only build config only
```

## Handoff Notes

После phase-02:
- Phase-03 (data) создаёт `LessonAttemptRepositoryImpl` — получает `LessonAttemptDao` через Koin `get()` (из `persistenceModule`)
- Phase-07 (composition root) добавляет `MIGRATION_3_4` в список migration — уже готов в `Migration3to4.kt`

## Pattern Invariants

- `@ProvidedTypeConverter` ОБЯЗАН быть на всех new converters (`TopParticipantListConverter`) — per `room-database.md` и `PersistenceModule.kt:24` (StringSetConverter precedent)
- **REMOVED (ADR-LR-18)**: `DifficultyConverter` удалён — Room KMP 2.7+ отклоняет converter без Entity-binding (`IllegalArgumentException: Unexpected type converter`). Mapper обрабатывает `Difficulty ↔ Int`: `isHard = if (mode == HARD) 1 else 0`. Plan инвариант про `DifficultyConverter` superseded ADR-LR-18.
- `TopParticipantListConverter.fromDb` ОБЯЗАН содержать try/catch → `emptyList()` при malformed JSON (ADR-LR-10 risk mitigation)
- Migration SQL для `ALTER TABLE` ОБЯЗАН использовать `IF NOT EXISTS` эквиваленты где возможно; `ADD COLUMN` — SQLite стандарт без IF NOT EXISTS (OK)
- Новые DAO interface ОБЯЗАНЫ использовать `suspend` functions для single-shot queries и `Flow<T>` для observation — per `room-database.md` conventions

## Options Considered

| Критерий | Option A: настоящая Migration(3,4) (recommended) | Option B: продолжать с fallbackToDestructiveMigration |
|----------|---------------------------------------------------|-------------------------------------------------------|
| Data preservation | ✓ Все существующие данные сохранены | Все данные удаляются при upgrade |
| Сложность | Средняя (SQL + MigrationTestHelper tests) | Минимальная (0 кода) |
| Production-ready | ✓ Да | ✗ Нет (unacceptable post-launch) |
| Test coverage | MT-01..MT-07 | N/A |
| Reversal cost | Средняя (SQLite ALTER TABLE irreversible) | N/A (был дефолт) |
| Sync restore time | 0 (data preserved) | Полный re-sync (~минуты) |

**Recommended: Option A** (resolved в ADR-LR-10)

**Rationale:** `room-database.md`: "don't rely on destructive migration". Каскадный sync восстановит контент, но user-specific data (attempts) lost без migration — неприемлемо для production.

**Rejected Option B:** уничтожает sync-загруженный контент + future user attempts при каждом schema bump. Допустимо только pre-launch с явным документированием (ADR-LR-10 явно запрещает его в production build).
