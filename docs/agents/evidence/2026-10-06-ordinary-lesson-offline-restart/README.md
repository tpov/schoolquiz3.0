# Ordinary lesson completes offline and survives restart (story 2.1)

- Result: PASS. Orchestrator gate ran `:shared:feature:lesson-runner:domain:test`,
  `:shared:feature:lesson-runner:data:test`, `:shared:core:persistence:test`,
  `:android:feature:lesson-runner:presentation:test` — all green. The gate was executed by the
  orchestrator, not by the implementer (implementer runs were compile/dry-run only, per the
  night-window rule).
- Date: 2026-10-06.
- Story: `epic-ordinary-delivery` 2.1 `ordinary-lesson-offline-restart` (covers CAP-1); done-when:
  an ordinary lesson completes offline from prepared stock and shows the same attempt/outcome after
  restart; a ready saved question appears without an added network gate. Proof form mandated by the
  ticket: JVM tests of attempt/outcome persistence and RunnerLogic transitions, restart simulated
  by reopening the database.

## What changed and why

The production code was already in the tree (ticket status note: "весь нужный код в дереве"), so the
story is delivered by tests plus the gate-wiring they need.

- `shared/core/persistence/src/jvmTest/.../LessonAttemptIntentPersistenceTest.kt` — extended the
  existing JVM persistence suite with the restart branch:
  `restartReopenShowsTheSameAttemptOutcomeAndTheStillWaitingQueueRow` (offline save of
  attempt+answers+intent+WAITING outbox row → `db.close()`/reopen → identical rows, the same
  attempt/outcome via `observeByLesson` and the earnings projection, no mutation receipt while the
  row is unsent) and `afterRestartIdenticalResaveAddsNoRowAndChangedOutcomeConflicts` (identical
  re-save after reopen adds no queue row; a changed outcome throws `AttemptIntentConflict` and the
  first result stays).
- `shared/feature/lesson-runner/domain/src/commonTest/.../OrdinaryLessonOfflineRestartTest.kt` —
  new RunnerLogic-transition proof: start serves the prepared stock with zero network-shaped calls
  (recording fakes count `refreshByParents` — the CAP-1 network-gate negation), answers run to the
  sentinel with one stable attempt identity, completion saves exactly once with outcome 100%, and
  after "restart" the same attempt/outcome is read back while a new start works offline again.
- `shared/feature/lesson-runner/data/src/jvmTest/.../OrdinaryLessonOfflineRestartTest.kt` — the
  same scenario over production wiring: `LessonAttemptRepositoryImpl` + `RoomLessonResultOutboxWriter`
  against a file-backed Room database (seeded catalog→quest→section→theme→lesson), restart by
  closing and reopening the same file; the same attempt/outcome, answers and repetition schedule
  survive, the outbox row stays WAITING with no receipt, identical re-save does not duplicate, a
  changed outcome is rejected.
- `shared/core/persistence/build.gradle.kts`, `shared/feature/lesson-runner/domain/build.gradle.kts`,
  `shared/feature/lesson-runner/data/build.gradle.kts` — gate wiring: `<module>:test` must resolve.
  Persistence (plugin `schoolquiz.kmp.library.agp9`) had no exact `test` task and the bare name was
  ambiguous (`testAndroid`/`testAndroidHostTest`), so an exact `test` task is registered with
  `dependsOn(jvmTest)`. Domain/data (legacy `com.android.library`) already get AGP's `test`
  lifecycle in `afterEvaluate`; `jvmTest` is added to it there so the JVM tests run under
  `<module>:test`.

## What this confirms

An ordinary lesson completes fully offline from the local question stock; the attempt, its outcome,
the answers and the repetition schedule survive a process restart simulated by reopening the same
database file; the result is readable while the outbox row has never left WAITING and no server
receipt exists — no network gate is consulted anywhere on the start or read path
(`StartLessonAttemptUseCase` reads only `QuestionRepository.observeByLesson`, the recording fakes
prove `refreshByParents` is never called; the contract draft §7 explicitly forbids adding a network
gate for an offline-ready question).

## What this gate does NOT prove

- No instrumented/device tests: `androidDeviceTest` suites (e.g. `LessonAttemptDaoTest` on a real
  Android database) did not run — the scope rule for this story is code+JVM only, and no physical
  device was involved. Real-phone confirmation rides epic-candidate-rig stories 1.3/1.5, story 2.7
  and final acceptance.
- No online/backend verification: nothing here exercises the server, the outbox drain or a
  settlement receipt. Delivery of the queued row to an authoritative outcome is story 2.2
  (`outbox-120-drain`); the row intentionally remains WAITING in this proof.
- No ONLINE-O2 coverage: the online next-question transition is stories 2.5/2.9/2.6 (blocked); the
  offline path here is exactly the one the ONLINE-O2 draft says it does not touch.
- The full `ciCheck` (whole-repo `test`/`allTests`, `detekt`, `ktlintCheck`, `assembleDebug`) was
  not run in this story; the ktlint filter of the KMP convention plugin excludes `*Test/` paths, so
  the new test sources were not linted either.
- `android:feature:lesson-runner:presentation` was not modified; its green `:test` in the gate only
  confirms the presentation suite still passes against the shared modules, not anything new.

## Environment repairs, left uncommitted on purpose

Two machine-specific build-environment repairs were needed on this VPS and are NOT part of the
commit (per instruction they stay uncommitted in the worktree):

- `gradle.properties` — `org.gradle.java.home=/opt/jdks/jdk-27` (toolchain(27) is not auto-detected
  without JAVA_HOME here), memory caps (`-Xmx1400m`/`MaxMetaspaceSize=512m`, `workers.max=2`,
  `kotlin.daemon.jvmargs=-Xmx1024m`) after the host OOM-killed the build daemon during test
  execution with the old `-Xmx4096m`/unbounded-workers config.
- `local.properties` — `sdk.dir` repointed from the owner's Mac path
  (`/Users/tpov/Library/Android/sdk`) to `/workspace/infrastructure/android-toolchain/sdk`.

If a fresh machine needs to run the gate, the equivalents of both repairs must be reapplied.
