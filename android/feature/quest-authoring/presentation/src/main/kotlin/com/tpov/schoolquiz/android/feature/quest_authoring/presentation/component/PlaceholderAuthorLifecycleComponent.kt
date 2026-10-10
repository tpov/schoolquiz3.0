package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.AuthorLifecycleUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlaceholderAuthorLifecycleComponent : AuthorLifecycleComponent {
    override val state: StateFlow<AuthorLifecycleUiState> =
        MutableStateFlow(AuthorLifecycleUiState(isLoading = false))

    override fun onRefreshClick() = Unit

    override fun onLoadMoreClick() = Unit

    override fun onLessonSelected(lessonId: String) = Unit
}
