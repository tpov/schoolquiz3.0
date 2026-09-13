# Arena matrix device flows

These flows are deliberately separate from the retained-ticket E2B runner.  They never prepare,
start, cancel, settle, rate, install, clear, or reset an application.  Each device phase takes a
non-blocking per-serial lock shared with E2B, writes an isolated result directory, and records
`PASS`, `FAIL`, or `NOT_RUN`.

`run-system-back-catalog.sh` proves the physical Android Back dispatcher on a safe route only:

```text
verified Home -> navigation-only route to unloaded catalog -> Android KEYCODE_BACK
  -> Arena root -> Android KEYCODE_BACK -> the same verified Home
```

Before it makes any navigation or sends either Back event, it captures the actual hierarchy and
refuses `NOT_RUN` unless the retained application is at Home.  It then uses only app-shell navigation
to reach an unloaded catalog — never `Load lessons` — and rechecks the hierarchy before each
physical Back.  A prepared ticket, ticket action, runner, result, synchronization, or rating
surface blocks the run.  The selector map is an explicit immutable input captured from the
installed candidate; it is checked across visible, accessibility, and hint text, with no
resource-id assumptions.

Run it only inside the controlled Maestro container and pass the candidate APK's immutable SHA-256
and certificate SHA-256.  Before the UI phase it also reads and compares the installed base APK;
it refuses split packages because a single candidate artifact would not prove their full identity.
The command has no installation or state-clearing option.

## Controlled `submitMutation` response-loss proxy

`submit-mutation-drop-proxy.js` is a deliberately narrow sidecar for a future Arena fault run. It
is not a general proxy and it is not itself a device acceptance result. Its only fault is to close
the client connection after one exact `submitMutation` request has received a complete successful
upstream response.

The private JSON configuration binds all of the following before the device route is changed:

- loopback listener and Firebase upstream;
- the complete callable path and `lesson_runner.SUBMIT_ATTEMPT` operation;
- one mutation ID and one 64-hex attempt ID.

It does not write those identifiers, request bodies, response bodies, or headers to its audit. The
audit records SHA-256 bindings and event hashes only. Configuration, ready, and audit files must be
new regular files in a private directory; `listenPort: 0` requests a fresh listener port, published
only in the private ready artifact.

Once the exact request reaches upstream, every further exact request is closed locally, including
concurrent retries and retries after a non-success response. This is intentional: an inconclusive
upstream outcome must not be turned into a second financial operation. Only an audit status of
`UPSTREAM_CONFIRMED_AND_DOWNSTREAM_DROPPED` proves the injected condition; an Android run must
then stop the sidecar, restore the single `adb reverse` mapping in `finally`, manually deliver the
retained outbox item, and use the receipt oracle. Any other status is `FAIL` or `INTERRUPTED`, not
a pass.

The proxy and its test use only Node's standard library. Run the test in the controlled Maestro
container:

```text
node /repo/.maestro/mvp/arena-matrix/submit-mutation-drop-proxy.test.js
```

The test uses an artificial loopback HTTP server. It exercises exact matching, one upstream call
under a concurrent retry, successful-response loss, pass-through traffic, non-success handling,
private artifacts, and invalid configuration. It does not change Firebase or an Android device.

## Pending SETTLE Room reader

`scripts/arena-e2e-pending-settlement-reader.js` is a read-only local proof helper for a future
offline or response-loss run. It must receive one explicit run, owner, ticket, and derived attempt,
plus a private **copy** of `schoolquiz.db` and any adjacent WAL/SHM sidecars made while the app is
stopped. It never opens adb or Firebase and emits only hashes, states, counts, and file digests;
it never emits the owner, ticket, attempt, payload, answers, or errors stored by Room.

The helper accepts only the installed C1+C3 Room v14 schema and its exact Room identity hash. It
requires one intact frozen snapshot, one exact pending `SETTLE` command, the matching pending
outbox row and first intent, no receipt for that mutation under any owner, and no locally scored
attempt rows. It rejects an already delivered, ambiguous, malformed, mismatched, or newer schema
state rather than treating it as pending.

Run it in the controlled Maestro container with Node v22's read-only `node:sqlite` runtime. There
is currently no sqlite3-CLI fallback. A successful local result proves only the retained local
state; an offline row still needs the independently recorded network cut and a later receipt
oracle, while a response-loss row needs the proxy audit and server receipt proof.
