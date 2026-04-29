# Plan: lesson-runner

Status: **planned — fix-loop round-5+6 applied (2026-04-27)**

fix-loop round-3 changes (NEW BLOCKERS A-D + HIGH + PARTIAL resolutions):
- **NEW BLOCKER A RESOLVED**: Navigation contract A2 hybrid fully applied — `LessonRunnerScreen(component, onNavigateBack, onSegmentClick)`, `RunnerEvent.NavigateBack` in phase-04/backend.md + phase-05/frontend.md + phase-06/backend.md. `QuizzesComponent.popCurrentChild()` added. `DefaultLessonRunnerRootComponent` does NOT take `StackNavigation` in constructor.
- **NEW BLOCKER B RESOLVED**: `single<LessonRunnerComponentFactory>` binding added to phase-07/backend.md AppApplication.kt; IT-09h added to phase-07/tests.md + overview.md AC
- **NEW BLOCKER C RESOLVED**: `lessonRunnerDomainKoinAdapter` Signature Card in phase-03/backend.md updated to canonical constructor params per `06-api-contract.md:568-590` (`attemptRepository`, `ratingRepository`, `clock`, `attemptIdProvider`)
- **NEW BLOCKER D RESOLVED**: ADR-QS-15 SUPERSEDED note added to phase-06/backend.md Pattern Invariants; design doc already updated
- **NEW HIGH (paths) RESOLVED**: Filesystem paths use hyphen `lesson-runner`, `quizzes-screen` consistently in validation greps across phase-06/tests.md, phase-07/overview.md
- **BLOCKER 3 (PARTIAL→RESOLVED)**: phase-02/tests.md DAO tests updated — `dao.upsert()`, `OnConflictStrategy.REPLACE`, `hasSubmitted: Flow<Boolean>`; phase-03/tests.md Fake blueprints updated
- **HIGH 6 (PARTIAL→RESOLVED)**: AC-55/58 greps added to phase-07/overview.md Validation with correct hyphen filesystem paths; AC-56 bidirectional grep fixed
- **ТЗ paths RESOLVED**: `LessonItemUi.kt` → `uistate/` (was `model/`); QuizzesScreen/LessonListScreen → `screen/` confirmed
- **Canonical refs RESOLVED**: all `§LR-N` refs replaced with `06-api-contract.md:NNN` line numbers in phase-04/backend.md, phase-03/backend.md, phase-06/backend.md, phase-06/frontend.md
- **Pattern Invariants file:line RESOLVED**: phase-06/backend.md, phase-06/frontend.md, phase-06/tests.md updated with `file:line` refs
- **Tests Required GWT RESOLVED**: phase-02/overview.md, phase-05/overview.md, phase-07/overview.md rewritten to given/when/then format

fix-loop round-2 changes (preserved for history):
- BLOCKER 3 RESOLVED: DAO signatures aligned (upsert+REPLACE, hasSubmitted: Flow<Boolean>)
- BLOCKER 4 RESOLVED: Provider interfaces added to phase-01/backend.md as New Files
- BLOCKER 1+2 (Open Q 1+3) RESOLVED: ADR-LR-16 + ADR-LR-17 applied
- HIGH 5 RESOLVED: FLAG_SECURE in Result screen uses `state.attempt.mode == Difficulty.HARD`
- HIGH 6 RESOLVED: AC 55-65 mapped
- phase-06 Options Considered added
- All canonical refs updated to line-number format
- Tests Required in overview files rewritten to given/when/then format
- Path consistency fixed: quizzes-screen uses `screen/` and `uistate/`, NOT `ui/`
- README module count corrected: 3 new Gradle modules
- Open Questions 1-3 RESOLVED

## Phase Strategy

**Bottom-up + parallel vertical slices.**

Phase-01 разрешает все structural BLOCKERS (core types, serialization, bidirectional coupling, naming, provider interfaces). Phase-02 (persistence) и Phase-03 (data) стартуют параллельно после Phase-01. Phase-04 (presentation component + interface/factory в lesson-runner/presentation per ADR-LR-16) идёт параллельно с Phase-02/03. Phase-05 (Compose UI) идёт параллельно с Phase-06 (quizzes-screen integration per ADR-LR-17) после Phase-04. Phase-07 (composition root) закрывает интеграцию.

