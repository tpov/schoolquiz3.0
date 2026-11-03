---
phase: 07
name: Cross-Feature Wiring
complex: true
---

# Phase-07 Overview: Cross-Feature Wiring

## Goal

Подключить `QuizzesComponent` к существующей app shell: обновить `DefaultRootComponent` (lambda closures), `DefaultHomeQuestsComponent` (constructor + onCatalogClick), `DefaultMyQuestsComponent` (constructor + onQuestClick), `MyQuestsComponent` interface, `QuestPresentationModule` Koin factories, `AppShellScreen` (`QuizzesContent` условный рендер), замена TODO в `HomeQuestsScreen` и `MyQuestsScreen`.

## Scope

- `DefaultRootComponent.kt` — добавить `quizzesComponent` поле, lambda closures, передать в factories
- `DefaultHomeQuestsComponent.kt` — заменить TODO в `onCatalogClick`; принять `onCatalogDrillDown` constructor param
- `DefaultMyQuestsComponent.kt` — добавить `onQuestDrillDown` constructor param; реализовать `onQuestClick`
- `MyQuestsComponent.kt` — добавить `fun onQuestClick(quest: QuestDisplayItem)` в interface
- `QuestPresentationModule.kt` — обновить factory lambdas (новые параметры)
- `AppShellPresentationModule.kt` — добавить `quizzesFactory` registration (если отдельный module — verify)
- `AppShellScreen.kt` — добавить `QuizzesContent` conditional render

## Role Inputs

- `frontend.md` — задачи frontend-dev
- `backend.md` — задачи backend-dev (Koin module ownership; AppApplication.kt уже обновлён Phase-03)
- `tests.md` — задачи test-dev

## Layer

Integration layer: `android/feature/app-shell/presentation` + `android/feature/quest/presentation`

## Review Tags

- `concurrency-review` — Phase-07 добавляет `quizzesComponent` как field в `DefaultRootComponent` (long-lived singleton). Lambda closure: `homeQuestsComponent.state.value.catalogs` (StateFlow read in lambda) — thread-safety верификация. Cross-component state access (homeQuestsComponent.state.value в onQuestDrillDown lambda) требует concurrency review.

## Options Considered

| Критерий | Option A (recommended): lambda callbacks | Option B: QuizzesNavigator в quest/presentation | Option C: EventBus в shared/core |
|----------|------------------------------------------|--------------------------------------------------|----------------------------------|
| Invariant 3 compliance | Compliant — no cross-feature import | VIOLATION — quest/presentation импортирует quizzes-screen | Compliant через generic event types |
| Compile-time safety | High — typed `(CatalogId, String) -> Unit` | High — typed interface | Low — строковые event keys |
| Testability | High — fake lambda в unit tests | High — mock navigator | Low — global bus registration |
| Coupling | Low — one-directional via DefaultRootComponent | Bidirectional feature coupling | Implicit coupling |
| Refactor cost если неверно | small | large (bidirectional import removal) | medium (event renaming) |

**Recommended: Option A (lambda callbacks через DefaultRootComponent)**

**Rationale:** User Decision Q3 (ADR-QS-01) зафиксировал lambda callbacks. Единственный вариант не нарушающий Invariant 3.

**Rejected Option B:** Bidirectional feature coupling — quest/presentation импортировал бы quizzes-screen/presentation (Invariant 3 violation, blocker).

**Rejected Option C:** EventBus — нет compile-time гарантий routing; тяжёлый паттерн для прямого navigation event.

## State Matrix Coverage

**Matrix 1 (Tap Actions):**
- R1: Catalog tap (HomeQuests) → `DefaultHomeQuestsComponent.onCatalogClick` → lambda → `openQuestList` (реализуется в Phase-07)
- R2 (MyQuests entry): Quest tap (MyQuests) → `DefaultMyQuestsComponent.onQuestClick` → lambda → `openSectionList`

**Matrix 3 (AppShellScreen conditional render):**
- `active is QuizzesChild.Idle` → overlay hidden (AppShellScreen нормальный UI)
- `active !is QuizzesChild.Idle` → `QuizzesScreen(component)` поверх

## Domain Contract Coverage

N/A

## Traceability

| Problem (from 2-grounding.md) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|-------------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 2: HomeQuestsComponent.onCatalogClick TODO (DefaultHomeQuestsComponent.kt:50-52) | frontend-dev (quest/presentation) | `HomeQuestsScreen.kt:56` → `component::onCatalogClick` | `06-api-contract.md:164` — modified constructor | Заменить TODO на `onCatalogDrillDown(id, catalogName)` | INT-01 — `openQuestList` called |
| Problem 3: MyQuestsScreen TODO + interface missing onQuestClick (MyQuestsScreen.kt:87) | frontend-dev (quest/presentation) | `MyQuestsScreen.kt:87` — QuestCard.onClick lambda | `06-api-contract.md:199` — interface + constructor update | Add `onQuestClick` to interface; replace TODO | INT-02 — `openSectionList` called |
| Problem 1 (wiring): QuizzesScreen not shown from AppShellScreen | frontend-dev (app-shell/presentation) | `AppShellScreen.kt` — no QuizzesContent render | `06-api-contract.md:249` — conditional render pattern | Add `if (active !is QuizzesChild.Idle) { QuizzesScreen(component) }` | Manual: HomeQuests → catalog tap → overlay visible |

