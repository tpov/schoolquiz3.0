# Update-over-populated journey on the redroid rig (stories 2.7 + 1.3) — 2026-10-09

Owner decision 2026-10-08 21:55: physical device unavailable — raise a lightweight
Android emulator instead. Rig: docker container `schoolquiz-emu`
(redroid/redroid:12.0.0-latest, `--memory 1g --memory-swap 3072m --privileged`),
adb via `localhost:5555`. The rig stands in for the runbook device C (ordinary
player). Demo backend only (`demo-schoolquiz-mvp` via `adb reverse tcp:9099
tcp:19090` / `tcp:8080 tcp:18090`) — no production accounts on the rig, per the
same owner decision. App under test: `com.tpov.schoolquiz.mvp` (versionName
`0.1.0-isolated`, applicationId suffix `.mvp`).

## Setup inherited from the 2026-10-08 evening session (docs/agents/evidence/2026-10-08-redroid-hitl/)

APK A installed 22:06, populated (first launches, one lesson completed offline
→ outbox op `6856201c…` WAITING, last_error NoNetwork), APK B built
(sha256 61acb693…) and installed over A (`adb install -r`, lastUpdateTime
23:15:49 [REVISED 2026-10-28: the recorded artifact
`../2026-10-08-redroid-hitl/install-times-after.txt` reads **23:10:48**; the
23:15:49 reading is this session's re-check, taken without its own artifact]),
inventory pulled (story 1.5). That session was interrupted before it
could write its README or update ticket statuses; this session verified its
artifacts and continued the journey.

## Retention across the update (core CAP-20 claim, stories 1.3 + 2.7)

Cross-compared three Room snapshots — `population-before-update/schoolquiz.db`
(08.10, before `install -r`), `after-update/schoolquiz.db` (08.10, right after),
and `db-after-update-first-launch.db` (09.10, first launch after update):

- catalogs, lessons, quests, sections, themes, questions, user_profiles,
  user_stats, sync_state, lesson_attempt_intents, lesson_attempts,
  question_answers, question_repetition, room_master_table — **byte-identical
  row content (SAME)**;
- `outbox` — same single WAITING op, identical payload (sha256 27e1efd7f78a…);
  only the retry bookkeeping moved (attempt_count 0→1, next_retry_at, last_error
  NoNetwork), which is the scheduler doing its job, not data change;
- auth store: identical on 08.10 before/after; on 09.10 the current
  `shared_prefs/com.google.firebase.auth.api.Store.W0RFRkFVTFRd….xml` still
  holds the same anonymous uid `HDb3uVEO99PLIaceEvkAabcCxtZp` (file
  `auth-store-after-update-first-launch.xml`).

Installation identity: firstInstallTime 2026-10-08 22:06:07 preserved through
both B installs (see inventory-15.txt); same debug-keystore cert for A and B.

UI proof: `01-home-first-launch-after-update.png` (HOME QUESTS, the quest, the
lesson with retained progress "CONTINUE 0/1"), `02-menu-after-update.png`
(Guest, "Not synced yet", v0.1.0-isolated).

## Ordinary-lesson path on the updated install

- **Offline (2.1 dimension):** reverses removed (`adb reverse --remove-all`,
  8080/9099 unreachable from the container — app-level offline while adb stays
  alive), full lesson replayed to `LESSON COMPLETE · EASY`
  (`03-offline-q2-correct.png`, `03-offline-lesson-complete.png`; flow
  `flows/30-offline-complete-lesson.yaml`, driven partly via maestro, partly via
  adb taps after maestro session instability). Completion screen honestly shows
  "Not sent yet — it will go with the next sync". DB: 2 attempts, outbox grows
  to 2 WAITING ops (new op `bfd35496…`), `db-after-offline-lesson.db`.
- **Restart (2.1):** `am force-stop` + relaunch, still offline. Everything
  retained: 2 attempts, 2 outbox ops, 2 intents
  (`04-home-after-restart-offline.png`, `05-menu-after-restart-offline.png`,
  `db-after-restart-offline.db`).
- **Queue (2.2 dimension):** accumulation + retention across update and restart
  proven (2 WAITING ops). **The live drain did not happen on the rig — see
  below.**
- **Account (2.3 dimension):** authentication retained across the update (same
  uid). The 0.1.0-isolated app has no account-switch UI (anonymous auto
  sign-in, `AppApplication.kt`); on-device account-switch isolation remains a
  physical-device item. The isolation logic itself is JVM-proven (story 2.3,
  5fb40db4).
- **Config (2.4 dimension):** post-update launches used the same isolated
  config; no config-related breakage observed. Economy-config refresh terms are
  JVM-proven (story 2.4, fe6cfc6d).

## Queue drain on the rig: blocked by a rig-level WorkManager dysfunction

**REVISED 2026-10-28** (retro F6): the verdict below was written mid-session
and is refuted by `drain/README.md` in this directory — the workers DID run
and failed on recoverable causes (NoNetwork backoff cycle, no host
functions-emulator at 5001, missing adb reverse; R8 had stripped the WM log
lines), and the 03:22 retry **succeeded**: the queue drained exactly once.
Read that file as the final word for the rig's drain; the text below is kept
as the honest 09.10-morning state.

Not an app-code verdict — a rig limitation, hit independently by both the
08.10 session (flows `flows/10-sync-now.yaml`…`12-sync-now3.yaml`, all
"SYNC NOW" attempts failed the same way) and this session:

- SYNC NOW taps do fire `onSyncNow()` → `enqueueManualSync()` (ABOUT tap in the
  same drawer opens its dialog, so input injection works), but the manual
  OneTime work never executes. WorkManager db (`rig-workmanager-dysfunction/
  workmanager-workspec.db`): manual head ENQUEUED since 08.10 23:19 with
  run_attempt_count 9, ~19 BLOCKED specs appended by repeated APPEND_OR_REPLACE
  enqueues.
- JobScheduler registers jobs for the three non-blocked specs, but worker
  bodies never run: `cmd jobscheduler run -f …` prints "Running job [FORCED]"
  and produces **zero** SchoolQuizSync/WorkerWrapper/SystemJobService log lines
  (`logcat-during-forced-run.txt`, count 0 in `worker-log-line-count.txt`),
  no state change, no attempt increment.
- Reproduced identically after a clean `docker restart schoolquiz-emu`
  (fresh jobs 68/70/71, same no-op).
- The stuck chain dates back to the 08.10 session's force-stops while the
  sync was failing with NoNetwork (backoff cycle); WorkManager on this
  userdebug redroid image never dispatches job starts into SystemJobService.
  [REVISED 2026-10-28: see the section head and `drain/README.md` — the
  "never" did not survive the night; the 03:22 drain settled both ops once.]

Consequently the outbox ops remain WAITING on the rig (`06-menu-final-queue-pending.png`,
"Not synced yet") — they belong to the demo anonymous user only. The drain
mechanism itself is continuously integration-tested off-device: story 2.2
(OutboxDrain120Test, 5 cases, 42a5df00), 2.12 (terms-attach retry gates),
2.13 (live suites vs the demo backend, 92/92).

## Story mapping

- **1.3 (install-over-populated) — done.** The A→B `install -r` over the
  populated installation with all five retention claims proven (content,
  history, authentication, installation identity, pending operations). This
  directory + `2026-10-08-redroid-hitl/`.
- **1.5 (device inventory) — done.** `2026-10-08-redroid-hitl/inventory-15.txt`
  (+ this README's uid/cert facts). GMS/Play absent — recorded as an external
  dependency per the story wording, not blocking.
- **2.7 (update-over-populated journey) — still blocked, retention half proven
  on the rig.** Remaining for the physical-device run: live outbox drain
  (exactly-once), one online transition (2.6 repeat per its ticket), account
  switch. Rig findings feed that run (flows and procedures in `flows/`).

Rig quirks worth keeping for future sessions: adb default port 5037 is broken
while the owner's sshd listens on `[::1]:5037` (use `ADB_SERVER_SOCKET=
tcp:127.0.0.1:5038` — the session-scoped server — or spawn a v4-only server);
launcher3 is disabled (FallbackHome forever; start the app via `am start`);
maestro needs the default 5037 server to be healthy (ddmlib ignores
ADB_SERVER_SOCKET).
