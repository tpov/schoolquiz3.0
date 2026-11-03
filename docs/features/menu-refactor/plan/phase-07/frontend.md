---
phase: 07
role: frontend-dev
---

# Phase 07 — Frontend Tasks

## Pattern Invariants

- `LaunchedEffect(rootComponent)` — ключ `rootComponent`, не `Unit`: предотвращает дублирование collectors при recomposition. Canonical: `AppShellScreen.kt:97` использует `LaunchedEffect(drawerState)` с object-key; `AppShellScreen.kt:109` использует `LaunchedEffect(state.isDrawerOpen)` с value-key. Для event stream — object reference (component) как ключ — повторный запуск только при замене rootComponent.
- `DrawerFooter` НЕ создаёт coroutines напрямую — только вызывает callbacks (`onVersionTap`, `onSyncNow`). Canonical: текущий `DrawerFooter.kt` (approx line 32-78) — leaf composable без LaunchedEffect; coroutine запускается в `AppShellScreen.kt:97` (screen-level), не в leaf composable.
- Version text tap target: `Modifier.clickable(onClick = onVersionTap).defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)` (accessibility ≥ 48dp). Canonical reference: `DrawerFooter.kt:59` — текущий `Text("v$versionName")` без clickable; Phase 07 добавляет clickable modifier к этой строке.
- `CatalogDisplayItem.pictureUrl` = HTTPS URL (pre-resolved в data layer) — `AsyncImage` не нужен кастомный Fetcher. Canonical: ADR-HLA-07 (`03-decisions.md:167-188`) — decision зафиксировано; rejected alternative = Coil custom Fetcher (known re-fetch issue).
- `CatalogSpinner` и `CatalogGrid` принимают `List<CatalogDisplayItem>` — нет зависимости на Firebase SDK. Canonical: ADR-L3-03 (`03-decisions.md:265-303`) — `CatalogDisplayItem` живёт в `android:core:designsystem`; rejected alternative = Firebase SDK leak в designsystem.
- `DesignCatalogRoot` condition: `isDebugBuild || state.userStats.qualification.developer >= QualificationLevel.LEVEL_1.points`. Canonical: `AppShellScreen.kt:248-266` — существующий condition `screen.config == LocalConfig.DesignCatalogRoot && isDebugBuild` (`2-grounding.md` VERIFIED). Phase 07 расширяет OR clause.
- `DrawerContent` — только pass-through: принимает `userStats` и передаёт в `DrawerFooter`; никакой логики не добавляет. Canonical: существующий `DrawerContent.kt:32-end` — смотреть структуру перед изменением; убедиться что добавляются только параметры, не бизнес-логика.

---

## 1. UPDATE AppShellScreen — SnackbarHostState + LaunchedEffect

- **Файл:** `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/feature/appshell/presentation/ui/AppShellScreen.kt`
- **Тип:** Composable update
- **Сигнатура:** `@Composable fun AppShellScreen(rootComponent: RootComponent, modifier: Modifier = Modifier)`
- **Вход:** существующий `AppShellScreen`; добавить `SnackbarHostState` + `LaunchedEffect` + `snackbarHost` slot в `Scaffold`
- **Поведение / Выход:**
  - Добавить `val snackbarHostState = remember { SnackbarHostState() }` перед Scaffold
  - Добавить `LaunchedEffect(rootComponent) { rootComponent.events.collect { event -> when(event) { ... } } }` — коллектор RootEvent
  - Коллектор обрабатывает:
    - `RootEvent.DevModeActivated` → `snackbarHostState.showSnackbar("Режим разработчика включён", duration = SnackbarDuration.Long)`
    - `RootEvent.DevModeAlreadyActive` → `snackbarHostState.showSnackbar("Уже в режиме разработчика", duration = SnackbarDuration.Short)`
    - `RootEvent.SyncStarted` → `snackbarHostState.showSnackbar("Синхронизация запущена", duration = SnackbarDuration.Short)`
    - `RootEvent.SystemBack` → `(LocalContext.current as? Activity)?.moveTaskToBack(true)` (существующая логика)
  - Добавить `snackbarHost = { SnackbarHost(snackbarHostState) }` в `Scaffold(...)` параметры
