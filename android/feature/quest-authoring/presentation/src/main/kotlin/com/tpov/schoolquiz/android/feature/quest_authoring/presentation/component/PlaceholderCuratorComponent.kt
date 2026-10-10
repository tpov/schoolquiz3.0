package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.CuratorUiState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonShelf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlaceholderCuratorComponent : CuratorComponent {
    override val state: StateFlow<CuratorUiState> =
        MutableStateFlow(CuratorUiState(isLoading = false))

    override fun onRefreshClick() = Unit

    override fun onLoadMoreClick() = Unit

    override fun onLessonSelected(lessonId: String) = Unit

    override fun onLessonMenuRequested(lessonId: String) = Unit

    override fun onMenuDismissed() = Unit

    override fun onTransitionRequested(target: LessonShelf) = Unit

    override fun onConfirmationDismissed() = Unit

    override fun onReasonChanged(value: String) = Unit

    override fun onTransitionClick(target: LessonShelf) = Unit
}
