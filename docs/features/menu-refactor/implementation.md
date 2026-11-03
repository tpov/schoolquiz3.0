---
feature: menu-refactor
implementation-date: 2026-04-21
status: implemented
---

# Implementation Report: Menu Refactor

## Summary

Реализована master-feature объединяющая 4 sub-specs: `qualification-levels`, `dev-mode`, `home-quests` (rename), `catalog-foundation`. Реализация заняла 8 фаз через pipeline `/feature-implement`, каждая фаза прошла autonomous review loop с 4-5 same-model reviewers. Финальный cross-phase adversarial review через Codex CLI выявил 6 integration issues, все исправлены.

**Всего изменено:** 56 файлов (+898 / -398 строк). Kotlin 1.9.22 → 2.1.20 upgrade применён в Phase 01 как user decision.

## Phases Completed

| Phase | Scope | Status | Reviewers |
|-------|-------|--------|-----------|
| 01 Foundation | AppDatabase + core:persistence/sync/foundation, Walking Skeleton cleanup, Kotlin 2.x upgrade | ✓ | architect, code, security, completeness, concurrency — all PASS |
| 02 Home Quests Rename | MyCourses→HomeQuests (8 prod lines, 26 test lines) | ✓ | architect, code, security, completeness — all PASS |
| 03 App-shell Domain Extensions | DrawerFooterAction.SyncNow, RootEvent variants, RootComponent methods, Visibility superqualification | ✓ | 5 reviewers all PASS (247 tests green) |
| 04 UserStats Data Layer | UserStatsRepositoryImpl Room+Firestore, Mapper, Koin update | ✓ | 5 reviewers all PASS (21 tests) |
| 05 Catalog Data Stack | CatalogRepositoryImpl + Firebase adapter, URL pre-resolution, Koin split | ✓ | 5 reviewers all PASS (14 tests) |
| 06 Sync Infrastructure | SyncWorker + SyncModule, periodic 1d schedule | ✓ | 4 reviewers all PASS |
| 07 Presentation Integration | DefaultRootComponent + SnackbarHost + DrawerFooter + CatalogDisplayItem/Spinner/Grid | ✓ | 5 reviewers all PASS |
| 08 Integration Tests + Firebase Rules | AppDatabaseMigration + DAO tests + journey integration tests + firestore.rules | ✓ | 4 reviewers PASS |

## Cross-Phase Codex Review

Запущен `codex exec --sandbox read-only` с 4-lens prompt (Skeptic + Architect + Minimalist + Realist) после всех 8 фаз. Output: `_review/codex-cross-phase.md`.

**Findings:**
- 2 blockers (observeStats auth re-subscribe, setLocalDeveloperLevel fresh install)
- 1 high (events Channel single-consumer split between MainActivity + AppShellScreen)
- 2 medium (orphaned CreateQuestUseCase DI, CatalogDisplayItem dropped pictureUrl)
- 1 low (duplicate Syncable binding)

**Все 6 закрыты** через autonomous fix loop (см. "Cross-Phase Fixes Applied" ниже).

## Review Verdicts

Per-phase same-model reviewers (8 phases × ~5 reviewers = 40 reviewer turns). Каждая фаза завершилась PASS от всех её reviewers после autonomous fix loop. Reviewer disagreement случился один раз (Phase 05 security vs code по prefix guard `catalog-pictures/`) — эскалирован пользователю, выбран Variant A (с guard).

Cross-model review (Codex CLI) — PASSED после 6 fixes.

## Changed Files

**Scaffold / build (backend-dev owner):**
- `gradle/libs.versions.toml` — kotlin 2.1.20, ksp 2.1.20-1.0.31, coil3 3.1.0, mockk 1.13.10, androidx-work 2.9.1
- `buildSrc/build.gradle.kts` + compose convention plugins — kotlin.plugin.compose
- `settings.gradle.kts` — `:shared:core:catalog:data`
- `platform/android-services/build.gradle.kts`, `platform/firebase/build.gradle.kts`
- `shared/core/persistence/build.gradle.kts` + catalog:data + app-shell:data + feature:qualification:domain
- `apps/android-next/build.gradle.kts`

