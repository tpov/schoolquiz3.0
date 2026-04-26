---
phase: 04
role: test-dev
---

# Phase-04 Test Tasks: Drill-down Child Components

### Pattern Invariants

- `StandardTestDispatcher` + `advanceUntilIdle()` для всех coroutine-based assertions. Ref: `.claude/rules/testing.md` (Coroutines test patterns section).
- `DefaultComponentContext` с `LifecycleRegistry` (Essenty) для JVM unit тестов; `LifecycleRegistry.resume()` для активации lifecycle. Ref: `04-testing.md §3` (QL-U test setup).
- `FakeQuestRepository`, `FakeSectionRepository`, `FakeThemeRepository`, `FakeLessonRepository` — backing `MutableStateFlow<List<X>>` с `emit()` методом. Fakes convention: `.claude/rules/testing.md` (Fakes convention section).
- `FakeStackNavigation` из Phase-03 — используется для проверки pushNew calls в onXxxClick тестах. Ref: `phase-03/tests.md` (FakeStackNavigation definition).
- Flow assertions через `.take(N).toList()` или `.value` (no Turbine). Ref: `.claude/rules/testing.md` (NOT used: Turbine).
- Naming: Kotlin backtick style. Ref: `.claude/rules/testing.md` (Naming conventions — Preferred).

---

## Create FakeSectionRepository

- **Файл:** `android/feature/quizzes-screen/presentation/src/test/kotlin/.../fake/FakeSectionRepository.kt`
- **Тип:** test fake class
- **Сигнатура:** `class FakeSectionRepository : SectionRepository`
- **Вход:** N/A
- **Поведение / Выход:**
  - `private val _sections = MutableStateFlow<List<Section>>(emptyList())`
  - `fun emit(sections: List<Section>)` — тестовый setter
  - `override fun observeByQuest(questId: QuestId): Flow<List<Section>>` — returns `_sections`
  - Все другие SectionRepository методы — stub `TODO()` или no-op
- **Edge cases:**
  - Verify полный интерфейс `SectionRepository` — все методы должны быть override (даже как TODO stubs)
- **Depends on:** `SectionRepository`, `Section`, `QuestId`
- **Canonical reference:** `04-testing.md §1`
- **Rationale:** Нужен для DefaultSectionListComponentTest изолированно от Room.

---

## Create FakeThemeRepository

- **Файл:** `android/feature/quizzes-screen/presentation/src/test/kotlin/.../fake/FakeThemeRepository.kt`
- **Тип:** test fake class
- **Сигнатура:** `class FakeThemeRepository : ThemeRepository`
- **Вход:** N/A
- **Поведение / Выход:**
  - `private val _themes = MutableStateFlow<List<Theme>>(emptyList())`
  - `fun emit(themes: List<Theme>)`
  - `override fun observeBySection(sectionId: SectionId): Flow<List<Theme>>` — returns `_themes`
- **Edge cases:**
  - Verify ThemeRepository methods enumeration
- **Depends on:** `ThemeRepository`, `Theme`, `SectionId`
- **Canonical reference:** `04-testing.md §1`
- **Rationale:** Аналогично FakeSectionRepository.

---

## Create FakeLessonRepository

- **Файл:** `android/feature/quizzes-screen/presentation/src/test/kotlin/.../fake/FakeLessonRepository.kt`
- **Тип:** test fake class
- **Сигнатура:** `class FakeLessonRepository : LessonRepository`
- **Вход:** N/A
- **Поведение / Выход:**
  - `private val _lessons = MutableStateFlow<List<Lesson>>(emptyList())`
  - `fun emit(lessons: List<Lesson>)`
  - `override fun observeByTheme(themeId: ThemeId): Flow<List<Lesson>>` — returns `_lessons`
- **Edge cases:**
  - Verify LessonRepository interface
- **Depends on:** `LessonRepository`, `Lesson`, `ThemeId`
- **Canonical reference:** `04-testing.md §1`
- **Rationale:** Необходим для DefaultLessonListComponentTest.

---

## Create DefaultQuestListComponentTest

