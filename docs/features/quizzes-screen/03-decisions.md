---
date: 2026-04-25
authors: architect-component (ADR-QS-06..12), architect-high-level (ADR-QS-01..05)
feature: quizzes-screen
---

# Architecture Decisions: Quizzes Screen

<!-- HL_SECTION_START: ADR-QS-01..05 (architect-high-level writes here) -->

## ADR-QS-01: Lambda callbacks для cross-module wiring (не QuizzesNavigator в quest/presentation)

**Status**: ACCEPTED — Invariant 3 enforcement

### Context

`HomeQuestsComponent.onCatalogClick` и `MyQuestsComponent.onQuestClick` (оба в `android/feature/quest/presentation/`) должны инициировать drill-down в `DefaultQuizzesComponent` (в `android/feature/quizzes-screen/presentation/`).

Нужно решить: как передать навигационный callback не нарушая Invariant 3 (`docs/invariants.md`: *no bidirectional import between feature modules*). Текущее состояние: `android/feature/quest/presentation/` имеет ZERO импортов из других `android/feature/*` модулей (`1-research.md:79`). Добавление `import ...quizzes_screen.presentation.QuizzesNavigator` нарушило бы этот инвариант.

### Decision

**Lambda callbacks через `DefaultRootComponent`** — единственный coordination shell.

1. `DefaultHomeQuestsComponent` получает `onCatalogDrillDown: (CatalogId, String) -> Unit` в конструктор. `CatalogId` — `shared/core/catalog/domain` (core, не feature) → Invariant 3 не нарушается.
2. `DefaultMyQuestsComponent` получает `onQuestDrillDown: (QuestDisplayItem) -> Unit` в конструктор. `QuestDisplayItem` — `android/core/designsystem/model` (core, не feature) → Invariant 3 не нарушается. После добавления `catalogId: CatalogId` в §3, объект несёт все данные для titles resolution.
3. `DefaultRootComponent` (`app-shell/presentation`) создаёт `quizzesComponent` первым, затем передаёт lambda closures в factory lambdas. Closure для MyQuests: `quizzesComponent.openSectionList(quest.id, listOf(catalogName, quest.title))` где `catalogName` берётся из `homeQuestsComponent.state.value.catalogs`.

`QuizzesNavigator` interface живёт **только** в `quizzes-screen/presentation`. `quest/presentation` не знает о его существовании. `CatalogId` и `QuestDisplayItem` — core types, нет cross-feature import.

Canonical wiring code — `06-api-contract.md §7`.

### Alternatives Considered

**Вариант B — QuizzesNavigator interface в `shared/core/` модуле**:
- (-) Overkill: контракт специфичен для одной фичи, не для core
- (-) Coupling shared/core → quizzes lifecycle; при удалении фичи core надо очищать
- (-) Противоречит паттерну ADR-CMP-51: feature-specific contracts живут в feature module

**Вариант C — EventBus / shared `MutableSharedFlow` в core**:
- (-) Неявная coupling через строковые event types → runtime ошибки, не compile-time
- (-) EventBus — анти-паттерн для прямых navigation events (нет compile-time гарантий что consumer зарегистрирован)

### Consequences

- `android/feature/quest/presentation/` — нулевые cross-feature imports сохраняются; Invariant 3 выполнен
- Coordination code полностью в `DefaultRootComponent.kt` — единственное место для чтения и изменения
- Testability: `DefaultHomeQuestsComponent` тестируется с mock lambda без зависимости от `quizzes-screen`
- Lambda types — core types (`CatalogId`, `QuestDisplayItem`), не raw `String`; type-safety сохраняется без cross-feature import
- `QuestDisplayItem.catalogId` (§3) — делает `onQuestDrillDown: (QuestDisplayItem) -> Unit` достаточным для catalog name lookup в `DefaultRootComponent` closure

---

## ADR-QS-02: @Serializable QuizzesConfig — первый сериализованный ChildStack

**Status**: ACCEPTED — User Decision Q2

### Context

Spec AC#21: *«process death: navigation state восстанавливается»*. Decompose `childStack()` API принимает параметр `serializer: SerializationStrategy<C>?`. При `serializer = null` — ChildStack не сериализуется, state теряется при process death.

Все существующие ChildStack в проекте используют `serializer = null` (`LocalTabComponent.kt:22`, `ShopTabComponent.kt:22`, `EventsTabComponent.kt:22`, `InternetTabComponent.kt:22`). Для `QuizzesConfig` user явно выбрал восстановление (User Decision Q2).

### Decision

**`@Serializable sealed class QuizzesConfig`** с `childStack(serializer = QuizzesConfig.serializer(), ...)`.

Все поля `QuizzesConfig` — только `String` и `List<String>`. Decompose сериализует `List<QuizzesConfig>` через `ListSerializer(QuizzesConfig.serializer())` → Bundle при `Activity.onSaveInstanceState`. AC#21 выполняется.

Требует `kotlinx-serialization` Gradle plugin в `android/feature/quizzes-screen/presentation/build.gradle.kts` (backend-dev ownership, scaffold rules).

Canonical `QuizzesConfig` definition — `06-api-contract.md §2`.

### Alternatives Considered

**Вариант A — `serializer = null` (как все existing stacks)**:
- (+) Нет изменений Gradle config, нет новых зависимостей
- (-) **AC#21 не выполняется** — navigation state теряется при process death
- (-) User Decision Q2 явно выбрал сериализацию

**Вариант B — Parcelable**:
- (+) Android native, без extra Gradle plugin
- (-) `kotlin-parcelize` — другой plugin, не переиспользуется ни одним existing module; `kotlinx-serialization` уже в проекте (shared KMP modules)
- (-) `data object Idle` с `@Parcelize` требует `@TypeParceler` или manual `Creator` — boilerplate
- (-) Противоречит KMP-first подходу; configs не могут переехать в shared при Parcelable

