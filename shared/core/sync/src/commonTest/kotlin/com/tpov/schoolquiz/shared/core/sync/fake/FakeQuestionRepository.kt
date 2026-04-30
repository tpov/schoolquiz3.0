package com.tpov.schoolquiz.shared.core.sync.fake

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.question.domain.model.Question
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeQuestionRepository : QuestionRepository {

    private val cache = MutableStateFlow<Map<QuestionId, Question>>(emptyMap())

    var refreshCallCount = 0
    var refreshByIdsCallCount = 0
    var lastRefreshByIds: Set<QuestionId> = emptySet()
    private var nextRefreshFailure: Throwable? = null
    private var nextRefreshByIdsFailure: Throwable? = null

    override fun observeByLesson(lessonId: LessonId): Flow<List<Question>> =
        cache.map { it.values.filter { q -> q.lessonId == lessonId }.sortedBy { it.order } }

    override suspend fun getById(id: QuestionId): Question? = cache.value[id]

    override suspend fun refreshByParents(lessonIds: Set<LessonId>, cursor: Long): Result<Unit> {
        refreshCallCount++
        val failure = nextRefreshFailure
        if (failure != null) {
            nextRefreshFailure = null
            return Result.failure(failure)
        }
        return Result.success(Unit)
    }

    override suspend fun refreshByIds(ids: Set<QuestionId>): Result<Unit> {
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
    fun seed(questions: List<Question>) { cache.value = questions.associateBy { it.id } }

    fun resetAll() {
        refreshCallCount = 0
        refreshByIdsCallCount = 0
        lastRefreshByIds = emptySet()
        nextRefreshFailure = null
        nextRefreshByIdsFailure = null
        cache.value = emptyMap()
    }
}
