# Technical-contract adversarial review — 2026-09-30

**Final verdict: PASS — H1 resolved; no remaining blocking divergence found.** No source files were changed by this reviewer. This review concerns the two architecture spines and the root-authored technical companion, not the reviewer's own SPEC/protocol derivation.

## H1 — RESOLVED: publish corrected admission consistently with the corrected result head

**Scope:** EXAM AD-8/9/24/26, initiative AD-14/15, technical C2. **Disposition:** corrected and independently rechecked against the final architecture inputs. The finding and attack below describe the earlier revision; they are retained as review history.

C2 appends a corrected result and CASes its head, then requires recomputing theme admission/best from usable latest results. It forbids known-invalid original outcomes as current admission proof. It does not explicitly couple head publication to the admission projection or give final-start an authoritative correction-generation barrier. AD-8 makes the original terminal result/admission transaction atomic; C3 defines a barrier for lesson coverage. Neither clearly supplies the equivalent boundary for the correction path over theme results.

Two lower-level implementers can make locally plausible but incompatible choices:

1. The correction worker CAS-publishes a new FAILED, UNSCORABLE_ALL_EXCLUDED or CORRECTION_PENDING_EVIDENCE head, then durably queues an admission/best rebuild. It preserves evidence, eventual recomputation, correction idempotency and another independently valid pass. A crash can delay the rebuild.
2. The final-start service checks the authoritative passed-theme projection for the current course, verifies its ordinary bank/cooldown/charge conditions and commits the new sitting. It does not read private result documents or an unspecified correction generation. Until the rebuild lands, the old passed projection admits a new final from the invalidated theme result.

This is a real ownership/publication seam: each service can satisfy the wording allocated to it, while their composition breaches the no-invalid-proof invariant. A start can occur in the head/projection interval even without a crash. First-ever corrected theme awards already have an explicit atomic admission/receipt requirement; the same clarity is missing for corrected admission when no first award is created.

**Minimum contract fix:** either atomically commit the corrected head and consistent admission/best projection, or atomically establish a durable pending correction generation with the head, publish the projection against that generation, and have final-start atomically reject an incomplete/mismatched generation before charge. Projection workers must not overwrite a newer generation. Preserve separately valid passes when the generation is complete. Do not backdate/revoke already-started frozen sittings or introduce a new monetary policy.

**Verification case:** pause/crash the correction worker immediately after head publication; race final start and projection completion. No start may consume stale passed proof. Replay, a second correction and a separate valid pass must converge without stale projection overwrite or duplicate effects.

## Divergence attempts blocked by the current contract

- **Reward calculators choosing answer time, sampled-question worth or translated copies:** C1/AD-36 fixes publication-selected canonical pool worth, separate ordinary EASY/HARD time, frozen capped 5/15 or 20 means and exact integer/rational arithmetic. Currency rounding is independent from the same unrounded value. The all-HARD answer allowance cannot reprice a reward.
- **Reward workers paying recovered progress twice or losing a rounded-zero history update:** C1 and initiative AD-9 bind eligibility, receipt, wallet/XP effects and maximum accounted progress in one transaction. The maximum is separate from the latest certificate. Lower results, renewal and zero rounding cannot reset it; unpaid theme failures cannot reduce the first-pass award.
- **Learning ingestion and exam admission interpreting partial/legacy completion differently:** C3/AD-27 requires exact immutable manifest tokens, union across attempts, validated help provenance and a confirmed materialization generation. Unknown/empty/legacy evidence cannot become a fabricated complete state. Ordinary offline progression remains immediate.
- **Old workers, nondeterministic signatures or delayed signing changing the current certificate:** C4/AD-19/30 binds fencing, frozen claims/key, outside-transaction signing, first verified CAS publication and earning order independent of signing completion. Rotation retains pending-bound capability and historical verification material; neither keys nor issue dates can silently change.

## Final owner policy and remaining activation gate

The owner subsequently chose to keep each original settled reward. Final C2 and both spines now preserve the receipt, balance effect and reward-accounted maximum through correction, without top-up, recoupment, reset or correction-driven advance. The distinct first-ever corrected theme award still follows the existing single-receipt entitlement and frozen basis/b0. This is an explicit owner decision, not a reviewer inference. Q-EX-5 still names concrete provider/algorithm/environment/secrets, numerical budgets/retention and operational proof before activation; that gate is not a defect or a permission to guess.

No code execution, build, application test, runtime, service, scheduler, key or deployment change was performed. Arithmetic/provider reality belongs to the separate grounding review; this lens checks convergence between independent lower-level units.

## Initial reviewed input hashes

```json
{
  "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md": "d7efb0e92d97ccffbbbd0d4d872a73b34ffbeb364dbb259b0c6171bd62eaf39d",
  "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/ARCHITECTURE-SPINE.md": "eee4783e3a3cdd673efe4461e1f55181d774826d5f42432c62eb48533b367de0",
  "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/assessment-technical-contracts.md": "8a4165e8fd1e9aaf014195058024fa53908867fa55963fc578fa220da258ca36"
}
```

## Final H1 recheck

**PASS.** Read the final root-authored C2, EXAM AD-24 and initiative AD-14/15 independently of the reviewer's SPEC output. The correction-head transaction now either commits the matching admission/best projection or advances a durable generation, marks pending and queues recoverable work. Final start checks current/applied generations and readiness **inside the start/charge transaction**. The materializer must still match the current result/correction version and generation. A stale worker cannot publish over a newer correction.

Replaying the original two-builder attack now fails: the async correction builder must publish its pending barrier with the new head, and the admission builder must inspect that barrier transactionally before charging. The interval, crash and stale-materializer paths cannot consume the old passed projection. Independent valid passes remain usable after completed recomputation; already-open sittings remain frozen.

The final C2 also validates the original twenty slot identities and retained digits1..9; missing slots, zero placeholders and out-of-range digits are corrupt evidence, with typed pending evidence handling rather than a shortened denominator or plausible score. No new compensation, reward recalculation or certificate policy is inferred. The other four attempted divergences remain blocked by the adopted contracts.

This final verdict reviews architecture only. The reviewer did not independently certify their own SPEC/protocol changes; those were handed to another reviewer. No code/runtime or activation was performed.

## Final reviewed input hashes

```json
{
  "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md": "c4f3d5ceb46067e3a30eb8b5892b93f0ff8268a18f3ff0cacf58ee0b1379451e",
  "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/ARCHITECTURE-SPINE.md": "4ea76d9e8d88e2d9f8bebb107cce0d289e3122d2d248b20bae8d43729b6326e0",
  "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/assessment-technical-contracts.md": "77ec948d24af5305c3642cc834d5b5e235d752bb3486627c7c4aa15f1a47dffe"
}
```
