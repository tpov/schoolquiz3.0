# Functions contract drift (story 2.13)

- Result: DONE. Both chartered pre-existing red functions suites are green, and with them the
  whole functions gate: **92 of 92 suites passed** both without the emulator env and against
  the live demo backend — the first fully green functions gate on this branch.
- Date: 2026-10-08.
- Story: `epic-ordinary-delivery` 2.13 `functions-contract-drift` (no CAP; branch hygiene) —
  chartered by the owner's 2026-10-08 decision (owner-decisions-2026-10-08.md, point 3); the
  two suites were attributed as pre-existing in the 2.8 evidence (2026-10-07).

## The two chartered suites and what was actually wrong

- `arena-lifecycle-views-handler.test.js` — 1 of 6: the pinned SHA-256 of the accepted
  callable-surface set (`ACCEPTED_NON_LIFECYCLE_EXPORT_SET_SHA256`, pinned 2026-09-13 in
  `99091eb5`) no longer matched. Attribution: story 2.6 (`1b207cc2`, 06.10) legitimately added
  the two ONLINE-O2 next-question callables — `beginLessonQuestionWindow` and
  `fetchNextLessonQuestion` (verified by diffing the export set against the pin commit; two
  additions, zero removals) — and nobody re-pinned the guard. Fix: re-pin to the actual set
  with the attribution comment naming both callables and both commits. Test-only.
- `lesson-ready-ack-handler.test.js` — 3 of 8: TWO stacked defects, one in the harness and one
  real, and the first masked the second:
  1. **Harness (test-only):** `withFixedClock` restored the real clock synchronously, while
     every callable under test is `onCall(async …)` whose body resumes on microtasks — the
     server moments under test were the wall clock, so the window/deadline assertions failed
     against real time. Fix: the helper awaits `fn` inside the clock window; every call site
     already awaited it. The production clock use is `Date.now()` at the transaction — exactly
     what the contract wants.
  2. **Production (one line, real bug born with 2.6):** with the harness fixed, the
     window-expired path crashed with `ReferenceError: WINDOW_EXPIRED is not defined`
     (`functions/index.js`, `fetchNextLessonQuestion`): the receiver destructured
     `WINDOW_OPEN` from `lesson-question-window.js` but used bare `WINDOW_EXPIRED` (the
     constant lives — and is exported — in the module). The expiry closure had never run
     green: the suite shipped with 2.6 and 2.6's gate covered the JVM half only. Fix: import
     the constant. The «window expired without delivery closes the attempt» case now proves
     the §7 closure it always meant to prove.

## Gates

- `node functions/arena-lifecycle-views-handler.test.js` → 6/6.
- `node functions/lesson-ready-ack-handler.test.js` → 8/8.
- Full `npm test` twice, logs in this directory:
  - `gate-no-env.log` — no emulator env: **92 of 92 suites passed**;
  - `gate-demo-backend.log` — `FIRESTORE_EMULATOR_HOST=127.0.0.1:18090`,
    `GCLOUD_PROJECT=demo-schoolquiz-mvp` (live demo container): **92 of 92 suites passed**.
- Post-run demo hygiene (same check as 2.8): the demo's own 23 `mutation_keys` intact, 0 probe
  keys, no probe profiles, `configs/economy` untouched (absent — read-only for the terms
  suite). Note: the demo container (`schoolquiz-demo-firebase-20261004`, no restart policy by
  the owner's 06.10 choice) was DOWN when this shift started and was raised with
  `docker start` per the same standing decision; it stayed up through both gate runs.

## Third pre-existing red family found by the 2.12 gate (NOT fixed here — owner follow-up)

The 2.12 module gate surfaced two more stale-pin tests in `platform:firebase` (JVM, not
functions): `FirebaseArenaTicketRemoteDataSourceTest
.fixtureHashesAndCallableNamesAreBoundToActualServerSources` and
`FirebaseArenaLessonCatalogRemoteDataSourceTest.generatedPublicContractAndCallableNameRemain
BoundToActualProducer`. The fixture JSONs (`arena-ticket-client-fixtures.json` from `5d006688`,
2026-09-07; the catalog sibling) pin the SHA-256 of the JS producers, and `34fe664b`
(2026-09-12, «share eligible lesson universe») changed the producers without regenerating the
captures. This is deliberately not fixed by hand: the pins guard captured payload fixtures,
and the capture tooling is not in the repository (the fixture's `producer` field is a prose
note; the 09-07 capture session tooling stayed session-local) — rewriting the recorded hashes
would launder a real producer change without regenerated payloads. **Owner follow-up:**
regenerate the two arena fixture JSONs with the capture procedure, then re-pin; until then
`platform:firebase:test` stays 90-of-92-style red on exactly these two tests (everything else
in the module is green, including the 2.12 wiring).

## Deliberate consequences

- The 2.6 production fix is one import line; no client change is needed (the client never saw
  the crash — the server path was broken, and the client's JVM contract tests mock the wire).
- The arena hash re-pin means the next callable addition will fail the same guard until
  re-pinned with attribution — that is the guard working as designed.
