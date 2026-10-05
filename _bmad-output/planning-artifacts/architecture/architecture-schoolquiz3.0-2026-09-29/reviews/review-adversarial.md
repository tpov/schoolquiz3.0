# Adversarial architecture gate — 2026-09-29

Lens: configured BMad adversarial reviewer. Altitude: initiative → independently built features; exam feature → E1–E10 epics. This is independent of input reconciliation and does not claim implementation or runtime acceptance.

**Current verdict: PASS after the appended R-ADV-1 resolution recheck.** No open critical, high or medium gate finding remains. The initial finding and pressure test below are retained as review history; its contract fix is now in the reviewed sources. Initial verdict was FAIL for one medium cross-epic authorization gap.

## Top results

1. **R-ADV-1 / medium:** E7 correction and E9 certificate issuance can disagree on which result version authorizes a not-yet-issued certificate after a final sitting passed and was then corrected downwards.
2. **PASS:** the previous certificate owner conflict R-EX-1 is fixed: exam owns issue/sign/verify; profile reads the projection/UI. The corrected E9 and amended ADR-0007 no longer independently authorize profile/rewards issuance.
3. **PASS:** narrow shared contracts preserve independent ordinary/Arena/exam/tournament lifecycles; recovery does not imply new money, time or session identity. Publication owns approved revisions and bank manifests; availability and historical preservation are separate.
4. **PASS with explicit prerequisites:** deferred arithmetic/display/renewal/environment details already have owners and revisit points. No finding demands implementation code, algorithm listings or moving every feature detail into the initiative spine.

## R-ADV-1 — The earning result version needs one irreversible award boundary

Severity: **medium**. Disposition: **tighten the exam contract before E7/E9 parallel implementation**. This is a result/certificate authority boundary, not a choice of signature provider or cryptographic algorithm.

### Current source evidence

- [Main AD-16](../ARCHITECTURE-SPINE.md), rule: a successful final creates the certificate; a later content-fault correction preserves the certificate already issued.
- [Exam AD-17/19/24](../../architecture-schoolquiz3.0-2026-08-29/ARCHITECTURE-SPINE.md): original results survive; corrections append new versions and may lower the score; only a successful terminal final authorizes issuance, while already-issued certificates remain immutable.
- [Exam protocol](../../../../specs/spec-theme-exams/exam-protocol.md), lifecycle and persistence sections: terminal TX owns result/admission/cooldown/first-pass effects; issuance has stable identity linked to the successful final result. The result projection includes a version, but no rule pins the version that wins issuance before any signature/certificate exists.
- [E7 and E9](../../architecture-schoolquiz3.0-2026-08-29/epic-breakdown.md): correction and issuance are distinct work units, with E9 consuming the final result and correction contract.

### Two compliant implementations diverge

1. E8 closes final sitting S as result v1, pass=true. Issuance/signing runs later; no certificate fact has yet committed.
2. E7 confirms a wrong question, appends result v2 with pass=false, and preserves v1 as required.
3. E9 implementation A binds issuance to the preserved successful terminal result v1 and issues the original attestation. E9 implementation B reads the latest authoritative result v2 and refuses issuance because there is no longer a successful result at the time it issues.
4. Both preserve any **already-issued** certificate and both can claim to issue only from a successful final result. The current text does not say when the entitlement/frozen certificate fact becomes irreversible or which result version is authoritative for it. Task scheduling changes the observable outcome.

Idempotency by sitting alone does not fix this: it can prevent duplicate work after an issuance decision but does not choose the decision/result version.

### Narrow proposed resolution and pressure test

The root proposes that the successful final terminal TX atomically records the immutable certificate issuance fact and durable sign/delivery work together with the result. The fact binds stable issuanceId, earning resultVersion, frozen public/signed claims, key identity, issuedAt and original expiresAt. Signing/publication fulfils the fact after commit, with no second score-eligibility check and no private-key/network operation inside the database transaction.

**Pressure-test verdict: sufficient.** Logical issuance is the committed fact; a later content correction leaves it intact even if the signature is not yet visible. A crash before commit creates neither terminal result nor award fact; after commit, the same bound work is retried. Retry does not change claims, key identity, dates or term. Pending signing/publication must remain an explicit projection state, not an invalid certificate or a newly authorized attempt.

This resolves the missing seam without granting profile any write authority, changing the approved correction policy, making a crypto/provider choice or requiring a synchronous external signing call in Firestore TX. It applies to the authorized initial issuance path and does not silently decide the separately gated renewal/improvement identity.

