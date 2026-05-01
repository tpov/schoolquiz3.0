package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

data class QuestAuthoringBundle(
    val draft: QuestDraft,
    val sections: List<DraftSection>,
    val themes: List<DraftTheme>,
    val lessons: List<DraftLesson>,
    val questions: List<DraftQuestion>,
) {
    init {
        val draftId = draft.id
        require(sections.all { it.draftId == draftId }) { "All sections must belong to draft ${draftId.value}" }
        require(themes.all { it.draftId == draftId }) { "All themes must belong to draft ${draftId.value}" }
        require(lessons.all { it.draftId == draftId }) { "All lessons must belong to draft ${draftId.value}" }
        require(questions.all { it.draftId == draftId }) { "All questions must belong to draft ${draftId.value}" }

        val sectionIds = sections.map { it.id }.toSet()
        require(themes.all { it.sectionId in sectionIds }) { "All themes must reference an existing section" }

        val themeIds = themes.map { it.id }.toSet()
        require(lessons.all { it.themeId in themeIds }) { "All lessons must reference an existing theme" }

        val lessonIds = lessons.map { it.id }.toSet()
        require(questions.all { it.lessonId in lessonIds }) { "All questions must reference an existing lesson" }

        require(sections.map { it.id }.toSet().size == sections.size) { "Section ids must be unique" }
        require(themes.map { it.id }.toSet().size == themes.size) { "Theme ids must be unique" }
        require(lessons.map { it.id }.toSet().size == lessons.size) { "Lesson ids must be unique" }
        require(questions.map { it.id }.toSet().size == questions.size) { "Question ids must be unique" }
    }
}
