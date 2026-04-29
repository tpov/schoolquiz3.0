package com.tpov.schoolquiz.shared.core.catalog.data

import com.tpov.schoolquiz.shared.core.catalog.data.mapper.toDomain
import com.tpov.schoolquiz.shared.core.catalog.data.mapper.toEntity
import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.persistence.CatalogEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * CF-19..CF-21, CF-23 — mapper round-trip scenarios.
 * CF-22 (DocumentSnapshot.toCatalogDto — Firebase-specific) — see
 * platform/firebase/../FirestoreCatalogDtoMapperTest.kt
 */
class CatalogMapperTest {

    // CF-19: CatalogEntity.toDomain() preserves id
    @Test
    fun `when entity has id surveys then toDomain returns CatalogId surveys`() {
        val entity = CatalogEntity(
            id = "surveys",
            name = "Опросы",
            picturePath = null,
            pictureUrl = null,
        )

        val domain = entity.toDomain()

        assertEquals(CatalogId("surveys"), domain.id)
    }

    // CF-20: CatalogEntity.toDomain() preserves name
    @Test
    fun `when entity has name Опросы then toDomain returns domain with same name`() {
        val entity = CatalogEntity(
            id = "surveys",
            name = "Опросы",
            picturePath = null,
            pictureUrl = null,
        )

        val domain = entity.toDomain()

        assertEquals("Опросы", domain.name)
    }

    // CF-21: Catalog.toEntity() preserves picturePath=null
    @Test
    fun `when catalog has null picturePath then toEntity returns entity with null picturePath`() {
        val catalog = Catalog(
            id = CatalogId("courses"),
            name = "Курсы",
            picturePath = null,
        )

        val entity = catalog.toEntity()

        assertNull(entity.picturePath)
    }

    // CF-23: CatalogDto.toEntity() preserves picturePath=null
    @Test
    fun `when dto has null picturePath then toEntity returns entity with null picturePath`() {
        val dto = CatalogDto(id = "games", name = "Игры", picturePath = null, version = 1L, contentsVersion = 0L, lastModifiedAt = 0L, archived = false)

        val entity = dto.toEntity()

        assertNull(entity.picturePath)
    }

    @Test
    fun `when dto has icon names then toEntity preserves icon names`() {
        val dto = CatalogDto(
            id = "games",
            name = "Games",
            picturePath = null,
            version = 1L,
            contentsVersion = 0L,
            lastModifiedAt = 0L,
            archived = false,
            iconNames = listOf("VideogameAsset", "Casino"),
        )

        val entity = dto.toEntity()

        assertEquals(listOf("VideogameAsset", "Casino"), entity.iconNames)
    }

    @Test
    fun `when entity has icon names then toDomain preserves icon names`() {
        val entity = CatalogEntity(
            id = "games",
            name = "Games",
            picturePath = null,
            pictureUrl = null,
            iconNames = listOf("VideogameAsset", "Casino"),
        )

        val domain = entity.toDomain()

        assertEquals(listOf("VideogameAsset", "Casino"), domain.iconNames)
    }
}
