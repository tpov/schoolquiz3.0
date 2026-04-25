package com.tpov.schoolquiz.shared.feature.lesson.data.mapper

import com.tpov.schoolquiz.shared.core.persistence.LessonEntity
import com.tpov.schoolquiz.shared.feature.lesson.data.dto.LessonDto

object LessonDtoMapper {
    fun LessonDto.toEntity(): LessonEntity = LessonEntity(
        id = id,
        themeId = themeId,
        title = title,
        order = order,
        version = version,
        contentsVersion = contentsVersion,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
    )
}
