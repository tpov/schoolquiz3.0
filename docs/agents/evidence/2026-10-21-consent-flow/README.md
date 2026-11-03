
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

## DRAIN PROVEN FROM DEVICE ROOM (2026-10-21, late) — the tail is fully closed

Fresh Room pull from the Samsung (.mvp, the mapping build): outbox table EMPTY — the
re-armed WAITING SUBMIT_ATTEMPT row was drained by the app itself (background drain on
the fresh build) and ACKed by the new mapping. mutation_receipts carries the marker:

  lesson_runner-SUBMIT_ATTEMPT-2e4b91c8… -> {"settlementAlreadyRecorded":true}

Server: ticket 8c206849 SETTLED (exactly once, from the rig fallback earlier). The
consent chain is closed end-to-end INCLUDING on-device delivery: park → dialog →
accept → requeue → drain → replay-ack → marker receipt. Nothing left in the outbox.

## UI-prep pass (2026-10-21, night) — the consent-fixed build plays the prep journey

On the fresh build (consent flow + mapping + NEEDS_RECOVERY sync): INTERNET →
«Випадковий урок» → Складно → Підтвердити → «Заявку записано» (request 95915030) →
server ISSUED ticket f26936e0 (RESERVED, HARD, 0 nolics). The UI prep journey works
end-to-end on the consent-fixed build.

Honest limit: driving the full play (open ticket → runner → answers → settle) from adb
is blocked by scroll-navigation flakiness under injection (long permissions list); the
journey needs Maestro flows or a finger. Everything server-side around it is proven.
