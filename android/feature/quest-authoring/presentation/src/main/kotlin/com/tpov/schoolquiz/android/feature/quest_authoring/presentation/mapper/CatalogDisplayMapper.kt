package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.mapper

import com.tpov.schoolquiz.android.core.designsystem.model.CatalogDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog

fun Catalog.toAuthoringDisplayItem(): CatalogDisplayItem =
    CatalogDisplayItem(
        id = id,
        name = name,
        pictureUrl = pictureUrl,
    )
