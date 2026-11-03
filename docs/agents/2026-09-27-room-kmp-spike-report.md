# Room/KMP toolchain spike — 2026-09-27

## State

- Candidate: `/workspace/projects/schoolquiz3.0-mvp-candidate`
- Branch: `continuation/mvp-candidate-20260926`
- Starting HEAD for this spike: `066651c2fe51ff589e3ce218438f205ac4dbb077`
- Result commit and ending HEAD: `0ff9481e851b4731b37cebaf599662d272218684` (`build(spike): isolate AGP 9 Room/KSP proof`). This is an experimental commit on the candidate branch, not a promotion to the main project.
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

## Environment follow-up (2026-09-27)

- Initial free space before Android verification was 949 MB. Investigation found a 1,769,770,411-byte /var/log/syslog whose repeated Android logd/init messages came from Redroid; it had not changed for over 13 hours, and both rsyslog.service and syslog.socket were inactive.
- Before rotation, /var/log/syslog and /var/log/btmp were copied to the owner's local machine with restrictive permissions. Gzip integrity passed and the SHA-256 of each decompressed copy matched its server source.
- apt-get clean removed only downloaded package archives and freed about 565 MB. The normal scheduled logrotate then moved the closed syslog to /var/log/syslog.1 and created a fresh 0640 syslog:adm file. The rotated file's SHA-256 still matched the preserved copy.
- After confirming no process had the rotated file open, /var/log/syslog.1 was compressed to /var/log/syslog.1.gz (226,835,408 bytes). gzip -t passed and its decompressed SHA-256 matched the source. No log contents were discarded; btmp was left unchanged. Free space is now about 3.0 GB (97% used).
- This is host-storage maintenance only: no product source or SDK package changed. Android Platform 34 and Build Tools 36 are still absent; SDK licenses remain unaccepted pending explicit owner approval.

## Next steps

1. After explicit owner approval, install Android Platform 34 and Build Tools 36.
2. Rerun Android KSP with `--no-build-cache --rerun-tasks`.
