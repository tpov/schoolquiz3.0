# 7.4 gift claims on-device — streak PASS, box open PASS, premium-wipe FINDING — 2026-10-19

Build: APK sha256 340be3da… (includes the sibling session's gift-claim wiring: 5e0d5b80
persistence migration 16→17 + 9f4440ed GiftClaimRepository/SyncBoxStreakUseCase/
ClaimPremiumDailyGiftUseCase). Device: Samsung A14, fresh install + pm clear, fresh account
UserCXPKJB (uid qopc1zUv4U2QSrpYKGgdXeXYbdi8).

## Fixture (admin write, 12:40)

`users/<uid>`: boxStreakDays=9, nextBoxAtMs=now−2h, premiumUntilMs=now+30d, boxCount=1 —
verified read-back (premiumActive: true).

## What PASSED

1. **Streak claim on entry (OFFLINE_STREAK → VISIT)** — with the fixture in place the next
   app launch fired the entry pass: server ops for the uid are VISIT (CLAIMED, boxCount 2,
   boxStreakDays **10**, earnedBoxes **1**, receipt BOX_ACCRUAL +1 BOXES), the device showed
   FAB badge «2» and the chip «Серія: 10». The day-10 grant ran through the real server
   ceiling logic (not a local fiction).
2. **Box opening** — FAB → «Відкриваємо…» → «Нагороду нараховано до профілю: 1278 ноликів»
   (op OPEN, receipt BOX_OPENED addNolics 1278). Evidence 01-streak10-boxes2.png.

## FINDING: premiumUntilMs silently wiped to 0 after the entry sync

The fixture set premiumUntilMs = now + 30 days and the read-back confirmed it (12:40). By
13:00 the server value is **0** (premium_accounts: none) and the home screen renders no
premium-gift claim (HomePremiumGiftState not Available). The wipe happened across the app's
entry pass/sync window (offline-claim → VISIT → refresh → profile fetch), so the suspect list
is: the profile upsert pushing the device's stale local economy state over the server doc, or
a box-op userPatch carrying premiumUntilMs from a stale read.

If real, this is a money-adjacent defect: a paying user's premium silently disappears on the
next sync. Needs a focused next-session investigation of the sync write paths
(ensureUserProfile/upsertUserProfile vs box-op userPatch) with the fixture replayed.

## Status

- 7.4: the streak-source journey is PROVEN on-device (entry grant, real receipts). The
  premium-daily-gift claim is NOT proven — blocked by the premiumUntilMs wipe above.
- The premium daily gift UI (quest_premium_gift_claim, HomePremiumGiftState.Available) exists
  in the new build and will render once the premium state survives a sync.
