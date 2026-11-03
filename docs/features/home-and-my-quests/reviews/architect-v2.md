## Previous Findings

1. AC#4 `50 vs 58 scenarios`
Severity: `BLOCKER`  
Status: `PARTIAL`  
Target: [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/03-decisions.md:618) `ADR-HMQ-10`, [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/0-spec.md:1091) `Acceptance Criteria`, [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:78) `§3`  
Evidence: ADR-HMQ-10 was added and accepts `58`, but [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/0-spec.md:1094) still says AC#4 = `50`; [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/0-spec.md:1175) still marks item 58 as integration-level, while [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:80) still says “58 Domain Test Scenarios” and mixes AC rows at [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:97).  
Impact: scenario coverage is still not backed by one clean canonical list, so domain vs integration traceability remains unverifiable.

2. Unmapped ACs `18, 35-40, 50-53`
Severity: `MEDIUM`  
Status: `RESOLVED`  
Target: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:515) `§10`, [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:550) `§11`  
Evidence: AC#18 is now explicitly manual at [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:536); AC#35-40 and AC#50-53 are explicitly marked manual / optional emulator at [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:541), [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:544), [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:560).  
Impact: those ACs are now traceable instead of silently unmapped.

3. State Matrix coverage in `04-testing §2`
Severity: `HIGH`  
Status: `PARTIAL`  
Target: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:25) `§2`, [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/02-behavior.md:281) `Matrix 1 Extended`, [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/02-behavior.md:300) `Matrix 2 Extended`, [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/02-behavior.md:332) `Matrix 4 Extended`  
Evidence: Matrix 4 is now present in [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:65) and rows 3.5/3.6 are mapped at [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:60), but `04 §2` still omits Matrix 1 edges 1.8/1.10 from [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/02-behavior.md:294) and [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/02-behavior.md:296), plus Matrix 2 row 2.1 / edge 2.8 from [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/02-behavior.md:304) and [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/02-behavior.md:311).  
Impact: the matrix map is improved, but still not complete enough to support a full “every cell covered” claim.

4. Journey Coverage table
Severity: `LOW`  
Status: `RESOLVED`  
Target: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:462) `§9`  
Evidence: `04-testing.md` now has an 11-row journey map with explicit test-file ownership for each journey and marks Journey 11 as manual-only at [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:478).  
Impact: primary journey traceability is restored.

5. SSoT duplication `01 -> 06`, `02 -> 06`
Severity: `HIGH`  
Status: `PARTIAL`  
Target: [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/01-architecture.md:339) `Quest Data Stack`, [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/02-behavior.md:9) `DFD 1`, [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:541) `§9`  
Evidence: both docs now reference `06`, but duplicate contract-like signatures remain. In particular, [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/01-architecture.md:375) still declares remote methods as `fetchMyQuestsInCatalogs` / `fetchPublicQuestsForShelves`, while canonical `06` defines `fetchOwnChanged` / `fetchPublicChanged` at [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:553).  
Impact: SSoT discipline improved, but drift is still happening inside duplicate diagrams.

6. Fake blueprints match canonical contracts
Severity: `LOW`  
Status: `RESOLVED`  
Target: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:106) `§4`, [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:162) `§2.2-2.4`  
Evidence: `FakeQuestRepository.refreshFromRemote` now uses `String?` UID and returns `Result<Set<QuestId>>` at [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:138); `FakeSectionRepository.refreshByParents` now returns `Result<Set<SectionId>>` at [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:176).  
Impact: the fake contracts now line up with the canonical repository interfaces.

7. `06 -> 08` DAO linkage
Severity: `LOW`  
Status: `RESOLVED`  
Target: [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:146) `§2`, [08-storage-model.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/08-storage-model.md:96) DAO sections  
Evidence: `06` now points repository methods to `08-storage-model.md` sections at [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:153), [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:167), [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:195), [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:219) instead of embedding old Room-query comments.  
Impact: Room-specific SSoT is back where it belongs.

8. `QuestDisplayItem` shape alignment across `01/04/06`
Severity: `LOW`  
Status: `RESOLVED`  
Target: [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:434) `§7`, [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/01-architecture.md:475) `Presentation Components`, [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:494) `§10`  
Evidence: all three docs now use the 5-field shape including `averageRatingCount`.  
Impact: the UI-model shape itself is now aligned across the design docs.

9. `CatalogEntity.lastModifiedAt` index in `08`
Severity: `LOW`  
Status: `RESOLVED`  
Target: [08-storage-model.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/08-storage-model.md:55) `CatalogEntity`, [08-storage-model.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/08-storage-model.md:502) `Schema indexes summary`  
Evidence: `CatalogEntity` now declares `Index(value = ["lastModifiedAt"])` at [08-storage-model.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/08-storage-model.md:59).  
Impact: the storage-model inconsistency is closed.

10. Firestore query shapes for all 7 indexes
Severity: `LOW`  
Status: `RESOLVED`  
Target: [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:575) `§10`  
Evidence: `06` now documents 6 manual composite index query shapes plus the `catalogs.lastModifiedAt` single-field auto index at [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:579) and [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:592).  
Impact: Firestore index/admin setup is now explicit and auditable.

11. Migration test rename or `v1 -> v2` path
Severity: `LOW`  
Status: `RESOLVED`  
Target: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:359) `§7`  
Evidence: `04-testing.md` now reframes the test as schema validation and adds `destructive_recreate_when_version_bumped()` for the `v1 -> v2` destructive path at [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:387).  
Impact: the original migration-traceability gap is materially closed.

## New Issues

1. Severity: `MEDIUM`  
Status: `NEW_ISSUE`  
Target: [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:451) `§7 Extension functions`  
Evidence: canonical `QuestDisplayItem` includes `averageRatingCount`, but `Quest.toDisplayItem()` maps only `id`, `title`, `pictureUrl`, and `averageRating` at [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:455).  
Impact: the canonical mapper drops a canonical field, so the UI contract is internally inconsistent.

2. Severity: `LOW`  
Status: `NEW_ISSUE`  
Target: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:359) `§7`, [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:515) `§10`, [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:564) `§12`  
Evidence: section 7 names `AppDatabaseSchemaValidationTest.kt`, but the AC map and per-module structure still reference `AppDatabaseMigrationTest.kt` at [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:530) and [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:589).  
Impact: small but real testing SSoT drift remains.

## Verdict: REJECT

Most of the contract/storage fixes landed, and several prior findings are genuinely closed. The remaining blockers are still coverage/SSoT problems: scenario counting is not canonically settled, the matrix map is still incomplete, and duplicate contract diagrams are still drifting away from `06`.