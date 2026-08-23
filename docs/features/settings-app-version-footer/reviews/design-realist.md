---
date: 2026-07-26
ticket: SCH-2
feature: settings-app-version-footer
lens: Realist
reviewer: crossmodel-reviewer
verdict: PASS
---

# Realist Design Review — SCH-2

## Verdict

PASS

No blocker/high findings.

## Findings

No findings.

## Checked code references

- `apps/android-next/build.gradle.kts`
- `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt`
- `android/feature/app-shell/presentation/build.gradle.kts`
- `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt`
- `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/tab/LocalTabComponent.kt`
- `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerContent.kt`
- `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerFooter.kt`
- `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt`
- `android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt`
- `android/feature/local/settings/presentation/build.gradle.kts`
- `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt`
- `android/core/designsystem/build.gradle.kts`
- `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/SchoolQuizDesign.kt`

## Realist scope checks

- `01-architecture.md` now models the real private intermediate shell hop `AppShellScreen -> AppShellContent -> LocalTabContent -> DesignSettingsScreen`, and that flow matches the current code structure in `AppShellScreen.kt` (`docs/features/settings-app-version-footer/01-architecture.md:59-90,138-171`; `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:287-346,422-466`).
- `02-behavior.md` now matches the real propagation path from app BuildConfig through the intermediate private shell renderer before settings (`docs/features/settings-app-version-footer/02-behavior.md:16-81,110-129`; `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:40-50`; `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:287-346,422-466`).
- `06-api-contract.md` now includes target signatures for all real internal Compose hops required by the actual code shape: `AppShellScreen`, private `AppShellContent`, private `LocalTabContent`, and `DesignSettingsScreen` (`docs/features/settings-app-version-footer/06-api-contract.md:35-166`; `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:121-127,287-346,422-466`; `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:48-52`).
- The feature-scoped Gradle graph and theming model remain aligned with code: app has direct dependencies on app-shell and design-system, runtime `SchoolQuizTheme` is applied in `MainActivity`, settings consumes `MaterialTheme` ambient plus `SchoolQuizDesignBackground`, and settings keeps only a one-way dependency on design-system (`docs/features/settings-app-version-footer/01-architecture.md:103-136,187-191`; `apps/android-next/build.gradle.kts:40-48`; `android/feature/app-shell/presentation/build.gradle.kts:10-24`; `android/feature/local/settings/presentation/build.gradle.kts:9-15`; `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:40-50`; `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/SchoolQuizDesign.kt:169-200`).
- Preserved drawer/About/dev-mode behavior still matches the design boundaries: `DrawerContent` continues to pass only `versionName` and `onVersionTap` into `DrawerFooter`, `DrawerFooter` keeps the clickable `v$versionName` label plus local About dialog, and `DefaultRootComponent.onVersionTap` remains the developer-mode path that SCH-2 must not reuse from settings (`docs/features/settings-app-version-footer/01-architecture.md:195-203`; `docs/features/settings-app-version-footer/02-behavior.md:83-108`; `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerContent.kt:25-78`; `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerFooter.kt:40-96`; `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:318-337`).
- The updated docs also remain aligned with the existing stale-call-site surface: `LocalTabComponent` still carries only the child stack and does not require any SCH-2 contract change, while `AppShellScreenTest` is still a known caller that must be updated once `AppShellScreen(appVersionCode: Int)` becomes required (`docs/features/settings-app-version-footer/06-api-contract.md:102-128,157-166`; `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/tab/LocalTabComponent.kt:11-27`; `android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt:254-260`).
- `06-api-contract.md` stays within internal Compose UI target signatures and does not invent any domain/backend/walking-skeleton contract; `shared/feature/settings-app-version-footer/domain/` remains absent, which is consistent with Feature Domain Contract = N/A.
