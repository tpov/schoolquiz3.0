---
phase: 06
name: Long-Press Share Menu
complex: false
---

# Phase-06 Overview: Long-Press Share Menu

## Goal

Реализовать long-press контекстное меню в `QuestListScreen` с единственным пунктом «Поделиться»: `DropdownMenu`, `Intent.ACTION_SEND`, `createChooser`, `ActivityNotFoundException` guard. Заменить Phase-05 stub `onLongClick=null` на реализацию. Intent dispatch — в `QuestListScreen` (UI layer через `LocalContext.current`), не в `DefaultQuestListComponent` (нет Context в presentation layer, per ADR-QS-08: `03-decisions.md:293`).

## Scope

- `QuestListScreen.kt` — добавить `DropdownMenu`, обновить `QuestCard(onLongClick = { expandedQuestId = it })`, dispatch `Intent.ACTION_SEND` через `LocalContext.current.startActivity(...)` в DropdownMenuItem.onClick
- `DefaultQuestListComponent.onShareClick` — остаётся stub `Unit` или удаляется из interface если screen dispatches напрямую (frontend-dev выбирает consistent подход — см. `phase-06/frontend.md`)
- Без изменений в `MyQuestsScreen` — AC#11: existing экраны не модифицируются

## Role Inputs

- `frontend.md` — задачи frontend-dev
- `tests.md` — задачи test-dev (Compose UI instrumented)

(нет `backend.md`)

## Layer

`ui` — `QuestListScreen.kt` (UI-local state + Intent dispatch via `LocalContext.current`). `DefaultQuestListComponent.onShareClick` может оставаться stub если screen dispatches напрямую.

## Review Tags

нет специальных review тегов — DropdownMenu + Intent dispatch синхронные, нет coroutines

## State Matrix Coverage

**Matrix 1 (Tap Actions):**
- Quest (QuestListComponent) long-press: `expandedQuestId = quest.id` → DropdownMenu opens
- Share tap в DropdownMenu: `expandedQuestId = null` → Intent.createChooser dispatched

## Domain Contract Coverage

N/A

## Traceability

| Problem (from 2-grounding.md) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|-------------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 5 (combinedClickable first usage) | frontend-dev | `QuestCard.kt` (Phase-02 заменил clickable → combinedClickable); `QuestListScreen.kt` передаёт `onLongClick` | `06-api-contract.md:318, §15` — `combinedClickable` если `onLongClick != null` | Передать `onLongClick = { expandedQuestId = it }` в QuestCard; реализовать DropdownMenu в QuestListScreen | QC-UI-01..05 |
| Problem 5 (standalone DropdownMenu first usage) | frontend-dev | `QuestListScreen.kt` | `06-api-contract.md:628` — expandedQuestId UI-local (ADR-QS-07) | `DropdownMenu(expanded=expandedQuestId==quest.id)` в `Box` wrapper вокруг `QuestCard` | QC-UI-01..05 |

## New Files

none

## Modified Files

- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/QuestListScreen.kt` — добавить DropdownMenu + обновить `QuestCard(onLongClick = ...)` + dispatch Intent через `LocalContext.current`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultQuestListComponent.kt` — stub `onShareClick` остаётся или удаляется из interface (frontend-dev decision; Intent dispatched from screen, not component)

## Deleted Files

none

## Dependencies

- Phase-02 completed: `QuestCard` с `onLongClick` nullable parameter существует
- Phase-04 completed: `DefaultQuestListComponent.onShareClick` stub существует
- Phase-05 completed: `QuestListScreen` с `expandedQuestId` var объявлен

## Acceptance Criteria

1. Long-press на `QuestCard` в `QuestListScreen` → `DropdownMenu` visible с пунктом «Поделиться» (AC#10).
2. Тап на «Поделиться» → меню закрывается (`expandedQuestId = null`) → `startActivity(Intent.createChooser(...))` (AC#15, AC#16).
3. `expandedQuestId` — `remember { mutableStateOf<QuestId?>(null) }` в `QuestListScreen`, не в UiState (ADR-QS-07).
4. `DropdownMenu` закрывается при тапе вне (`onDismissRequest = { expandedQuestId = null }`) (AC#14).
5. `ActivityNotFoundException` перехватывается тихо (`Log.w`); нет crash, нет Toast (AC#15).
6. Порядок: `expandedQuestId = null` ПЕРЕД `startActivity` (ADR-QS-08) (AC#16).
7. `MyQuestsScreen` не изменяется — `QuestCard(onLongClick=null)` там (AC#11).
8. `HierarchyItemCard` в SectionListScreen/ThemeListScreen/LessonListScreen — без long-press меню (AC#12); `onLongClick=null` в этих screens.
9. Share intent format: `"Квест «{quest.title}» — {appName}"` (AC#38).

## Tests Required (TDD-style)

Compose UI instrumented + Compose test для Intent interaction:

- `QuestCardMenuTest` (QC-UI-01..05):
  - `QC-UI-01`: long press opens DropdownMenu
  - `QC-UI-02`: tap outside closes menu
  - `QC-UI-03`: menu closed before share intent fired (порядок side effects)
  - `QC-UI-04`: ActivityNotFoundException handled silently
  - `QC-UI-05`: haptic feedback на long press

- `MyQuestsQuestCardNoMenuTest` (MY-UI-01):
  - long press в MyQuestsScreen не открывает menu

## Validation

| # | Command | Expected |
|---|---------|----------|
| 1 | `./gradlew :android:feature:quizzes-screen:presentation:compileDebugKotlin --no-configuration-cache` | passes — compile clean |
| 2 | `./gradlew :android:feature:quizzes-screen:presentation:assembleDebugAndroidTest --no-configuration-cache` | passes — instrumented APK build |
| 3 | `./gradlew :android:feature:quizzes-screen:presentation:connectedDebugAndroidTest --no-configuration-cache` | passes **(requires connected device)** — QuestCardMenuTest (QC-UI-01..05) + MyQuestsQuestCardNoMenuTest (MY-UI-01) |
| 4 | `grep -n "component\.onShareClick" android/feature/quizzes-screen/presentation/src/main/kotlin/ -r --include="*.kt"` | empty — share dispatch is UI-only, no component call |
| 5 | `grep -n "combinedClickable" android/feature/quizzes-screen/presentation/src/main/kotlin/ -r --include="*.kt"` | at least 1 match in QuestCard (Phase-02) or QuestListScreen |

## Handoff Notes

- `combinedClickable` — `@OptIn(ExperimentalFoundationApi::class)` НЕ требуется. Verified: BOM 2024.09.02 → `compose-foundation ~1.7.x`; stable since 1.4.0. Evidence: `gradle/libs.versions.toml:33`.
- Intent dispatch через `LocalContext.current.startActivity(...)` — стандартный Compose паттерн.
- `appName` через `context.applicationInfo.loadLabel(context.packageManager).toString()`.
- DropdownMenu в Phase-06 — `Box { QuestCard(...); DropdownMenu(expanded=expandedQuestId == quest.id, onDismissRequest={expandedQuestId=null}) { DropdownMenuItem(...) } }` в `items()` lambda LazyColumn.
