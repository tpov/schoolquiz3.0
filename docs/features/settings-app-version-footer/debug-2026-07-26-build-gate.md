---
date: 2026-07-26
feature: settings-app-version-footer
problem: build gate blocked during implementation
status: phase-1-complete
context: implement
---

# Debug Phase 1: Build Gate Blocked During Implementation

## Problem

During phase-01 implementation, the review owner reported that the UI/test changes were applied, but the Gradle build gate never reached `PASSED`, so mandatory reviewers were not launched.

Symptom source:

- Worker report: implementation files changed and non-Gradle guards passed, but Gradle/build gate is blocked by environment/repo hygiene. See `docs/features/settings-app-version-footer/run/agents/phase-01-frontend-dev.out:37`.
- Current run state: phase-01 is blocked with a `build_environment` blocker. See `docs/features/settings-app-version-footer/run/pipeline-state.json:46`.

## Phase 1 Context

### Feature docs

- SCH-2 is a Light, client-only UI enhancement; it is not meant to add domain/data/storage/Firebase logic. See `docs/features/settings-app-version-footer/0-spec.md:15` and `docs/features/settings-app-version-footer/0-spec.md:32`.
- The feature must pass app compile/JVM/instrumented/canonical validation where the environment permits it. See `docs/features/settings-app-version-footer/2-grounding.md:121`.
- The app-side Firebase client config is expected at `apps/android-next/google-services.json`. See `.claude/PROJECT-CONTEXT.md:10`.
- `google-services.json` is intentionally ignored as local configuration. See `.gitignore:19`.
- Invariants relevant to feature code remain presentation-boundary/scaffold-ownership checks, not domain or data rules. See `docs/features/settings-app-version-footer/0-spec.md:196`.

### Runtime logs

Skipped. This is a build/provisioning failure, not a runtime UI/device symptom; `adb devices -l` returned no connected devices.

### Past reports

No `debug-*.md` or `fix-spec-*.md` files existed for this feature before this report. A doc-only scan found older cross-feature documentation noting that missing Google Services config is a blocker-class build/provisioning issue, but no prior report for invalid SDK path or AppleDouble sidecars.

## Evidence Collected

### E1 — Current Gradle failure reproduces before SCH-2 feature code is validated

Command:

```bash
./gradlew :android:feature:local:settings:presentation:compileDebugKotlin --no-configuration-cache
```

Result: failed in `:buildSrc:compileKotlin` before reaching settings feature compilation.

Key failure:

```text
Execution failed for task ':buildSrc:compileKotlin'.
> Failed to load cache entry ... Could not load from local cache: Unable to delete directory '/Volumes/EXTERNAL/schoolquiz3.0/buildSrc/build/classes/kotlin/main'
    Failed to delete some children.
    - /Volumes/EXTERNAL/schoolquiz3.0/buildSrc/build/classes/kotlin/main/._AndroidApplicationConventionPlugin...
```

This matches the worker's observed `buildSrc cleanup fails on regenerated ._*.class sidecars` blocker. See `docs/features/settings-app-version-footer/run/agents/phase-01-frontend-dev.out:45`.

### E2 — AppleDouble sidecars are present and the workspace is on ExFAT

Commands:

```bash
find . -path './.git' -prune -o -name '._*' -print | wc -l
file android/core/navigation/build/intermediates/compile_r_class_jar/debug/generateDebugRFile/._R.jar
diskutil info /Volumes/EXTERNAL
```

Results:

- `631` AppleDouble-style `._*` files outside `.git`.
- Sample `._R.jar` reports `AppleDouble encoded Macintosh file`.
- `/Volumes/EXTERNAL` reports `File System Personality: ExFAT`.

This is a build-environment/workspace contamination issue. Cleaning `._*` on ExFAT is likely temporary because macOS can recreate AppleDouble files for metadata that ExFAT cannot store natively.

### E3 — Local SDK config is wrong for this machine

Commands:

```bash
sed -n '1,20p' local.properties
ls -ld /Users/tpov/Library/Android/sdk /home/tpov/Android/Sdk
```

