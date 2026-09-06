package com.tpov.schoolquiz.platform.firebase.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Имена событий живут в пространстве имён очереди (NFR4). */
class PushEventsTest {
    @Test
    fun `every event is feature dot UPPER_EVENT within a known feature`() {
        val shape = Regex("""^[a-z_]+\.[A-Z_]+$""")
        PushEvents.all.forEach { event ->
            assertTrue(event.eventType, shape.matches(event.eventType))
            assertTrue("${event.feature} не фича проекта", event.feature in PushEvents.knownFeatures)
        }
    }

    @Test
    fun `string keys are derived from the event type`() {
        assertEquals("push_quest_authoring_quest_published", PushEvents.QUEST_PUBLISHED.stringKey)
        assertEquals("push_quest_authoring_quest_published_title", PushEvents.QUEST_PUBLISHED.titleKey)
        assertEquals("push_quest_authoring_quest_published_text", PushEvents.QUEST_PUBLISHED.textKey)
    }

    @Test
    fun `lookup by type is exact`() {
        assertEquals(PushEvents.AUTHOR_RATING_CHANGED, PushEvents.byType("qualification.AUTHOR_RATING_CHANGED"))
        assertNull(PushEvents.byType("qualification.author_rating_changed"))
        assertNull(PushEvents.byType(null))
        assertNull(PushEvents.byType(""))
    }

    @Test
    fun `event types are unique`() {
        assertEquals(PushEvents.all.size, PushEvents.all.map { it.eventType }.toSet().size)
    }
}
