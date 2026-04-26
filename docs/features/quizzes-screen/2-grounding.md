---
date: 2026-04-25
researcher: Claude
commit: 5140ae3b
branch: kmp-skillify-4.0
---

# Grounding: Quizzes Screen

Этот документ — **gate** между research и design. Без него переход в design/plan ЗАПРЕЩЁН. Research отвечает «что есть в коде». Grounding отвечает «что сломается, если мы это изменим, и что реально возможно».

Все ключевые claims из `1-research.md` верифицированы по реальному коду через Independent Verification Protocol — см. секцию «Independent Verification Protocol Results» внизу. **Zero contradictions** обнаружено.

User decisions из `0-spec.md` (после research delta-вопросов):
- Q1 Quest sort: **lastModifiedAt DESC** (Recommended) — без schema change.
- Q2 Process death: **`@Serializable` + serializer != null для quizzes ChildStack** (Recommended) — first usage в проекте.
- Q3 Drill-down architecture: **внутренний ChildStack в `QuizzesComponent`** (Recommended) — изолирует от domain `NavStack` FSM.
- Q4 MyQuests breadcrumb null catalog: **resolve из `Quest.catalogId`** (Recommended) — расширить `QuestDisplayItem` полем `catalogId`.

---

## Problem 1: Внутренний ChildStack в `QuizzesComponent` для drill-down

### Symptom
Spec требует drill-down навигацию на 4 уровня (Quest → Section → Theme → Lesson + LessonPlaceholder) с breadcrumb-pop и process death restoration. Существующий проект использует domain `NavStack` FSM в `shared/feature/app-shell/domain/` для tab navigation, но не для drill-down. Решение: новый `QuizzesComponent` владеет своим `StackNavigation<QuizzesConfig>` и `ChildStack`.

### Entry Points (EXHAUSTIVE)
- `HomeQuestsScreen.kt:54-57` — `CatalogGrid(onCatalogClick = component::onCatalogClick)` → `HomeQuestsComponent.onCatalogClick(id: CatalogId)` → текущий TODO в `DefaultHomeQuestsComponent.kt:50-52`.
- `MyQuestsScreen.kt:84-91` — `QuestCard(onClick = { /* TODO: open quest detail */ })` (`MyQuestsScreen.kt:87`).
- LessonPlaceholder — внутренняя точка push после тапа на Lesson; не имеет внешнего entry.
- Configuration changes / process death restoration — Decompose `childStack(serializer = X)` восстанавливает stack автоматически из bundle.
- System back — `Activity.onBackPressed` → `OnBackPressedDispatcher` → если `quizzes ChildStack.handleBackButton = true`, пип попит. Иначе делегирует `DefaultRootComponent.backHandler` (Essenty `BackCallback`, `DefaultRootComponent.kt:136-142`).

### Code Owners
- New module `android/feature/quizzes-screen/presentation/` — content owned by quizzes-screen feature
- `android/feature/quest/presentation/` — TODO sites; модифицирует `frontend-dev` для подключения navigation
- `apps/android-next/.../AppApplication.kt` — Koin module list; `backend-dev` для добавления `quizzesPresentationModule` (см. invariant 7 — scaffold ownership)
- `settings.gradle.kts`, `build.gradle.kts` — `backend-dev` для нового Gradle module entry

### Flow Trace
**Entry от HomeQuests**:
```
CatalogGrid.onCatalogClick(catalogId)  → HomeQuestsScreen.kt:56
  → HomeQuestsComponent.onCatalogClick(id)  → HomeQuestsComponent.kt:17
    → DefaultHomeQuestsComponent.onCatalogClick  → DefaultHomeQuestsComponent.kt:50 (TODO replaced)
      → quizzesNavigator.openQuestList(catalogId, catalogName)
        → DefaultQuizzesComponent.navigation.pushNew(QuizzesConfig.QuestList(catalogId, catalogName))
          → ChildStack обновлён → AppShellScreen render switches to QuizzesScreen
```

**Entry от MyQuests**:
```
LazyColumn.items.QuestCard.onClick(questId)  → MyQuestsScreen.kt:87
  → MyQuestsComponent.onQuestClick(questId, catalogId)  → новый метод в interface
    → DefaultMyQuestsComponent.onQuestClick  → импл резолвит catalogName из state.catalogs
      → quizzesNavigator.openSectionList(questId, breadcrumbTitles)
        → DefaultQuizzesComponent.navigation.pushNew(QuizzesConfig.SectionList(questId, titles))
```

**Breadcrumb pop**:
```
BreadcrumbBar.onSegmentClick(levelIndex)  → composable
  → component.popToLevel(levelIndex)
    → navigation.popTo(levelIndex)  ← Decompose 3.1.0 API, verified
      → ChildStack обрезан → UI обновлён
```

### Backend / Contract Check
- **REST / WebSocket / Push**: N/A — фича читает только локальные данные через existing repository observers; никаких новых backend endpoints не требуется.
- **Сейчас НЕТ**: ничего из backend не отсутствует для navigation infrastructure.

