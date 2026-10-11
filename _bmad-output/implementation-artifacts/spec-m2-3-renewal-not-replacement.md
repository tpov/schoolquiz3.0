---
title: 'M2.3 — Renewal, not replacement'
type: 'feature'
created: '2026-10-11'
status: 'in-progress'
baseline_commit: 'd59a0299'
review_loop_iteration: 0
story_key: '2-3-продление-а-не-замена'
sprint_status_file: '_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml'
context:
  - '{project-root}/_bmad-output/planning-artifacts/epics-monetisation.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m2-1-premium-is-one-subscription.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m2-2-premium-for-gold-and-from-a-box.md'
  - '{project-root}/_bmad-output/mvp-candidate/owner-decisions/owner-decisions-2026-10-10-play-subscriptions.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** premium is one auto-renew subscription (`premium_30d`, owner model 2026-10-10), and the
renewal is invisible to everything built so far. The store queue only carries *unacknowledged*
subscriptions (`PlayBillingRepository` filters `!isAcknowledged`), and story 2.1 acknowledges the
token right after the first credit — so the same token never comes back. The settlement replays by
token hash alone, so even a re-presented token answers term one's saved response forever. RTDN
ignores `subscriptionNotification` entirely (clawback reads voided and one-time products only). The
result: Play charges the player every month and premium dies at the end of the first term. On top of
that, the "has premium" sign on the device is a stored boolean copied from the server document, not
something computed from the expiry date — it outlives the premium it describes.

**Approach:** the term is the truth, and everything keys on it. The settlement record keeps the
expiry it settled (`subscriptionExpiryMs`); a fresh Play lookup with the *same* expiry replays, a
*later* expiry settles as a renewal — a new FIFO lot through the same `premium-economy` engine, so
the new end date is `max(now, current end) + granted days` by construction (gold and box grants
stack the same way, and an expired premium simply starts again from now). Each settled term gets its
own grant id (`settlementId_expiry`), receipt and audit row, and "the same paid term grants once"
stays a pure consequence of the record. The server learns of renewals from the channel it already
owns: RTDN `subscriptionNotification` types that can move the term forward (RENEWED, RECOVERED,
RESTARTED) re-run the *same* settlement path with the uid taken from the existing record — no second
grant implementation. On the device the boolean disappears as an independent fact: the user-stats
sync computes `hasPremium` from `premiumUntilMs` against the clock, and the noir screens compare the
date with now instead of with zero.

## Boundaries & Constraints

**Always:** extend, never replace — `max(now, окончание) + длительность` for every path (PLAY, GOLD,
BOX); the lots engine is the only arithmetic. A paid term grants exactly once: same token + same
expiry → replayed saved answer, zero movement, forever. The pre-Play replay gate stops
short-circuiting premium settlements (it cannot know the fresh expiry without asking Play); gold
packs keep their one-read replay. Every renewal writes its own receipt and audit row; the settlement
record carries the latest settled term and a renewal count. RTDN messages that name no settlement
record are acknowledged with a logged reason, never retried into a storm. Client flag: computed at
the sync boundary from the date, recomputed on every sync — Room keeps the projection, not a second
opinion.

**Ask First:** subscription *refund/cancellation* semantics for a multi-term token (which lot a
voided order claws back) — that is story 1.3's review territory; this story only records per-term
provenance so that decision has something to read. Any change to `configs/economy`. Any new Play
Console product or notification type beyond RENEWED/RECOVERED/RESTARTED.

**Never:** replace `premiumUntilMs` instead of extending; grant the same expiry twice; consume the
subscription token; grant from `purchaseTime` or any client-supplied duration; key a settlement on
anything but the token hash (revocation fences and clawback read that key); let a stored boolean
decide "has premium" anywhere a date is available; touch owner files
(`gradle.properties`, `local.properties`, `functions/.secret.local`, `.maestro/**`).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Grant over active premium (any source) | premium until E, now < E, grant D days | new end = E + D·day (FIFO lot; the AC formula) | N/A |
| Grant over expired premium | premium ended before now, grant D days | new end = now + D·day | N/A |
| Gold purchase over active premium | enough gold | one debit of the offer's price, one new lot; replayed transaction charges nothing extra | `FAILED_PRECONDITION` when gold is short |
| Same token, same expiry | settled term recorded, Play reports the same | replayed saved response; no lot, no receipt, no audit row | N/A |
| Same token, later expiry (renewal) | Play reports a greater `expiryTime` | new lot `settlementId_expiry`, whole days to the new expiry capped by the table; settlement record updated (term + renewal count); own receipt + audit row | race: the losing call throws on the existing grant id and retries into a replay |
| Same token, earlier expiry | Play reports a smaller `expiryTime` | replayed saved answer (no movement); the fence, not this path, handles revocation | N/A |
| Renewal while revocation fence pending/confirmed | RTDN clawback fence exists | refusal wins over renewal (owner first) | terminal refusal |
| RTDN RENEWED/RECOVERED/RESTARTED | `subscriptionNotification` on `play-rtdn` | uid from the existing settlement record; the same `settlePurchase` runs; outcome ignored (no client to answer) | no settlement record → logged and acknowledged, no retry |
| Client flag | Firestore `users/{uid}` doc | `hasPremium = premiumUntilMs > now` computed at read; settings/profile rows compare the date with now, not with zero | N/A |
| Deactivated premium SKUs reappear | any path | unchanged refusal (`SKU_NOT_SOLD`) | terminal refusal |

