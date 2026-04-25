---
feature: menu-refactor
plan-version: 1.0
date: 2026-04-20
status: READY_FOR_IMPLEMENTATION
---

# Plan: Menu Refactor — Lead Dashboard

Feature slug: `menu-refactor`
Sub-specs: `qualification-levels`, `dev-mode` (revert overlay), `home-quests` (rename), `catalog-foundation`

---

## Phase Strategy

Вертикальный порядок по dependencies. Phase 01 и Phase 02 можно запустить параллельно (независимые scopes). Все остальные — последовательно.

```
Phase 01 (Foundation)
Phase 02 (HomeQuests Rename)   ← параллельно с Phase 01
     ↓
Phase 03 (Domain Extensions)
     ↓
Phase 04 (UserStats Data)
Phase 05 (Catalog Data)        ← параллельно с Phase 04 (оба зависят только от Phase 03)
     ↓
Phase 06 (Sync Infrastructure)
     ↓
Phase 07 (Presentation Integration)
     ↓
Phase 08 (Integration Tests + Firebase Rules)
```

**Walking Skeleton Variant:** Qualified. Phases 03-07 — adapter-only work на domain, уже сгенерированном на spec-фазе. Phase 01 — targeted rewrite (ADR-HLA-02 revert overlay).

---

## Phases Table

| Phase | Goal | Depends on | Role Inputs | Complexity | Validation |
|-------|------|------------|-------------|------------|------------|
| 01 | Foundation Infrastructure — AppDatabase, core:persistence, core:sync, core:foundation, Walking Skeleton cleanup | none | backend.md, tests.md | complex | `:shared:core:foundation:jvmTest` + `:shared:feature:qualification:domain:jvmTest` GREEN + QL-01..14 + DM-01..16 green |
| 02 | Home Quests Rename — `MyCourses → HomeQuests` в domain + labels | none (parallel to 01) | backend.md, frontend.md, tests.md | simple | `:shared:feature:app-shell:domain:jvmTest` GREEN + HQ-01..07 |
| 03 | App-shell Domain Extensions — `DrawerFooterAction.SyncNow`, `RootEvent` variants, `RootComponent` + `UserStatsRepository` methods, `Visibility` updates + compile stubs | phase-01 | backend.md, tests.md | simple | `:shared:feature:app-shell:domain:jvmTest` GREEN + `:android:feature:app-shell:presentation:assembleDebug` GREEN + DM-17..27 |
| 04 | UserStats Data Layer — `UserStatsRepositoryImpl` Room+Firestore+Syncable, mapper, Koin update | phase-01, phase-03 | backend.md, tests.md | simple | `:shared:feature:app-shell:data:jvmTest` GREEN + US-01..08 |
| 05 | Catalog Data Stack — `CatalogRepositoryImpl`, DataSources, mapper, Koin (clean DI split: `catalog:data` pure KMP, Firebase bindings in `platform:firebase`) | phase-01, phase-03 | backend.md, tests.md | complex | `:shared:core:catalog:data:jvmTest` GREEN + CF-11..23 + `:apps:android-next:assembleDebug` GREEN |
| 06 | Sync Infrastructure — `SyncWorker`, periodic schedule, `syncModule` Koin | phase-04, phase-05 | backend.md, tests.md | simple | `:apps:android-next:assembleDebug` GREEN + `:platform:android-services:test` GREEN |
| 07 | Presentation Integration — `DefaultRootComponent` + SnackbarHost + DrawerFooter + CatalogDisplayItem/Grid/Spinner (replaces Phase 03 stubs) | phase-01, phase-03, phase-04, phase-05, phase-06 | backend.md, frontend.md, tests.md | complex | `:apps:android-next:assembleDebug` GREEN + `:android:feature:app-shell:presentation:test` GREEN + manual 10-tap snackbar |
| 08 | Integration Tests + Firebase Rules — instrumented DAO tests + journey integration tests | phase-01..07 | backend.md, tests.md | simple | `:shared:core:persistence:connectedDebugAndroidTest` GREEN + `:shared:core:catalog:data:jvmTest` GREEN |

---

## File Map

### New Modules (created in Phase 01)

