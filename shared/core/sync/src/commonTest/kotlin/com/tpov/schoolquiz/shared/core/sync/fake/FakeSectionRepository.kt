package com.tpov.schoolquiz.shared.core.sync.fake

import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.section.domain.model.Section
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Sync-module fake for [SectionRepository].
 *
 * Supports batch-capture via [captureRefreshBatches]/[batchedRefreshCalls]
 * for verifying AC#50 (Firestore in-filter limit ≤ 30).
 */
class FakeSectionRepository : SectionRepository {

    private val cache = MutableStateFlow<Map<SectionId, Section>>(emptyMap())

    var refreshCallCount = 0
    var captureRefreshBatches = false
    val batchedRefreshCalls = mutableListOf<Set<QuestId>>()

    private var nextRefreshFailure: Throwable? = null
    private var nextRefreshChangedOverride: Set<SectionId>? = null

    override fun observeByQuest(questId: QuestId): Flow<List<Section>> =
        cache.map { it.values.filter { s -> s.questId == questId }.sortedBy { it.order } }

    override suspend fun getById(id: SectionId): Section? = cache.value[id]

    override suspend fun refreshByParents(questIds: Set<QuestId>, cursor: Long): Result<Set<SectionId>> {
        refreshCallCount++
        if (captureRefreshBatches) batchedRefreshCalls.add(questIds.toSet())

        val failure = nextRefreshFailure
        if (failure != null) {
            nextRefreshFailure = null
            return Result.failure(failure)
        }
        return Result.success(nextRefreshChangedOverride ?: emptySet())
    }

    override suspend fun getLocalContentsVersion(id: SectionId): Long? =
        cache.value[id]?.contentsVersion

    fun setNextRefreshFailure(error: Throwable) { nextRefreshFailure = error }
    fun setNextRefreshChanged(ids: Set<SectionId>) { nextRefreshChangedOverride = ids }
    fun seed(sections: List<Section>) { cache.value = sections.associateBy { it.id } }
    fun snapshot(): List<Section> = cache.value.values.sortedBy { it.id.value }

    fun resetAll() {
        refreshCallCount = 0
        captureRefreshBatches = false
        batchedRefreshCalls.clear()
        nextRefreshFailure = null
        nextRefreshChangedOverride = null
        cache.value = emptyMap()
    }
}
