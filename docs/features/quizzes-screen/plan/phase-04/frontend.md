---
phase: 04
role: frontend-dev
---

# Phase-04 Frontend Tasks: Drill-down Child Components

### Pattern Invariants

- Все DefaultXxx компоненты с repository ОБЯЗАНЫ иметь `private val componentJob = SupervisorJob()` + `private val scope = CoroutineScope(Dispatchers.Main.immediate + componentJob)` + `lifecycle.doOnDestroy { componentJob.cancel() }`. Паттерн взят из `DefaultHomeQuestsComponent.kt:33-48`.
- `Value<UiState>` (Decompose) — не `StateFlow`. Child screens используют `subscribeAsState()`. Ref: `06-api-contract.md:629` (Value<UiState> fields). Conversion: `stateIn(scope, SharingStarted.Eagerly, initialState)` → `.asValue()` или `MutableValue(initialState)` + collect loop.
- `navigation: StackNavigation<QuizzesConfig>` передаётся как constructor parameter из `DefaultQuizzesComponent.createChild()` — НЕ инжектируется через Koin (child factory pattern). Ref: `06-api-contract.md:392` (DefaultQuizzesComponent.createChild signature).
- `HierarchyItemUi.id` — raw `String`, не типизированный `XxxId`. `DefaultXxx.onItemClick` обёртывает: `XxxId(item.id)`. Ref: `06-api-contract.md:677` (HierarchyItemUi.id field).
- `parentTitles` для breadcrumb берётся из `override val titles: List<String>` — immutable snapshot переданный при создании компонента; НЕ из текущего flow repository. Паттерн заморозки: `06-api-contract.md:529` (QuestListComponent.titles).
- Mapper extension functions (`Section.toDrillItem()`, `Theme.toDrillItem()`, `Lesson.toDrillItem()`) живут в `mapper/` subpackage внутри `quizzes-screen/presentation` — domain models остаются чистыми. Ref: `.claude/rules/domain-models.md:5` (no Android framework types in domain).
- `Quest.toQuestDisplayItem()` — дублируется в `quizzes-screen/presentation/mapper/QuestToDisplayItemMapper.kt` (Option A resolved). НЕ импортируется из `quest/presentation` (Invariant 3 — `.claude/rules/clean-architecture.md:62-66`).

---

## Create QuestListUiState

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../uistate/QuestListUiState.kt`
- **Тип:** sealed interface
- **Сигнатура:** `sealed interface QuestListUiState`
- **Вход:** N/A
- **Поведение / Выход:**
  - `data object Loading : QuestListUiState`
  - `data object Empty : QuestListUiState`
  - `data class Loaded(val quests: List<QuestDisplayItem>) : QuestListUiState`
  - `expandedQuestId` — НЕ здесь; живёт как `remember { mutableStateOf<QuestId?>(null) }` в `QuestListScreen` (ADR-QS-07)
- **Edge cases:**
  - Нет `Error` variant — offline-first; если Flow из Room бросает exception, компонент логирует, состояние остаётся Loading (design decision aligned with existing pattern)
- **Depends on:** `QuestDisplayItem` (из `android/core/designsystem/model/`)
- **Canonical reference:** `06-api-contract.md:628`
- **Rationale:** Sealed interface — exhaustive when в QuestListScreen; `Loading` initial state показывает skeleton перед первой эмиссией Flow.

---

## Create HierarchyListUiState

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../uistate/HierarchyListUiState.kt`
- **Тип:** sealed interface
- **Сигнатура:** `sealed interface HierarchyListUiState`
- **Вход:** N/A
- **Поведение / Выход:**
  - `data object Loading : HierarchyListUiState`
  - `data class Empty(val levelLabel: String) : HierarchyListUiState` — уровень-специфичный текст («Нет секций», «Нет тем», «Нет уроков»)
  - `data class Loaded(val items: List<HierarchyItemUi>) : HierarchyListUiState`