### Constraints
- **Lifecycle**: Decompose `Lifecycle` (Essenty), не Android `Activity`. Component активен между `onCreate` и `onDestroy` — `componentJob.cancel()` в `doOnDestroy` (см. existing pattern `DefaultHomeQuestsComponent.kt:33-48`).
- **In-memory state**: `instanceKeeper.getOrCreate { }` для retained state (rotation survival). Existing pattern `DefaultMyQuestsComponent.kt:56` (`SelectedCatalogHolder`).
- **DB / Storage**: Read-only — `Flow<List<Section>>` / `Flow<List<Theme>>` / `Flow<List<Lesson>>` через existing repositories.
- **Process death restoration**: новый стек требует `@Serializable QuizzesConfig` + `serializer = QuizzesConfig.serializer()`. **Это первый стек в проекте с включённым serializer** (verified `LocalTabComponent.kt:22` `serializer = null`).
- **Offline / Online**: Room offline-first; cascade sync продолжает работать в фоне через existing `home-and-my-quests` infrastructure.

### Code Path Divergence
**Spec предполагает один навигационный путь, реально их 2 разных entry chains**:
- HomeQuests entry: тап → `QuestListComponent(catalogId)` (start at level 1)
- MyQuests entry: тап → `SectionListComponent(questId)` (start at level 2 — пропускает QuestList)

Эти paths не сходятся в общую FSM — каждая entry создаёт свой initial stack. **Risk**: design-фаза должна явно описать как `QuizzesConfig.serializer()` обрабатывает оба initialConfiguration scenario (создаются 2 ChildStack instances? один per Component lifecycle?).

### Fix Shape (минимально реализуемое решение)
**Client-only**: новый Gradle module `android/feature/quizzes-screen/presentation/` с:
- `QuizzesConfig` (`@Serializable sealed class`) — варианты QuestList / SectionList / ThemeList / LessonList / LessonPlaceholder
- `QuizzesComponent` interface + `DefaultQuizzesComponent` impl с внутренним `StackNavigation<QuizzesConfig>` и `childStack(serializer = QuizzesConfig.serializer(), ...)`
- 5 child Components (`QuestListComponent`, `SectionListComponent`, ...) с repository deps
- `QuizzesPresentationModule` (Koin) — factory для всех Components
- Регистрация в `AppApplication.kt` startKoin module list

`HomeQuestsComponent.onCatalogClick` body заменяется на push в `quizzesNavigator`. `MyQuestsComponent` interface получает новый метод `onQuestClick(id, catalogId)`. `QuestDisplayItem` расширяется полем `catalogId: CatalogId`.

**Не требуется backend**: фича pure presentation + read-only data layer extension (`observeByCatalog`).

### Validation
- **Manual scenario**: запустить app → HomeTab → тап каталог → видишь quest list с breadcrumb → тап quest → section list → ...→ lesson placeholder. Тап breadcrumb сегмент → pop до уровня. System back → pop. Force-stop app + reopen (process death imitation) → возвращаешься на тот же level с тем же breadcrumb.
- **Tests**:
  - `DefaultQuizzesComponentTest` — push/pop последовательность, popTo до index, через FakeRepositories.
  - `QuizzesConfigSerializationTest` — round-trip каждого variant `QuizzesConfig` через `Json.encodeToString` / `decodeFromString`.
  - `BreadcrumbBarComposeTest` — тап на сегмент → callback fired with correct index; ellipsis для длинных titles.
- **Success criterion**: navigation работает на 4 уровнях, breadcrumb корректно отображает frozen titles, system back pops correctly, process death restores последний level.

---

## Problem 2: HomeQuestsComponent.onCatalogClick activation

### Symptom
`DefaultHomeQuestsComponent.kt:50-52` — `onCatalogClick` body — empty TODO. Тап на каталог в `HomeQuestsScreen` не приводит к navigation. Spec требует push `QuestListComponent(catalogId)`.

### Entry Points
- `HomeQuestsScreen.kt:54-57` — `CatalogGrid(onCatalogClick = component::onCatalogClick)`. Single entry point.

### Code Owners
- `HomeQuestsComponent.kt:17` (interface) — `frontend-dev` для quest/presentation
- `DefaultHomeQuestsComponent.kt:50-52` (impl) — same owner
- Possibly `QuestPresentationModule.kt:40` (DI factory) — добавит navigator dependency

### Flow Trace
```
CatalogGrid.onCatalogClick(catalogId: CatalogId)  → HomeQuestsScreen.kt:56
  → DefaultHomeQuestsComponent.onCatalogClick(id)  → DefaultHomeQuestsComponent.kt:50 (currently TODO)
    → [TO BE IMPLEMENTED]: quizzesNavigator.openQuestList(catalogId, catalogName)
```

### Backend / Contract Check
N/A — pure UI wiring.

### Constraints
- `HomeQuestsComponent` сейчас injected в `DefaultRootComponent.kt:131` через `homeQuestsFactory(childContext("HomeQuestsContent"))` — фабрика принимает только `ComponentContext`, не `Navigator`. Если `quizzesNavigator` нужен в `DefaultHomeQuestsComponent`, фабрика должна расширяться (как `myQuestsFactory(ctx, navigator)`).
- Альтернатива: `QuizzesComponent` живёт как sibling в `DefaultRootComponent`, и `HomeQuestsComponent` получает callback `onCatalogClick: (CatalogId) -> Unit` извне.
- `catalogName` для breadcrumb — нужен lookup из `state.catalogs` по `catalogId`. Pattern: `state.catalogs.firstOrNull { it.id == catalogId }?.name`.

