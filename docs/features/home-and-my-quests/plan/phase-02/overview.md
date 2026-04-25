---
phase: 02
name: Cascade Data Stacks — 5 Feature Data Modules + Catalog Delta-Sync
complexity: complex
date: 2026-04-23
---

# Phase-02: Cascade Data Stacks

## Goal

Создать все data-layer компоненты для 6-уровневой каскадной синхронизации:
- Обновить `CatalogRepositoryImpl` на delta-sync с `lastModifiedAt` cursor + upsert/delete логику
- Создать 5 новых feature data модулей (quest/section/theme/lesson/question): RepositoryImpl, LocalDataSource, RemoteDataSource interface, Dto, Mappers, Koin module
- Создать 5 новых firebase platform модулей (RemoteDataSource impls + Firestore DtoMappers + Koin modules)
- Обновить `FirebaseCatalogRemoteDataSource` на `fetchChangedSince(cursor: Long)`

## Scope

`shared/core/catalog/data` (CatalogRepositoryImpl delta-sync + FirestoreCatalogDtoMapper), `shared/feature/quest/data` (NEW), `shared/feature/section/data` (NEW), `shared/feature/theme/data` (NEW), `shared/feature/lesson/data` (NEW), `shared/feature/question/data` (NEW), `platform/firebase` (new Firebase modules per feature), `settings.gradle.kts` (register 5 new data modules + 5 firebase sub-modules).

## Layer

data + platform adapters

## Role Inputs

- `backend.md` — backend-dev
- `tests.md` — test-dev

## Review Tags

- `concurrency-review` (dual parallel Firebase queries in QuestRepositoryImpl — merge/dedupe; cursor management in SyncStateRepository; Flow pipelines в LocalDataSource)
- `architecture-review` (5 new modules must observe clean-arch: no data→presentation imports, no bidirectional feature coupling)

---

## Options Considered

| Критерий | Option A — 5 separate feature data modules (recommended) | Option B — monolithic shared/core/cascade/data | Option C — extend SyncWorker directly |
|----------|----------------------------------------------------------|----------------------------------------------|--------------------------------------|
| Modularity | high — each feature owns its data | low — god module | low — SyncWorker god object |
| Test isolation | each module testable independently | coupled | coupled to Android |
| Coupling с domain | clean — data module depends on its domain | cross-domain coupling risk | SRP violation |
| Refactor cost | small per module | large (monolith) | large (SyncWorker split) |
| Consistency с existing pattern | follows CatalogData pattern exactly | new pattern | new anti-pattern |

**Recommended: Option A**

**Rationale:** Следует существующему `shared/core/catalog/data` паттерну. Каждый feature модуль независимо тестируется. Dependency direction одностороннее (data → domain). Consistent с ADR-CMP-51.

**Rejected Option B:** Монолитный cascade/data создаёт cross-feature coupling и нарушает Invariant #3 (bidirectional между features через общий модуль).

**Rejected Option C:** SRP violation — SyncWorker должен быть orchestration-only, не знать о конкретных entity types.

---

## Traceability

| Problem (from 2-grounding.md) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|-------------------------------|-----------|-------------|-----------------|-------------|-----------|
| P1: CatalogRepositoryImpl full-replace вместо delta | `CatalogRepositoryImpl.kt:24`, `FirebaseCatalogRemoteDataSource.kt:13` | `SyncWorker → CatalogRepositoryImpl.sync()` | cursor через SyncStateRepository (не параметр), existing callers не ломаются | Rewrite `refreshFromRemote()` на cursor-based; update Firebase query | `./gradlew :shared:core:catalog:data:jvmTest` |
| P2: Quest/Section/Theme/Lesson/Question data layer отсутствует | все 5 новых `*/data/` модулей | SyncWorker → CascadingSyncOrchestrator (phase-03) | walking skeleton domain interfaces — НЕ трогать | Create 5 data stacks following CatalogData pattern | `./gradlew :shared:feature:quest:data:jvmTest` + same for section/theme/lesson/question |
| P4: SyncStateRepository не подключён | `apps/android-next/di/SyncModule.kt:12` | Koin wiring | InMemorySyncStateRepository impl already exists | Add `single<SyncStateRepository> { InMemorySyncStateRepository() }` в syncModule | `./gradlew allTests` + Koin graph验证 |

