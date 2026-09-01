package com.tpov.schoolquiz.shared.feature.quest.data.mapper

import com.tpov.schoolquiz.shared.core.persistence.QuestEntity
import com.tpov.schoolquiz.shared.feature.quest.data.dto.QuestDto

object QuestDtoMapper {
    fun QuestDto.toEntity(pictureUrl: String?): QuestEntity = QuestEntity(
        id = id,
        catalogId = catalogId,
        authorUid = authorUid,
        title = title,
        picturePath = picturePath,
        pictureUrl = pictureUrl,
        visibleOn = visibleOn.toSet(),
        averageRating = averageRating?.toFloat(),
        averageRatingCount = averageRatingCount,
        version = version,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
    )
}