### Consequences

- Первый сериализованный ChildStack в проекте — потенциальный образец для других фич
- `kotlinx-serialization` plugin добавляется в `build.gradle.kts` (backend-dev task)
- Все fields в `QuizzesConfig` ОБЯЗАНЫ быть `String/List<String>/Int/Long/Boolean` — никаких `@JvmInline value class` (serialization edge cases)
- Bundle overhead: каждый active config несёт `titles: List<String>` с кумулятивными заголовками всей иерархии. Максимальная глубина — 5 уровней с 4 titles-элементами — объём пренебрежимо мал, но это не ≤ 5 items, а ≤ 5 конфигов × N titles каждый

**Stable serialized schema — contract для будущих изменений**:
- Все `@Serializable` variants — immutable after initial release. Новые fields добавляются только с `@SerialName` и default value (пример: `val newField: String = ""`).
- Удаление/переименование variants → breaking change: старый Bundle при restore throws `SerializationException`.
- **Restore failure mitigation**: при `SerializationException` на restore — Decompose fallback не встроен. Mitigation: try/catch в `DefaultQuizzesComponent` init при `stateKeeper.consume<List<QuizzesConfig>>(...)`, при exception — reset к `listOf(QuizzesConfig.Idle)`. Это поведение задокументировать в implementation notes.
- Future config variants (новый уровень иерархии) добавляются как новый `@Serializable data class` в sealed class без изменения существующих — forward-compatible.

---

## ADR-QS-03: Isolated inner ChildStack (не расширение app-shell NavStack FSM)

**Status**: ACCEPTED — User Decision Q3

### Context

Drill-down навигация Quest→Section→Theme→Lesson требует stack semantics. Два места где stack может жить:

1. **app-shell NavStack FSM** (`shared/feature/app-shell/domain/model/NavStack.kt`) — domain FSM управляющий tab navigation. `DefaultRootComponent` использует только `nav.navigate(transformer = { all })` (`DefaultRootComponent.kt:279`); `push/pop/popTo` не используются нигде (`1-research.md:27`).
2. **Изолированный ChildStack** в новом `QuizzesComponent`.

`LocalConfig` (sealed interface, 6 entries) не имеет `@Serializable` — `TabConfig.kt:15`: *«serialization is a data-layer concern»*. Расширение требовало бы нарушить этот принцип и изменить `shared/feature/app-shell/domain/` — blast radius на все features.

### Decision

**Isolated inner `ChildStack<QuizzesConfig, QuizzesChild>`** в `DefaultQuizzesComponent`, не расширение NavStack FSM.

`DefaultQuizzesComponent` владеет своим `StackNavigation<QuizzesConfig>`. `QuizzesConfig` — полностью изолированный `@Serializable sealed class`. App-shell NavStack FSM, `LocalConfig`, `DefaultRootComponent` navigation logic — без изменений.

### Alternatives Considered

**Вариант B — Расширение app-shell NavStack FSM**:
- (-) Изменения в `shared/feature/app-shell/domain/` — blast radius на все feature modules
- (-) `LocalConfig` без `@Serializable`; добавление drill-down configs нарушает принцип `TabConfig.kt:15`
- (-) Drill-down state — feature-specific деталь, принадлежит `quizzes-screen`, не core app-shell domain
- (-) Весь существующий navigation code (FSM transitions, usecases) нужно расширить для linear stack semantics — значительный рефакторинг
- (+) Один унифицированный navigation mechanism

**Вариант C — Bottom Sheet / Modal**:
- (-) Back gesture закрывает весь drill-down, не pop one level
- (-) Breadcrumb pop semantics несовместимы с Modal lifecycle
- (-) Inconsistent с легасным `QuizFragment` паттерном (spec: «как в легаси сделать»)

### Consequences

- `QuizzesComponent` — self-contained unit: navigation logic, configs, factories, serialization
- App-shell, `DefaultRootComponent`, `LocalTabComponent`, NavStack FSM — без архитектурных изменений
- `QuizzesComponent` рендерится как overlay поверх tab content в `AppShellScreen.kt` когда `active.configuration != Idle`
- Back handling через Decompose `BackCallback` (детали — ADR-QS-12)

---

## ADR-QS-04: Idle anchor config — высокоуровневое обоснование

**Status**: ACCEPTED (implementation details — ADR-QS-11)

### Context

Decompose `ChildStack` не может быть пустым. При drill-down нужно поведение: *«возврат из корня drill-down → скрыть overlay, показать HomeQuests/MyQuests»*. Решение принято на component-уровне в ADR-QS-11.

Этот ADR фиксирует module-level tradeoff: `QuizzesComponent` существует всё время lifetime `DefaultRootComponent`, не только в период активного drill-down.

### Decision

**`QuizzesConfig.Idle` как постоянный anchor** — `QuizzesComponent` создаётся один раз в `DefaultRootComponent.init`, не пересоздаётся при каждом открытии/закрытии drill-down.

Module-level следствие: `DefaultRootComponent` всегда держит `quizzesComponent` в памяти. Это даёт:
- Стабильный Decompose lifecycle для `QuizzesComponent`
- Возможность `DefaultRootComponent` передавать `quizzesComponent` как `QuizzesNavigator` в lambda callbacks до первого открытия drill-down

### Alternatives Considered

**Вариант B — Lazy creation / destroy-on-dismiss QuizzesComponent**:
- (-) Lifecycle coordination: `DefaultRootComponent` должен управлять созданием/уничтожением `ComponentContext` для `quizzesComponent`
- (-) Lambda callbacks захватывают ссылку на component — при pересоздании captured reference устаревает
- (-) Koin `factory<QuizzesComponent>` + cleanup предыдущего instance — нестандартный паттерн
- (+) Память не занята когда drill-down не активен (пренебрежимо мало — только Decompose state, нет Flow collectors при `Idle`)

