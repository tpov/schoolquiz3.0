package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsMarkStateUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsSurfaceUi
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalBox
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalDecision
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalQuestionPreview
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalRow
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionFilter
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.DecideQuestProposalUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.FetchQuestProposalsUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.FetchReviewDecisionsUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.MarkReviewDecisionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Раздел «Предложения» на вкладке «Решений» (история 4.25, UX-22): полки, деталь, решение
 * владельца на ждущем, отказ кнопок на устаревшем, исход предложившему.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DecisionsProposalsComponentTest {
    private lateinit var lifecycle: LifecycleRegistry
    private val rows = mutableListOf<QuestProposalRow>()
    private val decisions = mutableListOf<Triple<String, Boolean, String>>()
    private var decideReply: Throwable? = null

    private fun row(
        id: String,
        status: String = "CHECK_PASSED",
        stale: Boolean = false,
        decision: QuestProposalDecision? = null,
    ) = QuestProposalRow(
        proposalId = id, kind = "REPORT", status = status, stale = stale,
        targetLessonId = "lesson-1", targetThemeId = null, targetCatalogId = null, targetQuestId = null,
        createdAtMs = 1, checkedAtMs = 2, decidedAtMs = 0, feeNolics = 0, changedQuestions = 0,
        decision = decision,
        proposalText = "текст отчёта", proposalOwnVariant = null, lessonTitle = "Урок",
        questionPreviews = listOf(QuestProposalQuestionPreview("q1", "текст", true)),
    )

    private val fetchProposals = FetchQuestProposalsUseCase(
        object : com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestProposalsRepository {
            override suspend fun fetch(
                box: QuestProposalBox,
                limit: Int,
                cursor: String?,
            ): Result<QuestProposalPage> =
                if (box == QuestProposalBox.OUTGOING) {
                    Result.success(QuestProposalPage(listOf(row("mine", status = "CHECK_FAILED")), false, null))
                } else {
                    Result.success(QuestProposalPage(rows.toList(), rows.size > 1, null))
                }
        },
    )

    private val decide = DecideQuestProposalUseCase(
        object : com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ProposalDecisionTransport {
            override suspend fun decideProposal(
                proposalId: String,
                decision: String,
                comment: String,
            ): Result<Unit> {
                val error = decideReply
                if (error != null) return Result.failure(error)
                decisions += Triple(proposalId, decision == "APPROVE", comment)
                return Result.success(Unit)
            }
        },
    )

    @Before
    fun setUp() {
        lifecycle = LifecycleRegistry().apply { resume() }
        rows.clear()
        decisions.clear()
        decideReply = null
    }

    @After
    fun tearDown() {
        lifecycle.stop()
        lifecycle.destroy()
    }

    private fun build() = DefaultDecisionsComponent(
        componentContext = DefaultComponentContext(lifecycle = lifecycle),
        fetchDecisions = FetchReviewDecisionsUseCase(StubDecisionsRepository()),
        markDecision = MarkReviewDecisionUseCase(StubDecisionsRepository()),
        questionContentParser = StubQuestionParser(),
        fetchProposals = fetchProposals,
        decideProposal = decide,
        mainContext = Dispatchers.Unconfined,
    )

    @Test
    fun `proposals preload the incoming shelf and the detail shows the report body`() = runTest {
        rows += listOf(row("p1"), row("p2"))
        val component = build()
        component.onSurfaceSelected(DecisionsSurfaceUi.PROPOSALS)
        assertEquals(listOf("p1", "p2"), component.state.value.proposals.map { it.proposalId })
        component.onProposalSelected("p1")
        assertEquals("текст отчёта", component.state.value.proposalsDetail?.proposalText)
    }

    @Test
    fun `outgoing shelf shows failed checks to the proposer`() = runTest {
        val component = build()
        component.onSurfaceSelected(DecisionsSurfaceUi.PROPOSALS)
        component.onProposalsBoxSelected(QuestProposalBox.OUTGOING)
        assertEquals(listOf("mine"), component.state.value.proposals.map { it.proposalId })
        assertEquals("CHECK_FAILED", component.state.value.proposals.single().status)
    }

    @Test
    fun `owner decision on a pending proposal reaches the receiver and re-reads the shelf`() = runTest {
        rows += row("p1")
        val component = build()
        component.onSurfaceSelected(DecisionsSurfaceUi.PROPOSALS)
        component.onProposalSelected("p1")
        component.onProposalCommentChanged("принято")
        component.onProposalDecision(true)
        assertEquals(listOf(Triple("p1", true, "принято")), decisions)
        assertEquals(DecisionsMarkStateUi.SAVED, component.state.value.proposalDecisionState)
    }

    @Test
    fun `stale or already decided rows refuse the decision locally`() = runTest {
        rows += listOf(row("p-stale", stale = true), row("p-decided", decision = QuestProposalDecision("REJECT", null, 5)))
        val component = build()
        component.onSurfaceSelected(DecisionsSurfaceUi.PROPOSALS)
        component.onProposalSelected("p-stale")
        component.onProposalDecision(true)
        component.onProposalSelected("p-decided")
        component.onProposalDecision(true)
        assertTrue(decisions.isEmpty())
    }

    @Test
    fun `failed decision returns the buttons to idle with the message`() = runTest {
        decideReply = IllegalStateException("PROPOSAL_NOT_ACTIONABLE")
        rows += row("p1")
        val component = build()
        component.onSurfaceSelected(DecisionsSurfaceUi.PROPOSALS)
        component.onProposalSelected("p1")
        component.onProposalDecision(false)
        assertEquals(DecisionsMarkStateUi.NONE, component.state.value.proposalDecisionState)
        assertTrue(component.state.value.proposalsDetail != null, "the row stays open for retry")
    }
}

/** Заглушки вердиктной половины: эта сюита про «Предложения», вердикты ей не нужны. */
private class StubDecisionsRepository :
    com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewDecisionsRepository {
    override suspend fun fetch(
        filter: ReviewDecisionFilter,
        cursor: com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionCursor?,
    ): Result<com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionPage> =
        Result.failure(IllegalStateException("verdicts are not this suite subject"))

    override suspend fun mark(
        verdictId: String,
        questionId: String,
        mark: com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMarkValue,
        comment: String,
    ): Result<com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMark> =
        Result.failure(IllegalStateException("verdicts are not this suite subject"))
}

private class StubQuestionParser : QuestionContentParser {
    override fun parse(payload: String): Result<com.tpov.schoolquiz.shared.core.question_schema.QuestionContent> =
        Result.failure(IllegalStateException("not this suite subject"))

    override fun parse(
        payload: String,
        fallbackId: String,
        fallbackText: String,
        fallbackDifficulty: com.tpov.schoolquiz.shared.core.question_schema.Difficulty,
    ): Result<com.tpov.schoolquiz.shared.core.question_schema.QuestionContent> =
        Result.failure(IllegalStateException("not this suite subject"))
}