- **Edge cases:**
  - `Empty.levelLabel` — caller (DefaultXxx) предоставляет строку; не hardcoded в sealed class
- **Depends on:** `HierarchyItemUi`
- **Canonical reference:** `06-api-contract.md:628`
- **Rationale:** Единый UiState для SectionList, ThemeList, LessonList — структурно идентичны (ADR-QS-09).

---

## Create HierarchyItemUi

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../uistate/HierarchyItemUi.kt`
- **Тип:** data class
- **Сигнатура:** `data class HierarchyItemUi`
- **Вход:** N/A (model)
- **Поведение / Выход:**
  - `val id: String` — raw String (SectionId.value / ThemeId.value / LessonId.value)
  - `val title: String`
  - `val orderLabel: String?` — null если нет нумерации
  - `val subtitleCount: String? = null` — зарезервировано для будущего (сейчас null)
- **Edge cases:**
  - `id` — raw String, не value class. Child component при click выполняет `SectionId(item.id)`, не наоборот.
- **Depends on:** нет зависимостей (pure model)
- **Canonical reference:** `06-api-contract.md:628`
- **Rationale:** Передаётся в `HierarchyItemCard` по полям, не как typed domain type. Designsystem не импортирует feature presentation (ADR-QS-09).

---

## Create LessonPlaceholderUiState

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../uistate/LessonPlaceholderUiState.kt`
- **Тип:** data class
- **Сигнатура:** `data class LessonPlaceholderUiState`
- **Вход:** N/A (model)
- **Поведение / Выход:**
  - `val lessonTitle: String` — для placeholder text
  - `val titles: List<String>` — frozen breadcrumb snapshot из `QuizzesConfig.LessonPlaceholder.titles`
- **Edge cases:**
  - Статический — нет Loading/Empty/Loaded states; derived directly from config
- **Depends on:** нет зависимостей
- **Canonical reference:** `06-api-contract.md:628`
- **Rationale:** Простой data class; LessonPlaceholder — terminal leaf, нет repository.

---

## Create Section.toDrillItem mapper

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../mapper/SectionDrillMapper.kt`
- **Тип:** extension function (top-level)
- **Сигнатура:** `fun Section.toDrillItem(): HierarchyItemUi`
- **Вход:** `Section` — domain model из `shared/feature/section/domain`
- **Поведение / Выход:**
  - `id = id.value` (SectionId raw String)
  - `title = title`
  - `orderLabel = "${order + 1}."` — verified: `Section.order: Int` (non-nullable, `shared/feature/section/domain/.../model/Section.kt:27`). 0-based field → 1-based display label.
  - `subtitleCount = null` (reserved)
- **Edge cases:**
  - `order` — non-nullable Int; no null-check needed.
- **Depends on:** `Section` domain model, `HierarchyItemUi`
- **Canonical reference:** `06-api-contract.md:628`
- **Rationale:** Mapper в presentation, не в domain — сохраняет domain purity. Extension function — идиоматично.

---

## Create Theme.toDrillItem mapper

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../mapper/ThemeDrillMapper.kt`
- **Тип:** extension function (top-level)
- **Сигнатура:** `fun Theme.toDrillItem(): HierarchyItemUi`
- **Вход:** `Theme` — domain model из `shared/feature/theme/domain`
- **Поведение / Выход:**
  - `id = id.value` (ThemeId raw String)
  - `title = title`
  - `orderLabel = "${order + 1}."` — verified: `Theme.order: Int` (non-nullable, `shared/feature/theme/domain/.../model/Theme.kt:23`). 0-based → 1-based display.
  - `subtitleCount = null`
- **Edge cases:**
  - `order` — non-nullable Int.
- **Depends on:** `Theme`, `HierarchyItemUi`
- **Canonical reference:** `06-api-contract.md:628`
- **Rationale:** Отдельный файл на mapper — удобно разделять scope responsibility.

---