**Вариант C — Always-created QuizzesComponent + отдельный visibility flag**:
Всегда создаётся как в нашем решении, но видимостью управляет `MutableStateFlow<Boolean>` или Decompose `Value<Boolean>` в `DefaultQuizzesComponent`:
- (-) Dual source of truth: и `active.configuration == Idle`, и `isVisible == true/false`. При рассинхронизации (process death восстанавливает `Idle` config, но `isVisible` не сериализуется) overlay отображается некорректно
- (-) `AppShellScreen` должен подписываться на два источника состояния вместо одного (`childStack.active.configuration`)
- (-) Не идиоматично для Decompose: ChildStack уже является state machine; adding visibility flag создаёт parallel state outside the stack
- (-) При rotate `isVisible` в `MutableStateFlow` не сохраняется без `instanceKeeper` — дополнительный boilerplate
- (+) Idle config не нужен как отдельный variant — semantically чище

Итог: `Idle` anchor — единственный источник состояния «overlay скрыт», сериализуется вместе со стеком, идиоматичен для Decompose.

### Consequences

- `quizzesComponent` инициализируется в `DefaultRootComponent.init` через `childContext("QuizzesContent")`
- При `active.configuration == QuizzesConfig.Idle` нет Flow collectors в child components (childFactory для Idle возвращает `QuizzesChild.Idle` без repos)
- Memory overhead при Idle: только Decompose lifecycle state + `StackNavigation` — negligible
- Canonical: `06-api-contract.md §8`

---

## ADR-QS-05: QuestDisplayItem.catalogId — расширение designsystem model

**Status**: ACCEPTED — User Decision Q4

### Context

Spec п.5 / User Decision Q4: при входе из `MyQuestsScreen` тап на `QuestCard` → push `SectionList`. Breadcrumb начинается с уровня catalog (`catalogName > questName`). `state.selectedCatalogId: CatalogId?` может быть `null` (фильтр «все каталоги») — catalog name нельзя взять оттуда.

Решение: resolve catalog name по `quest.catalogId` из `state.catalogs`. Но `QuestDisplayItem` (живёт в `android/core/designsystem/model/`) не несёт `catalogId` (`QuestDisplayItem.kt:14-20`).

Вопрос: где разместить `catalogId` — в `QuestDisplayItem` (designsystem model) или через отдельный механизм.

### Decision

**Расширить `QuestDisplayItem`** новым полем `catalogId: CatalogId`.

`QuestDisplayItem` — presentation model в `android/core/designsystem/model/`; добавление `catalogId` (domain value type) не нарушает layer boundaries (`CatalogId` уже импортируется в `DefaultHomeQuestsComponent.kt:6`). Mapper `QuestToDisplayItem.kt:14` обновляется: `catalogId = quest.catalogId`.

Canonical: `06-api-contract.md §3`.

### Alternatives Considered

**Вариант B — Отдельный lookup при click: передавать `Quest` domain model вместо `QuestDisplayItem`**:
- (-) UI layer получает domain model напрямую — нарушает layer separation (`clean-architecture.md`)
- (-) `QuestListScreen` не должен знать о `Quest` domain type

**Вариант C — Дополнительный `selectedCatalogName: String?` в `MyQuestsUiState`**:
- (-) Работает только при non-null `selectedCatalogId`; при «все каталоги» — null, нет catalog name
- (-) `MyQuestsComponent.onQuestClick` вынужден проверять `state.selectedCatalogId != null`

### Consequences

- `QuestDisplayItem.kt` получает новое поле `val catalogId: CatalogId`
- `QuestToDisplayItem.kt:14` обновляется: `catalogId = catalogId` (mapper pass-through)
- `QuestToDisplayItemTest.kt` обновляется: тест проверяет `catalogId` round-trip
- Все existing callers (`MyQuestsUiState`, `HomeQuestsUiState` через mapper) компилируются без изменений если mapper обновлён
- BLAST RADIUS: `QuestDisplayItem` используется в `QuestCard.kt`, `MyQuestsScreen.kt`, `HomeQuestsScreen.kt`, тестах — проверить при implementation

<!-- HL_SECTION_END -->

---

## ADR-QS-06 — QuestCard.onLongClick: расширение существующего компонента vs wrapper

**Status**: Accepted  
**Context**: Spec п.12 требует long-press контекстное меню (Share) на QuestCard в `QuestListScreen`. Текущая сигнатура `QuestCard.kt:41`: `fun QuestCard(item: QuestDisplayItem, onClick: (QuestId) -> Unit, modifier: Modifier)` — `onLongClick` отсутствует. `QuestCard` живёт в `android/core/designsystem/components/` → покрыт `BrandComponentsInvariantsTest`.

**Decision**: Вариант A — расширить существующий `QuestCard` nullable параметром `onLongClick: ((QuestId) -> Unit)? = null` (default null).

**Rationale**:
- Backward compatible: все существующие вызовы компилируются без изменений (default null → `Modifier.clickable` без `combinedClickable`).
- Один компонент в дизайн-системе вместо split ownership.
- `BrandComponentsInvariantsTest` требует `@Preview` — он уже есть в файле; после изменения preview обновляется в том же файле.

**Alternatives Considered**:
- **(B) Wrapper composable `QuestCardWithLongPress` в quizzes-screen/presentation** — отклонён: дублирование UI логики; два компонента для одной карточки усложняет дизайн-систему; нарушает принцип single source of truth для QuestCard UI.

