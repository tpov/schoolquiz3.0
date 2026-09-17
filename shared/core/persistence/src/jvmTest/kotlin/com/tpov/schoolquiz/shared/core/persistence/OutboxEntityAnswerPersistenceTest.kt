package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.OutboxDecision
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Ответ про сущность против настоящего SQLite (история 4.4).
 *
 * Три вещи, каждая из которых на фейке не проверяется: порядок выборки задан текстом запроса,
 * отсутствие строки превращается в явное значение в маппинге, а отвергнутая постановка не должна
 * оставить строки — это видно только по таблице.
 */
class OutboxEntityAnswerPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var store: RoomOutboxStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
        store = RoomOutboxStore(db.outboxDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun anEmptyAnswerIsAValueOfItsOwnAndNotEmptiness() = runTest {
        val answer = store.observeEntity(OWNER, "lesson_runner:attempt:absent").first()

        assertEquals(OutboxEntitySync.None, answer)
    }

    @Test
    fun anotherOwnersRecordIsNotMyAnswer() = runTest {
        store.enqueue(record("m-1", owner = "someone-else"))

        val answer = store.observeEntity(OWNER, REF).first()

        assertEquals("чужая очередь под моим аккаунтом не читается (AD-8)", OutboxEntitySync.None, answer)
    }

    @Test
    fun quarantineOutweighsWaitingEvenWhenTheWaitingRecordIsNewer() = runTest {
        val quarantined = store.enqueue(record("m-old", createdAtMs = 1_000L))
        store.apply(
            quarantined.id,
            OutboxDecision(
                state = OutboxState.QUARANTINED,
                nextRetryAtMs = 0L,
                attemptCount = 5,
                lastError = "Refused",
            ),
        )
        store.enqueue(record("m-new", createdAtMs = 9_000L))

        val answer = store.observeEntity(OWNER, REF).first()

        assertTrue(answer is OutboxEntitySync.Queued)
        answer as OutboxEntitySync.Queued
        assertEquals("терминальное перевешивает ожидание", OutboxState.QUARANTINED, answer.state)
        assertEquals(quarantined.id, answer.recordId)
    }

    @Test
    fun aWaitingRecordIsAnsweredAsWaiting() = runTest {
        store.enqueue(record("m-1"))

        val answer = store.observeEntity(OWNER, REF).first()

        assertTrue(answer is OutboxEntitySync.Queued)
        assertEquals(OutboxState.WAITING, (answer as OutboxEntitySync.Queued).state)
    }

    @Test
    fun aReferenceFromAnotherFeatureIsRefusedAndLeavesNoRow() = runTest {
        val failure =
            runCatching {
                store.enqueue(record("m-foreign", entityRef = "quest.RATING:7"))
            }.exceptionOrNull()

        assertTrue("постановка отвергается исключением", failure is IllegalArgumentException)
        val message = failure!!.message.orEmpty()
        assertTrue("в тексте оба имени: $message", message.contains("quest") && message.contains("lesson_runner"))
        assertEquals("и строки в таблице нет", null, db.outboxDao().findByMutationId("m-foreign"))
    }

    private fun record(
        mutationId: String,
        owner: String = OWNER,
        createdAtMs: Long = 1_000L,
        entityRef: String? = REF,
    ) = OutboxRecord(
        id = 0L,
        mutationId = mutationId,
        ownerUid = owner,
        operation = OutboxOperations.SUBMIT_ATTEMPT,
        payload = """{"attemptId":"a1"}""",
        state = OutboxState.WAITING,
        createdAtMs = createdAtMs,
        entityRef = entityRef,
    )

    private companion object {
        const val OWNER = "uid-1"
        const val REF = "lesson_runner:attempt:a1"
    }
}
