package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueFilter
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueUiState
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

    fun onSegmentAcceptedChanged(
        questionId: String,
        segmentKey: String,
        accepted: Boolean,
    )

    fun onSubmitClick()
}
