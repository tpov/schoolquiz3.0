# 04 — Test Strategy: quizzes-screen

**Feature slug**: `quizzes-screen`  
**Walking Skeleton**: N/A (pure UI/navigation feature, no domain business rules)  
**TDD-style**: тесты пишутся параллельно с реализацией, не в отдельной фазе  
**Last updated**: 2026-04-25

---

## 1. Test Strategy Overview

### Подход

| Слой | Фреймворк | Тип | Fakes / Mocks |
|------|-----------|-----|---------------|
| Component logic (DefaultXxxComponent) | JUnit 4 + coroutines-test | JVM unit | Fake*Repository, FakeStackNavigation |
| QuizzesConfig serialization | JUnit 4 + kotlinx.serialization | JVM unit | — |
| Compose UI — design-system composables (BreadcrumbBar, HierarchyItemCard, QuestCard) | Compose UI Test (instrumented) | UI | primitive params, никаких state holders |
| Compose screens (QuestListScreen, SectionListScreen, etc.) | Compose UI Test (instrumented) | UI | Fake компоненты с `MutableValue<UiState>` (Decompose `Value`, не StateFlow) |
| Cross-feature wiring (Root → Quizzes → child) | JVM integration | JVM | Fakes для всех репозиториев |
| Repository extension (observeByCatalog) | JUnit 4 + Room in-memory | JVM/instrumented | — |
| StateKeeper restore contract (AC#21) | JUnit 4 + Decompose StateKeeperDispatcher | JVM unit | FakeQuestRepository |
| Configuration change / rotation (AC#22) | ActivityScenario.recreate() + Compose UI Test | Instrumented | — |

> **Примечание**: Child components экспонируют `Value<UiState>` (Decompose), UI читает через `subscribeAsState()`. Для тестирования feature screens создаётся fake component с `MutableValue(initialState)`. Design-system composables (BreadcrumbBar, HierarchyItemCard) принимают только primitive params — тестируются без state holders. HomeQuests/MyQuests interfaces используют StateFlow (per §6 `06-api-contract.md`) — там StateFlow fakes правильны.

### Конвенции

- **Fakes over mocks**: FakeSectionRepository, FakeThemeRepository, FakeLessonRepository, FakeQuestRepository (по аналогии с существующим FakeAuthRepository и FakeCatalogRepository).
- **Flow assertions**: `.take(N).toList()`, `.value` — Turbine не используется (не в зависимостях).
- **Coroutines**: `StandardTestDispatcher(testScheduler)` + `advanceUntilIdle()` для background корутин; `UnconfinedTestDispatcher` для немедленного выполнения Flow.
- **Naming**: Kotlin backtick-стиль (`fun \`when stack empty then backCallback disabled\`() { ... }`).
- **Document responsibility**: этот файл описывает **что тестировать**. Канонические сигнатуры — только в `06-api-contract.md`. Здесь — pseudocode / intent.

---

## 2. JVM Unit Tests — DefaultQuizzesComponent

**Файл**: `DefaultQuizzesComponentTest.kt`  
**Расположение**: `android/feature/quizzes/presentation/src/test/`

### 2.1 Back Callback lifecycle

```
// setup: TestComponentContext с FakeBackDispatcher
```

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| QZ-U-01 | `when stack=[Idle] then backCallback disabled` | `backCallback.isEnabled == false` |
| QZ-U-02 | `when pushNew(QuestList) then backCallback enabled` | `backCallback.isEnabled == true` |
| QZ-U-03 | `when pop to Idle then backCallback disabled` | `backCallback.isEnabled == false` |
| QZ-U-04 | `backCallback priority equals PRIORITY_OVERLAY` | `backCallback.priority == BackCallback.PRIORITY_OVERLAY` |
| QZ-U-05 | `when backCallback invoked and stack=[Idle,QuestList] then stack=[Idle]` | `childStack.value.active` is `QuizzesChild.Idle` после pop |

### 2.2 Navigation: dismissQuizzes()

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| QZ-U-06 | `when stack=[Idle,QuestList,SectionList] dismissQuizzes() collapses to [Idle]` | `childStack.value.items.size == 1` |
| QZ-U-07 | `when stack=[Idle] dismissQuizzes() is noop` | `childStack.value.items.size == 1`, без ошибок |

### 2.3 ChildStack initial state

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| QZ-U-08 | `initial stack contains exactly Idle` | `childStack.value.items.size == 1`; `active is QuizzesChild.Idle` |
| QZ-U-09 | `Idle child factory returns QuizzesChild.Idle` | childFactory(QuizzesConfig.Idle, ...) is `QuizzesChild.Idle` |

---

## 3. JVM Unit Tests — DefaultQuestListComponent

**Файл**: `DefaultQuestListComponentTest.kt`

### 3.1 State Loading

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| QL-U-01 | `initial state is Loading` | `uiState.value is QuestListUiState.Loading` |
| QL-U-02 | `when FakeQuestRepository emits list then state is Loaded` | `uiState.value is QuestListUiState.Loaded`; `quests.size == N` |
| QL-U-03 | `when repository emits empty list then state is Empty` | `uiState.value is QuestListUiState.Empty` |
| QL-U-04 | `when repository emits error then state is Error` | `uiState.value is QuestListUiState.Error` |

### 3.2 Navigation on click

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| QL-U-05 | `onCatalogClick pushes SectionList with correct config` | Navigation.pushNew вызван с `QuizzesConfig.SectionList(catalogId=..., titles=[...])` |
| QL-U-06 | `onQuestClick pushes SectionList with quest.title in titles[1]` | `titles[1] == quest.title` |
| QL-U-07 | `onQuestClick breadcrumb titles[0] == catalogName` | `titles[0] == catalogName` |

### 3.3 QuestDisplayItem.catalogId presence

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| QL-U-08 | `Loaded quests each have non-null catalogId` | `quests.all { it.catalogId != null }` (REQUIRES: `QuestDisplayItem` получит `catalogId` поле) |

### 3.4 Offline / cached data (AC#20)

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| QL-U-09 | `when repository emits cached list (no network) then navigation works normally` | `uiState.value is QuestListUiState.Loaded`; onQuestClick вызывает pushNew без ошибок — offline не влияет на cached Flow |

### 3.5 Frozen breadcrumb after data change (AC#23)

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| QL-U-10 | `titles in QuestList config unchanged after repository emits renamed quest` | `childStack.value.items[1].configuration.titles[0] == originalCatalogName` после того как FakeQuestRepository эмитит квест с изменённым title |

---

## 4. JVM Unit Tests — DefaultSectionListComponent

**Файл**: `DefaultSectionListComponentTest.kt`

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| SL-U-01 | `initial state is Loading` | `uiState.value is SectionListUiState.Loading` |
| SL-U-02 | `FakeSectionRepository emits sections → Loaded` | `items.size == N` |
| SL-U-03 | `empty list → Empty state` | `uiState.value is SectionListUiState.Empty` |
| SL-U-04 | `onSectionClick pushes ThemeList with correct sectionId` | Navigation.pushNew вызван с `QuizzesConfig.ThemeList(sectionId=..., titles=[...])` |
| SL-U-05 | `breadcrumb titles snapshot at push time` | `titles` совпадает с данными в момент вызова, не меняется после обновления Flow |
| SL-U-06 | `when sync adds new section Flow re-emits and Loaded.items updates` (AC#17) | После `FakeSectionRepository.emit(newList)` → `uiState.value.items.size` увеличивается |
| SL-U-07 | `when sync renames section breadcrumb titles remain frozen` (AC#23) | `childStack.value.active.configuration.titles` не меняются после rename-эмита |

**Fakes**: `FakeSectionRepository` из `shared/feature/section/domain/commonTest/`

---

## 5. JVM Unit Tests — DefaultThemeListComponent

**Файл**: `DefaultThemeListComponentTest.kt`

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| TH-U-01 | `initial state is Loading` | `uiState.value is ThemeListUiState.Loading` |
| TH-U-02 | `FakeThemeRepository emits themes → Loaded` | `items.size == N` |
| TH-U-03 | `empty list → Empty state` | `uiState.value is ThemeListUiState.Empty` |
| TH-U-04 | `onThemeClick pushes LessonList with correct themeId` | Navigation.pushNew вызван с `QuizzesConfig.LessonList(themeId=..., titles=[...])` |

**Fakes**: `FakeThemeRepository` из `shared/feature/theme/domain/commonTest/`

---

## 6. JVM Unit Tests — DefaultLessonListComponent

**Файл**: `DefaultLessonListComponentTest.kt`

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| LL-U-01 | `initial state is Loading` | `uiState.value is LessonListUiState.Loading` |
| LL-U-02 | `FakeLessonRepository emits lessons → Loaded` | `items.size == N` |
| LL-U-03 | `empty → Empty state` | `uiState.value is LessonListUiState.Empty` |
| LL-U-04 | `onLessonClick pushes LessonPlaceholder with correct lessonId` | Navigation.pushNew вызван с `QuizzesConfig.LessonPlaceholder(lessonId=..., titles=[...])` |

**Fakes**: `FakeLessonRepository` из `shared/feature/lesson/domain/commonTest/`

---

## 7. JVM Unit Tests — DefaultLessonPlaceholderComponent

**Файл**: `DefaultLessonPlaceholderComponentTest.kt`

> Back handling принадлежит `DefaultQuizzesComponent` (BackCallback). LessonPlaceholder только отдаёт UiState. `lessonId` — приватное поле конфига, не экспонируется напрямую.

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| LP-U-01 | `uiState.lessonTitle equals config.lessonTitle` | `uiState.value.lessonTitle == config.lessonTitle` |
| LP-U-02 | `uiState.titles equals config.titles (frozen breadcrumb)` | `uiState.value.titles == config.titles` |

---

## 8. QuizzesConfig Serialization Tests

**Файл**: `QuizzesConfigSerializationTest.kt`  
**Расположение**: JVM unit test  
**Цель**: Гарантировать round-trip сериализации для каждого config-варианта (process death restoration).

### 8.1 Round-trip тесты (по одному для каждого варианта)

| ID | Config вариант | Проверяемые поля |
|----|---------------|------------------|
| SER-01 | `QuizzesConfig.Idle` | data object — десериализация без полей |
| SER-02 | `QuizzesConfig.QuestList` | `catalogId: String`, `titles: List<String>` — catalogName хранится в `titles[0]`, отдельного поля нет |
| SER-03 | `QuizzesConfig.SectionList` | `questId: String`, `titles: List<String>` (2 элемента: catalogName + questTitle) |
| SER-04 | `QuizzesConfig.ThemeList` | `sectionId: String`, `titles: List<String>` (3 элемента) |
| SER-05 | `QuizzesConfig.LessonList` | `themeId: String`, `titles: List<String>` (4 элемента) |
| SER-06 | `QuizzesConfig.LessonPlaceholder` | `lessonId: String`, `lessonTitle: String`, `titles: List<String>` (полный путь включая lessonTitle последним) |

### 8.2 Stack serialization round-trip

| ID | Тест |
|----|------|
| SER-07 | `stack [Idle, QuestList, SectionList] serializes and deserializes to identical stack` |
| SER-08 | `titles with special chars (кириллица, emoji) survive round-trip` |
| SER-09 | `empty titles list survives round-trip` |

### 8.3 Schema evolution — unknown / corrupted payload

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| SER-10 | `crafted JSON with missing required field → SerializationException thrown` | `Json.decodeFromString(...)` бросает `SerializationException`; restore wrapper (если присутствует) возвращает `listOf(QuizzesConfig.Idle)` |
| SER-11 | `crafted JSON with unknown discriminator value → fallback to Idle` | restore wrapper перехватывает `SerializationException`, возвращает initial stack `[Idle]` |

> **NOTE (schema evolution risk)**: Без runtime wrapper вокруг `childStack` restore вызов `Json.decodeFromString` с неизвестным discriminator вызовет `SerializationException` и crash при cold start. Рекомендуется: реализация должна включить try/catch-обёртку вокруг `StateKeeper` restore, возвращающую `listOf(QuizzesConfig.Idle)` при любой ошибке десериализации. Тесты SER-10 и SER-11 верифицируют эту обёртку, а не голый kotlinx.serialization.

```kotlin
// Паттерн теста (pseudocode):
@Test fun `QuizzesConfig_QuestList round-trip`() {
    val original = QuizzesConfig.QuestList(
        catalogId = "cat-1",
        titles = listOf("Математика")  // catalogName → titles[0]
    )
    val json = Json.encodeToString(QuizzesConfig.serializer(), original)
    val decoded = Json.decodeFromString(QuizzesConfig.serializer(), json)
    assertEquals(original, decoded)
}

@Test fun `QuizzesConfig_LessonPlaceholder round-trip`() {
    val original = QuizzesConfig.LessonPlaceholder(
        lessonId = "lesson-1",
        lessonTitle = "Урок 1",
        titles = listOf("Математика", "Квест", "Раздел", "Тема", "Урок 1")
    )
    val json = Json.encodeToString(QuizzesConfig.serializer(), original)
    val decoded = Json.decodeFromString(QuizzesConfig.serializer(), json)
    assertEquals(original, decoded)
}
```

---

## 9. Compose UI Tests

**Расположение**: `android/feature/quizzes/presentation/src/androidTest/`  
**Framework**: `androidx.compose.ui.test.junit4`

### 9.1 BreadcrumbBar

**Файл**: `BreadcrumbBarTest.kt`

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| BB-UI-01 | `renders all segments in order` | Каждый title виден через `onNodeWithText(title).assertIsDisplayed()` |
| BB-UI-02 | `last segment not clickable` | Последний сегмент не имеет semantics Role.Button |
| BB-UI-03 | `non-last segment click invokes onBreadcrumbClick with correct index` | `onBreadcrumbClick(i)` вызван с правильным `uiLevel` |
| BB-UI-04 | `single segment (only root) renders without separator` | Нет разделителя между сегментами |
| BB-UI-05 | `long title truncates with ellipsis` | Видна `...` или `ellipsis` semantics |
| BB-UI-06 | `accessibility: non-last segment has contentDescription` | `onNodeWithContentDescription(...).assertExists()` |

### 9.2 HierarchyItemCard

**Файл**: `HierarchyItemCardTest.kt`

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| HI-UI-01 | `title is displayed` | `onNodeWithText(title).assertIsDisplayed()` |
| HI-UI-02 | `orderLabel null → order label not shown` | `onNodeWithText("1.")` не найден |
| HI-UI-03 | `subtitleCount null → count not shown` | Нет узла с count текстом |
| HI-UI-04 | `onClick fires when clicked` | Lambda вызван |
| HI-UI-05 | `onLongClick null → combinedClickable without long click semantic` | Нет onLongClick semantics |
| HI-UI-06 | `onLongClick non-null → long press fires` | Lambda вызван при `performLongClick()` |

### 9.3 QuestCard Long-Press Menu

**Файл**: `QuestCardMenuTest.kt`

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| QC-UI-01 | `long press opens DropdownMenu` | Меню видно (`onNodeWithTag("quest_menu").assertIsDisplayed()`) |
| QC-UI-02 | `tap outside closes menu` | Меню скрыто |
| QC-UI-03 | `menu closed before share intent fired` | Порядок: expandedQuestId=null → startActivity (проверяется через порядок composable side effects) |
| QC-UI-04 | `ActivityNotFoundException handled silently` | Нет crash; меню закрыто |
| QC-UI-05 | `haptic feedback fired on long press` | (проверяется через MockHapticFeedback если доступен) |

### 9.4 QuestListScreen

**Файл**: `QuestListScreenTest.kt`

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| QLS-UI-01 | `Loading state shows progress indicator` | `CircularProgressIndicator` или tag виден |
| QLS-UI-02 | `Loaded state shows quest items` | Все quest titles видны |
| QLS-UI-03 | `Empty state shows placeholder` | Плейсхолдер виден |
| QLS-UI-04 | `tap on catalog section fires onCatalogClick` | Lambda с правильным catalogId |
| QLS-UI-05 | `tap on quest fires onQuestClick with correct QuestDisplayItem` | Lambda с `quest.id`, `quest.catalogId`, `quest.title` |

### 9.5 MyQuestsScreen — long-press no-op (AC#11)

**Файл**: `MyQuestsQuestCardNoMenuTest.kt`

> AC#11: existing `MyQuestsScreen` не модифицируется в этой фиче — long-press на QuestCard там не открывает меню.

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| MY-UI-01 | `long press on MyQuestsScreen QuestCard does not open DropdownMenu` (AC#11) | `onNodeWithTag("quest_menu")` не существует после `performLongClick()` на карточке |

### 9.6 Rotation scroll retention (AC#22)

**Файл**: `QuizzesRotationTest.kt`  
**Тип**: Instrumented (ActivityScenario.recreate() = configuration change)

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| ROT-UI-01 | `after recreate() active child config is preserved` (AC#22) | После `scenario.recreate()` → `childStack.active.configuration == original` |
| ROT-UI-02 | `after recreate() LazyColumn scroll position is preserved` | Scroll offset не сбрасывается (instrumented с LazyListState) |

> Эти тесты покрывают configuration change (`ActivityScenario.recreate()`), не process death. Process death = отдельный ручной сценарий (см. §12).

---

## 10. Cross-Feature Integration Tests

**Файл**: `QuizzesRootIntegrationTest.kt`  
**Тип**: JVM unit (DefaultComponentContext, TestMainDispatcher)  
**Цель**: Проверить что DefaultRootComponent корректно создаёт и пробрасывает колбэки в Quizzes.

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| INT-01 | `HomeQuestsComponent.onCatalogClick → QuizzesComponent receives pushNew(QuestList)` | `quizzesComponent.childStack.value.active is QuizzesChild.QuestList` |
| INT-02 | `MyQuestsComponent.onQuestClick → QuizzesComponent receives pushNew(SectionList)` | `active is QuizzesChild.SectionList` |
| INT-03 | `QuizzesComponent.dismissQuizzes → stack=[Idle]` | `active is QuizzesChild.Idle` |
| INT-04 | `Root backCallback lower priority than QuizzesComponent backCallback` | При двух зарегистрированных callback — QuizzesComponent callback вызван первым |
| INT-05 | `QuizzesComponent backCallback disabled when Idle → Root backCallback handles back` | Системный back обрабатывается Root (SystemBack), не Quizzes |

---

## 11. Repository Extension Tests

### 11.1 FakeQuestRepository — observeByCatalog

Три существующих fake-а требуют добавления метода `observeByCatalog`:

| Fake файл | Модуль |
|-----------|--------|
| `FakeQuestRepository.kt` | `android/feature/quest/presentation/src/test/` |
| `FakeQuestRepository.kt` | `shared/feature/quest/domain/commonTest/` |
| `FakeQuestRepository.kt` | `shared/core/sync/src/test/` |

**Файл тестов**: `FakeQuestRepositoryObserveByCatalogTest.kt`

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| RX-01 | `observeByCatalog unknown catalogId emits empty list` | `flow.take(1).toList() == [emptyList()]` |
| RX-02 | `observeByCatalog known catalogId emits matching quests` | Только квесты с соответствующим catalogId |
| RX-03 | `observeByCatalog re-emits when backing data changes` | Второй элемент Flow содержит обновлённые данные |
| RX-04 | `FakeQuestLocalDataSource.observeByCatalog mirrors DAO contract` | Фильтрация по catalogId работает аналогично Room DAO |

### 11.2 Room DAO — QuestDao.observeByCatalog (instrumented)

**Файл**: `QuestDaoByCatalogTest.kt`  
**Тип**: Instrumented (Room in-memory)

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| DAO-01 | `quests with matching catalogId returned` | Только нужные строки |
| DAO-02 | `archived quests excluded` | `archived=1` строки не в результате |
| DAO-03 | `empty table emits empty list, no error` | Пустой список |
| DAO-04 | `insert new quest re-emits via Flow` | Flow обновляется после insert |

---

## 12. StateKeeper Restore Contract Tests

**Файл**: `QuizzesStateKeeperRestoreTest.kt`  
**Тип**: **JVM unit** (JUnit 4 + Decompose `StateKeeperDispatcher`) — НЕ instrumented  

> **Классификация**: Эти тесты верифицируют `ListSerializer(QuizzesConfig.serializer())` round-trip через `StateKeeperDispatcher.save()` / `StateKeeperDispatcher(savedState)`. Это НЕ настоящий process death.
>
> - `ActivityScenario.recreate()` = configuration change, а не process death (Android docs).
> - Реальный process death test потребует UIAutomator + `adb shell am kill` + повторного старта Activity. Разумно пропустить в MVP.
> - Реальная процесс-death восстановление верифицируется **ручным smoke-test сценарием** (per AC#21).

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| PD-01 | `stack [Idle,QuestList,SectionList] saved and restored via StateKeeper` | После воссоздания `childStack.value.items.size == 3` |
| PD-02 | `titles preserved after restoration` | `(active.instance as QuizzesChild.SectionList).titles == original.titles` |
| PD-03 | `restored active config is SectionList (not QuestList or Idle)` | `active.configuration is QuizzesConfig.SectionList` |
| PD-04 | `backCallback enabled after restoration (stack not empty)` | `backCallback.isEnabled == true` |
| PD-05 | `Idle anchor always at stack[0] after restoration` | `items[0].configuration is QuizzesConfig.Idle` |

```kotlin
// Паттерн (pseudocode):
@Test fun `state keeper restore preserves stack`() {
    val stateHolder = StateKeeperDispatcher(null)
    val component = DefaultQuizzesComponent(
        componentContext = DefaultComponentContext(lifecycle, stateKeeper = stateHolder),
        ...fakes
    )
    component.openQuestList(catalogId = CatalogId("cat-1"), catalogName = "Math")
    // open SectionList via child component's onQuestClick...

    val savedState = stateHolder.save()  // serializes stack via ListSerializer(QuizzesConfig.serializer())

    val stateHolder2 = StateKeeperDispatcher(savedState)
    val restored = DefaultQuizzesComponent(
        componentContext = DefaultComponentContext(lifecycle, stateKeeper = stateHolder2),
        ...fakes
    )
    assertEquals(3, restored.childStack.value.items.size)
    assertIs<QuizzesChild.SectionList>(restored.childStack.value.active.instance)
}
```

---

## 13. State Matrix Coverage

### Matrix 1 — Back Navigation

| Row | Scenario | Test ID(s) |
|-----|----------|------------|
| 1 | Stack=[Idle] → System back → SystemBack (handled by Root) | QZ-U-01, INT-05 |
| 2 | Stack=[Idle,A] → System back → pop → Stack=[Idle] | QZ-U-03, QZ-U-05 |
| 3 | Stack=[Idle,A,B] → System back → pop → Stack=[Idle,A] | QZ-U-05 (variant) |
| 4 | Stack=[Idle,A,B,C] → System back → pop → Stack=[Idle,A,B] | QZ-U-05 (variant) |
| 5 | Breadcrumb uiLevel 0 → popTo(1) → Stack=[Idle,A] | SL-U-05, Matrix-COV-01 |
| 6 | Breadcrumb uiLevel 1 → popTo(2) → Stack=[Idle,A,B] | Matrix-COV-02 |
| 7 | Breadcrumb uiLevel 2 → popTo(3) → Stack=[Idle,A,B,C] | Matrix-COV-03 |
| 8 | dismissQuizzes() → popToFirst() → Stack=[Idle] | QZ-U-06, INT-03 |
| 9 | dismissQuizzes() when Stack=[Idle] → noop | QZ-U-07 |

**Дополнительные тесты Matrix 1**:

| ID | Тест |
|----|------|
| Matrix-COV-01 | `breadcrumb segment uiLevel=0 click → popTo(1) → QuestList active` |
| Matrix-COV-02 | `breadcrumb segment uiLevel=1 click → popTo(2) → SectionList active` |
| Matrix-COV-03 | `breadcrumb segment uiLevel=2 click → popTo(3) → ThemeList active` |

### Matrix 2 — Empty/Error States

| Screen | Loading | Loaded | Empty | Error |
|--------|---------|--------|-------|-------|
| QuestList | QL-U-01 | QL-U-02 | QL-U-03 | QL-U-04 |
| SectionList | SL-U-01 | SL-U-02 | SL-U-03 | — (add if spec defines) |
| ThemeList | TH-U-01 | TH-U-02 | TH-U-03 | — |
| LessonList | LL-U-01 | LL-U-02 | LL-U-03 | — |
| LessonPlaceholder | — | LP-U-01 | — | — |

### Matrix 3 — Overlay Visibility

| Stack state | active | AppShellScreen shows Quizzes? | Test ID |
|-------------|--------|-------------------------------|---------|
| [Idle] | QuizzesChild.Idle | No (return early) | QZ-U-08 |
| [Idle, QuestList] | QuizzesChild.QuestList | Yes | QZ-U-09 (variant) |
| [Idle, QuestList, SectionList] | QuizzesChild.SectionList | Yes | INT-01 |
| [Idle, ...deep] | any non-Idle | Yes | — |
| After dismissQuizzes() | QuizzesChild.Idle | No | INT-03 |
| After process death [Idle, SectionList] | QuizzesChild.SectionList | Yes | PD-03 |

---

## 14. Primary User Journeys Coverage

| Journey | Описание | Test ID(s) |
|---------|----------|------------|
| PUJ-1 | Home → tap catalog → QuestList opens | INT-01, QL-U-05 |
| PUJ-2 | QuestList → tap quest → SectionList opens | INT-02, QL-U-06 |
| PUJ-3 | SectionList → tap section → ThemeList opens | SL-U-04 |
| PUJ-4 | ThemeList → tap theme → LessonList opens | TH-U-04 |
| PUJ-5 | LessonList → tap lesson → LessonPlaceholder opens | LL-U-04 |
| PUJ-6 | System back from SectionList → QuestList | QZ-U-05 (variant) |
| PUJ-7 | Breadcrumb tap → jump to level | Matrix-COV-01..03 |
| PUJ-8 | X/dismiss → overlay closes | QZ-U-06, INT-03 |
| PUJ-9 | Long-press quest → share menu opens | QC-UI-01 |
| PUJ-10 | Share intent fires → menu closed first | QC-UI-03 |
| PUJ-11 | Process death → restore stack | PD-01..05 |
| PUJ-12 | Rotation → state preserved (instanceKeeper) | (отдельный тест: `QuizzesRotationTest`) |

---

## 15. Acceptance Criteria Coverage Table

Нумерация AC# строго соответствует `0-spec.md:475-528`.

| AC# | Описание из spec (сокращённо) | Test ID(s) | Тип |
|-----|------------------------------|------------|-----|
| AC#1 | Home catalog tap → QuestList; breadcrumb="{c.name}"; последний сегмент некликабелен; только public non-archived квесты | INT-01, QL-U-01..03, BB-UI-02, DAO-02 | JVM integration + JVM unit + DAO |
| AC#2 | MyQuests selected-catalog quest tap → SectionList; breadcrumb="{c.name} > {q.title}"; sorted by order ASC | INT-02, QL-U-08 | JVM integration + JVM unit |
| AC#3 | QuestListComponent (entry с Home) quest tap → SectionList; breadcrumb="{c.name} > {q.title}"; sorted by order ASC | QL-U-05, QL-U-06, QL-U-07 | JVM unit |
| AC#4 | SectionListComponent section tap → ThemeList; breadcrumb="{c.name} > {q.title} > {s.title}"; sorted ASC | SL-U-04 | JVM unit |
| AC#5 | ThemeListComponent theme tap → LessonList; breadcrumb full path; sorted ASC | TH-U-04 | JVM unit |
| AC#6 | LessonListComponent lesson tap → LessonPlaceholder; breadcrumb full path; placeholder text | LL-U-04, LP-U-01, LP-U-02 | JVM unit |
| AC#7 | System back от любого уровня > первого → pop → breadcrumb обрезается | QZ-U-05 | JVM unit |
| AC#8 | Breadcrumb segment tap → popTo нужного уровня (все более глубокие удаляются) | BB-UI-03, Matrix-COV-01..03 | Compose UI + JVM unit |
| AC#9 | Последний (текущий) сегмент breadcrumb некликабелен | BB-UI-02, BB-UI-04 | Compose UI |
| AC#10 | QuestCard в QuestListComponent long-press → DropdownMenu с «Поделиться» | QC-UI-01 | Compose UI |
| AC#11 | MyQuestsScreen QuestCard long-press → ничего не происходит (existing UI не модифицируется) | MY-UI-01 | Compose UI |
| AC#12 | Меню «Поделиться» tap → `Intent.ACTION_SEND` type="text/plain" text="{title} — {appName}"; меню закрывается | QC-UI-03 | Compose UI |
| AC#13 | Тап вне меню → меню закрывается без action | QC-UI-02 | Compose UI |
| AC#14 | `ActivityNotFoundException` → ловится, логируется; меню закрывается; navigation state не меняется; без UI-уведомлений | QC-UI-04 | Compose UI |
| AC#15 | HierarchyItemCard (Section/Theme/Lesson) long-press → ничего (no menu in MVP) | HI-UI-05 | Compose UI |
| AC#16 | Уровень без детей → empty state «Нет секций/тем/уроков» | QL-U-03, SL-U-03, TH-U-03, LL-U-03, QLS-UI-03 | JVM unit + Compose UI |
| AC#17 | Flow.collect активен; sync обновляет Room → UI перерисовывается; breadcrumb остаётся frozen | SL-U-06, SL-U-07 | JVM unit |
| AC#18 | Sync архивирует родительскую секцию → Flow → empty state «Нет тем» (no auto-pop, no toast) | TH-U-03 (с archived cascade) | JVM unit |
| AC#19 | Fresh install; Room пустая → empty state каталогов; когда sync подтягивает → HomeQuestsScreen обновляется | QL-U-03, QL-U-02 (sequence) | JVM unit |
| AC#20 | Offline; drill-down по закешированным данным → навигация работает без ошибок | QL-U-09 | JVM unit |
| AC#21 | Process death → ChildStack восстанавливается на тот же уровень с тем же breadcrumb | PD-01..05 (StateKeeper JVM) + ручной smoke-test | JVM unit (StateKeeper) + manual |
| AC#22 | Rotation → Components не пересоздаются (instanceKeeper); scroll position сохраняется | ROT-UI-01, ROT-UI-02 | Instrumented |
| AC#23 | Sync переименовал quest → breadcrumb остаётся старым (frozen); список секций обновляется | SL-U-06, SL-U-07, QL-U-10 | JVM unit |
| AC#24 | HierarchyItemCard с orderLabel="1." и subtitleCount=null → отображается корректно | HI-UI-01, HI-UI-02, HI-UI-03 | Compose UI |
| AC#25 | HierarchyItemCard с orderLabel=null и subtitleCount=null → только title | HI-UI-02, HI-UI-03 | Compose UI |
| AC#26 | BreadcrumbBar 3 сегмента → разделяются «>»; последний некликабелен и визуально выделен | BB-UI-01, BB-UI-02, BB-UI-04 | Compose UI |
| AC#27 | Long title в breadcrumb → TextOverflow.Ellipsis, maxLines=1 | BB-UI-05 | Compose UI |
| AC#28 | Tap на сегмент breadcrumb n → ChildStack pop до уровня n | BB-UI-03, Matrix-COV-01..03 | Compose UI + JVM unit |
| AC#29 | DI: `QuizzesPresentationModule` зарегистрирован в AppApplication.kt startKoin; все Components через Koin factory | INT-01 (wiring) + ручная проверка DI | JVM integration + manual |
| AC#30 | Code: новые файлы фичи не импортируют Android/SDK types в shared/feature/*/domain | (grep static check, не тест) | Static |
| AC#31 | Code: ни одна Activity/Fragment не вызывает Repository/UseCase напрямую | (grep static check, не тест) | Static |
| AC#32 | Code: `QuestRepository.observeByCatalog` добавлен в domain interface + data + FakeQuestRepository | RX-01..04, DAO-01..04 | JVM unit + Instrumented DAO |
| AC#33 | Tests: JVM unit тесты для каждого DefaultXxxListComponent (empty→loaded→archived flows; order ASC) | QL-U-*, SL-U-*, TH-U-*, LL-U-* | JVM unit |
| AC#34 | Tests: JVM unit тест для breadcrumb pop logic — `PopBreadcrumbTest` | Matrix-COV-01..03, QZ-U-05..07 | JVM unit |
| AC#35 | Tests: JVM unit тест для LessonPlaceholderComponent (правильный title в state) | LP-U-01, LP-U-02 | JVM unit |
| AC#36 | Tests: Compose UI тесты для BreadcrumbBar + HierarchyItemCard | BB-UI-01..06, HI-UI-01..06 | Compose UI |
| AC#37 | Tests: Compose UI тест для QuestListComponent long-press → menu → Share → Intent | QC-UI-01..05 | Compose UI |
| AC#38 | Build: `./gradlew assemble --no-configuration-cache` зелёный | build gate | CI |
| AC#39 | Tests: `./gradlew allTests --no-configuration-cache` зелёный | build gate | CI |

---

## 16. Test Infrastructure

### Existing Fakes (переиспользуются)

| Fake | Расположение | Используется в |
|------|--------------|----------------|
| `FakeSectionRepository` | `shared/feature/section/domain/commonTest/` | SL-U-*, INT-* |
| `FakeThemeRepository` | `shared/feature/theme/domain/commonTest/` | TH-U-*, INT-* |
| `FakeLessonRepository` | `shared/feature/lesson/domain/commonTest/` | LL-U-*, INT-* |
| `FakeQuestRepository` (3 копии) | см. §11.1 | QL-U-*, INT-*, RX-* |
| `FakeAuthRepository` | `android/feature/auth/` | (не используется в quizzes напрямую) |
| `FakeCatalogRepository` | `android/feature/catalog/` | INT-* (опционально) |
| `FakeNavigator` | `android/feature/*/presentation/src/test/` | QL-U-05..07, SL-U-04, TH-U-04, LL-U-04 |

### New Fakes (создаются в рамках фичи)

| Fake | Расположение | Назначение |
|------|--------------|------------|
| `FakeStackNavigation` | `android/feature/quizzes/presentation/src/test/` | Отслеживание pushNew/popTo вызовов |
| `FakeBackDispatcher` | `android/feature/quizzes/presentation/src/test/` | Верификация BackCallback registration/priority |

```kotlin
// FakeStackNavigation (pseudocode):
// Implements StackNavigation<C> (полный тип, который принимают constructors child components)
// StackNavigation реализует StackNavigator — это его родитель.
class FakeStackNavigation : StackNavigation<QuizzesConfig> {
    val pushedConfigs = mutableListOf<QuizzesConfig>()
    val poppedToIndices = mutableListOf<Int>()
    val popsCount get() = popCalls
    private var popCalls = 0

    override fun navigate(transformer: (List<QuizzesConfig>) -> List<QuizzesConfig>, onComplete: (newStack: List<QuizzesConfig>, oldStack: List<QuizzesConfig>) -> Unit) {
        // record intent; delegate to in-memory stack for correctness if needed
    }
}
```

> Note: `StackNavigation<C>` — interface в Decompose 3.1.0 (`StackNavigatorExt.kt:34-41`, verified Codex pass-1 review). Child component constructors принимают `StackNavigation<QuizzesConfig>` (canonical per §13 `06-api-contract.md`). `FakeStackNavigation : StackNavigation<QuizzesConfig>` реализуется напрямую без делегата.

### TestComponentContext

Используется `DefaultComponentContext` из Decompose с `TestLifecycle` (Essenty) и `StateKeeperDispatcher` для process death тестов.

### Coroutines

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultQuestListComponentTest {
    private val testScheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(testScheduler)

    @Test fun `when repository emits list then state is Loaded`() = runTest(dispatcher) {
        val fake = FakeQuestRepository()
        val component = DefaultQuestListComponent(..., coroutineContext = dispatcher)
        fake.emitQuests(listOf(questFixture()))
        advanceUntilIdle()
        assertIs<QuestListUiState.Loaded>(component.uiState.value)
    }
}
```

---

## 17. Build Commands

```bash
# JVM unit тесты модуля quizzes (после создания модуля)
./gradlew :android:feature:quizzes:presentation:test --no-configuration-cache

# Все JVM тесты проекта
./gradlew test --no-configuration-cache

# Instrumented тесты (process death, DAO) — требуется подключённое устройство
./gradlew :android:feature:quizzes:presentation:connectedDebugAndroidTest

# Сборка instrumented APK без устройства
./gradlew :android:feature:quizzes:presentation:assembleDebugAndroidTest --no-configuration-cache

# Shared module тесты (FakeRepository coverage)
./gradlew :shared:feature:quest:domain:jvmTest --no-configuration-cache
./gradlew :shared:core:sync:jvmTest --no-configuration-cache
```

---

## Open Questions

| # | Вопрос | Блокирует |
|---|--------|-----------|
| OQ-04-01 | `REQUIRES verify`: `BackCallback.PRIORITY_OVERLAY` — точная числовая константа в Essenty зависит от версии. Проверить в `BackCallback.kt` перед реализацией `DefaultQuizzesComponent`. | QZ-U-04, QZ-U-05 |
| OQ-04-02 | RESOLVED: `StackNavigation<C>` — interface в Decompose 3.1.0 (verified Codex review). `FakeStackNavigation : StackNavigation<QuizzesConfig>` реализуется напрямую. | — |
| OQ-04-03 | AC#22 (Rotation): `instanceKeeper` retention зависит от того, что именно хранится в компонентах. Если DefaultQuestListComponent хранит только Flow — `selectedState` не переживёт ротацию. Уточнить в реализации перед написанием теста. | QuizzesRotationTest |
| OQ-04-04 | `QuestDisplayItem.catalogId` field (QL-U-08, AC#3) — не существует в текущем `QuestDisplayItem.kt:14-20`. Расширение модели зафиксировано в `06-api-contract.md §3` (frontend-dev ownership на phase-01). | QL-U-08 |
