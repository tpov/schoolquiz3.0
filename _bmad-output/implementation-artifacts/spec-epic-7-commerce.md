---
title: 'Epic 7 — store, premium, boxes and gift claims (commerce vertical, mvp-candidate)'
type: 'feature'
created: '2026-10-19'
status: 'done'
baseline_revision: 'bdbe448a636d26b245104a31c5483818689b7aef' # diff baseline; branch point is 89092843 (context-cache housekeeping), branch HEAD at dispatch 2dd95a7f
route: 'full'
route_source: 'auto'
review: 'thorough'
review_source: 'auto'
lenses_ran: ['edge-case-hunter', 'verification-gap']
review_loop_iteration: 0
followup_review_recommended: true
context: ['_bmad-output/implementation-artifacts/epic-7-context.md', 'config/gift-box-fixtures.json']
warnings: ['multiple-goals', 'oversized']
deferred:
  - summary: >-
      The ciCheck-runnable migration protection covers only CREATE TABLE steps, so the 15→16
      ALTER (nextBonusBoxAtMs) is verified by instrumented tests that ciCheck never runs; the
      hole is a pre-existing pattern shared by five earlier ALTER migrations, and this run's
      ALTER statement is review-verified correct against the exported 16.json.
    evidence: |-
      MigrationCoverageTest extracts only CREATE TABLE statements for its schema comparison and
      greps only for the existence of a runMigrationsAndValidate call (which lives in
      androidInstrumentedTest, never run by ciCheck); a corrupted ALTER (dropped NOT NULL or
      DEFAULT 1) would crash every upgrading install while ciCheck stays green. Verified for
      fairness: 14→15, 2→3, 4→5, 6→7, 7→8 share the same hole. Closing it properly means
      teaching MigrationCoverageTest to compare ALTER TABLE … ADD COLUMN against the exported
      schema — an infra-wide change beyond this story.
    location: >-
      shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/migrations/Migration15to16.kt:15
    severity: low
  - summary: >-
      [RESOLVED 2026-10-20, retro action item 11] The device finding recorded 2026-10-19 — a
      fixture-set premiumUntilMs (now+30d, read back verified) silently wiped to 0 server-side
      across the app's entry pass — was ROOT-CAUSED as a fixture error, not a product defect: the
      account was already premium-migrated at first login, `users.premiumUntilMs` is a DERIVED
      projection recomputed from header+lots by every premium advance, and the fixture wrote the
      derived field directly after the migration; a real user cannot lose premium this way (every
      real grant path issues lots atomically). The correct fixture is a GRANT lot through the
      server's own pure code — recipe `wire/premium-grant-fixture.js`; premium survival through
      the entry sync proven on-device the same night.
    evidence: |-
      docs/agents/evidence/2026-10-20-premium-wire-repair/README.md §1 (root cause, money-adjacent
      concern answered, fixture recipe issued and verified) and §2 (premium SURVIVES sync,
      device/74-home-1.png). tickets.toml 7.3 "УРОК ФИКСТУРАМ" records the cursor-mismatch
      corollary for future hitl sessions.
    location: >-
      docs/agents/evidence/2026-10-20-premium-wire-repair/README.md
    severity: resolved (fixture error, not a product defect)
---

<intent-contract>

## Intent

**Problem:** Epic 7 (epic-commerce) is mostly closed server-side (7.7 provenance, 7.5 box-opening resilience, 7.8 dependency audit — all done), but the client never learned to claim gifts: OFFLINE_STREAK and PREMIUM_DAILY are sent by nobody, `GiftBoxStreak.visited()` has zero production callers, there is no «Получить подарок» UI, `nextBonusBoxAtMs` is not even mapped client-side, and the premium surface shows only a raw `premiumUntilMs` badge. Stories 7.1/7.6 are external (owner Play Console, devices) and stay blocked; 7.2 is device-proven except Play-locked premium normalization; 7.3's server engine exists but its client surface and test proof are thin.

**Approach:** Build the missing client gift-claim wiring against the server's actual contract — three direct callables (`submitGiftBoxOperation` kinds VISIT / OFFLINE_STREAK / PREMIUM_DAILY) with locally persisted operationId intents, strict receipt mapping, and typed statuses-as-outcomes — plus an entry-time streak sync that claims offline accrual before the server can reset it, a minimal server-owned premium surface, honest NOT_RUN records for everything external, and status/evidence updates in the epic folder. No server contract changes.

## Boundaries & Constraints

**Always:**
- Server is the source of truth for box/premium state: read `boxCount / boxStreakDays / nextBoxAtMs / nextBonusBoxAtMs / premiumUntilMs` from the profile callable (`ensureUserProfile`); field name is `boxCount`, never `boxes`.
- Every claim carries an `operationId` in `[A-Za-z0-9_-]{16,128}`, persisted locally BEFORE the request and kept until the response arrives; retry after process death reuses the SAME operationId and body; a new claim intent gets a NEW operationId.
- Treat server statuses as typed outcomes, not errors: `CLAIMED`, `NO_CHANGE`, `DAILY_LIMIT`, `PREMIUM_REQUIRED`, `STALE_BASE`, `NO_BOXES` are normal UI states; `replayed:true` envelope results render identically to fresh ones.
- Client claims are conservative: `claimed` values come from the parity-tested local model (`GiftBoxStreak`) and never exceed what the device honestly counted — surpluses are audited server-side (CAP-7), so overclaiming is a defect even when accepted.
- `base` in OFFLINE_STREAK is copied verbatim from the last server word; on `STALE_BASE` re-sync the profile and drop the local claim (server state wins), never retry the stale base.
- All gradle through `sgw` with `JAVA_HOME=/opt/jdks/jdk-27`; keep green gates: functions 92/92 (no-env) and 92/92 (demo-backend), rules 193/193, JVM `ciCheck`.
- Update `_bmad-output/mvp-candidate/epic-commerce/tickets.toml` statuses and add evidence directories under `docs/agents/evidence/<date>-*/` for whatever is proven from this tree; unavailable Play/device checks are recorded NOT_RUN, never simulated.
- Migration/additivity rules hold for any new local persistence: new tables only, no destructive resets; pending intents survive restarts.

