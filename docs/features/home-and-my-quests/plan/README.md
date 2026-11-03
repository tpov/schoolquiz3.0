---
feature: home-and-my-quests
plan_version: 1.0
date: 2026-04-23
strategy: bottom-up integration (Walking Skeleton generated at spec-phase)
---

# Implementation Plan — Home & My Quests

## Phase Strategy

**Strategy: Bottom-up integration mode.**

Walking Skeleton (pure domain + repository interfaces + use cases + in-memory fakes for all 6 entities) was generated at spec-phase. Phases 01-05 = adapter-only integration — production implementations of interfaces against Room, Firebase, and Compose UI.

Phasing logic:
1. **Phase-01** — DB scaffold + schema v2 + quiz cleanup. No phase depends on another for compilation EXCEPT kspJvm (BD-1 blocker).
2. **Phase-02** — Data stacks (5 new feature modules). Requires Phase-01 (Room entities must exist before DAOs can be wrapped).
3. **Phase-03** — Sync orchestrator (CascadingSyncOrchestrator). Requires Phase-02 (all Repository interfaces implemented) and Phase-04 at runtime (AuthRepository in Koin).
4. **Phase-04** — AppShell navigation + AuthRepository Koin binding. Can start in parallel with Phase-02 (no schema dependency). Required by Phase-03 at runtime.
5. **Phase-05** — Presentation layer (Decompose Components + Compose screens + designsystem components). Requires Phase-02 (QuestRepository) and Phase-04 (navigation).

---

## Phases Table

| Phase | Name | Goal | Depends on | Role Inputs | Complexity | Validation |
|-------|------|------|------------|-------------|------------|------------|
| phase-01 | Scaffold Foundation + DB Schema v2 + Quiz Cleanup | AppDatabase v1→v2 + 5 entities + kspJvm blocker + Coil bump + quiz module removal | none | backend, tests | complex | `./gradlew :shared:core:persistence:connectedAndroidTest` + `./gradlew assemble` |
| phase-02 | Cascade Data Stacks | 5 feature data modules + Catalog delta-sync + Firebase adapters + SyncStateRepository in Koin | phase-01 | backend, tests | complex | `./gradlew :shared:feature:quest:data:jvmTest` + all 5 data modules |
| phase-03 | CascadingSyncOrchestrator + SyncLevel | Recursive 6-level orchestrator + SyncModule update | phase-02 (+ phase-04 runtime) | backend, tests | complex | `./gradlew :shared:core:sync:jvmTest` (JVM OK before phase-04); `./gradlew assemble` only after phase-04 backend task #1 (AuthRepository Koin binding) |
| phase-04 | AppShell + Navigation + AuthRepository | OpenQuestCreate routing + Labels.kt + verify AuthRepository Koin | phase-01 | backend, tests | simple | `./gradlew :shared:feature:app-shell:domain:jvmTest` + `:android:feature:app-shell:presentation:test` |
| phase-05 | Presentation + Designsystem | MyQuestsScreen + HomeQuestsScreen + QuestCard + StarRating + Decompose Components | phase-02, phase-04 | backend, frontend, tests | complex | `./gradlew :android:core:designsystem:test` + `:android:feature:quest:presentation:test` |

---

## File Map (Total)

### New Files

| Count | Category | Examples |
|-------|----------|---------|
| 15 | phase-01: DB entities + DAOs + tests | QuestEntity, QuestDao, SectionEntity, SectionDao, ThemeEntity, ThemeDao, LessonEntity, LessonDao, QuestionEntity, QuestionDao, StringSetConverter, AppDatabaseSchemaValidationTest, QuestDaoBoundaryTest, SectionDaoBoundaryTest, StringSetConverterTest (JVM) — `InMemorySyncStateRepositoryTest` pre-existing in Walking Skeleton |
| 50+ | phase-02: 5 data modules + firebase | QuestRepositoryImpl, QuestLocalDataSourceImpl, QuestRemoteDataSource, QuestDto, QuestDtoMapper, QuestMapper, QuestDataModule + firebase equivalents × 5 features |
| 4 | phase-03: sync orchestrator | CascadingSyncOrchestrator, SyncLevel, CascadingSyncOrchestratorTest, CascadeSyncIntegrationTest |
| 0 | phase-04: all existing files | (verify + implement in existing files only) |
| 15 | phase-05: presentation + designsystem | MyQuestsComponent, DefaultMyQuestsComponent, HomeQuestsComponent, DefaultHomeQuestsComponent, MyQuestsScreen, HomeQuestsScreen, QuestPresentationModule, QuestToDisplayItem, QuestCard, StarRating, QuestDisplayItem, DefaultMyQuestsComponentTest, DefaultHomeQuestsComponentTest, build.gradle.kts (quest/presentation) |

