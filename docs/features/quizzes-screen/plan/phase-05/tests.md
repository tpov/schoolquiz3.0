---
phase: 05
role: test-dev
---

# Phase-05 Test Tasks: Compose UI Screens

### Pattern Invariants

- Compose UI instrumented тесты используют `FakeXxxListComponent` с `MutableValue<UiState>` (Decompose) — не реальный DefaultXxx. Ref: `phase-05/frontend.md` (FakeQuestListComponent Signature Card); Decompose `MutableValue` — `06-api-contract.md:629`.
- Изолированный тест без nav stack: компонент помещается в `composeTestRule.setContent { Screen(fakeComponent) }`. Ref: `.claude/rules/testing.md` (Compose UI Test framework).
- Preferred assertions — по семантическому тексту или test tag; interaction через tap/long-press action. Тесты не проверяют pixel-perfect layout — только семантику (isDisplayed, isEnabled, hasClickAction).
- `ActivityScenario` для ROT-UI-01 (rotation) — instrumented, требует подключённого устройства. Ref: `04-testing.md §9.6` (ROT-UI-01..02).

---

## Create QuestListScreenTest

- **Файл:** `android/feature/quizzes-screen/presentation/src/androidTest/kotlin/.../screen/QuestListScreenTest.kt`
- **Тип:** Compose UI instrumented test (`@RunWith(AndroidJUnit4::class)`)
- **Сигнатура:** `@RunWith(AndroidJUnit4::class) class QuestListScreenTest`
- **Вход:** `FakeQuestListComponent` с различными начальными states
- **Поведение / Выход (из `04-testing.md §9.4`):**

  **QLS-UI-01**: `Loading state shows progress indicator`
  - given: fake component with state `QuestListUiState.Loading`
  - when: screen rendered
  - then: a CircularProgressIndicator (or node tagged "loading_indicator") is visible in the center

  **QLS-UI-02**: `Loaded state shows quest items`
  - given: fake component with state `Loaded` containing two quests — `quest1` and `quest2`
  - when: screen rendered
  - then: text matching `quest1.title` is visible; text matching `quest2.title` is visible

  **QLS-UI-03**: `Empty state shows placeholder`
  - given: fake component with state `QuestListUiState.Empty`
  - when: screen rendered
  - then: text "Нет квестов" is visible

  **QLS-UI-04**: `tap on quest fires onQuestClick with correct item`
  - given: fake component with `Loaded` state containing one quest `questA` (title "Quest A")
  - when: user taps on the node with text "Quest A"
  - then: `fakeComponent.onQuestClickCalled` equals `questA`

  **QLS-UI-05**: `BreadcrumbBar renders with component.titles`
  - given: fake component with `titles = listOf("Math")`
  - when: screen rendered
  - then: text "Math" is visible in the BreadcrumbBar area

- **Edge cases:**
  - `Empty` state — verify нет LazyColumn crash с 0 items
  - `Loaded` с 1 элементом — только он виден
- **Depends on:** `QuestListScreen`, `FakeQuestListComponent`, `QuestListUiState`, Compose UI Test
- **Canonical reference:** `04-testing.md §9.4`
- **Rationale:** AC#13 (display quests from catalog), AC#2 (loading state), AC#3 (empty state) UI coverage.

---

## Create SectionListScreenTest

- **Файл:** `android/feature/quizzes-screen/presentation/src/androidTest/kotlin/.../screen/SectionListScreenTest.kt`
- **Тип:** Compose UI instrumented test
- **Сигнатура:** `@RunWith(AndroidJUnit4::class) class SectionListScreenTest`
- **Вход:** `FakeSectionListComponent` с различными states
- **Поведение / Выход:**

  **SLS-UI-01**: `Loading state shows progress indicator`
  - given: fake component with state `HierarchyListUiState.Loading`
  - when: screen rendered
  - then: progress indicator is visible

  **SLS-UI-02**: `Loaded state shows section items`
  - given: fake component with state `Loaded` containing `sectionItem`
  - when: screen rendered
  - then: text matching `sectionItem.title` is visible

  **SLS-UI-03**: `Empty state shows levelLabel`
  - given: fake component with state `Empty("Нет секций")`
  - when: screen rendered
  - then: text "Нет секций" is visible

  **SLS-UI-04**: `tap section item fires onSectionClick`
  - given: fake component with `Loaded` state containing `HierarchyItemUi(id="s-1", title="Section A")`
  - when: user taps the node with text "Section A"
  - then: `fakeComponent.onSectionClickCalled.id` equals "s-1"

- **Edge cases:**
  - BreadcrumbBar with multiple titles — verify all visible
- **Depends on:** `SectionListScreen`, `FakeSectionListComponent`, `HierarchyListUiState`
- **Canonical reference:** `04-testing.md §9` (analogous pattern)
- **Rationale:** AC#16 (drill into sections) UI coverage.

---

## Create LessonPlaceholderScreenTest

- **Файл:** `android/feature/quizzes-screen/presentation/src/androidTest/kotlin/.../screen/LessonPlaceholderScreenTest.kt`
- **Тип:** Compose UI instrumented test
- **Сигнатура:** `@RunWith(AndroidJUnit4::class) class LessonPlaceholderScreenTest`
- **Вход:** `DefaultLessonPlaceholderComponent` с test config (или minimal fake)
- **Поведение / Выход:**

  **LP-SC-01**: `lessonTitle is displayed`
  - given: component with `uiState.lessonTitle = "Algebra Basics"`
  - when: screen rendered
  - then: text "Algebra Basics" is visible

  **LP-SC-02**: `placeholder text is displayed`
  - then: node containing "будет добавлено позже" is visible

  **LP-SC-03**: `BreadcrumbBar titles rendered`
  - given: `uiState.titles = listOf("Math", "Quest 1", "Section 1", "Theme 1", "Algebra Basics")`
  - then: all 5 titles render; last is not clickable

