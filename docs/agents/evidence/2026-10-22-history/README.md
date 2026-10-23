# Epic 8.6 — operation history and earnings read projections — 2026-10-22

Spec: `_bmad-output/implementation-artifacts/spec-epic-8-social-features.md` (baseline 6ac52f2f).
CONTRACT FIRST, then adapters — the UX-O10/O12 gate wording («…before corresponding real
actions»).

## The contract (written before any adapter)

`_bmad-output/mvp-candidate/epic-social-features/ux-o10-own-ledger-projection-contract.md` —
full edition in the `ux-o7-poll-contract.md` format, citing the gate wording (UX-O8/O10/O12) and
AD-30/AD-1/AD-8/AD-9 + UX-14/UX-20. Permitted fields (§3), bounded order + lexicographic
`(atMs, id)` cursors with server-encrypted scopes (§4), pending-vs-confirmed (§5), resources
never summed (§6), per-lesson earnings (§7), account boundaries and zero financial effect (§8),
bounds (§9).

## Server

- Family TZ06B (`functions/arena-lifecycle-views.js`, factory shared with royalty/history
  views) gains `listMyHistory` and `listMyLessonEarnings` — one authorized projection family,
  same `protectedRead` + encrypted cursor machinery:
  - uid from auth only; entries read with `where uid == auth uid`; a foreign lesson is NOT_FOUND
    (indistinguishable from absent);
  - permitted fields only: `{id, atMs, reason, currency, delta, status: "CONFIRMED", lessonId?}` —
    `details`/`sourceId` never travel;
  - pending = own RESERVED arena tickets as separate `{ticketId, atMs, currency, heldAmount,
    status: "PENDING"}` rows (per currency held), never inside `entries`;
  - `startAfter(atMs, id)` bounded pages (default/max 20), `nextCursor` only when a page is
    full, per-scope cursor (`own-history:{uid}`, `lesson-earnings:{uid}:{lessonId}`);
  - earnings query: `reason == arena_author_royalty` AND `details.lessonId == lessonId`.
- `functions/arena-tickets.js`: royalty ledger rows gain `details.lessonId` (additive
  provenance; row id derives from `(ticketId, uid, currency, reason)` and is unchanged;
  settlement logic untouched). Contract rows written before this change read with
  `lessonId: null`.
- Registered callables: `listMyOperationHistory`, `listMyLessonEarnings`
  (LIFECYCLE_CALLABLE_OPTIONS, same dispatch as the other TZ06B views).

## Client

- economy domain: `OperationHistoryRecord`/`PendingOperation`/`OwnHistoryPage` (grouping by
  day, filters and expansion preserve context on the presentation side), repository port,
  `ObserveOwnHistoryUseCase` (owner from auth; A→B→A restarts clean).
- economy data: `OwnHistoryWireParser` (strict; newest-first order verified at parse time;
  empty page never continues), `OwnHistoryRepositoryImpl` (epoch fence — a page requested under
  A is refused once the session is B; nothing is cached).
- platform/firebase: `FirebaseOwnHistoryRemoteDataSource` + `OwnHistoryCallableNamesTest`
  (client strings pinned to `functions/index.js` registrations).
- App shell: new drawer section «История операций» (`DrawerSection.LocalSection.OperationHistory`,
  emptyMap roles) → `LocalConfig.OperationHistoryRoot` → `OperationHistoryScreen`:
  date groups, Все/Начисления/Расходы filters, expandable record (signed amount, currency,
  status), pending card («не считается доходом, пока не разрешится»), distinct
  loading/empty/error/retry, bounded «Загрузить ещё» continuation. RU/UK/EN strings.

## Gates run

- `node own-history-views.test.js` — **5/5** (transcript in
  `../2026-10-22-private-quiz-negative/transcripts.txt`): permitted fields only; newest first;
  bounded pages continuing strictly after the cursor; a foreign uid sees no rows/tickets and
  the foreign lesson is un distinguished from absent; lesson earnings select author-royalty
  rows of that lesson only; reading writes nothing (document count and ledger unchanged).
- Gradle: `:shared:feature:economy:domain:jvmTest`, `:shared:feature:economy:data:jvmTest`
  (new `OwnHistoryRepositoryTest`: pending excluded from confirmed income, resources not summed,
  session change cuts off the late response), `:platform:firebase:testDebugUnitTest` (names
  guard), `:android:feature:app-shell:presentation:testDebugUnitTest` — green.

## Device-proof recipe (owner, account A→B→A)

1. Two accounts on one device (or two devices): A = an arena author with at least one paid
   arena play of their lesson (royalty rows exist), B = any other account.
2. A: drawer → «История операций» — records render, date-grouped; filters switch; «Загрузить
   ещё» continues across pages; pending card shows while an arena ticket is open.
3. Switch to B and open history — none of A's rows/tickets appear.
4. Switch back to A (fast, immediately after triggering a load under B): the screen shows only
   A's records; no stale page from B survives the switch.
5. From the author lesson surface open «Начисления»: only `arena_author_royalty` rows of that
   lesson; no summed cross-resource totals anywhere.

## Left to the owner

- The physical A→B→A pass above (HITL). `detektMetadataCommonMain` remains red tree-wide
  (JDK 27 vs detekt — known, pre-existing, not from this work).