| Module path | Содержимое | Фаза |
|-------------|-----------|------|
| `shared/core/foundation/` | `QualificationLevel.kt` (MOVED) | 01 |
| `shared/core/sync/` | `Syncable.kt` (interface) | 01 |
| `shared/core/persistence/` | `AppDatabase.kt`, `UserStatsEntity`, `UserStatsDao`, `CatalogEntity`, `CatalogDao` | 01 |
| `shared/core/catalog/data/` | `CatalogLocalDataSource`, `CatalogRemoteDataSource` interface, `CatalogRepositoryImpl`, `CatalogEntity↔Domain` mapper, `CatalogDto` (pure Kotlin DTO per ADR split), `CatalogDto.toEntity()` mapper, `CatalogDataModule` (pure KMP bindings) | 05 |
| `platform/firebase/` — catalog add-on | `FirebaseCatalogRemoteDataSource` impl, `FirestoreCatalogDtoMapper` (`DocumentSnapshot → CatalogDto`), `FirebaseCatalogModule` (Koin — Firebase bindings + `storageUrlResolver`) | 05 |
| `platform/android-services/` | `SyncWorker.kt`, `SyncModule.kt` | 06 |

### Modified Files по фазам

| Phase | Файл | Что меняется |
|-------|------|-------------|
| 01 | `settings.gradle.kts` | добавить `:shared:core:catalog:data` |
| 01 | `libs.versions.toml` | Coil 3.4.0 + ksp references |
| 01 | `shared/feature/qualification/domain/.../dev_mode/logic/RegisterTap.kt` | param rename |
| 01 | `shared/feature/qualification/domain/.../dev_mode/use_case/ActivateDevModeUseCase.kt` | REWRITE (lambda injection) |
| 01 | `shared/feature/qualification/domain/build.gradle.kts` | core:foundation dep |
| 02 | `shared/feature/app-shell/domain/.../model/DrawerSection.kt` | MyCourses → HomeQuests |
| 02 | `shared/feature/app-shell/domain/.../model/TabConfig.kt` | MyCoursesRoot → HomeQuestsRoot |
| 02 | `shared/feature/app-shell/domain/.../logic/Visibility.kt` | LOCAL section reorder + rootOf rename |
| 02 | `android/feature/app-shell/presentation/.../ui/Labels.kt` | HomeQuests displayName/icon |
| 03 | `shared/feature/app-shell/domain/.../model/DrawerFooterAction.kt` | ADD SyncNow |
| 03 | `shared/feature/app-shell/domain/.../model/RootEvent.kt` | ADD 3 new variants |
| 03 | `shared/feature/app-shell/domain/.../navigation/RootComponent.kt` | ADD onVersionTap/onSyncNow |
| 03 | `shared/feature/app-shell/domain/.../repository/UserStatsRepository.kt` | ADD setLocalDeveloperLevel/refreshProfile |
| 03 | `shared/feature/app-shell/domain/.../logic/Visibility.kt` | superqualification OR-bypass + visibleFooterActions new sig |
| 03 | `shared/feature/app-shell/domain/.../model/DrawerSection.kt` | ActiveEvents.requiredRoles magic 100 → QualificationLevel.LEVEL_1.points |
| 04 | `shared/feature/app-shell/data/.../UserStatsRepositoryImpl.kt` | REWRITE + UserStatsDao + fetchOnce |
| 04 | `shared/feature/app-shell/data/build.gradle.kts` | room + persistence deps |
| 04 | `shared/feature/app-shell/data/di/AppShellDataModule.kt` | UserStatsDao + currentUid params |
| 05 | `platform/firebase/build.gradle.kts` | `core:catalog:data` dep (for `CatalogRemoteDataSource` interface + `CatalogDto`) + `core:catalog:domain` dep (if mapper needs domain types) |
| 05 | `apps/android-next/.../AppApplication.kt` | register `firebaseCatalogModule` (from `platform/firebase/di/`) + `catalogDataModule` + `catalogDomainModule` в `startKoin` |
| 06 | `platform/android-services/build.gradle.kts` | WorkManager + core:sync deps |
| 06 | `apps/android-next/.../AppApplication.kt` | syncModule + periodic schedule |
| 07 | `android/feature/app-shell/presentation/.../component/DefaultRootComponent.kt` | _tapProgress + activateDevModeUseCase + onVersionTap + onSyncNow |
| 07 | `android/feature/app-shell/presentation/.../ui/AppShellScreen.kt` | SnackbarHostState + LaunchedEffect + snackbarHost + DesignCatalogRoot condition |
| 07 | `android/feature/app-shell/presentation/.../ui/drawer/DrawerContent.kt` | userStats pass-through |
| 07 | `android/feature/app-shell/presentation/.../ui/drawer/DrawerFooter.kt` | new params + clickable + SyncNow branch |
| 07 | `android/feature/app-shell/presentation/.../ui/Labels.kt` | SyncNow displayName/icon |
| 07 | `android/feature/app-shell/presentation/di/AppShellPresentationModule.kt` | WorkManager + UserStatsRepository params |
| 07 | `android/core/designsystem/build.gradle.kts` | Coil 3 + catalog:domain deps |
| 08 | `firestore.rules` | catalogs read block |