### Code Path Divergence
**None** — single entry point, single mechanism.

### Fix Shape
**Option A** (factory injection): `homeQuestsFactory: (ComponentContext, QuizzesNavigator) -> HomeQuestsComponent`. `DefaultHomeQuestsComponent` impl `onCatalogClick`: lookup catalog name → `quizzesNavigator.openQuestList(catalogId, catalogName)`.

**Option B** (callback): `HomeQuestsComponent` принимает callback `onCatalogClickInternal: (CatalogId, String) -> Unit` через constructor. `DefaultRootComponent` provides callback connecting to `quizzesComponent.openQuestList(...)`.

Design phase решает между A и B. Existing pattern в `DefaultMyQuestsComponent.kt` использует `Navigator` (existing pattern в проекте) — Option A более consistent.

### Validation
- Manual: тап на каталог → ChildStack обновляется → видишь Quest list.
- Tests: `DefaultHomeQuestsComponentTest` уже существует (`android/feature/quest/presentation/src/test/`); добавить тест с fake `QuizzesNavigator` проверяющий что `onCatalogClick` вызывает `openQuestList(catalogId, catalogName)`.
- Success: TODO заменён, integration test passes.

---

## Problem 3: MyQuestsScreen TODO + MyQuestsComponent extension + QuestDisplayItem.catalogId

### Symptom
`MyQuestsScreen.kt:87` — `onClick = { /* TODO: open quest detail */ }`. `MyQuestsComponent` interface не имеет `onQuestClick` метода. `QuestDisplayItem.kt:14-20` не несёт `catalogId` — для breadcrumb «catalog > quest» нужен lookup.

### Entry Points
- `MyQuestsScreen.kt:84-91` (LazyColumn items loop, single TODO site)

### Code Owners
- `MyQuestsScreen.kt` — `frontend-dev` (quest/presentation)
- `MyQuestsComponent.kt` (interface), `DefaultMyQuestsComponent.kt` (impl) — same owner
- `QuestDisplayItem.kt` — designsystem owner; модификация требует обновления mapping в `Quest.toDisplayItem()` (verify — нужна ли extension function в quest/presentation или в designsystem)
- `QuestCard.kt` — designsystem; не модифицируется (продолжает использовать новые поля)
- `MyQuestsUiState` — quest/presentation

### Flow Trace
```
LazyColumn.items(state.quests).forEach { quest: QuestDisplayItem -> 
  QuestCard.onClick(quest.id)  → MyQuestsScreen.kt:87 (currently TODO)
    → MyQuestsComponent.onQuestClick(id: QuestId, catalogId: CatalogId)  → новый метод
      → DefaultMyQuestsComponent.onQuestClick  → lookup catalogName из state.catalogs
        → quizzesNavigator.openSectionList(questId, listOf(catalogName, quest.title))
}
```

### Backend / Contract Check
N/A.

### Constraints
- `QuestDisplayItem` сейчас mapping происходит из `Quest` domain model в quest/presentation. Поле `catalogId` уже есть в `Quest.kt:32` — расширение `QuestDisplayItem` тривиально.
- BUT: `QuestDisplayItem` в `android/core/designsystem/model/`. Существует ли тест assertion на shape? Verified: BrandComponentsInvariantsTest проверяет только `components/`, не `model/`. Нет blocker.
- `MyQuestsComponent` имеет два consumer-а: `MyQuestsScreen` (UI) и `StubMyQuestsComponent` (test stub). Оба нужно обновить.

### Code Path Divergence
**Spec предполагает breadcrumb всегда `"<catalog.name> > <quest.title>"`, но `state.selectedCatalogId` может быть null** (фильтр «все каталоги» в `CatalogSpinner`). 

[USER DECIDED Q4=A]: catalog name берётся из `quest.catalogId` (resolve в `state.catalogs`), не из `state.selectedCatalogId`. Это работает для обоих cases (selected + null). Если `state.catalogs` ещё не загружены (rare race) — fallback "Без каталога".

### Fix Shape
**Client-only**:
1. `QuestDisplayItem` += `val catalogId: CatalogId`. Mapping в `MyQuestsUiState` или extension function в quest/presentation. — *frontend-dev*
2. `MyQuestsComponent` interface += `fun onQuestClick(id: QuestId, catalogId: CatalogId)`. — *frontend-dev*
3. `DefaultMyQuestsComponent` impl: lookup catalog name из state.catalogs, вызов `quizzesNavigator.openSectionList(...)`. — *frontend-dev*
4. `MyQuestsScreen.kt:87` lambda: `onClick = { questId -> component.onQuestClick(questId, quest.catalogId) }`. Note: lambda сейчас игнорирует `QuestId`; после изменения использует. — *frontend-dev*
5. Stub `StubMyQuestsComponent.kt` — обновить (test consumer). — *test-dev* / *frontend-dev*

### Validation
- Manual: запустить app → MyQuestsTab → тап на quest → SectionList с breadcrumb `"<catalog> > <quest>"`. Также: переключить `CatalogSpinner` на «все каталоги» → тап quest → breadcrumb resolve через `quest.catalogId`.
- Tests:
  - `MyQuestsScreenComposeTest` — тап на QuestCard → callback `onQuestClick(id, catalogId)` fired.
  - `DefaultMyQuestsComponentTest` — `onQuestClick` resolve catalog name correctly through state.catalogs.
