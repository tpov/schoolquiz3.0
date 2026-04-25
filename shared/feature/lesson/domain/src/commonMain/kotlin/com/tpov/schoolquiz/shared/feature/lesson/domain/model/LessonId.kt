package com.tpov.schoolquiz.shared.feature.lesson.domain.model

/**
 * Type-safe identifier for a [Lesson].
 *
 * Invariant: [value] is non-blank.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   Feature Domain Contract — LessonId invariants (scenario 5).
 */
@JvmInline
value class LessonId(val value: String) {
    init {
        require(value.isNotBlank()) { "LessonId must not be blank" }
    }
}
