# Epic 4 Context: Authoring, review, publication and curation

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Carry author material from a local draft through an automated model review to publication and curation, with conserved money and attributable history. Since the 2026-10-26 change, the reviewer is a model pipeline (no human reviewers in the MVP): a queued VPS worker checks each question with pass/fail verdicts plus a reason, translates passed content automatically, and admins audit every verdict. Users can also propose changes to another author's material; the target author approves or rejects (CAP-2/3/4/5/6/27).

## Stories

- 4.11: Submit a local draft from its card («Отправить на проверку»), fee shown before sending
- 4.12: Review queue worker on the VPS (drain, verdict record, linter retries, REVIEW_DELAYED, idempotency)
- 4.13: «Слова» stage (forbidden words for a children's audience; doubt rejects)
- 4.14: «Факты» stage (judge mode over quest-audit checklist, web sources recorded)
- 4.15: Automatic translation after the checks (RU/UK/EN, structure linter)
- 4.16: Author outcome in My Quests (strokes, «Не прошёл» tag, failed-question list, «Проверка задерживается»)
- 4.17: Admin Decisions tab (verdicts, ✓/✗ marks, logs, runner preview)
- 4.18: Fee burns whole (zero reward units, conservation test, draft-ownership check, nolics mirror)
- 4.19: Remove human review code (reviewer screens, stars/threshold, segment translation review, assignment)
- 4.20: Live pipeline journey on the rig (hitl/blocked)
- 4.21: Proposal record core (REPORT/COPY_EDIT/NEW_LESSON, routing, checks, fees, STALE)
- 4.22–4.25: Proposal entries and author side (error report, copy edit, new lesson, «Предложения» in Decisions)
- 4.26: Live proposals journey (hitl/blocked)

## Requirements & Constraints

- A submitted quest enters a server queue (`quest_review_requests/{submissionId}`, status `UNDER_REVIEW` after the fee transaction). The pipeline checks one quest at a time, lessons one by one, with the quest title and full category path as context.
- Two check stages: «Слова» (forbidden words/phrases for a children's audience; doubt rejects) then «Факты» (factual correctness with web search, sources recorded). Each question gets PASS/FAIL with a reason; no rating scale or threshold exists anymore.
- A format linter validates every model output; an invalid output returns a named error and is retried at most three times, then the lesson shows «Проверка задерживается» (REVIEW_DELAYED) — never a guessed verdict and never REJECTED-by-quality for a model failure.
- Verdicts are immutable records binding revision, stage, model, promptVersion, sources, rationale, attempts. The model judges and never edits the author's text; lesson text is passed as data inside delimiters.
- Passing lessons publish independently; a quest with failed questions shows «Не прошёл» and lists failed questions (lesson, stage, reason). Affected dependencies are rechecked; unaffected valid evidence is retained.
- The paid cycle charges the tariff once at acceptance (initial 1000 nolics, recheck 100 per question), pays zero rewards, and burns the whole fee: fee = entitlements (0) + burned remainder. Retry and retained prior evidence create no extra paid work. Private quizzes ride outside the public cycle as before.
- Proposals (REPORT free / COPY_EDIT 100 per changed question / NEW_LESSON 1000) pass the same checks before the author sees them; approval applies exactly the checked revision; stale base revisions refuse to apply; copies never carry secret answer keys.
- Removed code must be listed file-by-file in the removal story; fee-burn code stays.

## Technical Decisions

- AD-32: submission → server queue → a standalone VPS worker modeled on `arena-season-worker` (lease, bounded steps, durable checkpoint, no client-supplied authority) — deliberately not a scheduled function, because a model call with search takes minutes. Job identity is (submission, lesson, stage, attempt); repeats never duplicate verdicts. The GLM API key lives on the VPS outside Git; concrete model and per-quest budget are decided after trial runs (SPEC OQ-10).
- AD-33: proposal = (kind, target lesson or course node, base revision, proposer, payload revision); server builds a copy from the player projection without secret keys; approval is atomic and refuses STALE bases; rights are checked server-side (AD-20).
- Money keeps AD-5/6/9/10 exactness: one charge transaction with acceptance, immutable settlement manifest, tenth-of-nolic arithmetic, no floating point.
- Shared client/server names (stage names, proposal kinds, push event type) each get a sentinel test reading the other side.
- VPS rules: worker is a light Node process (model API calls only, no Gradle); functions tests run with `npm test`; client Kotlin builds go through the `sgw` wrapper only.

## UX & Interaction Patterns

- My Quests keeps category selection and the create FAB. Before approval a quest row shows two check strokes («Слова», «Факты»); after approval, publication-place tags replace them. Failed quests show the «Не прошёл» tag with a count; the expansion lists each failed question with lesson, stage and reason, then «Исправить вопросы». REVIEW_DELAYED reads «Проверка задерживается»; the detected language appears as a detail row (UX-6).
- No reviewer screens, star ratings, thresholds or segment translation review exist; their code is removed (UX-17).
- The Decisions tab is admin-only (64px review-queue rows, wash-review, radius-6 tags): filters «Без отметки / Верно / Неверно», ✓/✗ with an optional comment, «Логи» (model comment, sources, attempts, model/prompt/revision) and the question in the runner preview without timer or charges. Authors see only the «Предложения» section for their own material (UX-22).
- Proposal entries: «Сообщить об ошибке» after a lesson, «Предложить правку» on a lesson, and a target path in another author's course during quest creation; each ends with «Предложить автору» and shows its fee before sending (UX-23).

## Cross-Story Dependencies

Build order: 4.11 → 4.12 → (4.13, 4.14) → 4.15 → (4.16, 4.17) → 4.18 → 4.19 → (4.20 blocked hitl) → 4.21 → (4.22, 4.23, 4.24, 4.25) → (4.26 blocked hitl). 4.12 depends on 4.11's submittable draft; stages depend on the worker; the author outcome and Decisions tab depend on verdict records; the fee change depends on pipeline terminal states; removal comes last among review stories. Proposals depend on pipeline checks and the fee path; the two rig journeys (4.20, 4.26) are blocked on a physical device (hitl) and stay blocked.
