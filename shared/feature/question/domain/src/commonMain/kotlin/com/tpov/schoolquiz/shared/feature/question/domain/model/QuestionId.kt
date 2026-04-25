package com.tpov.schoolquiz.shared.feature.question.domain.model

/**
 * Type-safe identifier for a [Question].
 *
 * Invariant: [value] is non-blank.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   Feature Domain Contract — QuestionId invariants (scenario 5).
 */
@JvmInline
value class QuestionId(val value: String) {
    init {
        require(value.isNotBlank()) { "QuestionId must not be blank" }
    }
}
