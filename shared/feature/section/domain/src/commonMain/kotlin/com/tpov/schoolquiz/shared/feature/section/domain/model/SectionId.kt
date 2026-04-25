package com.tpov.schoolquiz.shared.feature.section.domain.model

/**
 * Type-safe identifier for a [Section].
 *
 * Invariant: [value] is non-blank.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   Feature Domain Contract — SectionId invariants (scenario 5).
 */
@JvmInline
value class SectionId(val value: String) {
    init {
        require(value.isNotBlank()) { "SectionId must not be blank" }
    }
}
