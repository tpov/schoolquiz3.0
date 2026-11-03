---
id: EXAM-TECH-1
status: accepted-with-open-questions
updated: 2026-09-30
acceptedSections: [C1, C2-arithmetic-and-existing-entitlements, C3, C4]
openQuestions: [Q-EX-2-settled-award-correction, Q-EX-5-concrete-activation]
---

# Assessment technical contracts

Companion to [EXAM AD-3/19/24/27/30/36/37](ARCHITECTURE-SPINE.md). Versioned target contracts, not evidence that the assessment feature is implemented. Ordinary learning, Arena and tournament lifecycles retain their own rules. Screen content still requires separate owner confirmation.

## C1 — Reward basis and exact arithmetic

The owner accepted ordinary EASY/HARD worth for rewards independently of the ordinary-HARD allowance used for every assessment answer. The five EASY positions keep EASY worth; the fifteen HARD positions keep HARD worth. No exam timer factor enters this calculation.

Publication owns the approved available canonical-question pool at the captured course/bank version. Collapse translated variants to one canonical identity and exclude archived, quarantined or otherwise ineligible revisions. The manifest binds exactly one ordinary worth-source revision per canonical question, chosen once by publication; language selection, retries and client ordering do not choose financial worth. The server freezes the resolved counts, time sums, source manifest/hash and rewardRuleVersion with the new sitting. Unavailable or ambiguous basis refuses before spending; it is not zero worth. Validate counts/time sums against the frozen manifest and every ordinary per-question minimum: Theme TE>=25 and TH>=75; Final TE=0 and TH>=100. A malformed persisted basis blocks settlement; it never reaches the payout calculator as a plausible amount.

For each source question, use the existing ordinary character count (UTF-16 code units of question and option/item/candidate text, plus100 for an image) and ordinary allocated time:
- EASY: `sE=max(5,roundHalfUp(chars*0.36))`.
- HARD: `sH=max(5,roundHalfUp(chars*0.24))`.

The capped pool mean preserves ordinary reward worth independent of a lucky random draw:
- Theme: `TE=roundHalfUp(5*sum(sE)/countE)`, `TH=roundHalfUp(15*sum(sH)/countH)`.
- Final: `TE=0`, `TH=roundHalfUp(20*sum(sH)/countH)`.
- `B=(TE+3*TH)/720`. Counts must satisfy the existing5/15 or20 draw gate. Do not call the ordinary20-question cap separately on both theme difficulties; that would price40 questions.
- Later publication or fault corrections do not mutate the sitting's frozen worth. Never substitute actual elapsed time, the all-HARD answer timer, current content or a new pool during settlement.

Use the single authoritative aggregate percentage `p` (integer0..100) and the durable maximum already reward-accounted percentage `b` for that user/assessment. Do not add independent EASY/HARD percentage histories. The existing cumulative tariff `W` has marginal bands0–25×1,25–50×2,50–70×3,70–90×4,90–100×5; W(100)=265.

| Mode | c for NEW | r for REPEATED | Eligible settlement |
| --- | --- | --- | --- |
| Ordinary | 2 | 0.1 | Existing ordinary contract, unchanged |
| Theme | 3 | 0 | One first pass per user/theme; b=0 before that award |
| Final | 10 | 0.1 | Every recorded terminal sitting, including failure |

`P=c*max(W(p)-W(b),0)+r*W(min(p,b))`; `R=P*B`. Compute `nolics=roundHalfUp(R/10)` and `skillPoints=roundHalfUp(R)` independently from the same unrounded R. No per-band/per-question currency rounding, no nolics derived from rounded XP, and no extra whole-reward mode multiplier.

Version1 can calculate exactly in nonnegative integers: let `A=10*c*max(W(p)-W(b),0)+(10*r)*W(min(p,b))` and `N=A*(TE+3*TH)`. Then XP rounds N/7200 and nolics N/72000; half-up(n/d)=floor((2*n+d)/(2*d)). Use checked wide/rational arithmetic, not an overflowing intermediate or accumulated binary-floating rounding. Invalid input produces an explicit failure/pending state, never a fallback zero.

One transaction binds the eligibility, financial receipt, wallet/XP effect and `bNext=max(b,p)`, even when both rounded amounts are zero. Bind owner, assessment, sitting/effect identity, earning result version, p, bBefore/bAfter, basis/hash, rule version and both integer amounts. Retry returns this receipt. A failed unpaid theme test consumes no first-pass progress; a recorded theme award can never occur twice. Concurrent attempts serialize on the same assessment history. Certificate display, expiry, renewal and corrections never reset b or select it.

