---
feature: quizzes-screen
plan-version: 1.0
created: 2026-04-25
status: READY_FOR_REVIEW
---

# Quizzes Screen — Implementation Plan Dashboard

## Phase Strategy

Фича реализуется в 7 последовательных фазах, разделённых по слоям и зависимостям. Каждая фаза независимо ревьюируема. Walking Skeleton: SKIP (Feature Domain Contract = N/A).

**Принцип декомпозиции:**

- Phase-01 — Data layer first: `QuestRepository.observeByCatalog` + Room DAO + fake updates. Все остальные фазы зависят от Phase-01 (QuestListComponent uses this method).
- Phase-02 — Designsystem components: `BreadcrumbBar`, `HierarchyItemCard`, `QuestCard.onLongClick`, `QuestDisplayItem.catalogId`. Независим от Phase-01 (concurrent).
- Phase-03 — Presentation skeleton: Gradle module + `QuizzesConfig` + `DefaultQuizzesComponent` (stub factory). Depends: Phase-02 (compile — `QuestDisplayItem.catalogId` must exist for QuestListComponent interface); Phase-01 (tests only — `FakeQuestRepository` used in Phase-03 integration tests).
- Phase-04 — Child components: 5 DefaultXxx + UiState types + mappers + full childFactory. Depends: Phase-01 + Phase-03.
- Phase-05 — Compose screens: QuizzesScreen router + 5 child screens. Depends: Phase-02 + Phase-04.
- Phase-06 — Share menu: DropdownMenu + Intent. Depends: Phase-05.
- Phase-07 — Integration: DefaultRootComponent wiring, AppShellScreen overlay, HomeQuests/MyQuests TODO replacement. Depends: all Phase-01..06.

**Parallel execution:** Phase-01 и Phase-02 могут запускаться параллельно (разные modules, нет runtime зависимости между ними; Phase-03 блокируется до завершения Phase-02 для QuestDisplayItem.catalogId compile).

---

## Phases Table

| Phase | Goal | Depends on | Role Inputs | Complex | Validation |
|-------|------|------------|-------------|---------|------------|
| Phase-01 | Data layer: QuestRepository.observeByCatalog + Room DAO + fake updates | — | backend.md, tests.md | No | `./gradlew :shared:feature:quest:data:test` + `:shared:core:persistence:connectedAndroidTest` (requires connected device) |
| Phase-02 | Designsystem: BreadcrumbBar + HierarchyItemCard + QuestCard.onLongClick + QuestDisplayItem.catalogId | — | frontend.md, tests.md | No | `./gradlew :android:core:designsystem:test` + `connectedAndroidTest` (requires connected device) |
| Phase-03 | Presentation skeleton: Gradle module + QuizzesConfig + DefaultQuizzesComponent (stub) | Phase-02 (compile); Phase-01 (tests: FakeQuestRepository) | backend.md, frontend.md, tests.md | Yes | `./gradlew :android:feature:quizzes-screen:presentation:test` |
| Phase-04 | Drill-down child components: 5 DefaultXxx + UiState + mappers + full childFactory | Phase-01, Phase-03 | frontend.md, tests.md | No | `./gradlew :android:feature:quizzes-screen:presentation:test` |
| Phase-05 | Compose UI screens: QuizzesScreen + 5 child screens | Phase-02, Phase-04 | frontend.md, tests.md | No | `./gradlew :android:feature:quizzes-screen:presentation:assembleDebugAndroidTest` |
| Phase-06 | Long-press Share menu: DropdownMenu + Intent.ACTION_SEND | Phase-05 | frontend.md, tests.md | No | `./gradlew :android:feature:quizzes-screen:presentation:connectedAndroidTest` (requires connected device) |
| Phase-07 | Cross-feature wiring: DefaultRootComponent + AppShellScreen + HomeQuests/MyQuests TODO | Phase-01..06 | backend.md, frontend.md, tests.md | Yes | `./gradlew assembleDebug allTests` + manual smoke test |

---

## File Map

### New Files (full list)

