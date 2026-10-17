---
title: 'Story 5.9 — typed KMP client contracts and data layer for competitions'
type: 'feature'
created: '2026-10-17'
status: 'done'
review: 'thorough'
review_source: 'pinned'
baseline_revision: '0e085be7008d2a74b36532dafd7e78781eb843db'
route: 'full'
route_source: 'pinned'
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 0
followup_review_recommended: false
context: []
warnings: []
deferred:
  - summary: >-
      Queue refusals of the permanent-receipt operation retry-forever (core outbox semantic shared
      with Arena): mutationErrorForCode flips Refused to Unknown for permanent ops unless the code
      is invalid-argument, so details.reason never quarantines them.
    evidence: |-
      Verified in FirebaseMutationTransport.kt mutationErrorForCode; the same semantics protect
      lesson_runner.SUBMIT_ATTEMPT (Arena precedent). Fixing it needs a core-level
      reason-aware decision, which this story was forbidden to make.
    location: >-
      platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/network/FirebaseMutationTransport.kt
    severity: medium
---

<intent-contract>

## Intent

**Problem:** The competition server is complete (stories 5.1–5.8) but the client has zero bindings: no DTOs, data sources, or DI for the eight callables (prepare/operate/session/readEvent/readSession/readMeeting/listEvents/recordDownload), no typed error surface for the tournament reason codes, and session mutations do not ride the outbox — the ONLINE-O2 §6 identity/retry machinery that Arena and ordinary already use.

**Approach:** Build the client data layer in a new `shared/feature/tournament/{domain,data}` pair with the Firebase implementations in `platform/firebase/.../tournament/`, mirroring the Arena precedents: domain models + data-source interfaces in domain; strict manual JsonObject wire parsers (exact key-set allowlists, fail-closed — the ArenaTicketWireParser house style, no @Serializable network DTOs) over `mutationResponseJson`-normalized replies; typed `TournamentCallableException(code, details)` reading `details.reason` (never the generic message text) mapped onto the existing `SyncError` hierarchy; session mutations (ANSWER/ADVANCE/READY_ACK) ride the outbox via a new server sink `tournament.SESSION_OP` → `courseTournaments.session` in `MUTATION_HANDLERS` plus the client `OutboxOperations` constant and codec (READY_ACK in `permanentReceiptOperations`); reads/prepare/operate/recordDownload stay direct httpsCallable per the Arena transport precedent; Koin bindings in FirebaseModule + a `tournamentDataModule` registered in AppApplication.

## Boundaries & Constraints

**Always:**
- Redaction by construction: parsers decode only the server's allowlisted projections (safeEvent/safeMeeting/safeSession/safeResponse shapes of controller.js); private forms, keys, fingerprints of unshared material, answer payloads and the organizer link token never appear in any client type or log.
- Identity/retry: every session mutation carries the operationId identity born with the intent; `mutationKey` per OutboxOperations; READY_ACK is a permanent-receipt operation; replay of the same key returns the same receipt (server-guaranteed; the client never invents a second identity).
- Fail-closed decoding: unknown field sets, wrong types or missing required keys are malformed (typed error), never silently accepted; server `details.reason` is read structurally.
- Gradle through the `sgw` wrapper (workspace rule); one heavy build at a time.

