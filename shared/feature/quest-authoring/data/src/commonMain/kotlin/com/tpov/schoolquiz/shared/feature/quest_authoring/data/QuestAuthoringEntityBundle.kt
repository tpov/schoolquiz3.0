package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.persistence.DraftLessonEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftQuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftSectionEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftThemeEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftEntity

data class QuestAuthoringEntityBundle(
    val draft: QuestDraftEntity,
    val sections: List<DraftSectionEntity>,
    val themes: List<DraftThemeEntity>,
    val lessons: List<DraftLessonEntity>,
    val questions: List<DraftQuestionEntity>,
)
