package com.tpov.schoolquiz.android.feature.quest.presentation.fake

import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.model.QuestType
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId

fun buildQuest(
    id: String = "q1",
    authorUid: String = "user1",
    catalogId: String = "cat1",
    title: String = "Test Quest",
    picturePath: String? = null,
    pictureUrl: String? = null,
    averageRating: Float? = null,
    averageRatingCount: Int = 0,
    archived: Boolean = false,
    visibleOn: Set<String> = setOf("home"),
    version: Long = 1L,
    lastModifiedAt: Long = 0L,
) = Quest(
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
    contentsVersion = 0L,
    lastModifiedAt = lastModifiedAt,
    archived = archived,
)

fun buildCatalog(
    id: String = "cat1",
    name: String = "Test Catalog",
    picturePath: String? = null,
    pictureUrl: String? = null,
    archived: Boolean = false,
    version: Long = 1L,
    questType: QuestType = QuestType.REGULAR,
) = Catalog(
    id = CatalogId(id),
    name = name,
    picturePath = picturePath,
    pictureUrl = pictureUrl,
    version = version,
    contentsVersion = 0L,
    lastModifiedAt = 0L,
    archived = archived,
    questType = questType,
)
