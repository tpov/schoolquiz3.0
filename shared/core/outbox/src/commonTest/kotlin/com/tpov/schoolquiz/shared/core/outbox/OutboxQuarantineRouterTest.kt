package com.tpov.schoolquiz.shared.core.outbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * AD-28 запрещает молчаливое расхождение: движок увёл запись в карантин, а локальное изменение,
 * сделанное вместе с ней одной транзакцией, осталось. Поэтому важно не только то, что известная
 * операция попадает своему обработчику, но и что неизвестная не проваливается в тишину.
 */
class OutboxQuarantineRouterTest {

    private fun record(operation: String) =
        OutboxRecord(
            id = 1L,
            mutationId = "m-1",
            ownerUid = "uid",
            operation = operation,
            payload = "{}",
            state = OutboxState.QUARANTINED,
            createdAtMs = 0L,
        )

    @Test
    fun `given a known operation then its own handler is told`() = runTest {
        val told = mutableListOf<String>()
        val unhandled = mutableListOf<String>()
        val router =
            OutboxQuarantineRouter(
                handlers = mapOf("UNLOCK_LESSON" to QuarantineListener { told += it.operation }),
                onUnhandled = QuarantineListener { unhandled += it.operation },
            )

        router.onQuarantined(record("UNLOCK_LESSON"))

        assertEquals(listOf("UNLOCK_LESSON"), told)
        assertTrue(unhandled.isEmpty())
    }

    @Test
    fun `given an operation nobody claims then it is surfaced, not swallowed`() = runTest {
        // Самый важный случай: забытый обработчик обязан быть громким, а не тихим.
        val unhandled = mutableListOf<String>()
        val router =
            OutboxQuarantineRouter(
                handlers = emptyMap(),
                onUnhandled = QuarantineListener { unhandled += it.operation },
            )

        router.onQuarantined(record("SOMETHING_NEW"))

        assertEquals(listOf("SOMETHING_NEW"), unhandled)
    }

    @Test
    fun `given an operation with no local half then saying so is explicit, not silence`() = runTest {
        // «Откатывать нечего» и «забыли написать» не должны выглядеть одинаково.
        var notified: OutboxRecord? = null
        val router =
            OutboxQuarantineRouter(
                handlers = mapOf("UNLOCK_LESSON" to NoLocalEffect { notified = it }),
                onUnhandled = QuarantineListener { throw IllegalStateException("не должно вызываться") },
            )

        router.onQuarantined(record("UNLOCK_LESSON"))

        assertEquals("UNLOCK_LESSON", notified?.operation)
    }
}
