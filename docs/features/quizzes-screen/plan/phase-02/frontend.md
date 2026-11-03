---
phase: 02
role: frontend-dev
---

# Phase-02 Frontend Tasks: Designsystem + Model Extension

### Pattern Invariants

- `@Preview` ОБЯЗАТЕЛЕН в каждом новом `.kt` файле в `components/` — BrandComponentsInvariantsTest.
- Только `MaterialTheme.colorScheme.*` для цветов — никаких `Color(0xFF...)`.
- `QuestCard.onLongClick` — backward-compatible nullable default.
- `HierarchyItemCard` принимает только примитивы, не feature types.
- `combinedClickable` — `@OptIn(ExperimentalFoundationApi::class)` НЕ требуется. Compose BOM 2024.09.02 (`gradle/libs.versions.toml:33`) → `compose-foundation ~1.7.x`; `combinedClickable` стабилен с 1.4.0. Использовать `Modifier.combinedClickable(...)` напрямую без аннотации.

---

## Create HierarchyItemCard

- **Файл:** `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/HierarchyItemCard.kt`
- **Тип:** Composable function + Preview
- **Сигнатура:** `fun HierarchyItemCard(title: String, orderLabel: String? = null, subtitleCount: String? = null, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, modifier: Modifier = Modifier)`
- **Вход:**
  - `title: String` — основной текст карточки (`titleMedium`, `maxLines=2`, `TextOverflow.Ellipsis`)
  - `orderLabel: String?` — нумерация слева ("1.", "2."); null = не показывать
  - `subtitleCount: String?` — зарезервировано на будущее; null в MVP = не показывать
  - `onClick: () -> Unit` — тап на карточку
  - `onLongClick: (() -> Unit)? = null` — long-press; null = no haptic, `Modifier.clickable`; non-null = `Modifier.combinedClickable`
  - `modifier: Modifier = Modifier`
- **Поведение / Выход:**
  - Layout: `BrandCard` содержащий `Row` — `if (orderLabel != null) Text(orderLabel, labelSmall)` + `Text(title, titleMedium, weight(1f), maxLines=2, Ellipsis)` + `if (subtitleCount != null) Text(subtitleCount)`
  - Минимальная высота 48dp (Material3 a11y)
  - `Modifier.combinedClickable` если `onLongClick != null`; `Modifier.clickable` иначе
  - Все цвета через `MaterialTheme.colorScheme.*`
  - `@Preview` функция с примерными данными
- **Edge cases:**
  - `orderLabel = null` → Row без левого элемента, title fills width
  - `subtitleCount = null` → Row без правого элемента
  - `onLongClick = null` → без haptic feedback (Modifier.clickable)
- **Depends on:** `BrandCard` (existing), `MaterialTheme`, Compose Foundation `combinedClickable`
- **Canonical reference:** `06-api-contract.md:677`
- **Rationale:** Один универсальный компонент для Section/Theme/Lesson (ADR-QS-09); принимает примитивы чтобы не нарушать layer boundary core→feature.

---

## Create BreadcrumbBar

- **Файл:** `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/BreadcrumbBar.kt`
- **Тип:** Composable function + Preview
- **Сигнатура:** `fun BreadcrumbBar(titles: List<String>, onSegmentClick: (uiLevel: Int) -> Unit, modifier: Modifier = Modifier)`
- **Вход:**
  - `titles: List<String>` — frozen breadcrumb path (e.g. `["Математика", "Квест 1", "Секция 2"]`)
  - `onSegmentClick: (uiLevel: Int) -> Unit` — callback при тапе на кликабельный сегмент; получает 0-based index сегмента
  - `modifier: Modifier = Modifier`
- **Поведение / Выход:**
  - `LazyRow` или `Row` (verify что LazyRow нужен для overflow) с horizontally scrollable behaviour
  - Каждый сегмент: `Text(title, maxLines=1, overflow=TextOverflow.Ellipsis)`
  - Разделитель `"›"` между сегментами (не `">"`)
  - Последний сегмент (index == `titles.lastIndex`): некликабелен, визуально distinct (например `alpha=1f` vs `alpha=0.6f`, или разный `fontWeight` — конкретику определяет frontend-dev в соответствии с Material3)
  - Остальные сегменты: `Modifier.clickable { onSegmentClick(index) }`, цвет `MaterialTheme.colorScheme.primary` или `onSurface`
  - `@Preview` функция с 3 сегментами
