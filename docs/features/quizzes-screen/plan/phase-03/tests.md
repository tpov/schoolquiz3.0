---
phase: 03
role: test-dev
---

# Phase-03 Test Tasks: Skeleton Tests

### Pattern Invariants

- Decompose `DefaultComponentContext` с `TestLifecycle` (Essenty) для JVM unit тестов.
- `StateKeeperDispatcher(null)` для initial; `StateKeeperDispatcher(savedState)` для restore.
- `StandardTestDispatcher` + `advanceUntilIdle()` для coroutine-based assertions.
- `FakeQuestRepository` и другие fakes — из Phase-01 и Phase-02 updates.

---

## Create DefaultQuizzesComponentTest

- **Файл:** `android/feature/quizzes-screen/presentation/src/test/kotlin/.../DefaultQuizzesComponentTest.kt`
- **Тип:** JVM unit test (JUnit 4)
- **Сигнатура:** `class DefaultQuizzesComponentTest`
- **Вход:** `DefaultQuizzesComponent` с fake repositories, `TestComponentContext`
- **Поведение / Выход (из `04-testing.md §2`):**

  **Back Callback lifecycle (QZ-U-01..05)**:
  - `when stack=[Idle] then backCallback disabled`: given fresh component, then backCallback.isEnabled == false
  - `when pushNew(QuestList) then backCallback enabled`: given openQuestList("cat-1", "Math"), then backCallback.isEnabled == true
  - `when pop to Idle then backCallback disabled`: given stack=[Idle,QuestList], when navigation.pop(), then backCallback.isEnabled == false
  - `backCallback priority equals PRIORITY_OVERLAY`: given component, then backCallback.priority == BackCallback.PRIORITY_OVERLAY (or 100 if constant absent)
  - `when backCallback invoked and stack=[Idle,QuestList] then stack=[Idle]`: given stack size 2, when backCallback fires, then active is QuizzesChild.Idle

  **dismissQuizzes (QZ-U-06..07)**:
  - `when stack=[Idle,QuestList,SectionList] dismissQuizzes() collapses to [Idle]`: then items.size == 1
  - `when stack=[Idle] dismissQuizzes() is noop`: then items.size == 1, no exception

  **ChildStack initial state (QZ-U-08..09)**:
  - `initial stack contains exactly Idle`: items.size == 1; active is QuizzesChild.Idle
  - `Idle childFactory returns QuizzesChild.Idle`: createChild(QuizzesConfig.Idle, ctx) is QuizzesChild.Idle

  **popToLevel**:
  - `popToLevel(0) with stack=[Idle,QuestList,SectionList] → stack=[Idle,QuestList]`: navigation.popTo(1) → active is QuestList
  - `popToLevel(1) with stack=[Idle,QuestList,SectionList,ThemeList] → stack=[Idle,QuestList,SectionList]`

- **Edge cases:**
  - `openQuestList` при `stack=[Idle]` → push; при повторном вызове с тем же config → Decompose `pushNew` no-ops для дубликатов (verify Decompose behavior)
- **Depends on:** `DefaultQuizzesComponent`, `FakeQuestRepository` (Phase-01), `DefaultComponentContext`, `LifecycleRegistry`
- **Canonical reference:** `04-testing.md §2`
- **Rationale:** AC#7 (back), AC#8 (breadcrumb pop), QZ-U-01..09.

---

## Create QuizzesConfigSerializationTest

- **Файл:** `android/feature/quizzes-screen/presentation/src/test/kotlin/.../QuizzesConfigSerializationTest.kt`
- **Тип:** JVM unit test (JUnit 4 + kotlinx.serialization)
- **Сигнатура:** `class QuizzesConfigSerializationTest`
- **Вход:** `Json` instance, `QuizzesConfig` variants
- **Поведение / Выход (из `04-testing.md §8`):**
  - `SER-01`: `QuizzesConfig.Idle` round-trip — data object без полей
  - `SER-02`: `QuizzesConfig.QuestList(catalogId="cat-1", titles=["Математика"])` round-trip
  - `SER-03`: `QuizzesConfig.SectionList(questId="q-1", titles=["Математика","Квест 1"])` round-trip
  - `SER-04`: `QuizzesConfig.ThemeList(sectionId="s-1", titles=["Математика","Квест 1","Секция 1"])` round-trip
  - `SER-05`: `QuizzesConfig.LessonList(themeId="t-1", titles=[...4 elements])` round-trip
  - `SER-06`: `QuizzesConfig.LessonPlaceholder(lessonId="l-1", lessonTitle="Урок 1", titles=[...5 elements])` round-trip — verify `lessonTitle` preserved
  - `SER-07`: stack `[Idle, QuestList, SectionList]` via `ListSerializer(QuizzesConfig.serializer())` round-trip
  - `SER-08`: titles with кириллица survive — no encoding corruption
  - `SER-09`: empty `titles = emptyList()` survives
  - `SER-10`: crafted JSON with missing required field → `SerializationException` thrown (verify restore wrapper catches it)
  - `SER-11`: unknown discriminator in JSON → restore wrapper returns `listOf(QuizzesConfig.Idle)`
