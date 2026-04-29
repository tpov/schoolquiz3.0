---
phase: phase-02
role: backend-dev
---

# Phase 02 — Backend Tasks

## Pattern Invariants

- `@ProvidedTypeConverter` на всех new converters — НЕ использовать `@TypeConverters` на entity (per Room KMP pattern, `PersistenceModule.kt:24` precedent)
- DAO suspend functions для DML; `Flow<List<T>>` для observation queries
- `LessonAttemptDao` и `LessonRatingLocalDao` — абстрактные функции в `AppDatabase` (abstract fun lessonAttemptDao(): LessonAttemptDao)
- Migration SQL из `08-storage-model.md` — использовать дословно; не упрощать
- `fallbackToDestructiveMigration` ДОЛЖЕН быть удалён из production path (возможен в debug build config с явной пометкой // DEBUG ONLY)

---

## New Entity: `LessonAttemptEntity`

- **Файл:** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/entity/LessonAttemptEntity.kt`
- **Тип:** data class
- **Сигнатура:** `@Entity(tableName = "lesson_attempts") data class LessonAttemptEntity(...)`
- **Вход:** все поля из `08-storage-model.md §New Table: lesson_attempts`
- **Поведение / Выход:**
  - `@PrimaryKey val attemptId: String` (= `AttemptId.value`)
  - `val userId: String`, `val lessonId: String`, `val lessonVersion: Long`
  - `val isHard: Int` (Boolean stored as Int 0/1; per Room convention)
  - `val codeAnswer: String`, `val percentScore: Int`, `val completedAt: Long`
  - `@ColumnInfo` имена соответствуют SQL columns в `08-storage-model.md`: `attempt_id`, `user_id`, `lesson_id`, `lesson_version`, `is_hard`, `code_answer`, `percent_score`, `completed_at`
  - Indexes определены в `@Entity(indices = [Index("user_id"), Index("lesson_id")])`
- **Edge cases:**
  - `isHard` как `Int` (0/1), НЕ `Boolean` — Room on Android/JVM stores Boolean нативно, но explicit Int — safer для migration validation
  - `codeAnswer` может быть длинной строкой (до `eligibleQuestions.size` chars)
- **Depends on:** Room annotations (existing persistence dep)
- **Canonical reference:** `06-api-contract.md:635` (§LR-16 entity schema)
- **Rationale:** Storage для `Attempt` domain objects; индексы per `08-storage-model.md` для sync query performance

---

## New Entity: `LessonRatingSubmittedLocalEntity`

- **Файл:** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/entity/LessonRatingSubmittedLocalEntity.kt`
- **Тип:** data class
- **Сигнатура:** `@Entity(tableName = "lesson_rating_submitted_local", primaryKeys = ["user_id", "lesson_id"]) data class LessonRatingSubmittedLocalEntity(...)`
- **Вход:** `userId: String`, `lessonId: String`, `submittedAt: Long`
- **Поведение / Выход:**
  - Compound PK: `primaryKeys = ["user_id", "lesson_id"]`
  - `val userId: String`, `val lessonId: String`, `val submittedAt: Long` (Unix millis)
  - Local deduplication flag — нет rating value (только факт отправки)
  - `@ColumnInfo(name = "user_id")` и т.д. per SQL schema
- **Edge cases:**
  - Compound PK = Room не может иметь `@PrimaryKey` annotation на поле, используется `@Entity(primaryKeys = [...])` array
  - `INSERT OR REPLACE` при дублировании не нужен (нет update semantics)
- **Depends on:** Room annotations
- **Canonical reference:** `06-api-contract.md:693` (§LR-17 rating entity LessonRatingSubmittedLocalEntity)
- **Rationale:** Local dedup check — "один раз оценил урок" per (userId, lessonId); не хранит сам rating value

---

## New DAO: `LessonAttemptDao`

- **Файл:** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/dao/LessonAttemptDao.kt`
- **Тип:** interface
- **Сигнатура:** `@Dao interface LessonAttemptDao`
- **Вход:** N/A — DAO interface
- **Поведение / Выход:**
  - `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: LessonAttemptEntity): Long` — canonical per `06-api-contract.md:645`; REPLACE strategy обеспечивает idempotent write при retry
  - `@Query("SELECT * FROM lesson_attempts WHERE user_id = :userId AND lesson_id = :lessonId") fun observeByLesson(userId: String, lessonId: String): Flow<List<LessonAttemptEntity>>`
  - `@Query("SELECT * FROM lesson_attempts WHERE user_id = :userId") fun observeAllByUser(userId: String): Flow<List<LessonAttemptEntity>>`
  - `observeByLesson` и `observeAllByUser` — `Flow` (observation), `upsert` — `suspend` returning `Long` (row id)
- **Edge cases:**
  - `observeByLesson` может вернуть empty list (no attempts yet for this lesson)
  - `upsert` с `OnConflictStrategy.REPLACE` — при повторной попытке сохранить тот же attemptId (retry) строка заменяется без ошибки
  - Возвращаемый `Long` — вставленный row id; результат проверяется в `LessonAttemptRepositoryImpl.save()`
- **Depends on:** `LessonAttemptEntity`
- **Canonical reference:** `06-api-contract.md:645`
- **Rationale:** Canonical signatures per SSoT `06-api-contract.md:635` (§LR-16); REPLACE strategy = idempotent retry safe; observing Flow позволяет `DefaultLessonListComponent` reactive update bestStars

---

## New DAO: `LessonRatingLocalDao`

- **Файл:** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/dao/LessonRatingLocalDao.kt`
- **Тип:** interface
- **Сигнатура:** `@Dao interface LessonRatingLocalDao`
- **Вход:** N/A
- **Поведение / Выход:**
  - `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: LessonRatingSubmittedLocalEntity): Long` — canonical per `06-api-contract.md:663`; REPLACE strategy per SSoT
  - `@Query("SELECT COUNT(*) > 0 FROM lesson_rating_submitted_local WHERE user_id = :userId AND lesson_id = :lessonId") fun hasSubmitted(userId: String, lessonId: String): Flow<Boolean>` — returns observable `Flow<Boolean>`, NOT `suspend fun ... : Int`; canonical per `06-api-contract.md:666`
  - `hasSubmitted` — `Flow<Boolean>` (reactive), `upsert` — `suspend` returning `Long`
