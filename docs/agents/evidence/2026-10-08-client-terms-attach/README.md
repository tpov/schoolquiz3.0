# Client terms attach (story 2.12)

- Result: PASS on its own scope. The client half of the 2.8 join is closed: the transport
  attaches `capturedTerms` to the operations of the consent registry, the server's
  `terms-consent-required` refusal parks the record for a player decision instead of
  quarantining it (and instead of the old permanent-refusal → `Unknown` → eternal-retry loop),
  and the player's acceptance returns the parked records to the send queue under the same
  mutation keys. Proof: module JVM gates listed below.
- Date: 2026-10-08.
- Story: `epic-ordinary-delivery` 2.12 `client-terms-attach` (covers CAP-16) — chartered by the
  owner's 2026-10-08 decision (owner-decisions-2026-10-08.md, point 2) to close the open join
  left by 2.8 (evidence 2026-10-07, «Deliberate consequences and the open join»): a candidate
  built from the branch was refused on attempt submits against a backend running 2.8 code.

## What changed and why

- `shared/core/outbox/OutboxOperations.kt` — `termsConsentOperations = {lesson_runner
  .SUBMIT_ATTEMPT}`, the client twin of the receiver's `MUTATION_TERMS_OPERATIONS` (same
  criterion and exclusions: `lesson.UNLOCK` is priced from the lesson's own allocated time).
  Name agreement both ways is guarded by the extended `OutboxOperationsRegistryTest`, which now
  also parses `MUTATION_TERMS_OPERATIONS` out of `functions/index.js` and asserts set equality
  with the client registry — the same textual-guard idiom the file already uses for
  `MUTATION_HANDLERS`.
- `platform/firebase/.../FirebaseMutationTransport.kt` — the envelope builder is extracted into
  a pure `mutationEnvelope(record, capturedTermsJson)`; a consent that exists for the operation
  rides as the `capturedTerms` field (JSON string — the server reads both forms with the same
  idiom as `parseMutationPayload`); a consent that does not exist is honest absence (no field),
  never a faked value. The constructor takes a `CapturedTermsJson` fun interface — the transport
  does not learn what consent is (AD-7); it only carries what it is given.
- `platform/firebase/.../EconomyCapturedTermsJson.kt` — NEW, the adapter: filters by the consent
  registry and encodes the captured `PayableTerms` through the existing `PayableTermsCodec` (the
  same codec the consent store writes with, so what is sent is byte-wise what the player agreed
  to). Wired in `FirebaseModule` into the single `MutationTransport`.
- `shared/core/network/SyncError.kt` — the sixth branch, `TermsConsentRequired`, disposition
  CONFLICT: not quarantine (AD-28 would roll back the written attempt — the exact damage the
  join produced) and not retry (without fresh consent the retry returns the same refusal). Like
  every branch it declares its disposition; `change` is deliberately not carried — the client
  owns the terms tables and the consent flow re-derives the named diff itself.
- `platform/firebase/.../CallableErrors.kt` — `TERMS_CONSENT_DETAIL = "terms-consent-required"`
  (twin of the server constant) and the FAILED_PRECONDITION reading: details-object with the
  mark → the consent branch; the `precondition-pending` string branch and the refusal fallback
  are unchanged. `mutationErrorForCode`'s permanent-receipt protection does not swallow the new
  branch (it only rewrites `Refused`) — pinned by a new `PermanentMutationErrorsTest` case.
- Outbox policy/engine need no change: CONFLICT disposition already parks without burning
  attempts or pause (`OutboxPolicy.onFailure`), the record stays non-terminal, and the attempt
  sync mark reads CONFLICT as NOT_SENT — a candidate now shows «не отправлено» instead of
  spinning forever. The branch's parking arithmetic is pinned by `OutboxPolicyTermsConsentTest`.
- `shared/core/outbox/OutboxConsentRequeue.kt` — NEW narrow interface (interface segregation:
  `OutboxStore` is untouched, existing fakes unaffected): return CONFLICT records of the consent
  registry to WAITING, same keys, same payloads, attempts untouched, last error cleared. The
  owner is deliberately not a parameter: consent is not auth-scoped by contract, so the
  consenting account releases the wait for every owner's records; owner isolation is preserved
  because each record still sends only under its own uid (AD-8).
- `shared/core/persistence` — the Room half: one `UPDATE` on `OutboxDao` with the operation
  filter living in the SQL (version conflicts must NOT be requeued — their resolution is a new
  mutation, AD-24), implemented by `RoomOutboxStore` and bound in the app's `SyncModule` next
  to the other concrete-type rebinds.
