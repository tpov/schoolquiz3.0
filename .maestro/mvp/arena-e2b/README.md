# Arena E2B: retained-account end-to-end draft

This directory contains the operational runner for the first Arena
end-to-end proof from `03-NEXT-STEPS.md` §2б.  It becomes evidence only after
the restored Firebase export, the exact C1+C3 APK, retained application data,
and selectors captured from that exact device have all been recorded in the
run artifacts.

The run is intentionally split into phases.  A ticket and an attempt do not
exist until preparation and start have completed, while `ratingId` is created
only when the user submits the rating.  A runner that guesses any of these
identities could accidentally validate an older receipt.

| Phase | Action | Required evidence before the next phase |
| --- | --- | --- |
| `preflight` | Preserved-data launch and retained-account UI check | Selector snapshot and immutable run context |
| `prepare` | SELECTED/HARD preparation | Read-only receipt context containing the new ticket and attempt |
| `settle` | Start, answer, force-stop/relaunch, finish | Pending UI proof plus a `RESERVED`/no-event server context for the exact saved SETTLE |
| `resume-settle` | Continue an interrupted saved-answer run | Same pending proof, without preparing or starting a replacement ticket |
| `deliver` | Re-open an existing local SETTLE request and synchronize it | Fresh read-only receipt context showing the matching settlement |
| `rate` | Queue rating `2` from the confirmed result screen | Read-only Room context containing the new `ratingId` and the exact ticket/attempt |
| `deliver-rating` | Re-open the existing queued rating and send it | Final server receipt and oracle verification |
| `verify` | Run the two-receipt oracle | `PASS` only when `SETTLE` and `RATE_LESSON` bind the same run |

`run-arena-e2b.sh` emits `result.json` in a new private output directory on
every phase.  Only `verify` may emit an overall `PASS`; completed earlier
phases remain `NOT_RUN` overall and name their next required evidence.
Its manifest binds the APK, run context, selector map, runner and every
executed Maestro flow by SHA-256.

## Inputs

1. Copy `run-context.env.example` outside this directory and fill it with the
   immutable candidate and retained-account intent.  It is parsed as plain
   `KEY=value` data, never sourced as shell code.
2. Capture the exact accessibility hierarchy on the installed candidate, then
   copy `selectors.env.example` outside this directory and fill every selector
   used by the chosen phase.  Values are blank by design; locale text and
   accessibility exposure must come from that capture.
3. Before `prepare`, use `arena-e2e-ticket-discovery.js baseline` to record the
   complete owned-ticket set.  After preparation, use its `discover` command;
   it refuses anything except one new ticket whose immutable fields match the
   run context.  Pass that exact ticket and attempt to the read-only
   `arena-e2e-context-reader.js`.
4. After `settle`, retain its pending server context and screenshot. `deliver`
   must consume that same context and produces the confirmed settlement context.
   After submitting
   the rating, stop the app only after the rating UI confirms its local save,
   take a read-only Room snapshot (database plus adjacent WAL/SHM), and run
   `arena-e2e-rating-id-reader.js`.  It refuses a missing, ambiguous, or
   differently bound rating intent.  The final verification uses this Room
   context to obtain the ID and has the server reader independently confirm
   the `RATE_LESSON` receipt.

The current UI evidence identifies the target lesson as `Catalog fixture title
changed` and the preparation controls as `Prepare selected lesson`, `Confirm
preparation`, `Hard`, and `Confirm`. Fixture titles may repeat, so the selector
map also requires the visible fixture-author line; the preparation flow scopes
its generic control to that author with Maestro's `below` relation. Place those
facts in the selector map only after the hierarchy capture confirms the exact candidate and locale. The
source also defines tags such as `arena_entry`, `arena_entry_dialog`,
`arena_difficulty_HARD`, `arena_entry_confirm`, and
`arena_start_<ticketId>`.  Tags are hints, not pre-filled Maestro selectors:
Compose test tags must first be confirmed as visible to Maestro on the
retained device.

## Runner contract

The runner requires explicit `--run-id`, `--candidate-head`, `--apk`,
`--run-context`, `--selectors`, and `--output-root` inputs.  Device phases
also require `--serial`.  It checks the APK SHA-256 and signing certificate
against the immutable context and requires an existing installation.
When the Android build tools do not already find Java on `PATH`, pass the
mounted JDK explicitly with `--java-home <dir>`.
`--install-candidate` is allowed only for `preflight` or `prepare` and performs
one `adb install -r` update; later phases verify the APK file and retained
package without reinstalling or restarting it.  It never clears app data,
uninstalls the package, or removes all reverse mappings.

Before Maestro starts, the runner rejects any existing reverse mapping outside
of its three allowed mappings and installs only these exact pairs:

```
tcp:9099 -> tcp:9099
tcp:8080 -> tcp:8080
tcp:5001 -> tcp:5001
```

The `settle` and `verify` phases receive paths to the read-only context reader;
`verify` also receives the settlement context, a Room rating-ID context, and
the two-receipt oracle.  The oracle gets `--rating-id` and `--rating`, never
the local outbox mutation key.

Example shape, with all paths and values supplied by the executor:

```bash
bash work/arena-e2b/run-arena-e2b.sh \
  --phase preflight \
  --run-id <unique-run-id> \
  --candidate-head <40-hex-head> \
  --apk <candidate-apk> \
  --run-context <immutable-run-context.env> \
  --selectors <confirmed-selectors.env> \
  --serial <adb-serial> \
  --output-root <outside-repository-results-directory>
```

For `settle`, add `--receipt-context <reader-output-after-prepare.json>` and
`--context-reader <arena-e2e-context-reader.js>`. Its output must remain
`RESERVED` with no event or receipt. Run `deliver` against that same pending
context; it sends the already saved operation and produces the confirmed context
for `rate`. For
`verify`, retain that settlement context, add `--rating-id-context <room-reader-output.json>`, and add
`--receipt-oracle <arena-e2e-receipt-oracle.js>`.

`rate` stops only when the UI confirms that rating `2` is queued locally. Take
the Room ID snapshot at that point, then use `deliver-rating` to send that exact
stored intent and wait for the accepted receipt.

If the tooling, rather than the product, interrupts after the first saved
answer, preserve the exact `RESERVED` context and use `resume-settle`; it only
opens that ticket and completes its saved lesson. It never prepares or starts a
replacement.

The Arena screen orders its items as catalog header, reservation header, local
tickets, then lesson cards.  The flows therefore scroll up from a selected
lesson to the reservation header, and down from that header to the exact
reader-verified ticket.  This preserves the distinction between the prepared
ticket's `Start` action and its later `Open` action after a process relaunch.
On a cold Arena entry the title may render while the lower reservation controls
are merely off-screen, so flows scroll to `Show local tickets` before waiting
for its enabled state.
Opening a retained run may show either the explicit recovery dialog or its
already-restored saved-answer state, so the flow waits for either observable
state before deciding whether to press `Continue`.
Where the UI exposes generic action text, Maestro scopes an enabled action
by first locating the reader-verified `Ticket: <id>` label and then revealing
the action directly below that card.  The UI currently does not expose test
tags as Android resource IDs, so this is a visible-layout guard rather than a
substitute for the independent server receipt binding.

The source tree is not evidence by itself: the private run artifacts record
the exact candidate, retained device, server contexts, and results.  Use
`bash -n work/arena-e2b/run-arena-e2b.sh` for a local syntax check only.
