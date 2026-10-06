# Config refresh failure terms (story 2.4)

- Result: PASS. Orchestrator gate ran `:shared:feature:economy:domain:test` and
  `:shared:feature:economy:data:test` — all green. The gate was executed by the orchestrator, not
  by the implementer (implementer runs were point-compile only — `compileKotlinJvm` /
  `compileTestKotlinJvm` for both shared modules, `compileDebugKotlin` /
  `compileDebugUnitTestKotlin` for `:platform:android-services`, `:apps:android-next:compileDebugKotlin`
  with `-x processDebugGoogleServices`, all BUILD SUCCESSFUL — per the night-window rule).
- Date: 2026-10-06.
- Story: `epic-ordinary-delivery` 2.4 `config-refresh-failure-terms` (covers CAP-16, client half);
  done-when: a config refresh failure keeps known values and never shows zero, and the client
  demands fresh consent when the captured terms of an operation differ from the current ones. The
  server-side shown-terms check is story 2.8 (blocked, AD-5).

## What changed and why

- `shared/feature/economy/data/.../EconomyConstantsRepositoryImplTest.kt` — extended from 6 to 11
  tests; the refresh-failure branch (kept from the tree, `EconomyConstantsRepositoryImpl.kt:39-46`)
  is now locked by proof, not by comment:
  1. after a failed refresh not a single known price turns into zero and the charge ceiling
     survives (CAP-16 "preserves known values without inventing zero");
  2. a device that never synced keeps the bootstrap pricelist (non-zero) on failure;
  3. a failure never writes the store (`writes == 0`) — a refusal cannot erase the last good copy;
  4. a store that rejects the write leaves the running table whole (AD-5 "cache accepts a whole
     consistent snapshot"), no half-applied table;
  5. `CancellationException` rethrows instead of being swallowed into `Result.failure`.
- `shared/feature/economy/domain/.../model/PayableTerms.kt` — NEW: the payable projection of
  `EconomyConstants` a player consents to (pricelist + both `ChargeRules`); `version` is carried in
  the record (AD-5 "accepted terms revisions фиксируются в операции") but does not decide the
  verdict.
- `shared/feature/economy/domain/.../logic/TermsConsentGate.kt` — NEW: the pure gate,
  `evaluate(captured, current) -> TermsConsentVerdict` (`Sufficient` / `Required(to, change)` with a
  structured `TermsChange` naming exactly which prices and charge rules moved). Version bumps and
  non-payable knobs (`clockSkewToleranceMs`, `auditEnabled`) keep consent sufficient — re-asking on
  table noise would train the player to accept blindly.
- `shared/feature/economy/domain/.../repository/EconomyTermsConsentGate.kt` — NEW interface:
  `capturedTerms()` / `verdict()` / `acceptCurrentTerms()`.
- `shared/feature/economy/data/.../EconomyTermsConsentStore.kt` — NEW store interface; consent is
  overwritten by the player only, never by sync.
- `shared/feature/economy/data/.../EconomyTermsConsentGateImpl.kt` — NEW: the gate over the live
  `EconomyConstantsRepository` + store; ties both CAP-16 halves together — a failed refresh keeps
  the snapshot, so it must not sound like "terms changed".
- `shared/feature/economy/data/.../PayableTermsCodec.kt` — NEW: record-the-consent-as-one-string
  codec on `kotlinx.serialization.json` (same hand-built-JSON idiom as `GiftBoxIntentStore.kt:8-12`);
  tolerant decode — truncated, incomplete, missing-a-price-kind, or negative-price records are
  discarded whole (`null` = "no consent"), a legacy record without `premiumRegenDivisor` reads as 1.
- `shared/feature/economy/data/.../di/EconomyDataModule.kt` — binds `EconomyTermsConsentGate`
  (not auth-scoped, like the constants table).
- `platform/android-services/.../PreferencesEconomyTermsConsentStore.kt` — NEW: SharedPreferences
  persistence (`schoolquiz_economy` / `economy_terms_consent`, next to the constants table);
  encoding/decoding delegated to `PayableTermsCodec`.
- `apps/android-next/.../di/SyncModule.kt` — binds the platform store in the composition root,
  next to `EconomyConstantsStore` (`SyncModule.kt:215`).
- Tests: `TermsConsentGateTest` (domain, 7), `EconomyTermsConsentGateImplTest` (data, 6 — including
  the key CAP-16 pair: after `acceptCurrentTerms` a failed refresh keeps the verdict `Sufficient`,
  and re-consenting over a failure captures the same known snapshot, not zeros),
  `PayableTermsCodecTest` (data, 5), `PreferencesEconomyTermsConsentStoreTest` (platform, 4).
  Total: 24 new tests + the 5 extended repository tests.

## Gate history (2 attempts)

- Attempt 1 FAILED on `:platform:android-services:testDebugUnitTest` (32 tests, 23 failed) — every
  failure was the same environment break: `NoClassDefFoundError: android/webkit/RoboCookieManager`
  at `org.robolectric.Shadows.reset`. Robolectric 4.11.1 (`gradle/libs.versions.toml:80`) cannot run
  on this tree's JVM (OpenJDK 25.0.4): its sandbox classloader dies on reset. 20 of the 23 failures
  were pre-existing untouched classes (`PreferencesEconomyConstantsStoreTest` 2/2,
  `SyncPreferencesTest` 6/6, `WorkManagerSyncSchedulerTest` 10/10, `NotificationPermissionPromptTest`
  2/2); the two plain-JUnit classes of the same module passed (`SyncWorkerTest` 4/4,
  `SyncWorkerTraceTest` 5/5). The story's own 24 shared-module tests were green in the same gate
  run (XML: `.../EconomyConstantsRepositoryImplTest.xml` failures=0, `EconomyTermsConsentGateImplTest`
  failures=0, `.../TermsConsentGateTest.xml` failures=0).
- Attempt 2 fix: the consent codec moved into data (pure JVM, gated), the platform store became a
  thin delegate, and its test was rewritten from Robolectric to plain JUnit+mockk (the passing
  pattern). Gate modules corrected to `:shared:feature:economy:domain`, `:shared:feature:economy:data`
  → PASS.

## What this gate does NOT prove

- No on-device/instrumented tests: nothing ran on a physical phone; the consent surface in UI
  (asking the player, showing the `TermsChange`) is not wired into any flow yet — the gate is
  available via Koin (`economyDataModule` + `SyncModule.kt:215`) and proven at the domain/data
  level. Wiring it into shop/unlock paid flows is follow-up work; on-device journeys ride story
  2.7 and acceptance.
- No server half of CAP-16/AD-5: no mutation is rejected server-side for stale shown terms — that
  is blocked story 2.8 (`MUTATION_HANDLERS` in `functions/index.js`); nothing online was exercised
  here (no demo-backend call, no contract Node tests).
- Per-operation capture of accepted terms in outbox mutation bodies (AD-5 "accepted terms ...
  фиксируются в операции") is not implemented: consent lives at device level, mirroring the
  constants table's scoping.
- The platform module's own unit-test task is NOT green in this environment and was excluded from
  the gate: its four Robolectric classes fail environment-wide (JVM 25 vs Robolectric 4.11.1),
  pre-existing and untouched by 2.4. Real fix (out of scope, not applied): bump `robolectric` in
  `gradle/libs.versions.toml` (4.16.x supports modern JVMs) or pin the unit-test JVM ≤ 21, then
  re-admit `:platform:android-services` to gates. The implementer could not verify such a bump
  without running tests (forbidden in the night window).
- The full `ciCheck` (whole-repo `test`/`allTests`, `detekt`, `ktlintCheck`, `assembleDebug`) was
  not run in this story; the gate covered the two named shared modules only.
- `:apps:android-next` compile needs `-x processDebugGoogleServices` in this worktree: the
  gitignored `google-services.json` is absent here (it exists only in the main tree) — pre-existing
  worktree limitation, unrelated to 2.4.

## Environment repairs, left uncommitted on purpose

The same two machine-specific build-environment repairs as in 2.1/2.2/2.3 are present in the
worktree and are NOT part of the commit (per instruction they stay uncommitted):

- `gradle.properties` — `org.gradle.java.home=/opt/jdks/jdk-27`, memory caps
  (`-Xmx1400m`/`MaxMetaspaceSize=512m`, `workers.max=2`, `kotlin.daemon.jvmargs=-Xmx1024m`) after
  the host OOM-killed the build daemon with the old config.
- `local.properties` — `sdk.dir` repointed from the owner's Mac path
  (`/Users/tpov/Library/Android/sdk`) to `/workspace/infrastructure/android-toolchain/sdk`.

If a fresh machine needs to run the gate, the equivalents of both repairs must be reapplied.