**Never:**
- No server-side changes: do not add MUTATION_HANDLERS keys, do not touch `MUTATION_TERMS_OPERATIONS`, do not modify box/premium handlers, rules, or the purchase pipeline (7.7 audited; box ops are deliberately NOT terms-gated — "open a box is a credit, not a debit"). *(Reconciled 2026-10-25, retro action item 11: the freeze was broken deliberately by `c2c1a414` on 19.10 — root-caused envelope defect this spec itself had laid: the committed client reader demanded the `{replayed, result}` envelope while the server suite pinned the flat body, both sides green against opposite contracts; evidence `docs/agents/evidence/2026-10-20-premium-wire-repair` §3. "No server changes" above is historical, not a live claim.)*
- No dropped rules may reappear: no ad-gated premium gift, no box-for-five-ads, no general 2-box cap, no client-commanded credits.
- No invented SKUs, money prices, or Play behavior; premium per-30-days normalization waits on story 7.1 money prices — do not fake it from gold prices.
- No local midnight/timezone/calendar-day math gating claims — the day model is a rolling 24h window from `nextBoxAtMs` on server time; local clocks only feed the offline visit counter, conservatively (clock backwards/forwards never inflates claimed days).
- No premium purchase eligibility decided client-side from `premiumUntilMs` for server effects — it is presentation state; the server re-verifies premium for PREMIUM_DAILY.
- No work on quest content pipeline, wallet history (epic 8), or Arena pricing (epic 3).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Entry, streak crosses day 10 | Profile read returns `boxStreakDays 9→10` (fixture: boxStreakDays=9, nextBoxAtMs=now−2h) | Box count +1 on the home surface without any user action; streak display updates | None — server auto-grants on profile read |
| Entry visit, same-day repeat | VISIT sent twice within one 24h window | Second VISIT returns `NO_CHANGE`; UI stays consistent; anchor untouched | Normal outcome, no error surfaced |
| Entry after ≥48h gap, no offline claim | Server state stale, device was NOT used offline | VISIT resets streak to 1 (`подряд значит подряд`); UI shows restart | Normal outcome |
| Reconnect after offline use | Local model counted N offline visit-days against base B | OFFLINE_STREAK `{base: B verbatim, claimed: local count}` sent BEFORE profile refresh/visit; anchor extends by claimed days; earned boxes appear | `STALE_BASE` → re-sync profile, drop local claim silently |
| First-ever word, no server anchor | `nextBoxAtMs == 0` | OFFLINE_STREAK may claim at most 1 day and 0 boxes (server refuses box claims on first word) | Claim clamped locally to match server rule |
| Premium daily gift, eligible | `premiumUntilMs > now`, tap «Получить подарок» | PREMIUM_DAILY → `CLAIMED`, box +1, button becomes unavailable until next 24h window | Double-tap guarded by persisted in-flight intent |
| Premium daily gift, limit hit | Claim within 24h of last bonus | `DAILY_LIMIT` shown as a neutral state (already claimed today) | Not an error |
| Premium daily gift, premium expired mid-session | `totalRemainingMs <= 0` server-side | `PREMIUM_REQUIRED`; button hides on next profile sync | Not an error |
| Process death before claim response | Intent persisted, response lost | On restart the SAME operationId+body is re-sent; `replayed:true` returns stored result; no second grant | Quarantine/local-effect mapping mirrors OPEN_GIFT_BOX (`NoLocalEffect`) |
| Unconfigured store product | Play SKU absent/unconfigured | Existing visibly-unavailable states keep working (7.2 device-proven) | No regression |

</intent-contract>

## Code Map

Baseline `bdbe448a` on `night/e5-inception`; work lands on a fresh `night/e7-commerce` branch.

