---
date: 2026-04-25
researcher: Claude
commit: 5140ae3b
branch: kmp-skillify-4.0
---

# Research: Quizzes Screen — Hierarchical Drill-Down Navigation

## Summary

Фича — порт легасного `QuizFragment` на новый стек (Decompose + Compose). Spec фиксирует navigation на 4 уровня (Quest → Section → Theme → Lesson + LessonPlaceholder), entry с `HomeQuestsScreen`/`MyQuestsScreen`, breadcrumb с pop-семантикой, long-press DropdownMenu c Share. Research подтвердил большинство существующих интеграционных точек (TODO в `HomeQuestsComponent.onCatalogClick`, `MyQuestsScreen.kt:87`), repository observers с `ORDER BY \`order\` ASC` для Section/Theme/Lesson, и cross-feature чистоту (Invariant 3 не нарушен).

При этом обнаружены **5 критических delta** между spec и реальной кодовой базой, требующих user decision до перехода в design (полный список — секция «State Matrix Validation»). Главные: (1) `Quest`/`QuestEntity` НЕ имеют поля `order` — spec AC#1 «sorted by order ASC» нереализуем без миграции схемы; (2) все existing childStack используют `serializer = null` — process death restoration (AC#21) не поддерживается; (3) `DefaultRootComponent` не использует прямой `push/pop` — навигация прогоняется через domain `NavStack` FSM в `shared/feature/app-shell/domain/`; (4) `LocalScreenComponent` имеет один `Placeholder` вариант — нет реальных child trees в текущей архитектуре tabs; (5) `QuestDisplayItem` не несёт `catalogId` — для breadcrumb от MyQuests требуется lookup из `state.catalogs` по `state.selectedCatalogId`, поведение при `selectedCatalogId=null` спекой не определено.

Архитектурный паттерн ChildStack (`childStack(...)`) уже используется в `DefaultLocalTabComponent`, но как формальный wrapper над FSM (`syncStack` транслирует `NavStack` → `StackNavigation`). Walking Skeleton корректно skip — Feature Domain Contract = N/A. Web research подтвердил Decompose 3.1.0 поддерживает `popTo(index)` (для breadcrumb pop), но `push` помечен `@DelicateDecomposeApi` — рекомендован `pushNew`.

## Architecture Overview

### Layer mapping
- **Domain** (`shared/feature/{quest,section,theme,lesson}/domain/`) — Quest/Section/Theme/Lesson models, repository interfaces. Pure Kotlin, Invariant 1 preserved.
- **Data** (`shared/feature/{quest,section,theme,lesson}/data/`) — Repository implementations, `*LocalDataSource`. DAOs живут в `shared/core/persistence/`.
- **Presentation** (`android/feature/quest/presentation/`) — `Default*Component`, `*Screen`, `QuestPresentationModule`. Новый module `android/feature/quizzes-screen/presentation/` будет в этом же layer.
- **Composition root** — `apps/android-next/.../AppApplication.kt:97` startKoin module list. App-shell координирует Components: `AppShellPresentationModule.kt:23` создаёт `DefaultRootComponent` с factory lambdas.

### Key architectural facts
- **DefaultRootComponent НЕ имеет Configuration sealed class** (`DefaultRootComponent.kt:69-115`). Навигация прогоняется через domain `NavStack&lt;C&gt;` FSM (4 stacks: `localNavigation/internetNavigation/eventsNavigation/shopNavigation`), синхронизация в `syncStack(...)` на `:272-280`: единственный API — `nav.navigate(transformer = { all }, onComplete = { _, _ -&gt; })`. Методы `push/pop/popTo/replaceCurrent/bringToFront` НЕ ИСПОЛЬЗУЮТСЯ нигде в codebase.
- **LocalConfig sealed interface** в `shared/feature/app-shell/domain/.../model/TabConfig.kt:22-38`: `MyQuestsRoot, HomeQuestsRoot, SettingsRoot, DesignCatalogRoot, EmptyRoot, QuestCreateRoot`. Без `@Serializable`. Расширение требует изменений в shared domain.
- **DefaultLocalTabComponent.kt:19-27** создаёт `ChildStack&lt;LocalConfig, LocalScreenComponent&gt;` с `serializer = null, handleBackButton = false, childFactory = { config, _ -&gt; LocalScreenComponent.Placeholder(config) }`.
- **LocalScreenComponent.kt:5-7** — sealed interface с одним `Placeholder(val config: LocalConfig)` вариантом. Нет реальных Component subtrees.
- **AppShellScreen.kt:298-321** диспатчит `screen.config` в `LocalTabContent`: `is LocalConfig.HomeQuestsRoot → HomeQuestsContent(...)` → `HomeQuestsScreen(rootComponent.homeQuestsComponent)`. Components создаются один раз в `DefaultRootComponent.kt:130-131` через `childContext(...)`, не через ChildStack.
- **Back handling** (`DefaultRootComponent.kt:136-142`): Essenty `BackCallback`, `onDestination(Destination.Back)` → domain FSM. `LocalTabComponent` не handles back (`handleBackButton = false`).

