### Finding #1 — `SyncWorker` Topology Is Not Canonical
- Severity: BLOCKER
- Document + section affected: `docs/features/menu-refactor/06-api-contract.md` §§11, 14, 16
- Issue: the authoritative API contract defines two incompatible `SyncWorker` designs at once: direct repo injection and `List<Syncable>`.
- Evidence: `06-api-contract.md:452-454` — "`private val userStatsRepository: UserStatsRepository, private val catalogRepository: CatalogRepository`"; `06-api-contract.md:611` — "`SyncWorker` ... получает `List<Syncable>` из Koin"; `06-api-contract.md:639` — "`[OPEN]`".
- Expected (per spec/ADR/rule): `03-decisions.md:95-103` accepts ADR-HLA-04 option B only: `SyncWorker` depends on `core:sync` and consumes `List<Syncable>`.
- Action: collapse `06-api-contract.md` to one accepted topology and remove the rejected/open alternative from the canonical contract.

### Finding #2 — Catalog UI Contract Still Uses `Catalog.picturePath` Instead of Accepted `CatalogDisplayItem.pictureUrl`
- Severity: BLOCKER
- Document + section affected: `docs/features/menu-refactor/06-api-contract.md` §13.2 and §16
- Issue: the canonical UI contract still passes domain `Catalog` into `CatalogGrid` and binds `AsyncImage` to `catalog.picturePath`, even though the accepted design moved resolved URLs into `CatalogDisplayItem`.
- Evidence: `06-api-contract.md:575` — "`catalogs: List<Catalog>,`"; `06-api-contract.md:589` — "`AsyncImage(model = catalog.picturePath, ...)`"; `06-api-contract.md:659-663` explicitly says the `pictureUrl` delivery problem is still open.
- Expected (per spec/ADR/rule): `03-decisions.md:275-288` accepts ADR-L3-03: `CatalogGrid`/`CatalogSpinner` take `List<CatalogDisplayItem>` with `pictureUrl`, while domain `Catalog` remains `picturePath`-only.
- Action: replace the section-13 UI signatures with the accepted `CatalogDisplayItem` contract and remove the open debate from the authoritative doc.

### Finding #3 — The Claimed `77` Test Scenarios Are Not Traceable Back to the Sub-Spec Source of Truth
- Severity: HIGH
- Document + section affected: `docs/features/menu-refactor/04-testing.md` §§3, 6
- Issue: the test plan labels the list as “by sub-spec” but totals `77` by silently mixing the `69` resolved sub-spec scenarios with `8` extra repository scenarios.
- Evidence: `04-testing.md:56` — "`## 3. Test Scenarios by Sub-Spec`"; `04-testing.md:211` — "`### 3.5 UserStatsRepositoryImpl — 8 сценариев`"; `04-testing.md:341` — "`**ИТОГО** | **77**`".
- Expected (per spec/ADR/rule): the resolved sub-spec counts are `14 + 25 + 7 + 23 = 69`; any supplemental scenarios must be explicitly separated so the spec total remains traceable.
- Action: split coverage into `spec-traceable scenarios` and `supplemental cross-cutting scenarios`, then show how the extra `8` map to specific ACs/ADRs.

### Finding #4 — Qualification Test Strategy Targets a Nonexistent API
- Severity: HIGH
- Document + section affected: `docs/features/menu-refactor/04-testing.md` §3.1
- Issue: most qualification-level cases are written against `QualificationLevel.fromPoints()`, which is not in the spec or the canonical API contract.
- Evidence: `04-testing.md:68` — "`QL-04 | fromPoints(0) -> null | QualificationLevel.fromPoints(0)`"; similar drift continues through `04-testing.md:69-73,77-78`.
- Expected (per spec/ADR/rule): `0-spec-qualification-levels.md:123-133` and `06-api-contract.md:32` define the required behavior around `isReachedBy(points)`, enum contents, and point ordering, not `fromPoints(...)`.
- Action: rewrite QL-04..QL-14 against the actual `isReachedBy` contract and the 14 spec scenarios.

### Finding #5 — Visibility Matrix Coverage Is Incomplete and One Row Is Not Testable
- Severity: HIGH
- Document + section affected: `docs/features/menu-refactor/04-testing.md` §3.2.3
- Issue: the visibility section does not map the full 7-cell matrix to concrete tests, and one listed scenario is explicitly non-deterministic.
- Evidence: `04-testing.md:124` — "`developer=99 НЕ bypass ... isVisible depends on roles`"; only `DM-17..DM-19` cover explicit `isVisible` outcomes.
- Expected (per spec/ADR/rule): `02-behavior.md:216-220` and `.claude/commands/feature-design.md:116` require each state-matrix cell to have at least one concrete test case.
- Action: add deterministic tests for every visibility-matrix row, including both normal-path true/false cases and both superqualification bypass cases.

