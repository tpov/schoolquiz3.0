package com.tpov.schoolquiz.shared.core.sync.fake

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Sync-module fake for [QuestRepository].
 *
 * Tracks the arguments passed to [refreshFromRemote] for assertion in
 * orchestrator tests. Supports override of the returned changed-ID set
 * via [setNextRefreshChanged], failure injection via [setNextRefreshFailure],
 * and structured-concurrency propagation via [throwCancellation].
 */
class FakeQuestRepository : QuestRepository {

    private val cache = MutableStateFlow<Map<QuestId, Quest>>(emptyMap())
    private var serverQuests: List<Quest> = emptyList()

    var refreshCallCount = 0
    var lastRefreshUid: String? = null
    var lastRefreshCatalogIds: Set<CatalogId> = emptySet()
    var lastRefreshShelves: Set<String> = emptySet()
    var lastRefreshCursor: Long = 0L
    var refreshByIdsCallCount = 0
    var lastRefreshByIds: Set<QuestId> = emptySet()

    private var nextRefreshFailure: Throwable? = null
    private var nextRefreshChangedOverride: Set<QuestId>? = null
    var throwCancellation = false

    override fun observeMyQuests(authorUid: String, catalogId: CatalogId?): Flow<List<Quest>> =
        cache.map { map ->
            map.values.filter { q ->
                q.authorUid == authorUid &&
                    (catalogId == null || q.catalogId == catalogId)
            }.sortedBy { it.id.value }
        }

    override fun observeByShelf(shelf: String): Flow<List<Quest>> =
        cache.map { it.values.filter { q -> shelf in q.visibleOn }.sortedBy { it.id.value } }

    override fun observeByCatalog(catalogId: CatalogId, shelf: String): Flow<List<Quest>> =
        cache.map { map ->
            map.values
                .filter { quest ->
                    quest.catalogId == catalogId &&
                        shelf in quest.visibleOn
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
        if (throwCancellation) throw CancellationException("test cancellation")
        refreshCallCount++
        lastRefreshUid = currentUserUid
        lastRefreshCatalogIds = catalogIdsToSync
        lastRefreshShelves = availableShelves
        lastRefreshCursor = cursor

        val failure = nextRefreshFailure
        if (failure != null) {
            nextRefreshFailure = null
            return Result.failure(failure)
        }
        if (catalogIdsToSync.isEmpty()) return Result.success(emptySet())

        // Apply server quests to cache (simple upsert for integration tests)
        if (serverQuests.isNotEmpty()) {
            cache.value = cache.value.toMutableMap().also { map ->
                for (quest in serverQuests) {
                    if (quest.catalogId !in catalogIdsToSync) continue
                    if (quest.visibleOn.isEmpty()) map.remove(quest.id) else map[quest.id] = quest
                }
            }
        }

        return Result.success(nextRefreshChangedOverride ?: emptySet())
    }

    override suspend fun refreshByIds(ids: Set<QuestId>): Result<Unit> {
        refreshByIdsCallCount++
        lastRefreshByIds = ids
        return Result.success(Unit)
    }

    fun setNextRefreshFailure(error: Throwable) { nextRefreshFailure = error }
    fun setNextRefreshChanged(ids: Set<QuestId>) { nextRefreshChangedOverride = ids }
    fun seedServerQuests(quests: List<Quest>) { serverQuests = quests }
    fun seed(quests: List<Quest>) { cache.value = quests.associateBy { it.id } }
    fun snapshot(): List<Quest> = cache.value.values.sortedBy { it.id.value }

    fun resetAll() {
        refreshCallCount = 0
        lastRefreshUid = null
        lastRefreshCatalogIds = emptySet()
        lastRefreshShelves = emptySet()
        lastRefreshCursor = 0L
        refreshByIdsCallCount = 0
        lastRefreshByIds = emptySet()
        nextRefreshFailure = null
        nextRefreshChangedOverride = null
        throwCancellation = false
        serverQuests = emptyList()
        cache.value = emptyMap()
    }
}
