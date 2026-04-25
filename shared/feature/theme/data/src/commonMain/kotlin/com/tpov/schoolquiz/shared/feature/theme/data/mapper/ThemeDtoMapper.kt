package com.tpov.schoolquiz.shared.feature.theme.data.mapper

import com.tpov.schoolquiz.shared.core.persistence.ThemeEntity
import com.tpov.schoolquiz.shared.feature.theme.data.dto.ThemeDto

object ThemeDtoMapper {
    fun ThemeDto.toEntity(): ThemeEntity = ThemeEntity(
        id = id,
        sectionId = sectionId,
        title = title,
        order = order,
        version = version,
        contentsVersion = contentsVersion,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
    )
}
