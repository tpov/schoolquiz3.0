---
phase: 01
role: backend-dev
---

# Phase-01 Backend Tasks

Все задачи — scaffold owner (build files) + Room schema foundation. Test-dev работает параллельно над androidTest файлами.

---

## Pattern Invariants

- Scaffold ownership: `build.gradle.kts`, `libs.versions.toml`, `settings.gradle.kts` — ТОЛЬКО backend-dev, никакой другой роли
- Все Entity ДОЛЖНЫ точно соответствовать `08-storage-model.md` SSoT (полная сигнатура там)
- `upsertByIdIfNewerVersion` — atomic raw SQL с `WHERE NOT EXISTS` (не @Upsert + Kotlin check)
- `@ProvidedTypeConverter` на `StringSetConverter` — обязательно для KMP Room
- Порядок изменений: сначала scaffold (build.gradle.kts, settings.gradle.kts, libs.versions.toml), затем schema, затем mapper обновления

---

## 1. Add `kspJvm` to persistence build.gradle.kts

- **Файл:** `shared/core/persistence/build.gradle.kts`
- **Тип:** build script modification
- **Сигнатура:** `add("kspJvm", libs.room.compiler)` рядом с существующим `add("kspAndroid", libs.room.compiler)`
- **Вход:** существующий `dependencies { add("kspAndroid", libs.room.compiler) }` блок
- **Поведение / Выход:**
  - Добавить строку `add("kspJvm", libs.room.compiler)` в `dependencies` блок
  - Это позволяет Room KSP генерировать код для JVM target (нужен для `./gradlew :shared:core:persistence:compileKotlinJvm` и commonTest Room in-memory tests)
- **Edge cases:**
  - Если `kspJvm` уже существует — не дублировать
  - Проверить что `schoolquiz.kmp.library` convention plugin декларирует `jvm()` target — иначе configuration `kspJvm` не будет найдена
- **Depends on:** `ksp = "2.1.20-1.0.31"` уже в `libs.versions.toml`
- **Canonical reference:** `06-api-contract.md` §12 BD-1; `08-storage-model.md` §kspJvm конфигурация
- **Rationale:** BD-1 blocker — без kspJvm Room не генерирует DAO implementations для JVM target, build падает

---

## 2. Bump Coil 3.1.0 → 3.4.0

- **Файл:** `gradle/libs.versions.toml`
- **Тип:** version catalog modification
- **Сигнатура:** `coil3 = "3.4.0"` (было `"3.1.0"`)
- **Вход:** `coil3 = "3.1.0"` строка 44
- **Поведение / Выход:**
  - Изменить значение: `coil3 = "3.4.0"`
  - Kotlin 2.1.20 уже в `libs.versions.toml` (≥ 2.1.0 требование выполнено — VERIFIED)
  - Не нужен Kotlin bump (risk mitigated)
- **Edge cases:**
  - Coil 3.4.0 убрал `modelEqualityDelegate` — текущий `CatalogGrid.kt:71` использует простой `AsyncImage(model=url)` без этого параметра → breaking change не задевает (VERIFIED in 2-grounding.md Problem 5)
  - `AsyncImagePainter.state` → StateFlow в 3.4.0 — текущий код не обращается к `.state` напрямую
- **Depends on:** `gradle/libs.versions.toml`, Kotlin 2.1.20 уже present
- **Canonical reference:** `06-api-contract.md` §12 BD-2; Problem 5 in `2-grounding.md`
- **Rationale:** Decision #43 — соответствие ADR-HLA-06. Консистентность с spec.

---

## 3. Remove quiz/ empty scaffolds from settings.gradle.kts

- **Файл:** `settings.gradle.kts`
- **Тип:** build script modification
- **Сигнатура:** удалить 3 строки include для quiz модулей
- **Вход:** строки `include(":shared:feature:quiz:domain")`, `include(":shared:feature:quiz:data")`, `include(":android:feature:quiz:presentation")` (verified существуют — нужно найти точные строки)
- **Поведение / Выход:**
  - Удалить все три строки include из settings.gradle.kts
  - Физически удалить директории `shared/feature/quiz/domain/`, `shared/feature/quiz/data/`, `android/feature/quiz/presentation/` (они содержат только `.gitkeep`)
