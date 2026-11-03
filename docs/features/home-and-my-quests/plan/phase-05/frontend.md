---
phase: 05
role: frontend-dev
---

# Phase-05 Frontend Tasks

Создание всех UI/presentation компонентов. Scaffold (build.gradle.kts) — backend-dev.

---

## Pattern Invariants

- `DefaultMyQuestsComponent` ДОЛЖЕН использовать `lifecycle.coroutineScope(mainContext + SupervisorJob())` через Essenty
- `mainContext: CoroutineContext` — constructor param (не hardcode `Dispatchers.Main`) для testability
- `DefaultMyQuestsComponent` ДОЛЖЕН использовать `authRepo.observeUid().flatMapLatest { uid -> ... }` (НЕ collect + nested launch)
- Если uid == null → `flowOf(emptyList<Quest>())` (не вызывать observeMyQuests)
- `QuestCard.kt` + `StarRating.kt` — ТОЛЬКО `MaterialTheme.colorScheme.primary` для синего; НИКАКОГО `Color(0xFF...)`
- Каждый файл в `components/` ОБЯЗАН иметь минимум 1 `@Preview` composable
- `AppShellScreen.LocalTabContent` when-блок — exhaustive, все LocalConfig subtypes обработаны

---

## 1. Create QuestDisplayItem (designsystem model)

- **Файл:** `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/model/QuestDisplayItem.kt`
- **Тип:** data class
- **Сигнатура:** `data class QuestDisplayItem(val id: QuestId, val title: String, val pictureUrl: String?, val averageRating: Float?, val averageRatingCount: Int = 0)`
- **Вход:** 5 fields
- **Поведение / Выход:** pure data holder; no logic
- **Edge cases:**
  - `averageRating == null` → не rated (display outline stars)
  - `pictureUrl == null` → placeholder icon
  - `averageRatingCount == 0` → hide count label
- **Depends on:** `QuestId` (Walking Skeleton `shared/feature/quest/domain`)
- **Canonical reference:** `06-api-contract.md` §7 QuestDisplayItem

---

## 2. Create StarRating

- **Файл:** `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/StarRating.kt`
- **Тип:** Composable function
- **Сигнатура:** `@Composable fun StarRating(rating: Float?, modifier: Modifier = Modifier)`
- **Вход:** `rating: Float?` (0.0..3.0 step 0.1; null = no ratings)
- **Поведение / Выход:**
  - 3 stars total; each star = filled, partially filled, or outline
  - rating=2.7 → star1 full, star2 full, star3 70% filled
  - rating=null → all 3 outline stars
  - rating=0f → all 3 outline stars
  - Color: `MaterialTheme.colorScheme.primary` (maps to `GoogleBlue`)
  - Size: Material3 default star icon (~18dp)
- **Edge cases:**
  - `rating = 0.0f` → all outline (same as null display? Design decision: yes, show outline for 0)
  - `rating = 3.0f` → all 3 filled
  - partial fill: `(rating % 1.0f)` determines fraction for fractional star
  - Clip/overlay technique for partial star (e.g., Box with fraction-width Color overlay)
- **Depends on:** MaterialTheme, SchoolQuizTheme
- **@Preview annotations (6 total, REQUIRED by BrandComponentsInvariantsTest):**
  - `StarRating0Preview()`, `StarRatingHalfPreview()`, `StarRating15Preview()`, `StarRating27Preview()`, `StarRating30Preview()`, `StarRatingNullPreview()`
- **Canonical reference:** `06-api-contract.md` §13 (designsystem); `04-testing.md` §10; `0-spec.md` FR#10

---

## 3. Create QuestCard

