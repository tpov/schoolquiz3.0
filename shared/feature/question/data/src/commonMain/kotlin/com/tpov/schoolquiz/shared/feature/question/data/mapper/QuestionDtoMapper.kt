package com.tpov.schoolquiz.shared.feature.question.data.mapper

import com.tpov.schoolquiz.shared.core.persistence.QuestionEntity
import com.tpov.schoolquiz.shared.feature.question.data.dto.QuestionDto

object QuestionDtoMapper {
    fun QuestionDto.toEntity(): QuestionEntity = QuestionEntity(
        id = id,
        lessonId = lessonId,
        text = text,
        payload = payload,
        language = language,
        order = order,
        version = version,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
        languageLevel = languageLevel,
    )
}
