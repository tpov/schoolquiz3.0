# TZ06D — resumable finalization of the full Arena season

## Status

**DESIGN ONLY — NOT IMPLEMENTED — NOT ACCEPTED.**

This is an architectural checkpoint, not evidence that TZ06D is complete.  It records the
implementation direction selected within the owner's plan. The examined preimage was
`8fc7af45`; the relevant Arena service/policy/callable code is unchanged through
`c7742584`. It neither changes the product result nor authorizes a different product rule.

## Verified problem and retained contract

`functions/arena-service.js` currently finalizes at lines 251–282 by reading the entire
`arena_seasons/{quarter}/members` and `arena_season_votes/{quarter}/votes` collections inside
one transaction, then calls `policy.rankSeasonLessons`.

That cannot become safe merely by adding a cursor.  `functions/arena-policy.js:129–156,226–255`
selects the latest **eligible** rating globally per `(lessonId, uid)` after completion,
self-rating and UTC-window checks.  A page sum can count a replaced vote twice or let an
ineligible later vote erase an eligible prior vote.

The retained product result is: one UTC-quarter Arena winner; 20 distinct eligible completed
raters; exact weighted score; ties by rater count, earlier publication, then stable lesson ID;
and at most one 10-BOX season award.  Frozen membership survives later shelf movement.

Existing finalized documents and their old `rankingDigest` values are immutable historical
records.  New calculation output uses a separately named, explicitly versioned
`calculationManifestDigest`; it must never rename, synthesize, or reinterpret `rankingDigest`.

## Public request and receipt contract

The existing `finalizeArenaSeason` callable remains the entry point.  Its request remains
`{mutationId, quarterAtMs}`; the server canonicalizes the quarter and takes UID only from Auth.

For a new, unfinished finalization it returns a **typed response**, not an `arena_mutations`
receipt:

```json
{
  "schemaVersion": 2,
  "operation": "FINALIZE_SEASON",
  "status": "PENDING",
  "quarterId": "2026-Q1",
  "finalizationId": "deterministic-server-id",
  "requestedAtMs": 0
}
```

The eventual document at `arena_mutations/{mutationDocumentId(uid, mutationId)}` is the normal
immutable final receipt: existing receipt shape, `status: "APPLIED"`, and the exact final result.
Old completed receipt replays return unchanged.  A retry of the original pending operation gets
the same typed pending response until publication, then gets that immutable final receipt.

`getArenaSeasonFinalizationStatus({quarterAtMs})` is a new curator-authorized, bounded read.  It
reads only the season and its job head and returns an allowlisted state (`PENDING`, `FINALIZED`,
or `REQUIRES_REVIEW`), public quarter/finalization identifiers, phase/update time, and a final
result only after publication.  It exposes no rows, cursors, lease tokens, source payloads, or
worker controls.

There is no tracked Kotlin/JS caller of `finalizeArenaSeason`; `functions/index.js:433–444` is a
transparent callable wrapper.  Therefore this is additive within the checked source.  Unknown
external clients cannot retain the former synchronous-success expectation under any resumable
implementation; an additive `...V2` callable is needed only if that external wire guarantee is
required separately.

## Mutation reservation seam (required)

Before a final receipt exists, reserve the mutation ID at the private document
`arena_mutation_reservations/{mutationDocumentId(uid, mutationId)}`.  It binds immutable core
fields `{uid, mutationId, operation, payloadHash, quarterId, finalizationId, requestDigest}` and
has operational state `PENDING` then `COMPLETED`.

All three Arena mutation operations — `RATE_LESSON`, `TRANSITION_LESSON`, and
`FINALIZE_SEASON` — must use the same receipt-plus-reservation lookup before writes:

1. A final receipt is validated and replayed exactly as today.
2. A same-identity pending finalization returns typed `PENDING` only to its finalize request.
3. A pending different operation/payload with the same `(uid, mutationId)` fails
   `MUTATION_REUSE`; rate/transition must not create a competing receipt.
4. A second mutation ID while the same season is already finalizing receives an ephemeral
   pending/status result, not another reservation which would need a second final receipt.

At publication, the worker atomically creates the final receipt for the initiating request and
marks its reservation completed.  The final receipt remains immutable; reservations are private
operational records and must not be deleted as a way to permit mutation-ID reuse.

## Fence, job, provenance, and continuation

The start transaction checks curator authority and server time, then atomically changes the
canonical season from `OPEN` (or absent) to `FINALIZING`.  It records a deterministic
`finalizationId`, a versioned fence, the initiating reservation binding, and creates:

`arena_seasons/{quarterId}/finalizations/{finalizationId}`.

Current rating/placement writers already read the season and create membership/vote rows only
when it is absent or `OPEN` (`arena-service.js:163–184,226–244`).  Their transaction retry sees
`FINALIZING`, so the fence freezes all normal server-owned writers before scanning.

The job head is sealed with `checkpointDigest` and contains at least:

```text
schemaVersion, finalizationId, identity, identityHash,
policyVersion, calculationVersion, phase, revision,
lease:{token,expiresAtMs}|null,
memberCursor/memberCount/memberChainHash,
voteCursor/voteCount/voteChainHash,
selectionCursor/selectionCount/selectionChainHash,
aggregateCursor/aggregateCount/aggregateChainHash,
winnerCursor/eligibleLessons/candidateChainHash,
calculationManifestDigest|null
```

