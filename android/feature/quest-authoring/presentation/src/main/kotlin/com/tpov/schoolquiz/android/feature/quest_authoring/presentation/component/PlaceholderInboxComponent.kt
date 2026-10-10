package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.InboxUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Заглушка для сборок без data-половины фичи: экран рисуется, данных не показывает. */
class PlaceholderInboxComponent : InboxComponent {
    private val _state = MutableStateFlow(InboxUiState(isLoading = false))
    override val state: StateFlow<InboxUiState> = _state.asStateFlow()

    override fun onRefreshClick() = Unit

    override fun onLoadMoreClick() = Unit
}
