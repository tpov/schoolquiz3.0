# Epic 1 Context: Candidate and rig: restore, identify, measure

<!-- Generated from planning artifacts. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Establish the platform baseline every later epic depends on: the demo Firebase backend restored from the verified saved export, one identified MVP candidate (source HEAD, backend, APK, Room schema, config, flags) whose install over the populated Samsung installation provably retains user state, inventoried physical devices, and a Maestro rig able to measure ordinary-lesson and Arena-preparation runs on a real phone. The known RED quiescence-preflight regression gets its production fix with the regression green, and root `ciCheck` runs at least once on the candidate with the result recorded. Without this baseline no new acceptance claim is valid — all later epics run against this restored backend and this identified candidate.

## Stories

- Story 1.1: Restore demo backend from the verified export into a new directory
- Story 1.2: Identify and record the candidate; reproduce it with assembleMvp
- Story 1.3: Install the candidate over the populated installation, retaining state (CAP-20)
- Story 1.4: Fix the quiescence-preflight regression to GREEN
- Story 1.5: Produce the device inventory; record missing settings as external dependencies
- Story 1.6: Run root ciCheck on the candidate and record the result

## Requirements & Constraints

- CAP-20: installing the new candidate over a populated installation retains content, history, authentication, installation identity and pending operations. Restore never substitutes an empty environment for missing/damaged input and never destroys the original export; rollback preserves confirmed financial history.
- Restore safety: re-verify Firebase and export hashes immediately before restoring; absence or corruption of the input stops the run; the saved export is read-only; any new data is exported separately first.
- Quiescence preflight gates the run: exactly five known counters, each numeric zero. A malformed preflight (missing/extra/nonzero/mistyped counters) must stop before Maestro, before preparation and before any network change.
- Scope boundary: environment, tooling and candidate identity only. Ordinary and Arena feature journeys belong to their own epics; the rig here only proves they can be measured. Storage/disk work stays with the owner.
- Historical PASS (including the archived E2B result and the 27 September summary) is not acceptance evidence for this candidate; evidence is re-produced against the identified candidate.
- Missing device/Play settings are recorded as explicit external dependencies and do not block the other stories.

## Technical Decisions

- Architecture: KMP + Decompose + Compose + Koin; no Hilt/ViewModel. Authoritative Git lives on the VPS (SSH alias `rnsrv`); active checkout `/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate`, branch `continuation/mvp-candidate-20260926`, verified HEAD `1e5961263a4c1ac85ad03eb7894a51bf44228a4a` (last commit: Arena evidence readers accept only exact Room v14/v15 pairs; legacy recovery v14-only). Do not confuse with the other server checkout at `/Users/tpov/schoolquiz3.0` (different HEAD `d9fb4cfa`, own unfinished edits).
- Build: `./gradlew :apps:android-next:assembleMvp` on the VPS with OpenJDK 27 (`/opt/jdks/jdk-27`), Gradle 9.8.0, `--no-parallel --max-workers=1`, 2 GiB Gradle heap, in-process Kotlin compiler; unconstrained builds get OOM-killed. Pins: AGP 9.3.1, Kotlin 2.4.20, KSP 2.3.12, compileSdk 34, Android SDK root `/workspace/infrastructure/android-toolchain/sdk`. Reference APK: `apps/android-next/build/outputs/apk/mvp/android-next-mvp.apk`, 42,038,981 bytes, SHA-256 `52c13e24582906d261f24e743d9f89e6fdba6fed0cd90f698fc797728c13d847`, isolated test-signing certificate SHA-256 `73d39c9caeac685788d5f466dc7668e60be42445879f2883a124c32230db81d9`. That APK was built at the earlier checkpoint HEAD `15fedc22…`, so re-verify APK↔HEAD correspondence when recording candidate identity (identity binds source/backend/APK/schema/config/flags).
- Devices (as of 2026-09-28, re-verify before use): Samsung Galaxy A14 (SM-A145F), serial `RF8W80MBR3Z`, Android 15, USB to the local Mac only — not visible to server ADB; installed `com.tpov.schoolquiz.mvp` versionCode 1 (`0.1.0-isolated`); ADB reverse only ports 9099 and 8080. Pixel `adb-56271FDCH00C5S-ow9AvR._adb-tls-connect._tcp`. Physical devices only, no emulator; one heavy build at a time; all work/artifacts stored on the VPS.
- Backend: running containers `schoolquiz-course-device-20260927` (VPS loopback 18081/19098) and `schoolquiz-lesson-device-20260927` (18080/19099), Auth/Firestore only, no Functions; `firebase.mvp.json` plans Functions Node22/5001 but `functions/node_modules` is absent. Recreate only the demo Firebase container, importing the saved export at `/workspace/infrastructure/android-toolchain/firebase-export-staging/arena-e2b-cycle4-20260913/export` (12 files, manifest SHA-256 `2e4237d84530f63520703c519803e3e4edd6f629fa8259e920bc4e7ba9e898ff`), directing the new export into a fresh directory and preserving network/version/resource limits. Export fitness is unverified — the restore entry verifies it or stops.
- Quiescence fix: RED test `.maestro/mvp/arena-matrix/offline-settlement-contract.test.js` (fails `AssertionError: unsafe quiescence passed: {}; 0 !== 2`); production defect in `run-arena-offline-settlement-leaf.sh` around lines 490–508, where `Object.values(q).some(...)` accepts an empty/partial counter object. Fix shape: require exactly the five known counters, each numeric zero. Tests run in Docker `node:22-bookworm-slim` (Node 22.23.1), repo read-only, network none.
- Rig (continuation plan steps 0–2): keep a continuation registry (HEAD, files, tool versions, containers, source/APK/export hashes); sequential Maestro scenarios (prepare/start/answer/restart/finish/rate) with a launcher taking explicit device/scenario/candidate, unique result directories, read-only context reader and a receipts/financial-journal oracle; selectors from the actual UI; per-run context carries runId, account, attempt identifiers and candidate hashes — old receipts never validate a new run. Existing flows live under `.maestro/mvp/`.
- Pre-existing uncommitted changes persist and are not yours to revert silently: `.maestro/mvp/06-open-ordinary-lesson-uk.yaml`, the checkpoint JSON, `local.properties`, untracked `docs/agents/evidence/` dirs. The account-bearing-emulator prohibition is scoped to `/srv/schoolquiz-mvp-tools-20260906/`; evaluate its reach together with the owner-permitted demo import.
- Update order: server → compatible migrations/new client → flags → scenarios → owner review (migrations follow the actual schema; no destructive fallback/uninstall/reset; `clearState:false`; bounded waits).

## Cross-Story Dependencies

- 1.1 (restored backend) and 1.2 (identified candidate) precede 1.3: the install-over-populated-device proof needs both, and retained authentication/pending operations are only meaningful against the restored backend.
- 1.6 (root ciCheck) runs on the identified candidate from 1.2; the offline launcher rig is trustworthy for measurement only after 1.4 is green.
- 1.5 is independent, but its inventory (incl. GMS/Play state) is a recorded output of this epic, not a blocker for the others.
- Downstream: every later epic consumes this epic's outputs — epic 2 needs the identified candidate, restored backend, device inventory and Maestro rig; epic 3 needs the rig and restored demo accounts/fixtures; epic 7 needs the device/Play inventory; final acceptance (epic 10) needs the single identified candidate this epic fixes.