## Create Lesson.toDrillItem mapper

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../mapper/LessonDrillMapper.kt`
- **Тип:** extension function (top-level)
- **Сигнатура:** `fun Lesson.toDrillItem(): HierarchyItemUi`
- **Вход:** `Lesson` — domain model из `shared/feature/lesson/domain`
- **Поведение / Выход:**
  - `id = id.value` (LessonId raw String)
  - `title = title`
  - `orderLabel = "${order + 1}."` — verified: `Lesson.order: Int` (non-nullable, `shared/feature/lesson/domain/.../model/Lesson.kt:23`). 0-based → 1-based display.
  - `subtitleCount = null`
- **Edge cases:**
  - `order` — non-nullable Int; `order + 1` for 1-based label.
- **Depends on:** `Lesson`, `HierarchyItemUi`
- **Canonical reference:** `06-api-contract.md:628`
- **Rationale:** Mapper в presentation layer — domain чистый.

---

## Update QuestListComponent interface (replace Phase-03 stub)

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/QuestListComponent.kt`
- **Тип:** interface (modification — replace stub)
- **Сигнатура:** `interface QuestListComponent`
- **Вход:** N/A
- **Поведение / Выход:**
  - `val state: Value<QuestListUiState>` — Decompose `Value`, не `StateFlow`
  - `val titles: List<String>` — frozen breadcrumb titles snapshot
  - `fun onQuestClick(quest: QuestDisplayItem)` — drill-down tap
  - `fun onShareClick(quest: QuestDisplayItem)` — long-press share (реализуется Phase-06; stub в Phase-04)
- **Edge cases:**
  - `onShareClick` — stub body в Phase-04 (`/* TODO Phase-06 */`); не блокирует компиляцию
- **Depends on:** `QuestListUiState`, `QuestDisplayItem`, Decompose `Value`
- **Canonical reference:** `06-api-contract.md:529`
- **Rationale:** Полный interface нужен Phase-04 для создания DefaultQuestListComponent; Phase-05 screens работают с interface.

---

## Update SectionListComponent / ThemeListComponent / LessonListComponent / LessonPlaceholderComponent interfaces (replace Phase-03 stubs)

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/SectionListComponent.kt` (и аналоги)
- **Тип:** interface (modification)
- **Сигнатура:** `interface SectionListComponent` / `ThemeListComponent` / `LessonListComponent` / `LessonPlaceholderComponent`
- **Вход:** N/A
- **Поведение / Выход (SectionListComponent):**
  - `val state: Value<HierarchyListUiState>`
  - `val titles: List<String>`
  - `fun onSectionClick(section: HierarchyItemUi)`
- **Поведение / Выход (ThemeListComponent):**
  - `val state: Value<HierarchyListUiState>`
  - `val titles: List<String>`
  - `fun onThemeClick(theme: HierarchyItemUi)`
- **Поведение / Выход (LessonListComponent):**
  - `val state: Value<HierarchyListUiState>`
  - `val titles: List<String>`
  - `fun onLessonClick(lesson: HierarchyItemUi)`
- **Поведение / Выход (LessonPlaceholderComponent):**
  - `val uiState: LessonPlaceholderUiState` — статический, нет `Value<>` wrapper нужен
- **Edge cases:**
  - Verify: `LessonPlaceholderComponent.uiState` — `val` не `Value<>`. Статический derivation из config, нет observing.
- **Depends on:** `HierarchyListUiState`, `HierarchyItemUi`, `LessonPlaceholderUiState`, Decompose `Value`
- **Canonical reference:** `06-api-contract.md:529`
- **Rationale:** Replace Phase-03 stubs полными definitions — QuizzesChild sealed interface референсирует эти interfaces.

---

## Create QuestToDisplayItemMapper (quizzes-screen local copy)

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../mapper/QuestToDisplayItemMapper.kt`
- **Тип:** extension function (top-level)
- **Сигнатура:** `fun Quest.toQuestDisplayItem(): QuestDisplayItem`
- **Вход:** `Quest` — domain model из `shared/feature/quest/domain`
- **Поведение / Выход:**
  - `id = id`, `catalogId = catalogId`, `title = title`, `pictureUrl = pictureUrl`, `averageRating = averageRating`, `averageRatingCount = averageRatingCount`
  - Идентичен `android/feature/quest/presentation/.../mapper/QuestToDisplayItem.kt` (копия — не импорт)