### Navigation entry points (TODO sites)
- `HomeQuestsScreen.kt:54-56` → `CatalogGrid(onCatalogClick = component::onCatalogClick)` → `HomeQuestsComponent.onCatalogClick(id: CatalogId)` → `DefaultHomeQuestsComponent.kt:50-52` (empty TODO body).
- `MyQuestsScreen.kt:84-91` → `QuestCard(onClick = { /* TODO: open quest detail */ })`. `MyQuestsComponent` interface не имеет `onQuestClick` метода. `quest: QuestDisplayItem` доступен в lambda scope; `state.selectedCatalogId: CatalogId?` через `component.state`.

## Existing Patterns

### Decompose Component
- DI factory: `factory&lt;ComponentInterface&gt; { (ctx: ComponentContext, ...) -&gt; DefaultImpl(componentContext = ctx, ...) }` — `QuestPresentationModule.kt:25-41`.
- Composition: `class Default*Component(componentContext: ComponentContext, ...) : ComponentContext by componentContext, ComponentInterface { ... }`.
- Coroutine scope: `private val componentJob = SupervisorJob()` + `Dispatchers.Main.immediate` + `lifecycle.doOnDestroy { componentJob.cancel() }` (`DefaultHomeQuestsComponent.kt:33-48`).
- StateFlow: `.stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = ...)`. Loading state не отделён от первого emit — UI решает по null/empty.

### UI patterns
- **Loading**: `Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }` (`HomeQuestsScreen.kt:36-37`, `MyQuestsScreen.kt:60-62`). Без size constraint.
- **Empty state**: `Box + Column + Text(titleMedium "Нет X") + Text(bodyMedium "explanation", padding(top = 8.dp))` (`HomeQuestsScreen.kt:39-52`, `MyQuestsScreen.kt:63-81`). Inline русские строки, не string resources.
- **LazyColumn padding**: `MyQuestsScreen.kt:90`: `padding(horizontal = 16.dp, vertical = 4.dp)` (фактически 4.dp, не 8.dp как в spec — DELTA).
- **Typography**: `titleMedium` для title/empty heading, `bodyMedium` для explanation, `titleMedium.copy(fontWeight = FontWeight.Bold)` для catalog name (`CatalogGrid.kt:87`).
- **MaterialTheme.colorScheme roles**: `primary` (icons, ratings — `QuestCard.kt:68`, `StarRating.kt:93`), `outline` (borders — `BrandCard.kt:24-25`), `surface` (containers).

### BrandComponentsInvariantsTest enforcement
- `BrandComponentsInvariantsTest.kt:23-36`: запрещает `Color(0x...)` substring во всех `.kt` files в `android/core/designsystem/src/main/.../components/`.
- `BrandComponentsInvariantsTest.kt:54-67`: каждый `.kt` file в `components/` обязан содержать `@Preview`. Новые `HierarchyItemCard.kt`/`BreadcrumbBar.kt` обязаны соблюдать оба правила.

## Integration Points

### Repository observers (все verified)
| Method | Location | Sort | Filter |
|--------|----------|------|--------|
| `QuestRepository.observeMyQuests(authorUid: String, catalogId: CatalogId? = null): Flow&lt;List&lt;Quest&gt;&gt;` | `shared/feature/quest/domain/.../QuestRepository.kt:39` | `lastModifiedAt DESC` (`QuestDao.kt:13-25`) | `authorUid + archived=0 + (catalogId?)` |
| `QuestRepository.observeByShelf(shelf: String): Flow&lt;List&lt;Quest&gt;&gt;` | `QuestRepository.kt:50` | `lastModifiedAt DESC` (`QuestDao.kt:28-34`) | delimiter-wrapped LIKE on `visibleOn` + `archived=0` |
| `QuestRepository.observeByCatalog(catalogId, shelf)` | **ABSENT** — confirmed grep | — | — |
| `SectionRepository.observeByQuest(questId: QuestId): Flow&lt;List&lt;Section&gt;&gt;` | `shared/feature/section/domain/.../SectionRepository.kt:24` | `\`order\` ASC` (`SectionDao.kt:13-18`) | `questId + archived=0` |
| `ThemeRepository.observeBySection(sectionId: SectionId): Flow&lt;List&lt;Theme&gt;&gt;` | `shared/feature/theme/domain/.../ThemeRepository.kt:21` | `\`order\` ASC` (`ThemeDao.kt:13`) | `sectionId + archived=0` |
| `LessonRepository.observeByTheme(themeId: ThemeId): Flow&lt;List&lt;Lesson&gt;&gt;` | `shared/feature/lesson/domain/.../LessonRepository.kt:21` | `\`order\` ASC` (`LessonDao.kt:13`) | `themeId + archived=0` |
| `AuthRepository.currentUid(): suspend String?` / `observeUid(): Flow&lt;String?&gt;` | `shared/feature/app-shell/domain/.../AuthRepository.kt:31,43` | — | — |

