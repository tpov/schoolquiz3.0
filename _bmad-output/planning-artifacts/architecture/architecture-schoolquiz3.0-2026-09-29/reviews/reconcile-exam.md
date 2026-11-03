# Exam input reconciliation — 2026-09-29

Verdict: **FAIL** — one conflicting owner instruction between independently implementable units. This is step 2 input reconciliation, not the separate adversarial reviewer gate and not an implementation/runtime verdict.

## Top findings

1. **R-EX-1 / medium / blocking reconciliation:** certificate ownership differs between the initiative spine and current E9 companion. The initiative assigns the authoritative fact to exam, while E9 tells its implementer to reuse profile ownership. Define one write owner and the other feature's read contract.
2. **PASS:** the latest owner decisions are consistently reflected: exams and certificates are current MVP; approved revisions are directly scorable without mandatory pretest; statistics/reports trigger review; content-fault downward correction preserves the issued certificate exactly; stable theme passes carry while new themes must pass; a new final uses the current approved bank.
3. **PASS:** quiet constraints survive reconciliation: independent checks and revision binding, frozen active banks, current-availability counts, separate final certification/draw minima, online server score, no per-item correctness, first ACK/answer identities, single exam terminator, current-MVP year certificate with signed-out verification, and independent tournament lifecycle.
4. **PASS with implementation prerequisites:** remaining mixed-star, correction arithmetic, full EASY evidence and renewal-identity details are explicitly gated before affected work. They are not reasons to demand the whole protocol inside the initiative spine. Optional improvement/placement and production rollout remain distinct from mandatory MVP paths.

## R-EX-1 — Two current instructions assign the certificate fact to different owners

**Evidence:**

- [Initiative spine AD-1](../ARCHITECTURE-SPINE.md), line 66: `exam — admission/certificates`; the same rule prohibits UI/shared core from writing another feature's facts.
- [Current exam E9](../../architecture-schoolquiz3.0-2026-08-29/epic-breakdown.md), line 104: `Reuse profile ownership` in the certificate issuance/signing/verification unit.
- [ADR-0007](../../../../../docs/architecture/0007-certificates.md), lines 45–51: profile owns the model **and issuance**, persistence, UI and the completion-based issuance flow. E9 explicitly reintroduces profile ownership; therefore the old ADR cannot simply be dismissed as an unreferenced historical implementation seed for this sentence.

**Failure mode:** the exam unit follows AD-1 and owns authoritative certificate issuance/state, while E9/profile follows its current work instruction and also owns the same issuance/state. No current boundary explicitly restricts profile to a read-only projection, so each implementation can be faithful to one input and incompatible with the other. This is a shared ownership contract issue, not a missing signature algorithm, schema or detailed API.

**Minimal resolution:** keep the chosen authoritative certificate owner consistent. If AD-1 remains, E9 should reuse profile's list/projection/UI through an exam-owned issuance/verification contract; state that profile does not write certificate facts. Alternatively choose profile as authoritative owner everywhere and define the successful-final-result contract from exam. No additional user product decision is required for this technical boundary.

**Recheck:** read AD-1 and E9 together after the edit and confirm exactly one certificate write/issuance owner; profile display/public access must still preserve the immutable issued claims and old-key verification contract.

## Verified constraints and source reconciliation

| Area | Matched contracts | Result |
| --- | --- | --- |
| Scope and stable identities | Initiative scope/CAP map; SPEC CAP-1–21; exam AD-1–37; epics E1–E10 | PASS: all exam AD-1–37 and CAP-1–21 occur exactly once in ordered identity checks. No renumbering required. |
| Approved revision eligibility | Main AD-3; exam AD-10–16/22–23/35; SPEC CAP-3/14/19; protocol quality/start sections; E5/E7 | PASS: approval allows scoring; no hidden denominator/pretest. Error statistics are not verdicts. |
| Quarantine and bank readiness | Main AD-3/14; exam AD-12/23/35; SPEC invariants; solution bank section | PASS: histories cannot stand in for drawable availability; theme gate 5 EASY/15 HARD is distinct from final certification gate of 12 themes with 100 EASY/100 HARD and 20-HARD final draw. Quarantine can block new starts without rewriting active banks. |
| Progress and correction | Main AD-14; exam AD-8/9/24–27; SPEC CAP-17/invariants; protocol start checks; E3/E6/E8 | PASS: stable course/theme carry, required-new-theme pass, archived extras ignored, authoritative lesson confirmation before spend; erroneous results remain correctable. |
| Certificate content | Main AD-16; exam AD-19/24; SPEC CAP-8/18/21; protocol lines 87/97; solution certificate section; E9 acceptance | PASS: successful terminal final is the issuance authority; signed/public snapshot stays immutable after content-fault correction; original expiry never extends; verification key identity/material retained. The owner seam above is separate. |
| Lifecycle and privacy | Main AD-2/15/23; exam AD-1–7/17/21/29–33/37; protocol lifecycle/timing/redaction; E4 | PASS: separate exam/tournament policies; server-only private evidence; redacted current question; first-write answer; sole exam terminator; explicit open result; fixed safe advice. |
| Financial/recovery | Main AD-6/9/14; exam AD-18/28–29/34–36; SPEC CAP-12/13/15/20; protocol | PASS: start/charge atomic, lost response returns same open sitting, mode-specific cooldown begins at closure, one theme first-pass payout, no repeated correction effect. |

