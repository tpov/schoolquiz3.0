# Adoption of the ONLINE-O2 contract (story 2.9)

- Result: DONE. The owner approved the full edition of the ONLINE-O2 contract on 2026-10-08;
  the decision is recorded and tied to the exact edition of the document, which is the story's
  done-when («решение владельца, привязанное к конкретной редакции документа»).
- Date: 2026-10-08.
- Story: `epic-ordinary-delivery` 2.9 `adopt-online-o2` (covers CAP-1, CAP-18) — the third,
  «adopted» part of the epic's Done-when 5.

## The decision and the edition it names

- Owner decision 2026-10-08, point 1: «УТВЕРЖДЕНА полная редакция контракта ONLINE-O2»
  (recorded verbatim-framed in `_bmad-output/mvp-candidate/owner-decisions-2026-10-08.md`,
  together with the four other same-day decisions). This closes morning-report question О7:
  the 06.10 answer had approved only the answer-clock rule, leaving the full edition open.
- The edition adopted is the working-tree document at
  `_bmad-output/mvp-candidate/epic-ordinary-delivery/online-o2-contract.md` — the «полная
  редакция (история 2.5, ночь 2026-10-06)» written by story 2.5 and committed in `cf8884dd`.
  Identity of the edition: 252 lines, unchanged since `cf8884dd`
  (`git diff cf8884dd HEAD -- <path>` is empty), git blob
  `e142341e0baaa76c59a0e918bf7a89e567f3dc32`.
- The adoption note is written into the contract header itself («Принято владельцем
  2026-10-08»), so the document carries its own approval status next to the checklist it
  closes; no content change was made — the gate checklist coverage stays as 2.5 wrote it:
  (1) completion event §2–§3; (2) delivery/ready-ACK receipt by mode §4 (EXAM:AD-3 §4.1, §8);
  (3) identity/retry §6; (4) answer-clock link by mode §5; (5) lost-ACK without an infinite
  gap or substitute clocks §7.

## What this unlocks

- Consumers of the contract (epics 3/5/6 inceptions and any next-question adapter work) are no
  longer gated on 2.9; the epic's Done-when 5 is fully closed: written (2.5) → proven (2.6,
  `1b207cc2`) → adopted (2.9, this decision).
- Per the owner's same-day instruction, epics 3/4/7/8 inceptions still require a separate
  owner approval before any work starts (owner-decisions file, point 5).

## Deliberate consequences

- The contract keeps its §8 position: EXAM AD-3 («a question deadline starts at its first
  acknowledgement») remains existing evidence, not an automatic overwrite of other lifecycles;
  nothing in the adoption re-opens the 06.10 clock-rule decision.
- No code changed in this story. The verification chain for the contract's behavior is 2.5's
  JVM identity/retry tests and 2.6's one-real-transition proof (`OnlineO2IdentityRetryContractTest`,
  `lesson-ready-ack-handler` wiring); the remaining red of that wiring suite is chartered as
  story 2.13 and is not part of this story's scope.
