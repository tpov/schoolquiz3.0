=== PHASE 01 — TESTS ===

Начни работу НЕМЕДЛЕННО, без ack и без ожидания подтверждения.

Feature: settings-app-version-footer
Ticket: SCH-2
Workspace: /Volumes/EXTERNAL/schoolquiz3.0

Твоя роль: прочитай `.claude/agents/test-dev.md`.

Прочитай базовые правила проекта: `AGENTS.md`, `.claude/PROJECT-CONTEXT.md`, `.claude/rules/testing.md`.

Прочитай только документы, нужные для тестовой части:
- `docs/features/settings-app-version-footer/plan/phase-01/tests.md`
- `docs/features/settings-app-version-footer/plan/phase-01/overview.md` (acceptance criteria + validation only)
- `docs/features/settings-app-version-footer/0-spec.md` (GIVEN/WHEN/THEN acceptance criteria)
- `docs/features/settings-app-version-footer/04-testing.md`

Ты работаешь параллельно с frontend-dev. Build gate и ревьюеров запускает frontend-dev после своего build PASS; ты их не запускаешь.

Write scope:
- Primary: `android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt`
- Test helpers in the same androidTest source set only if necessary.

Do not edit production code. Do not edit scaffold files or add dependencies. If a dependency/scaffold change seems necessary, report it as an Open Question instead of changing it.

Implement/update tests and guards from `tests.md`:
- exact label `v0.1.0 (1)`;
- display-only semantics (no click/long-click on settings footer);
- pinned visible-bottom bounds / not final short-list item;
- stale-call-site compile coverage through required params and updated `AppShellScreenTest` call site;
- drawer version behavior unchanged guard;
- debug/release visibility guard by construction/grep expectations.

If production API changes have not landed yet, write tests against the approved expected signatures from `06-api-contract.md` and leave any compile dependency to the frontend-dev build gate. Do not patch production to make tests compile.

You may run lightweight grep/static checks if useful, but do not run the phase build gate. Final report must include:
- added/updated test files;
- coverage table mapping spec/phase scenarios to test methods;
- behavior covered;
- remaining gaps/open questions;
- validation you ran, if any.
