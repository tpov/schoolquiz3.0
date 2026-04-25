package com.tpov.schoolquiz.shared.feature.lesson.data.mapper

import com.tpov.schoolquiz.shared.core.persistence.LessonEntity
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId

object LessonMapper {
    fun LessonEntity.toDomain(): Lesson = Lesson(
        id = LessonId(id),
        themeId = ThemeId(themeId),
        title = title,
        order = order,
        version = version,
        contentsVersion = contentsVersion,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
    )
}
