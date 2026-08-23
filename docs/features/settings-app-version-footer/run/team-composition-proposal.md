## 1. **Mandatory teammates per phase**

- `phase-01` — `frontend-dev`: required because this is a UI-only vertical slice that threads app build metadata through app/app-shell/settings Compose parameters and renders the settings footer. Scope is limited to the approved UI/presentation plan; no domain/data/storage/network/DI work.
- `phase-01` — `test-dev`: required by hard rule because production code changes are planned. Owns existing app-shell androidTest updates/guards and compile-time stale-call-site coverage.
- `phase-01` — `code-reviewer`: required to review the cross-module Kotlin/Compose API changes, stale call-site risk, test changes, and drawer preservation.
- `phase-01` — `architect-reviewer`: required because the phase is tagged `complex` and crosses `apps/android-next`, `android/feature/app-shell/presentation`, and `android/feature/local/settings/presentation`; must verify app `BuildConfig` stays app-layer-only, Compose screens remain view functions, and no new domain/data/DI/scaffold coupling appears.
- `phase-01` — `security-reviewer`: required by pipeline hard rule for every phase. Scope is narrow: confirm the footer is passive metadata display only and does not add storage, analytics, hidden interaction, network, auth, debug gating, or sensitive data exposure.
- `phase-01` — `completeness-reviewer`: required to check the approved acceptance criteria end-to-end: exact `v<versionName> (<versionCode>)` label, visible-bottom pinned placement, small low-emphasis centered styling, display-only behavior, debug/release availability, no stale call sites, and drawer behavior unchanged.

## 2. **Conditional teammates per trigger**

- `integration-tester` — trigger: after implementation, if Compose/androidTest coverage cannot confidently prove visual pinned-bottom behavior, or if lead wants manual UX smoke. Scope: launch app/emulator, open settings, verify footer placement/visual treatment/display-only behavior, then open drawer and verify existing drawer version/About/dev-mode behavior.
- `backend-dev` — trigger: only if implementation unexpectedly requires edits to scaffold-owned files (`build.gradle.kts`, `libs.versions.toml`, `settings.gradle.kts`, `gradle.properties`, Gradle wrapper, or root `AndroidManifest.xml`) and that plan deviation is explicitly approved. Reason: scaffold ownership invariant; default plan says no Gradle/dependency/backend changes.
- `concurrency-reviewer` — trigger: only if the implementation deviates from the approved overview by touching coroutines, Flow collection, `LaunchedEffect`, lifecycle callbacks, channels, or shared mutable state. Reason: phase overview explicitly says no concurrency-review for the planned immutable parameter threading/passive text rendering path.
- `diagnostics` — trigger: build/test/runtime failure is not immediately explained by compiler output or test assertion text, or repeated fix-loop findings of the same class occur. Scope: classify the failure and recommend targeted next evidence; no code changes without lead/user approval.
- `code-analyst` — trigger: unresolved/stale signature propagation, unexpected cross-module import/coupling, Compose semantics/bounds test failure, or grep guard mismatch that needs static call-flow analysis.
- `log-reader` — trigger: connected device/emulator smoke produces a crash or runtime-only failure when opening settings/drawer. Evidence must include focused logcat around the action and the full stacktrace.
- `web-researcher` — trigger: only if a failure depends on current external Android/Compose/Gradle behavior not answerable from local docs and source. Not needed for the approved path, which uses existing project Compose and generated app `BuildConfig`.

## 3. **Which teammates NOT to spawn**

- `backend-dev` — do not spawn by default: Feature Domain Contract is N/A and the plan says no backend/data/domain/DI/storage/network/Gradle dependency work.
- `firebase-dev` — do not spawn: no Firebase, Firestore, auth, network, backend rules, or server-side contract is involved.
- `concurrency-reviewer` — do not spawn by default: `phase-01/overview.md` explicitly excludes concurrency-review and the planned change is immutable Compose parameter threading plus passive text rendering.
- `diagnostics` — do not spawn as part of the normal implementation/review team; use only for the concrete failure triggers above.
- `code-analyst` — do not spawn by default; use only for unclear static/call-flow failures.
- `log-reader` — do not spawn unless there is device/emulator runtime log evidence to inspect.
- `web-researcher` — do not spawn by default: no new SDK/API/platform behavior is planned.

