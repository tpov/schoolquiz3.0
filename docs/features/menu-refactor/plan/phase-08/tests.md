---
phase: 08
role: test-dev
---

# Phase 08 — Test Tasks

## Pattern Invariants

- Instrumented tests (Room) — `Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()`
- `MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)` — для migration tests
- JVM integration tests (`commonTest`) — используют фейки из Phase 04/05; не Android runtime
- Тест-dev НЕ модифицирует production code
- `kotlin.test` framework в `commonTest`; JUnit4 в `androidTest`
- Не использовать Turbine — только `.take(1).toList()`, `.value`, `CountDownLatch`

---

## CREATE AppDatabaseMigrationTest (instrumented)

**Файл:** `shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabaseMigrationTest.kt`
**Source:** `04-testing.md §5.1`

**Framework:** `MigrationTestHelper` + Room in-memory

**Сценарии:**

- `version1_schema_valid`: given `MigrationTestHelper.createDatabase(version=1)`, when open with `runMigrationsAndValidate()`, then tables `user_stats` и `catalogs` существуют без schema validation errors
- `version1_user_stats_insert_query`: given in-memory `AppDatabase` v1, when `userStatsDao.upsert(validEntity)` + `userStatsDao.findByUid(uid)`, then returned entity == inserted entity (no crash)
- `version1_catalog_insert_query`: given in-memory `AppDatabase` v1, when `catalogDao.insertAll([validCatalogEntity])` + `catalogDao.observeAll().take(1)`, then list size == 1

**Helper:**
```
fun testEntity(uid: String = "test-uid") = UserStatsEntity(
    uid = uid,
    // all other fields with defaults
)
fun testCatalogEntity(id: String = "test-catalog") = CatalogEntity(
    id = id,
    name = "Test Catalog",
    picturePath = null,
    pictureUrl = null,
)
```

---

## CREATE UserStatsDaoTest (instrumented)

**Файл:** `shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/UserStatsDaoTest.kt`
**Source:** `04-testing.md §5.2`

**Setup:** `@Before fun setUp() { db = Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build(); dao = db.userStatsDao() }`

**Сценарии:**

- `upsert_and_observe_by_uid`: given `dao.upsert(entity(uid="user1"))`, when `dao.observeByUid("user1").take(1).toList()`, then `result.first()?.uid == "user1"`
- `updateDeveloperLevel_targeted_update`: given `dao.upsert(entity(uid="u", developerLevel=0, teacherLevel=50))`, when `dao.updateDeveloperLevel("u", 100)`, then `dao.findByUid("u")?.developerLevel == 100` AND `dao.findByUid("u")?.teacherLevel == 50` (другие поля не тронуты)
- `upsert_replace_overwrites_developer_level`: given `dao.upsert(entity(developerLevel=100))`, when `dao.upsert(entity(developerLevel=0))` (полный UPSERT), then `dao.findByUid(uid)?.developerLevel == 0`
- `observe_returns_null_for_unknown_uid`: given пустая БД, when `dao.observeByUid("unknown-uid").take(1).toList()`, then `result.first() == null`

**Note:** `UserStatsEntity` содержит 17 полей (per `08-storage-model.md`). Использовать helper с дефолтными значениями.

---

## CREATE CatalogDaoTest (instrumented)

**Файл:** `shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/CatalogDaoTest.kt`
**Source:** `04-testing.md §3.4.2`, scenarios CF-06..CF-10

**Setup:** `@Before fun setUp() { db = Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build(); dao = db.catalogDao() }`

**Сценарии:**

- CF-06: given `dao.insertAll([entity(id="a"), entity(id="b")])`, when `dao.observeAll().take(1).toList()`, then `result.first().size == 2`
- CF-07: given `dao.insertAll([entity(id="existing")])`, when `dao.replaceAll([entity(id="new")])`, then `observeAll()` никогда НЕ эмитит пустой список между delete и insert (атомарная операция — `@Transaction` гарантия); проверить через flow with CountDownLatch или single take(1)
- CF-08: given `dao.insertAll([entity(id="old1"), entity(id="old2")])`, when `dao.replaceAll([entity(id="new1")])`, then `dao.findById("old1") == null` AND `dao.findById("new1") != null`
- CF-09: given `dao.insertAll([entity(id="surveys"), entity(id="courses"), entity(id="games"), entity(id="school")])`, when `dao.observeAll().take(1).toList()`, then `result.first().map { it.id } == ["courses", "games", "school", "surveys"]` (ASC order)
- CF-10: given пустая БД, when `dao.findById("nonexistent")`, then returns `null`

---

