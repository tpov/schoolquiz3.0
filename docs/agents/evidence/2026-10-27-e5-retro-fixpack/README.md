# Epic 5 retro fix pack — proof of run (2026-10-27)

Spec: `_bmad-output/implementation-artifacts/spec-e5-retro-fixpack.md` (baseline `4e2ca56c0963289baf7ea67116b02a1cd914eebf`,
branch `night/e5-retro-fixpack`, worktree `.local/worktrees/e5-fixpack`).
Resumption of the blocked 2026-10-25 run: the 25.10 WIP in `functions/` (tasks 1–3: L9, L2, L5, L7, F3)
was audited against `tournament-source-bank.js` collect (the L5 predicate mirrors it: `visibleOn`
archive publication, version ≥ 1 safe integer, catalogId shaped like the bank's `id()`, live
unarchived catalog with COURSE quest type; authorization deliberately excluded), completed and
turned green; tasks 4–13 implemented fresh in this run.

## Gates actually executed (2026-10-27, VPS rnsrv)

1. **functions suite** — `cd functions && /root/.zcode/server/node run-tests.js`
   → **128 of 129 suites passed; the sole red is `mvp-isolation.test.js`** — the known
   pre-existing owner-tracked red (cleartext LAN-IP from 7d8b91bd, competitions 5.13), attributed and skipped per the wave brief.
2. **Kotlin gates** — `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :shared:feature:tournament:data:jvmTest
   :android:feature:tournament:presentation:test :platform:firebase:test --no-configuration-cache`
   → **BUILD SUCCESSFUL** (403 actionable tasks). jvmTest covers the L3 boundary fixture +
   hash pins (now including `tournament-policy.js`); presentation tests cover L1/L6 (ACK on the
   delivered window, OPEN-window negative, server-clock re-arm with floor/backoff), P2 wire
   shapes and L4 token state; firebase tests cover L8 (fail-literal greps over six modules) and
   the L2 live-path mapping. Note: Gradle needs `JAVA_HOME=/opt/jdks/jdk-27` on this box —
   buildSrc's `jvmToolchain(27)` is not auto-discovered (the toolchain-wide quirk attributed in 4.6–4.9).
3. **Syntax gate** — `node --check scripts/course-tournament-lifecycle-demo.test.js` → OK.
4. **Matrix test audit** — every I/O & Edge-Case row has a covering test that ran green:
   rows 1 (16-row boundary) in `TournamentEventWireParserTest`; row 2 (named admission refusal)
   in `tournament-service.test.js`/`course-tournament-controller.test.js`/`-handler.test.js` +
   transport table + `TournamentRefusalsBindingTest`; row 3 (ACK on DELIVERED, no ACK on OPEN)
   in `DefaultTournamentQualificationComponentTest` (positive + new negative) and the server
   half in the rig probe (see below); row 4 (re-arm floor/backoff, no zero-delay loop) in the new
   `an expired open window rearms the read on the server clock with floor and backoff`;
   row 5 (`SOURCE_NOT_ELIGIBLE` + existing-document legacy path) in
   `course-tournament-controller.test.js`; row 6 (degraded prizes → parseable v0) in the same file;
   row 7 (attestedDownload schemaVersion ≠ 1 → false) in `tournament-policy.test.js`.

## Rig (live) — sink mechanics proven; full-lifecycle PASS blocked by shared-rig instability

`e5-sink-probe.js` (in this folder, as executed) drove the real callable code over the firestore
stub: CREATE minted the admission link, START with that token admitted a player with no download
record, the ANSWER went through `submitMutation` (`tournament.SESSION_OP`) and returned
`ANSWER_ACCEPTED` with the window **nested in `session`, state `DELIVERED`**
(`win_928214e3b08e`, nextQuestionIndex 1), and the READY_ACK through the same sink returned
`READY_ACK` with the receipt triple (`questionIndex 1`, matching windowId,
`ackedAtMs ≤ answerDeadlineMs`). This is the L1 server half and the L4 receipt shape, proven live.

The full `course-tournament-lifecycle-demo.test.js` run (now carrying the retro-item-10 sink
phase: CREATE → link-token START → sink ANSWER/READY_ACK → worker pass leaves the next window
OPEN → second pass FORCED_CUTOFFs at the captured window edge → event cancels with full refund)
did **not** reach PASS in this session, four attempts:

- run 1 — my phase used a synthetic (non-Auth) account for the sink HTTP call → UNAUTHENTICATED
  (harness bug, fixed: real player);
- run 2 — FAILED_PRECONDITION at the sink ACK: the post-cutoff phases run on the rig's logical
  clock while the emulator serves real-clock code, so the HTTP-driven sink judged the future-dated
  session untimely (harness bug, fixed: `submitMutation.run` drained in-process under the logical
  clock — the same entry point under the same injected clock, per the file's own convention);
- run 3 — died BEFORE the sink phase at the main-event bracket close on a firestore-emulator
  `2 UNKNOWN` transaction flake (this phase had passed in runs 1–2);
- run 4 — died at preflight on `DEADLINE_EXCEEDED` to the firestore emulator while
  `schoolquiz-functions-emu` burned ~125–155% CPU (hot-reload churn from the sibling agent's
  active edits to the mounted main worktree, which the emulator watches).

The emulator is shared, 7 days up, and mounts the main worktree (sibling `night/e7-commerce`
wants its own dirty `functions/`); restarting it mid-wave is an owner decision, so the live
full-lifecycle rerun is left as an open item rather than forced through a degraded rig.

## Notes for the next reader

- The four `.gitignore`d worktree conveniences are not committed: the `functions/node_modules`
  symlink (points at the main worktree's install; package.json identical across branches) and the
  local fixture-dump script lived in /tmp.
- `tournament-client-fixtures.json` gained `eventReadPrizeBoundary16` — dumped from the real
  controller over `_firestore-stub` (16 rows, places 1..16, liability 22) — and refreshed
  sourceSha256 pins for `course-tournament-controller.js`, `tournament-session.js` and the newly
  pinned `tournament-policy.js`.
- ktlint: a module-wide `ktlintFormat` run auto-corrected FOREIGN main-source files (economy,
  exam, arena, network — neighbors' surfaces); all such collateral was reverted via git checkout;
  the only remaining known ktlint red is the pre-existing uncorrectable line in
  `FirebaseGiftClaimRemoteDataSource.kt:104` (not touched by this branch).