- **Edge cases:**
  - `LaunchedEffect(rootComponent)` — ключ `rootComponent` (object reference), не `Unit`. Если ключ `Unit` — collector запустится заново при каждой recomposition → дублирование обработчиков
  - `SystemBack` branch в `when` — существующая логика, теперь перенесена из отдельного `LaunchedEffect(Unit)` (если такой был) в единый коллектор
  - `snackbarHostState.showSnackbar()` — suspend; внутри `LaunchedEffect` scope — OK
  - `LocalContext.current as? Activity` — safe cast; не падает если контекст не Activity (например Preview)
- **Depends on:** Phase 03 (`RootEvent` variants), backend task 4+5 (`DefaultRootComponent.onVersionTap/onSyncNow`)
- **Canonical reference:** `07-events.md L3.4`, `06-api-contract.md §3.1`
- **Rationale:** `LaunchedEffect(rootComponent)` — standard Decompose + Compose pattern для event collection

---

## 2. UPDATE AppShellScreen — DesignCatalogRoot condition

- **Файл:** `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/feature/appshell/presentation/ui/AppShellScreen.kt`
- **Тип:** Composable update (condition change)
- **Сигнатура:** `if (isDebugBuild || state.userStats.qualification.developer >= QualificationLevel.LEVEL_1.points)`
- **Вход:** существующий condition для `DesignCatalogRoot` (строка ~255 по overview.md); текущий вид неизвестен точно — проверить grep
- **Поведение / Выход:**
  - Текущее condition: предположительно только `isDebugBuild`
  - Новое condition: `isDebugBuild || state.userStats.qualification.developer >= QualificationLevel.LEVEL_1.points`
  - `state` — текущий `AppShellState` из `rootComponent.appShellState.collectAsState()`
  - `QualificationLevel.LEVEL_1.points` из `shared:core:foundation` (import нужен в presentation)
