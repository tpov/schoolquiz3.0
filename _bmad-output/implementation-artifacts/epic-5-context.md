# Epic 5 Context: Competitions: qualification tournament to championship
<!-- Generated from planning artifacts. Regenerate with compile-epic-context if planning docs change. -->

## Goal

From a published, eligible course to a settled championship: an organizer creates a funded competition whose content and full prize fund freeze while the course stays in the library; qualification (турнир) selects an immutable top16 from the whole eligible cohort via three distinct EASY attempts; qualified players agree match times, play under the common online timing rule, and see the final bracket and awards (чемпионат) with the fund conserved. Covers CAP-11/CAP-12; competition server machinery and tournament/championship UX only — not Arena ratings/seasons, not content publication.

## Stories

- Story 5.1: online-o1-timing-contract
- Story 5.2: prize-list-contract
- Story 5.3: admission-and-discovery
- Story 5.4: server-dynamic-prize-list
- Story 5.5: qualification-loop-close
- Story 5.6: sessions-online-o2
- Story 5.7: meeting-restore-uxo3
- Story 5.8: awards-conservation
- Story 5.9: client-data-layer
- Story 5.10: client-tournament-ux
- Story 5.11: client-championship-ux
- Story 5.12: demo-e2e-server
- Story 5.13: device-proof-hitl (hitl, blocked on physical devices and owner visual acceptance)

## Requirements & Constraints

- Terms: турнир is the qualification part, чемпионат the complete final bracket/match part; a competition contains both; historical `tournament*`/`event` identifiers stay technical.
- Fund: bank must cover the full reserve before acceptance; frozen fund equals the sum of each prize row exactly once (no automatic second third place); conservation reserved = paid + returned + remaining, terminal remaining = 0; retries reuse the same event, never a second reserve.
- Prize list: one versioned dynamic ordered list drives CREATE/read/reserve/rank/payout; legacy fixed {first,second,third} events stay readable and immutable. A single rank/tie/technical-outcome→place mapping (absences, ties, byes, FORFEIT/DOUBLE_OUT, shared third place, 0/1/2/15/16/17-participant cases) must be adopted before server CREATE/reserve on the new list.
- Qualification: top16 from the full eligible cohort at cutoff; phase state (cursor/revision/lease/fencing) survives interruptions; no permanent wait above 501 accepted attempts; CANCELLED below two qualifiers. Owner TZ07A decision: fix early Number rounding for future unpublished calculations only; keep the LS formula and tie-break order; published rankings/brackets immutable, never recomputed; legacy publisher fenced once a calculation version is selected.
- Admission: exactly one admitted basis — downloaded corresponding course OR valid competition link — bound to account+courseId+tournamentId, only inside the server window [qualification.startsAtMs, qualification.endsAtMs); late request or retry creates no new admission; a returned receipt preserves history without reopening access; the link never bypasses stages or championship selection; earned championship rights survive link expiry.
- Meeting rules: C = stage end − 1h − form duration; proposal until C, slot between server now and C, confirmation until C and not later than the slot, READY inside [slot, slot+1h); PROPOSE never implies CONFIRM; per-slot proposals/confirmations persist independently (a radio choice cancels neither); the agreed slot is immutable; only the server starts a match when both are READY; no new turn-order concept.
- Test sizes, not product caps: 0/1/2/15/16/17 participants; 502 attempts; 502 distinct players; 1503 attempts; 4096/4097 as calculation stress tests.

## Technical Decisions

- AD-17: admission, fund/escrow, exact qualification calculation (resumable VALIDATE→…→PUBLISHED phases, full-cohort input hash, server-fixed calculation/rounding versions, exact rationals ≤3×3) and the meeting rules above.
- AD-23: worker capability separated from its invoking environment — bounded server-owned sweeps with cursor/lease/fencing resume after crash; demo controlled ticks invoke the same capability without an external scheduler; the client never sets server time or worker operations.
- AD-31: a question's original deadline runs regardless of connectivity; expiry closes it unanswered; then a captured 60-second next-question window starts at the accepted timely answer or original expiry — never at reconnect or a late worker. No common mode controller; the exam keeps its own first-ACK-fixes-deadline rule.
- ONLINE-O1 (gate entry, story 5.1): reconcile the 60s window with the legacy fixed endsAt = start + form.duration and championship stage/match cutoffs as an explicit, versioned time budget with cutoff priority — no silent window truncation, no silent stage/endsAt extension; adopted before dependent adapters are enabled.
- ONLINE-O2 (adopted): completion = timely accepted answer or original expiry; idempotent ready-ACK receipt keyed (attempt identity, questionIndex, windowId); non-exam answer clocks start at server delivery inside the window; retry returns the same question with server-remaining time — no second clock, no unbounded gap after a lost ACK, no window after the last question; client mutations ride the outbox with frozen keys/bodies and permanent receipts.
- Server competition machinery (state machine, sweep worker, escrow, exact calculation with 4096/4097 cases green) exists; the client has no bindings yet — journeys are built functional in current conventions; the final NOIR restyle belongs to a later epic.

## UX & Interaction Patterns

- Decompose + Compose, RU/UK/EN, AD-21 action states (local saved/pending/unknown/stale/rejected/confirmed/forbidden/empty plus retry/recovery), semantic navigation and route restore; no artificial loading delays.
- Honest server-authoritative surfaces: outside-window/refusal admission, meeting expiry states, per-row award receipts with the returned remainder shown.
- Leaderboard projection: averagePercent display-only; place and ratingPercent authoritative.
- Redaction: private forms, keys and private answer payloads never appear in DTOs, UI or logs; projections carry only allowlisted fields.

## Cross-Story Dependencies

- 5.1 (ONLINE-O1 reconciliation) and 5.2 (prize-list/mapping contract) are the leading contract drafts; their owner-adopted editions gate dependents: 5.1 → 5.5, 5.6, 5.7; 5.2 → 5.4.
- 5.4 + 5.6 + 5.7 → 5.8 (awards conservation); 5.4/5.6/5.7 also stabilize DTO shapes for 5.9 (client data layer).
- 5.3 + 5.5 + 5.9 → 5.10 (tournament UX); 5.8 + 5.9 → 5.11 (championship UX); 5.8 + 5.10 + 5.11 → 5.12 (demo e2e); 5.10 + 5.11 + 5.12 → 5.13 (HITL device proof).
- Prior-epic prerequisites are done in build terms (shared online window contract, Arena attempt cycle/full-cohort calculation, published eligible material); their device-proof hitl tails remain owner items blocking final acceptance, not dispatchable work.
