---
date: 2026-04-25
authors: architect-high-level (§1-§9), architect-component (§10-§16)
feature: quizzes-screen
---

# API Contract: Quizzes Screen

<!-- HL_SECTION_START: §1-§9 (architect-high-level writes here) -->

## §1 QuizzesNavigator — navigation interface

**File**: `android/feature/quizzes-screen/presentation/.../navigation/QuizzesNavigator.kt`

```kotlin
interface QuizzesNavigator {
    /**
     * Entry from HomeQuestsScreen — push QuestList over Idle anchor.
     * catalogId: typed domain value (CatalogId from shared/core, no cross-feature import).
     */
    fun openQuestList(catalogId: CatalogId, catalogName: String)

    /**
     * Entry from MyQuestsScreen — push SectionList directly over Idle anchor.
     * titles = [catalogName, questTitle] — frozen snapshot for BreadcrumbBar (ADR-QS-10).
     */
    fun openSectionList(questId: QuestId, titles: List<String>)

    /**
     * Return to HomeQuests/MyQuests by popping back to Idle anchor.
     * Implementation: navigation.popToFirst(). AppShellScreen visibility derives
     * solely from `active is QuizzesChild.Idle` — no extra callback (ADR-QS-11 Option A).
     */
    fun dismissQuizzes()
}
```

`DefaultQuizzesComponent` implements `QuizzesComponent` (§11) which extends `QuizzesNavigator`. Lives **only** in `quizzes-screen/presentation` — `quest/presentation` does NOT import this interface (ADR-QS-01).

---

## §2 Cross-module entry points — callback signatures

Cross-module wiring через stdlib lambda callbacks предоставляемые `DefaultRootComponent`. `quest/presentation` не импортирует `QuizzesNavigator` (ADR-QS-01).

### HomeQuestsScreen entry

**Lambda type** передаваемый в `DefaultHomeQuestsComponent` constructor:

```kotlin
onCatalogDrillDown: (catalogId: CatalogId, catalogName: String) -> Unit
```

`CatalogId` — из `shared/core/catalog/domain` (core, не feature) → нет нарушения Invariant 3.

### MyQuestsScreen entry

**Lambda type** передаваемый в `DefaultMyQuestsComponent` constructor:

```kotlin
onQuestDrillDown: (quest: QuestDisplayItem) -> Unit
```

`QuestDisplayItem` — из `android/core/designsystem/model` (core, не feature) → нет нарушения Invariant 3.
После Finding #5: `QuestDisplayItem` несёт `catalogId: CatalogId` — все данные для titles resolution доступны в лямбде.

**Wiring в `DefaultRootComponent`** (canonical: §8):

```kotlin
onQuestDrillDown = { quest ->
    val catalogName = homeQuestsComponent.state.value.catalogs
        .find { it.id == quest.catalogId }?.name.orEmpty()
    quizzesComponent.openSectionList(quest.id, listOf(catalogName, quest.title))
}
```

`homeQuestsComponent.state.value.catalogs` — catalogs уже загружены к моменту первого тапа на quest.

---

## §3 QuestDisplayItem — catalogId extension

**File**: `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/model/QuestDisplayItem.kt`

`QuestDisplayItem` расширяется полем `catalogId: CatalogId`. Существующая core designsystem model — НЕ создаётся wrapper. (ADR-QS-05)

```kotlin
data class QuestDisplayItem(
    val id: QuestId,
    val catalogId: CatalogId,        // NEW — required for MyQuests entry breadcrumb resolution
    val title: String,
    val pictureUrl: String?,
    val averageRating: Float?,
    val averageRatingCount: Int = 0,
)
```

**Mapper update** — `android/feature/quest/presentation/.../mapper/QuestToDisplayItem.kt`:

```kotlin
fun Quest.toDisplayItem(): QuestDisplayItem = QuestDisplayItem(
    id = id,
    catalogId = catalogId,           // NEW — pass-through from domain model
    title = title,
    pictureUrl = pictureUrl,
    averageRating = averageRating,
    averageRatingCount = averageRatingCount,
)
```

