---
date: 2026-04-26
authors: architect-component
feature: lesson-runner
---

# 08 Storage Model: Lesson Runner

## Overview

Room schema delta v3 → v4. Добавляются 2 новые таблицы + 3 колонки в `lessons`. TypeConverters для `Difficulty` и `List<TopParticipant>`. Migration(3,4) — полный SQL (ADR-LR-10: no fallbackToDestructive in prod).

---

## New Table: lesson_attempts

```sql
CREATE TABLE IF NOT EXISTS lesson_attempts (
    attempt_id      TEXT    NOT NULL PRIMARY KEY,
    user_id         TEXT    NOT NULL,
    lesson_id       TEXT    NOT NULL,
    lesson_version  INTEGER NOT NULL,
    is_hard         INTEGER NOT NULL DEFAULT 0,
    code_answer     TEXT    NOT NULL,
    percent_score   INTEGER NOT NULL,
    completed_at    INTEGER NOT NULL
);
CREATE INDEX idx_lesson_attempts_user_id ON lesson_attempts (user_id);
CREATE INDEX idx_lesson_attempts_lesson_id ON lesson_attempts (lesson_id);
```

| Entity field | SQL column | Notes |
|-------------|-----------|-------|
| `attemptId` | `attempt_id` PK | UUID string (AttemptId.value, ADR-LR-12) |
| `userId` | `user_id` | Firebase Auth UID |
| `lessonId` | `lesson_id` | LessonId.value |
| `lessonVersion` | `lesson_version` | snapshot at attempt start |
| `isHard` | `is_hard` | Boolean (0/1) |
| `codeAnswer` | `code_answer` | raw string '0'..'9' per eligible position |
| `percentScore` | `percent_score` | 0..100 |
| `completedAt` | `completed_at` | Unix millis |

---

## New Table: lesson_rating_submitted_local

```sql
CREATE TABLE IF NOT EXISTS lesson_rating_submitted_local (
    user_id         TEXT    NOT NULL,
    lesson_id       TEXT    NOT NULL,
    submitted_at    INTEGER NOT NULL,
    PRIMARY KEY (user_id, lesson_id)
);
```

Local deduplication flag — predcent check без network. Cloud truth в Firestore `lesson_ratings/{sha256(userId:lessonId)}`.

---

## Existing Table: lessons — ALTER

```sql
ALTER TABLE lessons ADD COLUMN average_rating REAL;
ALTER TABLE lessons ADD COLUMN rating_count   INTEGER NOT NULL DEFAULT 0;
ALTER TABLE lessons ADD COLUMN top3           TEXT    NOT NULL DEFAULT '[]';
```

| Lesson field | SQL column | Default | Notes |
|-------------|-----------|---------|-------|
| `averageRating: Float?` | `average_rating` | NULL | null до первой оценки |
| `ratingCount: Int` | `rating_count` | 0 | NON-NULL, DEFAULT 0 (ADR-LR-15) |
| `top3: List<TopParticipant>` | `top3` | '[]' | JSON via TypeConverter |

---

## Migration(3,4) — полный SQL

