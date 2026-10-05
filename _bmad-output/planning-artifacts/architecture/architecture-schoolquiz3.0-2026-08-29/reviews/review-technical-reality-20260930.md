# Technical reality gate — 2026-09-30

**Verdict: PASS for the adopted target contracts; no blocking reality finding.** This is a documentation architecture review, not implementation or production-readiness evidence. Correction economics remains PENDING OWNER ANSWER and the Q-EX-5 activation prerequisites remain open.

## Scope and evidence

Reviewed both architecture spines, EXAM-TECH-1 C1–C4 and its arithmetic fixture oracle against the current repository. Read AGENTS.md and project context. No application edits, builds, deployed services, financial scenarios or runtime activation were performed. All review work and this report stayed on the VPS.

- C1 constants and ordinary-worth mechanics exist in `functions/lesson-reward.js`: marginal W bands, ordinary 0.36/0.24 allocated-time coefficients, five-second floor, image equivalent 100, canonical translation deduplication, capped pool mean, HARD tariff 3, base 720 and separate integer currency conversion. `functions/index.js:1989` calls the current ordinary reward implementation. The mixed 5/15 weighting, captured publication basis and coefficients 3/10 are explicitly target rules grounded in the accepted owner decisions, not claims that current ordinary code already implements assessments.
- C2 nested truncation matches `functions/assessment-scoring.js:235–244` and `shared/core/scoring/.../Scoring.kt`. The existing scorer uses one percent contribution per nonzero digit. Assessment contracts correctly distinguish original selected-but-unanswered digit 1 from explicitly authorized exclusion, preserve all 20 original slots and do not reuse the empty-input zero as an all-excluded assessment result. Correction storage, CAS head and states are target obligations.
- C3 evidence gap is real: `ServedQuestion.kt:21–24` binds only question ID and codeAnswer index; `QuestionAnswerEntity.kt:26–43` contains lesson version and raw answer but no immutable question revision/required-manifest binding. Thus proposed manifests, raw evidence validation and confirmed set-union projection are new work. The document explicitly preserves ordinary offline local progression and does not pretend existing stars/counts prove revised full coverage.
- C4 reuses an existing adapter family: `functions/index.js:11` imports Firebase v2 `onSchedule`; scheduled workers are wired at lines 286 and 313. `functions/arena-season-worker.js` and `functions/tournament-worker.js` already provide bounded cursor/wrap/lease patterns. These are precedent, not evidence that exam sweeper/signing runtime exists. Target exam lifecycle remains separate. The [official Firebase scheduled-function documentation](https://firebase.google.com/docs/functions/schedule-functions), checked 2026-09-30, confirms the v2 onSchedule entry point. No new framework version, cryptographic algorithm or signing provider is invented or bound by C4.
- Existing `TimerCoefficients.kt:12` and `RunnerLogic.kt:166` still contain examFactor 0.75. The updated architecture deliberately supersedes that behavior for assessment implementation with ordinary HARD allowance; it does not falsely claim the code has changed.
- Independent exact rational recalculation verified 31 documentation cases: tariff endpoints, all reward and synthetic arithmetic cases, both worth cases and every correction score/pass/star case. This validates fixture arithmetic only; no Kotlin/JS assessment implementation parity has been demonstrated.

## Findings and limits

1. **Low — mixed document status label.** Both spines explicitly adopt C1, C2 arithmetic, C3 and C4, while the companion frontmatter still says `status: draft`. The surrounding text clearly records pending C2 economics, so this does not overturn the adopted sections. Prefer a qualified status or explicit adopted-section field to remove ambiguity. This is a presentation issue, not an invitation to decide pending economics.
2. **Open by design — activation and economic policy.** Concrete signing provider/algorithm, secret bindings, environment allowlist, numerical cadence/budgets/retention and runbook proof remain Q-EX-5. Whether an already settled reward is preserved or adjusted upward remains pending the owner. Neither is silently approved by this review.
3. **Implementation acceptance remains necessary.** Publication worth-source selection, immutable EASY proof, correction orchestration and exam signing/worker lifecycle are targets. Source grounding supports feasibility and consistency, not completion. Fixtures are accurately labeled a documentation oracle.

## Reviewed revision

Repository HEAD: `1e5961263a4c1ac85ad03eb7894a51bf44228a4a`.

- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/ARCHITECTURE-SPINE.md`: `eee4783e3a3cdd673efe4461e1f55181d774826d5f42432c62eb48533b367de0`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md`: `d7efb0e92d97ccffbbbd0d4d872a73b34ffbeb364dbb259b0c6171bd62eaf39d`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/assessment-technical-contracts.md`: `23c579cadb9cdb3ff824a8654671bb37d50c7d6828c5d23b31bb2192588b31ba`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/assessment-contract-fixtures.json`: `cfe53fda214981e0512c0191a9e49d4ed74550e7a440877eeff55533642d8c8a`

Review written at 2026-09-30T15:38:40.450740+00:00. Any material change to those inputs requires reconciliation against this review; identical hashes preserve this finding set.


## Post-fix final reality gate

**Final verdict: PASS. This addendum supersedes the earlier pending-economics and draft-status findings.** All C1–C4 target sections are accepted; only the concrete Q-EX-5 implementation/activation prerequisites remain open within this technical companion. This is still no claim that application behavior, runtime recovery or environment readiness has been implemented or verified.

Narrow review of the final changes:

- The companion now declares `accepted-with-open-questions`, lists C1–C4 as accepted and names the concrete Q-EX-5 activation remainder. The earlier low status-label finding is resolved.
- Explicit UTF-16 code-unit counting matches the existing JavaScript `String(...).length` operations in `questionCharsCount` in `functions/lesson-reward.js`. This clarifies the reused input convention without selecting a new time/reward basis.
- C2 now records the owner decision to preserve settled nolics/XP effects and reward-accounted history: no top-up, recoupment, reset or advance from a corrected display score. Both spines agree. The separate first-ever corrected theme pass remains a first award when no receipt exists, not an adjustment to an existing award.
- C1 binds delayed final reward work to the original terminal earning result and frozen basis. This prevents correction-before-fulfilment from silently choosing a different payout score. Recovery completes the original work and never creates a second effect.
- C2 now binds correction-head publication to atomic admission/best recomputation or an atomic durable correction-generation/pending barrier. The new-final start/charge transaction checks current/applied correction generation and readiness. Stale materializers are fenced by current version/generation. This closes the documented stale-pass authorization race as a target invariant and preserves already-open sittings. It makes no unsupported claim that the existing repository already has that projection/worker path.
- No provider, cryptographic algorithm, new dependency version, runtime permission or production activation was introduced by these changes. The unchanged fixture arithmetic was not rerun; its previously verified mathematical result remains applicable.

No remaining blocking or advisory reality finding for these final inputs. Implementation acceptance must still exercise the correction-head/projection/new-start race and durable reward recovery.

### Final reviewed hashes

- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/ARCHITECTURE-SPINE.md`: `4ea76d9e8d88e2d9f8bebb107cce0d289e3122d2d248b20bae8d43729b6326e0`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md`: `c4f3d5ceb46067e3a30eb8b5892b93f0ff8268a18f3ff0cacf58ee0b1379451e`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/assessment-technical-contracts.md`: `77ec948d24af5305c3642cc834d5b5e235d752bb3486627c7c4aa15f1a47dffe`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/assessment-contract-fixtures.json`: `cfe53fda214981e0512c0191a9e49d4ed74550e7a440877eeff55533642d8c8a`

Post-fix review recorded at 2026-09-30T15:44:38.752373+00:00.