### Deleted Files (Walking Skeleton cleanup в Phase 01)

| Файл | Причина |
|------|---------|
| `shared/feature/qualification/domain/.../dev_mode/model/LocalDeveloperOverride.kt` | ADR-HLA-02 revert overlay |
| `shared/feature/qualification/domain/.../dev_mode/model/DeveloperLevelStats.kt` | overlay удалена |
| `shared/feature/qualification/domain/.../dev_mode/logic/EffectiveDeveloperLevel.kt` | merge logic не нужна |
| `shared/feature/qualification/domain/.../dev_mode/repository/LocalDeveloperOverrideRepository.kt` | overlay репозиторий удалён |
| `shared/feature/qualification/domain/.../fake/FakeLocalDeveloperOverrideRepository.kt` | фейк overlay удалён |
| тест-файлы для overlay (3 файла) | см. `04-testing.md §2.1` |

---

## Test Coverage Mapping

| ID | Сценарий | Категория | Фаза | Файл |
|----|----------|-----------|------|------|
| QL-01..14 | QualificationLevel enum | qualification-levels | 01 | `QualificationLevelTest.kt` (MOVED) |
| DM-01..10 | RegisterTap FSM | dev-mode | 01 | `RegisterTapTest.kt` (updated) |
| DM-11..16 | ActivateDevModeUseCase | dev-mode | 01 | `ActivateDevModeUseCaseTest.kt` (REWRITE) |
| DM-17..23 | Visibility matrix 7 cells | dev-mode | 03 | `VisibilityTest.kt` |
| DM-24..27 | Footer matrix | dev-mode | 03 | `VisibilityTest.kt` |
| HQ-01..07 | HomeQuests rename | home-quests | 02 | multiple test files updated |
| CF-01..05 | Catalog domain | catalog-foundation | Walking Skeleton | `CatalogTest.kt` (green from spec) |
| CF-06..10 | Catalog DAO boundary | catalog-foundation | 08 | `CatalogDaoTest.kt` (instrumented) |
| CF-11..18 | CatalogRepositoryImpl | catalog-foundation | 05 | `CatalogRepositoryImplTest.kt` |
| CF-19..23 | Catalog mappers | catalog-foundation | 05 | `CatalogMapperTest.kt` |
| US-01..08 | UserStatsRepositoryImpl | supplemental | 04 | `UserStatsRepositoryImplTest.kt` |
| Journey: 10-tap | DevModeActivation | integration | 07 | `DevModeActivationIntegrationTest.kt` |
| Journey: SyncNow | SyncNowFlow | integration | 07 | `SyncNowFlowIntegrationTest.kt` |
| Journey: DesignCatalog | RenderCondition | integration | 07 | `DesignCatalogRenderConditionTest.kt` |
| Journey: FirstFetch | CatalogFirstFetch | integration | 08 | `CatalogFirstFetchIntegrationTest.kt` |
| Journey: WarmCache | CatalogWarmCache | integration | 08 | `CatalogWarmCacheIntegrationTest.kt` |
| Journey: Offline | CatalogOfflineEmpty | integration | 08 | `CatalogOfflineEmptyIntegrationTest.kt` |
| Journey: SyncDeactivates | DevModeAutoDeactivation | integration | 08 | `SyncDeactivatesDevModeIntegrationTest.kt` |
| DB Migration | AppDatabaseMigrationTest | instrumented | 08 | `AppDatabaseMigrationTest.kt` |
| DAO Boundary | UserStatsDaoTest | instrumented | 08 | `UserStatsDaoTest.kt` |