**Consequences**:
- `QuestCard.kt` получает `onLongClick: ((QuestId) -> Unit)? = null`; внутри — `Modifier.combinedClickable` если `onLongClick != null`, иначе `Modifier.clickable`.
- `@Preview` в том же файле остаётся; вызов без `onLongClick` = preview без long-press.
- `BrandComponentsInvariantsTest` проходит без изменений.
- **Haptic feedback**: `combinedClickable` включает haptic по умолчанию — long-press вибрирует. Явный вызов `LocalHapticFeedback` не нужен, но и не запрещён для override.
- **A11y (TalkBack)**: добавить `onLongClickLabel = stringResource(R.string.action_share)` в `combinedClickable` — иначе TalkBack не объявит действие long-press.
- **Compose UI test**: проверить что `click` и `long-click` не аннулируют друг друга — оба fired независимо (`performClick()` и `performLongClick()` в отдельных тестах).
- REQUIRES: verify `@OptIn(ExperimentalFoundationApi::class)` нужен ли для `combinedClickable` в текущей версии Compose Foundation (некоторые версии требуют, некоторые — нет).

---

## ADR-QS-07 — combinedClickable + Material3 DropdownMenu: first usage паттерн

**Status**: Accepted  
**Context**: Spec п.12 требует long-press → контекстное меню «Поделиться». В `android/` codebase 0 existing usages `combinedClickable` (verified grep). `CatalogSpinner.kt:55` использует `ExposedDropdownMenuBox` — другой компонент. `DropdownMenu` (standalone Material3) не используется.

**Decision**: Вариант B — Material3 standalone `DropdownMenu` + `Modifier.combinedClickable`.

**Implementation pattern**:
```
Box {
    QuestCard(
        item = quest,
        onLongClick = { expandedQuestId = quest.id },
        ...
    )
    DropdownMenu(
        expanded = expandedQuestId == quest.id,
        onDismissRequest = { expandedQuestId = null }
    ) {
        DropdownMenuItem(text = { Text("Поделиться") }, onClick = { ... })
    }
}
```
`expandedQuestId: QuestId?` — локальный `remember { mutableStateOf<QuestId?>(null) }` в `QuestListScreen`. **НЕ передаётся в component state** — pure UI ephemeral state. При recomposition или pop/push стека menu закроется (корректное поведение).

**Alternatives Considered**:
- **(A) Custom popup / Dialog** — отклонён: избыточная кастомизация для простого меню из 1 пункта; Material3 `DropdownMenu` покрывает кейс и соответствует Material Design guidelines.
- **(C) `expandedQuestId` в `QuestListUiState.Loaded`** — отклонён: menu open/close — ephemeral UI state, нет business value в сериализации; добавление `setExpandedQuest(id)` в component усложняет interface без необходимости; rotation/process death должны закрывать menu (ожидаемое UX поведение).

**Consequences**:
- Первое использование `combinedClickable` и standalone `DropdownMenu` в новом codebase — документируется как pattern для будущих фич.
- Haptic feedback включён по умолчанию Compose Foundation (не требует явного вызова `LocalHapticFeedback`).
- `expandedQuestId` — composition-local state, **не** в `QuestListUiState` — нет SSoT конфликта. `QuestListUiState.Loaded` содержит только `quests: List<QuestDisplayItem>` (без `expandedQuestId`).

---

## ADR-QS-08 — Intent.ACTION_SEND из Compose: createChooser vs bare startActivity

**Status**: Accepted  
**Context**: Share intent нужно диспетчеризовать из Composable (не из Activity/Fragment). Первое использование `Intent.ACTION_SEND` в новом `android/` codebase (в legacy есть `ReferralFragment.kt:154`). Spec п.15a: ошибка «нет приложений» = молчаливый лог, без UI.

**Decision**: Вариант A — `Intent.createChooser(intent, null)` обёрнутый в `try/catch (e: ActivityNotFoundException)`.

**Implementation pattern**:
```kotlin
val context = LocalContext.current
// в onClick DropdownMenuItem:
DropdownMenuItem(
    text = { Text(stringResource(R.string.action_share)) },
    onClick = {
        expandedQuestId = null  // ВСЕГДА ПЕРВЫМ — меню закрывается до startActivity
        val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Квест «${quest.title}» — $appName")
        }
        try {
            context.startActivity(Intent.createChooser(intent, null))
        } catch (e: ActivityNotFoundException) {
            Log.w("QuestListScreen", "Share unavailable", e)
            // spec п.15a: молчаливый лог, нет UI уведомления
        }
    }
)
```
`expandedQuestId = null` до `try` — гарантирует закрытие menu даже при exception. На exception path menu уже закрыт.

**Alternatives Considered**:
- **(B) Bare `context.startActivity(intent)`** — отклонён: без `createChooser` пользователь не видит меню выбора приложения (запускается default handler); `createChooser` — Android-recommended подход для share flows.

**Consequences**:
- `LocalContext.current` — единственное Android coupling в composable (допустимо, это UI layer).
- `ActivityNotFoundException` не показывает UI уведомление (spec п.15a).
- Pattern задокументирован для будущих share flows.

---

## ADR-QS-09 — HierarchyItemCard: универсальный компонент vs три отдельных

**Status**: Accepted  
**Context**: Section, Theme, Lesson карточки в drill-down списках имеют одинаковую структуру: `orderLabel` (слева, опционально) + `title` (fills width). Spec п.10 делегировал решение.

**Decision**: Вариант B — один `HierarchyItemCard` компонент с примитивными параметрами.

**Canonical signature (Option A — UI primitives)**:
```kotlin
@Composable
fun HierarchyItemCard(
    title: String,
    orderLabel: String? = null,
    subtitleCount: String? = null,   // delegated future field (spec/grounding placeholder)
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
)
```