### Finding #6 — The Test Execution Package Is Not Implementation-Ready
- Severity: HIGH
- Document + section affected: `docs/features/menu-refactor/04-testing.md` §§4, 5
- Issue: the plan omits the required journey-level integrations and its fake blueprints do not match the canonical APIs, so a test-dev agent cannot implement the suite from this doc alone.
- Evidence: `04-testing.md:303-323` lists only `AppDatabaseMigrationTest` and `UserStatsDaoTest`; `04-testing.md:274-281` defines `FakeCatalogFirebaseDataSource.fetchAll(): List<CatalogEntity>`, while `06-api-contract.md:396-397` requires `CatalogRemoteDataSource.fetchAll(): List<CatalogDto>`.
- Expected (per spec/ADR/rule): `.claude/rules/testing.md:114` requires at least one full resolved-value test for composed resources, and `feature-design.md:116` requires explicit primary-journey coverage; the fakes must also match the canonical signatures from `06-api-contract.md`.
- Action: add journey-to-integration mappings for dev-mode activation/deactivation, SyncNow, first-launch catalog pull, warm cache, offline launch, and DesignCatalog render-path checks; then rewrite the fake blueprints to the actual API, including a `storageUrlResolver` test double.

### Finding #7 — Storage Model Omits the Accepted `pictureUrl` Cache
- Severity: HIGH
- Document + section affected: `docs/features/menu-refactor/08-storage-model.md` §§4, 7.2, 9
- Issue: `CatalogEntity` stores only `picturePath`, but the accepted design depends on caching a resolved HTTPS URL to avoid Coil issue #2551 and feed the UI contract.
- Evidence: `08-storage-model.md:107` — "`val picturePath: String?,   // relative Firebase Storage path or null`".
- Expected (per spec/ADR/rule): `03-decisions.md:176` and `03-decisions.md:292` state that `CatalogRepositoryImpl.refreshFromRemote()` persists `CatalogEntity.pictureUrl: String?` and that Room caches the resolved URL.
- Action: add the canonical persisted URL field and mapper flow, or explicitly reverse ADR-HLA-07/L3-03 across all design docs; the current state is inconsistent.

### Finding #8 — `UserStatsEntity` No Longer Matches the Resolved Spec Field Contract
- Severity: HIGH
- Document + section affected: `docs/features/menu-refactor/08-storage-model.md` §2
- Issue: the canonical Room entity renames and splits several resolved spec fields without documenting a reconciliation, so the storage schema drifts from the agreed contract.
- Evidence: `08-storage-model.md:48-55` — "`hasPremium`, `stars`, `nolics`, `standardHearts`, `goldHearts`, `gold`, `currentSkill`".
- Expected (per spec/ADR/rule): `0-spec-dev-mode.md:44-59` resolves the local storage contract as `currentSkill`, `premium`, `heartsBalance`, `starsBalance`, `nolicsBalance`, `goldBalance`, plus the six qualification fields.
- Action: either align the entity names/shape to the resolved spec contract or add an explicit field-mapping table that explains the split/rename and proves no data is lost.

### Finding #9 — Prior-Art Still Recommends Rejected Patterns
- Severity: MEDIUM
- Document + section affected: `docs/features/menu-refactor/05-prior-art.md` §§1, 4, 6
- Issue: the prior-art doc still recommends patterns that the ADRs explicitly rejected, so it is not a safe reference source for implementation.
- Evidence: `05-prior-art.md:125` — "`stores HTTPS URL in Catalog.imageUrl: String`"; `05-prior-art.md:615-617` — "`data class ShowSnackbar(val message: String) : RootEvent`".
- Expected (per spec/ADR/rule): `03-decisions.md:181-183` keeps `pictureUrl` out of domain, and `07-events.md:31-43` fixes the event hierarchy as `DevModeActivated`, `DevModeAlreadyActive`, `SyncStarted`, `SystemBack`.
- Action: rewrite these sections as rejected alternatives or replace them with the adopted patterns so downstream readers do not implement stale recommendations.

- Total findings: 9 (2 blocker / 6 high / 1 medium)
- Verdict: REJECT

Cross-document alignment is strongest in `07-events.md`: I did not find missing `RootEvent` variants relative to `02-behavior.md`, and its event flow is materially coherent. The major drift is concentrated in `04-testing.md`, `06-api-contract.md`, and `08-storage-model.md`, with `05-prior-art.md` still carrying stale guidance that points back toward already-rejected solutions.