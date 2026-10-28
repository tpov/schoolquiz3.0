# redroid rig session 2026-10-08 evening (stories 1.5, 1.3; prep for 2.7) — completed 2026-10-09

Owner decision 2026-10-08 21:55: physical device unavailable (cable
disconnected) — raise a lightweight Android emulator instead:
`docker run -d --name schoolquiz-emu --memory 1g --privileged
-v /dev/binderfs:/dev/binderfs redroid/redroid:12.0.0-latest`, adb on
`localhost:5555`. The rig stands in for the physical Samsung for the hitl
device stories; demo backend only, nothing account-related (per the same
decision).

This directory holds the 08.10 evening session's artifacts. That session was
interrupted at 23:18 before writing a README or updating ticket statuses; the
2026-10-09 session (`docs/agents/evidence/2026-10-09-update-over-populated-journey/README.md`)
verified these artifacts, finished the journey and flipped the statuses.

## Timeline (UTC, 08.10)

- 22:04 `boot-screen.png` — redroid 12 booted (model redroid12_x86_64, SDK 31,
  720x1280@320dpi, x86_64+arm64 abilist, no GMS/Play).
- 22:06 APK A installed (`com.tpov.schoolquiz.mvp`, versionName 0.1.0-isolated,
  debug keystore cert 00:33:E7:FA…), first launches `A-first-launch*.png`.
- 22:06–23:0x population: demo backend catalogs synced, one lesson completed
  offline (Room v15, 28→30 tables; outbox op `6856201c…` WAITING NoNetwork;
  `population-before-update/`), maestro sync attempts (`10/11/12-sync-now`
  flows) hit the WorkManager wall (see the 09.10 README).
- 23:08–23:10 maestro-driven lesson run on the updated install; APK B built
  (`assemble-mvp-candidate-b.log`, sha256 `apk-b-sha256.txt` = 61acb693…) and
  installed over A (`adb install -r`), install times recorded
  (`install-times-*.txt`). Provenance and the two disclaimers for both APKs —
  see "APK provenance and disclaimers" below (reconciled 2026-10-28, retro F4).

## APK provenance and disclaimers (reconciled 2026-10-28, retro F4)

- **APK B (61acb693…) is NOT the identified candidate 1.2** (`eeb8fc6e…`,
  HEAD `1e596126` per `reproducibility.results`): it is a build surrogate
  assembled from this session's working tree, not from the identified
  candidate revision. The 1.3 retention claims stand for the surrogate only.
- Provenance of B: branch `night/e3-e8-inception`, worktree
  `.local/worktrees/night-impl`. The build window (23:08–23:10) sits between
  `1940b0dd` (08.10 21:56 — the last commit on that branch before the window)
  and `c0935a59` (09.10 01:12); `assemble-mvp-candidate-b.log` does not record
  the git HEAD, so the tip-at-build attribution is reconstructed from the
  branch history, not proven by the log.
- **APK A bytes were not captured** — no sha256 was recorded for A. What was
  recorded: the debug-keystore certificate (`00:33:E7:FA…`) and install times;
  the certificate was deemed sufficient identification at the time.
- Whether a candidate-faithful install-over is required before final
  acceptance (CAP-20) is an owner decision (retro item 1 of the re-verdict).
- 23:14 `inventory-15.txt` — story 1.5 device inventory (serial/Android/app/
  signature/DB schema/role/GMS) with the rig role explicit: device C
  (ordinary player), NOT a battle-session device.
- 23:15–23:16 `after-update/` pulls (auth-store.xml, schoolquiz.db).
- 23:18 — session interrupted (last maestro log dir empty).

## Continuation (09.10, this follow-up)

- Verified the after-update state survived the container restart: DB content
  identical to before-update for every retention-relevant table; auth store
  identical, same anonymous uid; outbox op intact.
- Finished the 2.7/1.3 journey on the live rig (offline lesson, restart,
  retention proof, drain attempt) — full narrative and verdicts in
  `docs/agents/evidence/2026-10-09-update-over-populated-journey/README.md`.

## Story mapping

- **1.5 device inventory — done** (`inventory-15.txt`; GMS/Play absence is an
  external dependency by the story's own wording, not blocking).
- **1.3 install-over-populated — done** (A→B `install -r` over the populated
  installation; retention claims proven across it — see the 09.10 README's
  retention table).
- **2.7 — still blocked** (retention half proven on the rig; live drain and
  account-switch device proof remain for the physical device run).

## Rig facts for future sessions

- Memory cap 1 GiB (`cgroup memory.max`), memory-swap widened to 3 GiB during
  the run; lmkd killed the app under swap exhaustion once, `launcher3` was
  disabled after crash-looping — drive the app via `am start` + maestro/adb.
- adb: the host default port 5037 is occupied on `[::1]` by the owner's sshd
  tunnel; use `ADB_SERVER_SOCKET='tcp:127.0.0.1:5038'` (the session-scoped adb
  server started 08.10) or spawn a v4-only server on 5037.
- The demo backend is reached from the app via `adb reverse` (9099→19090,
  8080→18090); removing the reverses is the rig's app-level offline switch.
