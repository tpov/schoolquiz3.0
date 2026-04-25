---
phase: 01
role: test-dev
---

# Phase-01 Test Tasks

Тесты пишутся ПАРАЛЛЕЛЬНО с production code (TDD). Как только backend-dev создаёт entity/DAO — test-dev пишет androidTest.

**Rule:** test-dev НЕ изменяет production code. Если нужен scaffold change (build.gradle.kts) — эскалировать через backend-dev.

---

## Pattern Invariants

- Все androidTest — `@RunWith(AndroidJUnit4::class)` + `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries()`
- Каждый DAO тест включает: tearDown `@After db.close()`
- DAO тесты проверяют version-guard (equal version → skip, higher version → update)
- `StringSetConverter` roundtrip тест: Set → String → Set должен вернуть исходный Set
- Тесты не удаляют существующие файлы — только добавляют новые

---

## 1. AppDatabaseSchemaValidationTest

- **Файл:** `shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabaseSchemaValidationTest.kt`
- **Тип:** instrumented test
- **Фреймворк:** AndroidJUnit4 + MigrationTestHelper

**Сценарии:**

```
schema_v2_has_all_7_tables:
  GIVEN: создан fresh DB version=2 через MigrationTestHelper.createDatabase(name, 2)
  WHEN: execSQL("SELECT * FROM quests LIMIT 1"), sections, themes, lessons, questions
  THEN: нет SqliteException (все таблицы существуют)

catalog_v2_has_new_columns:
  GIVEN: fresh DB v2
  WHEN: execSQL("SELECT version, contentsVersion, lastModifiedAt, archived FROM catalogs LIMIT 1")
  THEN: нет ошибки (столбцы существуют)

destructive_recreate_when_version_bumped:
  GIVEN: createDatabase(name, 1) создан и закрыт
  WHEN: runMigrationsAndValidate(name, 2, true) с fallbackToDestructiveMigration
  THEN: Room открывает без исключений (destructive migration применена)
```