- **Edge cases:**
  - Убедиться что никакой Kotlin файл в проекте не импортирует из этих модулей (grep перед удалением)
- **Depends on:** ничего
- **Canonical reference:** `2-grounding.md` Problem 6 + Problem 8; Decision #44
- **Rationale:** Decision #44 — cleanup пустых placeholder модулей; единственный QuestRepository остаётся в `shared/feature/quest/domain`

---

## 4. Delete quiz placeholder files from catalog/domain

- **Файлы для удаления:**
  - `shared/core/catalog/domain/src/.../model/Quest.kt`
  - `shared/core/catalog/domain/src/.../repository/QuestRepository.kt`
  - `shared/core/catalog/domain/src/.../use_case/CreateQuestUseCase.kt`
  - `shared/core/catalog/domain/src/.../fake/FakeQuestRepository.kt` (commonTest)
  - `shared/core/catalog/domain/src/.../QuestCatalogLinkTest.kt` (commonTest)
- **Тип:** file deletion
- **Сигнатура:** N/A (deletion)
- **Вход:** placeholder файлы с KDoc "TEMPORARY placeholder"
- **Поведение / Выход:**
  - Удалить все 5 файлов
  - В `CatalogDomainModule.kt` убрать комментарий "CreateQuestUseCase removed pending real binding" (если присутствует)
- **Edge cases:**
  - `QuestCatalogLinkTest` должен быть удалён вместе с FakeQuestRepository (тест зависит от placeholder)
  - Проверить что `CatalogDomainModule.kt` не регистрирует `CreateQuestUseCase` как Koin binding
- **Depends on:** task #3 (settings.gradle.kts cleanup)
- **Canonical reference:** `2-grounding.md` Problem 6 Fix Shape
- **Rationale:** Decision #44 — устраняет Problem 8 (два QuestRepository interface) + удаляет dead code

---

## 5. Extend CatalogEntity with 4 new fields

- **Файл:** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/CatalogEntity.kt`
- **Тип:** data class
- **Сигнатура:** `data class CatalogEntity(@PrimaryKey val id: String, val name: String, val picturePath: String?, val pictureUrl: String?, val version: Long = 1L, val contentsVersion: Long = 0L, val lastModifiedAt: Long = 0L, val archived: Boolean = false)`
- **Вход:** 4 новых поля с default values
- **Поведение / Выход:**
  - Добавить аннотацию `@Entity(tableName = "catalogs", indices = [Index(value = ["lastModifiedAt"])])` — индекс по lastModifiedAt для cursor-based delta sync
  - Добавить 4 поля с defaults (существующие тесты компилируются без изменений, defaults покрывают)
  - `init` блок: добавить `require(version >= 1L)`, `require(contentsVersion >= 0L)`, `require(lastModifiedAt >= 0L)`
- **Edge cases:**
  - 7 существующих тестов используют positional args `CatalogEntity(id, name, picturePath, pictureUrl)` — они должны быть обновлены на named args в тестах; либо defaults покроют (positional все ещё valid при 4 params = нет изменений в позиционных call sites, т.к. defaults добавлены после)
  - Kotlin позиционные конструкторы: `CatalogEntity("id", "name", null, null)` — всё ещё компилируется (4 обязательных параметра без defaults, 4 новых с defaults)
- **Depends on:** существующий `CatalogEntity.kt`
- **Canonical reference:** `08-storage-model.md` §CatalogEntity; `06-api-contract.md` §1.1
- **Rationale:** P1 fix — domain model уже расширен (VERIFIED), теперь нужна Room entity. `require(...)` blocks в `init {}` зеркалят domain-level invariants из `Catalog.kt` — `08-storage-model.md` SSoT содержит их явно. Цель: ранний fail при DB corruption (Room READ возвращает 0 в version вместо null при bitflip — `require(version >= 1L)` это поймает). Это не domain concern, а data-layer defensive check per SSoT. Оставить как есть (consistency со `08-storage-model.md §CatalogEntity:108`).

---

## 6. Extend CatalogDao with new methods

- **Файл:** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/CatalogDao.kt`
- **Тип:** interface
- **Сигнатура:** `@Dao interface CatalogDao` с новыми методами `upsertByIdIfNewerVersion(...)`, `deleteById(id)`, обновлённый `observeAll()`
- **Вход:** существующий CatalogDao + новые @Query аннотации
- **Поведение / Выход:**
  - Обновить `observeAll()` добавив `WHERE archived = 0` → `@Query("SELECT * FROM catalogs WHERE archived = 0 ORDER BY id ASC")`
  - Добавить `upsertByIdIfNewerVersion(id, name, picturePath, pictureUrl, version, contentsVersion, lastModifiedAt, archived)` с INSERT OR REPLACE ... WHERE NOT EXISTS pattern (см. `08-storage-model.md` §CatalogDao)
  - Добавить `deleteById(id: String)` — `@Query("DELETE FROM catalogs WHERE id = :id")`
  - Оставить `insertAll`, `deleteAll`, `replaceAll` — legacy, используется тестами
