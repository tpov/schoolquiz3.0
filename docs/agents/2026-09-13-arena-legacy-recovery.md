# Arena: reviewed recovery of the pre-fix e499 incident

This is recovery of one historical local record, not a fresh Arena acceptance
run, a new product settlement, or a release decision.

## Baseline and changed files

Authoritative repository: `/srv/schoolquiz-mvp-20260906/integration`, branch
`integration/mvp-20260906`. Slice starts from
`45ab9d82047ae1313864f91f3358cb1f41e79ef9` (the separate TZ07A commit).

New files:

- `scripts/arena-e2e-legacy-settlement.js` and its behavioral tests.
- `.maestro/mvp/arena-e2b/run-arena-legacy-recovery.js` and its behavioral tests.
- This report.

Changed: `.maestro/mvp/arena-e2b/README.md`, `scripts/package.json` (register
the diagnostic/wrapper tests and syntax checks, plus the committed TZ07A SDK
harness syntax check). The existing recovery flow is unchanged. No Android or
production Functions source is changed by this slice. User-owned untracked
files, including `scripts/arena-e2e-settlement-oracle.js`, remain excluded.

## Safety contract

The diagnostic accepts exactly one reviewed incident, pinned in executable
source to immutable ticket/event/finished mutation/ledger evidence. It verifies
the saved request and receipt in retained Room schema 14. It never rewrites an
event, changes timestamps, adds an epsilon, sends a mutation, or makes a strict
fresh-acceptance oracle accept the old timestamp mismatch.

The wrapper uses the existing shared device lock and the actual private Firebase
process environment. It retains stopped DB/WAL/SHM/Auth snapshots with private
permissions, bounds the UI flow, and has no automatic retry. Full current
server-account hashes and Auth bytes must be equal for automatic recovery
success; a mismatch remains `REQUIRES_REVIEW`, even if later explained manually.

Before execution the installed C1+C3 APK was re-read and matched SHA-256
`c0bf9657ca6d1ef00454af4c08522a476e2e669b8fc6262ea7d73d6613c8c4d2`.
Its previously recorded signing certificate is
`73d39c9caeac685788d5f466dc7668e60be42445879f2883a124c32230db81d9`;
the certificate was not independently re-extracted during this recovery.
Device B: `172.17.0.5:5555`; application `com.tpov.schoolquiz.mvp`.
Room identity: `ca5439bdc37decd3f21cb843d10bb3f4`.
No APK install, application reset, Firebase restart, export overwrite, account
switch, or replacement ticket occurred.

## Exact incident and outcome

Incident: `arena-clock-e499-20260912`; redacted ticket fingerprint
`e499c980671f4dcd`, attempt fingerprint `9cab3b00d9f794de`.
Historical terminal time `1789230842064`, event time `1789230842075`.
The production fix remains the earlier commit
`8dc782eab4bc85ecbea4ebe72c4c00755932beba`.

One UI run pressed only the exact retained ticket's server-status refresh.
Local ticket changed `RESERVED → SETTLED`, its saved SETTLE command changed
`PENDING → ACKNOWLEDGED`, and the original event, mutation and ledger hash
remained exactly
`4677fe2ad288781debde105558645d9e26038e58fd86c09c5239692c3381e278`.
The post-reader classified the record `RECONCILED`; do not repeat recovery.

The wrapper nevertheless correctly stopped with
`REQUIRES_REVIEW / SERVER_ECONOMY_CHANGED / POSTCHECK`. The overall server-account
hash changed from
`ea4925d4affb660f7e095afa44015f436581a27818af00e3706460dc889a8cdb`
to `57ac5eb14fccacc34ed78b9416073f0d783b89b2f250afb6f8bd78f2781fcc88`.
The original result file has not been rewritten or relabelled PASS.

## Read-only postcheck review

