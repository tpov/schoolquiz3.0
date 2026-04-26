---
date: 2026-04-25
feature: quizzes-screen
type: new-feature
commit: 7c52c200
---

# Feature Specification: Quizzes Screen — Hierarchical Drill-Down Navigation

## Source

- Описание фичи: «будем делать экран квизов, это экран который отображает список квестов и всю иерархию, так же вверху нужно отобразить путь в котором мы находимся, элементы уже созданы, по сути как в легаси сделать»
- Type: `new-feature` (новый presentation-слой над уже существующими data/domain слоями quest/section/theme/lesson)

Фича — порт легасного `QuizFragment` (`legacy/common/src/main/java/com/tpov/common/presentation/quiz/QuizFragment.kt`) на новый стек: Decompose Components + Compose. Один универсальный паттерн drill-down работающий на 4 уровнях иерархии (Quest → Section → Theme → Lesson) с breadcrumb-путём наверху.

## Requirements

### Functional Requirements

#### Архитектура drill-down

1. **Decompose `ChildStack`** — на каждый уровень иерархии отдельный Component: `QuestListComponent`, `SectionListComponent`, `ThemeListComponent`, `LessonListComponent`. Push/pop через `StackNavigation` (`pushNew()` — рекомендованный safe вариант для button-tap; `push()` помечен `@DelicateDecomposeApi` в Decompose 3.1.0). System back автоматически работает через интеграцию Decompose с Android. — [USER DECIDED] выбран вариант "Decompose ChildStack (Recommended)" в Phase 2 dialogue. **[ADDED IN RESEARCH: внутренний ChildStack в новом `QuizzesComponent`, который владеет своим `StackNavigation<QuizzesConfig>`. НЕ расширяет domain `NavStack` FSM в `shared/feature/app-shell/domain/` — изолирует фичу от app-shell architecture. Push из TODO callbacks `HomeQuestsComponent.onCatalogClick` и нового `MyQuestsComponent.onQuestClick` метода — см. п.30].**

2. **Глубина навигации — до Lesson включительно**. Drill-down останавливается на уровне списка уроков. Тап на урок ведёт на placeholder-экран урока (внешняя точка). Quest → Section → Theme → Lesson — внутри этой фичи; Lesson → детали урока — за её пределами. — [USER DECIDED] вариант "До Lesson включительно (Recommended)"

3. **Тап на Lesson → placeholder-экран** с заголовком урока и текстом «Прохождение урока будет добавлено позже». Placeholder — отдельный `LessonPlaceholderComponent` в этом же ChildStack, чтобы будущая фича прохождения урока заменила его без переработки навигации. — [USER DECIDED] вариант "Placeholder-экран урока"

#### Точки входа

4. **Из `HomeQuestsScreen`** — тап на каталог (`CatalogGrid` item) → push `QuestListComponent(catalogId)`. Сейчас в `HomeQuestsComponent.onCatalogClick` (`android/feature/quest/presentation/.../HomeQuestsComponent.kt:17`) стоит TODO — он подключается к новой навигации. — [USER DECIDED]

5. **Из `MyQuestsScreen`** — тап на элемент `QuestCard` → push `SectionListComponent(questId)`. Сейчас в `MyQuestsScreen.kt:87` стоит TODO `// TODO: open quest detail` — подключается. Breadcrumb в этом случае начинается с уровня Quest (т.к. user уже выбрал каталог через `CatalogSpinner`, в путь входит `selectedCatalog.name > quest.title`). — [USER DECIDED] вариант "Да, тап на квест → экран секций (Recommended)". **[ADDED IN RESEARCH: `QuestDisplayItem.kt:14-20` НЕ содержит `catalogId` — расширяется новым полем `catalogId: CatalogId` для resolve catalog name из `state.catalogs` (см. spec п.305a). `state.selectedCatalogId` может быть null (фильтр «все каталоги» в `CatalogSpinner`) — catalog name берётся из `quest.catalogId`, не из `state.selectedCatalogId`. Это требует расширить `QuestDisplayItem` + mapping в `MyQuestsUiState`.] [ADDED IN RESEARCH: добавить `MyQuestsComponent.onQuestClick(id: QuestId, catalogId: CatalogId)` метод — interface не имеет навигационных методов сейчас (`MyQuestsComponent.kt`).]**

#### Breadcrumb-путь

6. **Сверху каждого уровня** — горизонтальная полоса `BreadcrumbBar` с путём `"Каталог > Квест > Секция > Тема"`. Сегменты разделяются символом `>`. Каждый сегмент — текстовая ссылка с цветом `MaterialTheme.colorScheme.primary` (или эквивалентом из brand-палитры). — [USER DECIDED] формат соответствует легасному `QuizFragment.initPath()` (`legacy/common/.../QuizFragment.kt:55-72`)

7. **Тап на сегмент breadcrumb → pop ChildStack до этого уровня** (включая удаление всех более глубоких компонентов из стека). Текущий (последний) сегмент некликабелен. — [USER DECIDED] вариант "Кликабельный — pop до уровня (Recommended)"

8. **Длинные заголовки в сегменте** обрезаются через `TextOverflow.Ellipsis` после `maxLines = 1`. Полный заголовок виден через тап (drill-down) или контекстное действие. Конкретное оформление (font, padding, разделитель size) — `[DELEGATED]` design-фазе.

#### Карточки и списки

9. **`QuestCard`** — переиспользуется существующий компонент (`android/core/designsystem/.../QuestCard.kt`) для уровня квестов. Содержит заголовок, картинку (или placeholder-иконку), `StarRating`. — [USER DECIDED] компонент уже создан в `home-and-my-quests` фиче

10. **`HierarchyItemCard`** — новый компонент в `android/core/designsystem/components/`, переиспользуется для Section/Theme/Lesson. Поля:
    - `title: String` — основной заголовок (`titleMedium`, `maxLines = 2`, ellipsis)
    - `orderLabel: String?` — `"1."`, `"2."` и т.д. из `Section.order`/`Theme.order`/`Lesson.order` для визуальной нумерации (если null — не показывать)
    - `subtitleCount: String?` — параметр оставляем для будущего (counts «3 темы», «5 уроков»). **В MVP всегда `null`** для всех уровней — counts требовали бы дополнительные queries/precomputed fields, отложено. Research проверит наличие готовых count APIs.
    - `onClick: () -> Unit`
    - `onLongClick: (() -> Unit)?` — на Section/Theme/Lesson long-press пока не активен (передаём `null`)

    Без картинки, без рейтинга. Декоративные элементы из легасного `activity_quiz_item.xml` (gradient-индикаторы, swipe-кнопки, скрытые `imShare`/`imDeleteQuiz`) **не переносятся**. — [DELEGATED]

11. **Список** — `LazyColumn` под `BreadcrumbBar`. Padding между карточками — `8.dp` вертикально, `16.dp` горизонтально (соответствует `MyQuestsScreen.kt:91`). Empty state — текст `"Нет {уровень}"` (`"Нет квестов"`, `"Нет секций"`, `"Нет тем"`, `"Нет уроков"`) по центру. Loading — `CircularProgressIndicator`. Конкретный typography — `[DELEGATED]` design.

#### Long-press menu

12. **Long-press на `QuestCard`** → выпадающее меню (`DropdownMenu` from Material3) с одним пунктом «Поделиться» в MVP. Меню привязано к нажатой карточке (anchor). Тап вне меню — закрывает его. **Только в новом `QuestListComponent`** (drill-down с HomeQuests). На существующем `MyQuestsScreen.QuestCard` long-press пока не активен (existing UI не модифицируется в этой фиче). — [USER DECIDED] меню работает на квесте, паттерн как в легасном `QuizActivityAdapter`; user clarification «atm нету меню» в MyQuests