Results:

- `local.properties:8` sets `sdk.dir=/home/tpov/Android/Sdk`.
- `/home/tpov/Android/Sdk` does not exist.
- `/Users/tpov/Library/Android/sdk` exists.

This confirms the worker's SDK-path blocker. See `docs/features/settings-app-version-footer/run/agents/phase-01-frontend-dev.out:41`.

### E4 — App-level Google Services config is absent

Commands:

```bash
ls -la apps/android-next/google-services.json
git ls-files --error-unmatch apps/android-next/google-services.json
```

Results:

- `apps/android-next/google-services.json` is absent.
- It is not tracked by Git and is ignored by `.gitignore:19`.
- A local presence scan found only unrelated `google-services.json` files from other projects; no authorized `school-quiz-89336951` / `com.tpov.schoolquiz.next` config was found in the searched local candidates.

This confirms the worker's app-level gate blocker. See `docs/features/settings-app-version-footer/run/agents/phase-01-frontend-dev.out:44`.

## Classification

- Category: `build-environment` / `provisioning`
- Severity: `blocker`
- Scope: environment recovery, not SCH-2 feature logic
- Root cause status: `CONFIRMED` for the currently observed failure chain

## Hypotheses

| ID | Hypothesis | Status | Evidence |
|---|---|---|---|
| H1 | ExFAT workspace + recurring AppleDouble `._*` sidecars corrupt Gradle/Kotlin/Android intermediates and can fail build cleanup/resource tasks before feature code compiles. | Confirmed current blocker | Reproduced `:buildSrc:compileKotlin` failure on `._*.class`; 631 sidecars outside `.git`; sample sidecar is AppleDouble; workspace is ExFAT. |
| H2 | `local.properties` points to a Linux SDK path that does not exist on this macOS machine. | Confirmed blocker | `local.properties:8` vs existing `/Users/tpov/Library/Android/sdk`. |
| H3 | App-level Gradle gates need an ignored `apps/android-next/google-services.json`, but it is absent. | Confirmed blocker | PROJECT-CONTEXT requires that path; `.gitignore` ignores it; file is absent and untracked. |

## Team Composition Proposal

Deep feature-code debugging is not recommended for the current symptom: the reproduced failure occurs in `buildSrc` cleanup before SCH-2 UI code is compiled.

If the next node performs recovery/validation, use a small environment-focused team:

- Mandatory: lead/backend-dev-equivalent owner for local Android SDK + Gradle/build environment recovery.
- Conditional: frontend-dev only if a clean environment later reveals a real compile/UI/test failure in the SCH-2 changed files.
- Conditional: firebase-dev/security-reviewer only if the provided `google-services.json` source/package/project is ambiguous or someone proposes committing it.
- Do not raise domain/data/server/concurrency/logcat reviewers for this symptom.

Required recovery evidence:

1. `local.properties` points to the usable local Android SDK.
2. Presence-only verification that `apps/android-next/google-services.json` exists and Gradle validates it; do not print or commit contents.
3. Active validation happens from an APFS/local workspace, or another explicitly documented containment that prevents AppleDouble sidecars from appearing in project/build intermediates.
4. `find ... -name '._*'` returns zero in the active project before Gradle validation.
5. Staged gates run in order: `:buildSrc:compileKotlin`, `:apps:android-next:assembleDebug --no-configuration-cache`, relevant tests, then `./gradlew ciCheck --no-configuration-cache`.
6. `git status --short` confirms SCH-2 implementation changes are preserved and no generated artifacts enter the diff.

## Phase 1 Decision

Early-out to Fix Spec.

Reason: the root cause of the reported symptom is evident from the worker report plus reproduction: build gate failure is caused by environment/provisioning blockers (AppleDouble sidecars on ExFAT, invalid SDK path, missing ignored Firebase client config), not by unknown SCH-2 feature behavior. A deep feature-code debug team would not produce better signal until the environment is recovered.

No fix was applied in this debug pass.
