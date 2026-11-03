# Isolated qualification persistence check

`scripts/mvp-qualification-firestore-test.js` exercises the actual Admin SDK against the private
`demo-schoolquiz-mvp` Firestore emulator. It is not an Auth/HTTP, scheduler, production-index or
UI test. It never calls a global worker sweep or alters the public server clock.

## Safe default and explicit execution

Without `--execute`, the script only prints its plan and does not connect to Firebase.
Choose exactly one case: `--case=small` (2 players × 3 attempts) or `--case=large`
(167 players × 3 attempts). Run small first; review its result and the retained footprint before
authorizing large. It never automatically runs both.

Execution additionally requires matching `QS_APPROVED_CASE=small` or `large`, exactly
`GCLOUD_PROJECT=demo-schoolquiz-mvp`, and
`FIRESTORE_EMULATOR_HOST=schoolquiz-mvp-firebase:8080`. Both
`GOOGLE_APPLICATION_CREDENTIALS` and `FIREBASE_CONFIG` must be absent. Before invoking the script,
verify that Docker network `schoolquiz-mvp-private` is internal and the Firebase emulator has no
published host ports. Mount the integration source read-only at `/repo`; its existing
`functions/node_modules` provides the Admin SDK. Use the already verified Node 22.23.1 image,
192 MiB memory / 256 MiB memory+swap and 0.5 CPU. Do not restart the emulator or attach production
credentials to run this check. No device is needed.

Each invocation creates a fresh `qs_<UUID>` namespace. Seed writes are create-only; preflight
requires the planned documents to be absent. A transaction guard rejects writes outside that
event, its exact derived sessions/operations and its synthetic accounts. No cleanup is performed:
the script retains test records and the synthetic 12-BOXES escrow. Never rerun a failed case just
to hide its first outcome; retain its log and investigate its printed run/event IDs.

## What it checks

- Actual source preparation, funded event creation and three admitted EASY sessions per player.
  Only the eligibility loader is synthetic: it validates the owned published course and its two
  independent reviews. The bank, admissions, sessions, scores and ranking are produced by the
  unchanged integration modules.
- Small case: callback aborts after staging a discovery chunk, a verification proof and ranking
  publication. Actual SDK rollback must leave no partial write. A legitimately settled session
  survives a later proof abort; its PENDING row is repaired on retry.
- Real `where(status == PENDING)`, `orderBy(__name__)`, bounded `limit` and `startAfter` queries.
- Restartable controller continuation across all pages. There is no bracket or partial ranking
  before every admission is verified. The final ranking, top16 and fingerprints equal the unchanged
  LS implementation over the full authoritative cohort.
- Replays preserve the immutable ranking and a digest of all written financial document data.
  This financial digest does not include Firestore `updateTime`.

Unanswered questions receive the existing server scorer's timeout result; missing/unknown results
are not invented as zero. The injected clock advances only the harness's own event to its cutoff,
then consumes actual wall time. Cooperative deadlines do not forcibly cancel in-flight SDK calls.
The harness pins its controller/scanner/LS source hashes: on intentional source changes, review and
update those anchors; do not bypass them.

## Recorded September 7, 2026 executions

Both ran the exact script SHA-256
`4bb8a115a8bba18e1ab0ed3ae45947eca75d4857b78d2f0edaf92260dcada36a`.

| Case | Outcome | Retained documents | Runtime / peak Node RSS |
|---|---|---:|---|
| Small `qs_28f17ba7e18b46d984b158ebdd29fd18` | 7 groups; 6/6 verified | 269 | 6.45 s / 97.9 MiB |
| Large `qs_9352808f41524641b522abb4fde07fbd` | 501/501 verified; 167 players ranked; 12 continuations | 13,340 | 92.81 s / 120.5 MiB |

At large continuation 11, all 501 admissions were discovered but only 469 verified: neither ranking
nor bracket existed. Both appeared at continuation 12 after verification completed. Each fixture
retains 12 synthetic BOXES in escrow. Logs are in the dedicated server tools directory under
`arena-draft/qualification-firestore/{small,large}-r1.log`.

This proves the former 500-attempt discovery boundary is crossed in the emulator. It does not
remove the current measured computation envelope of 501 verified runs. Larger events currently
remain waiting without discarding participants, publishing a partial bracket or releasing escrow;
an alternative completion path is still a release requirement.
