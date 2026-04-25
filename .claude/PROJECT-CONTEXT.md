# Project Context — schoolquiz4.0

## Firebase

- **Project ID**: `school-quiz-89336951`
- **Package**: `com.tpov.schoolquiz.next` (new KMP app), legacy `com.tpov.schoolquiz`
- **Admin SDK service account key**: `/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json`
  - `client_email`: `firebase-adminsdk-h5hhr@school-quiz-89336951.iam.gserviceaccount.com`
  - Используется для backfill / admin-scope Firestore операций через Admin SDK (Node/Python).
  - Client-side `google-services.json` — `apps/android-next/google-services.json` (для public reads с rules).

## Build / Validation

- Canonical app build: `./gradlew assemble --no-configuration-cache`
- Canonical tests: `./gradlew allTests --no-configuration-cache`
- Instrumented tests: `./gradlew connectedAndroidTest` (требуется подключённое устройство)

## DI

- **Koin** (`io.insert-koin:koin-*`). Manual DI, composition root — `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt` startKoin.
- Project does NOT use Hilt / Dagger — exclusive binding rule not applicable.
- Canonical Koin module list — see `docs/features/home-and-my-quests/06-api-contract.md` §13 (SSoT).

## Architecture

- **KMP** (commonMain / androidMain / jvmMain), Android target + JVM test target.
- Layers: `domain` (Kotlin pure), `data` (Room + Firebase adapters), `presentation` (Decompose Components), `ui` (Compose).
- Structure: `shared/{core,feature}/*/{domain,data}`, `platform/firebase`, `platform/android-services`, `android/{core,feature}/*/presentation`, `apps/android-next`.
- Decompose Components — `ADR-CMP-51` pattern (see `docs/features/home-and-my-quests/03-decisions.md`).

## Testing

- JVM tests: JUnit 4 + MockK + coroutines-test.
- Convention: **fakes** for repositories/DAOs (see `.claude/rules/testing.md`). No Turbine.
- Test locations: `*/src/{test,jvmTest,commonTest,androidTest}/`.

## Known Debts (post-feature follow-ups)

- `getKoin()` anti-pattern — resolved in phase-05 via DefaultRootComponent preconstruction.
- Firestore nested content visibility (sections/themes/lessons/questions) — Option C (any-auth read) per ADR amendment; strict parent lookup deferred post-MVP.
- Cursor strategy — unified `Clock.System.now()` via CascadingSyncOrchestrator (see `03-decisions.md:804` Amendment).
- Backfill script (`scripts/backfill-catalogs.js` — TBD) для добавления `lastModifiedAt/version/contentsVersion/archived` существующим Firestore документам.