- Success: breadcrumb всегда корректный, в т.ч. при null `selectedCatalogId`.

---

## Problem 4: QuestRepository.observeByCatalog data layer addition

### Symptom
Spec п.22 требует `QuestRepository.observeByCatalog(catalogId, shelf)` для отображения public квестов в каталоге (HomeQuests entry). Этот метод **отсутствует** во всех слоях — `QuestRepository.kt`, `QuestLocalDataSource.kt`, `QuestDao.kt`. Verified grep на shared/feature/quest.

### Entry Points
Этот метод будет вызван из:
- `DefaultQuestListComponent` (новый component) при создании — `observeByCatalog(catalogId, shelf="home")`
- `FakeQuestRepository` — тесты

### Code Owners
- `shared/feature/quest/domain/.../QuestRepository.kt` — interface, *backend-dev*
- `shared/feature/quest/data/.../QuestLocalDataSource.kt` — local source method, *backend-dev*
- `shared/feature/quest/data/.../QuestRepositoryImpl.kt` — impl, *backend-dev*
- `shared/core/persistence/.../QuestDao.kt` — Room query, *backend-dev*
- `shared/feature/quest/domain/src/commonTest/.../fake/FakeQuestRepository.kt` — fake impl (mandatory: interface change), *test-dev*
- `android/feature/quest/presentation/src/test/.../fake/FakeQuestRepository.kt` — second fake copy (тоже implements interface), *test-dev*

### Flow Trace
```
DefaultQuestListComponent.init  
  → questRepository.observeByCatalog(catalogId, shelf="home")  → новый метод
    → QuestRepositoryImpl.observeByCatalog  → QuestLocalDataSource.observeByCatalog
      → QuestDao.observeByCatalog (новый Room query)
        → SQLite: SELECT * FROM quests 
                  WHERE catalogId = :catalogId 
                    AND (CHAR(31) || visibleOn || CHAR(31)) LIKE ('%' || CHAR(31) || :shelf || CHAR(31) || '%')
                    AND archived = 0
                  ORDER BY lastModifiedAt DESC
          → List<QuestEntity>  → mapper toDomain  → Flow<List<Quest>>
```

### Backend / Contract Check
- **Room schema**: `QuestEntity` уже имеет поля `catalogId, visibleOn, archived, lastModifiedAt` (verified `QuestEntity.kt:24-37`). Новый query — additive, не требует migration.
- **Firestore schema**: не затрагивается — это read-only Room query на закешированных данных. Cascade sync уже наполняет таблицу через existing `home-and-my-quests` infrastructure.
- **Сейчас НЕТ**: метод `observeByCatalog` в проекте отсутствует на всех слоях. Required addition.

### Constraints
- DAO query должен использовать **delimiter-wrapped LIKE** для `visibleOn` — exact-match как в existing `observeByShelf` (`QuestDao.kt:28-34`). Pattern: `(CHAR(31) || visibleOn || CHAR(31)) LIKE ('%' || CHAR(31) || :shelf || CHAR(31) || '%')`.
- Sort `lastModifiedAt DESC` (per User Decision Q1=A; Quest не имеет `order` поля).
- `archived = 0` filter обязателен (Invariant B downward cascade).
- **Two FakeQuestRepository copies** — обе нужно обновить, иначе compile breaks. Risk известен.
- `kotlinx.coroutines.flow.Flow` — return type. Same pattern что в существующих observers.

### Code Path Divergence
**None** — straightforward DAO addition. Одинаковый paradigm для обоих fakes (in-memory store + filter).

### Fix Shape
**Backend-dev work** (`shared/feature/quest/`):
1. `QuestRepository.kt` += `fun observeByCatalog(catalogId: CatalogId, shelf: String): Flow<List<Quest>>`
2. `QuestLocalDataSource.kt` += same method (interface + impl) + DAO call
3. `QuestRepositoryImpl.kt` += impl delegating to localDataSource + map entity → domain
4. `QuestDao.kt` += new `@Query` method

**Test-dev work**:
5. `FakeQuestRepository` (domain fake) += impl с in-memory filter
6. `FakeQuestRepository` (presentation fake) += impl

**Migration**: НЕ требуется — additive query на existing schema.

### Validation
- Tests:
  - `FakeQuestRepositoryTest` — verify `observeByCatalog` returns correct subset (catalogId match + visibleOn contains shelf + archived=0).
  - `QuestRepositoryImplTest` — verify mapping entity → domain.
  - DAO test (instrumented, `androidTest/`) — verify SQL query на real Room in-memory db.
- Manual: open `HomeQuestsScreen` → тап каталог → видишь только public квесты этого каталога (visibleOn contains "home"), без локальных.
- Success: 0 quest leakage между catalogs, archived фильтрация работает.

---

## Problem 5: QuestCard long-press menu support + Share Intent

### Symptom
Spec п.12 требует long-press на `QuestCard` в новом QuestListComponent → `DropdownMenu` с пунктом «Поделиться». `QuestCard.kt:41-49` не имеет `onLongClick` параметра, использует только `Modifier.clickable`. `DropdownMenu` (standalone) не используется в `android/` (verified). `Modifier.combinedClickable` тоже отсутствует. `Intent.ACTION_SEND` — first usage в новом codebase.

