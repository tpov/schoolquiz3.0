package com.tpov.schoolquiz.shared.feature.theme.data.mapper

import com.tpov.schoolquiz.shared.core.persistence.ThemeEntity
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId

object ThemeMapper {
    fun ThemeEntity.toDomain(): Theme = Theme(
        id = ThemeId(id),
        sectionId = SectionId(sectionId),
        title = title,
        order = order,
        version = version,
        contentsVersion = contentsVersion,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
    )
}