- **Файл:** `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/QuestCard.kt`
- **Тип:** Composable function
- **Сигнатура:** `@Composable fun QuestCard(item: QuestDisplayItem, onClick: (QuestId) -> Unit, modifier: Modifier = Modifier)`
- **Вход:** `QuestDisplayItem`, `onClick` callback
- **Поведение / Выход:**
  - Layout: card with picture (AsyncImage or placeholder), title, StarRating, optional count
  - `pictureUrl != null` → `AsyncImage(model = pictureUrl, ...)` (Coil 3.4.0)
  - `pictureUrl == null` → placeholder icon (CategoryIcon or similar from existing designsystem)
  - `averageRating != null` → show StarRating(rating)
  - `averageRating == null` → show StarRating(null) (outline)
  - Min touch target: ≥48dp (Material3 a11y)
  - onClick triggers with item.id
- **Edge cases:**
  - Long title → `maxLines = 1, overflow = TextOverflow.Ellipsis` (consistent with CatalogGrid)
  - `averageRatingCount > 0` → optionally show count below stars (per spec FR#11 "future display")
- **Depends on:** QuestDisplayItem (task #1), StarRating (task #2), Coil 3.4.0
- **@Preview annotations (4 total, REQUIRED):**
  - `QuestCardEmptyPreview()`, `QuestCardRatedPreview()`, `QuestCardUnratedPreview()`, `QuestCardLongTitlePreview()`
- **Canonical reference:** `06-api-contract.md` §13 (designsystem); `04-testing.md` §10; Problem 7 in `2-grounding.md`

---

## 4. Update CatalogGrid typography (polish)

- **Файл:** `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/CatalogGrid.kt`
- **Тип:** Composable update
- **Сигнатура:** existing `fun CatalogGrid(...)` — update text style
- **Вход:** existing parameters
- **Поведение / Выход:**
  - Catalog name text: change from whatever current style to `MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)`
  - `maxLines = 1, overflow = TextOverflow.Ellipsis`
  - Card corners: 16dp (RoundedCornerShape(16.dp))
  - Grid gap: 12dp
- **Edge cases:**
  - Typography change should not break existing `CatalogWarmCacheIntegrationTest` (no text-based assertions in integration tests)
  - Visual regression: check in Preview
- **Canonical reference:** AC#21 (`0-spec.md:1121`)

---

## 5. Create QuestToDisplayItem mapper

- **Файл:** `android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/mapper/QuestToDisplayItem.kt`
- **Тип:** extension function
- **Сигнатура:** `fun Quest.toDisplayItem(): QuestDisplayItem`
- **Поведение / Выход:** маппит Quest domain object → QuestDisplayItem display model
- **Canonical reference:** `06-api-contract.md` §7 Extension functions

---

## 6. Create MyQuestsComponent interface + UiState

- **Файл:** `android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/MyQuestsComponent.kt`
- **Тип:** interface + data class
- **Сигнатура:** `interface MyQuestsComponent { val state: StateFlow<MyQuestsUiState>; fun onCatalogSelected(id: CatalogId?); fun onCreateQuestClick() }`
- **Поведение / Выход:**
  - `onCatalogSelected(null)` = "Все категории" (no filter)
  - `onCreateQuestClick()` → navigates to OpenQuestCreate
  - `state` emits on any auth/catalog/quest change
- **Canonical reference:** `06-api-contract.md` §6.1 MyQuestsComponent

### MyQuestsUiState

- **Тип:** data class
- **Сигнатура:** `data class MyQuestsUiState(val quests: List<QuestDisplayItem> = emptyList(), val catalogs: List<CatalogDisplayItem> = emptyList(), val selectedCatalogId: CatalogId? = null, val isGuest: Boolean = false, val isLoading: Boolean = false)`
- **Canonical reference:** `06-api-contract.md` §6.1

---

## 7. Create DefaultMyQuestsComponent

- **Файл:** `android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultMyQuestsComponent.kt`
- **Тип:** class
- **Сигнатура:** `class DefaultMyQuestsComponent(componentContext: ComponentContext, authRepo: AuthRepository, observeMyQuests: ObserveMyQuestsUseCase, observeCatalogs: ObserveCatalogsUseCase, navigator: Navigator) : MyQuestsComponent, ComponentContext by componentContext`
- **Вход:** 5 params (per `06-api-contract.md §6.1 DefaultMyQuestsComponent` — параметры: `componentContext`, `authRepo`, `observeMyQuests`, `observeCatalogs`, `navigator`)
- **Поведение / Выход:**
  - `val scope = lifecycle.coroutineScope(mainContext + SupervisorJob())`
  - `val selectedCatalog = MutableStateFlow<CatalogId?>(null)`
  - `authRepo.observeUid().flatMapLatest { uid -> ... }` — если uid==null → flowOf(emptyList()); иначе `observeMyQuests(uid, selectedCatalog.value)`
  - Combine uid flow + selectedCatalog + catalogs flow → emit `MyQuestsUiState`
  - `state: StateFlow<MyQuestsUiState>` — stateIn scope with SharingStarted.WhileSubscribed
  - `onCatalogSelected(id)` → `selectedCatalog.value = id`
  - `onCreateQuestClick()` → `navigator.goTo(Destination.OpenQuestCreate)`
- **Edge cases:**
  - uid = null → `isGuest = true`, quests = emptyList, observeMyQuests NOT called
  - Mid-session login → `flatMapLatest` cancels previous inner flow, subscribes new one
  - selectedCatalog = null → `observeMyQuests(uid, null)` → all catalogs
  - Component lifecycle ends → coroutineScope cancelled → no leaks
- **Concurrency note:** `flatMapLatest` ensures previous inner Flow cancelled on uid change — safe for mid-session login/logout
- **Stateful field reset invariant:** `selectedCatalog` — resettable via `onCatalogSelected(null)`; scope tied to component lifecycle (auto-cancelled)
- **Depends on:** AuthRepository, ObserveMyQuestsUseCase, ObserveCatalogsUseCase, Navigator, Decompose lifecycle-coroutines
- **Canonical reference:** `06-api-contract.md` §6.1 DefaultMyQuestsComponent

---

## 8. Create HomeQuestsComponent interface + DefaultHomeQuestsComponent

- **Файл:** `android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/HomeQuestsComponent.kt`
- **Тип:** interface
- **Сигнатура:** `interface HomeQuestsComponent { val state: StateFlow<HomeQuestsUiState>; fun onCatalogClick(id: CatalogId) }`

### HomeQuestsUiState

`data class HomeQuestsUiState(val catalogs: List<CatalogDisplayItem> = emptyList(), val isLoading: Boolean = false)`

- **Файл:** `android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultHomeQuestsComponent.kt`
- **Тип:** class
- **Сигнатура:** `class DefaultHomeQuestsComponent(componentContext: ComponentContext, observeCatalogs: ObserveCatalogsUseCase) : HomeQuestsComponent, ComponentContext by componentContext`
- **Поведение / Выход:**
  - `observeCatalogs().map { catalogs -> HomeQuestsUiState(catalogs = catalogs.map { it.toDisplayItem() }) }` → stateIn scope
  - `onCatalogClick(id)` → TODO placeholder for future catalog detail navigation
- **Depends on:** ObserveCatalogsUseCase, Decompose lifecycle
- **Canonical reference:** `06-api-contract.md` §6.2

---

## 9. Create MyQuestsScreen

- **Файл:** `android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/ui/MyQuestsScreen.kt`
- **Тип:** Composable
- **Сигнатура:** `@Composable fun MyQuestsScreen(component: MyQuestsComponent, modifier: Modifier = Modifier)`
- **Поведение / Выход:**
  - `val state by component.state.collectAsState()`
  - Top bar area: `CatalogSpinner(items=state.catalogs, selectedId=state.selectedCatalogId, onSelectionChanged=component::onCatalogSelected)` (plate pod top bar — ExposedDropdownMenuBox)
  - LazyColumn: `items(state.quests) { quest -> QuestCard(item=quest, onClick={ component.onCatalogSelected(null) /* TODO */ }) }`
  - Empty state: `if (state.quests.isEmpty()) EmptyStateComposable(...)` — icon + text + arrow pointing to FAB
  - FAB: `FloatingActionButton(onClick=component::onCreateQuestClick) { Icon(Icons.Add, ...) }`
  - Min FAB touch target: 56dp (Material3)
- **Edge cases:**
  - `state.isGuest == true` → same empty state as 0 quests (no login CTA per spec)
  - `state.isLoading == true` → CircularProgressIndicator (optional placeholder)
- **Canonical reference:** AC#23-29; DFD 3 (`02-behavior.md`)

---

## 10. Create HomeQuestsScreen

- **Файл:** `android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/ui/HomeQuestsScreen.kt`
- **Тип:** Composable
- **Сигнатура:** `@Composable fun HomeQuestsScreen(component: HomeQuestsComponent, modifier: Modifier = Modifier)`
- **Поведение / Выход:**
  - `val state by component.state.collectAsState()`
  - `CatalogGrid(items=state.catalogs, onItemClick=component::onCatalogClick)`
  - Loading state: CircularProgressIndicator
- **Canonical reference:** AC#21-22; DFD 2 (`02-behavior.md`)

---

## 11. Create QuestPresentationModule

- **Файл:** `android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/di/QuestPresentationModule.kt`
- **Тип:** Koin module
- **Сигнатура:** `val questPresentationModule = module { factory<MyQuestsComponent> { params -> DefaultMyQuestsComponent(...) }; factory<HomeQuestsComponent> { params -> DefaultHomeQuestsComponent(...) } }`
- **Поведение / Выход (SSoT: `06-api-contract.md:800-807`):**
  - `factory<MyQuestsComponent> { (ctx: ComponentContext) -> DefaultMyQuestsComponent(ctx, get(), get(), get(), get()) }` — `ctx` via parametersOf() call site, domain deps via `get()` singleton
  - `factory<HomeQuestsComponent> { (ctx: ComponentContext) -> DefaultHomeQuestsComponent(ctx, get()) }` — `ctx` via parametersOf()
  - Call site (в AppShellScreen или parent Component): `getKoin().get<MyQuestsComponent> { parametersOf(componentContext) }` или `by inject<MyQuestsComponent> { parametersOf(componentContext) }`
  - Navigator — 5-й параметр `DefaultMyQuestsComponent`; передаётся через `get()` singleton если Navigator — Koin singleton, либо через factory params если per-scope. Проверить с `06-api-contract.md §13` factory definition: `factory<MyQuestsComponent> { (ctx: ComponentContext) -> DefaultMyQuestsComponent(ctx, get(), get(), get(), get()) }` — Navigator = 5-й `get()` = singleton binding
- **Canonical reference:** `06-api-contract.md:800-807` questPresentationModule SSoT

---

## 12. Update AppShellScreen.LocalTabContent routing

- **Файл:** `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt`
- **Тип:** Composable update
- **Сигнатура:** update `LocalTabContent` when-block
- **Вход:** existing when(config) block at lines 307-311
- **Поведение / Выход:**
  - `MyQuestsRoot → MyQuestsScreen(component = ...)` — component created via `koinInject<MyQuestsComponent>()` or passed from parent
  - `HomeQuestsRoot → HomeQuestsScreen(component = ...)` — replaces `CatalogGridSection`
  - `QuestCreateRoot → UnderConstructionScreen("Создание квеста в разработке")`
  - `SettingsRoot, DesignCatalogRoot, EmptyRoot` → existing behavior unchanged
  - Remove `CatalogGridSection` inline function (or keep if referenced elsewhere — check)
- **Edge cases:**
  - exhaustive when on all LocalConfig subtypes — QuestCreateRoot case MUST be present
  - Component instantiation: either pass components down from parent OR use Koin injection at call site; follow existing pattern for other LocalTabContent screens
- **Canonical reference:** `2-grounding.md` Problem 3 Fix Shape; AC#23, AC#29
