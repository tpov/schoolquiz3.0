---
title: 'M1.2 — The client consumes a purchase only after the server confirms it'
type: 'feature'
created: '2026-09-02'
status: 'in-review'
baseline_commit: 'a691e4420a76100ba11ed5cb7dea04b071874459'
review_loop_iteration: 0
story_key: '1-2-клиент-гасит-покупку-только-после-подтверждения'
sprint_status_file: '_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context-monetisation.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m1-1-server-purchase-verification.md'
  - '{project-root}/.claude/rules/clean-architecture.md'
  - '{project-root}/.claude/rules/use-cases.md'
  - '{project-root}/.claude/rules/testing.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The device can buy a gold pack (`PlayBillingRepository`) and the server can verify and credit it (`verifyPurchase`, story M1.1), but nothing connects the two: no code presents the token to the server, consumes the purchase afterwards, or re-presents an unconsumed purchase after the process died between paying and crediting. A paying player today would get nothing.

**Approach:** One settlement path used twice. A use case takes a Play purchase, asks the server to verify it, and only on `CREDITED` consumes it in Play and refreshes the balance from the server. The buy flow calls it right after Play reports a purchase; a process-long settler calls it for every unconsumed purchase Play re-delivers — at start, on account change, and when the network comes back. Offline, the buy flow refuses before Play is even opened. The purchase flow tags the buyer with `sha256(uid)` so the server can bind the token to the account.

## Boundaries & Constraints

**Always:** Order is verify → consume → refresh; never consume without `CREDITED`, never credit locally — after `CREDITED` the balance is pulled from the server (`UserStatsRepository.refreshProfile()`), not computed from `goldGranted` (SYNC-AD-25). Monetary calls are direct and synchronous, never enqueued in the mutation outbox (ADM-5). Offline is refused with `SyncFailure(SyncError.NoNetwork)` before `launchBillingFlow`. The buyer tag is lowercase hex sha256 of the UTF-8 uid — byte-identical to the server's `accountIdFor`. Refusals reach the caller as a code (`PurchaseRefusalCode` from `details.reasonCode`), never as English text to match on. Domain stays pure Kotlin: the verifier and the balance refresher are domain ports; Play, Firebase and `UserStatsRepository` stay behind them. Auth-scoped flows re-subscribe on uid change (`flatMapLatest`); the settler holds no uid in a field. Tests use fakes and controlled dispatchers; no Turbine.

**Ask First:** Any change to `functions/`, `firestore.rules`, `shared/core/network`, `shared/core/sync`, or another feature's module (app-shell, profile). Adding a Gradle dependency other than `:shared:core:network` to `:shared:feature:economy:domain` and `:shared:feature:economy:data`.

**Never:** Touch other sessions' uncommitted edits. Add shop UI or pack tiles (story 1.4). Emit analytics (story 1.5). Store or log the raw purchase token beyond what Play already holds. Use `Channel` for the settler's events.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Buy, happy path | online, uid set; Play → `Purchased(p)`; server → `CREDITED(goldGranted)` | `verify(p.token, sku)` once, `consume(p.token)` once, `refresh()` once; `Result.success(Credited(goldGranted))` | N/A |
| Died after paying | next start: `observeUnsettledPurchases()` emits `[p]` | settler settles `p` by the same path; consumed; balance refreshed | N/A |
| Server replays | token already settled → `CREDITED` again | consume, refresh, `Credited` — identical to first time | N/A |
| Server pending | `PENDING` | no consume; `Pending`; purchase stays in Play's queue | not an error |
| Play pending | Play → `Pending` | verifier never called; `Pending` | not an error |
| Offline before buying | `isOnline()` false | `launchBillingFlow` never called | `failure(SyncFailure(NoNetwork))` |
| Offline at verify | purchase done, verifier throws `SyncFailure(NoNetwork)` | no consume; purchase remains unsettled; settler retries when online | `failure(SyncFailure(NoNetwork))` |
| Server refuses | `PurchaseRefusedException(code)` | no consume; `success(Refused(code))` | code preserved, e.g. `SKU_NOT_SOLD` |
| Consume fails | `CREDITED`, `consume` → failure | `refresh()` still runs; `Credited` returned; token re-presents later and replays | logged, not surfaced |
| No account | uid null | Play never opened | `failure(IllegalStateException)` |
| Account switch | uid A → B while settler runs | A's subscription cancelled; B's unsettled purchases refreshed and settled | N/A |
| Network returns | `observeOnline()` false → true | `refreshUnsettledPurchases()` then settle what is unsettled | N/A |
| Play already owned | Play → `AlreadyOwned(p)` | `p != null` → settle it; `p == null` → `refreshUnsettledPurchases()` and fail with a named reason | `failure(IllegalStateException)` |
| Buyer tag | uid `"abc"` | `setObfuscatedAccountId("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")` | N/A |

