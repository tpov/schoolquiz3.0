# Epic 8.4 — private quiz run: limits and visibility — 2026-10-22

Spec: `_bmad-output/implementation-artifacts/spec-epic-8-social-features.md` (baseline 6ac52f2f).
Done bar: code + JVM/Node tests + evidence + device-proof recipe (two devices + timeout = owner).

## What was built

- **Gate extended into the run contract** (`functions/private-quiz-gate.js`): the start record
  now carries the frozen quiz identity — `quizSubmissionId`, `quizVersion`,
  `participantAttemptIndex` — fixed before dispatch (AD-6). Refusals ride with machine-readable
  codes in `details.reason` (LINK_DISABLED / LINK_EXPIRED / ATTEMPT_LIMIT_REACHED) so the client
  maps typed entry states instead of parsing prose (AD-21).
- **Pure run module** `functions/private-quiz-run.js`:
  - `attemptDeadlineMs` — attempt budget from the ORIGINAL start: Σ ordinary-HARD question
    times (`questionAllocatedSeconds(content, true)`, reused from lesson-reward) plus AD-31
    windows (60s between questions); recovery never extends it;
  - `finishAttempt` — FINISHED scores the submitted portion; TIMEOUT scores the WHOLE frozen
    quiz with remaining/unissued questions as served-unanswered ('1'), reusing the existing
    `scoreAttempt` (no new scorer, exam-terminal CLOSE_REASON.TIMEOUT precedent); a timed skip
    has no paid-correct effect (no charge form exists at all);
  - `projectOwnResult` — CURRENT showResult decides completion-only vs own numbers; unknown is
    never rendered as zero; `projectAuthorStatistics` — author scope, any flag.
- **Handlers** (`functions/index.js`):
  - mutation `private_quiz.SUBMIT_ATTEMPT` — idempotent by startId (a retry returns the stored
    terminal; the limit is never spent twice); a closed/expired link does NOT interrupt a
    started attempt (enabled/expiry deliberately unread here);
  - `fetchPrivateQuizRun` — the frozen set for the caller's own start, question payloads
    redacted server-side via `question-redaction.js` (keys never travel), deadline from the
    original start; showResult deliberately NOT sent (visibility decides at result reads);
  - `readPrivateQuizResult` — CURRENT showResult at every read/retry incl. past attempts;
    lazy timeout terminal when the recovery window has passed with no submission;
  - `readPrivateQuizStatistics` — author uid-scoped, any showResult.
- **Client (lesson-runner)**: domain `PrivateQuizRun`/`PrivateQuizEntry`/`PrivateQuizEntryRefusal`,
  `StartPrivateQuizAttemptUseCase` (retry reuses the same startId — AD-6), repository port;
  data `PrivateQuizRunWireParser` (strict; missing score fields stay missing — no fake zeros),
  `PrivateQuizRunRepositoryImpl` (door codes → typed refusals); firebase
  `FirebasePrivateQuizRunRemoteDataSource` + names guard test; DI wired.
- **Presentation**: `RunnerUiState.Result.privateCompletionOnly` + `ResultContent` branch —
  completion confirmation without percent/stars/question numbers (AD-21: unknown is not zero).

## Gates run (from this tree)

- `node private-quiz-run.test.js` — **8/8 pass** against real handlers: start consumes once,
  retry does not; run serves only redacted halves + deadline from the original start; finished
  attempt scores server-side (50%) and replays the stored terminal; showResult=false gives
  completion only on every read, true reveals past attempts, author statistics always; closing
  the link mid-attempt lets the started attempt finish and refuses new starts; limit 3 admits
  exactly three, lowering to 1 admits nobody new and preserves the counter; timeout terminates
  permanently scoring the whole quiz ([1,1]); no ledger row in the whole cycle.
- Callable-boundary fence (`arena-lifecycle-views-handler.test.js`, extended ENDPOINTS): the
  three new private-run readers are pinned into the reviewed callable surface — auth before any
  read, one fixed boundary message ("Private quiz run could not be loaded") with the
  machine-readable reason in `details`, inner texts do not travel.
- `node private-quiz-gate.test.js` — 8/8 (pre-existing suite, extended record fields additive).
- Gradle (via `sgw`): `:shared:feature:lesson-runner:domain:jvmTest` (new
  `PrivateQuizEntryContractTest`), `:shared:feature:lesson-runner:data:jvmTest` (new
  `PrivateQuizRunWireContractTest`), `:android:feature:lesson-runner:presentation:testDebugUnitTest`
  (new `PrivateCompletionPresentationTest`) — green.

## Device-proof recipe (owner, two devices + timeout case)

1. Install the MVP APK on two devices (author A, participant B).
2. A creates a private quiz (limit 1, show-result hidden) and submits it; B opens the quiz
   through the private category entry and starts: B sees redacted questions, no keys anywhere
   (verify: no "correctOptionId" in any payload the app received — logcat network or proxy).
3. Second start on device B (or A after B) → typed limit-reached state; no attempt session.
4. Close the link from A mid-run on B → B finishes normally; a new start refuses.
5. Timeout case: start on B, put the app in background past the deadline shown by the run,
   reopen → the result screen shows completion confirmation only; author statistics on A still
   show the timeout attempt (percent 0, reason timeout). A fresh start refuses (limit spent).
6. Flip show-result on A (resubmit settings): B's past result read now shows the percent.

## Left to the owner

- The physical two-device + timeout pass above (HITL).