**Total**: 77 spec-traced scenarios + integration/instrumented tests

---

## ADR Coverage

Источник истины: `docs/features/menu-refactor/03-decisions.md`

| ADR | Решение (из `03-decisions.md`) | Реализовано в |
|-----|-------------------------------|---------------|
| ADR-HLA-01 | `QualificationLevel` MOVED в `shared/core/foundation` — устраняет cross-feature import BLOCKER (`03-decisions.md §ADR-HLA-01`) | Phase 01 |
| ADR-HLA-02 | Прямая запись `developer=100` в Room, без overlay entity — revert codex fix #2 (`03-decisions.md §ADR-HLA-02`) | Phase 01 (delete overlay) + Phase 04 (UserStatsRepositoryImpl) |
| ADR-HLA-03 | Центральная `AppDatabase` в `core:persistence` — один Room DB для всех feature (`03-decisions.md §ADR-HLA-03`) | Phase 01 |
| ADR-HLA-04 | `SyncWorker` + `List<Syncable>` topology — platform не зависит от feature (`03-decisions.md §ADR-HLA-04`) | Phase 06 |
| ADR-HLA-05 | `RootComponent.onSyncNow()` + `RootEvent.SyncStarted` trigger — не через Destination или koinInject (`03-decisions.md §ADR-HLA-05`) | Phase 03 (interfaces) + Phase 07 (implementation) |
| ADR-HLA-06 | **Coil 3.4.0 (`io.coil-kt.coil3`)** для image loading в CatalogGridItem — не Glide (`03-decisions.md §ADR-HLA-06:146-159`) | Phase 01 (`libs.versions.toml`) + Phase 07 (`CatalogGridItem` + `designsystem/build.gradle.kts`) |
| ADR-HLA-07 | URL pre-resolution в data layer: `CatalogRepositoryImpl.refreshFromRemote()` сохраняет resolved HTTPS URL в `CatalogEntity.pictureUrl` (`03-decisions.md §ADR-HLA-07`) | Phase 05 |
| ADR-L3-01 | Lambda injection в `ActivateDevModeUseCase` — устраняет `qualification:domain → app-shell:domain` BLOCKER (`03-decisions.md §ADR-L3-01`) | Phase 01 |
| ADR-L3-02 | `_tapProgress` в `DefaultRootComponent` (не Composable remember) — testability + Decompose pattern (`03-decisions.md §ADR-L3-02`) | Phase 07 |
| ADR-L3-03 | `CatalogDisplayItem` в `android:core:designsystem` — presentation model отделён от domain (`03-decisions.md §ADR-L3-03`) | Phase 07 |
| ADR-L3-04 | **`CatalogDao.replaceAll()` как `@Transaction`** — атомарная замена, Room Flow не эмитит пустой список (`03-decisions.md §ADR-L3-04:306-312`) | Phase 01 (`CatalogDao` creation) |

---

## Grounding Problems → Phases

| Problem | Фаза fix-а | Статус |
|---------|-----------|--------|
| Problem 1: cross-feature import `app-shell → qualification` | Phase 01 (MOVE to foundation) + Phase 03 (LEVEL_1.points) | planned |
| Problem 2: 10-tap без UI hookup, нет SyncNow, нет SnackbarHost | Phase 03 (domain) + Phase 07 (presentation) | planned |
| Problem 3: MyCourses → HomeQuests rename | Phase 02 | planned |
| Problem 4: Catalog data stack не существует, нет Firebase Rules | Phase 01+05 (stack) + Phase 08 (rules) | planned |
| Problem 5: DesignCatalog condition не учитывает developer tier | Phase 03 (Visibility) + Phase 07 (AppShellScreen condition) | planned |

---

## Review Tags Summary

| Phase | Review Tags |
|-------|------------|
| 01 | `complex`, `concurrency-review` (StateFlow + Coroutines), `scaffold-ownership` |
| 02 | — |
| 03 | `concurrency-review` (Flow extensions) |
| 04 | `concurrency-review` (Room Flow + Firestore suspend), `REQUIRES verify` |
| 05 | `complex`, `concurrency-review` (Flow + suspend chain), `REQUIRES verify` |
| 06 | — |
| 07 | `complex`, `concurrency-review` (LaunchedEffect + Channel + StateFlow) |
| 08 | — |
