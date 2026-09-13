# TZ07A — bounded, exact qualification continuation

This is a server implementation slice, not MVP or device acceptance.

## Baseline and decision

Authoritative repository: `/srv/schoolquiz-mvp-20260906/integration`, branch
`integration/mvp-20260906`, starting HEAD
`8fc7af452e6727fec5487ef35e4e388359e14b8d`.

The owner approved correcting numerical error for future unpublished results.
The pairwise least-squares formula and existing tie-break sequence are retained.
Published rankings/brackets are never recomputed or overwritten.

## Implemented contract

- The trusted controller selects and persists the calculation/rounding/runtime
  contract before scanning. A changed freeze ID fences both legacy publishers.
- A complete frozen admission scan feeds a private calculation job. No public
  callable accepts a version, checkpoint, worker token, or trusted clock.
- Durable phases: `VALIDATE → REDUCE → SOLVE → PROJECT → PUBLISH → PUBLISHED`.
  Every checkpoint has an input identity, digest, cursor, revision, and invocation
  lease. An expired or replaced worker cannot publish or release another lease.
- Validation groups complete player results into private, UTF-16-order-preserving
  player documents. Original admissions, sessions, and verified runs are checked
  again in both reduction and projection.
- Sufficient statistics reduce the LS system to at most 3×3 exact rational
  equations. Rounding operates on fractions before conversion to display numbers.
  The independent second player stream must reproduce the first input hash.
- Only the top 16 ordinals are materialized for the bracket; all admitted verified
  players contribute to the calculation. Page size is an invocation bound, not a
  product admission cap. The old permanent wait above 501 runs is removed from
  the trusted controller only after proving the replacement path.

Version tuple:

```
pairwise-percent-least-squares-v1
qualification-three-form-rational-v2
compact-mask-reduction-v1
exact-half-positive-infinity-canonical-zero-v1
```

## Production and test files

New: `functions/tournament-qualification-compact.js`,
`functions/tournament-qualification-version.js`,
`functions/tournament-qualification-calculation.js`, their three test suites,
and `functions/qualification-calculation-test-fixture.js`.

Changed: `functions/tournament-qualification-freeze.js` and its tests,
`functions/tournament-qualification-scan.js`,
`functions/tournament-service.js` and its tests,
`functions/course-tournament-controller.js` and its tests.

New trusted SDK harness:
`scripts/mvp-qualification-calculation-firestore-test.js`.
Its default invocation is write-free. Execution requires the private emulator,
explicit case selection, and an exact seven-file source manifest. Fixtures use a
fresh namespace, create-only preflight, bounded writes, and are retained.

## Fresh Firebase SDK evidence

Evidence directory on the server:
`/srv/schoolquiz-mvp-tools-20260906/tz06c/qualification-sdk-aSsenU2p`.
`source-manifest.json` pins every changed production dependency of the harness.
Harness SHA-256:
`98ae4d33607ec869dc7441a33366a5340db0740a783b956bf4a94b7c3a4346ae`.

| Case | Result | Observations |
| --- | --- | --- |
| Two players, six runs | PASS, `small.log` | Seven injected transaction aborts: scan chunk, proof, four calculation transitions, publication. No partial ranking/bracket. Replay preserves financial state. 8,918 ms; peak RSS 101,484 KiB. |
| 502 distinct players, 502 runs | PASS, `large.log` | 19 controller continuations through admission scan, calculation, publication and bracket. All 502 players counted; independent equal-score UID tie oracle matches top 16. 108,283 ms; peak RSS 120,088 KiB. |

Large run: `qcalc_59d41cdbdfb642c9ba0a47d14c661df7`.
Input fingerprint:
`57241bf2552188e3e386f07d59e8af485d5a0d1f02526b6b758997066f892121`.
Output fingerprint:
`0d1a3483b6d06ed291ca8c0b504555878425b2938c86e205462aa74374813271`.

The large run retained 591 seed documents and 14,706 distinct committed paths,
including generated private forms and session state. The trace recorded 29,239
transaction document reads and 16,690 committed transaction writes. These
instrumented counts exclude direct reference reads, harness assertions/preflight,
seeding and service billing overhead; they are not total billed reads/writes.
The tools container remained at 0.5 CPU and 512 MiB. No Firebase restart occurred.

## Other evidence and limits

- Exact source controller: 42 behavioral cases PASS on Node 22.23.1.
- Scanner: 23 behavioral cases PASS on Node 22.23.1.
- Pure rational core: 19/19 PASS; version/freeze/service seam: 56/56 PASS on Node 22.
- Durable calculator: all 30 current cases PASS in three disjoint Node 22 runs,
  28 + 1 + 1. Logs and hashes are in `CALCULATION-TEST-EVIDENCE.md` beside the SDK
  logs. The 4096 and 4097 cases each finished in 248 bounded invocations, about
  116 and 115 seconds for the whole fake test. Peak sampled process RSS was about
  130 MB. Each transaction used at most 305 reads / 101 writes for these one-form
  fixtures; the three-form 1503-run case reached 655 reads / 101 writes.
- Two earlier combined runs exceeded their **test-file** limits (120 and 240
  seconds). The second used a negative-lookahead name filter that also matched
  the Node 22 parent file, accidentally including both large tests. Neither run is
  PASS; both logs are retained. Corrected runs use an explicit skip pattern for
  the first 28 cases and positive exact names for the two large cases. Production
  invocation budgets and container limits were not raised.
- Large 4096/4097 fake fixtures are not actual Firestore or device acceptance.
  The SDK large case uses equal-score timed-out sessions; unequal-score numeric
  correctness is covered by the separate pure behavioral suite.
- Source corruption before a page is read is rejected. This is not a promise to
  detect arbitrary privileged writes to already-processed immutable records after
  their last verification. Server-owned immutability remains a trust boundary.
- Unfinished jobs require their pinned ICU/locale runtime. Future algorithm
  upgrades must preserve old published-version readers and explicitly migrate or
  continue unfinished work; changing a version constant is not a migration.
- The SDK harness bypasses HTTP/Auth/scheduler and invokes the trusted controller.
  HTTP/rules/worker scheduling, Android, `ciCheck`, Play and final manual acceptance
  are separate gates. No APK was built or installed for this slice.

## Remaining work

Fresh overall server/rules/HTTP checks on the final combined candidate; limited
server-only worker execution/recovery; tournament deadlines and prize/refund
matrix; typed tournament client, durable operations, discovery and Decompose UI.
This slice does not close TZ07B/C or TZ08, nor the full MVP plan.