P3, P5, P6, P7, P8 — реализованы в phase-01, phase-03..05.

---

## State Matrix Coverage

**Matrix 1 (Catalog upsert/delete):**
- Row 1.1 (absent + archived → SKIP): CatalogRepositoryImpl phase-02
- Row 1.2 (absent + not archived → INSERT): CatalogRepositoryImpl upsert
- Row 1.3 (present + newer + archived → DELETE): `deleteById` call
- Row 1.4 (present + newer + not-archived → UPSERT): `upsertByIdIfNewerVersion`
- Row 1.5 (equal version → SKIP): DAO atomic check
- Row 1.6 (older version → SKIP): DAO atomic check
- EDGE 1.7 (stale tombstone → SKIP): version guard prevents re-delete
- EDGE 1.9 (visibleOn=[] + owner=me → DELETE): QuestRepositoryImpl delete logic

**Matrix 2 (Section/Theme/Lesson/Question):**
- Rows 2.2-2.7: каждый RepositoryImpl реализует upsert/delete по pattern Matrix 1

**Matrix 4:**
- Row 4.1 (baseline availableShelves={"home","arena"}): hard-coded в QuestRepositoryImpl MVP

---

## New Files

**Catalog data (modified):** нет новых files, только изменения

**Quest data (NEW module):**

| File | Module |
|------|--------|
| `shared/feature/quest/data/src/commonMain/.../QuestLocalDataSource.kt` | quest/data |
| `shared/feature/quest/data/src/commonMain/.../QuestLocalDataSourceImpl.kt` | quest/data |
| `shared/feature/quest/data/src/commonMain/.../QuestRemoteDataSource.kt` | quest/data |
| `shared/feature/quest/data/src/commonMain/.../dto/QuestDto.kt` | quest/data |
| `shared/feature/quest/data/src/commonMain/.../mapper/QuestDtoMapper.kt` | quest/data |
| `shared/feature/quest/data/src/commonMain/.../mapper/QuestMapper.kt` | quest/data |
| `shared/feature/quest/data/src/commonMain/.../QuestRepositoryImpl.kt` | quest/data |
| `shared/feature/quest/data/src/commonMain/.../di/QuestDataModule.kt` | quest/data |
| `shared/feature/quest/data/build.gradle.kts` | quest/data |
| `platform/firebase/src/main/.../quest/FirebaseQuestRemoteDataSource.kt` | firebase |
| `platform/firebase/src/main/.../quest/FirestoreQuestDtoMapper.kt` | firebase |
| `platform/firebase/src/main/.../di/FirebaseQuestModule.kt` | firebase |

Plus analogous files for section, theme, lesson, question (×4 = 40+ new files).

---

## Modified Files

| File | Change |
|------|--------|
| `shared/core/catalog/data/src/commonMain/.../CatalogRepositoryImpl.kt` | rewrite refreshFromRemote: cursor via SyncStateRepository, upsertByIdIfNewerVersion, deleteById for archived |
| `shared/core/catalog/data/src/commonMain/.../CatalogRemoteDataSource.kt` | `fetchChangedSince(cursor: Long)` replaces `fetchAll()` |
| `platform/firebase/src/main/.../catalog/FirebaseCatalogRemoteDataSource.kt` | Implement `fetchChangedSince(cursor)` — Firestore query with `where("lastModifiedAt",">",cursor)` |
| `apps/android-next/src/main/.../di/SyncModule.kt` | +`single<SyncStateRepository> { InMemorySyncStateRepository() }` |
| `settings.gradle.kts` | +5 new data modules + firebase sub-module entries |
| `apps/android-next/src/main/.../AppApplication.kt` | +import and include 5 data modules + 5 firebase modules in startKoin |

## Deleted Files

None.

---

## Dependencies

- Phase-01 MUST BE COMPLETE (AppDatabase v2 + 5 entities + DAOs + kspJvm)
- Walking Skeleton domain interfaces (quest/section/theme/lesson/question/domain) — already exist, do NOT modify

---

## Acceptance Criteria (phase-02 scope)

