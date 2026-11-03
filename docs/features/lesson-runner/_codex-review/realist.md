# Realist Review — lesson-runner design

## Verdict
REJECT — the pack repeatedly presents target-state design as if it matches current code; module graph, navigation, API snippets, and many file:line refs are false.

## High/Blocker Findings
- [BLOCKER] `TopParticipant` relocation is fiction. `01-architecture.md:16`, `01-architecture.md:134`, `01-architecture.md:168`, and `06-api-contract.md:612` claim `shared/core/leaderboard`; actual Gradle has only `:shared:feature:lesson-runner:domain` for lesson-runner (`settings.gradle.kts:52`) and `TopParticipant` still lives in `shared/feature/lesson-runner/domain/.../TopParticipant.kt:1`, class at `:3`. Fix: either implement the core module/imports or stop claiming the ADR is resolved.

- [BLOCKER] C4 module graph does not match Gradle. `01-architecture.md:101-103` says `quizzes-screen/presentation` depends on lesson-runner presentation/domain; actual `android/feature/quizzes-screen/presentation/build.gradle.kts:11-17` has no lesson-runner dependency. Existing ADR also rejects direct presentation→presentation import (`docs/features/quizzes-screen/03-decisions.md:541`, rule at `.claude/rules/clean-architecture.md:55`). Fix: align the graph to the accepted factory boundary or change Gradle/ADR.

- [BLOCKER] Quizzes flow is not implemented as documented. `01-architecture.md:197-206` and `06-api-contract.md:17-42` say `LessonPlaceholder` is removed and `LessonRunner` exists. Actual `QuizzesConfig.kt:34-39`, `QuizzesChild.kt:14`, `DefaultLessonListComponent.kt:56-57`, and `QuizzesScreen.kt:41-45` still use `LessonPlaceholder`. Fix: mark this as future target state or implement it before calling it canonical.

- [BLOCKER] The “canonical” DI/API snippets do not compile against Walking Skeleton constructors. `06-api-contract.md:447-450` uses `lessonAttemptRepository`/`lessonRatingRepository`; actual constructor names are `attemptRepository`/`ratingRepository` in `CompleteAttemptUseCase.kt:22-26`. `06-api-contract.md:455-458` repeats the wrong name for abort; actual is `AbortAttemptUseCase.kt:20-23`. `06-api-contract.md:465` calls `RatingIdProvider.next()`, but actual `SubmitLessonRatingUseCase` requires `(userId: String, lessonId: LessonId) -> RatingId` at `SubmitLessonRatingUseCase.kt:19-24`. Fix: regenerate contract snippets from source.

- [HIGH] Lesson result/top3 model contradicts actual lesson domain. `01-architecture.md:37`, `06-api-contract.md:116-123`, and `02-behavior.md:167-178` claim `Lesson.averageRating`, `ratingCount`, and `top3`. Actual `Lesson.kt:15-45` has only existing fields through `archived`; `LessonEntity.kt:23-32` also has no rating/top3 columns. Fix: describe these as pending schema changes, not actual readable data.

- [HIGH] Behavior sequence calls wrong domain API. `02-behavior.md:365-369` passes `UserAnswerDraft` into `RunnerLogic.submitAnswer`; actual signature is `submitAnswer(state, answer: UserAnswer, nowMs)` at `RunnerLogic.kt:25`. `UserAnswerDraft` is a separate type at `UserAnswerDraft.kt:12`. Fix: add explicit draft→submitted-answer mapping in the design.

- [HIGH] Sequence 2 double-computes timer. `02-behavior.md:371-374` says component calls `computeTimer` after `submitAnswer`; actual `submitAnswer` already computes next deadline at `RunnerLogic.kt:33-37` and returns it at `RunnerLogic.kt:42-47`. Fix: remove component-side recomputation from the sequence.

## Medium Findings
- [MEDIUM] State Matrix code locations are stale. Examples: `02-behavior.md:346` says `StartLessonAttemptUseCase.kt:50` for `invoke`, actual invoke is `StartLessonAttemptUseCase.kt:36`; `02-behavior.md:347` says `selectSubset ~180`, actual is `RunnerLogic.kt:154`; `02-behavior.md:391` says `submitAnswer ~50`, actual is `RunnerLogic.kt:25`.

- [MEDIUM] Question-schema serialization claims are not actual. `01-architecture.md:57-58` and `01-architecture.md:368` claim `KotlinxSerializationQuestionContentParser` and `@Serializable`. Actual `QuestionContent.kt:9` and `Difficulty.kt:3` have no serialization annotations, and only `QuestionContentParser` interface exists at `QuestionContentParser.kt:9-10`.

