package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.logic

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ArenaReadiness
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionValidationState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle

fun validateArenaReadiness(bundle: QuestAuthoringBundle): ArenaReadiness {
    val invalid = bundle.questions
        .filter { it.validationState != DraftQuestionValidationState.SAVED || it.payload == null }
        .map { it.id }
        .toSet()
    val saved = bundle.questions.filter { it.validationState == DraftQuestionValidationState.SAVED && it.payload != null }
    val hasEasy = saved.any { it.difficulty == Difficulty.EASY }
    val hasHard = saved.any { it.difficulty == Difficulty.HARD }

    return ArenaReadiness(
        canSend = hasEasy && hasHard && invalid.isEmpty(),
        hasEasyQuestion = hasEasy,
        hasHardQuestion = hasHard,
        invalidQuestionIds = invalid,
    )
}
