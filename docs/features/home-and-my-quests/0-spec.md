---
date: 2026-04-21
updated: 2026-04-22
feature: home-and-my-quests
type: new-feature
commit: 7c52c200
---

> **2026-04-22 update**: после `/feature-research` зафиксированы 4 новых решения (Decisions #41-44):
> #41 — `Destination.OpenQuestCreate` для FAB navigation (вместо несуществующего `Navigator.goTo(LocalConfig)`)
> #42 — Новый `AuthRepository.currentUid()` interface вместо расширения `UserStatsRepository`
> #43 — Bump Coil 3.1.0 → 3.4.0 (соответствие ADR-HLA-06)
> #44 — Cleanup пустых `quiz/` модулей + placeholder Quest в `core/catalog/domain`
>
> Walking Skeleton дополнен `AuthRepository` interface + `FakeAuthRepository` + 6 contract тестов в `shared/feature/app-shell/domain`.
>
> Cursor field противоречие в spec разрешено: используется `lastModifiedAt` (FR#14, Scope→Data layer обновлён). `version` остаётся для upsert monotonicity.

# Feature Specification: Home Quests & My Quests Screens + Cascading Catalog Sync

## Source

- Исходный запрос: "давай сделаем экраны домашние квесты, и мои квесты. отредактируем синхронизацию. у модель каталога добавим версию, если версия на сервер выше чем локальная - то мы удаляем все данные о каталоге и скачиваем заново, в данном случае это только фото, получается ну или название, а им айди нужен? они же по названиям синхронизируются или как? вот тут я не понял как мы идентифицируем что-бы обновить?."
- Дополнение по дизайну: "на экране домашние квесты все ок, только надо нормальный дизайн сделать например текст странно смотрится. мои квесты там давай пусть будут каталог не в 2 ряда а в виде спинера плашкой под топбаром, а на самом экране уже список квизов которые относятся к данному юзеру, и выбранному каталогу"
- Type: new-feature (расширение каталога + новая модель Quest/Section/Theme/Lesson/Question + 2 новых экрана + каскадная sync-инфраструктура)

## Requirements

### Functional Requirements

#### Модель данных — иерархия и versioning

1. **6-уровневая иерархия контента**: `Catalog → Quest → Section → Theme → Lesson → Question`. Каждый уровень (кроме leaf — Question) имеет **двойное versioning**: `version: Long` (сам элемент) + `contentsVersion: Long` (вложенные потомки). Question — только `version`. — [USER DECIDED] "внутри квестов нету вопросов, квесты содержат темы, тема содержит уроки и урок содержит вопросы" + [USER DECIDED] "я хотел еще добавить разделы до тем, стоит ли" → "Да, обязательный"

2. **Плоские Firestore collections + parentId**: 6 top-level collections (`catalogs`, `quests`, `sections`, `themes`, `lessons`, `questions`). Связь через parent-id поля (`Quest.catalogId`, `Section.questId`, `Theme.sectionId`, `Lesson.themeId`, `Question.lessonId`). Не subcollections. — [USER DECIDED] "плоский список с полями типа владелец, каталог, в каких экранах он есть" + [DELEGATED: плоские лучше подходят для cross-catalog фильтра "Мои квесты"]

3. **Identity = stable id (UUID)**: матчим всё по id (value classes `CatalogId`, `QuestId`, `SectionId`, `ThemeId`, `LessonId`, `QuestionId`), не по имени. Legacy `nameItem`-based sync заменяется id-based. — [USER DECIDED, ответ на вопрос "а им айди нужен? они же по названиям синхронизируются или как?"]

4. **Visibility через массив строк `visibleOn: List<String>`** у Quest: значения `["home", "arena", "tournament", "tournamentFinal", "archive"]`. Квест может быть виден в нескольких местах одновременно. Пустой `visibleOn` → локальное удаление. — [USER DECIDED] "Набор булеанов" (в Firestore моделируется через массив для `array-contains-any`)

5. **Catalog удаление через soft flag**: `Catalog.archived: Boolean`. При `archived=true` — клиент удаляет локально. Не физическое удаление документа. — [USER DECIDED] "я думаю мы просто скачиваем с него поля и типа там будет только поле архив активно значит оно уберется с данного экрана"

6. **Catalog domain model расширяется**: к существующему `Catalog(id, name, picturePath, pictureUrl)` добавляются поля `version: Long`, `contentsVersion: Long`, `lastModifiedAt: Long`, `archived: Boolean`. Existing invariants сохраняются; новые invariants: `version >= 1`, `contentsVersion >= 0`, `lastModifiedAt >= 0`. — [USER DECIDED]

7. **Quest domain model (минимум для MVP)**: `Quest(id: QuestId, catalogId: CatalogId, authorUid: String, title: String, picturePath: String?, pictureUrl: String?, visibleOn: Set<String>, averageRating: Float?, averageRatingCount: Int, version: Long, contentsVersion: Long, lastModifiedAt: Long, archived: Boolean)`. — [USER DECIDED] "Минимум" + дополнения про рейтинг + [USER DECIDED 2026-04-21] "authorUid вместо tpovId"

8. **`Quest.authorUid: String`** — Firebase Auth UID. Клиент читает свой UID из `FirebaseAuth.currentUser.uid` (доступен через `UserStatsRepository` или аналогичный интерфейс auth). Server-side защита через security rules `allow read: if request.auth.uid == resource.data.authorUid || resource.data.visibleOn.hasAny([...public...])`. Клиент НЕ может подделать чужой UID — rule отклонит запрос. — [USER DECIDED 2026-04-21] "ок, используем Firebase Auth UID, tpovId не нужен"

9. **`Quest.picturePath: String?`** — относительный Firebase Storage path (как у Catalog). URL резолвится в data layer через существующий `StorageUrlResolver`. Invalidation Coil-кеша через `?v={version}` query param. — [USER DECIDED] "в модели квиза есть путь к картинке у сторедж фаербейза, его и берем"

10. **`Quest.averageRating: Float?`** — диапазон 0.0..3.0 с шагом 0.1 (3 звезды × 10 долей). Источник: `sum(ratings)/count`. `null` для квестов без оценок (DRAFT). — [USER DECIDED] "3 звезды, делятся на 10 частей каждая"

11. **`Quest.averageRatingCount: Int`** — количество оценщиков. Пишет сервер. — [DELEGATED: нужно для будущего отображения "5 оценок"]

12. **Section/Theme/Lesson domain model**: `{Entity}(id, parentId, title, order: Int, version: Long, contentsVersion: Long, lastModifiedAt: Long, archived: Boolean)`. Все поля обязательны для MVP. `order` — для сортировки учебной последовательности (урок 1 перед уроком 2). `archived` — soft-delete механизм как у Quest/Catalog (архив → local delete). — [USER DECIDED] "Все 4 уровня сразу" + [Codex fix #5] "delete semantics для nested через archived" + [Codex fix #9] "order обязательный MVP-поле для учебного контента"

13. **Question domain model с language и archived**: `Question(id: QuestionId, lessonId: LessonId, text: String, payload: String, language: String, order: Int, version: Long, lastModifiedAt: Long, archived: Boolean)`. Поле `language` закладывается в модель + Firestore + Room, но **без логики** (никаких фильтров по языку). Готово к multilanguage фиче. `payload` — сериализованное тело вопроса (ответы, варианты) для ADR-0003 schema. `archived` — как у предыдущих. Question — leaf, **нет `contentsVersion`**. — [USER DECIDED] "поле язык вопроса, пока давай его не трогать но можешь его интегрировать для полноты" + [Codex fix #5, #9]

#### Синхронизация (каскадная)

14. **Cascading sync algorithm** — процесс использует `lastModifiedAt` как курсор (bookmark по времени), углубляется через early-exit по `contentsVersion`:

**Курсоры** — хранятся через `SyncStateRepository.getCursor(collectionId) / setCursor(collectionId, value)`:
- `catalogsCursor` → `getCursor("catalogs")` — `Clock.System.now()` (freshTime), выставляется `CascadingSyncOrchestrator` после subtree success; единообразно со всеми 6 уровнями (Amendment "Cursor Advance Strategy" в 03-decisions.md)
- `questsCursor` → `getCursor("quests")`
- `sectionsCursor` → `getCursor("sections")`, `themesCursor`, `lessonsCursor`, `questionsCursor` — аналогично

**Phase-01 storage**: `InMemorySyncStateRepository` — курсоры в `MutableStateFlow<Map>` внутри процесса. При kill процесса теряются. Следующий sync стартует с cursor=0 для всех collections → тянет заново, но upsert-by-id идемпотентен → нет дубликатов.

**Future** — `RoomSyncStateRepository` (отдельная фича "sync rollback"): cursors persistent между рестартами + atomic resume mid-cascade. Domain interface не меняется — только data-layer swap.

**Алгоритм:**
- **Step 1 (catalogs):** `catalogs.where('lastModifiedAt', '>', catalogsCursor).get()`. Upsert by id. Если `archived=true` → local delete.
- **Step 2 (quests)** — если среди изменившихся в Step 1 каталогов есть те где `contentsVersion > local.contentsVersion`:
  - Собираем `changedCatalogIds = [id]` (≤30 за раз, батчим)
  - **Два независимых Firebase-запроса** (Firebase не поддерживает `array-contains-any` + `where-in` в одном запросе):
    - **Query A (мои квесты)**: `quests.where('authorUid', '==', currentUserUid).where('catalogId', 'in', changedCatalogIds).where('lastModifiedAt', '>', questsCursor)` — composite equality + in + range allowed
    - **Query B (публичные квесты)**: `quests.where('visibleOn', 'array-contains-any', availableShelves).where('lastModifiedAt', '>', questsCursor)` — БЕЗ `catalogId` фильтра (Firebase ограничение). Клиент локально фильтрует `catalogId in changedCatalogIds`.
  - Merge + dedupe by id (один quest может прийти из обоих запросов). Upsert в Room. Если `archived=true || visibleOn.isEmpty()` → local delete (details: Matrix 1 в State Matrix section).
- **Step 3 (sections)** — если среди обновлённых quests в Step 2 есть с `contentsVersion > local.contentsVersion`:
  - `sections.where('questId', 'in', changedQuestIds).where('lastModifiedAt', '>', sectionsCursor)` — батчи по 30 questId.
  - Upsert. Если `archived=true` → local delete.
- **Step 4 (themes), Step 5 (lessons), Step 6 (questions)** — аналогично, через `contentsVersion` trigger.
- **Leaf questions** — без `contentsVersion`; остальное как выше.

**Курсор обновляется после каждого Step:** `newCursor = Clock.System.now()` (самплируется один раз на начало syncCascade как `freshTime`). Это консервативная стратегия: items, модифицированные между `freshTime` и завершением fetch, будут повторно получены на следующем цикле, но никогда не пропущены. Применяется ко всем 6 уровням (Catalog, Quest, Section, Theme, Lesson, Question) единообразно — cursor advance управляется `CascadingSyncOrchestrator`. Обновление курсора — **ТОЛЬКО** при успешном завершении **всего subtree** уровня (subtree-atomic advance). При ошибке — курсор остаётся прежний, следующий sync повторит Step с начала уровня. (ADR Amendment: docs/features/home-and-my-quests/03-decisions.md §Amendment "Cursor Advance Strategy".)

**Почему не нужен client-side bootstrap для "старых детей нового parent"**: server каскадно обновляет `lastModifiedAt` всех потомков при изменении parent.visibleOn/archived (см. Invariant B выше). То есть когда admin делает quest публичным — сразу все его секции/темы/уроки/вопросы получают свежий `lastModifiedAt`, и клиент подтягивает их через обычный delta-запрос.

**Server invariants** (зафиксированы отдельно, реализуются Cloud Function / admin-tool вне этой фичи):

**Invariant A — Upward propagation (при любом write)**: сервер обязан поднять `lastModifiedAt` + `version` измененного документа, **а также** `lastModifiedAt` + `version` + `contentsVersion` всех его **предков** вверх по иерархии. Иначе клиент не увидит изменений через курсор.

**Invariant B — Downward cascade (при изменении визибилити/архива)**: если админ меняет `parent.visibleOn` / `parent.archived` / любое поле, влияющее на доступ — сервер обязан также поднять `lastModifiedAt` **всех потомков** вниз по иерархии (секции → темы → уроки → вопросы). Это гарантирует: когда юзер впервые получает доступ к квесту, его старые секции/вопросы имеют свежий `lastModifiedAt` и тянутся обычным delta-запросом. Без invariant B — "новый parent + старые descendants" проблема (client видит parent, но пустые children).

Invariant B — trade-off: один админский write → N серверных writes (N = potомки). Для больших квестов (1000 вопросов) — заметная нагрузка. Приемлемо MVP; будущая оптимизация через batch writes или per-subtree version.

— [USER DECIDED 2026-04-21] "при обновлении родителя обновляется видимость (и lastModifiedAt) потомков" — server cascading вариант A

15. **Access filter composition**: клиент собирает `availableShelves: Set<String>` из `UserStatsRepository` (роль/квалификация определяет доступные полки). Для MVP `availableShelves = {"home", "arena"}` при baseline роли; `tournament`/`tournamentFinal` гатируется future-фичей квалификаций. — [DELEGATED MVP baseline: home + arena общедоступно, tournament — отдельная фича]

16. **Sync triggers** (все дополняют существующий `SyncWorker` в `platform/android-services`):
    - Cold start приложения (existing catalog sync trigger расширяется)
    - WorkManager periodic (periodicity из user settings, legacy values 1/2/3/4/7/14/30 дней — MVP default 1 день)
    - Manual: `DrawerFooterAction.SyncNow` (уже существует в catalog-foundation)
    — [USER DECIDED] "На старте приложения + dev-кнопка + WorkManager периодически"

17. **Pre-production migration**: существующие Firestore `catalogs` документы удаляются админом и заливаются заново с полями `version/contentsVersion/archived`. Без server-side migration script. — [USER DECIDED] "Pre-production — удалить все catalogs + залить заново"

18. **Picture cache invalidation**: resolved HTTPS URL получает `?v={version}` суффикс при изменении `version` у Catalog/Quest. Coil видит новый URL-ключ → качает свежий файл. — [DELEGATED: стандартный cache-busting pattern]

19. **Offline behavior**: приложение работает полностью offline после первого fetch. Sync worker тихо фейлится при отсутствии сети, retry через exponential backoff (1s → 2s → 4s → ...), user никак не уведомляется. Room отдаёт cached data. — [USER DECIDED] "никакой, наше приложение изначально оффлайн, с настройкой первой и периодическим обновлением"

#### UI экраны

20. **Экран "Домашние квесты" (существующий — дизайн-полировка)**:
    - **ВАЖНО (Codex fix #1):** экран показывает **каталоги**, НЕ квесты. Сохраняется логика catalog-foundation — это grid каталогов с картинками и названиями. Название "Домашние квесты" историческое (унаследовано из legacy); фактически отображаемое содержимое = каталоги.
    - Сохраняется `LazyVerticalGrid(columns = Fixed(2))` из catalog-foundation
    - Улучшается типография: `titleMedium bold` (было `bodySmall`), `maxLines = 1, overflow = Ellipsis`, увеличенный `padding` (12dp gap между карточками, 16dp внутри карточки)
    - Corner radius 16dp (ADR-0010)
    - Layout: картинка `aspectRatio(1f)` сверху + `Text` под ней (тот же layout, только лучше читаемо)
    - Источник данных: `CatalogRepository.observeAll()` с фильтром `!archived`
    - Клик на карточку — **TODO placeholder** (navigation в детали каталога — future feature)
    — [USER DECIDED] "Текст под картинкой, но крупнее и bold" + "на экране домашние квесты все ок, только надо нормальный дизайн сделать"

21. **Экран "Мои квесты" (новый)**:
    - Сверху под TopBar: плашка с `CatalogSpinner` (M3 `ExposedDropdownMenuBox`, существует в `android/core/designsystem` после catalog-foundation)
    - Default выбор в спинере: "Все категории" (псевдо-пункт)
    - Ниже: `LazyColumn` с универсальными `QuestCard` карточками
    - FAB в правом нижнем углу: `Icons.Default.Add`, клик → `UnderConstructionScreen("Создание квеста в разработке")`
    - Empty state: centered placeholder `Icon + "У тебя нет квестов в этой категории. Создай новый!"` со стрелкой-индикатором на FAB
    - Источник данных: `QuestRepository.observeMyQuests(authorUid: String, catalogId: CatalogId?)` — `catalogId = null` означает "все категории". `authorUid` передаётся ViewModel'ом из `AuthRepository.observeUid()` (Decision #42 + Decision #46 — реактивно для mid-session login).
    - Фильтр логики (локальный, над Room Flow): `quest.authorUid == currentUserUid && !quest.archived && (catalogId == null || quest.catalogId == catalogId)`
    — [USER DECIDED] "каталог не в 2 ряда а в виде спинера плашкой под топбаром, а на самом экране уже список квизов которые относятся к данному юзеру, и выбраном каталоге" + "Да, FAB есть, но экран создания заглушка" + "Placeholder с текстом + FAB"

22. **Универсальный `QuestCard` компонент (новый)** в `android/core/designsystem/components/`:
    - Layout: title + звёзды в ряд по центру, картинка 40×40dp в правом верхнем углу
    - Typography: `titleMedium` для title, `maxLines = 1, overflow = Ellipsis`
    - Звёзды: 3 штуки, **синий цвет** (`#4285F4` из брендовой палитры ADR-0010), partial-fill поддержка (3 × 10 fractional positions, вычисление из `averageRating`)
    - Картинка: `AsyncImage` (Coil 3) с HTTPS-only URL, fallback `Icons.Default.QuestionMark`
    - Min touch target ≥ 48dp
    - `onClick: (QuestId) -> Unit` — TODO placeholder (navigation в детали/редактирование квеста — future feature)
    - Preview composables для empty/rated/unrated/long-title вариантов
    — [USER DECIDED] "Title + звёзды вместе в ряд по центру, картинка в правом углу" + "В легаси так было"

### Non-Functional Requirements

1. **Domain layer purity** — все `shared/feature/*/domain` модули без Android/SDK imports (invariant 1) — [DELEGATED: invariant]
2. **KMP-compatible** — domain и data слои в `commonMain` source set (ADR-0002) — [DELEGATED: PROJECT_STRUCTURE §4]
3. **Koin DI** — per-feature module для каждого нового модуля (ADR-0009) — [DELEGATED]
4. **Material3 + Compose** — новый QuestCard / MyQuestsScreen следуют ADR-0010 — [DELEGATED]
5. **Offline-first** — все reads идут из Room, sync только заполняет кеш. Нет direct Firestore reads из UI (ADR-0004) — [DELEGATED invariant]
6. **Room migration**: снести + пересоздать AppDatabase (bump version + `fallbackToDestructiveMigration()`). Pre-production. — [USER DECIDED]
7. **Test coverage**: Walking Skeleton domain tests (phase-01 source of truth) + data-layer integration tests (sync mapper round-trip, upsert behavior) + Compose preview tests — [DELEGATED standard project pattern]
8. **Firestore cost control**: cascading sync использует `lastModifiedAt > cursor` filter + `array-contains-any` для ≤10 shelves за запрос → reads линейно растут только от изменений, не от размера коллекции. `version` остаётся в модели для upsert monotonicity (версия обновляется ↔ в Room пишется только если server.version > local.version), но delta-pull — строго по `lastModifiedAt`. — [DELEGATED инфраструктура]

## Scope

### In Scope

#### Sync state architecture seam (NEW — phase-01)

- `SyncStateRepository` interface в `shared/core/sync/domain/` (либо отдельный `sync-state` submodule)
- Phase-01 — `InMemorySyncStateRepository` implementation (stub, ничего не сохраняет)
- Tests: 3-5 scenarios покрывают basic state transitions (getCursor default=0, setCursor updates, markCascadeInProgress/Completed, getPendingCascades returns active)
- **Не меняется**: domain signatures когда в будущем data-layer implementation заменит stub на Room-based storage

#### Domain layer (Walking Skeleton Variant Y)
- **Расширение существующего** `shared/core/catalog/domain/model/Catalog.kt`:
  - Добавить `version: Long = 1`, `contentsVersion: Long = 0`, `archived: Boolean = false`
  - Обновить `CatalogRepository` — сохраняется signature, но поведение `refreshFromRemote` становится delta-based (см. data layer)
- **Новый** `shared/feature/app-shell/domain/repository/AuthRepository.kt` (Decision #42):
  - `interface AuthRepository { suspend fun currentUid(): String?; fun observeUid(): Flow<String?> }`
  - Domain types only (нет Firebase SDK), реализация в data-layer оборачивает существующий `authUidFlow` из `AppApplication.kt:41`
  - `FakeAuthRepository(initialUid: String?)` в `commonTest/fake/` с `signIn(uid)`, `signOut()` test helpers
  - Используется `MyQuestsViewModel` (через DI) для получения current Firebase UID для `QuestRepository.observeMyQuests(uid, catalogId?)`
- **Новый** `shared/feature/quest/domain/`:
  - `model/QuestId.kt` — value class
  - `model/Quest.kt` — data class с invariants
  - `repository/QuestRepository.kt` — `observeMyQuests(authorUid: String, catalogId: CatalogId?)`, `observeByShelf(shelf: String)`, `getById`, `refreshFromRemote(currentUserUid: String, availableShelves: Set<String>, catalogIdsToSync: Set<CatalogId>, cursor: Long)`
  - `use_case/ObserveMyQuestsUseCase.kt`
  - ~~`use_case/ObserveHomeQuestsUseCase.kt`~~ — **УДАЛЁН** (Codex fix #1: "Домашние квесты" экран = каталоги, не quest-list; use case не нужен в phase-01)
  - `use_case/SyncQuestsUseCase.kt`
- **Новые** `shared/feature/{section,theme,lesson,question}/domain/` с аналогичной структурой (минимальные модели, repository с `observeByParent`, `refreshByParents(parentIds: Set<Id>)`)
- **Новый** `shared/core/sync/CascadingSyncOrchestrator.kt` — recursive orchestrator (Decision #49):
  - **`enum class SyncLevel`** (не sealed — избегает forward-reference проблемы с `object` внутри sealed; Codex Round 7 W1 fix):
    ```kotlin
    enum class SyncLevel {
        Catalog, Quest, Section, Theme, Lesson, Question;

        val next: SyncLevel? get() = when (this) {
            Catalog -> Quest
            Quest -> Section
            Section -> Theme
            Theme -> Lesson
            Lesson -> Question
            Question -> null  // leaf
        }

        val collectionId: String get() = when (this) {
            Catalog -> "catalogs"
            Quest -> "quests"
            Section -> "sections"
            Theme -> "themes"
            Lesson -> "lessons"
            Question -> "questions"
        }
    }
    ```
  - `class CascadingSyncOrchestrator(catalogRepo, questRepo, sectionRepo, themeRepo, lessonRepo, questionRepo, syncStateRepo: SyncStateRepository) : Syncable`
  - `suspend fun syncCascade(level: SyncLevel, parentIds: Set<String>): Result<Unit>` — рекурсия только для тех parent где `contentsVersion > local.contentsVersion`. Cursor читается через `syncStateRepo.getCursor(level.collectionId)`, обновляется при успехе.
  - Special case для `SyncLevel.Quest` — split query A (own) + B (public) с merge/dedupe (Decision #40, FR#14 Step 2)
  - `override suspend fun sync(): Result<Unit>` = `syncCascade(SyncLevel.Catalog, emptySet())`
  - Регистрация в `syncModule` как `single<Syncable>(named("cascading"))` или добавление в `List<Syncable>`
- **In-memory fakes** для всех repositories — `commonTest/fake/`
- **JVM tests** (phase-01 source of truth): каждое правило из Feature Domain Contract + каждая ячейка State Matrix + domain test scenarios → `@Test` методы в `commonTest/`

#### Data layer
- **Изменение существующего** `shared/core/catalog/data/CatalogRepositoryImpl.kt`:
  - `refreshFromRemote` → delta-based через `where('lastModifiedAt', '>', cursor)` вместо `fetchAll + replaceAll` (cursor читается из `SyncStateRepository.getCursor("catalogs")` — signature interface не меняется). `version` остаётся для upsert monotonicity (см. Decision #15 superseded by #31).
  - `upsertByIdIfNewerVersion` вместо `replaceAll`
  - Обработка `archived=true` → `local.deleteById`
- **Изменение существующего** `shared/core/persistence/CatalogEntity.kt` + `CatalogDao`:
  - Добавить поля `version`, `contentsVersion`, `lastModifiedAt`, `archived` (Decision #53)
  - `upsertByIdIfNewerVersion(entity)` — compare-and-swap UPSERT
  - `deleteById(id)` для tombstones
  - `observeAll()` query обновить: `SELECT * FROM catalogs WHERE archived = 0 ORDER BY id ASC` (Decision #52 — фильтр в DAO)
  - 5 новых DAO (`QuestDao, SectionDao, ThemeDao, LessonDao, QuestionDao`) — все используют `WHERE archived = 0` в `observe*` queries
- **Новые** `shared/feature/quest/data/`:
  - `QuestEntity.kt` в `shared/core/persistence/` (`@Entity(tableName="quests")`)
  - `QuestDao.kt` с `observeMyQuests`, `observeByVisibility`, `upsertByIdIfNewerVersion`, `deleteById`
  - `QuestRemoteDataSource.kt` (interface) + `QuestRepositoryImpl.kt` + `QuestLocalDataSource.kt`
  - Mappers: `QuestEntity ↔ Quest` (domain), `QuestDto → QuestEntity`
  - `QuestDto.kt` — pure Kotlin DTO (как `CatalogDto`)
- **Новые** Section/Theme/Lesson/Question data stacks (аналогично Quest)
- **Koin** `catalogDataModule` обновить, `questDataModule`, `sectionDataModule` ... — новые per-feature modules

#### Platform layer
- **Новые** в `platform/firebase/{quest,section,theme,lesson,question}/`:
  - `Firebase{Entity}RemoteDataSource.kt` — Firestore queries с `where('version', '>', ...)` + `array-contains-any` для quests
  - `Firestore{Entity}DtoMapper.kt` — `DocumentSnapshot → {Entity}Dto`
  - `Firebase{Entity}Module.kt` — Koin bindings
- **Изменение существующего** `platform/firebase/catalog/FirebaseCatalogRemoteDataSource.kt`:
  - `fetchAll()` → `fetchChangedSince(cursor: Long): List<CatalogDto>` (delta query через `where('lastModifiedAt', '>', cursor)`)
- **Изменение существующего** `platform/android-services/SyncWorker.kt`:
  - Добавить steps для quest/section/theme/lesson/question sync
  - Orchestrate каскадный process (catalog → quest → section → theme → lesson → question)
  - Exponential backoff retry (`WorkRequest.setBackoffCriteria`)

#### Presentation / UI

**Новые Decompose Components (Decision #51) — расширенный phase-01 scope**:
1. `MyQuestsComponent` — для нового экрана "Мои квесты" (FAB + spinner + LazyColumn)
2. `HomeQuestsComponent` — параллельный refactor существующего "Домашние квесты" экрана (заменяет `AppShellScreen.CatalogGridSection` direct Repository inject — pre-existing нарушение `use-cases.md`)

Оба компонента создаются вместе в phase-01. Обоснование: единый Decompose-paradigm в проекте + закрытие архитектурного долга.

- **Новый** `android/feature/quest/presentation/` (Decision #51 — Decompose Component):
  - `MyQuestsScreen.kt` + `MyQuestsComponent.kt` (Decompose Component, не AndroidX ViewModel)
  - `interface MyQuestsComponent { val state: StateFlow<MyQuestsUiState>; fun onCatalogSelected(id: CatalogId?); fun onCreateQuestClick() }`
  - `class DefaultMyQuestsComponent(componentContext: ComponentContext, authRepo: AuthRepository, observeMyQuests: ObserveMyQuestsUseCase, observeCatalogs: ObserveCatalogsUseCase, navigator: Navigator) : MyQuestsComponent, ComponentContext by componentContext`
  - lifecycle через essenty (`coroutineScope(Dispatchers.Main + lifecycle)` или `instanceKeeper`)
  - Koin DI module — `questPresentationModule` с `factory<MyQuestsComponent> { params -> DefaultMyQuestsComponent(params.get<ComponentContext>(), get(), get(), get(), get()) }` со ссылками на `appShellDataModule` (для `AuthRepository`) + `questDomainModule` + `catalogDomainModule`
- **Новый** `HomeQuestsComponent.kt` (Decision #51 — параллельный refactor):
  - `interface HomeQuestsComponent { val state: StateFlow<HomeQuestsUiState>; fun onCatalogClick(id: CatalogId) }`
  - `class DefaultHomeQuestsComponent(componentContext: ComponentContext, observeCatalogs: ObserveCatalogsUseCase) : HomeQuestsComponent, ComponentContext by componentContext`
  - Заменяет `AppShellScreen.CatalogGridSection` (`AppShellScreen.kt:319`) — pre-existing нарушение `use-cases.md` (Composable инжектит `CatalogRepository` напрямую через `koinInject()`)
  - Локация: либо `android/feature/quest/presentation/` (consistent с MyQuestsComponent), либо отдельный `android/feature/home-quests/presentation/` (design phase решает)
- **Изменение существующего** `android/feature/app-shell/presentation/ui/AppShellScreen.kt`:
  - Заменить `LocalConfig.MyQuestsRoot` branch (сейчас `CatalogGridSection`) на `MyQuestsScreen(component = ...)` (реальный экран)
  - Заменить `LocalConfig.HomeQuestsRoot` branch (`CatalogGridSection` direct Repository inject) на `HomeQuestsScreen(component = ...)` через `HomeQuestsComponent` (Decision #51 — параллельный refactor)
  - Удалить `CatalogGridSection` Composable (`AppShellScreen.kt:319-329`) — заменён `HomeQuestsScreen`
  - Добавить branch `LocalConfig.QuestCreateRoot → UnderConstructionScreen("Создание квеста в разработке")` (Decision #41)
  - Components создаются в `LocalTabComponent` parent — children получают через `LocalScreenComponent` sealed (новые subtypes `MyQuests(component: MyQuestsComponent)` и `HomeQuests(component: HomeQuestsComponent)`)

#### Navigation (Decision #41 — FAB destination)

- **Изменение существующего** `shared/feature/app-shell/domain/model/TabConfig.kt`:
  - Добавить `data object QuestCreateRoot : LocalConfig` в sealed children
- **Изменение существующего** `shared/feature/app-shell/domain/model/Destination.kt`:
  - Добавить `data object OpenQuestCreate : Destination` (по аналогии с существующим `OpenDesignCatalog`)
- **Изменение существующего** `android/feature/app-shell/presentation/component/DefaultRootComponent.kt`:
  - Обработка `Destination.OpenQuestCreate` в `onDestination()` — push `LocalConfig.QuestCreateRoot` в локальный stack
  - `AppShellTransitions.navigate()` расширяется case'ом `OpenQuestCreate → setLocalStack(NavStack.push(QuestCreateRoot))`
- **Изменение существующего** `android/feature/app-shell/presentation/ui/labels/Labels.kt`:
  - Добавить ветку `LocalConfig.QuestCreateRoot → "Создание квеста"` в exhaustive when для `TabConfig.displayName`
- FAB callback в `MyQuestsScreen`: `onCreateQuestClick = { navigator.goTo(Destination.OpenQuestCreate) }`
- **Изменение существующего** `android/core/designsystem/components/CatalogGrid.kt`:
  - Typography update: `bodySmall` → `titleMedium bold`
  - `maxLines = 1, overflow = Ellipsis`
  - Corner radius 16dp
  - Padding adjustments
- **Новый** `android/core/designsystem/components/QuestCard.kt` + `QuestDisplayItem.kt` model:
  - Универсальная карточка (title + 3 stars + corner picture)
  - Preview variants
- **Новый** `android/core/designsystem/components/StarRating.kt`:
  - Partial-fill 3-star rating component, синий цвет, 0..3.0 step 0.1
  - Preview variants (0, 0.5, 1.5, 2.7, 3.0, null)

#### Firestore schema (new + modified)

Documents:
- `catalogs/{catalogId}` — extended: `{ name, picturePath, version: Long, contentsVersion: Long, archived: Boolean }`
- `quests/{questId}` — new: `{ catalogId, authorUid (Firebase UID String), title, picturePath, visibleOn: Array<String>, averageRating: Double?, averageRatingCount: Int, version, contentsVersion, archived, lastModifiedAt }`
- `sections/{sectionId}` — new: `{ questId, title, order, version, contentsVersion, archived, lastModifiedAt }`
- `themes/{themeId}` — new: `{ sectionId, title, order, version, contentsVersion, archived, lastModifiedAt }`
- `lessons/{lessonId}` — new: `{ themeId, title, order, version, contentsVersion, archived, lastModifiedAt }`
- `questions/{questionId}` — new: `{ lessonId, text, payload, language, order, version, archived, lastModifiedAt }`

Composite indexes (Firebase Console ручная регистрация):
- `quests`: `authorUid ASC, catalogId ASC, lastModifiedAt ASC` — для Query A ("мои квесты в изменившихся каталогах")
- `quests`: `visibleOn ARRAY, lastModifiedAt ASC` — для Query B ("публичные квесты", без catalogId т.к. Firebase не поддерживает `array-contains-any + where-in` в одном запросе)
- `sections`: `questId ASC, lastModifiedAt ASC`
- `themes`: `sectionId ASC, lastModifiedAt ASC`
- `lessons`: `themeId ASC, lastModifiedAt ASC`
- `questions`: `lessonId ASC, lastModifiedAt ASC`
- `catalogs`: `lastModifiedAt ASC` (single-field index — auto)

Security rules (`firestore.rules` addition) — единая модель для всех новых коллекций:

```js
match /quests/{questId} {
  // Read: свои квесты (authorUid == me) ИЛИ публичные (visibleOn имеет хотя бы один public shelf)
  allow read: if request.auth != null
    && (resource.data.authorUid == request.auth.uid
        || resource.data.visibleOn.hasAny(['home', 'arena', 'tournament', 'tournamentFinal']));
  // Create: только свои (request.resource.data.authorUid должен == request.auth.uid)
  allow create: if request.auth != null
    && request.resource.data.authorUid == request.auth.uid;
  // Update/Delete: только owner
  allow update, delete: if request.auth != null
    && resource.data.authorUid == request.auth.uid;
}

// Nested collections (sections, themes, lessons, questions) — MVP: admin-only write + read-any-auth'd
// Future: cascade ownership check через Cloud Function (read = parent quest read; write = parent quest write)
match /sections/{sectionId} {
  allow read: if request.auth != null;                      // any authenticated (MVP)
  allow write: if isAdmin();                                 // admin only — user-facing write вне scope этой фичи
}
match /themes/{themeId}   { allow read: if request.auth != null; allow write: if isAdmin(); }
match /lessons/{lessonId} { allow read: if request.auth != null; allow write: if isAdmin(); }
match /questions/{questionId} { allow read: if request.auth != null; allow write: if isAdmin(); }

// catalogs — unchanged from catalog-foundation (public read, admin write)

function isAdmin() {
  return request.auth != null
    && get(/databases/$(database)/documents/users/$(request.auth.uid)).data.qualifications.admin >= 100;
}
```

**Key security insight**: клиент делает `quests.where('authorUid', '==', me)` — если `me != request.auth.uid`, rule отклоняет весь запрос. Клиент НЕ может подделать чужой UID. Публичные квесты доступны всем authenticated через `visibleOn.hasAny(...)` проверку.

**Nested write restricted to admin** — это сознательное решение для phase-01: user-facing write (создание вопросов) реализуется в отдельной фиче "create quest" через server-side validation / Cloud Function. В этой фиче client только **читает** nested. Таким образом nested writes не могут быть abuse'нуты для bypass cascade ownership.

### Explicitly Out of Scope

- **Drafts (empty visibleOn + authorUid==me)** — quest с пустым `visibleOn`, созданный текущим юзером, сейчас **локально удаляется как любой пустой visibleOn**. Draft-режим (хранить локально, показывать в "Мои квесты") — **вне scope этой фичи**. Будет реализовано в create-quest фиче через отдельный механизм "заявка на создание" (локальный write → очередь → server). — [USER DECIDED] "при создании мы создаем заявку на создание, это отдельный механизм"
- **Создание квеста UI** — FAB ведёт на `UnderConstructionScreen`. Form + validation + upload — отдельная фича
- **Редактирование квеста / тем / уроков / вопросов** — отдельная фича
- **Прохождение квеста (play/session)** — отдельная фича (ADR-0005 QuizSessionMode)
- **Админ-tool для CRUD** — отдельная фича (server/admin-tools)
- **Листинг arenaQuests / tournamentQuests экраны** — эта фича заложила visibility-поля и sync-инфраструктуру; сами экраны — отдельно
- **Рейтинг / оценивание квестов** — UI для оценки не в scope; только чтение `averageRating` на карточке
- **Cloud Function для propagation `lastModifiedAt` + `contentsVersion` вверх по иерархии** — MVP полагается на admin-tool/manual. Автоматизация — future work.
- **Multilanguage logic** — `Question.language` поле заложено, но не используется в фильтрах / UI
- **Cascade delete поверх client** — если quest удалён, theme/lesson/question остаются orphan в Room до следующего sync. Orphan cleanup — future job (background worker)
- **Firestore realtime listeners** — только pull-based sync (ADR-0004 допускает listeners, но MVP обходится без них)
- **Tombstones retention server-side** — admin сам решает когда физически удалять `archived=true` записи
- **Pull-to-refresh gesture** — MVP использует dev-кнопку + periodic worker; pull-to-refresh — polish future
- **Переосмысление ADR-0005 (PublicationShelf enum → visibleOn Set)** — формально ADR-0005 не обновляется в этой фиче; реальная модель отличается от документа. Отдельной архитектурной задачей.
- **Room `@Migration`** — pre-production, destructive migration достаточно

#### Cleanup tasks (in scope phase-01 — Decision #44)

- **Удалить пустые `quiz/` модули**:
  - `shared/feature/quiz/domain` — удалить директорию, убрать `include(":shared:feature:quiz:domain")` из `settings.gradle.kts:47`
  - `shared/feature/quiz/data` — то же, убрать `:48`
  - `android/feature/quiz/presentation` — то же, убрать `:71`
- **Удалить placeholder Quest в `shared/core/catalog/domain`**:
  - `shared/core/catalog/domain/model/Quest.kt` (placeholder + QuestId duplicate)
  - `shared/core/catalog/domain/repository/QuestRepository.kt` (placeholder с одним методом save)
  - `shared/core/catalog/domain/use_case/CreateQuestUseCase.kt` (использует placeholder)
  - `shared/core/catalog/domain/src/commonTest/.../fake/FakeQuestRepository.kt` (для placeholder)
  - `shared/core/catalog/domain/src/commonTest/.../QuestCatalogLinkTest.kt` (тест placeholder)
  - `CatalogDomainModule.kt:8` комментарий "CreateQuestUseCase removed: ..." можно убрать после cleanup
- Все ссылки на placeholder `Quest`/`QuestRepository` удаляются вместе. Оставшийся (канонический) Quest — в `shared/feature/quest/domain/model/Quest.kt`.

#### Coil version bump (Decision #43)

- **Изменение** `gradle/libs.versions.toml:44`:
  - `coil3 = "3.1.0"` → `coil3 = "3.4.0"` (соответствие ADR-HLA-06)
- Breaking changes Coil 3.4.0 (web-research findings):
  - `AsyncImagePainter.state` теперь `StateFlow` — collect через `painter.state.collectAsState()`
  - `modelEqualityDelegate` removed — использовать composition local `LocalAsyncImageModelEqualityDelegate`
  - File last write timestamp не добавляется в cache key by default — restore через `.addLastModifiedToFileCacheKey(true)` если нужно
- Текущее использование (`CatalogGrid.kt:71` — `AsyncImage(model = safeUrl, ...)`) breaking changes не задевают
- `?v={version}` URL pattern работает в обеих версиях (URL-based cache key — different URL = different cache entry)

## User Decisions

| # | Question | Answer | Impact on Design |
|---|----------|--------|-----------------|
| 1 | Feature split (3 фичи vs 1)? | Одна большая фича | Единый 0-spec, single Walking Skeleton |
| 2 | Версионирование catalog? | Per-item version | `Catalog.version: Long`, delta-sync |
| 3 | Quest domain полнота? | Минимум (MVP) | id, catalogId, authorUid, title, picturePath, visibleOn, averageRating, version, contentsVersion, lastModifiedAt, archived |
| 4 | Quest design карточки? | Title + звёзды по центру, картинка в правом углу | Universal `QuestCard` в designsystem |
| 5 | Layout QuestCard? | Title + звёзды в ряд по центру | Legacy-паттерн, узнаваемый для юзеров |
| 6 | Звёзды — сколько и точность? | 3 звезды × 10 долей | `averageRating: Float?` 0.0..3.0 шаг 0.1 |
| 7 | Картинка квеста — откуда? | Своё поле picturePath | Domain model has picturePath, resolved via StorageUrlResolver |
| 8 | Rating source? | `averageRating: Float?` поле | Сервер вычисляет и пишет; null для DRAFT |
| 9 | FAB "+"? | Есть, ведёт на UnderConstructionScreen | MVP: FAB placeholder для создания |
| 10 | Empty state "Мои"? | Placeholder + CTA на FAB | Icon + текст + стрелка |
| 11 | Visibility model? | Набор булеанов / массив visibleOn | `visibleOn: Array<String>` в Firestore |
| 12 | Catalog удаление? | Soft flag archived + local delete | `Catalog.archived: Boolean` |
| 13 | Quest удаление? | Пустой visibleOn → delete local | Cleanup logic при sync |
| 14 | Nested sync (questions)? | Parent-level counter `contentsVersion` | Cascading sync pattern |
| 15 | ~~Sync diff — version или +updatedAt?~~ | **SUPERSEDED by Decision #31 (2026-04-21)**: используем `lastModifiedAt: Long` как delta cursor. `version` остаётся для upsert monotonicity safety. | См. Decision #31 |
| 16 | Firestore layout — flat или subcollections? | Flat + parentId | 6 top-level collections |
| 17 | Quest scope deep — все 4 уровня? | Все 5 уровней (с Section) | 5 новых feature-модулей |
| 18 | Раздел между Quest и Theme? | Да, обязательный | 6-уровневая иерархия |
| 19 | Public quests — как фильтруем? | Server filter via visibleOn array-contains-any + authorUid==me | 2 параллельных query в sync |
| 20 | ADR-0005 пересмотр? | Нет, оставляем enum как документ | Реальная модель — visibleOn Set, ADR не трогаем |
| 21 | ADR-0004 contentsVersion в Syncable? | Нет, per-entity own field | Base Syncable остаётся без contentsVersion |
| 22 | Question.language? | Закладываем поле, без логики | Domain + Firestore + Room, ready for future |
| 23 | Sync trigger? | Cold start + WorkManager + dev-кнопка | Расширение existing SyncWorker |
| 24 | Миграция catalog? | Pre-production: админ удаляет и заливает заново | Нет server migration script |
| 25 | ~~Access filter — где authorUid?~~ | ~~`UserStatsRepository.currentAuthUid()`~~ | **SUPERSEDED by Decision #42 (2026-04-22)**: новый `AuthRepository.currentUid()` interface вместо расширения `UserStatsRepository`. См. Decision #42. |
| 26 | Room migration? | Destructive (снести + пересоздать) | `fallbackToDestructiveMigration()`, bump schema version |
| 27 | Offline UX? | Никакой, silent cached | Нет snackbar/banner; приложение offline-first by design |
| 28 | Каскад-sync глубина? | Тянем themes/lessons/questions только для тех quest что прошли visibility filter | `in`-фильтр батчами по 30 |
| 29 | Visibility Firestore shape? | Массив `visibleOn: Array<String>` | 1 query c array-contains-any для всех доступных shelves |
| 30 | authorId тип (tpovId vs Firebase UID)? | Firebase Auth UID (String) | Quest.authorUid: String; security rules проверяют request.auth.uid == resource.data.authorUid; tpovId не нужен |
| 31 | Sync курсор — version или время? | lastModifiedAt: Long (Unix millis) | Server ставит через FieldValue.serverTimestamp(); решает проблему "как узнать про новые элементы" |
| 32 | Home Quests экран — каталоги или квесты? | Каталоги (CatalogGrid остаётся, НЕ меняем на quest list) | Codex fix #1; ObserveHomeQuestsUseCase удаляется из scope |
| 33 | Delete nested entities (Section/Theme/Lesson/Question)? | archived: Boolean на каждой | Codex fix #5; consistent с Catalog/Quest; cascade sync при archived=true → local delete |
| 34 | order: Int — MVP обязательный? | Да, на Section/Theme/Lesson/Question | Codex fix #9; нужен для учебной последовательности (урок 1 перед 2) |
| 35 | Server propagation version+contentsVersion+lastModifiedAt? | Server обязан поднимать все три вверх по иерархии при любом write | Codex fix #2; иначе клиент не увидит nested изменения. Реализация — отдельная серверная задача |
| 36 | Retry/frontier state? | Stub `SyncStateRepository` interface + in-memory impl phase-01; future — Room-based rollback | Codex v2 fix #1; legacy `SyncInteractor.rollbackStructureData` как reference для будущей реализации |
| 37 | New parent + старые descendants? | Server cascading invariant B — при изменении parent.visibleOn/archived сервер обновляет lastModifiedAt всех потомков | Codex v2 fix #2; client-side bootstrap не нужен |
| 38 | Drafts (empty visibleOn + owner=me)? | Out of scope — в phase-01 даже owner-drafts локально удаляются. Draft UX — отдельная create-quest фича через "заявку на создание" | Codex v2 fix #6; create flow — отдельный механизм |
| 39 | Guest на "Мои квесты"? | Empty state (без login CTA), FAB остаётся, ViewModel не вызывает repo при uid=null | Codex v2 fix #8; локальные квесты future (create flow) |
| 40 | Firebase query split? | Query A (own quests: authorUid + catalogId in + lastModifiedAt >) + Query B (public quests: visibleOn array-contains-any + lastModifiedAt > БЕЗ catalogId) — merge/dedupe клиент | Codex v2 fix #4; Firebase не поддерживает array-contains-any + where-in в одном запросе |
| 41 | FAB "+" navigation API? | `Destination.OpenQuestCreate` (data object, как существующий `OpenDesignCatalog`); push `LocalConfig.QuestCreateRoot` в локальный stack | Research finding: `Navigator.goTo(LocalConfig.QuestCreateRoot)` из ранней версии spec — compile error (Navigator принимает Destination, не LocalConfig). Узко-специфичный variant согласован с pattern в Destination |
| 42 | UID acquisition path? | Новый `AuthRepository.currentUid(): String?` + `observeUid(): Flow<String?>` в `shared/feature/app-shell/domain/repository/`; data-impl оборачивает существующий `authUidFlow` из `AppApplication.kt:41` | Research: `UserStatsRepository.currentAuthUid()` из ранней spec — метод не существует, расширение interface ломает 4 fakes; UserStats модель без `uid`; новый interface чище архитектурно (cross-feature reuse: future create-quest, profile, sign-in/out) |
| 43 | Coil version? | Bump `gradle/libs.versions.toml:44` 3.1.0 → 3.4.0 **(deferred to phase-01 — backend-dev owns libs.versions.toml per scaffold ownership rule)** | Research: spec/ADR-HLA-06 ссылается на 3.4.0, реальный код использует 3.1.0. Bump согласует. Breaking changes (StateFlow, modelEqualityDelegate) текущее использование не задевают. Codex Round 6 NNN2: bump НЕ выполнен в spec phase — это implementation task |
| 44 | Cleanup пустых quiz/ модулей? | Удалить `shared/feature/quiz/{domain,data}` + `android/feature/quiz/presentation` из settings.gradle.kts. Удалить placeholder Quest+QuestRepository+CreateQuestUseCase+FakeQuestRepository+QuestCatalogLinkTest из `shared/core/catalog/domain` | Research: `quiz/` модули содержат только `.gitkeep`. Placeholder Quest в core/catalog ссылается на `quiz/`, реальный код в `quest/`. Cleanup упрощает ментальную модель и убирает name conflict (два `Quest` класса) |
| 45 | `Destination.OpenQuestCreate` push vs replace семантика? | Push (`NavStack.push(QuestCreateRoot)` поверх `MyQuestsRoot`) — отличается от `OpenDesignCatalog` (который replaces весь LOCAL stack). Spec фраза "по аналогии" имеет в виду **наименование/паттерн** (data object Destination), не семантику transition | Codex Round 3 W1: чтобы избежать confusion. Add `AppShellTransitions.onOpenQuestCreate(state)` отдельный обработчик, не reuse `onOpenDesignCatalog` |
| 46 | `MyQuestsViewModel` — `currentUid()` vs `observeUid()`? | `observeUid()` (реактивный) — поддерживает mid-session login/logout (user логинится не покидая экрана → ViewModel автоматически переключается на `observeMyQuests(uid, catalogId)` flow) | Codex Round 3 W2: `currentUid()` one-shot не реагирует на смену auth state. `AuthRepository.observeUid()` уже в interface — используем его. `currentUid()` остаётся для use cases где flow не нужен (например, написать `Quest.authorUid` при create) |
| 47 | Re-tap FAB когда `QuestCreateRoot` уже active? | Guard в `AppShellTransitions.onOpenQuestCreate(state)`: если `state.localState.stack.active == LocalConfig.QuestCreateRoot` — no-op (уже на экране) | Codex Round 3 S3: без guard два tap'а создадут `backStack=[MyQuestsRoot, QuestCreateRoot]` + active=QuestCreateRoot снова → 2 нажатия Back для возврата. Guard сохраняет UX inv "FAB → один экран create" |
| 48 | Атомарность изменений в `Destination.kt` + `AppShellTransitions.kt`? | Изменяются в одном PR: добавление `Destination.OpenQuestCreate` (sealed) ломает exhaustive when в `AppShellTransitions.navigate()` — compile error. Phase plan документирует это как atomic constraint | Codex Round 3 W6: prevent split commits |
| 49 | Cascading sync orchestrator structure? | **Recursive** — одна функция `syncCascade(level: SyncLevel, parentIds: Set<String>)` рекурсивно идёт вглубь только для тех parent у кого `contentsVersion > local.contentsVersion`. Для этого `enum class SyncLevel` с 6 values (Catalog/Quest/Section/Theme/Lesson/Question) и computed properties `next: SyncLevel?` + `collectionId: String` через `when (this)`. Enum (не sealed class с object) — избегает forward-reference и init-order проблем. Глубина 6 — без stack overflow. Чище и расширяемее чем 6 hardcoded `Syncable`. Реализуется как один `CascadingSyncOrchestrator implements Syncable`. | User decision 2026-04-22; решает Open Question 8 в `1-research.md`. Codex Round 7 W1: enum pattern явно зафиксирован для избежания implementation impromptu |
| 50 | `?v={version}` URL для cache invalidation — какое version поле? | MVP использует `Quest.version` (всю сущность). При любом write quest (title/visibleOn/rating) → новый URL → новая загрузка картинки. **TODO для отдельной задачи**: выделить `pictureVersion` поле для granular invalidation (инкрементируется только при upload новой картинки) | User decision 2026-04-22; deferred polish task |
| 51 | `MyQuestsComponent` — AndroidX ViewModel или Decompose Component? Также HomeQuestsRoot — рефакторить или оставить pre-existing нарушение? | **Decompose Component** — consistent с уже существующим `DefaultRootComponent` и pattern Decompose-based навигации проекта. AndroidX ViewModel создал бы paradigm mix. Также **создать `HomeQuestsComponent` параллельно** — рефакторинг `AppShellScreen.CatalogGridSection` (pre-existing direct Repository injection в Composable) убирает архитектурный долг и делает оба экрана консистентными | User decision 2026-04-22; решает Open Questions 4 (ViewModel type) + 14 (CatalogGridSection refactor) в `1-research.md`. Расширяет phase-01 scope: `HomeQuestsComponent` создаётся вместе с `MyQuestsComponent` |
| 52 | `archived` фильтр — DAO query или Repository? | **DAO query** — `SELECT * FROM <table> WHERE archived = 0 ORDER BY ...`. Защита-инвариант: даже если sync не успел удалить archived row из Room (race condition), `observeAll()` его не вернёт. Эффективнее SQL filter чем Kotlin `.filter { !it.archived }`. Применяется ко всем 6 entities — Catalog, Quest, Section, Theme, Lesson, Question | User decision 2026-04-22; закрывает Open Question 7 в `1-research.md` |
| 53 | CatalogEntity extension strategy — extend или V2? | **Extend** — добавить 4 колонки (`version`, `contentsVersion`, `lastModifiedAt`, `archived`) в существующий `CatalogEntity`. Schema version 1→2, `fallbackToDestructiveMigration()`. Pre-production approach (Decision #26) — data loss приемлемо. Все 7 тестов с 4-arg `CatalogEntity(...)` constructor обновляются с named args + новыми полями (defaults в data class позволят минимизировать изменения) | User decision 2026-04-22; закрывает Open Question 11 в `1-research.md` (consistent с Decision #26) |
| 54 | Cloud Function для invariant A/B propagation — backend dependency? | **Documented hard dependency** в `06-api-contract.md` design phase. Out-of-scope client implementation, но **обязателен для working cascading sync MVP**. Без серверной реализации (admin-tool или Cloud Function trigger) клиент не получит nested изменения через cursor — фича функционирует только в degraded mode (catalog-level updates only). Backend track реализуется параллельно, не блокирует client phase-01 (клиент готов читать корректно структурированный server state) | User decision 2026-04-22; закрывает Open Question 12 в `1-research.md`. Backend ownership — отдельная серверная задача |
| 55 | Data-layer tests location — `commonTest` или `jvmTest`? | **`commonTest`** — следовать существующему pattern catalog (`shared/core/catalog/data/src/commonTest/`). KMP-pure, тесты переиспользуются на Android и JVM targets. App-shell использует `jvmTest` исторически (legacy convention) — design phase может зафиксировать `commonTest` как proper convention для всех новых data модулей | User decision 2026-04-22; закрывает Open Question 15 в `1-research.md` |

## Server-Side Context

### Firestore schema

**Collection `catalogs/{catalogId}`** (extended):
```json
{
  "name": "Опросы",
  "picturePath": "catalog-pictures/surveys.jpg",
  "version": 3,
  "contentsVersion": 17,
  "archived": false,
  "lastModifiedAt": 1714000000000
}
```

**Collection `quests/{questId}`** (new):
```json
{
  "catalogId": "surveys",
  "authorUid": "abc123xyzFirebaseUID",
  "title": "Мой квест о котах",
  "picturePath": "quest-pictures/q-uuid.jpg",
  "visibleOn": ["home", "arena"],
  "averageRating": 2.7,
  "averageRatingCount": 15,
  "version": 5,
  "contentsVersion": 3,
  "archived": false,
  "lastModifiedAt": 1714000000000
}
```

**Collection `sections/{sectionId}`** (new):
```json
{
  "questId": "q-uuid-1",
  "title": "Введение",
  "order": 0,
  "version": 1,
  "contentsVersion": 2,
  "archived": false,
  "lastModifiedAt": 1714000000000
}
```

**Collection `themes/{themeId}`** (new):
```json
{
  "sectionId": "s-uuid-1",
  "title": "Что такое коты",
  "order": 0,
  "version": 1,
  "contentsVersion": 1,
  "archived": false,
  "lastModifiedAt": 1714000000000
}
```

**Collection `lessons/{lessonId}`** (new):
```json
{
  "themeId": "t-uuid-1",
  "title": "История одомашнивания",
  "order": 0,
  "version": 1,
  "contentsVersion": 3,
  "archived": false,
  "lastModifiedAt": 1714000000000
}
```

**Collection `questions/{questionId}`** (new — leaf, no contentsVersion):
```json
{
  "lessonId": "l-uuid-1",
  "text": "Когда котов одомашнили?",
  "payload": "{\"type\":\"SingleChoice\",\"options\":[...],\"correctIndex\":0}",
  "language": "ru",
  "order": 0,
  "version": 1,
  "archived": false,
  "lastModifiedAt": 1714000000000
}
```

**`lastModifiedAt` — Unix timestamp (Long millis) в domain/Room модели; Firestore Timestamp в документе.**

**Firestore storage**: поле хранится как `Timestamp` (native Firestore тип), ставится через `FieldValue.serverTimestamp()` при любой записи. При mapping `DocumentSnapshot → Dto` клиент конвертирует `Timestamp.toDate().time` → `Long` millis.

**Domain / Room**: `Long` (Unix milliseconds since epoch) — простой тип, сортируемый.

Используется клиентом как курсор для delta-sync (`where('lastModifiedAt', '>', localCursorTimestamp)`). Firestore сравнение Timestamp vs Timestamp работает нативно — клиент передаёт `Timestamp(millis)` либо Firebase SDK автоматически конвертирует Long → Timestamp в `where` clause (зависит от версии SDK — в phase-01 уточнит data layer).

### Auth / rules

**`firestore.rules` additions:**
**Canonical security rules** — ЕДИНАЯ модель (см. также Scope→In Scope→Firestore rules block выше). Ниже повторение для Server-Side Context:

```js
match /catalogs/{catalogId} {
  allow read: if true;                                      // public — no auth required
  allow write: if isAdmin();
}

match /quests/{questId} {
  // Read: owner OR public (visibleOn has ≥1 public shelf)
  allow read: if request.auth != null
    && (resource.data.authorUid == request.auth.uid
        || resource.data.visibleOn.hasAny(['home', 'arena', 'tournament', 'tournamentFinal']));
  // Create: только свои; Update/Delete: только owner
  allow create: if request.auth != null
    && request.resource.data.authorUid == request.auth.uid;
  allow update, delete: if request.auth != null
    && resource.data.authorUid == request.auth.uid;
}

// Nested collections — admin-only write (user-facing write вне scope этой фичи)
match /sections/{sectionId}   { allow read: if request.auth != null; allow write: if isAdmin(); }
match /themes/{themeId}       { allow read: if request.auth != null; allow write: if isAdmin(); }
match /lessons/{lessonId}     { allow read: if request.auth != null; allow write: if isAdmin(); }
match /questions/{questionId} { allow read: if request.auth != null; allow write: if isAdmin(); }

function isAdmin() {
  return request.auth != null
    && get(/databases/$(database)/documents/users/$(request.auth.uid)).data.qualifications.admin >= 100;
}
```

### Side effects (server-side)

- При создании/обновлении quest через client (write) — сервер (Cloud Function / server/workers) **должен** инкрементировать `version` и `parent.contentsVersion` (в catalog). MVP: клиент сам пишет `version += 1` локально перед save (risky, но приемлемо для pre-production).
- При удалении тоже через flag `archived` или empty `visibleOn`.
- **Cloud Function для автоматической propagation contentsVersion вверх по иерархии** — future work. MVP: клиент / админ пишет `parent.contentsVersion` вручную.

### Server-Side Issues

**Issue 1: Propagation contentsVersion** — если server не автоматически поднимает `parent.contentsVersion` при изменении ребёнка, клиент не узнает про вложенные изменения. **Mitigation MVP**: client или admin-tool обновляет parent.contentsVersion вручную. **Proper fix**: Cloud Function trigger on write для каждой collection — future.

**Issue 2: Quest authorUid server validation** — MVP использует Firebase Auth UID через security rules. Rule: `request.resource.data.authorUid == request.auth.uid` (для create) и `resource.data.authorUid == request.auth.uid` (для update/delete). Клиент НЕ может создать/обновить quest с чужим UID. Безопасность полностью на rules.

## Search Criteria for Research

Эту секцию читает `/feature-research`.

### Обязательные search directions

1. **Catalog sync existing implementation** — `shared/core/catalog/data/CatalogRepositoryImpl.kt`, `CatalogLocalDataSource.kt`, `CatalogDao.kt`, `CatalogRemoteDataSource.kt`, `FirebaseCatalogRemoteDataSource.kt`. Нужно понять текущий `refreshFromRemote` (полный replaceAll) → будет переделан в delta. Catalog-foundation feature как основу.

2. **SyncWorker в platform/android-services** — какие steps уже есть, как добавить новые. `WorkManager` configuration, `CoroutineWorker`, backoff strategy.

3. ~~**UserStats & Firebase Auth UID source**~~ — **CLOSED 2026-04-22 by Decision #42**: новый `AuthRepository` interface вместо расширения `UserStatsRepository`. Research подтвердил: `UserStats` модель не содержит `uid`; `UserStatsEntity.uid` существует но не экспонируется через domain. `AppApplication.kt:41` уже создаёт `authUidFlow` через `FirebaseAuth.AuthStateListener` — `AuthRepositoryImpl` оборачивает этот flow без изменения существующей инфраструктуры.

4. **CatalogSpinner существование** — `android/core/designsystem/components/` — `CatalogSpinner.kt` (если был создан в catalog-foundation) либо только `CatalogGrid.kt`. В catalog-foundation spec упомянут `CatalogSpinner`, но фактически создан ли — проверить.

5. **AppDatabase structure** — `shared/core/persistence/AppDatabase.kt` — какие entities уже включены, какая текущая schema version. Нужно: bump schema version, добавить 5 новых Entity, setup `fallbackToDestructiveMigration()`.

6. **Existing Quest placeholder** — в catalog-foundation был упомянут minimal Quest placeholder. Проверить `shared/feature/quiz/` или `shared/feature/quest/` — что реально создано. Имя модуля — `quiz` (legacy) или `quest` (новое)? Spec использует `quest` — research уточнит, что использовать.

7. **Legacy `StructureDataLocal` reference** — `legacy/common/src/main/java/com/tpov/common/data/model/local/StructureDataLocal.kt` — для понимания legacy-паттерна versioning (уже изучен). Не используется в новом коде, только для reference при mapping решений.

8. **Navigation configuration** — `shared/feature/app-shell/domain/navigation/LocalConfig.kt` — есть ли `MyQuestsRoot` и как сейчас рендерится (placeholder через `UnderConstructionScreen` или `CatalogGridSection`). `android/feature/app-shell/presentation/ui/AppShellScreen.kt:298-315` — текущая маршрутизация.

9. **Koin modules pattern** — как organized `catalogDomainModule`, `catalogDataModule`, `firebaseCatalogModule`, `appShellDataModule`, `appShellPresentationModule`. Следовать pattern для новых `questDomainModule`, `questDataModule`, `firebaseQuestModule` и т.д. **Note**: domain use cases в проекте регистрируются либо в отдельном `*DomainModule` (как `catalogDomainModule`), либо непосредственно в `*PresentationModule` (как app-shell use cases в `appShellPresentationModule`). Phase-01 design выбирает один pattern для quest.

10. **Firebase Storage path conventions** — `StorageUrlResolver` / `FirebaseStorage` usage в catalog-foundation. Нужно установить path prefix для quests: `quest-pictures/{questId}.jpg`.

11. **Coil3 integration status** — ADR-HLA-06 говорит Coil 3.4.0. `libs.versions.toml` — проверить установлен ли. `android/core/designsystem/build.gradle.kts` — зависимость. Нужно для AsyncImage в QuestCard.

12. **Existing tests pattern** — `shared/core/catalog/data/src/commonTest/` — `CatalogRepositoryImplTest`, `CatalogFirstFetchIntegrationTest`, `CatalogWarmCacheIntegrationTest`, `FakeCatalogRepository` — как structured. Следовать pattern для Quest/Section/Theme/Lesson/Question tests.

13. **`DrawerFooterAction.SyncNow` implementation** — уже реализовано в catalog-foundation feature. Проверить `DefaultRootComponent.onSyncNow()`, `RootEvent.SyncStarted`, how triggered (не нужно переделывать, только использовать расширенный SyncWorker).

14. **Current quiz module state** — `shared/feature/quiz/domain/` + `data/` + `android/feature/quiz/presentation/` — пустой каркас по PROJECT_STRUCTURE. Нужно решить: использовать `quiz` или создать новый `quest`. Spec предполагает новые модули `shared/feature/quest/`, `section/`, `theme/`, `lesson/`, `question/`.

### Completeness check

- Research должен явно подтвердить или опровергнуть: есть ли в `shared/feature/quiz/domain/` какие-либо файлы Quest/placeholder, нужно ли создавать новый модуль vs переиспользовать `quiz`
- Firestore-индексы не создаются автоматически — research должен задокументировать весь список composite indexes для admin
- Migration path для существующего Firestore: research может не найти production-катaлогов (pre-production), но тесты на `:shared:core:catalog:data:jvmTest` могут заточены на текущий `fetchAll`-паттерн — их нужно обновить

## Primary User Journeys

### 1. Happy path: первый запуск (cold start) с сетью

- **Start**: юзер впервые открывает приложение, Room пуст, сеть есть
- **Trigger**: приложение стартует → `SyncWorker.enqueue(OneTime)` запускается при init
- **State changes**:
  - Step 1: Pull catalogs `where lastModifiedAt > 0` (первый sync — курсор нулевой) → все catalogs upsert
  - Step 2: Для каждого catalog с выросшим contentsVersion: pull quests `where (authorUid==myUid OR visibleOn array-contains-any {home,arena})` AND `lastModifiedAt > 0`
  - Step 3-6: Для quests/sections/themes/lessons с `contentsVersion > 0` → cascade
  - Юзер открывает "Домашние квесты" — `CatalogGrid` сразу рендерит из Room (может быть пустым пока sync не завершился) → Flow emit'ит обновление
  - Юзер открывает "Мои квесты" — spinner "Все категории" выбран по умолчанию → `LazyColumn` показывает все свои квесты
- **Expected result**: юзер видит каталоги с картинками в "Домашних", список своих квестов в "Моих". FAB видим.
- Decision: [USER DECIDED] "На старте приложения"

### 2. Happy path: warm cache (возврат в приложение)

- **Start**: Room уже содержит catalogs/quests после первого sync
- **Trigger**: юзер возвращается на экран
- **State changes**:
  - ViewModel подписывается на Room Flow → мгновенно получает cached data → UI рендерит без задержек
  - Periodic `SyncWorker` (если пришло время) тихо обновляет в фоне
- **Expected result**: экран моментальный, фоновое обновление прозрачно для юзера
- Decision: [DELEGATED: offline-first pattern из ADR-0004]

### 3. Offline-first: пользователь без сети

- **Start**: Room есть cached data, сеть пропала
- **Trigger**: юзер открывает приложение / экран
- **State changes**:
  - `observeAll()` / `observeMyQuests()` возвращает cached list
  - `SyncWorker` при периодическом запуске фейлится на Firestore requests → retry через exponential backoff
  - UI ничего не показывает — данные cached, юзер работает
- **Expected result**: опыт такой же как offline-first предполагает. Юзер не уведомляется об отсутствии сети.
- Decision: [USER DECIDED] "никакой, наше приложение изначально оффлайн"

### 4. Cascade sync: админ обновил catalog

- **Start**: админ изменил `catalogs/surveys.name` через admin-tool → сервер инкрементирует `catalogs/surveys.version` (например с 3 до 4)
- **Trigger**: клиент запускает next `SyncWorker` (periodic или dev-кнопка)
- **State changes**:
  - Step 1: `catalogs.where('version', '>', 3).get()` → вернёт `[catalogs/surveys with version=4]`
  - Upsert by id в Room — локальный catalog обновлён с новым `name`
  - Если `contentsVersion` тоже вырос → pull quests этого catalog (Step 2)
  - Иначе — останавливается на Step 1 для этого catalog
- **Expected result**: UI "Домашние квесты" через Flow обновляется автоматически, юзер видит новое название
- Decision: [DELEGATED: standard sync pattern из ADR-0004]

### 5. Cascade sync: админ добавил вопрос в урок

- **Start**: админ добавил `questions/{newId}` в `lessons/{someId}`. Сервер инкрементирует:
  - `lessons/{someId}.contentsVersion += 1`
  - `themes/{parentTheme}.contentsVersion += 1`
  - `sections/{parentSection}.contentsVersion += 1`
  - `quests/{parentQuest}.contentsVersion += 1`
  - `catalogs/{parentCatalog}.contentsVersion += 1`
  (propagation вверх — admin-tool или Cloud Function)
- **Trigger**: клиент запускает SyncWorker
- **State changes**:
  - Step 1: catalog contentsVersion вырос → идём в step 2
  - Step 2: quest contentsVersion вырос → идём в step 3
  - Step 3: section contentsVersion вырос → идём в step 4
  - Step 4: theme contentsVersion вырос → идём в step 5
  - Step 5: lesson contentsVersion вырос → pull questions `where('lessonId', 'in', [lessonId]).where('version', '>', lastQuestionVersion)`
  - Upsert новый question в Room
- **Expected result**: данные скачаны, локально доступны. UI для "Мои квесты" не изменился (экран только про quest-уровень), но при открытии урока — вопрос уже в Room
- Decision: [USER DECIDED] "начинаем с каталога, где есть обновления там и углубляемся"

### 6. Архивация: админ снимает catalog с публикации

- **Start**: админ выставляет `catalogs/old.archived = true`, version++
- **Trigger**: клиент sync
- **State changes**:
  - Pull catalog → видит `archived=true` → `local.deleteById(old)` → удаляет из Room
  - Все quests с `catalogId=old` становятся orphan (их catalog пропал) — остаются в Room, но показываются с placeholder caption "Unknown" (либо через `JOIN` UI пропускает)
- **Expected result**: catalog пропадает с экрана "Домашние квесты"
- Decision: [USER DECIDED] "я думаю мы просто скачиваем с него поля и типа там будет только поле архив активно значит оно уберется"

### 7. "Мои квесты": выбор каталога в спинере

- **Start**: юзер открыл "Мои квесты", spinner default "Все категории", LazyColumn показывает все свои квесты
- **Trigger**: юзер выбирает "Опросы" в spinner
- **State changes**:
  - `MyQuestsViewModel.onCatalogSelected(CatalogId("surveys"))` → updates `StateFlow<CatalogId?>`
  - combined Flow: `questRepository.observeMyQuests(catalogId=surveys)` filter'ит локально
  - LazyColumn обновляется — только квесты юзера в этом каталоге
- **Expected result**: список отфильтрован
- Decision: [USER DECIDED] "мы фильтруем локально для отображения в нужном экране"

### 8. FAB "+": создание квеста (заглушка)

- **Start**: юзер на "Мои квесты"
- **Trigger**: тап FAB `+`
- **State changes**:
  - `onCreateQuestClick()` navigates via `Navigator.goTo(Destination.OpenQuestCreate)` (Decision #41 — `Destination.OpenQuestCreate` data object, по аналогии с `OpenDesignCatalog`; push `LocalConfig.QuestCreateRoot` в локальный stack)
  - `UnderConstructionScreen("Создание квеста в разработке")` рендерится
- **Expected result**: юзер видит заглушку, back возвращает на "Мои квесты"
- Decision: [USER DECIDED] "Да, FAB есть, но экран создания заглушка"

### 9. Cold start offline (нет сети, пустой Room)

- **Start**: юзер **впервые** открывает приложение **без интернета** (сразу авиарежим или первый запуск вне сети). Room пустой.
- **Trigger**: open "Домашние квесты" или "Мои квесты"
- **State changes**:
  - `CatalogRepository.observeAll()` возвращает `emptyList` (ничего в Room)
  - `SyncWorker.enqueue` стартует, Firestore fails c NetworkException → `Result.retry()` → WorkManager откладывает с exponential backoff
  - UI отображает empty state
- **Expected result**:
  - "Домашние квесты": скелетон / empty placeholder "Каталоги пока не загружены"
  - "Мои квесты": empty state "У тебя нет квестов. Создай новый!" (FAB активен)
  - Юзер может работать с приложением; sync retry в background
  - Когда сеть появляется → WorkManager автоматически продолжает retry → данные подтягиваются → UI обновляется через Flow
- Decision: [USER DECIDED] "никакой, наше приложение изначально оффлайн"; [Codex fix #6]

### 10. Partial cascade fail + retry later

- **Start**: юзер авторизован, имеет cached каталоги и квесты, сеть нестабильная
- **Trigger**: `SyncWorker.run()` во время background periodic
- **State changes**:
  - Step 1 (catalogs) — успех: 3 catalogs upserted в Room. `catalogsCursor` обновляется `CascadingSyncOrchestrator` через `SyncStateRepository.setCursor("catalogs", freshTime)` — subtree-atomic (после успеха всего subtree каталога, не внутри репозитория)
  - Step 2 (quests) — fail: Firestore timeout на середине batch. `questsCursor` **НЕ обновляется** (setCursor вызывается только при успехе целого Step).
  - Worker returns `Result.retry()` — остаток cascade не выполняется
  - WorkManager backoff: ждёт exponential duration, потом запускает снова
- **Expected result (phase-01 — in-memory cursors)**:
  - Worker жив между retry'ами → курсоры сохраняются в `InMemorySyncStateRepository`:
    - catalogsCursor сдвинут (Step 1 done), questsCursor остался старый
    - Retry: Step 1 повтор с новым cursor → nothing to fetch; Step 2 с тем же старым questsCursor → повторяет те же quests + новые (upsert-by-id идемпотентен)
  - Worker убит (process death) → `InMemorySyncStateRepository` теряет всё:
    - Следующий sync стартует с cursor=0 для всех collections
    - Полный re-sync (дешёво в сравнении с corrupted state) → upsert-by-id идемпотентен → дубликатов не будет
    - Phase-01 приемлемо: pre-production, юзеров мало, worker обычно живёт до завершения
  - Юзер не видит ошибок; UI показывает cached data из Room
- **Future (sync-rollback фича с `RoomSyncStateRepository`)**: cursors persist через kill процесса → точный resume с того места где упали. Но это вне scope этой фичи.
- Decision: [USER DECIDED 2026-04-21] "для этого будем делать свою систему реверта ... пока заглушку и прокинем архитектурно"; [Codex fix #1, #6]

### 11. Guest на "Мои квесты"

- **Start**: юзер не авторизован (гость), `AuthRepository.observeUid()` эмитит `null`
- **Trigger**: юзер открывает "Мои квесты" из drawer
- **State changes**:
  - `MyQuestsViewModel.init()` подписывается на `AuthRepository.observeUid()` (Decision #46 — реактивно)
  - При первом emit `null` — ViewModel НЕ вызывает `QuestRepository.observeMyQuests(...)` (требует non-blank authorUid)
  - StateFlow emits `MyQuestsUiState(quests = emptyList(), isGuest = true)`
  - **Mid-session login**: если пользователь залогинится не покидая экран, `observeUid()` эмитит non-null UID → ViewModel автоматически переключается на `observeMyQuests(uid, catalogId)` flow и UI обновляется реактивно. Аналогично sign-out → возврат в guest state.
- **Expected result**:
  - LazyColumn пустой
  - Empty state placeholder "У тебя нет квестов. Создай новый!" (тот же что и для залогиненного юзера с 0 квестов — без login CTA)
  - Spinner работает (items из CatalogRepository, не зависит от auth)
  - FAB "+" виден, клик → `UnderConstructionScreen("Создание квеста в разработке")` (create flow отдельная фича; там будет logic "guest создаёт локально → snackbar после sync 'не залиты, войдите'" — вне scope)
- Decision: [USER DECIDED 2026-04-21] "локально он может делать все что угодно, просто можно сделать уведомление после синхронизации - мол созданные квесты не были загружены на сервер" — уведомление = create flow scope

## Feature Domain Contract

### Terms / Entities / Value Constraints

- `CatalogId` — `@JvmInline value class CatalogId(val value: String)`. Invariant: `value.isNotBlank()`. *(существует в catalog-foundation)*
- `Catalog` — `data class Catalog(id: CatalogId, name: String, picturePath: String?, pictureUrl: String?, version: Long, contentsVersion: Long, lastModifiedAt: Long, archived: Boolean)`. Invariants:
  - `name.isNotBlank()`
  - `picturePath == null || (picturePath.isNotBlank() && !picturePath.startsWith("https://") && !picturePath.startsWith("http://") && !picturePath.startsWith("gs://"))`
  - `version >= 1`
  - `contentsVersion >= 0`
  - `lastModifiedAt >= 0` (Unix millis)

- `QuestId` — `@JvmInline value class QuestId(val value: String)`. Invariant: `value.isNotBlank()`.
- `Quest` — `data class Quest(id: QuestId, catalogId: CatalogId, authorUid: String, title: String, picturePath: String?, pictureUrl: String?, visibleOn: Set<String>, averageRating: Float?, averageRatingCount: Int, version: Long, contentsVersion: Long, lastModifiedAt: Long, archived: Boolean)`. Invariants:
  - `title.isNotBlank()`
  - `authorUid.isNotBlank()` (Firebase Auth UID — strings не могут быть empty)
  - `picturePath` constraints (same as Catalog)
  - `visibleOn` — only values from `{"home", "arena", "tournament", "tournamentFinal", "archive"}`
  - `averageRating == null || (averageRating in 0.0f..3.0f)`
  - `averageRatingCount >= 0`
  - `version >= 1`, `contentsVersion >= 0`
  - `lastModifiedAt >= 0`

- `SectionId` — value class, `isNotBlank` invariant
- `Section` — `data class Section(id: SectionId, questId: QuestId, title: String, order: Int, version: Long, contentsVersion: Long, lastModifiedAt: Long, archived: Boolean)`. Invariants: `title.isNotBlank()`, `order >= 0`, `version >= 1`, `contentsVersion >= 0`, `lastModifiedAt >= 0`

- `ThemeId` — value class, `isNotBlank`
- `Theme` — `data class Theme(id: ThemeId, sectionId: SectionId, title: String, order: Int, version: Long, contentsVersion: Long, lastModifiedAt: Long, archived: Boolean)`. Same pattern.

- `LessonId` — value class, `isNotBlank`
- `Lesson` — `data class Lesson(id: LessonId, themeId: ThemeId, title: String, order: Int, version: Long, contentsVersion: Long, lastModifiedAt: Long, archived: Boolean)`. Same pattern.

- `QuestionId` — value class, `isNotBlank`
- `Question` — `data class Question(id: QuestionId, lessonId: LessonId, text: String, payload: String, language: String, order: Int, version: Long, lastModifiedAt: Long, archived: Boolean)`. **Leaf — нет `contentsVersion`**. Invariants:
  - `text.isNotBlank()`
  - `payload.isNotBlank()` (serialized JSON)
  - `language.isNotBlank()` (ISO 639-1 code; minimal validation)
  - `order >= 0`
  - `version >= 1`
  - `lastModifiedAt >= 0`

### Auth Repository (Decision #42)

- `AuthRepository` — interface в `shared/feature/app-shell/domain/repository/`:
  ```kotlin
  interface AuthRepository {
      /** One-shot snapshot of current Firebase Auth UID. Returns null for guest. */
      suspend fun currentUid(): String?
      /** Continuously observes UID. Emits new value on sign in / sign out. */
      fun observeUid(): Flow<String?>
  }
  ```

- **Phase-01 реализация** — `AuthRepositoryImpl` в `shared/feature/app-shell/data/`:
  - Получает `currentUidFlow: () -> Flow<String?>` через DI (тот же flow что уже прокидывается в `UserStatsRepositoryImpl`)
  - `currentUid()` = `currentUidFlow().first()` (no LOCAL_UID substitution — возвращает реальный Firebase UID или null)
  - `observeUid()` = `currentUidFlow()` напрямую
  - Регистрируется в `appShellDataModule` рядом с `UserStatsRepository`

- **Использование**:
  - `MyQuestsViewModel(auth: AuthRepository, ...)` — для получения UID для `QuestRepository.observeMyQuests(uid, catalogId?)`
  - Future create-quest feature — для записи `Quest.authorUid` при создании
  - Future profile / sign-in/out — реактивное обновление UI при смене auth state

- **FakeAuthRepository(initialUid: String? = null)** в `commonTest/fake/`:
  - `signIn(uid: String)` — устанавливает non-blank uid (test helper)
  - `signOut()` — устанавливает null

### Sync State (phase-01 stub + architectural seam)

- `SyncStateRepository` — interface в `shared/core/sync/domain` (либо аналогичном common module):
  ```kotlin
  interface SyncStateRepository {
      suspend fun getCursor(collectionId: String): Long  // 0 by default if absent
      suspend fun setCursor(collectionId: String, value: Long)
      suspend fun markCascadeInProgress(parentId: String, parentType: String, pendingChildIds: Set<String>)
      suspend fun markCascadeCompleted(parentId: String, parentType: String)
      suspend fun getPendingCascades(): List<PendingCascade>
  }

  data class PendingCascade(val parentId: String, val parentType: String, val pendingChildIds: Set<String>)
  ```

- **Phase-01 реализация** — in-memory stub: `InMemorySyncStateRepository` хранит всё в `MutableStateFlow<Map>`. При kill приложения всё теряется — sync в следующий раз стартует с нуля. Это **acceptable для MVP**.
- **Future фича "sync rollback"** — заменит stub на Room-таблицу `sync_state` + Legacy-подобный revert логики (из `SyncInteractor.rollbackStructureData`). Domain interface **не меняется** — только data-layer implementation.

**Назначение seam**: пока не реализован rollback, клиент просто продолжает нормально. При kill sync на середине — следующий запуск качает заново через курсор (может дать дубликаты writes Room, но это идемпотентно через upsert-by-id). Когда rollback будет добавлен — будет атомарный revert неконченного cascade.

### Business Rules / Invariants / Guards

1. **Version monotonicity (upsert safety guard)**: для любой entity при upsert-by-id в Room клиент принимает `dto` только если `dto.version > local.version`. Защищает от случайного downgrade (server stale response или race condition). **НЕ используется** для delta-pull (для delta — только `lastModifiedAt` cursor).
2. **Identity по id**: sync никогда не матчит по name/title — только по id.
3. **Catalog archived → cascade**: при `catalog.archived=true` локальный delete catalog. Quests с этим `catalogId` остаются в Room (orphan), обрабатываются на UI уровне через missing catalog lookup.
4. **Quest empty visibleOn → cascade**: при `quest.visibleOn.isEmpty()` локальный delete quest. Sections/themes/lessons/questions с этим parent остаются orphan.
5. **Author ownership (MVP via Firebase Auth)**: `Quest.authorUid == request.auth.uid` проверяется security rules (read/write). Клиент НЕ может подделать чужой UID. Для UI-фильтра "Мои квесты" клиент берёт свой UID через `AuthRepository.observeUid()` / `currentUid()` (Decision #42).
6. **Access filter composition**: `availableShelves` берётся из `UserStats` → roles. Для MVP: baseline `{"home", "arena"}` всегда; `tournament`, `tournamentFinal` — при наличии соответствующей квалификации (future gating).
7. **Cascade sync предикат**: клиент идёт на уровень N+1 **только если** `dto.contentsVersion > local.contentsVersion` для элемента уровня N.
8. **Batch limit**: `in`-фильтр для cascading sync — максимум 30 parent-ids за запрос (Firestore limit); клиент chunks parent-ids по 30.
9. **averageRating только чтение**: клиент никогда не пишет `averageRating` / `averageRatingCount` в Firestore. Это server-managed.
10. **Catalog CRUD — admin-only**: клиент никогда не пишет в `catalogs/*`. Server security rules enforce.
11. **Quest CRUD — MVP не реализован на клиенте**: FAB ведёт в placeholder. Future create flow должен проверять `averageRating=null`, `averageRatingCount=0`, `visibleOn=["home"]` по умолчанию.

### State / Decision Rules

Sync-решение для одного элемента (Catalog / Quest / Section / Theme / Lesson):

| Состояние local | dto.version | dto.contentsVersion (если есть) | dto.archived / dto.visibleOn | Действие |
|-----------------|-------------|----------------------------------|-------------------------------|----------|
| absent (нет в Room) | any | any | `archived=true` / empty | ignore (не создаём удалённого) |
| absent | any | any | `archived=false` / non-empty | insert |
| present, `local.version < dto.version` | > local | any | `archived=true` / empty | delete local |
| present, `local.version < dto.version` | > local | any | `archived=false` / non-empty | upsert |
| present, `local.version >= dto.version` | <= local | any | any | skip (already up-to-date) |
| present, after upsert, `dto.contentsVersion > local.contentsVersion` | — | > local | non-empty | **recurse into child level** |
| present, after upsert, `dto.contentsVersion <= local.contentsVersion` | — | <= local | non-empty | stop (children up-to-date) |

### Error / Recovery Rules

- **Network error при fetch**: Worker возвращает `Result.retry()` — WorkManager делает exponential backoff.
- **Firestore permission denied**: Worker логирует + `Result.failure()`, без retry (permanent).
- **Firestore in-filter >30**: client batch'ит — не должно возникать как ошибка.
- **Partial cascade fail** (catalog pull OK, quest pull fail): успешные upserts остаются в Room; worker помечает next sync нужным (state через WorkManager backoff).
- **Duplicate id от Firestore** (если админ ошибся) — Room `REPLACE` conflict strategy — второй с тем же id перетрёт первого.
- **Malformed dto** (отсутствуют обязательные поля) — mapper возвращает `null`, запись пропускается, log warning.

### Domain Test Scenarios (phase-01 source of truth)

**Value classes:**
1. GIVEN `CatalogId("")` WHEN construct THEN throws IllegalArgumentException
2. GIVEN `CatalogId("surveys")` WHEN construct THEN no exception
3. GIVEN `QuestId("")` WHEN construct THEN throws
4. GIVEN `QuestId("q1")` WHEN construct THEN no exception
5. Аналогично для SectionId, ThemeId, LessonId, QuestionId

**Catalog invariants (расширение — все конструкторы используют named args для ясности):**
6. GIVEN `Catalog(id, name, picturePath=null, pictureUrl=null, version=0L, contentsVersion=0L, lastModifiedAt=0L, archived=false)` WHEN construct THEN throws (version must be >= 1)
7. GIVEN `Catalog(id, name, picturePath=null, pictureUrl=null, version=1L, contentsVersion=-1L, lastModifiedAt=0L, archived=false)` WHEN construct THEN throws (contentsVersion >= 0)
8. GIVEN `Catalog(id, "Name", picturePath=null, pictureUrl=null, version=1L, contentsVersion=0L, lastModifiedAt=1000L, archived=false)` WHEN construct THEN no exception
9. GIVEN `Catalog(id, "Name", picturePath=null, pictureUrl=null, version=1L, contentsVersion=0L, lastModifiedAt=0L, archived=true)` WHEN construct THEN no exception (archived може быть true)
9b. GIVEN `Catalog(id, name, ..., lastModifiedAt=-1L, archived=false)` WHEN construct THEN throws (lastModifiedAt >= 0)

**Quest invariants (все конструкторы используют named args):**
10. GIVEN `Quest(id, catalogId, authorUid="", title="T", ..., version=1L, contentsVersion=0L, lastModifiedAt=0L, archived=false)` WHEN construct THEN throws (authorUid must not be blank)
11. GIVEN `Quest(id, catalogId, authorUid="uid-A", title="", ..., version=1L, contentsVersion=0L, lastModifiedAt=0L, archived=false)` WHEN construct THEN throws (title blank)
12. GIVEN `Quest(id, catalogId, authorUid="uid-A", title="T", picturePath=null, pictureUrl=null, visibleOn=setOf("invalid"), averageRating=null, averageRatingCount=0, version=1L, contentsVersion=0L, lastModifiedAt=0L, archived=false)` WHEN construct THEN throws (unknown shelf value)
13. GIVEN `Quest(id, catalogId, authorUid="uid-A", title="T", picturePath=null, pictureUrl=null, visibleOn=emptySet(), averageRating=null, averageRatingCount=0, version=1L, contentsVersion=0L, lastModifiedAt=0L, archived=false)` WHEN construct THEN no exception (empty visibleOn разрешён на создании — sync cleanup применяется в Matrix 1)
14. GIVEN `Quest(id, catalogId, authorUid="uid-A", title="T", picturePath=null, pictureUrl=null, visibleOn=setOf("home"), averageRating=-0.1f, averageRatingCount=0, version=1L, contentsVersion=0L, lastModifiedAt=0L, archived=false)` WHEN construct THEN throws (rating out of range)
15. GIVEN `Quest(..., averageRating=3.5f, ...)` WHEN construct THEN throws
16. GIVEN `Quest(..., averageRatingCount=-1, ...)` WHEN construct THEN throws (count >= 0)
17. GIVEN valid Quest (all fields valid) THEN constructs fine; `averageRating=null` и `averageRating=2.7` оба принимаются

**Section/Theme/Lesson/Question invariants:**
18. Section, Theme, Lesson с `order < 0` — throws
19. Question с `language=""` — throws
20. Question с `payload=""` — throws

**Repository contract tests (через fakes):**
21. GIVEN `FakeCatalogRepository` empty AND pull returns `[catalog(v=1)]` WHEN sync THEN observeAll emits `[catalog]`
22. GIVEN `FakeCatalogRepository` has `catalog(v=2)` AND pull returns `catalog(v=2)` (same version) WHEN sync THEN no change emitted (upsert skipped)
23. GIVEN `FakeCatalogRepository` has `catalog(v=1)` AND pull returns `catalog(v=3, archived=true)` WHEN sync THEN observeAll emits empty (local deleted)
24. GIVEN `FakeQuestRepository` empty AND pull filtered `authorUid="uid-A" OR visibleOn ∈ {home, arena}` returns `[myQuest, homeQuest]` WHEN sync THEN observeAll emits both
25. GIVEN `FakeQuestRepository` has `myQuest(v=1)` AND pull returns `myQuest(v=2, visibleOn=emptySet, archived=true)` WHEN sync THEN observeAll does not emit myQuest (deleted)
26. GIVEN `FakeQuestRepository` with 2 quests, one `authorUid="A"`, one `authorUid="B"` WHEN `observeMyQuests("A", catalogId=null)` collected THEN emits 1 quest
27. GIVEN same WHEN `observeMyQuests("A", catalogId=SOMECAT)` collected THEN emits only quest of that catalog owned by "A"

**Cascading sync tests:**
28. GIVEN catalog v=1 locally AND server catalog v=1 (same) WHEN sync runs THEN NO pull on quests (contentsVersion not checked since catalog unchanged)
29. GIVEN catalog v=1/cv=3 locally AND server v=1/cv=5 WHEN sync runs THEN pull quests for that catalog
30. GIVEN quest v=1/cv=2 AND server v=1/cv=3 WHEN sync runs AND catalog contentsVersion changed THEN pull sections for that quest
31. GIVEN quest v=1/cv=2 AND server v=1/cv=2 WHEN sync runs AND catalog contentsVersion changed THEN NO pull sections (quest unchanged)

**Use case tests:**
32. GIVEN `ObserveMyQuestsUseCase` invoked WHEN authorUid="A" AND 3 quests in repo (authorUids="A","A","B") THEN returns Flow emitting 2
33. ~~GIVEN `ObserveHomeQuestsUseCase`...~~ **УДАЛЕН** — Home screen показывает каталоги (`CatalogGrid`), не quests. См. Decision #32 и Walking Skeleton updated.
34. GIVEN `SyncQuestsUseCase` with fake dependencies WHEN invoked AND remote has changes THEN local reflects changes

**AuthRepository (Decision #42 — Walking Skeleton):**
34a. GIVEN `FakeAuthRepository()` (no initial uid) WHEN `currentUid()` called THEN returns null
34b. GIVEN `FakeAuthRepository(initialUid="user-A")` WHEN `currentUid()` called THEN returns "user-A"
34c. GIVEN guest fake AND `signIn("user-A")` WHEN `observeUid()` collected (take 2) THEN emits [null, "user-A"]
34d. GIVEN authenticated fake AND `signOut()` WHEN `observeUid()` collected (take 2) THEN emits ["user-A", null]
34e. GIVEN fake WHEN `signIn("")` or `signIn("   ")` THEN throws IllegalArgumentException (uid invariant)

**`Destination.OpenQuestCreate` push semantics (Decision #45 / W1):**
41a. GIVEN `AppShellState.default()` AND `localState.stack.active == MyQuestsRoot` WHEN `navigate(state, OpenQuestCreate)` THEN new state: `localState.stack.active == QuestCreateRoot`, `backStack == [MyQuestsRoot]`
41b. GIVEN state from 41a (stack=[MyQuestsRoot, QuestCreateRoot]) WHEN `navigate(state, Back)` THEN `localState.stack.active == MyQuestsRoot`, `backStack == []`
41c. GIVEN `AppShellState.default()` with `activeTab == EVENTS` AND `localState.stack.active == HomeQuestsRoot` WHEN `navigate(state, OpenQuestCreate)` THEN `activeTab` switches to LOCAL AND `localState.stack.active == QuestCreateRoot` (push) AND `backStack == [HomeQuestsRoot]`
41d. **Re-tap guard (Decision #47 / S3)**: GIVEN `localState.stack.active == QuestCreateRoot` WHEN `navigate(state, OpenQuestCreate)` THEN state unchanged (no-op — уже на экране)
41e. **Atomicity check (Decision #48 / W6)**: GIVEN `Destination.OpenQuestCreate` exists in sealed THEN `AppShellTransitions.navigate()` exhaustive when **MUST** include `OpenQuestCreate -> onOpenQuestCreate(state)` case (compile-time invariant)

**`MyQuestsViewModel` reactive auth (Decision #46 / W2):**
45a. GIVEN `MyQuestsViewModel(authRepo=FakeAuthRepository(null), ...)` WHEN init AND collect uiState THEN emits `MyQuestsUiState(quests=emptyList, isGuest=true)` AND `questRepository.observeMyQuests` НЕ вызывается
45b. GIVEN `MyQuestsViewModel` initialised as guest AND `authRepo.signIn("user-A")` mid-session WHEN collect uiState THEN second emission has `isGuest=false` AND `questRepository.observeMyQuests("user-A", null)` вызван
45c. GIVEN `MyQuestsViewModel` initialised as authenticated `user-A` AND `authRepo.signOut()` mid-session WHEN collect uiState THEN second emission has `isGuest=true, quests=emptyList` AND `observeMyQuests` flow cancelled (no leak)

**Star rating logic (UI concern, но pure logic):**
35. GIVEN `StarRatingModel(averageRating=0.0f)` WHEN compute filledStars THEN all 3 empty (0 filled, 0 partial)
36. GIVEN `averageRating=1.5f` WHEN compute THEN 1 full + 1 half + 1 empty
37. GIVEN `averageRating=2.7f` WHEN compute THEN 2 full + partially-filled 3rd (70% of 3rd = fractional)
38. GIVEN `averageRating=3.0f` WHEN compute THEN all 3 full
39. GIVEN `averageRating=null` WHEN compute THEN all 3 empty + marker "no ratings"
40. GIVEN `averageRating=0.35f` WHEN compute THEN 0 full + fractional 1st (35% of 1st) + 2 empty

**Cascade sync edges (Codex fix #8):**
41. GIVEN `FakeSectionRepository` with no sections AND server returns `[Section(questId=q1, lastMod=1000)]` AND cursor=500 WHEN `refreshByParents(setOf(q1))` THEN section upsert; repository returns Result.success (cursor advance is orchestrator responsibility — Clock.System.now(), not max(lastMod))
42. GIVEN `FakeThemeRepository` with theme(sectionId=s1, lastMod=500) AND server returns theme(sectionId=s1, lastMod=2000) AND cursor=1000 WHEN `refreshByParents(setOf(s1))` THEN theme updated; repository returns Result.success (cursor advance is orchestrator responsibility — Clock.System.now(), not max(lastMod))
43. GIVEN `FakeLessonRepository` with existing lesson AND server returns lesson with `archived=true` AND higher lastModifiedAt WHEN refresh THEN local delete
44. GIVEN `FakeQuestionRepository` with 3 questions AND server returns 1 updated + 1 new (higher lastModifiedAt both) WHEN refresh THEN existing updated, new inserted, cursor advances
45. GIVEN quest.contentsVersion NOT changed WHEN sync THEN NO call to `sectionRepository.refreshByParents(questId)` (early-exit test)

**Upsert/skip/dedupe (Codex fix #8):**
46. GIVEN local Quest(v=5) AND server Quest(v=5) WHEN sync THEN skip (version equal, no upsert)
47. GIVEN local Quest(v=5) AND server Quest(v=3) WHEN sync THEN skip (server stale; ignore)
48. GIVEN author-filter query returns [Quest(id=q1), Quest(id=q2)] AND visible-filter query returns [Quest(id=q1), Quest(id=q3)] WHEN merged THEN final list is [q1, q2, q3] (dedupe by id)
49. GIVEN Quest(id=q1) returned by both parallel queries WHEN upsert THEN Room has exactly 1 row for q1
50. GIVEN Firestore `in`-filter has >30 parent ids WHEN client chunks THEN multiple parallel queries, merged on client; dedupe maintained

**Server invariant for propagation (documented expectation, tested via integration — not domain):**
- GIVEN admin edits question q1 WHEN server applies write THEN q1.lastModifiedAt+version bumped, l1.lastModifiedAt+version+contentsVersion bumped, t1.* bumped, s1.* bumped, quest.* bumped, catalog.* bumped
- Domain-уровень тесты: `FakeRepository` имитирует это поведение при mutation для проверки cascade правильно (see test 45)

## Delegated Decisions Summary

| # | Область | Решение агента | Обоснование | Risk |
|---|---------|---------------|-------------|------|
| 1 | Плоские Firestore collections vs subcollections | Плоские + parentId | Нужно для cross-catalog "Мои квесты" фильтра одним query | low |
| 2 | `Quest.averageRatingCount` добавление | Int non-null, 0 by default | Нужно для UI "5 оценок" в будущем; не ломает ничего | low |
| 3 | Access filter baseline MVP | `{"home", "arena"}` общедоступны | Tournament/final — gated отдельной quals фичей | low |
| 4 | Cascade propagation server-side | Клиент / admin-tool обновляет parent.contentsVersion вручную MVP | Cloud Functions — future; MVP приемлемо в pre-production | medium |
| 5 | Coil cache invalidation через `?v=` | Query param — Coil видит новый URL | Стандартный cache-busting pattern | low |
| 6 | FAB navigation destination | `Destination.OpenQuestCreate` (data object, как `OpenDesignCatalog`) → push `LocalConfig.QuestCreateRoot` в локальный stack (Decision #41) | Nav stub; фича создания — отдельная | low |
| 7 | Click на catalog в Home Quests | TODO placeholder | Navigation в детали каталога — future feature | low |
| 8 | Click на QuestCard | TODO placeholder | Navigation в детали квеста / редактирование — future | low |
| 9 | 2 параллельных Firestore query (authorUid + visibleOn) с merge/dedupe | Firestore не поддерживает нативный OR | Стандартный pattern для Firestore | low |
| 10 | Batch size для `in`-filter | 30 (Firestore limit) | Enforced by Firestore | low |
| 11 | Room destructive migration | Pre-production — допустимо | User confirmed | low |
| 12 | Order fields у Section/Theme/Lesson/Question | Int `order` хранится, но UI-sort не в scope MVP | Готовность к future ordering | low |
| 13 | Question.payload format | Serialized JSON string | Соответствует ADR-0003 question schema | low |
| 14 | Sync retry strategy | WorkManager exponential backoff (1s → 1h max) | Standard ADR-0004 pattern | low |
| 15 | Star rating color | Синий `#4285F4` | ADR-0010 брендовая палитра + USER "пусть это обозначение будет синим цветов звзды" | low |
| 16 | Star rating precision | 10 fractional positions per star | USER "делятся на 10 частей каждая" | low |
| 17 | Empty state дизайн на "Мои квесты" | Icon + текст + arrow-to-FAB | Material3 empty state guidelines | low |
| 18 | CatalogGrid typography update | `titleMedium bold`, maxLines=1 | USER "текст под картинкой, но крупнее и bold" + стандартная Material3 density | low |

## State Matrix

### Matrix 1: Catalog / Quest (имеют `archived` и/или `visibleOn`)

**Delete semantics для phase-01** (drafts out-of-scope, reviewed Codex fix #6):
- Catalog: `archived=true` → local delete
- Quest: `archived=true` ИЛИ `visibleOn.isEmpty()` → local delete (independent of authorUid — drafts для owner вне scope)

| local state | server dto.version | dto.archived (Catalog) / dto.archived OR visibleOn.isEmpty() (Quest) | Action | Tested in scenario |
|-------------|---------------------|----------------------------------------------------------------------|--------|---------------------|
| absent | any | delete-marker true | SKIP (не создаём tombstone) | covered implicitly |
| absent | any | delete-marker false | INSERT | 21, 24, 44 |
| present | dto.v > local.v | delete-marker true | DELETE local | 23, 25, 47, 48, 49 |
| present | dto.v > local.v | delete-marker false | UPSERT | 42, 44 |
| present | dto.v == local.v | any | SKIP | 22, 46 |
| present | dto.v < local.v | any | SKIP (server stale — не бывает на практике) | 47 |

### Matrix 2: Section / Theme / Lesson / Question (имеют только `archived`)

| local state | server dto.version | dto.archived | Action | Tested in scenario |
|-------------|---------------------|--------------|--------|---------------------|
| absent | any | true | SKIP | covered implicitly |
| absent | any | false | INSERT | 41, 44 |
| present | dto.v > local.v | true | DELETE local | 43 |
| present | dto.v > local.v | false | UPSERT | 42, 44 |
| present | dto.v == local.v | any | SKIP | 46 |
| present | dto.v < local.v | any | SKIP | 47 |

### Matrix 3: Cascading recurse predicate (применяется после Matrix 1/2 upsert)

Применимо ко всем НЕ-leaf сущностям (Catalog → Quest, Quest → Section, Section → Theme, Theme → Lesson, Lesson → Question):

| parent state | dto.contentsVersion vs local | Action | Tested in scenario |
|--------------|-------------------------------|--------|---------------------|
| absent (just inserted) | dto.cv > 0 | RECURSE (тянем детей впервые) | implicit in scenario 41 |
| present, upserted | dto.cv > local.cv | RECURSE into child level | 30, 45 (inverse) |
| present, upserted/skipped | dto.cv == local.cv | STOP (children up-to-date) | 28, 45 |
| present, upserted/skipped | dto.cv < local.cv | STOP (impossible на практике, но не recursing) | — |

### Matrix 4: Visibility filter composition (доступные полки по роли)

| user role | availableShelves | Firestore query shape |
|-----------|------------------|------------------------|
| baseline (MVP) | `{"home", "arena"}` | `visibleOn array-contains-any ["home", "arena"]` + параллельно `authorUid == me` |
| + tournament qual (future) | `{"home", "arena", "tournament"}` | расширить array `[..., "tournament"]` |
| + finalist qual (future) | `{"home", "arena", "tournament", "tournamentFinal"}` | расширить array |
| admin (future) | all 5 shelves incl. archive | `visibleOn array-contains-any [all 5]` |

Future rows помечены явно — **MVP реализует только baseline (home+arena)**. Tournament/final — gating отдельной фичей квалификаций.

## Acceptance Criteria

### Domain

1. [ ] GIVEN `shared/core/catalog/domain/model/Catalog.kt` WHEN inspect THEN содержит поля `version: Long`, `contentsVersion: Long`, `lastModifiedAt: Long`, `archived: Boolean` с init-invariants
2. [ ] GIVEN `shared/feature/quest/domain/model/Quest.kt` WHEN inspect THEN содержит все поля из Feature Domain Contract (incl. `authorUid: String`, `lastModifiedAt: Long`, `archived: Boolean`) + invariants
3. [ ] GIVEN `shared/feature/{section,theme,lesson,question}/domain/model/` WHEN inspect THEN entities соответствуют contract (incl. `lastModifiedAt`, `archived`, `order`)
4. [ ] GIVEN все 58 Domain Test Scenarios WHEN run как `@Test` в commonTest THEN зелёные (canonical count per ADR-HMQ-10: 50 base + 34a-e AuthRepository + 41a-e OpenQuestCreate + 45a-c reactive auth)
5. [ ] GIVEN `QuestRepository`, `SectionRepository`, `ThemeRepository`, `LessonRepository`, `QuestionRepository` interfaces WHEN inspect THEN имеют `observeByParent`, `refreshByParents(parentIds: Set<Id>)`, `getById`
6. [ ] GIVEN fakes для всех 6 repositories в `commonTest/fake/` WHEN in-memory backed THEN поведение соответствует contract и cascade sync через lastModifiedAt работает корректно

### Data layer

7. [ ] GIVEN `CatalogRepositoryImpl.refreshFromRemote()` WHEN called с `SyncStateRepository.getCursor("catalogs") == 0L` (first sync — cursor не установлен → default 0) AND remote возвращает 3 каталога с `lastModifiedAt > 0` AND `version >= 1` THEN все 3 upsert в Room AND `setCursor` НЕ вызван репозиторием (cursor advance — ответственность `CascadingSyncOrchestrator` после subtree success; B2 fix; Decision #15 superseded by #31; W3 fix)
8. [ ] GIVEN `CatalogRepositoryImpl.refreshFromRemote()` WHEN called AND remote возвращает `catalog(v=3, archived=true)` для existing local `catalog(v=2)` THEN local deleted
9. [ ] GIVEN `QuestRepositoryImpl.refreshFromRemote(currentUserUid, availableShelves, catalogIdsToSync, cursor)` WHEN called THEN делает 2 параллельных Firestore query (by authorUid + by visibleOn) + merge/dedupe
10. [ ] GIVEN cascading sync orchestrator WHEN catalog contentsVersion не вырос THEN quests sync для этого catalog SKIPPED (no Firestore read)
11. [ ] GIVEN cascading sync orchestrator WHEN quest contentsVersion вырос THEN sections pull'ятся с `where('questId', '==', questId)`
12. [ ] GIVEN `AppDatabase` WHEN inspect schema THEN содержит tables `catalogs, quests, sections, themes, lessons, questions` с правильными columns
13. [ ] GIVEN `CatalogDao.upsertByIdIfNewerVersion(entity)` WHEN existing version >= new version THEN no update
14. [ ] GIVEN `QuestDao.observeMyQuests(authorUid, catalogId=null)` WHEN collected THEN emits only quests with `authorUid == given AND !archived`
15. [ ] GIVEN `QuestDao.observeMyQuests(authorUid, catalogId=SOMECAT)` WHEN collected THEN emits only quests with `authorUid == given AND catalogId == SOMECAT AND !archived`

### Platform / infrastructure

16. [ ] GIVEN `SyncWorker` WHEN enqueue via cold start THEN runs catalog → quest → section → theme → lesson → question cascading steps
17. [ ] GIVEN `SyncWorker` WHEN network fails THEN returns `Result.retry()` and WorkManager schedules exponential backoff
18. [ ] GIVEN dev-button `SyncNow` clicked WHEN dev mode active THEN enqueues manual `SyncWorker` with REPLACE policy
19. [ ] GIVEN Firestore has 4 catalogs + 20 quests WHEN first-sync runs THEN Room содержит все 4 + все 20 (sorted by id)
20. [ ] GIVEN Firestore `quests` collection has 1M документов WHEN client syncs с `lastModifiedAt > cursor` filter THEN читает только ~N изменившихся (проверка в контракт-тестах с FakeRemote)

### UI

21. [ ] GIVEN "Домашние квесты" screen WHEN render THEN использует `CatalogGrid` с обновлённой типографикой (`titleMedium bold`, `maxLines=1`, ellipsis, 16dp corners, 12dp gap)
22. [ ] GIVEN "Домашние квесты" screen WHEN `archived=true` catalog в Room THEN он не отображается (DAO query фильтр `WHERE archived = 0`, Decision #52)
23. [ ] GIVEN "Мои квесты" screen WHEN open THEN показывает top-bar plate `CatalogSpinner` + `LazyColumn` QuestCard'ов + FAB "+"
24. [ ] GIVEN "Мои квесты" WHEN user нет квестов THEN показывает empty state placeholder с CTA на FAB
25. [ ] GIVEN "Мои квесты" WHEN user selects "Опросы" в spinner THEN LazyColumn фильтруется на catalogId="surveys"
26. [ ] GIVEN QuestCard WHEN averageRating=2.7 THEN показывает 2 полных синих звезды + partially-filled 3rd (70%) 
27. [ ] GIVEN QuestCard WHEN averageRating=null THEN показывает 3 пустых звезды (outline)
28. [ ] GIVEN QuestCard WHEN picturePath=null THEN показывает placeholder icon вместо AsyncImage
29. [ ] GIVEN FAB "+" click WHEN on "Мои квесты" THEN navigates к `UnderConstructionScreen("Создание квеста в разработке")`
30. [ ] GIVEN `QuestCard.onClick` WHEN tapped THEN navigates к placeholder (TODO navigation)

### Integration

31. [ ] GIVEN Room contains cached data AND sync disabled WHEN open screens THEN UI работает нормально (offline-first)
32. [ ] GIVEN Admin изменил `catalog.name` WHEN SyncWorker runs THEN UI экрана "Домашние квесты" обновляется через Flow (reactive) — caталоги, не квесты
33. [ ] GIVEN Admin изменил `quest.title` WHEN SyncWorker runs AND юзер на "Мои квесты" THEN UI обновляется через Flow (reactive)
34. [ ] GIVEN Admin сделал quest публичным (добавил "home" в visibleOn) WHEN server invariant B применяется THEN все секции/темы/уроки/вопросы этого quest получают обновлённый `lastModifiedAt` AND client тянет их обычным delta-запросом

### Firebase rules

35. [ ] GIVEN client без auth WHEN attempt read `catalogs/*` THEN success (public read)
36. [ ] GIVEN client без auth WHEN attempt read `quests/*` (without filter) THEN permission denied (quests require auth — нет public unauthenticated access)
37. [ ] GIVEN authenticated client (user A) WHEN `quests.where('authorUid', '==', 'A').get()` THEN success — rule проверяет `request.auth.uid == A`
38. [ ] GIVEN authenticated client (user A) WHEN `quests.where('authorUid', '==', 'B').get()` (чужой uid) THEN permission-denied (rule отвергает)
39. [ ] GIVEN authenticated client WHEN `quests.where('visibleOn', 'array-contains', 'home').get()` THEN success (public read allowed для publicly-visible quests)
40. [ ] GIVEN non-admin WHEN attempt write `catalogs/*` THEN permission denied

### Sync state + guest (new — phase-01 scope)

41. [ ] GIVEN `InMemorySyncStateRepository` WHEN `getCursor(collectionId="catalogs")` called without prior set THEN returns 0L
42. [ ] GIVEN `setCursor(collectionId="catalogs", value=1000L)` WHEN `getCursor("catalogs")` THEN returns 1000L
43. [ ] GIVEN `markCascadeInProgress(parentId="catalog:X", parentType="catalog", pendingChildIds=setOf("q1", "q2"))` WHEN `getPendingCascades()` THEN returns list containing `PendingCascade(parentId="catalog:X", parentType="catalog", pendingChildIds=setOf("q1","q2"))`
44. [ ] GIVEN `markCascadeCompleted(parentId="catalog:X", parentType="catalog")` WHEN `getPendingCascades()` THEN returns empty list for this (parentId, parentType) pair
45. [ ] GIVEN `MyQuestsViewModel` initialised with `AuthRepository.observeUid()` emitting `null` (guest) WHEN collect UI state THEN emits empty list + isGuest=true AND does NOT call `questRepository.observeMyQuests()`
46. [ ] GIVEN guest на "Мои квесты" WHEN render THEN empty state placeholder (same as 0 quests for authenticated) AND FAB visible AND click FAB opens UnderConstructionScreen
47. [ ] GIVEN sync upsert quest с `archived=true` WHEN apply to Room THEN quest **физически удалён** (не просто помечен); "Мои квесты" не показывает его
48. [ ] GIVEN sync upsert quest с `visibleOn=emptySet` AND `authorUid=me` WHEN apply to Room THEN quest **физически удалён** (drafts вне scope phase-01)
49. [ ] GIVEN sync upsert quest с `visibleOn=emptySet` AND `authorUid=otherUid` WHEN apply to Room THEN quest **физически удалён**

### Nested entity security rules (Codex v3 fix #6)

50. [ ] GIVEN authenticated non-admin client WHEN read `sections/*` / `themes/*` / `lessons/*` / `questions/*` THEN success
51. [ ] GIVEN unauthenticated client WHEN read `sections/*` THEN permission-denied
52. [ ] GIVEN authenticated non-admin client WHEN write `sections/*` / `themes/*` / `lessons/*` / `questions/*` THEN permission-denied (admin-only write в phase-01)
53. [ ] GIVEN admin client (qualifications.admin >= 100) WHEN write `sections/*` THEN success

### Sync retry semantics (Codex v3 fix #6)

54. [ ] GIVEN SyncWorker runs AND Step 1 (catalogs) succeeds AND Step 2 (quests) fails THEN `catalogsCursor` advanced via `setCursor` AND `questsCursor` NOT advanced (остаётся prev value)
55. [ ] GIVEN SyncWorker retries AND worker still alive (в InMemorySyncStateRepository курсоры сохранены) WHEN retry runs THEN Step 1 возвращает пусто (catalogs до max уже в cursor), Step 2 повторяет с того же questsCursor (upsert-by-id идемпотентен)
56. [ ] GIVEN SyncWorker process killed after partial fail (InMemorySyncStateRepository lost) WHEN new worker starts THEN cursors reset to 0 AND sync starts from scratch AND Room данные не повреждены (upsert-by-id идемпотентен)
57. [ ] GIVEN Step 2 partial fail WHEN retry завершится успешно THEN questsCursor finally advances to Clock.System.now() (freshTime sampled at cascade start)

### Server Invariant B validation (phase-01 scope — domain test only)

58. [ ] **INTEGRATION-LEVEL (phase-01 scope, not domain)**: GIVEN real `SyncWorker` или `CascadingSyncOrchestrator` (появится в phase-01) AND admin changes `quest.visibleOn` AND server Invariant B применяется (bumps descendants' `lastModifiedAt`) WHEN sync runs THEN клиент через cursor реально тянет sections/themes/lessons/questions. **Domain-level части закрыты**: (a) cascade predicate `shouldRecurseIntoChildren` (scenarios 45b/c в `CascadeDecisionTest.kt`), (b) delta cursor pull logic в `FakeQuestRepository.refreshFromRemote` (scenarios 24, 28-31). **Cross-module integration test (quest orchestrator → section fake) вне domain boundaries**, нарушил бы invariant #3 (no bidirectional coupling между feature-модулями). Поэтому scenario 58 реализуется в integration-test модуле phase-01 (например `shared/core/sync` или specialized integration module), не в domain layer.

## Invariant Check (from docs/invariants.md)

| Invariant | Impact | Decision |
|-----------|--------|----------|
| 1. Domain layer purity | Новые 5 feature-модулей domain: pure Kotlin, no Android/SDK | preserve |
| 2. Activity/Fragment calls only ViewModel | `MyQuestsScreen` использует `MyQuestsViewModel`, не делает direct repo calls | preserve |
| 3. No bidirectional coupling | `shared/feature/quest` и др. — one-way deps; UI depends on domain | preserve |
| 4. onDestroy не для business cleanup | `MyQuestsScreen` — Composable, не Activity; N/A direct | N/A |
| 5. DI exclusive binding | Koin модули — один подход per class (factory/single) | preserve |
| 6. Walking Skeleton ownership | Phase 3.8 генерирует full domain stack для 6 entities | preserve + generate |
| 7. Scaffold file ownership | Новые Gradle-модули — owner `backend-dev` для build.gradle.kts / settings.gradle.kts / libs.versions.toml | preserve |

## Constraints (from PROJECT_STRUCTURE.md + ADRs)

- **KMP** `androidTarget + jvm` (ADR-0002) для всех новых `shared/feature/*/domain` и `data`
- **Koin DI** (ADR-0009) — per-feature module для каждого нового модуля
- **Compose + Material3** (ADR-0010) для `MyQuestsScreen`, `QuestCard`, `StarRating`
- **Coil 3.4.0** (ADR-HLA-06) для image loading в QuestCard — Decision #43: bump 3.1.0 → 3.4.0 в этой фиче (research нашёл что в libs.versions.toml была версия 3.1.0)
- **ADR-0004 sync contract** — cascading sync следует per-entity version+updatedAt принципам; `contentsVersion` — extension (per-entity, не в Syncable base)
- **ADR-0005 quest lifecycle** — shelf enum формально сохраняется в ADR; в данной фиче используется `visibleOn: Set<String>` реально (документально не зафиксировано, отдельная ADR update задача)
- **ADR-0003 question schema** — `Question.payload: String` — serialized JSON соответствующий 4 типам вопросов (SingleChoice, MultipleChoice, Ordering, FillBlank)
- **PROJECT_STRUCTURE §4** — `shared/feature/quest/`, `shared/feature/section/`, etc. (NOT в `shared/core/`) т.к. это feature-specific domain (не cross-cutting как catalog)
- **Decompose navigation** (ADR-0008) — `LocalConfig.MyQuestsRoot` и новый `LocalConfig.QuestCreateRoot` — sealed children

## Dependencies on Other Features

- **catalog-foundation** (menu-refactor feature) — prerequisite ✅ (уже реализовано):
  - `shared/core/catalog/domain` — Catalog + CatalogId + CatalogRepository interface (ёxtend)
  - `shared/core/catalog/data` — CatalogRepositoryImpl, CatalogLocalDataSource, CatalogRemoteDataSource (изменить refreshFromRemote)
  - `shared/core/persistence` — CatalogEntity, CatalogDao (extend)
  - `platform/firebase/catalog` — FirebaseCatalogRemoteDataSource, FirestoreCatalogDtoMapper (extend)
  - `platform/android-services/SyncWorker` — существующий worker расширяется (добавить step для quests+sections+themes+lessons+questions)
  - `android/core/designsystem/CatalogGrid` + `CatalogSpinner` — reused (Spinner — проверить research что реально создан)
  - `DrawerFooterAction.SyncNow` — reused из existing

- **menu-refactor/dev-mode** — prerequisite ✅ — SyncNow footer action visibility logic (qualification + debug build)

- **UserStats / profile foundation** — need:
  - ~~`UserStatsRepository.currentAuthUid(): String?`~~ — **SUPERSEDED Decision #42**: новый `AuthRepository.currentUid()` interface (не extension UserStatsRepository)
  - `UserStats.qualification.*` — для будущего access control гатирования (tournament shelf)
  - `UserStatsRepository` — extend для возврата `availableShelves: Set<String>` (computed)

- **AppApplication.kt:41 `authUidFlow`** — prerequisite ✅ — `callbackFlow<String?>` через `FirebaseAuth.AuthStateListener` уже создан и прокидывается в `appShellDataModule`. `AuthRepositoryImpl` (phase-01) переиспользует тот же flow.

- **home-quests rename** — prerequisite ✅ — `DrawerSection.LocalSection.HomeQuests` + `LocalConfig.HomeQuestsRoot` уже переименованы

## Open Questions (для research / design)

1. ~~**UserStats tpovId existence**~~ — **РЕШЕНО 2026-04-21**: tpovId НЕ используется. `Quest.authorUid: String` = Firebase Auth UID. **Updated 2026-04-22 (Decision #42)**: новый `AuthRepository.currentUid()` interface — не extension `UserStatsRepository`. Walking Skeleton дополнен `AuthRepository` + `FakeAuthRepository` + 6 contract тестов.

2. ~~**Quest vs quiz модуль**~~ — **РЕШЕНО 2026-04-22 (Decision #44)**: cleanup task в scope phase-01. Удалить `shared/feature/quiz/{domain,data}` + `android/feature/quiz/presentation` из settings.gradle.kts; удалить placeholder Quest+QuestRepository+CreateQuestUseCase+FakeQuestRepository+QuestCatalogLinkTest из `shared/core/catalog/domain`. Канонический Quest остаётся в `shared/feature/quest/domain`.

3. ~~**CatalogSpinner реальное состояние**~~ — **РЕШЕНО research 2026-04-22**: `CatalogSpinner` уже существует в `android/core/designsystem/components/CatalogSpinner.kt:34` со всеми требуемыми параметрами (`items, selectedId, onSelectionChanged, modifier`) и pseudo-item "Все категории". Используется в `MyQuestsScreen` без изменений.

4. **Cloud Function для parent.contentsVersion propagation**: MVP полагается на ручную propagation. Если admin tool не делает — клиент не узнаёт про nested changes. Research / design должен определить: (a) создать Cloud Function trigger, (b) задокументировать admin contract, (c) игнорировать и делать workaround.

5. **Orphan cleanup** — если catalog удалён, quests orphan в Room. UI показывает их как "Unknown catalog" или фильтрует? Design решит.

6. **Server-side cascading**: когда admin создаёт question в lesson, сервер ideally инкрементирует `lesson.contentsVersion`, `theme.contentsVersion`, `section.contentsVersion`, `quest.contentsVersion`, `catalog.contentsVersion`. Без этого — клиент не видит изменений. Cloud Function trigger — future.

7. **Tombstone retention** — ADR-0004 говорит 30 дней. Спецификация для `archived=true` catalog: они живут в Firestore как архив или физически удаляются server-janitor'ом через 30 дней? Открытый вопрос.

8. **Rate limiting при массовом create** — если юзер создал 100 quests разом, SyncWorker может не справиться с 100 updates за один запуск. Чunking + backoff — стандартный WorkManager pattern.

9. **Quest.ratingCount overflow** — Int max = 2.1B, приемлемо для MVP. Для scale > 2B рейтингов — future Long migration.

10. **Section order conflict при concurrent creates** — 2 юзера админа создают section с order=0 в одном quest одновременно. MVP: оба сохраняются, UI сортирует по `order then id`. Design решит.
