---
phase: 07
role: test-dev
---

# Phase-07 Test Tasks: Cross-Feature Wiring

### Pattern Invariants

- `DefaultHomeQuestsComponentTest` и `DefaultMyQuestsComponentTest` — JVM unit tests с fake lambdas (не реальный QuizzesComponent). Ref: `.claude/rules/testing.md` (Fakes convention).
- `StubMyQuestsComponent` — обязан добавить `override fun onQuestClick(quest: QuestDisplayItem) { /* stub */ }` после interface extension. Ref: `06-api-contract.md:199` (MyQuestsComponent interface).
- Bidirectional import check — запускается как validation grep команда в `phase-07/overview.md` Validation section; тест-девелопер верифицирует grep output пустой. Ref: `.claude/rules/clean-architecture.md:62-66`.
- `QuizzesRootIntegrationTest` из Phase-05 — расширить INT-01..05 с реальной `DefaultRootComponent` wiring. Ref: `04-testing.md §10` (INT test definitions).

---

## Update DefaultHomeQuestsComponentTest — onCatalogClick activation

- **Файл:** `android/feature/quest/presentation/src/test/kotlin/.../component/DefaultHomeQuestsComponentTest.kt`
- **Тип:** JVM unit test (modification — добавить test cases)
- **Сигнатура:** existing `class DefaultHomeQuestsComponentTest`
- **Вход:** `DefaultHomeQuestsComponent` с fake `onCatalogDrillDown` lambda + fake catalogs in state
- **Поведение / Выход:**

  **HC-U-01**: `when onCatalogClick called then onCatalogDrillDown lambda invoked`
  - given: `var capturedId: CatalogId? = null; var capturedName: String? = null`
  - given: `onCatalogDrillDown = { id, name -> capturedId = id; capturedName = name }`
  - given: `state.catalogs = listOf(Catalog(id=CatalogId("cat-1"), name="Mathematics"))`
  - when: `component.onCatalogClick(CatalogId("cat-1"))`
  - then: `capturedId == CatalogId("cat-1")`; `capturedName == "Mathematics"`

  **HC-U-02**: `when onCatalogClick called with unknown catalogId then catalogName is empty`
  - given: `state.catalogs = emptyList()` или catalog list не содержит `cat-unknown`
  - when: `component.onCatalogClick(CatalogId("cat-unknown"))`
  - then: lambda invoked; `capturedName == ""`

  **HC-U-03**: `onCatalogClick does not invoke old Navigator-based behavior`
  - given: Phase-07 removed Navigator navigation from onCatalogClick
  - then: `onCatalogDrillDown` lambda — единственная side effect (нет Navigator.navigate calls)

- **Edge cases:**
  - Verify `DefaultHomeQuestsComponent` не выполняет двойной вызов lambda при onCatalogClick
- **Depends on:** `DefaultHomeQuestsComponent`, fake `onCatalogDrillDown` lambda, `CatalogId`
- **Canonical reference:** `04-testing.md §10` (INT test context), Problem 2 (2-grounding.md)
- **Rationale:** AC#1 (HomeQuests → openQuestList), Problem 2 fix validation.

---

## Update DefaultMyQuestsComponentTest — onQuestClick + catalogName resolve

- **Файл:** `android/feature/quest/presentation/src/test/kotlin/.../component/DefaultMyQuestsComponentTest.kt`
- **Тип:** JVM unit test (modification — добавить test cases)
- **Сигнатура:** existing `class DefaultMyQuestsComponentTest`
- **Вход:** `DefaultMyQuestsComponent` с fake `onQuestDrillDown` lambda
- **Поведение / Выход:**

  **MC-U-01**: `when onQuestClick called then onQuestDrillDown lambda invoked with quest`
  - given: `var capturedQuest: QuestDisplayItem? = null`
  - given: `onQuestDrillDown = { quest -> capturedQuest = quest }`
  - given: `quest = QuestDisplayItem(id=QuestId("q-1"), catalogId=CatalogId("cat-1"), title="Quest A", ...)`
  - when: `component.onQuestClick(quest)`
  - then: `capturedQuest == quest`

  **MC-U-02**: `onQuestClick passes entire QuestDisplayItem (including catalogId) to lambda`
  - given: quest with `catalogId = CatalogId("cat-2")`
  - when: `component.onQuestClick(quest)`
  - then: `capturedQuest?.catalogId == CatalogId("cat-2")`

