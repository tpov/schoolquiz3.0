# Feature: Lesson Runner

Экран прохождения урока — центральный gameplay-loop викторины. Заменяет `LessonPlaceholderComponent` фичи `quizzes-screen` на полноценный flow: один вопрос на экран, таймер, auto-random на timeout, результат с процентами/звёздами/топ-3, опрос «Оцените урок», запись попытки в Room.

## Status: implemented (Codex Round 3 PASS, 0 issues)

| Phase | Document | Status |
|-------|----------|--------|
| Spec | [0-spec.md](./0-spec.md) | Complete (1234 lines, 6 rounds Codex review + ratingCount Amendment 2026-04-27) |
| Walking Skeleton | `shared/feature/lesson-runner/domain/` | Complete (~89 tests green) |
| Research | [1-research.md](./1-research.md) | Complete |
| Grounding | [2-grounding.md](./2-grounding.md) | Complete (10 problem cards, 5 blockers, 10 open questions) |
| Design — Architecture | [01-architecture.md](./01-architecture.md) | Complete (C4 L1-L3, module graph, Koin registration) |
| Design — Behavior | [02-behavior.md](./02-behavior.md) | Complete (5 DFD + 4 sequences + extended State Matrix 1-8) |
| Design — Decisions | [03-decisions.md](./03-decisions.md) | Complete (15 ADRs LR-01..LR-15, all с Risk-if-wrong) |
| Design — Testing | [04-testing.md](./04-testing.md) | Complete (DT/PT/CT/IT/MT, AC 1-65 coverage map) |
| Design — Prior Art | [05-prior-art.md](./05-prior-art.md) | Complete (10 SDK + best practices) |
| Design — API Contract | [06-api-contract.md](./06-api-contract.md) | Complete (canonical signatures SSoT) |
| Design — Events | [07-events.md](./07-events.md) | Complete (Channel→Flow per existing pattern) |
| Design — Storage Model | [08-storage-model.md](./08-storage-model.md) | Complete (Migration v3→v4 + TypeConverters) |
| Design — Adversarial Review | [_codex-review/](./_codex-review/) | **PASS** after 4 fix-loop rounds (5 Codex 3-lens reviews) |
| Plan | [plan/](./plan/) | **Complete** (7 phases, 6 Codex rounds — 5 BLOCKER + 14+ HIGH/MEDIUM resolved through fix-loop; round-6 final residuals all swept) |
| Implement | [implementation.md](./implementation.md) | 7 phases PASS + phase-08 Codex fix loop (3 rounds). Final: 0 BLOCKER / 0 HIGH / 0 MEDIUM. |
| Quality Scorecard | [quality-scorecard.md](./quality-scorecard.md) | Overall A (post-Round 3). |
| Codex Round 1 | [_codex-review/cross-phase/codex-output.md](./_codex-review/cross-phase/codex-output.md) | 4 BLOCKERS + 5 HIGH + 1 MEDIUM (2026-04-28) |
| Codex Round 2 | [_codex-review/cross-phase/codex-output-round2.md](./_codex-review/cross-phase/codex-output-round2.md) | 0 BLOCKER + 3 HIGH + 3 MEDIUM (after phase-08 round 1 fixes) |
| Codex Round 3 | [_codex-review/cross-phase/codex-output-round3-focused.md](./_codex-review/cross-phase/codex-output-round3-focused.md) | **PASS — 6/6 closed, 0 issues** (after phase-08 round 3 fixes) |
| Retrospective | [retrospective.md](./retrospective.md) | **Complete** (2026-05-01) — 17 findings analyzed across 6 Codex rounds (4 BLOCKER + 8 HIGH + 5 MEDIUM); 7 pipeline fixes applied: Codex prompts include code references (Walking Skeleton + Gradle), `check-c4-vs-gradle.sh` + `check-api-contract-types.sh` hooks для manual SSoT drift, Existing cross-cutting ADR re-validation в `feature-spec.md`, mandatory E2E instrumented test stage перед Codex, Grounding BLOCKER as phase gate, Lifecycle patterns checkpoint в research. |

## Key Decisions Summary

- **Pool size** = 20 константа (random subset из eligibleQuestions(mode))
- **Score scale 0-9** (legacy `QuestionDetailLocal.codeAnswer`): `'0'`=не показан, `'1'`=0%, `'5'`=50%, `'9'`=100%
- **Stars** — `Stars(rawTenths: Int 0..30)` value class, integer math, derived (не stored в Attempt)
- **HARD unlock** — string-based: `∃ EASY-attempt с codeAnswer.allShownAnswersAre9 == true`
- **Сохранение только в Room** через repository; cascade sync → Firebase отдельно
- **FLAG_SECURE только в HARD mode** (toggle on start, untoggle on exit)
- **onStop auto-random** + onResume диалог «Продолжить?»
- **Process kill = lost** (no StateKeeper для runner state)
- **Rating prompt** — после первого `allShownAnswersAre9 + ¬оценивал`, lifetime per `(userId, lessonId)`
- **Top-3** — поле в Lesson document, агрегируется на сервере (отдельная задача)
- **ADR-0003 4 amendments** — EASY error continues, no feedback, timer formula, module path

