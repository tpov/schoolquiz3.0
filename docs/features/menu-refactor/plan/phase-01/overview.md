---
phase: 01
name: Foundation Infrastructure
complexity: complex
---

# Phase 01: Foundation Infrastructure

## Goal

Создать всю необходимую инфраструктуру для feature: core:persistence (AppDatabase + DAOs + Entities), core:sync (Syncable), core:foundation (QualificationLevel MOVED сюда), подключить ksp в нужных модулях, добавить coil3/WorkManager в libs.versions.toml, включить :shared:core:catalog:data в settings.gradle.kts, выполнить Walking Skeleton cleanup (удалить overlay-файлы, переписать ActivateDevModeUseCase на lambda injection).

## Scope

- MOVE `QualificationLevel.kt` + `QualificationLevelTest.kt` из `qualification:domain` → `core:foundation`
- REMOVE Walking Skeleton overlay-файлы (5 файлов + тесты к ним) из `qualification:domain`
- REWRITE `ActivateDevModeUseCase` — lambda injection вместо overlayRepo
- UPDATE `RegisterTap.kt` — переименовать параметр `currentEffectiveDeveloperLevel → currentDeveloperLevel`
- CREATE `core:persistence` content: `AppDatabase`, `UserStatsEntity`, `UserStatsDao`, `CatalogEntity`, `CatalogDao`
- CREATE `core:sync` content: `Syncable` interface
- CREATE `shared:core:catalog:data` module — включить в settings.gradle.kts + build.gradle.kts
- UPDATE `settings.gradle.kts` — добавить `:shared:core:catalog:data`
- UPDATE `libs.versions.toml` — добавить coil3 3.4.0, active ksp plugin references
- UPDATE `build.gradle.kts` для нескольких модулей: ksp plugin, coroutines, core:foundation deps

## Layer

infrastructure + domain (Walking Skeleton cleanup)

## Role Inputs

- `backend.md` — основная работа этой фазы
- `frontend.md` — none (нет UI-изменений в этой фазе)
- `tests.md` — Walking Skeleton test cleanup + новые DAO/entity тесты

## Dependencies

phases_ref: none (blocker-фаза, все остальные зависят от неё)

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 1: magic numbers + cross-feature import | backend-dev | `DrawerSection.kt:100-103`, `qualification:domain/build.gradle.kts` | ADR-HLA-01: QualificationLevel → core:foundation | MOVE QualificationLevel + update imports | `grep -rn "to 100" shared/feature/app-shell/domain/` → 0 matches |
| Problem 2: Walking Skeleton overlay files vs revert codex fix #2 | backend-dev | `qualification:domain/dev_mode/model/`, `dev_mode/logic/`, `dev_mode/repository/` | ADR-HLA-02: прямая Room write, нет overlay | DELETE 5 overlay files + их тесты; REWRITE ActivateDevModeUseCase | `shared/feature/qualification/domain:jvmTest` green |
| Problem 4: Room/ksp инфраструктура отсутствует | backend-dev | `settings.gradle.kts:26`, `libs.versions.toml:25,27` | ADR-HLA-03: central AppDatabase | CREATE core:persistence + enable ksp | `./gradlew :shared:core:persistence:jvmTest` green |
| Problem 4: :shared:core:catalog:data не включён | backend-dev | `settings.gradle.kts` | модуль нужен для CatalogRepositoryImpl | `include(":shared:core:catalog:data")` + create build.gradle.kts | module resolves in IDE + build |
| Problem 2: Syncable interface отсутствует | backend-dev | `shared/core/sync/` (пустой stub) | ADR-HLA-04: Syncable в core:sync | CREATE Syncable.kt | compiles, импортируется из data modules |

## New Files

- `shared/core/foundation/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/foundation/QualificationLevel.kt` (MOVED)
- `shared/core/foundation/src/commonTest/kotlin/.../QualificationLevelTest.kt` (MOVED)
- `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt` (NEW)
- `shared/core/persistence/src/commonMain/kotlin/.../UserStatsEntity.kt` (NEW)
- `shared/core/persistence/src/commonMain/kotlin/.../UserStatsDao.kt` (NEW)
- `shared/core/persistence/src/commonMain/kotlin/.../CatalogEntity.kt` (NEW)
- `shared/core/persistence/src/commonMain/kotlin/.../CatalogDao.kt` (NEW)
- `shared/core/sync/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/sync/Syncable.kt` (NEW)
- `shared/core/catalog/data/build.gradle.kts` (NEW — new module)
- `shared/core/catalog/data/src/commonMain/...` (directory structure — NEW)
- `shared/feature/qualification/domain/.../dev_mode/use_case/ActivateDevModeUseCase.kt` (REWRITTEN)