- **Edge cases:**
  - Верификация `lessonTitle` в LessonPlaceholder — отдельное поле, не просто последний элемент titles
- **Depends on:** `QuizzesConfig`, `kotlinx.serialization.json.Json`
- **Canonical reference:** `04-testing.md §8`, ADR-QS-02
- **Rationale:** AC#21 (process death) — serialization round-trip — fundamental guarantee.

---

## Create QuizzesStateKeeperRestoreTest

- **Файл:** `android/feature/quizzes-screen/presentation/src/test/kotlin/.../QuizzesStateKeeperRestoreTest.kt`
- **Тип:** JVM unit test (JUnit 4 + Decompose StateKeeperDispatcher)
- **Сигнатура:** `class QuizzesStateKeeperRestoreTest`
- **Вход:** `StateKeeperDispatcher`, `DefaultQuizzesComponent`, fake repositories
- **Поведение / Выход (из `04-testing.md §12`):**
  - `PD-01`: stack `[Idle, QuestList, SectionList]` saved via `stateHolder.save()` → restored via `StateKeeperDispatcher(savedState)` → `items.size == 3`
  - `PD-02`: titles preserved after restoration — `(active.configuration as QuizzesConfig.SectionList).titles == originalTitles`
  - `PD-03`: restored active config is SectionList, not QuestList or Idle
  - `PD-04`: backCallback enabled after restoration (`stack.backStack.isNotEmpty() == true`)
  - `PD-05`: Idle anchor always at stack[0] after restoration — `items[0].configuration is QuizzesConfig.Idle`
- **Edge cases:**
  - Restore с corrupted state → fallback to `[Idle]` (tests SER-10/11 + PD extension)
- **Depends on:** `DefaultQuizzesComponent`, `StateKeeperDispatcher` (Decompose), `DefaultComponentContext`, fake repos
- **Canonical reference:** `04-testing.md §12`, ADR-QS-02
- **Rationale:** AC#21 (process death restoration). StateKeeperDispatcher позволяет тестировать save/restore без реального process death.

---

## Test Infrastructure — FakeStackNavigation

- **Файл:** `android/feature/quizzes-screen/presentation/src/test/kotlin/.../fake/FakeStackNavigation.kt`
- **Тип:** test fake class
- **Сигнатура:** `class FakeStackNavigation : StackNavigation<QuizzesConfig>`
- **Вход:** N/A
- **Поведение / Выход:**
  - Implements `StackNavigation<QuizzesConfig>` (или Decompose `StackNavigator<QuizzesConfig>` — verify actual interface)
  - `val pushedConfigs = mutableListOf<QuizzesConfig>()` — track pushNew calls
  - `val poppedToIndices = mutableListOf<Int>()` — track popTo calls
  - `val popCalls: Int` — count navigation.pop() calls
  - Override `navigate(transformer, onComplete)` — record intent, apply transformer to in-memory list for correctness
- **Edge cases:**
  - Verify что `StackNavigation<C>` реализует правильный Decompose interface
- **Depends on:** Decompose `StackNavigation`
- **Canonical reference:** `04-testing.md §16`
- **Rationale:** Позволяет unit-тестировать navigation calls в child components без реального Decompose runtime.

---

## Test Infrastructure — FakeBackDispatcher

- **Файл:** `android/feature/quizzes-screen/presentation/src/test/kotlin/.../fake/FakeBackDispatcher.kt`
- **Тип:** test helper
- **Сигнатура:** `class FakeBackDispatcher` (Essenty `BackDispatcher` or TestLifecycle BackHandler)
- **Вход:** N/A
- **Поведение / Выход:**
  - Позволяет регистрировать `BackCallback` и вручную fire back events
  - Verify registered callbacks' `isEnabled` и `priority`
  - Метод `triggerBack()` для имитации system back
- **Edge cases:**
  - Если Decompose/Essenty предоставляет `TestBackDispatcher` — использовать его
- **Depends on:** Essenty `BackDispatcher`
- **Canonical reference:** `04-testing.md §16`
- **Rationale:** Необходим для QZ-U-04 (priority verification) и QZ-U-05 (back fires pop).
