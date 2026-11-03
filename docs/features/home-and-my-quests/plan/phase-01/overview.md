---
phase: 01
name: Scaffold Foundation + DB Schema v2 + Quiz Cleanup
complexity: complex
date: 2026-04-23
---

# Phase-01: Scaffold Foundation + DB Schema v2 + Quiz Cleanup

## Goal

Подготовить инфраструктурный фундамент, без которого все последующие фазы не компилируются:
- Bump `AppDatabase` v1 → v2 + добавить 5 новых entities + `StringSetConverter`
- Расширить `CatalogEntity` + `CatalogDao` 4 новыми полями (version, contentsVersion, lastModifiedAt, archived)
- Добавить `kspJvm` в `shared/core/persistence` (BD-1 blocker)
- Bump Coil 3.1.0 → 3.4.0 (проверить Kotlin ≥ 2.1.0 — уже 2.1.20, выполнено) (BD-2)
- Cleanup пустых `quiz/` модулей + placeholder файлов в `catalog/domain` (Decision #44)
- `AppDatabaseSchemaValidationTest` (androidTest) как gate для schema v2

## Scope

`shared/core/persistence` (DB schema), `gradle/libs.versions.toml` (Coil bump), `settings.gradle.kts` (quiz cleanup), `shared/core/catalog/domain` (placeholder cleanup), `shared/core/catalog/data` (CatalogLocalDataSource upsert/delete), `shared/core/persistence/di/PersistenceModule.kt` (fallbackToDestructiveMigration + TypeConverter + DAOs).

## Layer

data (infrastructure) + scaffold

## Role Inputs

- `backend.md` — backend-dev
- `tests.md` — test-dev

## Review Tags

- `schema-review` (5 new Room tables + TypeConverter)
- `concurrency-review` (уже использует Flow в CatalogDao — ensure archived filter не нарушает existing collectors)

---

## Options Considered

| Критерий | Option A — destructive migration v1→v2 (recommended) | Option B — @Migration(1,2) scripted | Option C — rename DB |
|----------|------------------------------------------------------|-------------------------------------|----------------------|
| Complexity | low (1 builder call) | medium (5 new tables + 4 new cols script) | high (breaks DAOs) |
| Test cost | `AppDatabaseSchemaValidationTest` validates v2 fresh | `MigrationTestHelper` runMigrationsAndValidate | large refactor |
| Refactor cost если неверно | small (pre-production, no live users) | medium (migration script bugs hard to debug) | large |
| Risk при schema error | auto-recreate = no data loss MVP | migration fails = data corrupt | rename + re-sync |
| Coupling с backend | none (client-only) | none | none |

**Recommended: Option A (destructive migration)**

**Rationale:** Pre-production (нет live users), 5 новых таблиц — риск ошибки в @Migration скриптах выше пользы. Decision #53 + #26 явно принимают destructive. `fallbackToDestructiveMigration()` + идемпотентный sync восстановит данные.

**Rejected Option B:** @Migration(1,2) нужен для production release (когда появятся live users). Будет написан отдельно перед prod-деплоем.

**Rejected Option C:** Ломает всю существующую DAOs wire-up; не обосновано.

---

## Traceability

| Problem (from 2-grounding.md) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|-------------------------------|-----------|-------------|-----------------|-------------|-----------|
| P1: Catalog data layer full-replace (CatalogEntity только 4 поля, нет cursor) | `CatalogEntity.kt`, `CatalogDao.kt`, `CatalogDto.kt`, mappers | `CatalogRepositoryImpl.kt:24` | schema v2, BC compat defaults | Add 4 fields w/ defaults; add upsert/delete DAO methods; update mappers | `./gradlew :shared:core:persistence:connectedAndroidTest` + `CatalogRepositoryImplTest` |
| P5: Coil 3.1.0 vs 3.4.0 | `gradle/libs.versions.toml:44` | `android/core/designsystem/build.gradle.kts` | Kotlin ≥ 2.1.0 (already 2.1.20) | Bump `coil3 = "3.4.0"` | `./gradlew :android:core:designsystem:assembleDebug` |
| P6: Quiz module placeholder cleanup | `settings.gradle.kts:47-48,71`, `shared/core/catalog/domain` | `settings.gradle.kts`, placeholder files | none | Delete 3 empty scaffold modules + 5 placeholder files | `./gradlew assemble` зелёный |
| P8: Two QuestRepository interfaces (auto-resolved by P6 cleanup) | `shared/core/catalog/domain/repository/QuestRepository.kt` | placeholder | compile conflict | Delete placeholder → single QuestRepository in quest/domain | `./gradlew :shared:core:catalog:domain:compileKotlinJvm` |

P2 (Quest data layer absent), P3 (MyQuestsScreen absent), P4 (SyncStateRepository not connected), P7 (BrandComponentsInvariantsTest coverage) — реализуются в phase-02..05.

---

## Domain Contract Coverage

Walking Skeleton уже сгенерирован (Catalog.kt уже имеет version/cv/lastModifiedAt/archived поля — VERIFIED). Phase-01 = **adapter-only integration**:
- AC#1: Catalog.kt поля — уже есть в Walking Skeleton, phase-01 не трогает (VERIFIED)
- AC#3: Section/Theme/Lesson/Question entities — создаются в этой фазе (Room layer, не domain)
- AC#12: AppDatabase schema v2 — создаётся в этой фазе

## State Matrix Coverage

- Matrix 1 rows 1.2, 1.4, 1.5, 1.6 (upsert/skip logic) — DAO методы создаются; полная логика в phase-02 CatalogRepositoryImpl
- Matrix 1 row 1.3 (delete on archived) — `deleteById` DAO создаётся; логика в phase-02

---

## New Files

| File | Module | Layer |
|------|--------|-------|
| `shared/core/persistence/src/commonMain/.../StringSetConverter.kt` | persistence | data |
| `shared/core/persistence/src/commonMain/.../QuestEntity.kt` | persistence | data |
| `shared/core/persistence/src/commonMain/.../QuestDao.kt` | persistence | data |
| `shared/core/persistence/src/commonMain/.../SectionEntity.kt` | persistence | data |
| `shared/core/persistence/src/commonMain/.../SectionDao.kt` | persistence | data |
| `shared/core/persistence/src/commonMain/.../ThemeEntity.kt` | persistence | data |
| `shared/core/persistence/src/commonMain/.../ThemeDao.kt` | persistence | data |
| `shared/core/persistence/src/commonMain/.../LessonEntity.kt` | persistence | data |
| `shared/core/persistence/src/commonMain/.../LessonDao.kt` | persistence | data |
| `shared/core/persistence/src/commonMain/.../QuestionEntity.kt` | persistence | data |
| `shared/core/persistence/src/commonMain/.../QuestionDao.kt` | persistence | data |
| `shared/core/persistence/src/androidTest/.../AppDatabaseSchemaValidationTest.kt` | persistence | test |
| `shared/core/persistence/src/androidTest/.../QuestDaoBoundaryTest.kt` | persistence | test |

## Modified Files

| File | Change |
|------|--------|
| `shared/core/persistence/src/commonMain/.../AppDatabase.kt` | version 1→2, 5 new entities, @TypeConverters(StringSetConverter) |
| `shared/core/persistence/src/commonMain/.../CatalogEntity.kt` | +4 fields with defaults (version, contentsVersion, lastModifiedAt, archived), +Index lastModifiedAt |
| `shared/core/persistence/src/commonMain/.../CatalogDao.kt` | +upsertByIdIfNewerVersion, +deleteById, +WHERE archived=0 in observeAll |
| `shared/core/persistence/src/androidMain/.../di/PersistenceModule.kt` | +fallbackToDestructiveMigration, +addTypeConverter, +5 new DAO singles |
| `shared/core/persistence/build.gradle.kts` | +add("kspJvm", libs.room.compiler) |
| `gradle/libs.versions.toml` | coil3 "3.1.0" → "3.4.0" |
| `settings.gradle.kts` | remove include(":shared:feature:quiz:domain"), ":shared:feature:quiz:data", ":android:feature:quiz:presentation" |
| `shared/core/catalog/data/src/commonMain/.../CatalogLocalDataSource.kt` | +upsertByIdIfNewerVersion, +deleteById methods on interface + impl |
| `shared/core/catalog/data/src/commonMain/.../CatalogDto.kt` | +version, +contentsVersion, +lastModifiedAt, +archived |
| `shared/core/catalog/data/src/commonMain/.../mapper/CatalogDtoMapper.kt` | map new 4 fields |
| `shared/core/catalog/data/src/commonMain/.../mapper/CatalogMapper.kt` | map new 4 fields |
| `platform/firebase/src/main/.../catalog/FirestoreCatalogDtoMapper.kt` | read 4 new fields from DocumentSnapshot |

## Deleted Files

| File | Reason |
|------|--------|
| `shared/feature/quiz/domain/` (entire module dir) | Decision #44 — empty scaffold |
| `shared/feature/quiz/data/` (entire module dir) | Decision #44 — empty scaffold |
| `android/feature/quiz/presentation/` (entire module dir) | Decision #44 — empty scaffold |
| `shared/core/catalog/domain/src/.../model/Quest.kt` | Decision #44 — placeholder, duplicate QuestId |
| `shared/core/catalog/domain/src/.../repository/QuestRepository.kt` | Decision #44 — placeholder (1 method save) |
| `shared/core/catalog/domain/src/.../use_case/CreateQuestUseCase.kt` | Decision #44 — uses placeholder |
| `shared/core/catalog/domain/src/.../fake/FakeQuestRepository.kt` | Decision #44 — for placeholder |
| `shared/core/catalog/domain/src/.../QuestCatalogLinkTest.kt` | Decision #44 — tests placeholder |

---

## Dependencies

- None (phase-01 = foundation; no dependency on other phases)
- REQUIRES resolved: Kotlin version = 2.1.20 (≥ 2.1.0 required for Coil 3.4.0) — VERIFIED in libs.versions.toml

---

## Acceptance Criteria (phase-01 scope)

- AC#3: Section/Theme/Lesson/Question Room entities созданы с правильными columns
- AC#12: AppDatabase schema v2 — 7 tables (userStats, catalogs, quests, sections, themes, lessons, questions) — `AppDatabaseSchemaValidationTest` green
- AC#13: `CatalogDao.upsertByIdIfNewerVersion` skips on equal/older version — androidTest green
- AC#14: `QuestDao.observeMyQuests(authorUid, null)` correct filter — `QuestDaoBoundaryTest` green
- AC#15: `QuestDao.observeMyQuests(authorUid, catalogId)` catalog filter — `QuestDaoBoundaryTest` green
- Coil bump: `./gradlew :android:core:designsystem:assembleDebug` green
- Quiz cleanup: `./gradlew assemble` green (no references to deleted modules)
- `CatalogDomainModule.kt` — комментарий "CreateQuestUseCase removed pending..." убран

---

## Tests Required

```
AppDatabaseSchemaValidationTest:
  - validateSchema_v2_tables_and_columns: given fresh v2 DB, when SELECT * FROM quests/sections/themes/lessons/questions, then no SQL error; when SELECT version/contentsVersion/lastModifiedAt/archived FROM catalogs, then no error
  - destructive_recreate_when_version_bumped: given v1 DB, when open as v2 with fallbackToDestructiveMigration, then Room recreates all tables

QuestDaoBoundaryTest:
  - observeMyQuests_excludes_archived: given quest(archived=true), when observeMyQuests, then not in result (AC#14)
  - observeMyQuestsInCatalog_filters_by_catalogId: given 2 quests in different catalogs, when filter by catalogId, then only 1 returned (AC#15)
  - upsertByIdIfNewerVersion_skips_on_equal_version: given v=5, upsert again v=5 different title, then original title unchanged (AC#13)
  - upsertByIdIfNewerVersion_updates_on_higher_version: given v=3, upsert v=5, then updated (AC#13)
  - deleteById_removes_entity: given inserted quest, when deleteById, then findById returns null
  - stringSetConverter_roundtrip: given visibleOn={"home","arena"}, when stored and retrieved, then same set (no data loss via \u001F separator)

CatalogDao updated tests (existing test files need named-arg migration):
  - 7 existing CatalogEntity constructor call sites → update to named args (no new tests; just compile fix)
```

---

## Pattern Invariants

- Все новые Entity файлы ДОЛЖНЫ использовать `@Entity(tableName=..., indices=[...])` pattern из `08-storage-model.md` SSoT
- `upsertByIdIfNewerVersion` во всех 6 DAOs ДОЛЖЕН использовать `INSERT OR REPLACE ... WHERE NOT EXISTS (SELECT 1 WHERE id=:id AND version >= :version)` pattern (atomic — per ADR-CMP-52)
- `observeAll/observeByX` запросы ДОЛЖНЫ содержать `WHERE archived = 0` (Decision #52)
- `StringSetConverter` ДОЛЖЕН быть аннотирован `@ProvidedTypeConverter` (KMP Room requirement)
- `PersistenceModule` ДОЛЖЕН вызывать `.addTypeConverter(StringSetConverter())` при builder (required при @ProvidedTypeConverter)

---

## Validation

```bash
# BD-1 resolved: kspJvm added
./gradlew :shared:core:persistence:compileKotlinJvm

# Schema v2 gate (instrumented)
./gradlew :shared:core:persistence:connectedAndroidTest

# Coil 3.4.0 compat check only, not app-build gate
./gradlew :android:core:designsystem:assembleDebug

# Quiz cleanup gate
./gradlew assemble

# Full JVM test (should still be green — domain not touched)
./gradlew allTests
```

---

## Handoff Notes

- `CatalogEntity` existing constructor positional args: 7 existing tests use `CatalogEntity(id, name, picturePath, pictureUrl)` — test-dev updates to named args (defaults cover new fields)
- OQ-TEST-2 (kspAndroid codegen verification) — разрешён в этой фазе: phase-01 build pass = gate перед phase-02 test-dev работой
- OQ-TEST-1 (decompose-testutils для quest/presentation) — DEFERRED to phase-05; не нужен phase-01
- После удаления quiz модулей — `CatalogDomainModule.kt` может содержать dead comment; backend-dev убирает
- Backend dependency (P1): Firestore `catalogs` документы должны содержать поля version/cv/lastModifiedAt/archived; без них client получит defaults (0/0/0/false) что приемлемо для фазы
