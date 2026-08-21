package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.logic

import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType

fun QuestionContent.toDraftQuestionType(): DraftQuestionType =
    when (this) {
        is QuestionContent.SingleChoice -> DraftQuestionType.SINGLE_CHOICE
        is QuestionContent.MultipleChoice -> DraftQuestionType.MULTIPLE_CHOICE
        is QuestionContent.Ordering -> DraftQuestionType.ORDERING
        is QuestionContent.FillBlank -> DraftQuestionType.FILL_BLANK
        is QuestionContent.Survey -> DraftQuestionType.SURVEY
    }