**Blast radius** — все файлы требующие обновления (compile-break при добавлении required field):

| Файл | Изменение |
|------|-----------|
| `QuestDisplayItem.kt` | Добавить `val catalogId: CatalogId` |
| `QuestToDisplayItem.kt` | `catalogId = catalogId` в mapper |
| `QuestToDisplayItemTest.kt` | Добавить `catalogId` в тест round-trip |
| Все конструкторы `QuestDisplayItem(...)` в тестах | Передать `catalogId` |
| `QuestListUiState.Loaded.quests` (§14) | `List<QuestDisplayItem>` — не wrapper |

---

## §4 QuestRepository.observeByCatalog — новый метод domain interface

**File**: `shared/feature/quest/domain/src/commonMain/.../repository/QuestRepository.kt`

Additive extension — без изменения существующих сигнатур, без Room schema migration.

```kotlin
interface QuestRepository {
    // ... existing methods: observeMyQuests, observeByShelf, getById, refreshFromRemote ...

    /**
     * Observes non-archived quests in [catalogId] visible on [shelf].
     *
     * Filter: catalogId == :catalogId AND visibleOn CONTAINS shelf AND archived == false
     * Sort: lastModifiedAt DESC (Quest has no 'order' field — User Decision Q1)
     *
     * DAO pattern (same delimiter-wrapped LIKE as QuestDao.kt:30):
     *   WHERE catalogId = :catalogId
     *     AND (CHAR(31) || visibleOn || CHAR(31)) LIKE ('%'||CHAR(31)||:shelf||CHAR(31)||'%')
     *     AND archived = 0
     *   ORDER BY lastModifiedAt DESC
     *
     * shelf = "home" for all current call sites (QuestListComponent).
     */
    fun observeByCatalog(catalogId: CatalogId, shelf: String): Flow<List<Quest>>
}
```

**Blast radius — FakeQuestRepository copies** (compile-break без нового метода):

| Файл | Строка | Изменение |
|------|--------|-----------|
| `shared/feature/quest/domain/src/commonTest/.../fake/FakeQuestRepository.kt` | `:57` | `override fun observeByCatalog(...)` |
| `android/feature/quest/presentation/src/test/.../fake/FakeQuestRepository.kt` | `:11` | То же |
| `shared/core/sync/.../FakeQuestRepository.kt` | `:20` | То же (третья копия) |
| `FakeQuestLocalDataSource.kt` | `:14` | Если `QuestLocalDataSource` также расширяется |

Все файлы обновляются **единым PR** (несинхронное обновление → compile failure).

---

## §5 DefaultHomeQuestsComponent — modified constructor

**`HomeQuestsComponent` interface** не изменяется — `onCatalogClick(id: CatalogId)` остаётся. Изменяется только конструктор `DefaultHomeQuestsComponent` и реализация `onCatalogClick`.

**Modified constructor** — `android/feature/quest/presentation/.../DefaultHomeQuestsComponent.kt`:

```kotlin
class DefaultHomeQuestsComponent(
    componentContext: ComponentContext,
    private val observeCatalogs: ObserveCatalogsUseCase,
    private val onCatalogDrillDown: (CatalogId, String) -> Unit,  // NEW (typed CatalogId)
    mainContext: CoroutineContext = Dispatchers.Main.immediate,
) : HomeQuestsComponent, ComponentContext by componentContext {

    override fun onCatalogClick(id: CatalogId) {
        val catalogName = state.value.catalogs.find { it.id == id }?.name.orEmpty()
        onCatalogDrillDown(id, catalogName)          // typed CatalogId, not String
    }
}
```

**Koin factory update** — `QuestPresentationModule.kt`:

```kotlin
factory<HomeQuestsComponent> { (ctx: ComponentContext, onCatalogDrillDown: (CatalogId, String) -> Unit) ->
    DefaultHomeQuestsComponent(
        componentContext = ctx,
        observeCatalogs = get(),
        onCatalogDrillDown = onCatalogDrillDown,
    )
}
```

---

## §6 DefaultMyQuestsComponent — modified constructor + interface update

### MyQuestsComponent interface (updated)

```kotlin
interface MyQuestsComponent {
    val state: StateFlow<MyQuestsUiState>
    fun onCatalogSelected(id: CatalogId?)
    fun onCreateQuestClick()
    fun onQuestClick(quest: QuestDisplayItem)     // NEW — drill-down from MyQuests
}
```

### DefaultMyQuestsComponent constructor (modified)

```kotlin
class DefaultMyQuestsComponent(
    componentContext: ComponentContext,
    private val authRepo: AuthRepository,
    private val observeMyQuests: ObserveMyQuestsUseCase,
    private val observeCatalogs: ObserveCatalogsUseCase,
    private val navigator: Navigator,
    private val onQuestDrillDown: (quest: QuestDisplayItem) -> Unit,  // NEW (full typed object)
    mainContext: CoroutineContext = Dispatchers.Main.immediate,
) : MyQuestsComponent, ComponentContext by componentContext {

    override fun onQuestClick(quest: QuestDisplayItem) {
        onQuestDrillDown(quest)
        // DefaultRootComponent lambda resolves catalog name from homeQuestsComponent.state
    }
}
```

**Koin factory update** — `QuestPresentationModule.kt`:

```kotlin
factory<MyQuestsComponent> { (ctx: ComponentContext, nav: Navigator, onQuestDrillDown: (QuestDisplayItem) -> Unit) ->
    DefaultMyQuestsComponent(
        componentContext = ctx,
        authRepo = get(),
        observeMyQuests = get(),
        observeCatalogs = get(),
        navigator = nav,
        onQuestDrillDown = onQuestDrillDown,
    )
}
```

---

## §7 DefaultRootComponent — lambda wiring pattern

Conceptual pattern; точный код зависит от текущей `DefaultRootComponent.kt:130-131`. Это архитектурный контракт wiring, не implementation code.

```kotlin
// DefaultRootComponent — создание компонентов и lambda wiring (ADR-QS-01)

// Step 1: create quizzesComponent FIRST (нужен как capture в lambdas ниже)
val quizzesComponent: DefaultQuizzesComponent = quizzesFactory(
    childContext("QuizzesContent"),
)
// Visibility derived from active is Idle — no onDismiss callback (ADR-QS-11 Option A).

// Step 2: create homeQuestsComponent с typed lambda
val homeQuestsComponent: HomeQuestsComponent = homeQuestsFactory(
    ctx = childContext("HomeQuestsContent"),
    onCatalogDrillDown = { catalogId: CatalogId, catalogName: String ->
        quizzesComponent.openQuestList(catalogId, catalogName)
    }
)

// Step 3: create myQuestsComponent с typed lambda
val myQuestsComponent: MyQuestsComponent = myQuestsFactory(
    ctx = childContext("MyQuestsContent"),
    navigator = navigator,
    onQuestDrillDown = { quest: QuestDisplayItem ->
        val catalogName = homeQuestsComponent.state.value.catalogs
            .find { it.id == quest.catalogId }?.name.orEmpty()
        quizzesComponent.openSectionList(quest.id, listOf(catalogName, quest.title))
    }
)
```

**AppShellScreen rendering**:
```kotlin
val active = quizzesComponent.childStack.subscribeAsState().value.active.instance
if (active !is QuizzesChild.Idle) {
    QuizzesScreen(component = quizzesComponent)
}
```

**quizzesFactory type** (no onDismiss — visibility from Idle state per ADR-QS-11):
```kotlin
quizzesFactory: (componentContext: ComponentContext) -> QuizzesComponent
```

---