## CREATE CatalogFirstFetchIntegrationTest

**Файл:** `shared/core/catalog/data/src/commonTest/kotlin/com/tpov/schoolquiz/core/catalog/data/CatalogFirstFetchIntegrationTest.kt`
**Source:** `04-testing.md §5.3`, Journey "First-launch catalog pull"

**Setup:** `FakeCatalogLocalDataSource` (обёртка над `FakeCatalogDao`) + `FakeCatalogRemoteDataSource` + `FakeCatalogUrlResolver`

**Сценарии:**

- given пустая БД (`fakeLocal.observeAll()` эмитит пустой список), `fakeRemote.result = Result.success([dto(id="a", name="A", picturePath="a.jpg")])`, when `CatalogRepositoryImpl.refreshFromRemote()`, then `fakeLocal.replaceAllCalls == 1` AND `fakeLocal` содержит entity с `id="a"` AND `fakeUrlResolver.callCount == 1` (URL resolved для `"a.jpg"`)
- given `fakeRemote.result = Result.success([])`, when `refreshFromRemote()`, then `fakeLocal.replaceAllCalls == 1` AND entities list пустой (пустой remote = clear local)

---

## CREATE CatalogWarmCacheIntegrationTest

**Файл:** `shared/core/catalog/data/src/commonTest/kotlin/com/tpov/schoolquiz/core/catalog/data/CatalogWarmCacheIntegrationTest.kt`
**Source:** `04-testing.md §5.3`, Journey "Warm cache (Room → UI)"

**Сценарии:**

- given `fakeLocal` предзаполнен `[entity(id="b"), entity(id="a")]`, when `CatalogRepositoryImpl.observeAll().take(1).toList()`, then `result.first()[0].id == CatalogId("a")` (sort ASC) AND `fakeRemote.fetchAllCalls == 0` (Firestore не вызван)

---

## CREATE CatalogOfflineEmptyIntegrationTest

**Файл:** `shared/core/catalog/data/src/commonTest/kotlin/com/tpov/schoolquiz/core/catalog/data/CatalogOfflineEmptyIntegrationTest.kt`
**Source:** `04-testing.md §5.3`, Journey "Offline launch"

**Сценарии:**

- given пустая БД + `fakeRemote.result = Result.failure(IOException("offline"))`, when `CatalogRepositoryImpl.observeAll().take(1).toList()`, then `result.first().isEmpty()` AND no exception thrown
- given пустая БД + offline, when `CatalogRepositoryImpl.refreshFromRemote()`, then returns `Result.failure(IOException)` AND no crash AND `fakeLocal.replaceAllCalls == 0` (ничего не записано при ошибке)

---

## CREATE SyncDeactivatesDevModeIntegrationTest

**Файл:** `shared/feature/app-shell/data/src/commonTest/kotlin/com/tpov/schoolquiz/feature/appshell/data/SyncDeactivatesDevModeIntegrationTest.kt`
**Source:** `04-testing.md §5.3`, Journey "Dev mode auto-deactivation via sync", scenario US-08

**Setup:** `FakeUserStatsDao` + `FakeFirebaseUserStatsDataSource` + `UserStatsRepositoryImpl`

**Сценарии:**

- given `fakeDao` содержит entity с `developerLevel=100` (клиент активировал dev mode), `fakeFirebase.result = Result.success(rawStats(developerLevel=0))` (сервер не знает о dev mode), when `UserStatsRepositoryImpl.refreshProfile()`, then:
  - `fakeDao.lastUpserted?.developerLevel == 0` (server value перезаписал клиентский 100)
  - `fakeDao.observeByUid(uid).take(1).first()?.developerLevel == 0`
  - returns `Result.success(Unit)`

**Note:** этот тест фиксирует известный race condition из ADR-HLA-02 "last-write-wins, edge case". Тест dokumentiert поведение, не фиксирует его как баг.

---

## Validation

| Команда | Ожидаемый результат |
|---------|---------------------|
| `./gradlew :shared:core:catalog:data:jvmTest --no-configuration-cache` | GREEN — CatalogFirstFetch/WarmCache/Offline + CF-11..23 |
| `./gradlew :shared:feature:app-shell:data:jvmTest --no-configuration-cache` | GREEN — SyncDeactivatesDevModeIntegrationTest + US-01..08 |
| `./gradlew :shared:core:persistence:connectedDebugAndroidTest` | GREEN — AppDatabaseMigrationTest + UserStatsDaoTest + CatalogDaoTest (CF-06..10) |
| Firebase deploy + `firebase emulator` | catalogs read по authenticated user работает |
