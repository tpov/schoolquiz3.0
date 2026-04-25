package com.tpov.schoolquiz.shared.feature.section.data.mapper

import com.tpov.schoolquiz.shared.core.persistence.SectionEntity
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.section.domain.model.Section
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId

object SectionMapper {
    fun SectionEntity.toDomain(): Section = Section(
        id = SectionId(id),
        questId = QuestId(questId),
        title = title,
        order = order,
        version = version,
        contentsVersion = contentsVersion,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
    )
}
