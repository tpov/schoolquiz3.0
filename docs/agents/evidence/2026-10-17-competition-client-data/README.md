# Story 5.9 — typed KMP client contracts and data layer for competitions

Spec: `_bmad-output/implementation-artifacts/spec-5-9-client-data-layer.md`.
Baseline revision: `0e085be7008d2a74b36532dafd7e78781eb843db` (HEAD matched at start).
Worktree policy: nothing committed; all changes left dirty for the orchestrator to commit.

## What was built

- `shared/feature/tournament/domain` — NEW module: typed mirrors of the server projections
  (`safeEvent`/`safeMeeting`/`safeSession`/`safeQuestion`/`safeResponse`) plus the four remote
  data-source ports (`TournamentEventRemoteDataSource`, `TournamentMeetingRemoteDataSource`,
  `TournamentSessionReadRemoteDataSource`, `TournamentOperationRemoteDataSource`) and
  `TournamentRemoteFailure(kind)`.
- `shared/feature/tournament/data` — NEW module: strict wire parsers
  (`TournamentEventWireParser`, `TournamentMeetingWireParser`, `TournamentSessionWireParser`),
  the outbox codec (`TournamentSessionOutboxCodec` + intents), the receipt parser
  (`TournamentSessionReceiptParser`), repositories (`TournamentCompetitionRepositoryImpl`,
  `TournamentSessionOutboxRepository`), the androidMain `tournamentDataModule`, and
  `jvmTest/resources/tournament-client-fixtures.json`.
- `platform/firebase/.../tournament/` — `TournamentCallableExchange` (ArenaCallableExchange
  composition, not generalized), `TournamentCallableException(code, details)`,
  `tournamentSyncError(reason→SyncError)` and `FirebaseTournamentRemoteDataSource` implementing
  the four ports over the direct callables.
- `shared/core/outbox/.../OutboxOperations.kt` — additive registration only:
  `TOURNAMENT_SESSION_OP = "tournament.SESSION_OP"` + entry in `permanentReceiptOperations`
  (READY_ACK rides the whole operation; never ages to quarantine).
- `functions/index.js` — the `tournament.SESSION_OP` sink in `MUTATION_HANDLERS`
  (uid + payload passthrough into `courseTournaments.session`, refusals mapped to HttpsError with
  `details.reason` via the factored-out `courseTournamentFailure`, the same table the direct
  callables use).
- DI: `FirebaseModule` binds `FirebaseTournamentRemoteDataSource` to the four ports;
  `tournamentDataModule` registered in `apps/android-next/.../AppApplication.kt` (+1 module line,
  +1 dep line in `apps/android-next/build.gradle.kts`); `platform/firebase/build.gradle.kts`
  gained the two tournament project deps.

## Shape mapping (server projection → client model)

| Server (controller.js / service / session) | Client type | Notes |
|---|---|---|
| `safeEvent` (+`prizeView` v0 legacy & v1 list, escrow, cancelReason) | `TournamentEventView` | prizes decode both stored forms: `{listVersion:0, rows[,{maximumLiabilityBoxes}]}` and `{listVersion:1, rows, maximumLiabilityBoxes}`; rows are `{place, amountBoxes}` in both |
| `safeProgress` (resolved/pending/placements) | `TournamentProgressView` | readEvent-only; matches rows `{matchId, stage, stageStartsAtMs, stageEndsAtMs, maxDurationMs, schedulingDeadlineMs}` |
| `fetchCourseTournamentEvents` page | `TournamentEventPage` | cursor invariant enforced: `hasMore ⇔ nextEventId != null` and cursor names the last row |
| `safeMeeting` + `readMeeting` page (offers/confirmations cursors, ready, participants) | `TournamentMeetingView` / `TournamentMeetingPage` | per-page cursor boundness enforced like listEvents |
| `safeSession` (+window) | `TournamentSessionView` | roundNumber/matchId/window optional, exactly as `pick` emits |
| window projection `{windowId, completedQuestionIndex, nextQuestionIndex, windowSeconds, openedAtMs, expiresAtMs, state}` | `TournamentWindowView` | `expiresAtMs` is authoritative for countdowns; `windowSeconds` is the coarse captured constant |
| `safeResponse` (operate / session ops / readSession) | `TournamentOperationView` | `schemaVersion`/`operationId`/`status`/`serverNowMs` optional — the real readSession reply carries none of them (verified against a live dump); `deadlineReached`/`questionDeadlineReached`/`deliveredNowMs` optional; `sessions[]`, `meeting`, `event` decoded when present |
| `safeQuestion` (+`publicPayload` rows `{id,text}`, blanks, protectedTextSegments) | `TournamentPublicQuestion` / `TournamentPublicQuestionPayload` | `admissionLink` accepted by key (`exact("token")`) and decoded into NOTHING — the organizer link token never enters a client type or log |
| ready-ACK receipt `{schemaVersion, questionIndex, windowId, questionId, issuedAtMs, answerDeadlineMs, ackedAtMs}` | `TournamentReadyAckReceipt` | schemaVersion must be 1 |
| result pick `{percentScore, elapsedMs, completedAtMs}` (settled read / COMPLETED op) | `TournamentSessionResult` | percentScore 0..100, finite |
| `recordDownload` `{courseId, downloadedAtMs}` / `…, replayed: true` | `TournamentDownloadRecord` | both exact key-sets accepted; replayed only ever true |
| `prepareCourseTournamentBank` `{snapshotId, counts{easy,hard,total}, timingVersion}` | `TournamentPrepareReply` | |

