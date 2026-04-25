package com.tpov.schoolquiz.android.core.designsystem.model

import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId

/**
 * Presentation model for a catalog entry displayed in UI grids and spinners.
 *
 * [pictureUrl] is a pre-resolved HTTPS URL (null if no picture or not yet resolved).
 * Domain [Catalog.picturePath] is a relative Storage path — URL resolution happens in data layer.
 * ADR-L3-03: presentation model lives in android:core:designsystem, not in domain.
 */
data class CatalogDisplayItem(
    val id: CatalogId,
    val name: String,
    val pictureUrl: String?,
)

fun Catalog.toDisplayItem(): CatalogDisplayItem = CatalogDisplayItem(
    id = id,
    name = name,
    pictureUrl = pictureUrl,
)
