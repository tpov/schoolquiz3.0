# Demo backend restore — raw evidence log (2026-10-04)

All commands executed on the VPS (`ssh racknerd`). Times UTC. Container: `schoolquiz-demo-firebase-20261004`.

## 1. Pre-restore staged export re-verification (~17:20Z)

Command: `cd /workspace/infrastructure/android-toolchain/firebase-export-staging/arena-e2b-cycle4-20260913/export && find "$PWD" -type f | sort | xargs sha256sum | sha256sum`

```
manifest=2e4237d84530f63520703c519803e3e4edd6f629fa8259e920bc4e7ba9e898ff
MANIFEST_MATCH
31379966 total
12
```

Matches the required `sha256OfSortedFileSha256Manifest`. 12 files, 31,379,966 bytes.
Per-file SHA-256 list (captured before touching anything): `staged-export-sha256.txt`.

## 2. Port and state pre-checks

`ss -tln | grep -E '18090|19090'` → no output (both free). Disk: 30G free on `/`.
Pre-existing containers `schoolquiz-course-device-20260927` and `schoolquiz-lesson-device-20260927` remain `Exited` and untouched. The Oct 3 export `firebase-exports/lesson-performance-run-20260927/` untouched.

## 3. Container creation

First `docker run` attempt (17:24Z) failed: firebase-cli could not spawn `java -version` — the original lesson-device container carried an explicit `PATH` including `/tools/jdk21/bin`; the failed container was removed (`docker rm -f`) and recreated with the full env below. No data directories were written by the failed attempt (emulators never started).

Final creation (17:25:41Z, container id a25b269bfdfa…, also re-created on restart by `docker start`):

```
docker run -d --name schoolquiz-demo-firebase-20261004 \
  -p 127.0.0.1:18090:8080 -p 127.0.0.1:19090:9099 \
  --memory 1468006400 \
  -e PATH=/tools/jdk21/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
  -e JAVA_HOME=/tools/jdk21 \
  -e FIREBASE_EMULATORS_PATH=/tools/firebase-emulators \
  -e JAVA_TOOL_OPTIONS=-Xmx640m \
  -e NODE_OPTIONS=--max-old-space-size=384 \
  -e GCLOUD_PROJECT=demo-schoolquiz-mvp \
  --entrypoint /tools/firebase-cli/node_modules/.bin/firebase \
  -v /workspace/infrastructure/android-toolchain/jdk21:/tools/jdk21:ro \
  -v /workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate:/repo:ro \
  -v /workspace/infrastructure/android-toolchain/firebase-cli:/tools/firebase-cli:ro \
  -v /workspace/infrastructure/android-toolchain/firebase-emulators:/tools/firebase-emulators:ro \
  -v /workspace/infrastructure/android-toolchain/firebase-export-staging/arena-e2b-cycle4-20260913/export:/tools/firebase-import:ro \
  -v /workspace/infrastructure/android-toolchain/firebase-exports/demo-restore-20261004:/tools/firebase-output-root \
  -v /workspace/infrastructure/android-toolchain/firebase-runtime/demo-restore-20261004:/tmp \
  node:22-bookworm-slim \
  emulators:start --config /repo/firebase.mvp.json --project demo-schoolquiz-mvp --only auth,firestore \
    --non-interactive --import /tools/firebase-import --export-on-exit /tools/firebase-output-root/export
```

Scoping note: only the mounts this restore needs are attached (firebase-cli, firebase-emulators, jdk21 — all read-only). No other account-bearing emulator paths (`/srv/...`) were used.

Startup log (excerpt):

```
i  firestore: Importing data from /tools/firebase-import/firestore_export/firestore_export.overall_export_metadata
✔  firestore: Firestore Emulator was started in standard edition.
i  auth: Importing config from /tools/firebase-import/auth_export/config.json
i  auth: Importing accounts from /tools/firebase-import/auth_export/accounts.json
│ ✔  All emulators ready! It is now safe to connect your app. │
│ Authentication │ 0.0.0.0:9099 │
│ Firestore      │ 0.0.0.0:8080 │
```

`curl` port check: `auth_port=200 firestore_port=200`.

## 4. Baseline (pre-save) spot-checks (~17:28Z)

Auth users:

