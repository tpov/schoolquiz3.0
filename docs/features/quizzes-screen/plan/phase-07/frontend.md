---
phase: 07
role: frontend-dev
---

# Phase-07 Frontend Tasks: Cross-Feature Wiring

### Pattern Invariants

- `android/feature/quest/presentation/` НИКОГДА не импортирует `android/feature/quizzes-screen/presentation/` (ADR-QS-01: `03-decisions.md:11`; Invariant 3: `docs/invariants.md:25`). Lambda callback types — только core types: `CatalogId` (shared/core/catalog/domain), `QuestDisplayItem` (android/core/designsystem/model).
- `android/feature/quizzes-screen/presentation/` НИКОГДА не импортирует `android/feature/quest/presentation/` или `android/feature/app-shell/presentation/`. Bidirectional coupling check: `.claude/rules/clean-architecture.md:62-66`.
- `quizzesComponent` создаётся в `DefaultRootComponent` ПЕРЕД `homeQuestsComponent` и `myQuestsComponent` — иначе lambda closure capture нарушается. Существующий порядок: `DefaultRootComponent.kt:130-131`.
- `homeQuestsComponent.state.value.catalogs` — доступ в lambda closure в Main thread (Decompose state); coroutine sync не нужен. Ref: `DefaultRootComponent.kt:131`.
- `QuizzesScreen` overlay рендерится ПОВЕРХ tab content в AppShellScreen — не вместо. Оба существуют одновременно; QuizzesScreen перекрывает visually. Ref: `06-api-contract.md:249`.

---

## Update DefaultHomeQuestsComponent — activate onCatalogClick

- **Файл:** `android/feature/quest/presentation/src/main/kotlin/.../component/DefaultHomeQuestsComponent.kt`
- **Тип:** class (modification — replace TODO)
- **Сигнатура:** existing `class DefaultHomeQuestsComponent`
- **Вход:**
  - Добавить `private val onCatalogDrillDown: (CatalogId, String) -> Unit` как constructor parameter
- **Поведение / Выход:**
  - Заменить существующий `onCatalogClick(id: CatalogId)` TODO тело:
    - `val catalogName = state.value.catalogs.firstOrNull { it.id == id }?.name.orEmpty()`
    - `onCatalogDrillDown(id, catalogName)`
  - Остальная логика компонента не изменяется
- **Edge cases:**
  - `state.value.catalogs` — StateFlow; `.value` в Main thread корректно. Если catalogs ещё не загружены (редкий race при первом тапе до completion) → `catalogName = ""` → BreadcrumbBar рендерит пустую строку. Допустимо для MVP (spec не описывает loading guard для catalog name).
  - `orEmpty()` вместо `?: ""` — идиоматично
- **Depends on:** `HomeQuestsComponent` interface (не меняется — `onCatalogClick(id: CatalogId)` остаётся), `CatalogId`, `DefaultHomeQuestsComponent` existing state
- **Canonical reference:** `06-api-contract.md:164`
- **Rationale:** Problem 2 fix — заменить пустой TODO на actual navigation dispatch.

---

## Update MyQuestsComponent interface — add onQuestClick

- **Файл:** `android/feature/quest/presentation/src/main/kotlin/.../component/MyQuestsComponent.kt`
- **Тип:** interface (modification)
- **Сигнатура:** existing `interface MyQuestsComponent`
- **Вход:** N/A
- **Поведение / Выход:**
  - Добавить: `fun onQuestClick(quest: QuestDisplayItem)` в interface
  - Существующие методы остаются: `val state`, `onCatalogSelected`, `onCreateQuestClick`
- **Edge cases:**
  - `QuestDisplayItem` — из `android/core/designsystem/model`; уже импортирован в quest/presentation (используется в UiState)
  - `StubMyQuestsComponent` (test stub) — ОБЯЗАН добавить пустой override (test-dev owner)
- **Depends on:** `QuestDisplayItem`, existing `MyQuestsComponent` interface
- **Canonical reference:** `06-api-contract.md:199`
- **Rationale:** Interface extension позволяет `MyQuestsScreen` вызывать `component.onQuestClick(quest)` type-safely.

---

## Update DefaultMyQuestsComponent — add onQuestDrillDown + implement onQuestClick

- **Файл:** `android/feature/quest/presentation/src/main/kotlin/.../component/DefaultMyQuestsComponent.kt`
- **Тип:** class (modification)
- **Сигнатура:** existing `class DefaultMyQuestsComponent`
- **Вход:**
  - Добавить `private val onQuestDrillDown: (QuestDisplayItem) -> Unit` как constructor parameter
- **Поведение / Выход:**
  - Добавить override: `override fun onQuestClick(quest: QuestDisplayItem) { onQuestDrillDown(quest) }`
  - Примечание: `catalogName` resolve происходит в `DefaultRootComponent` closure (`homeQuestsComponent.state.value.catalogs`), **не** в `DefaultMyQuestsComponent`. Это соответствует `06-api-contract.md:42` (onQuestDrillDown лямбда принимает `QuestDisplayItem`, а DefaultRootComponent resolves catalogName).
  - Остальная логика не изменяется
- **Edge cases:**
  - `quest.catalogId` — несёт `CatalogId` из Phase-02 extension. DefaultRootComponent использует это для lookup.
- **Depends on:** `MyQuestsComponent` interface (updated), `QuestDisplayItem`
- **Canonical reference:** `06-api-contract.md:199`
- **Rationale:** Problem 3 fix — replace MyQuestsScreen TODO.

---

## Update MyQuestsScreen — replace TODO with component.onQuestClick

