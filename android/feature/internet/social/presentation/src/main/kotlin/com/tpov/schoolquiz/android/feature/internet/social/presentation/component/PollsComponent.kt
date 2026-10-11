package com.tpov.schoolquiz.android.feature.internet.social.presentation.component

import com.tpov.schoolquiz.android.feature.internet.social.presentation.uistate.PollsUiState
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Поверхность опросов (UX-O7, история 8.2): список опубликованных серий, вопрос с голосованием
 * и авторская форма серии. Экран рендерит состояние и эмитит колбэки — разрешение зависимостей
 * и хода данных здесь нет (правила презентации AGENTS).
 *
 * Право проверяет серверная вью; незалогубший гость читает отказ транспорта, а не чужие серии.
 */
interface PollsComponent {
    val state: StateFlow<PollsUiState>

    fun onRefreshClick()

    // ── участник ──
    fun onOpenSeriesClick(seriesId: String)

    fun onToggleOptionClick(optionId: String)

    fun onConfirmVoteClick()

    fun onNextQuestionClick()

    fun onCloseSeriesClick()

    // ── автор ──
    fun onCreateSeriesClick()

    fun onAuthorTitleChange(title: String)

    fun onAuthorAddQuestionClick()

    fun onAuthorRemoveQuestionClick(questionIndex: Int)

    fun onAuthorQuestionTextChange(
        questionIndex: Int,
        text: String,
    )

    fun onAuthorModeChange(
        questionIndex: Int,
        mode: PollMode,
    )

    fun onAuthorAddOptionClick(questionIndex: Int)

    fun onAuthorRemoveOptionClick(
        questionIndex: Int,
        optionIndex: Int,
    )

    fun onAuthorOptionTextChange(
        questionIndex: Int,
        optionIndex: Int,
        text: String,
    )

    fun onPublishClick()

    fun onAuthorCancelClick()
}
