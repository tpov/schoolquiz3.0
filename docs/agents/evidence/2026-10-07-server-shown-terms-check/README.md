# Server shown-terms check (story 2.8)

- Result: PASS on its own scope. The receiver-level contract proof (8 cases of
  `submitMutation` against the live demo backend) is green; the full functions gate is
  90 of 92 suites with the only 2 red suites attributed to pre-existing branch drift
  (identical on the clean base, see "Pre-existing failures" below).
- Date: 2026-10-07.
- Story: `epic-ordinary-delivery` 2.8 `server-shown-terms-check` (covers CAP-16, server half
  of AD-5); done-when: a new mutation has its relevant shown terms checked by the server, and
  changed payable terms are rejected without fresh player consent — proof: contract Node tests
  of the receiver (`submitMutation` / `MUTATION_HANDLERS` registry) against the demo backend.
- Unblock: the demo backend was raised by the owner's 06.10 decision (container
  `schoolquiz-demo-firebase-20261004`, Firestore :18090 / Auth :19090, project
  `demo-schoolquiz-mvp`), and the owner dispatched server-side work — the night scope rule
  that kept Node contract tests out of "code+JVM" no longer applies to this story.

## What changed and why

- `functions/mutation-terms.js` — NEW pure module, the server twin of the client half (2.4):
  `payableTermsProjection(constants)` picks only the payable fields (version, five
  `activityPrices`, both charge rules) from the parsed `configs/economy` table — non-payable
  knobs (`clockSkewToleranceMs`, `auditEnabled`) cannot leak into the comparison;
  `readCapturedTerms(envelope)` reads the new optional `capturedTerms` envelope field
  (object or JSON string, same idiom as `parseMutationPayload`); a consent that does not name
  a complete payable projection is invalid, never "partially checked"; `termsVerdict(captured,
  current)` compares payable fields by name — the version number alone is NOT a change
  (same semantics as the client `TermsConsentGate`: a non-payable knob bump that lifts the
  version must not re-ask consent over unchanged prices); `TERMS_CONSENT_DETAIL =
  "terms-consent-required"` — the stable detail mark the client branch will be named by.
- `functions/index.js` — the receiver wiring:
  - `MUTATION_TERMS_OPERATIONS = {lesson_runner.SUBMIT_ATTEMPT}` — the registry of operations
    whose verdict reads the consented table, next to `MUTATION_VERSION_SOURCES`. The criterion
    and the exclusions are documented in place: `lesson.UNLOCK` is priced from the lesson's own
    allocated time (not from the table), rating/READY_ACK/arena/review/shelf/cancel are unpaid,
    gift-box open is a credit not a charge. Today exactly one queued operation is priced from
    the consented table: the attempt's `activityPrice` plus the charge-rule ceiling applied at
    settlement.
  - Envelope shape of `capturedTerms` is validated before the idempotency key is reserved
    (malformed consent cannot burn the key — same reason as the `expectedVersion` shape check).
  - The semantic check runs after the key reservation and before the handler, mirroring the
    version-conflict pattern: missing consent → `failed-precondition` with
    `{code: "terms-consent-required"}` (final refusal until the player consents to the current
    terms; the key reservation is released); changed payable fields → same code plus
    `change` naming exactly which prices and charge rules moved (`{from, to}` per field).
    Replays of a known key return the stored result BEFORE any terms check — a replay is not a
    new operation (AD-6), consent is never re-asked on it.
  - Accepted terms revision is recorded in the operation (AD-5): the reservation record (and,
    through the spread, the completion record) carries `termsVersion` of the consent the
    mutation was accepted under; operations without relevant terms carry no field.
  - `capturedTerms` deliberately does NOT enter the request fingerprint: the re-consent retry
    must be allowed under the same mutationId (the intent/body did not change, only the
    consent metadata), and the body-fingerprint conflict check stays about the body.
  - `admin.initializeApp()` is guarded (`!admin.apps || admin.apps.length === 0`) so the
    receiver contract test can pre-initialize the app with the emulator projectId; suites that
    inject a firebase-admin stub (no `apps` field) keep the unconditional call.
- `functions/_firestore-stub.js` — the stub transaction gained `delete` (tombstoned in
  `pending`, applied at commit): the real transaction API has it, `releaseReservation` uses
  it, and the new release path was the first to reach it in stub-driven suites. Side effect:
  one previously red case of `lesson-ready-ack-handler.test.js` (4/8 → 3/8 failures) got past
  its `transaction.delete` and proceeds to its real assertion — see attribution below.
- `functions/attempt-handler.test.js` — the two cases that drive `submitMutation` with a paid
  operation now carry `capturedTerms: freshTerms()` (built from the world's `configs/economy`
  by the same `payableTermsProjection(readEconomyConstants(...))` the receiver uses). They
  encode the queue/receipt contract, not the terms contract; under the new server rule a paid
  mutation without consent is refused before the handler, which is exactly what the new
  receiver suite asserts elsewhere.
