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
| `settle` | Start, answer, force-stop/relaunch, read retained Room progress, finish | Pending UI proof, a redacted Room checkpoint for the same first answer, and a `RESERVED`/no-event server context for the exact saved SETTLE |
| `resume-settle` | Continue an interrupted saved-answer run | Same pending proof, without preparing or starting a replacement ticket |
| `deliver` | Re-open an existing local SETTLE request and synchronize it | Fresh read-only receipt context showing the matching settlement |
| `rate` | Queue rating `2` from the confirmed result screen | Read-only Room context containing the new `ratingId` and the exact ticket/attempt |
| `deliver-rating` | Force-stop/relaunch, re-open the existing queued rating, then send it | Final server receipt and oracle verification |
| `verify` | Run the two-receipt oracle | `PASS` only when `SETTLE` and `RATE_LESSON` bind the same run |

`run-arena-e2b.sh` emits `result.json` in a new private output directory on
every phase.  Only `verify` may emit an overall `PASS`; completed earlier
phases remain `NOT_RUN` overall and name their next required evidence.
Its manifest binds the APK, run context, selector map, runner and every
executed Maestro flow by SHA-256.

## Recovery of a retained settled ticket

`flows/00-recover-one-settled-ticket.yaml` is a deliberately narrow repair
flow, not an E2B phase and not a substitute for `settle` or `deliver`. It may
be used only when a fresh, read-only settlement oracle has already proved the
same owner, ticket, and attempt `SETTLED` with its exact event and ledger.
The caller records a stopped-app Room snapshot before and after the flow and
binds the visible `Ticket: <id>` label to that oracle context.

The flow opens the already available local-ticket list and presses only
`Refresh this ticket from server` under the matched card. It does not prepare,
start, finish, restore a settlement, refresh a wallet, rate a lesson, or
refresh a catalog. It is therefore safe to fail closed if the local-ticket
list is not already available. Its selector values are literal visible text,
not regular expressions.

Acceptance for this repair requires all of the following: the pre- and
post-oracle evidence hashes are identical and report zero writes; the target
changes only from local `RESERVED`/`SETTLE:PENDING` to
`SETTLED`/`SETTLE:ACKNOWLEDGED`; the exact local receipt count is unchanged;
other tickets, commands, receipts, outbox rows, intents, and stats are
unchanged; and the app is stopped afterwards. Any unexplained server mismatch,
unknown terminal timestamp, or unexpected local change leaves the record
quarantined and stops the workflow.

### Pinned pre-fix clock incident (not acceptance)

`scripts/arena-e2e-legacy-settlement.js` is a separate, operator-only path for
an individually audited incident predating `8dc782ea`. That defect sampled
the clock twice in the same settlement transaction. It does not authorize
changing either stored timestamp or resubmitting a settlement.

The operator supplies a private incident file with schemaVersion `1`, kind
`PRE_CANONICAL_SETTLEMENT_CLOCK`, incidentId, full fixCommit, ownerSha256,
ticketSha256, attemptSha256, exact terminalSettledAtMs/eventSettledAtMs, and
serverEvidenceSha256. The last field is the reviewed `serverDigest` of the
original ticket, event, completed mutation key and exact bounded ledger rows.
The executable additionally pins the reviewed e499 server evidence SHA-256 in
source; a supplied incident file cannot authorize a different snapshot.
There is no timestamp tolerance and no wildcard incident. The diagnostic
also requires a stopped Room v14 snapshot: one matching saved request, one
local receipt identical to the event response, the original request hashes,
and no outbox delivery under that mutation ID. All other strict identity,
score, frozen price, conservation and ledger checks remain mandatory.

Invoke only against the private demo emulator. Both the reader's validated environment
and the actual Node process environment must name that endpoint; Firebase Admin does
not inherit an arbitrary environment object passed to a JavaScript function. Unset
`GOOGLE_APPLICATION_CREDENTIALS` and `FIREBASE_CONFIG` (do not assign empty strings):

```
GCLOUD_PROJECT=demo-schoolquiz-mvp FIRESTORE_EMULATOR_HOST=schoolquiz-mvp-firebase:8080 node scripts/arena-e2e-legacy-settlement.js --context private-context.json --incident reviewed-incident.json --db stopped-copy.db
```

`LEGACY_RECOVERY_ELIGIBLE` permits only the same exact-ticket read/recovery
flow above, with before/after snapshots and unchanged serverEvidenceSha256.
The post-snapshot must show `RECONCILED`; separately compare all unrelated
rows, which this one-ticket reader intentionally does not inspect. This is
not `PASS`: freshAcceptance stays `FAIL`, and the strict settlement and
two-receipt oracles must continue to reject the historical timestamp pair.
The first new acceptance run must use its own fresh ticket and exact current
receipts. A missing/changed pin, pending sender, other discrepancy, or absent
mutation key remains blocked for separate investigation.

`run-arena-legacy-recovery.js` is the one-shot wrapper for the reviewed
`arena-clock-e499-20260912` incident on retained device B (`172.17.0.5:5555`).
Before using it, verify the installed C1+C3 APK checksum against the candidate
registry; never install, clear application state or manufacture a replacement
context for recovery. Supply private `--context`, `--incident`, `--selectors`,
and `--output-root` paths, plus explicit `--adb` and `--maestro` executable paths.
The actual process must have the emulator environment above. `--help` lists the
exact command shape; `--inspect <run-directory>` is read-only and does not retry.

Selectors are literal observed UI labels. The selector file contains
`E2B_SELECTOR_INTERNET`, `E2B_SELECTOR_ARENA_TITLE`,
`E2B_SELECTOR_SHOW_LOCAL_TICKETS`, `E2B_SELECTOR_TICKET_LABEL_TEMPLATE` (exactly one
`{{ticketId}}` placeholder), `E2B_SELECTOR_RECOVER_TICKET`, and
`E2B_SELECTOR_TERMINAL_TICKET`. The public CLI cannot override the shared device
lock, database snapshot path, target owner/ticket/attempt or reviewed evidence pin.

