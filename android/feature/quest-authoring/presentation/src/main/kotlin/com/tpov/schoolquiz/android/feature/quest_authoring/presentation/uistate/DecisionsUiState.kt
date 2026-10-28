package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalBox
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalRow
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionFilter
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMarkValue

/**
 * Состояние отметки на карточке (UX-22): «Отправляется…» в полёте, «Сохранено» после ответа.
 */
enum class DecisionsMarkStateUi {
    NONE,
    SAVING,
    SAVED,
}

data class DecisionsUiState(
    val isLoading: Boolean = true,
    /** Сервер отказал по роли: вкладка показывает авторскую сторону — только «Предложения». */
    val accessRefused: Boolean = false,
    /** Поверхность вкладки: вердикты админа или «Предложения» (история 4.25, UX-22). */
    val surface: DecisionsSurfaceUi = DecisionsSurfaceUi.VERDICTS,
    /** Активная полка «Предложений» и её строки (страница прав сервера). */
    val proposalsBox: QuestProposalBox = QuestProposalBox.INCOMING,
    val proposals: List<QuestProposalRow> = emptyList(),
    val proposalsHasMore: Boolean = false,
    val proposalsLoading: Boolean = false,
    val proposalsLoadingMore: Boolean = false,
    val proposalsDetail: QuestProposalRow? = null,
    val proposalCommentDraft: String = "",
    val proposalDecisionState: DecisionsMarkStateUi = DecisionsMarkStateUi.NONE,
    val proposalsErrorMessage: UiMessage? = null,
    val selectedFilter: ReviewDecisionFilter = ReviewDecisionFilter.UNMARKED,
    val entries: List<DecisionsListItemUiState> = emptyList(),
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val detail: DecisionsDetailUiState? = null,
    val errorMessage: UiMessage? = null,
)

/** Поверхность «Решений»: вердикты — админская половина, «Предложения» — общая (UX-22). */
enum class DecisionsSurfaceUi {
    VERDICTS,
    PROPOSALS,
}

data class DecisionsListItemUiState(
    val verdictId: String,
    val questionId: String,
    val unreadable: Boolean,
    val questTitle: String,
    val lessonTitle: String,
    val stage: String,
    val questionText: String,
    val verdictPass: Boolean?,
    val markValue: ReviewDecisionMarkValue?,
)

data class DecisionsDetailUiState(
    val verdictId: String,
    val questionId: String,
    val unreadable: Boolean,
    val questTitle: String,
    val lessonTitle: String,
    val stage: String,
    val attempt: Int,
    val revision: Long,
    val model: String,
    val promptVersion: String,
    val rationale: String,
    val sources: List<String>,
    val verdictPass: Boolean?,
    val reason: String,
    val preview: DecisionsQuestionPreviewUiState?,
    val markValue: ReviewDecisionMarkValue?,
    val markComment: String,
    val commentDraft: String,
    val markState: DecisionsMarkStateUi,
)

/** Статический предпросмотр вопроса в стиле раннера: без таймера и плат, только чтение. */
data class DecisionsQuestionPreviewUiState(
    val text: String,
    val options: List<DecisionsPreviewOptionUiState>,
    val answerKeyLine: String?,
    val parseFailed: Boolean,
)

data class DecisionsPreviewOptionUiState(
    val text: String,
    val correct: Boolean,
)
