---
phase: 01
name: Data Layer Extension — QuestRepository.observeByCatalog
layer: data (shared)
status: ready
---

# Phase-01: Data Layer Extension

## Goal

Добавить `QuestRepository.observeByCatalog(catalogId, shelf)` в shared domain interface и реализовать в data layer. Обновить все три копии `FakeQuestRepository` и `FakeQuestLocalDataSource`. Это единственный новый метод data layer, требуемый всей фичей quizzes-screen.

## Scope

Только `shared/feature/quest/` — domain interface, data implementation, DAO, fakes. Никакого presentation кода, никаких UI изменений.

## Role Inputs

- `backend.md` — backend-dev
- `tests.md` — test-dev

`frontend.md` — не создаётся: фаза не затрагивает UI/presentation.

## Layer

**data** (shared KMP) — `shared/feature/quest/domain/` + `shared/feature/quest/data/` + `shared/core/persistence/`.

## Review Tags

`data-layer`, `kmp-shared`, `room-query`

## State Matrix Coverage

Matrix rows: не применимо (эта фаза — data layer, без UI states). Матрицы 1/2/3 из `02-behavior.md` покрываются в фазах 03–07 (presentation + UI).

## Domain Contract Coverage

Feature Domain Contract = N/A (spec). Эта фаза реализует **Data/Repository Contract** — additive DAO query без schema migration. Canonical contract в `06-api-contract.md:123`.

## Traceability

| Problem (from 2-grounding.md) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|-------------------------------|------------|--------------|-----------------|--------------|------------|
| **Problem 4**: `QuestRepository.observeByCatalog` data layer addition | backend-dev (`shared/feature/quest/`) | `DefaultQuestListComponent.init` (Phase-04) будет первым вызывающим | Additive query, no Room migration; CHAR(31) delimiter-wrapped LIKE pattern (per `QuestDao.kt:30`) | Добавить метод в interface + `QuestLocalDataSource` + `QuestRepositoryImpl` + `QuestDao`; обновить 3 fakes | DAO instrumented test + `FakeQuestRepositoryObserveByCatalogTest` |

## New Files

| File | Owner | Note |
|------|-------|------|
| (нет новых файлов) | — | Все изменения — additive к существующим файлам |

## Modified Files

| File | Owner | Change |
|------|-------|--------|
| `shared/feature/quest/domain/src/commonMain/.../repository/QuestRepository.kt` | backend-dev | `+fun observeByCatalog(catalogId: CatalogId, shelf: String): Flow<List<Quest>>` |
| `shared/feature/quest/data/src/commonMain/.../source/QuestLocalDataSource.kt` | backend-dev | `+fun observeByCatalog(catalogId: String, shelf: String): Flow<List<QuestEntity>>` |
| `shared/feature/quest/data/src/commonMain/.../repository/QuestRepositoryImpl.kt` | backend-dev | `+override fun observeByCatalog(...)` — delegates to localDataSource, maps Entity→Domain |
| `shared/core/persistence/src/main/.../dao/QuestDao.kt` | backend-dev | `+@Query fun observeByCatalog(catalogId: String, shelf: String): Flow<List<QuestEntity>>` |
| `shared/feature/quest/domain/src/commonTest/.../fake/FakeQuestRepository.kt` | test-dev | `+override fun observeByCatalog(...)` — in-memory filter |
| `android/feature/quest/presentation/src/test/.../fake/FakeQuestRepository.kt` | test-dev | `+override fun observeByCatalog(...)` — in-memory filter |
| `shared/core/sync/.../FakeQuestRepository.kt` | test-dev | `+override fun observeByCatalog(...)` — in-memory filter |

## Deleted Files

none

## Dependencies

Ни от каких других фаз не зависит. Может выполняться параллельно с Phase-02.

## Acceptance Criteria

