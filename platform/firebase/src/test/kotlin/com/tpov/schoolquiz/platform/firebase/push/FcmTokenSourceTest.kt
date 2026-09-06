package com.tpov.schoolquiz.platform.firebase.push

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FcmTokenSourceTest {
    @Test
    fun `first observation asks the SDK once and later tokens come from onNewToken`() =
        runTest {
            var fetches = 0
            val source =
                FcmTokenSource {
                    fetches++
                    "t-1"
                }

            assertEquals("t-1", source.observeToken().first())
            source.publish("t-2")

            assertEquals("t-2", source.currentToken())
            assertEquals(listOf("t-2"), source.observeToken().take(1).toList())
            assertEquals(1, fetches)
        }

    @Test
    fun `sdk failure yields no token and does not throw`() =
        runTest {
            val source = FcmTokenSource { error("no play services") }

            assertNull(source.currentToken())
        }
}