```
Phase-01 (Foundation)
├── Phase-02 (Persistence)    ─┐
├── Phase-03 (Data)            ├─ parallel
└── Phase-04 (Presentation)   ─┘
        ├── Phase-05 (Compose UI)          ─┐ parallel
        └── Phase-06 (Quizzes Integration) ─┘
                └── Phase-07 (Composition Root)
```

---

## Phases Table

| Phase | Goal | Depends on | Role Inputs | Validation | AC covered |
|-------|------|------------|-------------|------------|-----------|
| phase-01 | Foundation & Core Types: TopParticipant→core, @Serializable, parser, rename raw→value, Lesson extension | — | backend.md, tests.md | `allTests` + bidirectional grep | ADR-LR-05/06/08/12 prerequisites |
| phase-02 | Persistence: Migration v3→v4, new entities/DAOs, TypeConverters, LessonEntity+mapper chain | phase-01 | backend.md, tests.md | `connectedAndroidTest` (MT-01..MT-07) | AC-39 (lessonVersion), prep for AC-16,37,38,40,44 |
| phase-03 | Data Layer: lesson-runner/data NEW module, RepositoryImpl, Providers, Koin adapter | phase-01, phase-02 | backend.md, tests.md | `jvmTest` + IT-01,08 proxy | AC-53 (Koin wiring) prep |
| phase-04 | Presentation Component: DefaultLessonRunnerRootComponent, states, events, Koin module | phase-01, phase-03 | backend.md, tests.md | `test` (PT-01..PT-41, IT-02..IT-08) | AC-1..5, 15..43, 52a, 52b |
| phase-05 | Compose UI: LessonRunnerScreen, 4 question types, dialogs, result, FLAG_SECURE, timer | phase-04 | frontend.md, tests.md | `connectedAndroidTest` (CT-01..CT-30) | AC-1..5, 28..34, 41..52b |
| phase-06 | Quizzes-screen Integration: LessonPlaceholder→LessonRunner atomic replace, LessonItemCard, bestStars/hardUnlocked | phase-01, phase-03, phase-04, phase-05 | backend.md, frontend.md, tests.md | `test` + `connectedAndroidTest` (PT-15..17, PT-34..36, CT-22..24) | AC-21..23, 47..49 |
| phase-07 | Composition Root & Smoke: AppApplication.startKoin + LessonRunnerComponentFactory binding + KoinModuleWiringTest + assembleDebug | ALL | backend.md, tests.md | `assembleDebug` + `ciCheck` + manual smoke (IT-09a..IT-09h) | AC-53 + smoke |

---

## AC Coverage Map

AC 1..65 distribution across phases:

| AC Range | Phase |
|----------|-------|
| AC-1..5 (entry, init, exit, complete) | phase-04 (component), phase-05 (UI) |
| AC-6..14 (score per type: SingleChoice, MultipleChoice, Ordering, FillBlank) | DT-01..12 already green (Walking Skeleton) |
| AC-15 (auto-random on timeout) | phase-04 PT-10, phase-05 CT-10 |
| AC-16 (Room save + observe) | phase-02 (DAO), phase-03 (repo), phase-04 PT-26 |
| AC-17..20 (Stars formula EASY/HARD) | DT-21..29 already green; phase-04 PT-11..14 |
| AC-21..23 (bestStars/hardUnlocked) | phase-06 PT-15..17 |
| AC-24..27 (timer formula + floor) | DT-36..39 already green; phase-04 PT-18..21 |
| AC-28..30 (FLAG_SECURE HARD/EASY; Result screen uses `state.attempt.mode == HARD`) | phase-05 CT-11..13, CT-29; HIGH 5 fix applied |
| AC-31..34 (onStop/onResume/continue/exit) | phase-04 PT-22..25; phase-05 CT-14..17 |
| AC-35..36 (instanceKeeper rotation) | phase-04 IT-02, IT-03 |
| AC-37..40 (save attempt: once, content, lessonVersion) | phase-04 PT-26..28; phase-03 IT-01 |
| AC-41..44 (rating prompt, submit) | phase-04 PT-29..32; phase-05 CT-18..19 |
| AC-45..46 (top3 section, avatar placeholder) | phase-04 PT-33; phase-05 CT-20..21 |
| AC-47..49 (LessonItemCard: stars, checkbox) | phase-06 PT-34..36; phase-05/06 CT-22..24 |
| AC-50..51 (empty pool/no valid questions) | phase-04 PT-37..38; phase-05 CT-25..26 |
| AC-52..52b (invalid payload, save/rating errors) | DT-58..61 already green; phase-04 PT-39..41 |
| AC-53 (Koin wiring: both LessonRunnerDataModule + PresentationModule registered) | phase-07 IT-09a..g |
| AC-54 (domain purity: no serialization import in lesson-runner/domain) | phase-01 validation grep |
| AC-55 (no Activity/Fragment calls Repository/UseCase directly) | Architectural invariant — enforced via phase-07 validation grep restricted to screen/ files: `rg "getKoin\(\|koinInject\(\|inject<" android/feature/lesson-runner/presentation/src/main -g "**/screen/**/*.kt"` — must be empty (Component/data layer legitimate consumers; only Compose screens forbidden from DI access) |
| AC-56 (lesson-runner/presentation does NOT import quizzes-screen/presentation) | Enforced by ADR-LR-17 reverse-direction blocker; phase-07 validation grep: `rg "^import .*quizzes_screen" android/feature/lesson-runner -g "*.kt"` — must be empty (filesystem hyphen path) |
| AC-57 (quizzes-screen imports lesson-runner config for push — documented in design) | ADR-LR-16 + ADR-LR-17 docs; phase-06 backend.md |
| AC-58 (no Hilt/Dagger annotations) | phase-07 validation grep: `rg "@(Inject\|Provides\|Binds\|Module\|HiltAndroidApp)"` — must be empty |
| AC-59 (no direct Firebase/Firestore writes from feature; only via Repository) | Architectural invariant — `clean-architecture.md`; Repository impls in data layer only; verified by phase-03 + phase-07 architecture-review tag |
| AC-60 (domain tests cover all Domain Test Scenarios ~89 tests) | DT-01..82 already green in Walking Skeleton (`./gradlew :shared:feature:lesson-runner:domain:jvmTest`); phase-01 compilation gate |
| AC-61 (JVM unit tests for presentation Component via fakes) | phase-04 PT-01..PT-41 |
| AC-62 (Compose UI tests for key scenarios) | phase-05 CT-01..CT-30 |
| AC-63 (domain jvmTest green) | phase-01 validation: `./gradlew :shared:feature:lesson-runner:domain:jvmTest` |
| AC-64 (assemble green after implementation) | phase-07 IT-09a smoke build: `./gradlew :apps:android-next:assembleDebug` |
| AC-65 (test + allTests green) | phase-07 final gate: `./gradlew test --no-configuration-cache` + `./gradlew allTests --no-configuration-cache` |

---

## File Map

### New Files (~38 production + ~15 test files)

**New Gradle Modules (3):**
- `shared/core/leaderboard/` (1 production file: `TopParticipant.kt`)
- `shared/feature/lesson-runner/data/` (8 production files: repos, mappers, providers, Koin modules)
- `android/feature/lesson-runner/presentation/` (14 production files: interface, factory, component, state types, Koin module, UI composables)

**New files in existing modules (~15):**
- `shared/core/question-schema/`: `KotlinxSerializationQuestionContentParser.kt`, `QuestionSchemaModule.kt`
- `shared/core/persistence/`: `LessonAttemptEntity.kt`, `LessonRatingSubmittedLocalEntity.kt`, `LessonAttemptDao.kt`, `LessonRatingLocalDao.kt`, `DifficultyConverter.kt`, `TopParticipantListConverter.kt`, `Migration3to4.kt`
- `shared/feature/lesson-runner/domain/provider/`: `AttemptIdProvider.kt`, `RandomSeedProvider.kt`, `RatingIdProvider.kt` (phase-01 BLOCKER 4 fix)
- `android/core/navigation/`: no new files (ADR-LR-16 — NOT lesson-runner concern)
- `android/feature/quizzes-screen/presentation/`: `LessonItemUi.kt` (uistate/), `LessonItemCard.kt` (screen/)

### Modified Files (~20 production files)