`identity` pins the canonical quarter window, source paths, finalization ID, policy/calculation
versions, and initiating reservation digest.  Every hash has a specified canonical JSON input;
the implementation must fail closed on a bad version, counter, cursor, hash, row, or fence.

Phases are strictly ordered:

1. `SCAN_MEMBERS` — document-ID bounded pages copy and hash immutable member rows/chunks.
2. `SCAN_AND_SELECT_VOTES` — bounded vote pages copy/hash immutable rows; after member/window,
   completion, author, and schema validation, choose the deterministic latest eligible candidate
   per `(lessonId, uid)` in durable selection rows.
3. `AGGREGATE` — bounded selection pages create per-lesson exact sum/count rows.  Cursor and
   contribution changes commit together, so retry cannot add twice.
4. `PICK_WINNER` — bounded aggregate pages apply the existing exact comparator to a running
   winner and count all eligible lessons.  The candidate-chain plus frozen manifest becomes the
   new versioned `calculationManifestDigest`; this is not a sorted legacy `rankingDigest`.
5. `PUBLISH` / `PUBLISHED` — a small terminal transaction revalidates all terminal counts,
   chains, identity, lease/revision and season fence before any award or final receipt.

Selection must preserve the pure policy's **global** validation too: durable seen-ID indexes
for rating and completion identities must reject conflicting duplicates across pages, while
accepting exact repetitions according to the old normalizer. Validate raw schema/identity
before eligibility filtering; an ignored late/ineligible vote cannot hide malformed input.
Member IDs must agree with their canonical lesson identity. Per-user selection alone does
not prove these invariants. Add explicit cross-page duplicate/corruption regressions.

Every worker invocation acquires a random lease token in a transaction.  Each page rereads the
season, job and required source/projection documents; it requires the exact token, revision,
unexpired lease, identity and deadline before writing its next sealed checkpoint.  Page size,
max pages, deadline, lease and server clock are trusted worker configuration, never callable
payload.  `REQUIRES_REVIEW` is terminal without a prize for malformed/missing/corrupt input;
it is distinct from a valid no-winner season.

## Publication and award invariants

The terminal transaction alone may call the existing exactly-once award logic: same season award
key, `arena_awards` marker, winner `boxCount +10`, and one BOXES ledger row.  It writes the final
season result and initiating immutable `APPLIED` receipt in that transaction.  It records
`finalizedByUid` as the initiating curator and executor identity separately in private audit
metadata.  No scan, reduction, retry, or status read may award BOXES.

`prepareAward`/`applyAward` currently live inside `functions/arena-service.js:98–121`; they must
be extracted or injected as one shared server-only helper rather than duplicated in the worker.

## Rollout constraint

After rollout, the old synchronous `finalizeSeason` publisher must only replay an existing
legacy `FINALIZED` receipt/result.  It must reject a new-version season and every `FINALIZING`
season; it must never scan and publish an `OPEN`/`FINALIZING` season beside the job.

This gate protects code containing the gate.  It does **not** promise safety against an already
running stale binary that lacks it; deployment/operational ownership must prevent that binary
from publishing before the new path is enabled.

## Planned implementation ownership

Checked sources: `functions/arena-service.js`, `functions/arena-policy.js`, `functions/index.js`,
`functions/tournament-qualification-{scan,calculation}.js`, `functions/tournament-worker.js`,
`functions/arena-service.test.js`, `functions/qualification-calculation-test-fixture.js`, and
`firestore.rules`.

Planned changes: new `functions/arena-mutation-reservations.js`,
`functions/arena-season-finalization.js`, `functions/arena-season-worker.js`, and season
finalizer fixture/tests; modify `arena-service.js`, `index.js`, and rules to deny the new
reservation root.  Extract an award helper if required.  Add an index only if the concrete
`FINALIZING` sweeper query requires one.

## Required evidence before acceptance

- 19 vs 20 distinct raters; repeated votes and a later invalid vote prove latest **eligible**
  selection, not latest raw row.
- UTC start-inclusive/end-exclusive boundaries, publication boundary, self/completion filters,
  exact score and all tie-breaks.
- More rows than a page/transaction budget: every member/vote participates; no first/last-page
  truncation or unbounded query/read.
- Restart after every phase/page, transaction callback retry, failed commit, changed page size,
  corrupted/missing frozen row/checkpoint, held/expired/stale lease and deadline exhaustion.
- Concurrent starts and start-versus-rating/placement at `OPEN → FINALIZING`; exactly one job,
  one final receipt, one award marker, +10 wallet effect and ledger row.
- Pending reservation collision with rate/transition and payload reuse; duplicate original
  request, final replay, and historical completed legacy replay remain exact.
- Curator-only bounded status response and direct Firestore denial for reservation/job/raw data.

Remaining work is implementation, bounded Node tests, Firebase/rules/emulator checks, deployment
of the server-only sweeper, and measured read/write/RSS/time evidence.  None is completed here.