## Modified Files

- `settings.gradle.kts` — добавить `include(":shared:core:catalog:data")` (backend-dev owner)
- `libs.versions.toml` — добавить coil3 = "3.4.0", active ksp references (backend-dev owner)
- `shared/core/foundation/build.gradle.kts` — добавить kotlin multiplatform targets если нет
- `shared/core/persistence/build.gradle.kts` — ksp plugin + Room 2.7 deps
- `shared/core/sync/build.gradle.kts` — kotlin multiplatform (только interface, нет deps)
- `shared/feature/qualification/domain/build.gradle.kts` — добавить `kotlinx.coroutines.core` + `core:foundation` dep
- `shared/feature/app-shell/domain/build.gradle.kts` — добавить `core:foundation` dep
- `shared/feature/qualification/domain/.../dev_mode/logic/RegisterTap.kt` — param rename

## Deleted Files

- `shared/feature/qualification/domain/.../dev_mode/model/LocalDeveloperOverride.kt`
- `shared/feature/qualification/domain/.../dev_mode/model/DeveloperLevelStats.kt`
- `shared/feature/qualification/domain/.../dev_mode/logic/EffectiveDeveloperLevel.kt`
- `shared/feature/qualification/domain/.../dev_mode/repository/LocalDeveloperOverrideRepository.kt`
- `shared/feature/qualification/domain/.../dev_mode/repository/FakeLocalDeveloperOverrideRepository.kt` (в commonTest)
- `shared/feature/qualification/domain/.../dev_mode/model/LocalDeveloperOverrideTest.kt`
- `shared/feature/qualification/domain/.../dev_mode/logic/EffectiveDeveloperLevelTest.kt`
- `shared/feature/qualification/domain/.../dev_mode/fake/FakeLocalDeveloperOverrideRepositoryTest.kt`

## Acceptance Criteria

- [ ] `QualificationLevel` находится в `shared/core/foundation/`, package `com.tpov.schoolquiz.shared.core.foundation`
- [ ] `shared/feature/qualification/domain:jvmTest` — все тесты зелёные (QL-01..14 зелёные из нового пути)
- [ ] Overlay файлы удалены; `grep -rn "LocalDeveloperOverride\|DeveloperLevelStats\|EffectiveDeveloperLevel" shared/feature/qualification/` → 0 prod matches
- [ ] `ActivateDevModeUseCase` принимает `readCurrentDeveloperLevel: () -> Int` + `onDevModeActivated: suspend () -> Unit` (lambda injection per ADR-L3-01)
- [ ] `AppDatabase` в core:persistence; `@Database(entities = [UserStatsEntity::class, CatalogEntity::class], version = 1)`
- [ ] `UserStatsDao` содержит `observeByUid`, `findByUid`, `upsert`, `updateDeveloperLevel`
- [ ] `CatalogDao` содержит `observeAll`, `findById`, `insertAll`, `deleteAll`, `replaceAll` (с @Transaction)
- [ ] `Syncable` interface в `core:sync`
- [ ] `:shared:core:catalog:data` включён в settings.gradle.kts и build успешен
- [ ] `./gradlew :shared:core:foundation:jvmTest :shared:core:persistence:jvmTest :shared:feature:qualification:domain:jvmTest` — green

## State Matrix Coverage

Matrix rows (из `02-behavior.md` Tap FSM State Matrix): DM-01..DM-10 покрыты в `RegisterTapTest.kt` (PRESERVED — только param rename).

## Domain Contract Coverage

Feature Domain Contract затронут: удаление overlay-части + MOVE QualificationLevel. Все QL-01..14 + DM-01..10 scenarios остаются зелёными.

## Pattern Invariants

- Все Walking Skeleton файлы в `qualification:domain/dev_mode/` которые ОСТАЮТСЯ (`TapProgress.kt`, `RegisterTap.kt`, `TapResult.kt`) — не трогать бизнес-логику, только param rename в RegisterTap
- `core:foundation` не зависит от `feature/*` — одностороннее: `feature/* → core/foundation`, НЕ наоборот
- `core:persistence` не зависит от `feature/*` — только Room entities и DAOs, без domain знаний
- `core:sync` — только interface `Syncable`, нет Android imports (KMP-compatible commonMain)
- `ActivateDevModeUseCase` после rewrite НЕ импортирует `app-shell:domain` (lambda injection = единственный способ избежать cross-feature BLOCKER)
- Новые stateful fields в фазе (`_tapProgress` будет в Phase 07) — здесь только инфраструктура

