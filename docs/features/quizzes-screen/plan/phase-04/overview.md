---
phase: 04
name: Drill-down Child Components
complex: true
---

# Phase-04 Overview: Drill-down Child Components

## Goal

Реализовать 5 дочерних компонентов (`DefaultQuestListComponent`, `DefaultSectionListComponent`, `DefaultThemeListComponent`, `DefaultLessonListComponent`, `DefaultLessonPlaceholderComponent`) с UI-state типами, domain→UI маппинг-функциями и заменой stub-заглушек из Phase-03 полными реализациями. Обновить `childFactory` в `DefaultQuizzesComponent`.

## Scope

- 5 новых DefaultXxx implementation классов в `android/feature/quizzes-screen/presentation/`
- 4 UI state типа: `QuestListUiState`, `HierarchyListUiState`, `LessonPlaceholderUiState`, `HierarchyItemUi`
- 3 mapper extension функции: `Section.toDrillItem()`, `Theme.toDrillItem()`, `Lesson.toDrillItem()`
- 1 local Quest→QuestDisplayItem mapper (`QuestToDisplayItemMapper.kt`) — Option A resolved (see Options Considered)
- Обновление `DefaultQuizzesComponent.createChild()` — замена Phase-03 stub на полный `when` блок
- Обновление 5 stub child interfaces в Phase-03 на полные (если стабы были пустыми — добавить `val state` и click handlers согласно `06-api-contract.md:529`)

## Role Inputs

- `frontend.md` — задачи frontend-dev
- `tests.md` — задачи test-dev

(нет `backend.md` — эта фаза не меняет Gradle scaffold, AndroidManifest или AppApplication)

## Layer

`presentation` — только внутри `android/feature/quizzes-screen/presentation/`

## Review Tags

- `concurrency-review` — Phase-04 добавляет `StateFlow` / `stateIn(scope, Eagerly, Loading)` в 4 DefaultXxx компонента (QuestListComponent, SectionListComponent, ThemeListComponent, LessonListComponent); `SupervisorJob` и `lifecycle.doOnDestroy { job.cancel() }` lifecycle pattern; Flow-collect в long-lived component scope. Все `stateIn` вызовы и `scope.cancel()` подлежат concurrency review.

## State Matrix Coverage

Из `02-behavior.md` State Matrix:

**Matrix 1 (Tap Actions):**
- R2: Tap QuestCard → push SectionList (реализуется в `DefaultQuestListComponent.onQuestClick`)
- R3: Tap SectionItem → push ThemeList (реализуется в `DefaultSectionListComponent.onSectionClick`)
- R4: Tap ThemeItem → push LessonList (реализуется в `DefaultThemeListComponent.onThemeClick`)
- R5: Tap LessonItem → push LessonPlaceholder (реализуется в `DefaultLessonListComponent.onLessonClick`)

**Matrix 2 (Loading/Loaded/Empty/Error):**
- QuestList: Loading → `QuestListUiState.Loading`; Loaded → `QuestListUiState.Loaded`; Empty → `QuestListUiState.Empty`
- SectionList / ThemeList / LessonList: Loading → `HierarchyListUiState.Loading`; Loaded → `.Loaded(items)`; Empty → `.Empty(levelLabel)`
- LessonPlaceholder: статический — нет Loading/Empty (config-derived)

Matrix rows: [R2, R3, R4, R5, Loading, Loaded, Empty для всех drill-down компонентов]

## Domain Contract Coverage

Feature Domain Contract = N/A (Walking Skeleton SKIP). Эта фаза не создаёт domain классы. Работает с существующими: `QuestRepository`, `SectionRepository`, `ThemeRepository`, `LessonRepository` (все interfaces из shared domain).

### Options Considered