- **Edge cases:**
  - Verify: `DefaultMyQuestsComponent.onQuestClick` не resolves `catalogName` itself — это DefaultRootComponent lambda responsibility. Тест confirms `capturedQuest` = original quest объект, не обогащённый.
- **Depends on:** `DefaultMyQuestsComponent`, fake `onQuestDrillDown` lambda, `QuestDisplayItem`
- **Canonical reference:** `04-testing.md §10` (INT context), Problem 3 (2-grounding.md)
- **Rationale:** AC#7 (MyQuests → openSectionList), Problem 3 fix validation.

---

## Update StubMyQuestsComponent — add onQuestClick override

- **Файл:** Все файлы содержащие `StubMyQuestsComponent` или test-double реализацию `MyQuestsComponent`
- **Тип:** test stub update (bulk — compile fix)
- **Сигнатура:** existing stub classes
- **Вход:** N/A
- **Поведение / Выход:**
  - Добавить в каждый stub: `override fun onQuestClick(quest: QuestDisplayItem) { /* stub */ }`
  - Compile fix — required после добавления метода в interface
- **Edge cases:**
  - Найти все реализации `MyQuestsComponent` через `grep -rn "MyQuestsComponent" android/ --include="*.kt"` — verify все обновлены
- **Depends on:** `MyQuestsComponent` interface (updated Phase-07 frontend)
- **Canonical reference:** `06-api-contract.md:199`
- **Rationale:** Compile fix — нельзя пропускать.

---

## Create DefaultRootComponentWiringTest

- **Файл:** `android/feature/app-shell/presentation/src/test/kotlin/.../component/DefaultRootComponentWiringTest.kt`
- **Тип:** JVM unit test (JUnit 4 + DefaultComponentContext + fake repositories)
- **Сигнатура:** `class DefaultRootComponentWiringTest`
- **Вход:** `DefaultRootComponent` с fake repositories + fake lambdas
- **Поведение / Выход:**

  **WIRE-01**: `HomeQuestsComponent.onCatalogClick triggers QuizzesComponent.openQuestList`
  - given: DefaultRootComponent создан с реальными sub-components (или fakes)
  - when: `rootComponent.homeQuestsComponent.onCatalogClick(CatalogId("cat-1"))`
  - then: `rootComponent.quizzesComponent.childStack.value.active.instance is QuizzesChild.QuestList`

  **WIRE-02**: `MyQuestsComponent.onQuestClick triggers QuizzesComponent.openSectionList`
  - given: catalogs loaded in homeQuestsComponent.state
  - when: `rootComponent.myQuestsComponent.onQuestClick(quest)`
  - then: `rootComponent.quizzesComponent.childStack.value.active.instance is QuizzesChild.SectionList`

  **WIRE-03**: `catalogName in SectionList config resolved from homeQuestsComponent catalogs`
  - given: homeQuestsComponent state has catalog `CatalogId("cat-1") → name = "Mathematics"`; quest has `catalogId = CatalogId("cat-1")`
  - when: `myQuestsComponent.onQuestClick(quest)`
  - then: `(active.configuration as QuizzesConfig.SectionList).titles[0] == "Mathematics"`

  **WIRE-04**: `dismissQuizzes makes overlay hidden`
  - given: overlay shown (active is QuestList)
  - when: `rootComponent.quizzesComponent.dismissQuizzes()`
  - then: `rootComponent.quizzesComponent.childStack.value.active.instance is QuizzesChild.Idle`

  **WIRE-05**: `bidirectional import check (structural — verify via grep in validation, not runtime)`
  - Note: этот тест — validation grep (в Validation section overview.md), не JVM test. JVM тест не нужен для import check.

- **Edge cases:**
  - `WIRE-03`: если catalogs пустые → titles[0] == "" (fallback); assert non-crash
  - `DefaultRootComponent` wiring test — самый сложный в фазе; может потребовать TestKoin setup если DefaultRootComponent использует Koin internally
- **Depends on:** `DefaultRootComponent`, all sub-components, fake repositories, `DefaultComponentContext`
- **Canonical reference:** `04-testing.md §10` (INT-01..05)
- **Rationale:** End-to-end wiring verification без instrumentation; самый важный test в Phase-07.