- **Edge cases:**
  - `observeAll()` с `WHERE archived=0` — тесты, которые вставляют archived=false каталоги, должны продолжать работать
  - `upsertByIdIfNewerVersion` — атомарная операция, никаких двух-шаговых check+insert
- **Depends on:** CatalogEntity (task #5)
- **Canonical reference:** `08-storage-model.md` §CatalogDao
- **Rationale:** P1 fix — delta-sync требует upsert-by-version и soft-delete; full-replace устаревает

---

## 7. Create StringSetConverter

- **Файл:** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/StringSetConverter.kt`
- **Тип:** class
- **Сигнатура:** `@ProvidedTypeConverter class StringSetConverter`
- **Вход:** N/A (singleton converter)
- **Поведение / Выход:**
  - `@TypeConverter fun fromSet(value: Set<String>?): String?` — joinToString с разделителем `"\u001F"` (Unit Separator)
  - `@TypeConverter fun toSet(value: String?): Set<String>?` — split + toSet
  - `@ProvidedTypeConverter` — Room не создаёт через reflection в KMP; instance передаётся явно в builder
- **Edge cases:**
  - null input → null output (безопасно)
  - Пустое множество → пустая строка → split даёт [""] → нужно фильтровать: `value?.split("\u001F")?.filter { it.isNotEmpty() }?.toSet()`
  - Shelf names не содержат `\u001F` — VERIFIED (home/arena/tournament/tournamentFinal/archive)
- **Depends on:** androidx.room imports
- **Canonical reference:** `08-storage-model.md` §TypeConverter; `06-api-contract.md` (data layer decision)
- **Rationale:** `Quest.visibleOn: Set<String>` хранится в Room — TypeConverter необходим; @ProvidedTypeConverter — KMP Room requirement

---

## 8. Create QuestEntity + QuestDao

- **Файл (entity):** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestEntity.kt`
- **Тип:** data class
- **Сигнатура:** `data class QuestEntity(@PrimaryKey val id: String, val catalogId: String, val authorUid: String, val title: String, val picturePath: String?, val pictureUrl: String?, val visibleOn: Set<String>, val averageRating: Float?, val averageRatingCount: Int, val version: Long, val contentsVersion: Long, val lastModifiedAt: Long, val archived: Boolean)`
- **Вход:** все поля обязательны (нет defaults)
- **Поведение / Выход:**
  - `@Entity(tableName = "quests", indices = [Index(value=["authorUid"]), Index(value=["catalogId"]), Index(value=["lastModifiedAt"])])`
  - `visibleOn: Set<String>` — хранится через `StringSetConverter`
- **Edge cases:**
  - `averageRating: Float?` — nullable
  - `visibleOn` — может быть пустым множеством (deleted quest marker)
- **Depends on:** StringSetConverter (task #7)
- **Canonical reference:** `08-storage-model.md` §QuestEntity

- **Файл (dao):** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestDao.kt`
- **Тип:** interface
- **Сигнатура:** `@Dao interface QuestDao`
- **Поведение / Выход:**
  - `observeMyQuests(authorUid: String): Flow<List<QuestEntity>>` — WHERE authorUid=:authorUid AND archived=0 ORDER BY lastModifiedAt DESC
  - `observeMyQuestsInCatalog(authorUid: String, catalogId: String): Flow<List<QuestEntity>>` — +catalogId filter
  - `observeByShelf(shelf: String): Flow<List<QuestEntity>>` — LIKE '%shelf%' AND archived=0
  - `findById(id: String): QuestEntity?`
  - `upsertByIdIfNewerVersion(id, catalogId, authorUid, title, picturePath, pictureUrl, visibleOn, averageRating, averageRatingCount, version, contentsVersion, lastModifiedAt, archived)` — INSERT OR REPLACE WHERE NOT EXISTS version >= :version
  - `deleteById(id: String)`
- **Edge cases:**
  - `observeByShelf` LIKE pattern — может дать false-positive при substring overlap; текущие shelf names безопасны (08-storage-model.md REQUIRES note)
- **Canonical reference:** `08-storage-model.md` §QuestDao
- **Rationale:** P2 foundation — data stack фазы-02 требует QuestDao существующего в DB

---

## 9. Create SectionEntity + SectionDao

- **Файл (entity):** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/SectionEntity.kt`
- **Тип:** data class
- **Сигнатура:** `data class SectionEntity(@PrimaryKey val id: String, val questId: String, val title: String, val order: Int, val version: Long, val contentsVersion: Long, val lastModifiedAt: Long, val archived: Boolean)`
- **Вход:** все поля обязательны
- **Поведение / Выход:**
  - `@Entity(tableName = "sections", indices = [Index(value=["questId"]), Index(value=["lastModifiedAt"])])`
- **Depends on:** ничего нового
- **Canonical reference:** `08-storage-model.md` §SectionEntity

- **Файл (dao):** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/SectionDao.kt`
- **Тип:** interface
- **Сигнатура:** `@Dao interface SectionDao`
- **Поведение / Выход:**
  - `observeByQuest(questId: String): Flow<List<SectionEntity>>` — WHERE questId=:questId AND archived=0 ORDER BY `order` ASC
  - `findById(id: String): SectionEntity?`
  - `upsertByIdIfNewerVersion(id, questId, title, order, version, contentsVersion, lastModifiedAt, archived)` — INSERT OR REPLACE WHERE NOT EXISTS
  - `deleteById(id: String)`
- **Canonical reference:** `08-storage-model.md` §SectionDao

---

## 10. Create ThemeEntity + ThemeDao

Идентично SectionEntity/SectionDao с заменой:
- tableName = "themes"
- parentField: `sectionId: String` (вместо `questId`)
- Индексы: `["sectionId"]`, `["lastModifiedAt"]`
- `observeBySection(sectionId: String)` (вместо `observeByQuest`)

- **Файл (entity):** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/ThemeEntity.kt`
- **Canonical reference:** `08-storage-model.md` §ThemeEntity
- **Файл (dao):** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/ThemeDao.kt`
- **Canonical reference:** `08-storage-model.md` §ThemeDao

---

## 11. Create LessonEntity + LessonDao

Идентично SectionEntity/SectionDao с заменой:
- tableName = "lessons"
- parentField: `themeId: String`
- Индексы: `["themeId"]`, `["lastModifiedAt"]`
- `observeByTheme(themeId: String)`

- **Файл (entity):** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonEntity.kt`
- **Canonical reference:** `08-storage-model.md` §LessonEntity
- **Файл (dao):** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonDao.kt`
- **Canonical reference:** `08-storage-model.md` §LessonDao

---

## 12. Create QuestionEntity + QuestionDao

- **Файл (entity):** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestionEntity.kt`
- **Тип:** data class
- **Сигнатура:** `data class QuestionEntity(@PrimaryKey val id: String, val lessonId: String, val text: String, val payload: String, val language: String, val order: Int, val version: Long, val lastModifiedAt: Long, val archived: Boolean)`
- **Вход:** leaf entity — нет `contentsVersion`
- **Поведение / Выход:**
  - `@Entity(tableName = "questions", indices = [Index(value=["lessonId"]), Index(value=["lastModifiedAt"])])`
- **Canonical reference:** `08-storage-model.md` §QuestionEntity

- **Файл (dao):** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestionDao.kt`
- **Тип:** interface
- **Сигнатура:** `@Dao interface QuestionDao`
- **Поведение / Выход:**
  - `observeByLesson(lessonId: String): Flow<List<QuestionEntity>>` — WHERE lessonId=:lessonId AND archived=0 ORDER BY `order` ASC
  - `findById(id: String): QuestionEntity?`
  - `upsertByIdIfNewerVersion(id, lessonId, text, payload, language, order, version, lastModifiedAt, archived)` — INSERT OR REPLACE WHERE NOT EXISTS (NO contentsVersion param — leaf)
  - `deleteById(id: String)`
- **Edge cases:**
  - QuestionEntity не имеет `contentsVersion` — upsert SQL не включает этот столбец
- **Canonical reference:** `08-storage-model.md` §QuestionDao

---

## 13. Update AppDatabase to v2

- **Файл:** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt`
- **Тип:** abstract class
- **Сигнатура:** `@Database(entities=[UserStatsEntity::class, CatalogEntity::class, QuestEntity::class, SectionEntity::class, ThemeEntity::class, LessonEntity::class, QuestionEntity::class], version=2, exportSchema=true) @TypeConverters(StringSetConverter::class) abstract class AppDatabase : RoomDatabase()`
- **Вход:** N/A
- **Поведение / Выход:**
  - `version = 2` (было 1)
  - Добавить 5 новых entity classes в `entities` array
  - Добавить `@TypeConverters(StringSetConverter::class)` аннотацию
  - Добавить 5 новых abstract fun DAO: `questDao()`, `sectionDao()`, `themeDao()`, `lessonDao()`, `questionDao()`
- **Edge cases:**
  - `@TypeConverters` на уровне Database — необходимо для Room KSP code generation
- **Depends on:** tasks #5-12
- **Canonical reference:** `08-storage-model.md` §AppDatabase

---

## 14. Update PersistenceModule (androidMain)

- **Файл:** `shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/di/PersistenceModule.kt`
- **Тип:** Koin module
- **Сигнатура:** `val persistenceModule = module { ... }` — расширенный
- **Вход:** существующий module с AppDatabase + UserStatsDao + CatalogDao
- **Поведение / Выход:**
  - В builder: `.fallbackToDestructiveMigration()` перед `.build()`
  - В builder: `.addTypeConverter(StringSetConverter())` (required при @ProvidedTypeConverter)
  - Добавить singles: `single<QuestDao> { get<AppDatabase>().questDao() }`, аналогично для SectionDao, ThemeDao, LessonDao, QuestionDao
- **Edge cases:**
  - Порядок важен: `addTypeConverter` должен быть до `build()`
  - `fallbackToDestructiveMigration()` — только pre-production; при production release заменить @Migration(1,2)
- **Depends on:** AppDatabase v2 (task #13)
- **Canonical reference:** `08-storage-model.md` §Деструктивная миграция
- **Rationale:** Room builder API требует явной регистрации @ProvidedTypeConverter + destructive migration opt-in

---

## 15. Update CatalogDto + mappers

### CatalogDto
- **Файл:** `shared/core/catalog/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogDto.kt`
- **Тип:** data class
- **Сигнатура:** `data class CatalogDto(val id: String, val name: String, val picturePath: String?, val version: Long, val contentsVersion: Long, val lastModifiedAt: Long, val archived: Boolean)`
- **Вход:** +4 новых обязательных поля
- **Поведение / Выход:** структура для десериализации Firestore документа
- **Edge cases:** нет defaults — все поля обязательны (приходят из Firestore)
- **Canonical reference:** `06-api-contract.md` §8.1

### CatalogDtoMapper
- **Файл:** `shared/core/catalog/data/src/commonMain/.../mapper/CatalogDtoMapper.kt`
- **Тип:** extension functions
- **Сигнатура:** `fun CatalogDto.toEntity(): CatalogEntity`
- **Поведение / Выход:** маппит все 7 полей CatalogDto → CatalogEntity (включая 4 новых)
- **Canonical reference:** internal (no api-contract entry)

### CatalogMapper
- **Файл:** `shared/core/catalog/data/src/commonMain/.../mapper/CatalogMapper.kt`
- **Тип:** extension functions
- **Сигнатура:** `fun CatalogEntity.toDomain(): Catalog`
- **Поведение / Выход:** маппит все 8 полей CatalogEntity → Catalog (включая 4 новых)
- **Canonical reference:** internal (no api-contract entry)

### FirestoreCatalogDtoMapper
- **Файл:** `platform/firebase/src/main/.../catalog/FirestoreCatalogDtoMapper.kt`
- **Тип:** extension functions
- **Сигнатура:** `fun DocumentSnapshot.toCatalogDto(): CatalogDto`
- **Вход:** Firestore DocumentSnapshot
- **Поведение / Выход:**
  - Читать `version: Long` через `getLong("version") ?: 1L`
  - Читать `contentsVersion: Long` через `getLong("contentsVersion") ?: 0L`
  - Читать `lastModifiedAt: Long` через `getTimestamp("lastModifiedAt")?.toDate()?.time ?: 0L`
  - Читать `archived: Boolean` через `getBoolean("archived") ?: false`
- **Edge cases:** server может не иметь полей → safe defaults
- **Canonical reference:** `06-api-contract.md` §10 Firestore Document Schemas

---

## 16. Extend CatalogLocalDataSource interface + impl

- **Файл:** `shared/core/catalog/data/src/commonMain/.../CatalogLocalDataSource.kt`
- **Тип:** interface + class
- **Сигнатура:** `interface CatalogLocalDataSource` с новыми методами; `class CatalogLocalDataSourceImpl(dao: CatalogDao)`
- **Вход:** существующий interface
- **Поведение / Выход:**
  - Добавить `suspend fun upsertByIdIfNewerVersion(entity: CatalogEntity)` — делегирует в DAO
  - Добавить `suspend fun deleteById(id: String)` — делегирует в DAO
  - Оставить `replaceAll`, `findById`, `observeAll` — legacy compatibility
  - `CatalogLocalDataSourceImpl` реализует новые методы через `dao.upsertByIdIfNewerVersion(...)` и `dao.deleteById(id)`
- **Edge cases:**
  - `upsertByIdIfNewerVersion(entity)` — entity содержит все поля; impl распаковывает в DAO call
- **Depends on:** CatalogDao (task #6)
- **Canonical reference:** internal (no api-contract entry — local data source is internal data layer)
- **Rationale:** CatalogRepositoryImpl (phase-02) будет использовать эти методы вместо replaceAll

---

## Validation Checklist

```bash
# Scaffold tasks
./gradlew :shared:core:persistence:build                    # kspJvm + all entities compile
./gradlew :android:core:designsystem:assembleDebug          # Coil 3.4.0 compat
./gradlew assemble                                          # quiz cleanup + full build

# Schema gate (instrumented — requires connected device/emulator)
./gradlew :shared:core:persistence:connectedAndroidTest     # AppDatabaseSchemaValidationTest + QuestDaoBoundaryTest

# JVM tests (existing tests should still be green)
./gradlew allTests
```
