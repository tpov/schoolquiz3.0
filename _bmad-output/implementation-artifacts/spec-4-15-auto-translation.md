---
title: '4.15 auto-translation — перевод прошедших уроков внутри конвейера проверки'
type: 'feature'
created: '2026-10-27'
status: 'done'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'auto'
lenses_ran: []
review_loop_iteration: 0
baseline_commit: '657bcfbb5a8d5215048a11230902b443bdd612f5'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The pipeline (4.12–4.14) judges lessons and stops: nothing translates passed content, yet publication needs the quest in all three app languages (AD-32: «после прохождения — шаг перевода: определение языка источника, перевод на остальные языки приложения, линтер структуры»).

**Approach:** A third pipeline stage TRANSLATE after WORDS and FACTS, per lesson. Only a lesson whose every question got PASS in both check stages is translated; a lesson with any FAIL skips translation (stage state SKIPPED, no model call, no record). The stage skill detects the source language (no author language field exists) and returns translations for the other two languages of {ru, uk, en}; a structure linter refuses any deviation with a named error — option/item/candidate counts and ids, answer keys, FillBlank `___` marker count, protected segments kept verbatim (AD-27: protected spans are code/terminology that must survive untranslated). The model reply carries only text fields; the assembler rebuilds each translated question document from the source payload, so ids and keys in the output are the server's own, never the model's. An immutable translation record lands in a new server-private collection; publication (4.16) consumes it later.

## Boundaries & Constraints

**Always:**
- Linter refusals are named (`TRANSLATION_*`), cost an attempt, retry ≤3, then the lesson is REVIEW_DELAYED — same rules as the check stages; transport failure is not an attempt.
- `protectedTextSegments` never come from the model: the assembler copies them verbatim; the linter refuses a reply whose translated text or pool drops a protected term the source carried (positional candidate rule: a candidate equal to a protected segment stays byte-identical).
- FillBlank: translated text keeps the exact `___` marker count of the source (blanks map to markers by order, RunnerStateMapper.kt:249).
- Translation record id `{submissionId}_{lessonId}_TRANSLATE_{attempt}` via create-only — idempotency by document name, like verdicts.
- New collection `review_translations` closed in `firestore.rules` (`allow read, write: if false`) + probe row in `scripts/review-pipeline-private-rules.test.js`.

**Never:**
- No publication, no author-facing surfaces, no quest-review_requests status changes (4.16).
- No fee changes (4.18). No client code — client names arrive with 4.16+.
- No live model calls in tests (scripted replies only); translation quality is 4.20's trial runs (OQ-10).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Passed lesson | WORDS+FACTS done, every question PASS | TRANSLATE job: one model call, one immutable record with both target languages, stage DONE | No error expected |
| Failed lesson | any FAIL verdict in WORDS/FACTS | Stage SKIPPED without a model call or record; quest can still reach QUEST_REVIEWED | No error expected |
| Structure deviation | option count/id changed, key touched, `___` count wrong, protected term translated/missing, blank text, bad coverage | Named `TRANSLATION_*` refusal, zero output fields, attempt spent | Retry ≤3 → REVIEW_DELAYED |
| Bad language | detectedLanguage ∉ {ru,uk,en} or wrong target keys | `TRANSLATION_LANGUAGE_UNKNOWN` / `TRANSLATION_LANGUAGE_KEYS_INVALID` refusal | Same retry path |
| Transport failure | REVIEW_MODEL_UNAVAILABLE | Not an attempt; lesson stays queued | Bounded retry later |
| Crash after record write | record exists, checkpoint lost | Resume applies the verdict idempotently; no second call or record | No duplicates |
| Registry incomplete | TRANSLATE skill missing | Sweep refuses STAGE_MISSING, queue untouched | Loud |
| Source payload unparsable | lesson question payload not JSON | `TRANSLATION_SOURCE_PAYLOAD_INVALID` stage error | Retry path; never a guessed output |
| Legacy pipeline doc | lesson.stages without TRANSLATE | Loud RangeError in nextJob (repair), never silent skip | Reported by sweep |

</frozen-after-approval>

## Code Map