### Entry Points
- Long-press detection — `QuestCard` instance в `QuestListComponent` UI screen
- DropdownMenu rendering — composable inside `QuestListContent` (новый screen)
- Share Intent dispatch — Composable `LocalContext.current` + `context.startActivity(...)`

### Code Owners
- `QuestCard.kt` (designsystem) — *frontend-dev*; **спорно расширять existing API vs создать wrapper в quizzes-screen**
- New `QuestListContent` Composable — quizzes-screen owner
- BrandComponentsInvariantsTest — automatic enforcement

### Flow Trace
```
User long-press на QuestCard в QuestListComponent  
  → Modifier.combinedClickable(onLongClick = { expanded = true }, onClick = { onClick(quest.id) })
    → expanded: Boolean state holder в parent Composable
      → DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) 
        anchored к Box обернувшему QuestCard + Menu
          → DropdownMenuItem(text = { Text("Поделиться") }, onClick = { 
                onShareClick(quest)
                expanded = false 
            })
              → onShareClick(quest)  → Composable lambda
                → val context = LocalContext.current
                  → val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Квест «${quest.title}» — ${appName}")
                    }
                  → try { context.startActivity(Intent.createChooser(intent, null)) }
                    catch (e: ActivityNotFoundException) { logger.warn(...) }
```

### Backend / Contract Check
N/A — system Intent, не network.

### Constraints
- **Material3 `DropdownMenu` версия**: BOM 2024.09.02 → material3 1.3.0. `DropdownMenu` стабилен. `DropdownMenuItem.shape: Shape` — параметр без default в `expect fun`, но `actual` для android предоставляет `MenuDefaults.itemShape` — design phase verify exact API.
- **`combinedClickable` haptic feedback**: default `hapticFeedbackEnabled = true` — long-press вибрирует. Спека не уточняет — ОК accept default.
- **`Intent.createChooser`**: Android docs strongly recommend для consistency. Spec п.66 не mentions chooser, но best practice — use it.
- **`ActivityNotFoundException`**: spec п.15a требует tip + log без UI. `try/catch` вокруг `startActivity`.
- **App name source**: `Intent.EXTRA_TEXT` template `"Квест «{title}» — {appName}"`. App name — обычно из resources `R.string.app_name` или через `context.applicationInfo.loadLabel(context.packageManager)`. Design phase fixates source.
- **Long-press на Section/Theme/Lesson** — *никаких* действий (spec п.14). Если используется тот же Composable wrapper — `onLongClick = null`.

### Code Path Divergence
**`QuestCard` API extension vs new wrapper composable**:
- **Option A**: расширить `QuestCard.kt:41` параметром `onLongClick: ((QuestId) -> Unit)? = null`. Существующий `Modifier.clickable` заменить на `Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)` — backward compatible (null default). **Risk**: затрагивает existing consumer (`MyQuestsScreen.kt:84` — там null = no long-press, OK), и BrandComponentsInvariantsTest проверяет всё в `components/` — необходим обновлённый `@Preview` (если изменения совместимы — no impact).
- **Option B**: создать `QuestCardWithLongPress` wrapper в `android/feature/quizzes-screen/presentation/`. Reuses `QuestCard` без модификации, оборачивает в `Box + combinedClickable + DropdownMenu`. **Risk**: дублирование visual styling, но независимость от designsystem changes.

[DELEGATED to design phase].

### Fix Shape
**Client-only**, depending на Option A vs B above:
- New Composable `QuestListContent` (или `QuestListScreen`) с `combinedClickable`, `DropdownMenu`, `LocalContext.current` для share intent.
- New helper `dispatchShareIntent(context, text)` либо inline в Composable, либо в utility file.
- Option A: модификация `QuestCard.kt` (соблюдение BrandComponentsInvariantsTest).

### Validation
- Manual: long-press на QuestCard → menu появляется → тап «Поделиться» → system chooser → выбираешь приложение → text shared. Long-press на HierarchyItemCard (Section/Theme/Lesson) → ничего. Tap outside menu → menu закрывается.
- Tests:
  - `QuestListScreenComposeTest` (Compose UI test, `androidTest/`) — long-press fires menu open, тап Share → mock context startActivity verified called with correct intent.
  - `QuestCardLongClickTest` (если Option A) — verifies onLongClick invoked.
- Success: share intent dispatched correctly, menu закрывается, no crash на отсутствии apps (`ActivityNotFoundException` swallowed).

---

## Problem 6: Process death + StateKeeper for QuizzesConfig

### Symptom
Spec AC#21 требует ChildStack восстанавливается на тот же level с тем же breadcrumb после process death. Все existing tabs `serializer = null` (verified `LocalTabComponent.kt:22`) — process death restoration в проекте отсутствует. Quizzes-screen — first feature с включённым serializer.

### Entry Points
- `DefaultQuizzesComponent` `init` — `childStack(serializer = QuizzesConfig.serializer(), ...)` setup
- Process death event — Android system kills process; user reopens → Decompose внутренне вызывает `consume`
- Configuration change (rotation) — `instanceKeeper` survives без serializer; не зависит от этого fix

