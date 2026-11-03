# Story 5.11 — championship client journeys: agreement, match play, bracket and awards

Spec: `_bmad-output/implementation-artifacts/spec-5-11-client-championship-ux.md` (route: full,
baseline `996e171435208b0adc74c3b7d80bbbe5f46120de`).
Worktree policy: nothing committed; all changes left dirty for the orchestrator to commit. Only
this story's files were touched (see `git status --porcelain` — the other dirty entries under
`.working/`, `exports/` etc. belong to the parallel session and were left alone).

## Journeys → components → tests

All in `android/feature/tournament/presentation` unless noted. The 5.10 component/screen/DI/test
layout is extended, not reinvented; the final NOIR polish stays epic 9.

| Journey | Component | Mounted at | Tests |
|---|---|---|---|
| Match agreement (readMeeting render; PROPOSE/CONFIRM/READY with honest boundary refusals; restore by re-reading) | `TournamentAgreementComponent` / `DefaultTournamentAgreementComponent` | opened from the bracket's pending-match action (one destination with the bracket, like Browser+Admission in 5.10) | `DefaultTournamentAgreementComponentTest` (9): propose renders the offer per author and re-reads the meeting; re-entry rebuilds identical server facts (and clears the slot input); propose after agreement is refused `FAILED_PRECONDITION` → `MEETING_BOUNDARY` with the agreed slot kept; retried propose keeps one operationId, a new slot gets a fresh one; confirm rides the offer's slot; READY before the window is refused, inside it the server returns the caller's own MATCH session (`sessions[]` is server-filtered to the caller, captured into `playSessionId`); load-more pulls the next offers page by cursor and appends; empty/non-numeric slot input never sends an operation; closing drops meeting + play session |
| Match play (the 5.10 session machine on the MATCH kind: outbox ANSWER/READY_ACK, window by `expiresAtMs`) | `TournamentMatchPlayComponent` / `DefaultTournamentMatchPlayComponent`, both over the shared play machine `TournamentSessionPlayComponent` (extracted verbatim from the 5.10 qualification component; the qualification component now extends it with zero public-API change) | opened from the agreement surface when the meeting is STARTED and the server has created the caller's session | `DefaultTournamentMatchPlayComponentTest` (5): open plays the question and answers through the outbox (answer receipt carries the OPEN window → READY_ACK enqueued → next question with the ack window id); settled session shows the server result without invented time; closing the match leaves answers in flight unapplied (generation guard + dropped pending answer); re-read of the same question keeps the collected selection; reopening re-reads the session by id |
| Bracket view (immutable published state: rounds/matches from readEvent `matches[]` + `progress`, placements for FINISHED) | `TournamentBracketComponent` / `DefaultTournamentBracketComponent` | `EventsConfig.WorldChampionshipRoot` (supersedes the static `TournamentEventContent` hub for this destination, as 5.10 superseded the qualifier hub) | `DefaultTournamentBracketComponentTest` (4): rounds/matches render from the published progress (resolved with winner, pending with entrant uids); the bracket is immutable across re-reads (detail equal); FINISHED carries terminal placements (rank/uid/slot name) and escrow truth (remaining=0, paid, returned); closing drops the detail |
| Awards / results (placements + escrow truth; receipts stay server-side) | `TournamentResultsComponent` / `DefaultTournamentResultsComponent` | opened from the bracket's FINISHED detail ("Open results") | `DefaultTournamentResultsComponentTest` (3): placements + terminal escrow render (reserved = paid + returned + remaining, remaining=0 as a server fact); refusal renders honestly with results closed; reopening reads the same publication again |
| Koin wiring | `tournamentPresentationModule` | `AppApplication.kt` | `TournamentPresentationKoinWiringTest` (1): all eight journeys (5.10 four + 5.11 four) resolve over the shared 5.9 layer |
| Events-stack mounting | `DefaultEventsTabComponent` gains four factories with Placeholder defaults; `EventsScreenComponent.Championship(bracket, agreement, matchPlay, results)`; `DefaultRootComponent` gains the four factories; `AppShellPresentationModule` resolves them `runCatching → Placeholder`; `TournamentChampionshipContent` in `AppShellScreen` switches bracket → agreement → match play / results with retain-safe overlay flags | — | `DefaultRootComponentTest` (2 new): the world destination mounts the four injected layers; without a binding it falls back to the four placeholders |

## The one shared-machine refactor (flagged)

`DefaultTournamentQualificationComponent`'s play machinery (answer build, outbox enqueue, receipt
polling, READY_ACK, window-edge re-read, AD-14 pending observer, `act`/notice handling) moved
verbatim into an abstract base `TournamentSessionPlayComponent` with a narrow `TournamentPlayHost`
interface (play state, notice, server-now, round merge, generation, busy). The qualification
component implements the host over its existing `QualificationUiState`; the match-play component
implements it over `MatchPlayUiState`. Both play the same machine; the "no qualification-surface
regressions" gate is the untouched 14-test suite, which passes unchanged.

