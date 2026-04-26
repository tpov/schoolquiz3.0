# Realist Review — quizzes-screen 01+02

## Verdict
REJECT

## Findings

### [SEVERITY: blocker] Quizzes `ChildStack` has no valid initial/closed state
**Evidence**: `docs/features/quizzes-screen/01-architecture.md:211`, `:269-281`; `docs/features/quizzes-screen/02-behavior.md:104-127`; Decompose `ChildStackFactory.kt:146-148`, `StackNavigatorExt.kt:70-74`, `ChildStackFactory.kt:132-137`; `AppShellTransitions.kt:103-112`  
**Claim in design**: Quizzes starts from `[*]`, pushes `QuestList`/`SectionList`, and can “pop stack empty” to return to Home/MyQuests.  
**Reality**: Decompose `ChildStack` cannot be empty and `pop()` does nothing at size `1`. Existing app-shell back at LOCAL root emits `SystemBack`, it does not clear a quizzes overlay. No `Idle`/`Closed` config or external visibility state is defined.  
**Impact**: Seq-1/Seq-2 entry, root-level back, process-death cold restore, and state-machine exits are not technically executable as written.  
**Suggested fix**: Add an explicit `Idle/Closed` model or nullable/lazy stack ownership, plus a `dismissQuizzes()` path; update `pushNew/popTo/back` semantics accordingly.

### [SEVERITY: high] Back priority claim is not guaranteed and likely false with existing construction order
**Evidence**: `docs/features/quizzes-screen/01-architecture.md:131`, `:329`; `DefaultRootComponent.kt:128-142`; Decompose `ChildrenFactory.kt:122-150`; Essenty `BackCallback.kt:8-12`, `DefaultBackDispatcher.kt:53-58`  
**Claim in design**: Quizzes `handleBackButton=true` callback will be higher priority than `DefaultRootComponent.backHandler`.  
**Reality**: Essenty calls the last enabled callback at the highest priority. Existing root children are created before root registers its always-enabled callback. If quizzes is created as the same kind of flat sibling, root may be registered later at the same default priority and win.  
**Impact**: System back can go to app-shell instead of popping quizzes, especially disastrous at LOCAL root where app-shell emits `SystemBack`.  
**Suggested fix**: Specify priority/order explicitly: root callback lower priority, quizzes callback higher priority, or create/register quizzes after root back policy is adjusted.

### [SEVERITY: high] Wiring model contradicts itself: `QuizzesNavigator` injection vs lambda callbacks
**Evidence**: `docs/features/quizzes-screen/01-architecture.md:54-58`, `:127-128`, `:333-365`; `docs/invariants.md:25-28`; `DefaultHomeQuestsComponent.kt:27-31`; `QuestPresentationModule.kt:25-40`  
**Claim in design**: Root injects `QuizzesNavigator` into Home/My factories, but later says quest/presentation must receive only lambdas to avoid cross-feature import.  
**Reality**: `QuizzesNavigator` lives in the new quizzes presentation module. Passing that type into `DefaultHomeQuestsComponent`/`DefaultMyQuestsComponent` would make quest/presentation import quizzes-screen and violate the stated boundary. Lambdas are implementable; direct interface injection is not under current invariant.  
**Impact**: Implementers can follow the wrong section and introduce forbidden coupling or incompatible factory signatures.  
**Suggested fix**: Replace all Home/My “QuizzesNavigator” arrows with stdlib callback signatures; keep `QuizzesNavigator` only inside quizzes/app-shell.

### [SEVERITY: medium] `observeByCatalog` blast radius is undercounted
**Evidence**: `docs/features/quizzes-screen/01-architecture.md:186`; `FakeQuestRepository.kt` in `shared/feature/quest/domain/...:57`, `android/feature/quest/presentation/...:11`, `shared/core/sync/...:20`; `FakeQuestLocalDataSource.kt:14`; `QuestRepositoryImpl.kt:16-28`; `QuestLocalDataSource.kt:7-23`; `QuestDao.kt:13-34`  
**Claim in design**: Two `FakeQuestRepository` implementations need updating.  
**Reality**: There are three `QuestRepository` fakes, plus `FakeQuestLocalDataSource` if the local data source interface is extended.  
**Impact**: Implementation plan will compile-break sync/data tests if it updates only the two listed fakes.  
**Suggested fix**: Add `shared/core/sync/.../FakeQuestRepository.kt` and `shared/feature/quest/data/.../FakeQuestLocalDataSource.kt` to the API contract/change list.