### Code Owners
- `android/feature/quizzes-screen/presentation/.../component/DefaultQuizzesComponent.kt` — *frontend-dev*
- `android/feature/quizzes-screen/presentation/.../config/QuizzesConfig.kt` — *frontend-dev*
- `android/feature/quizzes-screen/presentation/build.gradle.kts` — `kotlinx-serialization` plugin — *backend-dev* (scaffold)

### Flow Trace
```
[Initial app launch]
DefaultQuizzesComponent.init  
  → childStack(
        source = navigation,
        serializer = QuizzesConfig.serializer(),  ← NEW (отличается от existing tabs)
        initialConfiguration = QuizzesConfig.QuestList(catalogId, catalogName),
        ...
    )

[Process death]
Android kills process (low-memory)  
  → Decompose автоматически сериализует ChildStack list через ListSerializer(QuizzesConfig.serializer())
    → Bundle saved by Activity.onSaveInstanceState

[Process resurrection]
User reopens app  
  → Activity restored from Bundle
    → Decompose restores ChildStack из Bundle через QuizzesConfig.serializer()
      → Tournament resumes на тот же level с тем же breadcrumb (frozen titles из configurations)
        → Repository observers re-emit current data
          → UI рендерит restored level
```

### Backend / Contract Check
N/A — process state, не network.

### Constraints
- **Decompose 3.1.0**: `saveable` delegate ОТСУТСТВУЕТ (только с 3.2.0). Manual `consume`/`register` если нужен component-level state. Для ChildStack только сам `serializer` параметр требуется.
- **`kotlinx-serialization` plugin**: должен быть в `build.gradle.kts` нового presentation module.
- **`@Serializable` annotations**: все варианты `QuizzesConfig` (включая `data class` с параметрами) обязаны иметь `@Serializable`.
- **Frozen titles**: configurations несут `id + initialTitle` snapshot at push time. Если sync переименует item — breadcrumb остаётся со старым названием до следующего входа. Это explicit spec decision (п.20).
- **Bundle size limit**: ~500KB на Android. `QuizzesConfig` хранит только `String/Long/Int` поля + small list — overhead pренебрежимо мал.

### Code Path Divergence
**Spec предполагает только process death restoration, но сериализация также фактически работает на pause/resume / configuration change** (Decompose сохраняет в случаях когда Activity recreated). При rotation — `instanceKeeper` сохраняет Component instance напрямую, serializer не задействуется. Это разные code paths но spec-compliant.

### Fix Shape
**Client-only**:
1. Объявить `@Serializable sealed class QuizzesConfig`:
   ```kotlin
   @Serializable
   sealed class QuizzesConfig {
       @Serializable data class QuestList(val catalogId: String, val catalogName: String) : QuizzesConfig()
       @Serializable data class SectionList(val questId: String, val titles: List<String>) : QuizzesConfig()
       @Serializable data class ThemeList(val sectionId: String, val titles: List<String>) : QuizzesConfig()
       @Serializable data class LessonList(val themeId: String, val titles: List<String>) : QuizzesConfig()
       @Serializable data class LessonPlaceholder(val lessonId: String, val titles: List<String>) : QuizzesConfig()
   }
   ```
2. В `DefaultQuizzesComponent`: `childStack(source = navigation, serializer = QuizzesConfig.serializer(), initialConfiguration = ..., handleBackButton = true, ...)`.
3. Add `kotlinx-serialization` plugin к новому presentation module's `build.gradle.kts`.

**Не требуется backend** — pure local state.

### Validation
- **Tests**:
  - `QuizzesConfigSerializationTest` (JVM) — `Json.encodeToString` / `decodeFromString` round-trip каждого variant. Verify: `initialTitle` поля сохраняются точно.
  - `DefaultQuizzesComponentTest` — emulate process death через `stateKeeper` mock и проверить restoration.
- **Manual scenario**: дрилаешься на LessonList → press Home → drain memory (Android Studio "Stop") → reopen → возвращаешься на LessonList с правильным breadcrumb.
- **Success**: stack restored с frozen titles. Если bug — user возвращается на root (QuestList) как с existing tabs.

---

## Problem 7: New designsystem components — HierarchyItemCard + BreadcrumbBar

### Symptom
Spec п.10-11 требуют новых компонентов `HierarchyItemCard` (для Section/Theme/Lesson) и `BreadcrumbBar` (для path display). Оба не существуют в designsystem (verified grep: `Breadcrumb`, `OrderLabel`, `numberLabel`, `IndexLabel` — 0 results). BrandComponentsInvariantsTest применяет правила ко всем `.kt` в `components/`.

### Entry Points
- `HierarchyItemCard` — used by `SectionListContent`, `ThemeListContent`, `LessonListContent` Composables
- `BreadcrumbBar` — used by ALL drill-down screens (top of LazyColumn)

### Code Owners
- `android/core/designsystem/.../components/HierarchyItemCard.kt` — *frontend-dev*
- `android/core/designsystem/.../components/BreadcrumbBar.kt` — *frontend-dev*
- BrandComponentsInvariantsTest — auto-enforces rules

