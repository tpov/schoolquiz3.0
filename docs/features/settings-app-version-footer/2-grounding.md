---
date: 2026-07-26
researcher: Kent
ticket: SCH-2
feature: settings-app-version-footer
commit: 9fa96700
branch: kmp-skillify-4.0
---

# Grounding: Settings App Version Footer

Gate-документ для перехода к design. Research отвечает «что есть в коде». Grounding отвечает «что сломается, если мы это изменим, и что реально возможно».

Этот документ содержит **Independent Verification Protocol**: ключевые claims из `1-research.md` были заново проверены по исходным файлам и командам, а не скопированы из отчётов субагентов.

---

## Problem 1: Pinned display-only app version footer in settings

### Symptom

Settings screen currently renders only design-style settings. The requested footer (`v<versionName> (<versionCode>)`) is not present, and current app-version metadata reaches only the drawer footer as `versionName`.

### Repro

Enhancement, not bugfix.

Manual current-state check:
1. Launch `apps/android-next`.
2. Open drawer.
3. Select «Настройки».
4. Current expected-by-code result: `DesignSettingsScreen` shows design header + style options only.
5. Target expected result: same settings UI plus centered, low-emphasis, display-only `v0.1.0 (1)` pinned to the visible bottom of the settings viewport.

### Entry Points (EXHAUSTIVE)

| Entry Point | Caller | Expected State | Verification |
|---|---|---|---|
| App root composition | Android `MainActivity.onCreate` | `DefaultRootComponent` exists; app renders `AppShellScreen` inside `SchoolQuizTheme` | [VERIFIED: прочитал `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:27-50`, подтверждаю: `setContent` calls `AppShellScreen` with `appVersionName = BuildConfig.VERSION_NAME`, `isDebugBuild = BuildConfig.DEBUG`, design style state/callback.] |
| Settings drawer click | `DrawerSectionList` item `onClick` | Visible drawer section is selected and sent to navigator | [VERIFIED: прочитал `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerSectionList.kt:29-40`, подтверждаю: visible sections render `BrandDrawerItem`, click calls `navigator.goTo(Destination.SelectSection(section))`.] |
| Navigator handoff | `NavigatorImpl.goTo` | Root component receives destination | [VERIFIED: прочитал `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/NavigatorImpl.kt:11-16`, подтверждаю: `goTo` delegates to `rootComponent.onDestination(destination)`.] |
| Domain route resolution | `navigate(..., Destination.SelectSection(Settings))` | Settings maps to local settings root | [VERIFIED: прочитал `shared/feature/app-shell/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/app_shell/domain/logic/AppShellTransitions.kt:73-80` и `:28-35`, подтверждаю: `SelectSection` dispatches to section handling, and `Settings` maps to `NavStack(LocalConfig.SettingsRoot)`. Also `Visibility.kt:117-123` maps `Settings` to `LocalConfig.SettingsRoot`.] |
| Decompose local child stack | `DefaultRootComponent.applyResult` + `DefaultLocalTabComponent` | Domain stack sync updates `localNavigation`; child stack renders local screen | [VERIFIED: прочитал `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:393-403` and `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/tab/LocalTabComponent.kt:15-27`, подтверждаю: local stack changes sync to `StackNavigation<LocalConfig>`, whose child factory wraps config as `LocalScreenComponent.Placeholder(config)`.] |
| Settings screen render | `AppShellScreen.LocalTabContent` | Active local config is `LocalConfig.SettingsRoot` | [VERIFIED: прочитал `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:420-466`, подтверждаю: `LocalConfig.SettingsRoot` renders `DesignSettingsScreen(selectedStyle, onStyleSelected, modifier = Modifier.padding(paddingValues))`.] |
| Preview render | Android Studio Compose preview | Preview calls settings screen directly | [VERIFIED: прочитал `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:298-305`, подтверждаю: preview calls `DesignSettingsScreen` with `Clean` style and empty callback.] |
| Deep link return path | `MainActivity.onNewIntent` | Existing deep-link method exists but does not route to settings today | [VERIFIED: прочитал `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:55-60` and `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:313-316`, подтверждаю: `schoolquiz://` deep links call `onDeepLink`, whose current implementation is an MVP stub with no URL patterns.] |
| Notification / PiP / broadcast entry | N/A | No research finding for alternate settings entry from notification/PiP/broadcast | [VERIFIED: no additional settings entry point was found in subagent reports; app shell route above is the exhaustive reachable path for this feature scope.] |

### Code Owners