- `shared/feature/economy` — the moment of resolution is the acceptance: `acceptCurrentTerms()`
  is now a suspend function (it writes the consent AND releases the queue; the call must wait
  for the decision to be durably next to the released records) and heals the parked records in
  the same step. Production callers of the consent flow do not exist yet (the consent dialog is
  future UX work), so the loop closes for whoever drives the gate next; until then a refused
  candidate shows the parked state honestly.
- `RoomSyncRunTraceStore` + settings screen — the branch name round-trips the schema
  (`TERMS_CONSENT_REQUIRED`, exhaustive `when` both ways) and the settings screen names it
  («Ждёт согласия с новыми условиями» / EN / UK) instead of falling into a generic label.

## Gate (recorded run; sgw-wrapped)

`sgw ./gradlew :shared:core:network:jvmTest :shared:core:outbox:test :shared:core:persistence:test
:shared:feature:economy:data:jvmTest :platform:firebase:test`

- Attempt 1: red — my own policy test passed the kotlin.test message first (compile error,
  test-only).
- Attempt 2: red — my persistence test helper called a suspend `enqueue` from a non-suspend
  helper (compile error, test-only).
- Attempt 3: red — `acceptCurrentTerms` was declared non-suspend while it must await the
  requeue; the interface signature changed (no production callers yet).
- Attempt 4: red — missing import in the gate test (test-only).
- Attempt 5: red — `BoxOpeningMarkerPersistenceTest` (attributed below); fixed test-only.
- Attempts 6–7: red — JUnit assertion overload/argument-order slips in my new envelope test
  (test-only).
- Attempt 8 (recorded): all five modules green EXCEPT two `platform:firebase` tests of the
  pre-existing stale-fixture family attributed below — the same shape the 2.8 gate recorded
  (90 of 92 with attribution). Everything this story touches is green: network, outbox
  (registry twin + policy), persistence (requeue SQL + trace round-trip + the fixed marker
  count), economy data (gate heal), firebase (envelope, error table, permanent-refusal pin,
  Koin wiring with the new dependency).

## Pre-existing failures found by the gate (attributed, not 2.12's)

- `BoxOpeningMarkerPersistenceTest.bothPermanentOperationsRespectOldRowsBackoffAndMarkers
  NeverBecomeDue` — expected `<2>` due rows, was `<3>`. The test iterates
  `OutboxOperations.permanentReceiptOperations` but pinned the expected count as a literal:
  it was written 2026-09-06 (`15a402d9`) when the registry had two members, and story 2.6
  (`1b207cc2`, 06.10) added `READY_ACK` as the third. The suite had not been run since — 2.6's
  own gate covered outbox + lesson-runner data, 2.8's gate was the functions one. Fix is
  test-only: the expected count now derives from the registry size.
- `FirebaseArenaTicketRemoteDataSourceTest.fixtureHashesAndCallableNamesAreBoundToActual
  ServerSources` and `FirebaseArenaLessonCatalogRemoteDataSourceTest.generatedPublicContract
  AndCallableNameRemainBoundToActualProducer` — the fixture JSONs
  (`arena-ticket-client-fixtures.json` from `5d006688`, 2026-09-07) pin the SHA-256 of the
  producers (`arena-tickets.js`, `arena-lesson-catalog.js`, …), and `34fe664b` (2026-09-12,
  «share eligible lesson universe») changed the producers without regenerating the captures.
  NOT fixed here on purpose: the pins guard captured payload fixtures, and the capture tooling
  is not in the repository (`producer` is a prose note, the 09-07 session tooling stayed
  session-local) — editing the recorded hashes by hand would launder a real producer change
  without regenerated payloads. Owner follow-up: regenerate the two fixture JSONs with the
  capture procedure and re-pin. The Koin wiring failures my transport change caused in
  `FirebaseArenaKoinWiringTest` are a different matter — those were 2.12's to fix (consent-gate
  fake added to the test graphs).

## Deliberate consequences and what stays open

- The refusal branch is reachable only when the local table already differs from the captured
  consent; in the common first-consent case the transport now simply passes the check the
  candidate used to fail.
- The consent dialog (who calls `verdict()`/`acceptCurrentTerms()` in the UI) is deliberately
  out of scope: it belongs to the UX line and the owner-confirmed acceptance stories (2.7 and
  final acceptance), not to the transport join the owner chartered here.
- The demo backend runs 2.8 code without a functions emulator, so nothing live serves this
  client yet; the client half is pinned by JVM tests against the registry constants the
  registry guard keeps in sync with `functions/index.js`.
