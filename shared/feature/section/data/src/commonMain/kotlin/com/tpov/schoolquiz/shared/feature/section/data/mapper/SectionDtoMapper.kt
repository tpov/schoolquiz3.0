package com.tpov.schoolquiz.shared.feature.section.data.mapper

import com.tpov.schoolquiz.shared.core.persistence.SectionEntity
import com.tpov.schoolquiz.shared.feature.section.data.dto.SectionDto

object SectionDtoMapper {
    fun SectionDto.toEntity(): SectionEntity = SectionEntity(
        id = id,
        questId = questId,
        title = title,
        order = order,
        version = version,
        contentsVersion = contentsVersion,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
    )
}
