package com.tpov.schoolquiz.shared.feature.quest.domain.model

/**
 * Type-safe identifier for a [Quest].
 *
 * Wraps the Firestore document id (UUID string) in a value class so the
 * compiler distinguishes [QuestId] from [CatalogId] and other String-backed
 * identifiers.
 *
 * Invariant: [value] is non-blank. An empty or whitespace-only id is illegal
 * both on the client and in Firestore.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   Feature Domain Contract — QuestId invariants (scenario 3-4).
 */
@JvmInline
value class QuestId(val value: String) {
    init {
        require(value.isNotBlank()) { "QuestId must not be blank" }
    }
}
