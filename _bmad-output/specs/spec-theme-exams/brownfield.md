# Brownfield notes

What already exists for this feature, what is reusable as-is, and where the gaps are. Written so an
implementer does not rebuild a formula the repo already has, and does not assume a field the repo
never had.

## Already built — reuse unchanged

| Thing | Where | Note |
|---|---|---|
| `SessionMode.EXAM` | `shared/feature/lesson-runner/domain/.../model/SessionMode.kt` | `revealsCorrectAnswer()` is already false for both difficulties in EXAM. |
| Exam timer factor | `RunnerLogic.kt` `computeTimer`, `TimerCoefficients.examFactor = 0.75` | Exams already get a tighter allowance than practice. |
| Per-question scoring | `RunnerLogic.kt` `evaluateAnswer` → `Score` 1..9 | Covers all five `QuestionContent` variants. |
| Percent + stars | `RunnerLogic.kt` `computePercentScore`, `computeStars` | EASY → 0..20 tenths, HARD → 20..30. Three stars = 30 tenths. |
| Difficulty ladder precedent | `RunnerLogic.kt` `computeHardUnlocked` | HARD unlocks after an EASY attempt whose shown digits are all 9. |
| Star fill rendering | `shared/feature/quest/domain/.../logic/StarRating.kt` `computeStarFills` | 3 stars × 10 fractions, already used for quest ratings. |
| Server-side score check | `functions/result-verification.js` `recomputePercentScore` | A JS mirror of the Kotlin formula, already trusted by `submitLessonResultEvents`. |
| Difficulty on questions | `shared/core/question-schema/.../QuestionContent.kt` | Every variant carries `difficulty`; the pool filter is a field read, not an inference. |
| Pool sampling | `StartLessonAttemptUseCase` `selectSubset(…, POOL_SIZE = 20, seed)` | The shape the exam draw should follow, moved server-side. |
| Navigation carrier | `QuizzesConfig.LessonRunner(sessionMode = …)` | Already serializes a session mode through Decompose config. |

## Reserved in architecture, never implemented

- **ADR-0007 `Certificate`** — model, server signature, public verification endpoint, profile
  screens. No `Certificate` type exists anywhere in the codebase; this is greenfield inside an
  accepted ADR.
- **ADR-0005 `CompletionEffect.IssueCourseCertificate`** — the event-driven issuance path. Also
  unimplemented. ADR-0005 already states exams are primarily for courses, and rule 4 forbids the
  client from processing the effect itself.
- **`server/workers/rewards`** — the module ADR-0005 and ADR-0007 both name as the issuer, present
  as a directory.

## Gaps this feature must fill

- **`Theme` has no rating fields.** `shared/feature/theme/domain/.../model/Theme.kt` carries id,
  section, title, order, versions, `lastModifiedAt`, `archived` — and nothing else. Best exam stars
  and exam average are new fields, with the sync-contract consequences (version bumps, cursor) that
  every other field in that model has.
- **`ThemeListScreen` renders no stars.** It builds `HierarchyItemCard(title, orderLabel,
  subtitleCount, onClick)`; lessons get their stars from `LessonItemCard`, which themes do not use.
  Adding theme stars means either extending `HierarchyItemCard` or giving themes their own card.
- **"Lesson passed" is not a stored fact, though the predicate exists.** Passing means every easy
  question answered correctly, which is exactly `computeHardUnlocked`'s all-9 test — but it is
  evaluated per device from local attempts (`LessonAttemptRepository`, `computeBestStars`). The
  exam gate and the sequential unlock both need the server to hold it. The server does receive every
  attempt through `submitLessonResultEvents`, so the data is there; the aggregate is not.

- **Why the gate is phrased on answers, not on stars.** `computeStars` gives HARD a floor of 20
  tenths — two stars — even at 0%, so a bare `bestStars >= 20` would be satisfied by a blank hard
  attempt. Stating the predicate as the all-9 EASY test instead avoids that, and also avoids the
  two-percent gap between "all easy correct" (100%) and the lowest score that still rounds to two
  stars (98%). One predicate, `computeHardUnlocked`, already written.

- **Sequential lesson unlock does not exist.** `DefaultLessonListComponent` gates only difficulty —
  `hardUnlocked` decides EASY versus HARD for a lesson the player may already open. Nothing locks a
  lesson behind its predecessor, and nothing spends nolics to open one. Both are new, both scoped to
  course catalogs rather than to lesson lists generally, and the purchase is an arbitrary-target
  unlock rather than a "buy the next one" button.

- **Nothing tracks section or course completion.** The certificate fires when the last section of a
  course closes, and no aggregate above the lesson exists — not on `Theme`, not on `Section`, not on
  `Quest`.
- **The parser assumes a complete payload.** `KotlinxSerializationQuestionContentParser` parses the
  full ADR-0003 body. Redacted exam questions need it to parse a body with the correctness fields
  absent, without falling back to a fabricated answer.

## Frictions to expect

- **Offline-first everywhere else.** `LessonResultOutboxWriter` and the Room outbox exist precisely
  so a result survives having no connection. The exam has no such path by design, so its failure
  modes are genuinely new UI, not a reuse of the outbox's.
- **Questions are already on the device.** Sync pulls question payloads, correct answers included,
  into Room. Server-side redaction protects the wire, not the device — see SPEC open question 2.
- **The charge economy, and its old name.** `functions/life-points.js` holds it:
  `LIFE_POINTS_PER_HEART = 100`, `LESSON_ATTEMPT_LIFE_COST = 33`, lazy regeneration of one heart an
  hour, ceiling `MAX_STANDARD_HEARTS = 5` (500 points). An exam costs one whole charge — 100 points,
  three lesson attempts — but deducted at `startThemeExam`, not on sync, because the exam is online.
  The product now calls this "заряд"/energy rather than "жизни"; the code, the shop and the legacy
  price list still say life and hearts throughout. Renaming is SPEC open question 3 and is not part
  of this feature's diff.
- **Function instance quota.** `functions/index.js` caps every function's `maxInstances` for a
  documented quota reason; five new callables land inside that budget and should follow the same
  `FUNCTION_OPTIONS`.