### `visibleOn` storage и фильтр
- Domain field: `Quest.visibleOn: Set&lt;String&gt;` (`Quest.kt:55`). Valid shelves: `setOf("home", "arena", "tournament", "tournamentFinal", "archive")` (`Quest.kt:94-98`).
- Persistence: `QuestEntity.visibleOn: Set&lt;String&gt;` через `StringSetConverter` — `joinToString(separator = "\u001F")` (CHAR(31), `StringSetConverter.kt:7`).
- DAO LIKE pattern (`QuestDao.kt:30`): `(CHAR(31) || visibleOn || CHAR(31)) LIKE ('%' || CHAR(31) || :shelf || CHAR(31) || '%')` — exact-element match.

### Count APIs — отсутствуют
Поиск `COUNT`/`count` в `SectionDao`, `ThemeDao`, `LessonDao`, `QuestDao` — 0 results. `subtitleCount = null` в MVP — единственный возможный вариант с current schema.

### Cross-feature import status (Invariant 3)
- `android/feature/quest/presentation/` имеет ZERO импортов из других `android/feature/*` (verified grep).
- `android/feature/app-shell/presentation/` импортирует `android/feature/quest/presentation/` (one-directional, ADR-CMP-51 в `home-and-my-quests/03-decisions.md`).
- Данные cascade `section/data → quest/domain` etc. — задокументированы в ADR-HMQ-06.
- Reflection в feature код — 0 results (no `Class.forName`/`KClass&lt;`/`kotlin.reflect`).

## Detailed Findings

### 1. DefaultRootComponent — domain-FSM-driven navigation
- **Location**: `android/feature/app-shell/presentation/.../component/DefaultRootComponent.kt:69`
- **`StackNavigation`**: 4 instances `DefaultRootComponent.kt:112-115` (`localNavigation`, `internetNavigation`, `eventsNavigation`, `shopNavigation`), все типа `StackNavigation&lt;TabConfig&gt;`.
- **API в codebase**: только `nav.navigate(transformer, onComplete)` (`DefaultRootComponent.kt:279`). `push/pop/popTo/replaceCurrent/bringToFront` НЕ ИСПОЛЬЗУЮТСЯ нигде. Тест `DefaultRootComponentTest.kt:488` явно отмечает: «`push` не в MVP».
- **`syncStack`** (`:272-280`) вызывается из `state.collectLatest { ... }` — domain FSM эмитит новый `NavStack&lt;C&gt;`, presentation rebuild через `transformer = { all }`.
- **Back handling** (`:136-142`): Essenty `BackCallback`, делегирует в `onDestination(Destination.Back)` → domain FSM (`navigateUseCase.invoke(Destination.Back)`).
- **`homeQuestsComponent`/`myQuestsComponent`** — flat children, не в ChildStack (`:130-131`): `myQuestsFactory(childContext("MyQuestsContent"), navigator)`. Существуют весь lifetime root component'а.

### 2. LocalConfig + LocalScreenComponent
- `LocalConfig` (`shared/feature/app-shell/domain/.../model/TabConfig.kt:22-38`): 6 entries без `@Serializable`. Comment line 15: «serialization is a data-layer concern».
- `LocalScreenComponent.kt:5-7`:
  ```kotlin
  sealed interface LocalScreenComponent {
      data class Placeholder(val config: LocalConfig) : LocalScreenComponent
  }
  ```
- `DefaultLocalTabComponent.kt:19-27`:
  ```kotlin
  childStack(
      source = navigation, serializer = null,
      initialConfiguration = LocalConfig.HomeQuestsRoot,
      handleBackButton = false, key = "LocalStack",
      childFactory = { config, _ -&gt; LocalScreenComponent.Placeholder(config) },
  )
  ```
- `AppShellScreen.kt:298-321` exhaustive when по `screen.config: LocalConfig` → renders `HomeQuestsContent`/`MyQuestsContent`/etc. напрямую через `rootComponent.homeQuestsComponent`/`rootComponent.myQuestsComponent`.

### 3. HomeQuestsComponent integration
- Interface (`HomeQuestsComponent.kt:17`): `fun onCatalogClick(id: CatalogId)`.
- Default impl (`DefaultHomeQuestsComponent.kt:50-52`):
  ```kotlin
  override fun onCatalogClick(id: CatalogId) {
      // TODO: future catalog detail navigation
  }
  ```
- UI call (`HomeQuestsScreen.kt:54-57`):
  ```kotlin
  CatalogGrid(
      items = state.catalogs,
      onCatalogClick = component::onCatalogClick,
      modifier = modifier.fillMaxSize(),
  )
  ```
