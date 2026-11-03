---
title: 'Story 5.4 — server CREATE/reserve/read/payout over the adopted versioned prize list (UX-O2 implementation)'
type: 'feature'
created: '2026-10-16'
status: 'done'
review: 'thorough'
review_source: 'pinned'
baseline_revision: 'f9ab7e9c1ff63d5a401bfd7c435297ac297e9fef'
route: 'full'
route_source: 'pinned'
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 0
followup_review_recommended: true
context: []
warnings: []
deferred: []
---

<intent-contract>

## Intent

**Problem:** The adopted prize-list contract (edition 1, owner adoption recorded 2026-10-16, sha256 366860da…) defines the v1 dynamic ordered prize list, but the server still runs the fixed `{first, second, third}` DTO: `validatePrizeSchedule` requires exactly three named fields with `maximumLiabilityBoxes = first+second+2*third`, `planPrizePayouts` hardcodes rank→field amounts, and the redacted event view pins `["first","second","third"]`. Story 5.4 is the contract's designated implementer (its Appendix A1–A12 is the binding change list).

**Approach:** Implement v1 end-to-end in `functions/`: dual-form validation (v1 rows for new CREATE, legacy shape only for stored-event settlement/revalidation), CREATE accepting v1 only with canonical freeze and BigInt-guarded liability, payout assignment by `(place, row-index)` over the fixed finisher order with canonical `rowKey` serialization, unified redacted read projection (`listVersion 0` rows for legacy events), and the legacy settlement equivalence preserved (L3). Migrate test fixtures mechanically from legacy DTO to equivalent v1 rows (`{first:f, second:s, third:t}` → rows `[{1,f},{2,s},{3,t},{3,t}]`) across the functions suites and the rig scripts, re-pinning the rig source hashes.

## Boundaries & Constraints

**Always:**
- Conservation: frozen liability = Σ rows exactly once (BigInt + safe-range rejection); `fundedBoxes ≥ liability` with the surplus returned at finish via the existing `unusedReturnBoxes` path; every case keeps `reserved = paid + returned + remaining`, terminal `remaining = 0`.
- One version: CREATE/reserve/read/mapping/payout consume the single frozen `event.prizes` (`{listVersion: 1, rows}` canonically sorted at CREATE); no code path writes `prizes` after CREATE.
- Legacy stored events immutable: settlement through the listVersion-0 projection must produce the same amounts and liability as today (the executed-equivalence case `100/50/30 → paid 210, returned deposit surplus`); revalidation (`planPrizePayouts` :394) is dual-form so legacy settlement does not throw.
- Row assignment deterministic: per place p with k rows and m finishers, finishers in the fixed slot order (place 3: `third-upper` then `third-lower`), j-th finisher gets the j-th row; finisher without a row gets nothing; row without a finisher is returned in the remainder; `technicalForfeit` placements keep the existing :404-405 skip; zero-amount rows pay nothing.
- Payout keys: `["tournament", eventId, "prize", rowKey]` with the canonical `rowKey` serialization `key(eventId, "prize", listVersion, place, rowIndex)`; existing pre-check + `transaction.create` receipt discipline unchanged.

**Never:**
- No client code (`shared/`, `android/` untouched); no organizer whitelist; no new idempotence mechanisms; no migration or rewrite of stored legacy events; no prize row auto-duplication; no admission/timing changes (5.3/5.1 land stays intact).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| CREATE v1 | `{listVersion: 1, rows: [...]}`, unsorted input, deposit ≥ liability | Canonicalized (stable sort), frozen in the event; escrow = liability; surplus stays deposit-side | Rows >16, place outside 1..16, negative/unsafe amount, non-object rows → typed rejection; Σ outside safe range → refused |
| CREATE legacy DTO | `{first, second, third}` at new CREATE | Rejected (validator v1; L4) | Typed error naming the v1 shape |
| Empty rows list | `rows: []`, any deposit | Legal; liability 0; full deposit returned at finish | `prizes` object itself still required |
| Underfunded CREATE | deposit < Σ rows | Refused (existing "insufficient prize fund" path) | No partial reserve |
| Two third rows payout | Rows `[{3,a},{3,b}]`, both semifinal losers finish | `third-upper` → a, `third-lower` → b, per fixed slot order | Duplicate finisher/slot guards (:402-405) unchanged |
| One third row | Row `[{3,a}]`, both losers finish | `third-upper` paid a; `third-lower` gets nothing; nothing unreturned | — |
| Legacy settlement | Stored `{first,second,third}` event settles | Amounts identical to today via listVersion-0 projection (incl. both thirds); revalidation passes dual-form | Any divergence = regression |
| Legacy read | fetch of a legacy event | Projection `{listVersion: 0, rows: [{1,first},{2,second},{3,third},{3,third}], maximumLiabilityBoxes}`; no `first/second/third` fields leak | — |
| Repeat CREATE | Same operationId replay / new operationId on existing eventId | Same response replayed / ALREADY_EXISTS before any staging | No double reserve (existing guards) |