**Never:**
- No UI/screens/components (5.10/5.11); no changes to `lesson-question-window.js`, the adopted contracts, Arena/ordinary/exam lifecycles or their parsers (reuse, not fork); no new server collections/rules; no editing of shared/core/outbox semantics (additive registration only); no forked JSON stack.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Read event (both list forms) | fetchCourseTournamentEvent / fetchCourseTournamentEvents replies | Typed domain models mirroring safeEvent (+progress/matches/prizeView rows); v1 and listVersion-0 prize projections both decode | Malformed/extra key sets → typed decode failure |
| Meeting read | readMeeting reply incl. paging cursors | Proposals/confirmations/ready/participants/boundaries decoded; cursors round-trip for the next page | — |
| Session read with window | readSession reply with window + result | Window (windowId/state/expiresAtMs authoritative) + settled result decoded; expiresAtMs is the edge | — |
| Session mutation via outbox | ANSWER/ADVANCE/READY_ACK intent | OutboxEntity enqueued with canonical mutationKey and frozen payload; drain submits `tournament.SESSION_OP`; READY_ACK receipt permanent | Server conflict codes map to typed dispositions (quarantine/conflict per SyncError) |
| Typed refusals | details.reason ∈ {NO_ADMISSION_BASIS, OUTSIDE_QUALIFICATION_WINDOW, CONFLICTING_REPLAY, SCORING_UNAVAILABLE, INCONSISTENT_*…} | Typed domain failures on the SyncError hierarchy with the right dispositions; message text never parsed | Unknown reason → typed unknown, never a crash |
| Record download | create-only server record; repeat returns `replayed` | Decoded; replayed flag surfaced | — |
| Account switch guard | owner+epoch change mid-flight | Existing outbox session guard applies (reuse, no new machinery) | — |

## Code Map

- `shared/feature/lesson-runner/{domain,data}` — the structural template (build files, layering, parser style, outbox codec); `ReadyAckOutboxCodec.kt` + `ReadyAckReceiptParser.kt` — the outbox template.
- `shared/core/outbox/.../OutboxOperations.kt` — operation registry (add `TOURNAMENT_SESSION_OP`), `mutationKey` (:144), `permanentReceiptOperations` (:56).
- `platform/firebase/.../arena/ArenaCallableExchange.kt` + `FirebaseArenaTicketRemoteDataSource.kt` — transport + execute/decode/account-switch precedent; `network/CallableErrors.kt` (timeout, `syncErrorForCode`), `network/MutationResponseJson.kt`, `FirebaseMutationTransport.kt` (outbox transport; envelope shape).
- `functions/course-tournament-controller.js` — the projection shapes to mirror (safeResponse/safeEvent/safeMeeting/safeSession/readMeeting/listEvents/recordDownload, prizeView); `functions/index.js:1588` MUTATION_HANDLERS — add the `tournament.SESSION_OP` sink → `courseTournaments.session` (uid + payload passthrough, server idempotence already enforced by operationId).
- DI: `platform/firebase/.../di/FirebaseModule.kt` (:82-109 binds), `LessonRunnerDataModule.kt` pattern, `apps/android-next/.../AppApplication.kt` startKoin registration.
- Settings: `settings.gradle.kts` shared-feature block — add `:shared:feature:tournament:domain` and `:data`.
- Tests: `platform/firebase/src/test/.../arena/FirebaseArenaTicketRemoteDataSourceTest.kt` (fake exchange + fixtures), `shared/feature/lesson-runner/data/src/jvmTest/` (wire contract tests), `shared/core/outbox/src/jvmTest/OnlineO2IdentityRetryContractTest.kt` — the templates; server fixtures from the Node suites' outputs.
- Evidence: `docs/agents/evidence/2026-10-17-competition-client-data/README.md` — NEW.

## Tasks & Acceptance

**Execution:**
- [ ] `shared/feature/tournament/{domain,data}` + settings includes + build files — domain models and data-source interfaces (domain); strict wire parsers, repository impls, outbox codec (data); DI module (data androidMain).
- [ ] `functions/index.js` -- register the `tournament.SESSION_OP` mutation sink → `courseTournaments.session` with the standard handler shape; `functions/*test.js` cover the sink's happy/replay paths.
- [ ] `platform/firebase/.../tournament/` -- Firebase data sources over `ArenaCallableExchange`-style transport + `TournamentCallableException` reading `details.reason`; FirebaseModule binds; AppApplication registration.
- [ ] Tests -- wire-parser contract tests (every projection, both prize forms, window/result/readyAck, fail-closed on unknown keys), outbox codec/identity tests (mutationKey stability, READY_ACK permanent receipt), Firebase data-source tests with a fake exchange (typed refusals mapping, account-switch guard), Node sink test.
- [ ] `docs/agents/evidence/2026-10-17-competition-client-data/README.md` -- shape mapping (server projection → client model), outbox registration record, gate outputs.

