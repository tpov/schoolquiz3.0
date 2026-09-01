package com.tpov.schoolquiz.shared.feature.quest.domain.model

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId

/**
 * Domain entity representing a quest that belongs to a catalog.
 *
 * A quest is a user-authored content package containing sections, themes, lessons,
 * and questions. It lives in the Firestore collection `quests/{questId}` and
 * is cached locally in Room for offline-first access.
 *
 * Visibility is controlled by [visibleOn] — a set of shelf names that determine
 * where the quest appears in the UI. An empty set means the quest is removed
 * from all local views (cascade-delete-like behaviour during sync).
 *
 * [picturePath] follows the same relative-path convention as Catalog.picturePath:
 * a Firebase Storage relative path (e.g. `"quest-pictures/q-uuid.jpg"`). Never a
 * full URL — the URL is resolved by the infrastructure layer.
 *
 * [averageRating] is server-managed (0.0..3.0 with 0.1 precision). null for
 * quests that have not yet been rated. Clients never write this field.
 *
 * [authorUid] is the Firebase Auth UID of the quest author. Used for "my quests"
 * ownership filter. Verified server-side via Firestore security rules.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   FR#7, Feature Domain Contract — Quest invariants (scenarios 10-17).
 *   [USER DECIDED 2026-04-21] authorUid replaces legacy Int tpovId.
 */
data class Quest(
    val id: QuestId,
    val catalogId: CatalogId,
    /**
     * Firebase Auth UID of the quest author. Used for "my quests" ownership filter.
     * Verified server-side via Firestore security rules.
     * Invariant: non-blank.
     */
    val authorUid: String,
    val title: String,
    /**
     * Relative Firebase Storage path or null if no picture is set.
     * Same constraints as Catalog.picturePath: must not be blank, must not start
     * with https://, http://, or gs://.
     */
    val picturePath: String?,
    /** Pre-resolved HTTPS download URL cached in Room by data layer; null if not yet resolved. */
    val pictureUrl: String? = null,
    /**
     * Shelf identifiers where this quest is visible.
     * Valid values: "home", "arena", "tournament", "tournamentFinal", "archive".
     * Empty set → quest removed from local views during sync.
     *
     * Spec: FR#4 — visibleOn model.
     */
    val visibleOn: Set<String>,
    /**
     * Average rating computed by the server. Range: 0.0..3.0 (3 stars × 10 fractions).
     * null for unrated (DRAFT) quests.
     *
     * Spec: FR#10.
     */
    val averageRating: Float? = null,
    /**
     * Number of users who have rated this quest. Written by the server.
     * Invariant: >= 0.
     *
     * Spec: FR#11.
     */
    val averageRatingCount: Int = 0,
    /**
     * Monotonically-increasing version of this quest item's own fields.
     * Invariant: >= 1.
     */
    val version: Long,
    /**
     * Server-set timestamp (Unix millis) of the last write.
     * Used as delta-sync cursor: `where('lastModifiedAt', '>', localCursor)`.
     * Set by Firestore FieldValue.serverTimestamp() at write time.
     * Invariant: >= 0.
     */
    val lastModifiedAt: Long,
    /**
     * On-demand content marker for root quests/courses. When `true`, the quest
     * remains visible, but question payload is synced through lesson_content only.
     * Removal from local views is controlled by [visibleOn] becoming empty or by a
     * missing concrete document during sync-list refresh.
     */
    val archived: Boolean = false,
) {
    companion object {
        /** Valid shelf names per spec FR#4. */
        val VALID_SHELVES: Set<String> = setOf(
            "home", "arena", "tournament", "tournamentFinal", "archive",
        )
    }

    init {
        require(title.isNotBlank()) { "Quest.title must not be blank" }
        require(authorUid.isNotBlank()) { "Quest.authorUid must not be blank (Firebase Auth UID)" }
        if (picturePath != null) {
            require(picturePath.isNotBlank()) {
                "Quest.picturePath must be null or non-blank; got blank"
            }
            require(!picturePath.startsWith("https://")) {
                "Quest.picturePath must be a relative Storage path, not an https URL"
            }
            require(!picturePath.startsWith("http://")) {
                "Quest.picturePath must be a relative Storage path, not an http URL"
            }
            require(!picturePath.startsWith("gs://")) {
                "Quest.picturePath must be a relative Storage path, not a gs URI"
            }
        }
        val invalidShelves = visibleOn - VALID_SHELVES
        require(invalidShelves.isEmpty()) {
            "Quest.visibleOn contains unknown shelf values: $invalidShelves. " +
                "Allowed: $VALID_SHELVES"
        }
        if (averageRating != null) {
            require(averageRating in 0.0f..3.0f) {
                "Quest.averageRating must be in 0.0..3.0, got $averageRating"
            }
        }
        require(averageRatingCount >= 0) {
            "Quest.averageRatingCount must be >= 0, got $averageRatingCount"
        }
        require(version >= 1) { "Quest.version must be >= 1, got $version" }
        require(lastModifiedAt >= 0) { "Quest.lastModifiedAt must be >= 0, got $lastModifiedAt" }
    }
}
