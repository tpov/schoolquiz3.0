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

- `node private-quiz-run.test.js` — **9/9 pass** against real handlers: start consumes once,
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

## Device-proof recipe (owner, SAME signed-in account on two devices + timeout case)

> Открытый вопрос владельцу: у приватных квизов НЕТ механизма обнаружения/шаринга для другого
> участника — клиентский список показывает только собственные черновики автора, и механизм
> «ссылки/доступа участнику» решением не назначен (UX-16). Пока он не назначен, прогон чужого
> квиза с другого аккаунта недостижим из UI — рецепт ниже использует ОДИН подписанный аккаунт
> на двух устройствах (AD-29 это разрешает: startedCount считает каждый старт, лимит общий).

1. Install the MVP APK on two devices; sign in with the SAME SchoolQuiz account (author).
2. Device 1: create a private quiz (limit 1, show-result hidden), submit, wait for sync
   (status SYNCED). Device 2: sync, open «Приватные квизы» category, tap the quiz, start:
   redacted questions only (no "correctOptionId" in any received payload — proxy/logcat).
3. Device 2: a second start attempt on the same quiz → typed limit-reached state; the first
   attempt (device 1) is untouched.
4. Close the link from device 1 mid-run on device 2 → the started attempt finishes normally;
   a NEW start refuses (LINK_DISABLED).
5. Timeout case: start on device 2, background the app past the run deadline, reopen →
   completion-only result (showResult=false). A fresh start refuses (limit spent). Note: a
   submission delivered PAST the deadline scores 0 — late answers are never credited (AD-29).
6. Flip show-result on device 1 (resubmit settings): device 2's past-result read now shows
   the percent.
7. Author statistics: no statistics UI exists — the AC is callable-level and node-covered
   (private-quiz-run.test.js: "showResult=false gives completion only ... the author always
   sees statistics").

## Second pass, same day — the run is reachable from the UI

- `fetchPrivateQuizRun` now FREEZES the run at the first serve, in a transaction: the redacted
  halves AND the translation keys (`run.keys`) are stored under the start record; every later
  fetch (second device, retry) serves the identical set. Keys never travel to the client.
- `private_quiz.SUBMIT_ATTEMPT` translates Ordering/FillBlank answers back through those keys
  before scoring (choices are not id-rewritten and need no key); the deadline basis is the frozen
  run the participant actually saw. Round-trip proven: `an ordering answer in scrambled ids is
  translated back by the frozen keys` (private-quiz-run.test.js 9/9).
- UI path: quizzes screen → category «Приватные квизы» → quest rows (saved owner quizzes) →
  `QuizzesConfig.PrivateQuizRunner` (a pointer only; dropped on process restore — the server
  times the attempt out) → `PrivateQuizRunnerScreen`: door-checking → typed refusal card
  (LINK_DISABLED / LINK_EXPIRED / ATTEMPT_LIMIT_REACHED / …) → questions on their original
  schedule (AD-31 clock, expiry closes shown-without-answer) → submit enqueued via
  `PrivateQuizSubmitOutboxWriter` (key = startId, permanent receipt, no consent) → result read
  via `readPrivateQuizResult`, completion-only when showResult=false. Signed-in accounts only
  (AUTH_REQUIRED otherwise); no guest/password path.
- New tests: `PrivateQuizRunMachineTest` (door refusal, served/answers payload, expiry closes
  without answer, completion-only read), `PrivateQuizSubmitOutboxContractTest` (key by startId,
  receiver fields, explicit null for no-answer, permanent receipt without terms consent).
- Gates: node 128/129 (inherited mvp-isolation pin), gradle lesson-runner domain/data,
  outbox, lesson-runner + quizzes-screen presentation — green.

## Left to the owner

- The physical two-device + timeout pass above (HITL).
