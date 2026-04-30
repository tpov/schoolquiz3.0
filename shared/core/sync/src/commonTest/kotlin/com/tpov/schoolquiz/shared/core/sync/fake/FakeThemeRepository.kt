package com.tpov.schoolquiz.shared.core.sync.fake

import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeThemeRepository : ThemeRepository {

    private val cache = MutableStateFlow<Map<ThemeId, Theme>>(emptyMap())

    var refreshCallCount = 0
    var refreshByIdsCallCount = 0
    var lastRefreshByIds: Set<ThemeId> = emptySet()
    private var nextRefreshFailure: Throwable? = null
    private var nextRefreshChangedOverride: Set<ThemeId>? = null

    override fun observeBySection(sectionId: SectionId): Flow<List<Theme>> =
        cache.map { it.values.filter { t -> t.sectionId == sectionId }.sortedBy { it.order } }

    override suspend fun getById(id: ThemeId): Theme? = cache.value[id]

    override suspend fun refreshByParents(sectionIds: Set<SectionId>, cursor: Long): Result<Set<ThemeId>> {
        refreshCallCount++
        val failure = nextRefreshFailure
        if (failure != null) {
            nextRefreshFailure = null
            return Result.failure(failure)
        }
        return Result.success(nextRefreshChangedOverride ?: emptySet())
    }

    override suspend fun refreshByIds(ids: Set<ThemeId>): Result<Unit> {
        refreshByIdsCallCount++
        lastRefreshByIds = ids
        return Result.success(Unit)
    }

    override suspend fun getLocalContentsVersion(id: ThemeId): Long? =
        cache.value[id]?.contentsVersion

    fun setNextRefreshFailure(error: Throwable) { nextRefreshFailure = error }
    fun setNextRefreshChanged(ids: Set<ThemeId>) { nextRefreshChangedOverride = ids }
    fun seed(themes: List<Theme>) { cache.value = themes.associateBy { it.id } }

    fun resetAll() {
        refreshCallCount = 0
        refreshByIdsCallCount = 0
        lastRefreshByIds = emptySet()
        nextRefreshFailure = null
        nextRefreshChangedOverride = null
        cache.value = emptyMap()
    }
}
