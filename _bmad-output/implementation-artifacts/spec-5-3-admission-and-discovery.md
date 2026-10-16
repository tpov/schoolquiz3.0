---
title: 'Story 5.3 — UX-O4/access admission adapter (downloaded course OR link) + authorized event discovery'
type: 'feature'
created: '2026-10-16'
status: 'done'
review: 'thorough'
review_source: 'pinned'
baseline_revision: '96a9d23c252aee5204ce5a8bc92e90462d732179'
route: 'full'
route_source: 'pinned'
review: ''
review_source: ''
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 0
followup_review_recommended: true
context: []
warnings: []
deferred:
  - summary: >-
      Runtime rules-engine probe (403 for clients) of course_tournament_links,
      course_tournament_downloads and the admissions subcollection is pending demo-rig
      availability; only source-text checks executed during the story.
    evidence: |-
      The probe file scripts/course-tournament-private-rules.test.js was extended with both
      collections and the admissions subcollection, but it requires the demo emulator hub
      (schoolquiz-mvp-firebase:4400), which was down for the whole story; the executed check is
      the source-text tournament-storage-contract test. What settles it: bring the rig up and
      run the probe.
    location: >-
      firestore.rules:172-176; scripts/course-tournament-private-rules.test.js
    severity: medium (unverified)
---

<intent-contract>

## Intent

**Problem:** The UX-O4/access gate is settled but unimplemented: nothing today decides WHO may attempt a course tournament — any signed-in user can START_QUALIFICATION any event inside the window (`tournament-service.js:200-224` has no eligibility check); "downloaded course" exists only as client-local Room state with no server record; competition links do not exist; and there is no list read of events (only by-eventId fetch). The gate requires the server to confirm exactly one admitted basis — downloaded corresponding course OR valid competition link — bound to account+courseId+tournamentId, only inside `[qualification.startsAtMs, qualification.endsAtMs)`, with late requests and retries creating no new admission, the link never bypassing championship selection, and earned championship rights surviving link expiry — plus an authorized paginated event-discovery read.

