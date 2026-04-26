---
phase: 05
name: Compose UI Screens
complex: false
---

# Phase-05 Overview: Compose UI Screens

## Goal

Создать Compose UI screens для quizzes drill-down навигации: `QuizzesScreen` (router), `QuestListScreen`, `SectionListScreen`, `ThemeListScreen`, `LessonListScreen`, `LessonPlaceholderScreen`. Экраны подключают компоненты Phase-04 через Decompose `subscribeAsState()`. Без long-press Share меню (Phase-06).

## Scope

- `QuizzesScreen.kt` — router Composable, `when(active)` switch; читает `childStack.subscribeAsState()`
- `QuestListScreen.kt` — Loading/Empty/Loaded states; `QuestCard` list + `BreadcrumbBar`
- `SectionListScreen.kt` — `HierarchyItemCard` list + `BreadcrumbBar`
- `ThemeListScreen.kt` — аналогично SectionListScreen
- `LessonListScreen.kt` — аналогично с уровнем «уроки»
- `LessonPlaceholderScreen.kt` — статический placeholder

## Role Inputs

- `frontend.md` — задачи frontend-dev
- `tests.md` — задачи test-dev (Compose UI instrumented + JVM unit)

(нет `backend.md` — нет Gradle/manifest/AppApplication изменений)

## Layer

`ui` (Compose screens) внутри `android/feature/quizzes-screen/presentation/`

## Review Tags

- нет `concurrency-review` — экраны используют `subscribeAsState()` + `remember { mutableStateOf() }` (UI-local state). Нет coroutines в screens напрямую.

## State Matrix Coverage

**Matrix 1 (Tap Actions):**
- R2: `QuestListScreen` — `QuestCard.onClick` → `component.onQuestClick(quest)` (реализуется в Phase-05 UI layer)
- R3: `SectionListScreen` — `HierarchyItemCard.onClick` → `component.onSectionClick(item)`
- R4: `ThemeListScreen` — `HierarchyItemCard.onClick` → `component.onThemeClick(item)`
- R5: `LessonListScreen` — `HierarchyItemCard.onClick` → `component.onLessonClick(item)`
- Breadcrumb: `BreadcrumbBar.onSegmentClick(i)` → `component.popToLevel(i)` (в QuizzesScreen или caller)

**Matrix 2 (Loading/Loaded/Empty):**
- `QuestListScreen`: Loading → `CircularProgressIndicator`; Empty → «Нет квестов»; Loaded → LazyColumn
- `SectionListScreen` / `ThemeListScreen` / `LessonListScreen`: Loading → progress; Empty → levelLabel text; Loaded → LazyColumn
- `LessonPlaceholderScreen`: Static only — renders lessonTitle + placeholder text

**Matrix 3 (Breadcrumb Path):**
- Все screens рендерят `BreadcrumbBar(titles=component.titles, onSegmentClick=component::popToLevel)` из Phase-02

Matrix rows: [R2, R3, R4, R5, все Loading/Empty/Loaded UI branches, все Breadcrumb render scenarios]

## Domain Contract Coverage

Feature Domain Contract = N/A. Screens потребляют Phase-04 component interfaces.

## Traceability

| Problem (from 2-grounding.md) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|-------------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 1: Drill-down UI rendering (QuizzesScreen router + 5 child screens) | frontend-dev (quizzes-screen/presentation) | `AppShellScreen` → `QuizzesScreen(component)` wiring в Phase-07 | `06-api-contract.md:500, §13, §14, §15` | Создать 6 Composable screens; `when(active)` exhaustive switch | `./gradlew :android:feature:quizzes-screen:presentation:assembleDebugAndroidTest` |
| Problem 1: Breadcrumb tap UI → popToLevel | frontend-dev | `BreadcrumbBar.onSegmentClick(i)` в каждом screen | `06-api-contract.md:392` — `popToLevel(i)` | Pass `component::popToLevel` к `BreadcrumbBar` | Compose UI test BB-UI-03 (Phase-02) + QLS-UI screen tests |

## New Files

- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/QuizzesScreen.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/QuestListScreen.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/SectionListScreen.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/ThemeListScreen.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/LessonListScreen.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/LessonPlaceholderScreen.kt`

## Modified Files

none (Phase-05 только добавляет новые файлы)

## Deleted Files

none

## Dependencies

- Phase-02 completed: `BreadcrumbBar`, `HierarchyItemCard`, `QuestCard` (extended) существуют в designsystem
- Phase-03 completed: `QuizzesComponent`, `QuizzesChild`, `QuizzesConfig` существуют
- Phase-04 completed: все 5 child component interfaces + DefaultXxx + UiState types существуют

## Acceptance Criteria

1. `QuizzesScreen` — exhaustive `when(active)` по `QuizzesChild`; каждый child рендерит соответствующий screen.
2. `QuestListScreen` рендерит `CircularProgressIndicator` при `Loading`, пустой placeholder при `Empty`, `LazyColumn` с `QuestCard` при `Loaded`.
3. `SectionListScreen`, `ThemeListScreen`, `LessonListScreen` аналогично: Loading / Empty (levelLabel) / Loaded (LazyColumn с `HierarchyItemCard`).
4. `LessonPlaceholderScreen` рендерит `lessonTitle` и placeholder текст по центру.
5. `BreadcrumbBar(titles=component.titles, onSegmentClick=...)` присутствует во всех 5 drill-down screens (включая QuizzesScreen или каждый индивидуально — решает frontend-dev).
6. `onLongClick` на `QuestCard` в `QuestListScreen` — Phase-06 stub: `null` (не передаётся, не открывает меню). Phase-06 заменит.
7. Нет прямых imports из `quest/presentation` или `app-shell/presentation`.
8. Каждый screen файл содержит `@Preview` annotated composable (BrandComponentsInvariantsTest не сканирует `screen/`, но good practice; ОБЯЗАТЕЛЕН если файл попадает в designsystem scan — verify).

## Tests Required (TDD-style)

Compose UI instrumented тесты (Compose UI Test junit4):

- `QuestListScreenTest`:
  - `QLS-UI-01`: Loading state → CircularProgressIndicator visible
  - `QLS-UI-02`: Loaded state → quest titles visible
  - `QLS-UI-03`: Empty state → placeholder visible
  - `QLS-UI-05`: tap on quest card → onQuestClick called with correct item

- `SectionListScreenTest`:
  - Loading, Loaded, Empty states
  - tap section → onSectionClick fired

- `LessonPlaceholderScreenTest`:
  - lessonTitle visible in rendered output
  - placeholder text visible

- `QuizzesRotationTest` (instrumented — AC#22):
  - `ROT-UI-01`: after `scenario.recreate()` active child config preserved

## Validation

| # | Command | Expected |
|---|---------|----------|
| 1 | `./gradlew :android:feature:quizzes-screen:presentation:compileDebugKotlin --no-configuration-cache` | passes — compile clean |
| 2 | `./gradlew :android:feature:quizzes-screen:presentation:assembleDebugAndroidTest --no-configuration-cache` | passes — instrumented APK build |
| 3 | `./gradlew :android:feature:quizzes-screen:presentation:connectedDebugAndroidTest --no-configuration-cache` | passes **(requires connected device)** — QuestListScreenTest + SectionListScreenTest + LessonPlaceholderScreenTest + QuizzesRotationTest (ROT-UI-01) |
| 4 | `grep -rE "^import .*android\.feature\.(quest\|app_shell)\.presentation" android/feature/quizzes-screen/presentation/src/main/ --include="*.kt"` | empty — no cross-feature imports (Invariant 3) |

## Deferred Tests

- `INT-01..05` (QuizzesRootIntegrationTest) — deferred to Phase-07. Функционально покрыто `DefaultQuizzesComponentTest` QZ-U-01..12 (openQuestList, dismissQuizzes, backCallback priority). Phase-07 = natural phase для cross-feature wiring + cross-component integration tests.
- `QLS-UI-04/05 numbering` — overview.md использует QLS-UI-05 для tap, tests.md/код = QLS-UI-04. Impact на трассировку только (не на функциональность). Оставить как есть.
- `ROT-UI-01` — проверяет no-crash on recreate + fake state consistency. Real Decompose instanceKeeper retention requires custom ComponentActivity host — TODO post-MVP.

## Handoff Notes

- Phase-07 добавит QuizzesScreen в AppShellScreen wiring. Phase-05 screens готовы к интеграции но не подключены к AppShellScreen.
- `expandedQuestId: QuestId?` — UI-only state в `QuestListScreen`: `var expandedQuestId by remember { mutableStateOf<QuestId?>(null) }`. НЕ в UiState (ADR-QS-07). Phase-06 добавит DropdownMenu использующий это состояние. Phase-05 декларирует `var expandedQuestId = ...` (unused в Phase-05) для удобства Phase-06 без конфликта.
- `QuestCard(item=quest, onClick={ component.onQuestClick(quest) }, onLongClick=null)` — Phase-06 заменит `null` на `{ expandedQuestId = it }`.
