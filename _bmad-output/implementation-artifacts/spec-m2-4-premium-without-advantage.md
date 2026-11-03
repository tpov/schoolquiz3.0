---
title: 'M2.4 — Premium without advantage'
type: 'feature'
created: '2026-10-11'
status: 'done'
baseline_commit: 'd3591c6a'
review: 'thorough'
review_source: 'pinned'
lenses_ran: [blind-hunter, edge-case-hunter, verification-gap, intent-alignment]
followup_review_recommended: true
deferred:
  - summary: >-
      The premium static fence enumerates eight files; a scoring/ranking module added tomorrow is
      unfenced by default, and the audit-named display/bookkeeping readers sit outside by design.
    evidence: |-
      The inverted allowlist (scan every server module, name the files allowed to mention
      premium) is the fence's v2 — a design step beyond this acceptance's hardening, which
      closed the reachable holes (rules, server-wide sponsor, options channel, trophy tier).
    location: >-
      functions/premium-neutrality.test.js (static premium scan)
    severity: low
  - summary: >-
      The box roll's «pinned by signature» claim has no direct test (arity, exactly-eight draws).
    evidence: |-
      An arity check is defeated by a defaulted third parameter in JS, and draw-count needs
      instrumentation; the trophy tier now reaching the same-entropy pin closes the reachable
      hole. A full signature pin belongs to the fence's v2 with the inverted allowlist.
    location: >-
      functions/premium-neutrality.test.js (box pins)
    severity: low
  - summary: >-
      The score pin's keyDocument input stays null; a premium branch reading the key document is
      covered only by the static fence on attempt-scoring.js.
    evidence: |-
      Hand-building a valid key document risks a vacuous fixture (both runs refusing); the
      static /premium/i ban on attempt-scoring.js catches the direct mention. A validated
      keyDocument fixture belongs to the fence's v2.
    location: >-
      functions/premium-neutrality.test.js (score pin)
    severity: low
review_loop_iteration: 0
story_key: '2-4-премиум-без-преимущества'
sprint_status_file: '_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml'
context:
  - '{project-root}/_bmad-output/planning-artifacts/epics-monetisation.md'
  - '{project-root}/_bmad-output/specs/spec-schoolquiz3.0/SPEC-REVIEW.html (CAP-13)'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m2-1-premium-is-one-subscription.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m2-3-renewal-not-replacement.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** nothing in the codebase *states* that premium is neutral. The planning AC — two
players with identical play, one paying, must take identical scores, places and income; sponsor
points accumulate and are visible but unlock nothing — is a property of the whole system, and no
test anywhere fails when someone gives money a channel into comparison. The audit (this story) found
the surface already clean: premium is read by the charge-regeneration speed (the product itself,
CAP-13), by display echoes of the balance, and by premium's own settlement plumbing; the scoring,
ranking and drop modules never mention it; firestore.rules grant privileges only on admin and
moderator levels. But "currently clean" is not a boundary — one added field away from gone.

**Approach:** turn the property into a fence. A new `premium-neutrality.test.js` pins the three
seams where income and placement are computed — `scoreAttempt` (score), `calculateTournamentLeaderboard`
(placement), `generateGiftBoxReward` (box income) — behaviorally (premium fields injected into every
input change nothing, bit for bit) and statically (the modules that decide score and place must not
even mention premium; the box module is exempt on purpose: a box may *award* premium as a drop, and
its roll is pinned by signature and by the same-entropy-same-reward test). Sponsor neutrality is
pinned the same two ways: the rules file never matches "sponsor", and every server mention of the
sponsor level is a copy between documents or its read-back for display. No production code changes —
the audit found nothing to fix, so the deliverable is the proof that stays.

## Boundaries & Constraints

**Always:** the charge-regeneration ×2 stays premium's only material effect (CAP-13: личный и
несравнимый); the overspend audit keeps reading premium because it must verify charges with the
regen rate the player actually had; the box drop may award premium time — granting is not scoring.
`gift-box-rewards.js` is deliberately exempt from the static mention check.

**Ask First:** any new premium effect (a new place reading `hasPremium`/`premiumUntilMs`) — that is
an owner decision about the product, not a code change. Any new qualification level with privileges.

**Never:** branch scoring, ranking, drop rolls, qualification or firestore rules on premium or on
the sponsor level; let a sponsor level do anything but be displayed.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Same answers, premium fields injected | `scoreAttempt` with `hasPremium`/`premiumUntilMs` in every input | identical result, bit for bit | N/A |
| Identical play, one paying | leaderboard with premium fields on entries and runs | identical rows and places | N/A |
| Same entropy, any owner | `generateGiftBoxReward` | same reward; entropy is the only channel | N/A |
| Scoring/ranking modules gain a premium mention | static check | test fails with the file's name | the fence |
| Rules grant by sponsor | static check on `firestore.rules` | test fails | the fence |
| Sponsor level in server code | every mention is a document copy or display read-back | test fails on anything else | the fence |