| Критерий | Option A (recommended): duplicate mapper in quizzes-screen | Option B: move Quest.toDisplayItem() to designsystem |
|----------|------------------------------------------------------------|------------------------------------------------------|
| Gradle changes | None | backend-dev must add quest-domain dependency to designsystem build.gradle.kts |
| Invariant 3 compliance | Compliant — no cross-feature import | Compliant — designsystem is core |
| Duplication | Minimal (1 tiny file) | No duplication |
| Blast radius | quizzes-screen only | All modules depending on designsystem see quest-domain types |
| Refactor cost если неверно | small — rename/delete one file | medium — reverse Gradle dep change |

**Recommended: Option A (duplicate minimal mapper)**

**Rationale:** No Gradle changes needed; quizzes-screen stays self-contained. Duplication is minimal (5-field mapper). Long-term Option B is cleaner but requires backend-dev scaffold work outside Phase-04 scope.

**Rejected Option B:** Requires `backend-dev` to modify `android/core/designsystem/build.gradle.kts` (scaffold ownership) adding quest domain as dependency — cross-phase blocker for Phase-04.

## Traceability

| Problem (from 2-grounding.md) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|-------------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 1: Внутренний ChildStack drill-down children отсутствуют | frontend-dev (quizzes-screen/presentation) | `DefaultQuizzesComponent.createChild()` — Phase-03 stub заменяется | `06-api-contract.md:529` — canonical DefaultXxx signatures | Создать 5 DefaultXxx + обновить createChild() | `./gradlew :android:feature:quizzes-screen:presentation:test` |
| Problem 4: QuestRepository.observeByCatalog (добавлен в Phase-01) | frontend-dev consumer | `DefaultQuestListComponent.init { questRepository.observeByCatalog(...) }` | Phase-01 provides the interface method | Collect observeByCatalog в DefaultQuestListComponent.stateIn | Test: QL-U-01..10 |

## New Files

- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../uistate/QuestListUiState.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../uistate/HierarchyListUiState.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../uistate/LessonPlaceholderUiState.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../uistate/HierarchyItemUi.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../mapper/QuestToDisplayItemMapper.kt` — local copy (Option A)
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../mapper/SectionDrillMapper.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../mapper/ThemeDrillMapper.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../mapper/LessonDrillMapper.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultQuestListComponent.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultSectionListComponent.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultThemeListComponent.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultLessonListComponent.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultLessonPlaceholderComponent.kt`

## Modified Files

- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/QuestListComponent.kt` — replace Phase-03 stub с полным interface согласно `06-api-contract.md:529`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/SectionListComponent.kt` — replace stub
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/ThemeListComponent.kt` — replace stub
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/LessonListComponent.kt` — replace stub
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/LessonPlaceholderComponent.kt` — replace stub
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultQuizzesComponent.kt` — заменить stub `createChild()` на полный `when` блок из `06-api-contract.md:392`

## Deleted Files

none

## Dependencies

- Phase-03 completed: `QuizzesConfig`, `QuizzesChild`, `DefaultQuizzesComponent` (stub), `QuizzesPresentationModule` существуют
- Phase-01 completed: `QuestRepository.observeByCatalog(...)` добавлен в interface
- `SectionRepository.observeByQuest(questId)` — exists at `shared/feature/section/domain/` `:24`
- `ThemeRepository.observeBySection(sectionId)` — exists at `shared/feature/theme/domain/` `:21`
- `LessonRepository.observeByTheme(themeId)` — exists at `shared/feature/lesson/domain/` `:21`

## Acceptance Criteria

