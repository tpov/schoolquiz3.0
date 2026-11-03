# Implementation Report: quizzes-screen

**Status**: implemented
**Pipeline**: 7 phases sequential (no parallel) + post-cross-phase fix
**Walking Skeleton**: SKIP (Feature Domain Contract = N/A)

## Summary

Реализована drill-down навигация Catalog → Quest → Section → Theme → Lesson с breadcrumb-путём в новом `android/feature/quizzes-screen/presentation` модуле. Entry points: `HomeQuestsScreen` (тап каталога) + `MyQuestsScreen` (тап своего квеста). Long-press Share menu в `QuestListScreen`. Все 39 acceptance criteria из `0-spec.md` покрыты.

## Phases Completed

| Phase | Goal | Status |
|-------|------|--------|
| 01 | Data layer: `QuestRepository.observeByCatalog` + `QuestDao` + 3 fakes | ✓ DONE — все 4 reviewers PASS |
| 02 | Designsystem: `BreadcrumbBar` + `HierarchyItemCard` + `QuestCard.onLongClick` + `QuestDisplayItem.catalogId` | ✓ DONE — все 4 reviewers PASS |
| 03 | Module skeleton: Gradle module + `QuizzesConfig` + `DefaultQuizzesComponent` + Koin DI | ✓ DONE — все 5 reviewers (incl. concurrency) PASS |
| 04 | Drill-down children: 5 DefaultXxx + UiState + mappers + childFactory | ✓ DONE — все 5 reviewers PASS (uiState canonical via user decision) |
| 05 | Compose screens: QuizzesScreen + 5 child screens | ✓ DONE — все 4 reviewers PASS |
| 06 | Long-press Share menu: DropdownMenu + Intent.ACTION_SEND | ✓ DONE — все 4 reviewers PASS |
| 07 | Cross-feature wiring: DefaultRootComponent + AppShellScreen overlay + HomeQuests/MyQuests TODO | ✓ DONE — все 5 reviewers PASS |
| Cross-phase | Codex Skeptic/Architect/Minimalist + smoke test fix | ✓ DONE — 1 blocker + 3 high resolved |

## Review Verdicts

### Same-model per-phase reviewers
Все 7 фаз получили PASS от полного набора reviewers (architect / code / security / completeness; concurrency для phases 03, 04, 07). См. `quality-scorecard.md` за detailed counts findings.

### Cross-phase Codex (Шаг 3)
- **Skeptic** (`_codex-review/cross-phase/skeptic.md`): 1 blocker + 3 high + 1 medium + 1 low. Все blocker/high resolved.
- **Architect** (`_codex-review/cross-phase/architect.md`): 0 findings.
- **Minimalist** (`_codex-review/cross-phase/minimalist.md`): 2 medium + 4 low. Defer как cleanup.

### Smoke test (Шаг 4)
`./gradlew test --no-configuration-cache` — BUILD SUCCESSFUL после fix-цикла.
`./gradlew assemble --no-configuration-cache` — BUILD SUCCESSFUL.

## Changed Files

### Shared data layer (Phase-01)
- `shared/feature/quest/domain/src/commonMain/kotlin/.../repository/QuestRepository.kt` — +`observeByCatalog`
- `shared/core/persistence/src/commonMain/kotlin/.../QuestDao.kt` — +`@Query observeByCatalog` (delimiter-wrapped LIKE, archived=0, ORDER BY lastModifiedAt DESC)
- `shared/feature/quest/data/src/commonMain/kotlin/.../source/QuestLocalDataSource.kt` — +interface method + impl
- `shared/feature/quest/data/src/commonMain/kotlin/.../repository/QuestRepositoryImpl.kt` — +override
- 3 копии `FakeQuestRepository.kt` (domain commonTest, presentation test, sync commonTest) — +override

### Designsystem (Phase-02)
- `android/core/designsystem/.../components/HierarchyItemCard.kt` (NEW)
- `android/core/designsystem/.../components/BreadcrumbBar.kt` (NEW)
- `android/core/designsystem/.../components/QuestCard.kt` — +`onLongClick: ((QuestId) -> Unit)?`, `combinedClickable`, Uri host validation, `R.string.long_press_action`
- `android/core/designsystem/.../model/QuestDisplayItem.kt` — +`catalogId: CatalogId`
- `android/core/designsystem/src/main/res/values/strings.xml` (NEW)
- `android/feature/quest/presentation/.../mapper/QuestToDisplayItem.kt` — +catalogId

