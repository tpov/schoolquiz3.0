package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.question.domain.model.Question
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeQuestionRepository : QuestionRepository {
    private val questions = MutableStateFlow<List<Question>>(emptyList())

    fun emit(items: List<Question>) {
        questions.value = items
    }

    override fun observeByLesson(lessonId: LessonId): Flow<List<Question>> =
        questions.map { items -> items.filter { it.lessonId == lessonId }.sortedBy { it.order } }

    override suspend fun getById(id: QuestionId): Question? =
        questions.value.firstOrNull { it.id == id }

    override suspend fun refreshByParents(lessonIds: Set<LessonId>, cursor: Long): Result<Unit> =
        Result.success(Unit)
}