1. `DefaultQuestListComponent` публикует `Value<QuestListUiState>` через `mutableStateOf` / Decompose `MutableValue`; initial state `Loading`.
2. `DefaultQuestListComponent` при `onQuestClick(quest)` вызывает `navigation.pushNew(QuizzesConfig.SectionList(quest.id.value, titles + [quest.title]))`.
3. `DefaultSectionListComponent`, `DefaultThemeListComponent`, `DefaultLessonListComponent` аналогично: состояние `HierarchyListUiState`, при click — pushNew соответствующего next config.
4. `DefaultLessonPlaceholderComponent` — статический; `uiState = LessonPlaceholderUiState(lessonTitle=config.lessonTitle, titles=config.titles)`.
5. Маппинг `Section.toDrillItem()`, `Theme.toDrillItem()`, `Lesson.toDrillItem()` возвращает `HierarchyItemUi` с корректными `id`, `title`, `orderLabel`, `subtitleCount`.
6. `DefaultQuizzesComponent.createChild()` компилируется без `NotImplementedError`; все 6 ветвей `when` обработаны.
7. Новые stateful fields (`stateIn` scope) сбрасываются при lifecycle destroy — `lifecycle.doOnDestroy { componentJob.cancel() }` присутствует во всех 4 DefaultXxx с repository.
8. Нет прямых imports из `android/feature/quest/presentation` или `android/feature/app-shell/presentation` в новом модуле.
9. Compose-Preview пока не требуется — это presentation layer, не designsystem.

## Tests Required (TDD-style)

Пишутся параллельно с production code (TDD). JVM unit тесты:

- `DefaultQuestListComponentTest`:
  - `QL-U-01`: given component, initial state is Loading
  - `QL-U-02`: given FakeQuestRepository emits list, state is QuestListUiState.Loaded with quests
  - `QL-U-03`: given empty emission, state is QuestListUiState.Empty
  - `QL-U-05`: onQuestClick calls navigation.pushNew with correct SectionList config
  - `QL-U-07`: breadcrumb titles[0] in pushed config equals original catalogName
  - `QL-U-10`: titles in QuestList config unchanged after repository emits renamed quest

- `DefaultSectionListComponentTest`:
  - `SL-U-01..07` — see `04-testing.md §4`

- `DefaultThemeListComponentTest`:
  - `TH-U-01..04` — see `04-testing.md §5`

- `DefaultLessonListComponentTest`:
  - `LL-U-01..04` — see `04-testing.md §6`

- `DefaultLessonPlaceholderComponentTest`:
  - `LP-U-01`: uiState.lessonTitle equals config.lessonTitle
  - `LP-U-02`: uiState.titles equals config.titles (frozen)

- `DrillItemMapperTest` (JVM, pure):
  - `section.toDrillItem()` id, title, orderLabel round-trip
  - `theme.toDrillItem()` round-trip
  - `lesson.toDrillItem()` round-trip

## Validation

```bash
# Compile check (no test run)
./gradlew :android:feature:quizzes-screen:presentation:compileDebugKotlin

# Unit tests
./gradlew :android:feature:quizzes-screen:presentation:test

# Invariant: no forbidden cross-feature import
grep -rE "^import .*android\.feature\.(quest|app_shell)\.presentation" \
  android/feature/quizzes-screen/presentation/src/main/ --include="*.kt"
# expected: empty output

# Stateful fields cleanup: verify doOnDestroy pattern
grep -n "doOnDestroy" \
  android/feature/quizzes-screen/presentation/src/main/kotlin/**/component/Default*Component.kt
# expected: at least 1 match per component with repository (4 components)
```

## Handoff Notes

- Phase-05 создаёт Compose UI screens, которые читают `Value<UiState>` через `subscribeAsState()`. Эти screens зависят от Phase-04 интерфейсов и DefaultXxx implementations.
- `HierarchyItemUi.id` — raw `String` (not typed `XxxId`). `childFactory` в DefaultXxx конструирует `XxxId(id)` при pushNew.
- Breadcrumb titles accumulation: каждый child component при click добавляет `parentTitles + [clicked.title]` в следующий config. `parentTitles = config.titles` (immutable snapshot, frozen at push time — ADR-QS-10).
- `QuestListComponent.onShareClick(quest: QuestDisplayItem)` — интерфейс имеет этот метод (06-api-contract.md:529), но реализация делегируется Phase-06 (Share Intent). В Phase-04: stub implementation (например `Unit` тело), чтобы не блокировать компиляцию.