13. **Меню расширяется будущими фичами** — Edit, Delete, Send to arena, и т.д. добавляют свои пункты в этот же `DropdownMenu`. Архитектура должна предусматривать расширение через список действий (например `List<MenuAction>` параметр компонента). — [USER DECIDED]

14. **Long-press на Section / Theme / Lesson** — пока без действий (long-press игнорируется). Когда появятся CRUD-фичи для этих уровней, они добавят свои `onLongClick` обработчики. — [USER DECIDED]

15. **«Поделиться» — system Intent** `Intent.ACTION_SEND` с `type = "text/plain"`. Текст: `"Квест «{quest.title}» — {appName}"`. Без attached URL/deep-link в MVP (пока нет deep-linking инфраструктуры). — [DELEGATED]

15a. **Share error recovery** — `ActivityNotFoundException` (нет приложения для обработки `ACTION_SEND`) ловится и логируется, без UI-уведомлений. Меню всегда закрывается после тапа. Navigation state не меняется. — [DELEGATED]

#### Состояния и edge cases

16. **Empty state на любом уровне** — если `Flow` отдаёт пустой список, показываем placeholder «Нет {level}» по центру. Без явных «Sync now» / «Pull to refresh» кнопок. Cascade-sync продолжает работать в фоне через существующий `home-and-my-quests` infrastructure. — [DELEGATED]

17. **Архивирование текущего уровня в фоне** — если sync архивирует отображаемый Quest/Section/Theme/Lesson, `Flow` отдаёт пустой список → показываем empty state. Без auto-pop ChildStack, без toast «Раздел удалён». User сам уходит через back. — [USER DECIDED] "просто обработка ошибки и все"

18. **Logout не релевантен** — фича работает с локальной БД (Room), `currentUid` (anonymous Firebase Auth) всегда есть. Logout/login — забота app-shell. Эта фича не подписывается на auth events. — [USER DECIDED] «логаут влияет только на вкладку интернет»

19. **Offline / no internet** — Room read-only flows работают offline. Если данных нет (fresh install + offline) → empty state. Без явного оффлайн-индикатора. — [DELEGATED]

20. **Process death + breadcrumb titles** — Decompose `ChildStack` сериализуется через `StateKeeper`. Configurations хранят `id + initialTitle` (snapshot захваченный на момент push). **Breadcrumb titles — frozen**, читаются один раз при push (одноразовое чтение перед отрисовкой). Если sync переименует элемент в фоне — breadcrumb остаётся старым до следующего входа в drill-down. Live observers на parent chain не делаем. — [USER DECIDED] «одноразовое чтение перед отрисовкой». **[ADDED IN RESEARCH: новый quizzes ChildStack использует `serializer = QuizzesConfig.serializer()` (kotlinx-serialization). Это единственный стек в проекте с включённым serializer — все existing tabs (`LocalTabComponent.kt:22`, `ShopTabComponent.kt:22`, `EventsTabComponent.kt:22`, `InternetTabComponent.kt:22`) используют `serializer = null`. Decompose 3.1.0 не имеет stable `saveable` delegate (только с 3.2.0); используется manual `consume`/`register` pattern если нужен дополнительный component-level state. `kotlinx-serialization` plugin требуется в новом presentation module.]**

21. **Configuration changes (rotation)** — Decompose сохраняет state через `instanceKeeper`. Components переживают rotation без переподписки на Flow. — [DELEGATED]

#### Данные

22. **Источник данных** — существующие domain repositories через `observeByX` методы:
    - Quests of catalog → `QuestRepository.observeByCatalog(catalogId, shelf)` **— метод НЕ существует** (verified `QuestRepository.kt:34-69`, grep confirmed); создаётся в design/implementation phase. Sort: `lastModifiedAt DESC` (см. п.23)
    - Sections of quest → `SectionRepository.observeByQuest(questId)` (`shared/feature/section/domain/.../SectionRepository.kt:24`); sort `order ASC` (`SectionDao.kt:13-18`)
    - Themes of section → `ThemeRepository.observeBySection(sectionId)` (`shared/feature/theme/domain/.../ThemeRepository.kt:21`); sort `order ASC` (`ThemeDao.kt:13`)
    - Lessons of theme → `LessonRepository.observeByTheme(themeId)` (`shared/feature/lesson/domain/.../LessonRepository.kt:21`); sort `order ASC` (`LessonDao.kt:13`)

23. **Фильтрация для уровня квестов** в catalog-view:
    - **MyQuests entry**: пользователь уже на уровне Quest в `MyQuestsScreen`. При тапе сразу открывается `SectionListComponent(questId)` — фильтрация на уровне Quest не применяется в этой фиче (existing behavior MyQuestsScreen остаётся). MyQuests = локально созданные пользователем квесты (см. user clarification).
    - **HomeQuests entry**: показывает **только public** квесты этого каталога — `visibleOn contains "home"` AND `archived=false`. Свои локальные квесты пользователя сюда **не попадают** (для них есть MyQuests). distinct by id, sorted by `lastModifiedAt DESC` **[ADDED IN RESEARCH: `Quest`/`QuestEntity` НЕ имеют поля `order` (verified `Quest.kt:30-93`, `QuestEntity.kt:24-38`); existing `QuestDao.observeMyQuests`/`observeByShelf` сортируют `lastModifiedAt DESC` (`QuestDao.kt:13-34`). Новый `observeByCatalog` наследует ту же конвенцию.]**. — [USER DECIDED] «мои квесты это все что локально мы создали а хоум квесты это то что передал сервер» (концептуальное разделение public vs local)

24. **Quest model split (draft/public) — Out of Scope** этой фичи. В будущей Edit-фиче добавится разделение `Quest.status: DRAFT | PUBLISHED` или эквивалент: автор видит свой draft через MyQuests, другие — только PUBLISHED. Сейчас drill-down работает с unified `Quest` моделью; когда split появится — добавится фильтр в DAO `WHERE status = PUBLISHED OR authorUid = currentUid`, навигация продолжит работать без изменений. — [USER DECIDED] «по поводу второго ответа я все таки разделю на общедоступные и на локальные»

#### DI

25. **Koin module** — `QuizzesPresentationModule` в `android/feature/quizzes-screen/presentation/.../di/`. Регистрирует `factory { ... }` для каждого Component (Quest/Section/Theme/Lesson list + LessonPlaceholder). Зависимости — repository interfaces из shared domain. — [DELEGATED]

26. **Регистрация в `apps/android-next` AppApplication.kt** — модуль добавляется в startKoin список модулей. Детали — design phase. — [DELEGATED]

### Non-Functional Requirements

1. **Тестирование Components** — JVM unit-тесты для каждого `DefaultXxxListComponent` через `FakeXxxRepository` (existing fakes в `shared/feature/*/domain/src/commonTest/.../fake/`). Coverage: empty → loaded, archived disappears, breadcrumb pop semantics. — [DELEGATED]

2. **Compose UI тесты** — instrumented тесты для `BreadcrumbBar` (тап на сегмент → callback fired with correct level) и `HierarchyItemCard` (long-press fires callback when set). — [DELEGATED]

3. **Никакой Activity/Fragment** — фича строится на Decompose Components + Compose (`AppShellScreen` уже host-Compose-screen для всех табов). Соответствует existing pattern `home-and-my-quests`. — [USER DECIDED]

4. **Brand consistency** — все цвета через `MaterialTheme.colorScheme`, никаких hardcoded `Color(0xFF...)`. Соответствует `BrandComponentsInvariantsTest`. — [USER DECIDED]

