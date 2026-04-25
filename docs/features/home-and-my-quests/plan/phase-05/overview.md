---
phase: 05
name: Presentation + Designsystem — MyQuestsScreen + HomeQuestsComponent + QuestCard + StarRating
complexity: complex
date: 2026-04-23
---

# Phase-05: Presentation + Designsystem

## Goal

Создать UI-уровень фичи:
- `android/feature/quest/presentation` module — `MyQuestsComponent` + `DefaultMyQuestsComponent` + `HomeQuestsComponent` + `DefaultHomeQuestsComponent` + Koin module
- `MyQuestsScreen` Composable + `HomeQuestsScreen` Composable (полностью заменяет `CatalogGridSection` inline в AppShellScreen)
- `QuestCard` + `StarRating` в `android/core/designsystem` — BrandComponentsInvariantsTest compliant
- `QuestDisplayItem` model в designsystem
- Обновить `AppShellScreen.LocalTabContent` routing: `MyQuestsRoot → MyQuestsScreen`, `HomeQuestsRoot → HomeQuestsScreen`, `QuestCreateRoot → UnderConstructionScreen`
- `CatalogGrid` typography polish (titleMedium bold, AC#21)

## Scope

`android/feature/quest/presentation` (NEW module), `android/core/designsystem` (QuestCard, StarRating, QuestDisplayItem), `android/feature/app-shell/presentation` (AppShellScreen routing update, AppShellPresentationModule), `apps/android-next/AppApplication.kt` (questPresentationModule registration).

## Layer

presentation + ui

## Role Inputs

- `frontend.md` — frontend-dev
- `backend.md` — backend-dev (build.gradle.kts для нового модуля + AppApplication)
- `tests.md` — test-dev

## Review Tags

- `concurrency-review` (DefaultMyQuestsComponent: `flatMapLatest` на authUid Flow, combine с catalogId StateFlow — lifecycle scope management, cancellation on sign-out)
- `ui-review` (BrandComponentsInvariantsTest compliance: @Preview in every components/ file, no hardcoded Color(0xFF...))

---

## Options Considered

| Критерий | Option A — Decompose Component (recommended) | Option B — AndroidX ViewModel | Option C — Direct koinInject in Composable |
|----------|----------------------------------------------|-------------------------------|-------------------------------------------|
| Consistency | matches existing pattern (CatalogGrid uses Component — per 03-decisions.md ADR-CMP-51) | different from rest | anti-pattern, uses koinInject directly (pre-existing violation) |
| Testability | TestComponentContext + fakes | HiltTest / manual factory | Not testable |
| Lifecycle | Decompose lifecycle tied to component tree | Activity/Fragment lifecycle | no lifecycle |
| Scope | component-scoped coroutines | viewModelScope | no scope |

**Recommended: Option A (ADR-CMP-51)**

**Rationale:** ADR-CMP-51 (accepted by User Decision #51). Consistent with existing Decompose usage. `DefaultMyQuestsComponent` and `DefaultHomeQuestsComponent` follow `DefaultLocalTabComponent` pattern.

**Rejected Option B:** Mixing AndroidX ViewModel with Decompose — inconsistency per ADR-CMP-51.

**Rejected Option C:** Pre-existing violation in `CatalogGridSection` (`koinInject<CatalogRepository>()` — Problem 3 code path divergence). Phase-05 **eliminates** this anti-pattern for MyQuestsRoot and HomeQuestsRoot.

---

## Traceability

| Problem (from 2-grounding.md) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|-------------------------------|-----------|-------------|-----------------|-------------|-----------|
| P3: MyQuestsScreen absent + ViewModel absent + CatalogGridSection anti-pattern for HomeQuestsRoot | `android/feature/quest/presentation` (NEW), `AppShellScreen.kt:307-311` | `AppShellScreen.LocalTabContent when-block` | `koinInject` anti-pattern removed for new screens | Create HomeQuestsComponent + MyQuestsComponent; update AppShellScreen routing | `DefaultMyQuestsComponentTest`, `DefaultHomeQuestsComponentTest` |
| P7: BrandComponentsInvariantsTest coverage для новых components | `android/core/designsystem/components/QuestCard.kt`, `StarRating.kt` | `BrandComponentsInvariantsTest.kt:24-65` | @Preview в каждом файле; no Color(0xFF...) hardcoded | Create QuestCard + StarRating with correct @Preview + MaterialTheme colors | `./gradlew :android:core:designsystem:test` |

---

## State Matrix Coverage

- DFD 3 (MyQuests screen data flow): реализуется в `DefaultMyQuestsComponent` — authUid → flatMapLatest → quest list
- DFD 2 (HomeQuests screen data flow): `DefaultHomeQuestsComponent` — ObserveCatalogs → StateFlow

---

## New Files

| File | Module |
|------|--------|
| `android/feature/quest/presentation/src/main/.../MyQuestsComponent.kt` | quest/presentation |
| `android/feature/quest/presentation/src/main/.../DefaultMyQuestsComponent.kt` | quest/presentation |
| `android/feature/quest/presentation/src/main/.../HomeQuestsComponent.kt` | quest/presentation |
| `android/feature/quest/presentation/src/main/.../DefaultHomeQuestsComponent.kt` | quest/presentation |
| `android/feature/quest/presentation/src/main/.../ui/MyQuestsScreen.kt` | quest/presentation |
| `android/feature/quest/presentation/src/main/.../ui/HomeQuestsScreen.kt` | quest/presentation |
| `android/feature/quest/presentation/src/main/.../di/QuestPresentationModule.kt` | quest/presentation |
| `android/feature/quest/presentation/src/main/.../mapper/QuestToDisplayItem.kt` | quest/presentation |
| `android/feature/quest/presentation/build.gradle.kts` | quest/presentation |
| `android/core/designsystem/src/main/.../components/QuestCard.kt` | designsystem |
| `android/core/designsystem/src/main/.../components/StarRating.kt` | designsystem |
| `android/core/designsystem/src/main/.../model/QuestDisplayItem.kt` | designsystem |
| `android/feature/quest/presentation/src/test/.../DefaultMyQuestsComponentTest.kt` | quest/presentation |
| `android/feature/quest/presentation/src/test/.../DefaultHomeQuestsComponentTest.kt` | quest/presentation |

## Modified Files

| File | Change |
|------|--------|
| `android/feature/app-shell/presentation/src/.../ui/AppShellScreen.kt` | update LocalTabContent: MyQuestsRoot→MyQuestsScreen, HomeQuestsRoot→HomeQuestsScreen, QuestCreateRoot→UnderConstructionScreen; remove CatalogGridSection |
| `android/feature/app-shell/presentation/src/.../di/AppShellPresentationModule.kt` | add QuestPresentationModule wiring (or reference from AppApplication) |
| `android/core/designsystem/src/main/.../components/CatalogGrid.kt` | typography polish: titleMedium bold, maxLines=1, ellipsis (AC#21) |
| `apps/android-next/src/main/.../AppApplication.kt` | add `questPresentationModule` in startKoin (backend-dev) |
| `settings.gradle.kts` | add `include(":android:feature:quest:presentation")` (backend-dev) |

## Deleted Files

None (CatalogGridSection inline code in AppShellScreen is removed, not in separate file).

---

## Dependencies

- Phase-01 complete (quiz cleanup — no dead references)
- Phase-02 complete (QuestRepository, CatalogRepository in Koin)
- Phase-03 complete (sync infrastructure)
- Phase-04 complete (AuthRepository in Koin, navigation)
- OQ-TEST-1: `decompose-testutils` dependency в `quest/presentation/build.gradle.kts` — backend-dev добавляет в androidTest dependencies

---

## Acceptance Criteria (phase-05 scope)

- AC#21: HomeQuestsScreen uses CatalogGrid with titleMedium bold + 16dp corners + 12dp gap
- AC#22: HomeQuestsScreen excludes archived catalogs (DAO WHERE archived=0 already done; UI test)
- AC#23: MyQuestsScreen shows CatalogSpinner + LazyColumn + FAB
- AC#24: MyQuestsScreen empty state when no quests
- AC#25: Catalog filter in spinner → list filtered
- AC#26: QuestCard averageRating=2.7 → 2 full + partial 3rd star
- AC#27: QuestCard averageRating=null → 3 outline stars
- AC#28: QuestCard picturePath=null → placeholder icon
- AC#29: FAB click → navigate OpenQuestCreate (confirmed in phase-04; this phase wires in screen)
- AC#30: QuestCard.onClick → placeholder (TODO)
- AC#45-49: DefaultMyQuestsComponentTest guest + reactive auth scenarios
- `./gradlew :android:core:designsystem:test` green (BrandComponentsInvariantsTest)

---

## Tests Required

```
DefaultMyQuestsComponentTest (AC#45-49, Journey 7-8):
  - when_guest_then_state_empty_isGuest_true (AC#45, scenario 45a)
  - mid_session_login_switches_to_quest_list (AC#45, scenario 45b)
  - sign_out_returns_to_guest_state (AC#45, scenario 45c)
  - onCatalogSelected_filters_quests (Journey 7)
  - onCreateQuestClick_triggers_OpenQuestCreate (Journey 8 / AC#29)
  - archived_quest_not_in_list (AC#47)

DefaultHomeQuestsComponentTest:
  - when_observeCatalogs_emits_then_state_updated (AC#21-22)
  - when_catalog_archived_then_not_in_state (AC#22 UI-level)

BrandComponentsInvariantsTest (existing):
  - automatically passes if QuestCard.kt + StarRating.kt have @Preview + no hardcoded Color(0xFF...)

PartialFailRetryTest (NEW — Journey 10):
  - see tests.md for details
```

---

## Pattern Invariants

- `DefaultMyQuestsComponent` ДОЛЖЕН использовать `flatMapLatest` на `authRepo.observeUid()` Flow (не collect + launch — per DFD 3)
- Если `uid == null` → `questRepository.observeMyQuests` НЕ вызывается (no false database query for guest)
- `QuestCard.kt` и `StarRating.kt` ДОЛЖНЫ использовать `MaterialTheme.colorScheme.primary` для синего цвета (не `Color(0xFF4285F4)` — BrandComponentsInvariantsTest)
- Каждый файл в `components/` директории ДОЛЖЕН иметь минимум один `@Preview` composable (BrandComponentsInvariantsTest scan)
- `AppShellScreen.LocalTabContent` when-блок ДОЛЖЕН быть exhaustive по всем LocalConfig subtypes — добавление QuestCreateRoot требует нового branch
- `HomeQuestsScreen` заменяет inline `CatalogGridSection` — old code делает `koinInject<CatalogRepository>()` напрямую (use-cases.md violation); new code использует Component

---

## Validation

```bash
./gradlew :android:core:designsystem:test                    # BrandComponentsInvariantsTest
./gradlew :android:feature:quest:presentation:test           # DefaultMyQuestsComponentTest
./gradlew :android:feature:app-shell:presentation:test       # existing tests still green
./gradlew assemble
./gradlew allTests
```

---

## Handoff Notes

- OQ-TEST-1 (decompose-testutils): backend-dev добавляет `testImplementation(libs.decompose.testutils)` в `quest/presentation/build.gradle.kts`. Если `decompose.testutils` нет в `libs.versions.toml` — добавить там тоже.
- `CatalogSpinner` уже существует в designsystem (VERIFIED: `CatalogSpinner.kt:34`) — реиспользуется без изменений
- `CatalogDisplayItem` уже существует — реиспользуется
- `ObserveCatalogsUseCase` — существует в `shared/core/catalog/domain` — добавить в `QuestPresentationModule` как `get()`
- `HomeQuestsScreen` заменяет `CatalogGridSection` — typography polish: проверить `CatalogGrid.kt:71` параметры для `titleMedium bold`