## Code Map

- `functions/purchase-verification.js` — `settlementGate(existing, uid, freshExpiry)`: premium
  settlements with a recorded term defer to the expiry comparison (later → fall through to the full
  decision, same/earlier/unreadable → replay); the credit for a premium product gains
  `subscriptionExpiryMs`; `settlementRecord`/`receiptRecord`/`auditRecord` carry the term, and the
  grant id is derived from `settlementId_expiryMs`.
- `functions/purchase-settlement.js` — the pre-Play replay gate stops short-circuiting premium
  settlements (one `subscriptionsv2` read per re-presentation of a settled token); the premium
  branch derives the grant source id from the settled term and updates the existing settlement
  record on renewal instead of `create`-ing a second one.
- `functions/purchase-notifications.js` — `readSubscriptionRenewal(message)`: the term-moving
  subscription types (2 RENEWED, 1 RECOVERED, 7 RESTARTED) reduced to the token; everything else
  stays `null`.
- `functions/purchase-clawback.js` or a sibling — `settleSubscriptionRenewal({db, playApi, now,
  purchaseToken, log})`: record lookup → uid → the *same* `settlePurchase`; missing record is a
  logged acknowledgement, not an error.
- `functions/index.js` — `playPurchaseNotifications` tries the renewal read first, clawback
  otherwise (the notification families never overlap).
- `functions/premium-economy.test.js`, `functions/premium-shop.test.js` — the AC as law: extend over
  active, restart from now over expired, one debit per purchase, replay without a second lot.
- `platform/firebase/.../FirebaseUserStatsDataSource.kt` — `hasPremium` computed from
  `premiumUntilMs` against an injectable clock; `RawUserStats` unchanged otherwise.
- `android/feature/local/settings/.../NoirSettingsScreen.kt`,
  `android/feature/internet/profile/.../NoirProfileSections.kt` — the premium row compares
  `premiumUntilMs` with now, not with zero.
- Tests: `purchase-verification.test.js`, `purchase-settlement.test.js`,
  `purchase-notifications.test.js`, platform/firebase mapper test; Kotlin gates for the touched
  modules.

## Tasks & Acceptance

**Execution:**
- [ ] Engine law tests: extension over active/expired, single debit, replay idempotence.
- [ ] Settlement: expiry-aware gate, per-term grant id, record update + receipt/audit per term.
- [ ] RTDN: term-moving subscription notifications re-run the settlement; unknown records
      acknowledged.
- [ ] Client: `hasPremium` from the date in the stats sync; noir screens compare with now.
- [ ] Gates via `sgw ./gradlew` for touched modules; functions `node run-tests.js`.
- [ ] `sprint-status-monetisation.yaml`: 2-3 → review.

**Acceptance Criteria:**
- Given у игрока активный или истёкший премиум, when приходит новый любым путём (деньги, золото,
  коробка), then новая дата окончания равна `max(сейчас, окончание) + длительность`.
- Покупка поверх активного не списывает дважды: один оплаченный срок — одно продление, один дебет;
  повтор того же срока ничего не двигает.
- Признак «есть премиум» вычисляется из даты (`premiumUntilMs` против часов), а не хранится
  отдельно.

## Verification

**Commands:**
- `cd functions && node run-tests.js`
- `sgw ./gradlew :platform:firebase:testDebugUnitTest :shared:feature:economy:data:allTests --no-configuration-cache` (touched)
- `sgw ./gradlew ktlintCheck detekt --no-configuration-cache` (touched modules)
- `sgw ./gradlew :apps:android-next:assembleDebug --no-configuration-cache`
- Rig (HITL): install, shop opens, premium row explains the unreachable store; renewal itself needs
  a GMS device with a sandbox subscription — stays with the owner.
</frozen-after-approval>
