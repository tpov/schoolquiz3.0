package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.feature.economy.data.remote.OwnHistoryRemoteDataSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertTrue

/**
 * Проекция собственной истории (AD-30/UX-20, история 8.6): pending — не доход, ресурсы не
 * суммируются, смена аккаунта отсекает ответ, чтение эффекта не создаёт.
 */
class OwnHistoryRepositoryTest {

    private class FakeRemote(
        private val reply: Any?,
    ) : OwnHistoryRemoteDataSource {
        var calls = 0

        override suspend fun historyPage(
            cursor: String?,
            pageSize: Int,
        ): Any? {
            calls += 1
            return reply
        }

        override suspend fun lessonEarningsPage(
            lessonId: String,
            cursor: String?,
            pageSize: Int,
        ): Any? = reply
    }

    private val reply =
        mapOf(
            "entries" to
                listOf(
                    mapOf(
                        "id" to "row-2", "atMs" to 2_000L, "reason" to "attempt_toll",
                        "currency" to "standardChargePoints", "delta" to -2, "status" to "CONFIRMED",
                        "lessonId" to null,
                    ),
                    mapOf(
                        "id" to "row-1", "atMs" to 2_000L, "reason" to "attempt_reward",
                        "currency" to "nolics", "delta" to 12, "status" to "CONFIRMED",
                        "lessonId" to "lesson-1",
                    ),
                ),
            "pending" to
                listOf(
                    mapOf("ticketId" to "ticket-1", "atMs" to 1_000L, "currency" to "nolics", "heldAmount" to 7L),
                ),
            "nextCursor" to null,
        )

    @Test
    fun page_parsesEntriesAndPending_withoutSummingResources() = runTest {
        var session: String? = "uid-1"
        val repository = OwnHistoryRepositoryImpl(FakeRemote(reply)) { session }
        val page = repository.historyPage("uid-1", cursor = null).getOrThrow()

        assertEquals(2, page.entries.size)
        assertEquals(12L, page.entries[1].delta, "нолики построчно")
        assertEquals(-2L, page.entries[0].delta, "заряды построчно: разные ресурсы не складываются")
        assertEquals(true, page.entries[1].isIncome)
        assertEquals(false, page.entries[0].isIncome)
        // Pending едет отдельно: клиент никогда не включит его в подтверждённый доход.
        assertEquals(1, page.pending.size)
        assertEquals(7L, page.pending[0].heldAmount)
        assertNull(page.nextCursor)
    }

    @Test
    fun sessionChange_cutsOffTheLateResponse() = runTest {
        // A→B: страница запрошена под A, а сессия уже B — ответ A не рендерится и не кэшируется.
        val remote = FakeRemote(reply)
        val repository = OwnHistoryRepositoryImpl(remote) { "uid-2" }
        val result = repository.historyPage("uid-1", cursor = null)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("session changed"))
        assertEquals(0, remote.calls, "отсечённый ответ до транспорта и разбора не доходит")
    }

    @Test
    fun sessionChange_duringTheCall_cutsOffBeforeParsing() = runTest {
        // Сессия меняется, ПОКА транспорт отвечал: ответ A разобран не будет — ограда
        // отрабатывает и после вызова (AD-30/AD-8), до OwnHistoryWireParser.
        var calls = 0
        val repository =
            OwnHistoryRepositoryImpl(
                remote = FakeRemote(reply),
                currentUid = {
                    calls += 1
                    if (calls <= 1) "uid-1" else "uid-2"
                },
            )
        val result = repository.historyPage("uid-1", cursor = null)
        assertTrue(result.isFailure, "ответ, приехавший под чужой сессией, не рендерится")
        assertTrue(result.exceptionOrNull()!!.message!!.contains("session changed"))
    }

    @Test
    fun blankOwner_refuses_withoutCallingTheTransport() = runTest {
        val remote = FakeRemote(reply)
        val repository = OwnHistoryRepositoryImpl(remote) { "uid-1" }
        val result = repository.historyPage("", cursor = null)
        assertTrue(result.isFailure)
        assertEquals(0, remote.calls, "пустой владелец до транспорта не доходит")
    }

}