## §8 Blast radius summary — additive changes to existing files

Единым PR — все файлы изменяются синхронно во избежание compile failure:

| Файл | Изменение | Тип |
|------|-----------|-----|
| `QuestDisplayItem.kt` | `+val catalogId: CatalogId` | REQUIRED field |
| `QuestToDisplayItem.kt` | `+catalogId = catalogId` | mapper |
| `QuestToDisplayItemTest.kt` | `+catalogId = ...` в тестах | test update |
| `QuestRepository.kt` | `+observeByCatalog(...)` | additive interface |
| `FakeQuestRepository.kt` (×3 copies) | `+override fun observeByCatalog(...)` | compile fix |
| `FakeQuestLocalDataSource.kt` | условно — если `QuestLocalDataSource` расширяется | compile fix |
| `DefaultHomeQuestsComponent.kt` | `+onCatalogDrillDown` param | constructor |
| `DefaultMyQuestsComponent.kt` | `+onQuestDrillDown` param | constructor |
| `MyQuestsComponent.kt` | `+fun onQuestClick(quest: QuestDisplayItem)` | interface |
| `QuestPresentationModule.kt` | updated factory params | DI wiring |
| `DefaultRootComponent.kt` | lambda wiring + `quizzesFactory` param | integration |
| `AppShellPresentationModule.kt` | `quizzesFactory` registration | DI |

---

## §9 QuestCard — onLongClick extension

See canonical definition → §15 (architect-component zone).

Summary: add `onLongClick: ((QuestId) -> Unit)? = null` — backward-compatible default.
`combinedClickable` if non-null; `clickable` if null. BrandComponentsInvariantsTest compliance required.

<!-- HL_SECTION_END -->

---

## §10 QuizzesConfig — `@Serializable sealed class`

### Serialization constraint

Все `XxxId` value classes (`CatalogId`, `QuestId`, `SectionId`, `ThemeId`, `LessonId`) определены как `@JvmInline value class` **без** `@Serializable` (verified: `CatalogId.kt`, `QuestId.kt`, `SectionId.kt`, `ThemeId.kt`, `LessonId.kt`). Добавление `@Serializable` к ним требует изменений в domain — нарушение domain purity. Поэтому `QuizzesConfig` использует `String` поля. `DefaultQuizzesComponent.childFactory` оборачивает строки в value types при создании child components.

### Canonical definition

```kotlin
@Serializable
sealed class QuizzesConfig {

    /** Anchor конфига — всегда stack[0]. AppShellScreen не рендерит quizzes UI при Idle. */
    @Serializable
    data object Idle : QuizzesConfig()

    @Serializable
    data class QuestList(
        val catalogId: String,           // CatalogId.value
        val titles: List<String>,        // [catalogName]
    ) : QuizzesConfig()

    @Serializable
    data class SectionList(
        val questId: String,             // QuestId.value
        val titles: List<String>,        // [catalogName, questTitle]
    ) : QuizzesConfig()

    @Serializable
    data class ThemeList(
        val sectionId: String,           // SectionId.value
        val titles: List<String>,        // [catalogName, questTitle, sectionTitle]
    ) : QuizzesConfig()

    @Serializable
    data class LessonList(
        val themeId: String,             // ThemeId.value
        val titles: List<String>,        // [catalogName, questTitle, sectionTitle, themeTitle]
    ) : QuizzesConfig()

    @Serializable
    data class LessonPlaceholder(
        val lessonId: String,            // LessonId.value
        val lessonTitle: String,
        val titles: List<String>,        // полный путь включая lessonTitle последним
    ) : QuizzesConfig()
}
```

### Titles accumulation at pushNew

