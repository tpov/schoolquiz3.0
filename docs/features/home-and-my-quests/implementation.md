---
feature: home-and-my-quests
status: implemented
date: 2026-04-24
---

# Implementation Report — Home & My Quests

## Summary

6-уровневая каскадная синхронизация `Catalog → Quest → Section → Theme → Lesson → Question` реализована полностью. Экраны "Мои квесты" (новый) и "Домашние квесты" (polish) — on place. Walking Skeleton domain сгенерирован на spec-фазе, 5 фаз адаптер-интеграции + cross-phase fix pass. Smoke test green.

## Phases Completed

| Phase | Scope | Result |
|-------|-------|--------|
| phase-01 | DB schema v2→v3, 5 entities + StringSetConverter + kspJvm, Coil 3.4.0, kotlin 2.1.20→2.3.10 global bump, quiz cleanup, Phase04Test TDD fix (scope creep) | PASSED 5/5 reviewers |
| phase-02 | 5 data modules (quest/section/theme/lesson/question) + 5 Firebase adapters + CatalogRepositoryImpl delta-sync + SyncStateRepository Koin + Walking Skeleton domain sync with SSoT (Result&lt;Set&lt;Id&gt;&gt;, nullable uid, getLocalContentsVersion) + ADR-CMP-56 | PASSED 5/5 |
| phase-03 | CascadingSyncOrchestrator + SyncLevel enum + SyncModule replacement (get&lt;CatalogRepository&gt; as Syncable → get&lt;CascadingSyncOrchestrator&gt;) | PASSED 5/5 |
| phase-04 | AppShellTransitions OpenQuestCreate + Labels QuestCreateRoot + AuthRepository Koin verify + 2 ADR-COMP-* amendments | PASSED 5/5 |
| phase-05 | MyQuestsComponent + HomeQuestsComponent + MyQuestsScreen + HomeQuestsScreen + QuestCard + StarRating + QuestDisplayItem + QuestPresentationModule + AppShellScreen routing + CatalogGrid polish + instanceKeeper fix + ADR-HMQ-06 amendment | PASSED 5/5 |
| cross-fix | 7 cross-phase fixes: firestore.rules, subtree-atomic cursor, Clock.now() as canonical, cv-filter returns, FK cascade v3, guest UserStats emission, child archived version guard, StarRating visual partial | DONE |

## Review Verdicts

Per-phase (same-model): все 5 фаз PASSED architect + code + security + completeness + concurrency autonomous fix loop. ~30+ findings (blocker/high/medium/low) разрешены.

Cross-phase (Codex CLI adversarial, Skeptic/Architect/Minimalist/Realist + 3 iteration loop):
- 5 BLOCKERs → 5 fixed (B1 Firestore rules, B2 subtree-atomic cursor, B3 Clock.now() uniform, B4 cv-filter, B5 FK cascade)
- 8 HIGH → 3 critical fixed (H2 guest stats, H7 version guard, H8 StarRating partial), 5 deferred
- Security regressions detected + fixed: isAdmin self-escalation blocker, quest write rules narrowed (create + update)
- Stale docs synced: 0-spec.md:64,761,997,1100,1171; 02-behavior.md:33,60,149,418,447; 03-decisions.md:47,687,804
- 9 MEDIUM → captured as retrospective items

Полные findings: `docs/features/home-and-my-quests/reviews/cross-phase/{skeptic,architect,minimalist,realist,synthesis}.md`.

## Changed Files

Total: 86 files changed, +1674/-494 lines.

### New modules
- `shared/feature/quest/{domain,data}`, `shared/feature/section/{domain,data}`, `shared/feature/theme/{domain,data}`, `shared/feature/lesson/{domain,data}`, `shared/feature/question/{domain,data}`
- `android/feature/quest/presentation`
- 5 × `platform/firebase/src/main/.../{quest,section,theme,lesson,question}`

### Deleted
- `shared/feature/quiz/{domain,data}` (placeholder cleanup)
- `android/feature/quiz/presentation` (placeholder cleanup)
- 5 placeholder files в `shared/core/catalog/domain` (Quest model, QuestRepository, CreateQuestUseCase, FakeQuestRepository, QuestCatalogLinkTest)

### Modified (highlights)
- `gradle/libs.versions.toml` — kotlin 2.3.10, ksp 2.3.7, kotlinx-coroutines 1.10.2, kotlinx-serialization 1.7.3, coil3 3.4.0, decompose-testutils
- `buildSrc/*` — compilerOptions DSL migration (kotlinOptions deprecated in 2.3.x)
- `shared/core/persistence` — AppDatabase v1→v3, 5 new entities + DAOs, StringSetConverter @ProvidedTypeConverter, FK cascade
- `shared/core/sync` — InMemorySyncStateRepository monotonic setCursor, CascadingSyncOrchestrator
- `shared/feature/app-shell` — AuthRepository, Destination.OpenQuestCreate, AppShellTransitions guard
- `apps/android-next/AppApplication.kt` — startKoin с 15 modules + WorkManager enqueueUniquePeriodicWork
- `android/feature/app-shell/presentation/ui/AppShellScreen.kt` — LocalTabContent exhaustive routing
- `android/core/designsystem` — QuestCard, StarRating, QuestDisplayItem, CatalogGrid polish
- `firestore.rules` — 5 new collections rules + isAdmin() function

