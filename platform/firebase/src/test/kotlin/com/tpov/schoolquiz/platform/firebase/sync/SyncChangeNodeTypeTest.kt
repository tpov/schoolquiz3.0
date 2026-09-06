package com.tpov.schoolquiz.platform.firebase.sync

import com.tpov.schoolquiz.shared.core.sync.CatalogSyncNodeType
import com.tpov.schoolquiz.shared.core.sync.JournalReadPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Терпимость к прежней форме типа узла снимается одним переключателем — тем же, что у остальных
 * трёх журналов (AD-11). До backfill множественное число разбирается, после — становится непонятой
 * записью, и прогон на ней останавливается, а не проходит мимо.
 */
class SyncChangeNodeTypeTest {

    @Test
    fun `given tolerant reading then the plural form is still understood`() {
        assertEquals(CatalogSyncNodeType.Quest, "quests".toSyncNodeType(JournalReadPolicy.TOLERANT))
        assertEquals(CatalogSyncNodeType.Question, "questions".toSyncNodeType(JournalReadPolicy.TOLERANT))
        assertEquals(CatalogSyncNodeType.Catalog, "Catalogs ".toSyncNodeType(JournalReadPolicy.TOLERANT))
    }

    @Test
    fun `given strict reading then the plural form is not understood any more`() {
        assertNull("quests".toSyncNodeType(JournalReadPolicy.STRICT))
        assertNull("questions".toSyncNodeType(JournalReadPolicy.STRICT))
        assertNull("catalogs".toSyncNodeType(JournalReadPolicy.STRICT))
    }

    @Test
    fun `given strict reading then the current form is still understood`() {
        assertEquals(CatalogSyncNodeType.Quest, "quest".toSyncNodeType(JournalReadPolicy.STRICT))
        assertEquals(CatalogSyncNodeType.Lesson, " Lesson ".toSyncNodeType(JournalReadPolicy.STRICT))
    }

    @Test
    fun `given a type nobody knows then it is unreadable in both modes`() {
        assertNull("шестигранник".toSyncNodeType(JournalReadPolicy.TOLERANT))
        assertNull(null.toSyncNodeType(JournalReadPolicy.TOLERANT))
        assertNull("шестигранник".toSyncNodeType(JournalReadPolicy.STRICT))
    }
}
