package com.tpov.schoolquiz.shared.feature.theme.domain.model

/**
 * Type-safe identifier for a [Theme].
 *
 * Invariant: [value] is non-blank.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   Feature Domain Contract — ThemeId invariants (scenario 5).
 */
@JvmInline
value class ThemeId(val value: String) {
    init {
        require(value.isNotBlank()) { "ThemeId must not be blank" }
    }
}