**Client — gift/box core (economy feature):**
- `shared/feature/economy/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/economy/data/GiftBoxIntentStore.kt` — the writer pattern to mirror: `reserve()` builds payload JSON, `mutationKey(OPERATION, id)`, one active intent, delivery row + presentation marker via `OutboxDao.reserveSynchronousIntent`; `entityRef "economy.BOX_OPENING:<openingId>"`; `active()/observeActive()` resume hook.
- `shared/feature/economy/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/economy/domain/logic/GiftBoxStreak.kt` — `visited(nowMs)` rolling-24h model, `STREAK_TARGET_DAYS=10`, `STREAK_BOXES_PER_DAY=1`; tests-only callers today; extend here (or sibling) with the offline-claim computation mirroring `boxAccrualVerdict` ceilings.
- `shared/feature/economy/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/economy/data/GiftBoxRepositoryImpl.kt` — `openGiftBox/resumePendingOpening/acknowledgeOpening`; receipt resolution via `GiftBoxDelivery.drain` (40s foreground wait).
- `shared/core/outbox/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/outbox/OutboxOperations.kt` — op constants, `mutationKey` charset `[A-Za-z0-9_-]`, `permanentReceiptOperations` (includes OPEN_GIFT_BOX), `termsConsentOperations` (SUBMIT_ATTEMPT only). Gift claims do NOT add queueable ops — direct callables instead.
- `shared/core/outbox/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/outbox/OutboxOperationsRegistryTest.kt` — parses `functions/index.js` `MUTATION_HANDLERS`/`MUTATION_TERMS_OPERATIONS`; will fail if anyone adds an op one-sided. Do not touch unless registry truth changes (it must not).
- `apps/android-next/src/main/java/com/tpov/schoolquiz/di/SyncModule.kt` — `QuarantineListener` map l.139-180 (`OPEN_GIFT_BOX → NoLocalEffect`); new claim intents need analogous no-local-effect entries if routed via delivery rows.
- `shared/feature/internet/profile/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/internet/profile/domain/model/UserProfile.kt` — `boxCount/boxStreakDays/nextBoxAtMs/premiumUntilMs`; `nextBonusBoxAtMs` MISSING — add to model, Room entity (`shared/core/persistence/.../UserProfileEntity.kt`), Firebase mapping (`platform/firebase/.../profile/FirebaseProfileRemoteDataSource.kt` consts l.102-105, `ProfileMapper.kt`).
- Direct-callable client precedent: the purchase-verification call path (`BuyGoldPackUseCase` → economy repository → platform/firebase data source; responses parsed strictly by `platform/firebase/src/test/kotlin/com/tpov/schoolquiz/platform/firebase/economy/GiftBoxReceiptMappingTest.kt` — mirror this strictness for gift-claim envelopes `{replayed, result}`).

**Client — presentation:**
- `android/feature/quest/presentation/src/main/kotlin/.../DefaultHomeQuestsComponent.kt` — owns gift state (`giftBoxOpening`, `pendingGiftBox`, `giftBoxCount/giftBoxStreakDays` l.168-169), init l.181-198 collects `observePending` + auto-resume; add per-entry streak sync (Essenty `lifecycle.doOnResume`, precedent `DefaultArenaEntryComponent.kt` l.58) and the gift-claim actions.
- `android/feature/quest/presentation/.../ui/HomeQuestsScreen.kt` — `GiftBoxFab` l.318, `GiftBoxOpeningOverlay` l.374; add «Получить подарок» action + streak state display.
- `android/feature/economy/presentation/component/DefaultShopComponent.kt` — store shelf statuses l.438-463 (UNAVAILABLE/NOT_CONFIGURED proven on device), premium-for-gold messages l.567-582; premium surface states live here (7.3 minimal scope).
- `shared/feature/economy/domain/.../EconomyConstants.kt` — `premiumGoldOffers` l.29/l.121 (server-owned gold prices), `regenMsFor(hasPremium)` l.181 (no production caller — leave as-is).
- DI: `shared/feature/economy/data/src/commonMain/.../di/EconomyDataModule.kt` (l.49-78), `EconomyDomainModule.kt` (l.46), `QuestPresentationModule.kt` (l.41), composition root `apps/android-next/.../AppApplication.kt` (`economyDataModule { sharedAuthUidFlow }`), graph gate `apps/android-next/src/test/.../KoinModuleWiringTest.kt`.
- Local persistence for claims: Room via `shared/core/persistence` (new tables only; additivity rule).

**Server contract — reference only, DO NOT MODIFY:**
- `functions/index.js:3228` `ensureUserProfile` → profile response l.8156+ (box/premium fields; auto-grants the day-10 box on read while preserving `nextBoxAtMs` — pinned by `functions/economy-handler.test.js` "profile refresh preserves offline anchor…").
- `functions/index.js:4404-4412` `openGiftBox` / `submitGiftBoxOperation` callables + wire-code mapping (`already-exists` = CONFLICTING_REPLAY, `permission-denied` = uid mismatch).
- `functions/box-service.js` — `applyBoxOperation` l.232 (receipt fence `box_operations/{sha256(id)}`, statuses `OPENED/CLAIMED/NO_CHANGE/STALE_BASE/NO_BOXES/PREMIUM_REQUIRED/DAILY_LIMIT`), OFFLINE_STREAK branch l.128, PREMIUM_DAILY branch l.144 (shares one 24h slot with ad boxes: sets `nextBonusBoxAtMs` AND `nextAdRewardBoxAtMs`).
- `functions/gift-boxes.js` — `readBoxState` l.59, `advanceStreak` l.79 (anchor moves ONLY on VISIT; ≥48h late → streak reset to 1; max one box per visit), `boxAccrualVerdict` l.114 (offline ceilings: elapsed days, streak ceiling, box days beyond max(base,9), one box/day; surplus → overclaim audit record, under-claim accepted).
- `functions/mutation-terms.js` + `MUTATION_TERMS_OPERATIONS` (`index.js:1976`) — gate set covers ONLY `lesson_runner.SUBMIT_ATTEMPT`; box ops intentionally excluded; purchase ops not gated either (fresh-consent for commerce = deferred, see Deferred context).
- Rules (`firestore.rules`): `box_operations` closed :106, `premium_accounts` closed :85-87, receipts owner-read :33-40 — client sees money facts only through callables.
- Server tests to mirror/extend NOTHING in (server is frozen), but they pin the contract: `functions/economy-handler.test.js` (queued-vs-direct equality, PREMIUM_DAILY DAILY_LIMIT, VISIT, profile-preserves-anchor), `functions/box-service.test.js` (OFFLINE_STREAK base/claimed, ceilings, stale base).
- `config/gift-box-fixtures.json` — parity fixtures incl. boxStreakDays 9→10 case; consumed by `GiftBoxStreakParityTest`.