## Code Map

- `functions/premium-neutrality.test.js` — NEW: the fence (behavioral + static, see above).
- Audit findings (no code touched): premium reads live in `economy-constants.js regenMsFor` +
  `index.js` charge integration and overspend audit (the product), `index.js:3712/7652-7653/7736`
  and `box-service.js:194` (display echoes), `tournament-service.js`/`arena-entry-runtime.js`
  (ADVANCE bookkeeping), and premium's own settlement/clawback/engine modules. Sponsor level:
  `index.js readQualification`/profile writes, client display only; `firestore.rules` never names it.

## Tasks & Acceptance

**Execution:**
- [x] Audit of every premium read on the server and the client — classified, none outside the
      product, the display and premium's own plumbing.
- [x] `premium-neutrality.test.js`: behavioral pins on the three seams + static fence + sponsor pins.
- [x] `node run-tests.js` — 101/101 suites; no Kotlin files touched, app gates unaffected.
- [x] `sprint-status-monetisation.yaml`: 2-4 → review.

**Acceptance Criteria:**
- Given два игрока с одинаковой игрой, один с премиумом, when оба играют и попадают в рейтинг, then
  счёт, позиция и содержимое коробки одинаковы — зажато тестом на трёх швах дохода.
- Эффекты премиума остаются личными (реген) и косметическими/отображаемыми (бейджи, строки экранов).
- Очки квалификации «спонсор» копятся и видны, но ничего не дают — правила и сервер не дают им
  ни одной ветки, кроме переноса и показа.

## Verification

**Commands:**
- `cd functions && node premium-neutrality.test.js`
- `cd functions && node run-tests.js`

## Code review 2026-10-11, thorough (blind-hunter + edge-case-hunter + verification-gap + intent-alignment)

- [x] [Review][Patch] The behavioral score pin injected premium only at the input's root
  [premium-neutrality.test.js] — fixed: the fields now ride every question row, every served row and
  every answer row too; a branch reading premium off a row falls as loudly as one reading the root.
- [x] [Review][Patch] Qualification was outside the static fence — fixed: it decides who reaches
  the ranking at all, the same comparison seam as placement; the four `tournament-qualification*`
  modules are premium-clean today and join the list.

**Rejected**
- The qualification seam also needs a behavioral pin — false by construction: a module that may not
  even name premium cannot branch on it; the static fence is the stronger check there, and the
  behavioral pins cover the input-channel case on the score, placement and box seams.
- The sponsor mention whitelist is brittle — by design: it is a fence, and a refactor that breaks
  the shape fails the test with the line in the message, asking to re-classify it as copy or
  display.
