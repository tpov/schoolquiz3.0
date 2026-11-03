# SKEPTIC REVIEW — home-and-my-quests cross-phase

## Blocker findings (real bugs, not style)

- [blocker] `firestore.rules:16` — new cascade collections are not readable at all.
  Evidence: rules only define `/users` and `/catalogs`; spec requires `/quests`, `/sections`, `/themes`, `/lessons`, `/questions` rules in `docs/features/home-and-my-quests/06-api-contract.md:669`.
  Fix: add the canonical rules block for all five new collections and align it with guest/public-read behavior.

- [blocker] `shared/core/sync/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/sync/CascadingSyncOrchestrator.kt:119` — partial cascade failure loses pending child sync.
  Evidence: quest cursor is advanced before `cascadeLevel(SyncLevel.Section, ...)` at line 124; the TODO at lines 120-122 admits pending children are lost. Catalog cursor is also advanced inside `CatalogRepositoryImpl.kt:49`.
  Fix: either make cursor advancement all-or-nothing for the subtree, or use `markCascadeInProgress` before advancing and resume pending parent IDs on retry.

- [blocker] `shared/core/sync/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/sync/CascadingSyncOrchestrator.kt:68` — cursor uses client `Clock.System.now()` instead of returned server `lastModifiedAt`.
  Evidence: spec requires `newCursor = max(oldCursor, max(dto.lastModifiedAt))` in `docs/features/home-and-my-quests/0-spec.md:86`; code advances to `freshTime` at lines 119, 140, 161, 182, 199.
  Fix: return `maxLastModifiedAt` from each repository step and set cursor to that value only when non-empty and successful.

- [blocker] `shared/core/catalog/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogRepositoryImpl.kt:41` — archived catalog deletes the catalog but does not trigger child cleanup.
  Evidence: archived branch deletes, but only non-archived branch adds `changedIds` at line 46; orchestrator exits on empty IDs at `CascadingSyncOrchestrator.kt:100`. `QuestDao.kt:13` observes MyQuests by `authorUid` and `archived=0`, with no catalog join.
  Fix: include archived catalog IDs in a cleanup cascade, or explicitly delete local descendants by `catalogId` / add FK cascade.

## High findings

- [high] `platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/quest/FirebaseQuestRemoteDataSource.kt:36` — public quest removal via `visibleOn=[]` is unobservable for non-owners.
  Evidence: Query B requires `whereArrayContainsAny("visibleOn", shelves)`, but delete handling for `visibleOn.isEmpty()` is in `QuestRepositoryImpl.kt:66` and only runs if the DTO is returned.
  Fix: introduce readable tombstones/removal events, or another query path that can fetch visibility removals.

- [high] `shared/feature/app-shell/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/app_shell/data/UserStatsRepositoryImpl.kt:25` — sign-out does not emit guest stats.
  Evidence: `uid == null` maps to `emptyFlow()` at line 27; `ObserveAppShellStateUseCase.kt:31` updates only on emissions. A logged-in user can sign out while AppShell keeps stale privileges.
  Fix: emit `UserStats.guest()` for null UID and add a root/component test for authenticated → sign-out.

- [high] `shared/core/catalog/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogRepositoryImpl.kt:36` — transient Storage URL failure is cached as `null` and cursor still advances.
  Evidence: resolver failure becomes `getOrNull()`; cursor advances at lines 49-52. Same pattern exists for quests in `QuestRepositoryImpl.kt:61`.
  Fix: treat resolver failure as retryable, or persist unresolved paths and retry URL resolution separately without advancing content cursor.

## Medium findings

- [medium] `shared/core/catalog/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogRepositoryImpl.kt:44` — cascade trigger ignores `contentsVersion > local.contentsVersion`.
  Evidence: every non-archived catalog DTO is returned as changed at line 46; quest IDs are added before local/version/content checks in `QuestRepositoryImpl.kt:55`.
  Fix: return only IDs whose local `contentsVersion` actually grew, plus first-time inserts that need bootstrap.

## Open Questions for lead

- Should public quest sync work for guests? ADR says null UID still runs Query B, but the canonical security block requires `request.auth != null`.
- Is orphan cleanup truly deferred? If yes, MyQuests needs a catalog-existence filter now, otherwise archived catalog quests stay visible.
- Do we want MVP sync to be subtree-atomic, or should `SyncStateRepository` pending cascade recovery be implemented before ship?