**Fixtures & rig (for evidence runs from this tree):**
- Demo backend container `schoolquiz-demo-firebase-20261004` (projectId `demo-schoolquiz-mvp`, firestore 127.0.0.1:18090, auth 127.0.0.1:19090); staged fixture: `users/VLqWYMHXqyI8pPWQdNXdkQg9FY6E` with `boxStreakDays=9, nextBoxAtMs=now−2h` (evidence `docs/agents/evidence/2026-10-18-pixel-commerce-hitl/gift-sources-finding/`).
- Node contract gates: `cd functions && npm test` (no-env) and `FIRESTORE_EMULATOR_HOST=127.0.0.1:18090 GCLOUD_PROJECT=demo-schoolquiz-mvp npm --prefix functions test` (demo-backend).

## Tasks & Acceptance

**Execution:**
- [ ] `_bmad-output/mvp-candidate/epic-commerce/tickets.toml` -- restate 7.4's finding as the run's scope; statuses updated at the end for every touched story -- the epic folder is the initiative's source of truth.
- [ ] `shared/feature/internet/profile/...` (UserProfile.kt, UserProfileEntity.kt + DAO/migration, FirebaseProfileRemoteDataSource.kt, ProfileMapper.kt) -- add `nextBonusBoxAtMs` end-to-end (model, Room column via additive migration, Firebase const+parse, mapper) -- the daily-gift availability field is not synced today.
- [ ] `shared/feature/economy/domain/.../logic/GiftBoxStreak.kt` (+ `config/gift-box-fixtures.json`, `GiftBoxStreakParityTest`) -- add offline-claim computation: given base (server word) + locally counted visit days, produce `claimed {boxStreakDays, boxesEarned}` clamped to the server's ceilings (elapsed days, box days beyond max(base,9), one box/day, first-word limit) -- conservatism keeps overclaim audits at zero.
- [ ] `shared/feature/economy/data/...` new `GiftClaimIntentStore` (+ Room tables via additive migration) -- persist one in-flight intent per claim kind (kind, operationId, payload JSON, createdAtMs, state), reserve/resolve/active-observe mirroring `GiftBoxIntentStore` -- process death must never lose or duplicate a claim.
- [ ] `shared/feature/economy/data/...` new `GiftClaimRepository` (+ DI in EconomyDataModule) -- direct-callable transport for `submitGiftBoxOperation` (mirror the purchase-verification client path), strict envelope parsing (`replayed` + typed statuses), `STALE_BASE` re-sync handling, resolve intent on any terminal outcome -- statuses are outcomes, not exceptions.
- [ ] `shared/feature/economy/domain/.../use_case/` new `SyncBoxStreakUseCase` and `ClaimPremiumDailyGiftUseCase` (+ EconomyDomainModule DI) -- entry orchestration: offline claim FIRST (base = last server word from Room), then VISIT (idempotent per window), then profile refresh; premium gift gated by persisted intent + `nextBonusBoxAtMs`/`premiumUntilMs` presentation state -- ordering prevents the 48h reset from eating honest offline days.
- [ ] `android/feature/quest/presentation/.../DefaultHomeQuestsComponent.kt` + `HomeQuestsScreen.kt` (+ strings) -- per-entry `doOnResume` streak sync; «Получить подарок» action with in-flight/disabled/claimed-today states; streak + box display wired to synced fields -- the surfaces the device proofs will exercise.
- [ ] `android/feature/economy/presentation/.../DefaultShopComponent.kt` (+ views/state) -- minimal premium surface typing from server-owned fields: active-until, daily-gift availability, pending-purchase/pending-refund states already proven -- keeps 7.3's client half honest without a full premium screen.
- [ ] `apps/android-next/.../di/SyncModule.kt`, `AppApplication.kt`, `KoinModuleWiringTest.kt` -- register new stores/use-cases; quarantine/local-effect entries for claim intents -- graph gate stays green.
- [ ] Tests per layer -- commonTest for GiftBoxStreak claim math + fixtures parity; data commonTest for GiftClaimIntentStore/GiftClaimRepository (mirroring `GiftBoxRepositoryTest` structure: replay, process-death retry, STALE_BASE, typed statuses); component JVM tests for entry sync and gift button states (mirroring `DefaultHomeQuestsComponentTest`); mapping tests for the gift envelope (mirroring `GiftBoxReceiptMappingTest`); Node: `functions/economy-handler.test.js` needs NO changes (server frozen) -- prove client payload conformance by feeding the exact payload shapes of the new Kotlin writers into a fixture-based Kotlin contract test instead -- every AC needs a test that can run on the VPS.
- [ ] Premium engine proof (7.3 server half) -- audit `functions/premium-economy.js` test coverage for both doubled regeneration effects, holds/overlaps, independent remaining time; add Node cases ONLY where a documented behavior lacks any case, without touching production code -- 7.3's "server computes with real intervals and saved remainders" must be provable from this tree.
- [ ] `docs/agents/evidence/2026-10-19-epic7-commerce-client/README.md` -- record what was proven (gates, contract tests, fixture-driven demo-backend checks via Node harness) and mark explicitly NOT_RUN: 7.1 inventory, 7.6 sandbox matrix, 7.2 premium normalization (Play-locked), regeneration UI device proof (7.3), two-device gift claims on hardware (7.4) -- statuses PASS/FAIL/NOT_RUN preserved per epic rule.
- [ ] Assemble the MVP APK for the next hitl device session: `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :apps:android-next:assembleMvp -PmvpEmulatorHost=204.44.66.27 -PmvpEmulatorFunctionsPort=5002` -- 7.4's device proof becomes runnable the moment the owner attaches a phone.
- [ ] `_bmad-output/mvp-candidate/epic-commerce/tickets.toml` -- final statuses: 7.2 note fresh-consent deferral, 7.3 note client-surface + server-test proof done / regeneration UI hitl, 7.4 note client wiring done / device proof pending, 7.1/7.6 unchanged blocked-external -- the next session reads truth, not hopes.