- **Edge cases:**
  - `OnConflictStrategy.REPLACE` — при повторной попытке (retry после network error) старая запись заменяется; idempotent
  - `hasSubmitted` как `Flow<Boolean>` — repository собирает через `.first()` для одноразовой проверки, или `.stateIn()` если нужна reactivity
  - `COUNT(*) > 0` — SQLite вернёт 1 (true) или 0 (false); Room автоматически конвертирует в `Boolean`
- **Depends on:** `LessonRatingSubmittedLocalEntity`
- **Canonical reference:** `06-api-contract.md:663`
- **Rationale:** Canonical DAO signatures per SSoT; `Flow<Boolean>` вместо `suspend Int` — позволяет реактивно показывать рейтинг-промпт без дополнительных запросов; уточнённый контракт из `06-api-contract.md:635` (§LR-16)

---

## New Converter: `DifficultyConverter`

- **Файл:** `shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/converter/DifficultyConverter.kt`
- **Тип:** class
- **Сигнатура:** `@ProvidedTypeConverter class DifficultyConverter`
- **Вход:** `Difficulty` ↔ `String`
- **Поведение / Выход:**
  - `@TypeConverter fun toDb(difficulty: Difficulty): String = difficulty.name`
  - `@TypeConverter fun fromDb(value: String): Difficulty = Difficulty.valueOf(value)`
  - `EASY` → `"EASY"` → `EASY` round-trip
  - `@ProvidedTypeConverter` — добавляется через `.addTypeConverter(DifficultyConverter())` в Room builder
- **Edge cases:**
  - `fromDb("")` → `IllegalArgumentException` от `Difficulty.valueOf` — рассмотреть `Difficulty.valueOf(value.ifBlank { "EASY" })` как defensive default
  - `fromDb` для unknwon value — defensive: log + return EASY default
- **Depends on:** `Difficulty` (from `shared/core/question-schema`)
- **Canonical reference:** `08-storage-model.md`, `ADR-LR-10`
- **Rationale:** `isHard: Int` в entity достаточен для boolean storage, но если Difficulty хранится как enum text — нужен converter; проверить LessonAttemptEntity подход (isHard = Int); если Difficulty не хранится в entity как enum — converter используется только для LessonEntity или future use

---

## New Converter: `TopParticipantListConverter`

- **Файл:** `shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/converter/TopParticipantListConverter.kt`
- **Тип:** class
- **Сигнатура:** `@ProvidedTypeConverter class TopParticipantListConverter`
- **Вход:** `List<TopParticipant>` ↔ `String` (JSON)
- **Поведение / Выход:**
  - `@TypeConverter fun toDb(list: List<TopParticipant>): String = Json.encodeToString(list)`
  - `@TypeConverter fun fromDb(json: String): List<TopParticipant>` — try/catch `SerializationException` → `emptyList()`
  - `fromDb("")` или `fromDb("null")` → `emptyList()` (no crash per ADR-LR-10 risk mitigation)
  - Использует `kotlinx.serialization` Json (уже в classpath через question-schema dep transitive или прямо)
