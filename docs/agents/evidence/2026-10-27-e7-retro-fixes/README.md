# e7 retro fix-pack — wire-contract pin and the claim loop's exits — 2026-10-27 (UTC)

Tree-only fix-pack from the epic-commerce retrospective (2026-10-25, accepted-with-open-items).
Spec: `_bmad-output/implementation-artifacts/spec-e7-retro-fixes.md` (route full, review standard).
Baseline: spec time `1e793122`; resume base `44fc0f83` (git index repaired 25.10, tree moving).
Production `functions/` code untouched — the pack changes server files ONLY by tests.

## What was proven (gates run from this tree)

- **F7 — functions gate green on HEAD.** `mvp-isolation.test.js` cleartext pin caught up to the
  committed NSC: loopback strictly pinned, externals exactly `["204.44.66.27", "192.168.31.218"]`
  with provenance (a2298084 VPS hitl host / 7d8b91bd LAN overlay 5.13, merged ed16fc81). Note: the
  spec's provenance listed `a2299284`, which does not exist in the repo — the VPS-host commit is
  `a2298084`; the comment cites the existing hashes.
- **F1a — full envelope pin.** `economy-handler.test.js` pins the exact 12-key set of the claim
  `result` (derived from `planBoxOperation` construction, `functions/box-service.js:205-208` +
  `strictBoxState`) on CLAIMED / DAILY_LIMIT / VISIT answers, plus `typeof replayed === "boolean"`.
  Dropping/renaming any field now reddens the server suite instead of a live device.
- **F1b — client-side SHA pin.** New `BoxServiceSourcePinTest` (platform/firebase) pins
  sha256 of `functions/box-service.js` per the arena precedent
  (`FirebaseArenaLessonCatalogRemoteDataSourceTest`), with a re-pin journal in the test body.
  Editing the server envelope source without re-reading it client-side is no longer silent.
- **F2 — premium-gift re-arm.** `DefaultHomeQuestsComponent` re-arms a lying premium intent on
  `NetworkMonitor.observeOnline()` (no attempt while offline) and on `doOnResume` (timeout case);
  double dispatch is guarded by the single in-flight job. Tests: offline failure → reconnect →
  settle with no new operationId; failed resume → screen re-entry → settle.
- **F3 — streak base without boxCount.** `SyncBoxStreakUseCase` compares the local base to the
  server word by `(streakDays, nextBoxAtMs)` only; the day-10 auto-grant (boxCount-only shift) no
  longer reseeds an honestly counted offline streak. Test: auto-granted profile + 3 offline days →
  OFFLINE_STREAK (12, 2) rides before VISIT, base verbatim.
- **F4 — CONFLICTING_REPLAY is terminal.** `GiftClaimRepositoryImpl` resolves the intent on the
  fence refusal (decision for THIS body never exists) and rethrows typed; `SyncBoxStreakUseCase`
  continues the entry pass with VISIT. The old `conflictingReplayKeepsTheIntent…` test is
  inverted and renamed. Use-case test: CONFLICTING_REPLAY no longer blocks the visit.
- **F5a — terminal statuses re-read the profile.** DAILY_LIMIT / PREMIUM_REQUIRED now run
  `ensureCurrentProfile()`; the button redraws from the fresh server word instead of minting a
  new operationId per tap. Repository test asserts refreshes == 2 across both statuses.
- **F5b — both directions of the money invariant pinned by type.** New distinct
  `GiftClaimFailureReason.REQUEST_INVALID` for PRE-CALL body-refusal (`giftClaimRequestFrom`):
  the request never left the device, no decision can exist → intent quarantined (released).
  Response-side refusal stays `ENVELOPE_INVALID`: the server may have decided → intent kept,
  verbatim replay. Parser helpers throw typed; no bare `error()` on the request path. Tests:
  mapping (REQUEST_INVALID ×6 shapes, ENVELOPE_INVALID on a broken answer) + repository
  (corrupted body releases / response ENVELOPE_INVALID keeps + replay settles).
- **F6 — dual-shape completion records.** `openingFromReceipt` accepts both the flat `result`
  body (post-c2c1a414 records) and the pre-conversion plan-nested `result.response`; new records
  stay flat. Mapping test: flat and plan-nested parse to identical `GiftBoxReceipt`.
- **D1–D3 — documents to the recorded reality.** `spec-epic-7-commerce.md`: "No server changes"
  / "Server untouched" retracted and replaced with the c2c1a414 record (retro item 11); deferred
  premiumUntilMs-wipe moved to resolved (fixture error, `wire/premium-grant-fixture.js` recipe);
  Spec Change Log entry added. `epic-7-context.md:56`: ADR note direct-callables-vs-outbox from
  the spec's Design Notes. `tickets.toml` 7.1/7.2/7.3/7.6: owner acceptance wording 2026-10-25
  appended to status comments; `status =` fields untouched (owner decides at tail-closing).
  `sprint-status.yaml` → `sprint-status-legacy-sync-2026-09.yaml` via `git mv` + one explanation
  line (retro item 12); no semantic yaml edits; no live tracker created here.

## Gates (exact commands, run 2026-10-27)

- `docker exec schoolquiz-functions-emu env -u FIRESTORE_EMULATOR_HOST -u GCLOUD_PROJECT npm --prefix /repo/functions test`
  → **135 of 135 suites passed** (no-env). The no-env run mounts the repo read-only at /repo.
- `docker exec schoolquiz-functions-emu npm --prefix /repo/functions test`
  → **135 of 135 suites passed** (demo-backend env of the container: FIRESTORE_EMULATOR_HOST=127.0.0.1:18090,
  GCLOUD_PROJECT=demo-schoolquiz-mvp).
  Suite count grew past the spec's expected 129 via sibling arena test files (allowed: growth by
  new files, not failures; the arena files are foreign in-flight work, untouched).
- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :platform:firebase:testDebugUnitTest :shared:feature:economy:domain:test :shared:feature:economy:data:test :android:feature:quest:presentation:test :android:feature:app-shell:presentation:test :shared:core:persistence:jvmTest --no-configuration-cache`
  → result recorded in the spec's Verification block and the final commit message.
- First gate run failed to COMPILE the new `GiftBoxReceiptMappingTest` case (JUnit4
  `assertEquals` argument order) — fixed, re-run green; recorded here for honesty.

## Deliberately NOT_RUN (external or out of scope by owner decision)

- **Silent-Buy UX** (retro item 8, tickets 7.1/7.6): billing-unavailable/no-account Buy feedback —
  its own story, not this pack.
- **x2 regeneration** (`premiumRegenDivisor`): versioned owner decision (retro item 9).
- **Play/AAB 40001 actions**: owner uploads; no outward-facing actions from this tree.
- **Device walk of the claim loop** (re-arm/reconnect on hardware): device not attached; the
  fixes are covered by component/repository/use-case JVM tests above.
- **detekt toolchain-wide**: pre-existing environmental red (jvm-target 27), as in 4.6–4.9; not run.

## Hygiene

- Sibling arena in-flight files untouched and uncommitted here: `scripts/generate-arena-client-fixtures.js`
  (foreign, per dispatch), `functions/_firestore-stub.js` (committed by its owner), arena tests.
- All commits are narrow (`git add <files>`); siblings commit to the same branch in parallel.
