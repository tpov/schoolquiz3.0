package com.tpov.schoolquiz.shared.feature.question.data.mapper

import com.tpov.schoolquiz.shared.core.persistence.QuestionEntity
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.question.domain.model.Question
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId

object QuestionMapper {
    fun QuestionEntity.toDomain(): Question = Question(
        id = QuestionId(id),
        lessonId = LessonId(lessonId),
        text = text,
        payload = payload,
        language = language,
        languageLevel = languageLevel,
        order = order,
        version = version,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
    )
}