</frozen-after-approval>

## Code Map

- `shared/feature/economy/domain/.../model/PurchaseVerification.kt` -- NEW sealed `Credited(productId, goldGranted, settlementId)` / `Pending(productId)`; `enum PurchaseRefusalCode` (the eleven server codes + `UNKNOWN`, `fromWire(String?)`); `class PurchaseRefusedException(code, message)`.
- `shared/feature/economy/domain/.../model/PurchaseOutcome.kt` -- NEW sealed `Credited(goldGranted)` / `Pending` / `Cancelled` / `Refused(code)`.
- `shared/feature/economy/domain/.../repository/PurchaseVerifier.kt` -- NEW port `suspend fun verify(purchaseToken: String, productId: StoreProductId): PurchaseVerification` (throws `PurchaseRefusedException` or `SyncFailure`).
- `shared/feature/economy/domain/.../repository/ServerBalanceRefresher.kt` -- NEW port `suspend fun refresh(): Result<Unit>`.
- `shared/feature/economy/domain/.../repository/BillingRepository.kt` -- `purchase(productId, buyerId: String)`; add `suspend fun refreshUnsettledPurchases()`. Existing KDoc explains the queue-lives-in-Play model — keep it.
- `shared/feature/economy/domain/.../use_case/SettlePurchaseUseCase.kt` -- NEW: verify → consume → refresh, per matrix. **Owns the one-settlement-per-token guard** (a `Mutex`-guarded map token → `CompletableDeferred`), because both entry points are live at the same moment: Play's listener resolves the buy flow *and* refreshes the queue the settler watches, so a guard in either caller alone would let every ordinary purchase run two verifications and race two consumes. A second caller awaits the first's answer; the claim is released under `NonCancellable`, or a cancelled settlement would hold its token for the life of the process. Bound as a `single`, never a `factory` — a guard handed out fresh per caller guards nothing. Both a returned and a thrown consume failure are survivable, since the store adapter re-reads its own queue inside its success path. `use_case/BuyGoldPackUseCase.kt` -- NEW: uid via `currentUidFlow().first()`, `NetworkMonitor.isOnline()` gate, `billing.purchase(productId, buyerTag(uid))`, then `SettlePurchaseUseCase`. The tag is computed through an injected port rather than inside the store adapter, so the value that actually reaches the store is visible to a test — the adapter's flow parameters expose no getter, and a tag that quietly stopped being hashed would refuse every real purchase with every suite green. Also compares the delivered product with the requested one: the store's callback is process-wide and may hand back an older unconsumed purchase, which still deserves settling but must not be reported as this purchase's outcome. Pattern: `operator fun invoke`, `Result<T>`, `runCatchingCancellable` as in `EconomyRepositoryImpl.kt:98`.
- `shared/feature/economy/domain/build.gradle.kts` -- add `implementation(project(":shared:core:network"))` (first feature to depend on it; `NetworkMonitor` is a core contract). `di/EconomyDomainModule.kt` -- becomes `fun economyDomainModule(currentUidFlow: () -> Flow<String?> = { flowOf(null) })` mirroring `economyDataModule`; bind both use cases.
- `shared/feature/economy/data/.../UnsettledPurchaseSettler.kt` -- NEW `start(): Job`; nested `collectLatest` over uid and connectivity — **not** `flatMapLatest`, because only `collectLatest` cancels the settlements already running for the previous account rather than just the subscription that found them. Keeps its own retry clock with bounded backoff and a cap on consecutive failed rounds: the store's queue is a conflating flow of a list, so re-reading it unchanged emits nothing and it cannot be the clock. Remembers refused tokens per account, so an answer that is final by definition stops being re-presented on every start, switch and reconnection. Contains throws from the store adapter, whose process can be replaced underneath it. Holds no token set of its own — the use case owns it. `data/build.gradle.kts` -- add `:shared:core:network`. `di/EconomyDataModule.kt` -- `single { UnsettledPurchaseSettler(...) }` with `scope = get<CoroutineScope>()` (app scope is bound in `AppApplication.appScopeModule`).
- `platform/billing/.../PlayBillingRepository.kt` -- `purchase(productId, buyerId)` passes the already-hashed tag straight to `BillingFlowParams.setObfuscatedAccountId`; `refreshUnsettledPurchases(): Result<Unit>` = `refreshUnsettled()`, which now reports failure instead of returning silently — a caller that cannot tell "nothing to settle" from "could not ask" loses the recovery path for a whole session on a cold start right after a Play Store update — and counts the unconsumed purchases whose SKU this build does not know rather than dropping them in silence. `loadProducts` names *which* SKUs are missing from the console (`productsOrFailure`, tested), instead of only failing when every one of them is. `platform/billing/.../BuyerTag.kt` -- NEW pure `of(uid): String` via `java.security.MessageDigest("SHA-256")`, lowercase hex. `platform/billing/src/test/.../BuyerTagTest.kt` -- NEW (module has no tests yet; JUnit4 per project).
- `platform/firebase/.../economy/FirebasePurchaseVerifier.kt` -- NEW, modelled on `FirebaseEconomyRemoteDataSource.kt` (online check, `withAppTimeout()`, `IOException → NoNetwork`); callable `verifyPurchase`, payload `{purchaseToken, sku}`; response `status` CREDITED/PENDING; `FirebaseFunctionsException.details` as `Map` with `reasonCode` → `PurchaseRefusedException`, otherwise `SyncFailure(e.toSyncError())`. Keep the mapping in `internal fun`s taking plain values (`purchaseVerificationFrom(data, productId)`, `purchaseRefusalFrom(details, message)`) — SDK types do not load in JVM tests (see `CallableErrors.kt` header). `platform/firebase/src/test/.../economy/PurchaseVerificationMappingTest.kt` -- NEW. `di/FirebaseModule.kt:69` -- `single<PurchaseVerifier> { FirebasePurchaseVerifier(functions = get(), networkMonitor = get()) }`.
- `apps/android-next/.../di/EconomyGlueModule.kt` -- NEW `UserStatsServerBalanceRefresher(userStats: UserStatsRepository) : ServerBalanceRefresher { refresh() = userStats.refreshProfile() }` + `single<ServerBalanceRefresher>`; the composition root is where app-shell and economy meet (no new feature→feature dependency). `AppApplication.kt` -- `economyDomainModule(currentUidFlow, buyerTag = BuyerTag::of, log = …)`, register the glue module, and `startPurchaseSettlement()` next to `registerDeviceToken()`. `buyerTag` has no default on purpose: a default is exactly how a silently unhashed tag would reach production.
- `apps/android-next/src/test/.../KoinModuleWiringTest.kt` -- adjust for the new `economyDomainModule(...)` signature; stub `PurchaseVerifier`, `BillingRepository`, `ServerBalanceRefresher` where the test omits `firebaseModule`/`billingModule`.
- Tests (fakes in `shared/feature/economy/domain/src/commonTest/.../fake/`): `FakeBillingRepository` (scripted `purchase` outcome, recorded `consume`/`refresh` calls, `MutableStateFlow` of unsettled), `FakePurchaseVerifier` (scripted per token), `FakeNetworkMonitor` (`MutableStateFlow<Boolean>`), `FakeServerBalanceRefresher`. `BuyGoldPackUseCaseTest`, `SettlePurchaseUseCaseTest`, `UnsettledPurchaseSettlerTest` (data commonTest, `StandardTestDispatcher`).
- Read-only: `functions/**`, `firestore.rules`, `shared/core/network/**`, `android/feature/economy/presentation/**`.

