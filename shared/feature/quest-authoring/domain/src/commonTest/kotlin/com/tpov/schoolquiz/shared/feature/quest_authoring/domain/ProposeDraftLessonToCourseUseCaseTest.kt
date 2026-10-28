package com.tpov.schoolquiz.shared.feature.quest_authoring.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.fake.FakeQuestAuthoringRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLesson
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionValidationState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftThemeId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.NewLessonProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.NewLessonProposalTarget
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraft
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonEditCopy
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonProposalFeeQuote
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.LessonProposalRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ProposeDraftLessonToCourseUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitNewLessonProposalUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * «Предложить автору» урок черновика (4.24): тело собирается из черновика (только изменённый
 * урок — его вопросы с payload), недописанный вопрос — отказ до очереди, чужой урок черновика —
 * отказ. Урок едет в авторской форме, словарь типов — серверный CONTENT_TYPE.
 */
class ProposeDraftLessonToCourseUseCaseTest {
    private val queued = mutableListOf<NewLessonProposal>()

    private val proposalRepository =
        object : LessonProposalRepository {
            override suspend fun queueErrorReportProposal(proposal: com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ErrorReportProposal) =
                Result.success(Unit)

            override suspend fun queueNewLessonProposal(proposal: NewLessonProposal): Result<Unit> {
                queued += proposal
                return Result.success(Unit)
            }

            override suspend fun queueCopyEditProposal(proposal: com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.CopyEditProposal) =
                Result.success(Unit)

            override suspend fun fetchLessonEditCopy(lessonId: String): Result<LessonEditCopy> =
                Result.failure(IllegalStateException("unused"))

            override suspend fun quoteProposalFee(
                kind: String,
                changedQuestions: Int,
            ): Result<LessonProposalFeeQuote> = Result.failure(IllegalStateException("unused"))
        }

    private val target =
        NewLessonProposalTarget(
            catalogId = "catalog-1",
            questId = "quest-1",
            sectionId = "section-1",
            themeId = "theme-1",
        )

    private fun bundle(vararg questions: DraftQuestion): QuestAuthoringBundle {
        val draftId = QuestDraftId("draft-1")
        return QuestAuthoringBundle(
            draft =
                QuestDraft(
                    id = draftId,
                    ownerUid = "author",
                    catalogId = com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId("catalog-9"),
                    title = "Черновик",
                    description = null,
                    defaultLanguage = "ru",
                    defaultDifficulty = Difficulty.EASY,
                    status = QuestDraftStatus.DRAFT,
                    localRevision = 1,
                    serverRevision = null,
                    publicQuestId = null,
                    createdAtMs = 0L,
                    updatedAtMs = 0L,
                    isActive = true,
                ),
            sections =
                listOf(
                    com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftSection(
                        id = com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftSectionId("section-1"),
                        draftId = draftId,
                        title = "Раздел",
                        order = 0,
                    ),
                ),
            themes =
                listOf(
                    com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftTheme(
                        id = DraftThemeId("theme-1"),
                        draftId = draftId,
                        sectionId = com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftSectionId("section-1"),
                        title = "Тема",
                        order = 0,
                    ),
                ),
            lessons =
                listOf(
                    DraftLesson(
                        id = DraftLessonId("lesson-1"),
                        draftId = draftId,
                        themeId = DraftThemeId("theme-1"),
                        title = "Мой урок",
                        order = 0,
                    ),
                ),
            questions = questions.toList(),
        )
    }

    private fun question(id: String, payload: String?): DraftQuestion =
        DraftQuestion(
            id = DraftQuestionId(id),
            draftId = QuestDraftId("draft-1"),
            lessonId = DraftLessonId("lesson-1"),
            type = DraftQuestionType.SINGLE_CHOICE,
            language = "ru",
            difficulty = Difficulty.EASY,
            order = 0,
            text = "Вопрос?",
            imagePath = null,
            payload = payload,
            validationState = DraftQuestionValidationState.SAVED,
            updatedAtMs = 0L,
        )

    @Test
    fun `queues the draft lesson in authoring form with the server type dictionary`() = runTest {
        val repository = FakeQuestAuthoringRepository(
            listOf(
                bundle(
                    question(
                        "q-1",
                        "{\"type\":\"SingleChoice\",\"correctOptionId\":\"a\"}",
                    ),
                ),
            ),
        )
        val useCase =
            ProposeDraftLessonToCourseUseCase(
                authoringRepository = repository,
                submitProposal =
                    SubmitNewLessonProposalUseCase(
                        repository = proposalRepository,
                        idProvider = { prefix -> "$prefix-test-1" },
                        timestampProvider = { 42L },
                    ),
            )

        val outcome = useCase("author", QuestDraftId("draft-1"), DraftLessonId("lesson-1"), target)

        assertTrue(outcome.isSuccess)
        assertEquals(1, queued.size)
        val proposal = queued.single()
        assertEquals("Мой урок", proposal.command.lessonTitle)
        val wireQuestion = proposal.command.questions.single()
        assertEquals("q-1", wireQuestion.id)
        assertEquals("SingleChoice", wireQuestion.type)
        assertEquals("EASY", wireQuestion.difficulty)
        assertTrue(wireQuestion.payload.contains("correctOptionId"))
    }

    @Test
    fun `a question without payload is refused before the queue`() = runTest {
        val repository = FakeQuestAuthoringRepository(listOf(bundle(question("q-1", null))))
        val useCase =
            ProposeDraftLessonToCourseUseCase(
                authoringRepository = repository,
                submitProposal =
                    SubmitNewLessonProposalUseCase(
                        repository = proposalRepository,
                        idProvider = { prefix -> "$prefix-test-1" },
                        timestampProvider = { 42L },
                    ),
            )

        val outcome = useCase("author", QuestDraftId("draft-1"), DraftLessonId("lesson-1"), target)

        assertTrue(outcome.isFailure)
        assertTrue(queued.isEmpty())
    }

    @Test
    fun `a lesson outside the draft is refused`() = runTest {
        val repository = FakeQuestAuthoringRepository(listOf(bundle(question("q-1", "{}"))))
        val useCase =
            ProposeDraftLessonToCourseUseCase(
                authoringRepository = repository,
                submitProposal =
                    SubmitNewLessonProposalUseCase(
                        repository = proposalRepository,
                        idProvider = { prefix -> "$prefix-test-1" },
                        timestampProvider = { 42L },
                    ),
            )

        val outcome = useCase("author", QuestDraftId("draft-1"), DraftLessonId("other-lesson"), target)

        assertTrue(outcome.isFailure)
        assertTrue(queued.isEmpty())
    }
}
