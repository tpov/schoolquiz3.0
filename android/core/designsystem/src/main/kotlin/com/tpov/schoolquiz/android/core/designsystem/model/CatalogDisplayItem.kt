package com.tpov.schoolquiz.android.core.designsystem.model

import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.model.QuestType

/**
 * Presentation model for a catalog entry displayed in UI grids and spinners.
 *
 * [pictureUrl] is a pre-resolved HTTPS URL (null if no picture or not yet resolved).
 * [picturePath] is kept for bundled offline thumbnails when remote URL resolution is unavailable.
 * ADR-L3-03: presentation model lives in android:core:designsystem, not in domain.
 */
data class CatalogDisplayItem(
    val id: CatalogId,
    val name: String,
    val pictureUrl: String?,
    val picturePath: String? = null,
    /** Drives editor and publication behaviour; see [QuestType]. */
    val questType: QuestType = QuestType.REGULAR,
)

fun Catalog.toDisplayItem(): CatalogDisplayItem =
    CatalogDisplayItem(
        id = id,
        name = name,
        pictureUrl = pictureUrl,
        picturePath = picturePath,
        questType = questType,
    )
