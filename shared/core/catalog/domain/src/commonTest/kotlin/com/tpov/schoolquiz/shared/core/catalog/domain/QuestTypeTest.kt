package com.tpov.schoolquiz.shared.core.catalog.domain

import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.model.QuestType
import kotlin.test.Test
import kotlin.test.assertEquals

class QuestTypeTest {

    @Test
    fun `stored names round-trip`() {
        for (type in QuestType.entries) {
            assertEquals(type, QuestType.fromStorage(type.name))
        }
    }

    @Test
    fun `unknown value degrades to regular instead of failing`() {
        // Content written by a newer client must still be readable by an older one.
        assertEquals(QuestType.REGULAR, QuestType.fromStorage("HOMEWORK"))
        assertEquals(QuestType.REGULAR, QuestType.fromStorage(""))
        assertEquals(QuestType.REGULAR, QuestType.fromStorage(null))
    }

    @Test
    fun `parsing ignores case`() {
        assertEquals(QuestType.COURSE, QuestType.fromStorage("course"))
        assertEquals(QuestType.SURVEY, QuestType.fromStorage("Survey"))
    }

    @Test
    fun `catalogs are regular unless told otherwise`() {
        // Every catalog that existed before types were introduced behaves exactly as before.
        val catalog = Catalog(id = CatalogId("games"), name = "Игры", picturePath = null)
        assertEquals(QuestType.REGULAR, catalog.questType)
    }
}
