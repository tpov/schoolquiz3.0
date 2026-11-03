# Skeptic Review — lesson-runner ADRs

## Verdict
REJECT

The ADR set does not meet its own bar. Most ADRs do not explicitly answer “what breaks if this is wrong in 6 months?”, LR-08 assumes a payload format not proven against existing data, LR-14 claims ADR-0003 amendments are formalized while `docs/architecture/0003-question-schema.md` is still unamended, and OQ9 is only partially outsourced to quizzes-screen docs.

## ADR-LR-NN per-ADR scorecard

| ADR | Problem clear? | ≥2 genuine alternatives? | Trade-offs honest? | Consequences both directions? | Maps to | Risk if wrong |
|---|---:|---:|---:|---:|---|---|
| LR-01 | Y | Y | Partial | N | Cross-feature ADR / Grounding P6 | `lesson-runner ↔ lesson` coupling or loss of typed `LessonId`; version snapshot path breaks. |
| LR-02 | Y | Weak | Partial | N | Cross-feature ADR / Grounding P6 | Question fetch/filter path moves into wrong bounded context; codeAnswer ordering can drift. |
| LR-03 | Y | Weak | Partial | N | Cross-feature ADR / Grounding P6 | Auth guard leaks to presentation; attempts may save under stale/wrong user. |
| LR-04 | Y | Weak | Partial | N | Parser/schema blocker | Duplicate or mismatched schema types; timer/parser/runtime behavior diverges. |
| LR-05 | Y | Y | N | N | OQ2 / TopParticipant blocker | New `shared/core/leaderboard` may become premature core pollution; if name/model is wrong, multiple modules depend on a bad abstraction. |
| LR-06 | Y | Y | Partial | N | OQ3 / Difficulty serializable blocker | Process-death restore fails, or navigation config gets locked to enum serialization without migration plan. |
| LR-07 | Y | Y | Partial | N | OQ8 | Saved `LessonPlaceholder` stacks may fail restore; fallback/removal assumptions are not tested. |
| LR-08 | Y | Weak | N | N | OQ1 / parser blocker | Real `Question.payload` may not parse; user sees empty/no-valid-question states for valid lessons. |
| LR-09 | Y | Y | Partial | N | OQ6 / Koin lambda blocker | Koin resolution fails or domain/data dependency direction becomes illegal. |
| LR-10 | Y | Y | N | N | OQ5 / migration blocker | Bad migration loses data or crashes upgrades; “zero risk” claim is unjustified. |
| LR-11 | Y | Y | Partial | N | OQ7 | Lesson card forks from design system and may regress layout/accessibility; generic slot option dismissed too lightly. |
| LR-12 | Y | Weak | N | N | Grounding P10 | Rename churn hits Walking Skeleton tests/call sites; spec/docs drift if not updated. |
| LR-13 | Y | Y | Partial | N | OQ10 | Cursor sync gaps or duplicate/missed attempts if server timestamp strategy does not match sync implementation. |
| LR-14 | Y | Weak | N | N | ADR-0003 amendments A-D | Architecture remains contradictory; future implementers follow old EASY/feedback/timeLimit rules. |
| LR-15 | Y | Weak | N | N | OQ4 | Spec nullability is changed without formal amendment; missing backfill vs true zero becomes indistinguishable. |

## High/Blocker findings

1. **LR-14 is not actually formalized.**  
   `03-decisions.md` says apply amendments to ADR-0003, but `docs/architecture/0003-question-schema.md` still says `timeLimitSec` is mandatory, EASY stops on error/timeout, EASY may reveal answers, and runtime rules live in `shared/feature/quiz/domain`. That is not “captured”; it is deferred.

2. **LR-08 does not prove parser compatibility.**  
   It chooses `@JsonClassDiscriminator("kind")` and serial names like `single_choice`, but existing examples use `"type":"SingleChoice"` with a different payload shape. Spec explicitly required checking current `Question.payload` / legacy format. If wrong, every real question parses as invalid.

3. **LR-09 is not implementable cleanly as written.**  
   Wrapper interfaces live in `lesson-runner/data`, but the adapter is described inside the domain Koin module. Domain cannot depend on data. Also the canonical data module shown later does not register `Clock`, despite domain factories requiring it.

4. **OQ9 is not closed in `lesson-runner/03-decisions.md`.**  
   LR-07 only says to add QS-15/QS-16. QS-16 exists, but QS-15 conflicts with LR API: `LessonRunnerRootComponent` is documented in `lesson-runner/presentation`, while QS-15 says the return type should live in domain/core to avoid presentation→presentation import.

5. **No ADR explicitly answers the 6-month failure question.**  
   The scorecard had to infer risks. That is a process failure across all LR ADRs.

## Medium findings

- LR-05 underplays the cost of a new core module for one type. `leaderboard` naming is speculative; `TopParticipant` may later need `userId`, rank, lessonVersion, or tie-break metadata.
- LR-10 says `ALTER TABLE` has “zero risk.” False. Migrations fail through SQL mistakes, default mismatches, converter errors, schema export drift, and partial upgrade paths.
- LR-12 says ~10 files, but research says the Walking Skeleton has ~89 tests. The ADR should explicitly call out test churn and required compile/test gate.
- LR-15 reclassifies `ratingCount: Int?` from spec as an “opечатка” without a spec amendment. That is reopening a product/domain contract by assertion.

## Unresolved open questions
- [ ] Existing `Question.payload` JSON format and migration/compat strategy.
- [ ] LR-side ADR for OQ9 producer/consumer boundary, consistent with QS-15/QS-16.
- [ ] Actual ADR-0003 amendment edit, not just LR-14 text.
- [ ] Koin adapter location that does not make domain depend on data.
- [ ] Negative consequences and 6-month failure mode for every ADR.

## Recommendation
Reject `03-decisions.md` as the implementation gate. Fix LR-08, LR-09, LR-14, and OQ9 first; then add explicit negative consequences and “if wrong in 6 months” sections to all ADRs. Only then should phase-01 treat these decisions as locked.