## New Files

none

## Modified Files

- `android/feature/app-shell/presentation/src/main/kotlin/.../screen/AppShellScreen.kt` — добавить QuizzesContent conditional render
- `android/feature/app-shell/presentation/src/main/kotlin/.../component/DefaultRootComponent.kt` — добавить quizzesComponent + lambda closures
- `android/feature/quest/presentation/src/main/kotlin/.../component/HomeQuestsComponent.kt` — если interface меняется (verify — grounding указывает interface не меняется, только impl)
- `android/feature/quest/presentation/src/main/kotlin/.../component/DefaultHomeQuestsComponent.kt` — заменить TODO + добавить `onCatalogDrillDown` param
- `android/feature/quest/presentation/src/main/kotlin/.../component/MyQuestsComponent.kt` — добавить `fun onQuestClick(quest: QuestDisplayItem)` в interface
- `android/feature/quest/presentation/src/main/kotlin/.../component/DefaultMyQuestsComponent.kt` — добавить `onQuestDrillDown` param + реализовать `onQuestClick`
- `android/feature/quest/presentation/src/main/kotlin/.../di/QuestPresentationModule.kt` — обновить factories
- `android/feature/app-shell/presentation/src/main/kotlin/.../di/AppShellPresentationModule.kt` — добавить quizzesComponent factory (или QuizzesPresentationModule уже зарегистрирован в Phase-03/AppApplication)

## Deleted Files

none

## Dependencies

- Phase-03 completed: `quizzesPresentationModule` зарегистрирован в `AppApplication.kt` (backend-dev Phase-03); `QuizzesComponent` factory доступен через Koin
- Phase-04 completed: все child components существуют
- Phase-05 completed: `QuizzesScreen` Composable существует
- Phase-06 completed: `QuestListScreen` с Share Menu завершён

## Acceptance Criteria

1. Тап на каталог в `HomeQuestsScreen` → `QuizzesScreen` overlay появляется с `QuestListScreen` (AC#13, AC#1).
2. Тап на квест в `MyQuestsScreen` → `QuizzesScreen` overlay с `SectionListScreen` и breadcrumb `<catalog> › <quest>` (AC#7).
3. System back закрывает overlay — возврат к HomeQuests/MyQuests (AC#4, AC#5).
4. `dismissQuizzes()` → overlay hidden (`active is QuizzesChild.Idle`) (AC#6).
5. `DefaultRootComponent` создаёт `quizzesComponent` до передачи lambda в `homeQuestsFactory`/`myQuestsFactory` (порядок инициализации).
6. Lambda closure в MyQuests: `catalogName` берётся из `homeQuestsComponent.state.value.catalogs` find (Q4 decision). Fallback `"Без каталога"` если catalogs пусты.
7. `MyQuestsComponent` interface с новым `onQuestClick` — все consumers обновлены: `StubMyQuestsComponent`, `MyQuestsScreen` call site.
8. Bidirectional import check: `quest/presentation` не импортирует `quizzes-screen/presentation` и наоборот (Invariant 3).
9. `quizzesComponent` stateful field — не требует explicit reset; lifecycle управляется Decompose ComponentContext.

## Tests Required (TDD-style)

- `DefaultHomeQuestsComponentTest` (JVM):
  - `when onCatalogClick called then onCatalogDrillDown lambda invoked with correct catalogId and catalogName`
  - `when onCatalogClick called then catalogName resolved from state.catalogs`

- `DefaultMyQuestsComponentTest` (JVM):
  - `when onQuestClick called then onQuestDrillDown lambda invoked with quest`
  - `when onQuestClick called and catalogs empty then catalogName is fallback`

- `QuizzesRootIntegrationTest` (Phase-05 tests — verify INT-01..05 pass with real DefaultRootComponent wiring)

## Validation

```bash
# Full build
./gradlew assembleDebug --no-configuration-cache

# Bidirectional coupling check
grep -rE "^import .*quizzes_screen\.presentation" \
  android/feature/quest/presentation/src/ --include="*.kt"
# expected: empty output

grep -rE "^import .*quest\.presentation" \
  android/feature/quizzes-screen/presentation/src/ --include="*.kt"
# expected: empty output

grep -rE "^import .*app_shell\.presentation" \
  android/feature/quizzes-screen/presentation/src/ --include="*.kt"
# expected: empty output

# All unit tests
./gradlew allTests --no-configuration-cache
```

## Handoff Notes

- `StubMyQuestsComponent` (используется в тестах `MyQuestsScreen`) — тоже реализует `MyQuestsComponent` interface; после добавления `onQuestClick` в interface — нужно добавить stub override. Test-dev owner.
- `DefaultRootComponent.kt` уже инжектирует `homeQuestsFactory` и `myQuestsFactory` через Koin. Verify текущую сигнатуру factory — нужно ли добавлять `onCatalogDrillDown` / `onQuestDrillDown` как параметры через `parametersOf(...)`.
- `AppShellScreen` wiring — точный код зависит от текущего `AppShellScreen.kt` layout (tab pager + modal overlay). `06-api-contract.md:249` описывает pattern; `QuizzesScreen` рендерится условно поверх существующего tab content.
- `QuizzesComponent` создаётся как field в `DefaultRootComponent`, а не в ChildStack — это верно по ADR-QS-03 (isolated ChildStack). QuizzesComponent живёт параллельно с tab navigation.
