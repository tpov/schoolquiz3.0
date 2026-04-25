package com.tpov.schoolquiz.shared.feature.section.domain.fake

import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.section.domain.model.Section
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory fake implementation of [SectionRepository] for use case testing.
 *
 * The [refreshByParents] implementation simulates cursor-based delta sync:
 *  - Items with lastModifiedAt <= cursor → skip.
 *  - Items with `archived=true` and higher version → local delete.
 *  - Items with incoming.version <= local.version → skip (version guard).
 *  - Otherwise → upsert.
 *
 * [lastCursor] tracks the cursor advanced after each [refreshByParents] call.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md — AC#6.
 *   Domain Test Scenarios 41, 45.
 */
class FakeSectionRepository(
    initial: List<Section> = emptyList(),
) : SectionRepository {

    private val cache = MutableStateFlow<Map<SectionId, Section>>(
        initial.associateBy { it.id },
    )

    private var pendingRemote: List<Section>? = null
    private var nextRefreshFailure: Throwable? = null

    /** The cursor value advanced by the last [refreshByParents] call. */
    var lastCursor: Long = 0L
        private set

    /** How many times [refreshByParents] was called. Used for early-exit tests. */
    var refreshCallCount: Int = 0
        private set

    override fun observeByQuest(questId: QuestId): Flow<List<Section>> =
        cache.map { map ->
            map.values.filter { it.questId == questId }.sortedBy { it.order }
        }

    override suspend fun getById(id: SectionId): Section? = cache.value[id]

    override suspend fun refreshByParents(questIds: Set<QuestId>, cursor: Long): Result<Set<SectionId>> {
        refreshCallCount++
        lastCursor = cursor
        val failure = nextRefreshFailure
        if (failure != null) {
            nextRefreshFailure = null
            return Result.failure(failure)
        }
        val remote = pendingRemote
        val processedIds = mutableSetOf<SectionId>()
        if (remote != null) {
            pendingRemote = null
            cache.update { current ->
                val mutable = current.toMutableMap()
                var newMaxLastMod = cursor
                for (incoming in remote) {
                    if (incoming.questId !in questIds) continue
                    // Cursor guard
                    if (incoming.lastModifiedAt <= cursor) continue
                    newMaxLastMod = maxOf(newMaxLastMod, incoming.lastModifiedAt)
                    processedIds.add(incoming.id)
                    val existing = mutable[incoming.id]
                    when {
                        incoming.archived && (existing == null || incoming.version > existing.version) -> {
                            // archived + higher version → local delete
                            mutable.remove(incoming.id)
                        }
                        !incoming.archived -> {
                            if (existing == null || incoming.version > existing.version) {
                                mutable[incoming.id] = incoming
                            }
                        }
                        // archived but not newer version → skip
                    }
                }
                lastCursor = newMaxLastMod
                mutable
            }
        }
        return Result.success(processedIds)
    }

    override suspend fun getLocalContentsVersion(id: SectionId): Long? = cache.value[id]?.contentsVersion

    // ── Test helpers ──────────────────────────────────────────────────────────

    fun seed(sections: List<Section>) {
        cache.value = sections.associateBy { it.id }
    }

    fun simulateRemoteSections(sections: List<Section>) {
        pendingRemote = sections
    }

    fun setNextRefreshFailure(error: Throwable) {
        nextRefreshFailure = error
    }

    fun snapshot(): List<Section> = cache.value.values.sortedBy { it.id.value }
}