Caller (в `quizzes-screen/presentation`):
```kotlin
HierarchyItemCard(
    title = item.title,
    orderLabel = item.orderLabel,
    subtitleCount = item.subtitleCount,
    onClick = { onSectionClick(item) },
)
```

`HierarchyItemUi` остаётся в `quizzes-screen/presentation` — presentation-layer DTO, не передаётся в designsystem компонент.

**Alternatives Considered**:
- **(A) Три отдельных компонента (SectionCard, ThemeCard, LessonCard)** — отклонён: идентичный UI, дублирование кода; изменения отступов/типографии нужно менять в 3 местах; нарушает DRY.
- **(B) HierarchyItemCard принимает HierarchyItemUi из quizzes-screen/presentation** — отклонён: `android/core/designsystem` не может импортировать `android/feature/quizzes-screen/presentation` — нарушение layer boundary (core ↔ feature). Option A (примитивы) — standard designsystem pattern, аналогично существующим компонентам.

**Consequences**:
- `HierarchyItemCard` живёт в `android/core/designsystem/components/`, принимает только примитивы — нет зависимостей на feature modules.
- `BrandComponentsInvariantsTest` требует `@Preview` в файле.
- `onLongClick = null` → `Modifier.clickable`; `onLongClick != null` → `Modifier.combinedClickable` (аналогично QuestCard, ADR-QS-06).
- `HierarchyItemUi` — presentation-layer DTO в `quizzes-screen/presentation`, не в designsystem.
- `subtitleCount: String?` — зарезервирован для будущего отображения (spec/grounding delegated decision); default null.

---

## ADR-QS-10 — Frozen breadcrumb titles vs live observers

**Status**: Accepted  
**Context**: BreadcrumbBar показывает путь от catalog до current level. Данные для breadcrumb (каталог, квест, секция, тема) могут меняться в фоне через sync. User Decision #14 зафиксировал выбор.

**Decision**: Вариант B — frozen titles: снимок имён на момент `pushNew`, хранится в `QuizzesConfig.titles: List<String>`.

**Rationale**:
- Простая реализация: titles — immutable часть конфига, сериализуемы вместе со стеком (process death).
-避免 cascade flow subscriptions на каждый уровень breadcrumb.
- User Decision #14: явно выбран этот вариант.

**Alternatives Considered**:
- **(A) Live observer chain** — подписки на `CatalogRepository.observeById`, `QuestRepository.observeById`, ... для каждого уровня breadcrumb одновременно. Отклонён: N live subscriptions на один экран (cascade); синхронизация имён при рефакторинге требует изменений во всех уровнях; user принял вариант B.

**Consequences**:
- Breadcrumb не обновляется при фоновом rename (sync). Текущий список — live (Room Flow); только заголовки — frozen.
- При process death titles восстанавливаются из Bundle (сериализованы вместе с конфигом).
- `BreadcrumbBar` всегда получает `config.titles` — единый источник правды.
- **Rollback note**: если в будущем потребуются live breadcrumbs (rename отображается во время drill-down) — нужно добавить parent ID chains в configs (например `parentIds: List<String>` для каждого уровня) и поднять observer chain в компоненте. Migration saved bundle потребует версионирования config (новые поля с defaults или отдельная `@Serializable` версия).

---

## ADR-QS-11 — Idle anchor в ChildStack (Decompose non-empty constraint)

**Status**: Accepted  
**Context**: Decompose `ChildStack` не может быть пустым: `pop()` — no-op при `size == 1`. `AppShellTransitions.kt:103-112` при пустом LOCAL root emits `SystemBack`. Quizzes-screen — overlay внутри app-shell, должен уметь «закрыться» без SystemBack.

**Decision**: Вариант A — `QuizzesConfig.Idle` как 6-й variant, всегда stack[0].

**Механизм (Option A — single source of truth)**:
- `DefaultQuizzesComponent` инициализируется с `initialStack = listOf(QuizzesConfig.Idle)`.
- `openQuestList` / `openSectionList` → `navigation.pushNew(...)` поверх Idle.
- `dismissQuizzes()` → только `navigation.popToFirst()` — возврат к Idle. **Никакого дополнительного callback.**
- AppShellScreen: `subscribeAsState()` на `childStack` → `if (active.instance is QuizzesChild.Idle) return` — overlay visibility derived from Idle state, не от отдельного флага.
- `childFactory(Idle)` → возвращает `QuizzesChild.Idle` (data object, без component).

Visibility = single source: Idle active → overlay hidden. Idiomatic Decompose pattern.

**Alternatives Considered**:
- **(B) Nullable QuizzesComponent** — `quizzesComponent: DefaultQuizzesComponent?` в `DefaultRootComponent`, создаётся lazily при первом `openQuestList`. Отклонён: требует nullable handling во всех обращениях; Koin lazy injection усложняет DI scope; Decompose ComponentContext lifecycle начинается только при создании — нет гарантии что lazy creation не конкурирует с rotation.

**Consequences**:
- ChildStack никогда не пустой → нет SystemBack при dismiss.
- Breadcrumb `popTo` offset: `navigation.popTo(uiLevel + 1)` — Idle занимает stack[0].
- Idle сериализуется вместе со стеком при process death (stack[0] всегда Idle при restore).
- `QuizzesConfig.Idle` — `data object` (нет полей, нет titles).

---

## ADR-QS-12 — BackCallback priority: manual registration vs childStack handleBackButton

**Status**: Accepted  
**Context**: Quizzes overlay должен перехватывать system back раньше `DefaultRootComponent.backHandler`. `DefaultRootComponent.kt:139` регистрирует `BackCallback(isEnabled = true)` без явного priority → default priority. Decompose `childStack(handleBackButton = true)` также создаёт BackCallback с default priority — нет параметра priority в `childStack` API (verified Decompose 3.1.0 `ChildStackFactory.kt`). При одинаковом priority Essenty dispatches LIFO — порядок регистрации определяет поведение, что хрупко.

