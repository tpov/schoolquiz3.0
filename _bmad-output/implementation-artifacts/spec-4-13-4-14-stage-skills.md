---
title: '4.13+4.14 stage skills — «Слова» and «Факты» over the shared strict verdict schema'
type: 'feature'
created: '2026-10-27'
status: 'done'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'auto'
lenses_ran: []
review_loop_iteration: 0
baseline_commit: '3046b778b3dd2c62beae08b19f5ee869dfbe5251'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The worker (4.12) runs the plan WORDS → FACTS, but no stage skill exists: the queue refuses to move (STAGE_MISSING) and nothing judges content. Stories 4.13 (forbidden words for a children's audience, doubt rejects) and 4.14 (facts with web search and recorded sources) are the two skills the plan names.

**Approach:** One shared strict verdict schema with a linter that refuses any deviation with a named error; two versioned skills that own only their prompts, tools and lint — the model call stays in the shared client. Doubt rejects (owner decision D2): the schema has exactly PASS and FAIL, and a doubt is a FAIL with the doubt named in the reason. The model never receives a way to edit the author's text: the schema has no edit fields.

## Boundaries & Constraints

**Always:**
- Skill contract as the worker validates it: `promptVersion` (string), `systemPrompt()`, `userPrompt(context)`, `lint(content, context)`; optional `tools()` for model-call instruments.
- Lesson text rides only inside the shared `<<<LESSON_DATA>>>` envelope; prompts state that data is never instructions.
- Every question of the lesson named exactly once; unknown verdicts, empty reasons, missing rationale, non-https sources — named refusals.
- «Факты» requires at least one https source per reply and passes its search tool (`web_search`) to the call; «Слова» passes no tools.
- The registry covers the full `STAGE_PLAN` and a coverage guard (`completeReviewStages`) turns an incomplete deployment into a loud error.

**Never:**
- No live model calls in tests (scripted replies only); prompt quality on a real model is 4.20's trial runs (SPEC OQ-10), not a gate here.
- No client names/sentinels yet (client surfaces come with 4.16+).
- No changes to the worker's scheduling, verdict records, or fee logic.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Valid reply | Strict JSON covering all questions | Parsed verdict records; «Слова» accepts empty sources; «Факты» requires ≥1 https source | No error expected |
| Fenced JSON | ` ```json ` wrapper | Unwrapped whole and parsed | No error expected |
| Any schema deviation | Not JSON / not object / no questions / dup or foreign or uncovered question id / unknown verdict / blank reason / blank rationale / bad source scheme / empty sources in facts | Named refusal (`REVIEW_SCHEMA_*`), zero verdict fields produced | Linter refusal → worker retry ledger |
| Doubt | Model unsure | Expressed as FAIL with the doubt named in the reason | No third verdict exists |

</frozen-after-approval>

## Code Map

- `functions/review-queue-worker.js` (`requireStageSkill`, `advanceStep`) — the validated skill contract and where `tools` reaches the model call.
- `functions/review-model-client.js` (`wrapLessonData`, `judge({system, user, tools})`) — the shared transport the skills never bypass.
- `functions/review-pipeline.js` (`STAGE_PLAN`) — the plan the registry must cover.
- `scripts/review-queue-worker-run.js` — consumes `completeReviewStages()`.

## Tasks & Acceptance

**Execution:**
- [x] `functions/review-stage-schema.js` — strict reply parser/linter with named refusals; tests in `review-stage-schema.test.js` (12 deviations + sources policy).
- [x] `functions/stage-words.js` — words-v1 skill (children's-audience rules, doubt rejects, no tools); `functions/stage-facts.js` — facts-v1 skill (quest-audit checklist in judge mode, mandatory https sources, `web_search` tool).
- [x] `functions/review-stages.js` — registry + `completeReviewStages` coverage guard; runner wired to it.
- [x] Worker and model client extended to carry `tools` from the skill to the call body.

**Acceptance Criteria:**
- Given a scripted model reply, when each skill lints it, then only schema-perfect replies produce verdict records and every deviation yields its named `REVIEW_SCHEMA_*` error.
- Given the registry, when the worker validates stages, then both plan stages resolve with full contracts and a missing skill is a loud startup refusal, never a half-running queue.

## Implementation Notes

- First implementation round exported `PROMPT_VERSION`; the worker contract validates `promptVersion` — renamed the export to the contract, tests read the same field.
- The `sed -i` attempt inside the read-only /repo mount failed; edits go through the workspace tooling, tests through the container.

## Spec Change Log

## Review Triage Log

## Design Notes

- Why sources are model-listed and https-only: the reply's own citation list is the provider-neutral record the verdict binds; envelope-level search-result capture is provider-specific and belongs to the trial runs (4.20/OQ-10).

## Verification

**Commands:**
- `node review-stage-schema.test.js`, `node review-stages.test.js`, `node review-queue-worker.test.js`, `node review-model-client.test.js` -- all green (4+4+8+5).
- `npm test` (functions, in `schoolquiz-functions-emu`) -- 134/135; the single red is the pre-existing `mvp-isolation.test.js` (attribution in spec-4-11).