**Total new files: ~84**

### Modified Files (summary)

| Count | Category |
|-------|----------|
| 11 | phase-01: CatalogEntity, CatalogDao, AppDatabase, PersistenceModule, CatalogLocalDataSource, CatalogDto, mappers, FirestoreCatalogDtoMapper, libs.versions.toml, persistence/build.gradle.kts, settings.gradle.kts |
| 6 | phase-02: CatalogRepositoryImpl, CatalogRemoteDataSource, FirebaseCatalogRemoteDataSource, SyncModule, settings.gradle.kts, AppApplication.kt |
| 1 | phase-03: SyncModule.kt |
| 3 | phase-04: AppShellTransitions, DefaultRootComponent, Labels.kt |
| 5 | phase-05: AppShellScreen, AppShellPresentationModule, CatalogGrid, AppApplication.kt, settings.gradle.kts |

**Total modified files: ~26**

### Deleted Files

| Count | Category |
|-------|----------|
| 8 | phase-01: quiz module dirs (3) + placeholder files in catalog/domain (5) |

---

## AC Coverage Map

All 58 Acceptance Criteria distributed across phases. ACs from `0-spec.md` lines 1088-1175.

| Phase | ACs Covered | ACs |
|-------|-------------|-----|
| phase-01 | AC#3, AC#12, AC#13, AC#14, AC#15 | 5 ACs |
| phase-02 | AC#7, AC#8, AC#9, AC#19, AC#20, AC#48, AC#49 | 7 ACs |
| phase-03 | AC#10, AC#11, AC#16, AC#17, AC#50, AC#54, AC#55, AC#56, AC#57 | 9 ACs |
| phase-04 | AC#29* (domain: scenarios 41a-41e) | 1 AC |
| phase-05 | AC#21, AC#22, AC#23, AC#24, AC#25, AC#26, AC#27, AC#28, AC#29* (UI wire), AC#30, AC#45, AC#46, AC#47 | 13 ACs |
| Walking Skeleton (pre-existing green) | AC#1, AC#2, AC#4, AC#5, AC#6, AC#41, AC#42, AC#43, AC#44 | 9 ACs (domain + InMemorySyncStateRepository tests already exist) |
| Out-of-scope (Firebase rules — server-enforced) | AC#35, AC#36, AC#37, AC#38, AC#39, AC#40, AC#51, AC#52, AC#53 | 9 ACs (Firestore security rules: client cannot unit-test server rules) |
| Out-of-scope (server-side / post-MVP) | AC#18 (dev-mode SyncNow button — separate dev-tools feature), AC#31 (offline-first integration — covered by OfflineEmptyIntegrationTest at component level), AC#32 (reactive catalog update — covered by DefaultHomeQuestsComponentTest), AC#33 (reactive quest update — covered by DefaultMyQuestsComponentTest), AC#34 (Server Invariant B propagation — Cloud Function contract, not client code), AC#58 (full integration cross-module orchestrator + server — see CascadeSyncIntegrationTest for client portion) | 6 ACs (server contracts + post-MVP scope) |