- Component creation: `DefaultRootComponent.kt:131` — `homeQuestsFactory(childContext("HomeQuestsContent"))`.

### 4. MyQuestsScreen integration
- TODO call site (`MyQuestsScreen.kt:84-91`):
  ```kotlin
  items(state.quests) { quest -&gt;  // quest: QuestDisplayItem
      QuestCard(
          item = quest,
          onClick = { /* TODO: open quest detail */ },
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
      )
  }
  ```
- `QuestDisplayItem.kt:14-20` поля: `id, title, pictureUrl, averageRating, averageRatingCount`. **`catalogId` ОТСУТСТВУЕТ.**
- `MyQuestsUiState.selectedCatalogId: CatalogId?` доступен через `component.state`. `state.catalogs: List&lt;CatalogDisplayItem&gt;` тоже доступен.
- `MyQuestsComponent` interface не имеет `onQuestClick` — для wiring требуется добавить новый метод или принять callback.

### 5. Domain models (полные сигнатуры)
**Quest** (`shared/feature/quest/domain/.../model/Quest.kt:30-93`):
```kotlin
data class Quest(
    val id: QuestId, val catalogId: CatalogId, val authorUid: String,
    val title: String, val picturePath: String?, val pictureUrl: String? = null,
    val visibleOn: Set&lt;String&gt;, val averageRating: Float? = null,
    val averageRatingCount: Int = 0, val version: Long, val contentsVersion: Long,
    val lastModifiedAt: Long, val archived: Boolean = false,
)
```
**Quest.order — ОТСУТСТВУЕТ.** `QuestEntity.kt:24-38` тоже без `order`. `Quest.status` (DRAFT/PUBLISHED) отсутствует — только KDoc comment на `averageRating` упоминает "DRAFT".

**Section** (`Section.kt:19`): `id, questId, title, order: Int (>=0), version, contentsVersion, lastModifiedAt, archived`. **Имеет `order`.**

**Theme** (`Theme.kt:15`): `id, sectionId, title, order: Int (>=0), version, contentsVersion, lastModifiedAt, archived`.

**Lesson** (`Lesson.kt:15`): `id, themeId, title, order: Int (>=0), version, contentsVersion, lastModifiedAt, archived`.

Linkage: `Section.questId` ✓, `Theme.sectionId` ✓, `Lesson.themeId` ✓.

### 6. Designsystem components
| Component | Location | Signature | Long-press? |
|-----------|----------|-----------|-------------|
| `QuestCard` | `components/QuestCard.kt:41` | `(QuestDisplayItem, onClick: (QuestId) -&gt; Unit, Modifier)` | **NO** — только `Modifier.clickable` (`:48`) |
| `BrandCard` | `components/BrandCard.kt:16` | `(Modifier, content: @Composable ColumnScope.() -&gt; Unit)` | NO |
| `CatalogGrid` / `CatalogGridItem` | `components/CatalogGrid.kt:41,62` | `(items, onCatalogClick, Modifier)` / `(CatalogDisplayItem, onClick:()->Unit, Modifier)` | NO |
| `CatalogSpinner` | `components/CatalogSpinner.kt:34` | uses `ExposedDropdownMenuBox + DropdownMenuItem` (form variant, не context menu) | — |
| `BrandProgressBar`, `BrandPrimaryButton`, `BrandSecondaryButton`, `BrandCircleIconButton` | `components/` | utility | — |
| `StarRating`, `CategoryIcon` | `components/` | utility | — |
| `HierarchyItemCard` / `BreadcrumbBar` | — | **NOT FOUND** — новые компоненты | — |

`Modifier.combinedClickable` — **0 usages** в `android/`. Первое появление паттерна.

`androidx.compose.material3.DropdownMenu` (standalone context menu) — **0 usages**. `ExposedDropdownMenu` (form variant) используется в `CatalogSpinner.kt:55` — это другой компонент.

`Intent.ACTION_SEND` — **0 usages в новом codebase** (`android/`). Только legacy `legacy/shop/.../ReferralFragment.kt:154`.

`LocalContext.current` — единственное usage в новом коде: `AppShellScreen.kt:102,146` — `(context as? Activity)?.moveTaskToBack(true)`. Pattern для system actions из Compose не задокументирован в проекте.

### 7. DI pattern (Koin)
`QuestPresentationModule.kt:25-41`:
```kotlin
factory&lt;MyQuestsComponent&gt; { (ctx: ComponentContext, nav: Navigator) -&gt;
    DefaultMyQuestsComponent(componentContext = ctx, authRepo = get(), observeMyQuests = get(), observeCatalogs = get(), navigator = nav)
}
factory&lt;HomeQuestsComponent&gt; { (ctx: ComponentContext) -&gt;
    DefaultHomeQuestsComponent(componentContext = ctx, observeCatalogs = get())
}
```
`AppShellPresentationModule.kt:33-38` использует `parametersOf(...)` для проброса `ComponentContext`/`Navigator`. Anti-pattern `getKoin()` присутствует в `AppShellPresentationModule.kt:24` — отмечен как known debt в `PROJECT-CONTEXT.md` (не блокер для quizzes-screen).