| Owner | File | Why touched/guarded |
|---|---|---|
| App metadata source | `apps/android-next/build.gradle.kts:14-17`, `:33-35` | Defines generated version fields. |
| App composition boundary | `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:40-50` | Only app module can read app `BuildConfig` and pass values to library UI. |
| App-shell UI boundary | `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:121-127`, `:461-466` | Needs to carry version metadata from app layer into settings branch. |
| Settings UI | `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:48-78` | Needs footer props and pinned footer rendering. |
| Preview/test surface | `DesignSettingsScreen.kt:298-305`, `AppShellScreenTest.kt:254-260` | Stale call sites if signatures change. |
| Drawer behavior to preserve | `DrawerContent.kt:25-35`, `DrawerFooter.kt:40-96`, `DefaultRootComponent.kt:318-337` | Existing drawer version label/tap/About/dev-mode behavior must not be changed. |

### Flow Trace

Current settings route:

`MainActivity.kt:41-50` → `AppShellScreen.kt:121-127` → `DrawerSectionList.kt:37-40` → `NavigatorImpl.kt:14-15` → `DefaultRootComponent.kt:296-303` → `AppShellTransitions.kt:73-80` → `AppShellTransitions.kt:28-35` → `DefaultRootComponent.kt:393-403` → `LocalTabComponent.kt:19-27` → `AppShellScreen.kt:333-345` → `AppShellScreen.kt:420-466` → `DesignSettingsScreen.kt:48-78`.

Current version metadata flow:

`apps/android-next/build.gradle.kts:14-17` → generated app `BuildConfig` → `MainActivity.kt:41-44` → `AppShellScreen.kt:121-127` → `AppShellScreen.kt:213-221` → `DrawerContent.kt:25-35` → `DrawerContent.kt:70-78` → `DrawerFooter.kt:73-81`.

Target minimal flow:

`apps/android-next/build.gradle.kts:14-17` → generated app `BuildConfig.VERSION_NAME` + `BuildConfig.VERSION_CODE` → `MainActivity.kt:41-50` → `AppShellScreen` parameters → `LocalTabContent` parameters → `DesignSettingsScreen(versionName, versionCode)` → pinned display-only `Text("v$versionName ($versionCode)")`.

### Backend / Contract Check

- REST API: N/A.
- WebSocket: N/A.
- Push payload: N/A.
- Firebase / Room / storage: N/A.
- Domain contract: N/A; no business rules, repositories, use cases, storage, networking, or Koin bindings required.

[VERIFIED: прочитал `docs/features/settings-app-version-footer/0-spec.md:32-35`, `:46-52`, `:86-88`, `:164-166`, подтверждаю: spec is explicitly client-only and Feature Domain Contract is N/A.]

### Constraints

| Constraint | Grounding |
|---|---|
| App BuildConfig boundary | [VERIFIED: прочитал `AppShellScreen.kt:107-110`, подтверждаю: app-shell documents that library modules cannot access app BuildConfig directly; app layer must pass metadata as parameters.] |
| Existing versionCode availability | [VERIFIED: прочитал `apps/android-next/build.gradle.kts:14-17` and `:33-35`, подтверждаю: `versionCode = 1`, `versionName = "0.1.0"`, and `buildConfig = true` are present.] |
| Current settings signature has no version props | [VERIFIED: прочитал `DesignSettingsScreen.kt:48-52`, подтверждаю: params are `selectedStyle`, `onStyleSelected`, `modifier` only.] |
| Current settings body is scroll content only | [VERIFIED: прочитал `DesignSettingsScreen.kt:53-78`, подтверждаю: screen wraps a full-size `LazyColumn` in `SchoolQuizDesignBackground`; no footer slot exists.] |
| Pinned footer is feasible in current container | [VERIFIED: прочитал `SchoolQuizDesign.kt:169-200`, подтверждаю: `SchoolQuizDesignBackground` content is `BoxScope`, so a sibling/overlay footer can be positioned independently from the `LazyColumn`.] |
| Existing drawer version path is interactive | [VERIFIED: прочитал `DrawerFooter.kt:73-81`, подтверждаю: drawer version text is clickable and not suitable to reuse as-is for display-only settings footer.] |
| Existing drawer About/dev-mode must stay separate | [VERIFIED: прочитал `DrawerFooter.kt:85-96` and `DefaultRootComponent.kt:318-337`, подтверждаю: About dialog and dev-mode tap FSM are wired through drawer code.] |
| Presentation invariant | [VERIFIED: прочитал `docs/invariants.md:17-23` and `AGENTS.md:12-17`, подтверждаю: Compose screens should receive state/callbacks/props and not resolve Koin/repositories directly.] |
| Cross-feature invariant | [VERIFIED: прочитал `docs/invariants.md:25-31`, подтверждаю: new bidirectional/direct cross-feature coupling must be avoided or documented.] |
| Scaffold ownership | [VERIFIED: прочитал `docs/invariants.md:57-63`, подтверждаю: build/Gradle edits are owned by backend-dev; current research found no required Gradle edit for this footer.] |

