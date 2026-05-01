package com.tpov.schoolquiz.shared.feature.quest_authoring.domain

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.KotlinxSerializationQuestionContentParser
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.command.CreateQuestDraftCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.command.SaveDraftQuestionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.fake.FakeQuestAuthoringRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.CreateQuestDraftUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.GetActiveQuestDraftUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.GetArenaReadinessUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveQuestDraftSummariesUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SaveDraftQuestionUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QuestAuthoringUseCaseTest {
    private val catalogId = CatalogId("math")
    private val parser = KotlinxSerializationQuestionContentParser()

    @Test
    fun `create draft builds full private hierarchy and marks it active`() = runTest {
        val repo = FakeQuestAuthoringRepository()
        val ids = FakeIdProvider()
        val clock = FakeTimestampProvider(1_000L)
        val useCase = CreateQuestDraftUseCase(repo, ids, clock)

        val result = useCase(createCommand())

        assertTrue(result.isSuccess)
        val bundle = repo.getDraft(result.getOrThrow())
        assertNotNull(bundle)
        assertEquals("uid-1", bundle.draft.ownerUid)
        assertEquals(QuestDraftStatus.DRAFT, bundle.draft.status)
        assertTrue(bundle.draft.isActive)
        assertEquals(1, bundle.sections.size)
        assertEquals(1, bundle.themes.size)
        assertEquals(1, bundle.lessons.size)
        assertEquals(emptyList(), bundle.questions)
    }

    @Test
    fun `get active draft returns unfinished draft for owner`() = runTest {
        val repo = FakeQuestAuthoringRepository()
        val ids = FakeIdProvider()
        val clock = FakeTimestampProvider(1_000L)
        val create = CreateQuestDraftUseCase(repo, ids, clock)
        val draftId = create(createCommand()).getOrThrow()

        val active = GetActiveQuestDraftUseCase(repo)("uid-1").getOrThrow()

        assertNotNull(active)
        assertEquals(draftId, active.draft.id)
    }

    @Test
    fun `save valid question parses payload and stores canonical saved question`() = runTest {
        val repo = FakeQuestAuthoringRepository()
        val ids = FakeIdProvider()
        val clock = FakeTimestampProvider(1_000L)
        val draftId = CreateQuestDraftUseCase(repo, ids, clock)(createCommand()).getOrThrow()
        val lessonId = repo.getDraft(draftId)!!.lessons.single().id
        clock.value = 2_000L
        val save = SaveDraftQuestionUseCase(repo, parser, ids, clock)

        val result = save(
            SaveDraftQuestionCommand(
                draftId = draftId,
                lessonId = lessonId,
                questionId = null,
                type = DraftQuestionType.SINGLE_CHOICE,
                language = "ru",
                difficulty = Difficulty.EASY,
                order = 0,
                imagePath = null,
                payload = singleChoicePayload("q-easy", Difficulty.EASY),
            ),
        )

        assertTrue(result.isSuccess)
        val bundle = repo.getDraft(draftId)!!
        assertEquals(1, bundle.questions.size)
        assertEquals("Question q-easy?", bundle.questions.single().text)
        assertEquals(2L, bundle.draft.localRevision)
        assertEquals(2_000L, bundle.draft.updatedAtMs)
    }

    @Test
    fun `save question rejects payload when declared type differs`() = runTest {
        val repo = FakeQuestAuthoringRepository()
        val ids = FakeIdProvider()
        val clock = FakeTimestampProvider(1_000L)
        val draftId = CreateQuestDraftUseCase(repo, ids, clock)(createCommand()).getOrThrow()
        val lessonId = repo.getDraft(draftId)!!.lessons.single().id
        val save = SaveDraftQuestionUseCase(repo, parser, ids, clock)

        val result = save(
            SaveDraftQuestionCommand(
                draftId = draftId,
                lessonId = lessonId,
                questionId = null,
                type = DraftQuestionType.ORDERING,
                language = "ru",
                difficulty = Difficulty.EASY,
                order = 0,
                imagePath = null,
                payload = singleChoicePayload("q-easy", Difficulty.EASY),
            ),
        )

        assertTrue(result.isFailure)
        assertEquals(emptyList(), repo.getDraft(draftId)!!.questions)
    }

    @Test
    fun `arena readiness requires both easy and hard saved questions`() = runTest {
        val repo = FakeQuestAuthoringRepository()
        val ids = FakeIdProvider()
        val clock = FakeTimestampProvider(1_000L)
        val draftId = CreateQuestDraftUseCase(repo, ids, clock)(createCommand()).getOrThrow()
        val lessonId = repo.getDraft(draftId)!!.lessons.single().id
        val save = SaveDraftQuestionUseCase(repo, parser, ids, clock)
        saveQuestion(save, draftId, lessonId, "q-easy", Difficulty.EASY, order = 0)

        val afterEasy = GetArenaReadinessUseCase(repo)(draftId).getOrThrow()
        assertFalse(afterEasy.canSend)
        assertTrue(afterEasy.hasEasyQuestion)
        assertFalse(afterEasy.hasHardQuestion)

        saveQuestion(save, draftId, lessonId, "q-hard", Difficulty.HARD, order = 1)
        val afterHard = GetArenaReadinessUseCase(repo)(draftId).getOrThrow()
        assertTrue(afterHard.canSend)
        assertEquals(emptySet(), afterHard.invalidQuestionIds)
    }

    @Test
    fun `observe summaries returns local draft for my quests surface`() = runTest {
        val repo = FakeQuestAuthoringRepository()
        val ids = FakeIdProvider()
        val clock = FakeTimestampProvider(1_000L)
        val draftId = CreateQuestDraftUseCase(repo, ids, clock)(createCommand()).getOrThrow()

        val summaries = ObserveQuestDraftSummariesUseCase(repo)("uid-1").first()

        assertEquals(1, summaries.size)
        assertEquals(draftId, summaries.single().id)
        assertEquals("Private Math Quest", summaries.single().title)
        assertTrue(summaries.single().isActive)
    }

    private fun createCommand(): CreateQuestDraftCommand =
        CreateQuestDraftCommand(
            ownerUid = "uid-1",
            catalogId = catalogId,
            sourceQuestId = null,
            title = "Private Math Quest",
            description = "Draft",
            defaultLanguage = "ru",
            defaultDifficulty = Difficulty.EASY,
            sectionTitle = "Section",
            themeTitle = "Theme",
            lessonTitle = "Lesson",
        )

    private suspend fun saveQuestion(
        save: SaveDraftQuestionUseCase,
        draftId: com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId,
        lessonId: com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId,
        id: String,
        difficulty: Difficulty,
        order: Int,
    ) {
        val result = save(
            SaveDraftQuestionCommand(
                draftId = draftId,
                lessonId = lessonId,
                questionId = null,
                type = DraftQuestionType.SINGLE_CHOICE,
                language = "ru",
                difficulty = difficulty,
                order = order,
                imagePath = null,
                payload = singleChoicePayload(id, difficulty),
            ),
        )
        assertTrue(result.isSuccess)
    }

    private fun singleChoicePayload(id: String, difficulty: Difficulty): String =
        """{"type":"SingleChoice","id":"$id","difficulty":"${difficulty.name}","text":"Question $id?","imageUrl":null,"options":[{"id":"A","text":"A"},{"id":"B","text":"B"}],"correctOptionId":"A"}"""

    private class FakeIdProvider : QuestAuthoringIdProvider {
        private var counter = 0

        override fun nextId(prefix: String): String = "$prefix-${counter++}"
    }

    private class FakeTimestampProvider(
        var value: Long,
    ) : QuestAuthoringTimestampProvider {
        override fun nowMs(): Long = value
    }
}