- AC#7: `CatalogRepositoryImpl.refreshFromRemote()` with cursor=0 upserts all 3 catalogs + advances cursor — `CatalogRepositoryImplTest` green
- AC#8: refreshFromRemote archives: `archived=true` dto → local deleted — `CatalogRepositoryImplTest` green
- AC#9: QuestRepositoryImpl makes 2 parallel Firestore queries (A+B) + merge/dedupe — `QuestRepositoryImplTest` green
- AC#19: first-sync run upserts all catalogs + quests (with fake remote) — `CatalogFirstFetchIntegrationTest` updated
- AC#20: delta-sync reads only changed items (cursor advanced) — `CatalogFirstFetchIntegrationTest` updated

---

## Tests Required

```
CatalogRepositoryImplTest (update existing):
  - when cursor=0 then fetchChangedSince(0) called and all returned items upserted (AC#7)
  - when dto archived=true and version>local then deleteById called (AC#8)
  - when dto version=local then skip (Matrix 1.5)
  - when step succeeds then setCursor called with max(dto.lastModifiedAt) (P4 / cursor management)
  - when Firestore fails then cursor not advanced (cursor stays 0)

QuestRepositoryImplTest (new):
  - given changed catalogIds, when refreshFromRemote, then fetchOwnChanged AND fetchPublicChanged both called (AC#9)
  - when same quest in Query A and B, then Room has exactly 1 row (dedupe)
  - when quest archived=true, then deleteById called (Matrix 1 Quest analog)
  - when quest visibleOn=[], then deleteById called (EDGE 1.9)
  - when refreshFromRemote succeeds, then cursor advanced (cursor management)

SectionRepositoryImplTest / ThemeRepositoryImplTest / LessonRepositoryImplTest / QuestionRepositoryImplTest (new):
  - when dto not archived, then upserted (Matrix 2.2)
  - when dto archived + newer version, then deleted (Matrix 2.3)
  - when dto version <= local, then skip (Matrix 2.5-2.6)
  - when refreshByParents succeeds, then cursor advanced

CatalogArchiveIntegrationTest (NEW — Journey 6):
  given: catalog in Room + SyncNow
  when: remote returns archived=true for that catalog
  then: catalog deleted from Room, observeAll emits without it

MyQuestsOfflineEmptyIntegrationTest (NEW — Journey 3):
  given: Room empty, no network
  when: observeMyQuests("uid")
  then: emits emptyList (no crash)
```

---

## Pattern Invariants

- Каждый `*RepositoryImpl.refreshFromRemote/refreshByParents` ДОЛЖЕН: (1) читать cursor через `SyncStateRepository.getCursor(collectionId)`, (2) вызывать setCursor ТОЛЬКО при success, (3) возвращать `Result.failure` без retry при ошибке
- `QuestRepositoryImpl` ДОЛЖЕН запускать Query A и Query B независимо (не последовательно) — merge/dedupe клиент-сайд по id
- Все `*LocalDataSource` интерфейсы ДОЛЖНЫ делегировать в DAO без бизнес-логики
- Все mapper `.toDomain()` функции ДОЛЖНЫ быть pure (no side effects, no coroutines)
- Scaffold файлы (build.gradle.kts, settings.gradle.kts, AppApplication.kt) изменяет ТОЛЬКО backend-dev

---

## Validation

```bash
./gradlew :shared:core:catalog:data:jvmTest
./gradlew :shared:feature:quest:data:jvmTest
./gradlew :shared:feature:section:data:jvmTest
./gradlew :shared:feature:theme:data:jvmTest
./gradlew :shared:feature:lesson:data:jvmTest
./gradlew :shared:feature:question:data:jvmTest
./gradlew assemble
./gradlew allTests
```

---

## Handoff Notes

- `CatalogRemoteDataSource` interface меняется: `fetchAll()` → `fetchChangedSince(cursor: Long)`. Если `CatalogFirstFetchIntegrationTest` использует FakeCatalogRemoteDataSource с методом `fetchAll` — test-dev обновляет fake (или override обоих методов с default impl)
- `AppApplication.kt` — backend-dev добавляет ~12 новых Koin module регистраций в startKoin (quest/section/theme/lesson/question domain+data modules + firebase modules + syncStateRepository). Полный список в `06-api-contract.md` §13
- Firebase composite indexes (6 штук) — ручная регистрация admin-ом в Firebase Console (out-of-scope client code, documented in `06-api-contract.md` §10)
- Backend hard dependency (P2): Firestore collections `quests/sections/themes/lessons/questions` должны существовать; без них `fetchChangedSince` вернёт пустой список (graceful)
