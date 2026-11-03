---
phase: 02
name: Designsystem — HierarchyItemCard + BreadcrumbBar + QuestCard.onLongClick + QuestDisplayItem.catalogId
layer: ui (designsystem) + presentation (model)
status: ready
---

# Phase-02: Designsystem Components + Model Extension

## Goal

Создать два новых Compose-компонента в `android/core/designsystem/` (`HierarchyItemCard`, `BreadcrumbBar`) и расширить `QuestCard` nullable `onLongClick` параметром. Расширить `QuestDisplayItem` полем `catalogId: CatalogId`. Обновить маппер и все тест-конструкторы. BrandComponentsInvariantsTest должен оставаться зелёным.

## Scope

- `android/core/designsystem/components/`: 2 новых файла + 1 модификация
- `android/core/designsystem/model/QuestDisplayItem.kt`: 1 новое поле
- `android/feature/quest/presentation/.../mapper/QuestToDisplayItem.kt`: маппер update
- Тесты для новых компонентов + маппера

## Role Inputs

- `frontend.md` — frontend-dev
- `tests.md` — test-dev

`backend.md` — не создаётся: фаза не трогает scaffold файлы.

## Layer

**ui** (android designsystem) + **presentation** (model + mapper в quest/presentation)

## Review Tags

`compose-ui`, `designsystem`, `brand-compliance`, `BrandComponentsInvariantsTest`

## State Matrix Coverage

Matrix rows (из `02-behavior.md`):
- Matrix 2 (Empty/Loading/Loaded): строки `Loaded - SectionList/ThemeList/LessonList` — `HierarchyItemCard` используется в этих состояниях
- Matrix 3 (Breadcrumb path): все строки — `BreadcrumbBar` рендерится на каждом уровне

## Domain Contract Coverage

Feature Domain Contract = N/A. Эта фаза реализует **presentation model** (`QuestDisplayItem.catalogId`) и **designsystem primitives**.

## Traceability

| Problem (from 2-grounding.md) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|-------------------------------|------------|--------------|-----------------|--------------|------------|
| **Problem 3**: `QuestDisplayItem.catalogId` для breadcrumb от MyQuests | frontend-dev (`android/core/designsystem/model/`, `quest/presentation/mapper/`) | `MyQuestsScreen.kt:84-91` (onClick lambda context), `DefaultMyQuestsComponent.onQuestClick` (Phase-07) | Additive field `val catalogId: CatalogId`; обратная совместимость маппера | Добавить поле в data class; обновить mapper + тест round-trip; обновить все `QuestDisplayItem(...)` конструкторы в тестах | `QuestToDisplayItemTest` round-trip |
| **Problem 5**: `QuestCard` long-press меню support | frontend-dev (`android/core/designsystem/components/QuestCard.kt`) | `QuestListScreen` (Phase-05) — первый consumer с non-null `onLongClick` | Backward-compatible nullable param; `BrandComponentsInvariantsTest` compliance | `+onLongClick: ((QuestId) -> Unit)? = null`; `combinedClickable` if non-null, `clickable` if null | `QuestCardLongClickTest` + BrandComponentsInvariantsTest |
| **Problem 7**: Новые designsystem компоненты `HierarchyItemCard` + `BreadcrumbBar` | frontend-dev (`android/core/designsystem/components/`) | `SectionListScreen`, `ThemeListScreen`, `LessonListScreen` (Phase-05) | `@Preview` обязателен; никаких `Color(0x...)`; BrandComponentsInvariantsTest | 2 новых Composable файла | `HierarchyItemCardTest`, `BreadcrumbBarTest`, BrandComponentsInvariantsTest |

## New Files

| File | Owner | Note |
|------|-------|------|
| `android/core/designsystem/src/main/kotlin/.../components/HierarchyItemCard.kt` | frontend-dev | Новый Composable + `@Preview`. Canonical ref: `06-api-contract.md:677` |
| `android/core/designsystem/src/main/kotlin/.../components/BreadcrumbBar.kt` | frontend-dev | Новый Composable + `@Preview`. Canonical ref: `06-api-contract.md:677` |

## Modified Files

| File | Owner | Change |
|------|-------|--------|
| `android/core/designsystem/src/main/kotlin/.../components/QuestCard.kt` | frontend-dev | `+onLongClick: ((QuestId) -> Unit)? = null`; `combinedClickable` if non-null |
| `android/core/designsystem/src/main/kotlin/.../model/QuestDisplayItem.kt` | frontend-dev | `+val catalogId: CatalogId` (required field) |
| `android/feature/quest/presentation/src/main/.../mapper/QuestToDisplayItem.kt` | frontend-dev | `+catalogId = quest.catalogId` в mapper |
| `android/feature/quest/presentation/src/test/.../mapper/QuestToDisplayItemTest.kt` | test-dev | `+catalogId` в тест round-trip assertions |
| Все тест-конструкторы `QuestDisplayItem(...)` | test-dev | Добавить `catalogId = ...` во все существующие test fixtures |

## Deleted Files

none

## Dependencies

Не зависит от Phase-01. Может выполняться параллельно с Phase-01. Phase-03+ зависят от этой фазы (используют `QuestDisplayItem.catalogId`).

## Acceptance Criteria