**Domain (Walking Skeleton integration + Phase 03 extensions):**
- `shared/core/foundation/.../QualificationLevel.kt` (MOVED from qualification:domain)
- `shared/feature/qualification/domain/dev_mode/logic/RegisterTap.kt` (param rename)
- `shared/feature/qualification/domain/dev_mode/use_case/ActivateDevModeUseCase.kt` (REWRITE lambda injection)
- 5 overlay файлов удалены (LocalDeveloperOverride/DeveloperLevelStats/EffectiveDeveloperLevel)
- `shared/feature/app-shell/domain/model/DrawerSection.kt` (HomeQuests, magic 100 → LEVEL_1.points)
- `shared/feature/app-shell/domain/model/DrawerFooterAction.kt` (+ SyncNow)
- `shared/feature/app-shell/domain/model/RootEvent.kt` (+ 3 variants)
- `shared/feature/app-shell/domain/model/TabConfig.kt` (HomeQuestsRoot)
- `shared/feature/app-shell/domain/navigation/RootComponent.kt` (onVersionTap/onSyncNow)
- `shared/feature/app-shell/domain/repository/UserStatsRepository.kt` (setLocalDeveloperLevel + refreshProfile)
- `shared/feature/app-shell/domain/logic/Visibility.kt` (superqualification bypass + visibleFooterActions new sig)
- `shared/feature/app-shell/domain/logic/AppShellTransitions.kt` (rename)
- `shared/core/catalog/domain/model/Catalog.kt` (+ pictureUrl field, cross-phase fix)
- `shared/core/catalog/domain/di/CatalogDomainModule.kt` (NEW, CreateQuestUseCase commented per cross-phase fix)
- `shared/core/sync/.../Syncable.kt` (NEW interface)

**Data layer:**
- `shared/core/persistence/.../AppDatabase.kt` (NEW), UserStatsEntity/Dao, CatalogEntity/Dao (@Transaction replaceAll), PersistenceModule
- `shared/feature/app-shell/data/.../UserStatsRepositoryImpl.kt` (REWRITE, + auth flow re-subscribe per cross-phase fix)
- `shared/feature/app-shell/data/mapper/UserStatsMapper.kt` (NEW)
- `shared/feature/app-shell/data/di/AppShellDataModule.kt` (currentUidFlow injection)
- `shared/core/catalog/data/` — NEW module: CatalogDto, CatalogRemoteDataSource interface, CatalogRepositoryImpl, CatalogLocalDataSource, StorageUrlResolver (fun interface), CatalogMapper, CatalogDtoMapper, CatalogDataModule
- `platform/firebase/.../FirebaseUserStatsDataSource.kt` (fetchRaw)
- `platform/firebase/catalog/` — NEW: FirebaseCatalogRemoteDataSource, FirestoreCatalogDtoMapper, FirebaseCatalogModule

**Platform/sync:**
- `platform/android-services/sync/SyncWorker.kt` + SyncWorkerFactory (NEW)
- `apps/android-next/di/SyncModule.kt` (NEW)
- `apps/android-next/AppApplication.kt` (Configuration.Provider, periodic + bootstrap SyncWorker)
- `apps/android-next/AndroidManifest.xml` (WorkManager auto-init disabled)

**Presentation:**
- `android/feature/app-shell/presentation/component/DefaultRootComponent.kt` — _tapProgress, onVersionTap (with pre-suspend reset), onSyncNow, events SharedFlow, sendEvent helper
- `android/feature/app-shell/presentation/ui/AppShellScreen.kt` — SnackbarHost + LaunchedEffect(rootComponent), canSeeDesignCatalog condition
- `android/feature/app-shell/presentation/ui/drawer/DrawerFooter.kt` + DrawerContent (userStats pass-through, clickable version, SyncNow branch)
- `android/feature/app-shell/presentation/ui/labels/Labels.kt` (Синхронизация + Home icon)
- `android/feature/app-shell/presentation/di/AppShellPresentationModule.kt` (userStatsRepository + workManager)
- `apps/android-next/MainActivity.kt` (events collector удалён per cross-phase fix)
- `android/core/designsystem/model/CatalogDisplayItem.kt` (NEW, + Catalog.toDisplayItem with pictureUrl pass-through)
- `android/core/designsystem/components/CatalogSpinner.kt` (NEW, ExposedDropdownMenu + "Все категории")
- `android/core/designsystem/components/CatalogGrid.kt` (NEW, LazyVerticalGrid + AsyncImage с HTTPS-only guard)

**Tests:**
- JVM: QualificationLevelTest (MOVED), RegisterTapTest (updated), ActivateDevModeUseCaseTest (REWRITE), VisibilityTest (superqualification DM-17..27), DrawerFooterActionTest, CatalogRepositoryImplTest (CF-11..23), CatalogMapperTest, FirestoreCatalogDtoMapperTest (CF-22), UserStatsRepositoryImplPhase04Test (US-01..08), UserStatsMapperTest, SyncWorkerTest (3 scenarios), CatalogFirstFetchIntegrationTest, CatalogWarmCacheIntegrationTest, CatalogOfflineEmptyIntegrationTest, SyncDeactivatesDevModeIntegrationTest, VisibilityDesignCatalogTest (DC-phase7-01..04), DevModeActivationIntegrationTest (DM-phase7-01..04), SyncNowFlowIntegrationTest (SN-phase7-01..03)
- Instrumented: AppDatabaseMigrationTest, UserStatsDaoTest, CatalogDaoTest (CF-06..10)