- `functions/review-pipeline.js` — STAGE_PLAN gains TRANSLATE; `STAGE.SKIPPED`, `applyStageSkip`; `nextJob` emits TRANSLATE only when both checks are DONE; `questStatus` counts SKIPPED as done; `translationDocumentId`; `nextJob` guards a missing stage entry with a loud RangeError.
- `functions/review-translation-schema.js` (new) — `parseTranslationReply(content, {questions})`: strict JSON, detected language + exactly the two target keys, per-question id coverage, per-type text lists, all structure rules; assembles translated question documents (`{id}__{lang}`, language=lang, payload rebuilt from source with translated texts, top-level text=payload.text, everything else verbatim).
- `functions/stage-translate.js` (new) — translate-v1 skill: system/user prompts (audience 7–15, keep ids/markers/protected terms verbatim), no tools, `lint` delegates to the schema module.
- `functions/review-stages.js` — registry + coverage guard gain TRANSLATE.
- `functions/review-queue-worker.js` — `advanceStep` branch for TRANSLATE: read lesson verdicts (ids from pipeline state) → any FAIL ⇒ skip; crash-recovery record check; record write via create-only transaction (`review_translations`); `TRANSLATIONS_COLLECTION` export.
- `scripts/review-queue-worker-run.js` — comment update only (registry carries all three skills).
- `firestore.rules` + `scripts/review-pipeline-private-rules.test.js` — close `review_translations` and probe it.
- Tests to extend: `review-pipeline.test.js` (SKIPPED/order/DELAYED/legacy doc), `review-queue-worker.test.js` (scripted stages gain TRANSLATE; seeds gain the stage entry), `review-stages.test.js` (coverage). New: `review-translation-schema.test.js`, `stage-translate.test.js`.
- Do not touch: fee paths, publication, `quest_review_requests` writes, client code, `functions/mvp-isolation.test.js` (in-flight sibling edit).

## Tasks & Acceptance

**Execution:**
- [x] `functions/review-translation-schema.js` + tests: every matrix row's refusal named; assembled documents byte-verified against source structure.
- [x] `functions/stage-translate.js` + prompt-version test.
- [x] `functions/review-pipeline.js` machine extension + tests (SKIPPED transition, TRANSLATE ordering, legacy loudness).
- [x] `functions/review-queue-worker.js` TRANSLATE branch + tests (pass→record, fail→skip, linter ×3→DELAYED, idempotent resume, STAGE_MISSING).
- [x] `firestore.rules` + rules probe extension; registry/runner comment updates.

**Acceptance Criteria:**
- Given a lesson with all questions PASS in both stages, when the worker reaches it, then exactly one translation record exists binding revision, model, promptVersion, detectedLanguage and both target languages, with structure equal to the source modulo translated text fields.
- Given a lesson with any FAIL verdict, when the pipeline advances, then TRANSLATE is SKIPPED with no model call and the quest still terminates.
- Given any linter refusal repeated three times, when the attempts exhaust, then the lesson is REVIEW_DELAYED with zero translation records and named errors preserved.

## Implementation Notes

- Workflow deviation, sanctioned by the run mandate: autonomous wave under sprint-change-proposal-2026-10-26 §5 step 5; spec goes straight to `ready-for-dev` (same as 4.11–4.14).
- Baseline moved 44fc0f83 → 657bcfbb between wave start and spec write: sibling e7 fix-pack committed its spec; working tree carries their in-flight `functions/mvp-isolation.test.js` edit — untouched here.

## Spec Change Log

- 2026-10-27, verification notes: gates as run — focused suites `node review-translation-schema.test.js` (10), `review-stages.test.js` (5), `review-pipeline.test.js` (13), `review-queue-worker.test.js` (11) all green; full functions gate `npm test` **136/136 suites passed, zero reds** — the long-standing `mvp-isolation` red was fixed at HEAD by the sibling e7 wave (commit c1f07487 re-pinned the cleartext test to the committed NSC), not by this story. Rules probe `scripts/review-pipeline-private-rules.test.js` PASS with 37 checks; `review_translations` now probed alongside the other three review collections (4 collections × 3 roles × 3 methods denied + live-ruleset positive control; the running demo emulator's default-deny covers the new collection until the next owner-gated rules deploy, same caveat as 4.12).
- 2026-10-27, implementation loopback: the eligibility check reads verdict contents (any FAIL ⇒ SKIPPED) rather than trusting stage DONE alone — the machine stays pure, the policy lives in the worker's `translateLessonEligibility`, with named refusals `TRANSLATE_VERDICT_MISSING`/`TRANSLATE_VERDICT_UNREADABLE` when a bound verdict cannot be read (never a guessed skip).

## Review Triage Log

## Design Notes

- Why the model never sees or returns ids-as-authority: the reply's ids are alignment keys the linter checks against the source; the assembler takes structure from the source payload, so a hostile or sloppy reply cannot move an answer key.
- Why SKIPPED (not DONE-with-flag): the machine's terminal math must distinguish «translated» from «not translatable» without inventing a verdict-shaped record for a lesson the author will rework.

## Verification

**Commands:**
- `ssh rnsrv 'docker exec schoolquiz-functions-emu sh -c "cd /repo/functions && npm test"'` — full functions suite (expect the single pre-existing mvp-isolation red only; sibling edits may alter it — attribute, don't fix).
- Rules probe: `ssh rnsrv 'docker exec schoolquiz-functions-emu sh -c "cd /repo && node scripts/review-pipeline-private-rules.test.js"'` with emulator env — all denies hold including `review_translations`.

**Manual checks (if no CLI):**
- None beyond the suites.
