package com.tpov.schoolquiz.shared.core.catalog.data.mapper

import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.persistence.CatalogEntity

fun CatalogEntity.toDomain(): Catalog = Catalog(
    id = CatalogId(id),
    name = name,
    picturePath = picturePath,
    pictureUrl = pictureUrl,
    version = version,
    contentsVersion = contentsVersion,
    lastModifiedAt = lastModifiedAt,
    archived = archived,
    iconCategoryKey = iconCategoryKey,
    iconNames = iconNames,
)

fun Catalog.toEntity(): CatalogEntity = CatalogEntity(
    id = id.value,
    name = name,
    picturePath = picturePath,
    pictureUrl = pictureUrl,
    version = version,
    contentsVersion = contentsVersion,
    lastModifiedAt = lastModifiedAt,
    archived = archived,
    iconCategoryKey = iconCategoryKey,
    iconNames = iconNames,
)