## Safe deferrals versus actual blockers

- **Mixed-sitting stars:** exam/result projection is the explicit owner; pass/fail is independently tied to captured percent threshold. Choosing display mapping before implementation is a valid local prerequisite, not a contradiction between units.
- **Correction arithmetic:** authorized exam correction owns versioned recomputation and admission effect; immutable issued certificates are already fenced. Denominator/rounding fixtures must be settled before E7 implementation; no arithmetic is invented by this report.
- **Full EASY coverage:** learning owns confirmed progress; exam AD-27 and E3 demand coverage of all required questions/version before exam integration. This is a mandatory integration prerequisite, not permission to substitute legacy rounded stars or a twenty-question sample.
- **Annual renewal:** year validity and re-earning through a new final are settled and remain mandatory for the relevant MVP path. Issuance identity/history is explicitly a prerequisite. Voluntary improvement before expiry is separately deferred and cannot replace an active certificate by accident.
- **Sweeper adapter:** main AD-23 already defines bounded trusted demo ticks using the same capability; target adapter/cadence evidence is due before runtime validation. This does not require new runtime permission during architecture work.
- **Actual blocker:** the certificate write owner conflict in R-EX-1 must be fixed before parallel exam/profile implementation. No other cross-unit incompatibility was established in this exam-focused reconciliation.

## Review conditions

Read-only inspection used VPS SSH in the authorized candidate. No builds, services, live sessions, financial actions, input/spine/memlog edits or Mac artifacts were made. Root AGENTS.md and .claude/PROJECT-CONTEXT.md were read; root README.md is absent. Existing lint-exam.json's `{uid}` placeholder warning is a literal Firestore path, not an unfilled template. This report does not turn old runtime evidence into a new PASS.

Reviewed inputs and SHA-256 at report generation:

- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md` — `5b6b2de6d2b8c3d1c2cfc810805b53de09ca399b0fb88802ddfbd115dd292ab8`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/ARCHITECTURE-SPINE.md` — `19bd9ba29aedf971c488e3b5e0c03d662826c2d0ef84611f3cd8e997f171fb87`
- `_bmad-output/specs/spec-theme-exams/SPEC.md` — `885d44e0e9290fcf120fedb838e7e5dc6522b29d9ecf3e93774bad4a460be235`
- `_bmad-output/specs/spec-theme-exams/exam-protocol.md` — `a0f6bc9c935ca1686b8deceeaa1a5354b66d48fff003129be3bcdd882858655d`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/solution-design.md` — `40bd6cc174668e7940b8eca9693189dc72bd01ba2676c156936733f8bc6e0014`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/epic-breakdown.md` — `c6299f0121ae4137337802ec6e8d2e7934c565330974da765d9028224cc30a6d`

Generated UTC: 2026-09-29T20:30:10.142815+00:00

## Resolution recheck — 2026-09-29T20:34:53.884292+00:00

**R-EX-1 RESOLVED; current reconciliation verdict PASS.** The earlier FAIL above is retained as review history. Main AD-1, exam AD-19, current E9 and amended ADR-0007 now consistently name exam as the authoritative issue/sign/verify owner; profile consumes the read-only projection/list/UI. The legacy generic completion/rewards path is explicitly not issuance authority. The minimal inter-feature boundary is sufficient; no signature algorithm or implementation detail is requested. No inputs were edited by this reviewer. The subsequent adversarial gate is reported independently in `review-adversarial.md`.