| Entry point | Вызов | Результат |
|-------------|-------|-----------|
| `openQuestList(catalogId, catalogName)` | — | `QuestList(catalogId.value, [catalogName])` |
| `QuestListComponent.onQuestClick(quest)` | pushNew | `SectionList(questId.value, parentTitles + [quest.title])` |
| `SectionListComponent.onSectionClick(section)` | pushNew | `ThemeList(sectionId.value, parentTitles + [section.title])` |
| `ThemeListComponent.onThemeClick(theme)` | pushNew | `LessonList(themeId.value, parentTitles + [theme.title])` |
| `LessonListComponent.onLessonClick(lesson)` | pushNew | `LessonPlaceholder(lessonId.value, lesson.title, parentTitles + [lesson.title])` |

`parentTitles` = `config.titles` текущего active child.

---

## §11 QuizzesComponent — interface + DefaultQuizzesComponent

```kotlin
interface QuizzesComponent {
    val childStack: Value<ChildStack<QuizzesConfig, QuizzesChild>>

    /** Entry from HomeQuests: catalog tap. */
    fun openQuestList(catalogId: CatalogId, catalogName: String)

    /** Entry from MyQuests: quest tap. */
    fun openSectionList(questId: QuestId, titles: List<String>)

    /**
     * Breadcrumb segment tap.
     * @param uiLevel 0-based index пользователем видимого сегмента
     *                (catalog=0, quest=1, section=2, theme=3).
     * Внутри: navigation.popTo(uiLevel + 1) — +1 offset для Idle anchor stack[0].
     */
    fun popToLevel(uiLevel: Int)

    /**
     * Dismiss quizzes overlay.
     * Calls popToFirst() — sets active to Idle.
     * AppShellScreen derives visibility from active is Idle (ADR-QS-11 Option A).
     * No external callback needed.
     */
    fun dismissQuizzes()
}
```

### DefaultQuizzesComponent constructor

```kotlin
class DefaultQuizzesComponent(
    componentContext: ComponentContext,
    private val questRepository: QuestRepository,
    private val sectionRepository: SectionRepository,
    private val themeRepository: ThemeRepository,
    private val lessonRepository: LessonRepository,
    // NO onDismiss — AppShellScreen derives visibility from active is Idle (ADR-QS-11)
) : ComponentContext by componentContext, QuizzesComponent {

    private val navigation = StackNavigation<QuizzesConfig>()

    // handleBackButton = false — manual BackCallback with explicit priority (ADR-QS-12)
    override val childStack: Value<ChildStack<QuizzesConfig, QuizzesChild>> =
        childStack(
            source = navigation,
            serializer = QuizzesConfig.serializer(),
            initialStack = { listOf(QuizzesConfig.Idle) },
            handleBackButton = false,
            childFactory = ::createChild,
        )

    private val backCallback = BackCallback(
        priority = BackCallback.PRIORITY_OVERLAY,  // выше default root; REQUIRES verify Essenty
        isEnabled = false,                          // initially disabled: stack=[Idle]
    ) {
        navigation.pop()
    }

    init {
        backHandler.register(backCallback)
        childStack.subscribe { stack ->
            // Enabled only while stack has entries above Idle (backStack non-empty)
            backCallback.isEnabled = stack.backStack.isNotEmpty()
        }
    }

    override fun openQuestList(catalogId: CatalogId, catalogName: String) {
        navigation.pushNew(QuizzesConfig.QuestList(catalogId.value, listOf(catalogName)))
    }

    override fun openSectionList(questId: QuestId, titles: List<String>) {
        navigation.pushNew(QuizzesConfig.SectionList(questId.value, titles))
    }

    override fun popToLevel(uiLevel: Int) {
        navigation.popTo(uiLevel + 1)  // +1 offset: Idle at stack[0]
    }

    override fun dismissQuizzes() {
        navigation.popToFirst()
        // No callback: AppShellScreen derives visibility from active is Idle (ADR-QS-11)
    }

    private fun createChild(
        config: QuizzesConfig,
        ctx: ComponentContext,
    ): QuizzesChild = when (config) {
        is QuizzesConfig.Idle ->
            QuizzesChild.Idle
        is QuizzesConfig.QuestList ->
            QuizzesChild.QuestList(DefaultQuestListComponent(ctx, CatalogId(config.catalogId), config.titles, questRepository, navigation))
        is QuizzesConfig.SectionList ->
            QuizzesChild.SectionList(DefaultSectionListComponent(ctx, QuestId(config.questId), config.titles, sectionRepository, navigation))
        is QuizzesConfig.ThemeList ->
            QuizzesChild.ThemeList(DefaultThemeListComponent(ctx, SectionId(config.sectionId), config.titles, themeRepository, navigation))
        is QuizzesConfig.LessonList ->
            QuizzesChild.LessonList(DefaultLessonListComponent(ctx, ThemeId(config.themeId), config.titles, lessonRepository, navigation))
        is QuizzesConfig.LessonPlaceholder ->
            QuizzesChild.LessonPlaceholder(DefaultLessonPlaceholderComponent(ctx, config))
    }
}
```