- `functions/mutation-terms.test.js` — NEW, 13 pure cases: projection picks only payable
  fields and survives a degraded table; verbatim consent is sufficient; version bump alone is
  not a change; price/rule/ladder-rung changes are named by kind; no consent at all is
  insufficient with `change: null`; completeness enforcement (missing price, missing rule
  field, negative price, zero divisor, non-object); JSON-string consent rides like a queued
  envelope; extra envelope fields are not consent; the detail mark is pinned.

## Contract tests of the receiver (the done-when proof)

`functions/submit-mutation-terms.test.js` — NEW, runs `submitMutation.run(...)`
(firebase-functions v2 test entry) with `admin` initialized against the live demo backend
Firestore emulator, project `demo-schoolquiz-mvp`. Without `FIRESTORE_EMULATOR_HOST` the
suite skips, so `npm test` stays green on machines without the backend; the wide run executes
it against the demo container. Eight cases:

1. Paid operation without consent → `failed-precondition`, details `{code:
   terms-consent-required}`, no `change` (first consent), no key left behind (reservation
   released).
2. Malformed `capturedTerms` (not an object) → `invalid-argument` at the envelope-shape phase,
   no key touched.
3. Stale price in consent → `failed-precondition` with `change` named; key released.
4. Stale charge rule in consent → same refusal; key released.
5. Version-only bump with identical payable fields → gate passes; the payload's own
   `invalid-argument` from the intake proves the mutation reached the handler, and the key is
   released by the body failure.
6. Fresh consent captured verbatim from the live table → gate passes (same cross-the-gate
   proof).
7. Unpaid operation (`lesson_runner.SUBMIT_RATING`) without consent → gate skips entirely
   (registry, not a blanket rule).
8. Replay of a seeded completed key with stale consent → `{replayed: true, result}` returned
   BEFORE the terms check — replays never re-ask consent.

Cleanup deletes the seeded `profiles/it_terms_contract_probe` and every `mutation_keys` doc
with the probe uid (verified after the runs: probe profile absent, 0 probe keys, the demo's
own 23 unrelated keys untouched; `configs/economy` is only read — and is absent in the demo,
so the table degrades to the shared defaults file, the same values the tests captured).

## Gate history (2 attempts)

- Attempt 1: red — my own test built the stale-cases by shallow-mutating a shared projection,
  poisoning later cases; fixed by deep-copying the captured terms per case. Test-only.
- Attempt 2: green. Full `npm test` (92 suites) run twice: without the emulator env —
  90 of 92 passed, and with the demo backend env — 90 of 92 passed (one run flaked an extra
  pure suite once, green on immediate re-run and in the recorded run). Raw logs:
  `gate-no-env.log`, `gate-demo-backend.log`. Exit code 1 comes solely from the two
  pre-existing suites below.

## Pre-existing failures (attribution, not night-of 2.8)

- `arena-lifecycle-views-handler.test.js` — 1 of 6 cases: pinned sha256 of the callable
  surface does not match (`c6582f3a…` expected vs `9f17310c…` actual).
- `lesson-ready-ack-handler.test.js` — 3 of 8 cases (window deadline/clock assertions).
- Both fail identically with my changes stashed (verified on this tree against
  `functions/index.js` + `_firestore-stub.js` + the two test files reverted). Neither touches
  the terms path: the first is a pinned-hash drift from an earlier commit on this branch, the
  second is the 2.6-era window logic. Proposed follow-up story: attribute and fix both in a
  small "functions contract drift" ticket (candidate 2.12), together with the client-side join
  below.

## Deliberate consequences and the open join

- The rule is strict: a paid operation without consent is refused. This is the AD-5 reading —
  a check that no-ops on absence is not a check. Today nothing in the repo SENDS
  `capturedTerms` yet: the client half (2.4) built the gate/store/codec but does not attach
  consent to the outbox envelope. The natural next client story (candidate 2.12 "client terms
  attach", or a section of 2.7 preparation): `FirebaseMutationTransport` attaches the captured
  `PayableTerms` for `MUTATION_TERMS_OPERATIONS` operations, and the client maps the
  `terms-consent-required` detail to the consent flow instead of quarantine. Until that
  lands, a candidate built from this branch would be refused on attempt submits against a
  backend running this code — the contract tests pin the server half, the client join is the
  remaining half of CAP-16's loop.
- The demo backend runs auth+firestore emulators only (no functions emulator), so nothing
  live serves this code yet; the receiver tests load the local tree and point it at the
  demo's Firestore — that is the "against demo-backend" proof the ticket names.
