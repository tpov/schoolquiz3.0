# Lesson runner JVM proof — 2026-09-27

## Starting state

- Candidate: `/workspace/projects/schoolquiz3.0-mvp-candidate`
- Branch: `continuation/mvp-candidate-20260926`
- Source HEAD: `485c21f1b40b046d79b210586d6befb938b1ff9d`
- The existing uncommitted performance change in `StartLessonAttemptUseCase.kt` and its `StateMachineTest.kt` test were preserved and compiled from their real source directories. They were not changed by this validation step.

## What was validated

Added `toolchain-spike/lesson-runner-jvm`, an isolated JVM-only Gradle root. It reads the real source directories for the lesson-runner domain and its manually declared dependency closure, while avoiding the repository root's AGP 8.11 configuration path that fails under Gradle 9.8 because it calls the removed Gradle `InternalProblems` API.

Toolchain: server JDK 27, Gradle 9.8.0 wrapper, Kotlin Multiplatform/serialization 2.4.20. One worker; Gradle and Kotlin daemon memory capped by the harness settings.

Commands run from the candidate root:

- `./gradlew -p toolchain-spike/lesson-runner-jvm :shared:feature:lesson-runner:domain:jvmTest --tests 'com.tpov.schoolquiz.shared.feature.lesson_runner.domain.StateMachineTest' --no-configuration-cache --no-build-cache --rerun-tasks --max-workers=1`
- `./gradlew -p toolchain-spike/lesson-runner-jvm :shared:feature:lesson-runner:domain:jvmTest --no-configuration-cache --no-build-cache --rerun-tasks --max-workers=1`
- `git diff --check`

Results:

- Targeted `StateMachineTest`: 8 tests, 0 skipped, 0 failures, 0 errors. The report includes `questionPreparationAndSelectionUseInjectedDispatcher`.
- Full lesson-runner domain JVM suite: 31 test classes, 213 tests, 0 skipped, 0 failures, 0 errors.
- `git diff --check`: passed.

## Limits and remaining work

- This is a focused JVM proof, not the regular root build, an Android build, an APK validation, or evidence of improved first-question latency on the Samsung A14.
- The harness manually reproduces the domain dependency declarations and only enables a JVM target. Android source sets, Room/KSP Android generation, UI, network/backend behavior, and device tests are not covered.
- The test result does not approve the uncommitted performance change by itself. Next, compare a known baseline APK with a candidate built from the same source on the physical A14, measuring tap-to-first-render with equivalent app data and network state.
- The Android SDK is still incomplete on the server and SDK licenses were not accepted. Obtain the owner's approval before accepting licenses/installing SDK packages; first check available free space.
- Continue the Arena server restore and end-to-end evidence, then work through the remaining TZ06–TZ10 and final TZ11 matrix. This proof does not make the project release-ready.
