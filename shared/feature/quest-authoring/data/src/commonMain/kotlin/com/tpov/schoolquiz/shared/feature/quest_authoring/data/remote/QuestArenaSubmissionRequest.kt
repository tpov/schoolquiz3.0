package com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote

data class QuestArenaSubmissionRequest(
    val submissionId: String,
    val draftId: String,
    val ownerUid: String,
    val localRevision: Long,
    val requestedAtMs: Long,
    val draft: ArenaDraftDto,
    val sections: List<ArenaSectionDto>,
    val themes: List<ArenaThemeDto>,
    val lessons: List<ArenaLessonDto>,
    val questions: List<ArenaQuestionDto>,
    val review: ArenaReviewDto,
    val targetShelf: String = "arena",
    val targetLessonIds: Set<String> = emptySet(),
)

data class ArenaReviewDto(
    val isTested: Boolean = false,
    val testingScore: Double? = null,
    val isLogicReviewed: Boolean = false,
    val logicScore: Double? = null,
    val isTranslationReviewed: Boolean = false,
    val translationScore: Int? = null,
    val translatedLanguages: Map<String, Int> = emptyMap(),
) {
    init {
        require(testingScore == null || testingScore in 0.0..3.0) {
            "ArenaReviewDto.testingScore must be null or in 0.0..3.0"
        }
        require(logicScore == null || logicScore in 0.0..3.0) {
            "ArenaReviewDto.logicScore must be null or in 0.0..3.0"
        }
        require(translationScore == null || translationScore in 0..100) {
            "ArenaReviewDto.translationScore must be null or in 0..100"
        }
        require(translatedLanguages.values.all { it >= 0 }) {
            "ArenaReviewDto.translatedLanguages levels must be >= 0"
        }
    }
}

data class ArenaDraftDto(
    val id: String,
    val catalogId: String,
    val title: String,
    val description: String?,
    val defaultLanguage: String,
    val defaultDifficulty: String,
    val publicQuestId: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

data class ArenaSectionDto(
    val id: String,
    val draftId: String,
    val title: String,
    val order: Int,
)

data class ArenaThemeDto(
    val id: String,
    val draftId: String,
    val sectionId: String,
    val title: String,
    val order: Int,
)

data class ArenaLessonDto(
    val id: String,
    val draftId: String,
    val themeId: String,
    val title: String,
    val order: Int,
)

data class ArenaQuestionDto(
    val id: String,
    val draftId: String,
    val lessonId: String,
    val type: String,
    val language: String,
    val languageLevel: Int,
    val difficulty: String,
    val order: Int,
    val text: String,
    val imagePath: String?,
    val payload: String,
    val updatedAtMs: Long,
)
