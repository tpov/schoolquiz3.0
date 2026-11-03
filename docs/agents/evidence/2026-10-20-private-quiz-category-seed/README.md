# «Приватные квизы» category seeded — 8.3 blocker removed — 2026-10-20

The 8.3 blocker (2026-10-18-samsung-social/private-quiz-authoring: the AD-29 category was
absent from all 49 demo catalog docs) is removed by an idempotent server-side seed:

- `private-catalog-seed.js` — creates `catalogs/private-quizzes-v1-catalog`
  {name: «Приватные квизы», questType: REGULAR, version: 1, archived: false}; refuses to
  overwrite a diverged doc; second run prints ALREADY SEEDED. AD-29: the category is NAMED
  «Приватные квизы», technical ids stay technical — REGULAR because a private quiz is a
  scored HARD-only set (not a survey), per the enum docs.

Device proof (Samsung A14, UserXYBDI8, catalog sync 00:56):

- 81-dropdown.png — «Мої квести» category picker now lists: Усі категорії, MVP checks,
  MVP validated checks, **Приватные квизы**.
- 81-create-dropdown.png — the «Створення квесту» form category picker lists it too (the
  exact picker the 2026-10-18 finding showed without it).
- 81-selected.png — the category selected in the creation form; the form still renders the
  standard Квест/Розділ/Тема/Урок hierarchy — the flat HARD-only adaptation and the
  attempt-limit/show-result fields are 8.3 remaining work, now RUNNABLE on this device.

Side observation in the same pass: the drawer profile header renders «Premium» (server word
from the premium grant issued tonight), and the polls screen holds Poll_run_8.4 from earlier
testing.
