package com.tpov.schoolquiz.shared.feature.quest.domain.repository

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import kotlinx.coroutines.flow.Flow

/**
 * Domain boundary for reading, observing, and syncing [Quest]s.
 *
 * Production implementation (Firestore + Room) lives in the data layer and is
 * introduced in phase-01. Use case tests drive this boundary through the
 * in-memory fake ([FakeQuestRepository]) in the test source set.
 *
 * Signatures use only domain types — no DTO, Entity, HTTP codes, or
 * framework-specific types. [suspend] and [Flow] are allowed here because this
 * is the boundary between the pure core and the imperative shell.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   Feature Domain Contract — QuestRepository contract (AC#5).
 *   Primary User Journeys 1, 2, 3, 7.
 */
interface QuestRepository {

    /**
     * Observes quests authored by [authorUid].
     *
     * When [catalogId] is non-null, only quests from that catalog are emitted.
     * When null, all quests from the author are emitted (i.e. "all categories" mode).
     *
     * Filtering contract:
     *   `quest.authorUid == authorUid && !quest.archived && (catalogId == null || quest.catalogId == catalogId)`
     *
     * Filters out `archived=true` quests locally (spec AC#47: archived is always
     * deleted, never displayed).
     *
     * Spec: FR#21, AC#47, Primary User Journey 7, Domain Test Scenario 26-27.
     */
    fun observeMyQuests(authorUid: String, catalogId: CatalogId? = null): Flow<List<Quest>>

    /**
     * Observes public quests belonging to [catalogId] that are visible on [shelf].
     *
     * Filter: `catalogId == catalogId AND visibleOn contains shelf AND archived == false`.
     * Sort: `lastModifiedAt DESC` (Quest has no order field — User Decision Q1).
     *
     * DAO pattern: delimiter-wrapped LIKE — `(CHAR(31) || visibleOn || CHAR(31)) LIKE
     * ('%' || CHAR(31) || :shelf || CHAR(31) || '%')` — prevents "home" matching "homeExtra".
     * `archived = 0` filter is applied in the DAO query, not in this layer.
     *
     * Emits empty list when no matching quests exist. Re-emits on Room writes.
     *
     * Spec: docs/features/quizzes-screen/plan/phase-01/backend.md, AC#1-4.
     */
    fun observeByCatalog(catalogId: CatalogId, shelf: String): Flow<List<Quest>>

    /**
     * Observes quests where [visibleOn] contains [shelf].
     *
     * NOT used by the "Home Quests" screen in this feature — Home shows catalogs
     * (CatalogGrid), not quests. This method is a generic shelf-based query
     * intended for future screens (Arena, Tournament, etc.).
     *
     * Spec: FR#4 (visibleOn model).
     */
    fun observeByShelf(shelf: String): Flow<List<Quest>>

    /**
     * Returns a single quest from the local cache, or null if absent.
     *
     * Does not trigger a remote fetch.
     *
     * Spec: AC#5.
     */
    suspend fun getById(id: QuestId): Quest?

    /**
     * Pulls quests from remote for the given filter and persists them locally.
     *
     * **No-op guard**: if [catalogIdsToSync] is empty, returns immediately without
     * any Firestore reads. Step 2 of the cascade is only triggered when at least one
     * catalog had its `contentsVersion` changed.
     *
     * **Two independent Firestore queries** (Firebase does not support
     * `array-contains-any` + `where-in` in a single query — see spec FR#14):
     *
     *   **Query A (own quests in changed catalogs)**:
     *     `quests.where('authorUid', '==', currentUserUid)
     *            .where('catalogId', 'in', catalogIdsToSync)
     *            .where('lastModifiedAt', '>', cursor)`
     *     Composite equality + in + range is allowed by Firestore.
     *
     *   **Query B (public quests — NO catalogId filter)**:
     *     `quests.where('visibleOn', 'array-contains-any', availableShelves)
     *            .where('lastModifiedAt', '>', cursor)`
     *     Firebase does not allow combining `array-contains-any` with `where-in`.
     *     The client filters `catalogId in catalogIdsToSync` **locally** after fetch.
     *
     *   Results are merged and deduped by id before upsert.
     *
     * **State Matrix 1 — per-item processing order (CRITICAL for phase-01 implementation):**
     *
     * For each returned DTO:
     * ```
     * 1. If local == null (absent):
     *    - delete-marker (archived=true || visibleOn.isEmpty()) → SKIP (no tombstone created)
     *    - else → INSERT
     * 2. If local != null (present):
     *    - dto.version <= local.version → SKIP (stale or equal — regardless of archived/visibleOn)
     *    - dto.version > local.version + delete-marker → DELETE local
     *    - dto.version > local.version + not delete-marker → UPSERT
     * ```
     *
     * **Version check comes FIRST for the present case.** A stale tombstone
     * (archived=true but version <= local.version) must be ignored, not applied.
     * Tested in scenarios 23b, 23c (quest analogs).
     *
     * After successful refresh, cursor must be updated to `max(lastModifiedAt)` of all
     * processed items.
     *
     * Spec: FR#14 (cascading sync step 2), State Matrix 1, AC#47-49.
     *   Scenarios 23b, 23c (version guard for delete-marker).
     *
     * @param currentUserUid   Firebase Auth UID of the current user, or null if guest.
     *                         When null, Query A (own quests by authorUid) is skipped;
     *                         only Query B (public quests by visibleOn) runs.
     * @param availableShelves shelves the current user has access to (e.g. `{"home", "arena"}`).
     * @param catalogIdsToSync catalog ids whose `contentsVersion` changed (triggers quest pull).
     *                         Empty set → no-op (no Firestore reads, returns success immediately).
     * @param cursor           local questsCursor (max `lastModifiedAt` seen so far).
     * @return [Result.success] containing the set of processed [QuestId]s (passed to section sync
     *   as parentIds); [Result.failure] on network/permission errors.
     */
    suspend fun refreshFromRemote(
        currentUserUid: String?,
        availableShelves: Set<String>,
        catalogIdsToSync: Set<CatalogId>,
        cursor: Long,
    ): Result<Set<QuestId>>
}
