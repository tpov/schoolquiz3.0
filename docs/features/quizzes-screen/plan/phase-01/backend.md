---
phase: 01
role: backend-dev
---

# Phase-01 Backend Tasks: Data Layer Extension

### Pattern Invariants

- Delimiter-wrapped LIKE ОБЯЗАН использоваться в DAO: `(CHAR(31) || visibleOn || CHAR(31)) LIKE (...)` — как в `QuestDao.kt:28-34`.
- `archived = 0` фильтр ДОЛЖЕН быть в SQL WHERE, не в Kotlin-коде.
- Mapper chain: Entity → RepositoryImpl.map → Domain. Никакого Entity за пределами data layer.
- Все 3 FakeQuestRepository в одном PR — compile safety.

---

## Add `observeByCatalog` to QuestRepository

- **Файл:** `shared/feature/quest/domain/src/commonMain/kotlin/.../repository/QuestRepository.kt`
- **Тип:** interface (additive method)
- **Сигнатура:** `fun observeByCatalog(catalogId: CatalogId, shelf: String): Flow<List<Quest>>`
- **Вход:** `catalogId: CatalogId` — фильтр по каталогу; `shelf: String` — shelf-label для visibleOn match (вызывающие передают `"home"`)
- **Поведение / Выход:**
  - Возвращает `Flow<List<Quest>>` — реактивный поток публичных квестов данного каталога
  - Фильтр: `catalogId == catalogId AND visibleOn contains shelf AND archived == false`
  - Sort: `lastModifiedAt DESC` (Quest не имеет поля `order` — User Decision Q1)
  - Эмитит пустой список если нет matching квестов (не null, не error)
  - Re-emits при изменениях Room
- **Edge cases:**
  - `catalogId` без matching квестов → `Flow<List<Quest>>` emits `emptyList()`
  - `shelf` не совпадает ни с одним квестом → `emptyList()`
  - Archived квесты исключаются автоматически через `archived = 0` в DAO
- **Depends on:** `CatalogId` (shared/core/catalog/domain), `Quest` (domain model), `Flow` (kotlinx.coroutines)
- **Canonical reference:** `06-api-contract.md:123`
- **Rationale:** Additive extension к существующему interface. HomeQuests drill-down требует выборки публичных квестов конкретного каталога — паттерн отличается от `observeByShelf` (нет catalogId фильтра) и `observeMyQuests` (нет visibleOn фильтра).

---

## Add `observeByCatalog` to QuestLocalDataSource

- **Файл:** `shared/feature/quest/data/src/commonMain/kotlin/.../source/QuestLocalDataSource.kt`
- **Тип:** class/interface method (additive, verify структуру — может быть конкретный класс без interface)
- **Сигнатура:** `fun observeByCatalog(catalogId: String, shelf: String): Flow<List<QuestEntity>>`
- **Вход:** `catalogId: String` — raw String (не value class, data layer не зависит от domain value types в параметрах DAO); `shelf: String`
- **Поведение / Выход:**
  - Делегирует в `QuestDao.observeByCatalog(catalogId, shelf)`
  - Возвращает `Flow<List<QuestEntity>>` — raw Room entities
- **Edge cases:**
  - DAO возвращает пустой список — пробрасывается как есть
- **Depends on:** `QuestDao`, `QuestEntity` (data layer)
- **Canonical reference:** `06-api-contract.md:123` (impl details)
- **Rationale:** Слой local data source изолирует DAO от repository impl. Принимает raw String, не `CatalogId` value class — data layer не должен зависеть от domain value types.

---

## Add `observeByCatalog` to QuestRepositoryImpl

- **Файл:** `shared/feature/quest/data/src/commonMain/kotlin/.../repository/QuestRepositoryImpl.kt`
- **Тип:** class (override method)
- **Сигнатура:** `override fun observeByCatalog(catalogId: CatalogId, shelf: String): Flow<List<Quest>>`
- **Вход:** `catalogId: CatalogId` (domain value type), `shelf: String`
- **Поведение / Выход:**
  - Вызывает `localDataSource.observeByCatalog(catalogId.value, shelf)` — извлекает raw String из value class
  - Маппит через `.map { entities -> entities.map { it.toDomain() } }` (existing mapper extension)
  - Возвращает `Flow<List<Quest>>`
- **Edge cases:**
  - Пустой список entities → пустой список Quest (не null)
- **Depends on:** `QuestLocalDataSource`, `QuestEntity.toDomain()` mapper, `CatalogId.value`
- **Canonical reference:** `06-api-contract.md:123`
- **Rationale:** Repository impl — единственный код который знает о маппинге Entity→Domain. Передача `catalogId.value` соблюдает data layer boundary.

---

## Add `observeByCatalog` to QuestDao

- **Файл:** `shared/core/persistence/src/main/kotlin/.../dao/QuestDao.kt`
- **Тип:** interface (Room DAO, additive `@Query` method)
- **Сигнатура:** `@Query(...) fun observeByCatalog(catalogId: String, shelf: String): Flow<List<QuestEntity>>`
- **Вход:** `catalogId: String` — DAO параметр; `shelf: String` — для LIKE pattern
- **Поведение / Выход:**
  - SQL WHERE: `catalogId = :catalogId AND (CHAR(31) || visibleOn || CHAR(31)) LIKE ('%' || CHAR(31) || :shelf || CHAR(31) || '%') AND archived = 0`
  - ORDER BY: `lastModifiedAt DESC`
  - Returns: `Flow<List<QuestEntity>>`
  - Room auto-invalidates при write в quests table
- **Edge cases:**
  - Нет rows → `Flow` emits `emptyList()`
  - shelf = "home" при visibleOn = "homeExtra" → NOT matched (delimiter-wrapped exact match)
  - shelf = "home" при visibleOn = "home" или "home|arena" → matched
- **Depends on:** `QuestEntity`, Room `@Query`, `StringSetConverter` (existing visibleOn storage — CHAR(31) delimiter)
- **Canonical reference:** `06-api-contract.md:123` (DAO pattern section)
- **Rationale:** Exact-element match через delimiter-wrapped LIKE — единственный надёжный способ проверки membership в CHAR(31)-разделённом Set, уже используется в `QuestDao.kt:30` для `observeByShelf`.