1. `HierarchyItemCard.kt` содержит `@Preview`, никаких `Color(0x...)`, соответствует `06-api-contract.md:677` signature.
2. `BreadcrumbBar.kt` содержит `@Preview`, никаких `Color(0x...)`, последний сегмент некликабелен.
3. `QuestCard.kt` расширен `onLongClick: ((QuestId) -> Unit)? = null` — backward compatible; все существующие вызовы компилируются.
4. `QuestDisplayItem.kt` содержит `val catalogId: CatalogId`.
5. `QuestToDisplayItem.kt` маппит `catalogId = quest.catalogId`.
6. BrandComponentsInvariantsTest зелёный после изменений.
7. `./gradlew :android:core:designsystem:test --no-configuration-cache` — зелёный.
8. `./gradlew assemble --no-configuration-cache` — зелёный.

## Tests Required

TDD — параллельно с реализацией:

**BreadcrumbBarTest (Compose UI, instrumented)**:
- `renders all segments in order`: given segments=["Математика", "Квест 1", "Секция 2"], when rendered, then all three texts visible
- `last segment not clickable`: given 3 segments, when tap last segment, then onSegmentClick NOT called
- `non-last segment click fires with correct index`: when tap segment at index 0, then onSegmentClick(0) called; when tap segment at index 1, then onSegmentClick(1) called
- `single segment renders without separator`: given single segment, then no "›" separator visible
- `long title truncates with ellipsis`: given segment with 40+ char title, then text is truncated (maxLines=1)

**HierarchyItemCardTest (Compose UI, instrumented)**:
- `title displayed`: given title="Algebra basics", then text visible
- `orderLabel null then not shown`: given orderLabel=null, then no "1." or order label text
- `orderLabel non-null then shown on left`: given orderLabel="2.", then "2." visible
- `subtitleCount null then not shown`: given subtitleCount=null, then no count text
- `onClick fires when clicked`: given onClick lambda, when performClick, then lambda called
- `onLongClick null then no long-press semantic`: given onLongClick=null, then performLongClick does not fire onLongClick
- `onLongClick non-null then fires on long press`: given onLongClick lambda, when performLongClick, then lambda called

**QuestCardLongClickTest (Compose UI, instrumented)**:
- `existing onClick still works with null onLongClick`: given QuestCard with only onClick, when performClick, then onClick fires
- `onLongClick fires on long press`: given QuestCard with onLongClick lambda, when performLongClick, then lambda fires
- `both onClick and onLongClick work independently`: click fires onClick, long press fires onLongClick — no interference

**QuestToDisplayItemTest update (JVM)**:
- `round-trip preserves catalogId`: given Quest with catalogId="cat-5", when toDisplayItem(), then displayItem.catalogId.value == "cat-5"

## Pattern Invariants

1. Все новые Composable в `android/core/designsystem/components/` ОБЯЗАНЫ содержать `@Preview` — BrandComponentsInvariantsTest (`:53-67`) это проверяет.
2. Все цвета через `MaterialTheme.colorScheme.*` — никаких `Color(0xFF...)` или `Color(0x...)` — BrandComponentsInvariantsTest (`:23-36`) это проверяет.
3. `QuestCard.onLongClick` изменение — backward compatible: `? = null` default. Existing callers (`MyQuestsScreen.kt:84`, `HomeQuestsScreen.kt`) НЕ изменяются.
4. `QuestDisplayItem.catalogId` — required field (не nullable). Все существующие конструкторы требуют обновления. Verify blast radius перед мержем.
5. `HierarchyItemCard` принимает только примитивы (`String`, `String?`, `() -> Unit`) — не `HierarchyItemUi` из quizzes-screen/presentation. `android/core/designsystem` не может импортировать feature modules (ADR-QS-09).
6. `BreadcrumbBar` принимает `titles: List<String>` — не feature-specific types.
7. `combinedClickable` — verified: Compose BOM 2024.09.02 (`gradle/libs.versions.toml:33`) → `compose-foundation ~1.7.x`. `combinedClickable` стабилен начиная с 1.4.0; `@OptIn(ExperimentalFoundationApi::class)` НЕ требуется. Implementer может использовать `Modifier.combinedClickable(...)` напрямую.

## Validation

| # | Command | Expected |
|---|---------|----------|
| 1 | `./gradlew :android:core:designsystem:test --no-configuration-cache` | passes — BrandComponentsInvariantsTest green |
| 2 | `./gradlew allTests --no-configuration-cache` | passes |
| 3 | `./gradlew assemble --no-configuration-cache` | passes |
| 4 | `./gradlew :android:core:designsystem:assembleDebugAndroidTest --no-configuration-cache` | passes — Compose UI instrumented APK build (BreadcrumbBarTest + HierarchyItemCardTest) |
| 5 | `./gradlew :android:core:designsystem:connectedDebugAndroidTest --no-configuration-cache` | passes **(requires connected device)** — BreadcrumbBarTest (BB-UI-01..05) + HierarchyItemCardTest (HI-UI-01..06) + QuestCardLongClickTest |

## Handoff Notes

- Phase-03 зависит от `QuestDisplayItem.catalogId` (используется в QuizzesConfig entry logic).
- Phase-05 зависит от `HierarchyItemCard`, `BreadcrumbBar`, `QuestCard.onLongClick`.
- `combinedClickable` — no `@OptIn` needed (verified: BOM 2024.09.02 → foundation 1.7.x, stable).
- После Phase-02 blast radius `QuestDisplayItem` construction fully resolved — все тесты компилируются.
