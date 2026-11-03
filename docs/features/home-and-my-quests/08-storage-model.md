---
date: 2026-04-22
author: architect-component
feature: home-and-my-quests
---

# Storage Model: Room Schema — home-and-my-quests

**Canonical source** для всех Room entity/DAO signatures. `06-api-contract.md` ссылается сюда для Room-specific типов.

---

## AppDatabase — версионирование

```kotlin
// shared/core/persistence/src/commonMain/kotlin/.../AppDatabase.kt
@Database(
    entities = [
        UserStatsEntity::class,
        CatalogEntity::class,          // EXTENDED (v1 → v2)
        QuestEntity::class,            // NEW
        SectionEntity::class,          // NEW
        ThemeEntity::class,            // NEW
        LessonEntity::class,           // NEW
        QuestionEntity::class,         // NEW
    ],
    version = 2,                       // bumped from 1
    exportSchema = true,
)
@TypeConverters(StringSetConverter::class)   // NEW — для Quest.visibleOn
abstract class AppDatabase : RoomDatabase() {
    abstract fun userStatsDao(): UserStatsDao
    abstract fun catalogDao(): CatalogDao
    abstract fun questDao(): QuestDao       // NEW
    abstract fun sectionDao(): SectionDao   // NEW
    abstract fun themeDao(): ThemeDao       // NEW
    abstract fun lessonDao(): LessonDao     // NEW
    abstract fun questionDao(): QuestionDao // NEW
}
```

### Деструктивная миграция (Decision #53 + #26)

```kotlin
// shared/core/persistence/src/androidMain/kotlin/.../PersistenceModule.kt
Room.databaseBuilder(context, AppDatabase::class.java, "schoolquiz.db")
    .fallbackToDestructiveMigration()    // ADD — pre-production data loss acceptable
    .build()
```

**Rationale**: pre-production (ни одного live юзера), 5 новых таблиц + расширение CatalogEntity. `@Migration` — излишняя сложность. Зафиксировано в ADR-CMP-53.

---

## CatalogEntity — расширение (v1 → v2)

```kotlin
// shared/core/persistence/src/commonMain/kotlin/.../CatalogEntity.kt
@Entity(
    tableName = "catalogs",
    indices = [Index(value = ["lastModifiedAt"])],  // NEW: for cursor-based delta sync query
)
data class CatalogEntity(
    @PrimaryKey val id: String,
    val name: String,
    val picturePath: String?,
    val pictureUrl: String?,
    // NEW fields — defaults allow existing tests to compile with named args
    val version: Long = 1L,
    val contentsVersion: Long = 0L,
    val lastModifiedAt: Long = 0L,
    val archived: Boolean = false,
) {
    init {
        require(pictureUrl == null || pictureUrl.startsWith("https://")) {
            "pictureUrl must be null or https://"
        }
        if (picturePath != null) {
            require(!picturePath.startsWith("https://") &&
                    !picturePath.startsWith("http://") &&
                    !picturePath.startsWith("gs://")) {
                "picturePath must be a relative Storage path"
            }
        }
        require(version >= 1L) { "version >= 1, got $version" }
        require(contentsVersion >= 0L) { "contentsVersion >= 0" }
        require(lastModifiedAt >= 0L) { "lastModifiedAt >= 0" }
    }
}
```

**Impact на тесты**: 7 тестов с `CatalogEntity(id, name, picturePath, pictureUrl)` позиционными args. Обновляются на named args с дефолтами — минимальный diff.

---

## CatalogDao — новые методы (Decision #52)

