package com.tpov.schoolquiz.platform.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.tpov.schoolquiz.platform.firebase.lesson.toLessonDto
import com.tpov.schoolquiz.platform.firebase.quest.toQuestDto
import com.tpov.schoolquiz.platform.firebase.section.toSectionDto
import com.tpov.schoolquiz.platform.firebase.theme.toThemeDto
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The server keeps writing `contentsVersion` until epic 8, while the client has stopped reading it.
 *
 * That leaves a window where every published document carries a field no mapper knows about, so the
 * mappers have to be indifferent to it rather than merely unaware of it: a snapshot holding the
 * legacy key must parse into exactly the same DTO as one without it, and must not throw. These
 * tests pin that window shut — they are what stops the field's removal from the client turning into
 * a parse failure against documents the server has not stopped writing.
 */
class LegacyContentsVersionFieldTest {

    /** A snapshot backed by a real field map, so an extra key is genuinely present, not implied. */
    private fun snapshotOf(id: String, data: Map<String, Any?>): DocumentSnapshot {
        val snapshot = mockk<DocumentSnapshot>(relaxed = true)
        every { snapshot.id } returns id
        every { snapshot.get(any<String>()) } answers { data[firstArg<String>()] }
        every { snapshot.getString(any()) } answers { data[firstArg<String>()] as? String }
        return snapshot
    }

    private val legacyField = "contentsVersion" to 42L

    @Test
    fun `quest snapshot carrying contentsVersion parses every other field unchanged`() {
        val snapshot = snapshotOf(
            "quest-1",
            mapOf(
                legacyField,
                "catalogId" to "cat-1",
                "authorUid" to "author-1",
                "title" to "Столица Франции",
                "picturePath" to "quests/quest-1.png",
                "visibleOn" to listOf("arena", "home"),
                "averageRating" to 4.5,
                "averageRatingCount" to 12L,
                "version" to 7L,
                "lastModifiedAt" to 1_700_000_000_000L,
                "archived" to false,
            ),
        )

        val dto = snapshot.toQuestDto()

        assertEquals("quest-1", dto.id)
        assertEquals("cat-1", dto.catalogId)
        assertEquals("author-1", dto.authorUid)
        assertEquals("Столица Франции", dto.title)
        assertEquals("quests/quest-1.png", dto.picturePath)
        assertEquals(listOf("arena", "home"), dto.visibleOn)
        assertEquals(4.5, dto.averageRating!!, 0.0001)
        assertEquals(12, dto.averageRatingCount)
        assertEquals(7L, dto.version)
        assertEquals(1_700_000_000_000L, dto.lastModifiedAt)
        assertFalse(dto.archived)
    }

    @Test
    fun `section snapshot carrying contentsVersion parses every other field unchanged`() {
        val snapshot = snapshotOf(
            "section-1",
            mapOf(
                legacyField,
                "questId" to "quest-1",
                "title" to "Раздел",
                "order" to 3L,
                "version" to 2L,
                "lastModifiedAt" to 1_700_000_000_000L,
                "archived" to false,
            ),
        )

        val dto = snapshot.toSectionDto()

        assertEquals("section-1", dto.id)
        assertEquals("quest-1", dto.questId)
        assertEquals("Раздел", dto.title)
        assertEquals(3, dto.order)
        assertEquals(2L, dto.version)
        assertEquals(1_700_000_000_000L, dto.lastModifiedAt)
        assertFalse(dto.archived)
    }

    @Test
    fun `theme snapshot carrying contentsVersion parses every other field unchanged`() {
        val snapshot = snapshotOf(
            "theme-1",
            mapOf(
                legacyField,
                "sectionId" to "section-1",
                "title" to "Тема",
                "order" to 1L,
                "version" to 5L,
                "lastModifiedAt" to 1_700_000_000_000L,
                "archived" to true,
            ),
        )

        val dto = snapshot.toThemeDto()

        assertEquals("theme-1", dto.id)
        assertEquals("section-1", dto.sectionId)
        assertEquals("Тема", dto.title)
        assertEquals(1, dto.order)
        assertEquals(5L, dto.version)
        assertEquals(1_700_000_000_000L, dto.lastModifiedAt)
        assertEquals(true, dto.archived)
    }

    @Test
    fun `lesson snapshot carrying contentsVersion parses every other field unchanged`() {
        val snapshot = snapshotOf(
            "lesson-1",
            mapOf(
                legacyField,
                "themeId" to "theme-1",
                "title" to "Урок",
                "order" to 0L,
                "version" to 1L,
                "lastModifiedAt" to 1_700_000_000_000L,
                "archived" to false,
            ),
        )

        val dto = snapshot.toLessonDto()

        assertEquals("lesson-1", dto.id)
        assertEquals("theme-1", dto.themeId)
        assertEquals("Урок", dto.title)
        assertEquals(0, dto.order)
        assertEquals(1L, dto.version)
        assertEquals(1_700_000_000_000L, dto.lastModifiedAt)
        assertFalse(dto.archived)
    }

    @Test
    fun `a snapshot with the legacy field parses identically to one without it`() {
        val withoutLegacy = mapOf(
            "questId" to "quest-1",
            "title" to "Раздел",
            "order" to 3L,
            "version" to 2L,
            "lastModifiedAt" to 1_700_000_000_000L,
            "archived" to false,
        )

        val plain = snapshotOf("section-1", withoutLegacy).toSectionDto()
        val carrying = snapshotOf("section-1", withoutLegacy + legacyField).toSectionDto()

        assertEquals(plain, carrying)
    }
}
