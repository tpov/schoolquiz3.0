---
title: 'M2.4 — Premium without advantage'
type: 'feature'
created: '2026-10-11'
status: 'review'
baseline_commit: 'd3591c6a'
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