```kotlin
// shared/core/persistence/src/commonMain/kotlin/.../CatalogDao.kt
@Dao
interface CatalogDao {

    // UPDATED: добавлен WHERE archived = 0 (Decision #52)
    @Query("SELECT * FROM catalogs WHERE archived = 0 ORDER BY id ASC")
    fun observeAll(): Flow<List<CatalogEntity>>

    @Query("SELECT * FROM catalogs WHERE id = :id")
    suspend fun findById(id: String): CatalogEntity?

    // NEW: compare-and-swap upsert (Business Rule #1)
    @Query("""
        INSERT OR REPLACE INTO catalogs (id, name, picturePath, pictureUrl,
            version, contentsVersion, lastModifiedAt, archived)
        SELECT :id, :name, :picturePath, :pictureUrl,
               :version, :contentsVersion, :lastModifiedAt, :archived
        WHERE NOT EXISTS (
            SELECT 1 FROM catalogs WHERE id = :id AND version >= :version
        )
    """)
    suspend fun upsertByIdIfNewerVersion(
        id: String, name: String, picturePath: String?, pictureUrl: String?,
        version: Long, contentsVersion: Long, lastModifiedAt: Long, archived: Boolean,
    )

    // NEW: soft-delete tombstone handling
    @Query("DELETE FROM catalogs WHERE id = :id")
    suspend fun deleteById(id: String)

    // LEGACY — kept for any existing test wiring; NOT used by new CascadingSyncOrchestrator
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CatalogEntity>)

    @Query("DELETE FROM catalogs")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(entities: List<CatalogEntity>) {
        deleteAll()
        insertAll(entities)
    }
}
```

> **Note**: `upsertByIdIfNewerVersion` использует raw SQL `INSERT OR REPLACE ... WHERE NOT EXISTS` pattern.
> Альтернатива — `@Upsert` (Room 2.5+) + version check в Kotlin — отклонена в ADR-CMP-52 (atomicity в DAO layer).

---

## TypeConverter — StringSetConverter

