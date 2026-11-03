# Implementation — lesson-runner

Status: **implemented (Codex Round 3 PASS, 0 issues)**
Дата: 2026-04-28
Branch: `kmp-skillify-4.0`

## Summary

Lesson-runner — gameplay loop викторины — реализован атомарной заменой `LessonPlaceholder` на полноценный flow в `quizzes-screen`. Один вопрос на экран, таймер, auto-random на timeout, результат с процентами/звёздами/топ-3, опрос «Оцените урок», запись попытки в Room. Все 65 acceptance criteria из `0-spec.md` покрыты. 7 фаз pipeline pass (phase-01 → phase-07).

## Phases Completed

| Phase | Goal | Verdict | Notable findings |
|-------|------|---------|-------------------|
| Phase-01 | Foundation & Core Types | PASS (5/5 reviewers) | mini-fix `KoinModuleWiringTest.kt:320,393` (pre-existing baseline) |
| Phase-02 | Persistence — Migration v3→v4 | PASS (5/5 reviewers) | ADR-LR-18: DifficultyConverter removed |
| Phase-03 | Data Layer (NEW lesson-runner/data) | PASS (5/5 reviewers) | jvmAndAndroidMain intermediate source set |
| Phase-04 | Presentation Component | PASS (5/5 reviewers) | 3 compile blockers + HIGH (componentJob, double-complete) — все resolved fix loop |
| Phase-05 | Compose UI (LessonRunnerScreen + 12 composables) | PASS (5/5 reviewers) | ADR-LR-19: RunnerUiState.Result flat projection (security-driven) |
| Phase-06 | Quizzes-screen Integration (atomic replace) | PASS (5/5 reviewers) | LessonListUiState sealed (Loading/Empty/Loaded) |
| Phase-07 | Composition Root + Smoke | PASS (4/5 reviewers; concurrency N/A) | ADR-LR-20: LessonRunnerComponentFactory in presentation module |

## Review Verdicts

| Reviewer | Phases | Total findings | Blockers resolved | High resolved | Medium resolved | Low documented |
|----------|--------|----------------|-------------------|---------------|-----------------|----------------|
| architect-reviewer | 1-7 | 5 | 0 | 2 | 2 | 1 |
| code-reviewer | 1-7 | 9 | 1 | 2 | 4 | 2 |
| security-reviewer | 1-7 | 7 | 0 | 0 | 2 | 5 |
| completeness-reviewer | 1-7 | 8 | 5 | 0 | 1 | 2 |
| concurrency-reviewer | 2-6 | 5 | 0 | 1 | 1 | 3 |

Все findings закрыты автономно через reviewer↔coder loop. Lead вмешался только для 3 design escalations (ADR-LR-18/19/20).

## Changed Files

### NEW Modules (3 Gradle modules)

- `shared/core/leaderboard/` — TopParticipant @Serializable (1 file)
- `shared/feature/lesson-runner/data/` — RepositoryImpls, mappers, providers, Koin modules (10 files)
- `android/feature/lesson-runner/presentation/` — Component, states, events, mapper, Compose UI, fakes (~30 files including tests)

### NEW Files in existing modules (~12)

- `shared/core/question-schema/`: KotlinxSerializationQuestionContentParser.kt, di/QuestionSchemaModule.kt
- `shared/core/persistence/`: LessonAttemptEntity, LessonRatingSubmittedLocalEntity, LessonAttemptDao, LessonRatingLocalDao, TopParticipantListConverter, Migration3to4
- `shared/feature/lesson-runner/domain/provider/`: AttemptIdProvider, RandomSeedProvider, RatingIdProvider
- `android/feature/quizzes-screen/presentation/`: uistate/LessonItemUi.kt, screen/LessonItemCard.kt, uistate/LessonListUiState.kt

### Modified Files (~20)

