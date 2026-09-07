package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutboxArenaSessionProviderTest {
    @Test
    fun constructionIsLazyAndNullStaysNull() {
        val source = Sessions(null)
        val provider = OutboxArenaSessionProvider(source)
        assertEquals(0, source.reads)
        assertNull(provider.current())
        assertEquals(1, source.reads)
    }

    @Test
    fun projectsEveryFieldWithoutInventingAnEpoch() {
        val source = Sessions(OutboxSession("owner-a", 17))
        val provider = OutboxArenaSessionProvider(source)
        val stamp = requireNotNull(provider.current())
        assertEquals("owner-a", stamp.ownerUid)
        assertEquals(17L, stamp.generation)
        assertEquals(stamp, provider.current())
        assertEquals(2, source.reads)
    }

    @Test
    fun sameUidAfterAccountRoundTripHasTheActualNewEpoch() {
        val source = Sessions(OutboxSession("owner-a", 4))
        val provider = OutboxArenaSessionProvider(source)
        assertEquals(4L, provider.current()?.generation)
        source.value = OutboxSession("owner-b", 5)
        assertEquals("owner-b", provider.current()?.ownerUid)
        source.value = OutboxSession("owner-a", 6)
        val returned = requireNotNull(provider.current())
        assertEquals("owner-a", returned.ownerUid)
        assertEquals(6L, returned.generation)
        source.value = null
        assertNull(provider.current())
    }

    private class Sessions(var value: OutboxSession?) : OutboxSessionProvider {
        var reads = 0

        override fun current(): OutboxSession? {
            reads++
            return value
        }
    }
}
