# Appium E2E

This project has an Appium harness for real Android-device checks under `e2e/appium`.
It is meant for flows that must prove the deployed Firebase data and the installed APK work together, not for Compose UI unit tests.

## Setup

```bash
cd e2e/appium
npm install
npm run driver:install
npm run doctor
```

The harness uses Appium with the UiAutomator2 driver and WebdriverIO as the client.

## Arena Sync And Rating Flow

```bash
cd e2e/appium
ANDROID_SERIAL=adb-56271FDCH00C5S-ow9AvR._adb-tls-connect._tcp \
SCHOOLQUIZ_FIREBASE_SERVICE_ACCOUNT=/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json \
npm run test:arena-rating-sync
```

What it verifies on a real Pixel:

- Seeds a real public quest hierarchy into Firestore under catalog `courses`.
- Clears app data, installs the debug APK, launches the app through Appium.
- Reads the fresh anonymous Firebase UID from the app, writes developer access to `users/{uid}` on the server, taps `Синхронизация`, and waits until `Арена` appears in the drawer.
- Opens `Internet -> Arena`, waits until the seeded quest appears from Firestore sync.
- Opens section/theme/lesson, waits until lesson questions appear from `lesson_content` sync.
- Answers the easy question perfectly, submits the local lesson rating, and checks the Room table.
- Confirms hard mode unlock, switches to `Сложный`, answers the hard question, and checks the hard completion result.

Runtime screenshots and page-source dumps are written to `e2e/appium/artifacts/<prefix>/`.

Useful env vars:

- `SKIP_BUILD=1` skips `:apps:android-next:assembleDebug`.
- `CLEAR_APP_DATA=0` keeps current app data.
- `KEEP_E2E_FIXTURE=1` keeps seeded Firestore documents after a successful run.
- `APPIUM_EXTERNAL_SERVER=1` uses an already-running Appium server on `APPIUM_HOST`/`APPIUM_PORT`.
- `APPIUM_SYSTEM_PORT=8201` sets the UiAutomator2 system port for parallel real-device runs.
- `APPIUM_E2E_PROFILE_ROLE=developer` seeds the current anonymous uid with a role profile.
- `CAPTURE_STEPS=0` disables step screenshots.

## Multi-Device Real Runs

```bash
cd e2e/appium
MAX_PARALLEL=3 npm run test:arena-rating-sync:devices
```

The multi-device runner reads `adb devices -l`, excludes emulators by default, builds the APK once,
then runs the arena sync/rating flow on each real device with separate Appium and UiAutomator2 ports.
Each device gets a unique Firestore fixture prefix and one role from:
`developer, participant, tester, moderator, admin, full_access`.

Useful env vars:

- `ANDROID_SERIALS=serial1,serial2` limits the run to specific devices.
- `APPIUM_E2E_PROFILE_ROLES=developer,participant,tester` overrides role assignment.
- `MAX_PARALLEL=2` limits simultaneous devices.
- `INCLUDE_EMULATORS=1` includes emulators in addition to real devices.
- `APPIUM_BASE_PORT=4723` and `APPIUM_SYSTEM_BASE_PORT=8200` change port ranges.

## Codex MCP

There is an Appium MCP server package for Codex-style tool access. Copy the desired block from `e2e/appium/codex-mcp.toml` into `~/.codex/config.toml`, then restart Codex so the MCP tools are loaded.

The normal npm harness is still kept because it is deterministic, reviewable, and can run from CI or from a terminal without depending on a live Codex session.
