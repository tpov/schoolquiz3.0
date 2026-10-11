package com.tpov.schoolquiz.android.feature.internet.social.presentation.uistate

import androidx.annotation.StringRes
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollMode
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollQuestionState
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollSeriesSummary

/** Сообщение экрана: строковый ресурс или сырой текст транспорта. */
sealed interface PollsUiMessage {
    data class Res(
        @StringRes val id: Int,
    ) : PollsUiMessage

    data class Raw(val value: String) : PollsUiMessage
}

/** Черновик автора на экране создания серии: правится свободно до публикации (UX-O7 §6.2). */
data class PollAuthorQuestion(
    val text: String = "",
    val mode: PollMode = PollMode.SINGLE,
    val optionTexts: List<String> = listOf("", ""),
)

data class PollAuthorDraft(
    val title: String = "",
    val questions: List<PollAuthorQuestion> = listOf(PollAuthorQuestion()),
)

/**
 * Состояние поверхности опросов. Три экрана одного компонента: список серий, вопрос серии,
 * авторская форма ([authoring] != null). Скрытое распределение и нулевой счёт различаются
 * доменом ([PollQuestionState.distribution] == null против нулей) — экран ничего не додумывает.
 */
data class PollsUiState(
    val isLoading: Boolean = false,
    val errorMessage: PollsUiMessage? = null,
    val series: List<PollSeriesSummary> = emptyList(),
    val openSeriesId: String? = null,
    val questionIndex: Int = 0,
    val questionState: PollQuestionState? = null,
    val questionLoading: Boolean = false,
    val questionError: PollsUiMessage? = null,
    /** Локально выбранный набор до подтверждения — правка свободна, голоса ещё нет (§6.2). */
    val selectedOptionIds: Set<String> = emptySet(),
    /** Голос поставлен в очередь и ещё не подтверждён серверной вью — «не отправлено» по AD-8. */
    val voteQueued: Boolean = false,
    val authoring: PollAuthorDraft? = null,
    val publishQueued: Boolean = false,
) {
    val isQuestionOpen: Boolean get() = openSeriesId != null && authoring == null
}