**Placement: `shared/core/persistence`** (shared по всем feature entities, Decision #CMP-TC).

```kotlin
// shared/core/persistence/src/commonMain/kotlin/.../StringSetConverter.kt
import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter

@ProvidedTypeConverter   // KMP: Room не создаёт через reflection; instance передаётся явно
class StringSetConverter {

    @TypeConverter
    fun fromSet(value: Set<String>?): String? =
        value?.joinToString(separator = "\u001F")   // Unit Separator (0x1F) — безопасен в shelf names

    @TypeConverter
    fun toSet(value: String?): Set<String>? =
        value?.split("\u001F")?.filter { it.isNotEmpty() }?.toSet()
}
```

**KMP Room builder** — instance передаётся явно (не через reflection):

```kotlin
// shared/core/persistence/src/androidMain/kotlin/.../PersistenceModule.kt
Room.databaseBuilder(context, AppDatabase::class.java, "schoolquiz.db")
    .fallbackToDestructiveMigration()
    .addTypeConverter(StringSetConverter())   // REQUIRED with @ProvidedTypeConverter
    .build()
```

**Placement rationale** (ADR-CMP-TC):
- `Set<String>` нужен только для `Quest.visibleOn` в phase-01
- Если поместить в `shared/feature/quest/data` — `AppDatabase` в `shared/core/persistence` не видит converter (разные модули)
- `@TypeConverters(StringSetConverter::class)` на `AppDatabase` должен ссылаться на тот же модуль
- Решение: `StringSetConverter` живёт в `shared/core/persistence` рядом с `AppDatabase`

**Separator choice**: `\u001F` (ASCII Unit Separator) не встречается в shelf names (`"home"`, `"arena"` и т.д.); безопасно.

---

## QuestEntity

```kotlin
// shared/core/persistence/src/commonMain/kotlin/.../QuestEntity.kt
@Entity(
    tableName = "quests",
    indices = [
        Index(value = ["authorUid"]),
        Index(value = ["catalogId"]),
        Index(value = ["lastModifiedAt"]),
    ],
)
data class QuestEntity(
    @PrimaryKey val id: String,
    val catalogId: String,
    val authorUid: String,
    val title: String,
    val picturePath: String?,
    val pictureUrl: String?,
    val visibleOn: Set<String>,              // stored via StringSetConverter
    val averageRating: Float?,
    val averageRatingCount: Int,
    val version: Long,
    val contentsVersion: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
)
```

### QuestDao

```kotlin
// shared/core/persistence/src/commonMain/kotlin/.../QuestDao.kt
@Dao
interface QuestDao {

    // Observe own non-archived quests (all catalogs)
    @Query("""
        SELECT * FROM quests
        WHERE authorUid = :authorUid AND archived = 0
        ORDER BY lastModifiedAt DESC
    """)
    fun observeMyQuests(authorUid: String): Flow<List<QuestEntity>>

    // Observe own non-archived quests filtered by catalog
    @Query("""
        SELECT * FROM quests
        WHERE authorUid = :authorUid AND catalogId = :catalogId AND archived = 0
        ORDER BY lastModifiedAt DESC
    """)
    fun observeMyQuestsInCatalog(authorUid: String, catalogId: String): Flow<List<QuestEntity>>

    // Observe by visibility shelf (for HomeQuests future, Arena, etc.)
    // Note: full-text visibleOn is stored as delimited string; SQL LIKE used for shelf check
    @Query("""
        SELECT * FROM quests
        WHERE (visibleOn LIKE '%' || :shelf || '%') AND archived = 0
        ORDER BY lastModifiedAt DESC
    """)
    fun observeByShelf(shelf: String): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests WHERE id = :id")
    suspend fun findById(id: String): QuestEntity?

    @Query("""
        INSERT OR REPLACE INTO quests
            (id, catalogId, authorUid, title, picturePath, pictureUrl, visibleOn,
             averageRating, averageRatingCount, version, contentsVersion, lastModifiedAt, archived)
        SELECT :id, :catalogId, :authorUid, :title, :picturePath, :pictureUrl, :visibleOn,
               :averageRating, :averageRatingCount, :version, :contentsVersion, :lastModifiedAt, :archived
        WHERE NOT EXISTS (
            SELECT 1 FROM quests WHERE id = :id AND version >= :version
        )
    """)
    suspend fun upsertByIdIfNewerVersion(
        id: String, catalogId: String, authorUid: String, title: String,
        picturePath: String?, pictureUrl: String?, visibleOn: Set<String>,
        averageRating: Float?, averageRatingCount: Int,
        version: Long, contentsVersion: Long, lastModifiedAt: Long, archived: Boolean,
    )

    @Query("DELETE FROM quests WHERE id = :id")
    suspend fun deleteById(id: String)
}
```

> **REQUIRES verify**: `observeByShelf` использует SQL `LIKE '%shelf%'` — может дать false-positive если shelf name является подстрокой другого (например `"home"` vs `"homeQuests"`). Текущие shelf names (`"home"`, `"arena"`, `"tournament"`, `"tournamentFinal"`, `"archive"`) не пересекаются этим образом. При добавлении новых shelf names — валидировать. Альтернатива: хранить как JSON array + Room FTS, но это over-engineering для 5 значений.

---

## SectionEntity

```kotlin
// shared/core/persistence/src/commonMain/kotlin/.../SectionEntity.kt
@Entity(
    tableName = "sections",
    indices = [
        Index(value = ["questId"]),
        Index(value = ["lastModifiedAt"]),
    ],
)
data class SectionEntity(
    @PrimaryKey val id: String,
    val questId: String,
    val title: String,
    val order: Int,
    val version: Long,
    val contentsVersion: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
)
```

### SectionDao

```kotlin
@Dao
interface SectionDao {

    @Query("""
        SELECT * FROM sections
        WHERE questId = :questId AND archived = 0
        ORDER BY `order` ASC
    """)
    fun observeByQuest(questId: String): Flow<List<SectionEntity>>

    @Query("SELECT * FROM sections WHERE id = :id")
    suspend fun findById(id: String): SectionEntity?

    @Query("""
        INSERT OR REPLACE INTO sections (id, questId, title, `order`, version, contentsVersion, lastModifiedAt, archived)
        SELECT :id, :questId, :title, :order, :version, :contentsVersion, :lastModifiedAt, :archived
        WHERE NOT EXISTS (SELECT 1 FROM sections WHERE id = :id AND version >= :version)
    """)
    suspend fun upsertByIdIfNewerVersion(
        id: String, questId: String, title: String, order: Int,
        version: Long, contentsVersion: Long, lastModifiedAt: Long, archived: Boolean,
    )

    @Query("DELETE FROM sections WHERE id = :id")
    suspend fun deleteById(id: String)
}
```

---

## ThemeEntity

```kotlin
@Entity(
    tableName = "themes",
    indices = [Index(value = ["sectionId"]), Index(value = ["lastModifiedAt"])],
)
data class ThemeEntity(
    @PrimaryKey val id: String,
    val sectionId: String,
    val title: String,
    val order: Int,
    val version: Long,
    val contentsVersion: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
)
```

### ThemeDao

```kotlin
@Dao
interface ThemeDao {
    @Query("SELECT * FROM themes WHERE sectionId = :sectionId AND archived = 0 ORDER BY `order` ASC")
    fun observeBySection(sectionId: String): Flow<List<ThemeEntity>>

    @Query("SELECT * FROM themes WHERE id = :id")
    suspend fun findById(id: String): ThemeEntity?

    @Query("""
        INSERT OR REPLACE INTO themes (id, sectionId, title, `order`, version, contentsVersion, lastModifiedAt, archived)
        SELECT :id, :sectionId, :title, :order, :version, :contentsVersion, :lastModifiedAt, :archived
        WHERE NOT EXISTS (SELECT 1 FROM themes WHERE id = :id AND version >= :version)
    """)
    suspend fun upsertByIdIfNewerVersion(
        id: String, sectionId: String, title: String, order: Int,
        version: Long, contentsVersion: Long, lastModifiedAt: Long, archived: Boolean,
    )

    @Query("DELETE FROM themes WHERE id = :id")
    suspend fun deleteById(id: String)
}
```

---

## LessonEntity

```kotlin
@Entity(
    tableName = "lessons",
    indices = [Index(value = ["themeId"]), Index(value = ["lastModifiedAt"])],
)
data class LessonEntity(
    @PrimaryKey val id: String,
    val themeId: String,
    val title: String,
    val order: Int,
    val version: Long,
    val contentsVersion: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
)
```

### LessonDao

```kotlin
@Dao
interface LessonDao {
    @Query("SELECT * FROM lessons WHERE themeId = :themeId AND archived = 0 ORDER BY `order` ASC")
    fun observeByTheme(themeId: String): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE id = :id")
    suspend fun findById(id: String): LessonEntity?

    @Query("""
        INSERT OR REPLACE INTO lessons (id, themeId, title, `order`, version, contentsVersion, lastModifiedAt, archived)
        SELECT :id, :themeId, :title, :order, :version, :contentsVersion, :lastModifiedAt, :archived
        WHERE NOT EXISTS (SELECT 1 FROM lessons WHERE id = :id AND version >= :version)
    """)
    suspend fun upsertByIdIfNewerVersion(
        id: String, themeId: String, title: String, order: Int,
        version: Long, contentsVersion: Long, lastModifiedAt: Long, archived: Boolean,
    )

    @Query("DELETE FROM lessons WHERE id = :id")
    suspend fun deleteById(id: String)
}
```

---

## QuestionEntity (leaf — нет contentsVersion)

```kotlin
@Entity(
    tableName = "questions",
    indices = [Index(value = ["lessonId"]), Index(value = ["lastModifiedAt"])],
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val lessonId: String,
    val text: String,
    val payload: String,
    val language: String,
    val order: Int,
    val version: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
    // NOTE: no contentsVersion — Question is leaf (spec FR#13, Domain Contract)
)
```

### QuestionDao

```kotlin
@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE lessonId = :lessonId AND archived = 0 ORDER BY `order` ASC")
    fun observeByLesson(lessonId: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun findById(id: String): QuestionEntity?

    @Query("""
        INSERT OR REPLACE INTO questions (id, lessonId, text, payload, language, `order`, version, lastModifiedAt, archived)
        SELECT :id, :lessonId, :text, :payload, :language, :order, :version, :lastModifiedAt, :archived
        WHERE NOT EXISTS (SELECT 1 FROM questions WHERE id = :id AND version >= :version)
    """)
    suspend fun upsertByIdIfNewerVersion(
        id: String, lessonId: String, text: String, payload: String,
        language: String, order: Int, version: Long, lastModifiedAt: Long, archived: Boolean,
    )

    @Query("DELETE FROM questions WHERE id = :id")
    suspend fun deleteById(id: String)
}
```

---

## kspJvm конфигурация

**Verification required (REQUIRES: verify before implementation)**:

Текущее состояние (`1-research.md:§2`):
```kotlin
// shared/core/persistence/build.gradle.kts:39
add("kspAndroid", libs.room.compiler)
```

Для KMP Room с JVM target нужен `kspJvm`:
```kotlin
add("kspAndroid", libs.room.compiler)
add("kspJvm", libs.room.compiler)       // ADD — needed for JVM target codegen
```

**Если `kspJvm` уже работает** (`AppDatabase_Impl.kt` генерируется в `build/generated/ksp/jvm/jvmMain/` — подтверждено что артефакт существует) → только `kspAndroid` достаточно для production. `kspJvm` нужен для running Room DAO тестов на JVM (in-memory Room в `commonTest` / `jvmTest`).

**Decision**: `backend-dev` верифицирует и при необходимости добавляет `kspJvm` в `build.gradle.kts`. Это scaffold ownership change.

---

## Schema indexes summary

| Table | Index columns | Type | Reason |
|-------|--------------|------|--------|
| `catalogs` | `lastModifiedAt` | B-tree | Cursor-based delta sync |
| `quests` | `authorUid` | B-tree | `observeMyQuests` WHERE |
| `quests` | `catalogId` | B-tree | Spinner filter |
| `quests` | `lastModifiedAt` | B-tree | Cursor |
| `sections` | `questId` | B-tree | `observeByQuest` + cascade |
| `sections` | `lastModifiedAt` | B-tree | Cursor |
| `themes` | `sectionId` | B-tree | `observeBySection` + cascade |
| `themes` | `lastModifiedAt` | B-tree | Cursor |
| `lessons` | `themeId` | B-tree | `observeByTheme` + cascade |
| `lessons` | `lastModifiedAt` | B-tree | Cursor |
| `questions` | `lessonId` | B-tree | `observeByLesson` + cascade |
| `questions` | `lastModifiedAt` | B-tree | Cursor |

**Note**: Room добавляет B-tree index автоматически через `@Entity(indices = [...])`. Composite indexes (например `(authorUid, catalogId, lastModifiedAt)`) — не в Room, они только в **Firestore Console** для server-side queries (см. `06-api-contract.md`).

---

## Деструктивная миграция — rationale (Decision #26, #53)

Spec FR#17 и NFR#6 явно выбирают pre-production destructive migration:

1. **Pre-production**: нет live пользователей → data loss = acceptable
2. **5 новых таблиц + 4 новых колонки в CatalogEntity**: написание корректных `@Migration` скриптов для этого объёма — риск ошибки
3. **Idempotent sync**: после `fallbackToDestructiveMigration()` первый `SyncWorker` re-fetches всё с cursor=0 → данные восстанавливаются
4. **Будущий path**: когда фича выйдет в prod — `@Migration(1, 2)` должен быть написан и протестирован. `AppDatabaseMigrationTest` (instrumented) фиксирует это как regression gate.

---

## Open Questions

- **OQ-1**: `upsertByIdIfNewerVersion` raw SQL pattern — Room 2.5+ предоставляет `@Upsert`. Но `@Upsert` не имеет встроенной version-guard логики. Если team хочет `@Upsert` + Kotlin-level check — это два SQL calls (SELECT + INSERT/UPDATE), не atomic. Raw SQL с subquery — atomic. Финальный выбор — backend-dev при реализации.
- **OQ-2**: `observeByShelf` LIKE pattern для visibleOn — достаточно для MVP. При расширении shelf names необходима валидация.
