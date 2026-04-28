package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLessonAttemptRepository : LessonAttemptRepository {

    private val stored = MutableStateFlow<List<Attempt>>(emptyList())
    var saveCallCount: Int = 0
        private set
    var saveResult: Result<Unit> = Result.success(Unit)
    val savedAttempts: List<Attempt> get() = stored.value

    override suspend fun save(attempt: Attempt): Result<Unit> {
        saveCallCount++
        if (saveResult.isSuccess) {
            stored.value = stored.value + attempt
        }
        return saveResult
    }

    override fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>> =
        stored.map { list ->
            list.filter { it.userId == userId && it.lessonId == lessonId }
        }

    override fun observeAllByUser(userId: String): Flow<List<Attempt>> =
        stored.map { list -> list.filter { it.userId == userId } }
}
