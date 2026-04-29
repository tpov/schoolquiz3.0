package com.tpov.schoolquiz.shared.core.question_schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Parsed question content from ADR-0003 JSON payload.
 *
 * Invariants are enforced in each subtype's init block.
 * Domain question-runner imports only this sealed type and [QuestionContentParser].
 */
@Serializable
sealed interface QuestionContent {

    val id: String
    val difficulty: Difficulty
    val text: String
    val imageUrl: String?
    val info: String?

    @Serializable
    data class Option(val id: OptionId, val text: String)

    @Serializable
    data class OrderItem(val id: OptionId, val text: String)

    @Serializable
    data class Blank(val id: BlankId, val correctCandidateId: CandidateId)

    @Serializable
    data class Candidate(val id: CandidateId, val text: String)

    /**
     * Single-choice question. Exactly one correct answer.
     * Invariant: options.size in 2..8.
     */
    @Serializable
    @SerialName("SingleChoice")
    data class SingleChoice(
        override val id: String,
        override val difficulty: Difficulty,
        override val text: String,
        override val imageUrl: String?,
        val options: List<Option>,
        val correctOptionId: OptionId,
        override val info: String? = null,
    ) : QuestionContent {
        init {
            require(id.isNotBlank()) { "SingleChoice.id must not be blank" }
            require(text.isNotBlank()) { "SingleChoice.text must not be blank" }
            require(options.size in 2..8) { "SingleChoice.options.size must be in 2..8, got ${options.size}" }
            require(options.map { it.id }.toSet().size == options.size) { "SingleChoice.options must have unique ids" }
            require(correctOptionId in options.map { it.id }) {
                "SingleChoice.correctOptionId must be in options, got $correctOptionId"
            }
        }
    }

    /**
     * Multiple-choice question. Two or more correct answers.
     * Invariant: options.size in 2..8; correctOptionIds.size >= 2.
     */
    @Serializable
    @SerialName("MultipleChoice")
    data class MultipleChoice(
        override val id: String,
        override val difficulty: Difficulty,
        override val text: String,
        override val imageUrl: String?,
        val options: List<Option>,
        val correctOptionIds: Set<OptionId>,
        override val info: String? = null,
    ) : QuestionContent {
        init {
            require(id.isNotBlank()) { "MultipleChoice.id must not be blank" }
            require(text.isNotBlank()) { "MultipleChoice.text must not be blank" }
            require(options.size in 2..8) { "MultipleChoice.options.size must be in 2..8, got ${options.size}" }
            require(options.map { it.id }.toSet().size == options.size) { "MultipleChoice.options must have unique ids" }
            require(correctOptionIds.size >= 2) { "MultipleChoice.correctOptionIds.size must be >= 2, got ${correctOptionIds.size}" }
            val optionIds = options.map { it.id }.toSet()
            require(correctOptionIds.all { it in optionIds }) {
                "MultipleChoice.correctOptionIds must all be in options, got missing: ${correctOptionIds - optionIds}"
            }
        }
    }

    /**
     * Ordering question. Correct order = order of items in [items] list.
     * Invariant: items.size in 2..8.
     */
    @Serializable
    @SerialName("Ordering")
    data class Ordering(
        override val id: String,
        override val difficulty: Difficulty,
        override val text: String,
        override val imageUrl: String?,
        val items: List<OrderItem>,
        override val info: String? = null,
    ) : QuestionContent {
        init {
            require(id.isNotBlank()) { "Ordering.id must not be blank" }
            require(text.isNotBlank()) { "Ordering.text must not be blank" }
            require(items.size in 2..8) { "Ordering.items.size must be in 2..8, got ${items.size}" }
        }
    }

    /**
     * Fill-in-the-blank question. Text contains blank markers; candidates are word pool.
     * Invariant: blanks.size in 1..3; candidates.size == 5 or 10.
     */
    @Serializable
    @SerialName("FillBlank")
    data class FillBlank(
        override val id: String,
        override val difficulty: Difficulty,
        override val text: String,
        override val imageUrl: String?,
        val blanks: List<Blank>,
        val candidates: List<Candidate>,
        override val info: String? = null,
    ) : QuestionContent {
        init {
            require(id.isNotBlank()) { "FillBlank.id must not be blank" }
            require(text.isNotBlank()) { "FillBlank.text must not be blank" }
            require(blanks.size in 1..3) { "FillBlank.blanks.size must be in 1..3, got ${blanks.size}" }
            require(candidates.size == 5 || candidates.size == 10) {
                "FillBlank.candidates.size must be 5 or 10, got ${candidates.size}"
            }
            require(candidates.map { it.id }.toSet().size == candidates.size) { "FillBlank.candidates must have unique ids" }
            require(blanks.map { it.id }.toSet().size == blanks.size) { "FillBlank.blanks must have unique ids" }
            val candidateIds = candidates.map { it.id }.toSet()
            require(blanks.all { it.correctCandidateId in candidateIds }) {
                "FillBlank.blanks.correctCandidateId must all be in candidates"
            }
        }
    }
}
