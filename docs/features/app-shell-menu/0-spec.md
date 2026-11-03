---
date: 2026-04-17
feature: app-shell-menu
type: new-feature
commit: 35aeae89
---

# Feature Specification: App Shell Menu (Bottom Navigation + Side Drawer)

## Source
- Description: «сейчас будем делать меню, есть как и нижнее меню (вкладки) так и боковое, читай доки»
- Type: new-feature
- Базовый ADR: `docs/architecture/0008-navigation.md` (Decompose + Single-Activity Compose) и `docs/architecture/0010-designsystem.md` (Material3 + брендовая палитра). Legacy: `legacy/app/src/main/res/layout/activity_main.xml` + `legacy/app/src/main/java/com/tpov/schoolquiz/presentation/main/MainActivity.kt` — референс для преемственности, не для миграции кода.

## Requirements

### Functional Requirements

1. **Нижнее меню** (Material3 `NavigationBar`) содержит 4 вкладки: Локальная / Интернет / События / Магазин — [USER DECIDED] «4 вкладки по ADR-0008».
2. **Боковое меню** (Material3 `ModalNavigationDrawer`) имеет **свои пункты на каждой вкладке** (per-tab drawer), кроме Магазина — [USER DECIDED] «3 drawer-а, у каждой вкладки свои пункты (как ADR-0008)».
3. **Магазин** — одна страница без drawer, без подразделов Shop/Referrals/Donate в MVP — [USER DECIDED] «магазин это просто экран без бокового меню».
4. **Дизайн-система** полного объёма из ADR-0010 (`SchoolQuizTheme` + `darkColorScheme` + все wrapper-компоненты + каталог компонентов + брендовые drawable'ы) **создаётся в этой же фиче** — [USER DECIDED] «Всё в одном — shell + полный DS».
5. **Drawer открывается** одним из трёх способов: tap по hamburger-иконке в TopAppBar; edge swipe слева; программный вызов `navigator.goTo(Destination.OpenDrawer)` (используется deep link / future feature-module). Закрывается: обратный swipe; tap по scrim; system back; программный `navigator.goTo(Destination.CloseDrawer)` — [USER DECIDED] «Hamburger + edge swipe + back/tap-outside» + [DELEGATED: programmatic open/close для deep link через `Destination.OpenDrawer`/`CloseDrawer` — без отдельных Navigator-методов].
6. **Default tab при старте** — Локальная. Персистентность tab между запусками — нет — [USER DECIDED] «Локальная всегда по умолчанию».
7. **Default drawer section** вычисляется формулой `defaultSection(tab, stats) = visibleSections(tab, stats).firstOrNull()` — **не** статические жёстко заданные значения. Для guest (`UserStats.guest()`) получается: Локальная → `MyQuests` (первая видимая), Интернет → `Qualifications` (первая в declaration order с empty requiredRoles), События → `null` (все section скрыты за progressive unlock). Shop → `null` всегда (нет drawer). Legacy reference (static default для справки): Local→MyQuests, Internet→Arena, Events→ActiveEvents — применяется только когда все section unlocked. Формула — [DELEGATED: вывод из FR #20 progressive unlock, заменяет prior static rule].
8. **Переключение между вкладками сохраняет** `TabState` каждой вкладки (активная section + полный `ChildStack`). Возврат во вкладку — тот же экран, что был — [USER DECIDED] tab state preservation.
9. **Re-tap по активной вкладке**: UI вызывает `rootComponent.onActiveTabRetap(tab: Tab): RetapOutcome` (параметр `tab` позволяет валидировать в domain, что tap пришёл именно по активной). Domain возвращает `POP_TO_ROOT` если `backStack.isNotEmpty()`, иначе `NO_OP`. UI-слой additionally делает scroll-to-top через свой хук (`ScrollToTopHook` — маленький абстрактный интерфейс, реализуемый каждым scrollable экраном), когда outcome = `NO_OP` и у экрана есть зарегистрированный hook — [USER DECIDED] + [DELEGATED: domain/UI разделение].
10. **Back-кнопка** — единственный публичный API `navigator.goTo(Destination.Back)` (system-back handler `BackHandler` / `OnBackPressedDispatcher` UI-слой тоже мапит в этот вызов). Реализация внутри `RootComponent.onBackClicked()` FSM: (1) drawer открыт → `goTo(Destination.CloseDrawer)`; (2) `backStack.isNotEmpty()` → `currentTabComponent.pop()`; (3) `backStack.isEmpty()` + `activeTab != LOCAL` → `switchTab(LOCAL)`; (4) `backStack.isEmpty()` + `activeTab == LOCAL` → emit `RootEvent.SystemBack` в `rootComponent.events` flow (UI делает `finish()` / `moveTaskToBack()`) — [USER DECIDED] «Drawer → pop → Локальная → exit».
11. **TopAppBar** минимальный: hamburger слева (невидим на Shop), заголовок активного раздела центром, actions-zone справа — пусто в MVP — [USER DECIDED].
12. **Drawer header** содержит user stats: avatar, nickname, premium flag, daily streak bar, stars / nolics / hearts / gold — читаются через `UserStatsRepository.observeStats(): Flow<UserStats>` (domain repository interface, production impl — phase-01 backend-dev) с in-memory fake для тестов — [USER DECIDED] «Abstract UserStats + fake impl». Provider был переименован в Repository в ходе Variant Y refactor.
13. **Placeholder-экраны** для всех drawer-разделов и вкладок — один generic Composable `UnderConstructionScreen(title, icon)` — [USER DECIDED].
14. **Иконки** для вкладок и drawer-разделов — из `androidx.compose.material:material-icons-extended`. Кастомные drawable из legacy не переносим — [USER DECIDED].
15. **Crossfade анимация ~300ms** применяется к контент-переходам (смена tab при сохранении одного Scaffold; смена section внутри tab; push/pop stack). Drawer open/close анимируется **стандартной slide-анимацией `ModalNavigationDrawer`** — не crossfade — [USER DECIDED] + [DELEGATED: drawer-анимация стандартная из Material3].
16. **`Navigator` interface** в `shared/feature/app-shell/domain/commonMain` с единственным методом `goTo(destination: Destination)`. `Destination` sealed с вариантами `Back`, `SwitchTab(tab)`, `SelectSection(section)`, `OpenDrawer`, `CloseDrawer`, **`OpenDesignCatalog`** (debug-only footer action target). Никаких прямых методов вроде `goBack` / `navigateToTab` — всегда через `Destination` — [DELEGATED: единый API boundary для feature-модулей].
17. **Design catalog** — runtime Composable `DesignCatalogScreen` в `android/core/designsystem`. **НЕ `DrawerSection`** — смоделирован как `DrawerFooterAction.DesignCatalog` (sealed footer action, отдельный от section hierarchy). Открывается через `navigator.goTo(Destination.OpenDesignCatalog)` — domain переводит в `activeTab=LOCAL, activeSection=null, childStack.active=LocalConfig.DesignCatalogRoot, isDrawerOpen=false`. UI-фильтр: (a) drawer footer содержит пункт «Design catalog» когда `visibleFooterActions(isDebugBuild).contains(DrawerFooterAction.DesignCatalog)` = только при `BuildConfig.DEBUG == true`; (b) `AppShellScreen` рендерит `DesignCatalogScreen` по `LocalConfig.DesignCatalogRoot` только в debug, в release — fallback на `UnderConstructionScreen("Недоступно")` (защита от corrupted state). Back / crossfade / state-preservation работают через стандартный механизм Decompose — [DELEGATED: Codex v3 fix #3 — footer action, не drawer section].
18. **Badges API** предусмотрен в data-модели `NavigationBarItem` / `NavigationDrawerItem` (nullable `badge: BadgeContent?`), но в MVP всегда `null` — [DELEGATED: future-proof hook].
19. **Deep links архитектурный hook** `RootComponent.onDeepLink(deepLink: DeepLink)` где `data class DeepLink(val uri: String)` — **platform-neutral** (Android `Intent` → `DeepLink` маппится в `apps/android-next` layer). Конкретные URL-паттерны (`schoolquiz://...`) в MVP не регистрируются — [DELEGATED: Codex v2 item A — избегаем Android-зависимости в commonMain].

20. **Progressive section unlock** — каждая `DrawerSection` имеет `requiredRoles: Map<Role, Int>`. Секция видна в drawer тогда и только тогда, когда для каждой пары `(role, minLevel)` в `requiredRoles` выполняется `actualLevel(role, userStats) >= minLevel`. `Role.USER` маппится в `UserStats.currentSkill` (опыт/quiz-баллы); остальные роли — в соответствующие поля `UserStats.qualification`. Пустой `requiredRoles` → секция всегда видна. Скрытая секция **не отрисовывается** в drawer (нет lock-иконки, нет disabled-stateа). Bottom-tabs НЕ подчинены unlock rules — все 4 всегда видны (Shop имеет отдельный guard на отсутствие drawer, см. FR #3). Конкретные пороги для каждой section фиксируются в «Section Visibility Rules» таблице (see State Matrix section) — [USER DECIDED] «1A (просто не отрисовывать) + 2A (tabs всегда видны) + 3A (все 6 ролей Qualification + USER как 7-я)» + [DELEGATED: пороги скопированы 1-в-1 из legacy `MenuList.kt` как отправная точка, пользователь сам корректирует после проверки].

### Non-Functional Requirements

1. **KMP-compatibility**: вся navigation-логика (`RootComponent`, `AppShellComponent`, tab components, `Navigator`, `Destination`, `Config`, `UserStats`, `UserStatsRepository`, use cases) живёт в `shared/feature/app-shell/domain/commonMain` и не зависит от Android-SDK — [DELEGATED: соответствие ADR-0008].
2. **State-saving** через **kotlinx-serialization**: все `Config` sealed — **future state-saving** через `@Serializable` + kotlinx-serialization (MVP: `serializer=null`, каждый cold start = default state per ADR-LEAD-01). `@Parcelize` НЕ используется — [USER DECIDED 2026-04-18: state-saving deferred to future phase, ADR-LEAD-01].
3. **Architectural isolation**: feature-presentation модули импортируют только `Navigator` / `Destination` из `shared/feature/app-shell/domain`, не Decompose напрямую — [DELEGATED: ADR-0008 правило 1].
4. **Dark-only UI**: light colorScheme не реализуется — [DELEGATED: ADR-0010].
5. **Elevation = 0dp** везде (flat-дизайн с 1dp stroke), исключение — `NavigationBar` с tonal elevation — [DELEGATED: ADR-0010].

## Scope

### In Scope

1. **Дизайн-система полная**:
   - `SchoolQuizTheme` Composable в `android/core/designsystem`.
   - `darkColorScheme` с брендовой палитрой.
   - `MaterialTheme.shapes` (4/8/12/16/24 dp).
   - Типографика: Material3 defaults.
   - Wrappers: `BrandCard`, `BrandPrimaryButton`, `BrandSecondaryButton`, `BrandProgressBar`, `BrandCircleIconButton`, `CategoryIcon`.
   - `DesignCatalogScreen` Composable (runtime debug-screen).
   - Брендовые drawable'ы, если нужны для stats (например, premium icon).

2. **Navigation infrastructure**:
   - `RootComponent` (Decompose) в `shared/feature/app-shell/domain/commonMain`.
   - `AppShellComponent` exposes: `activeTab: Value<Tab>`, `activeSection: Value<DrawerSection?>`, `isDrawerOpen: Value<Boolean>`, и ссылки на четыре tab components (`localTabComponent`, `internetTabComponent`, `eventsTabComponent`, `shopTabComponent`). Сам shell **не владеет** общим `ChildStack` — каждая TabComponent держит свой typed stack внутри.
   - 4 tab components: `LocalTabComponent`, `InternetTabComponent`, `EventsTabComponent`, `ShopTabComponent`. Каждая exposes `childStack: Value<ChildStack<X, *>>`, где `X` — свой sealed config (LocalConfig / InternetConfig / EventsConfig / ShopConfig). ShopTabComponent имеет тот же ChildStack, только всегда с одним элементом (`active = ShopConfig.ShopRoot`, `backStack = []`).
   - Decompose `StackNavigation<LocalConfig>`, `<InternetConfig>`, `<EventsConfig>`, `<ShopConfig>` — по одному per-tab.
   - `Navigator` interface + `Destination` sealed.
   - `@Serializable` Config sealed per-tab.
   - `RootComponent.onBackClicked()` — **внутренняя** реализация FSM для system-back. Публичный entry-point для «normal back» — `navigator.goTo(Destination.Back)` (делегирует в `onBackClicked()`).
   - `onActiveTabRetap(tab: Tab): RetapOutcome` — domain-метод на `RootComponent`.
   - `RootComponent.events: Flow<RootEvent>` где `sealed interface RootEvent { data object SystemBack : RootEvent }`. Единственный канал domain→UI-события.

3. **Android UI**:
   - `MainActivity` создаёт `DefaultComponentContext` + `RootComponent`.
   - `AppShellScreen` Composable: `Scaffold` + `TopAppBar` + `NavigationBar` + `ModalNavigationDrawer`.
   - Per-tab `TabScreen` Composable с `childStack` rendering.
   - `UnderConstructionScreen(title, icon)` — generic placeholder.
   - `Crossfade(targetState = ..., animationSpec = tween(300))` для контент-переходов.
   - Reactive drawer state: `LaunchedEffect(component.isDrawerOpen) { ... drawerState.open()/close() }`.
   - Scroll-to-top UI-hook:
     - `interface ScrollToTopHook { suspend fun scrollToTop(): Boolean /* true = scrolled, false = already at top */ }`.
     - `class ScrollToTopRegistry { fun register(tab: Tab, hook: ScrollToTopHook); fun unregister(tab: Tab, hook: ScrollToTopHook) /* identity-checked: снимает запись ТОЛЬКО если текущая ссылка `===` переданному hook */ ; fun current(tab: Tab): ScrollToTopHook? }`. Identity-aware unregister необходим, чтобы crossfade-переход (одновременное существование outgoing+incoming экрана одного tab) не удалял свежую регистрацию при dispose старого экрана.
     - `AppShellScreen` создаёт `remember { ScrollToTopRegistry() }` и передаёт его вниз через `CompositionLocalProvider(LocalScrollToTopRegistry provides registry)`.
     - Каждый scrollable placeholder-экран в `DisposableEffect(Unit)` вызывает `registry.register(currentTab, myHook); onDispose { registry.unregister(currentTab, myHook) }`. `myHook` — реализация над Compose-state (`LazyListState.animateScrollToItem(0)`, `ScrollState.animateScrollTo(0)`, и т.п.) — это детали реализации, не контракт.
     - `AppShellScreen` по `RetapOutcome.NO_OP` делает `registry.current(activeTab)?.scrollToTop()`.

4. **Drawer content**:
   - Header: `UserStats` → avatar + nickname + premium badge + 10-сегментная streak-полоска + stats row (stars/nolics/hearts/gold).
   - Локальная drawer: Мои квесты / Мои курсы / Настройки.
   - Интернет drawer: Арена / Каталог курсов / Квалификации / Профиль / Социалка / Лидерборды.
   - События drawer: Активные события / Мини-игры.
   - Магазин: drawer отсутствует.
   - Footer: debug → «Design catalog» + «О приложении» + версия; release → «О приложении» + версия.

5. **UserStats abstraction**:
   - `data class UserStats(nickname, avatarUrl, hasPremium, streakDays, stars, nolics, standardHearts, goldHearts, gold, currentSkill, qualification)` в `shared/feature/app-shell/domain/commonMain`.
   - `interface UserStatsRepository { fun observeStats(): Flow<UserStats>; suspend fun currentStats(): UserStats }` в `shared/feature/app-shell/domain/commonMain/.../repository/`.
   - `FakeUserStatsRepository` в `shared/feature/app-shell/domain/src/commonTest/.../fake/` (test-only, не production data layer): in-memory реализация через `MutableStateFlow<UserStats>`.
   - **Production `UserStatsRepository` реализация** создаётся в phase-01 backend-dev (Firebase-backed или Firebase+Room combo) в `shared/feature/app-shell/data/`.
   - Koin-module `appShellModule` (phase-01) резолвит `UserStatsRepository` через `single` → production impl.

6. **Progressive unlock domain concepts** (в `shared/feature/app-shell/domain/commonMain`):
   - `enum class Role { USER, TESTER, MODERATOR, SPONSOR, TRANSLATOR, ADMIN, DEVELOPER }` (7 ролей).
   - `enum class Title { RECRUIT, NOVICE, TEACHINGS, PLAYER, AMATEUR, SCRABBLE, JUNIOR, TEACHER, SENIOR, EXPERT, LEGEND }` с сопутствующим `Title.skillRange: IntRange` (ranges из legacy `TitleUserValue.kt`).
   - `data class Qualification(val tester: Int, val moderator: Int, val sponsor: Int, val translator: Int, val admin: Int, val developer: Int)` + `Qualification.zero()` factory.
   - `DrawerSection` (sealed) расширяется abstract `val requiredRoles: Map<Role, Int>`.
   - Pure function `visibleSections(tab: Tab, stats: UserStats): List<DrawerSection>` — возвращает только секции `tab`, для которых удовлетворены все requiredRoles.
   - Pure function `actualLevel(role: Role, stats: UserStats): Int` — USER → `stats.currentSkill`, TESTER → `stats.qualification.tester`, и т.д.

### Explicitly Out of Scope

- **Реальный auth flow** (LoginScreen, SignUpScreen) — отдельная фича.
- **FAB-ы** (`fab_add_item`, `fab_box`, `fab_search`) — зависят от quiz-create, economy/gifts, search.
- **Shop / Referrals / Donate** подразделы — пользователь решил не делать в MVP.
- **Lock-иконки / disabled-state для скрытых секций** — скрытая section просто не отрисовывается (legacy-совместимо). Если в будущем понадобится «Откроется на уровне PLAYER» — это отдельная фича.
- **Progressive unlock для bottom-tabs** — все 4 tabs всегда видны независимо от скилла/квалификации. Visibility применяется только к drawer sections.
- **Редактирование порогов в runtime** — `requiredRoles` — compile-time константы на каждой section. Админ-панель для изменения порогов — отдельная фича.
- **Badges values** — API есть, данных нет.
- **Deep link URL регистрация** — архитектурный hook есть, паттерны нет.
- **Реальные экраны фич** — placeholder-ы, контент в отдельных фичах.
- **Persistence последней вкладки** между запусками.
- **Swipe-by-gesture между вкладками** — тап-only.
- **Light theme**.
- **Sync progress bar** (был в legacy) — часть sync-фичи.
- **Loading / Error state для UserStats** — fake всегда валидный, real impl доводим до ума в будущих фичах.
- **Scroll-to-top как domain-логика** — scroll-offset живёт в UI, domain возвращает только `RetapOutcome`.

## User Decisions

| # | Question | Answer | Impact on Design |
|---|----------|--------|------------------|
| 1 | DS scope | Всё в одном — shell + полный DS | 6-8 phases в plan; все wrappers + каталог создаются сразу |
| 2 | Placeholder-экраны | Generic `UnderConstructionScreen(title, icon)` | Минимум кода для ~14 разделов |
| 3 | Default tab | Локальная всегда | Без DataStore для tab persistence |
| 4 | Re-tap active tab | Scroll-to-top + pop-to-root fallback | Разделено: domain → RetapOutcome; UI → scroll-to-top |
| 5 | Back policy | Drawer → pop → Local → exit | 4-ступенчатый FSM в `RootComponent.onBackClicked()` |
| 6 | Drawer open/close | Hamburger + edge swipe + back/tap-outside + programmatic | `ModalNavigationDrawer` + domain-state `isDrawerOpen` |
| 7 | Drawer header | Abstract UserStats + test fake + production impl | `data class UserStats` + `interface UserStatsRepository` в domain + `FakeUserStatsRepository` в commonTest + production impl в phase-01 data module |
| 8 | Bottom tabs | 4 по ADR-0008 | Enum `Tab { LOCAL, INTERNET, EVENTS, SHOP }` |
| 9 | Top-bar | Минимальный + stats в drawer | `TopAppBar { title, navigationIcon = hamburger }` |
| 10 | Drawer model | Per-tab (3 из 4) | 3 sealed `DrawerSection` per-tab |
| 11 | Магазин | Один экран, без drawer | `ShopTabComponent` = single child; **ADR-0008 требует update** |
| 12 | Иконки | Material Icons Extended | Зависимость в designsystem-модуле |
| 13 | Анимации контента | Crossfade 300ms | Только tab-switch/section-switch/stack-push-pop; drawer — стандартная slide |
| 14 | Apply all Codex fixes | Да | См. NFR + Domain Contract ниже |
| 15 | Progressive unlock секций drawer | 1A: не отрисовывать; 2A: tabs без requirement; 3A: все 6 Qualification ролей + USER как 7-я; 4: пороги как в legacy (пользователь сам отредактирует) | `DrawerSection.requiredRoles: Map<Role, Int>`; `UserStats.currentSkill` + `UserStats.qualification: Qualification`; pure `visibleSections(tab, stats)` |

## Server-Side Context

**Не применимо.** Фича — чистый UI / navigation.

## Search Criteria for Research

### Обязательные search directions

1. **Decompose API version** — `gradle/libs.versions.toml`: что именно за версия `decompose` и `essenty`, какие API (StackNavigation, ChildStack, StateSaving через kotlinx-serialization) доступны. Подтвердить, что 3.x ветка поддерживает `@Serializable` для Config state-saving в KMP.

2. **Material Icons Extended** — есть ли `androidx.compose.material:material-icons-extended` в libs.versions.toml. Если нет — flag «dependency add required», вернуть точный coordinate через Google Maven.

3. **kotlinx-serialization setup** — подключён ли plugin `kotlin("plugin.serialization")` в `buildSrc/src/main/kotlin/schoolquiz.kmp.library.gradle.kts`. Если нет — flag. Нужна для `@Serializable` Config.

4. **Compose BOM version** — актуальная версия для корректного Material3 API (ModalNavigationDrawer, NavigationBar, TopAppBar).

5. **Koin setup** — ищем Koin модули в `shared/core/*` или `apps/android-next`. Цель: как стартует Koin (`startKoin {}` в Application), как организованы modules, чтобы добавить `appShellModule` единообразно.

6. **Legacy `SetItemMenu*.kt`** — `legacy/app/src/main/java/com/tpov/schoolquiz/presentation/main/SetItemMenu*.kt`. Читать для наименований пунктов drawer и понимания qualification-ролей (справочно, не для копирования).

7. **Legacy colors/shapes/drawables** — `legacy/app/src/main/res/values/colors.xml`. Сверить с ADR-0010 палитрой. Если ADR расходится — зафиксировать.

8. **Existing scaffold** — `apps/android-next/src/main/AndroidManifest.xml` (theme? activity declaration?), `apps/android-next/src/main/res/values/themes.xml` (если есть).

9. **Empty module verification** — `find shared/feature/app-shell android/feature/app-shell android/core/navigation android/core/designsystem -type f -name "*.kt"`. Ожидаем пусто; если нет — прочитать содержимое.

10. **Convention plugins** — `buildSrc/src/main/kotlin/schoolquiz.kmp.library.gradle.kts` и `schoolquiz.android.library.gradle.kts`: какие KMP targets, source sets, какие base-зависимости. Повлияет на module build.gradle.kts.

11. **Settings.gradle.kts маркеры** — подтверждено ли включение всех 5 модулей (shared/feature/app-shell/{domain,data}, android/feature/app-shell/presentation, android/core/navigation, android/core/designsystem).

### Completeness check

- Для пункта 1 (Decompose): grep `decompose` и `essenty` в libs.versions.toml — должно быть минимум 2 строки; research возвращает version string.
- Для пункта 2 (Material Icons): grep `material-icons-extended` — либо есть, либо нет.
- Для пункта 3 (kotlinx-serialization): grep `plugin.serialization` в `buildSrc/`.
- Для пункта 6 (SetItemMenu): `find legacy/app/src/main/java -name "SetItemMenu*.kt"` — 1-2 файла; читаем целиком.
- Для пункта 9 (empty check): обязательный find, ожидаем 0 файлов; если >0 — research читает каждый.

## Primary User Journeys

### Journey 1 — Cold start (Happy Path)
- Start: приложение не запущено.
- Trigger: user opens app.
- Domain state changes:
  - `RootComponent` → `activeTab = Tab.LOCAL`
  - `LocalTabComponent.activeSection = LocalSection.MyQuests`
  - `LocalTabComponent.childStack.active = LocalConfig.MyQuestsRoot`, `backStack = []`
  - `isDrawerOpen = false`
- Expected result: TopAppBar «Мои квесты» + hamburger слева; NavigationBar активен Локальная; контент = `UnderConstructionScreen("Мои квесты")`.
- Decision: [USER DECIDED] Q3

### Journey 2 — Drawer navigation via hamburger (Happy Path)
- Start: Локальная / Мои квесты, drawer closed.
- Trigger: user taps hamburger.
- Domain state changes:
  - `navigator.goTo(Destination.OpenDrawer)` → `isDrawerOpen = true`
  - User taps «Настройки» → `navigator.goTo(Destination.SelectSection(LocalSection.Settings))` → `activeSection = LocalSection.Settings`, `childStack.active = LocalConfig.SettingsRoot`, `backStack = []`, `isDrawerOpen = false`.
- Expected result: drawer анимированно закрылся (стандартная slide); контент = SettingsPlaceholder с crossfade 300ms; TopAppBar заголовок «Настройки».
- Decision: [USER DECIDED] Q10

### Journey 3 — Tab switching с state preservation (Happy Path)
- Start: Интернет / Арена, `childStack.active = ArenaRoot`, backStack=[].
- Trigger: user taps «Магазин» → user taps «Интернет».
- Domain state changes:
  - `goTo(SwitchTab(Tab.SHOP))`: InternetTabState saved, `activeTab = Tab.SHOP`.
  - `goTo(SwitchTab(Tab.INTERNET))`: ShopTabState saved, `activeTab = Tab.INTERNET`; InternetTabState restored (`activeSection = InternetSection.Arena`, `backStack = []`).
- Expected result: Возврат на Интернет показывает Арена с тем же drawer-section; crossfade 300ms.
- Decision: [USER DECIDED] Q8

### Journey 4 — Back sequence (Recovery Path)
- Start: Интернет / Профиль, `childStack.active = ProfileDetail`, `backStack = [ProfileRoot]`.
- Trigger: systemBack нажат подряд.
- Domain state changes shag za shagom:
  1. `navigator.goTo(Destination.Back)`: drawer closed, `backStack.isNotEmpty()` → pop → `active = InternetConfig.ProfileRoot`, `backStack = []`.
  2. `navigator.goTo(Destination.Back)`: drawer closed, `backStack.isEmpty()`, `activeTab != Tab.LOCAL` → `switchTab(Tab.LOCAL)`. Internet TabState saved (`activeSection = InternetSection.Profile`).
  3. `navigator.goTo(Destination.Back)`: drawer closed, `backStack.isEmpty()`, `activeTab == Tab.LOCAL` → emit `RootEvent.SystemBack` в `rootComponent.events`; UI вызывает `activity.finish()` / `moveTaskToBack()`.
- Expected result: каждый back выполняет один шаг.
- Decision: [USER DECIDED] Q5

### Journey 5 — Re-tap active tab (Edge Path)
- Start: Локальная / Мои квесты; ChildStack — `active = LocalConfig.MyQuestsDetail2`, `backStack = [LocalConfig.MyQuestsRoot, LocalConfig.MyQuestsDetail]` (гипотетические detail-экраны для примера поведения). UI scrollOffset > 0 на текущем экране.
- Trigger: user taps «Локальная» в NavigationBar.
- Domain:
  - `onActiveTabRetap(Tab.LOCAL)` returns `RetapOutcome.POP_TO_ROOT` (потому что `backStack.isNotEmpty()`).
  - Domain выполняет `popToRoot` на текущем `LocalTabComponent.stackNavigation`: `active = LocalConfig.MyQuestsRoot`, `backStack = []`.
- UI:
  - Applying domain outcome `POP_TO_ROOT` → контент переходит на root (MyQuestsRoot) с crossfade 300ms.
  - Scroll-to-top UI-hook не срабатывает (outcome был POP_TO_ROOT, не NO_OP).
- Expected result: активным экраном вкладки становится `LocalConfig.MyQuestsRoot`, backStack пуст.
- Decision: [USER DECIDED] Q4 + [DELEGATED: разделение domain/UI]

### Journey 6 — Re-tap active tab, уже на root (Edge Path)
- Start: Локальная / Мои квесты, `backStack = []`, scrollOffset > 0.
- Trigger: user taps «Локальная».
- Domain: `onActiveTabRetap(Tab.LOCAL)` returns `RetapOutcome.NO_OP` (backStack пуст).
- UI: `AppShellScreen` по outcome `NO_OP` читает из `ScrollToTopRegistry` hook, зарегистрированный текущим scrollable экраном (экран регистрирует себя в `DisposableEffect` через CompositionLocal-provided registry); вызывает `hook.scrollToTop()`, возвращает `true`, контент проскроллен к началу.
- Expected result: плавный скролл к началу; backStack не меняется.
- Decision: [USER DECIDED] Q4 + [DELEGATED]

### Journey 7 — Edge swipe open drawer (Happy Path)
- Start: Локальная / Мои квесты, drawer closed.
- Trigger: swipe от левого края вправо.
- UI: `ModalNavigationDrawer` ловит gesture → вызывает `navigator.goTo(Destination.OpenDrawer)` → domain `isDrawerOpen = true`.
- Expected result: drawer slide-анимация стандартная Material3.
- Decision: [USER DECIDED] Q6

### Journey 8 — Scrim close drawer (Edge Path)
- Start: Интернет / Арена, drawer open.
- Trigger: user taps on scrim (область вне drawer).
- UI: `ModalNavigationDrawer` → callback → `navigator.goTo(Destination.CloseDrawer)` → `isDrawerOpen = false`.
- Expected result: drawer закрывается slide-анимацией; контент не меняется.
- Decision: [DELEGATED: Codex item 8]

### Journey 9 — Swipe close drawer (Edge Path)
- Start: Интернет / Арена, drawer open.
- Trigger: user swipes drawer обратно влево.
- UI: `ModalNavigationDrawer` обрабатывает gesture → `navigator.goTo(Destination.CloseDrawer)` → `isDrawerOpen = false`.
- Expected result: drawer закрывается.
- Decision: [DELEGATED: Codex item 8]

### Journey 10 — System back closes drawer (Recovery Path)
- Start: Интернет / Арена, drawer open, childStack `active = InternetConfig.ArenaDetail`, `backStack = [InternetConfig.ArenaRoot]`.
- Trigger: system back → UI вызывает `navigator.goTo(Destination.Back)`.
- Domain: `navigator.goTo(Destination.Back)` → внутренний `onBackClicked()` → step 1 (drawer open) → `isDrawerOpen = false`. Stack не меняется.
- Expected result: drawer закрыт; `active = ArenaDetail`, `backStack = [ArenaRoot]` остались.
- Decision: [USER DECIDED] Q5 step 1

### Journey 11 — Programmatic drawer open (deep link hook) (Edge Path)
- Start: любая вкладка с drawer, drawer closed.
- Trigger: внешний вызов `navigator.goTo(Destination.OpenDrawer)` (позже — deep link).
- Domain: `isDrawerOpen = true`.
- UI: `LaunchedEffect(isDrawerOpen)` синхронизирует Compose DrawerState → открывает drawer slide-анимацией.
- Expected result: drawer открыт без user gesture.
- Decision: [DELEGATED: Codex item 8]

### Journey 12 — Tap по активной drawer section (Edge Path)
- Start: Интернет / Профиль, drawer open.
- Trigger: user taps «Профиль» повторно.
- Domain: `navigator.goTo(Destination.SelectSection(InternetSection.Profile))` — детектит `activeSection == target` → только `goTo(Destination.CloseDrawer)` effect (isDrawerOpen=false), без изменения `activeSection` и `childStack`.
- Expected result: drawer закрывается, контент не меняется.
- Decision: [DELEGATED: Codex item 7]

### Journey 13 — Back на root Shop (Edge Path)
- Start: Магазин, `active = ShopRoot` (Shop = single child без stack).
- Trigger: systemBack, drawer closed (на Shop drawer-а нет).
- Domain: `navigator.goTo(Destination.Back)` → внутренний FSM → шаг 2 (backStack empty для Shop всегда) → шаг 3 (tab != LOCAL) → `switchTab(Tab.LOCAL)`.
- Expected result: переключение на Локальную / MyQuests (restored state).
- Decision: [DELEGATED: Codex item 8]

### Journey 14 — Corrupted saved state fallback (Recovery Path)
- Start: app restart с corrupted SavedState (например, удалённый Config class).
- Trigger: Decompose не может восстановить state.
- Domain: `RootComponent` init fails → catch → fallback `activeTab = Tab.LOCAL`; для каждой tab `tabState = initialTabState(tab, currentStats)`. Для guest stats (типичный startup case — user ещё не аутентифицирован): Local→MyQuests/MyQuestsRoot; Internet→Qualifications/QualificationsRoot; Events→null/EmptyRoot; Shop→null/ShopRoot.
- Expected result: приложение запускается в default state, не падает.
- Decision: [DELEGATED: Codex item 8]

### Journey 14b — Guest заходит на Интернет (Happy Path для baseline user)
- Start: cold start, `stats = UserStats.guest()`, user на Локальной.
- Trigger: user taps «Интернет» в NavigationBar.
- Domain: `navigator.goTo(SwitchTab(INTERNET))` — TabState пуст (первая активация) → `initialTabState(INTERNET, guest)` вычисляется: `firstVisible = Qualifications` → `TabState(activeSection = Qualifications, stack.active = QualificationsRoot)`. `activeTab = INTERNET`.
- Expected result: TopAppBar title «Квалификации»; drawer hamburger visible; контент = `UnderConstructionScreen("Квалификации")`. Drawer при открытии показывает только 2 пункта: Квалификации, Профиль.
- Decision: [DELEGATED: Codex v3 fix #8]

### Journey 14c — Guest заходит на События (Edge Path — empty state)
- Start: cold start, `stats = UserStats.guest()`, user на Локальной.
- Trigger: user taps «События» в NavigationBar.
- Domain: `navigator.goTo(SwitchTab(EVENTS))` — первая активация → `initialTabState(EVENTS, guest)` вычисляется: `visibleSections(EVENTS, guest) = []` → `TabState(activeSection = null, stack.active = EventsConfig.EmptyRoot)`. `activeTab = EVENTS`.
- Expected result: TopAppBar title «События» (канонический fallback когда `activeSection = null`); drawer hamburger visible, но при открытии drawer main section list пустой, остаётся footer (debug: [DesignCatalog, About] + version label, release: [About] + version label — version всегда отображается); контент = `UnderConstructionScreen("Доступные события появятся при повышении уровня", icon=Events)`. User повышает уровень → flow emission через `ObserveAppShellStateUseCase` обновляет `userStats`, но **не авто-navigate**-ит (BR #19) — stay on EmptyRoot; user сам откроет drawer когда увидит что появились пункты.
- Decision: [DELEGATED: Codex v3 fix #8, runtime-unlock non-auto-navigate]

### Journey 15 — Cross-tab deep link section (Edge Path)
- Start: Локальная / MyQuests.
- Trigger: вызов `navigator.goTo(Destination.SelectSection(InternetSection.Profile))` (deep link).
- Domain: section type-safe знает свой tab (InternetSection → Tab.INTERNET). Если `activeTab != section.tab` → сначала `switchTab(section.tab)` → затем setSection → закрыть drawer если открыт.
- Expected result: переключение на Интернет + активный section Профиль.
- Decision: [DELEGATED: Codex item 7]

## Feature Domain Contract

### Terms / Entities / Value Constraints

- **`Tab`** — enum из 4 значений: `LOCAL`, `INTERNET`, `EVENTS`, `SHOP`.
- **`DrawerSection`** — `sealed interface` с **per-tab** реализациями. Каждая реализация имеет абстрактный `val tab: Tab`:
  - `sealed interface LocalSection : DrawerSection { override val tab = Tab.LOCAL }` с объектами `MyQuests`, `MyCourses`, `Settings`. **`DesignCatalog` больше НЕ `LocalSection`** — см. new term `DrawerFooterAction.DesignCatalog` ниже.
  - `sealed interface InternetSection : DrawerSection { override val tab = Tab.INTERNET }` с объектами `Arena`, `Catalog`, `Qualifications`, `Profile`, `Social`, `Leaderboard`.
  - `sealed interface EventsSection : DrawerSection { override val tab = Tab.EVENTS }` с объектами `ActiveEvents`, `Minigames`.
  - Shop не имеет `DrawerSection` — `Tab.SHOP → activeSection = null` always.
- **Config hierarchy** — **каноническая форма**: отдельные `@Serializable` sealed interfaces per-tab (не nested под одним `Config`). Каждая TabComponent параметризуется своим sealed:
  - `sealed interface LocalConfig { data object MyQuestsRoot; data object MyCoursesRoot; data object SettingsRoot; data object DesignCatalogRoot /* target когда user tap-ает footer-action DesignCatalog */; data object EmptyRoot /* sentinel, см. BR #19; для LOCAL практически не используется (MyQuests всегда visible) */; /* + future detail screens */ }`
  - `sealed interface InternetConfig { data object ArenaRoot; data object CatalogRoot; data object QualificationsRoot; data object ProfileRoot; data object SocialRoot; data object LeaderboardRoot; data object EmptyRoot /* sentinel */ }`
  - `sealed interface EventsConfig { data object ActiveEventsRoot; data object MinigamesRoot; data object EmptyRoot /* sentinel: rendered when visibleSections(EVENTS, stats) isEmpty */ }` — `EmptyRoot` существует чтобы `ChildStack` всегда имел `active` (Decompose инвариант). UI рендерит generic placeholder `UnderConstructionScreen("Доступные события появятся при повышении уровня", icon = Events)`. `EmptyRoot` — **не `DrawerSection`**, его нет в drawer; только `childStack.active`.
  - `sealed interface ShopConfig { @Serializable data object ShopRoot : ShopConfig }` — единственный элемент в MVP.
  - Нет общего супер-типа `Config` — каждый TabComponent строго типизирован через свой sealed.
- **`ChildStack<C, *>`** (Decompose) — контейнер `{ active: Child<C, *>, backStack: List<Child<C, *>> }`, где `C` = свой sealed config per-tab. Инварианты: `active` — всегда текущий верхний экран; `backStack` — экраны под ним (не содержит `active`). `backStack.isEmpty()` ↔ на root-экране текущей section (тогда `active` = root-config этой section).
- **`TabState<C>`** — внутренняя модель per-tab (один на каждую вкладку со своим типом config-а): `activeSection: DrawerSection?` + `childStack: ChildStack<C, *>`. Сохраняется в AppShellComponent при switchTab для preservation.
- **`UserStats`** — `data class` в `shared/feature/app-shell/domain/commonMain`:
  ```
  data class UserStats(
      val nickname: String,
      val avatarUrl: String?,
      val hasPremium: Boolean,
      val streakDays: Int,       // 0..10
      val stars: Long,
      val nolics: Long,
      val standardHearts: Int,   // 0..5
      val goldHearts: Int,       // 0..1
      val gold: Long,
      val currentSkill: Int,     // >= 0, опыт юзера (Role.USER level)
      val qualification: Qualification,
  ) {
      companion object { fun guest(): UserStats = ... /* currentSkill = 0, qualification = Qualification.zero() */ }
  }
  ```
- **`Role`** — `enum` из 7 значений: `USER, TESTER, MODERATOR, SPONSOR, TRANSLATOR, ADMIN, DEVELOPER`. `USER` — это обычный опыт (skill), остальные 6 — административные квалификации.
- **`Title`** — `enum` из 11 значений с сопутствующим `skillRange: IntRange` (пороги skill-баллов, скопированы из legacy `TitleUserValue.kt`):
  - `RECRUIT` → `0..999`
  - `NOVICE` → `1000..2999`
  - `TEACHINGS` → `3000..9999`
  - `PLAYER` → `10_000..19_999`
  - `AMATEUR` → `20_000..49_999`
  - `SCRABBLE` → `50_000..99_999`
  - `JUNIOR` → `100_000..149_999`
  - `TEACHER` → `150_000..299_999`
  - `SENIOR` → `300_000..499_999`
  - `EXPERT` → `500_000..999_999`
  - `LEGEND` → `1_000_000..Int.MAX_VALUE`
  - Helper `Title.first: Int` = нижняя граница диапазона (используется в `requiredRoles` как минимальный порог для достижения Title).
- **`Qualification`** — `data class`:
  ```
  data class Qualification(
      val tester: Int,
      val moderator: Int,
      val sponsor: Int,
      val translator: Int,
      val admin: Int,
      val developer: Int,
  ) {
      init {
          require(tester >= 0 && moderator >= 0 && sponsor >= 0 && translator >= 0 && admin >= 0 && developer >= 0)
      }
      companion object { fun zero(): Qualification = Qualification(0, 0, 0, 0, 0, 0) }
  }
  ```
- **`DrawerSection.requiredRoles`** — `abstract val requiredRoles: Map<Role, Int>` на каждой реализации `DrawerSection`. Пустая map → секция всегда видна. Не-пустая → AND семантика (все требования должны выполняться).
- **`actualLevel(role, stats)`** — pure helper:
  - `USER` → `stats.currentSkill`
  - `TESTER` → `stats.qualification.tester`
  - `MODERATOR` → `stats.qualification.moderator`
  - `SPONSOR` → `stats.qualification.sponsor`
  - `TRANSLATOR` → `stats.qualification.translator`
  - `ADMIN` → `stats.qualification.admin`
  - `DEVELOPER` → `stats.qualification.developer`
- **`isVisible(section, stats)`** — pure predicate: `section.requiredRoles.all { (role, minLevel) -> actualLevel(role, stats) >= minLevel }`.
- **`visibleSections(tab, stats)`** — pure function: возвращает отсортированный list всех `DrawerSection` у которых `section.tab == tab` и `isVisible(section, stats) == true`. Порядок — согласованный с default порядком в «Section Visibility Rules» таблице (= порядок в sealed interface declaration).
- **`UserStatsRepository`** — `interface UserStatsRepository { fun observeStats(): Flow<UserStats>; suspend fun currentStats(): UserStats }` в `domain/.../repository/`.
- **`FakeUserStatsRepository`** в `domain/src/commonTest/.../fake/` — test-only in-memory implementation через `MutableStateFlow(UserStats.guest())`. Production impl в phase-01.
- **`Destination`** — sealed для Navigator:
  ```
  sealed interface Destination {
      data object Back : Destination
      data class SwitchTab(val tab: Tab) : Destination
      data class SelectSection(val section: DrawerSection) : Destination
      data object OpenDrawer : Destination
      data object CloseDrawer : Destination
      data object OpenDesignCatalog : Destination  // special debug navigation target
  }
  ```
- **`Navigator`** — единственный публичный API для навигации: `interface Navigator { fun goTo(destination: Destination) }`. Без отдельного `goBack()` — «обычный back» выражается как `goTo(Destination.Back)`. System-back, прилетевший от платформы (`BackHandler`, `OnBackPressedDispatcher`), UI конвертирует в тот же `navigator.goTo(Destination.Back)`.
- **`RootComponent.onBackClicked()`** — внутренний FSM (реализация Destination.Back); не публичный API для feature-модулей. Вызывается только из `Navigator.goTo(Destination.Back)` / UI system-back handler.
- **`RetapOutcome`** — enum: `POP_TO_ROOT`, `NO_OP`. Возвращается методом `RootComponent.onActiveTabRetap(tab: Tab): RetapOutcome`.
- **`RootEvent`** — единственный механизм domain→UI events:
  ```
  sealed interface RootEvent { data object SystemBack : RootEvent }
  val RootComponent.events: Flow<RootEvent>  // KMP Flow, не Channel
  ```
  UI собирает flow, при `SystemBack` вызывает `activity.finish()` или `moveTaskToBack()`.
- **`DeepLink`** — `data class DeepLink(val uri: String)` в commonMain. Android-адаптер в `apps/android-next` маппит `Intent` → `DeepLink(intent.dataString ?: "")` и передаёт в `RootComponent.onDeepLink(deepLink)`.
- **`BadgeContent`** — `sealed interface { data class Count(val value: Int); data class Text(val value: String) }`; nullable в API.
- **`DrawerFooterAction`** — sealed interface для footer-actions в drawer (НЕ `DrawerSection`):
  - `DesignCatalog` — debug-only; tap-contract: `navigator.goTo(Destination.OpenDesignCatalog)` → domain: `activeTab = LOCAL, activeSection = null, childStack.active = LocalConfig.DesignCatalogRoot, isDrawerOpen = false`.
  - `About` — debug+release; **tap-contract для About в MVP out of scope** (UI может показывать inline snackbar/dialog с version info без domain navigation; либо отдельная фича добавит `Destination.OpenAbout` позже). Tap по About в MVP → no domain state change; UI handles locally.
  
  Domain contract предоставляет `visibleFooterActions(isDebugBuild: Boolean): List<DrawerFooterAction>` — канонический API (stats не влияет в MVP: debug flag — единственный фактор видимости). UI-слой рендерит actions в drawer footer разделе (ниже main section list). **Version label** (text "v1.2.3") — **не `DrawerFooterAction`**, а отдельный UI element всегда отображаемый рядом с actions (и в debug, и в release; читается UI-слоем из `BuildConfig.VERSION_NAME`).
- **`rootOf(section: DrawerSection): <TabConfig>`** — pure mapping function:
  | DrawerSection | → | Config root |
  |---------------|---|-------------|
  | `LocalSection.MyQuests` | → | `LocalConfig.MyQuestsRoot` |
  | `LocalSection.MyCourses` | → | `LocalConfig.MyCoursesRoot` |
  | `LocalSection.Settings` | → | `LocalConfig.SettingsRoot` |
  | `InternetSection.Arena` | → | `InternetConfig.ArenaRoot` |
  | `InternetSection.Catalog` | → | `InternetConfig.CatalogRoot` |
  | `InternetSection.Qualifications` | → | `InternetConfig.QualificationsRoot` |
  | `InternetSection.Profile` | → | `InternetConfig.ProfileRoot` |
  | `InternetSection.Social` | → | `InternetConfig.SocialRoot` |
  | `InternetSection.Leaderboard` | → | `InternetConfig.LeaderboardRoot` |
  | `EventsSection.ActiveEvents` | → | `EventsConfig.ActiveEventsRoot` |
  | `EventsSection.Minigames` | → | `EventsConfig.MinigamesRoot` |
- **`emptyRootFor(tab: Tab): <TabConfig>`** — pure mapping function for empty-state sentinel:
  | Tab | → | EmptyRoot |
  |-----|---|-----------|
  | `LOCAL` | → | `LocalConfig.EmptyRoot` (не используется в practice, MyQuests всегда visible) |
  | `INTERNET` | → | `InternetConfig.EmptyRoot` (не используется, Qualifications всегда visible) |
  | `EVENTS` | → | `EventsConfig.EmptyRoot` (используется для guest) |
  | `SHOP` | → | `ShopConfig.ShopRoot` (Shop не имеет empty-state, single child всегда) |
- **`initialTabState(tab: Tab, stats: UserStats): TabState<*>`** — pure function. Возвращает `TabState` для tab на cold start / corrupted fallback:
  - Compute `firstVisible = visibleSections(tab, stats).firstOrNull()`
  - Если `firstVisible != null`: `TabState(activeSection = firstVisible, stack = ChildStack(active = rootOf(firstVisible), backStack = emptyList()))`
  - Иначе (pусто для EVENTS при guest): `TabState(activeSection = null, stack = ChildStack(active = emptyRootFor(tab), backStack = emptyList()))`
- **`UserStats` source в domain**: `AppShellState.userStats` — **cached latest snapshot** из `UserStatsRepository.observeStats()` (см. repository interface). `ObserveAppShellStateUseCase` подписывается на flow и эмитит new `AppShellState.copy(userStats = newStats)` при каждой эмиссии. Navigation state (`activeTab`, per-tab `TabState`) **не сбрасывается** при stats update — только `userStats` обновляется. UI читает stats через exposed `AppShellState.userStats`, не напрямую через `UserStatsRepository`.

### Business Rules / Invariants / Guards

1. **activeTab всегда одно из 4 значений Tab enum** — enum type safety.
2. **Default tab = `Tab.LOCAL`** при cold start. Persistence между запусками отсутствует.
3. **Default DrawerSection** вычисляется как `defaultSection(tab, stats) = visibleSections(tab, stats).firstOrNull()`. Для текущих unlock-правил + guest stats: Local → `MyQuests`, Internet → `Qualifications`, Events → `null` (нет видимых section), Shop → `null` (нет drawer). Static reference (когда все unlocked): Local → MyQuests, Internet → Arena, Events → ActiveEvents.
4. **activeSection на Tab.SHOP всегда `null`**.
5. **`isDrawerOpen` не может быть `true` при `activeTab == SHOP`**: `goTo(OpenDrawer)` при Shop активна — no-op (domain guard); hamburger скрыт в UI, edge swipe заблокирован.
6. **Section type-safe привязан к tab**: каждая `DrawerSection` имеет `val tab: Tab`. `goTo(SelectSection(s))` при `activeTab != s.tab` → domain сначала `switchTab(s.tab)`, затем set `activeSection = s`, затем close drawer.
7. **Переключение section внутри вкладки** очищает `childStack` текущей вкладки до root соответствующей section.
8. **Переключение между вкладками сохраняет** полный `TabState` (activeSection + childStack) каждой вкладки.
9. **Back priority** (запускается через `navigator.goTo(Destination.Back)` или system-back handler, который делает тот же вызов):
   - drawer open → `goTo(CloseDrawer)`
   - `backStack.isNotEmpty` → pop текущего TabComponent.stackNavigation
   - `backStack.isEmpty` + `activeTab != LOCAL` → `switchTab(LOCAL)` (Local TabState восстанавливается)
   - `backStack.isEmpty` + `activeTab == LOCAL` → emit `RootEvent.SystemBack` в `events` flow
10. **`onActiveTabRetap` domain-логика**: если `backStack.isNotEmpty()` → `POP_TO_ROOT` (domain очищает backStack, active остаётся root-config); иначе → `NO_OP`.
11. **Scroll-to-top** не входит в domain — UI-hook.
12. **Tap по активной `DrawerSection`** при drawer open → только closeDrawer, без изменения activeSection / childStack.
13. **Crossfade 300ms** применяется к: tab switch, section switch, stack push/pop — в UI. Drawer open/close анимируется стандартной `ModalNavigationDrawer` slide-анимацией.
14. **Design catalog footer-item в drawer** виден только когда `BuildConfig.DEBUG == true`. Проверка производится в UI-слое.
15. **Badges nullable**: `BadgeContent? = null` по умолчанию. Domain в MVP не поставляет значения.
16. **Progressive section unlock (AND семантика)**: секция видна ↔ `section.requiredRoles.all { (role, minLevel) -> actualLevel(role, stats) >= minLevel }`. Пустой `requiredRoles` → always visible.
17. **Скрытая секция не отрисовывается**: UI-слой вызывает `visibleSections(activeTab, stats)` и рендерит только результат. Скрытая секция не отображается вообще (ни как disabled, ни с lock-иконкой).
18. **Bottom-tabs не подчинены visibility**: все 4 `Tab` значения всегда видны в NavigationBar. Unlock применяется только к `DrawerSection`.
19. **Default section при cold-start / corrupted-state fallback** = `defaultSection(tab, stats) = visibleSections(tab, stats).firstOrNull()`. Behaviour per tab:
    - Если `firstVisible != null`: `activeSection = firstVisible`; `childStack.active = rootOf(firstVisible)`; `backStack = []`
    - Если `visibleSections(tab, stats).isEmpty()`: `activeSection = null`; `childStack.active = <TabConfig>.EmptyRoot` (sentinel); `backStack = []`
    - Для LOCAL при guest — всегда `MyQuests` visible (empty requiredRoles)
    - Для INTERNET при guest — `Qualifications` (первая в declaration с empty requiredRoles)
    - Для EVENTS при guest — `null` + `EventsConfig.EmptyRoot` (обе section non-empty requirements)
    - Для SHOP — не применяется (Shop=single child `ShopConfig.ShopRoot` всегда)
    
    **Не применяется при обычном switchTab с сохранённым TabState** — восстанавливается как есть (BR #8). Runtime-unlock (user прокачался во время сессии и появилась новая видимая section) **не авто-navigate**-ит — stay on empty root; user сам откроет drawer и выберет section [DELEGATED: не-сюрприз-UX; auto-navigate может прервать текущее действие пользователя].
20. **Tap по скрытой section невозможен из UI** (её нет в drawer), но domain guard всё равно применяется: `goTo(SelectSection(s))` где `!isVisible(s, currentStats)` → no-op (silent). Защищает от deep link / programmatic calls.

### State / Decision Rules

- См. State Matrix ниже для back-policy, re-tap, drawer visibility, tab switch.

### Error / Recovery Rules

- **Decompose SavedState corrupted / migration failed** → RootComponent init fallback = **stats-aware** через `initialTabState(tab, currentStats)` for каждой tab. `activeTab = LOCAL`. `isDrawerOpen = false`. Таблица для guest stats: Local→MyQuests/MyQuestsRoot (backStack=[]); Internet→Qualifications/QualificationsRoot; Events→null/EmptyRoot; Shop→null/ShopRoot. При ненулевом skill/qualification fallback корректно обнаружит более первую visible section через ту же формулу. В Decompose `ChildStack` всегда минимум один `active` элемент — `EmptyRoot` гарантирует инвариант когда visibleSections пустые.
- **`goTo(OpenDrawer)` при `activeTab == SHOP`** → domain no-op (log on debug build, silent on release).
- **`goTo(SelectSection(s))` при `activeTab == SHOP` AND `s` не существует для Shop** → domain сначала switchTab, как по rule 6.
- **UserStatsRepository.observeStats() failure** → fake impl не падает (emits initial guest). Production impl (phase-01) должен `.catch { emit(UserStats.guest()) }` с silent retry. MVP не описывает конкретную retry-политику.
- **Re-tap active tab**: никогда не падает. Всегда domain → outcome; UI всегда имеет defined behavior.

### Domain Test Scenarios (source of truth for domain-first phase)

> Фаза, где эти тесты должны появиться первыми, определяется `/feature-plan`, обычно это phase навigation-infra (после DS). Текущее spec не фиксирует номер фазы — это делается в plan.

1. **GIVEN** cold start **WHEN** `RootComponent` создан **THEN** `activeTab == Tab.LOCAL` AND `LocalTabComponent.activeSection == LocalSection.MyQuests` AND `childStack.active == LocalConfig.MyQuestsRoot` AND `childStack.backStack.isEmpty()` AND `isDrawerOpen == false`.

2. **GIVEN** `activeTab == LOCAL`, backStack=[], `stats.currentSkill >= TEACHINGS.first` (Arena unlocked) **WHEN** `navigator.goTo(SwitchTab(INTERNET))` с пустым сохранённым Internet TabState (первая активация) **THEN** `activeTab == INTERNET` AND `InternetTabComponent.activeSection == defaultSection(INTERNET, stats) == InternetSection.Arena` (первая visible при unlocked) AND Local TabState preserved (`activeSection == MyQuests`). *Для guest (currentSkill=0) см. Journey 14b — firstVisible = Qualifications.*

3. **GIVEN** `activeTab == INTERNET`, `activeSection == Arena`, backStack=[] **WHEN** `goTo(SelectSection(InternetSection.Profile))` **THEN** `activeSection == Profile` AND `childStack.active == InternetConfig.ProfileRoot` AND `backStack.isEmpty()` AND `isDrawerOpen == false`.

4. **GIVEN** Internet/Profile active, `childStack.active == ProfileRoot`, backStack=[] **WHEN** `goTo(SwitchTab(LOCAL))` then `goTo(SwitchTab(INTERNET))` **THEN** Internet activeSection == Profile AND childStack.active == ProfileRoot (restored).

5. **GIVEN** Local/MyQuests, backStack=[] **WHEN** `rootComponent.onActiveTabRetap(Tab.LOCAL)` **THEN** returns `RetapOutcome.NO_OP` AND backStack остаётся пустым.

6. **GIVEN** Local/MyQuests, backStack=[MyQuestsDetail] **WHEN** `rootComponent.onActiveTabRetap(Tab.LOCAL)` **THEN** returns `RetapOutcome.POP_TO_ROOT` AND backStack=[] AND `active` = `MyQuestsRoot`.

7. **GIVEN** `isDrawerOpen == true` **WHEN** `navigator.goTo(Destination.Back)` **THEN** `isDrawerOpen == false` AND activeTab/childStack не меняются.

8. **GIVEN** Internet/Arena, childStack `active = InternetConfig.ArenaDetail`, `backStack = [InternetConfig.ArenaRoot]`, drawer closed **WHEN** `navigator.goTo(Destination.Back)` **THEN** `active = InternetConfig.ArenaRoot` AND `backStack = []`.

9. **GIVEN** Internet/Arena, childStack `active = InternetConfig.ArenaRoot`, `backStack = []`, drawer closed **WHEN** `navigator.goTo(Destination.Back)` **THEN** `activeTab == LOCAL` (Local TabState restored).

10. **GIVEN** Local/MyQuests, childStack `active = LocalConfig.MyQuestsRoot`, `backStack = []`, drawer closed **WHEN** `navigator.goTo(Destination.Back)` **THEN** `RootEvent.SystemBack` emit-ится в `rootComponent.events` flow (проверка через collect-into-list).

11. **GIVEN** `activeTab == SHOP` **WHEN** `goTo(OpenDrawer)` **THEN** `isDrawerOpen == false` (no-op).

12. **GIVEN** Local/MyQuests **WHEN** `goTo(SelectSection(InternetSection.Profile))` **THEN** `activeTab == INTERNET` AND `activeSection == Profile` AND childStack.active == ProfileRoot (cross-tab auto-switch).

13. **GIVEN** Internet/Profile, drawer open **WHEN** `goTo(SelectSection(InternetSection.Profile))` (tap по активной section) **THEN** `isDrawerOpen == false` AND activeSection/childStack НЕ меняются.

14. **GIVEN** `UserStats.guest()` **WHEN** любой caller reads fields **THEN** nickname="Гость", avatarUrl=null, hasPremium=false, streakDays=0, stars=0, nolics=0, standardHearts=5, goldHearts=0, gold=0, currentSkill=0, qualification=Qualification.zero(). *(Чистый domain-тест на companion factory, без fake repository.)*

15. **GIVEN** Shop, childStack `active = ShopConfig.ShopRoot`, `backStack = []` (Shop всегда в этом состоянии), drawer closed **WHEN** `navigator.goTo(Destination.Back)` **THEN** `activeTab == LOCAL` (restore Local TabState).

16. **GIVEN** corrupted SavedState + `currentStats = UserStats.guest()` **WHEN** `RootComponent` init **THEN** fallback state через `initialTabState(tab, guest)` для каждой tab: `activeTab=LOCAL`; LocalTabState = `(activeSection=MyQuests, stack.active=MyQuestsRoot)`; InternetTabState = `(activeSection=Qualifications, stack.active=QualificationsRoot)`; EventsTabState = `(activeSection=null, stack.active=EventsConfig.EmptyRoot)`; ShopTabState = `(activeSection=null, stack.active=ShopRoot)`; `isDrawerOpen=false`. Verify через catch+fallback; assert per-tab через `initialTabState(tab, stats)` call.

17. **GIVEN** `activeTab == LOCAL`, `isDrawerOpen == false` **WHEN** `navigator.goTo(Destination.OpenDrawer)` **THEN** `isDrawerOpen == true` AND activeTab/activeSection/childStack не меняются.

18. **GIVEN** `activeTab == INTERNET`, `isDrawerOpen == true` **WHEN** `navigator.goTo(Destination.CloseDrawer)` (имитация scrim-tap / swipe-close из UI) **THEN** `isDrawerOpen == false` AND activeTab/activeSection/childStack не меняются.

19. **GIVEN** `activeTab == SHOP`, `isDrawerOpen == false` **WHEN** `navigator.goTo(Destination.OpenDrawer)` **THEN** `isDrawerOpen` остаётся `false` (guard: Shop не имеет drawer).

20. **GIVEN** Internet/Profile (`activeSection == InternetSection.Profile`), `isDrawerOpen == true` **WHEN** `navigator.goTo(Destination.SelectSection(InternetSection.Profile))` (tap по активной section) **THEN** `isDrawerOpen == false` AND activeSection и childStack не меняются.

21. **GIVEN** Local/MyQuests (`activeTab == Tab.LOCAL`) **WHEN** `rootComponent.onDeepLink(DeepLink(uri = "stub://unrecognized"))` (MVP stub accept any URI без зарегистрированных паттернов) **THEN** state не меняется (stub hook просто валиден вызовом, не делает navigation). В будущем этот тест будет расширен когда появятся URL-паттерны.

22. **GIVEN** `stats = UserStats.guest()` (currentSkill=0, qualification=zero) **WHEN** `visibleSections(Tab.LOCAL, stats)` **THEN** returns `[MyQuests, MyCourses, Settings]` (все три — empty requiredRoles).

23. **GIVEN** `stats = UserStats.guest()` **WHEN** `visibleSections(Tab.INTERNET, stats)` **THEN** returns `[Qualifications, Profile]` в порядке declaration `InternetSection` (Arena/Catalog/Leaderboard hidden — требуют TEACHINGS.first = 3000; Social скрыт — требует PLAYER.first = 10000; Qualifications и Profile — empty requiredRoles и в такой же последовательности объявлены в sealed interface).

24. **GIVEN** `stats = UserStats.guest()` **WHEN** `visibleSections(Tab.EVENTS, stats)` **THEN** returns `[]` (ActiveEvents требует TESTER/MODERATOR/ADMIN/DEVELOPER ≥ 100; Minigames требует PLAYER.first = 10000).

25. **GIVEN** `stats = UserStats.guest().copy(currentSkill = 3000)` (достиг TEACHINGS) **WHEN** `visibleSections(Tab.INTERNET, stats)` **THEN** returns `[Arena, Catalog, Qualifications, Profile, Leaderboard]` (Social ещё скрыт — нужен PLAYER = 10000).

26. **GIVEN** `stats = UserStats.guest().copy(currentSkill = 10_000)` (PLAYER) **WHEN** `visibleSections(Tab.INTERNET, stats)` **THEN** returns все 6 Internet sections: `[Arena, Catalog, Qualifications, Profile, Social, Leaderboard]`.

27. **GIVEN** `stats = UserStats.guest().copy(currentSkill = 10_000)` **WHEN** `visibleSections(Tab.EVENTS, stats)` **THEN** returns `[Minigames]` только (ActiveEvents ещё скрыт — требует все 4 Qualification роли ≥ 100, но `qualification = zero()`).

28. **GIVEN** `stats = UserStats.guest().copy(qualification = Qualification(tester=100, moderator=100, sponsor=0, translator=0, admin=100, developer=100))` **WHEN** `visibleSections(Tab.EVENTS, stats)` **THEN** returns `[ActiveEvents]` (Minigames скрыт — currentSkill=0 < PLAYER=10000).

29. **GIVEN** `section = InternetSection.Arena` (требует USER ≥ TEACHINGS.first = 3000), `stats.currentSkill = 2999` **WHEN** `isVisible(section, stats)` **THEN** returns `false` (строгое неравенство, граница inclusive: 3000 достаточно, 2999 — нет).

30. **GIVEN** `section = InternetSection.Arena`, `stats.currentSkill = 3000` **WHEN** `isVisible(section, stats)` **THEN** returns `true`.

31. **GIVEN** `section = EventsSection.ActiveEvents` (требует TESTER=100 AND MODERATOR=100 AND ADMIN=100 AND DEVELOPER=100), `qualification = Qualification(tester=100, moderator=100, sponsor=0, translator=0, admin=100, developer=99)` **WHEN** `isVisible(section, stats)` **THEN** returns `false` (developer=99 не удовлетворяет требованию 100; AND семантика).

32. **GIVEN** `Role.USER` enum value **WHEN** `actualLevel(Role.USER, stats)` для `stats.currentSkill = 5000` **THEN** returns `5000`.

33. **GIVEN** `Role.ADMIN` enum value **WHEN** `actualLevel(Role.ADMIN, stats)` для `stats.qualification.admin = 42` **THEN** returns `42`.

34. **GIVEN** Internet/Profile, drawer open, `stats.currentSkill = 0` (Arena скрыт) **WHEN** `navigator.goTo(Destination.SelectSection(InternetSection.Arena))` **THEN** state не меняется (domain guard: visibility check fail → no-op; защита от deep link).

35. **GIVEN** любая Tab, пустой `requiredRoles = emptyMap()` **WHEN** `isVisible(section, stats)` для любого `stats` **THEN** returns `true` (all-predicate на пустой коллекции = true).

36. **GIVEN** cold start, `stats = UserStats.guest()` **WHEN** `initialTabState(Tab.INTERNET, stats)` **THEN** returns `TabState(activeSection = InternetSection.Qualifications, stack = ChildStack(active = InternetConfig.QualificationsRoot, backStack = []))` (первая видимая = Qualifications).

37. **GIVEN** cold start, `stats = UserStats.guest()` **WHEN** `initialTabState(Tab.EVENTS, stats)` **THEN** returns `TabState(activeSection = null, stack = ChildStack(active = EventsConfig.EmptyRoot, backStack = []))` (нет видимых section → sentinel EmptyRoot).

38. **GIVEN** `stats = UserStats.guest()`, `AppShellState.default()` initialized **WHEN** `navigator.goTo(Destination.SwitchTab(Tab.EVENTS))` (previous TabState пуст — первая активация) **THEN** `activeTab = EVENTS`; `EventsTabState.activeSection = null`; `EventsTabState.childStack.active = EventsConfig.EmptyRoot`. UI рендерит `UnderConstructionScreen("Доступные события появятся при повышении уровня", icon=Events)`.

39. **GIVEN** corrupted SavedState + `stats.currentSkill = 0` **WHEN** `RootComponent` init fallback **THEN** для каждой tab применяется `initialTabState(tab, stats)`: Local → MyQuests; Internet → Qualifications; Events → EmptyRoot (null section); Shop → ShopRoot. `isDrawerOpen = false`.

40. **GIVEN** Events tab активна, `activeSection = null`, `active = EventsConfig.EmptyRoot` (guest) **WHEN** stats обновляется до `currentSkill = 10_000` (PLAYER unlock — `Minigames` теперь visible) через `ObserveAppShellStateUseCase` flow emission **THEN** `AppShellState.userStats` обновляется; Events tab **НЕ авто-navigate**-ит (BR #19 DELEGATED: stay on empty) — `activeSection` остаётся `null`, `childStack.active` = `EmptyRoot`. User сам откроет drawer и tap-нет «Мини-игры» для перехода.

41. **GIVEN** любой debug build, drawer open **WHEN** `navigator.goTo(Destination.OpenDesignCatalog)` **THEN** `activeTab = LOCAL`; `activeSection = null` (DesignCatalog не LocalSection); `childStack.active = LocalConfig.DesignCatalogRoot`; `backStack = []`; `isDrawerOpen = false`.

42. **GIVEN** release build **WHEN** `visibleFooterActions(isDebugBuild = false)` **THEN** returns `[DrawerFooterAction.About]` (DesignCatalog скрыт; About всегда).

43. **GIVEN** debug build **WHEN** `visibleFooterActions(isDebugBuild = true)` **THEN** returns `[DrawerFooterAction.DesignCatalog, DrawerFooterAction.About]`.

44. **GIVEN** table-driven test для `rootOf(section)` **WHEN** iterate по всем 11 `DrawerSection` implementations **THEN** каждая возвращает correct `TabConfig`-member согласно mapping table (см. Terms/Entities секцию). Table: MyQuests→MyQuestsRoot, MyCourses→MyCoursesRoot, Settings→SettingsRoot, Arena→ArenaRoot, Catalog→CatalogRoot, Qualifications→QualificationsRoot, Profile→ProfileRoot, Social→SocialRoot, Leaderboard→LeaderboardRoot, ActiveEvents→ActiveEventsRoot, Minigames→MinigamesRoot. **11 assertions** в одном `@Test` или параметризованном тесте.

45. **GIVEN** table-driven test для `emptyRootFor(tab)` **WHEN** iterate по всем 4 `Tab` values **THEN** returns correct empty sentinel: LOCAL→LocalConfig.EmptyRoot; INTERNET→InternetConfig.EmptyRoot; EVENTS→EventsConfig.EmptyRoot; SHOP→ShopConfig.ShopRoot (Shop особый case — нет empty).

### Data-module Test Scenarios (phase-01 — production `UserStatsRepository` impl в `shared/feature/app-shell/data`)

D1. **GIVEN** production `UserStatsRepository` impl (Firebase-backed) **WHEN** `observeStats().first()` для nonauthenticated user **THEN** emits `UserStats.guest()`.

D2. **GIVEN** production impl singleton в Koin `appShellModule` **WHEN** `koin.get<UserStatsRepository>()` **THEN** returns тот же instance (singleton scope).

D3. **GIVEN** production impl, user authenticated, Firebase stats changed **WHEN** `observeStats()` subscribed **THEN** emits new `UserStats` снапшот с обновлёнными полями (real-time sync).

D1-D3 принадлежат **phase-01 integration tests**, не spec-этапу. На spec-этапе уже покрыты тесты через `FakeUserStatsRepository` (in-memory, в commonTest).

## State Matrix

### Back-policy FSM (`onBackClicked()` on `RootComponent`)

| # | drawerOpen | backStack.isEmpty | activeTab | Action                                   | Decision       |
|---|------------|-------------------|-----------|------------------------------------------|----------------|
| 1 | true       | any               | any       | `closeDrawer()`                          | [USER DECIDED] |
| 2 | false      | false             | any       | `currentTabComponent.pop()`              | [USER DECIDED] |
| 3 | false      | true              | LOCAL     | emit `RootEvent.SystemBack` в `events` flow | [USER DECIDED] |
| 4 | false      | true              | INTERNET  | `switchTab(LOCAL)` (restore Local state) | [USER DECIDED] |
| 5 | false      | true              | EVENTS    | `switchTab(LOCAL)`                       | [USER DECIDED] |
| 6 | false      | true              | SHOP      | `switchTab(LOCAL)`                       | [USER DECIDED] |

Все ячейки заполнены однозначно.

### Re-tap active tab FSM (`onActiveTabRetap(tab)` returns `RetapOutcome`)

| # | backStack.isEmpty | Returns                        | Domain side-effect   | Decision       |
|---|-------------------|--------------------------------|----------------------|----------------|
| 1 | false             | `RetapOutcome.POP_TO_ROOT`     | backStack cleared    | [USER DECIDED] |
| 2 | true              | `RetapOutcome.NO_OP`           | none                 | [USER DECIDED] |

UI отдельно обрабатывает: при `NO_OP` `AppShellScreen` делает `scrollToTopRegistry.current(activeTab)?.scrollToTop()`. Если hook вернул `true` — контент проскроллен; если `false` (уже наверху) или hook не зарегистрирован — UI ничего не делает.

### Drawer visibility FSM

| # | activeTab | hamburgerVisible | edgeSwipeEnabled | Decision       |
|---|-----------|------------------|------------------|----------------|
| 1 | LOCAL     | true             | true             | [USER DECIDED] |
| 2 | INTERNET  | true             | true             | [USER DECIDED] |
| 3 | EVENTS    | true             | true             | [USER DECIDED] |
| 4 | SHOP      | false            | false            | [USER DECIDED] |

### Tab switch FSM (`goTo(SwitchTab(target))`)

| # | currentTab | target     | Action                                                              | Decision       |
|---|-----------|------------|---------------------------------------------------------------------|----------------|
| 1 | X         | X          | `onActiveTabRetap(X)` → см. Re-tap FSM                              | [USER DECIDED] |
| 2 | X         | Y (Y ≠ X)  | `save(X.TabState); activeTab = Y; restore(Y.TabState)`              | [USER DECIDED] |

### Section Visibility Rules (source of truth для `requiredRoles` на каждой `DrawerSection`)

Значения взяты 1-в-1 из legacy `MenuList.kt` + `TitleUserValue.kt`. Пользователь корректирует пороги после проверки.

Условные обозначения: `TEACHINGS.first` = 3000, `PLAYER.first` = 10_000 (см. Title enum ranges выше). `Qualification=100` — legacy literal (не Title-based).

| # | Tab      | Section          | `requiredRoles`                                                                                                   | Legacy origin        | Decision |
|---|----------|------------------|-------------------------------------------------------------------------------------------------------------------|----------------------|----------|
| 1 | LOCAL    | MyQuests         | `emptyMap()`                                                                                                      | MENU_HOME_QUIZ       | [DELEGATED] |
| 2 | LOCAL    | MyCourses        | `emptyMap()`                                                                                                      | MENU_MY_QUIZ (was RECRUIT.first = 0, эквивалент empty) | [DELEGATED] |
| 3 | LOCAL    | Settings         | `emptyMap()`                                                                                                      | MENU_SETTING         | [DELEGATED] |
| 4 | — | — | — — `DesignCatalog` reclassified as `DrawerFooterAction`, **НЕ `DrawerSection`**. Не попадает в `visibleSections()`. Visibility — через `visibleFooterActions(isDebugBuild)` = `if (isDebugBuild) [DesignCatalog, About] else [About]` | — | [DELEGATED] Codex v3 fix #3 |
| 5 | INTERNET | Arena            | `{ USER to TEACHINGS.first }` (3000)                                                                              | MENU_ARENA           | [DELEGATED] |
| 6 | INTERNET | Catalog          | `{ USER to TEACHINGS.first }` (3000)                                                                              | аналогия ARENA/LEADER | [DELEGATED] |
| 7 | INTERNET | Qualifications   | `emptyMap()`                                                                                                      | N/A (новая)          | [DELEGATED] |
| 8 | INTERNET | Profile          | `emptyMap()`                                                                                                      | MENU_PROFILE         | [DELEGATED] |
| 9 | INTERNET | Social           | `{ USER to PLAYER.first }` (10000)                                                                                | MENU_CHAT_TOURNAMENT | [DELEGATED] |
| 10 | INTERNET | Leaderboard      | `{ USER to TEACHINGS.first }` (3000)                                                                              | MENU_LEADER          | [DELEGATED] |
| 11 | EVENTS   | ActiveEvents     | `{ TESTER to 100, MODERATOR to 100, ADMIN to 100, DEVELOPER to 100 }`                                             | MENU_EVENT           | [DELEGATED] |
| 12 | EVENTS   | Minigames        | `{ USER to PLAYER.first }` (10000)                                                                                | аналогия MENU_MASSAGE | [DELEGATED] |

### Drawer section switch FSM (`goTo(SelectSection(section))`)

| # | section.tab == activeTab | section == activeSection | drawerOpen | Action                                                                                            | Decision       |
|---|--------------------------|--------------------------|------------|---------------------------------------------------------------------------------------------------|----------------|
| 1 | false                    | any                      | any        | Pre-guard: если `!isVisible(section, stats)` → no-op. Иначе: `switchTab(section.tab); activeSection = section; childStack.replaceAll(rootOf(section)); isDrawerOpen = false` | [DELEGATED]    |
| 2 | true                     | false                    | any        | Pre-guard: если `!isVisible(section, stats)` → no-op. Иначе: `activeSection = section; childStack.replaceAll(rootOf(section)); isDrawerOpen = false`           | [DELEGATED]    |
| 3 | true                     | true                     | true       | `isDrawerOpen = false` (no other change)                                                          | [DELEGATED]    |
| 4 | true                     | true                     | false      | no-op                                                                                              | [DELEGATED]    |

Pre-guard добавлен во всех переходах где `section` меняется (rows 1-2). Для rows 3-4 guard не нужен: `section == activeSection` означает что section уже была visible когда стала активной, а `requiredRoles` compile-time константы — не могут резко перестать выполняться если stats не изменился. Если же stats изменился и активная section стала invisible — это отдельная ситуация, не покрыта в MVP (visibility re-check при stats update — future-work).

## Delegated Decisions Summary

| # | Область                                        | Решение                                                                                                              | Обоснование                                                         | Risk |
|---|-----------------------------------------------|----------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------|------|
| 1 | Default DrawerSection per tab (stats-aware)   | `defaultSection(tab, stats) = visibleSections(tab, stats).firstOrNull()`. Static reference (fully unlocked): Local→MyQuests, Internet→Arena, Events→ActiveEvents. Для guest: Local→MyQuests, Internet→Qualifications, Events→null (EmptyRoot). | ADR-0008 порядок + FR #20 progressive unlock | Low  |
| 2 | Navigator API surface                         | Единственный метод `goTo(Destination)`; back выражается как `goTo(Destination.Back)`                                  | Codex items 2 и pass-3 item 3: единый commonMain контракт           | Low  |
| 3 | Config state-saving mechanism                 | `@Serializable` + Decompose kotlinx-serialization (не `@Parcelize`)                                                  | Codex item 3: KMP-совместимость                                     | Low  |
| 4 | Stack model                                   | Decompose ChildStack (`active` + `backStack`), `backStack.isEmpty()` ↔ on root                                       | Codex item 4: единая семантика                                      | Low  |
| 5 | Re-tap domain/UI разделение                   | Domain → `RetapOutcome`; UI → scroll-to-top отдельным hook                                                           | Codex item 5: domain не знает про scrollOffset                      | Low  |
| 6 | Drawer ownership                              | Domain владеет `isDrawerOpen`; UI реактивно отражает                                                                 | Codex item 6: testable back-policy + programmatic open              | Low  |
| 7 | Section type-safe binding to tab              | `DrawerSection.tab: Tab` abstract val; cross-tab auto-switch                                                         | Codex item 7: запретить undefined behavior                          | Low  |
| 8 | Drawer animation                              | Стандартная slide `ModalNavigationDrawer`, не crossfade                                                              | Codex item 9: Material3 стандарт                                    | Low  |
| 9 | Design catalog как runtime screen             | `DesignCatalogScreen` Composable, не IDE preview                                                                     | Codex item 9: clarity                                               | Low  |
| 10 | Деф.-экраны placeholder-ов                    | Один generic `UnderConstructionScreen(title, icon)`                                                                  | USER decision Q2                                                    | Low  |
| 11 | Badges API                                    | `BadgeContent? = null` в NavBar/Drawer item                                                                          | Future-proof                                                        | Low  |
| 12 | Deep link                                     | Architectural hook `onDeepLink(deepLink: DeepLink)` (platform-neutral, Android `Intent` маппится в `apps/android-next`); паттерны в MVP пустые | Decompose + ADR-0008 + Codex v2 item A                              | Low  |
| 13 | Koin DI для UserStatsRepository (phase-01)    | `appShellModule { single<UserStatsRepository> { FirebaseUserStatsRepository(...) } }` (production impl создаётся в phase-01) | ADR-0009                                                            | Low  |
| 14 | Legacy drawable не переносим                  | Material Icons Extended достаточно                                                                                    | USER Q12                                                            | Low  |
| 15 | TopAppBar actions пустая в MVP                | Резерв под notifications/profile позже                                                                                | Нет данных                                                          | Low  |
| 16 | Design catalog visibility via BuildConfig.DEBUG| Compile-time flag (Gradle-generated)                                                                                 | Android standard                                                    | Low  |
| 17 | Progressive section unlock thresholds         | `requiredRoles` скопированы 1-в-1 из legacy `MenuList.kt`; пользователь сам корректирует после проверки              | Пользователь: «сделай как в легаси, потом я сам отредактирую»       | Medium (возможна ревизия порогов) |
| 18 | Unlock semantics                              | AND на map (все `(role, minLevel)` должны выполняться); `Role.USER → currentSkill`; остальные → `Qualification` поля | Legacy `SetItemMenu.getRoleLevel` 1-в-1                             | Low |
| 19 | Скрытая секция поведение                      | Не отрисовывается (нет lock-иконки, нет disabled)                                                                    | User Q15 (1A)                                                       | Low |
| 20 | Bottom-tabs scope                             | Tabs без requirement, только drawer sections                                                                         | User Q15 (2A)                                                       | Low |
| 21 | `ObserveAppShellStateUseCase` stale closure fix | User-approved domain signature change (ADR-LEAD-02, 2026-04-18). Walking Skeleton exception: use case stays in production pipeline, parameter changes `initialState: AppShellState` → `currentStateProvider: () -> AppShellState`. Phase-01 backend-dev updates 2 files in domain: `ObserveAppShellStateUseCase.kt` (signature + body) + `ObserveAppShellStateUseCaseTest.kt` (adapt 9 tests, lambda call site `invoke { initialState }`). | ADR-LEAD-02 | High (Walking Skeleton delta, user-approved) |

## Invariant Check (from ADRs)

| Invariant source | Impact                                                                                                                      | Decision |
|-------------------|-----------------------------------------------------------------------------------------------------------------------------|----------|
| ADR-0001 shared = KMP only | `RootComponent`, `AppShellComponent`, tab components, `Navigator`, `Destination`, `Config`, `UserStats`, `UserStatsRepository`, все use cases — в `commonMain`. | preserve |
| ADR-0001 rule 1 (android/* не ссылается на platform/*) | Не нарушается. | preserve |
| ADR-0001 rule 5 (новая фича = leaf-модули) | Используем существующие пустые модули. | preserve |
| ADR-0008 Single-Activity + Decompose | Следуем. | preserve |
| ADR-0008 4 bottom-tabs | Следуем. | preserve |
| ADR-0008 per-tab drawer | Следуем. | preserve |
| ADR-0008 Shop pager (Shop/Referrals/Donate) | **Изменяется**: Shop = один экран. Требует update ADR-0008. | **modify** |
| ADR-0008 Navigator interface | Реализуем минимальный Navigator (единый `goTo(Destination)`). | preserve |
| ADR-0008 deep links через Config | Hook `onDeepLink(deepLink: DeepLink)` (platform-neutral) + `Navigator.goTo(Destination)`. | preserve (deferred impl) |
| ADR-0008 Android state-saving через @Parcelize | **Изменяется**: используем kotlinx-serialization Decompose 3.x. Update ADR-0008 если есть упоминание @Parcelize. | **modify** |
| ADR-0009 Koin DI | `UserStatsRepository` через Koin (phase-01 production impl). | preserve |
| ADR-0010 Material3 + dark-only | Следуем. | preserve |
| ADR-0010 Elevation=0 + 1dp stroke + 16dp corners | Следуем. NavigationBar tonal elevation — исключение. | preserve |
| ADR-0010 Брендовая палитра | darkColorScheme как в ADR. | preserve |
| ADR-0010 Design catalog | `DesignCatalogScreen` runtime в drawer-footer под debug. | preserve |

**Critical follow-ups** (не в scope этой фичи, но tracked):
- Обновить ADR-0008: убрать описание Shop pager; заменить `@Parcelize` на kotlinx-serialization.

## Constraints (from CLAUDE.md / ADRs)

- Модули добавляются только через `// layered-scaffold:start` / `// layered-scaffold:end` маркеры в `settings.gradle.kts`. Нужные модули уже подключены.
- Все deps через `gradle/libs.versions.toml`.
- Convention plugins: `schoolquiz.kmp.library` (shared), `schoolquiz.android.library` (android, platform), `schoolquiz.android.application` (apps).
- JDK 17, Kotlin 1.9.22, AGP 8.11.0, compileSdk 34, minSdk 26.
- Gradle configuration cache включён.
- Detekt maxIssues=10, MaxLineLength=120; Ktlint обязательно.

## Acceptance Criteria

### Functional AC

1. [ ] GIVEN cold start WHEN приложение запускается AND MainActivity.onCreate завершился THEN виден AppShellScreen: TopAppBar «Мои квесты» + hamburger icon слева; NavigationBar с 4 вкладками, активна «Локальная»; контент = `UnderConstructionScreen("Мои квесты")`.

2. [ ] GIVEN активная вкладка кроме Shop, drawer closed WHEN tap на hamburger THEN `isDrawerOpen == true` в domain AND drawer slide-анимирован в открытое состояние (стандартный Material3); header показывает `UserStats.guest()` данные (nickname="Гость", streakDays=0, hearts=5/5, gold=0, premium=false).

3. [ ] GIVEN Shop tab активна WHEN inspection TopAppBar THEN hamburger icon НЕ отрисован; WHEN edge swipe слева THEN drawer НЕ открывается AND `isDrawerOpen` остаётся false.

4. [ ] GIVEN drawer открыт WHEN tap на новой section в том же tab THEN drawer slide-анимирован закрыт; `activeSection` обновлён на новый; `childStack.active` = root новой section; `backStack = []`; TopAppBar title обновлён.

5. [ ] GIVEN Локальная/Мои квесты, user с `currentSkill >= TEACHINGS.first = 3000` (Arena unlocked), пустой Internet TabState WHEN `goTo(SwitchTab(INTERNET))` THEN `activeTab = INTERNET`, `activeSection = defaultSection(INTERNET, stats) = InternetSection.Arena` (firstVisible при unlocked), UI показывает `UnderConstructionScreen("Арена")` с crossfade 300ms. *Для guest (`currentSkill=0`) поведение описано в Journey 14b / AC 23b — firstVisible = Qualifications.*

6. [ ] GIVEN Интернет/Профиль (backStack=[]) WHEN переключить на Локальную → обратно на Интернет THEN UI показывает Профиль (не Арена); `activeSection == InternetSection.Profile`.

7. [ ] GIVEN любая вкладка, `backStack.isNotEmpty()` WHEN re-tap активной вкладки THEN domain возвращает `POP_TO_ROOT`; `backStack = []`; UI показывает root-экран вкладки.

8. [ ] GIVEN любая вкладка, `backStack.isEmpty()`, UI scrollable content scrolled down WHEN re-tap активной вкладки THEN domain возвращает `NO_OP`; UI scroll-to-top hook анимирует scroll к началу.

9. [ ] GIVEN любая вкладка, `backStack.isEmpty()`, scrollOffset=0 WHEN re-tap активной вкладки THEN domain `NO_OP`; UI ничего не делает.

10. [ ] GIVEN drawer открыт WHEN systemBack (physical/gesture) THEN `isDrawerOpen == false`; backStack не меняется; activeTab не меняется.

11. [ ] GIVEN Интернет/Арена, backStack=[], drawer closed WHEN systemBack THEN `activeTab = LOCAL`, Local TabState restored (activeSection=MyQuests).

12. [ ] GIVEN Локальная/Мои квесты, backStack=[], drawer closed WHEN systemBack THEN `RootEvent.SystemBack` emit-ится в `rootComponent.events` flow; UI вызывает `activity.finish()` / `moveTaskToBack()`.

13. [ ] GIVEN любой placeholder-экран WHEN он рендерится THEN виден icon + title соответствующей section + subtitle «Скоро здесь будет...»; layout центрирован.

14. [ ] GIVEN MaterialTheme применён через `SchoolQuizTheme` в `MainActivity.setContent { }` THEN background `#000000`, primary `#4285F4`, secondary `#FFD700`, surface `#242429`; shapes.large = 16dp; все карточки с 1dp outline stroke; elevation = 0 для карточек (NavigationBar исключение).

15. [ ] GIVEN debug build (`BuildConfig.DEBUG == true`) WHEN drawer открыт THEN footer содержит пункт «Design catalog» (элемент `DrawerFooterAction.DesignCatalog`); WHEN tap → `navigator.goTo(Destination.OpenDesignCatalog)` → domain применяет: `activeTab = LOCAL`, `activeSection = null` (потому что `DesignCatalog` не `LocalSection`), `childStack.active = LocalConfig.DesignCatalogRoot`, `backStack = []`, drawer закрыт; `AppShellScreen` рендерит runtime `DesignCatalogScreen`; back применяет стандартный FSM (step 3: `backStack.isEmpty()` + `activeTab == LOCAL` → emit `RootEvent.SystemBack`).

16. [ ] GIVEN release build (`BuildConfig.DEBUG == false`) THEN пункт «Design catalog» в drawer footer НЕ виден; если каким-то образом активировали `LocalConfig.DesignCatalogRoot` (например через corrupted state) — `AppShellScreen` рендерит `UnderConstructionScreen("Недоступно")`.

17. [ ] GIVEN tab switch, section switch, или stack push/pop WHEN переход происходит THEN виден crossfade ~300ms в контентной области.

18. [ ] GIVEN drawer open/close WHEN переход происходит THEN видна стандартная slide-анимация `ModalNavigationDrawer` (не crossfade).

19. [ ] GIVEN **все requirements выполнены** (`currentSkill >= PLAYER.first` + `qualification.tester/moderator/admin/developer >= 100`) WHEN drawer отрендерен на каждой вкладке THEN **полный каталог** section видим: LOCAL содержит 3 (MyQuests, MyCourses, Settings); INTERNET — 6 (Arena, Catalog, Qualifications, Profile, Social, Leaderboard); EVENTS — 2 (ActiveEvents, Minigames). **Порядок** — declaration order. **Это полный каталог при всех unlock; при partial unlock — см. AC 23a-g для конкретных unlock states**.

20. [ ] GIVEN `NavigationBarItem` / `NavigationDrawerItem` code inspection THEN каждый API имеет параметр `badge: BadgeContent? = null`.

21. [ ] GIVEN cross-tab deep link WHEN `navigator.goTo(SelectSection(InternetSection.Profile))` из Local THEN `activeTab = INTERNET`, `activeSection = Profile`, childStack.active = ProfileRoot.

22. [ ] GIVEN drawer open, активная section WHEN tap по той же активной section THEN `isDrawerOpen = false`; activeSection и childStack не меняются.

23. [ ] GIVEN programmatic `navigator.goTo(OpenDrawer)` на не-Shop tab WHEN invocation THEN `isDrawerOpen = true`, drawer открывается в UI.

23a. [ ] GIVEN `stats = UserStats.guest()`, активная вкладка Локальная, drawer open THEN drawer показывает только 3 пункта: «Мои квесты», «Мои курсы», «Настройки» (debug-build также показывает «Design catalog» в footer — не в основном списке).

23b. [ ] GIVEN `stats = UserStats.guest()`, активная вкладка Интернет, drawer open THEN drawer показывает только 2 пункта в порядке declaration: «Квалификации», «Профиль». Арена/Каталог/Социалка/Лидерборды НЕ отрисованы.

23c. [ ] GIVEN `stats = UserStats.guest()`, активная вкладка События, drawer open THEN main section list drawer-а пустой (ни ActiveEvents, ни Minigames не видны при guest stats); footer всегда содержит actions из `visibleFooterActions(isDebugBuild)` + version label (debug и release оба); debug: [Design catalog, О приложении] + version; release: [О приложении] + version. TopAppBar title = «События» (fallback когда `activeSection = null`).

23d. [ ] GIVEN `stats.currentSkill = 3000`, вкладка Интернет, drawer open THEN drawer показывает 5 пунктов: Арена, Каталог, Квалификации, Профиль, Лидерборды (Социалка ещё скрыта).

23e. [ ] GIVEN `stats.currentSkill = 10_000`, вкладка Интернет THEN drawer показывает все 6 пунктов.

23f. [ ] GIVEN `stats.qualification = Qualification(tester=100, moderator=100, sponsor=0, translator=0, admin=100, developer=100)`, вкладка События THEN drawer показывает только «Активные события» (Minigames скрыт — нужен USER=PLAYER).

23g. [ ] GIVEN deep link `navigator.goTo(SelectSection(InternetSection.Arena))` при `stats.currentSkill = 0` THEN state не меняется (domain guard блокирует переход на невидимую section).

### Architectural / KMP AC

24. [ ] GIVEN `./gradlew :shared:feature:app-shell:domain:compileCommonMainKotlinMetadata` THEN сборка проходит без ошибок AND no references to android.*, @Parcelize, Compose в common source.

25. [ ] GIVEN `./gradlew :shared:feature:app-shell:domain:jvmTest` THEN все **229** domain tests (45 scenarios 1-45 + State Matrix FSM coverage + Primary User Journeys + use case tests через in-memory fakes) пройдут зелёными.

25a. [ ] **Phase-01 AC** (не spec): GIVEN `./gradlew :shared:feature:app-shell:data:jvmTest` THEN все data-module integration tests (D1-D3) пройдут зелёными. На spec-этапе этот AC не применяется — data module пустой.

26. [ ] GIVEN `android/feature/*/presentation` модули (в будущем) WHEN они импортируют `Navigator` THEN он из `shared/feature/app-shell/domain`; Decompose НЕ появляется в их dependencies (проверка через `./gradlew :android:feature:X:presentation:dependencies`).

27. [ ] GIVEN Koin `appShellModule` (phase-01 wiring) WHEN `get<UserStatsRepository>()` THEN возвращается production implementation (Firebase-backed); integration test подтверждает через isolated Koin с test modules.

### Build / runtime AC

28. [ ] GIVEN `./gradlew :apps:android-next:assembleDebug` THEN BUILD SUCCESSFUL без ошибок и warnings сверх проектной базы.

29. [ ] GIVEN установленный APK, manual launch на Android 8.0+ device/эмулятор THEN приложение запускается без crash; AppShellScreen виден; AC #1 выполняется.

30. [ ] GIVEN `./gradlew detekt ktlintCheck` на изменённых модулях THEN оба проходят без новых suppressions.

## Open Questions

Нет открытых вопросов для pipeline.

> **Примечание к историческому логу ниже:** упоминания старых терминов (`UserStatsProvider`, `FakeUserStatsProvider`, `LocalSection.DesignCatalog`, статический `Internet→Arena` default, `@Parcelize`, `flowOf`) — это **описание того, что было заменено в ходе refactor**. Это НЕ активные концепции. Канонические термины: `UserStatsRepository`, `FakeUserStatsRepository` (в commonTest), `DrawerFooterAction.DesignCatalog`, `defaultSection(tab, stats)` formula, kotlinx-serialization, `MutableStateFlow`.

Spec прошёл **10 проходов** Codex cross-review + Variant Y Walking Skeleton refactor:

- **Pass 1-2** (initial): 9+4 дыр → закрыты через [DELEGATED] + [USER DECIDED] Q14
- **Pass 3** (после progressive unlock FR #20 добавления): 8 дыр — 3 архитектурных (default section formula, EmptyRoot sentinel, DesignCatalog classification) + 5 major (currentStats source, rootOf mapping, missing scenarios, downstream notes, AC #19 consistency) → все закрыты
- **Pass 4**: 5 editorial рассинхронов (UserStatsProvider→Repository terminology в FR/NFR/Scope/ADR/AC; DesignCatalog в FR #16-17; Internet→Arena остаточные defaults; corrupted fallback через `initialTabState`; Journey 14c vs AC 23c согласование) → все закрыты
- **Pass 5**: 5 мелких editorial (baseline числа 45/229, D1-D3 согласование, `visibleFooterActions` signature, Journey 14c title, FakeUserStatsProvider хвост в notes) → все закрыты
- **Pass 6-8**: финальные editorial (counter mismatches, дублирующий заголовок, устаревшая review-заметка) → закрыты

Все расхождения были editorial/unification — архитектурные решения стабильны с Q14 + Q15 (progressive unlock) + Variant Y refactor.

## Notes for Downstream Phases

### For `/feature-research`
- Критические поиски: Decompose 3.x API + state-saving через kotlinx-serialization; `material-icons-extended` в libs.versions.toml; plugin kotlin-serialization в convention plugin; Koin startup в apps/android-next.
- Прочитать legacy `SetItemMenu*.kt` только для наименований пунктов — НЕ для архитектуры.
- Verify empty modules: `shared/feature/app-shell/{domain,data}`, `android/feature/app-shell/presentation`, `android/core/navigation`, `android/core/designsystem`.

### For `/feature-design`
- Слои: (1) DS foundation (theme/color/shape/type), (2) DS wrappers + DesignCatalogScreen, (3) Decompose integration layer (RootComponent, AppShellComponent wrapping domain state в Decompose ChildStack; `@Serializable` wrappers над Config sealed для state-saving), (4) Tab components (4 Decompose components с ChildStack per tab), (5) AppShellScreen Composable (Scaffold + TopAppBar + NavigationBar + ModalNavigationDrawer + per-tab renderer; drawer header consuming `AppShellState.userStats`), (6) Production `UserStatsRepository` implementation (Firebase-backed) + Koin binding — **в phase-01 data module**, не design-этап, (7) UnderConstructionScreen + registration всех routes, (8) Back-policy FSM + Re-tap FSM + SystemBack event UI hooks, (9) MainActivity wiring + Koin startup + debug-only DesignCatalog footer visibility.
- KMP-границы: **domain уже в commonMain** (Variant Y: pure core + UserStatsRepository interface + use cases + FakeUserStatsRepository в commonTest). UI из (5, 7) — в androidMain presentation module. MainActivity wiring — в app-entrypoint. Production UserStatsRepository impl из (6) — в shared/feature/app-shell/data (commonMain или androidMain в зависимости от Firebase SDK support).

### For `/feature-plan`

Walking Skeleton режим: **Variant Y** (полный domain сгенерирован на spec-этапе). Уже готово в `shared/feature/app-shell/domain/`:
- Pure core (`model/`, `state/`, `logic/`) — все типы, transitions, visibility functions
- Repository interfaces (`repository/UserStatsRepository.kt`) — domain boundary
- Use cases (`use_case/`) — `InitializeAppShellUseCase`, `NavigateUseCase`, `HandleBackUseCase`, `OnTabRetapUseCase`, `ObserveAppShellStateUseCase`
- In-memory fakes (`test/.../fake/FakeUserStatsRepository.kt`) — для тестов use cases
- 229 JVM тестов зелёные

**Phase-01 = adapter-only integration** (Variant Y):
- Backend-dev реализует production `UserStatsRepository` (Firebase-backed or Room+Firebase combo) в `shared/feature/app-shell/data/`
- Koin module (`appShellModule`) связывает production repository с domain interface
- **Backend-dev НЕ пишет use cases или новые repository interfaces** — они готовы из spec
- DAO mappers если нужен persistence cache для UserStats

**Другие phases** (indicative):
- DS foundation (theme/colors/shapes/typography)
- DS wrappers + DesignCatalogScreen
- Decompose integration layer (`RootComponent`, `AppShellComponent`, `@Serializable` wrappers над Config sealed): wraps pure domain state в Decompose ChildStack API; переводит domain transitions в StackNavigation calls
- Tab components (4 Decompose components с ChildStack per tab)
- AppShellScreen Composable + NavigationBar + ModalNavigationDrawer + TopAppBar + drawer header consuming `AppShellState.userStats`
- Placeholder (UnderConstructionScreen) + route registration
- Back-policy/Re-tap UI-hooks + SystemBack handling + scroll-to-top UI-hook
- MainActivity wiring + Koin startup + debug-only DesignCatalog footer visibility

Все 45 domain scenarios уже покрыты на spec-этапе (**229 JVM tests зелёные**: pure core + use cases через in-memory fakes + integration journeys + FSM matrix + table-driven mapping tests). D1-D3 data scenarios принадлежат phase-01 integration (production repository + Firebase/Room round-trip). В phase-01 добавятся: data integration tests (D1-D3), Decompose-wrapper tests, UI/Compose tests.

### For ADR maintenance
- После реализации фичи: обновить ADR-0008 (убрать Shop pager, заменить @Parcelize на kotlinx-serialization в notes).
