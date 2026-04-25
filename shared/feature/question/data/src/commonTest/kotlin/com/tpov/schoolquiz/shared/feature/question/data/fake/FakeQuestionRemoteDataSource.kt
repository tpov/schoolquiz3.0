package com.tpov.schoolquiz.shared.feature.question.data.fake

import com.tpov.schoolquiz.shared.feature.question.data.QuestionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.question.data.dto.QuestionDto

class FakeQuestionRemoteDataSource : QuestionRemoteDataSource {

    var result: List<QuestionDto> = emptyList()
    var shouldThrow: Boolean = false
    var callCount: Int = 0
    var lastLessonIds: Set<String> = emptySet()
    var lastCursor: Long = -1L

    override suspend fun fetchChangedByParents(lessonIds: Set<String>, cursor: Long): List<QuestionDto> {
        callCount++
        lastLessonIds = lessonIds
        lastCursor = cursor
        if (shouldThrow) throw RuntimeException("FakeQuestionRemoteDataSource: network error")
        return result
    }
}
