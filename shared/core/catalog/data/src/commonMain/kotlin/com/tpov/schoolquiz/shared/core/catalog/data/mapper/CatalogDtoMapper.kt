package com.tpov.schoolquiz.shared.core.catalog.data.mapper

import com.tpov.schoolquiz.shared.core.catalog.data.CatalogDto
import com.tpov.schoolquiz.shared.core.persistence.CatalogEntity

fun CatalogDto.toEntity(): CatalogEntity =
    CatalogEntity(
        id = id,
        name = name,
        picturePath = picturePath,
        pictureUrl = null,
        version = version,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
        iconCategoryKey = iconCategoryKey,
        iconNames = iconNames,
        // Тип обязан доезжать от сервера до Room: полка домашних квестов фильтрует
        // COURSE-каталоги («у курсов свой вход»), и потеря поля здесь кладёт курсовый
        // каталог на домашнюю полку как REGULAR. Dto несёт строку — неизвестное значение
        // от нового клиента уже деградировало до REGULAR на разборе снапшота.
        questType = questType,
    )