**Decision**: Вариант A — `childStack(handleBackButton = false)` + явная ручная регистрация `BackCallback(priority = PRIORITY_OVERLAY)` в `DefaultQuizzesComponent.init`.

**Implementation**:
```kotlin
private val backCallback = BackCallback(
    priority = BackCallback.PRIORITY_OVERLAY,  // выше default root handler
    isEnabled = false,                          // initially disabled (stack=[Idle])
) {
    navigation.pop()  // pop one level; исходя из invariant: когда backStack пустой — callback disabled
}

init {
    backHandler.register(backCallback)
    childStack.subscribe { stack ->
        // Enabled только пока stack содержит элементы ВЫШЕ Idle (backStack non-empty)
        backCallback.isEnabled = stack.backStack.isNotEmpty()
    }
}
```

**Back delegation reconciliation**: когда `stack == [Idle]` — `backCallback.isEnabled = false`. Back достигает `DefaultRootComponent.backHandler` → `onDestination(Destination.Back)` → AppShell FSM (`DefaultRootComponent.kt:141`). В этот момент overlay уже скрыт (active is Idle → AppShellScreen не рендерит quizzes UI), и FSM корректно обрабатывает back (переключает tab или system back). `dismissQuizzes()` = просто `popToFirst()` — callback не нужен (see ADR-QS-11).

**Alternatives Considered**:
- **(B) `childStack(handleBackButton = true)`** — создаёт BackCallback с default priority. Проблема: тот же priority что у root backHandler → LIFO order, хрупко при рефакторинге `DefaultRootComponent.init`. Отклонён.
- **(C) Registration order** — зарегистрировать quizzes ПОСЛЕ root. Отклонён: неявный контракт, ломается при рефакторинге.

**Consequences**:
- `childStack(handleBackButton = false)` — Decompose не управляет BackCallback автоматически.
- Ручная подписка на `childStack.subscribe` для sync `isEnabled`.
- При `stack.backStack.isEmpty()` (stack=[Idle]) → callback disabled → `Destination.Back` через FSM → корректное поведение.
- REQUIRES: verify `BackCallback.PRIORITY_OVERLAY` constant в Essenty 2.x (`BackCallback.kt`). Если absent — numeric `priority = 100` (выше default = 0).

---

## ADR-QS-13 — DefaultXxx принимают `config: QuizzesConfig.XxxList` целиком (Phase-04 fact)

**Status**: Accepted — Phase-04 implementation decision

**Context**: `06-api-contract.md §13` описывал `DefaultXxx` конструкторы с raw полями (`private val catalogId: CatalogId`, `override val titles: List<String>`). Test-dev сгенерировал тесты с `config: QuizzesConfig.XxxList` параметром до реализации.

**Decision**: DefaultXxx компоненты принимают `config: QuizzesConfig.XxxList` как единый параметр. Extraction (`CatalogId(config.catalogId)`, `config.titles`) происходит внутри конструктора тела. Сигнатуры интерфейсов (`QuestListComponent`, etc.) не меняются — они не знают о `QuizzesConfig`.

**Consequences**: `06-api-contract.md §13` DefaultXxx constructor signatures отражают фактическую реализацию (config-based). Phase-05 screens получают компоненты через `QuizzesChild.XxxList.component` — они видят только interface, не config.

---

## ADR-QS-14 — AppShellScreen принимает DefaultRootComponent (concrete), не RootComponent (interface)

**Status**: Accepted — pre-existing by-design pattern (Phase-07 review finding)

**Context**: `AppShellScreen.kt` принимает `rootComponent: DefaultRootComponent` вместо `rootComponent: RootComponent` (interface из `shared/feature/app-shell/domain`). Code reviewer отметил это как medium deviation.

**Decision**: Оставить конкретный тип — by-design, pre-existing паттерн проекта.

**Rationale**:
- `AppShellScreen` — единственный consumer `DefaultRootComponent`; абстракция через `RootComponent` не даёт testability benefit (instrumented Compose тесты используют реальный `DefaultRootComponent` через `createTestComponentContext()`).
- `quizzesComponent`, `homeQuestsComponent`, `myQuestsComponent` — `internal`/`val` поля `DefaultRootComponent`, не экспонируются через `RootComponent` interface. Добавление их в interface вводит Decompose зависимость в `shared/feature/app-shell/domain/` — нарушение domain purity rule.
- Альтернатива — `RootComponentSurface` interface в `app-shell/presentation` — overly engineered для single-caller composable.

**Consequences**: `AppShellScreen` не тестируется с fake `RootComponent` в unit-тестах; все тесты AppShell — instrumented или snapshot через `DefaultRootComponent` напрямую. Это принятый tradeoff.

---

## ADR-QS-15 — quizzes-screen/presentation → lesson-runner/presentation: childFactory consumer side

**Status**: **SUPERSEDED by ADR-LR-16 + ADR-LR-17 (2026-04-27)**  
**Date**: 2026-04-27 (superseded note)  

> ⚠️ Initial design assumed factory + interface live in `android/core/navigation/`. Cycle analysis (ADR-LR-16 в `docs/features/lesson-runner/03-decisions.md`) установил что это создаёт circular Gradle dependency: `core/navigation → lesson-runner/presentation` (для `RunnerUiState`/`RunnerEvent`) + `lesson-runner/presentation → core/navigation` (для interface implementation). Корректное решение: factory + `LessonRunnerRootComponent` interface живут в `lesson-runner/presentation`. `quizzes-screen/presentation → lesson-runner/presentation` — одностороннее направление, разрешённое ADR-LR-17 (Compose composition exception). Текст ниже сохранён для исторической трассировки. Любые planner/dev refs должны идти на ADR-QS-17 + ADR-LR-16.

