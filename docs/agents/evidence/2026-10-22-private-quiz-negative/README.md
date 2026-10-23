# Epic 8.5 — private quiz negative proof (no device needed) — 2026-10-22

Spec: `_bmad-output/implementation-artifacts/spec-epic-8-social-features.md` (baseline 6ac52f2f).
«Забор, а не описание»: behavior holds against the real flow; the static fence reads code, not
promises.

## Behavioral fence — `functions/private-quiz-negative.test.js` (5/5 pass)

Driven through the real `index.js` handlers over the Firestore stub:

1. **Full cycle leaves the ledger empty.** Submission → start → submit attempt → read result →
   read statistics → close link: `audit/currency_ledger/entries` gains ZERO rows; author wallet
   (`pointsNolics`) untouched; `standardChargePoints`/`plasmaChargePoints`/`lifePoints` never
   read or written.
2. **Consent is never requested.** The private submit mutation passes WITHOUT `capturedTerms` —
   absence of consent is not an error for private ops (server registry holds only
   `lesson_runner.SUBMIT_ATTEMPT`).
3. **Static fence on private server modules** (`private-quiz-gate.js`, `private-quiz-run.js`)
   and on the private-run handler block + submission handler in `index.js`: no money vocabulary
   at all — `ledgerEntr*`, `LEDGER_PATH`, `ATTEMPT_TOLL`, `CHARGE_CLAIM`, `REVIEW_FEE`,
   `charge-claims`, `arena-tickets`, `attemptReward`, `lessonUnlockPrice`,
   `MUTATION_TERMS_OPERATIONS`, `capturedTerms`, `chargeClaims`. The fence strips comments
   first — it reads executable text, so naming a refused wire field in a doc comment is not a
   violation, using it is.
4. **Private submission opens no review-fee cycle** (`review_fee_cycles` stays empty) and no
   ledger row, while an ordinary submission still does (covered in
   `private-quiz-settings.test.js`).
5. **No deferral exists.** `ATTEMPT_REASON` = exactly {finished, timeout},
   `ATTEMPT_STATUS` = exactly {running, terminal}; no resumable/deferred/later state can appear
   without renaming these — the projection carries no `resumable` field. Kotlin mirror:
   `PrivateQuizEntryContractTest.everyRefusalIsNamed_andNoneOfThemIsAResume` pins the refusal
   set against RESUME/DEFER/LATER.

## Client registry fence — `OutboxOperationsRegistryTest` (extended)

- New operations (`quest_authoring.CLOSE_PRIVATE_QUIZ_LINK`, wire name
  `private_quiz.SUBMIT_ATTEMPT`) declared and bidirectionally agreed with `MUTATION_HANDLERS`.
- New case `given the private quiz operations then none of them asks for payable terms
  consent`: both directions — client-side `termsConsentOperations` and the server registry text
  parsed from `functions/index.js` — must not contain any private operation. The day private
  quizzes start costing anything, this test falls with the operation's name in the message.

## Gates run

Transcript: `transcripts.txt` in this directory (real runs, container
`schoolquiz-functions-emu`, plain `node *.test.js`):

- `private-quiz-settings.test.js` — 8/8
- `private-quiz-run.test.js` — 8/8
- `private-quiz-negative.test.js` — 5/5
- `own-history-views.test.js` — 5/5
- `private-quiz-gate.test.js` — 8/8
- `sync-change-clock.test.js` — OK
- Gradle `:shared:core:outbox:jvmTest` — green incl. the extended registry fence.

## Left to the owner

- Nothing for 8.5 beyond accepting this evidence; the behavioral fence re-runs on every
  `node run-tests.js`.