## Code Map

- `_bmad-output/mvp-candidate/epic-competitions/prize-list-contract.md` — the ADOPTED contract; its Appendix A1–A12 is the binding change list (locate symbols, not line numbers — 5.3 shifted lines).
- `functions/tournament-policy.js` — `validatePrizeSchedule` (dual-form rework), `createEventPlan` (freeze v1), `planPrizePayouts` (assignment by place/row-index + dual-form legacy path + :394 revalidation), `progressBracket` placements (unchanged, slots are the finisher order), `int/safe/key` helpers.
- `functions/tournament-service.js` — CREATE validation + event doc `prizes` freeze; escrow init (:192-195 area); `finish` escrow reconcile + `unusedReturnBoxes`; `eventView` prizes passthrough → projection; repeat-CREATE guards.
- `functions/course-tournament-controller.js` — `safeEvent`/`eventView` redaction: replace the `["first","second","third"]` pin with the unified projection (never leak legacy fields on v1 events, never leak rows mismatch); `listEvents` rows flow through the same projection.
- Test fixtures to migrate legacy→v1 rows (equivalence mapping above): `functions/tournament-policy.test.js`, `tournament-service.test.js`, `course-tournament-controller.test.js`, `course-tournament-handler.test.js` (`world()` CREATE), plus NEW cases per the matrix; `scripts/course-tournament-http.test.js` (createRequest + re-pin), `scripts/mvp-qualification-firestore-test.js` (controller hash re-pin + any legacy CREATE), `scripts/mvp-qualification-calculation-firestore-test.js` (legacy CREATE fixtures if present).
- `_bmad-output/mvp-candidate/owner-decisions-2026-10-16.md` — the adoption record (decision 2).

## Tasks & Acceptance

**Execution:**
- [ ] `functions/tournament-policy.js` -- dual-form `validatePrizeSchedule` (v1 canonical validation + legacy acceptance for stored shapes) and v1 `planPrizePayouts` (place/row-index assignment, canonical rowKey, legacy projection path); policy tests per matrix.
- [ ] `functions/tournament-service.js` + `functions/course-tournament-controller.js` -- CREATE v1-only with canonical freeze; escrow/liability via validator; finish surplus return; unified read projection in `safeEvent`/`eventView`; service/controller tests per matrix incl. legacy equivalence and redaction.
- [ ] Test fixture migration legacy→v1 rows across the listed suites and rig scripts; re-pin rig source hashes; `node --check` all edited scripts.
- [ ] `docs/agents/evidence/2026-10-16-dynamic-prize-list/README.md` -- what changed per the contract's A1–A12 (with the adopted-edition reference), test outcomes, honest notes (e.g., fixtures migrated, rig scripts pending live run).

**Acceptance Criteria:**
- Given a v1 CREATE with unsorted rows and a sufficient deposit, when the operation completes, then the event stores the canonically sorted frozen list, the escrow equals Σ rows, and a repeat operation replays without a second reserve.
- Given a legacy `{first,second,third}` DTO at new CREATE, when submitted, then it is rejected with a typed error; given a stored legacy event, when it settles and is read, then amounts and projection match today's behavior exactly (both thirds paid on the projected rows).
- Given two place-3 rows and both semifinal losers finishing, when payouts plan, then third-upper receives row 0 and third-lower row 1 under the canonical rowKey, and every conservation case in the matrix closes `remaining = 0`.
- Given the full `node run-tests.js`, then all suites pass including migrated fixtures and the new matrix cases, and `git status --porcelain -- shared/ android/` stays empty.

