# Cross-Phase Review — Synthesis

Date: 2026-04-24
Reviewers: Codex Skeptic + Architect + Minimalist + Realist (cross-model adversarial)
Base: complete diff across phase-01..phase-05

## Consensus BLOCKERs (multiple reviewers flagged)

| # | Finding | Reviewers | Spec ref | Risk |
|---|---------|-----------|----------|------|
| B1 | **firestore.rules missing для 5 new collections** (quests/sections/themes/lessons/questions) | Skeptic + Realist P0 | 06-api-contract.md §16, AC#35-40/51-53 | Production deploy blocker — Firestore rejects client reads |
| B2 | **Cursor advance BEFORE downstream cascade success** (CascadingSyncOrchestrator:119,140,161,182,199) — partial failure loses pending child sync | Skeptic + Architect | 0-spec.md:765 partial-cascade recovery | Real runtime bug on retry after mid-cascade failure |
| B3 | **Cursor strategy Clock.now() vs spec max(dto.lastModifiedAt)** | Skeptic + Architect + Realist | 0-spec.md:86 vs 03-decisions.md:804 ADR amendment | SSoT split — spec and code disagree |
| B4 | **Repository returns ALL processed IDs without cv>local filter** (Section/Theme/Lesson/Question + Quest) | Skeptic + Architect | 0-spec.md:889,906 ADR-CMP-49 | Cascade triggers on stale parents — wasted Firestore quota + late recursion |
| B5 | **Archived catalog does not trigger child cleanup cascade** | Skeptic | 0-spec.md orphan cleanup | Quests remain visible after catalog archive until own archive |

## HIGH findings

| # | Finding | Reviewers | File:line |
|---|---------|-----------|-----------|
| H1 | **visibleOn=[] removal unobservable via Query B** (array-contains-any requires non-empty) | Skeptic | FirebaseQuestRemoteDataSource:36, QuestRepositoryImpl:66 |
| H2 | **Sign-out does not emit guest UserStats** (emptyFlow at uid=null, AppShell stale privileges) | Skeptic | UserStatsRepositoryImpl:25-27 |
| H3 | **Storage URL resolver failure cached as null + cursor advances** (atomic violation) | Skeptic | CatalogRepositoryImpl:36, QuestRepositoryImpl:61 |
| H4 | **DefaultRootComponent → concrete SyncWorker import** (presentation layer violation) | Architect | DefaultRootComponent:18,231 |
| H5 | **Koin factory signature mismatch §13 SSoT vs real** (QuestPresentationModule requires (ctx, nav), SSoT says (ctx)) | Architect + Realist P1 | QuestPresentationModule:25, 06-api-contract.md:801 |
| H6 | **getKoin() в Composable (service locator anti-pattern)** — phase-05 debt | Architect + Realist P1 | AppShellScreen:332,349 |
| H7 | **Section/Theme/Lesson/Question archived delete без version guard** (stale tombstone может удалить newer) | Realist P1 | SectionRepositoryImpl:32 и аналоги |
| H8 | **AC#26 StarRating visual partial fill — rendering shows full filled star** (helper predicate correct, but rendering incorrect) | Realist P1 | StarRating:54 |

## MEDIUM findings

| # | Finding | Reviewers |
|---|---------|-----------|
| M1 | AC coverage traceability incomplete: AC#1-6, 12, 16, 18, 23-24, 30, 32-40, 42-44, 51-53 missing test markers | Realist P0 |
| M2 | KoinModuleWiringTest не проверяет realreg graph (quest/data/firebase modules) | Architect |
| M3 | ADR-CMP-50 cache-busting `?v={version}` не appended к URL | Realist P2 |
| M4 | ADR-HMQ-02 bootstrap `.limit(1000)` missing | Realist P2 |
| M5 | 3 FakeCatalogRepository (domain/sync/presentation) consolidation | Minimalist |
| M6 | 4 mechanical repository stacks (Section/Theme/Lesson/Question) — template/contract pattern unused | Minimalist |
| M7 | UserStatsRepository passed to CascadingSyncOrchestrator but @Suppress("unused") — dead dep | Minimalist |
| M8 | Badge API future-proofing with no caller | Minimalist |
| M9 | Unused `:shared:core:sync` dep в 5 data modules | Minimalist |

## Open Questions (require user decision)

1. Should public quest sync work for guests? ADR says null UID runs Query B, but canonical security requires `request.auth != null`.
2. Orphan cleanup deferred? Then MyQuests needs catalog-existence filter.
3. Subtree-atomic sync или SyncStateRepository pending cascade recovery before ship?

## Smoke Test Status

- `./gradlew allTests` — BUILD SUCCESSFUL (1132 tasks, 0 failures)
- `./gradlew assemble` — BUILD SUCCESSFUL (3940 tasks, debug + release APK)
- No runtime crashes in unit tests; production-level integration (real Firebase) not tested.
