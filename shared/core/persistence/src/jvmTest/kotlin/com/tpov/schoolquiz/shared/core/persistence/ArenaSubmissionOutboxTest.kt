package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Подача черновика на арену — против настоящего SQLite.
 *
 * Подача делает две вещи сразу: ставит заявку в очередь и запирает черновик на редактирование,
 * пометив его поданным. Порознь любая из них — потеря: заявка без пометки уедет дважды, пометка
 * без заявки запрёт черновик навсегда. Второе здесь дороже: рецензент такую заявку никогда не
 * увидит, а автор не сможет ни отредактировать черновик, ни подать его снова.
 */
class ArenaSubmissionOutboxTest {

    private lateinit var db: AppDatabase
    private lateinit var authoring: QuestAuthoringDao
    private lateinit var outbox: OutboxDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
        authoring = db.questAuthoringDao()
        outbox = db.outboxDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun submittingADraftQueuesItAndLocksIt() = runTest {
        authoring.insertDraft(draft(status = "DRAFT"))

        authoring.queueArenaSubmission(row("sub-1"), DRAFT_ID, updatedAtMs = 2_000L)

        assertNotNull("заявка не встала в очередь", outbox.findByMutationId(key("sub-1")))
        assertEquals("REVIEW_QUEUED", authoring.findDraftById(DRAFT_ID)!!.status)
    }

    /**
     * Ключ, занятый карантинной записью, обязан быть слышен.
     *
     * `IGNORE` отвечает на такую вставку теми же −1, что и на честный повтор намерения, и раньше
     * этот ответ никто не смотрел. Черновик при этом помечался поданным — и запирался навсегда:
     * заявки, которая его откроет, не существовало, а карантинная запись никуда не поедет.
     */
    @Test
    fun aKeyHeldByAQuarantinedRowLeavesTheDraftEditable() = runTest {
        authoring.insertDraft(draft(status = "DRAFT"))
        authoring.queueArenaSubmission(row("sub-1"), DRAFT_ID, updatedAtMs = 2_000L)
        val stuck = outbox.findByMutationId(key("sub-1"))!!
        outbox.applyDecision(stuck.id, OutboxState.QUARANTINED.name, 0L, 5, "Refused by server")

        // Откат по карантину: черновик снова редактируемый, автор может подать его заново.
        authoring.updateDraftStatus(DRAFT_ID, status = "DRAFT", updatedAtMs = 2_500L)

        // Подача под тем же ключом — то есть тем же намерением, которое уже не уедет.
        val failure = runCatching { authoring.queueArenaSubmission(row("sub-1"), DRAFT_ID, updatedAtMs = 3_000L) }

        assertTrue("занятый терминальной записью ключ обязан быть слышен", failure.isFailure)
        assertEquals(
            "черновик не должен запереться заявкой, которой не существует",
            "DRAFT",
            authoring.findDraftById(DRAFT_ID)!!.status,
        )
        assertEquals("попытки карантинной записи не обнулились", 5, outbox.findByMutationId(key("sub-1"))!!.attemptCount)
    }

    /** Пара к предыдущему: новое намерение — новый ключ, и оно проходит. */
    @Test
    fun aFreshIntentAfterQuarantineGoesThrough() = runTest {
        authoring.insertDraft(draft(status = "DRAFT"))
        authoring.queueArenaSubmission(row("sub-1"), DRAFT_ID, updatedAtMs = 2_000L)
        val stuck = outbox.findByMutationId(key("sub-1"))!!
        outbox.applyDecision(stuck.id, OutboxState.QUARANTINED.name, 0L, 5, "Refused by server")
        authoring.updateDraftStatus(DRAFT_ID, status = "DRAFT", updatedAtMs = 2_500L)

        authoring.queueArenaSubmission(row("sub-2"), DRAFT_ID, updatedAtMs = 3_000L)

        assertEquals(OutboxState.WAITING.name, outbox.findByMutationId(key("sub-2"))!!.state)
        assertEquals("REVIEW_QUEUED", authoring.findDraftById(DRAFT_ID)!!.status)
    }

    @Test
    fun theSameIntentOfferedTwiceQueuesOnce() = runTest {
        authoring.insertDraft(draft(status = "DRAFT"))

        // Переигранная транзакция, а не второе намерение: тот же ключ.
        authoring.queueArenaSubmission(row("sub-1"), DRAFT_ID, updatedAtMs = 2_000L)
        authoring.queueArenaSubmission(row("sub-1"), DRAFT_ID, updatedAtMs = 2_000L)

        assertEquals(1, outbox.countPending(OWNER_UID))
    }

    private fun draft(status: String) =
        QuestDraftEntity(
            id = DRAFT_ID,
            ownerUid = OWNER_UID,
            catalogId = "catalog-1",
            title = "Черновик",
            description = null,
            defaultLanguage = "ru",
            defaultDifficulty = "EASY",
            status = status,
            localRevision = 1L,
            serverRevision = null,
            publicQuestId = null,
            createdAtMs = 1_000L,
            updatedAtMs = 1_000L,
            isActive = true,
        )

    private fun key(submissionId: String) = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ARENA, submissionId)

    private fun row(submissionId: String) =
        OutboxEntity(
            mutationId = key(submissionId),
            ownerUid = OWNER_UID,
            operation = OutboxOperations.SUBMIT_ARENA,
            payload = """{"submissionId":"$submissionId","draftId":"$DRAFT_ID"}""",
            entityRef = "quest_authoring:draft:$DRAFT_ID",
            expectedVersion = null,
            state = OutboxState.WAITING.name,
            attemptCount = 0,
            nextRetryAtMs = 0L,
            lastError = null,
            createdAtMs = 1_000L,
        )

    private companion object {
        const val OWNER_UID = "user-1"
        const val DRAFT_ID = "draft-1"
    }
}
