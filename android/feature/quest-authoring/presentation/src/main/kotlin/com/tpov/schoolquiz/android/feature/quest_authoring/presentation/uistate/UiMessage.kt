package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate

import androidx.annotation.StringRes
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType

enum class QuestArenaTargetNode {
    QUEST,
    SECTION,
    THEME,
    LESSON,
}

sealed interface UiMessage {
    data class Res(
        @StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : UiMessage

    data class Raw(val value: String) : UiMessage

    data class ArenaQueued(
        val target: QuestArenaTargetNode,
        val toArchive: Boolean,
    ) : UiMessage
}

internal fun DraftQuestionType.validationMessageRes(): Int =
    when (this) {
        DraftQuestionType.SINGLE_CHOICE -> R.string.qa_validation_single_choice
        DraftQuestionType.MULTIPLE_CHOICE -> R.string.qa_validation_multiple_choice
        DraftQuestionType.ORDERING -> R.string.qa_validation_ordering
        DraftQuestionType.FILL_BLANK -> R.string.qa_validation_fill_blank
        DraftQuestionType.SURVEY -> R.string.qa_validation_survey
    }