- **Файл:** `android/feature/quizzes-screen/presentation/src/test/kotlin/.../component/DefaultQuestListComponentTest.kt`
- **Тип:** JVM unit test (JUnit 4)
- **Сигнатура:** `class DefaultQuestListComponentTest`
- **Вход:** `DefaultQuestListComponent` с `FakeQuestRepository`, `FakeStackNavigation`, `TestComponentContext`
- **Поведение / Выход (из `04-testing.md §3`):**

  **QL-U-01**: `initial state is Loading`
  - given: компонент только создан, FakeQuestRepository не эмитил
  - when: читаем `component.state.value`
  - then: `state.value is QuestListUiState.Loading`

  **QL-U-02**: `when FakeQuestRepository emits list then state is Loaded`
  - given: компонент создан
  - when: `fakeRepo.emit(listOf(questA, questB))`; `advanceUntilIdle()`
  - then: `state.value is QuestListUiState.Loaded`; `(state.value as Loaded).quests.size == 2`

  **QL-U-03**: `when repository emits empty list then state is Empty`
  - when: `fakeRepo.emit(emptyList())`; `advanceUntilIdle()`
  - then: `state.value is QuestListUiState.Empty`

  **QL-U-05**: `onQuestClick pushes SectionList with correct config`
  - given: `FakeStackNavigation` + loaded state
  - when: `component.onQuestClick(questA)` где `questA.id = QuestId("q-1")`, `questA.title = "Quest A"`
  - then: `fakeNavigation.pushedConfigs.last()` is `QuizzesConfig.SectionList(questId="q-1", titles=[...,"Quest A"])`

  **QL-U-06**: `onQuestClick breadcrumb titles include quest.title as last element`
  - given: component created with `titles = ["Math"]`
  - when: `component.onQuestClick(quest)`
  - then: pushed config `titles = listOf("Math", quest.title)`

  **QL-U-07**: `breadcrumb titles[0] in pushed config equals original catalogName`
  - given: component with `titles = listOf("Mathematics")`
  - when: `onQuestClick(quest)`
  - then: `pushedConfig.titles[0] == "Mathematics"`

  **QL-U-10**: `titles in QuestList config unchanged after repository emits renamed quest`
  - given: component created with `titles = listOf("Original Name")`
  - when: `fakeRepo.emit(listOf(questWithDifferentTitle))`; `advanceUntilIdle()`
  - then: component.titles still equals `listOf("Original Name")` (frozen at creation time)

- **Edge cases:**
  - `onShareClick` stub — вызов не бросает exception (LP-U-stub-01): `component.onShareClick(quest)` — no throw
- **Depends on:** `DefaultQuestListComponent`, `FakeQuestRepository` (Phase-01/Phase-04), `FakeStackNavigation` (Phase-03), `DefaultComponentContext`, `LifecycleRegistry`
- **Canonical reference:** `04-testing.md §3`
- **Rationale:** AC#13 (drill-down from HomeQuests), AC#23 (frozen breadcrumb), QZ-U coverage for QuestList.

---

## Create DefaultSectionListComponentTest

- **Файл:** `android/feature/quizzes-screen/presentation/src/test/kotlin/.../component/DefaultSectionListComponentTest.kt`
- **Тип:** JVM unit test (JUnit 4)
- **Сигнатура:** `class DefaultSectionListComponentTest`
- **Вход:** `DefaultSectionListComponent`, `FakeSectionRepository`, `FakeStackNavigation`, `TestComponentContext`
- **Поведение / Выход (из `04-testing.md §4`):**

  **SL-U-01**: `initial state is Loading`
  - then: `state.value is HierarchyListUiState.Loading`

  **SL-U-02**: `FakeSectionRepository emits sections → Loaded`
  - when: `fakeRepo.emit(listOf(sectionA, sectionB))`; `advanceUntilIdle()`
  - then: `state.value is HierarchyListUiState.Loaded`; `items.size == 2`

  **SL-U-03**: `empty list → Empty state`
  - when: `fakeRepo.emit(emptyList())`
  - then: `state.value is HierarchyListUiState.Empty`; `(state.value as Empty).levelLabel == "Нет секций"`

  **SL-U-04**: `onSectionClick pushes ThemeList with correct sectionId`
  - given: loaded state; section `HierarchyItemUi(id="s-1", title="Section A")`
  - when: `component.onSectionClick(sectionItem)`
  - then: pushed config is `QuizzesConfig.ThemeList(sectionId="s-1", titles=[...,"Section A"])`

  **SL-U-05**: `breadcrumb titles snapshot at push time`
  - given: component with `titles = listOf("Math", "Quest 1")`
  - when: `onSectionClick(section)`
  - then: pushed config `titles = listOf("Math", "Quest 1", section.title)`

  **SL-U-06**: `when sync adds new section Flow re-emits and Loaded.items updates`
  - given: loaded with 1 section
  - when: `fakeRepo.emit(listOf(section1, section2))`; `advanceUntilIdle()`
  - then: `(state.value as Loaded).items.size == 2`

  **SL-U-07**: `when sync renames section breadcrumb titles remain frozen`
  - given: pushed ThemeList config with `titles = ["Math", "Quest 1", "Original Section"]`
  - when: `fakeRepo.emit(listOf(renamedSection))`; `advanceUntilIdle()`
  - then: already-pushed config.titles unchanged (frozen at push time, not re-pushed)

