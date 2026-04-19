---
feature: app-shell-menu
created: 2026-04-18
phases: 7
strategy: Bottom-up Walking Skeleton Integration
---

# Implementation Plan: App Shell Menu

## Phase Strategy

**Bottom-up Walking Skeleton Integration.**

Domain слой (229 JVM tests) уже сгенерирован в spec-фазе. Стратегия строит снизу вверх:

1. **Phase-01** — Foundation: domain delta (2 modify + 2 new files), scaffold (Compose plugins, new `shared/core/stats` module), production adapter chain (`UserStatsDataSource` → `UserStatsRepositoryImpl` → Koin modules), `AppApplication`.
2. **Phase-02** — Design System foundation: `SchoolQuizTheme` + цвета + shapes. Независима по сути, но зависит от Phase-01 Compose convention plugin.
3. **Phase-03** — Brand components + DesignCatalogScreen. Зависит от Phase-02 (тема).
4. **Phase-04** — Decompose integration: `DefaultRootComponent` + 4 tab components + `NavigatorImpl` + Koin presentation module. Центральная фаза — всё сходится здесь.
5. **Phase-05** — AppShellScreen: ModalNavigationDrawer + NavigationBar + Crossfade + ScrollToTopRegistry. Зависит от Phase-04 (component) и Phase-03 (DesignCatalog).
6. **Phase-06** — Drawer content: DrawerHeader + DrawerSectionList (progressive unlock) + DrawerFooter + DrawerContent. Зависит от Phase-05.
7. **Phase-07** — MainActivity wiring: `defaultComponentContext()`, `repeatOnLifecycle`, `onNewIntent` deep link hook. Финальная интеграция — всё собирается в рабочий APK.

**Key principle**: Domain не переписывается. Только 2 user-approved exceptions: `ObserveAppShellStateUseCase` signature (ADR-LEAD-02) + 2 новых interface файла в domain/navigation/ (ADR-COMP-04, ADR-0011).

---

## Phases Table

| Phase | Goal | Depends on | Role Inputs | Validation |
|-------|------|-----------|-------------|------------|
| phase-01 | Walking Skeleton integration foundation: domain delta + scaffold + UserStatsRepositoryImpl + Koin wiring + AppApplication | Walking Skeleton (229 tests green) | backend.md, tests.md | `jvmTest` 229+ green; `data:jvmTest` D1-D3; `assembleDebug` |
| phase-02 | Design System foundation: SchoolQuizTheme + darkColorScheme + shapes | phase-01 (Compose convention plugin) | frontend.md, tests.md | `:designsystem:compileDebugKotlin` |
| phase-03 | Brand components wrappers + DesignCatalogScreen | phase-02 (SchoolQuizTheme) | frontend.md, tests.md | `:designsystem:compileDebugKotlin` |
| phase-04 | Decompose integration: DefaultRootComponent + tabs + NavigatorImpl + Koin presentation module | phase-01 (Navigator.kt, RootComponent.kt), phase-02 | frontend.md, tests.md | `:presentation:test` DefaultRootComponentTest green |
| phase-05 | AppShellScreen + ScrollToTopRegistry | phase-04 (component), phase-03 (DesignCatalog) | frontend.md, tests.md | `:presentation:compileDebugKotlin`; ScrollToTopRegistryTest |
| phase-06 | Drawer content: DrawerHeader + SectionList (progressive unlock) + DrawerFooter | phase-05 (AppShellScreen), phase-03 (BrandProgressBar) | frontend.md, tests.md | `:presentation:compileDebugKotlin`; DrawerFooterMapperTest |
| phase-07 | MainActivity wiring + SystemBack + deep link hook | ALL previous phases (incl. phase-06) | backend.md, tests.md | `assembleDebug`; detekt ktlintCheck; manual smoke test AC 28-30 |

---

## File Map

### New Files (by phase)

