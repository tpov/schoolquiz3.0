package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Переписывание ключей против настоящего SQLite (ретро эпика 8, находка 4).
 *
 * Механика проверяется на базе, а не на памяти: условие «только ожидающие», пропуск занятого
 * целевого ключа у уникального индекса и защищённый апдейт — свойства запросов, и именно они
 * здесь под нагрузкой. Правило фичи подставляется тестовое: ядру важна арифметика над его
 * ответами, а не опросная формула.
 */
class OutboxKeyRepairPersistenceTest {

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
    fun pendingRecordsOfTheOperationGetTheNewKeyAndSurviveWhole() = runTest {
        val old = store.enqueue(voteRecord(key = OLD_KEY, state = OutboxState.WAITING))
        val precondition = store.enqueue(
            voteRecord(key = "polls-SUBMIT_VOTE-series-s2-r1-q3", state = OutboxState.WAITING_PRECONDITION),
        )

        val rewritten = store.rewritePendingKeys(OutboxOperations.SUBMIT_POLL_VOTE, ::repairRule)

        assertEquals(2, rewritten)
        val moved = db.outboxDao().findByMutationId("$OLD_KEY-u-$OWNER_UID")
        assertNotNull(moved)
        assertEquals(old.id, moved!!.id)
        assertEquals("тело не тронуто", VOTE_PAYLOAD, moved.payload)
        assertEquals("ряд остаётся ожидающим", OutboxState.WAITING.name, moved.state)
        assertEquals("момент намерения не меняется", 1_000L, moved.createdAtMs)
        val second = db.outboxDao().findByMutationId("polls-SUBMIT_VOTE-series-s2-r1-q3-u-$OWNER_UID")
        assertEquals(precondition.id, second!!.id)

        // Повторный проход: правило отвечает null на новую форму, переписывать нечего.
        assertEquals(0, store.rewritePendingKeys(OutboxOperations.SUBMIT_POLL_VOTE, ::repairRule))
    }

    @Test
    fun quarantinedRecordsAndOtherOperationsAreNotTouched() = runTest {
        val quarantined = store.enqueue(voteRecord(key = OLD_KEY, state = OutboxState.QUARANTINED))
        val rating = store.enqueue(
            OutboxRecord(
                id = 0L,
                mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_RATING, "rating-1"),
                ownerUid = OWNER_UID,
                operation = OutboxOperations.SUBMIT_RATING,
                payload = """{"ratingId":"rating-1"}""",
                state = OutboxState.WAITING,
                createdAtMs = 1_000L,
                entityRef = null,
            ),
        )

        val rewritten = store.rewritePendingKeys(OutboxOperations.SUBMIT_POLL_VOTE, ::repairRule)

        assertEquals(0, rewritten)
        assertNotNull("карантин не вскрывается (AD-28)", db.outboxDao().findByMutationId(OLD_KEY))
        assertEquals(quarantined.id, db.outboxDao().findById(quarantined.id)!!.id)
        assertNotNull("чужая операция не тронута", db.outboxDao().findByMutationId(rating.mutationId))
    }

    @Test
    fun aTargetKeyAlreadyInUseIsSkippedInsteadOfFailingThePass() = runTest {
        val old = store.enqueue(voteRecord(key = OLD_KEY, state = OutboxState.WAITING))
        // Патология: целевой ключ уже занят другой строкой того же владельца. Уникальный индекс
        // обязан отвалить одну строку, а не весь проход — правила AD-2: один ключ — одно действие.
        db.outboxDao().insert(
            OutboxRecord(
                id = 0L,
                mutationId = "$OLD_KEY-u-$OWNER_UID",
                ownerUid = OWNER_UID,
                operation = OutboxOperations.SUBMIT_POLL_VOTE,
                payload = VOTE_PAYLOAD,
                state = OutboxState.WAITING,
                createdAtMs = 2_000L,
                entityRef = SERIES_REF,
            ).toOutboxEntity(),
        )

        val rewritten = store.rewritePendingKeys(OutboxOperations.SUBMIT_POLL_VOTE, ::repairRule)

        assertEquals(0, rewritten)
        assertEquals("старая строка осталась с прежним ключом", old.id, db.outboxDao().findByMutationId(OLD_KEY)!!.id)
        assertEquals("занявшая строка на месте", 2_000L, db.outboxDao().findByMutationId("$OLD_KEY-u-$OWNER_UID")!!.createdAtMs)
    }

    private suspend fun voteRecord(
        key: String,
        state: OutboxState,
    ) = OutboxRecord(
        id = 0L,
        mutationId = key,
        ownerUid = OWNER_UID,
        operation = OutboxOperations.SUBMIT_POLL_VOTE,
        payload = VOTE_PAYLOAD,
        state = state,
        createdAtMs = 1_000L,
        entityRef = SERIES_REF,
    )

    private companion object {
        const val OWNER_UID = "voter-1"
        const val OLD_KEY = "polls-SUBMIT_VOTE-series-s1-r1-q0"
        const val SERIES_REF = "polls.SERIES:s1"
        const val VOTE_PAYLOAD = """{"seriesId":"s1","revisionId":1,"questionIndex":0}"""

        /**
         * Тестовое правило в форме настоящего (PollVoteKeyRepair.migratedKey): новая форма
         * получает `null` — наивное «всегда дописать» переписывало бы строку и на втором
         * проходе, и у строки-оккупанта, вопреки собственным утверждениям тестов ниже.
         */
        fun repairRule(
            ownerUid: String,
            mutationId: String,
        ): String? =
            if (mutationId.endsWith("-u-$ownerUid")) null else "$mutationId-u-$ownerUid"
    }
}
