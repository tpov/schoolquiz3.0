---
phase: 06
role: frontend-dev
---

# Phase-06 Frontend Tasks: Long-Press Share Menu

### Pattern Invariants

- `expandedQuestId: QuestId?` — UI-local state в `QuestListScreen`. ЗАПРЕЩЕНО переносить в `QuestListUiState` (ADR-QS-07: `03-decisions.md:280`). Pattern: `var expandedQuestId by remember { mutableStateOf<QuestId?>(null) }`.
- Порядок в DropdownMenuItem.onClick: `expandedQuestId = null` ПЕРЕД `startActivity(...)` (ADR-QS-08: `03-decisions.md:307`). Нарушение порядка — меню остаётся visible во время Chooser.
- `Modifier.combinedClickable` используется ТОЛЬКО если `onLongClick != null` в `QuestCard` (Phase-02 паттерн). В Phase-06 `QuestListScreen` — передаём non-null lambda. Ref: `06-api-contract.md:318`.
- `ActivityNotFoundException` catch — ОБЯЗАТЕЛЕН. Silent catch `Log.w`. Нет Toast, нет Snackbar (AC#15). Ref: `03-decisions.md:293` (ADR-QS-08).
- `DropdownMenu` — standalone Material3 `DropdownMenu`, не `ExposedDropdownMenuBox`. Первое использование standalone DropdownMenu в проекте. Ref: `06-api-contract.md:628`.
- `HierarchyItemCard` в SectionList/ThemeList/LessonList — `onLongClick = null` (AC#12); не меняется. Ref: `06-api-contract.md:677`.
- `MyQuestsScreen` — НЕ ИЗМЕНЯЕТСЯ (AC#11); `QuestCard(onLongClick=null)` там. Ref: `android/feature/quest/presentation/src/main/.../ui/MyQuestsScreen.kt:85`.

---

## Update QuestListScreen — add DropdownMenu + onLongClick + Intent dispatch

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/QuestListScreen.kt`
- **Тип:** Composable function (modification)
- **Сигнатура:** existing `@Composable fun QuestListScreen(component: QuestListComponent, onSegmentClick: (Int) -> Unit)`
- **Вход:** N/A (modification)
- **Поведение / Выход:**
  - Добавить `val context = LocalContext.current` выше `when(uiState)`
  - Убедиться что `var expandedQuestId by remember { mutableStateOf<QuestId?>(null) }` объявлен (Phase-05 добавил)
  - В `QuestListUiState.Loaded(quests)` `items()` блок обернуть каждый QuestCard в `Box`:
    - `QuestCard(item=quest, onClick={component.onQuestClick(quest)}, onLongClick={expandedQuestId=it})`
    - `DropdownMenu(expanded=expandedQuestId==quest.id, onDismissRequest={expandedQuestId=null})`
    - `DropdownMenuItem(text={Text("Поделиться")}, onClick=<see below>)`
  - `DropdownMenuItem.onClick` — pure UI lambda, НЕ вызов `component.onShareClick(...)`:
    1. `expandedQuestId = null` — ПЕРВЫМ, до всего (ADR-QS-08: `03-decisions.md:307`)
    2. Построить `shareText = "Квест «${quest.title}» — $appName"` (AC#37 share format)
    3. `val intent = Intent(Intent.ACTION_SEND)` с `type="text/plain"`, `EXTRA_TEXT=shareText`
    4. `try { context.startActivity(Intent.createChooser(intent, null)) } catch (e: ActivityNotFoundException) { Log.w(TAG, e) }`
  - `appName` = `context.applicationInfo.loadLabel(context.packageManager).toString()`
  - `component.onShareClick(quest)` — НЕ ВЫЗЫВАЕТСЯ. Intent dispatch полностью в UI layer (`QuestListScreen`). `DefaultQuestListComponent.onShareClick` остаётся stub `Unit` (или удалить из interface — см. Open Questions в README).
- **Edge cases:**
  - `expandedQuestId == quest.id` — сравнение по value equality корректно для `QuestId` data class
  - `ActivityNotFoundException` — silent catch `Log.w`; нет Toast, нет Snackbar (AC#14)
  - Порядок: `expandedQuestId = null` ПЕРЕД `startActivity` — menu закрывается до Chooser появляется (ADR-QS-08)
- **Depends on:** `QuestCard` (Phase-02), `QuestDisplayItem`, `QuestId`, Material3 `DropdownMenu`, `DropdownMenuItem`, `LocalContext`, `Intent.ACTION_SEND`, `ActivityNotFoundException`
- **Canonical reference:** `06-api-contract.md:318`
- **Rationale:** `DefaultQuestListComponent` — presentation layer, не имеет `Context`. Screen (UI layer) dispatch Intent через `LocalContext.current`. `component.onShareClick` удалён из flow — ADR-QS-08 (`03-decisions.md:293`).

---

## Update DefaultQuestListComponent.onShareClick — keep as stub

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultQuestListComponent.kt`
- **Тип:** class (no-change or removal)
- **Сигнатура:** existing `override fun onShareClick(quest: QuestDisplayItem)` в `DefaultQuestListComponent`
- **Вход:** N/A
- **Поведение / Выход:**
  - `onShareClick` остаётся stub body `Unit` — тело не заполняется. Intent dispatch полностью перенесён в `QuestListScreen.DropdownMenuItem.onClick` (см. Signature Card выше).
  - Если `QuestListComponent` interface (см. `06-api-contract.md:529`) содержит `fun onShareClick(quest: QuestDisplayItem)` — метод остаётся в interface как no-op. Frontend-dev может предложить удалить из interface через Open Question (план/README.md Open Questions #6).
  - Компонент НЕ имеет `Context` и НЕ вызывает `startActivity` — это architectural invariant (ADR-QS-08: `03-decisions.md:293`).
- **Edge cases:**
  - Если interface удаляет `onShareClick` — все реализации `QuestListComponent` (DefaultQuestListComponent + FakeQuestListComponent) должны синхронно удалить override.
- **Depends on:** `QuestListComponent` interface
- **Canonical reference:** `06-api-contract.md:529`
- **Rationale:** Presentation layer не содержит Android `Context` — architectural boundary. Screen (UI layer) dispatches Intent напрямую. Этот Signature Card документирует намеренное НЕ-изменение.