## Implementation Notes

## Spec Change Log

## Review Triage Log

### 2026-10-16 — Review pass 1 (lenses: blind-hunter 16, edge-case-hunter 7, verification-gap 0 gaps + 3 other, intent-alignment 0 defects)
- verdicts: 26 findings — high 0, medium 8, low 14, false 3, maybe-false 1→resolved
- findings:
  - `[medium]` `[patch]` Read-path robustness regression: `prizeView`/`safeEvent` threw on prizeless/malformed stored docs where the old `pick` tolerated them; one malformed event could poison a whole discovery page — fix applied: non-object/absent prizes omitted (old tolerance), corrupt prize objects yield typed UNAVAILABLE «Event prizes require repair», `prizeView` made idempotent over already-redacted views (liability from the event or the projection), `maximumLiabilityBoxes` validated (safe integer ≥ 0) at both layers.
  - `[medium]` `[patch]` (edge-case-hunter, same defect group) non-object prizes TypeError + listVersion≠1 passing the projection unvalidated + unchecked liability — grouped, shares route; fix applied in the same hardening: the projection now rejects listVersion outside {0,1} and int-checks the fast-path liability.
  - `[medium]` `[patch]` (orchestrator bookkeeping) duplicate YAML keys `review`/`review_source` in the spec frontmatter (last-wins would blank the pinned review) — fix applied: duplicates removed, YAML single-pair verified.
  - `[medium]` `[patch]` Stale pre-existing pin `tournament-qualification-scan.js` (d1519936… vs actual 683b1c00…, stale since 45ab9d82) in the same pin list this story edited — the rig would abort before the emulator regardless of this story's re-pin; fix applied: re-pinned to the actual digest, README notes the pre-existing cause.
  - `[low]` `[patch]` Payout-key formula ambiguous in spec/evidence prose (nested vs the actual flat 6-element key) — fix applied: evidence README states the flat form; the code pins it by tests.
  - `[low]` `[patch]` Zero-row positional semantics untested in the decisive mixed case — fix applied: new policy test pins that a zero row consumes its position and emits no payout record (house skip semantics).
  - `[low]` `[patch]` `prizeListProjection` non-idempotent on its own bare v0 output (confusing «must be 1» error) — fix applied: bare `{listVersion: 0, rows}` accepted and passed through.
  - `[low]` `[patch]` v1 events never asserted through `listEvents` redaction — fix applied: controller test asserts the v1 page projection and no-leak.
  - `[low]` `[patch]` «No code path writes prizes after CREATE» verified only by reading — fix applied: service test pins the stored prizes byte-identical after finish.
  - `[low]` `[patch]` L4 rejection misdiagnosed non-legacy malformed objects as the fixed DTO — fix applied: legacy-shaped objects get the form-naming message, others a plain v1 requirement.
  - `[low]` `[patch]` README terminology drift (rowKey naming, createEventPlan attribution) and the unaddressed client-compatibility window — fix applied: pass-1 notes added (flat key form; policy-side freeze; no released client binds the callables, projection consumers are 5.9/5.10).
  - `[low]` `[reject]` Spec self-contradictions (matrix cell «escrow = liability» vs deposit escrow; line numbers after forbidding them; fixture-list omissions; deferred/followup frontmatter «denials») — all fix-would-edit-this-spec findings; the code, tests and evidence carry the correct facts; rejected by rule with the evidence README as the record.
  - `[low]` `[reject]` Duplicated policy-test coverage (insufficient-fund and BigInt-overflow asserted twice) — cosmetic; churn on green tests not warranted.
  - `[false]` `[reject]` «validateStoredPrizeSchedule is never called by production code» — refuted: `planPrizePayouts` calls it (tournament-policy.js:529); `prizeFunding` is the shared dual-form core both validators use.
  - `[false]` `[reject]` «Legacy settlement ordering divergence is a regression» — refuted: amounts, keys, receipts and escrow are byte-identical (pinned by tests); effect order is not a contract surface (L3 pins sums), and no client surface reads payout ordering; disclosed in the evidence README.
  - `[false]` `[reject]` (intent-alignment, descriptive) races-scoped-to-5.8 and bank-sufficiency NO-change diverge from the ticket's literal title — resolved by the adopted contract's own scoping (D9; A2), which supersedes the ticket title's shorthand.
  - `[maybe-false]` → resolved `[false]` «controller comment claims mismatches never leak while prizeView silently overrides liability» — resolved by the patch: the override is now a validated read (safe-integer check) with typed repair errors; a mismatch can no longer pass silently in either direction without being the stored value or a repair case.


