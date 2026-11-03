# 7.4 premium gift journey CLOSED — stranded intent settled on-device — 2026-10-20

Build: the mvp APK with the client auto-resume fix (component collector mirrors the
box-opening resume; tap routes through one request job) + the bounded first-launch
auth wait. Installed over the existing install (data kept), launched 09:41 EEST.

- 74-settled.png — the button that had been stuck in «Забираємо подарунок…» for a
  day now reads **«Подарунок забрано — новий завтра»** (ClaimedToday); FAB 2,
  «Серія: 10»; the seeded «Приватные квизы» catalog card also visible on home.

Server cross-check (admin read): box_operations with kind PREMIUM_DAILY — exactly **1**
(the claim committed the previous night, operationId 6a2d5cf9; the resume replayed it,
no second grant); boxCount 2; premiumUntilMs 1795035417005 intact. Device Room:
`gift_claim_intents` no longer holds the PREMIUM_DAILY row (resolved and deleted).

## The full 7.4 premium-gift chain, now proven end-to-end

1. tap → intent persisted → callable → server CLAIMED + receipt (19.10, Samsung);
2. wire envelope repair (server) — replay answers {replayed, result} (20.10 morning);
3. stranded-intent auto-resume (client) — the restart settles it with no user action
   (this session), and the resolve deletes the Room row so the button state machine
   is free.

7.4 status: streak journey PASS, box opening PASS, premium gift PASS (full chain).
7.3: premium surface + survival PASS; regeneration tick display remains config-bound
(the shipped table pins premiumRegenDivisor=1 by test — enabling x2 is a versioned
owner decision), base-tick observation running separately.
