---
title: 'Epic-3 arena retro fix pack — action items 1–7'
type: 'bugfix'
created: '2026-10-25'
status: 'done'
baseline_revision: cf2d8cb92107edd7dd8bca1e23b44fb5df77a147
route: 'full'
route_source: 'pinned'
review: 'thorough'
review_source: 'pinned'
route: 'full'
route_source: 'pinned'
review: ''
review_source: ''
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 0
followup_review_recommended: true
context: []
warnings: ['multiple-goals', 'oversized']
deferred:
  - summary: >-
      The consent dialog's «Позже»/outside-tap wiring is pinned only at the component boundary;
      the one remaining composable-lambda hop (AlertDialog onDismissRequest ->
      onDismissTermsConsent) has no running test because instrumented suites are compile-only in
      this repo's verification path.
    evidence: |-
      Reverting LessonRunnerScreen.kt's two onClick lines to component::onBack would pass every
      gate. Settling it means changing how verification runs (a running compose/instrumented
      gate), not adding a test to this change.
    location: >-
      android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:110-116
    severity: low
  - summary: >-
      TournamentNotice still folds SyncError.TermsConsentRequired into Retry while the remedy is
      accepting consent, not retrying — the same honesty gap item 7 fixed for
      SettlementAlreadyRecorded, but pre-existing and outside this pack's cited scope.
    evidence: |-
      TournamentNotice.kt folds TermsConsentRequired, VersionConflict, Unknown into Retry; the
      item-7 finding named only SettlementAlreadyRecorded. Pre-existing surface, owner-adjacent.
    location: >-
      android/feature/tournament/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/tournament/presentation/model/TournamentNotice.kt:104-109
    severity: low
  - summary: >-
      The Firestore stub's batch().commit() records and applies writes without consulting the
      fail-injection hook, so batch-driven handler paths cannot be failure-injected.
    evidence: |-
      Pre-existing stub asymmetry (document.maybeFail covers doc/transaction commits, not
      batch commit); the pack only added select() to the stub.
    location: >-
      functions/_firestore-stub.js:260-271
    severity: low
---

<intent-contract>

## Intent

**Problem:** The epic-3 arena retrospective (2026-10-22) left 7 dev-owned action items: a settle-replay ACK that persists a fabricated receipt and can spin or lie (L1/L2), a consent/NEEDS_RECOVERY UI round with a no-op Send button, an unguarded accept and wrong dialog wiring (L3–L5), a missing sync-trace read branch (L6), silently degrading demo fixtures and rig drivers (L8), client fixture SHA pins disconnected from any generator (L9), and the ONLINE-O2 adapter family with zero production callers plus its untested failure mapping (A1/L7/L10).

**Approach:** Seven independent fix clusters implemented and gated in one shift on branch `night/e7-commerce`, each committed separately (`fix(arena): retro item N — ...`). Client Kotlin fixes first, then rig/demo-fixture repair, fixture regeneration, ONLINE-O2 wiring last. Demo-backend repair only touches the demo emulator container (`demo-schoolquiz-mvp`, manifest pins own the truth).

## Boundaries & Constraints

**Always:**
- Every engine/dispatch change keeps the durable-receipt semantics: an ACKed row must leave behind a receipt the parser deliberately understands; no path may retry a server-terminal refusal forever.
- Offline arena play must stay legal without any network gate (CAP-1); ONLINE-O2 is best-effort with immediate local-timer fallback.
- Gradle via `sgw` (flock); one heavy build at a time; session ends committed and pushed.
- Demo emulator container: repair drift to manifest pins; never overwrite non-pinned player data; if the container is not running, record the repair command instead of starting services.

**Never:**
- No changes to server refusal semantics (SETTLEMENT_REUSE stays a refusal; fix is client-side).
- No sprint-status.yaml writes; no tickets.toml registry edits (retro item 8 is owner-owned).
- No mvpb build-type work, no owner decisions (retro item 8), no device/Maestro sessions.
- No parser laxity: QuestionWindowWireParser strictness stays.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Settle replay, same mutationId | Engine step() on SETTLEMENT_REUSE, op = SUBMIT_ATTEMPT | ACK with first-class marker receipt parsed to LEGACY_RECORDED (deliberate) | No retry, no zero-score |
| Settle replay, foreign op | Same error on any other operation | Falls through to policy; CONFLICT disposition parks the row | Never infinite retry |
| consent.accept() throws | Room I/O failure on either runner | Dialog stays open, error logged, no sync enqueued, no crash | Guarded launch |
| Send with NEEDS_RECOVERY frame | Journal unreadable | No Send button (removed from SYNC_STATUSES); Reload is the repair | No silent no-op |
| «Позже» / dismiss | Dialog visible | Flag cleared, screen stays, dialog may re-raise on next explicit sync request | No navigation away |
| Window resume, online HARD | Continue at PAUSED, HARD, network up | begin+fetch side-channel; server remainderMs bounds the deadline; expired → close path | Any failure → local timer |
| remainderMs corrupt | Larger than allocated window | Clamped to window remainder | No near-infinite clock |