**Depends on:** AppDatabase v2 (backend-dev task #13)
**Covers:** AC#12

---

## 2. QuestDaoBoundaryTest

- **Файл:** `shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestDaoBoundaryTest.kt`
- **Тип:** instrumented test
- **Фреймворк:** AndroidJUnit4 + Room in-memory

**Setup:** `Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().addTypeConverter(StringSetConverter()).build()`

**Сценарии:**

```
observeMyQuests_returns_only_matching_authorUid:
  GIVEN: inserted quest(authorUid="uid-a", archived=false), quest(authorUid="uid-b", archived=false)
  WHEN: dao.observeMyQuests("uid-a").first()
  THEN: result.size == 1 AND result[0].authorUid == "uid-a"

observeMyQuests_excludes_archived:
  GIVEN: quest(authorUid="uid-a", archived=false), quest(authorUid="uid-a", archived=true)
  WHEN: dao.observeMyQuests("uid-a").first()
  THEN: result.size == 1 AND result[0].archived == false
  [COVERS AC#14]

observeMyQuestsInCatalog_filters_by_catalogId:
  GIVEN: quest(authorUid="uid-a", catalogId="cat1"), quest(authorUid="uid-a", catalogId="cat2")
  WHEN: dao.observeMyQuestsInCatalog("uid-a", "cat1").first()
  THEN: result.size == 1 AND result[0].catalogId == "cat1"
  [COVERS AC#15]

upsertByIdIfNewerVersion_skips_on_equal_version:
  GIVEN: upsert quest(id="q1", version=5, title="original")
  WHEN: upsert quest(id="q1", version=5, title="updated")
  THEN: findById("q1")?.title == "original"
  [COVERS AC#13]

upsertByIdIfNewerVersion_skips_on_older_version:
  GIVEN: upsert quest(id="q1", version=5, title="original")
  WHEN: upsert quest(id="q1", version=3, title="older")
  THEN: findById("q1")?.title == "original"

upsertByIdIfNewerVersion_updates_on_higher_version:
  GIVEN: upsert quest(id="q1", version=3, title="old")
  WHEN: upsert quest(id="q1", version=5, title="new")
  THEN: findById("q1")?.title == "new"

deleteById_removes_entity:
  GIVEN: upsert quest(id="q1")
  WHEN: deleteById("q1")
  THEN: findById("q1") == null

visibleOn_set_survives_roundtrip:
  GIVEN: upsert quest(visibleOn=setOf("home", "arena"))
  WHEN: findById(id)?.visibleOn
  THEN: == setOf("home", "arena")  [StringSetConverter roundtrip]

visibleOn_empty_set_survives_roundtrip:
  GIVEN: upsert quest(visibleOn=emptySet())
  WHEN: findById(id)?.visibleOn
  THEN: emptySet() (no NPE, no stray elements)
```

**Depends on:** QuestEntity, QuestDao, StringSetConverter (backend-dev tasks #7, #8)
**Covers:** AC#13, AC#14, AC#15

---

## 3. SectionDaoBoundaryTest

- **Файл:** `shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/SectionDaoBoundaryTest.kt`
- **Тип:** instrumented test

**Сценарии:**

```
observeByQuest_excludes_archived:
  GIVEN: section(questId="q1", archived=false), section(questId="q1", archived=true)
  WHEN: dao.observeByQuest("q1").first()
  THEN: result.size == 1 AND result[0].archived == false

observeByQuest_orders_by_order_asc:
  GIVEN: section(questId="q1", order=2), section(questId="q1", order=0), section(questId="q1", order=1)
  WHEN: observeByQuest("q1").first()
  THEN: result[0].order == 0, result[1].order == 1, result[2].order == 2

upsertByIdIfNewerVersion_skips_on_equal_version:
  GIVEN/WHEN/THEN — same pattern as QuestDaoBoundaryTest

upsertByIdIfNewerVersion_updates_on_higher_version:
  GIVEN/WHEN/THEN — same pattern
```

**Depends on:** SectionEntity, SectionDao (backend-dev tasks #9)

---

## 4. CatalogDaoBoundaryTest — update existing tests

- **Файл:** существующий тест (`CatalogRepositoryImplTest.kt` или отдельный DAO test)
- **Тип:** update (НЕ создание нового файла)

**Обновления:**
```
upsertByIdIfNewerVersion_skips_on_equal_version:
  GIVEN: dao.upsertByIdIfNewerVersion("id", ..., version=3L, ...)
  WHEN: dao.upsertByIdIfNewerVersion("id", ..., version=3L, name="new", ...)
  THEN: findById("id")?.name == original

observeAll_excludes_archived:
  GIVEN: catalog(id="c1", archived=false), catalog(id="c2", archived=true)
  WHEN: dao.observeAll().first()
  THEN: result.map{it.id} containsOnly "c1"

Existing 7 positional-arg CatalogEntity call sites:
  UPDATE to named args: CatalogEntity(id="id", name="name", picturePath=null, pictureUrl=null)
  (new fields have defaults — compilable without change, but named args = explicit)
```

**Depends on:** CatalogEntity (task #5), CatalogDao (task #6)

---

## 5. StringSetConverterTest (JVM)

- **Файл:** `shared/core/persistence/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/StringSetConverterTest.kt`
- **Тип:** JVM unit test (не instrumented)

**Сценарии:**

```
fromSet_null_returns_null:
  GIVEN: converter.fromSet(null)
  THEN: == null

toSet_null_returns_null:
  GIVEN: converter.toSet(null)
  THEN: == null

roundtrip_non_empty_set:
  GIVEN: original = setOf("home", "arena")
  WHEN: converter.toSet(converter.fromSet(original))
  THEN: == original

roundtrip_empty_set:
  GIVEN: original = emptySet<String>()
  WHEN: converter.toSet(converter.fromSet(original))
  THEN: == emptySet()

fromSet_does_not_use_comma_separator:
  GIVEN: setOf("home", "arena")
  WHEN: fromSet(...)
  THEN: result does NOT contain ","  [validate separator is \u001F not comma]
```

**Depends on:** StringSetConverter (backend-dev task #7)

---

## Fakes (не создаются в phase-01)

Walking Skeleton уже содержит fakes для domain repositories. Phase-01 создаёт Room infrastructure — DAO тесты используют Room in-memory, не fakes. Новые fakes для data-layer repositories создаются в phase-02.

---

## Notes

- OQ-TEST-2 (kspAndroid codegen verification) — разрешён implicit: если `./gradlew :shared:core:persistence:connectedAndroidTest` проходит → kspAndroid генерация корректна
- Все 7 существующих positional-arg CatalogEntity конструкторов в тестах: нужно обновить если breaking change случится при добавлении полей. С Kotlin defaults — позиционные calls с 4 args всё ещё валидны (новые поля = position 5-8 = имеют defaults). Но именованные args более явны — test-dev обновляет при обнаружении compile ошибок.
