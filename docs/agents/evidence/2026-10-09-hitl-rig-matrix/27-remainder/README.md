# 2.7 remainder on the redroid rig — 2026-10-09 evening (owner decision: redroid for the hitl matrix)

The owner's 2026-10-09 morning supplement directs the hitl-matrix rows onto the redroid rig
(`schoolquiz-emu`, Android 12, 1 GiB cap) instead of waiting for the physical device. This
directory closes the two items the 09.10 drain session left open for 2.7:
**one online transition (2.6 repeat)** and **account switch**.

Rig state at start: APK C (`22a81c8a…`, debug App Check provider — commit 4003a978) installed
07:58, outbox ops 3/4 WAITING under uid `HDb3uVEO…` (the appcheck-wall failures, attempt_count 10),
terms consent present in `schoolquiz_economy.xml` (from the 03:22 drain), demo backend up,
functions emulator on host 5001 against demo Firestore/Auth (up since the morning session).

## What happened to the accounts (the honest narrative)

1. **The current session was not the drain session's uid.** The drawer showed `UserEVVVLZ`; the
   auth store decodes to uid `FaFXZCaw…` — the rig's current user changed between the 03:22 drain
   (uid `HDb3uVEO…`) and this session, off-session. Consequence observed live, twice: manual syncs
   at 18:38/18:43 ran the SyncWorker to SUCCESS while ops 3/4 stayed `WAITING` attempt_count 10 —
   `OutboxEngine.drain` finds no session matching the rows' owner and examines nothing
   (`FirebaseOutboxSessionProvider.current()` vs `ownerUid`, OutboxEngine.kt:88). The owner gate
   refusing foreign delivery is 2.3's JVM semantics observed live.
2. **A forged auth store for `HDb3uVEO…` was rejected** (`auth-store-A.xml` attempt: FaFXZC's blob
   heads with a swapped localId) — the SDK did not restore it and `signInAnonymously()` created a
   fresh uid `ck3DaiqEd…` (18:49). The forged file is kept for the record; no evidence relies on it.
3. **ck3D became the rig's working account.** Its first sync bootstrapped
   `profiles/ck3DaiqEd…` server-side (nickname `UserYF9FS9`, createdAt 18:50) — the profile
   bootstrap callable, one of the ops the appcheck wall had blocked, now delivered **from the app**
   with the fixed APK (e2e closure of 4003a978 on the app path).

## The account switch (2.7 dimension) — real-store A→B→A roundtrip, live

With uid A's ops 3/4 waiting as the constant foreign work:

- **ck3D does offline work:** reverses removed, lesson "MVP quick lesson" completed offline
  (LESSON COMPLETE · EASY, both answers correct) → outbox op 5
  (`lesson_runner-SUBMIT_ATTEMPT-ce864e51…`) `WAITING` owner ck3D, `db-before-switch.db`.
- **Switch away (ck3D → B):** force-stop, auth store removed, relaunch — the app signs in
  anonymously as fresh uid `gfOh2MRO…` (`auth-store-B-fresh.xml`). B's own sync bootstraps
  `UserXWS8B1` (`shot-B-synced.png`, Last sync 19:07). **Under B's session neither ck3D's op 5 nor
  A's ops 3/4 were delivered** — outbox rows untouched, demo `mutation_keys` stayed at 23, no
  record for any of the three mutationIds.
- **Return (B → ck3D):** the SDK-written ck3D store (`auth-store-ck3D.xml`, byte-identical
  restore) put the app back into ck3D's session (drawer `UserYF9FS9`, ck3D's own last-sync trace;
  `shot-ck3d-return.png`). SYNC NOW delivered **op 5 exactly once**: outbox rows are now only
  3/4, `mutation_keys` 23→24 with exactly one new `completed` record for `…-ce864e51…`, uid ck3D,
  receipt accepted=1, skillPoints 31, nolics 3
  (`server-ck3d-mutation-record.json`, `db-after-return-drain.db`).
- A new session of the same uid kept ownership (AD-8): the returnee's pending work survived two
  foreign sessions and settled once, the foreign ops (A) were never adopted.

