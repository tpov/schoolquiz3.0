---
phase: phase-06
role: frontend-dev
---

# Phase 06 — Frontend Tasks

## Pattern Invariants

- `LessonItemCard` ДОЛЖЕН быть в `quizzes-screen/presentation/screen/`, НЕ в `android/core/designsystem` — per `clean-architecture.md`: designsystem не знает о product features; enforcement: `rg "^import .*lesson_runner" android/core/designsystem/src -g "*.kt"` — must be empty
- `StarRating` используется as-is из `android/core/designsystem`: `StarRating(rating = bestStarsRawTenths / 10f)` — per `android/core/designsystem/src/main/kotlin/.../components/StarRating.kt`
- `Checkbox` visible ТОЛЬКО если `hardUnlocked == true` (НЕ `bestStarsRawTenths >= 20`) — spec §21 hardUnlock condition
- Все цвета через `MaterialTheme.colorScheme.*` — `BrandComponentsInvariantsTest` enforces (per `AppShellScreen.kt:53-56` pattern: no hardcoded colors)
- `LessonItemCard` имеет `@Preview` — per project convention (`CatalogGrid.kt`, `QuestCard.kt` precedents)

---

## Create `LessonItemCard`

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCard.kt`
- **Тип:** Composable fun
- **Сигнатура:** `@Composable fun LessonItemCard(item: LessonItemUi, onClick: () -> Unit, onHardCheckChanged: (Boolean) -> Unit, modifier: Modifier = Modifier)`
- **Вход:** `item: LessonItemUi`, `onClick`, `onHardCheckChanged`, optional modifier
- **Поведение / Выход:**
  - Layout: `Row` или `Card` wrapping
  - Left: `Text(item.title)` + optional `Text(item.orderLabel)` + optional `Text(item.subtitleCount)`
  - Right: `StarRating(rating = item.bestStarsRawTenths / 10f)` — existing designsystem component; `rating=0f` если нет попыток
  - Conditional `Checkbox(checked = item.isHardChecked, onCheckedChange = onHardCheckChanged, enabled = item.hardUnlocked)` — visible ТОЛЬКО если `item.hardUnlocked == true` (else hidden/gone)
  - `onClick` on card (excluding checkbox area) → triggers lesson tap
- **Edge cases:**
  - `hardUnlocked == false` → Checkbox `visible = false` (НЕ `enabled = false`, а скрыт completely)
  - `bestStarsRawTenths = 0` → `StarRating(0.0f)` — empty stars (zero state, no attempts)
  - `StarRating` принимает Float в range 0..3.0 (проверить existing API: если `rating: Float` goes 0..3.0 с шагом 0.1 — `rawTenths/10f` корректно)
  - Long title → ellipsis
- **Depends on:** `LessonItemUi`, `StarRating` (existing designsystem), Material3 Checkbox
- **Canonical reference:** `06-api-contract.md:474`, `ADR-LR-11`
- **Rationale:** Lesson-specific card; gameplay state (Stars + HARD checkbox) isolated from generic HierarchyItemCard

---

## Modify `LessonListScreen` — use `LessonItemCard`

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt`
- **Тип:** Composable fun (existing — modify)
- **Сигнатура:** update LazyColumn content to use `LessonItemCard` instead of `HierarchyItemCard` for lesson items
- **Вход:** N/A — UI modification
- **Поведение / Выход:**
  - Replace `HierarchyItemCard(title=..., rating=..., onClick=...)` per lesson item
  - With `LessonItemCard(item=lessonItemUi, onClick={ component.onLessonClick(item) }, onHardCheckChanged={ component.onHardCheckToggled(item.id) })`
  - Component state: `component.lessonItems: StateFlow<List<LessonItemUi>>` (new; was `List<HierarchyItemUi>`)
- **Edge cases:**
  - `component.lessonItems` type changes from `List<HierarchyItemUi>` → `List<LessonItemUi>` — adjust all usages
  - Existing drag handle / reorder if any → verify not broken
- **Depends on:** `LessonItemCard`, `LessonItemUi`, `LessonListComponent` (interface updated to expose `List<LessonItemUi>`)
- **Canonical reference:** ADR-LR-11
- **Rationale:** Switch from generic card to lesson-specific card for gameplay state display
