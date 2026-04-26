---
phase: 05
role: frontend-dev
---

# Phase-05 Frontend Tasks: Compose UI Screens

### Pattern Invariants

- Все screens читают component state через `component.state.subscribeAsState()` (Decompose `Value<UiState>` → Compose `State<UiState>`). Не `StateFlow.collectAsState()`. Ref: `06-api-contract.md:629` (Value<UiState> definition).
- `QuizzesScreen` использует `component.childStack.subscribeAsState()` — exhaustive `when(active.instance)` по `QuizzesChild`. Ref: `06-api-contract.md:500` (QuizzesScreen contract).
- `onSegmentClick: (Int) -> Unit` — параметр каждого child screen; заполняется как `component::popToLevel` в `QuizzesScreen`. `popToLevel` живёт на `QuizzesComponent` (`06-api-contract.md:392`), не на child component. Не передавать `component::popToLevel` там где `component: QuestListComponent` (нет этого метода).
- `QuestCard(onLongClick = null)` в Phase-05 — Share меню реализуется в Phase-06. Ref: `06-api-contract.md:318` (QuestCard onLongClick nullable).
- `LazyListState` сохраняется через `rememberLazyListState()` — необходим для AC#22 (rotation scroll retention). Ref: `02-behavior.md:AC#22`.
- Screens не импортируют domain repositories или use cases — только component interfaces. Ref: `.claude/rules/clean-architecture.md:9` (layer boundaries table).
- Prefix `Quizzes` в `QuizzesScreen`; child screens названы `QuestListScreen`, `SectionListScreen`, `ThemeListScreen`, `LessonListScreen`, `LessonPlaceholderScreen` — без `Quizzes` prefix. Существующий паттерн: `android/feature/quest/presentation/src/main/.../ui/MyQuestsScreen.kt:1`.

---

## Create QuizzesScreen

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/QuizzesScreen.kt`
- **Тип:** Composable function
- **Сигнатура:** `@Composable fun QuizzesScreen(component: QuizzesComponent)`
- **Вход:**
  - `component: QuizzesComponent` — предоставляет `childStack` + `popToLevel` + `dismissQuizzes`
- **Поведение / Выход:**
  - `val stack by component.childStack.subscribeAsState()`
  - `val active = stack.active.instance`
  - `when(active)`:
    - `QuizzesChild.Idle` → не рендерит ничего (`return` или пустой `Box`) — этот case не должен возникнуть в practice (AppShellScreen проверяет Idle перед вызовом), но exhaustive when требует обработки
    - `QuizzesChild.QuestList(child)` → `QuestListScreen(child, onSegmentClick = component::popToLevel)`
    - `QuizzesChild.SectionList(child)` → `SectionListScreen(child, onSegmentClick = component::popToLevel)`
    - `QuizzesChild.ThemeList(child)` → `ThemeListScreen(child, onSegmentClick = component::popToLevel)`
    - `QuizzesChild.LessonList(child)` → `LessonListScreen(child, onSegmentClick = component::popToLevel)`
    - `QuizzesChild.LessonPlaceholder(child)` → `LessonPlaceholderScreen(child, onSegmentClick = component::popToLevel)`
  - `component::popToLevel` — `popToLevel(uiLevel: Int)` живёт на `QuizzesComponent` (`06-api-contract.md:392`), не на child component interfaces. QuizzesScreen — единственное место где он доступен.
- **Edge cases:**
  - `QuizzesChild.Idle` в when — exhaustive; вернуть пустой Composable (не crash)
  - Animation between transitions — Phase-05 нет анимации; простой switch. Phase-05+ может добавить `AnimatedContent`.
- **Depends on:** `QuizzesComponent`, `QuizzesChild`, все 5 child screen Composables
- **Canonical reference:** `06-api-contract.md:500`
- **Rationale:** Router — единственное место с доступом к `QuizzesComponent.popToLevel`. Пробрасывается вниз как lambda в каждый child screen.

---

## Create QuestListScreen

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/QuestListScreen.kt`
- **Тип:** Composable function
- **Сигнатура:** `@Composable fun QuestListScreen(component: QuestListComponent, onSegmentClick: (Int) -> Unit)`
- **Вход:**
  - `component: QuestListComponent` — предоставляет `state`, `titles`, `onQuestClick`, `onShareClick`
  - `onSegmentClick: (Int) -> Unit` — пробрасывается из `QuizzesScreen` как `component::popToLevel` (`QuizzesComponent.popToLevel` — `06-api-contract.md:392`). Child component не имеет `popToLevel`.
- **Поведение / Выход:**
  - `val uiState by component.state.subscribeAsState()`
  - `var expandedQuestId by remember { mutableStateOf<QuestId?>(null) }` — Phase-06 stub, unused в Phase-05
  - `Column`:
    - `BreadcrumbBar(titles = component.titles, onSegmentClick = onSegmentClick)`
    - `when(uiState)`:
      - `QuestListUiState.Loading` → `Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Center)) }`
      - `QuestListUiState.Empty` → `Box(Modifier.fillMaxSize()) { Text("Нет квестов", Modifier.align(Center), style=MaterialTheme.typography.titleMedium) }`
      - `QuestListUiState.Loaded(quests)` → `LazyColumn(state=lazyListState) { items(quests, key={it.id.value}) { quest -> QuestCard(item=quest, onClick={component.onQuestClick(quest)}, onLongClick=null) } }`
  - `val lazyListState = rememberLazyListState()` — для AC#22 scroll retention