**Acceptance Criteria:**
- Given each server projection shape, when the wire parsers decode a real reply (fixtures mirrored from the Node suites), then the typed model carries every allowlisted field and fails closed on any unknown key-set.
- Given a session mutation intent, when enqueued and drained, then it reaches `tournament.SESSION_OP`, and a replayed drain returns the same receipt without a second effect; READY_ACK receipts are permanent (never quarantined).
- Given each server reason code, when the callable exchange fails, then the client surfaces the typed domain failure with the correct disposition and never the generic message text.
- Given `sgw ./gradlew :shared:feature:tournament:data:jvmTest :platform:firebase:test --no-configuration-cache` and the Node gate `cd functions && node run-tests.js`, then all pass (109/109 Node battery intact; the new modules' JVM tests green).

## Implementation Notes

## Spec Change Log

## Review Triage Log

### 2026-10-17 — Review pass 1 (lenses: blind-hunter 17, edge-case-hunter 10, verification-gap 3 gaps + 3 other, intent-alignment 0 defects)
- verdicts: 33 findings — high 0, medium 5, low 25, false 3
- findings (grouped):
  - `[medium]` `[patch]` (verification-gap gap 1 + blind 5 + intent D4) The review diff was not self-contained: the Firebase transport test, both build.gradle.kts dep lines and the fixture resources existed in the tree but sat outside the staged paths — orchestrator staging fix; the test pins typed refusals, the account-switch fence, malformed-wire typing and no-network exactly as acceptance requires.
  - `[medium]` `[patch]` WireJson optional scalars coerced present-but-wrong-typed values to null (the claimed fail-closed style did not hold); blank status coerced to null — fix applied: present-but-malformed = MalformedWire across text/int/whole/fraction/bit; tests pin it.
  - `[medium]` `[patch]` TournamentSessionOutboxRepository: codec IllegalArgumentException escaped the Result contract, and the frozen-body conflict check ran after the account-switch early return (post-switch conflicts masked as ACCOUNT_CHANGED) — fix applied plus a 4-test repository suite with fake store/session.
  - `[medium]` `[patch]` tournamentSyncError's else-branch swallowed permanent prepare/create refusals (SOURCE_NOT_ELIGIBLE, RESOURCE_EXHAUSTED) as retryable Unknown — fix applied: Refused; new binding test extracts the server's reason set from courseTournamentFailure and asserts every reason is intentionally classified.
  - `[medium]` `[defer]` Queue refusals of the permanent-receipt operation retry-forever (core outbox semantic shared with Arena; fixing needs a core-level reason-aware decision this story was forbidden to make) — deferred to the owner with the evidence note.
  - `[medium]` `[patch]` (verification-gap gap 2) Three fixture members (bracket event read, meeting page, hasMore list page) were hand-shaped while the parsers enforce exact key-sets — replaced with real firestore-stub dumps (real match ids, WAITING_FOR_READY meeting, true hasMore page); docstrings made truthful.
  - `[low]` `[patch]` Receipt-identity asymmetry (no status pin for Answer/Advance) — statuses pinned per intent from responseFor; contract test added.
  - `[low]` `[patch]` Hardcoded MAX_QUESTIONS/MAX_ANSWER_BYTES without drift guard — source-constant assertions added to the contract test.
  - `[low]` `[patch]` KDocs named the wrong callable — corrected to the sink; the two-level cause IO heuristic documented at the call site.
  - `[low]` `[patch]` Node sink test: ADVANCE happy path added (triad fully covered); test-name grammar fixed; fixture file trailing newline added.
  - `[low]` `[patch]` enqueue IllegalArgumentException ordering — merged into the repository fix above.
  - `[low]` `[reject]` Spec gate numbers (109 vs the now-correct 110) and the platform:firebase waiver (2 pre-existing arena drift failures) — fix-would-edit-this-spec; the Auto Run Result and evidence record the actual numbers and the pre-existing attribution.
  - `[low]` `[reject]` Duplicated fail/fixture boilerplate across parsers — cosmetic; hoisting is churn on green code.
  - `[low]` `[reject]` (intent-alignment D5) the whole TOURNAMENT_SESSION_OP permanent rather than per-kind — core semantics frozen by the spec's own Never; KDoc documents the choice.
  - `[low]` `[reject]` (intent-alignment D3) the enqueued-row-to-sink seam untested end-to-end — the core outbox is operation-agnostic and the registry name-match test binds the name; full-stack drain belongs to 5.10 integration.
  - `[low]` `[reject]` Koin wiring test not extended — the graph is unchecked until first production resolution in 5.10; noted for that story.
  - `[false]` `[reject]` «Evidence claims build-file changes absent from the diff» — the changes existed staged; the review diff's path list simply omitted build files — resolved by the staging fix.
  - `[false]` `[reject]` «ADVANCE never exercised» — resolved by the sink-test addition.
  - `[false]` `[reject]` «Fixtures never checked against real server replies» — resolved by the stub dumps.


## Design Notes

House style over convenience: strict parsers and typed detail-reasons are why Arena never leaks server internals. The outbox sink keeps the client's identity/retry story uniform with Arena/ordinary — the server's operationId idempotence is the backstop, the outbox is the transport. Deadline authority note for consumers: `expiresAtMs` is authoritative for window countdowns (windowSeconds is coarse). Reuse `ArenaCallableExchange` composition; do not generalize it in this story.

## Verification

**Commands:**
- `sgw ./gradlew :shared:feature:tournament:data:jvmTest :platform:firebase:test --no-configuration-cache` -- expected: green.
- `cd functions && node run-tests.js` -- expected: 109/109 suites pass (sink included).
- `git status --porcelain -- android/` -- expected: empty (no UI code; apps/ only the DI registration line).

**Manual checks (if no CLI):**
- Evidence README maps each server projection to its client model and records the outbox registration.

## Auto Run Result

- Summary: the competitions client data layer exists — `shared/feature/tournament/{domain,data}`
  with typed mirrors of every redacted server projection, strict fail-closed wire parsers
  (real-server fixtures bound by source hashes), the outbox transport for session mutations
  (server sink `tournament.SESSION_OP`, per-intent receipt status pinning, READY_ACK permanence,
  registry binding tests), the Firebase transport with typed reason→SyncError mapping and a
  reason-vocabulary binding test, the account-switch fence, repository identity guards with
  tests, and full Koin registration. No UI.
- Files changed: 2 new KMP modules (+ settings), shared/core/outbox registration (+registry
  test), functions/index.js sink + new Node sink suite (110/110), platform/firebase tournament
  package (+ build deps, DI binds, AppApplication line), evidence README, this spec,
  local.properties (stale Mac sdk.dir → the VPS SDK path — flagged to the owner).
- Review findings breakdown: 33 findings — medium 5 (staging completeness, WireJson
  fail-closed, repository guards, refusals mapping, fixtures made real — all patched; plus one
  deferred core limitation), low 25 (14 patched across the fix round, 11 rejected as
  spec-edit/cosmetic/delegated-to-5.10), false 3 resolved. Deferred: 1 (queue-refusal
  retry-forever core semantic, owner-visible).
- Follow-up review recommendation: false after the fix round — the round re-verified all gates;
  the residual knowns are recorded (2 pre-existing arena fixture-drift failures in
  platform:firebase, detekt/ktlint baseline, Koin wiring check at 5.10).
- Verification performed: `sgw ./gradlew :shared:feature:tournament:data:jvmTest
  :shared:feature:tournament:domain:jvmTest :shared:core:outbox:jvmTest
  :platform:firebase:test --no-configuration-cache` (JAVA_HOME=/opt/jdks/jdk-27) → 166 JVM tests
  green; platform:firebase 295 tests with exactly the 2 pre-existing failures; `cd functions &&
  node run-tests.js` → 110/110; ktlint green on touched modules; `git status --porcelain --
  android/` empty (DI registration line in apps/ only).
- Residual risks: pre-existing arena fixture-hash drift (owner item); detekt/ktlint baselines
  (owner items); queue-refusal retry-forever core semantic (deferred, owner-visible); Koin graph
  first resolved at 5.10.