- **Edge cases:**
  - `SL-U-03`: `Empty.levelLabel` presence — assert non-empty string
- **Depends on:** `DefaultSectionListComponent`, `FakeSectionRepository`, `FakeStackNavigation`
- **Canonical reference:** `04-testing.md §4`
- **Rationale:** AC#16 (drill into sections), AC#17 (live sync updates), AC#23 (frozen breadcrumb).

---

## Create DefaultThemeListComponentTest

- **Файл:** `android/feature/quizzes-screen/presentation/src/test/kotlin/.../component/DefaultThemeListComponentTest.kt`
- **Тип:** JVM unit test (JUnit 4)
- **Сигнатура:** `class DefaultThemeListComponentTest`
- **Вход:** `DefaultThemeListComponent`, `FakeThemeRepository`, `FakeStackNavigation`, `TestComponentContext`
- **Поведение / Выход (из `04-testing.md §5`):**

  **TH-U-01**: `initial state is Loading`
  - then: `state.value is HierarchyListUiState.Loading`

  **TH-U-02**: `FakeThemeRepository emits themes → Loaded`
  - when: `fakeRepo.emit(listOf(themeA))`; `advanceUntilIdle()`
  - then: `state.value is HierarchyListUiState.Loaded`; `items.size == 1`

  **TH-U-03**: `empty list → Empty state`
  - then: `state.value is HierarchyListUiState.Empty`

  **TH-U-04**: `onThemeClick pushes LessonList with correct themeId`
  - given: `HierarchyItemUi(id="t-1", title="Theme A")`
  - when: `component.onThemeClick(themeItem)`
  - then: pushed config is `QuizzesConfig.LessonList(themeId="t-1", titles=[...,"Theme A"])`

- **Edge cases:**
  - Verify breadcrumb titles accumulation — `titles = parentTitles + listOf(theme.title)`
- **Depends on:** `DefaultThemeListComponent`, `FakeThemeRepository`, `FakeStackNavigation`
- **Canonical reference:** `04-testing.md §5`
- **Rationale:** AC#16 (drill into themes), TH-U-01..04.

---

## Create DefaultLessonListComponentTest

- **Файл:** `android/feature/quizzes-screen/presentation/src/test/kotlin/.../component/DefaultLessonListComponentTest.kt`
- **Тип:** JVM unit test (JUnit 4)
- **Сигнатура:** `class DefaultLessonListComponentTest`
- **Вход:** `DefaultLessonListComponent`, `FakeLessonRepository`, `FakeStackNavigation`, `TestComponentContext`
- **Поведение / Выход (из `04-testing.md §6`):**

  **LL-U-01**: `initial state is Loading`
  - then: `state.value is HierarchyListUiState.Loading`

  **LL-U-02**: `FakeLessonRepository emits lessons → Loaded`
  - when: `fakeRepo.emit(listOf(lessonA))`; `advanceUntilIdle()`
  - then: `state.value is HierarchyListUiState.Loaded`

  **LL-U-03**: `empty → Empty state`
  - then: `state.value is HierarchyListUiState.Empty`

  **LL-U-04**: `onLessonClick pushes LessonPlaceholder with correct lessonId and lessonTitle`
  - given: `HierarchyItemUi(id="l-1", title="Lesson A")`
  - when: `component.onLessonClick(lessonItem)`
  - then: pushed config is `QuizzesConfig.LessonPlaceholder(lessonId="l-1", lessonTitle="Lesson A", titles=[...,"Lesson A"])`
  - and: `pushedConfig.lessonTitle == "Lesson A"` — отдельное поле, не только последний элемент titles

- **Edge cases:**
  - `LL-U-04` verify: `lessonTitle` и `titles.last()` оба равны `lesson.title` — отдельная проверка что `lessonTitle != null`