- **Edge cases:**
  - `titles = listOf("Математика")` → один сегмент, некликабелен, без разделителя
  - Пустой список → render ничего (нет crash)
  - Длинный заголовок → `maxLines=1, Ellipsis`
- **Depends on:** `MaterialTheme`, Compose Foundation
- **Canonical reference:** `06-api-contract.md:677`
- **Rationale:** Отдельный компонент для breadcrumb — переиспользуется всеми 5 drill-down screens. Последний сегмент некликабелен (spec AC#9 / Matrix 3).

---

## Extend QuestCard with onLongClick

- **Файл:** `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/QuestCard.kt`
- **Тип:** Composable function (modification)
- **Сигнатура:** `fun QuestCard(item: QuestDisplayItem, onClick: (QuestId) -> Unit, modifier: Modifier = Modifier, onLongClick: ((QuestId) -> Unit)? = null)`
- **Вход:**
  - `onLongClick: ((QuestId) -> Unit)? = null` — NEW; backward-compatible nullable default
  - Остальные параметры — без изменений
- **Поведение / Выход:**
  - Если `onLongClick != null`: заменить `Modifier.clickable { onClick(item.id) }` на `Modifier.combinedClickable(onClick = { onClick(item.id) }, onLongClick = { onLongClick(item.id) })`
  - Если `onLongClick == null`: оставить `Modifier.clickable { onClick(item.id) }` (или эквивалентный `combinedClickable` с null onLongClick — verify semantics)
  - `onLongClickLabel` для TalkBack доступности (ADR-QS-06): строковый ресурс `R.string.action_share` или аналог
  - `@Preview` обновить (параметр nullable — добавлять вторую Preview с onLongClick non-null необязательно)
- **Edge cases:**
  - `onLongClick = null` → поведение как сейчас, без регрессий в `MyQuestsScreen`
  - haptic feedback: `combinedClickable` включает по умолчанию при non-null onLongClick
- **Depends on:** `QuestDisplayItem` (с новым `catalogId` из этой же фазы), `combinedClickable`
- **Canonical reference:** `06-api-contract.md:677`, ADR-QS-06
- **Rationale:** Backward-compatible extension — MyQuestsScreen не изменяется (null default). First usage combinedClickable в проекте (ADR-QS-07).

---

## Extend QuestDisplayItem with catalogId

- **Файл:** `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/model/QuestDisplayItem.kt`
- **Тип:** data class (field addition)
- **Сигнатура:** `data class QuestDisplayItem(val id: QuestId, val catalogId: CatalogId, val title: String, val pictureUrl: String?, val averageRating: Float?, val averageRatingCount: Int = 0)`
- **Вход:** N/A (data class)
- **Поведение / Выход:**
  - `val catalogId: CatalogId` — required field (не nullable)
  - После добавления: все конструкторы `QuestDisplayItem(...)` в тестах должны передать `catalogId`
- **Edge cases:**
  - Required field вызывает compile error во всех существующих конструкторах — blast radius должен быть fully fixed в этой же фазе
- **Depends on:** `CatalogId` (shared/core/catalog/domain)
- **Canonical reference:** `06-api-contract.md:81`, ADR-QS-05
- **Rationale:** catalog id нужен в MyQuests entry для breadcrumb resolution (User Decision Q4). Single SSoT в presentation model.

---

## Update QuestToDisplayItem mapper

- **Файл:** `android/feature/quest/presentation/src/main/kotlin/.../mapper/QuestToDisplayItem.kt`
- **Тип:** extension function (modification)
- **Сигнатура:** `fun Quest.toDisplayItem(): QuestDisplayItem` (сигнатура не меняется)
- **Вход:** `Quest` domain model (теперь включает `catalogId: CatalogId`)
- **Поведение / Выход:**
  - Добавить `catalogId = catalogId` в QuestDisplayItem constructor
  - Остальные поля — без изменений
- **Edge cases:**
  - N/A — pass-through mapping
- **Depends on:** `Quest.catalogId` (существует в domain model — verified `Quest.kt:32`), `QuestDisplayItem.catalogId`
- **Canonical reference:** `06-api-contract.md:81`
- **Rationale:** Mapper — единственное место где Quest.catalogId → QuestDisplayItem.catalogId.
