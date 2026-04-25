package com.tpov.schoolquiz.shared.core.catalog.domain.model

/**
 * Type-safe identifier for a [Catalog].
 *
 * Wraps the Firestore document id (e.g. `"surveys"`, `"courses"`) in a value class
 * so the compiler distinguishes [CatalogId] from other `String`-backed ids
 * (e.g. `QuestId`, `UserId`).
 *
 * Invariant: [value] is non-blank. An empty or whitespace-only id is illegal
 * both on the client and in Firestore.
 *
 * Spec: docs/features/catalog-foundation/0-spec.md
 *   Feature Domain Contract — `CatalogId` invariants.
 */
@JvmInline
value class CatalogId(val value: String) {
    init {
        require(value.isNotBlank()) { "CatalogId must not be blank" }
    }
}
