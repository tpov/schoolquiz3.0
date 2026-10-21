
## Session 7b — requeue tail CLOSED, one mapped defect remains — 2026-10-21

- Rig ticket 84174aaa CANCELLED (was intercepting the attempt door); path to the
  8c206849 receipt made clean.
- Requeue criteria VINDICATED: with an EXACT uiautomator-bounds tap on
  «Прийняти та надіслати» the row re-armed CONFLICT→WAITING (attempt_count 1) and
  submitMutation executed (91ms) — the previous "accept did nothing" was an injection
  tap miss, not a requeue defect.
- Mapped defect (next client fix): the engine does not treat the settle ALREADY_EXISTS
  replay (stored settlement, ticket already SETTLED) as success — the row stays
  WAITING/Unknown instead of ACK; ticket state on the server is SETTLED, data intact.

## DRAIN CLOSED (2026-10-21, night) — the consent chain is proven end-to-end on device

One-line code change: NEEDS_RECOVERY added to SYNC_STATUSES (the recovery screen now
offers Sync — the parked row is a durable intent; resending it verbatim is safe, the
server fence is idempotent, and no replacement attempt is created).

Device run on the fresh build: Sync tap on the NEEDS_RECOVERY screen → consent dialog
→ accept → requeue CONFLICT→WAITING → drain → server replays already-settled
(SETTLEMENT_REUSE) → the new mapping maps it to SettlementAlreadyRecorded → the engine
ACKs the row (REMOVED from outbox; mutation_receipts carries the marker
{"settlementAlreadyRecorded":true}) → ticket stays SETTLED. No second settlement.

The whole chain now lives in the shipped build: park (terms-consent) → consent dialog →
acceptCurrentTerms (requeue) → drain → replay-ack. The JVM engine test covers the ack
mapping; the device run covers the flow.
