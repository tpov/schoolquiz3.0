## Findings

**Prev #3 — Designed Contracts Do Not Match Real Interfaces**  
Severity: HIGH  
Status: PARTIAL  
Evidence: [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:10) is now the declared SSoT, and other docs do reference it, but its canonical `QuestRepository.refreshFromRemote(...): Result<Set<QuestId>>` with nullable UID at [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:181) still disagrees with real code’s non-null UID and `Result<Unit>` at [QuestRepository.kt](/home/Programming/Android/schoolquiz4.0/shared/feature/quest/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/quest/domain/repository/QuestRepository.kt:114). It also models `CatalogRepository : Syncable` at [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:152), while real [CatalogRepository.kt](/home/Programming/Android/schoolquiz4.0/shared/core/catalog/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/domain/repository/CatalogRepository.kt:27) does not extend `Syncable`.  
Impact: The canonical file exists, but it is still not aligned with the live interfaces, so it remains unsafe as the implementation source of truth.

**Prev #6 — Home/MyQuests Behavior Does Not Match The Actual Decompose Tree**  
Severity: MEDIUM  
Status: PARTIAL  
Evidence: The planned tree is now explicit at [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/0-spec.md:274) and [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/02-behavior.md:222), but [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/01-architecture.md:627) still leaves the `LocalScreenComponent` subtype change open, while real code is still placeholder-only in [LocalScreenComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/screen/LocalScreenComponent.kt:5), [LocalTabComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/tab/LocalTabComponent.kt:19), and [AppShellScreen.kt](/home/Programming/Android/schoolquiz4.0/android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:298).  
Impact: The docs now acknowledge the tree change, but they still do not present one settled component model that cleanly maps onto the real shell.

**Prev #7 — StorageUrlResolver In The Docs Is Broader Than The Real Wiring**  
Severity: MEDIUM  
Status: PARTIAL  
Evidence: Current catalog-only wiring is now documented in [1-research.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/1-research.md:223) and matches code in [CatalogRepositoryImpl.kt](/home/Programming/Android/schoolquiz4.0/shared/core/catalog/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogRepositoryImpl.kt:29) and [FirebaseCatalogModule.kt](/home/Programming/Android/schoolquiz4.0/platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/di/FirebaseCatalogModule.kt:16). But the future-design wording still says the resolver has no prefix constraints at [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/01-architecture.md:629) and reuses it for quest pictures at [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/0-spec.md:49) without clearly separating that from today’s wiring.  
Impact: The limitation is clearer than before, but a reader can still overread the current resolver as already generic.

**NEW — `MyQuestsViewModel` Is Still Treated As The Primary UI Seam**  
Severity: MEDIUM  
Status: NEW_ISSUE  
Evidence: The spec still centers behavior and ACs on `MyQuestsViewModel` at [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/0-spec.md:176), [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/0-spec.md:1153), and [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/0-spec.md:1182), while the revised design switched to Decompose components at [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/01-architecture.md:252) and [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/02-behavior.md:249). The real app shell is also component-driven in [DefaultRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:67).  
Impact: The presentation model is internally split between ViewModel and Decompose terminology, which makes the design harder to implement and test consistently.

**Prev #8 — Research/Grounding Navigation Sections Are Stale**  
Severity: LOW  
Status: PARTIAL  
Evidence: Research later marks the issue closed at [1-research.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/1-research.md:383), but the earlier section still says `QuestCreateRoot` is missing at [1-research.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/1-research.md:198). Grounding is also stale at [2-grounding.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/2-grounding.md:21), while code now has [TabConfig.kt](/home/Programming/Android/schoolquiz4.0/shared/feature/app-shell/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/app_shell/domain/model/TabConfig.kt:22), [Destination.kt](/home/Programming/Android/schoolquiz4.0/shared/feature/app-shell/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/app_shell/domain/model/Destination.kt:36), and [AppShellTransitions.kt](/home/Programming/Android/schoolquiz4.0/shared/feature/app-shell/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/app_shell/domain/logic/AppShellTransitions.kt:342).  
Impact: Current-state guidance is still self-contradictory depending on which section the implementer reads first.