### [SEVERITY: medium] MyQuests Q4 flow lacks an explicit quest-title source
**Evidence**: `docs/features/quizzes-screen/02-behavior.md:230-231`; `MyQuestsScreen.kt:84-87`; `QuestDisplayItem.kt:14-20`; `MyQuestsComponent.kt:14-22`  
**Claim in design**: `DefaultMyQuestsComponent.onQuestClick(id, catalogId)` builds `titles = [catalogName, quest.title]`.  
**Reality**: The proposed component method receives id/catalogId only. `quest.title` exists in the UI lambda scope and in `state.quests`, but the design does not specify passing it or looking it up.  
**Impact**: Breadcrumb construction is underspecified and can drift under recomposition/Flow updates.  
**Suggested fix**: Make the API `onQuestClick(id, catalogId, questTitle)` or pass `QuestDisplayItem`; alternatively document a required lookup from current `state.quests`.

### [SEVERITY: low] Rotation/retention language overstates current component pattern
**Evidence**: `docs/features/quizzes-screen/01-architecture.md:313-326`; `DefaultRootComponent.kt:65-67`, `:133-134`; `DefaultHomeQuestsComponent.kt:33-48`; `DefaultMyQuestsComponent.kt:53-57`; `MainActivity.kt:18-22`  
**Claim in design**: Component instance is preserved on rotation and Flow collection continues without re-subscribe.  
**Reality**: Root/component scopes are explicitly cancelled on Activity destroy; `DefaultHomeQuestsComponent` has no retained instance. `DefaultMyQuestsComponent` retains only `SelectedCatalogHolder` via `instanceKeeper`.  
**Impact**: Test expectations around rotation can be too strong.  
**Suggested fix**: State that selected local state may be retained via `instanceKeeper`; repository Flow collection may restart with the recreated component.

## Confirmed claims

- Current HEAD is `5140ae3b`, matching research baseline.
- `DefaultRootComponent` creates Home/My as flat children via `childContext`: `DefaultRootComponent.kt:128-131`.
- Home catalog tap reaches TODO: `HomeQuestsScreen.kt:54-57` → `DefaultHomeQuestsComponent.kt:50-52`.
- My quest tap is still TODO: `MyQuestsScreen.kt:84-90`.
- `QuestDisplayItem` currently lacks `catalogId`: `QuestDisplayItem.kt:14-20`; `Quest.catalogId` exists: `Quest.kt:30-33`.
- `QuestRepository.observeByCatalog` is absent; existing methods are `observeMyQuests` and `observeByShelf`: `QuestRepository.kt:39-50`.
- `QuestDao.observeByShelf` uses delimiter-wrapped `CHAR(31)` LIKE and `archived=0`: `QuestDao.kt:27-34`; converter uses `\u001F`: `StringSetConverter.kt:9-19`.
- Section/Theme/Lesson observers and DAO sort/filter claims are real: `SectionRepository.kt:24`, `SectionDao.kt:13-18`, `ThemeRepository.kt:21`, `ThemeDao.kt:13-14`, `LessonRepository.kt:21`, `LessonDao.kt:13-14`.
- Existing tab stacks use `serializer = null` and `handleBackButton = false`: `LocalTabComponent.kt:20-26`, `InternetTabComponent.kt:20-26`, `EventsTabComponent.kt:20-26`, `ShopTabComponent.kt:20-26`.
- Decompose 3.1.0 local source confirms `pushNew`, `popTo(index)`, and `childStack(serializer=...)` with `ListSerializer`: `StackNavigatorExt.kt:34-41`, `:111-120`, `ChildStackFactory.kt:33-61`.