5. **Domain layer purity** — никакой Android/SDK импорт в shared/feature/*/domain (см. invariant 1). Эта фича не добавляет ничего в domain — только presentation. — [USER DECIDED]

## Scope

### In Scope

- Новый Android module: `android/feature/quizzes-screen/presentation/` с Decompose Components на 4 уровня + LessonPlaceholder
- Новый компонент в designsystem: `HierarchyItemCard` (Section/Theme/Lesson)
- Новый компонент в designsystem: `BreadcrumbBar`
- Подключение `HomeQuestsComponent.onCatalogClick` → push на новый stack
- Подключение `MyQuestsScreen.QuestCard.onClick` (TODO в `MyQuestsScreen.kt:88`) → push на новый stack
- Long-press меню «Поделиться» на `QuestCard` через `DropdownMenu`
- System share через `Intent.ACTION_SEND` с текстом-описанием квеста
- Empty / loading / archived-during-nav обработка
- DI module + регистрация в `AppApplication.kt`
- JVM unit-тесты + Compose UI тесты
- Возможное расширение `QuestRepository` методом `observeByCatalog(catalogId)` (если research подтвердит отсутствие)

### Explicitly Out of Scope

- **Edit квеста** — отдельный экран редактирования. Когда сделаем — добавляется пункт «Редактировать» в long-press меню. — [USER DECIDED]
- **Delete элементов** — серверный archived=true write через Cloud Function. Отдельная фича CRUD. — [USER DECIDED]
- **Send to arena** — публикация квеста (visibleOn flag toggle + server roundtrip). Отдельная фича. — [USER DECIDED]
- **Прохождение урока** — экран показа вопросов внутри урока. Сейчас только placeholder. Отдельная фича.
- **Прохождение Question (leaf)** — соответственно out of scope.
- **Quest model split (DRAFT/PUBLISHED)** — архитектурное решение для будущей Edit-фичи. Зафиксировано в `User Decisions` как «принято решение разделить, реализация — позже». — [USER DECIDED]
- **Pull-to-refresh** — sync triggers per-screen. Cascade syncs работает в фоне через `home-and-my-quests` infrastructure. — [DELEGATED]
- **Deep-linking / share URL** — для share Intent передаём только текст без URL. Deep-linking — отдельная фича. — [DELEGATED]
- **Question как leaf-уровень** — не дрилаемся до Question, останавливаемся на Lesson. — [USER DECIDED]
- **Поиск / фильтр / сортировка** — список идёт в порядке `order ASC` (existing repository behavior). Любые UI фильтры — отдельная фича.

## User Decisions

| # | Question | Answer | Impact on Design |
|---|----------|--------|-----------------|
| 1 | Архитектура drill-down | Decompose ChildStack — **внутренний** в `QuizzesComponent` (не расширяет domain `NavStack` FSM, не расширяет `LocalConfig`) | Изолирует фичу от app-shell architecture; push из TODO callbacks `HomeQuestsComponent.onCatalogClick`/`MyQuestsComponent.onQuestClick`; `pushNew()` API |
| 2 | Глубина | До Lesson включительно | Question как leaf — out of scope, lesson tap → placeholder |
| 3 | Entry points | HomeQuests (тап каталога) + MyQuests (тап квеста) | Подключение TODO в `HomeQuestsComponent.onCatalogClick` и `MyQuestsScreen.kt:88` |
| 4 | Breadcrumb behavior | Кликабельный — pop до уровня | StackNavigation.popTo(level) на тап сегмента |
| 5 | Author actions UX | Long-press меню (как в легаси) | DropdownMenu по long-press на `QuestCard`; в MVP только Share |
| 6 | Scope split | Только навигация и Share | Edit/Delete/SendToArena — отдельные фичи позже |
| 7 | Cards | HierarchyItemCard (новый) для Section/Theme/Lesson, QuestCard для квестов | Один универсальный card-компонент для 3 уровней |
| 8 | Lesson tap | Placeholder-экран | Отдельный LessonPlaceholderComponent в ChildStack |
| 9 | Archived during nav | Empty state, без auto-pop | Flow отдаёт пусто → стандартный empty state |
| 10 | Logout | N/A для фичи | Не подписываемся на auth, currentUid всегда есть |
| 11 | Quest split (draft/public) | Out of scope (для будущей Edit) | Сейчас unified модель; future filter в DAO |
| 12 | Walking Skeleton | N/A — pure UI feature | Skip Phase 3.8, нет business rules / domain logic |
| 13 | HomeQuests filter | Public-only (visibleOn contains "home", archived=false) | «MyQuests = локальные мои, HomeQuests = что передал сервер» |
| 14 | Breadcrumb titles after rename | Frozen (одноразовое чтение перед отрисовкой) | Простой UX, без live observers parent chain |
| 15 | MyQuestsScreen long-press | Не модифицируем existing UI | «atm нету меню» — long-press только в новом QuestListComponent |
| 16 | Archived parent → дети пустеют | Полагаемся на existing DAO archived=0 + Invariant B (server cascading) | Без дополнительных observers parent chain |

## Server-Side Context

**N/A** — фича читает только локальные данные через существующие repository observers. Sync infrastructure уже реализована в `home-and-my-quests` (cascading sync по `lastModifiedAt` через 6 уровней). Эта фича — pure presentation layer.

Server-side изменения, которые могут понадобиться (но **out of scope** этой фичи):
- Если research подтвердит отсутствие `QuestRepository.observeByCatalog(catalogId)` — это data layer change, не server change.
- Quest split (draft/public) и Delete actions потребуют security rules + Cloud Functions, но это для будущих CRUD-фич.

## Data / Repository Contract

**Business Domain Contract = N/A** (см. секцию ниже), но **Data/Repository Contract** возможно расширяется. Это не business rules, а технический контракт между presentation и data layer.

### Возможное расширение `QuestRepository`

Если research подтвердит отсутствие — добавить в `shared/feature/quest/domain/.../QuestRepository.kt`:

```kotlin
/**
 * Observes public quests in a given catalog (visibleOn contains "home", archived=false).
 * Used by QuestListComponent (entry from HomeQuestsScreen).
 *
 * Sort: order ASC (or existing repository convention — research confirms).
 * distinct by id.
 */
