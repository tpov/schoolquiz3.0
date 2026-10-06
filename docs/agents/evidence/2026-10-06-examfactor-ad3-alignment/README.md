# Exam factor alignment (story 2.10, examfactor-ad3-alignment)

- Result: PASS. Orchestrator gate ran `:shared:feature:lesson-runner:domain:test` and
  `:shared:feature:lesson-runner:data:test` — all green. The gate was executed by the orchestrator,
  not by the implementer (implementer runs were point-compile only, per the night-window rule).
- Date: 2026-10-06.
- Story: `epic-ordinary-delivery` 2.10 `examfactor-ad3-alignment`. Removes the inherited
  `examFactor 0.75` multiplication from the per-question timer, per EXAM:AD-3
  («Every ThemeTest and FinalExam question uses the ordinary HARD answer-time rule …
  seconds=max(5,roundHalfUp(chars*0.24)) … Do not multiply by examFactor0.75», owner decision
  2026-09-30; recorded as standing drift in `online-o2-contract.md` §8, lines 195–207, and in the
  spine 2026-09-29). Ordinary and tournament timers are unchanged: `computeTimer` picks `k` by
  difficulty only, and the LEARNING default behaves exactly as before.

## What changed and why

- `shared/feature/lesson-runner/domain/.../logic/RunnerLogic.kt` (`computeTimer`) — dropped the
  line `val k = if (sessionMode == SessionMode.EXAM) base * coefficients.examFactor else base`;
  `k` is now the plain difficulty coefficient. The `sessionMode` parameter stays in the signature
  (`@Suppress("UNUSED_PARAMETER")`) so every caller is untouched: `submitAnswer`/`advanceRaw`
  (`RunnerLogic.kt:66`, `:229`), `StartLessonAttemptUseCase.kt:92`,
  `ArenaRunRepositoryImpl.kt:195`, `DefaultLessonRunnerRootComponent.kt:197` — exam deadlines are
  pinned by their own ACK contract (EXAM:AD-3/AD-15), not scaled here. KDoc names the rule.
- `shared/feature/lesson-runner/domain/.../model/TimerCoefficients.kt` — removed the now-dead
  `examFactor: Double = 0.75` field (its only use was the deleted line; verified by grep over all
  `*.kt`); the class doc records that there is deliberately no exam factor.
- `shared/feature/lesson-runner/domain/.../SessionModeTest.kt` — the old test
  `an exam gives less time than practice for the same question` asserted the inherited 0.75
  behavior and directly contradicted EXAM:AD-3; it is replaced (not kept alongside) by:
  1. `an exam times the question without the exam factor` — EXAM timer equals the LEARNING timer
     and equals 84s for 232 chars × 0.36 (the old factor would have cut it to 63s); covers the
     five-EASY-theme-positions rule;
  2. `an exam hard question follows the ordinary hard rule` — 100 chars →
     max(5, round(100 × 0.24)) = 24s under `SessionMode.EXAM` (the factor would have squeezed it
     to 18s).

## Implementer point-compile (night-window rule; tests not run by the implementer)

`./gradlew -p <worktree> :shared:feature:lesson-runner:domain:compileKotlinJvm
:shared:feature:lesson-runner:domain:compileTestKotlinJvm
:shared:feature:lesson-runner:data:compileKotlinJvm
:shared:feature:lesson-runner:data:compileTestKotlinJvm --no-configuration-cache`
→ BUILD SUCCESSFUL in 31s (58 actionable tasks); the warnings in the log are pre-existing and sit
outside the changed files.

## What this gate does NOT prove

- No on-device/instrumented tests: nothing ran on a physical phone. A real exam sitting
  (countdown display, ACK-pinned deadlines, timeouts) is not exercised here; device journeys ride
  story 2.7 and acceptance.
- No online/backend verification: no demo-backend call, no Node contract tests; the server half of
  the exam timer rule (the server computes and freezes each allowance — EXAM:AD-3) was not
  touched or validated. This story only removes the client-side drift that
  `online-o2-contract.md` §8 recorded as «дрейф вне этого эпика … снимает экзаменационный
  адаптер».
- The full `ciCheck` (whole-repo `test`/`allTests`, `detekt`, `ktlintCheck`, `assembleDebug`) was
  not run; the gate covered the two named modules only. Static analyzers did not run on the
  changed files in this story.
- Behavioral side effect, intended by EXAM:AD-3 but not independently re-proven here: exam arena
  runs (`ArenaRunRepositoryImpl.FreshIntent.initial`, EXAM mode) now get the ordinary allowance —
  e.g. 24s instead of 18s for a 100-char question. The data-module gate tests compute their
  expected values with the same `computeTimer`, so they prove consistency, not the external
  number.
- The 84s/24s assertions were verified by the orchestrator's green gate run of
  `SessionModeTest`, not by an implementer-executed test run (forbidden in the night window).

## Environment repairs, left uncommitted on purpose

The same two machine-specific build-environment repairs as in 2.1–2.4 are present in the worktree
and are NOT part of this commit (per instruction they stay uncommitted):

- `gradle.properties` — `org.gradle.java.home=/opt/jdks/jdk-27`, memory caps (`-Xmx1400m`/
  `MaxMetaspaceSize=512m`, `workers.max=2`, `kotlin.daemon.jvmargs=-Xmx1024m`) after the host
  OOM-killed the build daemon with the old config.
- `local.properties` — `sdk.dir` repointed from the owner's Mac path
  (`/Users/tpov/Library/Android/sdk`) to `/workspace/infrastructure/android-toolchain/sdk`.

If a fresh machine needs to run the gate, the equivalents of both repairs must be reapplied.
