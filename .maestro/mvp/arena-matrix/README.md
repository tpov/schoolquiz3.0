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

## Offline transport guard

`offline-transport-guard.js` is the narrow transport boundary for a future real-offline Arena
row. It exists because this demo application points Firebase at `127.0.0.1` and normally reaches
the emulator through `adb reverse`; turning Wi-Fi/data off by itself would not prove an outage.
It is a guard around one already-bounded child command, not an acceptance runner and not a
substitute for the pending-SETTLE reader or receipt oracle.

The guard requires an explicit `--control-channel adb-independent` acknowledgement. That flag is
only valid when ADB control will survive disabling the device's Wi-Fi and mobile data, for example
a wired or container control plane. It must never be used for Wi-Fi ADB. The guard checks that
control channel after every reverse/toggle change, but the acknowledgement is still required
because no portable ADB query can prove the physical route.

Before it changes the device, it requires one ready target with Wi-Fi and data enabled and exactly
these three normal mappings, with no extras: `9099`, `8080`, and `5001`. It accepts both current
two-column and platform-tools-36 prefixed `adb reverse --list` rows. Any extra mapping (including
the Firebase Database or Storage ports) is `FAIL` before an app stop, reverse removal, or network
toggle, rather than leaving a possible backend bypass.

It also acquires the same per-serial `flock` used by E2B at
`/tools/arena-e2b-device-locks` (or an explicitly supplied private `--lock-root`) before the
first device read and holds it through restore. A competing cooperating runner returns `NOT_RUN`
before any device call. Loss of the lock, or a failed exact-offline check after the child, is a
`FAIL`. The post-child check cannot protect against a hostile process that deliberately bypasses
the shared lock and adds then removes a mapping between observations; therefore real use is only
valid in the controlled device environment where all device phases use this lock.
The helper normally notices that its guard parent is gone and releases the lock, but a hard host
failure can still leave a stale lock. In that recovery case, first prove there is no live owner and
restore the device transport manually; do not remove a lock merely to start another financial run.

Its fixed sequence is:

```text
preflight -> force-stop app -> recheck exact baseline -> remove only 9099/8080/5001
  -> disable Wi-Fi/data -> verify no mappings and live ADB control -> child
  -> reverify no mappings and held lock
  -> force-stop app -> restore only the three baseline mappings -> enable Wi-Fi/data
  -> verify exact baseline
```

It never calls `adb reverse --remove-all`. A concurrent or unexpected mapping is left untouched
and makes the result `FAIL` for manual recovery rather than being deleted. The child is placed in
its own POSIX process group; a timeout or any handled `SIGINT`/`SIGTERM` first terminates that
group and then escalates to `SIGKILL` after a bounded grace interval. Repeated handled signals do
not bypass cleanup. Sudden host loss, an unhandled `SIGKILL` to the guard, or a deliberately
detached grandchild cannot be made recoverable by a process-local guard.

The guard never inherits the child's stdout/stderr. It saves at most 64 KiB of that output in a
new adjacent `0600` private log and records only byte count/truncation status in the result JSON.
The JSON itself contains only a serial hash, state/counts, child exit outcome, and generic error
code — not the serial, child command, app data, output, or backend payloads. Both result paths
are checked as new paths under a private, canonical non-symlink directory before the lock or a
device command is acquired.

Run it only with a new result path inside a non-symlink `0700` directory:

```text
node /repo/.maestro/mvp/arena-matrix/offline-transport-guard.js \
  --serial <adb-serial> --adb <adb-binary> --app-id <application-id> \
  --control-channel adb-independent --output <new-private-dir>/transport-result.json \
  --timeout-ms <bounded-ms> -- <already-bounded-child-command> [arguments...]
```

The controlled environment supplies `flock`; `--lock-root` is only for an equivalent private
shared lock root. `--flock` exists solely to inject the fake helper in the standalone test and is
not an alternative locking protocol for real device runs.

The standalone fake-ADB test proves the normal cut/restore order, three-and-only-three mapping
removals, both mapping output shapes, shared-lock contention, revalidation after a concurrent
mapping, private child-output containment, restoration after child failure, partial cut failure,
timeout with a TERM-ignoring child and descendant, repeated handled signals, and refusal before
mutation for unexpected mappings, a missing control-channel acknowledgement, or a symlinked
output parent:

```text
node /repo/.maestro/mvp/arena-matrix/offline-transport-guard.test.js
```

No Device B, Firebase data, ticket, attempt, or financial flow is changed by that test. A future
offline runner must additionally bind an isolated fixture/context, save stopped Room copies for
the progress and pending-SETTLE readers, prove the server remains uncommitted while the guard is
closed, and only then perform a separately controlled delivery and receipt-oracle phase.
