# Feature: Menu Refactor

## Status: implemented (2026-04-21)

**Implement phase** (2026-04-21): все 8 фаз реализованы, Codex cross-phase adversarial review пройден (6 findings: 2 blockers + 1 high + 2 medium + 1 low — все closed). Full test suite GREEN (12 modules JVM tests + assembleDebug + assembleDebugAndroidTest). Kotlin 1.9.22 → 2.1.20 upgrade применён. Детали в [`implementation.md`](./implementation.md).

## Status: planned (2026-04-20) — ready for implement

**Plan phase** (2026-04-20): 8-phase bottom-up plan в `plan/` (27 markdown files). Стратегия: Phase 01 Foundation blocker, Phase 02 Home Quests rename параллельно, Phase 03 contracts, Phase 04+05 data layer параллельно, Phase 06 sync, Phase 07 presentation, Phase 08 integration tests. 3 Codex CLI adversarial review rounds пройдены. User Decision на architectural conflict (`CatalogDto` location) — **Variant A applied** (DTO в `core:catalog:data/commonMain`, pure Kotlin; Firebase adapter в `platform/firebase`). Minimal design edits в `06-api-contract.md §9.2-9.3, §12, §15` + `08-storage-model.md §7.3` для internal consistency.

## Status: designed (2026-04-20) — ready for plan

**Design phase** (2026-04-20): двухуровневая архитектура (C4 L1-L2 от architect-high-level + L3 от architect-component), 11 ADRs с Alternatives Considered, 77 test scenarios (69 spec-traced + 8 supplemental), 3 Codex adversarial review loops пройдены (Realist → Skeptic → Architect). 23 findings закрыты through design iteration. 0 spec ambiguities. Walking Skeleton diff явно задокументирован (KEPT / REMOVED / RENAMED / ADDED).

**Codex cross-review (2026-04-19)**: 11 findings выявлены, все исправлены в spec файлах. Теги `Codex fix #N` добавлены в relevant места. Key changes:
- dev-mode: `registerTap` signature + `LocalDeveloperOverride` overlay model + simplified FSM + combined Footer Contract
- catalog-foundation: `picturePath` sweep, stable id-sort, foundation-only scope, Quest delta contract
- home-quests: expanded rename checklist (33 references across 11 files)
- qualification-levels: ADR-0006 wording clarification (`enum class` не `value class`)

## Sub-tasks

| # | Sub-spec | Status | Walking Skeleton | Depends on |
|---|----------|--------|------------------|-----------|
| 1 | [qualification-levels](./0-spec-qualification-levels.md) — вынести пороги квалификаций (100/200/300) в enum | spec | ✅ 14 tests | — |
| 2 | [dev-mode](./0-spec-dev-mode.md) — 10-tap по версии → локально developer=LEVEL_1, superqualification bypass | spec | ✅ 40 tests | #1 |
| 3 | [home-quests](./0-spec-home-quests.md) — rename MyCourses → HomeQuests + порядок | spec | N/A (rename) | — |
| 4 | [catalog-foundation](./0-spec-catalog-foundation.md) — Catalog сущность + Firebase + SyncNow + Quest.catalogId | spec | ✅ 27 tests | #1 + #2 |

## Documents

| Document | Status | Owner / Content |
|----------|--------|----------------|
| `0-spec.md` (master) | Complete | cross-cutting scope + ADR updates |
| `0-spec-qualification-levels.md` | Complete | enum location, 14 Domain Test Scenarios |
| `0-spec-dev-mode.md` | Complete | 10-tap FSM, superqualification, visibility matrix |
| `0-spec-home-quests.md` | Complete | rename + reorder, 7 Test Scenarios |
| `0-spec-catalog-foundation.md` | Complete | Catalog entity, Firestore, UI, 23 Scenarios |
| `1-research.md` | Complete (2026-04-20) | module map, Walking Skeleton status |
| `2-grounding.md` | Complete (2026-04-20) | 5 Problems with VERIFIED claims |
| `01-architecture.md` | Complete (2026-04-20) | C4 L1/L2/L3, 10 Mermaid diagrams |
| `02-behavior.md` | Complete (2026-04-20) | 6 DFDs, Tap FSM, Visibility + Footer Matrix |
| `03-decisions.md` | Complete (2026-04-20) | 11 ADRs (HLA-01..07 + L3-01..04) with Alternatives |
| `04-testing.md` | Complete (2026-04-20) | 77 scenarios, 7 journey integration tests, fakes |
| `05-prior-art.md` | Complete (2026-04-20) | SDK references + SUPERSEDED markers |
| `06-api-contract.md` | Complete (2026-04-20) | canonical signatures (authoritative) |
| `07-events.md` | Complete (2026-04-20) | RootEvent hierarchy + event flow |
| `08-storage-model.md` | Complete (2026-04-20) | Room entities, Firestore schema, mappers |

**Codex review artifacts** (`_review/`):
- `realist-01-02.md` — 8 findings (semantic framing) → closed via TARGET STATE disclaimers
- `skeptic-03.md` — 6 findings (rubber-stamp alternatives) → closed via ADR expansion
- `architect-04-conditional.md` — 9 findings (cross-document drift) → closed via signature sync

