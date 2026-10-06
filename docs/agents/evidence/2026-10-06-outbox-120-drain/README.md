# The 120-action queue drains to authoritative results (story 2.2)

- Result: PASS. Orchestrator gate ran `:shared:core:outbox:test` (plus
  `:shared:core:sync`, `:shared:feature:lesson-runner:data`, `:shared:core:persistence:test`) —
  all green. The gate was executed by the orchestrator, not by the implementer (implementer runs
  were point-compile/dry-run only, per the night-window rule).
- Date: 2026-10-06.
- Story: `epic-ordinary-delivery` 2.2 `outbox-120-drain` (covers CAP-18); done-when: the 120-action
  queue drains to authoritative results without repeated manual sync; a cancelled or malformed
  response fabricates neither success nor duplicated effects. Proof form mandated by the ticket:
  a JVM run of `OutboxEngine` with a fake transport over 120 records (3 batches of 50) plus the
  quarantine/conflict/over-ripening branches.

## What changed and why

The production code was already in the tree (ticket status note: "движок уже пакует по 50 и не
глушит очередь на отказе"), so the story is delivered by the proof test plus the gate wiring it
needs.

- `shared/core/outbox/src/jvmTest/.../OutboxDrain120Test.kt` — new JVM proof against the shared
  `OutboxEngine` and the common `FakeOutboxStore`, five cases:
  1. **120-action drain**: one `drain(uid)` pass settles all 120 actions — selection page limits
     `[50, 100, 150]` plus the closing empty fetch `170` (page size 50, third page carries the
     remaining 20); every `mutationId` (built by `OutboxOperations.mutationKey`, AD-2) is sent
     exactly once — no duplicated effects; 120 durable receipts hold exactly the fake server's
     authoritative payload; the queue is empty (AD-4) and a follow-up pass examines 0 — no
     repeated manual sync.
  2. **Mixed 120 queue**: 100 successes + 8 server refusals (quarantine, feature listener told,
     `attemptCount == 1`) + 6 version conflicts (CONFLICT state with `serverVersion == 41`,
     untouched attempt counter/backoff, no quarantine call — AD-24/AD-28) + 6 over-ripened rows
     (quarantined with reason `Expired` and `attemptCount == 0`, never reaching the transport,
     AD-1); branch counts sum to 120 examined; one refused row never stalls the rest (AD-22); a
     resync pass never re-sends settled rows.
  3. **Cancelled answer**: `CancellationException` thrown mid-pass propagates and fabricates
     nothing — the interrupted row keeps WAITING with no receipt and no removal; the resuming pass
     sends it first under the same mutation key (no second effect under a new key, AD-2).
  4. **Cancellation wrapped in `Result.failure`** aborts the pass instead of being laundered into
     a retry or a success (engine `processTransportFailure` re-throw).
  5. **Malformed answer**: a durable receipt whose identity does not match the pending mutation
     (foreign operation under the attempt's key) is neither acknowledged nor re-sent — the row
     stays pending with its evidence intact (engine receipt-identity guard).
  Note: the over-ripening rows use `SUBMIT_RATING` deliberately — `SUBMIT_ATTEMPT` is in
  `permanentReceiptOperations`, and by design the age limit does not apply to permanent-receipt
  operations (2.1 property, ONLINE-O2 contract draft §5); expiry is only reachable for ordinary
  operations.
- `shared/core/outbox/build.gradle.kts` — gate wiring: `<module>:test` must resolve. The module
  uses plugin `schoolquiz.kmp.library.agp9`, has no exact `test` task and the bare name was
  ambiguous (`testAndroid`/`testAndroidHostTest` — the gate's attempt-1 failure), so an exact
  `test` task is registered with `dependsOn(jvmTest)`, mirroring the 2.1 fix in
  `shared/core/persistence/build.gradle.kts`.

## What this confirms

A 120-action queue drains in a single engine pass to server-authoritative receipts with stable
per-action idempotency keys, without manual resync; refusal, version conflict and over-ripening
each keep their own outcome inside the same hundred; an interrupted (cancelled) pass and a
distorted durable response leave the uncertainty uncertain — no fabricated success, no erased
pending evidence, no duplicated effects.

## What this gate does NOT prove

- No instrumented/device tests: nothing ran on a physical device; the scope rule for this story is
  code+JVM only (ticket `hitl = false` note). Real-phone confirmation rides stories 1.3/1.5/2.7 and
  final acceptance.
- No online/backend verification: the transport is a fake; the drain against the live
  demo-backend receiver (`MUTATION_HANDLERS` in `functions/index.js`) is explicitly left to
  acceptance (ticket note: "слив на живом demo-backend остаётся приёмке").
- No ONLINE-O2 coverage: ready-ACK / next-question window behavior is stories 2.5/2.9/2.6; this
  story proves only the generic mutation-queue half of CAP-18.
- The full `ciCheck` (whole-repo `test`/`allTests`, `detekt`, `ktlintCheck`, `assembleDebug`) was
  not run in this story; the ktlint filter of the KMP convention plugin excludes `*Test/` paths,
  so the new test sources were not linted either.
- `:shared:core:sync`, `:shared:feature:lesson-runner:data` and `:shared:core:persistence` were
  not modified by 2.2; their green `:test` runs in the gate only confirm those suites still pass
  against the shared modules, not anything new.

## Environment repairs, left uncommitted on purpose

The same two machine-specific build-environment repairs as in 2.1 were needed on this VPS and are
NOT part of the commit (per instruction they stay uncommitted in the worktree):

- `gradle.properties` — `org.gradle.java.home=/opt/jdks/jdk-27`, memory caps
  (`-Xmx1400m`/`MaxMetaspaceSize=512m`, `workers.max=2`, `kotlin.daemon.jvmargs=-Xmx1024m`)
  after the host OOM-killed the build daemon with the old config.
- `local.properties` — `sdk.dir` repointed from the owner's Mac path
  (`/Users/tpov/Library/Android/sdk`) to `/workspace/infrastructure/android-toolchain/sdk`.

If a fresh machine needs to run the gate, the equivalents of both repairs must be reapplied.