fun observeByCatalog(catalogId: CatalogId, shelf: String): Flow<List<Quest>>
```

- **Filter**: `quest.catalogId == catalogId AND quest.visibleOn contains shelf AND quest.archived == false`
- **Sort**: `order ASC` (presumed; research проверит существующие паттерны)
- **Источник данных**: Room DAO query, реализуется в `data/` layer
- **Test contract**: добавить в `FakeQuestRepository` соответствующее поведение + новые scenario тесты в `QuestUseCaseTest` если нужно (но напрямую — это data-layer концерн, не domain)

### Existing repositories — без изменений

- `SectionRepository.observeByQuest(questId)` — используется как есть
- `ThemeRepository.observeBySection(sectionId)` — как есть
- `LessonRepository.observeByTheme(themeId)` — как есть
- `QuestRepository.observeMyQuests(authorUid, catalogId?)` — используется существующим `MyQuestsScreen`, не модифицируется

### Архивирование parent → дети пустеют

Согласно `home-and-my-quests/0-spec.md` Invariant B (server downward cascade): при изменении `parent.archived=true` сервер каскадно обновляет `archived=true` всех потомков. Существующие DAO queries фильтруют `WHERE archived=0` → дети автоматически исчезают из observer. **Никаких дополнительных observers parent chain в этой фиче не делаем** — полагаемся на existing invariant. — [USER DECIDED] «Дети пустеют (Recommended)»

## Search Criteria for Research

Эту секцию читает `/feature-research`. Что именно research должен найти:

### Existing infrastructure (re-use mapping)

1. **Decompose ChildStack pattern** — найти как сейчас построен `DefaultRootComponent` (`android/feature/app-shell/presentation/.../DefaultRootComponent.kt`) и как там используется `StackNavigation`. Задокументировать: где init configurations, как push/pop, как сериализация для process death.

2. **HomeQuestsComponent integration point** — `android/feature/quest/presentation/.../HomeQuestsComponent.kt:17` имеет TODO `onCatalogClick`. Найти где хost screen вызывает `component.onCatalogClick(id)` (HomeQuestsScreen UI handler). Задокументировать сигнатуру и контекст.

3. **MyQuestsScreen integration point** — `MyQuestsScreen.kt:88` `onClick = { /* TODO: open quest detail */ }`. Найти точку подключения и какие параметры доступны (questId, isOwn, catalogId).

4. **CatalogGrid onClick path** — `android/feature/quest/presentation/.../ui/HomeQuestsScreen.kt:55` использует `CatalogGrid` с `onCatalogClick = component::onCatalogClick`. Задокументировать chain HomeQuestsScreen → DefaultHomeQuestsComponent.

5. **Repository observe methods** — задокументировать **полные сигнатуры**:
   - `QuestRepository.observeMyQuests(authorUid, catalogId?)` — `shared/feature/quest/domain/.../QuestRepository.kt:39`
   - `QuestRepository.observeByShelf(shelf)` — `:50`
   - **Проверить отсутствие** `QuestRepository.observeByCatalog(catalogId)` — если нет, документировать что нужно добавить
   - `SectionRepository.observeByQuest(questId)` — `shared/feature/section/domain/.../SectionRepository.kt:24`
   - `ThemeRepository.observeBySection(sectionId)` — `shared/feature/theme/domain/.../ThemeRepository.kt:21`
   - `LessonRepository.observeByTheme(themeId)` — `shared/feature/lesson/domain/.../LessonRepository.kt:21`

6. **Existing presentation modules pattern** — `android/feature/quest/presentation/.../di/QuestPresentationModule.kt` показывает как Koin регистрирует Components. Задокументировать паттерн (`factory { params -> ... }` с ComponentContext).

7. **App-shell tab integration** — найти как существующие табы (LocalTab) добавляются в `DefaultRootComponent`. Это покажет где будет registered новый stack для quizzes-screen или как он встраивается в существующие табы (через push в существующий stack или новый child-stack внутри HomeTab).

8. **Compose Card components** — задокументировать существующие card patterns: `BrandCard`, `QuestCard` (`android/core/designsystem/.../components/`). Их API (params, onClick, modifier), типографика, padding.

9. **Long-press handling в Compose** — найти существующие места использования `Modifier.combinedClickable(onClick, onLongClick)`. Если нет — это новый паттерн для проекта.

10. **DropdownMenu pattern** — найти существующие места `DropdownMenu` (Material3). Если нет — задокументировать что используется новый паттерн.

11. **System share Intent** — найти существующие места где используется `Intent.ACTION_SEND` (legacy or new code). Задокументировать как пробрасывается Context (через `LocalContext.current` в Compose vs через ViewModel callback).

12. **Authority resolution** — найти `AuthRepository.currentUid()` (упоминается в `home-and-my-quests`) или эквивалент. Задокументировать сигнатуру и где он используется.

### Legacy reference

13. **Legacy `QuizFragment`** — `legacy/common/src/main/java/com/tpov/common/presentation/quiz/QuizFragment.kt`. Задокументировать:
    - PathStructure модель (`legacy/common/.../PathStructure.kt`) — как там организован путь
    - `QuizActivityViewModel.listStructureDataLocalFlow` — какие данные отдавал
    - `initPath()` логика (`:55-72`) — формат breadcrumb
    - `onClick(structureDataLocal, typeQuestion)` — drill-down логика

14. **Legacy adapter** — `QuizActivityAdapter.kt` в той же директории. Какой layout (`activity_quiz_item.xml`) и какие действия (long-press menu items) были.

### Architecture invariants

15. **Cross-feature import check** — новый module `android/feature/quizzes-screen/presentation/` будет импортировать `android/core/designsystem` и `shared/feature/{quest,section,theme,lesson}/domain`. Проверить что нет cross-feature import в `android/feature/quest/presentation/` (текущая зависимость через TODO в HomeQuests/MyQuests, без direct call).

16. **Layer boundaries** — Components импортируют только Repository interfaces (не Impl, не DAO, не Entity). Должны быть suspend/Flow-based.

### Domain model fields and ordering

17. **Поля domain моделей** — задокументировать **полные сигнатуры** для:
    - `Quest` (`shared/feature/quest/domain/.../model/Quest.kt`): какие поля используются для отображения (title, picturePath, pictureUrl, averageRating, averageRatingCount, visibleOn, archived, order? authorUid)
    - `Section` (`shared/feature/section/domain/.../model/Section.kt`): title, order, archived, parent linkage (questId)
    - `Theme`: title, order, archived, sectionId
    - `Lesson`: title, order, archived, themeId

18. **Sort order на каждом уровне** — задокументировать существующие DAO/Repository `ORDER BY` clauses. Утверждение spec — `order ASC` — должно быть verified, или зафиксирована альтернативная сортировка.

19. **Count-of-children APIs** — проверить наличие готовых methods для `numberOfThemes(sectionId)`, `numberOfLessons(themeId)` в DAO/Repository. Если есть — `subtitleCount` сможет использоваться в будущем; если нет — фиксируем как future-feature, в MVP `subtitleCount = null`.

20. **Pluralization для empty states / counts** — найти как существующие screens оформляют тексты «Нет квестов», «Нет каталогов» (`HomeQuestsScreen`, `MyQuestsScreen` уже имеют их). Использовать ту же конвенцию (string resources / inline тексты).

### QuestCard onLongClick

21. **`QuestCard` API** — `android/core/designsystem/.../QuestCard.kt` сейчас принимает только `onClick`. Проверить нужно ли расширять параметром `onLongClick: (() -> Unit)?` или создать новый wrapper-компонент для new screen. Это design-фазы решение, но research должен задокументировать current API.

### Completeness check

- Для каждого entry point (HomeQuestsScreen tap catalog, MyQuestsScreen tap quest) — задокументировать **полную цепочку** UI → Component → Use Case / Repository.
- Для drill-down — задокументировать что `Section.questId`, `Theme.sectionId`, `Lesson.themeId` поля существуют в domain моделях.
- grep + manual verification всех мест где сейчас стоит `// TODO: open quest detail` или `// TODO: future catalog detail navigation`.
- Найти **все** существующие Decompose configurations (через `Configuration` sealed class в DefaultRootComponent) — чтобы понять как добавить новые без поломки existing навигации.
- Для `QuestRepository.observeByCatalog(catalogId, shelf)` — verify отсутствие через grep по shared domain.
- Для `combinedClickable` (long-press support) — verify наличие или отсутствие в существующем коде.
- Для `DropdownMenu` — verify первое или повторное использование Material3 component.

## Primary User Journeys

1. **Happy drill-down (HomeQuests entry)**
   - Start: `HomeQuestsScreen` (tab Home), отображены каталоги в `CatalogGrid`.
   - Trigger: тап на каталог.
   - State changes: push `QuestListComponent(catalogId)` → отображён список квестов с breadcrumb `"Каталог Имя"`.
   - User тапает квест → push `SectionListComponent(questId)` → breadcrumb `"Каталог > Квест"`.
   - User тапает секцию → push `ThemeListComponent(sectionId)` → breadcrumb `"Каталог > Квест > Секция"`.
   - User тапает тему → push `LessonListComponent(themeId)` → breadcrumb `"Каталог > Квест > Секция > Тема"`.
   - User тапает урок → push `LessonPlaceholderComponent(lessonId)` → показ placeholder-экрана.
   - Expected result: navigation работает на всех 4 уровнях, breadcrumb отображает корректный путь.
   - Decision: [USER DECIDED]