**Firebase Rules:**
- `firestore.rules` — добавлен `match /catalogs/{catalogId} { allow read: if request.auth != null; }`

## Cross-Phase Fixes Applied

| # | Sev | Fix applied in |
|---|-----|-----------------|
| 1 | blocker | `UserStatsRepositoryImpl` — `currentUidFlow: () -> Flow<String?>` + flatMapLatest для auth re-subscribe |
| 2 | blocker | `SyncWorker.WORK_NAME_BOOTSTRAP` — eager one-shot sync в `AppApplication.onCreate` для fresh install |
| 3 | high | `MainActivity` events collector удалён — AppShellScreen единственный consumer |
| 4 | medium | `CatalogDomainModule` — `CreateQuestUseCase` закомменчен с TODO до quiz feature |
| 5 | medium | `Catalog` domain model расширен `pictureUrl` полем; mapper chain пробрасывает resolved URL |
| 6 | low | `AppShellDataModule` — orphaned `single<Syncable>` удалён; SyncModule владеет `List<Syncable>` |

## Validation

**Final smoke test (все tasks):**
```bash
./gradlew test --no-configuration-cache                                 # GREEN (12 modules, all JVM tests)
./gradlew :apps:android-next:assembleDebug --no-configuration-cache     # GREEN
./gradlew :shared:core:persistence:assembleDebugAndroidTest --no-configuration-cache  # GREEN
```

Instrumented tests (`:shared:core:persistence:connectedDebugAndroidTest`) — APK компилируется, запуск на connected device не верифицирован в этой session.

## Remaining Issues (non-blockers)

1. **Firebase deploy manual**: `firestore.rules` update нужно задеплоить через `firebase deploy --only firestore:rules` перед release. Не автоматизируется через Gradle.
2. **Instrumented tests execution**: AppDatabaseMigrationTest, UserStatsDaoTest, CatalogDaoTest — код корректен (in-memory Room + MigrationTestHelper), но запуск на emulator/device в этой session не сделан. Рекомендуется перед release.
3. **Coil 3.1.0 vs 3.4.0**: downgrade до 3.1.0 для Kotlin 2.1.20 metadata compat. Если в будущем Kotlin → 2.3+, можно вернуть 3.4.0.
4. **`@OptIn(ExperimentalCoroutinesApi::class)`** в UserStatsRepositoryImpl для `flatMapLatest`. Можно снять при upgrade coroutines → 1.8+.
5. **`exportSchema = true` + schemaDirectory** — Room schema файлы нужно сгенерировать перед первой production миграцией (Phase 08 AppDatabaseMigrationTest использует MigrationTestHelper — требует наличия schemas/1.json при запуске на устройстве).
6. **QuestRepository production binding** — отложено до quiz feature. `CreateQuestUseCase` закомменчен в Koin module.
7. **Legacy security findings** (из Phase 01 security-reviewer): UID logging в `legacy/app/RepositoryProfileImpl.kt`, `allowBackup=true` в legacy AndroidManifest — вне scope menu-refactor, фиксируются отдельными задачами.

## Lessons Learned (для retrospective)

1. **Plan paths sometimes wrong** — `plan/phase-02/overview.md` указывал `android/feature/app-shell/presentation/.../AppShellTransitions.kt`, но файл в `shared/feature/app-shell/domain/logic/`. Devs верно обновляли реальные файлы. Recommendation: planner должен верифицировать paths перед handoff.
2. **Scope overlap для compile-required fixes** — frontend-dev-p02 взял backend scope (rename атомарный), backend-dev-p04 создал StorageUrlResolver в Phase 05 scope (Koin suspend lambda), backend-dev-p05 создал PersistenceModule (должен был быть в Phase 01). Все legitimate. Recommendation: легализовать "compile-fix outside role file" как accepted pattern в agent-communication.md.
3. **Pre-implementation reviews (premature)** — security-reviewer дважды пытался review до build gate (Phase 01, Phase 06). Самостоятельно отказывался. Recommendation: reviewer MUST check `TaskGet` + "Build PASSED" в assignment before start.
4. **Codex cross-phase review critical** — нашёл 6 issues (2 blockers) которые 40 per-phase reviewer turns пропустили. Cross-model review не обходим для integration-level issues.
5. **Carry-over tasks** — Kotlin 2.x upgrade был user decision из Phase 01, но blast radius требовал отдельного task после Phase 03. Recommendation: явное carry-over convention в pipeline instructions.
6. **Reviewer disagreement** — 1 раз (Phase 05 prefix guard). Escalation → user decision Variant A работал. Paper trail в `03-decisions.md` полезен.

## Status

**Feature `menu-refactor` — IMPLEMENTED.** Ready for merge после instrumented tests run на emulator/device и Firebase rules deploy.
