# Charge regeneration proven end-to-end on device — 7.3 — 2026-10-20 morning

Account qopc1zUv4U2QSrpYKGgdXeXYbdi8 (premium active, 30d grant from the night fixture;
shipped table pins premiumRegenDivisor=1 — premium does NOT speed regen by design, see
economy-constants.test.js «делитель 1 — не быстрее»).

## What was proven

- Fixture T0 06:35Z: lifePoints=0, charge cursor aligned to the premium header (after a
  repair — see the lesson below). Server regenerated LAZILY: the doc stayed 0 until the
  next economy transaction (regeneration is computed inside the advance, not on a timer).
- 07:40Z — a fresh box-open advance ran the real path (chargeBalances →
  regeneratePremiumPoints): lifePoints **0 → 500** (full 5×100), cursor moved to 07:40,
  remainder 0. Honest math: 21:56Z (header cursor) → 07:40Z ≈ 9.73h at 1 charge/hour →
  capped at the ceiling. The device renders it (hearts full in the drawer).

- 73-tick4.png — the same tap also recovered the box: the opening intent saved during the
  CURSOR_MISMATCH window replayed to the SAME stored reward (667 ноликів, «Залишилося
  коробок: 1») — a bonus loss/restart proof of 7.5 semantics on the new build.

## LESSON for every future fixture: charges are NOT freely writable

My first drain fixture set lifePointsUpdatedAtMs=now while the premium header cursor sat
9h earlier — the aggregate contract («charge cursors MUST equal the header cursor;
bypassing this module fails closed», premium-economy.js) answered CURSOR_MISMATCH and
failed closed EVERY economy transaction for the account until the cursor was realigned.
Correct fixture recipe: set lifePoints AND lifePointsUpdatedAtMs=premium_header.asOfMs,
lifeRegenRemainderMs=0 — the next advance then regenerates through the exact transition
span. 73-tick.png/tick2.png document the fail-closed state («Не вдалося відкрити»).

7.3 status: engine math proven (44 Node cases), premium surface proven, survival proven,
and now the regeneration path proven on-device end-to-end. The only remaining 7.3 item is
the x2 display — gated on the owner deciding to flip premiumRegenDivisor to 2 in a
versioned table change (one line + version bump), after which the same observation
recipe measures the doubled rate.