2. **Happy drill-down (MyQuests entry)**
   - Start: `MyQuestsScreen` (tab My Quests), катaлог выбран в `CatalogSpinner`, отображены свои квесты.
   - Trigger: тап на свой квест.
   - State changes: push `SectionListComponent(questId)` → breadcrumb начинается с `"Каталог Имя > Квест Название"` (катaлог берётся из `selectedCatalog.name` MyQuests state).
   - Дальше — как в Journey 1 от уровня Section.
   - Expected result: вход в drill-down с уровня Quest, breadcrumb корректно содержит каталог + квест.
   - Decision: [USER DECIDED]

3. **System back navigation**
   - Start: пользователь на любом уровне глубже первого (например `ThemeListComponent`).
   - Trigger: system back gesture / button.
   - State changes: ChildStack pop → возврат на предыдущий уровень с обрезанным breadcrumb.
   - Expected result: standard Android back behavior; на самом верхнем уровне stack — back закрывает фичу (возврат на HomeQuests/MyQuests).
   - Decision: [USER DECIDED]

4. **Breadcrumb pop**
   - Start: пользователь на `LessonListComponent` (4-й уровень).
   - Trigger: тап на сегмент `"Квест Название"` в breadcrumb.
   - State changes: ChildStack pop **до** `SectionListComponent(questId)` (удаляются ThemeListComponent + LessonListComponent из стека).
   - Expected result: пользователь на уровне секций, breadcrumb обрезан до `"Каталог > Квест"`.
   - Decision: [USER DECIDED]

5. **Share quest (long-press menu)**
   - Start: пользователь на `QuestListComponent` (или MyQuestsScreen, если меню работает там тоже).
   - Trigger: long-press на `QuestCard`.
   - State changes: открывается `DropdownMenu` с пунктом «Поделиться», anchored к карточке.
   - User тапает «Поделиться» → fires `Intent.ACTION_SEND` с текстом `"Квест «{quest.title}» — {appName}"`.
   - System chooser открывается; user выбирает приложение или закрывает.
   - Меню закрывается.
   - Expected result: share работает, drill-down state не меняется, после share user остаётся на том же уровне.
   - Decision: [USER DECIDED]

6. **Empty level**
   - Start: пользователь дрилается до `SectionListComponent(questId)`.
   - Trigger: квест существует, но не имеет секций (`Flow` отдаёт пустой список).
   - State changes: показывается empty state «Нет секций» по центру экрана.
   - Expected result: user видит explanatory placeholder, может вернуться через back или breadcrumb.
   - Decision: [DELEGATED]

7. **Archived during navigation**
   - Start: пользователь на `ThemeListComponent(sectionId)`.
   - Trigger: фоновый sync обновляет родительскую секцию `archived=true` (admin кикнул).
   - State changes: `Flow.observeBySection(sectionId)` отдаёт пустой список (DAO фильтрует archived).
   - Expected result: empty state «Нет тем». User сам уходит через back. Без auto-pop, без toast.
   - Decision: [USER DECIDED] «просто обработка ошибки и все»

8. **Process death**
   - Start: пользователь на `ThemeListComponent` с активным breadcrumb `"Каталог > Квест > Секция"`.
   - Trigger: Android убивает процесс из-за low-memory.
   - State changes: при возврате — Decompose `StateKeeper` восстанавливает ChildStack из сохранённых configurations (только id + title для breadcrumb).
   - Expected result: пользователь на том же уровне; данные перезагружаются из Room через `Flow`.
   - Decision: [DELEGATED]

9. **Configuration change (rotation)**
   - Start: любой уровень drill-down.
   - Trigger: rotation (portrait → landscape).
   - State changes: Decompose `instanceKeeper` сохраняет Components, ChildStack не перепушивается.
   - Expected result: state и scroll position сохраняются.
   - Decision: [DELEGATED]

10. **Fresh install / empty cache**
    - Start: пользователь только установил приложение, sync ещё не подтянул каталоги.
    - Trigger: открывает HomeTab.
    - State changes: HomeQuestsScreen показывает empty state «Нет каталогов» (existing behavior, не от этой фичи). Drill-down невозможен (нечего тапать).
    - Когда cascade sync подтянет каталоги — `Flow` обновит UI, drill-down станет доступен.
    - Expected result: graceful degradation, без ошибок.
    - Decision: [DELEGATED]

11. **Logout / login / account switch — N/A**
    - Reasoning: фича работает с локальной БД, `currentUid` (anonymous Firebase Auth) всегда есть. Auth state changes — забота app-shell. Эта фича не подписывается на auth events. — [USER DECIDED] «логаут влияет только на вкладку интернет»

12. **Offline**
    - Start: пользователь без интернета.
    - Trigger: drill-down по уже закешированным данным.
    - State changes: Room flows работают offline; data доступна.
    - Expected result: full navigation работает; новых обновлений нет (sync парализован), но UX не ломается.
    - Decision: [DELEGATED]

13. **Parallel changes (cascade sync во время навигации)**
    - Start: пользователь на `LessonListComponent`.
    - Trigger: cascade sync приходит с обновлёнными уроками.
    - State changes: `Flow` автоматически отдаёт новый список → `LazyColumn` перерисовывается.
    - Expected result: silent UI update, без toast.
    - Decision: [DELEGATED]

## Feature Domain Contract

**Business Domain Contract = N/A** — обоснование:

Эта фича — pure presentation/navigation layer. Все business rules (versioning, cascade sync, archived semantics, visibleOn shelf фильтрация, рейтинг computation, downward cascade при archived) уже зафиксированы в `home-and-my-quests/0-spec.md` Feature Domain Contract и реализованы в shared domain слое. Эта фича только **отображает** результаты existing repository observers.

Что есть «логика» в фиче (но это не business domain):
- **Breadcrumb path computation** = derived state из `ChildStack.activeChild` (presentation concern). Frozen titles из configurations.
- **ChildStack popTo logic** при тапе на breadcrumb сегмент — стандартный Decompose API, не custom business rule.
- **Long-press menu visibility** = static rule (показываем для Quest в новом экране, никогда для остальных в MVP). Не зависит от data state.
- **Empty state condition** = `list.isEmpty()` (тривиально).
- **Archived item handling** = делегировано repository (DAO `WHERE archived=0` уже работает), фича получает уже отфильтрованные данные. Полагается на Invariant B из `home-and-my-quests` (server cascading).
- **Share Intent text composition** = string template (`"Квест «{title}» — {appName}"`). Не business logic.

Никаких новых **business** domain entities, value objects, business rules, invariants не требуется. Никаких новых use cases — текущий объём работы покрывается existing `observeByX` методами на repositories.

**Что НЕ N/A** — это **Data/Repository Contract** (см. секцию выше): возможное добавление `QuestRepository.observeByCatalog(catalogId, shelf)` — но это техническое расширение data-layer API, не новое business rule.

**Phase 3.8 Walking Skeleton — пропускается** (см. Quality Gate 4.5: применяется только если Feature Domain Contract ≠ N/A).

### Тестирование без Walking Skeleton