### Options Considered

| Критерий | Option A (recommended): Central AppDatabase в core:persistence | Option B: Per-feature databases | Option C: DataStore для UserStats вместо Room |
|----------|----------------------------------------------------------------|--------------------------------|----------------------------------------------|
| Complexity | low | medium | low |
| Test cost | 1 instrumented DB class | 2+ instrumented DB classes | minimal |
| Refactor cost если неверно | small — добавить entity = 1 @Entity + DB version bump | medium — cross-database queries потребуют рефактор | large — DataStore не поддерживает JOIN/Flow per-field |
| Coupling с external SDK | Room (одна точка) | Room (несколько инстанций) | DataStore |
| Future-proofing | high | low | low (16+ fields неудобны) |

**Recommended: Option A**

**Rationale:** ADR-HLA-03 закрыл этот вопрос — central AppDatabase масштабируется, тестируется единым `Room.inMemoryDatabaseBuilder()`, нет дублирования Room setup overhead.

**Rejected Option B:** не масштабируется; future cross-feature queries потребуют refactor.

**Rejected Option C:** DataStore Preferences неудобен для 16+ полей; нет типобезопасного Flow per-entity; Room уже задекларирован в libs.versions.toml.

## Tests Required

Параллельно с реализацией (TDD):

- `QL-01..QL-14`: `QualificationLevelTest.kt` в `core:foundation/commonTest/` — перемещён из `qualification:domain`; обновить package declaration; все 14 тестов должны быть зелёными
- `DM-01..DM-10`: `RegisterTapTest.kt` — сохранить без изменений, только param rename в call sites (тест сам обновляется только в части аргументов)
- `DM-11..DM-16`: `ActivateDevModeUseCaseTest.kt` — REWRITE через lambda fakes:
  - `DM-11`: given Activated result, when invoke, then `onDevModeActivated` вызван 1 раз
  - `DM-12`: given NoChange, then `onDevModeActivated` НЕ вызван
  - `DM-13`: given AlreadyDev, then `onDevModeActivated` НЕ вызван
  - `DM-14`: invoke возвращает TapResult корректно
  - `DM-15`: `readCurrentDeveloperLevel` вызывается 1 раз per invoke
  - `DM-16`: suspend lambda не блокирует FSM
- `AppDatabaseMigrationTest` (instrumented): `version1_schema_valid` — verify tables `user_stats` + `catalogs` exist

## Validation

| Команда | Ожидаемый результат |
|---------|---------------------|
| `./gradlew :shared:core:foundation:jvmTest --no-configuration-cache` | GREEN — 14 QL tests |
| `./gradlew :shared:feature:qualification:domain:jvmTest --no-configuration-cache` | GREEN — DM-01..16 (RegisterTap + ActivateDevModeUseCase) |
| `./gradlew :shared:core:persistence:jvmTest --no-configuration-cache` | GREEN (может быть пусто — entity тесты instrumented) |
| `./gradlew :shared:core:sync:jvmTest --no-configuration-cache` | GREEN — Syncable interface compiles |
| `./gradlew :shared:feature:qualification:domain:compileKotlinJvm --no-configuration-cache` | GREEN — нет compile errors после MOVE + DELETE в qualification:domain |
| `./gradlew :shared:core:catalog:data:compileKotlinJvm --no-configuration-cache` | GREEN — новый KMP модуль компилируется |
| `grep -rn "LocalDeveloperOverride\|DeveloperLevelStats\|EffectiveDeveloperLevel" shared/feature/qualification/domain/src/` | 0 matches |
| `grep -rn "to 100" shared/feature/app-shell/domain/src/` | 0 matches (замена идёт в Phase 03) |

## Handoff Notes

После Phase 01 разблокированы:
- Phase 03 (App-shell Domain Extensions) — зависит от `core:foundation.QualificationLevel` + обновлённого `Syncable`
- Phase 04 (UserStats Data Layer) — зависит от `core:persistence.UserStatsDao + UserStatsEntity`
- Phase 05 (Catalog Data Stack) — зависит от `core:persistence.CatalogDao + CatalogEntity` + `catalog:data` module
- Phase 06 (Sync Infrastructure) — зависит от `Syncable` interface + `SyncWorker` (независим от Phase 03-05 scaffold-wise, но функционально нужны implementors)
- Phase 07 (Presentation Integration) — зависит от всего выше

Phase 02 (Home Quests Rename) — **может начаться параллельно** с Phase 01 (независимая rename-фаза).
