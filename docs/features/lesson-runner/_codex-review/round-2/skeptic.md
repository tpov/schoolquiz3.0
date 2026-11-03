# Skeptic Review (Round 2) — lesson-runner ADRs

## Verdict
REJECT

## Round 1 blockers verification
1. ✓ FIXED — ADR-LR-14: `0003-question-schema.md` was actually edited. Amendments A-D are present: “Прохождение EASY НЕ прерывается...” [0003-question-schema.md:166](/home/Programming/Android/schoolquiz4.0/docs/architecture/0003-question-schema.md:166), “после ответа сразу переход...” [0003-question-schema.md:170](/home/Programming/Android/schoolquiz4.0/docs/architecture/0003-question-schema.md:170), `timeLimitSec` ignored by runtime formula [0003-question-schema.md:174](/home/Programming/Android/schoolquiz4.0/docs/architecture/0003-question-schema.md:174), and `shared/feature/lesson-runner/domain` replacement [0003-question-schema.md:178](/home/Programming/Android/schoolquiz4.0/docs/architecture/0003-question-schema.md:178).

2. ⚠ PARTIAL — ADR-LR-08 fixed discriminator/name choice, but not “verified against existing payloads.” It now says default `"type"` and `@SerialName("SingleChoice")` etc. [03-decisions.md:361](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/03-decisions.md:361). Existing examples use `"type":"SingleChoice"`, yes, but the payload shape is different: `options` string array + `correctIndex` [QuestionDomainTest.kt:226](/home/Programming/Android/schoolquiz4.0/shared/feature/question/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/question/domain/QuestionDomainTest.kt:226), also documented in prior spec [0-spec.md:538](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/0-spec.md:538). No migration/compat story is present.

3. ✓ FIXED — ADR-LR-09 decision/API contract moved provider interfaces to domain and adapter to data: “wrapper interfaces в .../domain/.../provider/” [03-decisions.md:384](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/03-decisions.md:384), adapter in `data/src/androidMain/.../di/` [03-decisions.md:390](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/03-decisions.md:390), canonical interfaces in 06 [06-api-contract.md:453](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/06-api-contract.md:453).

4. ⚠ PARTIAL — ADR-LR-07/OQ9 is resolved in decisions/API/QS ADR, but architecture still contradicts it. Fixed decision: interface in `android/core/navigation`, factory in `quizzes-screen/presentation`, impl in `lesson-runner/presentation` [03-decisions.md:327](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/03-decisions.md:327), [quizzes-screen/03-decisions.md:507](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:507). Contradiction remains: architecture graph still has `QSP --> LRP` and allowed import `quizzes-screen/presentation → lesson-runner/presentation` [01-architecture.md:108](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/01-architecture.md:108), [01-architecture.md:143](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/01-architecture.md:143).

5. ✓ FIXED — ADR-LR-10 removed “zero risk” and added mitigations: “Risk Mitigations (C14: удалён claim "нулевой риск")” [03-decisions.md:427](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/03-decisions.md:427).

6. ✓ FIXED — ADR-LR-12 makes churn explicit: impact `~10 файлов` [03-decisions.md:475](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/03-decisions.md:475), then names affected files/tests [03-decisions.md:479](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/03-decisions.md:479).

7. ✓ FIXED — ADR-LR-15 records user-approved `ratingCount: Int = 0`: spec amendment [0-spec.md:187](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:187), ADR status “user resolution” [03-decisions.md:550](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/03-decisions.md:550), API contract [06-api-contract.md:137](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/06-api-contract.md:137).

8. ✓ FIXED — SSoT moved to 06. 03 now points TopParticipant to `06-api-contract.md §LR-5a` [03-decisions.md:172](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/03-decisions.md:172) and provider definitions to `§LR-13a` [03-decisions.md:386](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/03-decisions.md:386). The actual signatures live in 06 [06-api-contract.md:112](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/06-api-contract.md:112), [06-api-contract.md:453](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/06-api-contract.md:453).

9. ✓ FIXED — all ADR-LR-01..15 have “Risk if wrong (6 months out)” sections; `rg` shows 15 ADR headings and 15 risk headings, e.g. LR-01 [03-decisions.md:48](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/03-decisions.md:48), LR-15 [03-decisions.md:565](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/03-decisions.md:565).

## NEW issues found in round 2
- `01-architecture.md` still documents the old domain Koin module pattern, while 06 says the adapter is `lessonRunnerDomainKoinAdapter` in data. See stale domain module [01-architecture.md:653](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/01-architecture.md:653) vs data adapter contract [06-api-contract.md:475](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/06-api-contract.md:475).
- `01-architecture.md` still has stale `LessonRunnerRootComponent` callbacks (`onSingleChoiceAnswer`, `onPauseDialogResume`, `onRatingSelected`) [01-architecture.md:391](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/01-architecture.md:391), while 06 canonical API uses `onAnswer`, `onContinue`, `onSubmitRating` [06-api-contract.md:289](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/06-api-contract.md:289).

## ADR-LR-NN scorecard (only ADRs that changed in round 2)
- LR-01 Y; LR-02 Y; LR-03 Y; LR-04 Y; LR-05 Y; LR-06 Y; LR-07 Y; LR-08 Y; LR-09 Y; LR-10 Y; LR-11 Y; LR-12 Y; LR-13 Y; LR-14 Y; LR-15 Y.

## Recommendation
fix-loop