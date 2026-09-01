package com.tpov.schoolquiz.shared.core.question_schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A published question with its answer taken off — the half that is world-readable.
 *
 * This is the Kotlin side of the wire contract emitted by `functions/question-redaction.js`
 * (`redact`, status `"redacted"`). Every field here exists because that emitter writes it, and the
 * two are held together by `shared/core/question-schema/src/jvmTest/resources/
 * redacted-question-fixtures.json`, which both `RedactedQuestionWireTest` and
 * `functions/question-redaction-wire.test.js` read. Neither language can change this shape alone.
 *
 * Four things about the declaration are load-bearing:
 *
 * 1. **It is a sibling of [QuestionContent], not a subtype.** [QuestionContent] means "a question
 *    with its answer", and its invariants — a `correctOptionId` that names a real option, five or
 *    ten candidates, a `correctCandidateId` for every blank — say so. A redacted question satisfies
 *    none of them, and relaxing them to fit would weaken the validation every authoring path
 *    relies on. More concretely: `DefaultQuestCreateComponent` and `DefaultReviewQueueComponent`
 *    parse a payload and then re-encode what they parsed. If a redacted value could arrive there as
 *    a [QuestionContent], saving would rewrite the stored question into its own public half and the
 *    answer would be gone for good. Keeping the hierarchies apart makes that structurally
 *    impossible rather than merely unlikely.
 *
 * 2. **There are no `init` invariants, and no field is narrower than the wire.** This type
 *    describes what arrived, not what is valid. A payload that violates something is a fact about
 *    the server worth decoding and looking at, not an exception thrown inside a parser. That is why
 *    [difficulty] is a `String?` and not a [Difficulty]: see its own note.
 *
 * 3. **Ids are plain [String], not [OptionId] / [CandidateId] / [BlankId].** The ids in `items` and
 *    `candidates` are re-issued by the emitter as `ri-0…` and `rc-0…`; they are deliberately
 *    meaningless and bear no relation to the ids the authored question used. Typing them as the
 *    same value classes would let a redacted `rc-2` be compared against a real [CandidateId] and
 *    type-check, which is exactly the confusion redaction exists to prevent.
 *
 * 4. **Constructor parameter order is part of the contract.** The emitter builds each public half
 *    as `{...base, ...fields}` (`question-redaction.js`, `redact`), so the keys arrive in exactly
 *    the order `type`, `id`, `difficulty`, `text`, `imageUrl`, then the type's own fields. kotlinx
 *    encodes in declaration order, and `RedactedQuestionWireTest` asserts the re-encoded bytes
 *    equal the emitted ones. **A cosmetic reorder of these constructors breaks five fixtures with a
 *    diff that reads like an emitter change.** Reorder only alongside the emitter.
 *
 * There is no redacted Survey. A survey has no right answer, so there is nothing to take off it —
 * the emitter returns it unchanged with status `"not-applicable"`.
 *
 * `info` is not a field. The emitter builds the public half from an allow-list and never copies it.
 *
 * Nothing decodes this in production yet.
 */
@Serializable
sealed interface RedactedQuestionContent {

    /**
     * Null when the payload carried no id of its own — which is every question in the seed corpus,
     * where `buildLesson` puts the id on the wrapper document and never inside `payload`
     * (`scripts/seed-bulk/data/courses/<course>/_helpers.js`). The emitter copies `id` across only
     * when the source has a non-empty one (`question-redaction.js`, `publicBase`), so a required
     * field here would refuse the public half of every seeded question. The document id is stamped
     * into the answer key instead, as `questionId`.
     */
    val id: String?

    /**
     * The difficulty **as a string, exactly as it arrived** — not a [Difficulty].
     *
     * `resolveDifficulty` (`question-redaction.js`) copies a payload's own `difficulty` verbatim,
     * and that is deliberate: `parseQuestionPayload` merges `{...fallback, ...parsed}`, so a
     * payload's `""` already wins over the document field downstream, and `lessonAllocatedSeconds`
     * reads `"" || "EASY"` as EASY. Substituting anything there would move a question between the
     * easy and hard pools and change its reward, its unlock price and its client timer — the one
     * thing redaction must not do. So the emitter publishes `""` as `""` and `"MEDIUM"` as
     * `"MEDIUM"`, and omits the key entirely when the payload had none and no fallback was passed.
     *
     * All three shapes are things `redact` is written to produce, and a [Difficulty] here would
     * refuse every one of them. Deciding what a strange difficulty *means* belongs to whatever
     * consumes this type, not to the type that merely records what the server said.
     */
    val difficulty: String?

    val text: String

    /** Always written by the emitter, as a string or as `null`. Hence no default: it is required. */
    val imageUrl: String?

    /**
     * [difficulty] as the enum, or null when it is absent or names something the enum has no case
     * for. A convenience for consumers, not a field: it has no backing state and is not serialized.
     */
    val difficultyOrNull: Difficulty?
        get() = when (difficulty) {
            Difficulty.EASY.name -> Difficulty.EASY
            Difficulty.HARD.name -> Difficulty.HARD
            else -> null
        }

    /**
     * A visible label with an opaque id — the one row shape the emitter publishes, whether the row
     * is an option, an ordering item or a fill-blank candidate (`question-redaction.js`,
     * `readRows`).
     */
    @Serializable
    data class Row(val id: String, val text: String)

    /**
     * The options, all of them, in the order they were authored. Only the pointer to the right one
     * is missing.
     */
    @Serializable
    @SerialName("SingleChoiceRedacted")
    data class SingleChoice(
        override val id: String? = null,
        override val difficulty: String? = null,
        override val text: String,
        override val imageUrl: String?,
        val options: List<Row>,
    ) : RedactedQuestionContent

    /**
     * Structurally identical to [SingleChoice], which is the point: nothing here says how many
     * options are correct. A count — as a length, a `correctCount`, or a padded list — would cut
     * the search space from thirty-one guesses to ten.
     */
    @Serializable
    @SerialName("MultipleChoiceRedacted")
    data class MultipleChoice(
        override val id: String? = null,
        override val difficulty: String? = null,
        override val text: String,
        override val imageUrl: String?,
        val options: List<Row>,
    ) : RedactedQuestionContent

    /**
     * The answer to an ordering question *is* its array order, so [items] is shuffled and the ids
     * are re-issued `ri-0…` along the shuffled order — sorting them lexicographically hands back
     * the order they are already in, not the answer. The mapping home lives in the answer key.
     */
    @Serializable
    @SerialName("OrderingRedacted")
    data class Ordering(
        override val id: String? = null,
        override val difficulty: String? = null,
        override val text: String,
        override val imageUrl: String?,
        val items: List<Row>,
    ) : RedactedQuestionContent

    /**
     * [blanks] is a list of bare ids in the order the markers appear in [text] — no
     * `correctCandidateId` survives anywhere in this shape. [candidates] keep their texts and lose
     * their identity to `rc-0…`, because the authoring component writes the correct ones first.
     *
     * [protectedTextSegments] is filtered before publication: every entry is by construction an
     * answer spelled out in full, and republishing one beside a shuffled candidate list would undo
     * the shuffle in a single reading. The default is what an emitted payload with nothing left to
     * publish looks like — the emitter omits the key rather than writing an empty array.
     */
    @Serializable
    @SerialName("FillBlankRedacted")
    data class FillBlank(
        override val id: String? = null,
        override val difficulty: String? = null,
        override val text: String,
        override val imageUrl: String?,
        val blanks: List<String>,
        val candidates: List<Row>,
        val protectedTextSegments: List<String> = emptyList(),
    ) : RedactedQuestionContent
}