**phase-01** (21 files):
```
buildSrc/src/main/kotlin/AndroidComposeLibraryConventionPlugin.kt
buildSrc/src/main/kotlin/AndroidComposeApplicationConventionPlugin.kt
shared/core/stats/build.gradle.kts
shared/core/stats/src/commonMain/.../UserStatsDataSource.kt
shared/core/stats/src/commonMain/.../RawUserStats.kt
shared/feature/app-shell/data/src/commonMain/.../UserStatsRepositoryImpl.kt
shared/feature/app-shell/data/src/commonMain/.../di/AppShellDataModule.kt
platform/firebase/src/main/.../FirebaseUserStatsDataSource.kt
platform/firebase/src/main/.../di/FirebaseModule.kt
apps/android-next/src/main/.../AppApplication.kt
shared/feature/app-shell/domain/src/commonMain/.../navigation/Navigator.kt
shared/feature/app-shell/domain/src/commonMain/.../navigation/RootComponent.kt
shared/feature/app-shell/data/src/jvmTest/.../fake/FakeUserStatsDataSource.kt
shared/feature/app-shell/data/src/jvmTest/.../UserStatsRepositoryImplTest.kt
apps/android-next/src/test/.../KoinModuleWiringTest.kt
```

**phase-02** (4 files):
```
android/core/designsystem/src/main/.../Color.kt
android/core/designsystem/src/main/.../Shape.kt
android/core/designsystem/src/main/.../Type.kt
android/core/designsystem/src/main/.../SchoolQuizTheme.kt
```

**phase-03** (7 files):
```
android/core/designsystem/src/main/.../components/BrandCard.kt
android/core/designsystem/src/main/.../components/BrandPrimaryButton.kt
android/core/designsystem/src/main/.../components/BrandSecondaryButton.kt
android/core/designsystem/src/main/.../components/BrandProgressBar.kt
android/core/designsystem/src/main/.../components/BrandCircleIconButton.kt
android/core/designsystem/src/main/.../components/CategoryIcon.kt
android/core/designsystem/src/main/.../catalog/DesignCatalogScreen.kt
```

**phase-04** (12 files):
```
android/feature/app-shell/presentation/src/main/.../component/DefaultRootComponent.kt
android/feature/app-shell/presentation/src/main/.../component/NavigatorImpl.kt
android/feature/app-shell/presentation/src/main/.../component/tab/LocalTabComponent.kt
android/feature/app-shell/presentation/src/main/.../component/tab/InternetTabComponent.kt
android/feature/app-shell/presentation/src/main/.../component/tab/EventsTabComponent.kt
android/feature/app-shell/presentation/src/main/.../component/tab/ShopTabComponent.kt
android/feature/app-shell/presentation/src/main/.../screen/LocalScreenComponent.kt
android/feature/app-shell/presentation/src/main/.../screen/InternetScreenComponent.kt
android/feature/app-shell/presentation/src/main/.../screen/EventsScreenComponent.kt
android/feature/app-shell/presentation/src/main/.../screen/ShopScreenComponent.kt
android/feature/app-shell/presentation/src/main/.../di/AppShellPresentationModule.kt
android/feature/app-shell/presentation/src/test/.../DefaultRootComponentTest.kt
```

**phase-05** (6 files):
```
android/feature/app-shell/presentation/src/main/.../ui/labels/Labels.kt
android/feature/app-shell/presentation/src/main/.../ui/AppShellScreen.kt
android/feature/app-shell/presentation/src/main/.../ui/UnderConstructionScreen.kt
android/feature/app-shell/presentation/src/main/.../ui/scroll/ScrollToTopHook.kt
android/feature/app-shell/presentation/src/main/.../ui/scroll/ScrollToTopRegistry.kt
android/feature/app-shell/presentation/src/test/.../ScrollToTopRegistryTest.kt
```

**phase-06** (6 files):
```
android/feature/app-shell/presentation/src/main/.../ui/drawer/DrawerHeader.kt
android/feature/app-shell/presentation/src/main/.../ui/drawer/DrawerSectionList.kt
android/feature/app-shell/presentation/src/main/.../ui/drawer/DrawerFooter.kt
android/feature/app-shell/presentation/src/main/.../ui/drawer/DrawerContent.kt
android/feature/app-shell/presentation/src/androidTest/.../DrawerHeaderTest.kt
android/feature/app-shell/presentation/src/test/.../DrawerFooterMapperTest.kt
```

**phase-07** (0 new files — модификация существующих):

### Modified Files