```
curl -s http://127.0.0.1:19090/identitytoolkit.googleapis.com/v1/projects/demo-schoolquiz-mvp/accounts:query \
  -H 'Authorization: Bearer owner' -d '{}' → userInfoCount: 98
```

Firestore REST spot-checks on 127.0.0.1:18090 (`Authorization: Bearer owner`, owner mode):

- `lessons/ct_796451cb56504585ab586b3c8cf1a47e_lesson` → present (`themeId=ct_796451cb56504585ab586b3c8cf1a47e_theme`)
- `lessons/qcalc_59d41cdbdfb642c9ba0a47d14c661df7_lesson` → present (`themeId=qcalc_59d41cdbdfb642c9ba0a47d14c661df7_theme`)
- wallet: `users/1wqSlL9TOX1h28ifZGixKxBugdSJ` → `{"lifePoints":"1000","standardHearts":"10","goldHearts":"3","nolics":"1000"}`
- `arena_seasons/5990-Q4` → present, `status=FINALIZED`
- `history` → 0 documents (see "Deviations" below; also 0 post-restart — faithful to the staged export)

## 5. Control save (~17:38Z)

```
docker exec -w /tmp schoolquiz-demo-firebase-20261004 /tools/firebase-cli/node_modules/.bin/firebase \
  emulators:export /tools/firebase-output-root/control-save-1 \
  --config /repo/firebase.mvp.json --project demo-schoolquiz-mvp --non-interactive
→ i  Found running emulator hub for project demo-schoolquiz-mvp at http://127.0.0.1:4400
  i  Exporting data to: /tools/firebase-output-root/control-save-1
  ✔  Export complete
```

Two failed attempts first (recorded for the record, no side effects): (a) `--export-dir` is not an option in this firebase-tools build (path is positional); (b) running the exec from `/` or `/repo` breaks on `options.config.has(...)` (no config discovery) and on the read-only cwd (`Unable to obtain permissions for firebase-debug.log`). Working invocation uses a writable cwd (`/tmp`) plus explicit `--config /repo/firebase.mvp.json`.

Verification:

```
find .../control-save-1 -type f | wc -l → 12
auth users in control-save-1/auth_export/accounts.json → 98
```

## 6. Stop (export-on-exit) and restart (~17:41Z)

```
docker stop schoolquiz-demo-firebase-20261004
→ i  firestore: Stopping Firestore Emulator / i  auth: Stopping Authentication Emulator / i  hub: Stopping emulator hub
find .../export -type f | wc -l → 12
auth users in export/auth_export/accounts.json → 98
docker start schoolquiz-demo-firebase-20261004
→ i  firestore: Importing data from /tools/firebase-import/firestore_export/firestore_export.overall_export_metadata
```

Restart re-imports the read-only STAGED export (98-user baseline), as designed.

## 7. Post-restart spot-checks (~17:44Z) — all PASS

```
POST-RESTART auth users: 98
POST-RESTART wallet: {"lifePoints":"1000","standardHearts":"10","goldHearts":"3","nolics":"1000"}
POST-RESTART lessons/ct_796451cb…: present, themeId=ct_796451cb56504585ab586b3c8cf1a47e_theme
POST-RESTART lessons/qcalc_59d41cdb…: present, themeId=qcalc_59d41cdbdfb642c9ba0a47d14c661df7_theme
POST-RESTART arena_seasons/5990-Q4: present, status=FINALIZED
POST-RESTART history docs: 0
```

## Deviations from the spec text (recorded, no action)

1. Spec spot-check names vs live collection IDs in this backend: `seasons/5990-Q4` exists as `arena_seasons/5990-Q4`; wallet fields (`lifePoints`/`standardHearts`/`goldHearts`) live on `users/<uid>` (718 docs carry all three), not on `profiles/<uid>` (profiles carry `uid`/`nickname`/`syntheticCatalogRun`). The restored data is the hash-verified staged import; the spot-check targets were located where this backend actually stores them.
2. `history` contains zero documents in the restored backend, both before and after restart. The staged export's LevelDB files contain no `history` kind markers, so the canonical staged baseline has no history documents; the restore is byte-faithful (manifest hash verified before import).
3. `emulators:export` in this firebase-tools build takes the path positionally (spec's `--export-dir` flag does not exist) and needs a writable cwd with an explicit `--config`; see section 5.