- **Edge cases:**
  - `json == "[]"` → `emptyList()` (happy path для DEFAULT в migration)
  - Malformed JSON → catch → `emptyList()` + log warning (не crash)
  - `@Serializable` на `TopParticipant` — уже есть из Phase-01
- **Depends on:** `TopParticipant` (`shared/core/leaderboard`), `kotlinx-serialization-json`
- **Canonical reference:** `08-storage-model.md`, `ADR-LR-10`
- **Rationale:** `top3` хранится как JSON text в `lessons` table; TypeConverter прозрачен для DAO

---

## New Migration: `Migration3to4`

- **Файл:** `shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/migrations/Migration3to4.kt`
- **Тип:** object
- **Сигнатура:** `val MIGRATION_3_4 = object : Migration(3, 4) { override fun migrate(db: SupportSQLiteDatabase) { ... } }`
- **Вход:** `SupportSQLiteDatabase` — current database
- **Поведение / Выход:**
  - SQL точно из `08-storage-model.md §Migration(3,4)`:
    - `CREATE TABLE IF NOT EXISTS lesson_attempts (...)`
    - `CREATE INDEX idx_lesson_attempts_user_id ON lesson_attempts (user_id)`
    - `CREATE INDEX idx_lesson_attempts_lesson_id ON lesson_attempts (lesson_id)`
    - `CREATE TABLE IF NOT EXISTS lesson_rating_submitted_local (...)`
    - `ALTER TABLE lessons ADD COLUMN average_rating REAL`
    - `ALTER TABLE lessons ADD COLUMN rating_count INTEGER NOT NULL DEFAULT 0`
    - `ALTER TABLE lessons ADD COLUMN top3 TEXT NOT NULL DEFAULT '[]'`
  - Не добавлять ничего сверх этого SQL
- **Edge cases:**
  - `ALTER TABLE ADD COLUMN` необратим в SQLite (нет IF NOT EXISTS для column)
  - Если schema уже содержит колонку (double migration bug) → SQL error → test поймёт до release
  - DEFAULT `'[]'` для `top3` — кавычки обязательны в SQL
- **Depends on:** `androidx.room:room-runtime` (existing)
- **Canonical reference:** `08-storage-model.md §Migration(3,4)`
- **Rationale:** Производственная migration; `08-storage-model.md` содержит canonical SQL — использовать дословно

---

## Modify `AppDatabase`

- **Файл:** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt`
- **Тип:** abstract class (existing — modify)
- **Сигнатура:** `@Database(entities = [...existing..., LessonAttemptEntity::class, LessonRatingSubmittedLocalEntity::class], version = 4, exportSchema = true) @TypeConverters(DifficultyConverter::class, TopParticipantListConverter::class) abstract class AppDatabase : RoomDatabase()`
- **Вход:** N/A — annotation + new abstract functions
- **Поведение / Выход:**
  - `version = 4` (было 3)
  - Добавить 2 новых entity в entities list
  - `@TypeConverters(DifficultyConverter::class, TopParticipantListConverter::class)` — per Room KMP pattern
  - `abstract fun lessonAttemptDao(): LessonAttemptDao`
  - `abstract fun lessonRatingLocalDao(): LessonRatingLocalDao`
  - `exportSchema = true` — оставить (для Room schema history)
- **Edge cases:**
  - `@TypeConverters` с `@ProvidedTypeConverter` классами — они не instantiate Room автоматически; `.addTypeConverter()` в builder ОБЯЗАТЕЛЕН
  - Room schema export: убедиться что schema export directory настроен в build.gradle.kts
- **Depends on:** все новые entity + DAO классы (созданные выше)
- **Canonical reference:** `08-storage-model.md §AppDatabase Changes`
- **Rationale:** Центральная точка регистрации всех DB entities и DAOs

---

## Modify `PersistenceModule`

- **Файл:** `shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/di/PersistenceModule.kt`
- **Тип:** val (Koin module, existing — modify)
- **Сигнатура:** existing module, modify Room builder call
- **Вход:** N/A — modify builder
- **Поведение / Выход:**
  - Добавить `.addMigrations(MIGRATION_3_4)` в Room builder
  - Добавить `.addTypeConverter(DifficultyConverter())`
  - Добавить `.addTypeConverter(TopParticipantListConverter())`
  - Удалить или заменить `.fallbackToDestructiveMigration(dropAllTables = true)` на `// DEBUG ONLY` или условный debug check
  - Expose `single { get<AppDatabase>().lessonAttemptDao() }` и `single { get<AppDatabase>().lessonRatingLocalDao() }` — для Phase-03 `LessonAttemptRepositoryImpl`