**Shared data layer (Phase-01):**
- `shared/feature/quest/domain/src/commonMain/.../repository/QuestRepository.kt` — MODIFIED (add `observeByCatalog`)
- `shared/feature/quest/data/src/androidMain/.../local/QuestLocalDataSource.kt` — MODIFIED (add `observeByCatalog`)
- `shared/feature/quest/data/src/androidMain/.../repository/QuestRepositoryImpl.kt` — MODIFIED (add `observeByCatalog`)
- `shared/core/persistence/src/androidMain/.../dao/QuestDao.kt` — MODIFIED (add `@Query observeByCatalog`)
- `shared/feature/quest/domain/src/commonTest/.../fake/FakeQuestRepository.kt` — MODIFIED (add override)
- `android/feature/quest/presentation/src/test/.../fake/FakeQuestRepository.kt` — MODIFIED (add override)
- `shared/core/sync/src/test/.../FakeQuestRepository.kt` — MODIFIED (add override)

**Designsystem (Phase-02):**
- `android/core/designsystem/src/main/kotlin/.../components/HierarchyItemCard.kt` — NEW
- `android/core/designsystem/src/main/kotlin/.../components/BreadcrumbBar.kt` — NEW
- `android/core/designsystem/src/main/kotlin/.../model/QuestDisplayItem.kt` — MODIFIED (+catalogId field)
- `android/feature/quest/presentation/src/main/kotlin/.../mapper/QuestToDisplayItem.kt` — MODIFIED (+catalogId)
- `android/core/designsystem/src/main/kotlin/.../components/QuestCard.kt` — MODIFIED (+onLongClick nullable)

