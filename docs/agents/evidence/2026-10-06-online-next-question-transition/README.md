# One real online next-question transition per ONLINE-O2: server ready-ACK receiver + proven transition (story 2.6)

- Result: **PASS** (attempt 2). The orchestrator gate ran `:shared:core:outbox:test` and
  `:shared:feature:lesson-runner:data:test` (both wire `jvmTest`, see module build files) —
  green; the gate was executed by the orchestrator, not by the implementer (implementer runs were
  point-compile only, per the night-window rule). The server half runs through the root
  `functionsTest` (`npm test` in `functions/`, part of `ciCheck`); the two new Node suites are
  discovered by `run-tests.js` automatically. The blocker that made 2.6 `blocked` in
  `tickets.toml` is gone: the owner adopted ONLINE-O2 (story 2.9 resolved 2026-10-06 — the clock
  rule "the answer time starts from a server event" is the approved edition), the full contract
  edition exists (story 2.5, same night), and the demo backend is restored (story 1.1). The
  demo-backend receiver proof runs over the real `functions/index.js` code, the proof shape story
  2.8 already names ("контрактные Node-тесты приёмника … против demo-backend").
- Gate iterations: attempt 1 (2026-10-06) FAIL exit=1 — `:shared:core:outbox:jvmTest` 120 tests,
  1 failed (`OutboxOperationsRegistryTest → given every handler on the server then the client
  declares its name`: `lesson_runner.READY_ACK` was registered on the server but missing from the
  test's hand-maintained `declared` set — test-side fix: the name added to the set); and
  `:shared:feature:lesson-runner:data:jvmTest` 336 tests, 1 failed
  (`ReadyAckOutboxContractTest → sourceId carries the triple…`: the codec prefixed
  `attempt-` onto an attemptId that already carries it, producing
  `attempt-attempt-7-q3-window-win_1` instead of the contract's pinned sample form
  `attempt-7-q3-window-1`). Fix: `ReadyAckOutboxCodec.sourceId` now lays the attemptId in as is
  (production change, matching the form `OnlineO2IdentityRetryContractTest` pinned in 2.5), the
  test literal uses window id `1` to assert the exact sample, and the node handler test's key
  helper mirrors the codec. All other suites in both modules passed in that run.
- Date: 2026-10-06.
- Story: `epic-ordinary-delivery` 2.6 `online-next-question-transition` (covers CAP-1, CAP-18);
  done-when: one online next-question transition per the adopted ONLINE-O2 — the question/window
  completion event, the ready-ACK receipt, the next question's answer clock starting without an
  unbounded pause and without replacement clocks after a lost ACK — proven by one real transition
  through the callable receiver on the demo backend plus contract tests of both sides.
- Contract: `_bmad-output/mvp-candidate/epic-ordinary-delivery/online-o2-contract.md` (full
  edition; [corrected 2026-10-28, retro F3] on 2026-10-06 the owner approved the clock rule
  only — the full edition was adopted 2026-10-08, owner-decisions-2026-10-08 p.1, pinned to
  blob `e142341e…`). Section references below (§) are to that document.

## What changed and why

### Server half — the ready-ACK receiver and the window on the demo backend

- `functions/lesson-question-window.js` (new) — the pure verdicts of the transition, no
  `firebase-admin`: `openQuestionWindow` captures the 60-second window constant and the per-question
  seconds **at open** (§3 captured config; seconds come from the server-read question content via
  `questionAllocatedSeconds`, never from the request body, §5); `deliveryVerdict` issues once and
  answers a repeat with the same question and the same server deadline (§3, §5); a window expired
  without delivery closes the attempt without an answer (§7); `readyAckVerdict` returns the
  receipt assembled from the window's server facts only — the ACK body can never set or restart a
  deadline (§4, §5). The window id and document id are deterministic functions of
  `(attemptId, completedQuestionIndex)`, so a repeated completion event reads the same window
  instead of opening a second one (§7).
- `functions/index.js` — the wiring:
  - `MUTATION_HANDLERS["lesson_runner.READY_ACK"]` — the receiver half lands in the single
    mutation registry (AD-7; contract §6.2); the key's dedup/replay ("повтор известного ключа
    возвращает сохранённый результат", AD-6) is done by `submitMutation` itself before the handler
    runs, and a different body under the same key is the AD-6 conflict at the same gate.
  - `applyLessonReadyAck` — identity check against the window document; final refusal when the
    question was never delivered or the identity does not match.
  - `exports.beginLessonQuestionWindow` — the completion event + window open: the moment is the
    server clock, the pool is counted from server content, and after the last question the window
    does not start (§3); a next question that is foreign, archived or missing is refused before
    any window exists (AD-21: the client does not appoint server facts).
  - `exports.fetchNextLessonQuestion` — the delivery request: first call issues and starts the
    clock at the server moment; repeats return the same question with the same deadline plus the
    server remainder; an expired window closes instead of issuing.
  - Both callables share the transaction pattern of the existing receiver; scoring/rewards stay
    untouched with `applyLessonResultEvents` — the ready-ACK does not replace the attempt
    settlement (§4).
- `functions/lesson-question-window.test.js` (new) — the branch table of the verdicts: captured
  constants, deterministic identity, conflict on a changed body, first-issue clock start,
  same-question/same-deadline repeat, expiry closure, contradictory-state safety, and the
  ready-ACK facts/mismatch/undelivered cases.
- `functions/lesson-ready-ack-handler.test.js` (new) — **the one real transition**, end to end
  through the real `index.js` over the in-memory Firestore store (the harness of
  `attempt-handler.test.js`): completion → window → delivery → ready-ACK through
  `functions.submitMutation.run` — plus the lost-ACK branch (the repeated request returns the same
  question and the same deadline), the replayed key returning the byte-identical receipt, the
  distorted-body conflict, the final refusal before any delivery, expiry closure without an
  answer, and "no window after the last question".

### Client half — the operation, the encoding and the receipt

- `shared/core/outbox/src/commonMain/kotlin/.../OutboxOperations.kt` — `READY_ACK =
  "lesson_runner.READY_ACK"` and its inclusion in `permanentReceiptOperations`. This is the
  decision the contract explicitly left to this story (§6.1: «включение READY_ACK в этот набор —
  решение истории 2.6 вместе с именем операции»): the feature namespace follows the receiver
  registry, and the permanent-receipt exemption is the mechanism of the §6.1 retry rule —
  age-quarantine would kill the question before its own deadline, a direct §7 violation.
- `shared/feature/lesson-runner/data/src/commonMain/kotlin/.../outbox/ReadyAckOutboxCodec.kt`
  (new) — the triple `(attemptId, questionIndex, windowId)` encoded into `sourceId`
  (`<attemptId>-q<index>-window-<windowId>`, the attemptId laid in as is — the form the 2.5
  contract test pinned: `attempt-7-q3-window-1`; charset-guarded so the key stays in the server's
  `[A-Za-z0-9_-]`), the frozen intent body (identity + position + window + `createdAtMs`, no
  client clock for the server), and the row shape (`entityRef = lesson_runner:ready_ack:<attemptId>`,
  not versioned). The user of the codec is the online runner adapter of the consuming epic; the
  offline runner paths do not enqueue ready-ACKs (§1).
- `shared/feature/lesson-runner/data/src/commonMain/kotlin/.../outbox/ReadyAckReceiptParser.kt`
  (new) — typed parse of the receiver's response against the expected triple: identity guard
  (owner/operation/entityRef/key), schema check, and the server facts (`issuedAtMs`,
  `answerDeadlineMs`, `ackedAtMs`) required to exist — absence stays unknown, never zero. A
  receipt of another identity or shape is `Invalid` and never starts a clock (§5).
- `shared/core/outbox/src/jvmTest/.../OutboxOperationProducersTest.kt` — `READY_ACK` added to the
  declared producers: the codec is a real production builder of queue rows, so the "no
  paper-only operation" invariant now covers it.

### Client contract tests (JVM)

- `shared/feature/lesson-runner/data/src/jvmTest/.../ReadyAckOutboxContractTest.kt` — the key is
  born from the triple and never varies; the key stays in the server charset (exact literal
  `lesson_runner-READY_ACK-attempt-7-q3-window-1`); the row freezes the intent body without any
  client clock; READY_ACK is a permanent-receipt operation and never ages into quarantine.
- `shared/feature/lesson-runner/data/src/jvmTest/.../ReadyAckReceiptParserTest.kt` — the acked
  receipt yields the server facts; the replayed key parses to the same outcome; receipts of
  another identity/triple/shape are `Invalid`; a missing or malformed clock field stays unknown.

## What the gate ran (orchestrator, attempt 2 — PASS)

- `:shared:core:outbox:test` — `OutboxOperationsRegistryTest` (name parity with the registry,
  now including `lesson_runner.READY_ACK`), `OutboxOperationProducersTest` (now including
  `READY_ACK`), `PermanentReceiptPolicyTest` (iterates the set that now contains `READY_ACK`),
  and the existing engine/policy/120-drain suites.
- `:shared:feature:lesson-runner:data:test` — `ReadyAckOutboxContractTest`,
  `ReadyAckReceiptParserTest`, plus the existing data-module suites.
- The server half runs through the root `functionsTest` (`npm test` in `functions/`, part of
  `ciCheck`): new suites `lesson-question-window.test.js` and `lesson-ready-ack-handler.test.js`
  are discovered by `run-tests.js` automatically.

## What this does NOT prove — boundary and hitl-tail

- **HITL tail — physical device:** the moment the ready-ACK confirms — "the delivered question is
  rendered and ready" — cannot be proven without a physical device: there is no online runner UI
  in this tree, and the Compose/adapter path from rendering to `ReadyAckOutboxCodec` belongs to
  the consuming adapters. Repeat the transition on the physical Samsung rig (RF8W80MBR3Z) as part
  of story 2.7 / final acceptance; the device is not visible to server ADB (see
  `2026-10-04-epic1-rig-run.json`).
- **HITL tail — live callable on the demo container:** the restored demo backend
  (`schoolquiz-demo-firebase-20261004`, 127.0.0.1:18090/19090) runs auth+firestore emulators only
  — no functions runtime — so the transition is proven against the receiver code itself over the
  in-memory store (the same proof class story 2.8 names for the demo-backend receiver). A live
  deploy + real HTTP round trip against the container is an acceptance tail, not a local one.
- **Ordinary-online mode owner:** this receiver is the timing half of the contract only (window,
  delivery, ACK). Scoring, rewards and life spending stay with `applyLessonResultEvents`; the
  completion-kind discrimination (timely accepted answer vs original expiry) is not modeled —
  both events open the same window per §2, and their settlement belongs to the attempt result.
- **Exam mode:** EXAM:AD-3/AD-15 ("the first ACK fixes the deadline") is intentionally not
  implemented here — existing evidence of the exam lifecycle, not an overwrite (§4.1, §8); the
  exam adapter epic integrates it.
- **Private-scope lessons:** the window's content lookup reads the public `questions` collection;
  private-scope content paths are consumer work for the mode owner.
- **No instrumented/on-device tests:** the gate covered the two named modules' JVM tests only;
  nothing ran on a device or emulator (`connectedDebugAndroidTest` / instrumented suites are not
  part of this story's proof). The device-side confirmation of the transition is the hitl tail
  above.
- **No live online check on the backend:** no functions were deployed and no HTTP callable call
  was made against the demo container — the receiver proof is in-process over the real
  `functions/index.js` (see the hitl tail above).
- **Full `ciCheck` was not run:** the orchestrator gate for this story ran the two named Gradle
  modules; the full canonical gate (`:apps:android-next:assembleDebug`, all modules'
  `test`/`allTests`, `compileAndroidTests`, `functionsTest`, `scriptsTest`, `detekt`,
  `ktlintCheck`, `detektMetadataCommonMain`) was not executed for this story by anyone in this
  window. The Node suites above are covered by `ciCheck`'s `functionsTest` when it next runs.
- **Not run by the implementer:** no Kotlin/Node test suites, no `assemble`, no `ciCheck`, no
  detekt/ktlint — point-compiles only (`:shared:core:outbox:compileKotlinJvm`,
  `:shared:core:outbox:compileTestKotlinJvm`,
  `:shared:feature:lesson-runner:data:compileKotlinJvm`,
  `:shared:feature:lesson-runner:data:compileTestKotlinJvm`, all green; `node --check` green for
  the four touched JS files). The pre-existing uncommitted build-env files (`gradle.properties`,
  `local.properties`) remain out of story scope.
- `tickets.toml` was deliberately not edited: story dispatch/status is the orchestrator's input.
