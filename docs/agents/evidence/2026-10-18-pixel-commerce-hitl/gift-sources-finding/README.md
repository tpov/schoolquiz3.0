# Story 7.4 device proof blocked by missing client wiring — finding — 2026-10-18 evening

Attempted on Pixel 10 Pro (UserG9FY6E / uid VLqWYMHXqyI8pPWQdNXdkQg9FY6E) with the live
device→VPS transport (see store-surface/README.md).

Fixture state set server-side (admin write, still in place — ready for when the wiring lands):
`users/<uid>` boxStreakDays=9, nextBoxAtMs=now−2h (a visit within the next-day window reaches
day 10 = STREAK_TARGET_DAYS and must grant STREAK_BOXES_PER_DAY=1).

Result: two app relaunches produced NO streak claim — the server saw no OFFLINE_STREAK
operation and its state is untouched (boxStreakDays still 9).

Root cause (client code inspection): the app has no claim wiring for the gift sources.
- The outbox knows exactly one gift-box operation: `OPEN_GIFT_BOX`
  (GiftBoxIntentStore.OPERATION; GiftBoxRepositoryImpl checks `receipt.operation ==
  OPEN_GIFT_BOX`). There is no submitter for `OFFLINE_STREAK` / `PREMIUM_DAILY` — the kinds
  box-service.js implements and accepts.
- The domain logic exists with parity tests (shared/feature/economy/domain/logic/GiftBoxStreak.kt,
  GiftBoxStreakParityTest: visited() advances the streak on entry and grants from day 10), but
  nothing calls `visited()` — no entry-time registration anywhere in apps/android.
- There is no UI surface for the explicit premium daily gift claim («Получить подарок»).

Consequence: story 7.4's device journey ("automatic on entry" streak grant + explicit premium
claim, separate limits/receipts) cannot run on any device — the client half is unbuilt. This is
an implementation gap to schedule, not a test failure; the server half (box-service kinds,
overclaim ceilings, receipts) is ready and was exercised for OPEN only (7.5).

Fixture note: server state left at boxStreakDays=9 / nextBoxAtMs in the past so the first run
after the wiring lands exercises the day-10 grant immediately.
