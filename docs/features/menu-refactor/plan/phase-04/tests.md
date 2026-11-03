---
phase: 04
role: test-dev
---

# Phase 04 — Test Tasks

## Pattern Invariants

- Fakes для DAO и Firebase DataSource — не MockK (project convention)
- `FakeUserStatsDao` из `04-testing.md §4.1` — canonical implementation
- `FakeFirebaseUserStatsDataSource` из `04-testing.md §4.4` — canonical implementation
- `runTest` + `StandardTestDispatcher` для coroutines
- NO Turbine — `.take(1).toList()` или `.value` для Flow assertions

---

## CREATE UserStatsRepositoryImplTest

**Файл:** `shared/feature/app-shell/data/src/commonTest/.../UserStatsRepositoryImplTest.kt`
**Source:** `04-testing.md §3.5`, scenarios US-01..US-08

**Fakes необходимые:**

```
FakeUserStatsDao (create if not exists):
  - private val _flow = MutableStateFlow<UserStatsEntity?>(null)
  - var lastUpserted: UserStatsEntity? = null
  - var updateDeveloperLevelCalls: Int = 0
  - fun emit(entity: UserStatsEntity?)

FakeFirebaseUserStatsDataSource (create if not exists):
  - var result: Result<RawUserStats> = Result.success(RawUserStats())
  - var fetchCalls: Int = 0
```

**Сценарии:**

- US-01: given `currentUid()=null`, when `observeStats().take(1).toList()` within `runTest`, then empty list (no emission)
- US-02: given uid exists, `dao.emit(null)`, when `observeStats().take(1)`, then `UserStats.EMPTY` or `UserStats.guest()`
- US-03: given entity with all fields populated, when `entity.toDomain()`, then all 16 fields mapped: `qualification.developer == entity.developerLevel`, `qualification.tester == entity.testerLevel`, etc.
- US-04: given uid exists, when `setLocalDeveloperLevel(100)`, then `fakeDao.updateDeveloperLevelCalls == 1` and `fakeDao.lastUpserted == null` (upsert NOT called)
- US-05: given `currentUid()=null`, when `setLocalDeveloperLevel(100)`, then `fakeDao.updateDeveloperLevelCalls == 0`
- US-06: given Firebase returns `RawUserStats(developerLevel=0)`, when `refreshProfile()`, then `fakeDao.lastUpserted != null` and `result == Result.success`
- US-07: given `currentUid()=null`, when `refreshProfile()`, then `Result.failure` and `firebaseDataSource.fetchCalls == 0`
- US-08: given Room has `developerLevel=100`, Firebase returns `RawUserStats(developerLevel=0)`, when `refreshProfile()`, then `fakeDao.lastUpserted?.developerLevel == 0` (full overwrite)

---

## CREATE UserStatsMapper round-trip tests (как часть US-03)

В `UserStatsRepositoryImplTest.kt` или отдельный `UserStatsMapperTest.kt`:

- Mapper test: given `UserStatsEntity(all fields)`, when `.toDomain()`, then `UserStats` fields match entity fields
- Mapper test: given `RawUserStats(all fields)`, when `.toEntity(uid)`, then `UserStatsEntity` fields match RawUserStats
- Edge case: `avatarUrl=null` preserved through mapper

---

## Edge Cases

| Сценарий | Priority | Why |
|----------|----------|-----|
| US-04: ONLY updateDeveloperLevel called, not upsert | P0 | ADR-HLA-02 — targeted UPDATE invariant |
| US-08: full overwrite including developerLevel | P0 | Dev mode deactivation correctness |
| US-07: null uid → no Firebase call | P1 | Auth guard |

---

## Validation

| Команда | Ожидаемый результат |
|---------|---------------------|
| `./gradlew :shared:feature:app-shell:data:jvmTest --no-configuration-cache` | GREEN — US-01..08 |