**File**: `shared/core/persistence/src/androidMain/kotlin/.../migrations/Migration3to4.kt`

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS lesson_attempts (
                attempt_id      TEXT    NOT NULL PRIMARY KEY,
                user_id         TEXT    NOT NULL,
                lesson_id       TEXT    NOT NULL,
                lesson_version  INTEGER NOT NULL,
                is_hard         INTEGER NOT NULL DEFAULT 0,
                code_answer     TEXT    NOT NULL,
                percent_score   INTEGER NOT NULL,
                completed_at    INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_lesson_attempts_user_id ON lesson_attempts (user_id)")
        db.execSQL("CREATE INDEX idx_lesson_attempts_lesson_id ON lesson_attempts (lesson_id)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS lesson_rating_submitted_local (
                user_id         TEXT    NOT NULL,
                lesson_id       TEXT    NOT NULL,
                submitted_at    INTEGER NOT NULL,
                PRIMARY KEY (user_id, lesson_id)
            )
        """.trimIndent())

        db.execSQL("ALTER TABLE lessons ADD COLUMN average_rating REAL")
        db.execSQL("ALTER TABLE lessons ADD COLUMN rating_count INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE lessons ADD COLUMN top3 TEXT NOT NULL DEFAULT '[]'")
    }
}
```

---

## AppDatabase Changes

**File**: `shared/core/persistence/src/commonMain/kotlin/.../AppDatabase.kt`

```kotlin
@Database(
    entities = [
        // ... existing 7 entities ...
        LessonAttemptEntity::class,
        LessonRatingSubmittedLocalEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(DifficultyConverter::class, TopParticipantListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    // ... existing abstract DAOs ...
    abstract fun lessonAttemptDao(): LessonAttemptDao
    abstract fun lessonRatingLocalDao(): LessonRatingLocalDao
}
```

**PersistenceModule.kt**: `addMigrations(MIGRATION_3_4)` + `addTypeConverter(DifficultyConverter())` + `addTypeConverter(TopParticipantListConverter())`.

**ADR-LR-10**: удалить или заменить `fallbackToDestructiveMigration(dropAllTables=true)` в prod build. Debug build может оставить как последний fallback.

---

## Impact Scan (per room-database.md checklist)

| Шаг | Статус |
|-----|--------|
| 1. Migration(3,4) SQL | ✅ выше |
| 2. Новые entity классы | ✅ §LR-17 в 06-api-contract.md |
| 3. LessonEntity + mapper обновлены | ✅ §LR-5 в 06-api-contract.md |
| 4. DAO queries на `lessons` проверены | ✅ 4 queries в `LessonDao.kt` — все safe (C7 fix) |
| 5. Migration тесты MT-01..MT-07 | per 04-testing.md |

### LessonDao queries verified safe

Команда: `rg -n "FROM lessons" shared/core/persistence -g "*.kt"` (выполнена во время design phase):

| Query | Файл:строка | Безопасность после ADD COLUMN |
|-------|-------------|-------------------------------|
| `SELECT * FROM lessons WHERE themeId = :themeId AND archived = 0` | `LessonDao.kt:13` | ✅ `SELECT *` включает новые колонки автоматически |
| `SELECT * FROM lessons WHERE id = :id` | `LessonDao.kt:16` | ✅ аналогично |
| `DELETE FROM lessons WHERE id = :id` | `LessonDao.kt:30` | ✅ DELETE не зависит от схемы колонок |
| `SELECT contentsVersion FROM lessons WHERE id = :id` | `LessonDao.kt:33` | ✅ explicit column select, не затрагивается |

Все 4 запроса безопасны: `ALTER TABLE ADD COLUMN` не ломает существующие queries. Новые колонки с DEFAULT вернут правильные значения (`NULL`, `0`, `'[]'`) для существующих строк.

---

## Migration Test Template

```kotlin
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration3to4Test {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate3to4_lessonAttemptsCreated() {
        helper.createDatabase(TEST_DB, 3).close()
        helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4).use { db ->
            val c = db.query("SELECT * FROM lesson_attempts")
            assertThat(c.columnNames).containsAtLeast(
                "attempt_id", "user_id", "lesson_id", "lesson_version",
                "is_hard", "code_answer", "percent_score", "completed_at",
            )
        }
    }

    @Test
    fun migrate3to4_existingLessonsPreserved() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL("INSERT INTO lessons (id, theme_id, title, \"order\", version, contents_version, last_modified_at, archived) VALUES ('L1','T1','Test',1,1,1,0,0)")
        }
        helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4).use { db ->
            val c = db.query("SELECT id, average_rating, rating_count, top3 FROM lessons WHERE id='L1'")
            c.moveToFirst()
            assertThat(c.getString(0)).isEqualTo("L1")
            assertThat(c.isNull(1)).isTrue()   // average_rating = NULL
            assertThat(c.getInt(2)).isEqualTo(0) // rating_count = 0
            assertThat(c.getString(3)).isEqualTo("[]") // top3 = '[]'
        }
    }
}
```