### Quizzes-screen presentation module (Phase-03..06)
30+ новых файлов в `android/feature/quizzes-screen/presentation/`:
- `build.gradle.kts`, `AndroidManifest.xml`
- `config/QuizzesConfig.kt` — `@Serializable sealed class` (6 variants)
- `navigation/QuizzesNavigator.kt` — interface
- `component/QuizzesComponent.kt` + `QuizzesChild.kt` + `DefaultQuizzesComponent.kt`
- 5 child interfaces + 5 DefaultXxxComponent
- 4 UiState типа + 4 mapper
- `di/QuizzesPresentationModule.kt` — Koin
- 6 Compose screens
- JVM tests: `DefaultQuizzesComponentTest`, `QuizzesConfigSerializationTest`, `QuizzesStateKeeperRestoreTest`, 5 component tests, mapper test
- Compose UI instrumented tests: `QuestListScreenTest`, `SectionListScreenTest`, `LessonPlaceholderScreenTest`, `QuizzesRotationTest`, `QuestCardMenuTest`

### Cross-feature wiring (Phase-07)
- `android/feature/quest/presentation/.../component/MyQuestsComponent.kt` — +`onQuestClick`
- `android/feature/quest/presentation/.../component/DefaultMyQuestsComponent.kt` — +`onQuestDrillDown`
- `android/feature/quest/presentation/.../component/DefaultHomeQuestsComponent.kt` — +`onCatalogDrillDown`, replace TODO
- `android/feature/quest/presentation/.../screen/MyQuestsScreen.kt` — replace TODO
- `android/feature/quest/presentation/.../di/QuestPresentationModule.kt` — factory params
- `android/feature/app-shell/presentation/.../component/DefaultRootComponent.kt` — +`quizzesComponent` field, lambda closures, `quizzesComponentContext()` (override backHandler для priority=100 registration)
- `android/feature/app-shell/presentation/.../screen/AppShellScreen.kt` — `QuizzesContent` overlay
- `android/feature/app-shell/presentation/.../di/AppShellPresentationModule.kt` — quizzesFactory
- `apps/android-next/.../AppApplication.kt` — +`quizzesPresentationModule` (Phase-03)
- `settings.gradle.kts` — +`include(":android:feature:quizzes-screen:presentation")`

### Post-cross-phase fix
- **Smoke test fix (deps)**: `android/feature/quest/test-fixtures/` (NEW JVM module: FakeAuthRepository, FakeCatalogRepository, FakeQuestRepository, TestFixtures); `app-shell/presentation/build.gradle.kts` — +testImplementation на shared/feature/{section,theme,lesson}/domain + test-fixtures
- **Smoke test fix (Koin test)**: `apps/android-next/.../KoinModuleWiringTest.kt` — +stubs constructor params, +load missing modules
- **HIGH #1 fix**: `DefaultQuizzesComponent.kt:popToLevel` — virtualCount calculation для Path B (MyQuests entry)
- **HIGH #2 fix**: `QuizzesScreen.kt` — opaque background + touch-absorbing wrapper Box

### Documentation
- `docs/features/quizzes-screen/06-api-contract.md` — `val state` → `val uiState` (canonical update per Phase-04 user decision)
- `docs/features/quizzes-screen/03-decisions.md` — +ADR-QS-13 (config-based constructor) +ADR-QS-14 (concrete RootComponent in AppShellScreen)
- `docs/features/quizzes-screen/quality-scorecard.md` (NEW)
- `docs/features/quizzes-screen/_codex-review/cross-phase/{skeptic,architect,minimalist}.md` (NEW)

## Remaining Issues

### Known limitations (non-blocking, MVP-acceptable)

1. **Catalog name race** (Skeptic medium): на первом тапе MyQuests, если `homeQuestsComponent.state.value.catalogs` ещё пуст (Eagerly initial), фолбэк `"Без каталога"` замораживается в `QuizzesConfig.SectionList.titles`. Documented в `frontend.md:31`. Cosmetic UX bug, не functional.

2. **ROT-UI-01 partial verification** (Phase-05): тест использует `FakeQuizzesComponent`, не реальный `DefaultQuizzesComponent` через retainedComponent(). Documented в `QuizzesRotationTest.kt:29-35`. Полная verification instanceKeeper retention — post-MVP с custom host Activity.

3. **PRIORITY_OVERLAY deferred** (Phase-03 Pattern Invariant 5): Essenty 2.1.0 не имеет константы `BackCallback.PRIORITY_OVERLAY`; используется hardcoded `priority = 100`. Unblock: upgrade Essenty ≥ 2.4.0.

### Recommended follow-ups (cleanup)

См. `quality-scorecard.md` § Recommended follow-ups (7 items):
1. Удалить `onShareClick` из QuestListComponent interface (Minimalist medium)
2. Заменить `LessonPlaceholderComponent` на data class (Minimalist medium)
3. Унифицировать SectionListComponent/ThemeListComponent/LessonListComponent (Minimalist medium)
4. Объединить Section/Theme/Lesson mappers (Minimalist low)
5. Скрыть `Idle` sentinel за `isOpen` API (Minimalist low)
6. Очистить unused `doOnDestroy` import (Minimalist low)
7. Catalog race UX improvement (Skeptic medium)

## Open Questions

Нет открытых блокирующих questions. Все architectural decisions — задокументированы в ADR-QS-01..14.