## Tasks & Acceptance

**Execution:**
- [ ] `shared/feature/economy/domain` -- models, ports, `BillingRepository` changes, two use cases, Gradle dep, DI function -- the contract everything else implements.
- [ ] `shared/feature/economy/domain/src/commonTest` -- fakes + use-case tests covering every matrix row that ends in the use case.
- [ ] `shared/feature/economy/data` -- `UnsettledPurchaseSettler` + DI + Gradle dep; `UnsettledPurchaseSettlerTest` (died-after-paying, account switch, network returns, no double settlement).
- [ ] `platform/billing` -- buyer tag, `refreshUnsettledPurchases`, `BuyerTagTest`.
- [ ] `platform/firebase` -- `FirebasePurchaseVerifier` + mapping test + DI binding.
- [ ] `apps/android-next` -- glue module, `AppApplication` wiring and settler start, `KoinModuleWiringTest` update.
- [ ] `_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml` -- `1-2-…` → `in-progress`, then `review`.

**Acceptance Criteria:**
- Given the server confirmed crediting, when the client receives `CREDITED`, then the purchase is consumed in Play and the balance is refreshed from the server.
- Given the process was killed between paying and crediting, when the app next starts with an account, then the unconsumed purchase is presented again and credited.
- Given the device is offline, when a purchase is requested, then the call is refused with a named reason and nothing is queued.
- Given the server refuses with a reason code, when the client receives it, then nothing is consumed and the code reaches the caller intact.

