---
phase: 03
name: App-shell Domain Extensions
complexity: simple
---

# Phase 03: App-shell Domain Extensions

## Goal

Расширить `feature:app-shell:domain` согласно spec: добавить `DrawerFooterAction.SyncNow`, добавить методы в `RootComponent` interface и `UserStatsRepository` interface, добавить `RootEvent` variants, обновить `Visibility.isVisible/visibleSections/visibleFooterActions/rootOf` с superqualification bypass и новой signature. **Добавить minimal compile-fix stubs** в presentation call sites чтобы фаза оставалась compile-green до Phase 07 (Variant A из review).

## Scope

- ADD `DrawerFooterAction.SyncNow` в sealed set
- ADD `RootEvent.DevModeActivated`, `RootEvent.DevModeAlreadyActive`, `RootEvent.SyncStarted`
- ADD `RootComponent.onVersionTap(nowMillis: Long)` и `RootComponent.onSyncNow()`
- ADD `UserStatsRepository.setLocalDeveloperLevel(value: Int)` и `UserStatsRepository.refreshProfile(): Result<Unit>`
- UPDATE `Visibility.isVisible()` — добавить superqualification OR-bypass
- UPDATE `Visibility.visibleFooterActions()` — новая signature с `stats: UserStats`; добавить SyncNow в output
- UPDATE `DrawerSection.EventsSection.ActiveEvents.requiredRoles` — заменить magic `100` на `QualificationLevel.LEVEL_1.points`
- ADD minimal compile-fix stubs в presentation call sites (Variant A — compile-green contract):
  - `DefaultRootComponent.kt` — добавить `override fun onVersionTap(nowMillis: Long) = TODO()` и `override fun onSyncNow() = TODO()`
  - `DrawerFooter.kt` — добавить `DrawerFooterAction.SyncNow -> { /* TODO Phase 07 */ }` branch
  - `Labels.kt` — добавить `DrawerFooterAction.SyncNow -> "SyncNow" /* TODO Phase 07 */` branch (если exhaustive `when` over actions)
  - `FakeUserStatsRepository` (domain + presentation fakes) — add no-op overrides for new methods

## Layer

domain (interface contracts)

## Role Inputs

- `backend.md`
- `frontend.md` — none (только domain изменения, нет UI)
- `tests.md`

## Dependencies

phases_ref: [phase-01] — нужен `QualificationLevel` из `core:foundation` + `core:foundation` dep в `app-shell:domain/build.gradle.kts`

Примечание: Phase 02 (HomeQuests rename) работает в том же `Visibility.kt` — должна быть смерджена или выполнена последовательно. Если Phase 02 завершена первой — `visibleSections(Tab.LOCAL)` уже возвращает `HomeQuests` first, Phase 03 только добавляет superqualification bypass.

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 1: magic numbers → QualificationLevel | backend-dev | `DrawerSection.kt:100-103` | ADR-HLA-01 | replace `100` с `QualificationLevel.LEVEL_1.points` | `grep -rn "to 100" shared/feature/app-shell/domain/src/` → 0 |
| Problem 5: visibleFooterActions signature change breaks consumers | backend-dev | `Visibility.kt:142`, `DrawerFooterAction.kt:14` | breaking change — добавить compile stubs в call sites в рамках Phase 03 | расширить signature + add SyncNow + compile stubs | `:android:feature:app-shell:presentation:assembleDebug` green |
| Problem 2: isVisible нет OR-bypass для superqualification | backend-dev | `Visibility.kt:50-52` | ADR-HLA-02, domain AC | добавить `stats.qualification.developer >= LEVEL_1.points` bypass | DM-17..DM-23 green |

## New Files

none

## Modified Files

- `shared/feature/app-shell/domain/src/commonMain/.../model/DrawerFooterAction.kt` — ADD `SyncNow`
- `shared/feature/app-shell/domain/src/commonMain/.../model/RootEvent.kt` — ADD 3 new variants
- `shared/feature/app-shell/domain/src/commonMain/.../navigation/RootComponent.kt` — ADD 2 methods
- `shared/feature/app-shell/domain/src/commonMain/.../repository/UserStatsRepository.kt` — ADD 2 methods
- `shared/feature/app-shell/domain/src/commonMain/.../logic/Visibility.kt` — UPDATE isVisible + visibleFooterActions + (if Phase 02 done: visibleSections + rootOf already updated)
- `shared/feature/app-shell/domain/src/commonMain/.../model/DrawerSection.kt:100-103` — replace magic `100`
- `android/feature/app-shell/presentation/src/.../component/DefaultRootComponent.kt` — ADD TODO stubs for `onVersionTap` + `onSyncNow` (compile-fix, replaced in Phase 07)
- `android/feature/app-shell/presentation/src/.../ui/drawer/DrawerFooter.kt` — ADD `DrawerFooterAction.SyncNow -> { }` branch (compile-fix TODO, replaced in Phase 07)
- `android/feature/app-shell/presentation/src/.../ui/Labels.kt` — ADD SyncNow branch (compile-fix TODO, replaced in Phase 07)
- `shared/feature/app-shell/domain/src/commonTest/.../fake/FakeUserStatsRepository.kt` — ADD no-op new methods
- `android/feature/app-shell/presentation/src/.../fake/FakeUserStatsRepository.kt` — ADD no-op new methods

