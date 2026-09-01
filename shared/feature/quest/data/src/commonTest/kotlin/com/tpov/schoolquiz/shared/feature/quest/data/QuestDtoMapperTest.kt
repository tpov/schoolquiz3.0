package com.tpov.schoolquiz.shared.feature.quest.data

import com.tpov.schoolquiz.shared.feature.quest.data.dto.QuestDto
import com.tpov.schoolquiz.shared.feature.quest.data.mapper.QuestDtoMapper.toEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM tests for QuestDtoMapper.toEntity().
 * Source: docs/features/home-and-my-quests/plan/phase-02/tests.md §5
 */
class QuestDtoMapperTest {

    private fun makeDto(
        id: String = "q1",
        catalogId: String = "c1",
        authorUid: String = "uid-a",
        title: String = "Title",
        picturePath: String? = null,
        visibleOn: List<String> = listOf("home"),
        averageRating: Double? = null,
        averageRatingCount: Int = 0,
        version: Long = 1L,
        lastModifiedAt: Long = 0L,
        archived: Boolean = false,
    ) = QuestDto(id, catalogId, authorUid, title, picturePath, visibleOn, averageRating, averageRatingCount, version, lastModifiedAt, archived)

    // toEntity maps all fields correctly
    @Test
    fun `toEntity maps all fields`() {
        val dto = makeDto(
            id = "q1",
            catalogId = "c1",
            authorUid = "uid-a",
            title = "My Quest",
            picturePath = "quest-pictures/q1.jpg",
            visibleOn = listOf("home", "arena"),
            averageRating = 2.7,
            averageRatingCount = 5,
            version = 3L,
            lastModifiedAt = 12345L,
            archived = false,
        )

        val entity = dto.toEntity(pictureUrl = "https://example.com/pic.jpg")

        assertEquals("q1", entity.id)
        assertEquals("c1", entity.catalogId)
        assertEquals("uid-a", entity.authorUid)
        assertEquals("My Quest", entity.title)
        assertEquals("quest-pictures/q1.jpg", entity.picturePath)
        assertEquals("https://example.com/pic.jpg", entity.pictureUrl)
        assertEquals(setOf("home", "arena"), entity.visibleOn)
        assertEquals(3L, entity.version)
        assertEquals(12345L, entity.lastModifiedAt)
        assertEquals(false, entity.archived)
        assertEquals(5, entity.averageRatingCount)
        val rating = entity.averageRating
        assertTrue(rating != null && rating in 2.69f..2.71f, "averageRating should be ~2.7f, got $rating")
    }

    // Double to Float precision: 2.700000001 should round to ~2.7f within 0.01f
    @Test
    fun `toEntity double to float precision within 0 01 of 2 7`() {
        val dto = makeDto(averageRating = 2.700000001, visibleOn = listOf("home"))
        val entity = dto.toEntity(pictureUrl = null)
        val rating = entity.averageRating
        assertTrue(rating != null && rating in 2.69f..2.71f, "Expected ~2.7f within 0.01, got $rating")
    }

    // null averageRating → entity.averageRating = null
    @Test
    fun `toEntity null averageRating`() {
        val dto = makeDto(averageRating = null, visibleOn = listOf("home"))
        val entity = dto.toEntity(pictureUrl = null)
        assertNull(entity.averageRating)
    }

    // pictureUrl injected as parameter (not from dto)
    @Test
    fun `toEntity pictureUrl injected from parameter`() {
        val dto = makeDto(picturePath = "quest-pictures/q1.jpg", visibleOn = listOf("home"))
        val entity = dto.toEntity(pictureUrl = "https://resolved.url/pic.jpg")
        assertEquals("https://resolved.url/pic.jpg", entity.pictureUrl)
    }

    // visibleOn List<String> → Set<String>
    @Test
    fun `toEntity visibleOn converted to Set`() {
        val dto = makeDto(visibleOn = listOf("home", "arena", "home"))
        val entity = dto.toEntity(pictureUrl = null)
        assertEquals(setOf("home", "arena"), entity.visibleOn)
    }

    // archived=true preserved
    @Test
    fun `toEntity archived true preserved`() {
        val dto = makeDto(archived = true, visibleOn = listOf("home"))
        val entity = dto.toEntity(pictureUrl = null)
        assertEquals(true, entity.archived)
    }
}
