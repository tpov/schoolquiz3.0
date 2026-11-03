# Epic 8 Context: Polls, private quizzes, operation history

<!-- Generated from planning artifacts. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Deliver the three October product journeys on top of the shared delivery/projection/ledger foundations from epic 2 (done), without inventing parallel mechanisms: authors create poll series that participants vote in once with a distribution hidden until their own confirmed vote; authors share HARD-only private quizzes whose attempts, limits and result visibility are governed server-side under the settled private-quiz rules; and account holders inspect their income/spending while authors inspect lesson earnings through authorized read projections that never create money. These are new capabilities (CAP-24/25/26), so both the journeys and their gating contracts must behave exactly as the settled owner decisions say.

## Stories

- Story 8.1: UX-O7 poll contracts before vote writes
- Story 8.2: Poll series runs end to end
- Story 8.3: Private quiz authoring defaults
- Story 8.4: Private quiz run: limits and visibility
- Story 8.5: Private quiz negative proof (no charges, no deferral)
- Story 8.6: Operation history and earnings read projections

## Requirements & Constraints

**Polls.** Single- or multiple-choice questions with 2–8 options and no correctness or difficulty classification. A participant submits one confirmed nonempty choice set; retry never adds a second vote. The distribution is hidden until that participant's own confirmed vote. Percentages use distinct respondents per question (each participant counted once in the denominator), so multiple-choice totals may exceed 100%. Unknown statistics are never rendered as zero. Series progress and statistics keep their identity across revisions.

**Private quizzes.** The category is «Приватные квизы»; a quiz is a flat HARD-only set entered with a signed-in SchoolQuiz account. Creation defaults: 1 attempt, hidden result, no link expiry; loading an existing quiz keeps its saved settings. The author controls an attempt limit ≥1, result visibility, and an optional absolute link expiry (disabling earlier by hand is allowed). A new start atomically checks that the link is enabled and unexpired and that startedCount is below the current limit, then increments the counter; a retry of the same operation never spends the limit twice. Limit changes preserve startedCount — an increase admits only the remaining difference, a decrease never interrupts a started attempt. Closing or expiring the link closes only new starts; a confirmed active attempt finishes under its original identity and deadlines. There is no deferred continuation: only technical recovery of the same open operation within original deadlines is allowed. When the recovery window ends, the attempt terminates with reason timeout and the result is scored over the entire frozen quiz with all remaining questions (including unissued) unanswered — no paid-correct effect. The current show-result setting governs every own result, including past attempts, on every read/retry (false: completion confirmation only, without counts or percent; true: own result only). Author statistics remain available under a separate authorized scope regardless of the flag. No STANDARD/PLASMA charges for entry, hints or skips; no replacement fee; a timed skip is not a paid correct answer. No guest/password access, and no link auto-renewal/rotation/re-enable is assigned.

**History and earnings.** One authorized read projection serves both own operation history and per-lesson author earnings. Every record carries consistent identity, date, reason, signed amount, resource and status. Date grouping, filters, record expansion and bounded continued pagination preserve context. Pending is never credited as income; resources are never summed into one balance. Switching accounts (owner/epoch) reveals nothing foreign and cuts off stale responses. Reading history creates no financial effect and never executes money commands.

**Cross-cutting.** All views are server-authorized with redaction; answer keys, tokens and private payloads never leak into UI, diagnostics or logs. RU/UK/EN, semantic actions, rotation/background/recovery, and typed states (loading/empty/error/unknown/pending vs confirmed) apply to every screen.

## Technical Decisions

- Ownership follows facts, not screens: the polls module owns series, votes and allowed statistics; the private-quiz module owns access state, attempt limit/state and the separate author/participant projections; history is a read projection of economy, not a new ledger.
- Operations keep original intent: operationId, canonical body, owner and account epoch are fixed before dispatch; retries return the stored authoritative result and never duplicate a vote, an attempt or a charge.
- Projections sync without invented facts: server owns entity versions and change journals; persistent cursors are lexicographic (changedAtMs, docId); pages are bounded and continuable; absence on a page proves nothing; unknown/stale/forbidden/not-found/confirmed-zero stay distinct.
- Online single-sitting timing follows the shared rule: the original question deadline runs through disconnection; expiry closes the question unanswered; the default 60-second next-question window starts at an accepted timely answer or the original deadline; retry neither reopens questions nor extends attempts. The first question keeps its separate bounded start contract.
- Contracts before adapters: the poll vote contract (identity triple, respondent-question uniqueness, delivery/recovery, one counts snapshot) exists and is transport-tested — build vote writes on it, do not fork feature-local variants. The own-ledger projection contract (bounded order, cursors, permitted fields) is still an open gate and must be fixed before the history adapter.
- A private quiz inherits no exam fee, timer or pass/fail threshold; no shared mode controller is introduced. Poll votes and private quiz results stay outside scored lesson progress and its awards.

## UX & Interaction Patterns

- Polls: one question per full screen; the choice set stays editable until «Проголосовать» confirms it; distribution and Next/Done appear only after the confirmed vote; preview never votes.
- Private quizzes: category entry «Приватные квизы»; authoring exposes limit, show-result and expiry controls; a hidden result shows only completion, without N/M or percent.
- History: drawer-entry own history and per-lesson «Начисления» share date groups, All/Income/Expense filters and an expandable record showing signed amount, resource and status, with distinct loading/empty/error states.
- Screens follow the final design system's shared status semantics and accessibility rules; UI hiding never substitutes for server-side filtering.

## Cross-Story Dependencies

- The epic consumes epic 2 foundations (durable delivery, projection fencing, ledger identity) — done; Online O2 delivery also closed.
- 8.1 (poll contracts) precedes 8.2's vote writes; 8.3 precedes 8.4; 8.5's negative tests run against the real flow from 8.4. 8.6 is independent of 8.1–8.5 but is blocked on the own-ledger projection contract (UX-O10/O12) and on epic 4's author-payout server views (TZ06B, story 4.2), which its earnings part consumes.
- Several stories require HITL device proofs (two-device vote/retry flow, authoring screen, two-device and timeout cases, account A→B→A for history).
- Out of scope: the shared inbox (owned by the authoring-review epic), financial ledger writes (owned where money moves), and exam scoring (owned by the exams/certificates epic).