**Acceptance for closing this finding:** the exam spine/protocol/E9 and main certificate rule consistently place the immutable issuance fact at the successful final commit boundary, bind its earning result version, and distinguish fulfilment retries from eligibility decisions. A source read is enough for this architecture recheck; no runtime is authorized.

## Attempted attacks that did not establish another blocker

| Independent units / attempted incompatibility | Governing boundary | Assessment |
| --- | --- | --- |
| Profile creates a certificate from course completion while exam creates another | Main AD-1, exam AD-19, current E9 and ADR-0007 amendment | Rejected after R-EX-1 fix: profile is explicitly read-only and generic completion is not authority. |
| Tournament adopts exam ACK gaps/sweeper or ordinary play adopts continuous tournament clocks | Main AD-2; exam AD-1/3/20/30; protocol lifecycle | Rejected: lifecycle/timing/terminal ownership is explicitly mode-local. Shared answer/scoring types do not unify policy. |
| Review copies approval to changed questions or publication counts quarantined history as current available bank | Main AD-3/4; exam AD-10–16/23/35 | Rejected: immutable revision/provenance and available counts rule this out. Modes retain their own draw/certification gates. |
| Learning grants admission from purchased access, rounded stars or a sampled twenty-question lesson | Main AD-14; exam AD-27; E3; SPEC full EASY prerequisite | Rejected: learning owns authoritative full-coverage progress and exam waits before charging. The shared evidence detail is explicitly required before integration. |
| Terminal scoring and correction erase original evidence or mutate an already-issued certificate | Exam AD-17/19/24 and protocol correction section | Rejected for already-issued facts: append-only result versions and immutable attestation are explicit. Only the pre-issuance gap above survives. |
| Lost start/answer response causes second debit, new sitting, replacement answer or fresh timer | Main AD-6/9/14/15; exam AD-18/29–31 | Rejected: immutable operation identity, one open sitting, first ACK/answer and server recovery govern these effects. |
| Shared outbox deletes Arena retained settlement on a sent/terminal flag while feature still needs proof | Main AD-7/11/12 | Rejected: feature proof/exact resolution and retained SETTLE/CANCEL are explicit; transport cannot declare business completion. |
| Refund/grant or season/tournament workers duplicate value under replay or partial work | Main AD-9/13/17–19/23 | Rejected at architecture altitude: single economy ownership, stable effect/receipt identity, conservation, complete cohorts and bounded resumable fenced work are explicit. Arithmetic/handler code is implementation. |
| Sync misses equal-timestamp records or forces a destructive rebuild | Current main AD-8/22 | Rejected by current cursor=(changedAtMs,docId) and preserved pending/data migration rules. The earlier timestamp-only wording is no longer the reviewed rule. |
| Public verifier joins mutable profile/course/current corrected score, or key rotation invalidates old attestations | Exam AD-19, protocol public snapshot rule, amended ADR-0007 | Rejected: immutable public/signed data and retained verification identity/material are explicit. |
| A delayed worker or unsupported demo scheduler moves terminal authority to the client | Main AD-23 and exam AD-30 | Rejected: one server terminator and bounded trusted demo execution are explicit; target deployment adapter is a declared prerequisite. |

## Deferred items are not themselves failures

- Mixed-star mapping is owned by the server result projection; percent/pass rules already bind E6/UI. Set the mapping before building it.
- Correction denominator/rounding and version fixtures are required before E7; the gap above concerns **when authority transfers**, not those calculations.
- Full EASY evidence is a shared learning/exam integration prerequisite with an explicit predicate and owner, not a license for each feature to choose a different gate.
- Renewal requires a new final; identity/history must be settled before that mandatory path. Voluntary improvement before expiry is separately deferred and cannot silently replace an active certificate.
- Optional ranking/course-star surfaces and external production rollout do not weaken current MVP ownership. Monitoring/provider/cadence detail is due before operating the relevant path.
- Existing toolchain compatibility is explicitly unverified for the observed pins; this lens makes no new current-version assertion and does not count an unrun build as PASS. The configured reality-check reviewer covers that dimension.

## Review process and limits

Applied the installed bmad-architecture `references/reviewer-gate.md` adversarial test, not a demand for a complete implementation design. Considered both spines, SPEC/protocol, solution/E1–E10 breakdown and the amended certificate ADR. Stable AD/CAP identities and input reconciliation are covered in `reconcile-exam.md`; its initial FAIL is preserved with an appended resolution PASS. Main lint has zero findings; the exam `{uid}` token is a literal Firestore path, not a missing template value.

All reads/report writes were through SSH on VPS. No builds, tests, services, live sessions, financial actions, Mac project copies, source edits or memlog edits were performed by this reviewer. This document is not runtime evidence and does not replace the other independent reviewer lenses.