### 8. Walking Skeleton — verified absent
`shared/feature/quizzes_screen/`, `app/src/main/.../domain/quizzes_screen/` — НЕ СУЩЕСТВУЮТ. Spec корректно skip Walking Skeleton (Feature Domain Contract = N/A). Invariant 6 preserved.

### 9. Domain purity (Invariant 1)
Все 4 затронутых пакета (`shared/feature/{quest,section,theme,lesson}/domain/`) проверены: 0 imports `android.*`/`androidx.*`/`io.livekit.*`/`com.google.firebase.*`/`retrofit2.*`/`okhttp3.*`/`androidx.room.*`/`com.squareup.moshi.*`/`kotlinx.serialization.*`. 0 DI annotations. Invariant 1 preserved.

### 10. Existing fakes
- `shared/feature/quest/domain/src/commonTest/.../fake/FakeQuestRepository.kt:57` — full implementation (`observeMyQuests/observeByShelf/getById/refreshFromRemote`).
- `android/feature/quest/presentation/src/test/.../fake/FakeQuestRepository.kt` — partial (только `observeMyQuests/observeByShelf`).
- При добавлении `observeByCatalog` — обе копии нужно обновить.

### 11. Legacy QuizFragment reference
- **PathStructure** (`legacy/common/.../PathStructure.kt:7-15`): `data class` с 5 string полями, `toPath()` склеивает через `>` без пробелов. `nameEvent` в path не включается.
- **`QuizFragment.initPath()`** (`:55-72`): join через `" > "` (С ПРОБЕЛАМИ), весь путь в одном `TextView`, без кликабельности сегментов, без выделения цветом — единый `android:textColor="@color/white"`. Совпадает со spec format.
- **`QuizActivityViewModel.listStructureDataLocalFlow`** — `StateFlow&lt;List&lt;StructureDataLocal&gt;&gt;`, заполняется через `_value.value = ...` (не `Flow.collect` — одноразовое чтение).
- **`QuizFragment.onClick()`** (`:124-138`): drill-down через `restartFragment()` (Fragment.replace + addToBackStack) — заполняет `pathStructure` поля по порядку, лист = `openQuestionActivity()`.
- **`popup_menu.xml`** (3 пункта: «Редактировать», «Удалить», «Отправить») — `showPopupMenu()` определён в `QuizActivityAdapter.kt:98`, но **никогда не вызывался** (поиск `setOnLongClickListener` в `legacy/` — 0 results). Long-press menu в legacy был мёртвым кодом.
- **`Intent.ACTION_SEND` в legacy** — 0 results (search `ACTION_SEND|shareText` в legacy Kotlin). Share через system Intent — новый паттерн для MVP.
- **Legacy decorative elements** (`activity_quiz_item.xml`): `delete_button_swipe` (`gone`), `send_button_swipe` (`gone`), `imShare` (`gone`), `imDeleteQuiz` (`gone`), `imv_grad_button` (`gone`), `imv_gradient_translate_quiz` (`gone`), `tvNumQuestion/tvNumHardQuiz/tvName/tvTime` (все `gone`). `imv_gradient_light_quiz` единственный gradient с `VISIBLE` ветвью (`QuizActivityAdapter.kt:184` — `starsMaxLocal in [100,120)`). Spec п.10 «не переносим» — verified.

### 12. Web research summary (Decompose 3.1.0 + Compose Material3 BOM 2024.09.02)

**Decompose 3.1.0 ChildStack API**:
- `pushNew(C, onComplete)` — recommended для button taps; safe (`isSuccess: Boolean` callback).
- `push(C, onComplete)` — `@DelicateDecomposeApi`, throws при дубликате. **Spec пишет `push` — design phase должен использовать `pushNew`** (требует `@OptIn(DelicateDecomposeApi::class)` для `push`).
- `popTo(index: Int, onComplete)` — pop до 0-based index. **Поддерживается, для breadcrumb ok.**
- `popWhile(predicate)` — alternative.
- `popToFirst()` — alias `popTo(0)`.
- `replaceCurrent(C)` — replace top.

**Configuration serialization**: `@Serializable sealed class Config { @Serializable data object X; @Serializable data class Y(...) }`. Передать `Config.serializer()` в `childStack(...)`. `kotlinx-serialization` плагин обязателен. Сохраняется весь stack `List&lt;C&gt;` через `ListSerializer(serializer)`.