---

## §12 QuizzesChild — sealed interface

```kotlin
sealed interface QuizzesChild {
    /** Anchor — AppShellScreen не рендерит quizzes UI. */
    data object Idle : QuizzesChild

    data class QuestList(val component: QuestListComponent) : QuizzesChild
    data class SectionList(val component: SectionListComponent) : QuizzesChild
    data class ThemeList(val component: ThemeListComponent) : QuizzesChild
    data class LessonList(val component: LessonListComponent) : QuizzesChild
    data class LessonPlaceholder(val component: LessonPlaceholderComponent) : QuizzesChild
}
```

**AppShellScreen routing**:
```kotlin
val active = component.childStack.subscribeAsState().value.active.instance
if (active is QuizzesChild.Idle) return  // не рендерим overlay

QuizzesScreen(
    child = active,
    onSegmentClick = component::popToLevel,
    onBack = component::dismissQuizzes,
)
```

---

## §13 Child Component Interfaces

### QuestListComponent

```kotlin
interface QuestListComponent {
    val uiState: Value<QuestListUiState>
    val titles: List<String>
    fun onQuestClick(quest: QuestDisplayItem)    // QuestDisplayItem.catalogId contains catalog info (§3)
    fun onShareClick(quest: QuestDisplayItem)
}

class DefaultQuestListComponent(
    componentContext: ComponentContext,
    private val catalogId: CatalogId,
    override val titles: List<String>,
    private val questRepository: QuestRepository,
    private val navigation: StackNavigation<QuizzesConfig>,
) : ComponentContext by componentContext, QuestListComponent {
    // scope: SupervisorJob + lifecycle.doOnDestroy { job.cancel() }
    // state: questRepository.observeByCatalog(catalogId, shelf = "home")
    //        .map { quests -> ... }.stateIn(scope, Eagerly, QuestListUiState.Loading)
}
```

### SectionListComponent

```kotlin
interface SectionListComponent {
    val uiState: Value<HierarchyListUiState>
    val titles: List<String>
    fun onSectionClick(section: HierarchyItemUi)
}

class DefaultSectionListComponent(
    componentContext: ComponentContext,
    private val questId: QuestId,
    override val titles: List<String>,
    private val sectionRepository: SectionRepository,
    private val navigation: StackNavigation<QuizzesConfig>,
) : ComponentContext by componentContext, SectionListComponent
```

### ThemeListComponent

```kotlin
interface ThemeListComponent {
    val uiState: Value<HierarchyListUiState>
    val titles: List<String>
    fun onThemeClick(theme: HierarchyItemUi)
}

class DefaultThemeListComponent(
    componentContext: ComponentContext,
    private val sectionId: SectionId,
    override val titles: List<String>,
    private val themeRepository: ThemeRepository,
    private val navigation: StackNavigation<QuizzesConfig>,
) : ComponentContext by componentContext, ThemeListComponent
```

### LessonListComponent

