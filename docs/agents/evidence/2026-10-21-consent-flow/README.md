
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