Input SHA-256 at initial report creation:

- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md` — `889da3d76f8af3a07167c7ff5c23f50b07588d10575cbf5b78abb5a067435d04`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/ARCHITECTURE-SPINE.md` — `efbe677ad0da240491c81cc3d0378565903f8fb8cfaacf09ba974be13a8ca7bc`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/solution-design.md` — `cfeeb8fec595ad43423b612bd55767fb94a5b57333ddad1352eefaca9aaa5714`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/epic-breakdown.md` — `2954815708d5ab495acd3f6d3ce85e8971fee612dda18321a7c9ed22189a6227`
- `_bmad-output/specs/spec-theme-exams/SPEC.md` — `830f35d298101da94beb52a97b8af561de47623fd05bcd45d636424e8551f6e3`
- `_bmad-output/specs/spec-theme-exams/exam-protocol.md` — `6fca68712a9c794a3e8d91e40be63601f05606c5f11d27ec3bebd24e3fe563a2`
- `docs/architecture/0007-certificates.md` — `36cb6aafdb70821cce4dc449c718dd1602b680f07530332921859c4f9b254030`

Generated UTC: 2026-09-29T20:37:25.923080+00:00

## Final resolution recheck

**R-ADV-1 RESOLVED. Final adversarial-lens verdict: PASS.**

The root amended main AD-16, exam AD-19, E9, solution design, SPEC CAP-8, protocol persistence and ADR-0007. Each now binds initial terminal success to an immutable issuance fact in one commit with durable signing/publication work. The fact freezes issuanceId, earning resultVersion, claims, key identity and original dates. Signing happens outside the transaction; pending is explicit; fulfilment retry cannot reassess a later corrected grade, change identity or reset dates. Exam AD-19 also explicitly states that rollback commits neither success nor issuance. Renewal/improvement identity remains separately gated.

Re-applied counterexamples as a contract review (not executed tests):

- **Correction after result commit but before signature:** the issuance fact already exists for the earning version. E7 appends correction history; E9 fulfils the frozen fact. Both units must preserve the original attestation.
- **Crash/rollback before commit:** neither terminal success nor issuance fact exists. A retry reaches one atomic boundary; there is no committed pass with an unbound entitlement.
- **Crash after commit or lost signing response:** durable work and its stable binding survive. Retry signs/publishes the same fact, without another certificate identity or changed year.
- **Profile/public projection before signing finishes:** explicit pending is permitted and cannot masquerade as invalidity, revoke the award or authorize another issuance.

No new user product decision is required by this technical closure. It defines logical issuance at the server award boundary and preserves the owner's “already issued remains exactly as-is” rule; it does not introduce automatic administrative revocation, certificate improvement or renewal policy.

Operational consequence for the already-deferred key/worker runbook: a bound key's signing capability must remain usable for its pending issuance work until fulfilled, while verification material remains available for issued signatures. The exact provider/rotation procedure belongs to the E9/deployment owner before operation, not a new initiative-wide algorithm or blocker.

Final dispositions:

| Finding | Disposition | Remaining gate work |
| --- | --- | --- |
| R-EX-1 certificate owner conflict | Resolved in reconciliation and source recheck | None for this seam |
| R-ADV-1 earning-version/issuance race | Resolved by atomic logical issuance fact and durable fulfilment | None for this seam |
| Explicit display/arithmetic/progress/renewal/environment prerequisites | Keep their existing before-implementation/before-operation gates | These are not claimed complete by this review |

Final source SHA-256:

- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md` — `889da3d76f8af3a07167c7ff5c23f50b07588d10575cbf5b78abb5a067435d04`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/ARCHITECTURE-SPINE.md` — `efbe677ad0da240491c81cc3d0378565903f8fb8cfaacf09ba974be13a8ca7bc`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/solution-design.md` — `cfeeb8fec595ad43423b612bd55767fb94a5b57333ddad1352eefaca9aaa5714`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/epic-breakdown.md` — `2954815708d5ab495acd3f6d3ce85e8971fee612dda18321a7c9ed22189a6227`
- `_bmad-output/specs/spec-theme-exams/SPEC.md` — `830f35d298101da94beb52a97b8af561de47623fd05bcd45d636424e8551f6e3`
- `_bmad-output/specs/spec-theme-exams/exam-protocol.md` — `6fca68712a9c794a3e8d91e40be63601f05606c5f11d27ec3bebd24e3fe563a2`
- `docs/architecture/0007-certificates.md` — `36cb6aafdb70821cce4dc449c718dd1602b680f07530332921859c4f9b254030`

Final recheck UTC: 2026-09-29T20:38:48.717285+00:00