Fixtures (`tournament-client-fixtures.json`) were dumped from the real `functions/index.js`
over `_firestore-stub` (prepare, create, START, readSession started/after-ack/settled, ANSWER
view, READY_ACK view, both download forms, listEvents page) — see `sourceSha256` inside, which
binds the fixture to `course-tournament-controller.js` and `tournament-session.js` (asserted by
`TournamentEventWireParserTest`). The review fix-round re-dumped the last three hand-shaped
members from the real server over the same stub: the bracket event read (pending match, seeds,
real match ids, stage clocks with the 1h READY_WINDOW), the paged meeting (2-of-3 offers page
with hasMore, both confirmations, one ready signal, WAITING_FOR_READY) and the cursor-
continuation listEvents page (two live events, pageSize 1 → hasMore=true).

## Outbox registration record

- Client: `OutboxOperations.TOURNAMENT_SESSION_OP = "tournament.SESSION_OP"`; key =
  `OutboxOperations.mutationKey(op, sourceId)` with
  `sourceId = <sessionId>-q<index>-answer|advance` or `<sessionId>-q<index>-window-<windowId>`;
  `entityRef = tournament:session:<sessionId>` (namespace matches the operation's `tournament`).
- Server operationId inside the frozen payload: `tsop_<sha256(sourceId)>` — deterministic, so one
  intent is one server operation; the server's own operationId idempotence is the backstop.
- Payload per kind is exactly the callable allowlist:
  ANSWER `{kind, sessionId, operationId, questionIndex, questionId, answerPayload}`,
  ADVANCE `{kind, sessionId, operationId}`,
  READY_ACK `{kind, sessionId, operationId, questionIndex, windowId}`. No clocks, no
  formFingerprint (the controller derives it from the stored session), no ownerUid.
- `permanentReceiptOperations` now includes `TOURNAMENT_SESSION_OP` (READY_ACK receipts are
  permanent; the record never ages to quarantine).
- Server: `MUTATION_HANDLERS["tournament.SESSION_OP"]` in `functions/index.js` →
  `courseTournaments.session(uid, payload)`; controller refusals are rethrown as HttpsError with
  `{reason}` details, so queue refusals surface the same structural reasons as direct calls.
- Client reason mapping (`tournamentSyncError`): named refusals (NO_ADMISSION_BASIS,
  OUTSIDE_QUALIFICATION_WINDOW, CONFLICTING_REPLAY, PREPARATION_REUSE, STALE_PREPARATION,
  QUESTION_ALREADY_CLOSED, ANSWER_PENDING, INVALID_*, INSUFFICIENT_*, FAILED_PRECONDITION,
  PERMISSION_DENIED, …) → `SyncError.Refused(reason)` (QUARANTINE); UNAVAILABLE /
  SCORING_UNAVAILABLE / INCONSISTENT_* → `SyncError.Unknown()` (RETRY — server repair, never
  claimed as "no network"); SOURCE_TOO_LARGE → Refused; missing reason → shared
  `syncErrorForCode`; unknown future reason → `SyncError.Unknown()`. Message text is never parsed.

## Gate outputs

- `sgw ./gradlew :shared:feature:tournament:domain:jvmTest :shared:feature:tournament:data:jvmTest
  :shared:core:outbox:jvmTest :platform:firebase:test --no-configuration-cache`
  → tournament domain+data 29/29 green (23 parser/contract tests + 6 outbox-contract), outbox
  jvmTest green incl. `OutboxOperationsRegistryTest` (client↔server operation registry),
  platform:firebase 290/292 — the 2 failures are PRE-EXISTING at the baseline revision:
  `FirebaseArenaTicketRemoteDataSourceTest.fixtureHashesAndCallableNamesAreBoundToActualServerSources`
  and `FirebaseArenaLessonCatalogRemoteDataSourceTest.generatedPublicContract…` hash-drift
  (`functions/arena-tickets.js` changed in commit 34fe664b "share eligible lesson universe"
  after the arena fixture JSON was last generated; file untouched by this story —
  `git diff HEAD -- functions/arena-tickets.js` is empty).
- `cd functions && /root/.zcode/server/node run-tests.js` → **110 of 110 suites passed**
  (109 baseline suites intact + the new `tournament-session-sink.test.js`; 4 tests: sink happy
  path + replay-returns-same-receipt/no-second-effect, READY_ACK permanence, structured refusal
  reasons through the queue, owner derived from auth).
- `git status --porcelain -- android/` → empty (no UI code; apps/ carries only the DI
  registration line and its dependency line).
- `:apps:android-next:compileDebugKotlin` → BUILD SUCCESSFUL (DI registration compiles).
- ktlint: `:shared:feature:tournament:domain:ktlintCheck`,
  `:shared:feature:tournament:data:ktlintCheck` → green. detekt: tournament modules report
  NO-SOURCE exactly like all sibling KMP feature modules; `:platform:firebase:detekt` and
  `:platform:firebase:ktlintMainSourceSetCheck` fail on PRE-EXISTING baseline issues
  (detekt `--jvm-target 27` unsupported by the pinned detekt version; ktlint violations in
  arena/economy/question-window files untouched by this story).

## Known risks / notes for reviewers

- `sdk.dir` in `local.properties` (untracked) was repointed from a stale Mac path to
  `/usr/lib/android-sdk` so the VPS can build android modules at all.
- The two arena hash-drift test failures and platform:firebase detekt/ktlint failures pre-date
  this story; they need a fixture regeneration + a baseline decision, out of scope here.
- `answerPayload` travels as an opaque JSON object (frozen at enqueue, budget-checked ≤16 KiB);
  typed answer modelling stays with the runner integration in story 5.10.
