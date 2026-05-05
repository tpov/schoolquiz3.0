package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.ReviewQueueComponent

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun ReviewQueueScreen(
    component: ReviewQueueComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsStateWithLifecycle(component.state.value)
    ReviewQueueView(
        state = state,
        onFilterMenuClick = component::onFilterMenuClick,
        onFilterMenuDismiss = component::onFilterMenuDismiss,
        onFilterSelected = component::onFilterSelected,
        onAssignmentSelected = component::onAssignmentSelected,
        onBackToListClick = component::onBackToListClick,
        onScoreSelected = component::onScoreSelected,
        onLanguageSelected = component::onLanguageSelected,
        onTranslationTextChanged = component::onTranslationTextChanged,
        onSegmentAcceptedChanged = component::onSegmentAcceptedChanged,
        onSubmitClick = component::onSubmitClick,
        modifier = modifier,
    )
}