**StateKeeper / instanceKeeper** (3.1.0):
- Pattern manual: `stateKeeper.consume(key, strategy)` + `stateKeeper.register(key, strategy, supplier)`.
- `instanceKeeper.getOrCreate { } : InstanceKeeper.Instance` для retained instance с `onDestroy`.
- `saveable` delegate доступен только с 3.2.0 — **в проекте 3.1.0 надо manual**.

**Material3 DropdownMenu** (BOM 2024.09.02 → material3 1.3.0):
- `DropdownMenu(expanded, onDismissRequest, modifier, offset, scrollState, properties, shape, containerColor, ..., content)` — позиционируется относительно parent layout (Popup). Pattern: общий `Box` для anchor + menu, общий state `var expanded by remember { mutableStateOf(false) }`.
- `DropdownMenuItem(onClick, text, ...)` — основной API.

**`Modifier.combinedClickable`** (Compose Foundation):
- Signature: `combinedClickable(enabled, onClickLabel, role, onLongClickLabel, onLongClick, onDoubleClick, hapticFeedbackEnabled = true, interactionSource, onClick)`.
- Default haptic feedback **включён**. Accessibility: `onLongClickLabel` для TalkBack.

**`Intent.ACTION_SEND` from Compose**:
- Recommended pattern: `LocalContext.current` → `context.startActivity(Intent.createChooser(sendIntent, null))`. `createChooser` рекомендован Android docs (consistent UX).
- `try { ... } catch (e: ActivityNotFoundException) { /* log */ }` — spec п.15a совпадает с docs guidance.

## Cross-Feature Interactions

### Dependency Graph
| From | → | To | Mechanism | file:line | Documented in ADR? |
|------|---|----|-----------|-----------|---------------------|
| `android/feature/app-shell/presentation` | → | `android/feature/quest/presentation` | direct import | `DefaultRootComponent.kt:18-19`, `AppShellScreen.kt:50-53`, `AppShellPresentationModule.kt:6-7` | ADR-CMP-51 (`home-and-my-quests/03-decisions.md`) — one-directional |
| `android/feature/app-shell/presentation` (test) | → | `android/feature/quest/presentation` | test stubs | `StubQuestComponents.kt:3-6` | ADR-CMP-51 |
| `shared/feature/section/data` | → | `shared/feature/quest/domain` | import `QuestId` | data cascade | ADR-HMQ-06 (`home-and-my-quests/03-decisions.md:540-544`) |
| `shared/feature/theme/data` | → | `shared/feature/section/domain` | import `SectionId` | data cascade | ADR-HMQ-06 |
| `shared/feature/lesson/data` | → | `shared/feature/theme/domain` | import `ThemeId` | data cascade | ADR-HMQ-06 |
| `android/feature/app-shell/presentation` | → | `shared/core/foundation` | import `QualificationLevel` | `DefaultRootComponent.kt:21` | menu-refactor ADR |

### Bidirectional Coupling
**NONE FOUND.** `android/feature/quest/presentation` не импортирует `android/feature/app-shell/presentation` — verified zero grep results. Все cross-feature связи one-directional через `app-shell` как coordinator. Invariant 3 preserved.

### Shared SDK Across Features
| SDK | Used by | Recommended pattern | Current integration |
|-----|---------|---------------------|---------------------|
| Decompose ChildStack | только `app-shell/presentation` | one-instance per nav graph | один `localNavigation/internetNavigation/...` per tab — single root |
| Compose Material3 | все feature/presentation + designsystem | shared `MaterialTheme` | OK — централизованно через `BrandTheme` |
| Koin | composition root в `apps/android-next/.../AppApplication.kt` | per-module DSL | OK — каждая feature имеет свой `*PresentationModule` |
| Firebase, Room | `shared/feature/*/data/`, `shared/core/persistence/` | shared infrastructure | OK |

### Reflection / Unconventional Patterns
**0 results** для `Class.forName`, `KClass&lt;`, `kotlin.reflect` в `android/feature/`, `shared/feature/`. Reflection не используется для cross-feature dispatch.

### quizzes-screen impact
- **Planned imports**: `android/core/designsystem`, `shared/feature/quest/domain`, `shared/feature/section/domain`, `shared/feature/theme/domain`, `shared/feature/lesson/domain`, `shared/core/catalog/domain` (для `CatalogId`), `shared/feature/app-shell/domain` (для `LocalConfig` если расширяется).
- **Forbidden imports**: `android/feature/quest/presentation`, `android/feature/app-shell/presentation`, любые другие `android/feature/*/presentation`.
- **Coordination mechanism для entry points**: factory lambda injection из `AppShellPresentationModule` в `DefaultRootComponent` (как делается для `homeQuestsFactory`/`myQuestsFactory`). Альтернатива — внутренний ChildStack внутри `DefaultHomeQuestsComponent`/`DefaultMyQuestsComponent`. Это design-фазы decision — см. State Matrix Validation Open Question 3.
- **Risk**: **None at presentation boundary** при условии что quizzes-screen НЕ импортирует `quest/presentation` напрямую. Wiring через factory injection из `app-shell/presentation` сохраняет one-directional coupling.