**quizzes-screen/presentation module (Phase-03..06):**
- `android/feature/quizzes-screen/presentation/build.gradle.kts` — NEW
- `android/feature/quizzes-screen/presentation/src/main/AndroidManifest.xml` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../config/QuizzesConfig.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../navigation/QuizzesNavigator.kt` — NEW (interface only)
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/QuizzesComponent.kt` — NEW (interface)
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/QuizzesChild.kt` — NEW (sealed interface)
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultQuizzesComponent.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/QuestListComponent.kt` — NEW (interface, phase-04 full)
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/SectionListComponent.kt` — NEW (interface)
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/ThemeListComponent.kt` — NEW (interface)
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/LessonListComponent.kt` — NEW (interface)
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/LessonPlaceholderComponent.kt` — NEW (interface)
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../uistate/QuestListUiState.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../uistate/HierarchyListUiState.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../uistate/HierarchyItemUi.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../uistate/LessonPlaceholderUiState.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../mapper/SectionDrillMapper.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../mapper/ThemeDrillMapper.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../mapper/LessonDrillMapper.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultQuestListComponent.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultSectionListComponent.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultThemeListComponent.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultLessonListComponent.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultLessonPlaceholderComponent.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../di/QuizzesPresentationModule.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/QuizzesScreen.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/QuestListScreen.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/SectionListScreen.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/ThemeListScreen.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/LessonListScreen.kt` — NEW
- `android/feature/quizzes-screen/presentation/src/main/kotlin/.../screen/LessonPlaceholderScreen.kt` — NEW

**Cross-feature wiring (Phase-07):**
- `android/feature/quest/presentation/src/main/kotlin/.../component/MyQuestsComponent.kt` — MODIFIED (+onQuestClick)
- `android/feature/quest/presentation/src/main/kotlin/.../component/DefaultMyQuestsComponent.kt` — MODIFIED (+onQuestDrillDown param)
- `android/feature/quest/presentation/src/main/kotlin/.../component/DefaultHomeQuestsComponent.kt` — MODIFIED (+onCatalogDrillDown param, replace TODO)
- `android/feature/quest/presentation/src/main/kotlin/.../screen/MyQuestsScreen.kt` — MODIFIED (replace TODO QuestCard.onClick)
- `android/feature/quest/presentation/src/main/kotlin/.../di/QuestPresentationModule.kt` — MODIFIED (factory params)
- `android/feature/app-shell/presentation/src/main/kotlin/.../component/DefaultRootComponent.kt` — MODIFIED (+quizzesComponent + lambdas)
- `android/feature/app-shell/presentation/src/main/kotlin/.../screen/AppShellScreen.kt` — MODIFIED (+QuizzesContent)
- `apps/android-next/src/main/kotlin/.../AppApplication.kt` — MODIFIED (Phase-03: +quizzesPresentationModule)
- `settings.gradle.kts` — MODIFIED (Phase-03: +quizzes-screen:presentation include)

### Test Files (selected key tests)

- `shared/core/persistence/src/androidTest/.../dao/QuestDaoByCatalogTest.kt` — NEW (instrumented)
- `android/core/designsystem/src/androidTest/.../components/BreadcrumbBarTest.kt` — NEW (instrumented)
- `android/core/designsystem/src/androidTest/.../components/HierarchyItemCardTest.kt` — NEW (instrumented)
- `android/feature/quizzes-screen/presentation/src/test/.../component/DefaultQuizzesComponentTest.kt` — NEW (JVM)
- `android/feature/quizzes-screen/presentation/src/test/.../QuizzesConfigSerializationTest.kt` — NEW (JVM)
- `android/feature/quizzes-screen/presentation/src/test/.../QuizzesStateKeeperRestoreTest.kt` — NEW (JVM)
- `android/feature/quizzes-screen/presentation/src/test/.../component/DefaultQuestListComponentTest.kt` — NEW (JVM)
- `android/feature/quizzes-screen/presentation/src/androidTest/.../screen/QuestListScreenTest.kt` — NEW (instrumented)
- `android/feature/quizzes-screen/presentation/src/androidTest/.../screen/QuestCardMenuTest.kt` — NEW (instrumented)
- `android/feature/quizzes-screen/presentation/src/androidTest/.../screen/QuizzesRotationTest.kt` — NEW (instrumented)
- `android/feature/app-shell/presentation/src/test/.../component/DefaultRootComponentWiringTest.kt` — NEW (JVM)

---

## AC Coverage Summary

Full AC#1..39 from `0-spec.md:475-528`:

| AC# | Description (abbreviated) | Phase |
|-----|---------------------------|-------|
| AC#1 | HomeQuests catalog tap → QuestListComponent with breadcrumb "{c.name}", sorted DESC | Phase-07 |
| AC#2 | MyQuests quest tap → SectionListComponent with breadcrumb "{c.name} > {q.title}", sorted ASC | Phase-07 |
| AC#3 | QuestList quest tap → SectionListComponent "{c.name} > {q.title}" | Phase-04, Phase-07 |
| AC#4 | SectionList section tap → ThemeListComponent | Phase-04 |
| AC#5 | ThemeList theme tap → LessonListComponent | Phase-04 |
| AC#6 | LessonList lesson tap → LessonPlaceholderComponent with placeholder text | Phase-04, Phase-05 |
| AC#7 | System back pops ChildStack; breadcrumb trims last segment | Phase-03 |
| AC#8 | Breadcrumb segment tap → popTo that level (removes deeper) | Phase-03, Phase-05 |
| AC#9 | Tap current (last) breadcrumb segment → no action (not clickable) | Phase-02 (BreadcrumbBar) |
| AC#10 | QuestListComponent QuestCard long-press → DropdownMenu with «Поделиться» | Phase-06 |
| AC#11 | MyQuestsScreen QuestCard long-press → no action (existing UI unchanged) | Phase-06 (regression) |
| AC#12 | «Поделиться» tap → Intent.ACTION_SEND text/plain, menu closes | Phase-06 |
| AC#13 | Tap outside menu → menu closes, no action | Phase-06 |
| AC#14 | ActivityNotFoundException → caught, logged; no UI notification | Phase-06 |
| AC#15 | HierarchyItemCard long-press → nothing (no menu in MVP) | Phase-05, Phase-06 |
| AC#16 | Drill-down to empty level → empty state text centered | Phase-04 |
| AC#17 | Active Flow.collect: sync updates UI; breadcrumb stays frozen | Phase-04 |
| AC#18 | ThemeListComponent: parent section archived (cascade) → empty state «Нет тем», no auto-pop | Phase-04 |
| AC#19 | Fresh install (Room empty) → HomeQuests empty catalogs; drill-down unavailable until sync | Phase-04, Phase-07 |
| AC#20 | Offline: drill-down on cached data works; no offline indicator | Phase-04 |
| AC#21 | Process death during drill-down → ChildStack restored with same level + breadcrumb | Phase-03 |
| AC#22 | Rotation → Components preserved (instanceKeeper); LazyColumn scroll position retained | Phase-03, Phase-05 |
| AC#23 | Sync renames quest during drill-down → breadcrumb frozen (old title); list updates | Phase-04 |
| AC#24 | HierarchyItemCard with orderLabel="1." and subtitleCount=null → shows orderLabel + title | Phase-02, Phase-04 |
| AC#25 | HierarchyItemCard with orderLabel=null → shows title only | Phase-02 |
| AC#26 | BreadcrumbBar 3 segments: separated by «>», last not clickable and visually distinct | Phase-02 |
| AC#27 | BreadcrumbBar long segment (>20 chars) → TextOverflow.Ellipsis, maxLines=1 | Phase-02 |
| AC#28 | Breadcrumb segment n tap → popTo level n (deeper entries removed) | Phase-03, Phase-05 |
| AC#29 | DI: QuizzesPresentationModule registered in AppApplication startKoin; all Components created via Koin factory with correct ComponentContext and dependency parameters | Phase-03, Phase-07 |
| AC#30 | Code: no file in new feature imports Android/SDK types in shared/feature/*/domain (invariant 1 not violated) | Phase-01 |
| AC#31 | Code: no Activity/Fragment calls Repository/UseCase directly (invariant 2 not violated; all via Components) | Phase-03..07 |
| AC#32 | Code: QuestRepository.observeByCatalog(catalogId, shelf) added to shared domain interface, implemented in data layer + FakeQuestRepository updated | Phase-01 |
| AC#33 | Tests: JVM unit tests for each DefaultXxxListComponent via FakeXxxRepository (empty → loaded → archived disappears flows) | Phase-04 |
| AC#34 | Tests: JVM unit test for breadcrumb pop logic — PopBreadcrumbTest | Phase-03 |
| AC#35 | Tests: JVM unit test for LessonPlaceholderComponent (correct title in state) | Phase-04 |
| AC#36 | Tests: Compose UI tests for BreadcrumbBar (segment tap → callback) and HierarchyItemCard (null fields not shown) | Phase-02 |
| AC#37 | Tests: Compose UI test for QuestListComponent long-press → menu opens → tap Share → Intent fired | Phase-06 |
| AC#38 | Build: `./gradlew assemble --no-configuration-cache` green | Phase-07 |
| AC#39 | Tests: `./gradlew allTests --no-configuration-cache` green | Phase-07 |

---

## Open Questions (for lead)

1. **DEFERRED — BackCallback.PRIORITY_OVERLAY**: Verified absent in Essenty 2.1.0 (`gradle/libs.versions.toml:36`). Fallback: `priority = 100` (existing pattern: `DefaultRootComponent.kt:140`). Unblock: upgrade Essenty ≥ 2.4.0. No action required for MVP.

2. **RESOLVED — Quest mapper cross-module**: Option A chosen — duplicate `Quest.toQuestDisplayItem()` in `quizzes-screen/presentation/mapper/QuestToDisplayItemMapper.kt`. Does not import from `quest/presentation` (Invariant 3 compliant). See `phase-04/overview.md` Options Considered.

3. **RESOLVED — combinedClickable @OptIn**: No `@OptIn` needed. BOM 2024.09.02 → compose-foundation ~1.7.x; `combinedClickable` stable since 1.4.0. Ref: `gradle/libs.versions.toml:33`.

4. **RESOLVED — Section/Theme/Lesson .order field**: All three domain models have `val order: Int` (non-nullable). `orderLabel = "${order + 1}."` (0-based → 1-based display). Evidence: `shared/feature/section/domain/.../model/Section.kt:27`, `Theme.kt:23`, `Lesson.kt:23`.

5. **OPEN — AppShellScreen QuizzesContent z-order**: Phase-07 adds overlay. Frontend-dev Phase-07 must read `AppShellScreen.kt` to determine if `Box` or `Scaffold` is used (affects overlay layering). No blocker — verify at Phase-07 start.

6. **DESIGN UPDATE — `onShareClick` in QuestListComponent interface**: Share dispatch moved to UI layer (ADR-QS-08: `03-decisions.md:293`). `DefaultQuestListComponent.onShareClick` is a no-op stub. Consider removing `fun onShareClick(quest: QuestDisplayItem)` from `QuestListComponent` interface (`06-api-contract.md:529`) in a follow-up design update. If removed: `FakeQuestListComponent` also drops the override. Non-blocking for MVP — stub is harmless.