## Out of Scope (другие задачи)

- Cascade sync infrastructure для `lesson_attempts` / `lesson_ratings`
- Cloud Functions для агрегации `Lesson.averageRating` / `Lesson.top3`
- Аватарки sync (subset of `users/{uid}` documents)
- Logout cleanup для local lesson_attempts
- Sync state прохождения между app sessions
- Лидерборд экран (выше топ-3)
- Repetition mechanism

## Source Description

> «будем делать экран прохождения урока, экран вопросов»

См. [0-spec.md Source](./0-spec.md#source) для развёрнутого описания.

## Pipeline Next Steps

1. ~~Phase 0-3: Spec~~ ✅
2. ~~Phase 3.5: Domain Contract Lock~~ ✅
3. ~~Phase 3.8a: domain-designer создаёт signatures~~ ✅
4. ~~Phase 3.8b: domain-designer bodies + test-dev (~89 test scenarios)~~ ✅
5. ~~Phase 4: Codex review (6 rounds)~~ ✅
6. ~~Phase 5: README~~ ✅
7. ~~Phase 6: Human approval~~ ✅
8. ~~Phase 7: `/feature-research lesson-runner`~~ ✅
9. ~~Phase 8: `/feature-design lesson-runner` — 5 Codex review rounds + 4 fix-loop rounds → PASS~~ ✅
10. **Next**: `/feature-plan lesson-runner` — phase-01 implementation roadmap (Walking Skeleton integration через Room + sync, presentation Decompose Components + Compose UI, factory wiring через android/core/navigation).

## Research Findings (summary)

- **5 блокеров** для phase-01: `KotlinxSerializationQuestionContentParser` impl отсутствует; `Lesson.top3 → bidirectional coupling`; `AppDatabase fallbackToDestructiveMigration` data loss risk; `lessonRunnerDomainModule` не зарегистрирован; `Difficulty` не `@Serializable`
- **10 open questions** для design phase (см. `1-research.md` секция Open Questions)
- **Walking Skeleton зелёный**: ~89 domain tests pass, Feature Domain Contract полностью реализован
- **0 architectural mismatches** между spec и domain implementation
- **Server-side gaps** out of scope, контракт зафиксирован в spec §32-34 + grounding Problem 9

## Design Resolution (15 ADRs)

| Block | ADR | Resolution |
|-------|-----|------------|
| Cross-feature imports | LR-01..04 | one-way, документированы (lesson, question, app-shell, question-schema) |
| TopParticipant location (BLOCKER #2) | LR-05 | перемещён в `shared/core/leaderboard/` (new core module) — устраняет bidirectional coupling |
| Difficulty serialization (BLOCKER #5) | LR-06 | `@Serializable` annotation в shared/core/question-schema |
| LessonPlaceholder replacement | LR-07 | атомарная замена через factory в `android/core/navigation/` (no presentation→presentation) |
| QuestionContentParser location (BLOCKER #1) | LR-08 | `KotlinxSerializationQuestionContentParser` в `shared/core/question-schema`; SerialNames per ADR-0003; Migration plan для 2 fixtures |
| Koin lambda strategy (BLOCKER #4) | LR-09 | provider interfaces в domain, `Default*Provider` impls в data, `lessonRunnerDomainKoinAdapter` в data/src/androidMain/ |
| AppDatabase migration (BLOCKER #3) | LR-10 | настоящая Migration(3, 4) + risk mitigations + production fallback removal |
| Lesson card UI | LR-11 | новый LessonItemCard в quizzes-screen (изоляция от designsystem) |
| value/raw naming | LR-12 | rename AttemptId.raw → .value (test churn explicit) |
| lastModifiedAt sync | LR-13 | server timestamp в sync writer |
| ADR-0003 Amendments | LR-14 | A-D applied to `docs/architecture/0003-question-schema.md` |
| Lesson.ratingCount | LR-15 | **user-approved**: `Int = 0` (non-nullable, default 0) — Amendment в spec §187 |
| quizzes-screen consumer side | QS-15/16 | factory call через core/navigation, LessonAttemptRepository import для bestStars/hardUnlocked |

## Codex Adversarial Review Trail

5 rounds of cross-model review (Realist + Skeptic + Architect lenses через `codex exec`):

| Round | Verdict | Issues |
|-------|---------|--------|
| 1 | REJECT | 4 BLOCKERS + 5 PARTIAL — fundamentally flawed (target state mixed с current, domain depends on data, SSoT violations) |
| 2 | REJECT | 10 PARTIAL — fix loop applied, но не fully synced |
| 3 | REJECT | 2 BLOCKER + 1 HIGH — DI inconsistency, IOError, TopParticipant serialization conflict |
| 4 | REJECT | 0 BLOCKER + 1 HIGH + 3 MEDIUM — fake return types, clock binding, parser text, section refs |
| 5 | **PASS** | 0 across all severities — implementation-ready |

Full review files: [_codex-review/](./_codex-review/) (round-1 на baseline, round-2..5 в подкаталогах).