## Spec Change Log

### 2026-09-03 — the retry mechanism named in the Code Map did not exist

**Finding:** three review lenses independently showed that "a failed settlement leaves the token for
the next emission" cannot work. The store's queue is a conflating flow of a list of data classes, so
re-reading it when nothing changed produces an equal list and therefore no emission at all: on a
stable, online device a failed settlement was never attempted again until the process restarted. The
two tests that claimed to prove the retry passed only because they re-delivered a purchase with a
different `orderId` — a value the real adapter cannot produce.

**Amended:** the Code Map now specifies a retry clock the settler owns — bounded backoff, a cap on
consecutive failed rounds, and a wait on a real queue change when there is nothing to retry. The
same review showed the guard against settling one token twice sat in the settler while the buy flow
walked past it on every ordinary purchase; the Code Map now puts that guard in the settlement use
case, where both callers meet. The store fakes were changed so a purchase becomes visible *because*
a refresh ran, rather than because a test published it directly.

**Known-bad state avoided:** a paying player whose first settlement attempt met a slow server would
never be credited until they restarted the app — and every ordinary purchase would verify twice and
race two consumes — with a green suite asserting the opposite.

**KEEP:** the single settlement path shared by the buy flow and the settler; recovery must not be a
second implementation. The order verify → consume → refresh. Refusal as `success(Refused(code))` and
a lost answer as `Result.failure`, which is what lets the settler tell "stop asking" from "ask
again".

## Design Notes

`AlreadyOwned` is what Play answers for a consumable bought and never consumed; settling it is the recovery, not an error. Consume failure after `CREDITED` is deliberately non-fatal: Play keeps re-delivering the purchase, and the server's replay answers `CREDITED` again until consume succeeds — that loop is the durability, so it must not be short-circuited. The settler is started from the Application (headless starts included), like the device-token registrar.

A refused token is remembered but never consumed: whether the client may hand a dead token back to the store is a product decision, and the retry loop is stopped either way. The refusal memory is per account and in memory only — the cost of forgetting it is one wasted call after a restart, and persisting it would mean a second store to keep in step with Play's.

## Verification

**Commands:**
- `./gradlew :shared:feature:economy:domain:allTests :shared:feature:economy:data:allTests :platform:billing:testDebugUnitTest :platform:firebase:testDebugUnitTest --no-configuration-cache` -- expected: all green, including the new suites.
- `./gradlew :apps:android-next:testDebugUnitTest --tests "*KoinModuleWiringTest*" --no-configuration-cache` -- expected: green.
- `./gradlew :shared:feature:economy:domain:ktlintCheck :shared:feature:economy:data:ktlintCheck :platform:billing:ktlintCheck :platform:firebase:ktlintCheck :apps:android-next:ktlintCheck --no-configuration-cache` -- expected: green.
- `./gradlew :apps:android-next:assembleDebug --no-configuration-cache` -- expected: builds. (Full `ciCheck` is red from other workstreams' uncommitted files — verify the modules above, not the whole gate.)
