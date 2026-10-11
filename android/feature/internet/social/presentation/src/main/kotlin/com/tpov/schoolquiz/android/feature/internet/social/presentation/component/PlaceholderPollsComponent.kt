package com.tpov.schoolquiz.android.feature.internet.social.presentation.component

import com.tpov.schoolquiz.android.feature.internet.social.presentation.uistate.PollsUiState
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Заглушка на случай, если Koin-биндинг поверхности не собрался — экран остаётся читаемым. */
class PlaceholderPollsComponent : PollsComponent {
    private val _state = MutableStateFlow(PollsUiState())
    override val state: StateFlow<PollsUiState> = _state.asStateFlow()

    override fun onRefreshClick() = Unit

    override fun onOpenSeriesClick(seriesId: String) = Unit

    override fun onToggleOptionClick(optionId: String) = Unit

    override fun onConfirmVoteClick() = Unit

    override fun onNextQuestionClick() = Unit

    override fun onCloseSeriesClick() = Unit

    override fun onCreateSeriesClick() = Unit

    override fun onAuthorTitleChange(title: String) = Unit

    override fun onAuthorAddQuestionClick() = Unit

    override fun onAuthorRemoveQuestionClick(questionIndex: Int) = Unit

    override fun onAuthorQuestionTextChange(
        questionIndex: Int,
        text: String,
    ) = Unit

    override fun onAuthorModeChange(
        questionIndex: Int,
        mode: PollMode,
    ) = Unit

    override fun onAuthorAddOptionClick(questionIndex: Int) = Unit

    override fun onAuthorRemoveOptionClick(
        questionIndex: Int,
        optionIndex: Int,
    ) = Unit

    override fun onAuthorOptionTextChange(
        questionIndex: Int,
        optionIndex: Int,
        text: String,
    ) = Unit

    override fun onPublishClick() = Unit

    override fun onAuthorCancelClick() = Unit
}
