package com.tpov.schoolquiz.shared.core.catalog.domain.model

/**
 * Domain entity representing a flat category that groups quests.
 *
 * Instances are fetched from Firestore collection `catalogs/{catalogId}` and
 * persisted locally (Room) for offline-first access.
 *
 * Single-language MVP — [name] is the original language string; localisation
 * is deferred to a future feature.
 *
 * [picturePath] is a **relative** Firebase Storage path
 * (e.g. `"catalog-pictures/surveys.jpg"`). The fully-qualified download URL
 * is resolved by the image loader in the infrastructure layer — domain never
 * stores a full URL. See Codex fix #5 in the spec.
 *
 * Spec: docs/features/catalog-foundation/0-spec.md
 *   Feature Domain Contract — Terms/Entities/Value Constraints and
 *   Business Rules section.
 */
data class Catalog(
    val id: CatalogId,
    val name: String,
    val picturePath: String?,
    /** Pre-resolved HTTPS download URL cached in Room by data layer; null if not yet resolved. */
    val pictureUrl: String? = null,
    /**
     * Monotonically-increasing server version for this catalog item itself.
     * Invariant: >= 1. Incremented whenever catalog fields (name, picturePath, archived) change.
     * Delta sync uses `version > local.version` to skip unchanged items.
     *
     * Spec: docs/features/home-and-my-quests/0-spec.md — FR#6, Business Rule #1.
     */
    val version: Long = 1,
    /**
     * Server-set timestamp (Unix millis) of the last write.
     * Used as delta-sync cursor: `where('lastModifiedAt', '>', localCursor)`.
     * Set by Firestore FieldValue.serverTimestamp() at write time.
     * Invariant: >= 0.
     */
    val lastModifiedAt: Long = 0,
    /**
     * Soft-delete flag. When `true` the client deletes the local catalog entry.
     * The Firestore document is not physically removed.
     *
     * Spec: docs/features/home-and-my-quests/0-spec.md — FR#5, Business Rule #3.
     */
    val archived: Boolean = false,
    /**
     * Optional typed category key (e.g. "school", "music", "sport"). Set in admin/Firestore;
     * deterministically maps the catalog to a themed floating-icons set in presentation.
     * Null for legacy entries — UI falls back to fuzzy keyword matching by name.
     */
    val iconCategoryKey: String? = null,
    /**
     * Optional explicit list of Material Icon names (e.g. ["VideogameAsset", "Casino"])
     * that drives the floating-icons background. When non-empty, takes priority over
     * [iconCategoryKey] / name-based fuzzy matching. Names that are not present in the
     * client's whitelist registry are silently skipped at resolve time.
     */
    val iconNames: List<String> = emptyList(),
    /** What kind of quests this catalog holds; drives the editor, scoring and the runner. */
    val questType: QuestType = QuestType.REGULAR,
) {
    init {
        require(name.isNotBlank()) { "Catalog.name must not be blank" }
        if (picturePath != null) {
            require(picturePath.isNotBlank()) {
                "Catalog.picturePath must be null or non-blank; got blank"
            }
            require(!picturePath.startsWith("https://")) {
                "Catalog.picturePath must be a relative Storage path, not an https URL"
            }
            require(!picturePath.startsWith("http://")) {
                "Catalog.picturePath must be a relative Storage path, not an http URL"
            }
            require(!picturePath.startsWith("gs://")) {
                "Catalog.picturePath must be a relative Storage path, not a gs URI"
            }
        }
        require(version >= 1) { "Catalog.version must be >= 1, got $version" }
        require(lastModifiedAt >= 0) { "Catalog.lastModifiedAt must be >= 0, got $lastModifiedAt" }
    }
}