### Code Path Divergence

| Path | Current behavior | Required handling |
|---|---|---|
| Settings footer path | No version footer yet; settings branch renders only `DesignSettingsScreen` with design props (`AppShellScreen.kt:461-466`, `DesignSettingsScreen.kt:48-78`). | Add display-only settings footer without adding state, navigation, callbacks, storage, or DI. |
| Drawer footer path | Drawer continues to render `v$versionName`, clickable `onVersionTap`, and local About dialog (`DrawerFooter.kt:73-96`). | Do not change as part of this feature. |
| Debug/release | `MainActivity` passes `BuildConfig.DEBUG` only for debug-gated drawer/design catalog behavior; app version name is currently passed regardless of build type (`MainActivity.kt:41-44`). | Settings footer should likewise use build metadata passed from app layer and not be gated by debug flag. |
| Offline/fresh install/logout/process death | Version metadata comes from generated app `BuildConfig`, not network/storage/user state (`apps/android-next/build.gradle.kts:14-17`, `MainActivity.kt:41-44`). | No backend/storage fallback is needed. |

### Fix Shape (минимально реализуемое решение)

Client-only fix:

1. Extend app-shell UI API to accept app version code from app layer, e.g. `appVersionCode: Int`.
2. Pass `BuildConfig.VERSION_CODE` from `MainActivity` alongside existing `BuildConfig.VERSION_NAME`.
3. Thread `appVersionName` + `appVersionCode` through `AppShellScreen` / `LocalTabContent` into `DesignSettingsScreen`.
4. Extend `DesignSettingsScreen` with version props and render `v<versionName> (<versionCode>)` as centered low-emphasis text pinned to the visible bottom of the settings viewport.
5. Update `DesignSettingsScreenPreview` and `AppShellScreenTest` call sites.
6. Preserve drawer footer, drawer About dialog, `onVersionTap`, design-style selection, design-style SharedPreferences, navigation routes, DI, domain/data/storage.

No backend change required.

### Validation

| Validation | Success criterion |
|---|---|
| Grep call sites | `grep -RIn --include='*.kt' 'DesignSettingsScreen(' android apps shared` and `grep -RIn --include='*.kt' 'AppShellScreen(' android apps shared` show no stale call sites. |
| App compile | `./gradlew :apps:android-next:assembleDebug --no-configuration-cache` succeeds. |
| JVM tests | `./gradlew test --no-configuration-cache` succeeds. |
| Instrumented test APK compile | `./gradlew assembleDebugAndroidTest --no-configuration-cache` succeeds if `AppShellScreenTest` signature changes. |
| Canonical gate | `./gradlew ciCheck --no-configuration-cache` succeeds before merge if environment permits. |
| Manual UX | Open settings and see centered `v0.1.0 (1)` at visible bottom; tap/long-press does nothing; drawer footer still behaves as before. |

### Independent Verification Protocol

