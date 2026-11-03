---
phase: 02
name: Home Quests Rename
complexity: simple
---

# Phase 02: Home Quests Rename

## Goal

Переименовать `DrawerSection.LocalSection.MyCourses → HomeQuests` + `LocalConfig.MyCoursesRoot → HomeQuestsRoot`, поменять порядок на LOCAL tab: `[HomeQuests, MyQuests, Settings]`, обновить displayName/icon в Labels.kt. Pure rename + reorder — независимая от Phase 01 фаза.

## Scope

- RENAME `DrawerSection.LocalSection.MyCourses` → `HomeQuests` (5 production файлов, 8 строк)
- RENAME `LocalConfig.MyCoursesRoot` → `HomeQuestsRoot`
- REORDER `visibleSections(Tab.LOCAL)`: HomeQuests первым (position 1)
- UPDATE `Labels.kt:52` — displayName "Мои курсы" → "Домашние квесты"
- UPDATE `Labels.kt:68` — `Icons.Default.Book` → `Icons.Default.Home`
- UPDATE `Labels.kt:88` — `MyCoursesRoot` → "Домашние квесты"
- UPDATE tests — 26 строк в 6 файлах

## Layer

domain rename + presentation labels

## Role Inputs

- `backend.md` — production code rename (domain + transitions)
- `frontend.md` — Labels.kt update (icon + displayName)
- `tests.md` — test rename (26 строк, 6 файлов)

## Dependencies

phases_ref: none (independent — может выполняться параллельно с Phase 01)

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 3: rename MyCourses → HomeQuests + reorder | backend-dev, frontend-dev | `DrawerSection.kt:38`, `TabConfig.kt:24`, `Visibility.kt:70,108`, `AppShellTransitions.kt:31`, `Labels.kt:52,68,88` | нет backend contract, pure rename | atomic rename 8 lines + reorder + test update 26 lines | `grep -rn "MyCourses\|MyCoursesRoot" shared/ android/` → 0 prod matches |

## New Files

none

## Modified Files

**Production (8 строк, 5 файлов):**
- `shared/feature/app-shell/domain/src/commonMain/.../model/DrawerSection.kt:38` — `MyCourses` → `HomeQuests`
- `shared/feature/app-shell/domain/src/commonMain/.../model/TabConfig.kt:24` — `MyCoursesRoot` → `HomeQuestsRoot`
- `shared/feature/app-shell/domain/src/commonMain/.../logic/Visibility.kt:70` — reorder: HomeQuests first; rename MyCourses → HomeQuests
- `shared/feature/app-shell/domain/src/commonMain/.../logic/Visibility.kt:108` — rootOf: `MyCourses → HomeQuests`, `MyCoursesRoot → HomeQuestsRoot`
- `android/feature/app-shell/presentation/src/main/.../ui/AppShellTransitions.kt:31` — `MyCourses → HomeQuests`, `MyCoursesRoot → HomeQuestsRoot`
- `android/feature/app-shell/presentation/src/main/.../ui/Labels.kt:52,68,88` — displayName + icon update

**Tests (26 строк, 6 файлов):**
- `NavStackTest.kt` (9 строк)
- `PrimaryUserJourneyTest.kt` (1 строка)
- `OnTabRetapUseCaseTest.kt` (5 строк)
- `AppShellTransitionsTest.kt` (3 строки)
- `VisibilityTest.kt` (7 строк — включая assertion порядка `[HomeQuests, MyQuests, Settings]`)
- `DefaultRootComponentTest.kt` (1 закомментированная строка — hygiene)

## Deleted Files

none

## Acceptance Criteria

- [ ] `DrawerSection.LocalSection.HomeQuests` существует, `MyCourses` — нет
- [ ] `LocalConfig.HomeQuestsRoot` существует, `MyCoursesRoot` — нет
- [ ] `visibleSections(Tab.LOCAL)` возвращает `[HomeQuests, MyQuests, Settings]` в этом порядке (HQ-01, HQ-07)
- [ ] `rootOf(HomeQuests)` → `Config.HomeQuestsRoot` (HQ-04)
- [ ] Labels: displayName("HomeQuests") == "Домашние квесты", icon == `Icons.Default.Home`
- [ ] `grep -rn "MyCourses\|MyCoursesRoot\|Мои курсы" shared/ android/` → 0 совпадений вне `docs/`
- [ ] Все тесты в `app-shell:domain:jvmTest` и `app-shell:presentation:test` зелёные

## State Matrix Coverage

Matrix rows (из `02-behavior.md` Flow 5, Visibility matrix): HQ-01..HQ-07 — порядок [HomeQuests, MyQuests, Settings].

## Domain Contract Coverage

Rename-only — Feature Domain Contract: `HomeQuests` заменяет `MyCourses` во всех контрактах.

## Pattern Invariants

- Rename строго атомарный — все 8 production строк и 26 test строк в одном coherent commit
- `Icons.Default.Book` остаётся в imports Labels.kt — используется в строках MyQuests и InternetSection.Catalog (не удалять импорт)
- `Icons.Default.Home` уже есть в imports Labels.kt:8 — не дублировать import
- Нет deep links, нет serialized strings, нет Room/Koin hardcoding для MyCourses — подтверждено в grounding

## Tests Required

Параллельно с production rename:

- HQ-01: given `Tab.LOCAL`, when `visibleSections(LOCAL, emptyStats)`, then `result[0] is HomeQuests`
- HQ-02: given `Tab.LOCAL`, when `visibleSections`, then `HomeQuests in result`
- HQ-03: given `Tab.LOCAL`, when `visibleSections`, then `MyCourses !in result` (old name отсутствует)
- HQ-04: given `HomeQuests`, when `rootOf(HomeQuests)`, then `Config.HomeQuestsRoot`
- HQ-05: given `HomeQuests`, when `.requiredRoles`, then `emptyMap()` (always visible)
- HQ-06: given `HomeQuests`, when type check, then `HomeQuests is LocalSection`
- HQ-07: given `Tab.LOCAL`, when `visibleSections`, then порядок строго `[HomeQuests, MyQuests, Settings]`

## Validation

| Команда | Ожидаемый результат |
|---------|---------------------|
| `./gradlew :shared:feature:app-shell:domain:jvmTest --no-configuration-cache` | GREEN — HQ-01..07 зелёные |
| `./gradlew :android:feature:app-shell:presentation:assembleDebug --no-configuration-cache` | GREEN — нет compile errors после rename |
| `grep -rn "MyCourses\|MyCoursesRoot\|Мои курсы" shared/ android/ --include="*.kt"` | 0 matches (не считая docs/) |
| `grep -rn "Домашние квесты" android/ --include="*.kt"` | matches в Labels.kt |

## Handoff Notes

Phase 02 не блокирует и не блокируется Phase 01. Может начаться параллельно. После завершения обоих фаз — разблокирована Phase 03.