- **Edge cases:**
  - `lessonTitle` в breadcrumb — это `titles.last()` AND `uiState.lessonTitle` — оба одинаковы; один из тестов верифицирует что lessonTitle из uiState совпадает с последним breadcrumb сегментом
- **Depends on:** `LessonPlaceholderScreen`, `LessonPlaceholderComponent` (real or fake), `LessonPlaceholderUiState`
- **Canonical reference:** `04-testing.md §9` (LP screen tests)
- **Rationale:** AC#29 (placeholder текст), AC#8 (breadcrumb в placeholder screen).

---

## Create QuizzesRotationTest (instrumented — AC#22)

- **Файл:** `android/feature/quizzes-screen/presentation/src/androidTest/kotlin/.../screen/QuizzesRotationTest.kt`
- **Тип:** Instrumented test (`ActivityScenario.recreate()` = configuration change)
- **Сигнатура:** `@RunWith(AndroidJUnit4::class) class QuizzesRotationTest`
- **Вход:** Full Activity with `DefaultQuizzesComponent` + fake repositories
- **Поведение / Выход (из `04-testing.md §9.6`):**

  **ROT-UI-01**: `after recreate() active child config is preserved`
  - given: Activity + DefaultQuizzesComponent созданы; `openQuestList(...)` вызван → active is QuestList
  - when: `scenario.recreate()` (configuration change)
  - then: после recreate, `component.childStack.value.active.configuration is QuizzesConfig.QuestList`

  **ROT-UI-02**: `after recreate() LazyColumn scroll position is preserved` (optional — complex)
  - given: QuestListScreen с 20+ квестами; пользователь прокрутил вниз
  - when: `scenario.recreate()`
  - then: scroll offset не сбрасывается (проверяется через `LazyListState.firstVisibleItemIndex`)

- **Edge cases:**
  - ROT-UI-02 — требует `rememberLazyListState()` в QuestListScreen (Phase-05 обязан добавить). Если не добавлен — тест падает.
  - `ActivityScenario.recreate()` симулирует configuration change (поворот), не process death.
- **Depends on:** `QuizzesScreen`, `DefaultQuizzesComponent`, `ActivityScenario`, fake repositories, Decompose
- **Canonical reference:** `04-testing.md §9.6`
- **Rationale:** AC#22 (restore after rotation). Configuration change — простейший тест restore. Process death — ручной сценарий (per `04-testing.md §12`).

---

## Create QuizzesRootIntegrationTest

- **Файл:** `android/feature/quizzes-screen/presentation/src/test/kotlin/.../integration/QuizzesRootIntegrationTest.kt`
- **Тип:** JVM unit test (JUnit 4 + DefaultComponentContext)
- **Сигнатура:** `class QuizzesRootIntegrationTest`
- **Вход:** `DefaultQuizzesComponent` + fake repositories + `LifecycleRegistry` + `FakeBackDispatcher`
- **Поведение / Выход (из `04-testing.md §10`):**

  **INT-01**: `HomeQuestsComponent.onCatalogDrillDown → QuizzesComponent receives pushNew(QuestList)`
  - given: `DefaultQuizzesComponent` с fakes
  - when: `component.openQuestList(CatalogId("cat-1"), "Mathematics")`
  - then: `component.childStack.value.active.instance is QuizzesChild.QuestList`

  **INT-02**: `MyQuestsComponent.onQuestDrillDown → QuizzesComponent receives pushNew(SectionList)`
  - when: `component.openSectionList(QuestId("q-1"), listOf("Math", "Quest A"))`
  - then: `component.childStack.value.active.instance is QuizzesChild.SectionList`

  **INT-03**: `QuizzesComponent.dismissQuizzes → stack=[Idle]`
  - given: stack=[Idle, QuestList]
  - when: `component.dismissQuizzes()`
  - then: `component.childStack.value.active.instance is QuizzesChild.Idle`

  **INT-04**: `Root backCallback lower priority than QuizzesComponent backCallback`
  - given: component зарегистрирован с PRIORITY_OVERLAY backCallback
  - then: при двух зарегистрированных callback — QuizzesComponent callback (priority >= 100) выше default (priority = 0)
  - verify: `component` backCallback priority > DefaultBackDispatcher.PRIORITY_DEFAULT

  **INT-05**: `QuizzesComponent backCallback disabled when Idle → Root backCallback handles back`
  - given: stack = [Idle]; component backCallback.isEnabled = false
  - then: back dispatch не обрабатывается QuizzesComponent; root-level handler получает event

- **Edge cases:**
  - INT-04: если `BackCallback.PRIORITY_OVERLAY` не определён в Essenty 2.x → verify что explicit `priority = 100` > default
- **Depends on:** `DefaultQuizzesComponent`, fake repositories, `DefaultComponentContext`, `LifecycleRegistry`, Decompose back handling
- **Canonical reference:** `04-testing.md §10`
- **Rationale:** INT-01..05 cross-layer integration tests; AC#4 (back behavior), AC#5 (back to HomeQuests).
