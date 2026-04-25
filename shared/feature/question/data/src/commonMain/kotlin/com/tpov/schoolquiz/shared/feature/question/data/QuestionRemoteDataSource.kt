package com.tpov.schoolquiz.shared.feature.question.data

import com.tpov.schoolquiz.shared.feature.question.data.dto.QuestionDto

interface QuestionRemoteDataSource {
    /** Fetch questions whose lessonId is in lessonIds and lastModifiedAt > cursor. lessonIds.size ≤ 30. */
    suspend fun fetchChangedByParents(lessonIds: Set<String>, cursor: Long): List<QuestionDto>
}