- [VERIFIED: прочитал `apps/android-next/build.gradle.kts:14-17`, `:33-35`, подтверждаю: app version code/name and generated BuildConfig are configured.]
- [VERIFIED: прочитал `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:40-50`, подтверждаю: only `BuildConfig.VERSION_NAME` and `BuildConfig.DEBUG` are passed to `AppShellScreen`; `VERSION_CODE` is not passed today.]
- [VERIFIED: прочитал `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:107-127`, подтверждаю: `AppShellScreen` has `appVersionName: String` and no version-code parameter.]
- [VERIFIED: прочитал `AppShellScreen.kt:213-221`, подтверждаю: current version metadata goes to `DrawerContent(versionName = appVersionName)` and drawer tap delegates to `rootComponent.onVersionTap(System.currentTimeMillis())`.]
- [VERIFIED: прочитал `DrawerContent.kt:25-35`, `:70-78`, подтверждаю: drawer forwards `versionName` and `onVersionTap` to `DrawerFooter`.]
- [VERIFIED: прочитал `DrawerFooter.kt:73-81`, подтверждаю: drawer label renders `v$versionName`, uses `labelSmall` and low-emphasis color, and is clickable.]
- [VERIFIED: прочитал `DrawerFooter.kt:85-96`, подтверждаю: About dialog is local drawer behavior and displays `Версия $versionName`.]
- [VERIFIED: прочитал `DefaultRootComponent.kt:140-144`, `:318-337`, подтверждаю: version taps run developer-mode activation logic and may emit dev-mode events.]
- [VERIFIED: прочитал `DesignSettingsScreen.kt:48-78`, подтверждаю: settings screen has no version params and no footer; current content is a full-size `LazyColumn`.]
- [VERIFIED: прочитал `DesignSettingsScreen.kt:80-92`, `:139-145`, подтверждаю: existing low-emphasis text uses MaterialTheme typography and `onSurface.copy(alpha = ...)`.]
- [VERIFIED: прочитал `DesignSettingsScreen.kt:97-113`, подтверждаю: existing style cards are clickable and route only to `onStyleSelected`.]
- [VERIFIED: прочитал `DesignSettingsScreen.kt:298-305`, подтверждаю: preview call site needs update if signature adds required params.]
- [VERIFIED: прочитал `android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt:254-260`, подтверждаю: androidTest call site passes only `appVersionName = "test"`.]
- [VERIFIED: прочитал `android/feature/local/settings/presentation/build.gradle.kts:9-15`, подтверждаю: settings presentation already has designsystem/Compose/lifecycle dependencies and no test dependencies in this build file.]
- [VERIFIED: прочитал `android/feature/app-shell/presentation/build.gradle.kts:49-57`, подтверждаю: app-shell presentation has androidTest Compose UI dependencies.]
- [VERIFIED: прочитал `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/SchoolQuizTheme.kt:29-45`, подтверждаю: project theme supplies MaterialTheme color scheme/shapes/typography.]
- [VERIFIED: прочитал `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/SchoolQuizDesign.kt:169-200`, подтверждаю: background content is BoxScope and can contain pinned non-scroll content.]
- [VERIFIED: прочитал `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt:7-35` и `shared/core/catalog/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/domain/model/Catalog.kt:21-41`, подтверждаю: shared-core `version` fields are DB/catalog/server-sync concepts, not app build metadata.]
- [VERIFIED: прочитал `apps/android-next/build.gradle.kts:38-42`, подтверждаю: app module depends on app-shell presentation; app is the composition root for `AppShellScreen`.]
- [VERIFIED: прочитал `android/feature/app-shell/presentation/build.gradle.kts:10-11`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:61-69`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:15-25`, `:50-55`, подтверждаю: app-shell has existing direct composition imports to local/settings and other feature modules.]
- [VERIFIED: выполнил `grep -RIn --include='*.kt' -E 'appVersion|versionName|VERSION|VersionFooter|Footer|versionCode' android/core/designsystem shared/core android/feature/local/settings/presentation`, подтверждаю: no existing design-system/shared-core app-version/footer helper was found.]
- [VERIFIED: выполнил `find android/feature/local/settings/presentation/src/test android/feature/local/settings/presentation/src/androidTest -type f ! -name '._*' -maxdepth 10 -print`, подтверждаю: settings test roots contain only `.gitkeep` files.]
- [VERIFIED: выполнил `grep -RIn --include='*.kt' 'DesignSettingsScreen(' android apps shared`, подтверждаю: only declaration, app-shell production call, and settings preview call were found.]
- [VERIFIED: выполнил `grep -RIn --include='*.kt' 'AppShellScreen(' android apps shared`, подтверждаю: only declaration, `MainActivity`, and `AppShellScreenTest` call sites were found.]
- [VERIFIED: выполнил `grep -RIn --include='*.kt' 'BuildConfig' android apps shared`, подтверждаю: settings/app-shell production path has only app-layer `BuildConfig` usage plus documentation comments; settings library does not read app `BuildConfig` directly.]
- [VERIFIED: выполнил `grep -RIn --include='*.kt' 'feature.local.settings' android shared apps`, подтверждаю: app-shell imports settings, settings does not import app-shell.]
- [VERIFIED: выполнил `grep -RIn --include='*.kt' 'Class.forName\|forName(' platform android shared apps` and прочитал `platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/FirebaseInitializer.kt:29-35`, подтверждаю: only Firebase SDK debug-provider reflection was found; no feature-to-feature reflection in this scan.]

### Invariant Conflicts

- Domain layer purity: no domain change planned.
- Presentation boundary: preserved if version data is passed via params and `DesignSettingsScreen` remains a view function.
- Cross-feature coupling: preserved if implementation uses existing app → app-shell → local/settings path and adds no reverse dependency from local/settings.
- Koin binding uniqueness: no DI changes.
- Scaffold ownership: no Gradle/build edits required by current research.
- Auth-scoped Flow re-subscribe: N/A; no user-specific Flow.

### BLOCKER Findings

None. The existing undocumented app-shell → local/settings import should be documented/preserved in design, but it is not a new dependency introduced by this feature and does not block the next phase.