Поскольку Walking Skeleton skip — JVM unit-тесты пишутся **параллельно с реализацией Components в phase-01** (TDD style):
- `DefaultQuestListComponentTest`, `DefaultSectionListComponentTest`, `DefaultThemeListComponentTest`, `DefaultLessonListComponentTest`, `DefaultLessonPlaceholderComponentTest` через existing `FakeQuestRepository` / `FakeSectionRepository` / `FakeThemeRepository` / `FakeLessonRepository`.
- Compose UI тесты для `BreadcrumbBar` и `HierarchyItemCard` в `androidTest/`.
- Тестируется: empty → loaded transitions, archived disappears flow, sort order ASC, breadcrumb pop semantics, long-press menu visibility, share intent fired.
- См. AC #33-37 для конкретных тестов.

## Delegated Decisions Summary

| # | Область | Решение агента | Обоснование | Risk |
|---|---------|---------------|-------------|------|
| 1 | HierarchyItemCard как универсальный компонент | Один компонент для Section/Theme/Lesson с title + orderLabel + subtitleCount | Минимизирует boilerplate; Section/Theme/Lesson имеют идентичную структуру (title + order + дети). User: «он же уже создан в мвп?» → «удалишь если не подходит» | Low — если в design phase найдём существенные различия между уровнями, разделим на 3 компонента |
| 2 | Декоративные элементы из легасного `activity_quiz_item.xml` (gradient indicators, swipe-кнопки, hidden ImageButtons) | Не переносить | В легасе они в основном были `visibility="gone"`; не используются в текущем UI/UX | Low — если выяснится что какой-то элемент действительно нужен, добавим в design phase |
| 3 | Empty state на любом уровне | Текст «Нет {уровень}» по центру | Соответствует existing pattern в `MyQuestsScreen`, `HomeQuestsScreen` | Low |
| 4 | Loading state | `CircularProgressIndicator` по центру | Соответствует existing | Low |
| 5 | Long-press на Section/Theme/Lesson | Игнорируется (передаём `null` в `onLongClick`) | В MVP нет actions для этих уровней; будущие CRUD-фичи добавят | Low — добавление action в будущем = только параметр компонента |
| 6 | Share text format | `"Квест «{quest.title}» — {appName}"` без deep-link | Deep-linking — отдельная фича; текстовая ссылка достаточна для MVP | Medium — share без URL имеет ограниченное value, но добавим URL когда появится deep-link |
| 7 | DI module location | `android/feature/quizzes-screen/presentation/.../di/QuizzesPresentationModule.kt` | Соответствует existing pattern из `QuestPresentationModule` | Low |
| 8 | Walking Skeleton skip | Phase 3.8 пропускается, обоснование в Feature Domain Contract секции | Pure UI feature, нет новых domain rules | Low — quality gate 4.5 явно разрешает skip когда contract = N/A |
| 9 | Process death handling | Decompose `StateKeeper` через configurations | Standard Decompose pattern, уже используется в DefaultRootComponent | Low |
| 10 | Offline indicator | Не показываем явный «оффлайн» баннер | Existing screens (Home/MyQuests) не показывают; consistency | Low |
| 11 | HierarchyItemCard fields (orderLabel, subtitleCount) | nullable, скрываем если null | Гибкость для будущих использований | Low |
| 12 | LessonPlaceholderComponent в ChildStack | Отдельная configuration в той же фиче | Будущая Lesson detail-фича заменит placeholder без переработки навигации | Low |

## State Matrix

Фича имеет небольшую ветвистую логику в UI, но **не domain-level** — это presentation behavior. Матрица для clarity:

### Matrix 1: Tap action by element type

| Тип элемента | Тап → действие | Long-press → действие |
|--------------|----------------|-----------------------|
| Catalog (на HomeQuests) | Push `QuestListComponent(catalogId)` | (existing, вне scope этой фичи) |
| Quest (на QuestList — drill-down с HomeQuests) | Push `SectionListComponent(questId, breadcrumb=[catalog,quest])` | DropdownMenu с «Поделиться» |
| Quest (на MyQuests — existing screen) | Push `SectionListComponent(questId, breadcrumb=[catalog,quest])` (см. Journey 2) | None (existing UI не модифицируется) |
| Section (на SectionList) | Push `ThemeListComponent(sectionId, breadcrumb=[...,section])` | None |
| Theme (на ThemeList) | Push `LessonListComponent(themeId, breadcrumb=[...,theme])` | None |
| Lesson (на LessonList) | Push `LessonPlaceholderComponent(lessonId, breadcrumb=[...,lesson])` | None |
| Element на LessonPlaceholder (текст-плейсхолдер) | None (тапов нет) | None |
| Breadcrumb segment (не последний) | Pop ChildStack до уровня этого сегмента | None |
| Breadcrumb segment (последний) | None (некликабельно) | None |

### Matrix 2: Empty/Loading/Loaded rendering by level

| Состояние | Quest List | Section List | Theme List | Lesson List | Lesson Placeholder |
|-----------|-----------|--------------|-----------|-------------|--------------------|
| Loading | `CircularProgressIndicator` | `CircularProgressIndicator` | `CircularProgressIndicator` | `CircularProgressIndicator` | (no loading — static) |
| Empty | «Нет квестов» | «Нет секций» | «Нет тем» | «Нет уроков» | N/A |
| Loaded | LazyColumn of `QuestCard` | LazyColumn of `HierarchyItemCard` | LazyColumn of `HierarchyItemCard` | LazyColumn of `HierarchyItemCard` | Текст «Прохождение урока «{title}» будет добавлено позже» по центру |

### Matrix 3: Breadcrumb path by level

| Уровень | Breadcrumb format |
|---------|-------------------|
| QuestList (entry HomeQuests) | `"<catalog.name>"` |
| SectionList (entry HomeQuests) | `"<catalog.name> > <quest.title>"` |
| SectionList (entry MyQuests) | `"<selectedCatalog.name> > <quest.title>"` |
| ThemeList | `"<catalog.name> > <quest.title> > <section.title>"` |
| LessonList | `"<catalog.name> > <quest.title> > <section.title> > <theme.title>"` |
| LessonPlaceholder | `"<catalog.name> > <quest.title> > <section.title> > <theme.title> > <lesson.title>"` |

Все titles — frozen snapshot на момент push. Если sync переименует — breadcrumb остаётся старым.

## Acceptance Criteria

### Navigation flow

