package com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake

import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptDao
import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLessonAttemptDao : LessonAttemptDao {

    private val store = mutableListOf<LessonAttemptEntity>()
    private val _flow = MutableStateFlow<List<LessonAttemptEntity>>(emptyList())
    var upsertCallCount = 0
        private set

    override suspend fun upsert(entity: LessonAttemptEntity): Long {
        upsertCallCount++
        val idx = store.indexOfFirst { it.attemptId == entity.attemptId }
        if (idx >= 0) store[idx] = entity else store.add(entity)
        _flow.value = store.toList()
        return store.indexOf(entity).toLong() + 1
    }

    override fun observeByLesson(userId: String, lessonId: String): Flow<List<LessonAttemptEntity>> =
        _flow.map { list -> list.filter { it.userId == userId && it.lessonId == lessonId } }

    override fun observeAllByUser(userId: String): Flow<List<LessonAttemptEntity>> =
        _flow.map { list -> list.filter { it.userId == userId } }
}
