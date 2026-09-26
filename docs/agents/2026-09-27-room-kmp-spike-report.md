# Room/KMP toolchain spike — 2026-09-27

## State

- Candidate: `/workspace/projects/schoolquiz3.0-mvp-candidate`
- Branch: `continuation/mvp-candidate-20260926`
- Starting HEAD for this spike: `066651c2fe51ff589e3ce218438f205ac4dbb077`
- Result commit and ending HEAD: `9e08858471b183384937d42548eea09539f51347` (`build(spike): isolate AGP 9 Room/KSP proof`). This is an experimental commit on the candidate branch, not a promotion to the main project.
- The existing lesson-runner performance changes and JDK/Gradle changes were already dirty before this spike and are not included in its commit.

## Scope and changes

Added an isolated `toolchain-spike` build root that includes the real persistence dependency closure (13 KMP modules) without changing the repository's normal settings or version catalog. The spike uses JDK 27, Gradle 9.8.0, AGP 9.4.0, KGP 2.4.20, KSP 2.3.12, Room 2.7.0, and SQLite 2.5.0. Android bytecode remains 17.

The 13-module closure uses a separate AGP 9 KMP convention. Persistence keeps its existing Android test sources in place, maps them to the new device-test source set, and registers Room schemas as device-test assets.

## Evidence

- `./gradlew -p toolchain-spike help --no-configuration-cache --max-workers=1`: passed.
- `:shared:core:persistence:tasks --all`: passed; Android Device Test, JVM, and KSP tasks are registered.
- `:shared:core:persistence:dependencies --configuration androidCompileClasspath`: passed; the project dependency variants resolved without ambiguity.
- `:shared:core:persistence:kspKotlinJvm`: passed and generated 22 Room implementation files, including `AppDatabase_Impl`.
- `:shared:core:persistence:compileKotlinJvm`: passed.
- `:shared:core:persistence:jvmTest`: passed, 27 suites / 209 tests / 0 failures / 0 errors / 0 skipped.
- Root `:buildSrc:compileKotlin`: passed with the repository catalog; root build configuration then failed at `apps/android-next` because AGP 8.11 calls Gradle's removed `InternalProblems` API (removed in Gradle 9.6). This reproduces the pre-existing toolchain blocker.
- `git diff --check`: passed.

## Blockers and limits

- `:shared:core:persistence:kspAndroidMain` did not run. The server SDK at `/usr/lib/android-sdk` has no Platform 34 or Build Tools 36 and no accepted SDK licenses. Gradle stopped before KSP execution. Nothing was installed and no license was accepted.
- Android generated code, Android compilation/AAR, schema asset packaging, lint, and device migration tests remain unverified.
- The server root filesystem had about 980 MB free at the end of the run (99% used). Do not start an SDK install/build-tools download until space is addressed.
- This spike does not validate the 48-module migration or the application APK. Do not promote it as a release candidate.
- One initial compile attempt exhausted the isolated Gradle daemon's default 384 MB metaspace. With spike-local limits of 1 GB heap / 768 MB metaspace, the JVM compile and test gates passed.

## Next steps

1. Reclaim or expand server disk space and get owner approval to install the Android platform/build-tools and accept their licenses.
2. Rerun Android KSP with `--no-build-cache --rerun-tasks`.
3. Verify Android compilation/AAR, schema assets, lint, and device migration tests.
4. Only if those pass, ask Astra to reassess expanding the migration to the remaining KMP modules.