**NEW — Retry/Backoff Semantics Are Still Internally Inconsistent**  
Severity: LOW  
Status: NEW_ISSUE  
Evidence: [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/0-spec.md:112) promises `1s → 2s → 4s`, [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/02-behavior.md:162) says `30s → 5min cap`, and the real work requests still set no explicit backoff in [AppApplication.kt](/home/Programming/Android/schoolquiz4.0/apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:82) or [DefaultRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:231).  
Impact: Retry behavior is not a stable contract yet, so the sync acceptance criteria are still underspecified.

**Prev #1 — Missing Gradle And Koin Registration**  
Severity: LOW  
Status: RESOLVED  
Evidence: [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/01-architecture.md:9) now explicitly declares `Status: TO-BE` and points readers to [2-grounding.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/2-grounding.md:12) for AS-IS. The live code still only wires current modules in [settings.gradle.kts](/home/Programming/Android/schoolquiz4.0/settings.gradle.kts:39) and [AppApplication.kt](/home/Programming/Android/schoolquiz4.0/apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:68), but the docs no longer present future registration as already real.  
Impact: This is now clearly planned-state, not a false statement about the current build graph.

**Prev #2 — Room Schema Is Still Pre-Phase-01**  
Severity: LOW  
Status: RESOLVED  
Evidence: The target schema is now clearly future-facing via [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/01-architecture.md:9), while current-state grounding still records the old schema at [2-grounding.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/2-grounding.md:16), matching [AppDatabase.kt](/home/Programming/Android/schoolquiz4.0/shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt:6), [CatalogEntity.kt](/home/Programming/Android/schoolquiz4.0/shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/CatalogEntity.kt:6), and [PersistenceModule.kt](/home/Programming/Android/schoolquiz4.0/shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/di/PersistenceModule.kt:10).  
Impact: The schema gap remains, but it is now modeled as planned implementation work instead of mistaken current-state documentation.

**Prev #4 — Catalog State Matrix Assumes Data The Repository Never Persists**  
Severity: LOW  
Status: RESOLVED  
Evidence: The desired delta-sync behavior remains in the future spec, and current limitations are now explicitly grounded in [1-research.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/1-research.md:136) and [2-grounding.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/2-grounding.md:37), matching [CatalogRemoteDataSource.kt](/home/Programming/Android/schoolquiz4.0/shared/core/catalog/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogRemoteDataSource.kt:3), [CatalogDto.kt](/home/Programming/Android/schoolquiz4.0/shared/core/catalog/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogDto.kt:3), [CatalogRepositoryImpl.kt](/home/Programming/Android/schoolquiz4.0/shared/core/catalog/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogRepositoryImpl.kt:24), and [CatalogDao.kt](/home/Programming/Android/schoolquiz4.0/shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/CatalogDao.kt:13).  
Impact: The docs now distinguish the future matrix from the current repository behavior.

**Prev #5 — SyncWorker Still Implements A Flat List**  
Severity: LOW  
Status: RESOLVED  
Evidence: Future cascade behavior is now clearly documented as planned in [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/01-architecture.md:9) and [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/02-behavior.md:11), while current-state research still captures the flat-list worker at [1-research.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/1-research.md:23), matching [SyncWorker.kt](/home/Programming/Android/schoolquiz4.0/platform/android-services/src/main/kotlin/com/tpov/schoolquiz/platform/android_services/sync/SyncWorker.kt:15) and [SyncModule.kt](/home/Programming/Android/schoolquiz4.0/apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt:14).  
Impact: The worker gap is still implementation work, but it is no longer described as if it already exists.

## Verdict: REJECT

The docs are closer than the previous pass, but the design model still does not fully match the real codebase. The main blocker is that the new canonical API contract still disagrees with live interfaces, and the presentation/navigation story is still split across Decompose-based docs, stale research/grounding, and lingering `MyQuestsViewModel` assumptions.