| File | Phase | Change |
|------|-------|--------|
| `shared/feature/app-shell/domain/.../ObserveAppShellStateUseCase.kt` | phase-01 | parameter `initialState` → `currentStateProvider: () -> AppShellState` (ADR-LEAD-02) |
| `shared/feature/app-shell/domain/.../ObserveAppShellStateUseCaseTest.kt` | phase-01 | adapt 9 tests + add stale closure test |
| `shared/feature/app-shell/data/build.gradle.kts` | phase-01 | add deps: domain, core:stats, koin |
| `platform/firebase/build.gradle.kts` | phase-01 | add dep: shared:core:stats |
| `apps/android-next/build.gradle.kts` | phase-01 | full Compose/Koin/Decompose/feature deps |
| `apps/android-next/AndroidManifest.xml` | phase-01 | add android:name=".AppApplication" |
| `settings.gradle.kts` | phase-01 | add include(":shared:core:stats") |
| `buildSrc/build.gradle.kts` | phase-01 | register new convention plugins |
| `android/core/designsystem/build.gradle.kts` | phase-02 | apply compose.library plugin + BOM |
| `android/feature/app-shell/presentation/build.gradle.kts` | phase-04 | full deps: domain/navigation/designsystem/Compose/Koin/Decompose |
| `android/core/navigation/build.gradle.kts` | phase-05 | apply compose plugin + expose decompose-extensions |
| `apps/android-next/src/main/.../AppApplication.kt` | phase-04 | add appShellPresentationModule to startKoin |
| `android/feature/app-shell/presentation/src/main/.../ui/AppShellScreen.kt` | phase-06 | replace drawer placeholder with DrawerContent |
| `apps/android-next/src/main/.../MainActivity.kt` | phase-07 | full implementation |

### Deleted Files

none

---

## Cross-Phase Dependencies Graph

```
phase-01 ──────────────────────────────────────────────────── (BLOCKS ALL)
    │
    ├──► phase-02 ──► phase-03 ──────────────────┐
    │                             │               │
    │                             └──► phase-06 ──┤
    │                                             │
    └──► phase-04 ────────────────────────────────┼──► phase-05 ──► phase-06 ──► phase-07
         (also requires phase-02)                 │                              │
                                                  └──────────────────────────────┘
                                                  (phase-07 requires ALL previous, incl. phase-06)
```

M1 fix: добавлен явный ребро `phase-03 → phase-06` (phase-06 использует BrandProgressBar из phase-03). Добавлена зависимость `phase-06` в phase-07.

**Linearized execution** (minimal dependency path):
`phase-01 → phase-02 → phase-03 → phase-04 → phase-05 → phase-06 → phase-07`

**Parallel opportunity**: phase-02 and phase-04 CAN start in parallel after phase-01 (phase-04 requires phase-01 domain files; phase-02 requires phase-01 Compose plugin). phase-03 requires only phase-02. phase-05 requires phase-04 + phase-03. phase-06 requires phase-05 + phase-03.

---

## Open Questions (discovered during planning)

| OQ | Description | Status | Phase |
|----|-------------|--------|-------|
| OQ-PLAN-1 | `StackNavigation.replaceAll(vararg C)` exact signature in Decompose 3.1.0 | DEFERRED — frontend-dev verifies at phase-04 implementation time; fallback: `replaceAll(list)` overload | phase-04 |
| OQ-PLAN-2 | `essentyLifecycle()` extension vs manual `LifecycleRegistry()` in MainActivity | DEFERRED — resolved by using `defaultComponentContext()` per OQ-COMP-3; no manual lifecycle needed | phase-07 |
| OQ-PLAN-3 | `shared/core/stats` — новый модуль. Не существует в settings.gradle.kts. backend-dev должен создать директорию + `include()` | Action required in phase-01 backend.md | phase-01 |
| OQ-PLAN-4 | `DrawerSection.displayName` + `DrawerSection.icon` и другие label mappings — extension properties в presentation layer. `TabConfig.displayName` и `DrawerFooterAction.displayName` тоже нужны для `AppShellScreen` compile gate. | **RESOLVED (B2 fix)**: `Labels.kt` создаётся в phase-05. Domain НЕ содержит display strings (domain-purity). Все extensions в `presentation/ui/labels/Labels.kt`. Phase-06 использует готовые extensions — не дублирует. | phase-05 |
| OQ-PLAN-5 | `visibleFooterActions(isDebugBuild: Boolean)` — функция в domain `Visibility.kt`. Если не существует — phase-06 test-dev создаёт Open Question для backend-dev (domain delta, requires user approval per ADR-LEAD-02 precedent) | **RESOLVED (M3 fix)**: Функция уже существует в `shared/feature/app-shell/domain/src/commonMain/.../logic/Visibility.kt:142-144`. Phase-06 вызывает готовую функцию — никакого domain delta не требуется. | phase-06 |
