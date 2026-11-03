---
title: 'Epic 1 candidate-rig run: restore demo backend, identify candidate, fix quiescence preflight, root ciCheck'
type: 'bugfix'
created: '2026-10-04'
status: 'done'
baseline_revision: '1e5961263a4c1ac85ad03eb7894a51bf44228a4a'
review_loop_iteration: 0
followup_review_recommended: true
context: ['_bmad-output/implementation-artifacts/epic-1-context.md']
warnings: ['multiple-goals', 'oversized']
deferred:
  - summary: >-
      Root ciCheck stays RED until the owner supplies the untracked, owner-local debug google-services.json (or accepts the recorded RED baseline); until then the canonical gate exercises no later leg (JVM/KMP tests, detekt, ktlint, scriptsTest), and no epic tracking surface records the required re-run-and-re-record once the file arrives.
    evidence: |-
      Deterministic exit 1 at :apps:android-next:processDebugGoogleServices, reproduced three times (two implementation runs 2026-10-04 plus an independent verification run); the file is gitignored ('debug/', '**/google-services.json') and exists only in the owner's Mac checkout, so it cannot be fabricated or committed from the VPS. tickets.toml closes story 1.6 as done per the epic letter ("run at least once with the result recorded"); the follow-up lives only in the run record's nextSteps.
    location: >-
      docs/agents/evidence/2026-10-04-root-cicheck/cicheck-result.json
    severity: medium
  - summary: >-
      The quiescence gate's malformed-payload (17-case) oracle exists only in the owner's uncommitted offline-settlement-contract.test.js, and no automated verification path runs the contract test at all, so the fixed regression could silently return in the committed tree.
    evidence: |-
      Verification-gap layer demonstrated the revert: on a checkout of 8f8356ac the committed 428-line test (valid-quiescence happy path only) still passes with the old buggy gate restored; the 460-line matrix version is the owner's in-flight uncommitted edit. Committing it from this run is excluded by the skill's commit rule ("Commit only the files you wrote") and the spec Never (keep the owner's oracle verbatim). Settled when the owner commits their test version and decides whether to wire it into scripts/package.json's test chain (noting node/npm availability on the VPS is unverified).
    location: >-
      .maestro/mvp/arena-matrix/offline-settlement-contract.test.js (uncommitted working-tree edit)
    severity: medium (unverified — if-true grade; the committed-tree exposure is demonstrated, the owner's commit intent is not known)
---

<intent-contract>

## Intent

**Problem:** Epic 1 (`epic-candidate-rig`) is the platform baseline every later epic waits on, and four of its six Done-when items are dispatched for this run: the demo Firebase backend is not restored from the verified export, the candidate at HEAD 1e596126 is not identified (the existing APK predates HEAD and its recorded certificate is stale), the known quiescence-preflight regression is RED, and root `ciCheck` has never been run.

**Approach:** Recreate the demo Firebase emulator container from the hash-verified staged export under a new container name, new output directories and new loopback ports, then prove a control save/restart retains the saved data. Re-derive candidate identity with a fresh `assembleMvp` at HEAD and record APK SHA-256, signing certificate, Room schema, config and flags. Fix `validate_preparation_preflight` to demand exactly the five known counters, each numeric zero. Run root `ciCheck` once and record the result. Done-when items 1.3 (install over the populated Samsung) and 1.5 (device inventory) need physical devices attached to the owner's Mac — out of scope here, recorded as NOT RUN / external dependency.

## Boundaries & Constraints

**Always:**
- Work only in the worktree `.local/worktrees/mvp-candidate` on branch `continuation/mvp-candidate-20260926` (HEAD `1e5961263a4c1ac85ad03eb7894a51bf44228a4a`); the authoritative git lives on the VPS (`ssh racknerd`, remote path `/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate`).
- Re-verify the staged export manifest immediately before restoring (`find "$PWD" -type f | sort | xargs sha256sum | sha256sum` at the staged path must equal `2e4237d84530f63520703c519803e3e4edd6f629fa8259e920bc4e7ba9e898ff`); any mismatch or missing file stops the restore story.
- The staged export stays read-only (`:ro` mount); the new container writes only into NEW directories (`firebase-exports/demo-restore-20261004/`, `firebase-runtime/demo-restore-20261004/`).
- Quiescence gate: quiescence must be a non-null, non-array object whose key set is exactly `outboxRows, unresolvedArenaCommands, reservedArenaTickets, orphanedSubmitIntents, orphanedRatingIntents`, every value `typeof "number"` and `=== 0`; anything else exits 2 BEFORE Maestro prepare and before any network change (wifi/data untouched, no prepared ticket).
- Gradle builds run with OpenJDK 27 (`JAVA_HOME=/opt/jdks/jdk-27`), `--no-parallel --max-workers=1`, one heavy build at a time, `--no-configuration-cache` for gates.
- Evidence records follow `docs/agents/` conventions (`schemaVersion`, `capturedAtUtc`, ISO-dated filenames; raw logs under `docs/agents/evidence/<date>-<slug>/`).
- Record 1.3 and 1.5 as NOT RUN with the external dependency (Samsung `RF8W80MBR3Z` is USB-attached to the owner's Mac, not visible to server ADB) in the run result and evidence.

**Never:**
- Never modify, restart or delete the staged export, the exited old containers (`schoolquiz-course-device-20260927`, `schoolquiz-lesson-device-20260927`), or the Oct 3 `lesson-performance-run-20260927` export.
- Never revert or "clean up" the owner's pre-existing uncommitted changes — in particular the modified `.maestro/mvp/arena-matrix/offline-settlement-contract.test.js` IS the new RED test and must be kept as the acceptance oracle for the fix.
- Never run account-bearing emulators under `/srv/schoolquiz-mvp-tools-20260906/` beyond the mounts this restore needs (firebase-cli, firebase-emulators, jdk21 — read-only); note the scoping decision in the run log.
- No Functions in the demo container (`--only auth,firestore`); no destructive DB changes; no uninstall/reset paths; no secrets in logs or evidence files.
- No feature work: ordinary/Arena journeys belong to their epics; this run proves the rig baseline only.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Valid quiescence | Five keys, each numeric `0` | Validation passes; leaf proceeds to prepare and later phases | No error expected |
| Empty object | `{}` | Exit 2, `PRE_CUT_CHILD_FAILED`, `cut.attempted === false`, wifi/data still enabled, zero Maestro log entries, no `prepared-ticket.json` | Stopped before preparation and transport cut |
| Missing key | Any one of the five deleted | Same rejection as empty object | Stopped before preparation and transport cut |
| Extra key | Any additional key (even value `0`) | Same rejection | Stopped before preparation and transport cut |
| Mistyped value | `"0"` string, `NaN`, array payload, `null` root, `[0,0,0,0,0]` | Same rejection | Stopped before preparation and transport cut |
| Nonzero counter | Any of the five keys = `1` | Same rejection | Stopped before preparation and transport cut |
| Export hash mismatch | Manifest SHA-256 differs at restore time | Restore story stops; no container created | Abort, record blocking condition |

</intent-contract>

## Code Map

Investigation anchors (all paths relative to the worktree; line anchors as of HEAD 1e596126):

- `.maestro/mvp/arena-matrix/run-arena-offline-settlement-leaf.sh` — PRODUCTION FIX SITE. `validate_preparation_preflight()` at 490; embedded Node validator heredoc 492–508; defective reject condition 506–507: `!q || Object.values(q).some((count) => count !== 0)` — truthy `{}` yields `[].some(...) === false` and passes. Call site 741–742: capture+validate run pre-cut under the transport guard, so exit 2 already stops before Maestro and network changes. In-file exact-key-set precedents to mirror: line 150 (`Object.keys(item).sort().join(',') !== 'path,sha256'`), lines 179, 215, 558.
- `.maestro/mvp/arena-matrix/offline-settlement-contract.test.js` — the RED acceptance oracle (owner-modified, uncommitted — keep verbatim). Failing assertion line 393 in `testInvalidQuiescenceStopsBeforePreparationAndTransportCut` (389–406); driver loop 446–455 exercises 17 invalid payloads (`{}`, `null`, `[]`, `[0,0,0,0,0]`, `""`, extra key, `"0"` string, each key deleted, each key `= 1`). Pass line: 459 `offline settlement composition tests: PASS`. Fixtures self-fabricated in tmpdir (fake adb/maestro/apksigner/flock + real leaf sealed in).
- `scripts/arena-e2e-preparation-preflight-reader.js` — producer of the quiescence object; canonical five counters at 266–279: `outboxRows`, `unresolvedArenaCommands`, `reservedArenaTickets`, `orphanedSubmitIntents`, `orphanedRatingIntents`. Its own test `scripts/arena-e2e-preparation-preflight-reader.test.js` (quiescence assertions 99–103, 163–167, 232–236) is NOT touched by a leaf-only fix; only the contract test drives the leaf's consumer gate.
- Container runtime (VPS): image `node:22-bookworm-slim`; entrypoint `/tools/firebase-cli/node_modules/.bin/firebase`; flags `emulators:start --config /repo/firebase.mvp.json --project demo-schoolquiz-mvp --only auth,firestore --non-interactive --import /tools/firebase-import --export-on-exit /tools/firebase-output-root/export`; env `JAVA_HOME=/tools/jdk21`, `FIREBASE_EMULATORS_PATH=/tools/firebase-emulators`, `JAVA_TOOL_OPTIONS=-Xmx640m`, `NODE_OPTIONS=--max-old-space-size=384`; repo mounted `:/repo:ro`; tools from `/workspace/infrastructure/android-toolchain/{firebase-cli,firebase-emulators,jdk21}` (`/srv/schoolquiz-mvp-tools-20260906` is a symlink to it). Demo shape = the lesson-device container (the only one importing the staged export; course-device has no import — do not recreate it).
- Staged export (input, read-only): `/workspace/infrastructure/android-toolchain/firebase-export-staging/arena-e2b-cycle4-20260913/export` — 12 files, 31,379,966 bytes, manifest `sha256OfSortedFileSha256Manifest` (recipe hashes absolute paths — valid only at the staged location) = `2e4237d84530f63520703c519803e3e4edd6f629fa8259e920bc4e7ba9e898ff`. Contents: `firebase-export-metadata.json` (firebase-tools 15.29.0, firestore emulator 1.22.0 — matches installed tooling), `auth_export/` (98 users), `firestore_export/` (LevelDB; collections `profiles` = wallet: `lifePoints`/`standardHearts`/`goldHearts`; `lessons` (10), `questions` (400), `themes`/`sections`/`quests`/`catalogs` (5 each), `seasons` (e.g. `5990-Q4`), `course_tournament_verified_runs`, `history`).
- New container target (free at investigation, re-check `ss -tln` before launch): `127.0.0.1:18090→8080` (firestore), `127.0.0.1:19090→9099` (auth); name `schoolquiz-demo-firebase-20261004`; new dirs `/workspace/infrastructure/android-toolchain/firebase-exports/demo-restore-20261004/` (output root) and `.../firebase-runtime/demo-restore-20261004/` (tmp). Control save via `docker exec <name> .../firebase emulators:export --export-dir /tools/firebase-output-root/control-save-1 --project demo-schoolquiz-mvp`; `docker stop` fires `--export-on-exit`; `docker start` re-imports the STAGED export. Disk: 30G free — ample.
- Build identity: `apps/android-next/build.gradle.kts` — `mvp` is a build TYPE (L54–61): `initWith(debug)`, `applicationIdSuffix ".mvp"` → `com.tpov.schoolquiz.mvp`, `versionNameSuffix "-isolated"` → `0.1.0-isolated`, `versionCode 1` (L38), BuildConfig `MVP_EMULATORS=true`, `FIREBASE_APP_CHECK_DEBUG_SECRET=""`; no signingConfig block → inherits debug keystore `/root/.android/debug.keystore` (current cert SHA-256 `00:33:E7:FA:2C:7E:32:86:5A:5E:99:82:7A:06:8B:EC:76:52:90:4A:A2:76:E1:DB:9C:F4:23:D7:5E:6C:47:D3` — the documented `73d39c9c…` is STALE). Existing APK on disk (42,038,981 B, sha256 `78937196…`, mtime Sep 27 20:01) PREDATES HEAD (committed Sep 28 07:42) → must rebuild. `apps/ shared/ buildSrc/ gradle/` are clean in git — rebuild is reproducible from HEAD.
- Room schema: version 15 at `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt:38`; exported schemas `shared/core/persistence/schemas/com.tpov.schoolquiz.shared.core.persistence.AppDatabase/{14,15}.json`; pinned by `AppDatabaseMigrationsTest.kt:744` and `MigrationCoverageTest.kt:160`.
- Config/flags inventory for the identity record: `apps/android-next/src/mvp/google-services.json` (project `school-quiz-89336951`), `firebase.mvp.json` (emulator suite), `firebase.json` (root deploy), `config/*-fixtures.json` (economy), `firestore.rules` + `firestore.indexes.json`, BuildConfig `MVP_EMULATORS`/empty App Check secret. `local.properties` untracked — never print/commit.
- Root `ciCheck`: `build.gradle.kts:68-84` — depends on `:apps:android-next:assembleDebug`, all `test`, all `allTests`, `compileAndroidTests` (L36-41, legacy excluded), `functionsTest` (L46-53 — auto-skips, `functions/node_modules` absent), `scriptsTest` (L60-66 — `npm test` in `scripts/`), `detekt`, `ktlintCheck`, `detektMetadataCommonMain`. No Android lint. `gradle.properties`: Xmx4096m, parallel+caching+configuration-cache on (override with flags).
- Tooling: apksigner `/workspace/infrastructure/android-toolchain/sdk/build-tools/36.0.0/apksigner`; keytool `/opt/jdks/jdk-27/bin/keytool`; Docker 29.1.3; no node on VPS shell (contract test needs docker).
- Evidence conventions: top-level `docs/agents/YYYY-MM-DD-<slug>.json` (schemaVersion 1, capturedAtUtc, purpose, candidate{...}); raw evidence `docs/agents/evidence/YYYY-MM-DD-<slug>/`. Reference: `docs/agents/2026-09-27-mvp-progress-checkpoint.json` (also the source of `sha256OfSortedFileSha256Manifest` and `savedFirebaseExport` fileCount/bytes).

## Tasks & Acceptance

**Execution:**

- `.maestro/mvp/arena-matrix/run-arena-offline-settlement-leaf.sh` — in the `validate_preparation_preflight` heredoc (~497–507), replace `!q || Object.values(q).some((count) => count !== 0)` with an exact-shape check: `q` non-null, not an array, plain object, sorted `Object.keys(q).join(',')` equals `'orphanedRatingIntents,orphanedSubmitIntents,reservedArenaTickets,unresolvedArenaCommands,outboxRows'`, and every value `typeof "number"` and `=== 0`; otherwise `process.exit(2)` — closes the `{}`/missing/extra/mistyped holes while keeping the existing call site and exit semantics untouched.
- `.maestro/mvp/arena-matrix/offline-settlement-contract.test.js` — NO edit; it is the owner's RED oracle and must pass unmodified.
- VPS backend (no repo file; evidence file is the artifact) — after re-verifying the staged manifest hash, create the two new directories and `docker run` the new demo container per the Code Map runtime block (name `schoolquiz-demo-firebase-20261004`, ports 18090/19090, staged export mounted `:ro` as import); wait for emulator readiness; spot-check 98 auth users, wallet fields on a `profiles/<uid>`, `lessons/ct_…`, `lessons/qcalc_…`, `seasons/5990-Q4`, a `history` entry; run the control save to `control-save-1`, verify it (12 files, 98 users), `docker stop` (export-on-exit), `docker start`, repeat the spot-checks; record everything under `docs/agents/evidence/2026-10-04-demo-backend-restore/`.
- Candidate identity — `JAVA_HOME=/opt/jdks/jdk-27 ./gradlew :apps:android-next:assembleMvp --no-parallel --max-workers=1 --no-configuration-cache` at HEAD; record `git rev-parse HEAD`/`HEAD^{tree}`, `git diff | sha256sum`, APK bytes + SHA-256, `apksigner verify --print-certs`, keytool keystore cert, Room schema version 15 + v14/v15 schema files, config/flags inventory into `docs/agents/2026-10-04-mvp-apk-identity.json` per the checkpoint schema conventions.
- Root `ciCheck` — `JAVA_HOME=/opt/jdks/jdk-27 ./gradlew ciCheck --no-configuration-cache --no-parallel --max-workers=1`; capture full stdout/stderr + exit code under `docs/agents/evidence/2026-10-04-root-cicheck/`; record PASS/FAIL with the failing-task breakdown if RED.
- Run record — write `docs/agents/2026-10-04-epic1-rig-run.json` summarizing per-story outcomes (1.1/1.2/1.4/1.6 with evidence pointers; 1.3/1.5 NOT RUN with the device external dependency), update `epic-candidate-rig/tickets.toml` story statuses only via the spec/story files per store convention (build status lives in files, not ticketing edits), and commit ONLY files this run wrote (leaf fix, tickets.toml, evidence records, spec) on `continuation/mvp-candidate-20260926`.

**Acceptance Criteria:**

- Given the staged export manifest hash re-verified equal, when the new demo container is created and emulators report ready, then auth exposes exactly 98 users and Firestore serves the wallet/lessons/seasons/history spot-checks on ports 18090/19090.
- Given the control save verified (12 files, 98 users), when the container is stopped (export-on-exit) and started again, then the same spot-checks pass again (account, wallet, lessons, history retained).
- Given the working tree keeps the owner's RED test unmodified, when the contract test runs in `node:22-bookworm-slim` (repo `:ro`, network none), it exits 0 with `offline settlement composition tests: PASS`; reverting ONLY the leaf fix would again fail at line 393 with `unsafe quiescence passed: {}; 0 !== 2`.
- Given the malformed-preflight matrix (empty/missing/extra/mistyped/nonzero), when the leaf validates preflight, then every case exits 2 with `PRE_CUT_CHILD_FAILED`, no Maestro invocation, no wifi/data change, and no `prepared-ticket.json`.
- Given a clean `apps/ shared/ buildSrc/ gradle/` at HEAD, when `assembleMvp` completes, then the produced APK's SHA-256 and signing certificate are recorded in `docs/agents/2026-10-04-mvp-apk-identity.json` together with HEAD, tree hash, Room schema version and config/flags, and a re-run reproduces the same identity record inputs (HEAD, diff hash, cert).
- When `./gradlew ciCheck --no-configuration-cache --no-parallel --max-workers=1` finishes, its exit code and full log are recorded under `docs/agents/evidence/2026-10-04-root-cicheck/` and the run record states PASS or the exact failing tasks.
- Given the run record exists, stories 1.3 and 1.5 are listed NOT RUN with the blocking external dependency (physical Samsung on the owner's Mac), and no file outside the worktree's owned surfaces was modified.

## Spec Change Log

## Review Triage Log

### 2026-10-04 — Review pass
- verdicts: 22 findings — high 0, medium 7, low 10, false 5, maybe-false 0
- findings:
  - `[low]` `[reject]` BH: committed spec still carries the impossible literal sorted-key string, wrong collection names (`seasons`/`profiles`) and wrong google-services project id, so a spec-only reader could reintroduce the bug — fix would edit this build's spec (rejected per rule); corrections are durably recorded in the committed run record (story deviations), backend-restore-result.json deviations, and this file's Auto Run Result.
  - `[low]` `[reject]` BH: history acceptance criterion is unsatisfiable (staged baseline has zero history documents) and tickets marks 1.1 done without spec amendment — fix would edit this build's spec (rejected per rule); the faithful-restore fact (0 docs before and after, byte-faithful import) is recorded in backend-restore-result.json and the raw log.
  - `[medium]` `[patch]` BH: six decisive `.log` evidence files are gitignored and only pointed at, not excerpted — inline the decisive outputs (build result lines, ciCheck failure block, contract-test stdout) into the committed JSON/MD records.
  - `[medium]` `[patch]` BH: 1.2 reproducibility claim rests on an untracked rerun log; no second APK hash is inlined anywhere durable — inline the rerun hash/size and the independent verification run into 2026-10-04-mvp-apk-identity.json.
  - `[low]` `[patch]` BH: wallet types contradict between backend-restore-result.json (numbers) and the raw REST payload (strings), and `nolics` appears unnoted — correct to the captured string form and note the field.
  - `[false]` `[reject]` BH: status vocabularies don't map between tickets.toml (done/blocked) and run record (PASS/RECORDED (FAIL)), hiding that done can encode a failing result — refuted: the tickets.toml:207 comment states the RED outcome verbatim ("ciCheck executed and recorded — RED, exit 1, :apps:android-next:processDebugGoogleServices"); the store config defines status as the build's own words.
  - `[false]` `[reject]` BH: spec frontmatter committed as stale in-progress — refuted: transient mid-flow state by design; step-04 sets in-review then done in the finalization commit of this same run.
  - `[false]` `[reject]` BH: leaf hardcodes counter names with no producer pointer, so a producer change can silently desync the gate — refuted: a producer counter change changes the key set, and the shipped gate exits 2 on any extra/missing key — the desync fails loudly at preflight, which is the designed contract behavior.
  - `[low]` `[patch]` BH: contract-test-verification.md and raw-restore-log.md lack the schemaVersion/capturedAtUtc evidence conventions — add the two fields.
  - `[medium]` `[patch]` BH: restore evidence omits environment identity (firebase-tools CLI version, node image digest) and container lifecycle ownership (left running on 18090/19090; who stops it) — append an environment-identity and lifecycle block to backend-restore-result.json.
  - `[low]` `[patch]` BH: raw log elides the verification commands (shortened paths; `<staged export>` placeholder in the JSON recipe) — concretize the recipe path and the two commands.
  - `[false]` `[reject]` BH: "twice identical" is unproven for run 1 (exitCodeNotCaptured) and no --continue decision is recorded — refuted: cicheck-result.json discloses run 1's uncaptured code and explicitly records notRun as the fail-fast consequence; determinism independently reproduced by the orchestrator's third run (same task, same error, exit 1).
  - `[low]` `[patch]` BH: identity JSON duplicates buildLog at two levels and repeats the google-services deviation in two files — remove the top-level duplicate; keep the run-record pointer one-way.
  - `[low]` `[patch]` BH: raw log says the container was "re-created on restart by docker start", misstating Docker semantics — reword to "restarted (same container, same id)".
  - `[medium]` `[defer]` BH: no tracking surface records the ciCheck-to-green re-run once the owner supplies the debug google-services.json — deferred: the RED cause is the pre-existing owner-local untracked file, outside this run's owned surfaces; recorded in deferred list.
  - `[false]` `[reject]` ECH: a single key equal to the comma-joined five counter names with value 0 would pass the gate — refuted: the shipped guard requires `Object.keys(candidate).sort()` to equal the sorted five-element canonical array (`k.length===r.length` fails for any 1-key object).
  - `[low]` `[reject]` ECH: spec's literal sorted-key string mismatches JS default sort — same claim as row 1; fix would edit this build's spec (rejected per rule).
  - `[low]` `[reject]` ECH: spec spot-check names (profiles/seasons/history) diverge from live collections, so consumers trusting the acceptance text query wrong paths — fix would edit this build's spec (rejected per rule); real paths recorded in backend-restore-result.json deviations.
  - `[medium]` `[defer]` VG (main): the quiescence gate ships with no committed, automatically-run test covering it — the committed test predates the matrix and nothing wires the contract test into any verification leg, so the regression could silently return in the committed tree; deferred: the closing fix is committing the owner's in-flight test file, which the skill's commit rule and the spec Never exclude from this run; recorded in deferred list.
  - `[low]` `[reject]` VG: spec literal sorted string remains wrong for future readers — duplicate of row 1; fix would edit this build's spec (rejected per rule).
  - `[medium]` `[patch]` VG: committed verification evidence for the fix consists of summaries whose raw outputs are untracked — same root cause as row 3; inline the contract-test stdout (already done in contract-test-verification.md) and reference the inlined ciCheck/build blocks.
  - `[medium]` `[defer]` VG: root ciCheck is RED, so the canonical gate exercises none of the later legs — same root cause as the deferred ciCheck baseline row; deferred with it (pre-existing owner-local file).

## Design Notes

- Manifest-hash caveat: `sha256OfSortedFileSha256Manifest` hashes absolute paths, so it verifies only at the staged location; after any copy, verify the 12 per-file SHA-256s instead (capture the list before touching anything).
- Restart semantics: `docker start` re-imports the read-only STAGED export, so post-restart state equals the 98-user staged baseline; the save path (control save + export-on-exit) is what proves the container can persist runtime state into the NEW output directory. Keep both verifications in the evidence.
- The five-counter list is owned by the producer (`arena-e2e-preparation-preflight-reader.js`); the leaf validates untrusted JSON and must re-check shape rather than trust the producer.
- The Oct 3 export (99 users) under `firebase-exports/lesson-performance-run-20260927/` is the old container's dying export — deliberately NOT the restore source; the staged 98-user export is canonical for this epic.

## Verification

**Commands:**
- `ssh racknerd "docker run --rm --network none -v /workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate:/repo:ro node:22-bookworm-slim node /repo/.maestro/mvp/arena-matrix/offline-settlement-contract.test.js"` — expected: exit 0, stdout ends `offline settlement composition tests: PASS`
- `curl -s http://127.0.0.1:19090/identitytoolkit.googleapis.com/v1/projects/demo-schoolquiz-mvp/accounts:query -H 'Authorization: Bearer owner' -d '{}'` (on VPS) — expected: 98 users, before AND after the control restart
- Firestore REST spot-checks on `127.0.0.1:18090` (`lessons/ct_796451cb…`, `lessons/qcalc_59d41cdb…`, `seasons/5990-Q4`, `profiles/<uid>` with Bearer `<uid>`, a `history` id) — expected: documents present with wallet fields `lifePoints`/`standardHearts`/`goldHearts`
- `JAVA_HOME=/opt/jdks/jdk-27 ./gradlew :apps:android-next:assembleMvp --no-parallel --max-workers=1 --no-configuration-cache` — expected: BUILD SUCCESSFUL, APK at `apps/android-next/build/outputs/apk/mvp/android-next-mvp.apk`
- `JAVA_HOME=/opt/jdks/jdk-27 ./gradlew ciCheck --no-configuration-cache --no-parallel --max-workers=1` — expected: exit 0 (recorded either way)
- `git -C <worktree> status --short` after the run — expected: only this run's new/changed files on top of the documented pre-existing owner baseline

**Manual checks (if no CLI):**
- Identity record JSON parses, carries all seven identity fields (HEAD, tree, diffSha256, APK bytes+SHA-256, cert SHA-256, schema version, config/flags) and matches the freshly built APK.

## Auto Run Result

Status: done (2026-10-04). Dispatch scope delivered: Done-when items 1, 2, 4, 6 of epic `epic-candidate-rig`; items 3 (install over populated Samsung) and 5 (device inventory) NOT RUN — physical devices are USB-attached to the owner's Mac, invisible to server ADB (external dependency, recorded in the run record and tickets.toml).

### Summary of implemented change

- **1.1 restore (PASS)** — staged export manifest re-verified immediately before restore (`2e4237d8…898ff`, 12 files, 31,379,966 bytes); new container `schoolquiz-demo-firebase-20261004` (node:22-bookworm-slim, 127.0.0.1:18090→8080, 127.0.0.1:19090→9099, staged export mounted read-only, new output/runtime directories). Baseline 98 auth users, wallet `users/1wqSlL9…` = 1000/10/3 (strings in REST), both lessons, `arena_seasons/5990-Q4` FINALIZED; control save verified (12 files, 98 users); stop fired export-on-exit (12 files, 98 users); restart re-imported staged export and all spot-checks passed again. Left running for downstream epics.
- **1.2 identify (PASS)** — `assembleMvp` at HEAD `1e596126…`: APK 42,038,981 B, SHA-256 `eeb8fc6e…63a5fbb`, debug cert `00:33:E7:FA:…:47:D3` (supersedes the stale `73d39c9c…`/`78937196…` records), Room schema v15 (v14/v15 exported), `com.tpov.schoolquiz.mvp` 1 `0.1.0-isolated`, BuildConfig `MVP_EMULATORS=true`/App Check secret empty; reproducibility re-run identical (recorded durably after review).
- **1.4 quiescence fix (GREEN)** — `validate_preparation_preflight` now demands a non-null, non-array object whose sorted key set is exactly the five producer counters (`outboxRows, unresolvedArenaCommands, reservedArenaTickets, orphanedSubmitIntents, orphanedRatingIntents`), each value `typeof "number"` and `=== 0`, else exit 2 pre-cut. Owner's uncommitted RED oracle passes unmodified (docker node:22, no network): `offline settlement composition tests: PASS`, exit 0; revert-check reproduces the exact line-393 RED.
- **1.6 root ciCheck (RECORDED, FAIL)** — deterministic exit 1 at `:apps:android-next:processDebugGoogleServices`: the debug variant's `google-services.json` is a gitignored owner-local file absent on the VPS (only `src/mvp/` is tracked). No later gate executed (fail-fast); not fabricated, recorded with failing-task breakdown. Done-when 6's letter ("run at least once with the result recorded") is satisfied; going green needs the owner's file.

### Files changed

Commits on `continuation/mvp-candidate-20260926`: `8f8356ac` (run) → `1c120a19` (review patches) → finalization commit (this spec + regenerated epic context).
- `.maestro/mvp/arena-matrix/run-arena-offline-settlement-leaf.sh` — production quiescence gate fix (exact five zero counters).
- `_bmad-output/mvp-candidate/epic-candidate-rig/tickets.toml` — epic inspection deliverable: 6 story entries with statuses (1.1/1.2/1.4/1.6 done, 1.3/1.5 blocked-hitl).
- `_bmad-output/implementation-artifacts/spec-1-candidate-rig.md` — this spec, triage log, deferred list, auto run result.
- `_bmad-output/implementation-artifacts/epic-1-context.md` — regenerated epic context cache (stale monetisation-era content replaced).
- `docs/agents/2026-10-04-mvp-apk-identity.json` — candidate identity incl. reproducibility results.
- `docs/agents/2026-10-04-epic1-rig-run.json` — per-story run record with deviations and containment.
- `docs/agents/evidence/2026-10-04-demo-backend-restore/` — restore result JSON (env identity + lifecycle added in review), raw log, per-file export hashes.
- `docs/agents/evidence/2026-10-04-quiescence-preflight-fix/contract-test-verification.md` — green + revert-check evidence.
- `docs/agents/evidence/2026-10-04-root-cicheck/cicheck-result.json` — ciCheck record with inlined failure output.

### Review findings breakdown

22 findings (blind-hunter 15, edge-case 3, verification-gap 4): patched 9 (two medium entries — decisive outputs inlined into committed records — plus one medium environment-identity/lifecycle entry and six low precision fixes; applied in `1c120a19`); deferred 3 (two entries: ciCheck RED baseline + follow-up tracking; quiescence-matrix oracle durability — both root-caused outside this run's owned surfaces); rejected 10 (5 false with refutations in the triage log; 5 low whose fix would edit this build's spec — corrections durably recorded in the run records instead).

### Follow-up review recommendation

`true` — two medium patch entries were applied. Named unverified risk: the `reproducibility.results` rerun entry (4m 52s build) is transcribed from the untracked `assemble-mvp-rerun.log`; the orchestrator independently reproduced the primary APK hash, but not that specific log's contents.

### Verification performed

- Contract test (docker, no network, repo read-only) re-run by the orchestrator: exit 0, `offline settlement composition tests: PASS`.
- Restored backend re-checked live: container Up; auth `recordsCount: 98`; wallet doc serving `lifePoints`; both lesson docs and `arena_seasons/5990-Q4` HTTP 200.
- `assembleMvp` re-run by the orchestrator: BUILD SUCCESSFUL, APK SHA-256 identical (`eeb8fc6e…`), 42,038,981 B.
- `ciCheck` re-run by the orchestrator: exit 1, same failing task, same error — deterministic RED as recorded.
- I/O matrix audit: all malformed-payload rows driven by the 17-case loop in the unmodified owner test (ran, PASS); NaN unreachable via JSON (stringify→null; same tested typeof/===0 branch); export-hash-mismatch row is a procedural gate whose positive path (`MANIFEST_MATCH`) is in the restore log.
- Review patches: JSON parse checks, byte-identity of inlined ciCheck output vs run logs, diff review by the orchestrator.
- `git status` after the run: only the documented owner baseline plus this run's committed files.

### Residual risks

- Root ciCheck stays RED until the owner supplies the untracked debug `google-services.json` (or accepts the recorded RED baseline); later ciCheck legs (tests, detekt, ktlint, scriptsTest) remain unexercised on the VPS — and node/npm availability there is unverified, which may surface additional failures.
- The malformed-quiescence oracle lives only in the owner's uncommitted test file; until they commit it, the committed tree has no automated coverage of the new gate.
- Stories 1.3/1.5 await the owner's physical-device session; the restored backend container keeps serving on 18090/19090 under owner ownership.