### Flow Trace
**HierarchyItemCard render**:
```
SectionListContent  → LazyColumn.items(sections)
  → HierarchyItemCard(
        title = section.title,
        orderLabel = "${section.order + 1}.",   // "1.", "2.", ...
        subtitleCount = null,                    // MVP all null (no count APIs)
        onClick = { onSectionClick(section.id) },
        onLongClick = null,                      // spec п.14
    )
```

**BreadcrumbBar render**:
```
QuestListContent (или любой drill-down screen)  → BreadcrumbBar(
    segments = listOf("Каталог имя"),
    currentLevel = 0,                            // last segment не кликабелен
    onSegmentClick = { level -> component.popToLevel(level) },
)
```

### Backend / Contract Check
N/A.

### Constraints
- **`MaterialTheme.colorScheme`** обязательно — никаких `Color(0xFF...)` (BrandComponentsInvariantsTest enforces).
- **`@Preview`** обязателен в каждом `.kt` в `components/` (BrandComponentsInvariantsTest enforces).
- **Typography**: `titleMedium` для main title (per existing pattern), `bodySmall`/`labelMedium` для orderLabel.
- **Long title ellipsis**: `TextOverflow.Ellipsis`, `maxLines = 1` (для breadcrumb segments per spec п.41) или `maxLines = 2` (для card titles per spec п.49).
- **`onLongClick: (() -> Unit)?`**: если null — `Modifier.clickable` (no haptic), если non-null — `Modifier.combinedClickable`.

### Code Path Divergence
**Spec п.50 говорит `subtitleCount = null` в MVP**. Но Compose param `subtitleCount: String?` оставляется на будущее. Compose render сам ветвится: если null — не показывать. Future-proof без overhead.

### Fix Shape
**Client-only**, две новые Composable:

**HierarchyItemCard**:
```kotlin
@Composable
fun HierarchyItemCard(
    title: String,
    orderLabel: String? = null,
    subtitleCount: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) { /* ... */ }

@Preview
@Composable
private fun PreviewHierarchyItemCard() { /* ... */ }
```

**BreadcrumbBar**:
```kotlin
@Composable
fun BreadcrumbBar(
    segments: List<String>,
    onSegmentClick: (level: Int) -> Unit,
    modifier: Modifier = Modifier,
) { /* ... separator ">", last segment не clickable, ellipsis maxLines=1 ... */ }

@Preview
@Composable
private fun PreviewBreadcrumbBar() { /* ... */ }
```

Оба используют `MaterialTheme.colorScheme.*` exclusively.

### Validation
- **Tests**:
  - `HierarchyItemCardComposeTest` — orderLabel hidden when null; long-press fires when set; ellipsis maxLines=2.
  - `BreadcrumbBarComposeTest` — тап на сегмент `n` → callback invoked with `n`; last segment не reactive; ellipsis maxLines=1.
  - `BrandComponentsInvariantsTest` — auto-passes если `@Preview` присутствует и нет `Color(0x`.
- **Manual**: открыть screen → видишь правильное оформление. На длинных titles — ellipsis работает.
- **Success**: design-spec соответствие, BrandComponentsInvariantsTest зелёный, нет regressions в `MyQuestsScreen` / `HomeQuestsScreen` (используют QuestCard, не новые компоненты).

---

## Independent Verification Protocol Results

Все ключевые claims из `1-research.md` проверены через прямое чтение source files:

