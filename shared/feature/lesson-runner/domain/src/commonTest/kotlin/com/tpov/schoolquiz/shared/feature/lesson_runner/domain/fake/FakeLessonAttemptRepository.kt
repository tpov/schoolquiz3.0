package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AnsweredQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServedQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLessonAttemptRepository : LessonAttemptRepository {

    /** One call to the three-argument save — everything the use cases hand the repository. */
    data class Save(
        val attempt: Attempt,
        val answers: List<AnsweredQuestion>,
        val served: List<ServedQuestion>,
    )

    private val stored = MutableStateFlow<List<Attempt>>(emptyList())
    var saveCallCount: Int = 0
        private set
    var saveResult: Result<Unit> = Result.success(Unit)
    val savedAttempts: List<Attempt> get() = stored.value
    var lastSave: Save? = null
        private set

    override suspend fun save(attempt: Attempt): Result<Unit> = save(attempt, emptyList(), emptyList())

    override suspend fun save(
        attempt: Attempt,
        answers: List<AnsweredQuestion>,
        served: List<ServedQuestion>,
    ): Result<Unit> {
        saveCallCount++
        lastSave = Save(attempt, answers, served)
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
