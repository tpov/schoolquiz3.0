---
phase: 02
role: test-dev
---

# Phase-02 Test Tasks: Designsystem + Model

### Pattern Invariants

- BrandComponentsInvariantsTest: не нарушать — проверять через `./gradlew :android:core:designsystem:test`.
- Compose UI тесты — assertions по semantic text и test tags; interaction через tap или long-press action.
- `QuestDisplayItem` тест fixtures: добавить `catalogId = CatalogId("test-cat")` во все существующие конструкторы.

---

## Update QuestToDisplayItemTest

- **Файл:** `android/feature/quest/presentation/src/test/kotlin/.../mapper/QuestToDisplayItemTest.kt`
- **Тип:** JVM unit test (modification)
- **Сигнатура:** existing test class
- **Вход:** Quest с `catalogId = CatalogId("cat-1")`
- **Поведение / Выход:**
  - given: Quest with `catalogId = CatalogId("cat-1")`, when mapped to displayItem, then `displayItem.catalogId` equals `CatalogId("cat-1")`
  - Обновить все existing fixtures `QuestDisplayItem(...)` добавив `catalogId` параметр
- **Edge cases:**
  - N/A
- **Depends on:** `QuestToDisplayItem.kt` (Phase-02 frontend), `QuestDisplayItem.kt`
- **Canonical reference:** `06-api-contract.md:81`
- **Rationale:** Верифицирует round-trip mapper для нового поля.

---

## Create BreadcrumbBarTest (Compose UI, instrumented)

- **Файл:** `android/core/designsystem/src/androidTest/kotlin/.../components/BreadcrumbBarTest.kt`
- **Тип:** Compose UI instrumented test
- **Сигнатура:** `@RunWith(AndroidJUnit4::class) class BreadcrumbBarTest`
- **Вход:** `BreadcrumbBar` Composable с тестовыми segments
- **Поведение / Выход (тест-сценарии из `04-testing.md §9.1`):**
  - `BB-UI-01`: given titles=["Math", "Quest", "Section"], when rendered, then all three texts visible
  - `BB-UI-02`: given 3 segments, when tap last segment, then onSegmentClick NOT called
  - `BB-UI-03`: when tap segment at index 0 ("Math"), then onSegmentClick(0) called; when tap segment at index 1 ("Quest"), then onSegmentClick(1) called
  - `BB-UI-04`: given single segment, then no "›" separator visible
  - `BB-UI-05`: given segment with 50+ char title, then node shows ellipsis or truncated text
- **Edge cases:**
  - Empty titles list → no crash, nothing rendered
  - Single segment — not clickable
- **Depends on:** `BreadcrumbBar.kt` (Phase-02 frontend)
- **Canonical reference:** `04-testing.md §9.1`
- **Rationale:** AC#8, AC#9, AC#26, AC#27, AC#28 coverage — breadcrumb tap semantics critical path.

---

## Create HierarchyItemCardTest (Compose UI, instrumented)

- **Файл:** `android/core/designsystem/src/androidTest/kotlin/.../components/HierarchyItemCardTest.kt`
- **Тип:** Compose UI instrumented test
- **Сигнатура:** `@RunWith(AndroidJUnit4::class) class HierarchyItemCardTest`
- **Вход:** `HierarchyItemCard` Composable с различными param combinations
- **Поведение / Выход (из `04-testing.md §9.2`):**
  - `HI-UI-01`: given title="Algebra", when rendered, then text "Algebra" is visible
  - `HI-UI-02`: given orderLabel=null, then no "1." text present in composition
  - `HI-UI-03`: given subtitleCount=null, then no count text visible
  - `HI-UI-04`: given onClick lambda, when user taps the card, then lambda is called
  - `HI-UI-05`: given onLongClick=null, when user long-presses, then no long-click action fires
  - `HI-UI-06`: given onLongClick non-null lambda, when user long-presses, then lambda is called
- **Edge cases:**
  - `orderLabel="2."` → text "2." visible on the left
- **Depends on:** `HierarchyItemCard.kt` (Phase-02 frontend)
- **Canonical reference:** `04-testing.md §9.2`
- **Rationale:** AC#15, AC#24, AC#25 coverage.

---

## Create QuestCardLongClickTest (Compose UI, instrumented)

- **Файл:** `android/core/designsystem/src/androidTest/kotlin/.../components/QuestCardLongClickTest.kt`
  - Или добавить в существующий `QuestCardTest.kt` если он есть
- **Тип:** Compose UI instrumented test
- **Сигнатура:** `class QuestCardLongClickTest`
- **Вход:** `QuestCard` с `onLongClick = null` и с non-null lambda
- **Поведение / Выход:**
  - `existing onClick works with null onLongClick`: given QuestCard with onClick lambda and onLongClick=null, when user taps, then onClick fires
  - `onLongClick fires on long press`: given QuestCard with onLongClick lambda, when user long-presses, then lambda is called with item.id
  - `click and long-click independent`: tap fires onClick only; long-press fires onLongClick only — no interference
  - `null onLongClick no haptic semantic`: when onLongClick=null, no accessibility long-click action is present
- **Edge cases:**
  - `QuestDisplayItem` требует `catalogId` — создать fixture с `catalogId = CatalogId("test")`
- **Depends on:** `QuestCard.kt` (Phase-02 frontend), `QuestDisplayItem.kt` (with catalogId)
- **Canonical reference:** `04-testing.md §9.3` (QC-UI-* — partial; полный QC-UI-01..05 в Phase-06)
- **Rationale:** Verify backward compat + new long-press behavior; AC#10 partial (menu itself tested in Phase-06).

---

## Update test fixtures for QuestDisplayItem.catalogId

- **Файл:** Все тестовые файлы в `android/feature/quest/presentation/src/test/` содержащие `QuestDisplayItem(...)` конструкторы
- **Тип:** test fixtures update (bulk modification)
- **Сигнатура:** добавить `catalogId = CatalogId("test-cat")` в каждый конструктор
- **Поведение / Выход:**
  - Compile fix — required field
- **Edge cases:**
  - Verify что `CatalogId` уже импортирован в тестовых файлах или добавить import
- **Depends on:** `QuestDisplayItem.kt` (new field)
- **Canonical reference:** `06-api-contract.md:81`
- **Rationale:** Required field вызывает compile errors во всех existing `QuestDisplayItem(...)` constructor calls — все должны быть исправлены в этом же PR.