| Claim из 1-research.md | Verification |
|------------------------|--------------|
| `Quest.kt:30-93` нет поля `order` | **[VERIFIED: read Quest.kt lines 25-99, поля: id, catalogId, authorUid, title, picturePath, pictureUrl, visibleOn, averageRating, averageRatingCount, version, contentsVersion, lastModifiedAt, archived. Поле `order` ОТСУТСТВУЕТ. Companion VALID_SHELVES присутствует.]** |
| `QuestEntity.kt:24-37` нет поля `order` | **[VERIFIED: read QuestEntity.kt full file, поля идентичны Quest model — без `order`. Indices: authorUid, catalogId, lastModifiedAt. Никаких индексов или sort keys на `order`.]** |
| `QuestDao.observeMyQuests/observeMyQuestsInCatalog/observeByShelf` сортируют `lastModifiedAt DESC` | **[VERIFIED: read QuestDao.kt full file. observeMyQuests `:13-17`: ORDER BY lastModifiedAt DESC. observeMyQuestsInCatalog `:20-25`: ORDER BY lastModifiedAt DESC. observeByShelf `:28-34`: ORDER BY lastModifiedAt DESC. Никаких ORDER BY order. observeByCatalog отсутствует.]** |
| `QuestDisplayItem.kt:14-20` без `catalogId` | **[VERIFIED: read QuestDisplayItem.kt full file. data class с полями: id, title, pictureUrl, averageRating, averageRatingCount. catalogId ОТСУТСТВУЕТ.]** |
| `LocalScreenComponent.kt:5-7` single Placeholder | **[VERIFIED: read LocalScreenComponent.kt full file (8 lines). sealed interface с одним вариантом `data class Placeholder(val config: LocalConfig)`. Никаких других variants.]** |
| `LocalTabComponent.kt:22` `serializer = null` | **[VERIFIED: read LocalTabComponent.kt full file. childStack(...) на line 19-27 с явным `serializer = null` на line 22, `handleBackButton = false` на line 24, `childFactory = { config, _ -> LocalScreenComponent.Placeholder(config) }` на line 26.]** |
| `MyQuestsScreen.kt:87` TODO + `:90` padding 4.dp | **[VERIFIED: read lines 80-94. Line 87 содержит `onClick = { /* TODO: open quest detail */ }`. Line 90 содержит `.padding(horizontal = 16.dp, vertical = 4.dp)`. Spec упоминает 8.dp — это discrepancy, фактически 4.dp.]** |
| `QuestCard.kt:41-49` нет `onLongClick`, использует `Modifier.clickable` | **[VERIFIED: read QuestCard.kt lines 38-62. Signature: `fun QuestCard(item: QuestDisplayItem, onClick: (QuestId) -> Unit, modifier: Modifier = Modifier)`. Line 49: `.clickable { onClick(item.id) }`. Параметр `onLongClick` отсутствует.]** |
| `SectionDao.kt:13-18` `ORDER BY \`order\` ASC` | **[VERIFIED: read SectionDao.kt full file. observeByQuest на line 13-18: WHERE questId = :questId AND archived = 0 ORDER BY \`order\` ASC. Confirmed.]** |
| `DefaultRootComponent.kt:272-280` `syncStack` через `nav.navigate` | **[VERIFIED: read lines 260-285. Line 279: `nav.navigate(transformer = { all }, onComplete = { _, _ -> })`. KDoc на :270 явно указывает «Uses navigate() directly to avoid reified constraint of replaceAll(vararg C)». syncStack вызывается из collectLatest на line 260-263 для всех 4 stacks.]** |
| `BrandComponentsInvariantsTest.kt:23-67` Color(0x prohibition + @Preview required | **[VERIFIED: read BrandComponentsInvariantsTest.kt full file. Test 1 на line 22-36: walks `components/` recursively, asserts `"Color(0x" in it.readText()` is empty. Test 3 на line 53-67: walks `components/`, asserts every `.kt` file has `"@Preview"` substring. componentsSourceRoot на :18 — `src/main/kotlin/.../components` — relative to designsystem module.]** |

**Zero contradictions** обнаружено. Все claims из research точно соответствуют коду на момент `commit 5140ae3b`. Design phase может начинаться.

---

## Invariant Conflicts

**None.** Все 7 cross-feature invariants (`docs/invariants.md`) preserved этой фичей:

| # | Invariant | Status |
|---|-----------|--------|
| 1 | Domain layer purity | ✅ — фича не добавляет ничего в shared/feature/*/domain (только presentation + read-only data extension) |
| 2 | Activity/Fragment calls only ViewModel | ✅ — Activity/Fragment не используются (Compose + Decompose) |
| 3 | No bidirectional cross-feature coupling | ✅ — quizzes-screen не импортирует `android/feature/quest/presentation`; verified zero existing bidirectional imports в проекте |
| 4 | onDestroy не для business cleanup | ✅ — нет Activity/Fragment lifecycle, только Decompose `doOnDestroy` для `componentJob.cancel()` (стандартный pattern) |
| 5 | DI exclusive binding | N/A — Koin (правило для Hilt/Dagger) |
| 6 | Walking Skeleton ownership | ✅ — Feature Domain Contract = N/A, skeleton не генерировался; verified отсутствие `domain/quizzes_screen/` файлов |
| 7 | Scaffold file ownership | ✅ — новый `build.gradle.kts` + `settings.gradle.kts` entries делает `backend-dev`; другие teammates запрашивают через lead |

## Risks Summary

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Two `FakeQuestRepository` обновлены неконсистентно | High | Single PR обновляющий обе копии; CI catches через test compilation |
| `QuestDisplayItem` extension breaks existing consumers | Low | Default value `catalogId: CatalogId? = null` — backward compat. Альтернатива: required field, но тогда требуется update всех call sites одним PR |
| `kotlinx-serialization` plugin не добавлен в presentation module | Medium | Phase-01 implementation gate должен включать `assemble` build verification |
| Process death restoration не работает на edge cases | Low | `QuizzesConfigSerializationTest` round-trip + manual scenarios |
| BrandComponentsInvariantsTest fails при первом build | Low | Design phase должен обеспечить `@Preview` в каждом новом components/ файле |
| `popTo(index)` index расчёт некорректен при breadcrumb pop | Medium | Unit test `BreadcrumbPopTest` — verify index → expected ChildStack state |
| `Intent.ACTION_SEND` `ActivityNotFoundException` на устройствах без apps для type=text/plain | Low | spec п.15a уже определяет behavior — try/catch + log без UI |

## Status

**Grounding gate PASSED.** Design phase может начинаться. Conditional documents required:
- `06-api-contract.md` (новый `QuestRepository.observeByCatalog` + расширение `QuestDisplayItem.catalogId`)
- `08-storage-model.md` (НЕ требуется — только новый Room query, не migration)
- `09-modules.md` (новый Gradle module entry)
- `10-tests.md` (test plan для 5 Components + UI + repository)

Design phase commands:
1. `/feature-design quizzes-screen` → создаст 01-context.md, 02-design.md, 03-decisions.md, 04-acceptance.md + conditional docs
2. После design approval — `/feature-plan quizzes-screen` → разбивка на phases
3. После plan approval — `/feature-implement quizzes-screen` → реализация