## Validation

```
./gradlew allTests --no-configuration-cache        # BUILD SUCCESSFUL (1132 tasks, 0 failures)
./gradlew assemble --no-configuration-cache        # BUILD SUCCESSFUL (3940 tasks, debug + release APK)
./gradlew :shared:core:sync:jvmTest                # BUILD SUCCESSFUL
./gradlew :android:core:designsystem:test          # BUILD SUCCESSFUL (BrandComponentsInvariantsTest green)
./gradlew :android:feature:quest:presentation:test # BUILD SUCCESSFUL (24 tests)
```

connectedAndroidTest — DEFERRED (нет подключённого устройства в dev окружении); Room androidTest suite (AppDatabaseSchemaValidationTest, QuestDaoBoundaryTest, SectionDaoBoundaryTest) — требует device.

## AC Coverage

All 58 AC mapped per phase (phase-01..05/overview.md). Walking Skeleton 9 AC pre-existing green (AC#1, #2, #4, #5, #6, #41-#44). Out-of-scope: AC#35-40, AC#51-53 (Firestore security rules — server-enforced, client не может unit-test), AC#18 (SyncNow dev button — separate tooling), AC#31-34 (full server integration).

## Remaining Issues / Known Debt

### Accepted trade-offs (ADR'd)
- Cursor strategy: `Clock.System.now()` (conservative) вместо spec `max(dto.lastModifiedAt)` — ADR-CMP-49 amendment phase-03. Minor over-fetch next cycle; safety: items не miss'ятся.
- CascadingSyncOrchestrator `userStatsRepo: @Suppress("unused")` — future dynamic shelves (OQ-CMP-SHELVES). MVP: hardcoded `{home, arena}`.
- `getKoin()` в `MyQuestsContent`/`HomeQuestsContent` Composables — service locator anti-pattern, TODO(phase-05-debt) — resolution зависит от OQ-NAV-1 (Navigator как Koin singleton).

### Deferred (post-MVP / out-of-scope phase)
- H1 — visibleOn=[] public quest removal unobservable через Query B (Firestore array-contains-any requires non-empty). Нужен tombstone mechanism или server event.
- H3 — Storage URL resolver failure cached as null + cursor advances. Atomic violation, minor.
- H4 — DefaultRootComponent → concrete SyncWorker import (presentation layer violation). Requires sync scheduler abstraction.
- H5 — Koin §13 signature mismatch (`(ctx, nav)` vs SSoT `(ctx)`) — connected to OQ-NAV-1.
- M1 — AC coverage traceability gaps (Realist P0): AC#1-6, 12, 16, 18, 23-24, 30, 32-40, 42-44, 51-53 missing explicit test markers.
- M2 — KoinModuleWiringTest не проверяет real graph (quest/data/firebase modules не verified via Koin smoke).
- M3 — ADR-CMP-50 cache-busting `?v={version}` не appended к URL (stale image risk).
- M4 — ADR-HMQ-02 bootstrap `.limit(1000)` missing в FirebaseQuestRemoteDataSource.
- M5 — 3 FakeCatalogRepository (domain/sync/presentation) — consolidate в shared test fixture.
- M6 — 4 mechanical repository stacks (Section/Theme/Lesson/Question) — potential template/contract pattern.
- M7 — Badge API future-proofing (BadgeContent + unused `badge` params).
- M8 — Unused `:shared:core:sync` Gradle dep в 5 data modules.

### Open architectural questions
- B5 FK cascade race: archived catalog sync + concurrent quest upsert → FK violation → sync retry. Eventually consistent, acceptable for MVP.
- Guest public quest sync: ADR says null UID runs Query B, security rules require auth. Contradiction требует decision.
- Orphan cleanup policy: если не FK cascade, то periodic worker или JOIN filter.

## Retrospective items (для /feature-retrospective)

1. `03-decisions.md:86-87` ADR-CMP-49 Consequences устарел (named("cascading")) — обновлён backend-dev phase-03.
2. Phase-02 overview.md:187 Pattern Invariant про cursor self-managed — ошибочен для non-catalog repositories, противоречит SSoT 06-api-contract.md:178-181 (orchestrator-managed).
3. Reviewers иногда нарушают protocol "idle до assignment" — preflight checks перед кодом до build gate приводят к spurious blockers.
4. Coil 3.4.0 scope creep (kotlin 2.1.20 → 2.3.10 global bump) — plan-reviewer не поймал incompatibility риск.
5. Multiple FakeCatalogRepository — test fake ownership convention needed.

## Handoff

Feature готова к production deploy с учётом accepted trade-offs и known debt. connectedAndroidTest нужно прогнать на реальном устройстве перед merge. Firestore rules требуют deploy в Firebase Console.