## Deleted Files

none

## Acceptance Criteria

- [ ] `DrawerFooterAction` sealed set содержит 3 members: `DesignCatalog`, `SyncNow`, `About`
- [ ] `RootEvent` sealed contains: `SystemBack` (existing) + `DevModeActivated` + `DevModeAlreadyActive` + `SyncStarted`
- [ ] `RootComponent` interface содержит `onVersionTap(nowMillis: Long)` и `onSyncNow()`
- [ ] `UserStatsRepository` interface содержит `setLocalDeveloperLevel(value: Int)` и `refreshProfile(): Result<Unit>`
- [ ] `isVisible(section, UserStats(developer=100))` → `true` независимо от `requiredRoles` (superqualification)
- [ ] `visibleFooterActions(isDebugBuild=false, UserStats(developer=100))` → `[DesignCatalog, SyncNow, About]`
- [ ] `visibleFooterActions(isDebugBuild=false, UserStats(developer=0))` → `[About]`
- [ ] `DrawerSection.EventsSection.ActiveEvents.requiredRoles` не содержит литерал `100` — использует `QualificationLevel.LEVEL_1.points`
- [ ] `DefaultRootComponent` компилируется с TODO stub overrides для `onVersionTap` + `onSyncNow`
- [ ] `DrawerFooter.kt` компилируется с stub SyncNow branch (no-op TODO body)
- [ ] `Labels.kt` компилируется с stub SyncNow branch
- [ ] `./gradlew :android:feature:app-shell:presentation:assembleDebug --no-configuration-cache` GREEN (stubs present, no compile errors)

## State Matrix Coverage

Matrix rows (из `02-behavior.md`):
- Footer Action Visibility Matrix (DM-24..DM-27): все 4 rows покрыты `visibleFooterActions` обновлением
- Superqualification Visibility Matrix (DM-17..DM-23): все 7 rows покрыты `isVisible` обновлением

## Domain Contract Coverage

Feature Domain Contract: `RootComponent` + `UserStatsRepository` + `DrawerFooterAction` + `Visibility` — все контракты согласованы с `06-api-contract.md §3-4`.

## Pattern Invariants

- `visibleFooterActions` signature: `(isDebugBuild: Boolean, stats: UserStats)` — НЕ добавлять `overlay` параметр (ADR-HLA-02: нет overlay entity)
- `isVisible` signature остаётся `(section: DrawerSection, stats: UserStats)` — superqualification через `stats.qualification.developer`, не через отдельный параметр
- Breaking change `DrawerFooterAction` + `visibleFooterActions` signature — нужно обновить все call sites в одном commit (или обеспечить compile green после каждого шага)
- `DrawerFooterAction.SyncNow` добавляется в позицию 2 (между DesignCatalog и About) — order matters для `visibleFooterActions` output

## Tests Required

Параллельно с реализацией:

- DM-17..DM-23: `VisibilityTest.kt` — обновить с superqualification scenarios (7 matrix cells)
- DM-24..DM-27: `VisibilityTest.kt` — footer matrix (4 scenarios)
- `DrawerFooterActionTest.kt` — обновить `assertEquals(2, all.size)` → `assertEquals(3, all.size)` + добавить SyncNow в expected list

## Validation

| Команда | Ожидаемый результат |
|---------|---------------------|
| `./gradlew :shared:feature:app-shell:domain:jvmTest --no-configuration-cache` | GREEN — DM-17..27, HQ-01..07 |
| `./gradlew :android:feature:app-shell:presentation:assembleDebug --no-configuration-cache` | GREEN — compile stubs устранили все breaking when-errors |
| `grep -rn "to 100" shared/feature/app-shell/domain/src/` | 0 matches |

## Handoff Notes

После Phase 03 разблокированы:
- Phase 04 (UserStats Data Layer) — нужен `UserStatsRepository` с `setLocalDeveloperLevel` + `refreshProfile`
- Phase 07 (Presentation Integration) — нужен `RootComponent.onVersionTap/onSyncNow` + `RootEvent` variants + `DrawerFooterAction.SyncNow`
