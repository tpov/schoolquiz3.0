package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.question.domain.model.Question
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeQuestionRepository : QuestionRepository {

    private val questions = MutableStateFlow<List<Question>>(emptyList())

    fun setQuestions(list: List<Question>) {
        questions.value = list
    }

    fun addQuestion(question: Question) {
        questions.value = questions.value + question
    }

    override fun observeByLesson(lessonId: LessonId): Flow<List<Question>> =
        questions.map { list ->
            list.filter { it.lessonId == lessonId }.sortedBy { it.order }
        }

    override suspend fun getById(id: QuestionId): Question? =
        questions.value.find { it.id == id }

    override suspend fun refreshByParents(lessonIds: Set<LessonId>, cursor: Long): Result<Unit> =
        Result.success(Unit)
}