- **Edge cases:**
  - Divergence risk: если `quest/presentation` обновляет свой mapper — quizzes-screen копию нужно синхронизировать вручную. Приемлемо для MVP; long-term solution — перенос в designsystem (Option B, требует Gradle dep change, не в scope MVP).
  - Naming: `toQuestDisplayItem()` (не `toDisplayItem()`) — избегает confusion с quest/presentation extension.
- **Depends on:** `Quest` (shared domain), `QuestDisplayItem` (android/core/designsystem/model), `CatalogId`, `QuestId`
- **Canonical reference:** `06-api-contract.md:81` (QuestDisplayItem fields), `06-api-contract.md:81` (mapper pattern)
- **Rationale:** Option A (duplicate) selected — no Gradle dependency changes needed vs Option B (move to designsystem). Keeps quizzes-screen fully self-contained without cross-feature import (Invariant 3).

---

## Create DefaultQuestListComponent

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultQuestListComponent.kt`
- **Тип:** class
- **Сигнатура:** `class DefaultQuestListComponent(componentContext: ComponentContext, private val catalogId: CatalogId, override val titles: List<String>, private val questRepository: QuestRepository, private val navigation: StackNavigation<QuizzesConfig>) : ComponentContext by componentContext, QuestListComponent`
- **Вход:**
  - `componentContext: ComponentContext`
  - `catalogId: CatalogId` — оборачивается из `QuizzesConfig.QuestList.catalogId: String` в `createChild()`
  - `titles: List<String>` — frozen breadcrumb snapshot из config
  - `questRepository: QuestRepository` — имеет `observeByCatalog` (Phase-01)
  - `navigation: StackNavigation<QuizzesConfig>` — для pushNew
- **Поведение / Выход:**
  - `private val componentJob = SupervisorJob()`
  - `private val scope = CoroutineScope(Dispatchers.Main.immediate + componentJob)`
  - `lifecycle.doOnDestroy { componentJob.cancel() }`
  - `override val state: Value<QuestListUiState>` — `questRepository.observeByCatalog(catalogId, shelf = "home")` mapped → `stateIn(scope, Eagerly, Loading)` → convert to Decompose `Value`
  - `mapQuests(quests: List<Quest>)`: если empty → `Empty`, иначе `Loaded(quests.map { it.toQuestDisplayItem() })`
  - `override fun onQuestClick(quest: QuestDisplayItem)`: `navigation.pushNew(QuizzesConfig.SectionList(quest.id.value, titles + listOf(quest.title)))`
  - `override fun onShareClick(quest: QuestDisplayItem)`: Phase-06 stub — `Unit`
- **Edge cases:**
  - `it.toQuestDisplayItem()` — из `QuestToDisplayItemMapper.kt` в `quizzes-screen/presentation/mapper/` (создаётся этой же фазой; см. Signature Card ниже). НЕ из `quest/presentation` (Invariant 3 — `.claude/rules/clean-architecture.md:62-66`). Resolved: Option A (duplicate minimal mapper), не Option B (move to designsystem — потребовало бы backend-dev Gradle dependency change).
- **Depends on:** `QuestListComponent`, `QuestListUiState`, `QuestRepository`, `CatalogId`, Decompose `StackNavigation`, `Value`, `QuizzesConfig`, `QuestToDisplayItemMapper.kt` (see below)
- **Canonical reference:** `06-api-contract.md:529`
- **Rationale:** First implementation использующий `observeByCatalog` (Phase-01 data layer).

---

## Create DefaultSectionListComponent

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultSectionListComponent.kt`
- **Тип:** class
- **Сигнатура:** `class DefaultSectionListComponent(componentContext: ComponentContext, private val questId: QuestId, override val titles: List<String>, private val sectionRepository: SectionRepository, private val navigation: StackNavigation<QuizzesConfig>) : ComponentContext by componentContext, SectionListComponent`
- **Вход:**
  - `questId: QuestId` — из `QuestId(config.questId)` в createChild()
  - `titles: List<String>` — frozen snapshot
  - `sectionRepository: SectionRepository` — `observeByQuest(questId)` exists at `:24`
  - `navigation: StackNavigation<QuizzesConfig>`