### Undocumented Patterns
**None.** Все cross-feature связи задокументированы в ADRs или их отсутствие явно verified.

## State Matrix Validation

### Пропущенные условия (предложить пользователю обновить spec)
- **MyQuests breadcrumb when `selectedCatalogId == null`** — spec п.305 «catalog берётся из `selectedCatalog.name` MyQuests state», но `MyQuestsComponent.kt` позволяет `selectedCatalogId: CatalogId? = null` (см. `MyQuestsScreen.kt:55-57` `CatalogSpinner` → null = «все каталоги»). Spec не определяет breadcrumb в этом случае.

### Несостыковки (spec vs код)
1. **AC#1 / AC#3 / spec п.475 — sort by `order ASC` для quests**: `Quest` domain model + `QuestEntity` НЕ имеют поля `order`. `QuestDao.observeMyQuests/observeByShelf` сортируют `lastModifiedAt DESC`. Implementing `order ASC` требует Room migration + Firestore schema update.
2. **AC#21 (process death restoration) vs `serializer = null`**: все existing `childStack(...)` в проекте (`LocalTabComponent.kt:22`, `ShopTabComponent.kt:22`, `EventsTabComponent.kt:22`, `InternetTabComponent.kt:22`) используют `serializer = null`. Spec требует drill-down stack пережил process death через StateKeeper — нужны `@Serializable` configurations + serializer != null. Pattern в проекте отсутствует.
3. **User Decision #1 «push/pop через `StackNavigation.push/pop`» vs реальная архитектура**: `DefaultRootComponent` использует `nav.navigate(transformer = { all }, onComplete)` — навигация driven из domain `NavStack` FSM в `shared/feature/app-shell/domain/`. `push/pop/popTo` **ни разу не вызываются** в codebase. Spec предполагает прямой Decompose API; это противоречит current pattern.
4. **Padding spec п.56 «`8.dp` вертикально» vs MyQuestsScreen.kt:90 `vertical = 4.dp`**: spec ссылается на MyQuestsScreen, но фактическое значение в коде — 4.dp. Расхождение незначительное.
5. **Decompose `push` is `@DelicateDecomposeApi` (3.1.0)**: spec User Decisions #1 говорит про `push`, но idiomatic для button-tap navigation — `pushNew` (без `@OptIn`).

### Непокрытые комбинации
- Spec State Matrix 1 «Quest (на MyQuests)» предполагает `breadcrumb=[catalog,quest]`, но не определяет catalog когда `selectedCatalogId == null`. См. Open Question 4.

### Domain Contract Mismatches
- N/A — spec явно `Feature Domain Contract = N/A`. Walking Skeleton skip preserved.

## Conditional Documents Needed

| Document | Required? | Reason |
|----------|-----------|--------|
| `01-context.md`, `02-design.md`, `03-decisions.md`, `04-acceptance.md` | YES (default) | стандартный set |
| `05-fsm.md` (state machine spec) | **YES** if Open Question 3 resolved → «extend domain `NavStack` FSM» | новая FSM в `app-shell/domain` |
| `06-api-contract.md` | **YES** if Open Question 1 resolved → добавить `order` в Quest | data layer change + migration |
| `07-events.md` | NO | фича не публикует/слушает events |
| `08-storage-model.md` | **YES** if Open Question 1 resolved → добавить `order` в `QuestEntity` | Room schema change + migration |
| `09-modules.md` | YES | новый Gradle module `android/feature/quizzes-screen/presentation/` |
| `10-tests.md` | YES | comprehensive test plan для 5 Components + UI |

Conditional на Open Questions 1 (Quest.order) и 3 (FSM extension).

## Constraints

### Compose / Decompose / Material3
- Decompose 3.1.0: `push` is `@DelicateDecomposeApi`. `pushNew` recommended.
- Decompose 3.1.0: `saveable` delegate ОТСУТСТВУЕТ — manual `consume`/`register` для StateKeeper.
- Material3 BOM 2024.09.02: standalone `DropdownMenu` — стабильный API (1.3.0). `DropdownMenuItem.shape: Shape` — параметр без default в `expect fun`, но `actual` для android предоставляет `MenuDefaults.itemShape`.
- Compose Foundation: `combinedClickable` — стабильный API. Default haptic feedback `true`.
- BrandComponentsInvariantsTest требует `@Preview` + zero `Color(0x...)` для всех `.kt` в `components/`.

