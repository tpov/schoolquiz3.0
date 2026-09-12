package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.persistence.DraftLessonEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftQuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftSectionEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftThemeEntity
import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.outbox.OutboxDecision
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftSummaryEntity
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLesson
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionValidationState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftSection
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftSectionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftTheme
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftThemeId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmissionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraft
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuestAuthoringRepositoryImplTest {

    @Test
    fun observeDraftSummaries_mapsLocalProjectionToDomain() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        val repository = repository(local)
        local.summaries.value = listOf(
            QuestDraftSummaryEntity(
                id = "draft-1",
                catalogId = "catalog-1",
                title = "My draft",
                status = "SAVED",
                questionCount = 2,
                updatedAtMs = 42L,
                isActive = true,
            ),
        )

        val result = repository.observeDraftSummaries("owner-1").first()

        assertEquals(1, result.size)
        val summary = result.first()
        assertEquals(QuestDraftId("draft-1"), summary.id)
        assertEquals(CatalogId("catalog-1"), summary.catalogId)
        assertEquals("My draft", summary.title)
        assertEquals(QuestDraftStatus.SAVED, summary.status)
        assertEquals(2, summary.questionCount)
        assertEquals(42L, summary.updatedAtMs)
        assertTrue(summary.isActive)
        assertNull(summary.rejectionReason, "у неотклонённого черновика причины нет")
    }

    /**
     * Причина возврата обязана доехать до сводки: список «Мои квесты» — единственное место, где
     * автор вообще может её увидеть. Поле хранилось и доходило до модели черновика, но в сводку
     * не попадало, и автор не мог отличить свою недоделку от отклонённой заявки.
     */
    @Test
    fun observeDraftSummaries_carriesRejectionReason() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        val repository = repository(local)
        local.summaries.value = listOf(
            QuestDraftSummaryEntity(
                id = "draft-2",
                catalogId = "catalog-1",
                title = "Returned draft",
                status = "DRAFT",
                questionCount = 2,
                updatedAtMs = 43L,
                isActive = false,
                rejectionReason = "Слишком мало вопросов",
            ),
        )

        val summary = repository.observeDraftSummaries("owner-1").first().single()

        assertEquals("Слишком мало вопросов", summary.rejectionReason)
        assertEquals(QuestDraftStatus.DRAFT, summary.status)
    }

    @Test
    fun saveDraft_mapsDomainBundleToEntities() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        val repository = repository(local)

        val result = repository.saveDraft(domainBundle())

        assertTrue(result.isSuccess)
        val saved = assertNotNull(local.savedBundle)
        assertEquals("draft-1", saved.draft.id)
        assertEquals("owner-1", saved.draft.ownerUid)
        assertEquals("EASY", saved.draft.defaultDifficulty)
        assertEquals("section-1", saved.sections.single().id)
        assertEquals("theme-1", saved.themes.single().id)
        assertEquals("lesson-1", saved.lessons.single().id)
        assertEquals("question-1", saved.questions.single().id)
    }

    @Test
    fun getActiveDraft_mapsEntityBundleToDomain() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        val repository = repository(local)
        local.activeDraft = entityBundle()

        val result = repository.getActiveDraft("owner-1")

        assertNotNull(result)
        assertEquals(QuestDraftId("draft-1"), result.draft.id)
        assertEquals(DraftLessonId("lesson-1"), result.lessons.single().id)
        assertEquals(DraftQuestionType.SINGLE_CHOICE, result.questions.single().type)
        assertEquals(Difficulty.EASY, result.questions.single().difficulty)
    }

    @Test
    fun upsertQuestion_mapsDomainQuestionToEntity() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        val repository = repository(local)

        val result = repository.upsertQuestion(domainQuestion())

        assertTrue(result.isSuccess)
        val question = assertNotNull(local.upsertedQuestion)
        assertEquals("question-1", question.id)
        assertEquals("SINGLE_CHOICE", question.type)
        assertEquals("EASY", question.difficulty)
        assertEquals("SAVED", question.validationState)
    }

    @Test
    fun queueArenaSubmission_putsWholeRequestIntoSharedOutbox() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        local.draft = entityBundle()
        val repository = repository(local)

        val result = repository.queueArenaSubmission(domainSubmission())

        assertTrue(result.isSuccess)
        val row = assertNotNull(local.queuedSubmission)
        assertEquals(OutboxOperations.SUBMIT_ARENA, row.operation)
        // Ключ выведен из идентификатора заявки — той же строкой, что даёт миграция 5 → 6.
        assertEquals("quest_authoring-SUBMIT_ARENA-submission-1", row.mutationId)
        assertEquals("owner-1", row.ownerUid)
        assertEquals("quest_authoring:draft:draft-1", row.entityRef)
        assertEquals("WAITING", row.state)
        assertEquals(4L, row.createdAtMs)
        // Тело собрано в момент намерения и несёт содержимое черновика целиком (AD-2).
        assertTrue(row.payload.contains("\"submissionId\":\"submission-1\""))
        assertTrue(row.payload.contains("\"question-1\""))
        assertTrue(row.payload.contains("\"lesson-1\""))
        assertEquals("draft-1", local.queuedDraftId)
        assertEquals("submission-1", local.queuedSubmissionId)
    }

    /**
     * Заявка несёт порцию, а состав черновика — целиком: без второго сервер не отличает «урок
     * удалён автором» от «урок не в этой порции» и не снимает ни урока, ни темы, ни раздела.
     */
    @Test
    fun queueArenaSubmission_bodyNamesTheWholeDraftTreeNotOnlyTheBatch() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        local.draft = entityBundleWithSecondLesson()
        val repository = repository(local)

        repository.queueArenaSubmission(domainSubmission())

        val payload = Json.parseToJsonElement(assertNotNull(local.queuedSubmission).payload) as JsonObject
        val tree = assertNotNull(payload["draftTree"] as? JsonObject, "полный состав черновика на проводе")
        assertEquals(listOf("lesson-1", "lesson-2"), tree.stringList("lessons"))
        assertEquals(listOf("theme-1"), tree.stringList("themes"))
        assertEquals(listOf("section-1"), tree.stringList("sections"))
        // Порция при этом осталась порцией.
        assertEquals(listOf("lesson-1"), (payload["targetLessonIds"] as JsonArray).map { it.jsonPrimitive.content })
        assertEquals(listOf("lesson-1"), (payload["lessons"] as JsonArray).map { (it as JsonObject).string("id") })
    }

    /** Эпик 8: счётчику правок автора на проводе делать нечего (AD-12). */
    @Test
    fun queueArenaSubmission_bodyCarriesNoLocalRevisionAndTheVersionRidesAsAColumn() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        local.draft = entityBundle()
        val repository = repository(local)

        repository.queueArenaSubmission(domainSubmission())

        val row = assertNotNull(local.queuedSubmission)
        assertFalse(row.payload.contains("localRevision"), "локальный счётчик правок не уезжает")
        assertEquals(3L, row.expectedVersion, "версия для сверки едет колонкой очереди (AD-24)")
    }

    @Test
    fun queueArenaSubmission_firstPublicationCarriesNoExpectedVersion() = runTest {
        // Квест ещё не публиковался: серверной версии нет, сверять не с чем — и приёмник обязан
        // пропустить мутацию без проверки, а не отвергнуть её как устаревшую.
        val local = FakeQuestAuthoringLocalDataSource()
        local.draft = entityBundle()
        val repository = repository(local)

        repository.queueArenaSubmission(domainSubmission().copy(expectedVersion = null))

        assertNull(assertNotNull(local.queuedSubmission).expectedVersion)
    }

    // ── Эпик 8: расхождение видно и разрешается новой мутацией ────────────────

    @Test
    fun observeDraftConflict_reportsTheServerVersionAndWhatWasSent() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        val outbox = FakeOutboxStore()
        outbox.records.value = listOf(conflictedRecord())
        val repository = repository(local, outbox)

        val conflict = repository.observeDraftConflict("owner-1", QuestDraftId("draft-1")).first()

        assertNotNull(conflict)
        assertEquals(7L, conflict!!.serverVersion)
        assertEquals(4L, conflict.expectedVersion)
    }

    @Test
    fun observeDraftConflict_saysNothingWhileTheRecordIsMerelyWaiting() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        val outbox = FakeOutboxStore()
        outbox.records.value = listOf(conflictedRecord().copy(state = OutboxState.WAITING))
        val repository = repository(local, outbox)

        assertNull(repository.observeDraftConflict("owner-1", QuestDraftId("draft-1")).first())
    }

    @Test
    fun resolveDraftConflict_queuesANewMutationAndDropsTheOldRecord() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        val outbox = FakeOutboxStore()
        outbox.records.value = listOf(conflictedRecord())
        val repository = repository(local, outbox, nowMs = 9_000L)

        val result = repository.resolveDraftConflict("owner-1", QuestDraftId("draft-1"))

        assertTrue(result.isSuccess)
        val resolved = assertNotNull(local.resolvedConflict)
        assertEquals(42L, resolved.conflictedRowId, "прежняя запись снимается")
        assertEquals("quest_authoring-SUBMIT_ARENA-submission-1-r7", resolved.replacement.mutationId)
        assertEquals(7L, resolved.replacement.expectedVersion)
        assertEquals("WAITING", resolved.replacement.state)
        assertEquals(9_000L, resolved.replacement.createdAtMs)
        assertEquals(OutboxOperations.SUBMIT_ARENA, resolved.replacement.operation)
        assertEquals("quest_authoring:draft:draft-1", resolved.replacement.entityRef)
        assertEquals("draft-1", resolved.draftId)
    }

    @Test
    fun resolveDraftConflict_withoutAConflictChangesNothing() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        val repository = repository(local, FakeOutboxStore())

        val result = repository.resolveDraftConflict("owner-1", QuestDraftId("draft-1"))

        assertFalse(result.isSuccess)
        assertNull(local.resolvedConflict)
    }

    @Test
    fun resolveDraftConflict_withoutAServerVersionIsRefused() = runTest {
        // Сервер назвал расхождение, но версию не назвал: отправлять «поверх версии N» нечего,
        // а заявка без ожидаемой версии ушла бы без сверки вообще.
        val local = FakeQuestAuthoringLocalDataSource()
        val outbox = FakeOutboxStore()
        outbox.records.value = listOf(conflictedRecord().copy(serverVersion = null))
        val repository = repository(local, outbox, nowMs = 9_000L)

        val result = repository.resolveDraftConflict("owner-1", QuestDraftId("draft-1"))

        assertFalse(result.isSuccess, "расхождение без номера кнопкой не снимается")
        assertNull(local.resolvedConflict, "очередь не трогается: прежняя запись остаётся конфликтной")
    }

    private fun conflictedRecord() =
        OutboxRecord(
            id = 42L,
            mutationId = "quest_authoring-SUBMIT_ARENA-submission-1",
            ownerUid = "owner-1",
            operation = OutboxOperations.SUBMIT_ARENA,
            payload = """{"submissionId":"submission-1"}""",
            state = OutboxState.CONFLICT,
            createdAtMs = 1_000L,
            entityRef = "quest_authoring:draft:draft-1",
            expectedVersion = 4L,
            serverVersion = 7L,
        )

    @Test
    fun localFailure_returnsResultFailure() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        val repository = repository(local)
        local.saveFailure = IllegalStateException("room failed")

        val result = repository.saveDraft(domainBundle())

        assertFalse(result.isSuccess)
        assertEquals("room failed", result.exceptionOrNull()?.message)
    }

    private class FakeQuestAuthoringLocalDataSource : QuestAuthoringLocalDataSource {
        val rejections = mutableListOf<Pair<String, String?>>()

        override suspend fun findAwaitingReviewTargets(ownerUid: String): List<ArenaReviewSubmissionTarget> = emptyList()

        override suspend fun applyPublication(
            ownerUid: String,
            draftId: String,
            submissionId: String,
            updatedAtMs: Long,
        ): Boolean = false

        override suspend fun applyRejection(
            ownerUid: String,
            draftId: String,
            submissionId: String,
            reason: String?,
            updatedAtMs: Long,
        ): Boolean {
            rejections += draftId to reason
            return false
        }

        override suspend fun claimLegacyReviewSubmission(
            ownerUid: String,
            draftId: String,
            submissionId: String,
        ): Boolean = false

        val summaries = MutableStateFlow<List<QuestDraftSummaryEntity>>(emptyList())
        val draftFlow = MutableStateFlow<QuestAuthoringEntityBundle?>(null)
        var draft: QuestAuthoringEntityBundle? = null
        var activeDraft: QuestAuthoringEntityBundle? = null
        var savedBundle: QuestAuthoringEntityBundle? = null
        var upsertedQuestion: DraftQuestionEntity? = null
        var queuedSubmission: OutboxEntity? = null
        var queuedDraftId: String? = null
        var queuedSubmissionId: String? = null
        var statusUpdate: StatusUpdate? = null
        var saveFailure: Exception? = null

        override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummaryEntity>> =
            summaries

        override fun observeDraft(draftId: String): Flow<QuestAuthoringEntityBundle?> =
            draftFlow

        override suspend fun getDraft(draftId: String): QuestAuthoringEntityBundle? =
            draft

        override suspend fun getActiveDraft(ownerUid: String): QuestAuthoringEntityBundle? =
            activeDraft

        var activatedDraftId: String? = null

        override suspend fun activateDraft(
            ownerUid: String,
            draftId: String,
        ) {
            activatedDraftId = draftId
        }

        override suspend fun saveDraft(bundle: QuestAuthoringEntityBundle) {
            saveFailure?.let { throw it }
            savedBundle = bundle
        }

        override suspend fun upsertQuestion(question: DraftQuestionEntity) {
            upsertedQuestion = question
        }

        override suspend fun queueArenaSubmission(
            outboxRow: OutboxEntity,
            draftId: String,
            submissionId: String,
            requestedAtMs: Long,
        ) {
            queuedSubmission = outboxRow
            queuedDraftId = draftId
            queuedSubmissionId = submissionId
        }

        override suspend fun upsertSyncedPrivateQuest(snapshot: PrivateQuestSnapshot) = Unit

        override suspend fun setDraftStatus(
            draftId: String,
            status: String,
            updatedAtMs: Long,
        ) {
            statusUpdate = StatusUpdate(draftId, status, updatedAtMs)
        }

        var resolvedConflict: ResolvedConflict? = null

        override suspend fun resolveArenaConflict(
            conflictedRowId: Long,
            replacement: OutboxEntity,
            draftId: String,
            updatedAtMs: Long,
        ) {
            resolvedConflict = ResolvedConflict(conflictedRowId, replacement, draftId, updatedAtMs)
        }
    }

    private data class ResolvedConflict(
        val conflictedRowId: Long,
        val replacement: OutboxEntity,
        val draftId: String,
        val updatedAtMs: Long,
    )

    /** Очередь в памяти — ровно те три вопроса, которые репозиторий ей задаёт. */
    private class FakeOutboxStore : OutboxStore {
        val records = MutableStateFlow<List<OutboxRecord>>(emptyList())

        override suspend fun enqueue(record: OutboxRecord): OutboxRecord = record

        override suspend fun dueRecords(
            ownerUid: String,
            nowMs: Long,
            limit: Int,
        ): List<OutboxRecord> = emptyList()

        override suspend fun apply(
            id: Long,
            decision: OutboxDecision,
        ) = Unit

        override suspend fun remove(id: Long) = Unit

        override fun observeCounts(ownerUid: String): Flow<OutboxCounts> = MutableStateFlow(OutboxCounts())

        override fun observeEntity(
            ownerUid: String,
            entityRef: String,
        ): Flow<OutboxEntitySync> =
            records.map { list ->
                list.firstOrNull { it.ownerUid == ownerUid && it.entityRef == entityRef }?.let {
                    OutboxEntitySync.Queued(it.id, it.state, it.expectedVersion, it.serverVersion)
                } ?: OutboxEntitySync.None
            }

        override suspend fun findByEntityRef(
            ownerUid: String,
            entityRef: String,
        ): OutboxRecord? = records.value.firstOrNull { it.ownerUid == ownerUid && it.entityRef == entityRef }

        override suspend fun quarantined(ownerUid: String): List<OutboxRecord> = emptyList()

        override suspend fun countPending(ownerUid: String): Int = 0

        /** Вторая операция AD-8: после согласия на смену аккаунта записи прежнего владельца снимаются. */
        override suspend fun forget(ownerUid: String) {
            records.value = records.value.filterNot { it.ownerUid == ownerUid }
        }
    }

    private fun repository(
        local: QuestAuthoringLocalDataSource,
        outbox: OutboxStore = FakeOutboxStore(),
        nowMs: Long = 5_000L,
    ) = QuestAuthoringRepositoryImpl(
        local = local,
        outbox = outbox,
        timestampProvider = { nowMs },
    )

    private data class StatusUpdate(
        val draftId: String,
        val status: String,
        val updatedAtMs: Long,
    )

    private fun domainBundle(): QuestAuthoringBundle =
        QuestAuthoringBundle(
            draft = QuestDraft(
                id = QuestDraftId("draft-1"),
                ownerUid = "owner-1",
                catalogId = CatalogId("catalog-1"),
                title = "My draft",
                description = null,
                defaultLanguage = "ru",
                defaultDifficulty = Difficulty.EASY,
                status = QuestDraftStatus.SAVED,
                localRevision = 1L,
                serverRevision = null,
                publicQuestId = null,
                createdAtMs = 1L,
                updatedAtMs = 2L,
                isActive = true,
            ),
            sections = listOf(
                DraftSection(
                    id = DraftSectionId("section-1"),
                    draftId = QuestDraftId("draft-1"),
                    title = "Section",
                    order = 0,
                ),
            ),
            themes = listOf(
                DraftTheme(
                    id = DraftThemeId("theme-1"),
                    draftId = QuestDraftId("draft-1"),
                    sectionId = DraftSectionId("section-1"),
                    title = "Theme",
                    order = 0,
                ),
            ),
            lessons = listOf(
                DraftLesson(
                    id = DraftLessonId("lesson-1"),
                    draftId = QuestDraftId("draft-1"),
                    themeId = DraftThemeId("theme-1"),
                    title = "Lesson",
                    order = 0,
                ),
            ),
            questions = listOf(domainQuestion()),
        )

    private fun domainQuestion(): DraftQuestion =
        DraftQuestion(
            id = DraftQuestionId("question-1"),
            draftId = QuestDraftId("draft-1"),
            lessonId = DraftLessonId("lesson-1"),
            type = DraftQuestionType.SINGLE_CHOICE,
            language = "ru",
            difficulty = Difficulty.EASY,
            order = 0,
            text = "Question?",
            imagePath = null,
            payload = """{"type":"single_choice"}""",
            validationState = DraftQuestionValidationState.SAVED,
            updatedAtMs = 3L,
        )

    private fun domainSubmission(): QuestArenaSubmission =
        QuestArenaSubmission(
            id = QuestArenaSubmissionId("submission-1"),
            draftId = QuestDraftId("draft-1"),
            ownerUid = "owner-1",
            expectedVersion = 3L,
            requestedAtMs = 4L,
            lessonIds = setOf(DraftLessonId("lesson-1")),
            targetShelf = "arena",
        )

    private fun entityBundle(): QuestAuthoringEntityBundle =
        QuestAuthoringEntityBundle(
            draft = QuestDraftEntity(
                id = "draft-1",
                ownerUid = "owner-1",
                catalogId = "catalog-1",
                title = "My draft",
                description = null,
                defaultLanguage = "ru",
                defaultDifficulty = "EASY",
                status = "SAVED",
                localRevision = 1L,
                serverRevision = null,
                publicQuestId = null,
                createdAtMs = 1L,
                updatedAtMs = 2L,
                isActive = true,
            ),
            sections = listOf(DraftSectionEntity("section-1", "draft-1", "Section", 0)),
            themes = listOf(DraftThemeEntity("theme-1", "draft-1", "section-1", "Theme", 0)),
            lessons = listOf(DraftLessonEntity("lesson-1", "draft-1", "theme-1", "Lesson", 0)),
            questions = listOf(
                DraftQuestionEntity(
                    id = "question-1",
                    draftId = "draft-1",
                    lessonId = "lesson-1",
                    type = "SINGLE_CHOICE",
                    language = "ru",
                    difficulty = "EASY",
                    order = 0,
                    text = "Question?",
                    imagePath = null,
                    payload = """{"type":"single_choice"}""",
                    validationState = "SAVED",
                    updatedAtMs = 3L,
                ),
            ),
        )

    /** Второй урок той же темы, который в эту заявку не попадает, но из черновика не делся. */
    private fun entityBundleWithSecondLesson(): QuestAuthoringEntityBundle =
        entityBundle().let { bundle ->
            bundle.copy(
                lessons = bundle.lessons + DraftLessonEntity("lesson-2", "draft-1", "theme-1", "Lesson 2", 1),
            )
        }

    private fun JsonObject.stringList(name: String): List<String> =
        (this[name] as JsonArray).map { it.jsonPrimitive.content }

    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content
}