## Design Notes

The contract is the law; this story is its A1–A12 executed. The riskiest corner is the dual-form validator: v1 canonicalization must not accept legacy objects (L4) while settlement revalidation must accept them (A12) — the discriminator is the presence of `listVersion`. Fixture migration is mechanical but wide; the equivalence mapping (`{f,s,t}` → four rows with two place-3 rows) preserves both the old liability formula and both-thirds payouts, which is why the matrix's legacy-settlement case is the regression tripwire.

## Verification

**Commands:**
- `cd functions && /root/.zcode/server/node run-tests.js` -- expected: all suites pass (target: the full battery, ≥109 suites).
- `git status --porcelain -- shared/ android/` -- expected: empty.

**Manual checks (if no CLI):**
- `node --check` on every edited rig script; the pinned hashes in `mvp-qualification-firestore-test.js` match the new file digests.
- Evidence README maps each contract appendix row (A1–A12) to what was done.

## Auto Run Result

- Summary: implemented the adopted UX-O2 prize-list contract server-side — v1-only CREATE
  validation with canonical freeze and BigInt-guarded liability, dual-form settlement
  revalidation (listVersion discriminator; legacy liability = first+second+2*third via its
  projection), payout assignment by (place, row-index) over the fixed finisher order with flat
  canonical payout keys (legacy events keep slot keys), unified redacted read projection
  (`listVersion 0` rows for legacy events, DTO fields never leave the server), prizeless-doc
  read tolerance restored with typed repair errors; fixtures migrated legacy→v1 rows across
  five functions suites and three rig scripts with source re-pins.
- Files changed: `functions/tournament-policy.js`, `functions/tournament-service.js`,
  `functions/course-tournament-controller.js`, five functions test suites,
  `tournament-source-bank.test.js`, three rig scripts, evidence README, this spec.
- Review findings breakdown: 26 findings — medium 8 (read-path robustness regression group,
  projection validation, frontmatter duplicates, stale pre-existing scan pin — all patched),
  low 14 (6 patched: key wording, positional zero-row test, projection idempotency, listEvents
  v1 case, prizes-immutability pin, L4 message; 7 rejected as spec-edit or cosmetic; 1 merged
  into the medium group), false 3, maybe-false 1 resolved by the patch. Deferred: none new
  (rig live runs and the external QS_SOURCE_MANIFEST regeneration remain recorded in the
  evidence README as pre-existing pending items).
- Follow-up review recommendation: TRUE — the read-path hardening (prizeView idempotency,
  tolerance semantics) and the projection validation changes were applied by the orchestrator
  with full-suite re-verification (109/109) but without a fresh lens pass; a follow-up pass
  should re-examine the amended read path before 5.9/5.10 build client projections on it.
- Verification performed: full `node run-tests.js` → 109/109 suites pass (before review
  patches, after each patch round); the positional zero-row, listEvents-v1 and
  prizes-immutability tests added and green; `git status --porcelain -- shared/ android/`
  empty; frontmatter YAML single-pair verified.
- Residual risks: rig live runs pending (demo emulator down; QS_SOURCE_MANIFEST must be
  regenerated for the changed sources before the calculation rig's next run); legacy payout
  effect ordering differs from baseline (sums/keys identical, disclosed); `technicalForfeit`
  placements remain a preserved-guard interpretation (never produced today).