- **Depends on:** `DefaultLessonListComponent`, `FakeLessonRepository`, `FakeStackNavigation`
- **Canonical reference:** `04-testing.md §6`
- **Rationale:** AC#16 (drill into lessons), LL-U-01..04. Важен `lessonTitle` отдельный field — SER-06 depends on it.

---

## Create DefaultLessonPlaceholderComponentTest

- **Файл:** `android/feature/quizzes-screen/presentation/src/test/kotlin/.../component/DefaultLessonPlaceholderComponentTest.kt`
- **Тип:** JVM unit test (JUnit 4)
- **Сигнатура:** `class DefaultLessonPlaceholderComponentTest`
- **Вход:** `DefaultLessonPlaceholderComponent` с различными `QuizzesConfig.LessonPlaceholder` configs
- **Поведение / Выход (из `04-testing.md §7`):**

  **LP-U-01**: `uiState.lessonTitle equals config.lessonTitle`
  - given: `config = QuizzesConfig.LessonPlaceholder(lessonId="l-1", lessonTitle="Lesson Title", titles=[...])`
  - then: `component.uiState.lessonTitle == "Lesson Title"`

  **LP-U-02**: `uiState.titles equals config.titles (frozen breadcrumb)`
  - given: `config.titles = listOf("Math", "Quest", "Section", "Theme", "Lesson Title")`
  - then: `component.uiState.titles == config.titles`

- **Edge cases:**
  - `lessonTitle` не должен выводиться из `titles.last()` — assert что при `config.lessonTitle = "Special"`, `config.titles = ["A","B"]`, `uiState.lessonTitle == "Special"` (не "B")
  - Нет coroutines — тест синхронный, не нужен `runTest`
- **Depends on:** `DefaultLessonPlaceholderComponent`, `QuizzesConfig.LessonPlaceholder`, `LessonPlaceholderUiState`
- **Canonical reference:** `04-testing.md §7`
- **Rationale:** LP-U-01..02. Простой stateless component — тест быстрый.

---

## Create DrillItemMapperTest

- **Файл:** `android/feature/quizzes-screen/presentation/src/test/kotlin/.../mapper/DrillItemMapperTest.kt`
- **Тип:** JVM unit test (JUnit 4, pure — нет coroutines)
- **Сигнатура:** `class DrillItemMapperTest`
- **Вход:** domain test fixtures для `Section`, `Theme`, `Lesson`
- **Поведение / Выход:**

  **MAP-01**: `Section.toDrillItem() maps id correctly`
  - given: `section = Section(id=SectionId("s-1"), title="Section A", ...)` 
  - then: `section.toDrillItem().id == "s-1"`

  **MAP-02**: `Section.toDrillItem() maps title correctly`
  - then: `section.toDrillItem().title == "Section A"`

  **MAP-03**: `Section.toDrillItem() subtitleCount is null`
  - then: `section.toDrillItem().subtitleCount == null`

  **MAP-04**: `Theme.toDrillItem() maps id correctly`
  - given: `theme = Theme(id=ThemeId("t-1"), title="Theme B")`
  - then: `theme.toDrillItem().id == "t-1"`, `theme.toDrillItem().title == "Theme B"`

  **MAP-05**: `Lesson.toDrillItem() maps id correctly`
  - given: `lesson = Lesson(id=LessonId("l-1"), title="Lesson C")`
  - then: `lesson.toDrillItem().id == "l-1"`, `lesson.toDrillItem().title == "Lesson C"`

  **MAP-06**: `orderLabel is null when domain model has no order field` (или не-null если field exists)
  - verify: при создании domain fixture без order — `toDrillItem().orderLabel == null`
  - если `Section.order: Int? = null` present: `Section(order=null).toDrillItem().orderLabel == null`
  - если `Section.order = 3`: `Section(order=3).toDrillItem().orderLabel == "3."`

- **Edge cases:**
  - Domain models с `id = ""` (empty string) — verify mapper не throws, passes through
  - Long titles — pass-through без truncation в mapper
- **Depends on:** `SectionDrillMapper.kt`, `ThemeDrillMapper.kt`, `LessonDrillMapper.kt`, `Section`, `Theme`, `Lesson`, `HierarchyItemUi`
- **Canonical reference:** `04-testing.md §1` (mapper test category)
- **Rationale:** Mapper purity — unit тест без фреймворка; верифицирует что domain → UI преобразование корректно перед тем как screens используют данные.