**Acceptance Criteria:**
- Given the demo fixture (`boxStreakDays=9, nextBoxAtMs=now−2h`) and a fresh client sync, when the entry streak sync runs against the demo backend, then the profile shows `boxStreakDays=10` and `boxCount` incremented by exactly 1, with no user action.
- Given the same-day second entry, when the sync runs again, then VISIT yields `NO_CHANGE`, box count is unchanged, and no duplicate intent row remains active.
- Given a premium user with `nextBonusBoxAtMs` in the past, when they tap «Получить подарок», then exactly one PREMIUM_DAILY intent is persisted, the server responds `CLAIMED`, box count +1, and the button shows claimed-today; a second tap cannot send a second intent.
- Given a non-premium user, when the home surface renders, then the gift button is absent, and if claimed anyway (stale state) the `PREMIUM_REQUIRED` outcome hides it without an error dialog.
- Given N honestly counted offline days with base B, when connectivity returns, then OFFLINE_STREAK sends `{base: B, claimed: ≤ceilings}` before any profile refresh, the server accepts the claim, and a 5-day gap does NOT reset the streak.
- Given a stale server word (another device advanced the streak), when the offline claim gets `STALE_BASE`, then the client re-syncs the profile, drops the local claim, and renders the server state without user-visible errors.
- Given process death between sending a claim and its response, when the app restarts, then the same `operationId` is re-sent, the `replayed:true` stored result is applied once, and box count changes exactly once.
- Given any new Room persistence, when an old database opens, then the additive migration preserves all rows and pending intents survive restart.
- Given the full gate set, when the run finishes, then `cd functions && npm test` = 92/92 both modes, rules 193/193, JVM `ciCheck` green except the two known stale arena-pin reds in `platform:firebase` (owner follow-up, unrelated), and no new Kotlin/Node suite is skipped.

## Implementation Notes

