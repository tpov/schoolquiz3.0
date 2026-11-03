---
title: 'E2.6 — One way to read a question, answer key or not'
type: 'feature'
created: '2026-09-01'
status: 'in-progress'
baseline_commit: '0582392a'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `RedactedQuestionContent` exists and is inert — nothing decodes it but its own test, because every reader in the app is typed to `QuestionContent`. There is also no way to ask "parse this, whichever kind it is": the two existing overloads must keep refusing a redacted payload, since the authoring screens re-encode whatever they parse and would rewrite the stored question.

**Approach:** A common supertype carrying only what a question needs to be *shown* — never anything about its answer — and one parser entry point that returns it. Nothing consumes either yet; this is the seam the runner will move onto, landed on its own so the move is a type change rather than a design.

## Boundaries & Constraints

**Always:** The supertype exposes nothing that could reveal an answer. The character count that drives the lesson timer must come out identical for every existing question — it is the same number the server pays rewards and prices unlocks from. The existing `parse` overloads keep their exact signature and behaviour, so authoring and review stay structurally unable to receive a redacted payload.

**Ask First:** Any change to `QuestionContent`'s wire format, its invariants, or what the timer computes.

**Never:** Do not change the runner, the authoring or review screens, the scoring path, or the server. Do not add a member to the supertype that only one of the two implementors can answer.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|----------|--------------|---------------------------|
| Ordinary payload | any of the five shapes | the new entry point yields the full type, exactly as `parse` does |
| Redacted payload | any of the four redacted shapes | the new entry point yields the redacted type |
| Redacted payload, old entry points | either `parse` overload | still `Result.failure`, message naming the unregistered discriminator |
| Legacy payload | `{"type":"single-choice",…,"correctIndex":0}` | unchanged through every entry point |
| Timer input | every existing question | the character count is identical to today's, per type |
| Difficulty | a redacted payload whose difficulty is absent, empty, or not a known name | reads as unknown rather than failing — the emitter can produce all three |
| Wire format | the two types | unchanged: the supertype adds nothing to what is encoded |

</frozen-after-approval>

## Code Map

- `QuestionContent.kt:13-19` -- the sealed interface declares `id`, `difficulty`, `text`, `imageUrl`, `info`. These become overrides. The five data classes' existing `override val` keywords stay valid — they override transitively.
- **The difficulty mismatch is the design constraint.** `QuestionContent.difficulty` is the `Difficulty` enum; `RedactedQuestionContent` carries it as a nullable `String` with a computed `difficultyOrNull`, because the emitter copies it verbatim and may omit it (E2.4, commit `71beb048`). A supertype requiring the enum is therefore unimplementable. It must expose the nullable form; a question whose difficulty cannot be read then belongs to neither pool, which is the correct outcome.
- `RunnerLogic.kt:159-170` `computeCharsCount` -- `content.text.length` + the summed `text` of `options` / `items` / `candidates` per type + `100` when `imageUrl != null`. The supertype's texts member must be **exactly** those option/item/candidate texts — not the question text, not `info`, not `protectedTextSegments` — so this collapses to one expression with every number unchanged. Consumed by `computeTimer` (`:120`, `:126`), and its server twin is `questionCharsCount` (`lesson-reward.js:135`).
- Declare the texts member as a **body `val` with a getter**, never a constructor parameter: a constructor parameter joins the wire format and the `init` invariants.
- `QuestionContentParser.kt:10,19-24` -- two overloads, the enriched one with a default body. The new entry point needs a default body too: both `FakeQuestionContentParser`s (`shared/feature/lesson-runner/domain/src/commonTest/.../fake/`, `android/feature/lesson-runner/presentation/src/test/.../fake/`) override only `parse`, and an abstract method breaks them.
- `KotlinxSerializationQuestionContentParser.kt` -- `Json { ignoreUnknownKeys = true }`, discriminator `"type"`, which does not cover the discriminator itself. The two hierarchies have disjoint discriminator sets, so dispatch can be by attempt.
- Exhaustive `when` over `QuestionContent` with no `else`, which the compiler will guard if anything widens later — `RunnerLogic.kt:162`, `:175`; `QuestionContentMapping.kt:7`; `RunnerStateMapper.kt:49`; `DefaultQuestCreateComponent.kt:1264`; `DefaultReviewQueueComponent.kt:459`, `:506`. Two that would *not* be guarded, and so must not be reached by a widened type: `Scoring.kt:59` (`else -> Score(1)`) and `RunnerLogic.kt:257` (`else -> randomAnswer`). This slice widens nothing; they are listed so the next one does not discover them.
- `QuestionContentParserTest.kt` and `RedactedQuestionWireTest.kt` (`src/jvmTest`, reads a shared fixture off the classpath) -- the module's tests and their style.

## Tasks & Acceptance

**Execution:**
- [ ] `shared/core/question-schema/src/commonMain/.../QuestionDisplay.kt` -- new; the supertype, carrying identity, text, image, the display texts, and difficulty in the form both implementors can answer. Nothing about answers.
- [ ] `QuestionContent.kt` -- implement it; add the texts member as a body `val` per variant. No change to the wire format or the invariants.
- [ ] `RedactedQuestionContent.kt` -- implement it the same way.
- [ ] `QuestionContentParser.kt` -- add the entry point that returns the supertype, with a default body so the fakes keep compiling. The existing overloads are untouched.
- [ ] `KotlinxSerializationQuestionContentParser.kt` -- implement it.
- [ ] `commonTest` / `jvmTest` -- the Matrix rows, and a test per type that the character count is what it was, computed from the supertype.

**Acceptance Criteria:**
- Given every question shape the app has, when the timer's character count is computed through the supertype, then it equals what the current per-type code returns.
- Given a redacted payload, when the existing `parse` overloads are called, then they still refuse it.
- Given either type, when it is encoded, then the bytes are what they were before the supertype existed.
- Given a redacted payload whose difficulty is absent, empty, or unknown, when it is read through the supertype, then it reports unknown rather than throwing.

## Verification

**Commands:**
- `./gradlew :shared:core:question-schema:allTests --no-configuration-cache` -- green.
- `./gradlew ciCheck --no-configuration-cache` -- green apart from failures owned by the parallel session; every module that depends on question-schema still compiles.