- **Поведение / Выход:**
  - Lifecycle scope — аналогично DefaultQuestListComponent (SupervisorJob + doOnDestroy)
  - `override val state: Value<HierarchyListUiState>` — `sectionRepository.observeByQuest(questId)` mapped; empty → `Empty("Нет секций")`; иначе `Loaded(sections.map { it.toDrillItem() })`
  - `override fun onSectionClick(section: HierarchyItemUi)`: `navigation.pushNew(QuizzesConfig.ThemeList(section.id, titles + listOf(section.title)))`
- **Edge cases:**
  - `Empty.levelLabel = "Нет секций"` — локализованная строка; в Phase-04 допустимо hardcode строку как TODO-placeholder; Phase-05 или resource phase заменяет на `stringResource`.
- **Depends on:** `SectionListComponent`, `HierarchyListUiState`, `SectionRepository`, `QuestId`, `SectionDrillMapper.kt`, Decompose
- **Canonical reference:** `06-api-contract.md:529`
- **Rationale:** Структурно идентичен ThemeListComponent/LessonListComponent — один паттерн.

---

## Create DefaultThemeListComponent

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultThemeListComponent.kt`
- **Тип:** class
- **Сигнатура:** `class DefaultThemeListComponent(componentContext: ComponentContext, private val sectionId: SectionId, override val titles: List<String>, private val themeRepository: ThemeRepository, private val navigation: StackNavigation<QuizzesConfig>) : ComponentContext by componentContext, ThemeListComponent`
- **Вход:**
  - `sectionId: SectionId` — из `SectionId(config.sectionId)` в createChild()
  - `themeRepository: ThemeRepository` — `observeBySection(sectionId)` exists at `:21`
  - остальное — аналогично DefaultSectionListComponent
- **Поведение / Выход:**
  - State mapping: empty → `Empty("Нет тем")`; иначе `Loaded(themes.map { it.toDrillItem() })`
  - `override fun onThemeClick(theme: HierarchyItemUi)`: `navigation.pushNew(QuizzesConfig.LessonList(theme.id, titles + listOf(theme.title)))`
- **Edge cases:**
  - Аналогично SectionList
- **Depends on:** `ThemeListComponent`, `ThemeRepository`, `SectionId`, `ThemeDrillMapper.kt`
- **Canonical reference:** `06-api-contract.md:529`
- **Rationale:** Identical pattern to Section/Lesson components.

---

## Create DefaultLessonListComponent

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultLessonListComponent.kt`
- **Тип:** class
- **Сигнатура:** `class DefaultLessonListComponent(componentContext: ComponentContext, private val themeId: ThemeId, override val titles: List<String>, private val lessonRepository: LessonRepository, private val navigation: StackNavigation<QuizzesConfig>) : ComponentContext by componentContext, LessonListComponent`
- **Вход:**
  - `themeId: ThemeId` — из `ThemeId(config.themeId)` в createChild()
  - `lessonRepository: LessonRepository` — `observeByTheme(themeId)` exists at `:21`
- **Поведение / Выход:**
  - State mapping: empty → `Empty("Нет уроков")`; иначе `Loaded(lessons.map { it.toDrillItem() })`
  - `override fun onLessonClick(lesson: HierarchyItemUi)`:
    - `navigation.pushNew(QuizzesConfig.LessonPlaceholder(lessonId=lesson.id, lessonTitle=lesson.title, titles=titles + listOf(lesson.title)))`