- **Edge cases:**
  - Если `state.userStats` = guest (до login) — `developer == 0` → condition false → DesignCatalog скрыт (корректно)
  - Release build + `developer >= 100` → DesignCatalog visible (spec AC #13-14)
  - Debug build → всегда visible (существующее поведение сохраняется)
- **Depends on:** Phase 01 (`QualificationLevel.LEVEL_1.points` в core:foundation), Phase 03 (`UserStats.qualification.developer` в AppShellState)
- **Canonical reference:** `06-api-contract.md §4.3` (visibleFooterActions condition)
- **Rationale:** spec AC #13-14 — developer tier разблокирует DesignCatalog в release builds

---

## 3. UPDATE DrawerContent — userStats pass-through

- **Файл:** `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/feature/appshell/presentation/ui/drawer/DrawerContent.kt`
- **Тип:** Composable update (param добавление + pass-through)
- **Сигнатура:** `@Composable fun DrawerContent(navigator: Navigator, isDebugBuild: Boolean, versionName: String, userStats: UserStats, onVersionTap: () -> Unit, onSyncNow: () -> Unit, modifier: Modifier = Modifier)`
- **Вход:** добавить параметры `userStats: UserStats`, `onVersionTap: () -> Unit`, `onSyncNow: () -> Unit`; передать их в `DrawerFooter(...)`
- **Поведение / Выход:**
  - `DrawerContent` не содержит логики — только рендерит drawer sections и футер
  - Передаёт `userStats`, `onVersionTap`, `onSyncNow` в `DrawerFooter` без изменений
  - Caller (`AppShellScreen`) передаёт `userStats = state.userStats` и callbacks из `rootComponent`
- **Edge cases:**
  - При изменении `userStats` (например после sync) → recomposition DrawerContent → DrawerFooter обновится
  - `onVersionTap` = `{ rootComponent.onVersionTap(System.currentTimeMillis()) }` — создаётся в AppShellScreen (не в DrawerContent)
- **Depends on:** Phase 03 (`UserStats` domain type), задача 1 выше (`AppShellScreen` обновлён)
- **Canonical reference:** `06-api-contract.md §7` (DrawerFooter signature)
- **Rationale:** pass-through pattern — DrawerContent не знает о business logic; всё поднимается в AppShellScreen

---

## 4. UPDATE DrawerFooter — новые params + clickable version + SyncNow branch

- **Файл:** `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/feature/appshell/presentation/ui/drawer/DrawerFooter.kt`
- **Тип:** Composable update
- **Сигнатура:** `@Composable fun DrawerFooter(navigator: Navigator, isDebugBuild: Boolean, versionName: String, userStats: UserStats, onVersionTap: () -> Unit, onSyncNow: () -> Unit, modifier: Modifier = Modifier)`
- **Вход:** добавить три новых параметра: `userStats: UserStats`, `onVersionTap: () -> Unit`, `onSyncNow: () -> Unit`
- **Поведение / Выход:**
  - Version text: добавить `Modifier.clickable(onClick = onVersionTap).defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)`
  - Footer actions list: вызвать `visibleFooterActions(isDebugBuild, userStats)` для получения видимых actions
  - `when(action)` — добавить `DrawerFooterAction.SyncNow -> { onSyncNow() }` branch
  - Существующие branches (`DesignCatalog`, `About`) остаются без изменений
  - `DrawerFooter` НЕ создаёт coroutines — только вызывает callbacks
- **Edge cases:**
  - Если `visibleFooterActions` возвращает пустой список (только guest без debug) — список рендерится пустым (только `About` остаётся по spec)
  - `onVersionTap` вызывается при каждом tap — `System.currentTimeMillis()` передаётся из caller (`AppShellScreen`/`DrawerContent`), не из `DrawerFooter`
  - Exhaustive `when`: при добавлении нового `DrawerFooterAction` — Kotlin потребует добавить branch (sealed interface exhaustive)
- **Depends on:** Phase 03 (`DrawerFooterAction.SyncNow`, `visibleFooterActions` new signature)
- **Canonical reference:** `06-api-contract.md §7`, `07-events.md L3.3`
- **Rationale:** callback-only pattern — DrawerFooter = pure presentational Composable; бизнес-логика в component

---

## 5. UPDATE Labels.kt — SyncNow displayName + icon

- **Файл:** `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/feature/appshell/presentation/ui/Labels.kt`
- **Тип:** extension update
- **Сигнатура:** `val DrawerFooterAction.SyncNow.displayName: String get() = "Синхронизация"` + `val DrawerFooterAction.SyncNow.icon: ImageVector get() = Icons.Default.Refresh`
- **Вход:** существующий `Labels.kt`; добавить entries для нового `DrawerFooterAction.SyncNow`
- **Поведение / Выход:**
  - `displayName` для SyncNow = `"Синхронизация"` (RU)
  - `icon` для SyncNow = `Icons.Default.Refresh` (или аналог из Material3)
  - Если `Labels.kt` использует `when(action)` вместо отдельных extensions — добавить `DrawerFooterAction.SyncNow ->` branch
- **Edge cases:**
  - `Icons.Default.Refresh` — проверить доступность в `material-icons-extended` или аналоге. Если нет — использовать `Icons.AutoMirrored.Filled.ArrowBack` или другой близкий icon
  - Sealed interface `DrawerFooterAction` — при добавлении нового variant компилятор выдаст warning/error на `when` без exhaustive branch
- **Depends on:** Phase 03 (`DrawerFooterAction.SyncNow` добавлен в domain)
- **Canonical reference:** `06-api-contract.md §3.3`
- **Rationale:** Labels.kt = centralized string/icon mapping; не хардкодить строки в DrawerFooter

---

## 6. CREATE CatalogDisplayItem + Catalog.toDisplayItem()

- **Файл:** `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/designsystem/model/CatalogDisplayItem.kt`
- **Тип:** data class + extension fun
- **Сигнатура:** `data class CatalogDisplayItem(val id: CatalogId, val name: String, val pictureUrl: String?)`
- **Вход:** `CatalogId` из `shared:core:catalog:domain`; `name: String`; `pictureUrl: String?` — HTTPS URL (null если нет картинки)
- **Поведение / Выход:**
  - Presentation model — не domain type; живёт в `android:core:designsystem` (ADR-L3-03)
  - Extension `fun Catalog.toDisplayItem(): CatalogDisplayItem` — базовый маппер; `pictureUrl = null` (domain `Catalog` не содержит URL)
  - Реальный маппер с URL: `CatalogRepositoryImpl.observeAllForDisplay()` возвращает `List<CatalogDisplayItem>` прямо из Room entities (с `entity.pictureUrl` pre-resolved) — см. `06-api-contract.md §13.0`
- **Edge cases:**
  - `pictureUrl = null` в `toDisplayItem()` extension — корректно для случаев где URL не нужен (spinner item без картинки)
  - `CatalogId` — value class из catalog:domain (`value class CatalogId(val value: String)`)
  - Не добавлять бизнес-логику в data class — только поля
- **Depends on:** backend task 7 (designsystem build.gradle.kts — catalog:domain dep)
- **Canonical reference:** `06-api-contract.md §13.0`
- **Rationale:** ADR-L3-03 — presentation model отделён от domain; designsystem = canonical home для shared UI types

---

## 7. CREATE CatalogSpinner Composable

- **Файл:** `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/designsystem/components/CatalogSpinner.kt`
- **Тип:** Composable function
- **Сигнатура:** `@OptIn(ExperimentalMaterial3Api::class) @Composable fun CatalogSpinner(items: List<CatalogDisplayItem>, selectedId: CatalogId?, onSelectionChanged: (CatalogId?) -> Unit, modifier: Modifier = Modifier)`
- **Вход:**
  - `items: List<CatalogDisplayItem>` — список категорий для отображения
  - `selectedId: CatalogId?` — null = "Все категории" (pseudo-item)
  - `onSelectionChanged: (CatalogId?) -> Unit` — callback при выборе; `null` = "Все категории"
- **Поведение / Выход:**
  - Использует `ExposedDropdownMenuBox` + `menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)`
  - Prepend pseudo-item "Все категории" (id = null) — первый в списке
  - Текущий выбор отображается в `OutlinedTextField` (read-only)
  - При выборе → `onSelectionChanged(item.id)` или `onSelectionChanged(null)` для "Все категории"
  - Внутренний `expanded` state через `remember { mutableStateOf(false) }`
- **Edge cases:**
  - Пустой `items` → только "Все категории" в dropdown
  - `selectedId` не найден в `items` → отображать "Все категории" (defensive fallback)
  - `@OptIn(ExperimentalMaterial3Api::class)` — `ExposedDropdownMenuBox` + `menuAnchor` требуют оптин в Material3
- **Depends on:** задача 6 выше (`CatalogDisplayItem`), backend task 7 (designsystem build.gradle.kts)
- **Canonical reference:** `06-api-contract.md §13.1`
- **Rationale:** catalog spinner — reusable UI component для фильтрации по категории; живёт в designsystem для переиспользования в будущих screens

---

## 8. CREATE CatalogGrid + CatalogGridItem Composables

- **Файл:** `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/designsystem/components/CatalogGrid.kt`
- **Тип:** Composable functions (два в одном файле)
- **Сигнатура:** `@Composable fun CatalogGrid(items: List<CatalogDisplayItem>, onCatalogClick: (CatalogId) -> Unit, modifier: Modifier = Modifier)` + `@Composable fun CatalogGridItem(item: CatalogDisplayItem, onClick: () -> Unit, modifier: Modifier = Modifier)`
- **Вход:**
  - `CatalogGrid`: `items` — список для отображения; `onCatalogClick` — callback при клике на ячейку
  - `CatalogGridItem`: `item: CatalogDisplayItem`; `onClick: () -> Unit`
- **Поведение / Выход:**
  - `CatalogGrid`: `LazyVerticalGrid(columns = GridCells.Fixed(2))` → рендерит `CatalogGridItem` для каждого элемента
  - `CatalogGridItem`: вертикальный Card с:
    - `AsyncImage(model = item.pictureUrl, contentDescription = item.name)` из Coil 3 (`io.coil-kt.coil3:coil-compose`)
    - `Text(item.name)` под картинкой
    - `Modifier.clickable(onClick = onClick)` на Card
  - `item.pictureUrl == null` → `AsyncImage` показывает placeholder (Coil 3 умеет `placeholder = painterResource(...)` и `error = painterResource(...)`)
- **Edge cases:**
  - `item.pictureUrl = null` — `AsyncImage(model = null)` Coil отображает fallback/error drawable если задан; иначе пустое пространство
  - `items` пустой → `LazyVerticalGrid` показывает пустой экран (caller ответственен за empty state UI)
  - Coil 3 API: `AsyncImage` из `io.coil-kt.coil3:coil-compose`, импорт `coil3.compose.AsyncImage` (другой пакет от Coil 2)
  - contentScale: `ContentScale.Crop` — для квадратных превью в grid
- **Depends on:** задача 6 выше (`CatalogDisplayItem`), backend task 7 (Coil 3 dep в build.gradle.kts)
- **Canonical reference:** `06-api-contract.md §13.2`
- **Rationale:** `LazyVerticalGrid(Fixed(2))` — spec AC #15 требует grid layout; Coil 3 — актуальная версия для KMP-совместимых проектов
