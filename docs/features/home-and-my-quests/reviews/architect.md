## Findings
1. `BLOCKER` Target: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:65), [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/0-spec.md:1094).  
Evidence: the spec’s AC #4 still says “all 50 Domain Test Scenarios” while `04-testing.md` claims “All 58 Domain Test Scenarios”; the same coverage table then mixes non-domain AC items into the scenario map at [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:84). That makes the requested “58-scenario” coverage unverifiable from the current SSoT.  
Suggested fix: freeze one canonical scenario list in `0-spec.md`, mark deleted items as `N/A`, and keep AC traceability out of the domain-scenario map.

2. `HIGH` Target: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:454).  
Evidence: several ACs are not mapped to concrete test cases at all: AC 1-3 and 5-6 are only “Compile check” / “Exists in Walking Skeleton” [04:458-463], AC 18 is manual smoke only [04:475], AC 35-40 are manual/optional [04:480,499], and AC 50-53 have no test file [04:483].  
Suggested fix: either add named automated tests for those ACs or split them into a separate “design assertions / manual checks” section instead of claiming test coverage.

3. `HIGH` Target: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:25), [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/02-behavior.md:278).  
Evidence: the matrix-to-test map is incomplete against the extended matrices. Missing explicit traceability includes Matrix 1 edge 1.10 (`pictureUrl` resolution + `?v=`) [02:295], Matrix 2 row 2.1 and edge 2.8 [02:303,310], Matrix 3 rows 3.5/3.6 [02:324-325], and all Matrix 4 rows 4.1-4.6 [02:331-340]. `04-testing.md` only maps Matrix 1-3 partially [04:27-61] and omits Matrix 4 entirely.  
Suggested fix: expand `04-testing.md §2` to cover every extended row, and explicitly mark future-only Matrix 4 rows as `N/A` if they are out of phase-01 scope.

4. `HIGH` Target: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:205), [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/0-spec.md:642).  
Evidence: the spec defines 11 primary journeys, but `04-testing.md` has no journey-to-integration-test map. The only explicit journey reference is `// Journey 7` inside a component test, not an integration test, at [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:406).  
Suggested fix: add an explicit 11-row journey coverage table with one named integration test per journey, or clearly state which journeys are intentionally covered only at component level.

5. `HIGH` Target: [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:10), [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/01-architecture.md:265), [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/02-behavior.md:29).  
Evidence: `06-api-contract.md` says other docs must reference it and “do not duplicate signatures” [06:10-12], but grep found zero `06-api-contract.md` references in `01/02/03/04`. Those docs still restate signatures directly, and drift already exists: `01-architecture.md` shows `QuestRepositoryImpl.refreshFromRemote(...) Result<Unit>` [01:351-352] while canonical `06` defines `Result<Set<QuestId>>` [06:181-186]; `02-behavior.md` calls `CatalogRepository.refreshFromRemote(cursor=...)` [02:29-30] although canonical `06` keeps it parameterless [06:157-158].  
Suggested fix: remove duplicated signatures from `01/02/03` and replace them with `see 06-api-contract.md:NN`; keep only behavior/pseudocode outside `06`.

6. `HIGH` Target: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:95), [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:181).  
Evidence: the fake blueprints do not match the canonical interfaces. `FakeQuestRepository.refreshFromRemote` returns `Result<Unit>` and requires non-null `currentUserUid: String` [04:121-127], but canonical `QuestRepository` requires `currentUserUid: String?` and `Result<Set<QuestId>>` [06:181-186]. `FakeSectionRepository.refreshByParents` also returns `Result<Unit>` [04:152-156] while canonical `SectionRepository` requires `Result<Set<SectionId>>` [06:202-205].  
Suggested fix: regenerate fake blueprints from `06-api-contract.md`, including nullable auth and changed-id return sets for cascade triggering.

7. `MEDIUM` Target: [08-storage-model.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/08-storage-model.md:9), [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:153).  
Evidence: `08-storage-model.md` declares itself the canonical source for Room entity/DAO signatures and says `06` should refer to it [08:9], but grep found zero `08-storage-model.md` references in `06`. `06` still embeds DAO behavior inline via comments like `DAO: WHERE archived = 0 ORDER BY id ASC` [06:153] and similar Room-specific filters [06:167,195,219].  
Suggested fix: strip Room/DAO details out of `06` and replace them with line refs into `08`.

8. `MEDIUM` Target: [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:438), [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/01-architecture.md:469), [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:438).  
Evidence: the canonical UI model already drifts. `06` defines `QuestDisplayItem` without `averageRatingCount` [06:438-443], but `01` includes it [01:469-475], and the test/previews in `04` instantiate it with that field [04:438-441].  
Suggested fix: pick one canonical `QuestDisplayItem` shape in `06` and align `01`, `04`, and mapper code to it.

9. `MEDIUM` Target: [08-storage-model.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/08-storage-model.md:55), [08-storage-model.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/08-storage-model.md:499).  
Evidence: `08` says cursor sync relies on a `catalogs.lastModifiedAt` index in the schema summary [08:503], but `CatalogEntity` itself declares no indices [08:59-69]. That is an internal storage-model inconsistency.  
Suggested fix: either add the missing `Index(value = ["lastModifiedAt"])` to `CatalogEntity` or remove the claim from the summary.

10. `MEDIUM` Target: [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:576).  
Evidence: all 7 Firestore indexes are listed, but the section does not document full query patterns for every row; rows 3-7 only say “cascade step”, and the section title says “Composite Indexes” even though `catalogs.lastModifiedAt` is single-field auto [06:586].  
Suggested fix: add the exact Firestore query shape next to each index and separate manual composite indexes from automatic single-field indexes.

11. `LOW` Target: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:330), [08-storage-model.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/08-storage-model.md:42).  
Evidence: the “migration” test only creates a v2 schema and checks tables/columns [04:342-354]; it does not validate a v1→v2 path or destructive recreation. `08` does contain the destructive-migration rationale [08:42-51,520-527], so this is a naming/traceability weakness rather than a hard contradiction.  
Suggested fix: either rename the test to schema validation or add an explicit destructive-recreate verification.

## Checks That Passed
- Domain purity is preserved at the repository/use-case signature level in [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:146); I did not find Android/Room/Firebase/DI types in those signatures.
- The designed presentation path is compliant with invariant 2: [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/02-behavior.md:66) routes screens through `HomeQuestsComponent` / `MyQuestsComponent`, which is the Decompose-equivalent of “UI calls only ViewModel”.
- One-way feature coupling is documented consistently in [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/01-architecture.md:194) and corroborated by [1-research.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/1-research.md:247).
- KMP/Room `kspJvm` compatibility and the canonical six-collection security-rules block are both documented in [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:721) and [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:647).

## Verdict: REJECT
Must revise:
- [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/04-testing.md:25): sections `2`, `3`, `5`, and `10` for full matrix/journey/AC/scenario traceability.
- [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:10): SSoT discipline, `§7 UI Models`, and `§10 Indexes`.
- [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/01-architecture.md:254) and [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/02-behavior.md:9): remove duplicated signatures and replace with references to `06`.
- [08-storage-model.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/08-storage-model.md:55): fix the catalog index inconsistency and enforce actual `06`↔`08` linkage.
- Any retained plan/decision docs that restate canonical signatures, especially [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/03-decisions.md:28), should be normalized to references rather than duplicate code blocks.