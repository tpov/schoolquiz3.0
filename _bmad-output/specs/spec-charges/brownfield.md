# Brownfield — what exists, and what this touches

Companion to `SPEC.md`. Verified against the working tree on branch `noir/result-screen`
(2026-08-30). Every path and line number below was read, not recalled.

## The heart model today

A heart is a **slot**, not a unit. Owning a heart raises a ceiling of life points; playing spends
points. Two files hold the model and they mirror each other on purpose:

- `functions/life-points.js` — `LIFE_POINTS_PER_HEART = 100`, `HEART_REGEN_MS = 1h`,
  `LESSON_ATTEMPT_LIFE_COST = 33`, and the pure `maxLifePoints` / `regenerateLifePoints` /
  `spendLifePoints` trio. Regeneration is lazy: derived on every read from the stored value and
  elapsed time, so nothing runs while the player is away.
- `shared/feature/internet/profile/domain/src/commonMain/kotlin/.../model/UserProfile.kt` —
  `standardHearts`, `goldHearts`, `lifePoints`, `lifePointsUpdatedAtMs`, plus a companion holding
  `LIFE_POINTS_PER_HEART` and `LESSON_ATTEMPT_LIFE_COST` as a hand-kept mirror of the above.

`UserProfile.offline()` seeds `standardHearts = 5, goldHearts = 0`.

## The hardcoded constants

`functions/index.js:93-96`:

```js
const STANDARD_HEART_SLOT_COSTS = [1000, 2000, 5000, 10000, 20000];
const MAX_STANDARD_HEARTS = 5;
const MAX_GOLD_HEARTS = 1;
const GOLD_HEART_COST = 10;
```

These are the values CAP-4 moves into the config document. `readEconomyBalance` clamps stored
balances with `Math.min(MAX_*, …)` on every read — worth noting, because it means lowering a
ceiling today silently confiscates the excess. `economy-constants.md` changes that.

There is **no remote-config or constants-sync mechanism anywhere in the project.** Searched for
`remoteConfig`, `appConfig`, `serverConfig` across Kotlin and JS: nothing. This is new
infrastructure, and it is the largest single piece of work in the spec.

What does exist is the delivery half of the pattern: `configs/{configId}` is reserved in
`firestore.rules:42` as server-owned and closed to clients, and two documents already use it —
`configs/nickname_policy` (`functions/index.js:114`) and `configs/arena_review`
(`functions/index.js:2825`). Both are read server-side only. Nothing yet forwards such a document
to a device.

## The hint, today

`android/feature/lesson-runner/presentation/src/main/kotlin/.../component/DefaultLessonRunnerRootComponent.kt:247`
— `hintRequested()` reads the local `lives` figure, decrements it, writes it back to
`RunnerStateHolder.livesRemainingHearts` and to the UI state. That is the entire mechanism. The
count is seeded once per run from the profile (`readLivesFromProfile()`, line 324:
`lifePoints / LIFE_POINTS_PER_HEART`), and **nothing is ever sent to the server.** No hint spend
is recorded, charged, or audited anywhere.

CAP-3 replaces this: the tap records a claim in the attempt's mask, and the debit happens at
submission.

## Result submission — where settlement lands

`exports.submitLessonResultEvents` (`functions/index.js:301`) is the entry point and already has
the shape settlement needs:

- One `db.runTransaction`, all reads before the first write (Firestore's rule).
- Reads the user document, regenerates life points from the server clock, spends
  `LESSON_ATTEMPT_LIFE_COST` per new verified attempt, records a `lifeCharged` flag on each stored
  attempt.
- Pays rewards **only for charged attempts** — `skillPoints` / `nolics` via `attemptReward`, plus
  activity ratings and the tournament write.

That last point is why SPEC open question 1 blocks the data model: retiring the life-point
substrate retires the attempt toll, and the entire reward path is gated on `lifeCharged`.

## Score verification — the mirror that must not break

`functions/result-verification.js` holds `recomputePercentScore`, `isWellFormedCodeAnswer`
(`/^[0-9]*$/`), and `attemptActivityCounts`. Its own comment records that it is a mirror of
`computePercentScore` in `RunnerLogic.kt`, floor-division at both steps included, so honest
attempts are not rejected over rounding. `attemptActivityCounts` reads `'9'` as the only fully
correct digit.

This is the constraint behind the parallel-mask decision in `settlement-protocol.md`: any change
to the `codeAnswer` alphabet has to land in both implementations and is not backward-compatible
with stored attempts.

## Tournaments

`functions/tournament-ranking.js`, algorithm `pairwise-percent-least-squares-v1`. Shelves are
`tournament` / `tournamentFinal`; attempts group into one-hour windows by lesson and difficulty
(`tournamentGroupForAttempt`, `DEFAULT_GROUP_WINDOW_MS = 1h`).

`compareLeaderboardEntries` orders by `ratingPercent → averagePercent → groupsPlayed →
uniqueOpponents → userId`. **No time term, and no attempt field carries a duration** —
`tournamentResultForAttempt` normalises `completedAtMs` only. CAP-10 needs a duration captured
before it can rank on one, which is SPEC open question 4.

## Shop

`shared/feature/economy/domain/src/commonMain/kotlin/.../model/ShopItemId.kt` —
`STANDARD_HEART_SLOT`, `GOLD_HEART`, `QUIZ_SLOT`, `AD_REWARD_BOX`, `DONATE_GOOGLE_PLAY`,
`REFERRAL_PROGRAM`, `NICKNAME_MARKET`. Server side: `exports.applyShopPurchase`
(`functions/index.js:1184`) → `applyShopPurchaseToBalance` → `buyStandardHeart` / `buyGoldHeart`.

`buyGoldHeart` charges a flat `GOLD_HEART_COST = 10` gold against a ceiling of 1. CAP-9 changes
both: 1 gold per charge, ceiling from the config.

## Rename map

| Today (wire + code) | After | Notes |
|---|---|---|
| `standardHearts` | standard charges | Live field on user documents; migrate in place, keep values |
| `goldHearts` | plasma charges | Same |
| `ShopItemId.STANDARD_HEART_SLOT` | standard charge purchase | `wireName` is the stored value — changing it needs a server-side alias or a migration |
| `ShopItemId.GOLD_HEART` | plasma charge purchase | Same |
| `MAX_STANDARD_HEARTS`, `MAX_GOLD_HEARTS`, `GOLD_HEART_COST`, `STANDARD_HEART_SLOT_COSTS` | config document | Deleted from code |
| `LIFE_POINTS_PER_HEART`, `HEART_REGEN_MS`, `LESSON_ATTEMPT_LIFE_COST` | depends on SPEC open question 1 | — |

UI surfaces carrying the old vocabulary, all of which change: `NoirSettingsScreen.kt`
(`settings_ils` / `settings_gold_ils` rows), `NoirShopStore.kt` (item labels and state strings),
`NoirIcons.Heart` / `NoirIcons.GoldStack`, `QuestionProgressHeader.kt` (already drawing a bolt),
`RunnerUiState.lives`, `RunnerStateHolder.livesRemainingHearts`.

## Out of scope but adjacent

`legacy/app/.../LivesController.kt` draws partial hearts from life points in the old app. Legacy
is not built by the KMP module graph and is not touched by this work.

---

## Verified for the price list (round 2)

Read first-hand on 2026-08-30, after the price list was decided.

### The activity kind is client-supplied — this is the blocker

`sourceShelf` is the only field that comes close to naming what was played, and the **client
computes it**: `LessonResultOutboxWriter.kt:109-120` derives it from `quest.visibleOn` with a
`when` over `ARENA_SHELF` / `HOME_SHELF` / `ARCHIVE_SHELF`. The server then takes it straight from
the payload — `functions/index.js:3270`, `sourceShelf: stringValue(data.sourceShelf, …)`.

That is harmless today, when every attempt costs the same 33. Under a price list it is the whole
game: a client that declares `home` for a tournament run pays 33 points instead of 500. **The
server must re-derive the kind rather than bill from the declaration.** It can: the event carries
`questId`, and quest documents carry `visibleOn`.

### Only three of the five priced activities can be played

| Priced activity | Status |
|---|---|
| Ordinary lesson | Exists — `home` shelf, `SessionMode.LEARNING` |
| Arena | Exists, but as a **shelf, not a mode** — `PUBLIC_QUEST_SHELVES` is `["home", "arena", "tournament", "tournamentFinal"]` (`functions/index.js:117`). An arena quest plays through the same runner |
| Tournament | Exists — `tournament` / `tournamentFinal` shelf |
| Theme test (контрольная) | **No code at all.** Only the sibling `spec-theme-exams` |
| Final exam (экзамен) | **Reserved, unbuilt.** `SessionMode.EXAM` exists but its only production use is a timer coefficient (`RunnerLogic.kt:125`); nothing constructs an EXAM session, and `QuizzesConfig.kt:66` defaults to `LEARNING` |

So the price key is a **pair** — shelf plus session mode — and no enum of activity kinds exists to
hang the list on. One has to be introduced, and the server has to own how it is derived.

### Boxes do not accrue offline — they under-accrue everywhere

`advanceGiftBoxState` (`functions/index.js:3208`) runs **only inside the `openGiftBox` callable**,
and advances the streak by at most one day per call: `nextStreak = currentStreak + 1`, regardless
of how much time actually passed. Five days away and one open counts as one day, not five. Nothing
on the device tracks boxes at all.

So CAP-12 is not a tightening of today's behaviour but a change in two directions: accrual has to
start happening offline, *and* the server-side advance has to account for elapsed days rather than
ticking once per call.

The strictness half is already right: opening is a server callable, and the contents are worth
guarding — `generateGiftBoxReward` (`functions/index.js:3087`) can return `addGold`, and
`giftBoxRewardUpdate` (`3174`) increments `gold`, `premiumUntilMs`, `ownedLogos` and trophies.

### Plasma regeneration and the ceiling clamp

`regenerateLifePoints` is nearly generic — it takes stored points, the stored timestamp, now, and
a ceiling — but it derives its step from the module-level `LIFE_POINT_INTERVAL_MS` instead of
taking it as an argument. Reusing it for plasma's 24h period is a one-parameter signature change,
not a rewrite.

`readEconomyBalance` (`functions/index.js:3823-3824`) clamps both balances with `Math.min` against
the ceilings, confirming that lowering a ceiling today confiscates the excess.

### Where the constants pull goes

Clean and already-shaped: a `Syncable` registered in
`apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt`, following
`ProfileBootstrapSync`. The sync layer needs a new participant, not a new mechanism.