Domain: Lesson +3 fields (averageRating, ratingCount, top3), AttemptId/RatingId .raw→.value, QuestionContent + Difficulty @Serializable.
Data: LessonEntity +3 cols, LessonMapper backward-compat, LessonDtoMapper, FirestoreLessonDtoMapper (HTTPS-only avatarUrl).
Persistence: AppDatabase v4, PersistenceModule (addMigrations + addTypeConverter, removed fallbackToDestructiveMigration).
Quizzes-screen: QuizzesConfig (LessonRunner add, LessonPlaceholder remove), QuizzesChild, DefaultQuizzesComponent (+3 deps), QuizzesScreen, DefaultLessonListComponent (combine flow + 2 deps), LessonListScreen, QuizzesPresentationModule, build.gradle.kts.
Composition root: AppApplication.kt (+4 modules), apps/android-next/build.gradle.kts.

### Deleted Files

Production (4): LessonPlaceholderComponent.kt, DefaultLessonPlaceholderComponent.kt, LessonPlaceholderScreen.kt, LessonPlaceholderUiState.kt.
Tests (3): DefaultLessonPlaceholderComponentTest.kt, FakeLessonPlaceholderComponent.kt, LessonPlaceholderScreenTest.kt.
Domain (1): TopParticipant.kt в lesson-runner/domain (moved to core/leaderboard).
Phase-02 deletion: DifficultyConverter.kt + MT-05 tests (ADR-LR-18 supersedes).

## Test Coverage

- **Walking Skeleton domain (commonTest)**: ~89 tests via fakes (DT-01..82) — all green
- **Phase-01 (foundation)**: 19 tests — parser round-trip, rename verification, serialization, Lesson fields, providers, TopParticipant
- **Phase-02 (persistence)**: 19 tests — MT-01..07 migration (instrumented), DAO-01..05 (instrumented), Mapper-01..04 (jvm), TypeConverter tests
- **Phase-03 (data)**: 13 tests — IT-01/IT-08 proxy, providers, mappers
- **Phase-04 (presentation)**: 41 PT tests + IT-02/03 — Component lifecycle, state, events
- **Phase-05 (Compose UI)**: 27 CT tests + 3 @Ignore (CT-22..24 deferred to phase-06) — instrumented Compose tests
- **Phase-06 (quizzes integration)**: 11 tests — PT-15..17, PT-34..36, CT-22..24, serialization
- **Phase-07 (composition root)**: 8 IT-09a..h — Koin wiring resolution

**Total**: ~227+ tests (Walking Skeleton + 7 phases). All green at pipeline close.

## Build Validation

- `./gradlew ciCheck --no-configuration-cache` — GREEN (2300+ tasks)
- `./gradlew test --no-configuration-cache` — GREEN
- `./gradlew allTests --no-configuration-cache` — GREEN
- `./gradlew :apps:android-next:assembleDebug --no-configuration-cache` — GREEN
- `./gradlew :shared:core:persistence:connectedAndroidTest` — 46/46 на Pixel 10 Pro
- `./gradlew :android:feature:lesson-runner:presentation:connectedAndroidTest` — 27/27 (3 @Ignore deferred) на TECNO KG5m
- `./gradlew :android:feature:quizzes-screen:presentation:connectedAndroidTest` — included in build gate
- `./gradlew detekt ktlintCheck --no-configuration-cache` — GREEN

## Smoke

- App installed on Pixel 10 Pro (`adb install` → Success)
- Launched via `adb shell monkey -p com.tpov.schoolquiz.next -c android.intent.category.LAUNCHER 1` — no FATAL log entries
- Полная UI navigation (Catalog → Quest → Section → Theme → Lesson → tap → LessonRunnerScreen) — deferred user verification (interactive UI testing вне scope automated smoke)

## ADR Additions During Implementation

