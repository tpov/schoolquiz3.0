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
    /**
     * How many quests the catalogue holds, already worded for the tile, or null while unknown.
     *
     * A string rather than a number, because the tile shows it verbatim and Russian counts three
     * ways — deciding that here keeps the plural rule off the screen and out of the design system.
     */
    val questCountLabel: String? = null,
)

fun Catalog.toDisplayItem(): CatalogDisplayItem =
    CatalogDisplayItem(
        id = id,
        name = name,
        pictureUrl = pictureUrl,
        picturePath = picturePath,
        questType = questType,
    )
