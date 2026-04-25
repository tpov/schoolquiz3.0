package com.tpov.schoolquiz.shared.core.catalog.data.mapper

import com.tpov.schoolquiz.shared.core.catalog.data.CatalogDto
import com.tpov.schoolquiz.shared.core.persistence.CatalogEntity

fun CatalogDto.toEntity(): CatalogEntity = CatalogEntity(
    id = id,
    name = name,
    picturePath = picturePath,
    pictureUrl = null,
    version = version,
    contentsVersion = contentsVersion,
    lastModifiedAt = lastModifiedAt,
    archived = archived,
)