1. Current five server documents still matched the recorded post-run hash.
   Substituting only retained pre-UI profile clock values reconstructed the
   **exact original pre-run hash**, not an approximate economic comparison.
   The only four changed fields were player `users.updatedAtMs`,
   `users.lifePointsUpdatedAtMs`, `users.plasmaPointsUpdatedAtMs`, and
   `premium_accounts.asOfMs`, all from `1789285340790` to `1789292498850`.
   All other fields of both users, both premium headers (including one absent
   header), and the author's royalty account were unchanged. This includes
   balances, reserves, premium quantity and author payments.
2. Independent full Room comparison PASS: target ticket changed only
   `observed_at_ms`, `server_status`, `terminal_json`; target command only
   `state`; player profile only `updatedAtMs`, `lifePointsUpdatedAtMs`.
   All other tables/rows, receipts, intents, outbox and financial fields matched.
3. Auth files were not byte-identical. Parsing both retained preference files
   found only cached access-token/issue-time refreshes. Token headers and all
   claims except `iat`/`exp` matched; `sub` and `user_id` still identify the exact
   retained owner. Refresh tokens and all other stored fields matched. This is
   a structural retained-token comparison, **not signature verification or a
   fresh authentication test**; no credentials were printed or submitted.

The reviewed conclusion is that this historical local record is reconciled and
no financial change was found in the measured scope. The unchanged strict runner
still requires human review when startup refresh changes timestamps or tokens.
Raw pre-server account documents were not captured initially, only their hash;
the exact reconstructed preimage and all current documents are now retained
privately. Do not generalize this incident-specific reconstruction to other runs.

## Evidence and checks

Private server evidence root:
`/srv/schoolquiz-mvp-tools-20260906/tz06c/architecture-legacy-eYhpn6Y0`.

| Invocation | Result | Evidence |
| --- | --- | --- |
| First | BLOCKED `UNSAFE_INPUT`, no UI | `live-recovery.log`; wrapper initially applied private-input permissions to the public source flow. Fixed with regression. |
| Second | BLOCKED `CLASSIFIER_BLOCKED`, no UI | `live-recovery-r2.log`; SDK reads actual process environment, not the dependency object. Both are now checked. |
| Third | BLOCKED `CLASSIFIER_NOT_ELIGIBLE`, no UI | `live-recovery-r3.log`; wrapper incident label did not match the already reviewed incident. Corrected wrapper, not evidence. |
| Fourth | UI completed; REQUIRES_REVIEW | `live-recovery-r4.log`; directory `legacy-e499.1789292457202.55cbc7c9285e1719`. |

The fourth directory retains Maestro output/screenshots, pre/post classifiers,
stopped raw snapshots, the unchanged `result.json`, `postcheck-review.json`,
`auth-postcheck-review.json`, and private exact current/reconstructed server rows.
The two `inspect-legacy-*-postcheck.js` helpers live beside this private evidence;
they are one-off read-only diagnostics, not release code or general repair tools.

Current Node 22.23.1 diagnostic 34/34 and wrapper 16/16 behavioral tests PASS in
`tests/legacy-wrapper-node22-final-v4.log`. Earlier blocked invocations are not
counted as passes. The same targeted suites also passed locally on Node 26.
The complete registered `scripts` test command and its syntax-check command
also PASS on Node 22.23.1; logs are `tests/scripts-all-node22.log` and
`tests/scripts-lint-node22.log` under the same evidence root. Syntax checks are
not behavior, Firebase rules, or HTTP coverage.
No Gradle, Android build, `ciCheck`, real offline or Play checks were run here.

## Remaining work

The old timestamp pair remains a strict fresh-acceptance FAIL. A future matrix
scenario needs its own new run context/ticket and the fixed candidate, with
exact current receipts. Real offline, lost-response proxy, two-device/account
isolation, limits, cancellation races, hardware Back and backend-vs-network
diagnostics remain separate required cases. This recovery does not close that
matrix or turn historical evidence into release acceptance.
