# Account round-trip isolation (story 2.3)

- Result: PASS. Orchestrator gate ran `:shared:core:outbox:test` together with
  `:shared:feature:internet:auth:data:test`, `:shared:feature:internet:profile:data:test` and
  `:platform:firebase:test` — all green. The gate was executed by the orchestrator, not by the
  implementer (implementer runs were point-compile only — `:shared:core:outbox:compileTestKotlinJvm`
  and `:shared:core:outbox:compileKotlinJvm`, both BUILD SUCCESSFUL — per the night-window rule).
- Date: 2026-10-06.
- Story: `epic-ordinary-delivery` 2.3 `account-roundtrip-isolation` (covers CAP-19); done-when: an
  A→B→A account switch and a new session for the same UID preserve ownership — no foreign actions,
  no private data, no double settlement. Proof form mandated by the ticket: JVM tests of
  `AccountSwitchGuard` (`flushBefore`/`forgetAfterSwitch`/`switchAndForget`), of the
  `OutboxSession.generation` change, and of the engine owner gates.

## What changed and why

The production code was already in the tree (ticket status note: "AccountSwitchGuard и
owner/epoch-гейты в дереве"), so the story is delivered by the proof tests, like 2.2. No production
file was touched.

- `shared/core/outbox/src/commonTest/.../AccountSwitchGuardTest.kt` — the guard's `flushBefore` and
  `forgetAfterSwitch` were already covered (23 tests); `switchAndForget` had none. Added four:
  1. **A→B**: the sign-in landing on another account forgets the departing owner — queue records in
     every state (`WAITING`/`CONFLICT`/`QUARANTINED`) plus owner traces — and never the arriving
     one (the `!= newUid` filter is what keeps the arrival's data).
  2. **A→B→A return-to-same-uid**: `switchAndForget([B, A]) { A }` forgets only the intermediate
     owner; the returnee's records (all states) and his own traces (`ANSWER_HISTORY`) survive — a
     new session of the same UID keeps ownership, the roundtrip is not a loss (AD-8).
  3. **Failed sign-in**: the error propagates and nothing is removed — an unfinished sign-in leaves
     the previous owner the acting one, so erasing his traces has no justification.
  4. **Blank/duplicate previous uids** are filtered; each real predecessor is forgotten exactly
     once.
  The `flushBefore`/`forgetAfterSwitch` suites were extended in earlier stories and are part of
  this module's gate run (same compilation), so the ticket's triad guard coverage is complete here.
- `shared/core/outbox/src/jvmTest/.../AccountRoundtripIsolationTest.kt` — new end-to-end JVM proof
  against the shared `OutboxEngine`/`AccountSwitchGuard` and the common `FakeOutboxStore`, four
  cases:
  1. **The A→B→A roundtrip**: A's `a1` settles, `a2` stays (no network) — `flushBefore(A)` counts
     it into the warning; consent → `switchAndForget([A], signIn→B)` removes A's queue and traces
     after the sign-in, under the engine's pass mutex; B's `b1` settles under B's session; the
     return `flushBefore(B)` warns about B's private drafts (no queue left) and
     `switchAndForget([B], signIn→A)` removes them; the cleanup log reads
     `[queue:A, traces:A, queue:B, traces:B]` — each cleanup is queue-then-traces, and the
     returnee is never forgotten. After the full circle the queue holds no foreign row, A's new
     session (`generation` 3, not 1 — epochs are counted, not uid-equal) settles his new `a3`
     exactly once, and the receipt ledger holds exactly one authoritative receipt per settled
     mutation, each under the record's own owner — no double settlement.
  2. **New session of the same UID**: a re-auth event bumps the epoch while a response is in
     flight; the late success of the old session is not acknowledged — no receipt persisted, no
     record removed (engine `sessions.current() != session` gate at the transport outcome); the
     next pass under the new epoch delivers the record again under the *same* mutation key (AD-2),
     one receipt, one removal — server idempotency sees one effect, the client one settlement.
  3. **Engine owner gates**: a pass under another owner's session and a pass with no session at
     all (`OutboxSessionProvider.current() == null`, e.g. signed-out) examine nothing and send
     nothing; the rows stay intact for their owners.
  4. **Step-level gate**: a foreign row that reaches the due page (simulated legacy query without
     the owner filter) is returned without a transport attempt, untouched in every field, and
     cannot wedge the pass — the run terminates with the owner's row settled.
  The epoch model mirrors `FirebaseOutboxSessionProvider`: sign-ins count auth events, so
  A→B→A lands on `generation` 3 while `FirebaseOutboxSessionProviderTest` already proves the same
  counting against the real provider (same-uid auth events invalidate the epoch).

## What this confirms

Through a full A→B→A round trip and through re-authentication under the same UID, every queued
action stays with the account that created it — nothing of another owner is ever handed to the
transport, the departed owners' private rows and traces are removed only after a successful sign-in
and in the fixed queue-then-traces order, and no mutation is settled twice: the late response of a
superseded session is discarded, and the surviving owner's session redelivers under the original
idempotency key to a single authoritative receipt.

## What this gate does NOT prove

- No instrumented/device tests: nothing ran on a physical device — no second-device or real
  two-account round trip was exercised; the ticket's `hitl = false` note scopes this story to
  guard/engine/session-level proof, and the on-device journey (switch account on a populated
  install) is story 2.7 with the epic-candidate-rig.
- No online/backend verification: settlement single-ness is proven against a fake transport whose
  receipts honor the identity contract; no call reached the live demo-backend receiver
  (`MUTATION_HANDLERS` in `functions/index.js`) — its dedup and consent checks are stories
  2.8/2.6/acceptance.
- The full `ciCheck` (whole-repo `test`/`allTests`, `detekt`, `ktlintCheck`, `assembleDebug`) was
  not run in this story; the gate covered the four named modules only. The ktlint filter of the
  KMP convention plugin excludes `*Test/` paths, so the new test sources were not linted either.
- No Firebase-auth integration beyond existing suites: `FirebaseOutboxSessionProvider` epoch
  behavior and the platform-side switch order have their own suites (`FirebaseOutboxSessionProviderTest`,
  `AccountSwitchTest` in `platform/firebase`) which the gate re-ran green, but those modules were
  not modified by 2.3 — their green runs confirm the shared proof composes with the real provider,
  nothing new.

## Environment repairs, left uncommitted on purpose

The same two machine-specific build-environment repairs as in 2.1/2.2 are present in the worktree
and are NOT part of the commit (per instruction they stay uncommitted):

- `gradle.properties` — `org.gradle.java.home=/opt/jdks/jdk-27`, memory caps
  (`-Xmx1400m`/`MaxMetaspaceSize=512m`, `workers.max=2`, `kotlin.daemon.jvmargs=-Xmx1024m`)
  after the host OOM-killed the build daemon with the old config.
- `local.properties` — `sdk.dir` repointed from the owner's Mac path
  (`/Users/tpov/Library/Android/sdk`) to `/workspace/infrastructure/android-toolchain/sdk`.

If a fresh machine needs to run the gate, the equivalents of both repairs must be reapplied.