- `shared/core/question-schema/`: `QuestionContent.kt` (+@Serializable), `Difficulty.kt` (+@Serializable)
- `shared/feature/lesson-runner/domain/`: `AttemptId.kt` (rename raw→value), `RatingId.kt` (rename raw→value), Walking Skeleton ~10 files (import update for TopParticipant + raw→value)
- `shared/feature/lesson/domain/`: `Lesson.kt` (+3 fields), `build.gradle.kts` (+leaderboard dep)
- `shared/feature/lesson/data/`: `LessonEntity.kt`, `LessonMapper.kt`, `LessonDtoMapper.kt`, `FirestoreLessonDtoMapper.kt`, `LessonDto.kt`
- `shared/core/persistence/`: `AppDatabase.kt` (v4), `PersistenceModule.kt`
- `android/feature/quizzes-screen/presentation/`: `QuizzesConfig.kt`, `QuizzesChild.kt`, `DefaultQuizzesComponent.kt`, `QuizzesScreen.kt` (screen/), `DefaultLessonListComponent.kt`, `LessonListScreen.kt` (screen/), `QuizzesPresentationModule.kt`, `build.gradle.kts` (+lesson-runner dep, ADR-LR-17)
- `apps/android-next/`: `AppApplication.kt` (+4 new Koin modules), `settings.gradle.kts` (+leaderboard, +lesson-runner:data, +lesson-runner:presentation)

### Deleted Files (4 production + 3 tests + 1 domain)

**`android/feature/quizzes-screen/presentation/src/main/`:**
- `component/LessonPlaceholderComponent.kt` (interface)
- `component/DefaultLessonPlaceholderComponent.kt`
- `screen/LessonPlaceholderScreen.kt`
- `uistate/LessonPlaceholderUiState.kt`

**`android/feature/quizzes-screen/presentation/src/{test,androidTest}/`:**
- `test/.../component/DefaultLessonPlaceholderComponentTest.kt`
- `androidTest/.../fake/FakeLessonPlaceholderComponent.kt`
- `androidTest/.../screen/LessonPlaceholderScreenTest.kt`

**Updated tests (2 — replace LessonPlaceholder cases с LessonRunner, see phase-06/backend.md):**
- `test/.../QuizzesConfigSerializationTest.kt`
- `test/.../component/DefaultLessonListComponentTest.kt`

**Domain:**
- `shared/feature/lesson-runner/domain/.../model/TopParticipant.kt` (moved to `shared/core/leaderboard/`)

---

## Open Questions

1. ~~**LessonRunnerRootComponent interface location**~~: **RESOLVED (ADR-LR-16)** — Interface живёт в `android/feature/lesson-runner/presentation/`. Circular dep устранён. `android/core/navigation` не затронут.

2. ~~**navigation: StackNavigation в DefaultLessonRunnerRootComponent**~~: **RESOLVED (ADR-LR-17 + round-3)** — Component не получает `StackNavigation`. A2 hybrid: `LessonRunnerScreen(component, onNavigateBack, onSegmentClick)`. `RunnerEvent.NavigateBack` → `onNavigateBack()` → `QuizzesComponent.popCurrentChild()` → `navigation.pop()`. Design updated `07-events.md:13`, `06-api-contract.md:306,319,342`.

3. ~~**QuizzesScreen imports LessonRunnerScreen**~~: **RESOLVED (ADR-LR-17)** — Direct Compose import разрешён как ChildStack Compose rendering exception. Precedent: `AppShellScreen.kt:53-56`. Grep gate enforces one-way direction.

4. **RunnerLogic.computeBestStars / computeHardUnlocked**: подтвердить что эти functions существуют в Walking Skeleton `RunnerLogic.kt`. Если нет — backend-dev Phase-06 пишет inline computation (per spec formulas). **Test-dev проверяет в Walking Skeleton before Phase-06 starts.** (Остаётся open до Phase-06 kickoff.)

5. ~~**AC-55..65**~~: **RESOLVED** — Полная AC Coverage Map 1..65 добавлена выше; phase-07/overview.md greps added for AC-55, AC-56, AC-58 (hyphen filesystem paths).

6. ~~**LessonRunnerComponentFactory Koin binding**~~: **RESOLVED (round-3 NEW BLOCKER B)** — `single<LessonRunnerComponentFactory>` added to phase-07/backend.md (AppApplication.kt composition root per `06-api-contract.md:374`). IT-09h validates.

---

Status: **planned — fix-loop round-5 applied (2026-04-27)** — All round-4 BLOCKERs RESOLVED. Residual issues: 2 PARTIAL items (canonical 06-api-contract.md missing explicit Clock binding — design-side; phase-06 Pattern Invariants without file:line — acceptable per planner.md grep gates). 0 fenced kotlin blocks; ready for human approval.
