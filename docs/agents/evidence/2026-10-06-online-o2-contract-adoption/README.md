# ONLINE-O2 contract reaches its full edition with pinned client identity/retry invariants (story 2.5)

- Result: **PASS** (attempt 2). The orchestrator gate ran `:shared:core:outbox:test` and
  `:shared:feature:lesson-runner:domain:test` (both wire `jvmTest`, see module build files) —
  green; the gate was executed by the orchestrator, not by the implementer (implementer runs were
  point-compile only, per the night-window rule).
- Gate iterations: attempt 1 (2026-10-06 19:52) FAIL exit=1 — `:shared:core:outbox:jvmTest`
  ran 120 tests, 1 failed: `OnlineO2IdentityRetryContractTest → mutation key stays inside the
  server key charset` expected the literal `lesson-runner-SUBMIT_ATTEMPT-…`, but
  `OutboxOperations.mutationKey` (`OutboxOperations.kt:82`) replaces only the dot separator, so
  the real key keeps the namespace underscore:
  `lesson_runner-SUBMIT_ATTEMPT-attempt-7-q3-window-1`. Fix: the test's expected literal
  corrected (test-only; no production code touched). The other four cases of the suite and the
  whole domain module already passed in that run (`OnlineO2AttemptIdentityContractTest`:
  5 tests, 0 failures — `shared/feature/lesson-runner/domain/build/test-results/jvmTest`).
- Date: 2026-10-06.
- Story: `epic-ordinary-delivery` 2.5 `online-o2-contract-adoption` (covers CAP-1, CAP-18);
  done-when: the ONLINE-O2 contract is written in its full edition per the closed gate checklist
  (contract-map.md, Remaining gates) — (1) question/window completion event, (2) delivery/ready-ACK
  receipt with the mode split, (3) identity/retry, (4) answer-clock start relation per mode,
  (5) ACK loss without an unbounded gap or replacement clocks, (6) EXAM AD-3 cited as existing
  evidence without a rewrite — and the identity/retry invariants are pinned by client JVM tests.

## What changed and why

- `_bmad-output/mvp-candidate/epic-ordinary-delivery/online-o2-contract.md` — the full edition.
  It supersedes the night draft (`online-o2-contract-draft.md`, left untouched as history) and
  maps every checklist item to its section in the header. Primary sources were re-read and cited
  precisely on 2026-10-06: AD-31/AD-2/AD-6/AD-7/AD-15/AD-21 of the 2026-09-29 spine,
  EXAM:AD-3/AD-30 of the 2026-08-29 spine, SPEC Constraints. New relative to the draft: the
  checklist mapping; the client invariant table (§6.3) with invariant→test rows; the ready-ACK
  retry rule pinned to the existing permanent-receipt exemption mechanism with the explicit note
  that adding `READY_ACK` to `permanentReceiptOperations` is story 2.6's decision together with
  the operation name; a correction of the draft's loose citation — the quote "reconnect cannot
  reopen it or extend that deadline" lives in EXAM:AD-3 (2026-08-29), while 2026-09-29 AD-15
  carries the exam rule in its own wording; the code-state section (§10) updated for stories
  2.1–2.4 already landed.
- `shared/core/outbox/src/jvmTest/.../OnlineO2IdentityRetryContractTest.kt` — the client half of
  ONLINE-O2 §6 on the shared transport, five cases:
  1. **Key born with the intent**: `OutboxOperations.mutationKey` is pure and stable — the same
     `(operation, sourceId)` yields the same key forever; a different question position or
     operation yields a different key (AD-6; no dedup across intents, no split within one intent).
  2. **Server charset**: the key fits `[A-Za-z0-9_-]`, the operation dot is the only replacement
     (migration 5 → 6 parity), and a blank intent is rejected instead of coerced into a key.
  3. **Frozen body across retries**: an engine record failing once (`NoNetwork`) and succeeding on
     the next due pass sends twice under the same `mutationId` with identical
     `ownerUid/operation/payload/entityRef/expectedVersion/createdAtMs`; between the attempts the
     stored identity fields are untouched — only scheduling state (`attemptCount`, backoff)
     changes; the successful receipt carries the mutation's identity.
  4. **Known key → same receipt, no network**: after a crash between receipt persistence and queue
     removal, the next pass acknowledges the durable receipt locally — the transport is never
     called, the receipt is byte-identical, exactly one receipt row exists for the entity; a
     zero `createdAtMs` shows the known-key path ignores age/TTL.
  5. **Permanent-receipt mechanism never ages into quarantine**: for a permanent-receipt
     operation, `OutboxPolicy.isExpired` is false and `onFailure` stays `WAITING` even at
     `attemptCount = 99` and zero creation time — the mechanism the ready-ACK retry rule (§6.1)
     will reuse.
- `shared/feature/lesson-runner/domain/src/jvmTest/.../OnlineO2AttemptIdentityContractTest.kt` —
  the identity carrier invariant at the owner of the type, five cases: the accepted-answer
  advance, the raw advance, the timeout projection and pause/resume all keep the attempt identity
  born at start (a future online adapter minting a new `attemptId` per question fails here); the
  identity type itself refuses a blank user or negative lesson version, so an anonymous transition
  identity cannot be constructed.

## What the gate must run

- `:shared:core:outbox:test` — includes the new `OnlineO2IdentityRetryContractTest` plus the
  existing engine/policy/120-drain suites referenced by the contract's §6.3 table.
- `:shared:feature:lesson-runner:domain:test` — includes the new
  `OnlineO2AttemptIdentityContractTest` plus the existing runner suites.

## What this does NOT prove

- No online next-question transition exists yet: the server window event, ready-ACK callable and
  its `MUTATION_HANDLERS` registration are story 2.6 (blocked on owner adoption 2.9 and the demo
  backend 1.1). The contract is written and pinned, not adopted or proven by a live transition —
  no online/backend check of the ready-ACK path ran in this story.
- The owner has not approved this edition (story 2.9); the exam-adapter drift (`examFactor` in
  `RunnerLogic.computeTimer`) is recorded, not fixed (§8; exam adapter epic).
- The gate covers the two named modules' JVM tests only: no instrumented/on-device tests ran,
  and the full `ciCheck` (detekt/ktlint, other modules, app assemble) was not run for this story.
- Pre-existing uncommitted local build-env files `gradle.properties` and `local.properties` were
  deliberately left out of the story commit: they are VPS build environment, not story content.