- [MEDIUM] Public event-channel API ignores existing pattern. `06-api-contract.md:272-273` exposes `ReceiveChannel<RunnerEvent>`. Existing root pattern keeps `Channel` private at `DefaultRootComponent.kt:113` and exposes `Flow` at `DefaultRootComponent.kt:114`; the UI collects that flow at `AppShellScreen.kt:132`. Fix: use `Flow` publicly if reusing current pattern.

- [MEDIUM] `SaveError.IOError` is hallucinated. `02-behavior.md:384` names `SaveError.IOError`; actual variants are `SaveError.IoFailure` and `SaveError.UnknownError` at `SaveError.kt:3-5`.

- [MEDIUM] Several file paths are target-only or wrong. `06-api-contract.md:384` points to `model/LessonItemUi.kt`; current model is `uistate/HierarchyItemUi.kt:3-8`. `06-api-contract.md:430` points to `src/androidMain/.../LessonRunnerDomainKoinAdapter.kt`; actual module is `src/commonMain/.../LessonRunnerDomainModule.kt:17`.

## Spot-Check Matrix
| Design doc:section | Claim | Actual file:line | Verdict |
|--------------------|-------|------------------|---------|
| `01:126-137` | LR domain imports lesson/question/app-shell/question-schema | `shared/feature/lesson-runner/domain/build.gradle.kts:15-18` | PASS |
| `01:134`, `01:168` | LR domain imports `shared/core/leaderboard` | `settings.gradle.kts:52`; `TopParticipant.kt:1` | FAIL |
| `01:101-103` | QSP depends on LRP/LRD | `android/feature/quizzes-screen/presentation/build.gradle.kts:11-17` | FAIL |
| `01:197-206` | `LessonRunner` replaces `LessonPlaceholder` | `QuizzesConfig.kt:34-39` | FAIL |
| `02:45` | Lesson click replaces placeholder push | `DefaultLessonListComponent.kt:56-57` | FAIL |
| `02:47` | `createChild` has LessonRunner branch | `DefaultQuizzesComponent.kt:117-138` | FAIL |
| `02:153` | `RunnerState.Ready.isPaused` exists at line 47 | `RunnerState.kt:47` | PASS |
| `02:252` | `computeBestStars`/`computeHardUnlocked` are pure domain functions | `RunnerLogic.kt:120`, `RunnerLogic.kt:129` | PASS |
| `02:346` | `StartLessonAttemptUseCase.invoke()` at line 50 | `StartLessonAttemptUseCase.kt:36` | FAIL |
| `02:340` | `computeTimer(playOrder.first(), ...)` | `StartLessonAttemptUseCase.kt:83` passes `.content` | FAIL |
| `02:368` | `submitAnswer` accepts draft answer | `RunnerLogic.kt:25`; `UserAnswerDraft.kt:12` | FAIL |
| `02:372-374` | Component recomputes next deadline | `RunnerLogic.kt:34-37` | FAIL |
| `02:384` | `SaveError.IOError` | `SaveError.kt:3-5` | FAIL |
| `02:501` | SingleChoice scoring at `RunnerLogic.kt:~100` | `RunnerLogic.kt:66-70` | FAIL |
| `02:568` | EmptyPool location `StartLessonAttemptUseCase.kt:~85` | `StartLessonAttemptUseCase.kt:63-65` | FAIL |
| `06:66` | `Attempt.id.value` after rename | `AttemptId.kt:4` is `raw` | FAIL |
| `06:116-123` | `Lesson` has rating/top3 fields | `Lesson.kt:15-45` | FAIL |
| `06:222-255` | DefaultLessonList has attempt/auth deps | `DefaultLessonListComponent.kt:23-29` | FAIL |
| `06:447-450` | Complete use case named args | `CompleteAttemptUseCase.kt:22-26` | FAIL |
| `06:612` | `TopParticipant` in core and serializable | `TopParticipant.kt:1-3` has no `@Serializable` | FAIL |

Pattern availability check: `doOnStop`, `doOnResume`, and `FLAG_SECURE` have 0 production hits; `doOnDestroy` is the current lifecycle pattern, e.g. `DefaultLessonListComponent.kt:52`; `instanceKeeper` exists in production at `DefaultMyQuestsComponent.kt:59`; private `Channel` + public `Flow` exists at `DefaultRootComponent.kt:113-114`.