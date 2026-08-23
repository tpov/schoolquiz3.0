=== PHASE 01 — FRONTEND (владелец ревью-цикла фазы) ===

Начни работу НЕМЕДЛЕННО, без ack и без ожидания подтверждения.

Feature: settings-app-version-footer
Ticket: SCH-2
Workspace: /Volumes/EXTERNAL/schoolquiz3.0

Твоя роль: прочитай `.claude/agents/frontend-dev.md`.

Транспортная адаптация этого запуска: role-specific `kent run --agent <role>` недоступен в текущей среде; доступен default Kent subagent. Поэтому, когда тебе нужно поднять ревьюера или test-dev для фикса test finding/build failure, вызывай:

`/opt/homebrew/bin/kent run --workspace /Volumes/EXTERNAL/schoolquiz3.0 "<self-contained assignment that tells the subagent to read .claude/agents/<role>.md>"`

Не используй `--agent`. Если сам nested `kent run` вернул фактическую ошибку запуска, заверши ран `NESTED_SPAWN_UNAVAILABLE` с дословной ошибкой и готовым ASSIGNMENT FROM CODER.

В этой фазе нет `backend.md`; владелец ревью-цикла — ТЫ. Второго coder-а в фазе нет, ждать frontend/backend-coder не надо. Отдельный `test-dev` запущен параллельно лидом по `tests.md`; он не поднимает ревьюеров и не запускает build gate.

Лид пассивен: ты владеешь фазой целиком — реализация → build gate → вызов ревьюеров → fix loop → финальный RESULT. Лиду промежуточные сообщения не шли, кроме ERROR/NESTED_SPAWN_UNAVAILABLE.

## Шаг 1 — Реализация

Прочитай базовые правила проекта: `AGENTS.md` и `.claude/PROJECT-CONTEXT.md`.

Прочитай только документы фазы/контекста, нужные для твоей реализации:
- `docs/features/settings-app-version-footer/plan/phase-01/frontend.md`
- `docs/features/settings-app-version-footer/plan/phase-01/overview.md` (только scope, review tags, acceptance criteria, validation)
- `docs/features/settings-app-version-footer/06-api-contract.md`
- project rules: `.claude/rules/navigation.md`, `.claude/rules/clean-architecture.md`, `.claude/rules/kotlin-conventions.md`

Production write scope:
- `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt`
- `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt`
- `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt`

Do not edit androidTest files unless a later build/review finding is explicitly routed to a new test-dev run. Do not edit scaffold files (`build.gradle.kts`, `libs.versions.toml`, `settings.gradle.kts`, Gradle wrapper, root `AndroidManifest.xml`, `gradle.properties`). Do not add domain/data/storage/network/Koin/DI changes.

Implement the approved behavior:
- pass app `BuildConfig.VERSION_CODE` from app layer with existing `VERSION_NAME`;
- keep app `BuildConfig` reads app-module-only;
- add required `appVersionCode: Int` parameters with no defaults through AppShell/settings Compose APIs;
- render settings footer exactly as `v<versionName> (<versionCode>)`, pinned to visible settings viewport bottom, centered, small, low-emphasis, display-only;
- preserve drawer footer/About/repeated-tap developer-mode behavior and existing design-style selection/persistence.

## Шаг 2 — Build Gate (запускаешь ты, не лид)

Before build/review, include the test-dev changes if they have landed. The parallel test-dev output path is `docs/features/settings-app-version-footer/run/agents/phase-01-test-dev.out`, and the expected test file is `android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt`. Do not block indefinitely: if test-dev output is not finalized after a reasonable wait, continue and report the gap in Open Questions.

Run the phase gate yourself:
- `./gradlew ciCheck --no-configuration-cache`
- all grep guards and Gradle commands in `docs/features/settings-app-version-footer/plan/phase-01/overview.md` Validation
- because this phase includes androidTest updates, include `./gradlew assembleDebugAndroidTest --no-configuration-cache`
- Test Deletion Gate: `git diff --name-status HEAD -- '*/test/**'`; every deleted test must be listed in phase overview Deleted Files. Otherwise restore it.

Build FAIL:
- production error in your scope → fix and retry;
- test-code error → start a new default Kent subagent with a self-contained test-dev assignment (read `.claude/agents/test-dev.md`, include evidence file:line/stacktrace/expected behavior), wait for it, then retry;
- unclear/repeated failure → start a default diagnostics/code-analyst subagent with evidence from Team Composition Proposal triggers, wait for root cause/routing, then act;
- scope change / architectural mismatch → return ERROR to lead, do not improvise.

Do not start reviewers before Build Status is PASSED.

## Шаг 3 — Reviewers (ты поднимаешь сам после build PASS)

Mandatory reviewers for this phase:
- `code-reviewer`
- `architect-reviewer`
- `security-reviewer`
- `completeness-reviewer`

No `concurrency-reviewer` unless your implementation deviates into coroutines, Flow collection, lifecycle callbacks, channels, or shared mutable state. If that happens, add concurrency-reviewer and state the deviation in final RESULT.

For each reviewer, start a separate default Kent subagent. The assignment must be self-contained and tell it to read its role file in `.claude/agents/<role>.md`, `AGENTS.md`, `.claude/PROJECT-CONTEXT.md`, `docs/features/settings-app-version-footer/plan/phase-01/overview.md`, and its responsibility scope. Include the exact string:

`Build Status: PASSED (commit <sha-or-phase-ref>)`

Use the full changed-file list from `git diff --name-only HEAD` for this phase, including `AppShellScreenTest.kt` if test-dev changed it.

Reviewer scope hints:
- code-reviewer: Kotlin/Compose API changes, stale call sites, test changes, drawer preservation.
- architect-reviewer: app BuildConfig boundary, app-shell → settings dependency direction, Compose screen as view function, no domain/data/DI/scaffold coupling.
- security-reviewer: passive metadata display only; no storage, analytics, hidden interaction, network, auth, debug gating, or sensitive data exposure.
- completeness-reviewer: acceptance criteria end-to-end, exact label, pinned bottom, small grey centered styling, display-only, debug/release availability, stale call sites, drawer unchanged.

Reviewer final report contract: `FINDINGS: ...` or `review passed, 0 open findings`.

## Шаг 4 — Autonomous fix loop

For reviewer findings:
- your production scope → fix, rerun relevant gate, re-check the same reviewer with `kent run --session <reviewer-session-id>`;
- test code → spawn a new default test-dev run with self-contained evidence, wait, rerun gate, re-check reviewer;
- repeated blocker/high of same class, reviewer disagreement, or architectural mismatch → ERROR lead.

## Шаг 5 — Финальный RESULT

Return one final RESULT to lead only after all mandatory reviewers pass.

Include:
- changed files for the phase;
- Build Status and validation commands run;
- AC coverage;
- closed findings;
- reviewer session ids;
- open questions/gaps (if any).