1. [ ] GIVEN пользователь на `HomeQuestsScreen` с непустым списком каталогов WHEN тапает каталог `c` THEN открывается `QuestListComponent(catalogId=c.id)` с breadcrumb **точно** `"{c.name}"`, последний сегмент некликабелен; список квестов отсортирован по `lastModifiedAt DESC` **[ADDED IN RESEARCH: Quest model не имеет поля `order` — используется existing QuestDao convention]**; видны **только** public квесты (visibleOn contains "home"), archived=false
2. [ ] GIVEN пользователь на `MyQuestsScreen` с выбранным каталогом `c` и непустым списком квестов WHEN тапает свой квест `q` THEN открывается `SectionListComponent(questId=q.id)` с breadcrumb **точно** `"{c.name} > {q.title}"`; список секций отсортирован по `order ASC`. **[ADDED IN RESEARCH: catalog name берётся из `q.catalogId` lookup в `state.catalogs` — не зависит от `state.selectedCatalogId` (который может быть null при фильтре «все каталоги»). Для этого `QuestDisplayItem` расширяется полем `catalogId: CatalogId`.]**
3. [ ] GIVEN пользователь на `QuestListComponent` (entry с HomeQuests) WHEN тапает квест `q` THEN открывается `SectionListComponent(questId=q.id)` с breadcrumb **точно** `"{c.name} > {q.title}"`; список секций отсортирован по `order ASC`
4. [ ] GIVEN пользователь на `SectionListComponent` WHEN тапает секцию `s` THEN открывается `ThemeListComponent(sectionId=s.id)` с breadcrumb **точно** `"{c.name} > {q.title} > {s.title}"`; список тем отсортирован по `order ASC`
5. [ ] GIVEN пользователь на `ThemeListComponent` WHEN тапает тему `t` THEN открывается `LessonListComponent(themeId=t.id)` с breadcrumb **точно** `"{c.name} > {q.title} > {s.title} > {t.title}"`; список уроков отсортирован по `order ASC`
6. [ ] GIVEN пользователь на `LessonListComponent` WHEN тапает урок `l` THEN открывается `LessonPlaceholderComponent(lessonId=l.id)` с breadcrumb **точно** `"{c.name} > {q.title} > {s.title} > {t.title} > {l.title}"` и текстом по центру `"Прохождение урока «{l.title}» будет добавлено позже"`
7. [ ] GIVEN пользователь на любом уровне глубже первого WHEN нажимает system back THEN ChildStack pop возвращает на предыдущий уровень; breadcrumb обрезается на последний сегмент
8. [ ] GIVEN пользователь на `LessonListComponent` с breadcrumb `"К > Кв > С > Т"` WHEN тапает на сегмент «Кв» THEN ChildStack pop до `SectionListComponent(questId)` (удаляются Theme + Lesson из стека); breadcrumb становится `"К > Кв"`
9. [ ] GIVEN пользователь на `QuestListComponent` (entry с HomeQuests) WHEN тапает на текущий (последний) сегмент breadcrumb THEN ничего не происходит (некликабельно)

### Long-press menu и Share

10. [ ] GIVEN пользователь на `QuestListComponent` (drill-down c HomeQuests) видит `QuestCard` WHEN long-press THEN открывается `DropdownMenu` anchored к карточке, содержит пункт «Поделиться»
11. [ ] GIVEN пользователь на существующем `MyQuestsScreen` видит `QuestCard` WHEN long-press THEN ничего не происходит (existing UI не модифицируется в этой фиче)
12. [ ] GIVEN открыто меню WHEN тап на «Поделиться» THEN срабатывает `Intent.ACTION_SEND` с `type = "text/plain"` и текстом `"Квест «{q.title}» — {appName}"`, меню закрывается
13. [ ] GIVEN открыто меню WHEN тап вне меню THEN меню закрывается без action
14. [ ] GIVEN тап на «Поделиться» WHEN на устройстве нет приложения для `ACTION_SEND` THEN `ActivityNotFoundException` ловится и логируется, меню закрывается, navigation state не меняется, без UI-уведомлений
15. [ ] GIVEN пользователь на `HierarchyItemCard` (Section/Theme/Lesson) WHEN long-press THEN ничего не происходит (без меню в MVP)

### Empty / archived / fresh install

16. [ ] GIVEN пользователь дрилается до уровня без детей (например квест без секций) THEN отображается empty state «Нет секций» (текст центрирован, `MaterialTheme.typography.titleMedium`)
17. [ ] GIVEN пользователь на любом уровне с активным `Flow.collect` WHEN sync обновляет данные в Room (новый элемент добавлен / архивирован / переименован) THEN UI автоматически перерисовывается с новыми данными (breadcrumb остаётся старым — frozen)
18. [ ] GIVEN пользователь на `ThemeListComponent` WHEN sync архивирует родительскую секцию (cascading per Invariant B) THEN `Flow` отдаёт пустой список → отображается empty state «Нет тем» (без auto-pop, без toast)
19. [ ] GIVEN fresh install (Room пустая) WHEN пользователь открывает HomeQuestsScreen THEN видит empty state каталогов; drill-down не доступен (нечего тапать). Когда cascade sync подтянет данные → HomeQuestsScreen обновляется через `Flow`, drill-down становится доступен
20. [ ] GIVEN offline (no internet) WHEN drill-down по закешированным данным THEN навигация работает без ошибок; новые элементы не появляются (sync парализован), без оффлайн-индикатора

### Process death / rotation / breadcrumb behavior

21. [ ] GIVEN process death во время drill-down WHEN пользователь возвращается в приложение THEN ChildStack восстанавливается на тот же уровень с тем же breadcrumb (frozen titles из configurations); данные перезагружаются из Room через `Flow`. **[ADDED IN RESEARCH: новый quizzes ChildStack использует `serializer = QuizzesConfig.serializer()` (kotlinx-serialization). Это первый стек в проекте с включённым serializer — все existing tabs `serializer = null` и НЕ восстанавливаются после process death. AppShell-tab restoration происходит через domain `NavStack` rehydration, drill-down restoration — через Decompose StateKeeper.]**
22. [ ] GIVEN configuration change (rotation) WHEN пользователь поворачивает устройство THEN Components не пересоздаются (сохраняются через `instanceKeeper`), scroll position в `LazyColumn` сохраняется
23. [ ] GIVEN sync переименовал quest пока пользователь дрилается в его секциях THEN breadcrumb остаётся со **старым** title (frozen на момент push); список секций обновляется автоматически через `Flow`

### HierarchyItemCard / BreadcrumbBar UI

24. [ ] GIVEN `HierarchyItemCard` с `orderLabel = "1."` и `subtitleCount = null` THEN отображаются: orderLabel слева, title по центру/занимает width; без картинки, без рейтинга, без subtitleCount
25. [ ] GIVEN `HierarchyItemCard` с `orderLabel = null` и `subtitleCount = null` THEN отображается только title без вспомогательных полей
26. [ ] GIVEN `BreadcrumbBar` с путём из 3 сегментов THEN сегменты разделяются `>`, последний некликабелен и визуально выделен (например `MaterialTheme.colorScheme.primary` для последнего vs `onSurface` для остальных — конкретика в design)
27. [ ] GIVEN long title в breadcrumb сегменте (>20 символов) THEN текст обрезается через `TextOverflow.Ellipsis`, `maxLines = 1`
28. [ ] GIVEN тап на сегмент breadcrumb `n` THEN ChildStack pop до уровня `n` (удаляются все более глубокие)

### Code / DI / invariants