### Schema / Persistence
- `Quest` НЕ имеет `order` field. Если spec sort требование сохраняется — нужна Room migration + Firestore schema update.
- `QuestEntity.visibleOn: Set&lt;String&gt;` через `StringSetConverter` (CHAR(31) delimited). DAO LIKE pattern для exact-element match.
- `serializer = null` — pattern всех existing childStacks. Process death restoration не поддерживается без явного `@Serializable` setup.

### DI / Architecture
- Koin manual DI, composition root — `apps/android-next/.../AppApplication.kt:97`.
- `LocalConfig` в shared domain (`shared/feature/app-shell/domain/`). Расширение sealed interface = изменение в shared domain + exhaustive `when` branches в `AppShellScreen.kt:298-321` и `LocalTabComponent.kt`.
- `DefaultRootComponent.kt:130-131` создаёт `homeQuestsComponent`/`myQuestsComponent` как direct children через `childContext(...)`, не через ChildStack. Pattern для quizzes-screen может быть аналогичный или ChildStack-based.

### Navigation
- `BackCallback` в `DefaultRootComponent.kt:136-142` — Essenty, делегирует FSM. `LocalTabComponent.kt:24` — `handleBackButton = false`. Если новый ChildStack для quizzes-screen имеет `handleBackButton = true`, его back будет конкурировать с существующим — нужна координация.

## Open Questions

Эти вопросы блокируют переход в design phase. Требуют user decision (см. AskUserQuestion ниже после `1-research.md` записи).

1. **Quest sort order** — `Quest`/`QuestEntity` без поля `order`, текущая sort `lastModifiedAt DESC`. Spec AC#1 требует `order ASC`. Принять `lastModifiedAt DESC` ИЛИ добавить `order: Int` через миграцию?

2. **Process death + serializer** — все existing childStacks `serializer = null`. Spec AC#21 требует restoration drill-down breadcrumb через StateKeeper. Включить `@Serializable` configurations для нового стека (отдельно от existing tabs) ИЛИ принять отсутствие process death restoration?

3. **Drill-down architecture** — `DefaultRootComponent` driven through domain `NavStack` FSM, `push/pop` не используются. Архитектурный pattern для нового стека:
   - (A) Внутренний ChildStack в новом `QuizzesComponent`, который вызывается из TODO callbacks `HomeQuestsComponent`/`MyQuestsComponent` — изолирует quizzes-screen от FSM.
   - (B) Extend domain `NavStack` FSM с `QuizzesPath` destinations — следует existing pattern app-shell-навигации, но требует изменений в `shared/feature/app-shell/domain/`.
   - (C) Extend `LocalConfig` sealed interface + `LocalScreenComponent` для real component trees — нарушает текущую single-Placeholder design.

4. **MyQuests breadcrumb when `selectedCatalogId == null`** — `state.selectedCatalogId` может быть null (фильтр «все каталоги»). Что показывает breadcrumb? `Quest.catalogId` доступен в domain model, но `QuestDisplayItem` его не несёт.

5. **`QuestCard.onLongClick` API extension** — `QuestCard` сейчас не имеет `onLongClick`. Как добавить long-press menu: расширить existing `QuestCard` параметром `onLongClick: ((QuestId) -&gt; Unit)? = null`, ИЛИ создать новый wrapper composable в quizzes-screen module? (Это design-phase decision, но влияет на BrandComponentsInvariantsTest и существующих consumers.)

## Risks

1. **Quest sort schema change** (если выбрано Q1=B) — Room migration пользователей, Firestore backfill для существующих документов.
2. **Serializer setup для нового стека** (если выбрано Q2=A) — инжектируется единичный pattern в проект, требует gradle `kotlinx-serialization` plugin для presentation module, и configurations нужно тщательно держать `@Serializable`.
3. **NavStack FSM extension** (если выбрано Q3=B) — изменения в `shared/feature/app-shell/domain/` затрагивают tests `DefaultRootComponentTest`/`AppShellTransitions`/`NavStack` logic. Большой blast radius.
4. **Two `FakeQuestRepository` copies** — при добавлении `observeByCatalog` нужно обновить обе (`shared/feature/quest/domain/src/commonTest/.../fake/FakeQuestRepository.kt` и `android/feature/quest/presentation/src/test/.../fake/FakeQuestRepository.kt`).
5. **Breaking BrandComponentsInvariantsTest** при добавлении новых компонентов без `@Preview` или с `Color(0x...)`.

## Sources

- 6 codebase-researcher agents (Decompose+integration, Repository+models, Designsystem UI, Legacy reference, Cross-feature scanner) — full transcripts archived.
- web-researcher: Decompose 3.1.0 source/docs, Compose Material3 BOM 2024.09.02, Compose Foundation, Android `Intent.ACTION_SEND` guide.
- Verified file refs: `0-spec.md`, `home-and-my-quests/0-spec.md`, `home-and-my-quests/03-decisions.md`, `menu-refactor/03-decisions.md`, `PROJECT-CONTEXT.md`, `docs/invariants.md`.