## C2 — Confirmed-fault correction arithmetic

Keep original20 ordered slots with question/revision/key, issued permutation, canonical answer, timing, scoring version and captured pass mark. Unanswered/late selected positions have ordinary lowest digit1; missing evidence is different. Only an authorized confirmed revision fault creates an exclusion; statistics, later edits or client requests cannot do so.

Map the cumulative authorized exclusion set to distinct ORIGINAL slots. Reproduce each retained digit from frozen evidence and scoringVersion, without swapping keys/questions or reversing canonical ordering twice. For `N=20-excludedCount>0`, calculate exactly as the existing shared scorer:
`percent=floor(sum(floor(100*(digit_i-1)/8))/N)`.
All retained positions have equal assessment weight, including EASY/HARD and unanswered positions. Excluded positions leave both numerator and denominator. Never infer denominator from the submitted-answer list or overwrite original evidence with zero digits.

Compare percent to the captured pass mark. Theme stars remain `floor((30*percent+50)/100)` tenths; percentage itself is truncated, not rounded half-up. Reapply advice support/privacy rules after exclusions; the public projection exposes no private mask or item verdict.

Append correctionId, attributable review/actor/reason/time, original evidence identity/digest, cumulative exclusions, scoringVersion, correctionPolicyVersion1, denominator and the resulting version. CAS the head. Identical correctionId/body replays the same effect; conflicting reuse rejects. Recompute from original evidence plus cumulative exclusions, never from a previously rounded result; independent exclusion order produces the same score.

Recompute theme admission/best from usable latest results across sittings. An ordinary lower retake cannot remove a pass, but an authorized correction can; another independently valid pass still suffices. Correction preserves the original charge and cooldown facts and never creates another sitting.

If all slots are excluded, mark the affected result UNSCORABLE_ALL_EXCLUDED with no computable score. If required evidence is missing/corrupt or the scorer version cannot reproduce it, mark CORRECTION_PENDING_EVIDENCE for recoverable investigation. Both have numeric score/pass/stars absent; the permanent empty denominator must not look like ordinary work still running. Do not use an empty-input zero fallback, fabricated100%, or known-invalid original result as current admission proof. Preserve the original as history; a separate valid pass can still support admission. This state grants no invented refund, free retake or cooldown reset; permanent compensation/remediation needs its own product decision before being added.

Certificates follow the owner's explicit distinction: originally earned issuance, including pending signing, remains unchanged; correcting an original failure into a pass creates NO certificate. A new successful final sitting is required.

### Correction and economic effects

PENDING OWNER ANSWER: whether an already settled attempt keeps its original award or receives an upward correction adjustment. Do not implement an assumed adjustment policy.

A first-ever theme pass established by a valid correction is distinct from a second award: if no first-pass receipt exists, the existing first-pass eligibility applies once, atomically with admission, using that sitting's frozen reward basis and b=0. Original failed unpaid theme results did not consume reward progress. The stable user/theme receipt prevents another corrected or ordinary pass from paying again.

## C3 — Confirmed full-EASY coverage

Publication emits an immutable required-EASY manifest per lesson/course version: manifest identity/hash/schema version, hierarchy/lesson version and the exact set of canonical (questionId,approvedRevision) tokens with immutable validation provenance. Translation variants cannot inflate the set. Required membership comes from learning rules; an exam draw, count or stars cannot define it.

Freeze the manifest reference, per-question revision, raw/canonical answer, timing, completion kind and any permitted help-claim identity with the existing ordinary local attempt/outbox transaction. Offline ordinary practice requires no new server ticket and stays immediate. Preserve owner/account epoch, operation/body identity and append-only local evidence across content changes.

Learning ingestion validates owner, immutable request, exact manifest membership, difficulty/revision, and the answer against immutable approved content. On ingestion, validate and settle permitted learning-help claims under the existing ordinary transaction; freezing offline evidence does not require help to have already settled online. Client-claimed help or self-consistent digit9 alone proves nothing. Successfully completed tokens use the existing full-credit learning condition. Union confirmed successful tokens across attempts; the runner's20-question cap must not make a larger required set impossible. Repeated tokens count once and later ordinary mistakes do not erase earned coverage.

The authoritative predicate is `requiredTokens ⊆ confirmedSuccessfulTokens` at the current manifest. Bind its projection to requiredSetHash, coverage schema/generation and confirmed ingestion receipts. Counts are display-only. Missing/unknown/ambiguous empty manifests are unavailable, never vacuously complete. Publication must explicitly validate any intentionally empty required set under learning rules.

