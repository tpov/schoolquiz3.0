package com.tpov.schoolquiz.shared.feature.quest.domain.repository

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestShelfChange
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestShelfSyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
     *   `quest.authorUid == authorUid && (catalogId == null || quest.catalogId == catalogId)`.
     *
     * `archived=true` root quests remain visible and mark question content as
     * on-demand. Deletion/removal is represented by empty [Quest.visibleOn] or by
     * a missing concrete document during sync-list refresh.
     *
     * Spec: FR#21, AC#47, Primary User Journey 7, Domain Test Scenario 26-27.
     */
    fun observeMyQuests(
        authorUid: String,
        catalogId: CatalogId? = null,
    ): Flow<List<Quest>>

    /**
     * Observes public quests belonging to [catalogId] that are visible on [shelf].
     *
     * Filter: `catalogId == catalogId AND visibleOn contains shelf`.
     * Sort: `lastModifiedAt DESC` (Quest has no order field — User Decision Q1).
     *
     * DAO pattern: delimiter-wrapped LIKE — `(CHAR(31) || visibleOn || CHAR(31)) LIKE
     * ('%' || CHAR(31) || :shelf || CHAR(31) || '%')` — prevents "home" matching "homeExtra".
     * `archived` is not a visibility filter for root quests; it controls
     * on-demand question content.
     *
     * Emits empty list when no matching quests exist. Re-emits on Room writes.
     *
     * Spec: docs/features/quizzes-screen/plan/phase-01/backend.md, AC#1-4.
     */
    fun observeByCatalog(
        catalogId: CatalogId,
        shelf: String,
    ): Flow<List<Quest>>

    /**
     * Observes archived quests from [catalogId] on [shelf] whose lesson questions
     * already exist in the local cache.
     *
     * Used by the Courses/Home entry so users see only courses they downloaded,
     * while the Archive entry can still show the full downloadable catalog.
     */
    fun observeDownloadedArchivedByCatalog(
        catalogId: CatalogId,
        shelf: String,
    ): Flow<List<Quest>> = observeByCatalog(catalogId, shelf)

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
     * Pulls concrete quests by id for sync-list based refresh.
     *
     * Default keeps existing fakes source-compatible; production repositories
     * override it.
     */
    suspend fun refreshByIds(ids: Set<QuestId>): Result<Unit> = Result.success(Unit)

    /**
     * Кладёт в очередь намерение перенести публичный квест на другую полку (AD-5, AD-6).
     *
     * Локальная строка квеста не меняется: полка — серверно-защищённое поле (AD-25). Сервер, приняв
     * запись, пишет `quests/{questId}.visibleOn = [targetShelf]` и запись журнала
     * `catalogs/{catalogId}/sync_changes/quest_{questId}`, по которой ближайшая синхронизация
     * чтения и обновит локальную строку.
     *
     * Более раннее неотправленное намерение по тому же квесту снимается: очередь порядка не
     * обещает (AD-27), и два переноса одного квеста доехали бы в произвольном порядке.
     */
    suspend fun queueShelfChange(change: QuestShelfChange): Result<Unit> = Result.success(Unit)

    /**
     * Что очередь знает о переносе этого квеста — для пометки на карточке (AD-14, история 4.4).
     *
     * Ответ приходит из очереди по ссылке на сущность, а не из строки квеста: локально полка не
     * меняется, и единственный след намерения — запись очереди. Карантин отличается от ожидания
     * отдельным значением закрытого набора (AD-28).
     */
    fun observeShelfChangeState(
        ownerUid: String,
        questId: QuestId,
    ): Flow<QuestShelfSyncState> = flowOf(QuestShelfSyncState.NONE)

    /**
     * Takes a published quest off every shelf. Server contract: `visibleOn = []` plus one
     * sync_change; on the next refresh of this quest the empty shelf list deletes the local row.
     */
    suspend fun retirePublic(questId: QuestId): Result<Unit> = Result.success(Unit)

    /**
     * Pulls quests from remote for the given filter and persists them locally.
     *
     * **No-op guard**: if [catalogIdsToSync] is empty, returns immediately without
     * any Firestore reads. Step 2 is only triggered for catalogs the `sync_changes`
     * journal reported as changed.
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
     *    - visibleOn.isEmpty() → SKIP (no tombstone created)
     *    - else → INSERT
     * 2. If local != null (present):
     *    - dto.version <= local.version → SKIP (stale or equal — regardless of visibleOn)
     *    - dto.version > local.version + visibleOn.isEmpty() → DELETE local
     *    - dto.version > local.version + visibleOn not empty → UPSERT
     * ```
     *
     * **Version check comes FIRST for the present case.** A stale removal marker
     * (visibleOn empty but version <= local.version) must be ignored, not applied.
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
     * @param catalogIdsToSync catalog ids the `sync_changes` journal reported as changed
     *                         (triggers quest pull).
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