| ADR | Phase | Decision | Rationale |
|-----|-------|----------|-----------|
| LR-18 | 02 | DifficultyConverter removed; mapper-based Difficulty↔Int conversion in data layer | Room KMP 2.7+ rejects unused converters; entity uses Int isHard, mapper handles conversion. Plan invariant overview.md:129 superseded. |
| LR-19 | 04→05 | RunnerUiState.Result uses flat projection instead of attempt: Attempt aggregate | Security: attempt contained PII (userId, codeAnswer, attemptId) — minimal exposure principle in public StateFlow. 06-api-contract.md:408 + frontend.md Pattern Invariant superseded. |
| LR-20 | 04→07 | single<LessonRunnerComponentFactory> in lessonRunnerPresentationModule, NOT in AppApplication | Phase-04 already established single<> binding; phase-07 plan duplication would cause Koin override warning. Per 06-api-contract.md:374, factory binding stays in feature presentation module. |

Все ADRs added в `docs/features/lesson-runner/03-decisions.md` и referenced from updated phase plans.

## Phase-08 — Codex Cross-phase Fix Loop (3 rounds)

После first 7 phases был запущен Codex CLI cross-phase adversarial review (3 lens: Realist/Skeptic/Architect).

| Round | Verdict | Findings |
|-------|---------|----------|
| 1 | REJECT | 4 BLOCKERS (MultipleChoice/Ordering submit-as-final, FillBlank unwired, doOnStop on rotation, Result current vs best stars [later H1]) + 5 HIGH (pause+background deadline, save abort warning, rating retry, process death restore) + 1 MEDIUM (ADR-QS-16 scope) |
| 2 | PARTIAL | 0 BLOCKER (4→0) + 3 HIGH (rotation drafts in `remember`, system Back bypasses abort, FLAG_SECURE Loading window) + 3 MEDIUM (stars=0 hidden [my wrong RT1 instruction], avatar/image URLs not loaded, HARD checkbox stale) |
| 3 | **PASS** | 0/6 issues — все findings закрыты focused verification |

Дополнительно phase-08 включал runtime fixes от user manual smoke:
- RT1: LessonItemCard визуал (BrandCard wrapper, alignment с HierarchyItemCard)
- RT2: anonymous Firebase Auth on app start (resolves AuthRequired при tap урока)

## Remaining Issues / Known Gaps

1. **Manual smoke на Pixel** — APK build готов (`apps/android-next/build/outputs/apk/debug/android-next-debug.apk` 36MB). Pixel периодически отключается от ADB; после reconnect: `adb install -r -d ...debug.apk`. User verification flow: Catalog → Quest → Section → Theme → Lesson → tap → LessonRunnerScreen → answer all 4 question types → Result → rating → finish.
2. **Manual smoke полный flow** — interactive UI navigation deferred user verification. Все automated gates green.
3. **Pre-existing debt не trogался** в этой фиче:
   - `android/core/designsystem/QuestCard.kt:29` — импортирует shared.feature.quest.domain (core-imports-feature violation). Existed before phase-01.
   - `shared/core/sync/CascadingSyncOrchestrator.kt:5-12` — импортирует feature repositories. Existed before.
4. **Out of Scope per spec** (deferred features, документированы в 0-spec):
   - Cascade sync для lesson_attempts / lesson_ratings
   - Cloud Functions для агрегации Lesson.averageRating / top3
   - Avatar sync (subset of users/{uid})
   - Logout cleanup local lesson_attempts
   - Sync state прохождения между sessions
   - Лидерборд экран (выше top-3)
   - Repetition mechanism

## Quality Scorecard

См. `docs/features/lesson-runner/quality-scorecard.md` для детальной разбивки по параметрам (Architecture B, Correctness B, Completeness A, Security A, Code Organization A — Overall B+).

## Pipeline Statistics

- 7 phases × ~7-9 task-units = ~55 task instances
- 5 reviewer agents × 7 phases = 35 review passes (concurrency reviewer phases 2-6 only = 30 effective)
- 3 ADR escalations (LR-18/19/20) — all resolved within 1-2 lead cycles
- 0 architectural mismatch escalations требовавших спец работы вне plan
- 0 phases reverted / re-planned — все progressed forward через autonomous loop