The named boundary: the switch here is data-level (store swap), because the isolated build has no
account-switch UI (anonymous auto sign-in only, `AppApplication.kt`); the full AD-8 UI flow
(`AccountSwitch.begin` → consent → Google sign-in → `forgetAfterSwitch`) needs Google sign-in and
remains device-required (GMS absent on the rig) — it belongs to the physical-device rows.

## The online transition (2.6 repeat) — live against the demo backend

`online-transition/live-transition-output.json` — the 2.6 transition shape
(`functions/lesson-ready-ack-handler.test.js`) re-run through the **real functions emulator on
host 5001 against the real demo Firestore/Auth** (no in-memory stub), owner = the rig's current
player ck3D, real server clock, fresh zero-overwrite ids (`live-o2-20261009191049-*`):

1. completion event → `beginLessonQuestionWindow`: window OPEN, 60 s, allocatedSeconds 24
   (100 chars × 0.24 at HARD), server `openedAtMs`/`expiresAtMs`;
2. `fetchNextLessonQuestion`: DELIVERED, answer clock = issuedAtMs + 24 000 (server moment);
3. lost-ACK branch: the repeated delivery request returns the **same question and the same
   deadline**, remainder 23 937 ms — the clock never restarts (§5);
4. `lesson_runner.READY_ACK` through the single `submitMutation` receiver: `replayed:false`,
   ACKED, receipt carries the **delivery's** issuedAtMs/answerDeadlineMs and its own ackedAtMs;
5. replay of the same key: `replayed:true`, byte-identical result — no second effect (§6.1/AD-6);
6. the window document on the demo backend shows the captured config and DELIVERED state with the
   delivery facts (`windowDocs` in the output).

Boundary, named: the app-side driver of these callables does not exist in this build yet (no
production caller of `beginLessonQuestionWindow`; the READY_ACK outbox machinery is wired, the
online ordinary-lesson flow is future work) — so the device repeat of 2.6 is the live callable
receiver proof above, with the app online beside it, not an on-screen transition. The on-screen
online lesson arrives with the online-lesson stories and its acceptance will ride them.

## Smoke-lesson content fix (demo maintenance, documented)

"MVP quick lesson" said **Questions are invalid** under APK C: the smoke-lesson payloads in the
demo backend carry no top-level `id`, and the current schema parser reads only the new-format
discriminator plus the one legacy shape — an id-less `SingleChoice` payload is Unreadable
(`KotlinxSerializationQuestionContentParser.parse` → legacy branch refuses non-legacy type). The
same content completed under APK B because that build's start path predates the strict parse.
Fix applied (demo content maintenance, not owner files, not pinned fixtures):
- demo backend: `questions/mvp-smoke-v{1,2}-q-{0,1}` payload gained `"id":"<docId>"` and
  `lastModifiedAt := now`; `catalogs/mvp-smoke-v{1,2}-catalog.lastModifiedAt` bumped to trigger the
  catalog→content sync (content sync keys off catalog changes; v2 propagated through the pipeline,
  v1's catalog walk did not re-sync — see next line);
- rig local DB: the two v1 payloads got `"id"` by direct sqlite surgery (the 2.12 data-level
  precedent) after the online sync updated only v2.

After the fix the lesson starts, runs and completes offline (screenshots `shot-15-lesson.png` …
`shot-18-done.png`).

## Rig incidents this session (for the runbook)

- `adb root` triggered a framework soft-restart ("Phone is starting…" for minutes); afterwards the
  app package surfaced as `enabled=0` until `pm enable` — re-enabled, data intact.
- The launcher activity is `com.tpov.schoolquiz.apps.android_next.MainActivity` (not
  `presentation.main`); monkey/LAUNCHER resolution finds it, a hard-coded wrong component errors
  with type 3.
- The container died once (docker `Exited (137)`, host-side kill ~19:05, OOMKilled flag false);
  `docker start` brought it back with state intact. Reverses must be re-set after any `adb root`
  or container restart (drain README rule).
- The 24 h periodic sync job never fires naturally on this rig (`Minimum latency: +23h59m…`);
  manual SYNC NOW works (workers run) — the old "manual work never executes" verdict stays
  revised: it executes; passes skip only by the owner-gate as designed.