## Research summary

- Walking Skeletons **полностью сгенерированы** для 3 sub-spec: qualification-levels (14 tests), dev-mode (32+ tests), catalog-foundation (27 tests). Все зелёные на JVM.
- Home-quests — 8 production lines / 26 test lines rename в 11 files; no deep links, no DI hardcoding.
- Критичные gap-ы (phase-01 addressрует): `:shared:core:catalog:data` не в settings, `qualification:data` пусто, Room/WorkManager/DataStore/Coil не применяются в new-stack, нет SnackbarHost, нет `refreshProfile()` event в новом app.

## User Decisions (resolved 2026-04-20)

| # | Topic | Decision |
|---|---|---|
| 1 | QualificationLevel location | `shared/core/foundation/` |
| 2 | Dev mode model | Revert codex fix #2 — прямая запись `developer=100` в local Room (без overlay) |
| 3 | SyncNow trigger | `RootComponent.onSyncNow()` + `RootEvent.SyncStarted` |
| 4 | Quest placeholder | Остаётся в catalog/domain как TEMPORARY |
| 5 | Sync infrastructure | SyncWorker + refreshProfile() — полный WorkManager |
| 6 | Overlay storage | Room (central AppDatabase), persists между рестартами |
| 7 | Image loader | Coil 3.4.0 |
| 9 | Sync periodicity | 1/2/3/4/7/14/30 дней, default 1 день |

## Walking Skeleton changes (per Q2 decision)

**Удаляются** в phase-01 dev-mode (revert codex fix #2):
- `shared/feature/qualification/domain/.../dev_mode/model/LocalDeveloperOverride.kt`
- `shared/feature/qualification/domain/.../dev_mode/model/DeveloperLevelStats.kt`
- `shared/feature/qualification/domain/.../dev_mode/logic/EffectiveDeveloperLevel.kt`
- `shared/feature/qualification/domain/.../dev_mode/repository/LocalDeveloperOverrideRepository.kt`
- Тесты + fake к ним.

**Перемещаются** (per Q1 decision):
- `QualificationLevel.kt` → `shared/core/foundation/`
- `QualificationLevelTest.kt` → `shared/core/foundation/src/commonTest/`

**Остаются и обновляются**:
- `TapProgress`, `TapResult`, `RegisterTap` — param rename `currentDeveloperLevel`
- `ActivateDevModeUseCase` — принимает `UserStatsRepository`, вызывает `setLocalDeveloperLevel(100)` при Activated

## Next steps

1. ✅ **`/feature-plan menu-refactor`** — выполнено (2026-04-20). Plan в `plan/` — 8 фаз, 27 файлов, bottom-up strategy, 3 Codex review rounds.
2. **`/feature-implement menu-refactor`** — реализация по phase files. Start with Phase 01 (Foundation Infrastructure + Walking Skeleton cleanup); Phase 02 (Home Quests Rename) — параллельно.

## Key Design Decisions (snapshot)

- **HLA-01** `QualificationLevel` → `shared/core/foundation/`
- **HLA-02** No overlay entity — direct `setLocalDeveloperLevel(Int)` (User Decision #2)
- **HLA-03** Central `AppDatabase` в `shared/core/persistence/`
- **HLA-04** Single `SyncWorker(syncables: List<Syncable>)` + `Syncable` в `shared/core/sync/`
- **HLA-05** `RootComponent.onSyncNow()` method + `RootEvent.SyncStarted`
- **HLA-06** Coil 3.4.0 + Koin 4.2.1
- **HLA-07** URL pre-resolution in data layer → `CatalogEntity.pictureUrl` cache
- **L3-01** `ActivateDevModeUseCase` lambda injection
- **L3-02** `_tapProgress` в `DefaultRootComponent` (testability, not spec req)
- **L3-03** `CatalogDisplayItem` в `android:core:designsystem`
- **L3-04** `CatalogDao.replaceAll()` с `@Transaction`

## Scope summary

### In Scope
- Domain enum `QualificationLevel { LEVEL_1(100), LEVEL_2(200), LEVEL_3(300) }` в `shared/feature/qualification/domain/`
- Dev Mode — 10-tap по `v$versionName` → локально `developer = LEVEL_1.points`
- DEVELOPER как суперквалификация (OR-bypass в `isVisible`)
- `visibleFooterActions(isDebugBuild, stats)` — DesignCatalog видим при dev mode в release
- Rename `MyCourses → HomeQuests` + displayName "Домашние квесты" + порядок в LOCAL
- ADR-0006 extension (superqualification, local override)
- `docs/invariants.md` extension (2 new invariants)

### Out of Scope
- Каталог как сущность / spinner — будет отдельная фича
- Семантика `LEVEL_2 / LEVEL_3` — пока только пороги, без привязки к функциям
- Перенос legacy dev-пунктов меню (MENU_CHAT_BANNED, etc.)
- UI toggle для отключения Dev Mode (reset только через server sync)
- UI наполнение HomeQuests / MyQuests root screens