- **Edge cases:**
  - Порядок: `.addMigrations(...)` до `.build()` call
  - `fallbackToDestructiveMigration` — если убрать полностью, при missing migration Room throws `IllegalStateException` → лучше как safety. ADR-LR-10: prod build без него, debug может оставить
- **Depends on:** `MIGRATION_3_4`, `DifficultyConverter`, `TopParticipantListConverter`, `LessonAttemptDao`, `LessonRatingLocalDao`
- **Canonical reference:** `ADR-LR-10`; pattern: `PersistenceModule.kt:24` (addTypeConverter precedent)
- **Rationale:** Composition root для persistence layer; все converters + migrations регистрируются здесь

---

## Modify `LessonEntity`

- **Файл:** `shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/entity/LessonEntity.kt`
- **Тип:** data class (existing — modify)
- **Сигнатура:** add 3 new columns per `08-storage-model.md §Existing Table: lessons — ALTER`
- **Вход:** N/A — field additions
- **Поведение / Выход:**
  - `@ColumnInfo(name = "average_rating") val averageRating: Float? = null`
  - `@ColumnInfo(name = "rating_count") val ratingCount: Int = 0`
  - `@ColumnInfo(name = "top3") val top3: String = "[]"` — хранится как JSON text (TypeConverter используется AppDatabase-уровне)
  - Defaults обеспечивают backward-compat для existing rows после `ALTER TABLE` migration
- **Edge cases:**
  - `top3` хранится как `String` в entity (TypeConverter на AppDatabase уровне для `List<TopParticipant>`); mapper конвертирует между `String` и `List<TopParticipant>`
  - Nullability `averageRating: Float?` = Room хранит как `REAL` nullable
- **Depends on:** Phase-01 `Lesson.kt` changes (canonical field names)
- **Canonical reference:** `06-api-contract.md:129` (§LR-5), `08-storage-model.md §Existing Table`
- **Rationale:** Entity must mirror migration SQL; defaults protect existing rows

---

## Modify `LessonMapper`

- **Файл:** `shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonMapper.kt`
- **Тип:** extension function (existing — modify)
- **Сигнатура:** `fun LessonEntity.toDomain(): Lesson` (existing — add 3 field mappings)
- **Вход:** `LessonEntity` с новыми полями
- **Поведение / Выход:**
  - `averageRating = entity.averageRating` (nullable passthrough)
  - `ratingCount = entity.ratingCount` (Int passthrough)
  - `top3 = parseTopParticipants(entity.top3)` — `Json.decodeFromString<List<TopParticipant>>(entity.top3)` с try/catch → emptyList()
  - Existing 8 fields — без изменений
- **Edge cases:**
  - `entity.top3 == "[]"` → `emptyList()` (normal case)
  - Malformed JSON in `top3` — catch → `emptyList()` (defensive, mirror TypeConverter)
- **Depends on:** `TopParticipant` (`shared/core/leaderboard`), `kotlinx-serialization-json`
- **Canonical reference:** `06-api-contract.md:129` (§LR-5)
- **Rationale:** Mapper chain: Entity ↔ Domain; 3 новых поля mapped 1-to-1

---

## Modify `LessonDtoMapper` and `FirestoreLessonDtoMapper`

- **Файл:** `shared/feature/lesson/data/src/commonMain/kotlin/.../mapper/LessonDtoMapper.kt` (и FirestoreLessonDtoMapper.kt)
- **Тип:** extension function (existing — modify)
- **Сигнатура:** `fun LessonDto.toEntity(): LessonEntity` — add 3 new fields with defaults
- **Вход:** `LessonDto` — Firestore document representation
- **Поведение / Выход:**
  - `averageRating = dto.averageRating` (nullable, missing field = null per JSON)
  - `ratingCount = dto.ratingCount ?: 0` (backward-compat: old Firestore docs sans field → 0)
  - `top3 = dto.top3?.let { Json.encodeToString(it) } ?: "[]"` — encode to JSON string for entity
  - `LessonDto`: добавить `val averageRating: Float? = null`, `val ratingCount: Int? = null`, `val top3: List<TopParticipantDto>? = null` (Firestore backward-compat: missing = null)
- **Edge cases:**
  - Firestore docs без `top3` поля → `null` в DTO → `"[]"` в entity (backward-compat для existing Lesson documents)
  - `FirestoreLessonDtoMapper` — если существует отдельно от `LessonDtoMapper`, обновить оба
- **Depends on:** `LessonDto`, `TopParticipant`, `LessonEntity`
- **Canonical reference:** `06-api-contract.md:129` (§LR-5)
- **Rationale:** Firestore documents без новых полей должны gracefully map к defaults