**Approach:** Implement server-side admission and discovery in `functions/`: a pure `planAdmission` policy consumed by the START_QUALIFICATION branch; a server-attested download record written by a new callable at course-download time; a link token minted at CREATE (digest stored, token returned only in the organizer's CREATE response); admission persisted as an event subcollection doc; a new `fetchCourseTournamentEvents` discovery callable with the worker's paging shape and a redacted allowlist; both new top-level collections added to the rules deny block; Node unit suites extended (existing START tests updated to seed admission — an expected, story-caused test change).

## Boundaries & Constraints

**Always:**
- Server authority: the admission basis is confirmed from server-owned records only (download record / link digest) — a client-claimed download or a link string alone is never permission; the qualification window check stays the sole time gate and admission is evaluated inside the same server-ordered flow.
- Idempotence on existing discipline: same `operationId` replays the old receipt with no new writes; admission doc per `(eventId, uid)` is create-only inside the window; a repeated admission returns the existing record's outcome.
- Selection integrity: admission is consulted ONLY at START_QUALIFICATION; CLOSE_QUALIFICATION/ranking/bracket never read it — the link cannot bypass selection and earned championship rights survive any link state (structural, already true via the freeze).
- Redaction: discovery and every new read projection expose only allowlisted fields — never `serverSeed`, `privateBankFingerprint`, `poolFingerprint`, `qualificationForms`, question banks, or the link token; the token appears only in the organizer's CREATE response (receipt replay recovers it for the organizer only).
- New top-level collections (`course_tournament_links`, `course_tournament_downloads`) are added to the `firestore.rules` deny block; the rules probe extends to cover them.

**Never:**
- No organizer whitelist, no manual participant lists (AD-17 forbids); no new expiry concept for links — link validity IS the qualification window interval (contract-map "UX-O4/access — решено"); no rotation/revocation features.
- No admission semantics for bracket/match stages; no new product surfaces beyond the two bases; no direct client writes to any new collection.
- No changes to prize-list, timing, or escrow behavior; existing suites stay green except the explicitly listed START tests updated to seed admission.

**Deliberate reading (owner-visible, flagged in evidence):** for `source.kind === "ARENA_LESSON"` events there is no corresponding course to download; the gate's disjunction then has exactly one satisfiable basis, so those events are link-only — organizer distributes the token; download records do not apply. This reading is documented in code comments and the evidence note, not silently chosen.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Admitted via download | COURSE event, server download record `(uid, courseId = event.source.id)` exists, in window | START proceeds; admission doc created (`basis: COURSE_DOWNLOAD`) | Retry replays receipt, no new admission |
| Admitted via link | Link token presented, digest matches event's stored digest, in window | START proceeds; admission doc created (`basis: LINK`) | Wrong/missing token → refused, no admission doc |
| No basis | Neither record nor valid token | START refused with a typed admission error | No entry/session staged |
| Late request | Any basis, outside `[startsAtMs, endsAtMs)` | Refused `OUTSIDE_QUALIFICATION_WINDOW` (existing reason) | No admission doc; retry after expiry creates nothing |
| Record download | Client completes a course download, calls the record callable | Server-attested record upserted per `(uid, courseId)`; idempotent on repeat | Foreign uid impossible (auth); bad courseId shape → typed error |
| Discovery paging | `fetchCourseTournamentEvents` with cursor/pageSize | Bounded page of redacted events + `hasMore`/`nextEventId`, mirroring readMeeting's cursor discipline | pageSize bound (≤100) enforced; malformed cursor → typed error |
| Discovery redaction | Any page | Only allowlisted fields; no seed/fingerprints/forms/token | — |
| Championship survival | Link state after qualification ends; qualifier reaches bracket | Bracket participation unchanged (selection from the freeze) | — |

## Code Map

- `functions/tournament-service.js` -- START block :200-224 (entry :201, session :202, attempt-binding guard :204-207, `planQualificationAttempt` :208-210, status gate :215, clamp :218-220); receipt replay :148-155; CREATE :165-195 (event doc :188-192, `source {kind,id,snapshotId}` :55-57, escrow :192-195); `eventView` :85; readEvent paths :437-455; `finish` :165-177; freeze :111-127.
- `functions/tournament-policy.js` -- window check :158-160 (`OUTSIDE_QUALIFICATION_WINDOW`); `planQualificationAttempt` :146-157; `preflightPool` source kinds :47-56 (`ARENA_LESSON` requires `admitted`); helpers `int/safe/str/obj/arr/add` :33-56; `key()` discipline (idempotence).
- `functions/course-tournament-controller.js` -- `operate` auth/whitelist :130-137; `readEvent` :169-178; `readMeeting` paging pattern :179-205 (cursor `afterOfferId`, `pageSize` ≤100, `hasMore`/`nextOfferId`); `safeEvent` redacted view :86-94; controller op-id reserves :37-40.
- `functions/index.js` -- callable wiring :345-395 (`callCourseTournament` dispatch :378-394); add two new exports on the same pattern.
- `functions/tournament-worker.js:88-91` -- the paging query shape to reuse for discovery (`status in [...] + orderBy("__name__") + startAfter + limit(n+1)`).
- `firestore.rules:158-169` -- course_tournament deny block; add the two new collections.
- `scripts/course-tournament-private-rules.test.js` -- rules probe pattern (403 for all course_tournament_* collections; emulator hub check) — extend for the new collections.
- Test harnesses: `functions/tournament-service.test.js` (premiumTestDb service-level, START window/replay at :176-186 — update to seed admission), `functions/course-tournament-controller.test.js` (pagedDb controller-level, START at :115-147 — update + discovery paging tests), `functions/course-tournament-handler.test.js` (real index.js with stubbed admin — wire new callables).
- `shared/feature/quest/.../QuestRepository.kt:74`, `QuestDao.kt` -- client-local download semantics (context: why a server attestation callable is needed; the client-side call wiring itself is story 5.10, NOT this story).
- `_bmad-output/specs/spec-schoolquiz3.0/contract-map.md` -- UX-O4/access gate row (the binding text) and AD-17 admission rules.

## Tasks & Acceptance

**Execution:**
- [ ] `functions/tournament-policy.js` -- add pure `planAdmission({event, uid, nowMs, downloadRecord, linkTokenDigest})` returning allowed/basis/reason, enforcing the window and the one-basis rule (COURSE: download record for `event.source.id`; ARENA_LESSON: link only — flagged reading); unit tests in `functions/tournament-policy.test.js`.
- [ ] `functions/tournament-service.js` + `functions/index.js` + `functions/course-tournament-controller.js` -- enforce planAdmission in START_QUALIFICATION (transaction reads the download record/link digest, writes the admission subcollection doc create-only); mint the link token at CREATE (digest on a new link doc, token in organizer's CREATE response only); add the record-download callable and the `fetchCourseTournamentEvents` discovery callable (paging per readMeeting/worker pattern, redacted allowlist); extend handler/controller tests.
- [ ] `firestore.rules` + `scripts/course-tournament-private-rules.test.js` -- deny the two new collections; extend the probe.
- [ ] `docs/agents/evidence/2026-10-16-competition-admission/README.md` -- what was built, the ARENA_LESSON link-only reading (owner-visible), test outcomes, demo-container status (run http/rules probes if the demo container is up; otherwise record them as pending demo/rig evidence honestly).

**Acceptance Criteria:**
- Given any signed-in user, when START_QUALIFICATION runs without a server download record or a valid link token, then it is refused with a typed admission error and no entry/session/admission doc exists.
- Given a server download record for the event's course (or a valid link token), when START runs inside the window, then the attempt proceeds and the admission doc records basis and timestamps once; a retried operation replays the same receipt with no new writes.
- Given the qualification window closed, when any admission or START is attempted, then it is refused `OUTSIDE_QUALIFICATION_WINDOW` and no admission doc is created; a pre-window qualifier's bracket participation is unaffected by later link state.
- Given `fetchCourseTournamentEvents`, when a client pages events, then pages are bounded and ordered with a working cursor, and no redacted field (seed, fingerprints, forms, banks, link token) appears in any response.
- Given the full Node suite (`node --test` over the touched functions test files) runs, then all cases pass including the updated START tests, the new policy/controller/handler cases, and the extended rules probe file compiles (probe execution recorded per container availability).

## Implementation Notes

## Spec Change Log

## Review Triage Log

### 2026-10-16 — Review pass 1 (lenses: blind-hunter 16, edge-case-hunter 4, verification-gap 2 gaps + 1 other, intent-alignment 0 defects)
- verdicts: 23 findings — high 1, medium 6, low 11, false 5, maybe-false 0
- findings:
  - `[high]` `[patch]` The START admission gate silently breaks all three demo-rig acceptance flows (course-tournament-http player START has no download record and its endpoint allowlist excludes the new callables; both mvp-qualification-* scripts bulk-START without admission seeding and pin stale source hashes) — verification-gap lens traced every consumer and ran the suite; fix applied: http test extends the scoped endpoints and records the download before START, both qualification scripts seed course_tournament_downloads for the cohort and re-pin the controller hash; all suites green again (109/109).
  - `[medium]` `[patch]` planAdmission fails open on corrupt window timestamps (no int() validation; undefined/NaN compares false → admitted on basis alone) — verified by reading the guard; fix applied: int() guards on startsAtMs/endsAtMs.
  - `[medium]` `[patch]` recordDownload accepts any non-blank courseId and never checks the course exists (junk attestations accumulate; the basis weaker than intended) — fix applied: `quests/{courseId}` existence check (INVALID_ARGUMENT otherwise) plus an honest trust-model note in the evidence README (server-attested client claim is what UX-O4/access makes permission-bearing; stronger byte-level verification does not exist in the architecture).
  - `[medium]` `[patch]` (edge-case-hunter, same defect) missing course-existence check — grouped, shares route.
  - `[medium]` `[patch]` (orchestrator bookkeeping) spec frontmatter still ready-for-dev with empty review fields while the diff ships the implementation — fix applied at finalize: in-review fields, lenses, and this log written by the orchestrator.
  - `[medium]` `[defer]` Runtime rules-engine verification of the new collections/admissions subcollection never executed (probe is rig-only; demo container down all story) — deferred with location and settling condition (run the probe on the rig); source-text storage-contract check executed.
  - `[low]` `[patch]` Discovery query dropped the worker's status-in filter (spec Code Map cites the worker shape; FINISHED/CANCELLED events listed) — fix applied: `where("status","in",[QUALIFICATION,BRACKET])` added, test double extended to support the in-operator.
  - `[low]` `[patch]` listEvents wrapped a pure read in db.runTransaction (contention on a public endpoint) — fix applied: plain paged query, same cursor discipline.
  - `[low]` `[patch]` recordDownload's operationId "participates in nothing" (repeat returns the stored outcome without marking it) — fix applied: repeat responses carry `replayed: true`; handler/controller assertions updated.
  - `[low]` `[patch]` Admission error surface undocumented for the 5.9/5.10 client stories — fix applied: evidence README carries the code+message catalog.
  - `[low]` `[patch]` Evidence README test accounting ambiguous and two typos ("новою", "endpoинт") — fix applied.
  - `[low]` `[reject]` Spec Code Map anchors stale/overlapping after the diff's insertions — navigation-only, the deliverable is the code; the fix would edit this build's spec — rejected by rule.
  - `[low]` `[reject]` The Never-clause "existing suites stay green except listed START tests" was narrower than the mechanically required hash re-pin in arena-lifecycle-views-handler.test.js — the change is story-caused, documented in the evidence README, and the fix would edit this build's spec — rejected by rule.
  - `[low]` `[reject]` Template drift (empty Implementation Notes) — fix would edit this build's spec; decisions are recorded in the evidence README — rejected by rule.
  - `[low]` `[patch]` (edge-case-hunter) download-record schemaVersion mismatch fails "UNAVAILABLE" — verified NOT a defect: the wrapper maps UNAVAILABLE and every INCONSISTENT_* prefix to the typed unavailable HttpsError (index.js:396); no change needed — routed to reject-with-refutation, no patch required.
  - `[false]` `[reject]` "Repeat CREATE with a new operationId hits an unspecified link-doc failure mode" — refuted: the pre-existing `if (event) fail("ALREADY_EXISTS")` guard (tournament-service.js:193) precedes all staging, so the link stage never runs for an existing event.
  - `[false]` `[reject]` "Corrupt-record repair errors presume a runbook" — refuted: fail-closed typed errors for manually corrupted states are the established house discipline (INCONSISTENT_ESCROW precedent, 5.2 D9).
  - `[false]` `[reject]` "Token-mixing combinations untested (cross-event token; both bases)" — refuted: the service suite refuses a valid-format non-matching token (tournament-service.test.js:220) and the policy suite pins download-over-link precedence (:107-108); per-event digest lookup makes the cross-event case identical to the non-matching case.
  - `[false]` `[reject]` "Plain digest equality instead of timingSafeEqual" — refuted: house style compares server-stored digests with === everywhere (receipt fingerprints); the secret is 192 bits of server-minted randomness and the comparison runs server-side in a transaction.
  - `[low]` `[reject]` (verification-gap other) qualification-freeze/scan fixtures would need admission seeding if reused for in-window STARTs — informational; current behavior correct (window check fires first), no action in this change.


## Design Notes

Admission is evaluated inline at START (the only entry path) — no separate admission callable; the admission doc is the durable receipt of the check. The link token is a server-minted secret (digest stored on the event's link doc); discovery intentionally does NOT make knowing the eventId sufficient — the token is the out-of-band secret the organizer distributes. `viewerAdmitted`-style projections are story 5.9/5.10 client surface; this story adds no client code. Existing START tests fail after enforcement by design — seed admission in fixtures, do not weaken the check.

## Verification

**Commands:**
- `cd functions && /root/.zcode/server/node --test tournament-policy.test.js tournament-service.test.js course-tournament-controller.test.js course-tournament-handler.test.js` -- expected: all pass (including updated START fixtures and new admission/discovery cases).
- `git status --porcelain -- shared/ android/` -- expected: empty (server-side story; no client code).

**Manual checks (if no CLI):**
- Rules file contains the two new deny lines; the rules-probe file lists them.
- Evidence README names the ARENA_LESSON link-only reading and the demo-container probe status honestly.

## Auto Run Result

- Summary: implemented the UX-O4/access admission gate and authorized event discovery
  server-side: pure `planAdmission` policy (window first, exactly one basis — attested download
  record for COURSE events, link digest otherwise; ARENA_LESSON events are link-only, flagged
  as an owner-visible reading); START_QUALIFICATION enforces it in-transaction and stages a
  create-only admission doc per (event, account); CREATE mints a one-time link token (digest
  stored on `course_tournament_links/{eventId}`, plaintext only in the organizer's response,
  recoverable via receipt replay); `recordCourseTournamentDownload` upserts create-only
  server-attested download records with a course-existence check; `fetchCourseTournamentEvents`
  pages active events with the worker's cursor shape over a redacted allowlist; both new
  collections denied in firestore.rules with the probe extended.
- Files changed: `functions/tournament-policy.js` (+planAdmission, int guards), `functions/tournament-service.js`
  (admission enforcement, link minting, paths), `functions/course-tournament-controller.js`
  (listEvents, recordDownload, admissionLink passthrough), `functions/index.js` (two new
  callables), `firestore.rules` (two deny lines), `functions/*test.js` (new + updated cases),
  `functions/arena-lifecycle-views-handler.test.js` (export-set hash re-pin, story-caused),
  `_firestore-stub.js` (in-operator support), `scripts/course-tournament-http.test.js` +
  `scripts/mvp-qualification-*-test.js` (rig flows adapted to the gate, hash re-pin),
  `scripts/course-tournament-private-rules.test.js` (probe extended), evidence README,
  this spec.
- Review findings breakdown: 23 findings — high 1 (rig flows broken by the gate; patched),
  medium 6 (NaN fail-open, recordDownload existence check, orchestrator frontmatter, rules
  probe deferred, two grouped duplicates), low 11 (4 patched: status filter, plain query,
  replayed flag, README catalog/typos; 4 rejected as spec-edit or informational; 3 refuted
  count under false), false 5. Deferred: 1 (runtime rules probe on the rig). No bad_spec, no
  intent_gap.
- Follow-up review recommendation: TRUE — the high finding's rig-script patches and the
  discovery/recordDownload changes were applied by the orchestrator with full-suite re-verification
  (109/109) but without a fresh lens pass; the deferred rules probe adds an unexecuted runtime
  check that a follow-up pass (or the next rig run) should close.
- Verification performed: full functions suite `node run-tests.js` → 109/109 pass (before
  patches, after patches); the four story suites policy/service/controller/handler green with
  new admission/link/discovery/redaction cases; `git status --porcelain -- shared/ android/`
  empty; all three modified rig scripts pass `node --check`; frontmatter YAML valid.
- Residual risks: rules probe pending the demo rig (deferred); download basis is a
  server-attested client claim (trust model documented); pre-story events have no link doc
  (link basis unavailable to them); client wiring is stories 5.9/5.10.
