package com.tpov.schoolquiz.android.feature.quest.presentation.ui

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.model.CatalogDisplayItem
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTheme
import com.tpov.schoolquiz.android.feature.quest.presentation.DraftQuestDisplayItem
import com.tpov.schoolquiz.android.feature.quest.presentation.DraftSubmitState
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsUiState
import com.tpov.schoolquiz.android.feature.quest.presentation.R
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestReviewOutcomeSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestionReviewFailure
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewFeeQuote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose-покрытие «Моих квестов» в NOIR-оболочке (эпик 9.2), локале-независимое: каждая строка
 * берётся из ресурсов через контекст приложения, ни одного литерала.
 */
@RunWith(AndroidJUnit4::class)
class NoirMyQuestsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val state = MutableStateFlow(MyQuestsUiState())
    private val submitState = MutableStateFlow<DraftSubmitState?>(null)

    var draftClicks = 0
    var submitClicks = 0
    var confirmClicks = 0
    var dismissClicks = 0

    private val component =
        object : MyQuestsComponent {
            // Qualified this: внутри object-выражения имя override резолвится само на себя.
            override val state: StateFlow<MyQuestsUiState> = this@NoirMyQuestsScreenTest.state
            override val submitState: StateFlow<DraftSubmitState?> = this@NoirMyQuestsScreenTest.submitState

            override fun onCatalogSelected(id: CatalogId?) = Unit

            override fun onCreateQuestClick() = Unit

            override fun onQuestClick(quest: QuestDisplayItem) = Unit

            override fun onDraftClick(draft: DraftQuestDisplayItem) {
                draftClicks++
            }

            override fun onDraftSubmitClick(draft: DraftQuestDisplayItem) {
                submitClicks++
            }

            override fun onDraftSubmitDismiss() {
                dismissClicks++
            }

            override fun onDraftSubmitConfirm() {
                confirmClicks++
            }
        }

    private fun setContent() {
        composeRule.setContent {
            NoirTheme {
                MyQuestsScreen(component = component)
            }
        }
    }

    private fun draft(
        id: String,
        title: String,
        status: QuestDraftStatus = QuestDraftStatus.SAVED,
        questionCount: Int = 4,
        rejectionReason: String? = null,
    ) = DraftQuestDisplayItem(
        id = QuestDraftId(id),
        catalogId = CatalogId("cat-1"),
        title = title,
        status = status,
        questionCount = questionCount,
        isActive = true,
        rejectionReason = rejectionReason,
    )

    @Test
    fun `draft card shows title, question count and the submit button when submittable`() {
        state.value = MyQuestsUiState(
            drafts = listOf(draft("d1", "Первый черновик")),
        )
        setContent()

        composeRule.onNodeWithText("Первый черновик").assertExists()
        composeRule
            .onNodeWithText(context.getString(R.string.quest_questions_count, 4))
            .assertExists()
        composeRule
            .onNodeWithText(context.getString(R.string.quest_submit_for_review), ignoreCase = true)
            .assertExists()
    }

    @Test
    fun `a draft with no questions offers no submit button`() {
        // Подача вслепую невозможна (D13): без вопросов кнопки нет, а не disabled-серость.
        state.value = MyQuestsUiState(
            drafts = listOf(draft("d-empty", "Пустой черновик", questionCount = 0)),
        )
        setContent()

        composeRule.onNodeWithText("Пустой черновик").assertExists()
        composeRule
            .onNodeWithText(context.getString(R.string.quest_submit_for_review), ignoreCase = true)
            .assertDoesNotExist()
    }

    @Test
    fun `a rejected draft names the failure and its fix opens the draft`() {
        // UX-6: «Не прошёл» с числом, расширение по вопросам и «Исправить вопросы».
        val rejected = QuestReviewOutcomeSnapshot(
            submissionId = "sub-1",
            draftId = "d2",
            status = QuestReviewOutcomeSnapshot.STATUS_REJECTED,
            rejectionReason = "Причина модели",
            failedQuestions = listOf(
                QuestionReviewFailure(lessonId = "lesson-1", questionId = "q1", stage = "WORDS", reason = "неверный ключ"),
            ),
            detectedLanguage = "ru",
            outcomeReady = true,
        )
        state.value = MyQuestsUiState(
            drafts = listOf(draft("d2", "Отклонённый")),
            reviewOutcomes = mapOf("d2" to rejected),
        )
        setContent()

        composeRule
            .onNodeWithText(context.getString(R.string.quest_review_failed_tag, 1))
            .assertExists()
        composeRule
            .onNodeWithText(context.getString(R.string.quest_fix_questions), ignoreCase = true)
            .performClick()

        assertEquals(1, draftClicks)
    }

    @Test
    fun `submit dialog names the fee before sending and confirms exactly once`() {
        // D13: подтверждение без цитаты невозможно; плата названа до отправки.
        val item = draft("d3", "Диалоговый черновик")
        state.value = MyQuestsUiState(drafts = listOf(item))
        submitState.value = DraftSubmitState(
            draft = item,
            quote = ReviewFeeQuote(priorCycle = false, tariff = "initial", feeNolics = 3, tariffRevision = 1),
        )
        setContent()

        composeRule
            .onNodeWithText(context.getString(R.string.quest_submit_fee_line, 3))
            .assertExists()
        val confirm =
            composeRule.onNode(hasText(context.getString(R.string.quest_submit_confirm), ignoreCase = true))
        confirm.assertExists()
        confirm.performClick()

        assertEquals(1, confirmClicks)
    }

    @Test
    fun `submit dialog without a quote offers only the way out`() {
        // Ни цитаты, ни отказа — отправлять нечего: одна кнопка-выход, подтверждению нет места.
        // Спиннер загрузки — бесконечная анимация: часы теста заморожены, проверяется состав.
        composeRule.mainClock.autoAdvance = false
        val item = draft("d4", "Ожидающий цитаты")
        state.value = MyQuestsUiState(drafts = listOf(item))
        submitState.value = DraftSubmitState(draft = item, isQuoteLoading = true)
        setContent()

        composeRule
            .onNodeWithText(context.getString(R.string.quest_submit_confirm), ignoreCase = true)
            .assertDoesNotExist()
        composeRule
            .onNodeWithText(context.getString(R.string.quest_cancel), ignoreCase = true)
            .performClick()

        assertEquals(1, dismissClicks)
        assertEquals(0, confirmClicks)
    }

    @Test
    fun `a failed quote offers retry, and the way back stays reachable`() {
        val item = draft("d5", "Отказ цитаты")
        state.value = MyQuestsUiState(drafts = listOf(item))
        submitState.value = DraftSubmitState(draft = item, isQuoteFailed = true)
        setContent()

        composeRule
            .onNodeWithText(context.getString(R.string.quest_submit_quote_failed))
            .assertExists()
        composeRule
            .onNodeWithText(context.getString(R.string.quest_submit_retry), ignoreCase = true)
            .assertIsEnabled()
        composeRule
            .onNodeWithText(context.getString(R.string.quest_cancel), ignoreCase = true)
            .performClick()

        assertEquals(1, dismissClicks)
    }

    @Test
    fun `an empty shelf says it is empty rather than looking broken`() {
        state.value = MyQuestsUiState()
        setContent()

        composeRule
            .onNodeWithText(context.getString(R.string.quest_empty_quests_title))
            .assertExists()
        composeRule
            .onNodeWithText(context.getString(R.string.quest_empty_quests_hint))
            .assertExists()
    }

    @Test
    fun `quests from the shelf still render through the shared quest card`() {
        state.value = MyQuestsUiState(
            quests = listOf(
                QuestDisplayItem(
                    id = QuestId("q-1"),
                    catalogId = CatalogId("cat-1"),
                    title = "Опубликованный квест",
                    pictureUrl = null,
                    averageRating = null,
                ),
            ),
        )
        setContent()

        composeRule.onNodeWithText("Опубликованный квест").assertExists()
    }

    @Test
    fun `a submitting dialog keeps the confirm pressed-out`() {
        // Отправка в полёте: повторное подтверждение отключено, «отмена» жива.
        val item = draft("d6", "Уже уезжает")
        state.value = MyQuestsUiState(drafts = listOf(item))
        submitState.value = DraftSubmitState(
            draft = item,
            quote = ReviewFeeQuote(priorCycle = false, tariff = "initial", feeNolics = 1, tariffRevision = 1),
            isSubmitting = true,
        )
        setContent()

        composeRule
            .onNodeWithText(context.getString(R.string.quest_submit_sending), ignoreCase = true)
            .assertExists()
            .assertIsNotEnabled()
        composeRule
            .onNodeWithText(context.getString(R.string.quest_cancel), ignoreCase = true)
            .assertIsEnabled()
    }
}
