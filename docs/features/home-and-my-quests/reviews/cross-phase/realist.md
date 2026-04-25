**Findings**

- **P0 — AC coverage traceability is not complete.** Explicit test markers are missing for `AC#1-6, 12, 16, 18, 23, 24, 30, 32-40, 42-44, 51-53` from [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/0-spec.md:1087). A few markers are also misleading: `AC#50` is attached to a batch-size orchestrator test, while the spec’s AC#50 is authenticated nested Firestore reads; `AC#17` is attached to orchestrator failure, while the spec asks for `SyncWorker` retry/backoff behavior.

- **P0 — Firestore security ACs are documented but not implemented.** Spec AC#35-40 and AC#50-53 require quest/nested read and admin write rules, but [firestore.rules](/home/Programming/Android/schoolquiz4.0/firestore.rules:16) only exposes `/catalogs/{catalogId}` plus user rules. There are no `quests`, `sections`, `themes`, `lessons`, or `questions` rule blocks, and I found no matching rules tests.

- **P0 — The provided “Full diff” is not actually full.** [/tmp/hmq-cross-files.txt](/tmp/hmq-cross-files.txt:1) omits many new implementation/test files because they are untracked, including quest presentation, persistence entities/DAOs, sync orchestrator, Firebase remotes, and repository tests. That means Plan “Modified Files” cannot be validated from the supplied diff alone.

- **P1 — Koin §13 is not equal to real `startKoin`.** The SSoT module list in [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/06-api-contract.md:763) differs from [AppApplication.kt](/home/Programming/Android/schoolquiz4.0/apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:84): actual code includes extra `firebaseModule`, `appShellPresentationModule`, `catalogDomainModule`, uses a different order, and wires `questPresentationModule` differently. The contract says the My Quests factory takes `ComponentContext`; actual [QuestPresentationModule.kt](/home/Programming/Android/schoolquiz4.0/android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/di/QuestPresentationModule.kt:25) requires `ComponentContext, Navigator`.

- **P1 — UI still service-locates from Composable despite phase invariants.** Phase 05 moves toward component-driven UI, but [AppShellScreen.kt](/home/Programming/Android/schoolquiz4.0/android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:331) calls `getKoin()` inside composition to create child components. The code comment itself marks this as TODO debt.

- **P1 — Child repository archived behavior contradicts the walking-skeleton matrix.** The spec matrix says stale/equal archived DTOs should be skipped, but [SectionRepositoryImpl.kt](/home/Programming/Android/schoolquiz4.0/shared/feature/section/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/section/data/SectionRepositoryImpl.kt:32) deletes archived items without checking local version. Same pattern exists for theme, lesson, and question repositories. Catalog/quest repos do have the version guard.

- **P1 — Cursor behavior has split SSoT.** Canonical spec says cursor advances to max returned `lastModifiedAt`, but [CascadingSyncOrchestrator.kt](/home/Programming/Android/schoolquiz4.0/shared/core/sync/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/sync/CascadingSyncOrchestrator.kt:67) advances cursors to `Clock.System.now()`. [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/03-decisions.md:804) later blesses that implementation, so either the spec/API contract need updating or the code is wrong. Right now the design model is not singular.

- **P1 — AC#26 is not visually implemented.** Spec requires a 70% partially filled third star for `averageRating=2.7`; [StarRating.kt](/home/Programming/Android/schoolquiz4.0/android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/design/StarRating.kt:54) renders the partial star as a full filled star. Existing test only checks the helper predicate, not rendered partial fill.

- **P1 — AC#30 navigation is a no-op.** Spec says tapping `QuestCard` navigates to placeholder TODO navigation, but [MyQuestsScreen.kt](/home/Programming/Android/schoolquiz4.0/android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/ui/MyQuestsScreen.kt:83) passes `onClick = { /* TODO: open quest detail */ }`.

- **P2 — ADR-CMP-50 cache-busting is missing.** [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/03-decisions.md:287) requires appending `?v={version}` after resolving storage URLs. Actual repository code stores plain resolved URLs, e.g. [CatalogRepositoryImpl.kt](/home/Programming/Android/schoolquiz4.0/shared/feature/catalog/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/catalog/data/CatalogRepositoryImpl.kt:31), and tests assert the plain URL.

- **P2 — ADR-HMQ-02 bootstrap Query B limit is missing.** The ADR requires `.limit(1000)` for initial quest bootstrap; [FirebaseQuestRemoteDataSource.kt](/home/Programming/Android/schoolquiz4.0/platform/firebase/quest/src/commonMain/kotlin/com/tpov/schoolquiz/platform/firebase/quest/FirebaseQuestRemoteDataSource.kt:30) uses `whereArrayContainsAny` and `whereGreaterThan` without `limit(1000)`.

**Passing / Mostly Aligned**

- Repository interface signatures in §2 mostly match the real interfaces under `shared/feature/*/domain/repository/`. The main mismatch is behavioral, not signature-level.
- Core `SyncModule` bindings exist for `SyncStateRepository`, `CascadingSyncOrchestrator`, `Syncable`, `WorkManager`, and `WorkerFactory`.
- `AppApplication.kt` does pass the shared auth UID flow into `appShellDataModule`, so that part is semantically aligned even though the full §13 list is not equal.

**Verification**

I did a read-only review only. I did not run Gradle tests because the current sandbox is read-only and test/build tasks would write outputs.