- **Edge cases:**
  - `LessonPlaceholder.lessonTitle` — отдельное поле, НЕ просто `titles.last()`. Оба содержат `lesson.title` но semantically разные.
- **Depends on:** `LessonListComponent`, `LessonRepository`, `ThemeId`, `LessonDrillMapper.kt`
- **Canonical reference:** `06-api-contract.md:529`
- **Rationale:** Terminal drill-down before LessonPlaceholder leaf.

---

## Create DefaultLessonPlaceholderComponent

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultLessonPlaceholderComponent.kt`
- **Тип:** class
- **Сигнатура:** `class DefaultLessonPlaceholderComponent(componentContext: ComponentContext, config: QuizzesConfig.LessonPlaceholder) : ComponentContext by componentContext, LessonPlaceholderComponent`
- **Вход:**
  - `componentContext: ComponentContext`
  - `config: QuizzesConfig.LessonPlaceholder` — содержит `lessonId`, `lessonTitle`, `titles`
- **Поведение / Выход:**
  - `override val uiState = LessonPlaceholderUiState(lessonTitle = config.lessonTitle, titles = config.titles)`
  - НЕТ scope, НЕТ coroutines, НЕТ repository — статический
  - НЕТ `lifecycle.doOnDestroy` — нет ресурсов для освобождения
- **Edge cases:**
  - `config.lessonId` — не экспонируется через `uiState` (design spec: lessonId приватный, Placeholder только показывает заглушку)
- **Depends on:** `LessonPlaceholderComponent`, `LessonPlaceholderUiState`, `QuizzesConfig.LessonPlaceholder`
- **Canonical reference:** `06-api-contract.md:529`
- **Rationale:** Stateless component — нет repository, нет lifecycle resources. Простейший случай.

---

## Update DefaultQuizzesComponent — replace createChild() stub

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultQuizzesComponent.kt`
- **Тип:** class (modification)
- **Сигнатура:** existing `class DefaultQuizzesComponent`
- **Вход:** N/A (modification of existing `createChild()` function)
- **Поведение / Выход:**
  - Заменить Phase-03 stub `createChild()` на полный `when` блок:
    - `QuizzesConfig.Idle` → `QuizzesChild.Idle`
    - `QuizzesConfig.QuestList` → `QuizzesChild.QuestList(DefaultQuestListComponent(ctx, CatalogId(config.catalogId), config.titles, questRepository, navigation))`
    - `QuizzesConfig.SectionList` → `QuizzesChild.SectionList(DefaultSectionListComponent(ctx, QuestId(config.questId), config.titles, sectionRepository, navigation))`
    - `QuizzesConfig.ThemeList` → `QuizzesChild.ThemeList(DefaultThemeListComponent(ctx, SectionId(config.sectionId), config.titles, themeRepository, navigation))`
    - `QuizzesConfig.LessonList` → `QuizzesChild.LessonList(DefaultLessonListComponent(ctx, ThemeId(config.themeId), config.titles, lessonRepository, navigation))`
    - `QuizzesConfig.LessonPlaceholder` → `QuizzesChild.LessonPlaceholder(DefaultLessonPlaceholderComponent(ctx, config))`
- **Edge cases:**
  - Verify `navigation` field visibility: `private val navigation` → передаётся как parameter в child constructors — это нарушение encapsulation? Нет: child components получают ссылку на StackNavigation<QuizzesConfig> через конструктор (паттерн из `06-api-contract.md:529`). Navigation owner = DefaultQuizzesComponent (ADR-QS-03).
  - `when` блок exhaustive — sealed class, компилятор проверяет
- **Depends on:** все 5 DefaultXxx компонентов (Phase-04), `QuizzesConfig`, `QuizzesChild`, все 4 repository fields
- **Canonical reference:** `06-api-contract.md:392`
- **Rationale:** Полный childFactory необходим для корректной работы ChildStack — navigation.pushNew создаёт child instances через этот factory.