29. [ ] DI: `QuizzesPresentationModule` зарегистрирован в `AppApplication.kt` startKoin; все Components создаются через Koin `factory` с правильно проброшенным `ComponentContext` и dependency parameters
30. [ ] Code: ни один файл новой фичи не импортирует Android/SDK типы в shared/feature/*/domain (invariant 1 не нарушен)
31. [ ] Code: ни одна Activity/Fragment не вызывает Repository / UseCase напрямую (invariant 2 не нарушен; всё через Components)
32. [ ] Code: `QuestRepository.observeByCatalog(catalogId, shelf)` (если research подтвердит отсутствие) добавлен в shared domain interface, реализован в data layer + соответствующее behavior добавлено в `FakeQuestRepository`
33. [ ] Tests: JVM unit-тесты для каждого `DefaultXxxListComponent` через FakeXxxRepository (empty → loaded → archived disappears flows; sorted by order ASC)
34. [ ] Tests: JVM unit-тест для breadcrumb pop logic — `PopBreadcrumbTest` (имитирует ChildStack popTo и проверяет что correct configurations удалены)
35. [ ] Tests: JVM unit-тест для `LessonPlaceholderComponent` (отдаёт правильный title в state)
36. [ ] Tests: Compose UI тесты для `BreadcrumbBar` (тап сегмента → callback fired with correct level index; ellipsis работает) и `HierarchyItemCard` (long-press fires callback when set; null fields не отображаются)
37. [ ] Tests: Compose UI тест для `QuestListComponent` long-press → menu открывается → тап Share → Intent fired (через mock)
38. [ ] Build: `./gradlew assemble --no-configuration-cache` зелёный
39. [ ] Tests: `./gradlew allTests --no-configuration-cache` зелёный

## Invariant Check (from docs/invariants.md)

| Invariant | Impact | Decision |
|-----------|--------|----------|
| 1. Domain layer purity | Фича не добавляет ничего в shared domain | preserve — никаких Android/SDK импортов, никаких DI-аннотаций. Если research подтвердит необходимость `QuestRepository.observeByCatalog`, добавится только signature в interface (suspend/Flow ok), implementation в data |
| 2. Activity/Fragment calls only ViewModel | Фича без Activity/Fragment (только Compose + Decompose Components) | preserve — Activity/Fragment не используются. Compose screens вызывают только Component public API |
| 3. No bidirectional coupling between feature modules | Новый module `android/feature/quizzes-screen/presentation/` импортирует `android/core/designsystem` + shared domains. Не импортирует другие android/feature/* | preserve — research проверит cross-feature import constraints |
| 4. onDestroy is not for business cleanup | Фича без Activity/Fragment lifecycle, только Decompose Component lifecycle (`doOnDestroy` для cancel scope) | preserve — стандартный Decompose pattern, не Activity onDestroy |
| 5. DI exclusive binding | Koin (Project использует Koin, не Hilt/Dagger) — invariant N/A для Koin (см. di-patterns.md) | N/A |
| 6. Walking Skeleton ownership | Spec помечает Feature Domain Contract = N/A → Walking Skeleton не генерируется на spec-фазе | preserve — Phase 3.8 пропускается |
| 7. Scaffold file ownership | Новый module добавит `build.gradle.kts` + `settings.gradle.kts` entries — backend-dev ownership | preserve — другие teammates запрашивают scaffold изменения через lead |

## Constraints (from PROJECT-CONTEXT.md)

- **Koin manual DI** — composition root в `apps/android-next/.../AppApplication.kt`. Новый module добавляется в startKoin список.
- **Decompose Components (ADR-CMP-51)** — pattern из `home-and-my-quests/03-decisions.md`. Каждый level = отдельный Component с DI factory.
- **KMP layout** — domain в `shared/feature/*/domain/src/commonMain`, presentation в `android/feature/*/presentation/src/main`. Эта фича — только Android presentation, без shared changes (если не считать optional `observeByCatalog` в QuestRepository).
- **Build commands** — `./gradlew assemble --no-configuration-cache` и `./gradlew allTests --no-configuration-cache` (PROJECT-CONTEXT.md §Build/Validation).
- **Testing convention** — JVM unit tests + fakes (см. `.claude/rules/testing.md`); MockK как fallback. Compose UI tests в androidTest/.
- **Brand consistency** — `MaterialTheme.colorScheme.*`, никаких hardcoded `Color(0xFF...)` в новых компонентах.
- **Naming conventions** — package `com.tpov.schoolquiz.android.feature.quizzes_screen.presentation` (или `quizzes` без screen suffix — детали design phase).

## Open Questions for Research

**Все Open Questions закрыты в research-фазе. Findings зафиксированы в `1-research.md`. Ниже — резюме закрытий.**

1. ~~`QuestRepository.observeByCatalog(catalogId, shelf)` существует?~~ **Закрыто**: метод ОТСУТСТВУЕТ (verified `QuestRepository.kt:34-69`, full grep). Создаётся в design/implementation phase. Filter: `catalogId = :catalogId AND visibleOn LIKE delimiter-wrapped "%home%" AND archived = 0`. Sort: `lastModifiedAt DESC` (см. п.23 spec). — [RESOLVED IN RESEARCH]
2. ~~HomeQuests entry — какие квесты показывать в каталоге?~~ **Закрыто**: public-only (visibleOn contains "home"), archived=false. — [USER DECIDED]
3. ~~`AuthRepository.currentUid()` сигнатура~~ **Закрыто**: `suspend fun currentUid(): String?` (`shared/feature/app-shell/domain/.../AuthRepository.kt:31`) + `fun observeUid(): Flow<String?>` (`:43`). В quizzes-screen напрямую не нужен. — [RESOLVED IN RESEARCH]
4. ~~Tab integration~~ **Закрыто**: внутренний ChildStack в новом `QuizzesComponent` (см. п.1, User Decision #1). Не расширяет domain `NavStack` FSM, не расширяет `LocalConfig`. Заменяет content rendered в `HomeQuestsContent`/`MyQuestsContent` after first push. — [RESOLVED IN RESEARCH]
5. ~~Decompose configuration sealed class~~ **Закрыто**: `@Serializable sealed class QuizzesConfig` (новый, в quizzes-screen module) с вариантами `QuestList`, `SectionList`, `ThemeList`, `LessonList`, `LessonPlaceholder`. — [RESOLVED IN RESEARCH]
6. ~~Sort order~~ **Закрыто**: `SectionDao`/`ThemeDao`/`LessonDao` верифицированы — `ORDER BY \`order\` ASC` (`SectionDao.kt:13-18`, `ThemeDao.kt:13`, `LessonDao.kt:13`). Quest НЕ имеет поля `order` — sort `lastModifiedAt DESC` (см. п.23). — [RESOLVED IN RESEARCH]
7. ~~`combinedClickable` Compose API~~ **Закрыто**: `Modifier.combinedClickable` доступен в Compose Foundation, нулевые usages в `android/` (verified grep). Quizzes-screen — первое появление. Default `hapticFeedbackEnabled = true`. — [RESOLVED IN RESEARCH]
8. ~~Material3 `DropdownMenu` API~~ **Закрыто**: standalone `DropdownMenu` доступен в Material3 BOM 2024.09.02, нулевые usages в проекте (`CatalogSpinner.kt:55` использует `ExposedDropdownMenu` — другой компонент). Pattern: общий `Box` для anchor + menu, общий state `var expanded by remember { mutableStateOf(false) }`. — [RESOLVED IN RESEARCH]
9. ~~Compose `LocalContext.current` для share Intent~~ **Закрыто**: pattern: `val context = LocalContext.current` → `context.startActivity(Intent.createChooser(sendIntent, null))`. `try/catch(ActivityNotFoundException)` для defensive logging. `Intent.createChooser` рекомендован Android docs. Component получает `onShareClick(quest)` callback — Composable scope использует `LocalContext.current`. — [RESOLVED IN RESEARCH]

## Open Questions for Design (новые, добавлены research-фазой)

10. **`QuestCard.onLongClick` API extension** — `QuestCard.kt:41` имеет только `onClick: (QuestId) -> Unit`. Опции для design phase: (A) расширить existing `QuestCard` параметром `onLongClick: ((QuestId) -> Unit)? = null` (затрагивает existing consumers + BrandComponentsInvariantsTest требует `@Preview` для всех), (B) создать новый wrapper composable в quizzes-screen для long-press поведения. — [DELEGATED to design phase]
11. **`QuestDisplayItem` расширение полем `catalogId: CatalogId`** — `QuestDisplayItem.kt:14-20` сейчас содержит только `id, title, pictureUrl, averageRating, averageRatingCount`. Добавление `catalogId` — изменение в shared mapping в quest/presentation. Design phase решает где конкретно добавить mapping (в `Quest.toDisplayItem()` или в `MyQuestsUiState` отдельно). — [DELEGATED to design phase]
12. **Padding 4.dp vs 8.dp** — spec п.56 ссылается на `MyQuestsScreen.kt:91` со значением 8.dp вертикально, фактически в коде `:90` — `vertical = 4.dp`. Design phase утверждает финальное значение для quizzes lists (вероятно 4.dp для consistency с existing). — [DELEGATED to design phase]