</intent-contract>

## Code Map

- `shared/core/outbox/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/outbox/OutboxEngine.kt` — settle-replay branch :226-241 ACKs before `policy.onFailure`; add `record.operation == OutboxOperations.SUBMIT_ATTEMPT` guard. `acknowledge` :273-303 persists receipt then removes row.
- `shared/core/network/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/network/SyncError.kt` — SettlementAlreadyRecorded disposition RETRY :55-59 → change to CONFLICT (structural safety: policy parks instead of spins).
- `shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/outbox/AttemptSettlementReceiptParser.kt` — :61-77 envelope match, :68/:173 `legacy()` fallback; add named marker-envelope branch → LEGACY_RECORDED with KDoc.
- `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/RoomSyncRunTraceStore.kt` — `branchName()` :112-121 has SETTLEMENT_ALREADY_RECORDED; `toTrace()` :129-143 falls to else→Unknown; add read-side case. Test: `shared/core/persistence/src/jvmTest/.../SyncRunTracePersistenceTest.kt` (real SQLite round-trips; pin pattern :98-109).
- `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ArenaRunnerContent.kt` — SYNC_STATUSES :304-312 (drop NEEDS_RECOVERY), Send button :275-277, Reload button :272-274 stays.
- `.../presentation/component/ArenaRunHost.kt` — `sync()` :182-199 (consent branch raises only with pending work), guarded `acceptTerms` :202-216 (reference for the port), `canRetryReceiptSync` :464-476. Flag `needsTermsConsent` :70.
- `.../presentation/component/DefaultLessonRunnerRootComponent.kt` — unguarded `acceptTerms` :179-186 → port try/catch; `onSyncResult` :164-173 raise condition; flag :175-177.
- `.../presentation/ui/LessonRunnerScreen.kt` — dialog :100-119: onDismissRequest + «Позже» wired to `component::onBack` → add `onDismissTermsConsent` to both component interfaces.
- `android/feature/lesson-runner/presentation/src/test/...` — `ArenaRunHostTest.kt` + `ArenaRunHostFixture.kt` (consent defaults null at :60), `DefaultLessonRunnerRootComponentTest.kt` (terms-consent happy path ~:1684). JVM tests hit `android.util.Log` → needs `testOptions.unitTests.isReturnDefaultValues = true` in the module build.gradle.kts.
- `platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/network/CallableErrors.kt` — named-const convention with "must match functions/*.js" comments (:34, :43, :54); add SETTLEMENT_REUSE const. `FirebaseMutationTransport.kt` :141-150 inline literal → use it.
- `android/feature/tournament/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/tournament/presentation/model/TournamentNotice.kt:104-109` — SettlementAlreadyRecorded folded into Retry → own branch.
- `scripts/demo-fixtures/` — `manifest.json` (bank-high pin :105-127 rating 3/1/v1 seeded:true, price 100; bank-low :82-104 rating 4/2/v2 seeded:false → flip true), `seed-demo-fixtures.js` (zero-overwrite; assertOrCreate :51-63, assertPinnedDoc :66-74; env: GCLOUD_PROJECT/FIRESTORE_EMULATOR_HOST/FIREBASE_AUTH_EMULATOR_HOST, GOOGLE_APPLICATION_CREDENTIALS unset), `verify-demo-fixtures.js` (read-only; env :21-23), `arena-races-live-run.js` (mutation window :256-324, mutation :272, finally :317-324; weak royalty assert :457), `arena-quotas-live-run.js` (same fragile window :289, finally :295-300), `README.md` (17 lines, no repair guidance).
- `scripts/package.json:9-10` — explicit test/lint chains, zero demo-fixtures entries.
- Fixture pins: `platform/firebase/src/test/.../FirebaseArenaTicketRemoteDataSourceTest.kt:264-300` + `FirebaseArenaLessonCatalogRemoteDataSourceTest.kt:220-239` (sha pins + regeneration policy comments); fixtures `shared/feature/lesson-runner/data/src/jvmTest/resources/arena-ticket-client-fixtures.json`, `arena-catalog-client-fixtures.json` (exactly two; entry-runtime pinned inside ticket's sourceSha256). NO generator exists in repo (evidence: docs/agents/evidence/2026-10-08-functions-contract-drift/README.md:57, 2026-10-18-epic6-merge/README.md:88-89; two hand re-pins a762ffb2, d833cb24).
- `shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/ArenaOnlineO2Adapter.kt` — (a) :50-52 out-of-range index silently null; (b) :131-132 remainderMs bounded only by answerDeadlineMs; (d) :91 state only non-blank. (c) :194-198 already throws loudly (RELOAD_REQUIRED) — pin with test, no change. Object, not DI-registered.
- `shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/remote/QuestionWindowWireParser.kt` — strict parser; edge (e) from retro is WRONG: `fail{}` :118-121 already funnels all throwables into MalformedWindowWire (pinned by test :123). No change; add adapter-side regression tests only.
- `platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/arena/FirebaseQuestionWindowRemoteDataSource.kt` — callables :161-164 (beginLessonQuestionWindow/fetchNextLessonQuestion); mapping `callableFailure` :145-151 untested (fake call only throws IOException); `QuestionWindowCallableException` :156-159 internal.
- `platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/di/FirebaseModule.kt:117-124` — data source registered. `apps/android-next/.../AppApplication.kt:162-232` loads the graph.
- `.../lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:33-47` — `ArenaRunServices` (runs/entries/sessions/sessionEvents/wallet/result) lacks the window source; :45-48, :80-83 consent handles.
- `.../presentation/component/ArenaRunCoordinator.kt` — dispatch :78-103, pure transitions + checkpointCAS :96-100; `ArenaRunTransitions.resumePaused` :97-112 sets local deadline. Host intercepts Continue for side-channel; window calls must NOT go inside perform{}.
- Koin test patterns: `platform/firebase/src/test/.../arena/ArenaWalletKoinTest.kt` (module resolution), `apps/android-next/src/test/.../KoinModuleWiringTest.kt` (full graph).
- Server reference (no changes): `functions/arena-entry-runtime.js` settle :207, replay :222-227 (fingerprint match returns retained response; SETTLEMENT_REUSE only on fingerprint/uid mismatch), `functions/index.js:1253-1272` error surface, `functions/mutation-queue.js:89-96` same-key replay, `functions/lesson-question-window.js` (windows, OPEN/DELIVERED/EXPIRED :59-66).
- `shared/feature/economy/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/economy/data/EconomyTermsConsentGateImpl.kt:33-36` — accept persists + `requeueConflictedTerms()` (verify its operation list excludes SUBMIT_ATTEMPT).

## Tasks & Acceptance

**Execution:**
- [ ] `AttemptSettlementReceiptParser.kt` + `OutboxEngine.kt` + `SyncError.kt` -- item 1: named marker envelope parsed deliberately to LEGACY_RECORDED (constant + KDoc: server refused divergent re-settle; attempt terminal server-side; never zero-score); guard engine branch with `record.operation == OutboxOperations.SUBMIT_ATTEMPT`; disposition RETRY→CONFLICT so any policy path parks. Verify `requeueConflictedTerms` operation list excludes SUBMIT_ATTEMPT. -- L1+L2
- [ ] `OutboxEngineBoundaryTest.kt`, `AttemptSettlementReceiptTest.kt` (+ one outbox test) -- tests: marker envelope → LEGACY_RECORDED; non-SUBMIT_ATTEMPT op with the error → policy CONFLICT park; existing ACK pin updated. -- L1+L2
- [ ] `DefaultLessonRunnerRootComponent.kt` + `ArenaRunHost.kt` + `LessonRunnerScreen.kt` + both component interfaces -- item 2: port guarded acceptTerms; add `onDismissTermsConsent()` clearing the flag without leaving the screen, wire onDismissRequest + «Позже» + flag reset; raise the dialog only when a sync request has pending work for this run (pending decision/settlement observed), not on the bare gate verdict. -- L4+L5
- [ ] `ArenaRunnerContent.kt` -- item 2b: remove NEEDS_RECOVERY from SYNC_STATUSES (misleading no-op Send); Reload remains the NEEDS_RECOVERY repair. Update 0cba5717's stale comment. -- L3
- [ ] `android/feature/lesson-runner/presentation/build.gradle.kts` + `DefaultLessonRunnerRootComponentTest.kt` + `ArenaRunHostFixture.kt`/`ArenaRunHostTest.kt` -- item 2c: `isReturnDefaultValues = true` for unit tests; consent-flow tests: throwing accept on both runners (dialog stays, no sync, no crash), dismiss clears flag, dialog not raised without pending work. -- L4+L5+VG#2
- [ ] `RoomSyncRunTraceStore.kt` + `SyncRunTracePersistenceTest.kt` -- item 3: SETTLEMENT_ALREADY_RECORDED read-side case + round-trip test beside :98. -- L6
- [ ] `CallableErrors.kt` + `FirebaseMutationTransport.kt` + `TournamentNotice.kt` -- item 7: `SETTLEMENT_REUSE` named const with must-match comment naming `functions/arena-entry-runtime.js` and `functions/arena-tickets.js`; TournamentNotice gives the error its own branch (not Retry); KDoc at both mapping-convention sites recording the A3 decision: question-window typed failures are a separate error domain from outbox SyncError, divergence is deliberate. -- item 7, A3
- [ ] `manifest.json` + `seed-demo-fixtures.js` unchanged + one-off repair -- item 4a: flip bank-low rating pin to seeded:true (fields unchanged 4/2/v2); repair bank-high `lesson_ratings/ac_6ba84b006f7d423f8630ac7802407eb0_lesson_1` to pinned 3/1/v1 (merge) on the demo container if running (check `docker ps` on VPS first), then `verify-demo-fixtures.js` must pass; if container down, commit a `repair-pin.js` + README command and note it. -- L8
- [ ] `arena-races-live-run.js` + `arena-quotas-live-run.js` -- item 4b: startup preamble asserts manifest rating pins (bank-high 3/1/v1, bank-low 4/2/v2) and repairs drift with a loud log before the run (both drivers — quotas has the identical fragile window); keep finally-restores. -- L8/adversarial #14
- [ ] `arena-races-live-run.js:457` -- item 4c: exactly-once assert = exact expected royalty row count captured in part A + `sumLedger(...) === 1`, not existence. -- adversarial #15
- [ ] `scripts/package.json` + `scripts/demo-fixtures/README.md` -- item 4d: add `node --check` of all demo-fixtures scripts to lint; add `verify:demo-fixtures` script (env-parametrized host); README documents drift/repair procedure + the five drivers + baseline-read. -- VG#8
- [ ] New committed capture harness (e.g. `scripts/generate-arena-client-fixtures.js`) + the two fixture JSONs + the two pin tests -- item 5: harness runs the real producers (ticket: arena-tickets/arena-ticket-recovery/question-redaction/_question-fixtures; catalog: arena-lesson-catalog + index.js loader) under the functions test fake/emulator, emits both fixture files with per-section hashes and sourceSha256; regenerate; update pins from actual output; `:platform:firebase` unit tests must be green after (run them FIRST to learn the current red/green baseline). -- L9
- [ ] `ArenaOnlineO2Adapter.kt` + `ArenaOnlineO2AdapterTest.kt` -- item 6a: out-of-range index → require failure (typed contract, caller maps to RELOAD_REQUIRED), not silent null; remainderMs clamped to the allocated window remainder, not just answerDeadlineMs; requireWindowIdentity enforces the explicit expected window state per function (not just non-blank); regression tests pin (c) loud-throw and the adapter require→RELOAD_REQUIRED path. -- L7a/b/d
- [ ] `FirebaseQuestionWindowRemoteDataSourceTest.kt` -- item 6b: fake call throwing QuestionWindowCallableException with UNAUTHENTICATED/PERMISSION_DENIED → ACCOUNT_UNAVAILABLE, INVALID_ARGUMENT → INVALID_REQUEST, other codes → SERVER_FAILURE; malformed stays MALFORMED_RESPONSE. -- L10/VG#7
- [ ] `LessonRunnerPresentationModule.kt` + `ArenaRunServices` + `ArenaRunHost.kt` + `ArenaRunCoordinator.kt`/transitions + new Koin test -- item 6c: expose the window source to the host; at Continue-from-PAUSED on a HARD attempt with network up, host runs a side-channel (outside perform{}) begin+fetchNext: DELIVERED → resume with server-deadlined checkpoint (clamped remainder), WINDOW_EXPIRED → existing close path, ANY failure → local-timer resumePaused unchanged (CAP-1). Trigger is a dev default: HARD + online at the transition; recorded for owner review. Koin test: firebaseModule resolves QuestionWindowRemoteDataSource; ArenaRunnerComponentFactory graph builds with the new service. -- A1/item 6

**Acceptance Criteria:**
- Given an outbox SUBMIT_ATTEMPT row receiving SETTLEMENT_REUSE, when the engine steps it, then the row is ACKed exactly once with a receipt that parses to LEGACY_RECORDED, the result screen shows settled-without-proof (never zero, never spinning), and the sync trace screen names the branch after restart.
- Given the same error arriving for any non-SUBMIT_ATTEMPT operation, when policy handles it, then the row parks in CONFLICT (no retry loop).
- Given a throwing consent.accept() on either runner, when the player taps «Принять», then the dialog stays, the error is logged, no sync drains, no crash.
- Given a NEEDS_RECOVERY frame, when the player looks at the attention card, then there is no Send button and Reload is offered.
- Given the dialog visible, when the player taps «Позже» or dismisses, then the runner stays on the result screen with the flag cleared.
- Given a drifted demo container, when any live-run driver starts, then it repairs manifest rating pins first and logs the repair; verify-demo-fixtures passes after the bank-high repair.
- Given current producers, when the committed generator runs, then it emits both fixture files whose pins satisfy the platform tests (proven parity, no hand re-pin).
- Given an online HARD arena attempt, when the player continues from pause with the network up, then the next-question deadline comes from the server window (clamped) and expires through the existing close path; with any window failure the attempt proceeds on the local timer exactly as today.

## Implementation Notes

- Run state after implementer attempt 1 (network-fatal, orchestrator-recorded 2026-10-25): items 1, 2, 3, 4, 7 are COMMITTED on `night/e7-commerce` — 455f19ee (item 1: marker receipt via shared SettlementReplayReceipt + CONFLICT disposition), 6521e5df (item 3), c305427a (item 2: guarded accept both runners, onDismissTermsConsent, pending-work raise, NEEDS_RECOVERY off SYNC_STATUSES, isReturnDefaultValues), 919be5a5 (item 7: SETTLEMENT_REUSE_REASON const, TournamentNotice.AlreadyRecorded, A3 KDocs), ba915081 (item 4: manifest seeded:true, rating-pins.js preamble for both drivers, repair-pin.js, exact-once assert, lint+verify scripts; demo container RUNNING — lint green, repair-pin no-op, verify-demo-fixtures PASS, prices 1/100 held). Per-commit gates recorded green in their bodies.
- Item 6 was IN FLIGHT when the agent died: uncommitted working-tree changes in ArenaRunCoordinator.kt, ArenaRunHost.kt, LessonRunnerPresentationModule.kt, ArenaOnlineO2Adapter.kt (+ its test), FirebaseModule.kt, FirebaseArenaKoinWiringTest.kt, FirebaseQuestionWindowRemoteDataSourceTest.kt, ArenaRunCoordinatorTest.kt, ArenaRunHostFixture.kt, ArenaRunHostTest.kt, ArenaRunnerFactoryWiringTest.kt, presentation build.gradle.kts. Next agent: review that diff against spec item 6, finish/verify/commit it as `fix(arena): retro item 6 — ...`.
- Update (orchestrator-recorded after implementer attempt 2, also network-fatal): item 6 is COMMITTED as 98539e88 (ONLINE-O2 hardened adapter, typed refusal tests, real window side-channel on Continue-from-pause). Working tree on this spec's paths is CLEAN. Remaining: item 5 (fixture capture harness + regeneration, NOT STARTED), the Implementation Notes append, `sgw ./gradlew ciCheck`, push `night/e7-commerce`, final report (env-notes format) with the ONLINE-O2 dev-default flag.
- Update (orchestrator-recorded after implementer attempts 3–4, both network-fatal at ~23 min with zero disk writes; the orchestrator then implemented item 5 directly — DEVIATION from the subagent handoff, forced by four consecutive subagent provider deaths): item 5 is COMMITTED as b5454b41. The committed capture harness `functions/generate-arena-client-fixtures.js` rebuilds both recorded worlds from the fixtures' own content and regenerates every section through the real production entry points. Regeneration surfaced real drift the hand re-pins had masked: the recorded settled sections were synthetic (literal scoreDigest "private-score-digest", hand-set fractional percentScore 75.5 — the production settle path did not exist on 09-07); percentScore is now the real integer 100 (four client literals updated; the 1e999 malformed case now patches structurally instead of string-replacing); requestHash inputs changed shape; catalog fingerprints moved with the policy version while all recorded cards stayed byte-identical; Ordering/FillBlank shuffles differ; NEW FINDING (deferred): the shared `_question-fixtures.FILL_BLANK` carries a 3-candidate pool the client ticket parser refuses (it accepts exactly 5 or 10) — the capture world uses its own production-shaped five-candidate FillBlank; fixing the shared fixture is blocked on its consumers' pinned literals (question-key-store) and needs a deliberate decision. The Firestore stub learned `select()` (projection shapes reads, not rows). Gates: data jvmTest 367 green; platform:firebase 336 green (pins verified against regenerated-from-source output); functions `npm test` 128/129 with the single red `mvp-isolation` being the recorded foreign red (epic-6 retro). Commit: b5454b41.
- Item 5 (fixture capture harness + regeneration) NOT STARTED.
- After items 5+6: update this section, run `sgw ./gradlew ciCheck`, push `night/e7-commerce`, produce the final report (env-notes format), including the ONLINE-O2 dev-default flag for owner review.

## Spec Change Log

## Review Triage Log

### 2026-10-26 — Review pass
- verdicts: 30 findings — high 0, medium 4, low 14, false 12, maybe-false 0
- findings:
  - `[false]` `[reject]` diff artifact lacks commit delimiters (blind) — the staged review artifact is a concatenation of eight commit diffs by design; not a defect of the change.
  - `[medium]` `[patch]` window-state OPEN/DELIVERED/EXPIRED literals and SETTLEMENT_REUSE are comment-enforced only, no server-source binding test (blind + verification-gap, same root) — verified: every client test sources state strings from the adapter's own constants and functions tests from the server's; a coordinated rename ships RELOAD_REQUIRED on every online HARD continue with all gates green. Fixed: ServerWindowContractBindingTest reads functions/lesson-question-window.js + both SETTLEMENT_REUSE producers (repo's binding pattern).
  - `[false]` `[reject]` private_quiz.SUBMIT_ATTEMPT settle rows have no named CONFLICT decider (blind) — refuted: the transport maps SETTLEMENT_REUSE to SettlementAlreadyRecorded only for operation == OutboxOperations.SUBMIT_ATTEMPT; private_quiz settles never produce that error, so no such parked rows can exist.
  - `[false]` `[reject]` markerLegacy() duplicates legacy() (blind) — the byte-identical bodies are the design: two call sites with two documented meanings (deliberate marker vs pre-protocol fallback); merging them erases the distinction.
  - `[false]` `[reject]` no negative test for near-marker payloads (blind) — a near-marker string fails the exact match and falls to legacy(), landing in the same deliberately-intended LEGACY_RECORDED state; no distinct bad outcome exists to pin.
  - `[false]` `[reject]` guarded acceptTerms failure is silent UX with no recorded decision (blind) — the behavior (keep dialog, log, skip sync) is exactly what retro item 2/e289e3fc mandate; the recorded decision is the retrospective itself.
  - `[medium]` `[patch]` diverge() with active != null silently drops the divergence (blind; verified at ArenaRunCoordinator.kt:131 — reachable from the host's expiry path while a user-action CAS is in flight) — the KDoc forbids exactly this silence. Fixed: divergence is deferred and applied when the writer completes; pinned by a new coordinator test. Judged patch, not bad_spec: the spec's contract was correct and the smallest fix lives inside the diff's own file.
  - `[low]` `[patch]` applyExternal KDoc claims a fresh-state candidate; it actually relies on the revision CAS for freshness (blind) — KDoc corrected to state the real discipline.
  - `[low]` `[reject]` module-wide isReturnDefaultValues mutes unmocked android.* calls (blind) — real tradeoff, but the alternative (a Log seam) adds more complexity than the harm; the module's unit tests already rely on the flag.
  - `[false]` `[reject]` the capture harness and stub are wired into no gate (blind) — refuted: functions/check-syntax.js reads every functions/*.js (233/233 parse after this pass) and the stub is exercised by ~90 suites.
  - `[low]` `[patch]` catalogFixtures.full computed twice, first computation dead (blind) — dead work removed; the surviving full quote is documented as its own read-only transaction.
  - `[false]` `[reject]` rating-pin repair fabricates authorUid-less docs diverging from the seeder (blind) — refuted: the manifest's rating pins carry exactly the three repaired fields; repair-created and seeder-created docs are shape-identical.
  - `[low]` `[reject]` demo-fixtures lint chain is enumerative, not structural (blind) — restructuring the gate exceeds a direct correction; the chain matches the repo's existing package.json style.
  - `[low]` `[defer]` TournamentNotice keeps TermsConsentRequired -> Retry (blind) — real honesty gap, but pre-existing and outside item 7's named scope; recorded in deferred.
  - `[low]` `[patch]` SettlementAlreadyRecorded KDoc implies any *SUBMIT_ATTEMPT ACKs (blind) — KDoc now names OutboxOperations.SUBMIT_ATTEMPT exact equality and documents the terms-requeue interaction (one extra refuse cycle, same marker terminal).
  - `[low]` `[patch]` resumeDelivered dropped the answerDeadlineMs cross-check when adding the clamp (blind; verified at ArenaOnlineO2Adapter.kt:150-152) — both server facts are checked again; pinned: remainder beyond its own deadline throws as a corrupted delivery.
  - `[false]` `[reject]` expiredWindowRequestId mixes state generations (blind) — attemptId is the attempt's immutable identity; the two reads cannot diverge on it.
  - `[low]` `[patch]` the boundary test hardcodes the "private_quiz.SUBMIT_ATTEMPT" literal (blind) — now uses OutboxOperations.SUBMIT_PRIVATE_QUIZ_ATTEMPT.
  - `[low]` `[defer]` stub batch().commit() bypasses maybeFail (blind) — pre-existing stub asymmetry, recorded in deferred.
  - `[false]` `[reject]` owner-recorded decisions live only in KDoc (blind) — A3 and the ONLINE-O2 trigger default are recorded in this spec's Design Notes and the session report, which is the owner-facing trail.
  - `[false]` `[reject]` fetchNext failing after a committed beginWindow leaves an OPEN window that closes the attempt (edge-case) — server window expiry is lazy window-doc state computed inside the next fetch; it never closes the attempt, which settles through the ticket path unchanged.
  - `[false]` `[reject]` no timeout on window calls wedges pause (edge-case) — both callables go through withAppTimeout(); a hung call resolves to a failure and the local-timer fallback.
  - `[medium]` `[patch]` consent.satisfied() read inside sync() is unguarded — the same Room-I/O class the guarded accept survived crashes the sync tap (edge-case; verified at ArenaRunHost.kt:289 and DefaultLessonRunnerRootComponent.onSyncResult) — fixed on both runners: failed read keeps the tap hands-off (no dialog, no drain, no crash), pinned by tests on both.
  - `[low]` `[reject]` rating-pins preamble crashes on a malformed manifest entry (edge-case) — the manifest is repo-owned; malformed manifest is an immediate repo bug, not an everyday runtime path.
  - `[false]` `[reject]` repair-pin refuses the README-sanctioned parametrized host (edge-case) — repair-pin's contract is the demo backend by exact address; the README documents exactly that host.
  - `[false]` `[reject]` same as the fetchNext-after-begin claim, filed as a kind:claim by the lens — refuted as above.
  - `[low]` `[patch]` the verification spec's requeue-verification premise cannot pass: termsConsentOperations INCLUDES SUBMIT_ATTEMPT (edge-case; verified at OutboxOperations.kt:88) — interaction verified safe (engine ACK removes the row before CONFLICT parking; a resurrected row cycles once to the same marker terminal) and now documented in the disposition KDoc.
  - `[medium]` `[patch]` server window-state literals unbound (verification-gap; pre-verified) — fixed as above.
  - `[low]` `[defer]` consent-dialog dismiss wiring pinned only at the component, not the composable lambda (verification-gap; pre-verified) — recorded in deferred with the verification-path limitation.
  - `[low]` `[patch]` fixtures README overstates the rating-pin preamble coverage (verification-gap "other") — README now names arena-races and arena-quotas as the carriers; online-o2 does not touch ratings.

## Auto Run Result

Status: done
Blocking condition: none

### Summary

All seven dev-owned action items of the epic-3 arena retrospective are implemented, gated, and
committed on `night/e7-commerce` (baseline cf2d8cb9 → 42d0c9c + spec):

1. `455f19ee` — settle-replay ACK made a first-class marker receipt (parser-owned envelope,
   deliberate LEGACY_RECORDED), engine branch guarded to SUBMIT_ATTEMPT, disposition
   RETRY→CONFLICT (policy paths park, never spin).
2. `c305427a` (+`73c54cfa`) — consent/recovery round: guarded acceptTerms on both runners,
   onDismissTermsConsent wired without leaving the screen, dialog raised only on pending work,
   NEEDS_RECOVERY off SYNC_STATUSES (with a JVM-run contract pin), consent-flow tests incl.
   throwing accept.
3. `6521e5df` — sync-trace read side names SETTLEMENT_ALREADY_RECORDED after restart + round-trip
   test.
4. `ba915081` — demo-fixture drift stops being silent: manifest owns the ratings (bank-low
   seeded:true), rating-pins.js repair preamble in both ratings-mutating drivers, repair-pin.js,
   exact-once royalty assert, demo-fixtures in lint + verify:demo-fixtures. Live demo container
   verified: lint green, repair-pin no-op, verify-demo-fixtures PASS (prices 1/100 held).
5. `19f8c255` (re-land of b5454b41, dropped by a concurrent rebase) — committed capture harness
   `functions/generate-arena-client-fixtures.js` + regenerated fixtures; surfaced real drift the
   hand re-pins had masked (synthetic recorded settles with literal scoreDigest and fractional
   percentScore 75.5 → real integer 100 settlements; key-order, requestHash, fingerprint drift;
   the 3-candidate shared FillBlank the client parser refuses — capture world uses its own
   5-candidate source; the shared-fixture/client-pool gap reported, not laundered).
6. `98539e88` — ONLINE-O2 wired: hardened adapter (range, clamp, named window states), typed
   SDK-failure tests, production caller as a host side-channel on Continue-from-pause
   (HARD + online dev default, CAP-1 fallback, divergence → RELOAD_REQUIRED), Koin wiring tests.
7. `919be5a5` — SETTLEMENT_REUSE named constant, TournamentNotice.AlreadyRecorded, A3 documented
   at both mapping sites.

Review pass (4 lenses, 30 findings): 10 patch groups applied in `c42d0c9c` (see Review Triage
Log), 3 items deferred (frontmatter), 3 low rejected as not worth fixing, 12 refuted as false.

### Review findings breakdown

- Patched: 4 medium (window-state literal binding ×2 rows, diverge-during-writer, unguarded
  verdict read) and 7 low (deadline cross-check, requeue interaction doc, repair-pin premise n/a
  → doc, KDoc precision ×2, test literal, dead harness code, README wording).
- Deferred: 3 (composable dismiss wiring, tournament TermsConsent Retry fold — pre-existing,
  stub batch fail-injection — pre-existing).
- Rejected: 12 false with refutations in the triage log; 3 low rejected (complexity exceeds
  harm).

### Follow-up review recommendation

true — patched medium entries: 3 (two grouped roots + the verdict-read guard). Named unverified
risk: the ONLINE-O2 side channel (host requestServerResume + coordinator applyExternal/diverge +
adapter) is pinned by unit tests with fakes only; its first live/instrumented exercise on a rig
or device remains future work, as the retrospective itself recorded.

### Verification performed

- `sgw ./gradlew :shared:core:outbox:test :shared:core:persistence:jvmTest` — green (items 1, 3).
- `sgw ./gradlew :android:feature:lesson-runner:presentation:test` — green, 231 tests incl. new
  consent-flow, deferred-divergence, verdict-read, SYNC_STATUSES pins.
- `sgw ./gradlew :android:feature:tournament:presentation:test` — green (item 7).
- `sgw ./gradlew :platform:firebase:testDebugUnitTest` — green, 337 tests incl. both pin suites
  (regenerated-from-source pins) and the two new binding tests.
- `sgw ./gradlew :shared:feature:lesson-runner:data:jvmTest :shared:feature:lesson-runner:domain:jvmTest`
  — green, 367 + 257 tests.
- functions `npm test` — 128/129; the single red (`mvp-isolation.test.js`) is the recorded
  foreign red (epic-6 retro), failing identically on a clean HEAD worktree.
- functions `check-syntax.js` — 233/233 after AppleDouble junk cleanup.
- `node scripts/demo-fixtures/verify-demo-fixtures.js` against the running demo container — PASS.
- Full `ciCheck` could not complete on this checkout during the run: six enumerated foreign
  breaks, each committed by other sessions or environmental — app-shell and quest-authoring
  androidTest compile ripples (committed), detekt `--jvm-target 25` config (committed),
  functionsTest/scriptsTest configuration-cache onlyIf (documented in deferred-work.md),
  ktlint script formatting in a sibling's mid-rebuild apps/android-next (untracked files),
  packaging requiring the owner's keystore. Every module this pack touched ran green by name.

### Residual risks

- The ONLINE-O2 side channel is unit-pinned only (see follow-up recommendation).
- The demo backend's conformance is verified as of this run; any future driver run without the
  preamble can drift it again (the preamble now repairs at startup, so the window is one run).
- Concurrent sessions share this checkout: two of this run's commits had to be re-landed after a
  sibling rebase dropped one and a poisoned index briefly swept foreign staged deletions into a
  local (never-pushed) commit — both detected by post-commit inspection and repaired; final
  history verified clean.

## Design Notes

- Item 1 deliberately maps the marker to LEGACY_RECORDED (existing honest semantics: settled, no score proof, retry-excluded, never zero) instead of adding a new domain state; the fabricated payload becomes a first-class, parser-owned envelope. Server refusal semantics untouched: same-mutationId replays already succeed via mutation-queue replay; SETTLEMENT_REUSE only fires on divergent fingerprint/uid, where withholding the retained response is the server's deliberate integrity check.
- Retrospective corrections found during investigation, honored here: L3's Send is a no-op only when the gate is satisfied (consent branch fires first otherwise) and terms-parked rows read as PENDING_COMPLETION, not NEEDS_RECOVERY; L7(e) parser edge does not exist (already typed); the fragile ratings window exists in quotas too; fixture regeneration tooling never existed, so item 5 builds it rather than "re-runs" it.
- A3 decision (dev, recorded for owner): the two SDK-mapping conventions stay — `SyncError`/`syncErrorForCode` for outbox-domain callables, typed `QuestionWindowFailureKind` for the ONLINE-O2 read path; documented at both sites.
- ONLINE-O2 trigger default: attempt difficulty HARD AND network up at the Continue transition; window calls are host-scope side-channel, never inside the serialized checkpoint writer; all failures fall back to the unchanged local path. This is the one product-shaped decision in the pack — flag it in the session report for owner review.

## Verification

**Commands:**
- `sgw ./gradlew :shared:core:outbox:test :shared:core:persistence:jvmTest` -- expected: green (items 1, 3)
- `sgw ./gradlew :android:feature:lesson-runner:presentation:test` -- expected: green incl. new consent-flow tests (item 2)
- `sgw ./gradlew :android:feature:tournament:presentation:test` -- expected: green (item 7)
- `sgw ./gradlew :platform:firebase:testDebugUnitTest` -- expected: green incl. new mapping tests; establishes fixture-pin baseline before item 5 (items 6b, 5)
- `sgw ./gradlew :shared:feature:lesson-runner:data:jvmTest :shared:feature:lesson-runner:domain:jvmTest` -- expected: green (items 1, 6a)
- `sgw ./gradlew ciCheck` -- expected: green, full gate before the final push
- `node scripts/demo-fixtures/verify-demo-fixtures.js` (on VPS, demo container up, env per manifest) -- expected: PASS after item 4a
- `node --check scripts/demo-fixtures/*.js` -- expected: lint green (item 4d)
- functions suite (`node run-tests.js` in the functions emulator dir) -- expected: no new reds vs the 124/125 baseline (item 5 harness)

**Manual checks (if no CLI):**
- Demo container down → item 4a ends as committed repair script + README command + note in the report (do not start services).
- Online-HARD wiring: rig/device proof stays out of scope; correctness rests on the coordinator/host unit tests + the recorded dev-default decision.
