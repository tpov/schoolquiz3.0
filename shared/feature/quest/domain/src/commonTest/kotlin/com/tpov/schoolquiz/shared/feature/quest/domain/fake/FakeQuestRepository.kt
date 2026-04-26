package com.tpov.schoolquiz.shared.feature.quest.domain.fake

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory fake implementation of [QuestRepository] for use case testing.
 *
 * Backed by a [MutableStateFlow] of a quest map keyed by [QuestId]. Tests can:
 *   - [seed] initial local cache state before invoking use cases.
 *   - [seedServerQuests] / [simulateRemoteQuests] to configure server-side state.
 *   - [setNextRefreshFailure] to simulate network errors.
 *   - [snapshot] to inspect the current local cache state.
 *   - [lastCursor] to inspect the cursor advanced by [refreshFromRemote].
 *
 * This is a full fake (not a mock): it implements the repository contract
 * completely without any test framework dependencies.
 *
 * ## Delta-sync semantics — State Matrix 1 ordering (Fix #A — version guard FIRST)
 *
 * [refreshFromRemote] simulates the real Query A + Query B Firestore split:
 *
 *   **no-op guard**: if `catalogIdsToSync.isEmpty()` → returns immediately.
 *   This mirrors spec FR#14: Step 2 is only triggered when contentsVersion changed
 *   catalogs exist. Empty set means no catalog changed → skip quest sync entirely.
 *
 *   **Query A** (own quests in changed catalogs):
 *     `authorUid == currentUserUid && catalogId in catalogIdsToSync && lastModifiedAt > cursor`
 *
 *   **Query B** (public quests, no catalogId filter — Firebase limitation):
 *     `visibleOn ∩ availableShelves ≠ ∅ && lastModifiedAt > cursor`
 *     Client locally filters `catalogId in catalogIdsToSync` after fetch.
 *
 *   **Merge + dedupe** by id. Then apply State Matrix 1 rules with version FIRST:
 *
 *   **absent (local == null):**
 *   - delete-marker true (`archived=true` OR `visibleOn.isEmpty()`) → **SKIP** (no tombstone)
 *   - delete-marker false → **INSERT**
 *
 *   **present (local != null) — version check FIRST:**
 *   - `incoming.version <= local.version` → **SKIP** (stale or equal, regardless of delete-marker)
 *   - `incoming.version > local.version` + delete-marker true → **DELETE** local
 *   - `incoming.version > local.version` + delete-marker false → **UPSERT**
 *
 *   Cursor advances to `max(lastModifiedAt)` of all processed items.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   State Matrix 1, FR#14 (Query A + Query B), AC#6, AC#47-49.
 *   Domain Test Scenarios 24-27, 32-34, 46-50, 23b, 23c (quest analogs).
 */