## Shared additive fix (required for the awards journey — flagged)

The server freezes placement `slot` as the prize-slot NAME (`"champion"`, `"runner-up"`,
`"third-upper"`, `"third-lower"` — `tournament-policy.js` `progressBracket`/`planPrizePayouts`),
but the client mirror typed it `Long` and parsed `requiredWhole`, so any FINISHED read with
placements would have failed closed as `MalformedWire` before this story:

- `shared/feature/tournament/domain/.../TournamentEventModels.kt` — `TournamentPlacementRow.slot`
  is now `String` (the server's slot name).
- `shared/feature/tournament/data/.../TournamentEventWireParser.kt` — `nonBlank("slot")` instead of
  `requiredWhole("slot")`. Meeting math, prize arithmetic and all server files are untouched;
  `functions` suite stays 111/111.

## Honest states (AD-21) and contract facts the UI does not re-interpret

- One shared `TournamentNotice` vocabulary; the meeting-boundary refusal got its own client branch:
  the server's typed `FAILED_PRECONDITION` (the 5.7 meeting boundary refusals are the only client
  operations that carry it) maps to `TournamentRefusal.MEETING_BOUNDARY`; unknown names still fall
  to OTHER showing the server reason verbatim. Branching stays structural, never by message text.
- Boundaries are rendered from the 5.7 projection only: coordination deadline C
  (`schedulingDeadlineMs`), stage window, agreed slot, readiness window `[slot, slot+1h)` via
  `windowEndMs`. The client never derives policy: PROPOSE/CONFIRM/READY are always sendable and
  the server's typed refusal is the honest answer; after every operation — success or refusal —
  the meeting is re-read (refusal notice preserved), so both participants see identical facts and
  restore-on-re-entry is literally the same read.
- PROPOSE never implies CONFIRM; the agreed slot is rendered as immutable server fact; the client
  never starts a match — the READY response merely carries the session the server already created
  (the controller filters `sessions[]` to the caller's own uid, so no foreign session id can reach
  the client).
- Operation identity: one operationId per (intent, match, slot) born on first attempt and kept on
  retries; a new slot is a new intention; READY is keyed per match. Idempotency stays with the
  server's operationId discipline.
- testTags on slot rows (`tournament_agreement_offer_*`, `tournament_agreement_confirm_*`), READY
  rows (`tournament_agreement_ready*`), the agreed slot and window lines, and money rows
  (`tournament_results_escrow_*`, placement rows in both bracket and results).
- RU/UK/EN strings for every new surface (module strings.xml now 145 entries per language,
  mirrored 1:1).

## Destination mapping (recorded decisions)

- `WorldChampionshipRoot` → the championship route (bracket/agreement/match play/results).
  This supersedes the static world-championship hub screen (`TournamentEventContent`), which 5.10
  had kept; the standalone `ui/TournamentEventScreen.kt` composable stays in the tree because the
  app-shell androidTest still exercises it directly. The world lessons-shelf entry points that
  lived on that static hub are therefore no longer on the Events tab; the leaderboard/participants
  destinations (`WorldChampionshipLeaderboardRoot`/`...ParticipantsRoot`) keep the legacy shelf
  screens and the legacy stack stays unforked.
- `admissionContinueDestination` still routes admitted events to the qualifier participation
  destination: the event projection carries no marker that would honestly distinguish a
  championship event, and inventing a classification would violate the no-local-policy rule.
  Deferred until the server projection says which shelf an event belongs to.

## Gate outputs (actual)

- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :android:feature:tournament:presentation:test
  :android:feature:app-shell:presentation:testDebugUnitTest :apps:android-next:compileDebugKotlin
  --no-configuration-cache` → **BUILD SUCCESSFUL** (rerun with `--rerun-tasks`: BUILD SUCCESSFUL in
  4m11s). Tournament presentation: 51 tests green — qualification 14 (5.10 suite, untouched),
  admission 5, organizer 6, browser 4 (5.10 pre-existing), agreement 9, match play 5, bracket 4,
  results 3, Koin wiring 1. App-shell: `DefaultRootComponentTest` 27 tests, 0 failures, 1 skipped
  (the pre-existing `@Ignore`d deferred retap test).
- Extra safety for the shared parser change: `:shared:feature:tournament:data:jvmTest` → green
  (run together with the gate).
- `cd functions && /root/.zcode/server/node run-tests.js` → **111 of 111 suites passed**
  (no server changes in this story).
- ktlint/detekt were not run in this pass (same posture as the 5.10 evidence); the gate above is
  exactly the spec's verification command set.

## Rig notes / rig-visual strings for the owner

- No emulator, no app run, no screenshots (per worktree policy); journeys are verified by JVM
  component tests over the same fakes that mirror the 5.9/5.7 contracts. New user-visible strings
  are exactly the additions in
  `android/feature/tournament/presentation/src/main/res/values{,-ru,-uk}/strings.xml`
  (`tournament_agreement_*`, `tournament_meeting_*`, `tournament_match_play_*`,
  `tournament_bracket_*`, `tournament_results_*`, `tournament_refusal_meeting_boundary`) — owner to
  review wording before release; none are rig-asserted.
- Slot entry is a raw epoch-milliseconds text field: honest, but a date/time picker is obvious
  NOIR-polish work for epic 9, as is everything else visual here.
- Match-session recovery after process death is bounded by the server surfaces this story may use:
  readMeeting does not carry the caller's session id, so the play surface is reachable from the
  agreement surface either immediately after READY (response carries the session) or by re-issuing
  READY while the readiness window is open (idempotent on the server, returns the same session);
  outside the window the server refuses typed and the refusal renders. A read-driven session
  lookup needs a server projection change and was out of scope by the spec's "no new server
  surfaces".
- The bracket's per-round grouping uses the readEvent `matches[]` `stage` field; resolved/pending
  correlation is by `matchId` from `progress` — both server facts; unknown matches render their id.
- Placement rows render the server's slot name verbatim (`champion`, `runner-up`, …); no
  localization mapping was invented (epic 9 material if wanted).

## Review pass 1 (2026-10-17) — applied fixes

All in `android/feature/tournament/presentation` unless noted. Nothing committed (same worktree
policy); gates re-run after the pass (actual outputs at the end).

1. **FAILED_PRECONDITION scoping** (`model/TournamentNotice.kt`): the name→branch table now takes
   an `agreementSurface` flag — on the agreement surface (PROPOSE/CONFIRM/READY + the meeting
   READ, i.e. every `failureNotice` call site in `DefaultTournamentAgreementComponent`) it maps to
   `MEETING_BOUNDARY` as before; everywhere else (qualification START, session/bracket reads) the
   same refusal is honest OTHER with the server reason verbatim. Reason: the server sends
   `FAILED_PRECONDITION` for qualification-window refusals too (`tournament-service.js:244/249`,
   `details.reason = error.code`), so the old global mapping rendered "meeting boundary" for a
   closed qualification window. Tests: qualification `failed precondition outside the agreement
   surface stays out of the meeting branch` (→ OTHER); agreement keeps MEETING_BOUNDARY (existing
   propose test + the READY test now asserts the branch).
2. **Confirm input precedence** (`DefaultTournamentAgreementComponent.onConfirmClick`): row-level
   Confirm always sends the row's slot; the draft input only feeds PROPOSE. Test: input "999" +
   row confirm → request carries the row's slot.
3. **Window countdown fabrication** (`TournamentMatchPlayScreen.WindowNextLine`): the line is
   hidden when `serverNowMs` is absent instead of subtracting from `0L` (epoch-sized count).
4. **Agreement empty state** (`TournamentAgreementScreen`): with no match open the slot input,
   Propose, READY and Play controls are not rendered — only the honest notice.
5. **Confirm buttons retire**: row Confirm disables when a slot is agreed or that slot is already
   confirmed by this user (`rowConfirmEnabled`, pure function over the meeting page). Test:
   `TournamentAgreementRowConfirmTest` (agreed → disabled; own confirmation → disabled; another's
   confirmation → still available; busy → disabled).
6. **onOpenMatch state hygiene**: opening match B now clears the previous match's `playSessionId`
   (verified it persisted — fixed) and the slot input. Test added.
7. **Overlay open discipline** (`AppShellScreen.TournamentChampionshipContent`): `onOpenMatch`
   (`TournamentAgreementComponent`), `onOpenMatchSession` (`TournamentMatchPlayComponent`) and
   results `onOpenEvent` (`TournamentResultsComponent`) now return whether the component accepted
   the open (busy/missing-session guards); the overlay flags rise only on acceptance.
8. **Back discipline**: the system-back handler invokes the same close operations as the on-screen
   back buttons (`onCloseMatchSession` / `onCloseMatch` / `onClose`) via shared close lambdas — no
   stale session observation or loaded results survive a back press.
9. **Bracket component** (`DefaultTournamentBracketComponent`): a `detailGeneration` counter
   captured at accept-time rejects an in-flight read after the detail was closed; the refresh
   cursor reset moved after the busy check (a busy-refused refresh no longer orphans the cursor).
   Tests: close-while-in-flight does not reopen; busy refresh does not lose the load-more cursor.
10. **Play machine** (`TournamentSessionPlayComponent`): (a) the window-edge re-read runs through
    `act`, so a failed edge is an honest notice (failureNotice), not silence; (b) the same `act`
    entry respects the busy guard — an edge firing mid-delivery is skipped, and the delivery's own
    completion re-reads and re-arms the edge (the old call site had no busy check — confirmed and
    fixed); (c) **claim not confirmed**: an unknown payload type still makes submit a silent no-op
    (`buildAnswerPayload` `else -> null`), no Retry is written — left as is (same posture as the
    empty-selection no-op), flagged for the owner; (d) the four state writes of `readSessionInto`
    stay separate but are documented as unobservable-intermediate: there is no suspension point
    between them, so no other coroutine can interleave on the dispatcher thread.
11. **Meeting paging** (`DefaultTournamentAgreementComponent`): a page with `hasMore` but a null
    cursor is normalized to not-pageable (`honestPageable`) and `onLoadMoreClick` additionally
    requires a live cursor — no cursorless first-page re-read loop; the button disappears (honest
    end state). Test added.
12. **Match play open hygiene** (`DefaultTournamentMatchPlayComponent.onOpenMatchSession`): an
    accepted open stops the previous session's observation up front, so a failed open cannot leave
    the previous session's observer running. Test: after a failed open, an account switch + outbox
    emission does not turn the notice into AccountChanged.
13. **Post-refusal re-read**: after an operation refusal the re-read is contained — its own
    failure cannot overwrite the preserved refusal notice. Test added.
14. **Formatting/invented facts**: agreement times (C deadline, stage window, agreed slot,
    readiness window, offer/confirmation/ready timestamps) now render via `formatSyncTimestamp`
    in all three locales (strings moved from `%1$d ms` to `%1$s`); a participant seed the server
    did not name renders "—" instead of an invented `0`; meeting status literals became constants
    (`MEETING_WAITING_FOR_AGREEMENT/…FOR_WINDOW/…FOR_READY/FORFEIT/DOUBLE_OUT`) next to the
    session ones.
15. **Duplicate notice writes**: `afterOperation` is now the single writer for operation outcomes
    (the failure notice is written once; the re-read is contained; the trailing `getOrThrow`
    rethrow that double-wrote through `act`'s catch is gone) — `act`'s catch remains only the
    safety net for exceptions outside the outcome. Covered by the fix-13 test and the existing
    refusal tests.
16. **Parser contract test** (`shared/feature/tournament/data`): new `eventReadFinished` fixture
    (FINISHED, resolved final, frozen placements `champion`/`runner-up`, terminal escrow) +
    `TournamentEventWireParserTest` cases: slot names decode as strings, and a numeric slot fails
    closed (`MalformedWire`).
17. **Missing agreement tests**: stale-page CONFIRM refused (confirm a slot the read page never
    showed; refusal preserved, fresh truth re-read) and READY after the window edge refused
    (MEETING_BOUNDARY, no ready row) added.
18. **Dead strings**: `tournament_bracket_pick_title` removed in all three locales (was unused);
    `tournament_agreement_confirmations` is now wired as the confirmations section heading.
19. This section. Notes: the orphaned `ui/TournamentEventScreen.kt` (app-shell) stays — the
    androidTest `AppShellScreenTest` still exercises it directly; cleanup belongs to epic 9. The
    overlay-composition test gap (fixes 7/8 are Compose-level) stays deferred: there is no JVM
    Compose harness in the repo; the components and the pure row-confirm predicate on both sides
    of the overlays are tested.

Review-pass-1 gates (actual outputs):

- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :android:feature:tournament:presentation:test
  :android:feature:app-shell:presentation:testDebugUnitTest :apps:android-next:compileDebugKotlin
  --no-configuration-cache` → **BUILD SUCCESSFUL in 2m19s**; tournament presentation 65 tests /
  0 failures (agreement 15, match play 6, bracket 6, qualification 15, row-confirm 4, results 3,
  admission 5, organizer 6, browser 4, wiring 1), app-shell 54 tests / 0 failures / 2 skipped
  (pre-existing `@Ignore`s), `:apps:android-next:compileDebugKotlin` ok.
- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :shared:feature:tournament:data:jvmTest
  --no-configuration-cache` → **BUILD SUCCESSFUL**; 41 tests / 0 failures
  (`TournamentEventWireParserTest` now 11). Bare `sgw` without `JAVA_HOME=/opt/jdks/jdk-27` fails
  at configure time in this shell ("Cannot find a Java installation … languageVersion=27") —
  environment default JDK, not a code change.
- `cd functions && /root/.zcode/server/node run-tests.js` → **111 of 111 suites passed** (no
  server changes in this pass).
