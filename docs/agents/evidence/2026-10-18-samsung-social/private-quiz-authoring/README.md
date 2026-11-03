# Story 8.3 (private quiz authoring) — blocked by missing category — 2026-10-18 night

Device: Samsung A14 (the overnight device), fresh 4.0.0-isolated build d0c59bfc…, fresh
account UserOVDSRF, catalog synced live from the demo backend (1 quest per MVP category).

- 01-inbox.png — «Вхідні» (Inbox) empty: nothing addressed yet.
- 02-new-quest.png — «Створення квесту» authoring form opens (category/type/section/theme/
  lesson dropdowns, language, «Легкі/Складні питання» entries).
- 03-category-dropdown — the category picker lists ONLY: Усі категорії, MVP checks,
  MVP validated checks. «Приватні квизи» / «Приватные квизы» is absent.
- Server check (admin read of the demo Firestore): 49 catalog documents, **0** contain any
  private marker — the AD-29 category «Приватные квизы» does not exist on the demo backend.

Consequence: the private quiz authoring journey (flat HARD-only set, attempt limit ≥1,
show-result default hidden, optional link expiry, defaults kept on reload) cannot be reached
on any device until the category is seeded server-side (or the app grows a way to create it).
8.3 stays blocked; the delivery foundations it waits on (epic-2) are done, so the next step is
a server-side catalog fixture or a delivery change — then this story's device proof can run
immediately (the authoring form itself is alive and driving fine).