1. `QuestRepository.kt` содержит метод `observeByCatalog` с правильной KDoc-документацией (filter, sort, DAO pattern).
2. `QuestDao.kt` содержит `@Query` с delimiter-wrapped LIKE pattern идентичным `observeByShelf` (CHAR(31) pattern, `archived = 0`, `ORDER BY lastModifiedAt DESC`).
3. `QuestRepositoryImpl.kt` делегирует в `localDataSource.observeByCatalog` и маппит `List<QuestEntity>` → `List<Quest>`.
4. Все три `FakeQuestRepository` компилируются с новым методом; in-memory фильтрация корректна (catalogId match + visibleOn contains shelf + archived=false).
5. DAO instrumented тест (`QuestDaoByCatalogTest`) зелёный: DAO-01..04 из `04-testing.md §11.2`.
6. JVM тест фейков (`FakeQuestRepositoryObserveByCatalogTest`) зелёный: RX-01..04 из `04-testing.md §11.1`.
7. `./gradlew allTests --no-configuration-cache` — зелёный.
8. `./gradlew assemble --no-configuration-cache` — зелёный.

## Tests Required

Пишутся параллельно с production code (TDD):

**FakeQuestRepositoryObserveByCatalogTest (JVM unit)**:
- `when unknown catalogId then emits empty list`: given FakeQuestRepository with quest in catalogId="A", when observe catalogId="B", then Flow emits `[]`
- `when known catalogId then emits matching quests only`: given quests in catalogs A and B, when observe catalog A, then only A's quests returned
- `when backing data changes then flow re-emits`: given loaded quest in catalog, when FakeRepository adds another quest to same catalog, then Flow emits updated list
- `when quest is archived then excluded`: given quest with archived=true in catalog, when observe, then not in result

**QuestDaoByCatalogTest (instrumented, Room in-memory)**:
- DAO-01: `quests with matching catalogId returned`
- DAO-02: `archived=1 quests excluded from result`
- DAO-03: `empty table emits empty list, no error`
- DAO-04: `insert new quest re-emits via Flow`
- `delimiter-wrapped LIKE exact match`: given quest with visibleOn="home", when observe shelf="home", then returned; given quest with visibleOn="homeExtra", when observe shelf="home", then NOT returned (exact match, not substring)

## Pattern Invariants

1. Delimiter-wrapped LIKE pattern: DAO query ОБЯЗАН использовать `(CHAR(31) || visibleOn || CHAR(31)) LIKE ('%' || CHAR(31) || :shelf || CHAR(31) || '%')` — идентично `QuestDao.kt:28-34`. Любой другой LIKE pattern — bug (substring false positive).
2. `archived = 0` filter ОБЯЗАН быть в DAO query — не в QuestRepositoryImpl, не в FakeQuestRepository. Соответствует паттерну всех existing DAO queries.
3. `ORDER BY lastModifiedAt DESC` — `Quest` не имеет поля `order` (verified `Quest.kt:30-93`). Не `order ASC`.
4. Mapper chain: `QuestEntity → QuestRepositoryImpl.observeByCatalog mapper → Quest domain`. ViewModel/Component НИКОГДА не получает `QuestEntity` напрямую.
5. Все три FakeQuestRepository копии обновляются В ОДНОМ PR — несинхронное обновление вызывает compile failure.

## Validation

| # | Command | Expected |
|---|---------|----------|
| 1 | `./gradlew allTests --no-configuration-cache` | passes — все JVM тесты включая новые fake tests |
| 2 | `./gradlew assemble --no-configuration-cache` | passes |
| 3 | `./gradlew :shared:feature:quest:domain:jvmTest --no-configuration-cache` | passes — domain tests including FakeQuestRepositoryObserveByCatalogTest |
| 4 | `./gradlew :shared:core:sync:jvmTest --no-configuration-cache` | passes — sync module fake updated |
| 5 | `./gradlew :android:feature:quest:presentation:assembleDebugAndroidTest --no-configuration-cache` | passes (build APK for instrumented DAO test) |

## Handoff Notes

- После Phase-01 `QuestRepository.observeByCatalog` доступен для Phase-04 (`DefaultQuestListComponent`).
- Phase-02 (Designsystem) не зависит от Phase-01 — может выполняться параллельно.
- Если `QuestLocalDataSource` — конкретный класс без интерфейса — уточнить в implementation (добавить метод напрямую в impl, не создавать новый interface). Backend-dev verifies.
