---
phase: 06
role: test-dev
---

# Phase-06 Test Tasks: Long-Press Share Menu

### Pattern Invariants

- `FakeQuestListComponent` из Phase-05 используется для Compose UI тестов. Ref: `phase-05/frontend.md` (FakeQuestListComponent Signature Card).
- DropdownMenu тестируется через Compose semantic tree — не UIAutomator. Ref: `.claude/rules/testing.md` (Compose UI Test framework).
- Intent dispatch тестируется через side effect capture (порядок: menu dismissed first, then share callback). Если Espresso Intents доступен — через `Intents.intended()`. Ref: `04-testing.md §9.3` (QC-UI-03..04).
- `ActivityNotFoundException` тест — stub context throwing exception; verify нет crash. Ref: `06-api-contract.md:318` (ActivityNotFoundException handling).
- `MY-UI-01` (MyQuestsScreen no menu) — regression test через existing `MyQuestsScreen` test infrastructure. Ref: `android/feature/quest/presentation/src/main/.../ui/MyQuestsScreen.kt:85`.

---

## Create QuestCardMenuTest

- **Файл:** `android/feature/quizzes-screen/presentation/src/androidTest/kotlin/.../screen/QuestCardMenuTest.kt`
- **Тип:** Compose UI instrumented test (`@RunWith(AndroidJUnit4::class)`)
- **Сигнатура:** `@RunWith(AndroidJUnit4::class) class QuestCardMenuTest`
- **Вход:** `QuestListScreen` с `FakeQuestListComponent(QuestListUiState.Loaded(quests))`
- **Поведение / Выход (из `04-testing.md §9.3`):**

  **QC-UI-01**: `long press opens DropdownMenu`
  - given: `QuestListScreen` with one quest `questA`
  - when: user long-presses the node with `questA.title`
  - then: text "Поделиться" becomes visible

  **QC-UI-02**: `tap outside closes menu`
  - given: DropdownMenu is open (QC-UI-01 precondition)
  - when: user taps outside the menu area (node tagged "quest_list_screen" or screen background)
  - then: text "Поделиться" is no longer present in the composition

  **QC-UI-03**: `menu closed before share intent fired`
  - given: DropdownMenu is open; `FakeQuestListComponent` tracks `onShareClickCalled`
  - when: user taps "Поделиться"
  - then: the menu dismisses first (text "Поделиться" absent) before `onShareClick` is recorded; `fakeComponent.onShareClickCalled` equals `questA`

  **QC-UI-04**: `ActivityNotFoundException handled silently`
  - given: `QuestListScreen` where the underlying `startActivity` call will throw `ActivityNotFoundException` (injected via context stub or component fake)
  - when: user taps "Поделиться"
  - then: no crash occurs; test completes successfully; no Toast is shown

  **QC-UI-05**: `haptic feedback fired on long press`
  - given: `QuestCard` with non-null `onLongClick`
  - when: user long-presses the card
  - then: haptic feedback is triggered (verified via `MockHapticFeedback` if available in the Compose version; otherwise skip with `@Ignore` and a comment)

- **Edge cases:**
  - Multiple quests — long press on first quest opens menu for that quest only; second quest's QuestCard не имеет открытого menu
  - DropdownMenu `onDismissRequest` — системный back кнопка также должен закрывать menu (verify если Compose UI Test поддерживает)
- **Depends on:** `QuestListScreen`, `FakeQuestListComponent`, Compose UI Test, `DropdownMenu` semantics
- **Canonical reference:** `04-testing.md §9.3`
- **Rationale:** AC#10 (DropdownMenu opens), AC#14 (dismiss), AC#15 (ActivityNotFoundException), AC#16 (share intent) UI coverage.

---

## Create MyQuestsQuestCardNoMenuTest

- **Файл:** `android/feature/quest/presentation/src/androidTest/kotlin/.../screen/MyQuestsQuestCardNoMenuTest.kt`
  - Или добавить в существующий `MyQuestsScreenTest.kt` если существует
- **Тип:** Compose UI instrumented test
- **Сигнатура:** `@RunWith(AndroidJUnit4::class) class MyQuestsQuestCardNoMenuTest`
- **Вход:** `MyQuestsScreen` с fake `MyQuestsComponent` + loaded quests
- **Поведение / Выход (из `04-testing.md §9.5`):**

  **MY-UI-01**: `long press on MyQuestsScreen QuestCard does not open DropdownMenu`
  - given: `MyQuestsScreen` with a QuestCard in Loaded state
  - when: user long-presses the QuestCard (by title or test tag)
  - then: text "Поделиться" does not appear; node tagged "quest_menu" does not exist in the composition

- **Edge cases:**
  - `MyQuestsScreen` использует `QuestCard(onLongClick=null)` — verify что это не изменилось после Phase-02/06
  - Verify через grep: `MyQuestsScreen.kt` не передаёт `onLongClick` в `QuestCard`
- **Depends on:** `MyQuestsScreen`, fake `MyQuestsComponent`, `QuestCard` (с backward-compat null onLongClick)
- **Canonical reference:** `04-testing.md §9.5`, AC#11
- **Rationale:** AC#11 (existing screens не изменяются). Regression test — убеждаемся что QuestCard изменение backward compatible.
