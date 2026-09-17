package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Счётчики против настоящего SQLite (AD-14).
 *
 * До сих пор они проверялись только на фейках — то есть проверялось, что Kotlin умеет считать
 * элементы списка. Ни `SUM(state = …)`, ни обнуление `NULL → 0` на пустой таблице не выполнялись
 * ни разу, а именно они и стоят между игроком и числом на экране.
 *
 * Записи кладутся прямо в таблицу, в нужном состоянии: счётчики определены над набором состояний,
 * а не над тем, кто в него пишет.
 */
class OutboxCountsPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: OutboxDao
    private lateinit var store: RoomOutboxStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
        dao = db.outboxDao()
        store = RoomOutboxStore(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun eachStateIsCountedOnItsOwnAndTheSynchronousKeyStaysOutOfSight() = runTest {
        OutboxState.entries.forEach { state -> dao.insert(row("m-${state.name}", OWNER, state)) }

        val counts = store.observeCounts(OWNER).first()

        assertEquals("ждущих", 1, counts.waiting)
        assertEquals("ждущих предусловия", 1, counts.waitingPrecondition)
        assertEquals("конфликтных", 1, counts.conflicted)
        assertEquals("карантинных", 1, counts.quarantined)
        // Пятое состояние — ключ синхронной мутации (AD-2). Наружу оно не выходит: игроку нечего с
        // ним делать, а попав в любой из четырёх счётчиков, оно сделало бы число неверным.
        assertEquals(
            "четыре числа и ни одного лишнего",
            4,
            counts.waiting + counts.waitingPrecondition + counts.conflicted + counts.quarantined,
        )
    }

    @Test
    fun anEmptyQueueEmitsFourZerosRatherThanNothing() = runTest {
        val counts = store.observeCounts(OWNER).first()

        assertEquals(0, counts.waiting)
        assertEquals(0, counts.waitingPrecondition)
        assertEquals(0, counts.conflicted)
        assertEquals(0, counts.quarantined)
    }

    @Test
    fun anotherOwnersRowsAreNotCounted() = runTest {
        dao.insert(row("m-mine", OWNER, OutboxState.WAITING))
        dao.insert(row("m-theirs-1", "other-uid", OutboxState.WAITING))
        dao.insert(row("m-theirs-2", "other-uid", OutboxState.QUARANTINED))

        val counts = store.observeCounts(OWNER).first()

        assertEquals(1, counts.waiting)
        assertEquals("чужой карантин на моём экране не показывается (AD-8)", 0, counts.quarantined)
    }

    @Test
    fun theCountsFollowTheStateChangeInTheTable() = runTest {
        val queued = dao.insert(row("m-1", OWNER, OutboxState.WAITING))
        val counts = store.observeCounts(OWNER)

        assertEquals(1, counts.first().waiting)

        dao.applyDecision(
            id = queued,
            state = OutboxState.QUARANTINED.name,
            nextRetryAtMs = 0L,
            attemptCount = 5,
            lastError = "Refused",
            serverVersion = null,
        )

        val after = counts.first()
        assertEquals(0, after.waiting)
        assertEquals(1, after.quarantined)
    }

    private fun row(
        mutationId: String,
        ownerUid: String,
        state: OutboxState,
    ) = OutboxEntity(
        mutationId = mutationId,
        ownerUid = ownerUid,
        operation = "lesson_runner.SUBMIT_ATTEMPT",
        payload = "{}",
        entityRef = "lesson_runner:attempt:$mutationId",
        expectedVersion = null,
        state = state.name,
        attemptCount = 0,
        nextRetryAtMs = 0L,
        lastError = null,
        createdAtMs = 1_000L,
    )

    private companion object {
        const val OWNER = "uid-1"
    }
}