- Branch: `night/e7-commerce` from `bdbe448a636d26b245104a31c5483818689b7aef`; spec committed as first docs commit.
- (appended during implementation)
- 2026-10-19 run start: tickets.toml scope restated from 7.4's finding (client cannot claim gifts; this run builds the claim wiring + MVP APK). Plan of record: nextBonusBoxAtMs lands as additive migration 15→16 (column with DEFAULT 0, `@ColumnInfo(defaultValue="0")` like `SyncStateEntity.docId`); gift-claim persistence (gift_claim_intents + local offline streak counter) as 16→17; schema JSONs are re-exported by KSP and committed; instrumented migration cases added textually per existing practice (MigrationCoverageTest guards them from JVM).
- Surprise 1 (pre-existing baseline red, fixed minimally): `ActivityRepositoryImplTest.FakeAttemptDao` did not implement five `LessonAttemptDao` members added by the arena journal work (5d006688) — the whole `commonTest` source set of `:shared:feature:internet:profile:data` failed to compile, which `ciCheck`'s `allTests` would hit. Repaired the fake with neutral stubs only (commit 37b0878c); no production code touched. Note: the epic-6 evidence records full ciCheck was SKIPPED by decision, so this red was invisible in recent runs.
- Offline-claim math lives in `GiftBoxAccrual.claim(base, counted, nowMs)` next to `GiftBoxStreak` — mirror of `boxAccrualVerdict` ceilings (elapsed days, streak ceiling, box days beyond max(base,9), one box/day, first-word 1 day/0 boxes). Parity-tested against the existing `claims` section of `config/gift-box-fixtures.json` (no fixture changes needed: the server verdicts already pin the accepted values the client clamp must land on); clock-direction conservatism covered by `GiftBoxAccrualTest`.
- Surprise 2 (baseline state of the heavy gates is worse than the spec's premise): epic-6 evidence records full ciCheck was SKIPPED by decision; spot checks confirm more pre-existing reds besides the two arena-pin tests in platform:firebase — `detektMetadataCommonMain` fails tree-wide with `Invalid value (27) passed to --jvm-target` (detekt build vs JDK 27), platform:firebase ktlint has multiple pre-existing findings (untouched, matches epic-6 record). Strategy: touched modules are kept ktlint/detekt-clean where the toolchain allows, and the final gates run will verify MY suites green + the reds are exactly the documented pre-existing ones.
- Surprise 3 (pre-existing build break, fixed minimally): `AppApplication` references `BuildConfig.MVP_EMULATOR_HOST/PORT` unconditionally (a2298084) while those fields existed only in the `mvp` build type — `assembleDebug` and every `:apps:android-next` unit test (incl. the KoinModuleWiringTest graph gate) could not compile on the baseline. Repair: declare the two fields in `defaultConfig` with safe defaults; the `mvp` type still overrides them from `-P` properties (de1d1fdd).
- Premium engine proof (7.3 server half): audit of `functions/premium-economy.test.js` (15 cases) + `functions/premium-grants.test.js` (29 cases) against the documented behaviors — both doubled regeneration effects (both balances until expiry, normal rate after), holds (offline ticket reservations occupy charge capacity), overlaps/FIFO (grant over active premium = max(now,end)+duration; mid-way grant starts strictly after; queued-target refunds), independent remaining time per grant (partial Play refund preserves consumed time, parallel gifts untouched), saved remainders (rate change settles old gap, keeps earned work). Every documented behavior has at least one case; per the "add Node cases ONLY where a documented behavior lacks any case" rule, zero Node changes made — production code untouched.
- Live-fixture discipline: the demo fixture user (`VLqWYMHXqyI8pPWQdNXdkQg9FY6E`, boxStreakDays=9) was deliberately NOT claimed against (no live VISIT/OFFLINE_STREAK fired from this tree) so the device hitl session can still exercise the 9→10 crossing; its proof runs through the Node contract tests and the Kotlin contract tests instead (NOT_RUN in evidence).
- Verify-stage additions (separate verify session, same day): c51120be staged the four domain sources their own imports require — data/persistence/firebase layers and their tests were committed in 9f4440ed while the domain files (GiftClaim.kt, GiftClaimRepository.kt, GiftBoxLocalStreakStore.kt, SyncBoxStreakUseCase.kt) stayed untracked, so HEAD did not compile on a fresh checkout; the working tree contained the same files during every gate run, so those results stand. 253b0a90 fixed the offline-claim envelope: `boxAccrualVerdict` grants min(days beyond base, elapsed) boxes in ONE answer (honest week = CLAIMED earnedBoxes 7 — exactly what GiftBoxAccrualTest asserts), while the envelope reader demanded exactly 1 for every CLAIMED and would refuse a legal answer into an undecidable intent (ENVELOPE_INVALID loop); VISIT and the premium daily stay strictly one; +3 mapping cases.
- Verification re-runs (verify session): `:platform:firebase:testDebugUnitTest` 323 tests, failures exactly the 2 documented foreign arena pins; functions 124/124 no-env and 124/124 demo-backend (in-container); rules gate NOT re-run — `@firebase/rules-unit-testing` is absent from the current containers, and no commit of this run touched `firestore.rules` since the implementer's 212/212 at 10a463a1+; MVP APK rebuilt with the envelope fix (12:27 UTC) — note `packageMvp` requires `-I /tmp/signfix.gradle` for the mvp keystore password.
- Matrix test audit: all ten I/O rows map to green tests (entry day-10 crossing → SyncBoxStreakUseCaseTest reconnect-claims + server-pinned economy-handler; same-day VISIT → secondEntry + typedStatuses; 48h reset → clockSetBackwards + server advanceStreak; offline ordering → reconnectClaims…; first word → GiftBoxAccrualTest firstEver; premium eligible/limit/expired → tap-sends-one + limitAndExpiry + hidden-button; process death → undecidedCallKeepsIntent + processDeathResumes; unconfigured product → PremiumSurfaceStateTest + unchanged 7.2-proven shelf). The audit surfaced the multi-box envelope defect itself (row "Reconnect after offline use", N>1) — fixed in 253b0a90 and covered by the new mapping test.

## Spec Change Log

### 2026-10-25 — Retro reconciliation (epic-7-retro-2026-10-25 action item 11, applied by spec-e7-retro-fixes)
- Retracted "No server changes" (Boundaries, Never) and "Server untouched (0 changes)" (Auto Run Result): the freeze was broken deliberately by `c2c1a414` on 19.10 — the server answered `{replayed, result}` after the envelope defect this spec had laid (client reader vs server pin on opposite contracts, caught only on-device); replaced with a record of that commit and its cause.
- Deferred `premiumUntilMs-wipe` moved to resolved: fixture error (derived field written post-migration), recipe `wire/premium-grant-fixture.js`, survival proven on-device — evidence `2026-10-20-premium-wire-repair` §1–2.
- Transport decision direct-callables-vs-outbox recorded as an ADR note in `epic-7-context.md` (Technical Decisions), reasoned from the Design Notes below.

## Review Triage Log

### 2026-10-19 — Review pass (thorough: edge-case-hunter, verification-gap)
- verdicts: 13 findings — high 2, medium 2, low 7, false 1, maybe-false 1
- findings:
  - `[high]` `[patch]` Offline claim computed after `visited(now)` — a late reconnect (>48h after the last offline visit) let the local model's 48h reset destroy the honestly counted offline days before the claim, so owed=false and the days were lost while VISIT reset the server streak (contradicted the spec's AC "a 5-day gap does NOT reset the streak") — patched in 3870ca6c: the claim is computed from the count including today's visit unless the visit's reset cut the streak (then the pre-visit count stands; the server decides the reset on the VISIT itself); regression case `aLateReconnectStillClaimsTheHonestlyCountedOfflineDays` added, economy domain suites green.
  - `[high]` `[patch]` (claims-check, same root cause) Spec AC ordering row contradicted by the pre-patch code — resolved by the same 3870ca6c reorder; row kept separate per the claims finding, same fix.
  - `[medium]` `[patch]` (edge E1 + verification-gap V1, one root cause) `giftClaimFailureFrom` matched the wire literal "already-exists" while its only call site passes `FirebaseFunctionsException.Code.ALREADY_EXISTS.name` — the CONFLICTING_REPLAY branch was dead, a real fence refusal looped as TRANSPORT with verbatim retries forever, and the mapping test pinned the literal production never supplies — patched in 3870ca6c: arm matches "ALREADY_EXISTS", test feeds the real value with the SDK provenance comment.
  - `[low]` `[patch]` OFFLINE_STREAK stored payload missing base/claimed halves passed requestFrom and failed later in toWireMap as a bare IllegalStateException → wrapped as TRANSPORT → intent unresolvable, blocked VISIT every entry — patched in 3870ca6c: parse-time named refusal + malformed-body test case (writer is the sole producer; belt for a corrupted row).
  - `[low]` `[patch]` `number()` truncated fractional SDK Doubles (1.5→1), contradicting the exactness its own test asserts via the JsonFacade path — patched in 3870ca6c: non-integral Double refused; `fractionalSdkDoublesAreRefusedNotTruncated` case added with a hand-built SDK-shaped map.
  - `[low]` `[patch]` (verification-gap) Shop premium-surface wiring (profile collect → `state.premium`) unobserved by any test — deleting the collect block or transposing the two words shipped green — patched in 3870ca6c: `given the profile carries the premium words…` component test asserts both fields by name (transposition fails it).
  - `[low]` `[patch]` (verification-gap) ProfileMapper `nextBonusBoxAtMs` round trip untested anywhere; dropping the mapper line would zero the bonus clock at the persistence boundary and make ClaimedToday unreachable across sessions with every gate green — patched in 3870ca6c: `ProfileMapperTest` round-trips an entity carrying every server word and asserts the field both directions.
  - `[low]` `[defer]` Migration 15→16 ALTER has no ciCheck-runnable protection (instrumented-only; MigrationCoverageTest compares CREATE TABLE shapes only) — deferred: the hole is a pre-existing pattern shared by five earlier ALTER migrations, the new statement is review-verified correct against 16.json, and the proper fix is an infra-wide MigrationCoverageTest extension (see deferred list).
  - `[low]` `[reject]` Premium-gift button state not driven by a wall-clock tick — stale Available after expiry while the screen stays open — rejected: users rarely meet it, the harm is a stale presentation the server safely refuses (PREMIUM_REQUIRED outcome), and the fix (ticker flow) adds real complexity for no money-safety gain.
  - `[low]` `[reject]` NoirShopStore `remember { System.currentTimeMillis() }` freezes the premium card's dates for the composable's lifetime — rejected: display-only staleness (no action is gated by the card), same rare-meeting and complexity reasoning as the button tick.
  - `[false]` `elapsedDays` toInt() overflow for a far-future device clock — refuted: wrapping requires elapsed > 2^31 days (~5.9 billion years); no reachable clock state produces it.
  - `[maybe-false]` `[defer]` premiumUntilMs wiped to 0 by the entry sync on device (recorded finding blocking 7.4's premium device proof) — if true a high money-adjacent defect; the new code only reads the profile, so the suspect set is a server write path or a pre-existing client upsert — deferred unverified with the settling protocol in the deferred list.

## Design Notes

**Why direct callables, not outbox ops:** the server deliberately exposes exactly one queueable gift op (`economy.OPEN_GIFT_BOX`); VISIT/OFFLINE_STREAK/PREMIUM_DAILY exist only as `submitGiftBoxOperation` kinds with their own receipt fence (`box_operations/{sha256(operationId)}`: replay returns the stored response, altered body/uid → `already-exists`). Adding MUTATION_HANDLERS keys would change the audited 7.7 surface for zero benefit; instead the client reproduces the outbox's discipline locally: persist intent (operationId+body) BEFORE the request, resolve on terminal outcome, re-send identical body after process death. Server-side idempotency does the rest.

**Anchor semantics (load-bearing, pinned by `economy-handler.test.js`):**
- `nextBoxAtMs` = last counted visit + 24h; moves ONLY on VISIT (and OPEN), never on profile reads or offline claims' grant path.
- Profile read auto-grants the day-10 crossing box while preserving the anchor (test: `boxCount 1→2`, anchor unchanged) — "automatic on entry" is already server behavior; the client's job is to not lose data before it.
- VISIT with `now ≥ nextBoxAtMs + 24h` resets streak to 1 — so on reconnect the OFFLINE claim must go FIRST, extending the anchor by claimed days (`boxAccrualVerdict.next`), or the profile refresh + visit would eat the offline days as a "missed" gap.
- PREMIUM_DAILY shares ONE 24h slot with ad boxes (`nextBonusBoxAtMs` AND `nextAdRewardBoxAtMs` both set) — owner decision 12.10; the UI must treat DAILY_LIMIT as "already claimed today", not as an ad-box conflict.

**Payload contracts (client must satisfy exactly):**
- OFFLINE_STREAK: `{kind, ownerUid, operationId, base: {boxStreakDays, nextBoxAtMs}, claimed: {boxStreakDays, boxesEarned}}` — all exact non-negative safe ints; `base` verbatim from the last server word; first-ever word (`nextBoxAtMs=0`) may claim ≤1 day and 0 boxes.
- PREMIUM_DAILY / VISIT: `{kind, ownerUid, operationId}`.
- Response envelope `{replayed, result}` with `result.schemaVersion=1`, typed `status`; strict mapping per `GiftBoxReceiptMappingTest`.

## Verification

**Commands:**
- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew ciCheck --no-configuration-cache` -- expected: green except the two known stale arena-pin reds in `platform:firebase` (documented owner follow-up).
- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew test --no-configuration-cache` -- expected: all new suites green.
- `cd functions && npm test` -- expected: 92 of 92 suites passed (no-env).
- `FIRESTORE_EMULATOR_HOST=127.0.0.1:18090 GCLOUD_PROJECT=demo-schoolquiz-mvp npm --prefix functions test` -- expected: 92 of 92 against the live demo backend.
- `firebase emulators:exec --only auth,firestore "node scripts/rules-emulator-test.js"` (isolated ports per 7.7 practice) -- expected: 193/193.
- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :apps:android-next:assembleMvp -PmvpEmulatorHost=204.44.66.27 -PmvpEmulatorFunctionsPort=5002` -- expected: APK artifact for the hitl device session.

**Manual checks (no CLI):**
- Evidence README lists every story 7.1–7.8 with PASS / NOT_RUN and the exact reason for each NOT_RUN; no simulated device/Play results.

## Auto Run Result

Status: done
Blocking condition: none

**Summary.** Epic 7 (epic-commerce) closed from this tree: the client learned to claim gifts against the server's direct-callable contract — VISIT / OFFLINE_STREAK / PREMIUM_DAILY via `submitGiftBoxOperation` with a locally persisted operationId intent (outbox discipline without outbox ops), entry streak sync ordering the offline claim before the visit so the 48h reset cannot eat honest offline days, the explicit «Получить подарок» button with typed states (Available/InFlight/ClaimedToday/Hidden), `nextBonusBoxAtMs` synced end-to-end (additive migration 15→16), gift-claim persistence (16→17) joined to the owner-rows registry, a minimal shop premium surface naming the server's words, and the offline-claim math parity-tested against the shared fixtures' server verdicts. Server changed post-spec by `c2c1a414` (19.10, the `{replayed, result}` envelope repair) — a root-caused wire defect the spec itself had laid: the client reader demanded the envelope while the server suite pinned the flat body, both sides green against opposite contracts (evidence `docs/agents/evidence/2026-10-20-premium-wire-repair` §3; reconciled 2026-10-25, retro action item 11); firestore.rules and the purchase pipeline stayed untouched. Stories 7.1/7.6 recorded NOT_RUN/external (7.1 was resolved by the owner mid-run and recorded by the implementing session), 7.2/7.5/7.7/7.8 previously closed.

**Files changed (baseline bdbe448a → 3870ca6c, 19 commits + docs):** profile model/Room/Firebase mapping (+`nextBonusBoxAtMs`); `GiftBoxStreak.kt` (+`GiftBoxAccrual` ceiling mirror); new domain `GiftClaim.kt`/ports/`SyncBoxStreakUseCase`/`ClaimPremiumDailyGiftUseCase`; new data `GiftClaimIntentStore`/`GiftClaimRepositoryImpl`/`RoomGiftBoxLocalStreakStore`/remote port + `FirebaseGiftClaimRemoteDataSource` (strict envelope reader); persistence migrations 15→16, 16→17 + entities/DAOs/owner-registry; home entry sync + premium gift button + streak badge (strings ×3); shop premium card + `PremiumSurfaceState`; Koin modules + graph gates; baseline compile repairs (FakeAttemptDao stubs, MVP_EMULATOR buildConfig fields for all build types, Byte Buddy test flag); Node mvp-isolation stale-pin catch-up (test-only); evidence `2026-10-19-epic7-commerce-client` + `2026-10-19-gift-claims`; tickets.toml statuses restated.

**Review findings breakdown:** thorough pass (edge-case-hunter + verification-gap), 13 findings — 6 patch entries applied in 3870ca6c (late-reconnect offline-claim loss [high]; dead ALREADY_EXISTS fence branch with false test coverage [medium]; payload-shape parse refusal; fractional-Double refusal; premium-surface wiring test; profile-mapper round-trip test), 2 defers (migration ALTER ciCheck hole [low, pre-existing pattern]; premiumUntilMs device wipe [maybe-false, if-true high, settling protocol recorded]), 2 rejected lows (presentation clock staleness, server safely fences the outcome), 1 false (toInt overflow unreachable). Rejected findings' reasons are in the triage log rows.

**Follow-up review recommendation: true** — one patched entry was high. The named unverified risk: the offline-claim path across a real multi-day gap is proven by JVM tests and fixture parity only (no device walk), and 7.4's premium-gift device proof stays blocked by the recorded premiumUntilMs-wipe finding; a follow-up pass should run after the device session settles both.

**Verification performed:** functions no-env 124/124 and demo-backend 124/124 (in-container re-run); `:platform:firebase:testDebugUnitTest` 323 tests with failures exactly the 2 documented foreign arena pins; economy domain + economy presentation + profile data module suites green after the review patches; rules gate 212/212 per the implementing session's run at 10a463a1+ (not re-runnable in current containers — module absent; no commit touched firestore.rules); MVP APK rebuilt twice with the fixes via `-I /tmp/signfix.gradle` (final 13:03 UTC); full ciCheck remains environmentally red on this VPS beyond this run's control (detekt vs JDK 27 tree-wide, pre-existing ktlint findings, 2 arena pins — recorded in the evidence README).

**Residual risks:** device proof of the whole gift-claim UX (streak crossing, offline gap, premium gift) awaits the owner's hitl session with the re-staged fixture (`nextBoxAtMs = now − 2h` re-seed required); the premiumUntilMs-wipe investigation may surface a server-side defect; the migration-test hole is shared with five pre-existing ALTER migrations.