- **Файл:** `android/feature/quest/presentation/src/main/kotlin/.../screen/MyQuestsScreen.kt`
- **Тип:** Composable (modification — replace `TODO` in `QuestCard.onClick`)
- **Сигнатура:** existing `@Composable fun MyQuestsScreen(component: MyQuestsComponent)`
- **Вход:** N/A (modification)
- **Поведение / Выход:**
  - Заменить `MyQuestsScreen.kt:87` `onClick = { /* TODO: open quest detail */ }` на:
    - `onClick = { component.onQuestClick(quest) }` где `quest: QuestDisplayItem` из LazyColumn items
  - `QuestCard(onLongClick = null)` — НЕ добавлять long-press (AC#11)
- **Edge cases:**
  - Verify что `quest` в LazyColumn items lambda имеет тип `QuestDisplayItem` (с `catalogId` field из Phase-02)
  - Verify lambda capture: `component` доступен в inner lambda scope
- **Depends on:** `MyQuestsComponent.onQuestClick`, `QuestDisplayItem`, `QuestCard`
- **Canonical reference:** `06-api-contract.md:199`, Problem 3 (2-grounding.md:153-204)
- **Rationale:** Problem 3 fix — single line change; critical entry point для MyQuests drill-down.

---

## Update DefaultRootComponent — add quizzesComponent + lambda closures

- **Файл:** `android/feature/app-shell/presentation/src/main/kotlin/.../component/DefaultRootComponent.kt`
- **Тип:** class (modification — significant)
- **Сигнатура:** existing `class DefaultRootComponent`
- **Вход:** N/A (modification)
- **Поведение / Выход:**
  - Шаг 1 — создать `quizzesComponent`:
    - `private val quizzesComponent: QuizzesComponent = get<QuizzesComponent>(parameters = { parametersOf(childContext("quizzes")) })`
    - Или: `private val quizzesComponent: QuizzesComponent = DefaultQuizzesComponent(childContext("quizzes"), questRepository=get(), sectionRepository=get(), themeRepository=get(), lessonRepository=get())`
    - Frontend-dev выбирает Koin vs direct constructor. Koin preferred (consistent с ADR-CMP-51).
  - Шаг 2 — обновить `homeQuestsComponent` creation — передать `onCatalogDrillDown` лямбду:
    - `onCatalogDrillDown = { catalogId: CatalogId, catalogName: String -> quizzesComponent.openQuestList(catalogId, catalogName) }`
  - Шаг 3 — обновить `myQuestsComponent` creation — передать `onQuestDrillDown` лямбду:
    - `onQuestDrillDown = { quest: QuestDisplayItem -> val catalogName = homeQuestsComponent.state.value.catalogs.firstOrNull { it.id == quest.catalogId }?.name.orEmpty(); quizzesComponent.openSectionList(quest.id, listOf(catalogName, quest.title)) }`
  - `quizzesComponent` экспонируется через `val quizzesComponent: QuizzesComponent get() = _quizzesComponent` если нужно AppShellScreen; или через существующий `rootComponent` API.
- **Edge cases:**
  - Порядок инициализации критичен: `quizzesComponent` ПЕРВЫЙ; только потом `homeQuestsComponent` и `myQuestsComponent` (иначе closure capture `quizzesComponent` не initialized)
  - `homeQuestsComponent.state.value.catalogs` в lambda — в момент вызова (при тапе) homeQuestsComponent уже initialized. Но: если MyQuests tap происходит до HomeQuests загрузил catalogs — `catalogName = ""` fallback.
  - Verify существующий `DefaultRootComponent.kt:130-131` для точных field names и factory calls
- **Depends on:** `QuizzesComponent`, `DefaultHomeQuestsComponent`, `DefaultMyQuestsComponent`, `CatalogId`, `QuestDisplayItem`, `QuizzesNavigator` — НЕТ прямого импорта из quizzes-screen в quest/presentation
- **Canonical reference:** `06-api-contract.md:249`
- **Rationale:** Единственная точка координации (ADR-QS-01). Все cross-feature lambdas здесь.

---

## Update AppShellScreen — add QuizzesContent conditional render

- **Файл:** `android/feature/app-shell/presentation/src/main/kotlin/.../screen/AppShellScreen.kt`
- **Тип:** Composable (modification)
- **Сигнатура:** existing `@Composable fun AppShellScreen(component: RootComponent)`
- **Вход:** N/A (modification)
- **Поведение / Выход:**
  - Добавить после tab content:
    - `val quizzesStack by component.quizzesComponent.childStack.subscribeAsState()`
    - `val active = quizzesStack.active.instance`
    - `if (active !is QuizzesChild.Idle) { QuizzesScreen(component = component.quizzesComponent, onBreadcrumbClick = component.quizzesComponent::popToLevel) }`
  - Verify: `RootComponent` (interface) должен экспонировать `quizzesComponent: QuizzesComponent` — если нет, добавить в interface или получить через Koin в Composable (менее preferred).
- **Edge cases:**
  - `QuizzesScreen` рендерится поверх tab pager — z-order важен. Verify layout structure в AppShellScreen (tab pager + bottom nav + overlay position).
  - `dismissQuizzes` вызов — не нужен explicit callback; `active is Idle` → overlay исчезает (ADR-QS-11 Option A).
- **Depends on:** `QuizzesComponent`, `QuizzesChild`, `QuizzesScreen` (Phase-05), Decompose `subscribeAsState`
- **Canonical reference:** `06-api-contract.md:249, §12`
- **Rationale:** Overlay-based QuizzesScreen — не отдельный tab; рендерится условно поверх (ADR-QS-11).
