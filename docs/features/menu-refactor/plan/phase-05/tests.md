---
phase: 05
role: test-dev
---

# Phase 05 — Test Tasks

## Pattern Invariants

- Fakes для DAO и Remote DataSource — проверить наличие `FakeCatalogDao` + `FakeCatalogRemoteDataSource` в Walking Skeleton test; если нет — создать по blueprint из `04-testing.md §4.2:289` + `§4.3:307`
- `FakeCatalogUrlResolver` — создать по blueprint `04-testing.md §4.3b:323`; симметрично с existing `FakeUserStatsRepository` (`shared/feature/app-shell/data/src/commonTest/.../fake/FakeUserStatsRepository.kt` — project convention для test fakes)
- Flow testing: use `.toList()` / `.first()` inspection, не Turbine (project convention per `.claude/rules/testing.md:53`); canonical pattern — existing `VisibilityTest.kt:32` (class declaration)
- Test naming: backtick Kotlin-style `` `scenario name in russian`() `` — existing pattern `RegisterTapTest.kt:21` (class declaration; backtick test function naming)
- Тест-dev НЕ модифицирует production code (rule per `.claude/rules/testing.md:68`)

---

## CREATE CatalogRepositoryImplTest

**Файл:** `shared/core/catalog/data/src/commonTest/.../CatalogRepositoryImplTest.kt`
**Source:** `04-testing.md §3.4.3`, scenarios CF-11..CF-18

**Setup:**
```
FakeCatalogLocalDataSource (wraps FakeCatalogDao):
  - tracks replaceAllCalls, observeAll emits from internal state
FakeCatalogRemoteDataSource:
  - var result: Result<List<CatalogDto>>
  - var fetchAllCalls: Int = 0
FakeCatalogUrlResolver (non-null suspend lambda, matches production contract `suspend (String) -> String`):
  - var callCount: Int = 0
  - var shouldThrow: Boolean = false
  - resolver: suspend (String) -> String = { path -> callCount++; if (shouldThrow) throw IOException("fake") else "https://fake.example.com/$path" }
```

**Сценарии:**

- CF-11: given `observeAll()` called, when emit, then `fakeRemote.fetchAllCalls == 0` (no Firebase on observe)
- CF-12: given Firebase returns `[CatalogDto(id="a", name="A", picturePath="a.jpg")]`, when `refreshFromRemote()`, then `fakeLocal.replaceAllCalls == 1` and entities contain id="a"
- CF-13: given `fakeRemote.result = Result.failure(IOException())`, when `refreshFromRemote()`, then `Result.failure` returned
- CF-14: given `CatalogDto(picturePath=null)`, when `refreshFromRemote()`, then `fakeUrlResolver.callCount == 0` and `entity.pictureUrl == null`
- CF-15: given `CatalogDto(picturePath="catalog/test.jpg")`, when `refreshFromRemote()`, then `fakeUrlResolver.callCount == 1` and called with `"catalog/test.jpg"`
- CF-16: given `sync()` called, then `fakeLocal.replaceAllCalls == 1` (delegates to refreshFromRemote)
- CF-17: given Room has `[CatalogEntity(id="b"), CatalogEntity(id="a")]` (unordered), when `observeAll().take(1)`, then `result[0].id == CatalogId("a")` and `result[1].id == CatalogId("b")` (sorted ASC)
- CF-18: given Room has `CatalogEntity(id="test-id")`, when `getById(CatalogId("test-id"))`, then Catalog returned with matching id

---

## CREATE CatalogMapperTest

**Файл:** `shared/core/catalog/data/src/commonTest/.../CatalogMapperTest.kt`
**Source:** `04-testing.md §3.4.4`, scenarios CF-19..CF-23

- CF-19: given `CatalogEntity(id="surveys", ...)`, when `toDomain()`, then `domain.id == CatalogId("surveys")`
- CF-20: given `CatalogEntity(name="Опросы")`, when `toDomain()`, then `domain.name == "Опросы"`
- CF-21: given `Catalog(picturePath=null)`, when `toEntity()`, then `entity.picturePath == null`
- CF-22: given `DocumentSnapshot` with blank name, when `toCatalogDto()`, then returns null
- CF-23: given `CatalogDto(picturePath=null)`, when `toEntity()`, then `entity.picturePath == null`

---

## Validation

| Команда | Ожидаемый результат |
|---------|---------------------|
| `./gradlew :shared:core:catalog:data:jvmTest --no-configuration-cache` | GREEN — CF-11..23 |
| `./gradlew :shared:core:catalog:domain:jvmTest --no-configuration-cache` | GREEN — Walking Skeleton CF-01..05 still green |