- The stored `hasPremium` in the user document violates the fence — false: it is a display echo
  written server-side (the audit's classification); the client reads the date (M2.3), and the
  server's own balance computes it from the date.
- The box's static exemption is a loophole — false: the roll's signature takes only trophies and
  eight entropy draws; premium can enter only as the awarded content, which the same-entropy pin
  holds.

## Review Triage Log

### 2026-10-23 — Review pass (acceptance, fresh four-lens pass over the committed change set at HEAD; 2026-10-11 rows used as prior context)

- verdicts: 23 findings — high 0, medium 6, low 14, false 3, maybe-false 0
- findings:
  - `[medium]` `[patch]` [blind] The rules fence checked only sponsor — the Never clause forbids branching rules on premium too; fixed: the rules scan now walks premium lines and allows only the `match /premium_accounts` storage path.
  - `[medium]` `[patch]` [blind] The sponsor line whitelist passed non-copy lines (`sponsorLevel[,)]`, bare `qualifications`/`readQualification` substrings, any `${` line) — fixed: only the anchored copy/display shape passes; verified every current mention matches it.
  - `[medium]` `[patch]` [blind] The sponsor fence read only index.js while its claim covers the whole server — fixed: every server module is scanned with the same whitelist; mutation-verified (a sponsor gate injected into tournament-qualification.js fails the fence loudly).
  - `[low]` `[defer]` [blind] No direct signature pin on the box roll (arity, eight draws) — an arity check is defeated by a defaulted parameter and draw-count needs instrumentation; deferred to the fence's v2 (the trophy tier now reaches the same-entropy pin).
  - `[low]` `[defer]` [blind] The premium static fence is a closed eight-file list; new deciding modules escape — the inverted allowlist is the fence's v2, deferred with direction.
  - `[low]` `[reject]` [blind] The AC's «identical places» vs the pinned `[1, 2]` name tiebreak — the literal AC is unreachable without changing production tiebreaks, which the Never clause forbids; the pinned reading («premium cannot move your place») is the only coherent one and is documented in the test.
  - `[low]` `[defer]` [blind] The score pin's keyDocument stays null — a hand-built valid key document risks a vacuous fixture; the static fence on attempt-scoring.js holds the line; deferred.
  - `[low]` `[reject]` [blind] The box test's title promises a sponsor/payer comparison it does not perform — rhetoric; entropy determinism is the designed mechanism and now covers the trophy tier.
  - `[low]` `[reject]` [blind] The placement pin couples the tiebreak order — bit-for-bit is the fence's stated design: any placement change must fail loudly here.
  - `[low]` `[reject]` [blind] Stale bookkeeping (status `review`, matrix without the qualification seam) — the status half is closed by this pass; the matrix half would edit the frozen intent.
  - `[false]` `[reject]` [blind] No runner named for the fence — run-tests.js discovers `*.test.js` by naming; the fence runs in the standard functions gate (verified in this run).
  - `[low]` `[reject]` [blind] The sponsor-existence assertion pins a product fact — kept deliberately: if the product drops sponsor points, the fence must fail loudly and be revisited, not silently unfence.
  - `[low]` `[defer]` [edge] The keyDocument subtree never receives injected premium fields — grouped with the keyDocument defer above.
  - `[false]` `[reject]` [edge] The second answer is refused on an index mismatch, leaving its evaluation unpinned — refuted empirically: the fixture scores identically with the index corrected («served places the digit» by design; percent 50, scorable, no refusals in both shapes).
  - `[low]` `[patch]` [edge] The placement assertion compared only the leaderboard, not the metadata half — fixed: the whole result (components, confidence, connectivity) is compared bit for bit.
  - `[low]` `[patch]` [edge] The pinned entropy never reached the trophy branch — fixed: a second entropy tier (0.01/0.96/…) drives the roll through the rare tier into `pickGiftBoxTrophy`, and the same-entropy determinism pin covers it.
  - `[low]` `[defer]` [edge] A future deciding module escapes the hardcoded list — grouped with the closed-list defer above.
  - `[medium]` `[patch]` [edge] The sponsor scan missed non-index modules — grouped with the server-wide scan above; the mutation test proves the exact demonstrated case now fails.
  - `[low]` `[patch]` [edge] The whitelist admitted threshold-shaped lines — grouped with the tightened whitelist above.
  - `[medium]` `[patch]` [vg] The placement pin never exercised the `options` channel production uses (index.js passes `minGroupsForStableRank`) — fixed: the pin runs the production options shape with premium fields injected into the options too.
  - `[medium]` `[patch]` [vg] The sponsor fence was narrower than its claim — grouped with the server-wide scan above.
  - `[medium]` `[patch]` [vg] The whitelist's free passes broke the matrix's «test fails on anything else» — grouped with the tightened whitelist above.
  - `[low]` `[defer]` [vg→other] keyDocument uncovered (grouped above) and main() short-circuits at the first red seam — the short-circuit is accepted: run-tests runs suites in separate processes, so the aggregate signal stays whole; the keyDocument half is grouped above.

## Auto Run Result

Status: done — acceptance pass complete; six medium findings hardened the fence itself (the story's deliverable is a test, so the patches are test-only by nature); all applied and verified, including a mutation check.

### Summary of this pass
- Fresh four-lens thorough review over the story's committed change set — 848a2989 + 3e263237 — verified at HEAD; the 2026-10-11 pass served as prior triage context.
- 23 findings: 0 high, 6 medium, 14 low, 3 false → 10 patch rows applied (6 medium groups), 6 defer rows, 7 reject rows with recorded refutations.
- Fence hardening landed with this acceptance: firestore.rules scanned for premium (storage-path whitelist); the sponsor scan is server-wide with a copy-only whitelist; the placement pin drives the production `options` channel and compares the whole result including metadata; the box pin reaches the trophy branch; a mutation check (sponsor gate injected into tournament-qualification.js) fails the fence loudly and passes clean after removal.

### Verification performed
- `cd functions && node premium-neutrality.test.js` — 6/6 after hardening; mutation check red on the injected sponsor gate, green after restore.
- `cd functions && node run-tests.js` at HEAD — 128/129; the one failure is the pre-existing `mvp-isolation` LAN-address objection (documented under spec-m2-1's pass, unrelated to this change set).
- No Kotlin files in this change set; module gates unaffected (full gate record under spec-m2-1's pass).

### Follow-up review recommendation
- true — six medium entries were patched this pass. Unverified risk: the fence v2 direction (inverted allowlist, signature pin, keyDocument fixture) is recorded as deferred design, not built; until then new deciding modules and non-index sponsor reads outside the scanned set rely on review, not tests.

### Residual risks
- The fence remains syntactic: a premium channel written under a neutral name (an option computed from premium at the call site) passes any line-based fence; the Ask First clause is the process gate for that class.
- The box's signature claim and the score pin's keyDocument stay covered by the static ban only, pending the fence v2.