**Original Status**: Accepted (replaced).  
**Context**: `DefaultQuizzesComponent.childFactory` обрабатывает `QuizzesConfig.LessonRunner` (новый variant, ADR-LR-07). Для инстанциирования дочернего компонента нужно либо напрямую создать `DefaultLessonRunnerRootComponent` (cross-feature import presentation→presentation), либо получить его через фабрику из DI.

Existing precedent: `DefaultQuizzesComponent.childFactory` создаёт `DefaultQuestListComponent`, `DefaultSectionListComponent`, `DefaultThemeListComponent`, `DefaultLessonListComponent` — все принадлежат **тому же** `quizzes-screen/presentation` модулю, нет cross-feature imports.

`clean-architecture.md` запрещает прямой import `android/feature/A/presentation → android/feature/B/presentation`. Нужно решение для consumer side.

**Decision**: Вариант A (обновлён per consensus) — `LessonRunnerComponentFactory` функциональный интерфейс в **`android/core/navigation/`**, реализуется снаружи (в Koin composition root) конкретным лямбдой создающим `DefaultLessonRunnerRootComponent`. `LessonRunnerRootComponent` interface — там же. `quizzes-screen/presentation` импортирует из `android/core/navigation` (не cross-feature).

```kotlin
// в android/core/navigation/ — оба контракта
fun interface LessonRunnerComponentFactory {
    fun create(
        componentContext: ComponentContext,
        config: QuizzesConfig.LessonRunner,
    ): LessonRunnerRootComponent
}

// в apps/android-next (composition root) — конкретная фабрика
val quizzesScreenModule = module {
    factory<QuizzesComponent> { (ctx: ComponentContext) ->
        DefaultQuizzesComponent(
            componentContext = ctx,
            // ...
            lessonRunnerFactory = { context, cfg ->
                DefaultLessonRunnerRootComponent(
                    componentContext = context,
                    config = cfg,
                    lessonRepository = get(),
                    lessonAttemptRepository = get(),
                    // ...
                )
            },
        )
    }
}
```

`LessonRunnerRootComponent` interface (return type) живёт в `android/core/navigation/` — уже существующий core модуль. Это исключает прямой import `quizzes-screen/presentation → lesson-runner/presentation`. Canonical signature — architect-component зона (`06-api-contract.md §LR-9`).

**Alternatives Considered**:
- **(B) Прямой import `DefaultLessonRunnerRootComponent`** — отклонён: нарушает `clean-architecture.md` правило `android/feature/A/presentation → android/feature/B/presentation: NO`.
- **(C) Перенести всю навигацию в `apps/android-next`** — отклонён: расплывание navigation logic в composition root; существующий паттерн квизового экрана — локальная childFactory в DefaultQuizzesComponent.

**Consequences**:
- `quizzes-screen/presentation` не импортирует `lesson-runner/presentation` — граница соблюдена.
- Composition root (`apps/android-next`) знает оба presentation модуля — единственное место с cross-feature coupling.
- `LessonRunnerComponentFactory` — fun interface в `quizzes-screen/presentation`, инстанциируется только в Koin модуле.
- Scope: Koin `factory` для компонента (не `single` — ComponentContext lifecycle-bound).

---

## ADR-QS-16 — quizzes-screen/presentation → lesson-runner:domain: LessonAttemptRepository import

**Status**: Accepted  
**Date**: 2026-04-26  
**Context**: `DefaultLessonListComponent` должен показывать `bestStars` и вычислять `hardUnlocked` для каждого урока в списке. Данные приходят из `LessonAttemptRepository.observeByLessonId(lessonId)` — репозиторий принадлежит `lesson-runner:domain`. Без этого импорта Lesson list не может отобразить прогресс пользователя.

`clean-architecture.md` требует ADR для cross-feature imports `shared/feature/A → shared/feature/B`.

Existing precedent: `quizzes-screen/presentation` уже импортирует `quest:domain`, `section:domain`, `theme:domain`, `lesson:domain` — все через `shared/feature/<slug>/domain`. Добавление `lesson-runner:domain` — тот же паттерн.

**Decision**: Вариант A — прямой import `shared/feature/lesson-runner/domain` в `quizzes-screen/presentation`.

Направление зависимости: `quizzes-screen/presentation → lesson-runner:domain` (одностороннее). `lesson-runner:domain` не импортирует `quizzes-screen/presentation` — bidirectional coupling отсутствует.

```kotlin
// DefaultLessonListComponent.kt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository

class DefaultLessonListComponent(
    // ...
    private val lessonAttemptRepository: LessonAttemptRepository,  // NEW
    private val authRepository: AuthRepository,                      // NEW
) : ComponentContext by componentContext, LessonListComponent
```

**Scope ограничен**: только `LessonAttemptRepository` (interface). Никаких imports из `lesson-runner:data` или `lesson-runner/presentation`.

**Alternatives Considered**:
- **(B) Вынести LessonAttemptRepository в `shared/core/`** — отклонён: репозиторий специфичен для lesson-runner domain (знает об `Attempt`, `Stars`, `Difficulty`) — это не generic core contract. Переезд в core потянет за собой `Attempt`, `Stars`, `Difficulty` — core разбухает product-level domain concepts.
- **(C) Вычислять bestStars через Lesson.top3** — отклонён: `top3` — агрегированные данные сервера (Cloud Function), недоступны offline-first; `bestStars` — локальный прогресс пользователя из Room, должен работать без сети.