- **Edge cases:**
  - `QuestCard(onLongClick=null)` — Phase-06 заменит null на `{ expandedQuestId = it }`
  - `key={it.id.value}` в `items()` — для stable list diffing
  - BreadcrumbBar — последний элемент не кликабелен (enforced внутри компонента Phase-02)
- **Depends on:** `QuestListComponent`, `QuestListUiState`, `QuestCard` (Phase-02), `BreadcrumbBar` (Phase-02), Decompose `subscribeAsState`
- **Canonical reference:** `06-api-contract.md:529`, `06-api-contract.md:628`, `06-api-contract.md:677`
- **Rationale:** `onSegmentClick` параметр — `QuestListComponent` не знает о `QuizzesComponent.popToLevel`; QuizzesScreen владеет QuizzesComponent и пробрасывает лямбду вниз.

---

## Create SectionListScreen

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/SectionListScreen.kt`
- **Тип:** Composable function
- **Сигнатура:** `@Composable fun SectionListScreen(component: SectionListComponent, onSegmentClick: (Int) -> Unit)`
- **Вход:**
  - `component: SectionListComponent` — предоставляет `state: Value<HierarchyListUiState>`, `titles`, `onSectionClick`
  - `onSegmentClick: (Int) -> Unit` — пробрасывается из `QuizzesScreen` как `component::popToLevel` (`QuizzesComponent.popToLevel` — `06-api-contract.md:392`). `SectionListComponent` не имеет `popToLevel`.
- **Поведение / Выход:**
  - `val uiState by component.state.subscribeAsState()`
  - `Column`:
    - `BreadcrumbBar(titles = component.titles, onSegmentClick = onSegmentClick)`
    - `when(uiState)`:
      - `Loading` → `CircularProgressIndicator` centered
      - `Empty(levelLabel)` → `Text(levelLabel)` centered
      - `Loaded(items)` → `LazyColumn { items(items, key={it.id}) { item -> HierarchyItemCard(title=item.title, orderLabel=item.orderLabel, subtitleCount=item.subtitleCount, onClick={ component.onSectionClick(item) }) } }`
- **Edge cases:**
  - `onLongClick=null` на `HierarchyItemCard` — нет long-press меню (AC#12)
  - BreadcrumbBar — последний сегмент некликабелен (enforced внутри компонента Phase-02)
- **Depends on:** `SectionListComponent`, `HierarchyListUiState`, `HierarchyItemCard` (Phase-02), `BreadcrumbBar`
- **Canonical reference:** `06-api-contract.md:529`, `06-api-contract.md:677`
- **Rationale:** `onSegmentClick` параметр — `SectionListComponent` не знает о `QuizzesComponent.popToLevel`; QuizzesScreen владеет QuizzesComponent и пробрасывает лямбду вниз. Consistent с QuestListScreen.

---

## Create ThemeListScreen

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/ThemeListScreen.kt`
- **Тип:** Composable function
- **Сигнатура:** `@Composable fun ThemeListScreen(component: ThemeListComponent, onSegmentClick: (Int) -> Unit)`
- **Вход:**
  - `component: ThemeListComponent` — предоставляет `state`, `titles`, `onThemeClick`
  - `onSegmentClick: (Int) -> Unit` — пробрасывается из `QuizzesScreen` как `component::popToLevel` (`QuizzesComponent.popToLevel` — `06-api-contract.md:392`). `ThemeListComponent` не имеет `popToLevel`.
- **Поведение / Выход:**
  - Структурно идентичен `SectionListScreen` с заменой:
    - `component.onThemeClick(item)` вместо `onSectionClick`
    - `ThemeListComponent` тип
  - `BreadcrumbBar(titles = component.titles, onSegmentClick = onSegmentClick)`