## 4. **Scaling recommendations**

- Default: no scaling. One `frontend-dev`, one `test-dev`, and the mandatory reviewers are enough for the single atomic `phase-01` slice.
- Add `frontend-dev-2` only if the lead intentionally splits implementation into non-overlapping scopes: worker A handles app/app-shell metadata propagation, worker B handles settings footer layout/preview. Avoid parallel edits to the same `AppShellScreen.kt` or `DesignSettingsScreen.kt` regions.
- Add `test-dev-2` only if the existing app-shell androidTest file grows into independently separable areas and the lead can prevent same-file conflicts. Preferred split if needed: one test worker covers exact label/display-only semantics; the other covers pinned bounds/drawer/debug-release guards.
- Add `code-reviewer-2` only if the diff expands beyond the approved four-file scope or introduces scaffold/DI/navigation/lifecycle changes. Otherwise one code reviewer is sufficient.
- Do not scale backend/firebase/web/log roles for the approved path; those roles are conditional failure/scope-change tools, not implementation capacity.

## 5. **Debug hooks**

- Failure signal: Gradle compile error such as “no value passed for parameter `appVersionCode`”, unresolved `DesignSettingsScreen`/`AppShellScreen`/`AppShellContent`/`LocalTabContent`, or type mismatch between `VERSION_CODE` and the Compose API. Route to: `code-analyst`. Evidence required: full Gradle task output plus grep results for the affected call sites.
- Failure signal: grep guard shows app `BuildConfig` read in `android/feature/app-shell` or `android/feature/local/settings`, or a new reverse settings→app-shell/app dependency. Route to: `code-analyst`; escalate to `architect-reviewer` in the phase review. Evidence required: grep output with file paths and the relevant import/call lines.
- Failure signal: UI test fails because `v0.1.0 (1)` is missing, duplicated, not displayed, clickable/long-clickable, or has unexpected semantics actions. Route to: `code-analyst`. Evidence required: failing test name, assertion output, and Compose semantics tree if available.
- Failure signal: pinned-bottom bounds test fails, footer overlaps the last settings option, or footer behaves like a final scroll item. Route to: `code-analyst`; optionally add `integration-tester` if visual/manual confirmation is needed. Evidence required: root/container and footer bounds from the test, screenshot if available, and exact viewport/device configuration.
- Failure signal: drawer footer/About/developer-mode regression, including missing drawer `v<versionName>`, changed `onVersionTap`, or settings footer triggering drawer dev-mode behavior. Route to: `code-analyst`. Evidence required: failing test/smoke steps and grep output for `onVersionTap`.
- Failure signal: runtime crash on opening settings/drawer, e.g. Compose runtime exception, `NoSuchMethodError`, `ClassCastException`, or resource/theme crash. Route to: `log-reader` for stacktrace extraction and `diagnostics` for classification if the cause is not obvious. Evidence required: focused logcat, device/emulator details, app variant, and exact navigation steps.
- Failure signal: unexpected Koin missing binding, Room migration, network/Firebase, auth, lifecycle, or coroutine error during this feature. Route to: `diagnostics` because this indicates out-of-scope coupling or an unrelated pre-existing failure. Evidence required: full stacktrace/build output and the command or manual step that triggered it.

## 6. **Device/backend prerequisites**

- Backend/Firebase/Firestore/network/login/storage: none required for implementation or verification.
- Device/emulator: not required for compile gates or `assembleDebugAndroidTest`; required only for connected/manual smoke or if runtime/logcat evidence is needed.
- Build environment: Gradle commands from project context should be available to the implementation lead/coder after changes, especially `./gradlew ciCheck --no-configuration-cache`, debug/release app assemble, `test`, and `assembleDebugAndroidTest`.
- Test fixtures: existing app-shell androidTest surface is the planned verification surface; no new settings-module test dependencies should be required by default.

## 7. **Confidence**

High — the approved plan has exactly one phase, Feature Domain Contract is N/A, backend role input is `none`, production scope is limited to app/app-shell/settings UI plus one existing androidTest surface, and `phase-01/overview.md` explicitly tags the phase `complex` but not `concurrency-review`. Unknowns are limited to implementation-time details: exact Compose bounds tolerance for the pinned footer and whether the local environment can run all Gradle/device validation commands.