**Total in-scope ACs across phases: 35 unique ACs** (phases 01–05)
**Pre-existing (Walking Skeleton — tests already green): 9 ACs** (AC#1, AC#2, AC#4, AC#5, AC#6, AC#41, AC#42, AC#43, AC#44)
**Out-of-scope (Firebase security rules — server-enforced): 9 ACs (AC#35-40, AC#51-53)**
**Out-of-scope (server-side contracts / post-MVP): 6 ACs (AC#18, AC#31-34, AC#58) — see note below**

> * AC#29 засчитывается в phase-04 (domain-level AppShellTransitionsTest) и phase-05 (UI-level DefaultMyQuestsComponentTest) — одна уникальная AC, два gate events. В подсчёте "35 unique ACs" считается один раз.
>
> Note: AC#41-44 (InMemorySyncStateRepository contract) перенесены в Walking Skeleton — `InMemorySyncStateRepositoryTest.kt` уже существует в `shared/core/sync/src/commonTest/` (VERIFIED). AC#48-49 перенесены из phase-05 в phase-02 (QuestRepositoryImplTest Edge 1.9 scenarios). AC#50 IS IN-SCOPE — covered by CascadingSyncOrchestratorTest in phase-03. AC#31-33 partially covered at component level (phase-05); full SyncWorker integration beyond plan scope. AC#58 client portion covered by CascadeSyncIntegrationTest (phase-03).

### Detailed AC Distribution

#### phase-01 ACs

| AC | Statement (abbreviated) | Test |
|----|--------------------------|------|
| AC#3 | Section/Theme/Lesson/Question entities created with correct columns | AppDatabaseSchemaValidationTest |
| AC#12 | AppDatabase v2 — 7 tables | AppDatabaseSchemaValidationTest |
| AC#13 | CatalogDao.upsertByIdIfNewerVersion skips on equal/older version | QuestDaoBoundaryTest |
| AC#14 | QuestDao.observeMyQuests(uid, null) correct filter | QuestDaoBoundaryTest |
| AC#15 | QuestDao.observeMyQuests(uid, catalogId) catalog filter | QuestDaoBoundaryTest |

> AC#41-44 (InMemorySyncStateRepository) — Walking Skeleton pre-existing. `InMemorySyncStateRepositoryTest.kt` VERIFIED at `shared/core/sync/src/commonTest/`. Phase-01 не создаёт этот файл.

#### phase-02 ACs

| AC | Statement (abbreviated) | Test |
|----|--------------------------|------|
| AC#7 | CatalogRepositoryImpl cursor=0 → all upserted + setCursor called | CatalogRepositoryImplTest |
| AC#8 | CatalogRepositoryImpl archived=true → deleteById | CatalogRepositoryImplTest |
| AC#9 | QuestRepositoryImpl makes 2 parallel Firestore queries + dedupe | QuestRepositoryImplTest |
| AC#19 | First-sync: 4 catalogs + 20 quests all in Room | CatalogFirstFetchIntegrationTest |
| AC#20 | Delta-sync: cursor filters only changed items | CatalogFirstFetchIntegrationTest |
| AC#48 | Quest visibleOn=emptySet + authorUid=me → deleted (QuestRepositoryImpl Edge 1.9) | QuestRepositoryImplTest |
| AC#49 | Quest visibleOn=emptySet + authorUid=other → deleted (QuestRepositoryImpl Edge 1.9) | QuestRepositoryImplTest |

#### phase-03 ACs

| AC | Statement (abbreviated) | Test |
|----|--------------------------|------|
| AC#10 | Catalog cv unchanged → quests sync SKIPPED | CascadingSyncOrchestratorTest |
| AC#11 | Quest cv grew → sections pulled | CascadingSyncOrchestratorTest |
| AC#16 | SyncWorker runs cascade steps (manual smoke) | manual |
| AC#17 | SyncWorker network fail → Result.retry() | CascadingSyncOrchestratorTest |
| AC#50 | Batch > 30 parent ids → chunked | CascadingSyncOrchestratorTest |
| AC#54 | Catalog step success + quest step fail → catalogsCursor advanced, questsCursor not | CascadingSyncOrchestratorTest |
| AC#55 | Retry with same cursor → idempotent | CascadeSyncIntegrationTest |
| AC#56 | Process death → full resync idempotent | CascadeSyncIntegrationTest |
| AC#57 | After successful retry → questsCursor advances | CascadeSyncIntegrationTest |

#### phase-04 ACs

| AC | Statement (abbreviated) | Test |
|----|--------------------------|------|
| AC#29 | FAB click → OpenQuestCreate navigation (domain) | AppShellTransitionsTest scenarios 41a-41e |

#### phase-05 ACs

| AC | Statement (abbreviated) | Test |
|----|--------------------------|------|
| AC#21 | HomeQuestsScreen CatalogGrid titleMedium bold + 16dp corners + 12dp gap | BrandComponentsInvariantsTest + visual |
| AC#22 | HomeQuestsScreen excludes archived catalogs | DefaultHomeQuestsComponentTest |
| AC#23 | MyQuestsScreen: CatalogSpinner + LazyColumn + FAB | DefaultMyQuestsComponentTest |
| AC#24 | MyQuestsScreen empty state when no quests | DefaultMyQuestsComponentTest |
| AC#25 | Catalog filter in spinner → list filtered | DefaultMyQuestsComponentTest |
| AC#26 | QuestCard averageRating=2.7 → 2 full + partial 3rd star | QuestToDisplayItemTest |
| AC#27 | QuestCard averageRating=null → 3 outline stars | QuestToDisplayItemTest |
| AC#28 | QuestCard picturePath=null → placeholder icon | QuestToDisplayItemTest |
| AC#29 | FAB click → navigate OpenQuestCreate (UI wire) | DefaultMyQuestsComponentTest |
| AC#30 | QuestCard.onClick → placeholder (TODO) | DefaultMyQuestsComponentTest |
| AC#45 | MyQuestsComponent guest state: empty + isGuest=true, no DB query | DefaultMyQuestsComponentTest |
| AC#46 | Guest on MyQuests: empty state + FAB → UnderConstructionScreen | DefaultMyQuestsComponentTest |
| AC#47 | Archived quest not in list | DefaultMyQuestsComponentTest |

---

## Traceability Coverage (all 8 Problems)

| Problem | Phase(s) | Coverage |
|---------|----------|---------|
| P1: CatalogEntity lacks 4 fields; CatalogRepositoryImpl full-replace | phase-01 (schema + DAO methods), phase-02 (cursor rewrite) | Full |
| P2: Quest/Section/Theme/Lesson/Question data layers absent + Orchestrator absent | phase-02 (5 data modules), phase-03 (orchestrator) | Full |
| P3: MyQuestsScreen absent + ViewModel absent + CatalogGridSection anti-pattern | phase-04 (navigation), phase-05 (screens + components) | Full |
| P4: SyncStateRepository not in Koin; cursor management disconnected | phase-02 (Koin binding), phase-03 (usage in orchestrator) | Full |
| P5: Coil 3.1.0 vs 3.4.0 | phase-01 (libs.versions.toml bump) | Full |
| P6: Quiz module placeholder cleanup | phase-01 (delete 3 modules + 5 files) | Full |
| P7: BrandComponentsInvariantsTest coverage for new components | phase-05 (QuestCard, StarRating with @Preview) | Full |
| P8: Two QuestRepository interfaces (compile conflict) | phase-01 (delete placeholder from catalog/domain) | Full |

**All 8 problems covered across phases.**

---

## Open Questions (carried to implement phase)

| ID | Question | Blocking? | Phase |
|----|----------|-----------|-------|
| OQ-TEST-1 | `decompose-testutils` artifact availability in Decompose 3.1.0 — verify Maven Central; fallback: `DefaultComponentContext(LifecycleRegistry())` without testutils | no (workaround available) | phase-05 backend |
| OQ-L3-1 | AppShellScreen.LocalTabContent exhaustive when — ensure QuestCreateRoot branch added | yes (compile error if missing) | phase-05 frontend |
| OQ-CLOUD-1 | Cloud Function for parent.contentsVersion propagation — without it, client may miss nested changes | no (workaround: full resync) | post-MVP |
| OQ-ORPHAN-1 | Orphan quest cleanup when catalog deleted — show "Unknown catalog" or filter? | no (MVP: filter via catalogId == null branch) | post-MVP |
| OQ-SHELVES-1 | availableShelves hard-coded {"home","arena"} — dynamic via UserStats.qualification future | no (MVP: hardcoded) | post-MVP |

---

## Validation (Full Suite)

```bash
# Phase-01 gate
./gradlew :shared:core:persistence:compileKotlinJvm
./gradlew :shared:core:persistence:connectedAndroidTest
./gradlew :android:core:designsystem:assembleDebug  # Coil 3.4.0 compat check only, not app-build gate
./gradlew assemble

# Phase-02 gate
./gradlew :shared:core:catalog:data:jvmTest
./gradlew :shared:feature:quest:data:jvmTest
./gradlew :shared:feature:section:data:jvmTest
./gradlew :shared:feature:theme:data:jvmTest
./gradlew :shared:feature:lesson:data:jvmTest
./gradlew :shared:feature:question:data:jvmTest

# Phase-03 gate
./gradlew :shared:core:sync:jvmTest

# Phase-04 gate
./gradlew :shared:feature:app-shell:domain:jvmTest
./gradlew :android:feature:app-shell:presentation:test

# Phase-05 gate
./gradlew :android:core:designsystem:test
./gradlew :android:feature:quest:presentation:test
./gradlew :android:feature:app-shell:presentation:test

# Full suite
./gradlew allTests
./gradlew assemble
```
