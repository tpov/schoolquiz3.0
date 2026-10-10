package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueFilter
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewSegmentDecisionUi
import kotlinx.coroutines.flow.StateFlow

interface ReviewQueueComponent {
    val state: StateFlow<ReviewQueueUiState>

    fun onFilterMenuClick()

    fun onFilterMenuDismiss()

    fun onFilterSelected(filter: ReviewQueueFilter)

    fun onAssignmentSelected(id: String)

    fun onBackToListClick()

    fun onScoreSelected(score: Int)

    fun onLanguageSelected(language: String)

    fun onTranslationTextChanged(
        questionId: String,
        segmentKey: String,
        value: String,
    )

    fun onQuestionScoreSelected(
        questionId: String,
        score: Int,
    )

    fun onSegmentDecided(
        questionId: String,
        segmentKey: String,
        decision: ReviewSegmentDecisionUi,
    )

    fun onSubmitClick()
}
