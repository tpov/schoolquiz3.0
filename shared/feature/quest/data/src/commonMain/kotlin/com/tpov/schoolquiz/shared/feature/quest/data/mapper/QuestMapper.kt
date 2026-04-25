package com.tpov.schoolquiz.shared.feature.quest.data.mapper

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.persistence.QuestEntity
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId

object QuestMapper {
    fun QuestEntity.toDomain(): Quest = Quest(
        id = QuestId(id),
        catalogId = CatalogId(catalogId),
        authorUid = authorUid,
        title = title,
        picturePath = picturePath,
        pictureUrl = pictureUrl,
        visibleOn = visibleOn,
        averageRating = averageRating,
        averageRatingCount = averageRatingCount,
        version = version,
        contentsVersion = contentsVersion,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
    )
}