An updated manifest keeps proof for unchanged exact tokens; newly required or revised tokens need evidence and removed/archived tokens cease to be required. This does not revoke an already passed stable theme for final admission: AD-25/26 still carry passed course/theme IDs. Active sittings remain frozen.

Commit evidence/receipt/projection atomically or use durable materialization with an explicit pending barrier. New exam start atomically rechecks current course/manifest and complete confirmed generation before charge. Known incomplete is Locked; relevant undelivered/unmaterialized progress is ResultsNotSynced; missing authority is Unavailable. An upload acknowledgement alone is not proof. The client flushes relevant existing outbox work and presents its receipt barrier; account changes cannot attach another session's response. Admission ingestion never repays ordinary results.

Legacy coverage is reconstructed only from conclusive immutable historical manifest/raw-answer provenance. Do not infer revisions from current text, best stars or lessonVersion alone. Preserve unknown/pending and require recoverable evidence. No ordinary offline progression rule is silently changed by this stricter server exam gate.

## C4 — Execution, recovery and signing boundary

Exam owns a bounded server worker with injected trusted clock/store. An isolated controlled test/demo driver calls this capability; production uses the repository's existing Firebase Functions v2 onSchedule adapter. Neither mode exposes a client-selected worker clock, cursor, lease or work item. The adapter is a wake-up source, not an exactly-once guarantee.

The exam worker remains the sole logical terminal writer. CAS state/version and a lease/fencing token at each mutation; use durable bounded sweep progress and wrap so work behind a cursor is eventually visited. Retry, overlap, expired leases and crashes converge on the same terminal, charge, reward and issuance receipts. A poison record remains actionable pending work and cannot starve unrelated work. Foreground finish requests never become a competing terminal writer. Scheduler delay never extends question deadlines.

Signing claims durable work with an expiring fence, reads the exact frozen claims/key identity, signs outside the transaction, verifies, then CAS-publishes against issuance identity, claims digest and fence. First committed verified artifact wins; do not require signatures to be byte-deterministic. Before/after-publication retries preserve identity/dates, while latest-earned selection remains independent of signing completion.

Rotation publishes verification material before switching the active key for NEW earning outcomes. Retain old verification material for retained attestations and old signing capability while pending facts remain bound to it. Never rebind pending facts silently. Missing bound keys leave explicit recoverable pending state, not unsigned publication or a fabricated invalid result. Drain/verify pending and historical cases before retiring signing capability.

The runbook must prove interruption before/after terminal commit, after signing/before publish, overlapping workers, lost fence, poison work, restart/backlog recovery, old pending issuance after rotation and delayed older publication. No runtime, scheduler, IAM, secret or service activation is authorized by this document.

Signing provider/algorithm, secret bindings, environment allowlist, numerical cadence/budgets/retention and operational evidence remain implementation/activation prerequisites under Q-EX-5. Select concrete adapters and prove them before enabling the relevant environment; this contract does not claim production readiness or bind a new provider.

## Contract fixtures and implementation acceptance

Versioned [arithmetic fixtures](assessment-contract-fixtures.json) accompany C1/C2. They are a documentation oracle for later Kotlin/JS integration, not a passing implementation test. The full source tree's existing shared scorer fixtures remain authoritative for per-item scoring.

- Reward: weighted-band endpoints, independent currency rounding, zero award still advancing b, first theme pass after failures, final90→70→90→95, frozen worth across pool edits, duplicate translations and per-attempt replay/concurrency.
- Correction: exclusions in either direction, unanswered denominator, nested truncation, all-excluded/missing evidence, cumulative-order independence, another valid theme pass, no retrospective certificate, preserved original-success pending issuance, and the finalized economic boundary.
- Coverage: required30 with first20 then remaining10; repeating20/wrong IDs/duplicate language/forged digits/wrong revisions/owner rejects; offline local progress before confirmed admission; publication-version race and legacy unknown.
- Recovery: every crash/fencing/key-rotation case in C4; preserve server times and receipts, no hidden keys/per-item verdicts.

## Grounding and ownership

C1 reuses functions/lesson-reward.js bands, ordinary time, capped mean, difficulty tariff and independent currency conversion; the mode coefficients and ordinary-worth/timer separation were explicitly accepted by the owner. C2 reuses functions/assessment-scoring.js and shared/core/scoring percent truncation. C3 closes gaps observed in ordinary served/answer/outbox records that currently lack per-question revision evidence. C4 follows existing functions/index.js scheduled adapters and worker patterns without importing tournament policy.

The current [Firebase scheduled-function documentation](https://firebase.google.com/docs/functions/schedule-functions) confirms the existing v2 onSchedule integration. Checked2026-09-30; no new framework version or cryptographic provider is selected.