class FakeQuestRepository(
    initial: List<Quest> = emptyList(),
) : QuestRepository {

    private val cache = MutableStateFlow<Map<QuestId, Quest>>(
        initial.associateBy { it.id },
    )

    /**
     * The "server" state used by [refreshFromRemote] to simulate Query A + Query B.
     * Set via [seedServerQuests] or [simulateRemoteQuests].
     */
    private var serverQuests: List<Quest> = emptyList()

    private var nextRefreshFailure: Throwable? = null

    /** The cursor value advanced in the last [refreshFromRemote] call. */
    var lastCursor: Long = 0L
        private set

    /** How many times [refreshFromRemote] was called (includes no-op calls). */
    var refreshCallCount: Int = 0
        private set

    // ── QuestRepository implementation ────────────────────────────────────────

    override fun observeMyQuests(authorUid: String, catalogId: CatalogId?): Flow<List<Quest>> =
        cache.map { map ->
            map.values
                .filter { quest ->
                    quest.authorUid == authorUid &&
                        !quest.archived &&
                        (catalogId == null || quest.catalogId == catalogId)
                }
                .sortedBy { it.id.value }
        }

    override fun observeByShelf(shelf: String): Flow<List<Quest>> =
        cache.map { map ->
            map.values
                .filter { shelf in it.visibleOn }
                .sortedBy { it.id.value }
        }

    override fun observeByCatalog(catalogId: CatalogId, shelf: String): Flow<List<Quest>> =
        cache.map { map ->
            map.values
                .filter { quest ->
                    quest.catalogId == catalogId &&
                        shelf in quest.visibleOn &&
                        !quest.archived
                }
                .sortedByDescending { it.lastModifiedAt }
        }

    override suspend fun getById(id: QuestId): Quest? = cache.value[id]

    override suspend fun refreshFromRemote(
        currentUserUid: String?,
        availableShelves: Set<String>,
        catalogIdsToSync: Set<CatalogId>,
        cursor: Long,
    ): Result<Set<QuestId>> {
        refreshCallCount++

        val failure = nextRefreshFailure
        if (failure != null) {
            nextRefreshFailure = null
            return Result.failure(failure)
        }

        // Fix #3: no-op if catalogIdsToSync is empty (Step 2 not triggered)
        if (catalogIdsToSync.isEmpty()) return Result.success(emptySet())

        // Simulate Query A: own quests in changed catalogs, newer than cursor.
        // Skipped when currentUserUid is null (guest mode — ADR-CMP-49 Contract Shape #2).
        val queryA = if (currentUserUid != null) {
            serverQuests.filter { quest ->
                quest.authorUid == currentUserUid &&
                    quest.catalogId in catalogIdsToSync &&
                    quest.lastModifiedAt > cursor
            }
        } else emptyList()

        // Simulate Query B: public quests (no catalogId filter — Firebase limitation),
        // then client-side filter by catalogId in catalogIdsToSync
        val queryB = serverQuests.filter { quest ->
            quest.visibleOn.intersect(availableShelves).isNotEmpty() &&
                quest.lastModifiedAt > cursor &&
                quest.catalogId in catalogIdsToSync  // client-side post-filter
        }

        // Merge + dedupe by id (a quest may appear in both Query A and Query B)
        val merged = (queryA + queryB).distinctBy { it.id }

        val processedIds = mutableSetOf<QuestId>()
        cache.update { current ->
            val mutable = current.toMutableMap()
            var newMaxLastMod = cursor
            for (incoming in merged) {
                newMaxLastMod = maxOf(newMaxLastMod, incoming.lastModifiedAt)
                processedIds.add(incoming.id)

                // delete-marker = archived=true OR visibleOn.isEmpty() (AC#47, AC#48, AC#49)
                val deleteMarker = incoming.archived || incoming.visibleOn.isEmpty()
                val existing = mutable[incoming.id]

                if (existing == null) {
                    // absent case
                    if (deleteMarker) continue  // SKIP: no tombstone for absent+delete-marker
                    mutable[incoming.id] = incoming  // INSERT
                    continue
                }
                // present case — version check FIRST (Matrix 1 ordering)
                if (incoming.version <= existing.version) continue  // SKIP: stale or equal, ignore delete-marker
                // incoming.version > existing.version
                if (deleteMarker) {
                    mutable.remove(incoming.id)  // DELETE local
                } else {
                    mutable[incoming.id] = incoming  // UPSERT
                }
            }
            lastCursor = newMaxLastMod
            mutable
        }
        return Result.success(processedIds)
    }

    // ── Test helpers (not part of the repository interface) ───────────────────

    /**
     * Seeds the local cache directly, bypassing any refresh logic.
     * Useful for arranging baseline state.
     */
    fun seed(quests: List<Quest>) {
        cache.value = quests.associateBy { it.id }
    }

    /**
     * Configures the server-side quest state that [refreshFromRemote] will
     * use to simulate Query A and Query B. Preferred for delta-sync tests.
     *
     * Unlike [simulateRemoteQuests], the server state persists across multiple
     * [refreshFromRemote] calls (until overwritten).
     */
    fun seedServerQuests(quests: List<Quest>) {
        serverQuests = quests
    }

    /**
     * Configures the quests that [refreshFromRemote] will apply on the next call.
     * Equivalent to [seedServerQuests]; kept for backward compatibility with
     * existing tests.
     *
     * Note: with the new Query A+B simulation, the quests passed here act as the
     * full server state. The fake applies the same filtering logic as the real
     * implementation to derive queryA and queryB results.
     */
    fun simulateRemoteQuests(quests: List<Quest>) {
        serverQuests = quests
    }

    /**
     * Directly applies State Matrix 1 delete/upsert rules to [items] without
     * Query A+B filtering. Use this when testing the delete semantics of arrived
     * items in isolation (e.g. testing that archived=true or visibleOn=empty
     * items are deleted when they arrive, regardless of how they arrived).
     *
     * This corresponds to AC#47-49: testing the apply-to-Room semantics, not the
     * Firestore query path. In reality, AC#49 (authorUid=other, visibleOn=empty)
     * cannot arrive via normal queries — this method simulates admin/edge-case delivery.
     */
    fun applyRawRemoteItems(items: List<Quest>, cursor: Long = 0L) {
        cache.value = cache.value.toMutableMap().also { mutable ->
            for (incoming in items) {
                val deleteMarker = incoming.archived || incoming.visibleOn.isEmpty()
                val existing = mutable[incoming.id]
                if (existing == null) {
                    // absent case
                    if (deleteMarker) continue  // SKIP: no tombstone
                    mutable[incoming.id] = incoming  // INSERT
                    continue
                }
                // present case — version check FIRST
                if (incoming.version <= existing.version) continue  // SKIP
                if (deleteMarker) {
                    mutable.remove(incoming.id)  // DELETE
                } else {
                    mutable[incoming.id] = incoming  // UPSERT
                }
            }
        }
    }

    /**
     * Causes the next [refreshFromRemote] call to surface [error].
     */
    fun setNextRefreshFailure(error: Throwable) {
        nextRefreshFailure = error
    }

    /** Current snapshot of the local cache sorted by quest id ASC. */
    fun snapshot(): List<Quest> = cache.value.values.sortedBy { it.id.value }
}
