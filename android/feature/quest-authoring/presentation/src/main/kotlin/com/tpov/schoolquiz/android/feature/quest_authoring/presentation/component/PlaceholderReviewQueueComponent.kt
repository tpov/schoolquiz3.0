package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueFilter
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlaceholderReviewQueueComponent : ReviewQueueComponent {
    override val state: StateFlow<ReviewQueueUiState> =
        MutableStateFlow(ReviewQueueUiState(isLoading = false))

    override fun onFilterMenuClick() = Unit

    override fun onFilterMenuDismiss() = Unit

    override fun onFilterSelected(filter: ReviewQueueFilter) = Unit

    override fun onAssignmentSelected(id: String) = Unit

    override fun onBackToListClick() = Unit

    override fun onScoreSelected(score: Int) = Unit

    override fun onLanguageSelected(language: String) = Unit

    override fun onTranslationTextChanged(
        questionId: String,
        segmentKey: String,
        value: String,
    ) = Unit

    override fun onSegmentAcceptedChanged(
        questionId: String,
        segmentKey: String,
        accepted: Boolean,
    ) = Unit

    override fun onSubmitClick() = Unit
}
