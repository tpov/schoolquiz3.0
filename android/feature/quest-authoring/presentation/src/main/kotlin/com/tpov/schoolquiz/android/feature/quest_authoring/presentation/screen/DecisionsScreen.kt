package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.DecisionsComponent

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun DecisionsScreen(
    component: DecisionsComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsStateWithLifecycle(component.state.value)
    DecisionsView(
        state = state,
        onFilterSelected = component::onFilterSelected,
        onLoadMoreClick = component::onLoadMoreClick,
        onEntrySelected = component::onEntrySelected,
        onBackToListClick = component::onBackToListClick,
        onMarkSelected = component::onMarkSelected,
        onCommentChanged = component::onCommentChanged,
        modifier = modifier,
    )
}