```kotlin
interface LessonListComponent {
    val uiState: Value<HierarchyListUiState>
    val titles: List<String>
    fun onLessonClick(lesson: HierarchyItemUi)
}

class DefaultLessonListComponent(
    componentContext: ComponentContext,
    private val themeId: ThemeId,
    override val titles: List<String>,
    private val lessonRepository: LessonRepository,
    private val navigation: StackNavigation<QuizzesConfig>,
) : ComponentContext by componentContext, LessonListComponent
```

### LessonPlaceholderComponent

```kotlin
interface LessonPlaceholderComponent {
    val uiState: LessonPlaceholderUiState  // статический, нет repository
}

class DefaultLessonPlaceholderComponent(
    componentContext: ComponentContext,
    config: QuizzesConfig.LessonPlaceholder,
) : ComponentContext by componentContext, LessonPlaceholderComponent {
    override val uiState = LessonPlaceholderUiState(
        lessonTitle = config.lessonTitle,
        titles = config.titles,
    )
}
```

---

## §14 UI State Types

### QuestListUiState

```kotlin
sealed interface QuestListUiState {
    data object Loading : QuestListUiState
    data object Empty : QuestListUiState
    data class Loaded(
        val quests: List<QuestDisplayItem>,      // QuestDisplayItem.catalogId added in §3
        // expandedQuestId НЕ здесь — UI-only ephemeral state,
        // живёт как remember { mutableStateOf<QuestId?>(null) } в QuestListScreen (ADR-QS-07)
    ) : QuestListUiState
}

### HierarchyListUiState

```kotlin
sealed interface HierarchyListUiState {
    data object Loading : HierarchyListUiState
    data class Empty(val levelLabel: String) : HierarchyListUiState   // «Нет секций», «Нет тем», «Нет уроков»
    data class Loaded(val items: List<HierarchyItemUi>) : HierarchyListUiState
}
```

### HierarchyItemUi

```kotlin
data class HierarchyItemUi(
    val id: String,                  // raw String (SectionId.value / ThemeId.value / LessonId.value)
    val title: String,
    val orderLabel: String?,         // null если нет нумерации
    val subtitleCount: String? = null,  // reserved future field (spec/grounding delegated); null = not shown
)
```

Маппинг: `Section.toDrillItem()`, `Theme.toDrillItem()`, `Lesson.toDrillItem()` — extension functions в `quizzes-screen/presentation/mapper/`. Domain models остаются чистыми.

### LessonPlaceholderUiState

```kotlin
data class LessonPlaceholderUiState(
    val lessonTitle: String,
    val titles: List<String>,   // для BreadcrumbBar
)
```

---

## §15 Designsystem Component Signatures

### QuestCard (EXTENDED)

```kotlin
@Composable
fun QuestCard(
    item: QuestDisplayItem,
    onClick: (QuestId) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: ((QuestId) -> Unit)? = null,   // NEW — default null для backward compat
)
```

Внутри: `Modifier.combinedClickable(onClick = { onClick(item.id) }, onLongClick = onLongClick?.let { { it(item.id) } })` если `onLongClick != null`, иначе `Modifier.clickable { onClick(item.id) }`.

REQUIRES: проверить нужен ли `@OptIn(ExperimentalFoundationApi::class)` для `combinedClickable` в текущей версии Compose Foundation (зависит от версии BOM в `libs.versions.toml`).

### BreadcrumbBar (NEW — android/core/designsystem/components/)

```kotlin
@Composable
fun BreadcrumbBar(
    titles: List<String>,
    onSegmentClick: (uiLevel: Int) -> Unit,  // last segment не кликабелен (enforced internally)
    modifier: Modifier = Modifier,
)
```

Рендерит `LazyRow` или `Row` с `titles` сегментами. Разделитель `›`. Последний сегмент — некликабелен + visually distinct (alpha или другой стиль). `maxLines = 1`, `overflow = TextOverflow.Ellipsis` на каждом сегменте.

BrandComponentsInvariantsTest compliance: `@Preview` обязателен в файле.

### HierarchyItemCard (NEW — android/core/designsystem/components/)

Принимает только примитивы — designsystem НЕ импортирует feature presentation types (ADR-QS-09 Option A).

```kotlin
@Composable
fun HierarchyItemCard(
    title: String,
    orderLabel: String? = null,
    subtitleCount: String? = null,   // delegated future field (spec/grounding reserved)
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
)
```

Caller в `quizzes-screen/presentation`:
```kotlin
HierarchyItemCard(
    title = item.title,
    orderLabel = item.orderLabel,
    subtitleCount = item.subtitleCount,
    onClick = { onSectionClick(item) },
)
```

Layout: `Row { if (orderLabel != null) Text(orderLabel, width=fixed); Text(title, Modifier.weight(1f)); if (subtitleCount != null) Text(subtitleCount) }`. `combinedClickable` если `onLongClick != null`. Минимальная высота 48dp (Material3 a11y).

BrandComponentsInvariantsTest compliance: `@Preview` обязателен в файле.

---

## §16 Repository Wiring

### Child component → Repository interface mapping

| Child component | Repository interface | Method | Location |
|-----------------|---------------------|--------|----------|
| `DefaultQuestListComponent` | `QuestRepository` | `observeByCatalog(catalogId: CatalogId, shelf: String): Flow<List<Quest>>` **(NEW — отсутствует)** | `shared/feature/quest/domain/.../QuestRepository.kt` |
| `DefaultSectionListComponent` | `SectionRepository` | `observeByQuest(questId: QuestId): Flow<List<Section>>` (exists, `:24`) | `shared/feature/section/domain/` |
| `DefaultThemeListComponent` | `ThemeRepository` | `observeBySection(sectionId: SectionId): Flow<List<Theme>>` (exists, `:21`) | `shared/feature/theme/domain/` |
| `DefaultLessonListComponent` | `LessonRepository` | `observeByTheme(themeId: ThemeId): Flow<List<Lesson>>` (exists, `:21`) | `shared/feature/lesson/domain/` |
| `DefaultLessonPlaceholderComponent` | — | нет repository | — |

### QuestRepository.observeByCatalog — NEW method

Отсутствует в текущем `QuestRepository.kt:39-50` (verified research). Требует:
1. Добавить метод в interface (`shared/feature/quest/domain/` — backend-dev).
2. Имплементировать в `QuestRepositoryImpl` через новый `QuestDao.observeByCatalog(catalogId: String, shelf: String)` с SQL: `WHERE catalogId = :catalogId AND visibleOn LIKE '%\u001F' || :shelf || '\u001F%' AND archived = 0 ORDER BY lastModifiedAt DESC`.
3. Обновить fakes: `FakeQuestRepository` в `shared/feature/quest/domain/.../FakeQuestRepository.kt`, `shared/feature/quest/data/.../FakeQuestRepository.kt`, `shared/core/sync/.../FakeQuestRepository.kt` (три места — verified research, Codex finding #4).

### Koin DI wiring (quizzesPresentationModule)

```kotlin
val quizzesPresentationModule = module {
    // No onDismiss param — visibility derived from Idle state (ADR-QS-11 Option A)
    factory<QuizzesComponent> { (ctx: ComponentContext) ->
        DefaultQuizzesComponent(
            componentContext = ctx,
            questRepository = get(),
            sectionRepository = get(),
            themeRepository = get(),
            lessonRepository = get(),
        )
    }
}
```

`DefaultRootComponent` получает `QuizzesComponent` через:
```kotlin
private val quizzesComponent: QuizzesComponent = get<QuizzesComponent>(
    parameters = { parametersOf(childContext("quizzes")) }
)
```

---

*Canonical signatures в этом файле — единственный источник правды. `01-architecture.md` и `02-behavior.md` ссылаются на §10-§16 описательно, не дублируют signatures.*