The wrapper holds the existing per-device lock through all stops, snapshots,
UI work and failure diagnostics. It retains stopped DB/WAL/SHM/Auth copies under
private 0700/0600 directories, uses a bounded UI flow, and never submits a mutation.
Successful recovery requires equal server evidence, equal current payer/author/
premium-header/royalty-account hashes, unchanged Auth bytes and a full Room row
comparison. Only the target ticket's terminal observation, target SETTLE command
acknowledgement and the already-reviewed profile timestamps may change. Monetary
fields, unrelated rows, receipts and intent bodies must remain unchanged.

The outcome is `RECOVERY_RECONCILED`, **not** a new E2B PASS. An interrupted UI
run receives one diagnostic stopped snapshot and `REQUIRES_REVIEW`; there is no
automatic UI retry or rollback. A blocked precheck never proceeds to the flow.

The pinned incident was executed once on 2026-09-13 and is now locally
`RECONCILED`; **do not run the recovery flow again**. The original wrapper result
remains `REQUIRES_REVIEW / SERVER_ECONOMY_CHANGED`, not automated success. A
separate read-only review reconstructed the exact pre-run five-document hash:
only the player's three profile clocks and premium header clock advanced. Full
Room comparison passed; retained Auth differences were same-owner token/cache
refresh only. See `docs/agents/2026-09-13-arena-legacy-recovery.md` for evidence,
the explicit review limitations, and all preceding blocked invocations. This
does not relax the wrapper's comparisons or the fresh acceptance oracle.

## Inputs

1. Copy `run-context.env.example` outside this directory and fill it with the
   immutable candidate and retained-account intent.  It is parsed as plain
   `KEY=value` data, never sourced as shell code.
2. Capture the exact accessibility hierarchy on the installed candidate, then
   copy `selectors.env.example` outside this directory and fill every selector
   used by the chosen phase.  Values are blank by design; locale text and
   accessibility exposure must come from that capture. In particular,
   `E2B_SELECTOR_SAVED_ANSWER` must identify the post-answer feedback control,
   not the selected option text: HARD content may intentionally expose neither
   the option nor a verdict. The read-only Room progress reader, rather than
   that UI control, proves that the exact first answer was retained.
   `E2B_SELECTOR_SETTLE_PENDING` similarly comes from the result receipt's
   pending status, not from a generic local-outbox notification; the following
   server reader proves that its exact SETTLE has not yet been delivered.
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
   the `RATE_LESSON` receipt.  In the retained Maestro image, pass
   `--sqlite3 /tools/sdk/platform-tools/sqlite3` explicitly: `/usr/bin/sqlite3`
   is not installed there.  The reader stays read-only in either runtime.
5. The fresh `settle` invocation must receive
   `--attempt-progress-reader scripts/arena-e2e-attempt-progress-reader.js` and,
   in the retained Maestro runtime, `--sqlite3 /tools/sdk/platform-tools/sqlite3`.
   After the first force-stop/relaunch flow visibly restores the saved-answer
   feedback state,
   the runner keeps the app stopped, copies `schoolquiz.db` with adjacent
   WAL/SHM files to a private temporary directory, runs the reader, then
   deletes the raw copy. Its `post-relaunch-room-progress.json` contains only
   the bound ticket/attempt, checkpoint hashes, and redacted progress flags;
   it contains no owner ID, question ID, selected answer, raw checkpoint, or
   Room mutation payload. The runner then makes one more retained-data launch
   and completes the exact saved ticket.

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

Every receipt reader and the final oracle are invoked with the fixed private
demo Firestore endpoint and empty credential variables. Their JSON is written
through a private temporary file and renamed only after a successful read, so
a failed reader cannot leave a partial receipt artifact behind.

The `settle` phase receives the server context reader plus the local attempt
progress reader. `verify` receives the settlement context, the redacted
post-relaunch Room context, a Room rating-ID context, and the two-receipt
oracle. The oracle gets `--rating-id` and `--rating`, never the local outbox
mutation key.

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

For `settle`, add `--receipt-context <reader-output-after-prepare.json>`,
`--context-reader <arena-e2e-context-reader.js>`,
`--attempt-progress-reader <arena-e2e-attempt-progress-reader.js>`, and an
explicit read-only SQLite binary. Its output must remain `RESERVED` with no
event or receipt. Run `deliver` against that same pending context; it sends the
already saved operation and produces the confirmed context for `rate`. For
`verify`, retain that settlement context, add
`--attempt-progress-context <settle-output/post-relaunch-room-progress.json>`,
`--rating-id-context <room-reader-output.json>`, and
`--receipt-oracle <arena-e2e-receipt-oracle.js>`.

`rate` stops only when the UI confirms that rating `2` is queued locally. Take
the Room ID snapshot at that point, then use `deliver-rating`: it performs
`stopApp`, relaunches without clearing data, re-opens the exact ticket, proves
that the same rating is still `Queued`, and only then sends that exact stored
intent. It never taps `Rate 2` during this recovery path.

If the tooling, rather than the product, interrupts after the first saved
answer, preserve the exact `RESERVED` context and use `resume-settle`; it only
opens that ticket and completes its saved lesson. It never prepares or starts a
replacement.

The Arena screen orders its items as catalog header, reservation header, local
tickets, then lesson cards.  A preparation phase first re-establishes the
catalog-header anchor because preflight intentionally finishes at the selected
lesson card. The flows therefore scroll up from a selected lesson to the
reservation header, and down from that header to the exact
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