- **Edge cases:**
  - `onLongClick=null` на `HierarchyItemCard` — нет long-press меню (AC#12)
- **Depends on:** `ThemeListComponent`, `HierarchyListUiState`, `HierarchyItemCard`, `BreadcrumbBar`
- **Canonical reference:** `06-api-contract.md:529`, `06-api-contract.md:677`
- **Rationale:** Consistent с SectionListScreen — `onSegmentClick` передаётся из QuizzesScreen как `component::popToLevel`.

---

## Create LessonListScreen

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/LessonListScreen.kt`
- **Тип:** Composable function
- **Сигнатура:** `@Composable fun LessonListScreen(component: LessonListComponent, onSegmentClick: (Int) -> Unit)`
- **Вход:**
  - `component: LessonListComponent` — предоставляет `state`, `titles`, `onLessonClick`
  - `onSegmentClick: (Int) -> Unit` — пробрасывается из `QuizzesScreen` как `component::popToLevel` (`QuizzesComponent.popToLevel` — `06-api-contract.md:392`). `LessonListComponent` не имеет `popToLevel`.
- **Поведение / Выход:**
  - Структурно идентичен ThemeListScreen с заменой `component.onLessonClick(item)`
  - `BreadcrumbBar(titles = component.titles, onSegmentClick = onSegmentClick)`
- **Edge cases:**
  - `onLongClick=null` на `HierarchyItemCard` — нет long-press меню (AC#12)
- **Depends on:** `LessonListComponent`, `HierarchyListUiState`, `HierarchyItemCard`, `BreadcrumbBar`
- **Canonical reference:** `06-api-contract.md:529`, `06-api-contract.md:677`
- **Rationale:** Terminal list before LessonPlaceholder. Consistent `onSegmentClick` parameter.

---

## Create LessonPlaceholderScreen

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/LessonPlaceholderScreen.kt`
- **Тип:** Composable function
- **Сигнатура:** `@Composable fun LessonPlaceholderScreen(component: LessonPlaceholderComponent, onSegmentClick: (Int) -> Unit)`
- **Вход:**
  - `component: LessonPlaceholderComponent` — предоставляет `uiState: LessonPlaceholderUiState`
  - `onSegmentClick: (Int) -> Unit` — пробрасывается из `QuizzesScreen` как `component::popToLevel` (`QuizzesComponent.popToLevel` — `06-api-contract.md:392`). `LessonPlaceholderComponent` не имеет `popToLevel`.
- **Поведение / Выход:**
  - `val uiState = component.uiState` — статический, нет `subscribeAsState()`
  - `Column(Modifier.fillMaxSize())`:
    - `BreadcrumbBar(titles = uiState.titles, onSegmentClick = onSegmentClick)`
    - `Box(Modifier.fillMaxSize())` содержит:
      - `Text(uiState.lessonTitle, style = titleLarge)`
      - `Text` с placeholder текстом «Прохождение урока будет добавлено позже» (spec AC#29)
  - Нет Loading/Empty states — полностью статический
- **Edge cases:**
  - `uiState.lessonTitle` — может быть пустой строкой если config некорректен; не крашится, рендерит пустое
  - Нет `rememberLazyListState` — нет LazyColumn
- **Depends on:** `LessonPlaceholderComponent`, `LessonPlaceholderUiState`, `BreadcrumbBar`
- **Canonical reference:** `06-api-contract.md:529`, `06-api-contract.md:742`
- **Rationale:** Static terminal screen — нет repository, нет Loading state. `onSegmentClick` consistent с остальными 4 screens.

---

## Create stub FakeQuestListComponent (test helper in androidTest)

- **Файл:** `android/feature/quizzes-screen/presentation/src/androidTest/kotlin/.../fake/FakeQuestListComponent.kt`
- **Тип:** test fake class (для Compose UI instrumented тестов)
- **Сигнатура:** `class FakeQuestListComponent(initialState: QuestListUiState, override val titles: List<String> = emptyList()) : QuestListComponent`
- **Вход:** `initialState: QuestListUiState` — начальный state для тестирования разных UI состояний
- **Поведение / Выход:**
  - `private val _state = MutableValue(initialState)`
  - `override val state: Value<QuestListUiState> get() = _state`
  - `fun setState(state: QuestListUiState) { _state.value = state }` — test control
  - `var onQuestClickCalled: QuestDisplayItem? = null` — call capture
  - `override fun onQuestClick(quest: QuestDisplayItem) { onQuestClickCalled = quest }`
  - `override fun onShareClick(quest: QuestDisplayItem) { /* stub */ }`
- **Edge cases:**
  - `MutableValue` из Decompose (`com.arkivanov.decompose.value.MutableValue`) — не `MutableStateFlow`
- **Depends on:** `QuestListComponent`, `QuestListUiState`, Decompose `MutableValue`, `Value`
- **Canonical reference:** `04-testing.md §1`
- **Rationale:** Compose UI тесты используют Fake компонент с `MutableValue` (Decompose) — нет реального DefaultQuestListComponent в instrumented тесте.

---

## Create stub FakeHierarchyListComponent variants (for SectionListScreen tests)

- **Файл:** `android/feature/quizzes-screen/presentation/src/androidTest/kotlin/.../fake/FakeSectionListComponent.kt` (и аналоги для Theme, Lesson)
- **Тип:** test fake classes
- **Сигнатура:** `class FakeSectionListComponent(initialState: HierarchyListUiState, override val titles: List<String> = emptyList()) : SectionListComponent`
- **Вход:** `initialState: HierarchyListUiState`
- **Поведение / Выход:**
  - Аналогично `FakeQuestListComponent` но для `HierarchyListUiState`
  - `var onSectionClickCalled: HierarchyItemUi? = null`
  - `override fun onSectionClick(section: HierarchyItemUi) { onSectionClickCalled = section }`
- **Edge cases:**
  - Аналогично FakeQuestListComponent
- **Depends on:** `SectionListComponent`, `HierarchyListUiState`, Decompose `MutableValue`
- **Canonical reference:** `04-testing.md §1`
- **Rationale:** Тот же паттерн что и FakeQuestListComponent.