**Consequences**:
- `shared/feature/lesson-runner/domain` добавляется в `build.gradle.kts` зависимости `android/feature/quizzes-screen/presentation` — scaffold change для `backend-dev`.
- `DefaultQuizzesComponent` получает `lessonAttemptRepository` и `authRepository` → передаёт в `DefaultLessonListComponent` через childFactory.
- Validation: `rg "^import .*lesson_runner" android/feature/quizzes-screen/presentation/src/main -g "*.kt"` должен находить только `domain` imports, никаких `data` или `presentation`.
- Invariant 3 (no bidirectional coupling): `rg "^import .*quizzes_screen" shared/feature/lesson-runner -g "*.kt"` должен быть пустым (note: filesystem path uses hyphen, Kotlin package uses underscore).

---

## ADR-QS-17 — Compose composition exception для ChildStack cross-feature rendering

**Status**: Accepted (user-approved 2026-04-27)

### Context

`QuizzesScreen.kt` содержит exhaustive `when(active)` ChildStack dispatch block. При добавлении `QuizzesChild.LessonRunner` (ADR-LR-07) нужно вызвать `LessonRunnerScreen(child.component)` — `@Composable` функция из `android/feature/lesson-runner/presentation`. Это создаёт прямой import `android/feature/quizzes-screen/presentation → android/feature/lesson-runner/presentation`.

`clean-architecture.md:55` запрещает: `android/feature/A/presentation → android/feature/B/presentation` direct import: **NO**.

Правило написано без учёта Decompose ChildStack UI rendering pattern, в котором parent screen хостирует child `@Composable` функции разных feature modules. Запрет нуждается в явном ADR-исключении для этого паттерна.

**Precedent в проекте (verified grep):**
`android/feature/app-shell/presentation/src/main/.../ui/AppShellScreen.kt:53-56` уже содержит:
```
import com.tpov.schoolquiz.android.feature.quest.presentation.ui.HomeQuestsScreen
import com.tpov.schoolquiz.android.feature.quest.presentation.ui.MyQuestsScreen
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen.QuizzesScreen
```
Это три cross-feature presentation imports в production code — установленный проектный паттерн до lesson-runner.

### Decision

Разрешить **односторонний** import `android/feature/quizzes-screen/presentation → android/feature/lesson-runner/presentation` **исключительно** для `@Composable` rendering target (`LessonRunnerScreen` composable function).

Импортируемый артефакт: только `@Composable fun LessonRunnerScreen(component: LessonRunnerRootComponent)` (или аналогичная top-level screen composable из lesson-runner/presentation).

Обратное направление `android/feature/lesson-runner/presentation → android/feature/quizzes-screen/presentation` остаётся заблокировано — является blocker invariant независимо от механизма.

### Constraints

- Импортируется **только** `@Composable` screen function — NOT component classes, NOT use cases, NOT repositories, NOT internal state types, NOT sealed interfaces internal to lesson-runner.
- Verifiable через grep:
  ```bash
  rg "^import com\.tpov\.schoolquiz\.android\.feature\.lesson_runner\.presentation" \
    android/feature/quizzes-screen/presentation/src/main -g "*.kt"
  ```
  Допустимые совпадения: только строки содержащие `LessonRunnerScreen` (или аналогичную screen composable). Любые другие типы из `lesson-runner/presentation` = blocker.
- Reverse blocker:
  ```bash
  rg "^import com\.tpov\.schoolquiz\.android\.feature\.quizzes_screen\.presentation" \
    android/feature/lesson-runner/presentation/src/main -g "*.kt"
  ```
  Должно быть пусто всегда — нарушение = blocker независимо от типа импортируемого символа.

### Alternatives Considered

- **(A) Slot pattern** — `LessonRunnerNavigationSlot` @Composable extension в `android/core/navigation/`; quizzes-screen вызывает slot; каждая feature регистрирует свой slot в одном центральном registry. Отклонён: обёрточный overhead, slot registration complexity, все фичи должны регистрировать slotы где-то централизованно без compile-time гарантии.
- **(B) Централизовать lesson-runner в app-shell вместо quizzes-screen** — нарушает navigation hierarchy: lesson runner живёт внутри quiz drill-down (Quest → Section → Theme → Lesson → Runner), а не как top-level destination. AppShell не является правильным хостом для gameplay screen.
- **(C) ADR exception (CHOSEN)** — minimal change, follows Decompose convention, backed by existing project precedent (AppShellScreen imports 3 sibling features).

### Rationale

Decompose ChildStack pattern (Decompose 3.1.0 docs) требует чтобы parent screen знал @Composable функции child screens. Запрет в `clean-architecture.md` написан до full Decompose adoption и был ориентирован на предотвращение bidirectional coupling и business logic leakage — не Compose function composition. Existing `AppShellScreen.kt:53-56` является de-facto прецедентом этого исключения в проекте.

### Risk if wrong (6 months out)

Очень низкий: паттерн широко установлен в Decompose ecosystem и уже существует в проекте. Если `LessonRunnerScreen` переименуется или переедет — compile error при сборке, не runtime. Если bidirectional coupling проникнет — grep check в architect-reviewer выявит немедленно.

### Architect-reviewer checklist addition (Phase-06)

Добавить в architect-reviewer review check для Phase-06:
```bash
# ADR-QS-17: только LessonRunnerScreen import из lesson-runner/presentation
rg "^import com\.tpov\.schoolquiz\.android\.feature\.lesson_runner\.presentation" \
  android/feature/quizzes-screen/presentation/src/main -g "*.kt"
# Expected: только строки с LessonRunnerScreen

# ADR-QS-17 reverse blocker
rg "^import com\.tpov\.schoolquiz\.android\.feature\.quizzes_screen\.presentation" \
  android/feature/lesson-runner/presentation/src/main -g "*.kt"
# Expected: empty
```
