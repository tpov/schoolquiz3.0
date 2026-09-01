package com.tpov.schoolquiz.shared.core.sync.fake

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLessonRepository : LessonRepository {

    private val cache = MutableStateFlow<Map<LessonId, Lesson>>(emptyMap())

    var refreshCallCount = 0
    var refreshByIdsCallCount = 0
    var lastRefreshByIds: Set<LessonId> = emptySet()
    private var nextRefreshFailure: Throwable? = null
    private var nextRefreshByIdsFailure: Throwable? = null
    private var nextRefreshChangedOverride: Set<LessonId>? = null

    override fun observeByTheme(themeId: ThemeId): Flow<List<Lesson>> =
        cache.map { it.values.filter { l -> l.themeId == themeId }.sortedBy { it.order } }

    override suspend fun getById(id: LessonId): Lesson? = cache.value[id]

    override suspend fun refreshByParents(themeIds: Set<ThemeId>, cursor: Long): Result<Set<LessonId>> {
        refreshCallCount++
        val failure = nextRefreshFailure
        if (failure != null) {
            nextRefreshFailure = null
            return Result.failure(failure)
        }
        return Result.success(nextRefreshChangedOverride ?: emptySet())
    }

    override suspend fun refreshByIds(ids: Set<LessonId>): Result<Unit> {
        refreshByIdsCallCount++
        lastRefreshByIds = ids
        val failure = nextRefreshByIdsFailure
        if (failure != null) {
            nextRefreshByIdsFailure = null
            return Result.failure(failure)
        }
        return Result.success(Unit)
    }

    fun setNextRefreshFailure(error: Throwable) { nextRefreshFailure = error }
    fun setNextRefreshByIdsFailure(error: Throwable) { nextRefreshByIdsFailure = error }
    fun setNextRefreshChanged(ids: Set<LessonId>) { nextRefreshChangedOverride = ids }
    fun seed(lessons: List<Lesson>) { cache.value = lessons.associateBy { it.id } }

    fun resetAll() {
        refreshCallCount = 0
        refreshByIdsCallCount = 0
        lastRefreshByIds = emptySet()
        nextRefreshFailure = null
        nextRefreshByIdsFailure = null
        nextRefreshChangedOverride = null
        cache.value = emptyMap()
    }
}
