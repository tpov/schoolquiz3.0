---
phase: 01
role: test-dev
---

# Phase-01 Test Tasks: Data Layer Extension

### Pattern Invariants

- Fakes: in-memory filter должен точно имитировать delimiter-wrapped LIKE pattern из DAO — exact element match (не substring).
- `archived` fakes: квесты с `archived = true` НЕ должны появляться в результатах `observeByCatalog`.
- Flow assertions: `.take(1).toList()` или `.value` — Turbine не используется.
- Все три FakeQuestRepository обновляются в едином PR.

---

## Update FakeQuestRepository (domain commonTest)

- **Файл:** `shared/feature/quest/domain/src/commonTest/kotlin/.../fake/FakeQuestRepository.kt`
- **Тип:** class (additive override)
- **Сигнатура:** `override fun observeByCatalog(catalogId: CatalogId, shelf: String): Flow<List<Quest>>`
- **Вход:** `catalogId: CatalogId`, `shelf: String`
- **Поведение / Выход:**
  - Возвращает `Flow<List<Quest>>` из in-memory backing store
  - Фильтрует: `quest.catalogId == catalogId AND quest.visibleOn.contains(shelf) AND !quest.archived`
  - Реактивен: при изменении backing store re-emits (через `MutableStateFlow` или `Channel`)
- **Edge cases:**
  - Нет matching квестов → `emptyList()` (не null)
  - `archived = true` → не включается
- **Depends on:** Существующая backing структура FakeQuestRepository
- **Canonical reference:** `06-api-contract.md:123`
- **Rationale:** Interface change требует обновления всех implementors. Fake должен точно воспроизводить семантику реального метода.

---

## Update FakeQuestRepository (quest/presentation test)

- **Файл:** `android/feature/quest/presentation/src/test/kotlin/.../fake/FakeQuestRepository.kt`
- **Тип:** class (additive override)
- **Сигнатура:** `override fun observeByCatalog(catalogId: CatalogId, shelf: String): Flow<List<Quest>>`
- **Вход:** `catalogId: CatalogId`, `shelf: String`
- **Поведение / Выход:**
  - Идентично domain fake — in-memory filter, реактивный Flow
- **Edge cases:**
  - Те же что у domain fake
- **Depends on:** Backing state этого fake
- **Canonical reference:** `06-api-contract.md:123`
- **Rationale:** Вторая копия fake — compile-break без обновления.

---

## Update FakeQuestRepository (sync module test)

- **Файл:** `shared/core/sync/src/test/kotlin/.../FakeQuestRepository.kt`
- **Тип:** class (additive override)
- **Сигнатура:** `override fun observeByCatalog(catalogId: CatalogId, shelf: String): Flow<List<Quest>>`
- **Вход:** `catalogId: CatalogId`, `shelf: String`
- **Поведение / Выход:**
  - Минимальная реализация (sync module не использует этот метод) — возвращает `flowOf(emptyList())`
  - Если backing state есть — фильтрует как другие fakes
- **Edge cases:**
  - Не используется sync логикой — stub implementation приемлема
- **Depends on:** Backing state sync fake
- **Canonical reference:** `06-api-contract.md:123`
- **Rationale:** Третья копия fake — compile-break без обновления.

---

## Create FakeQuestRepositoryObserveByCatalogTest

- **Файл:** `shared/feature/quest/domain/src/commonTest/kotlin/.../FakeQuestRepositoryObserveByCatalogTest.kt`
- **Тип:** JVM unit test (JUnit 4)
- **Сигнатура:** `class FakeQuestRepositoryObserveByCatalogTest`
- **Вход:** FakeQuestRepository с тестовыми квестами
- **Поведение / Выход:**
  - Тест-сценарии (4 теста, из `04-testing.md §11.1`):
  - `RX-01`: given quest in catalogId="A", when observeByCatalog catalogId="B", then Flow emits `[]`
  - `RX-02`: given quests in catalogs A and B, when observeByCatalog catalogId="A", then only A's quests returned
  - `RX-03`: given backing data changes (add quest to catalog), then Flow re-emits updated list
  - `RX-04`: given `archived = true` quest in catalog, when observe, then not in result
  - Дополнительно: `shelf exact match` — quest с visibleOn="homeExtra" при shelf="home" NOT returned; quest с visibleOn="home" при shelf="home" returned
- **Edge cases:**
  - Нет квестов в backing store → пустой список
  - Все квесты archived → пустой список
- **Depends on:** `FakeQuestRepository` (domain commonTest), `Quest` domain model, coroutines-test
- **Canonical reference:** `04-testing.md §11.1` (RX-01..04)
- **Rationale:** Верифицирует что fake корректно имитирует реальный DAO — фундамент для component тестов в Phase-03/04.

---

## Create QuestDaoByCatalogTest (instrumented)

- **Файл:** `shared/core/persistence/src/androidTest/kotlin/.../dao/QuestDaoByCatalogTest.kt`
  - Если androidTest директория отсутствует в persistence — создать. Verify путь с backend-dev.
- **Тип:** AndroidJUnit4 + Room in-memory database
- **Сигнатура:** `@RunWith(AndroidJUnit4::class) class QuestDaoByCatalogTest`
- **Вход:** Room in-memory database с `QuestDao`
- **Поведение / Выход:**
  - `DAO-01`: given quests in catalog A and B, when observeByCatalog(A, "home"), then only A's non-archived public quests returned
  - `DAO-02`: given quest with archived=1 in catalog, when observeByCatalog, then not in result
  - `DAO-03`: given empty table, when observeByCatalog, then Flow emits empty list (no exception)
  - `DAO-04`: given initial empty result, when insert new quest matching filter, then Flow re-emits with new quest
  - `delimiter exact match`: given quest with visibleOn joined as "CHAR(31)homeExtra CHAR(31)", when observe shelf="home", then NOT in result (exact match, not substring)
- **Edge cases:**
  - Malformed visibleOn — пустая строка → не crashing
- **Depends on:** Room in-memory builder, `QuestEntity`, `StringSetConverter`, `QuestDao`
- **Canonical reference:** `04-testing.md §11.2` (DAO-01..04)
- **Rationale:** DAO instrumented тест верифицирует SQL правильность — критично для LIKE-based фильтрации, которую нельзя проверить JVM тестом.

---

## Test Infrastructure Notes

- `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries()` для DAO tests.
- `runTest` + `StandardTestDispatcher` для Flow assertions в JVM unit тестах.
- `.take(1).toList()` для single-emit Flow verification.
- Для reactive (multi-emit) Flow tests: emit initial state, verify, emit updated state, verify `.take(2).toList()[1]`.
