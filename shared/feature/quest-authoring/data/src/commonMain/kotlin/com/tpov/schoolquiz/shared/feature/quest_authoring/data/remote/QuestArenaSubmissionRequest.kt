package com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote

data class QuestArenaSubmissionRequest(
    val submissionId: String,
    val draftId: String,
    val ownerUid: String,
    /**
     * Версия квеста на сервере, на которую автор опирался, подавая заявку (AD-24).
     *
     * Не счётчик правок автора: тот считает, сколько раз он трогал черновик, и к тому, разошлась
     * ли работа с чужой, отношения не имеет. Пусто — квест ещё не публиковался, сверять не с чем.
     */
    val expectedVersion: Long?,
    val requestedAtMs: Long,
    val draft: ArenaDraftDto,
    val sections: List<ArenaSectionDto>,
    val themes: List<ArenaThemeDto>,
    val lessons: List<ArenaLessonDto>,
    val questions: List<ArenaQuestionDto>,
    val review: ArenaReviewDto,
    val targetShelf: String = "arena",
    val targetLessonIds: Set<String> = emptySet(),
    /**
     * Полный состав черновика — все разделы, темы и уроки, а не только попавшие в порцию.
     *
     * Заявка несёт порцию: выбранные уроки, их темы и их разделы. По ней одной «урок удалён» и
     * «урок не в этой порции» на проводе неразличимы, и сервер без этого поля снимает только
     * вопрос целевого урока. Поле объявляет, что у автора вообще осталось, и включает снятие
     * урока, темы и раздела.
     *
     * По контракту поле необязательное (AD-13): сервер, не увидев его, работает по-старому, а
     * старый клиент, его не шлющий, продолжает публиковать. Этот клиент шлёт его всегда.
     */
    val draftTree: ArenaDraftTreeDto = ArenaDraftTreeDto(),
)

/**
 * Три списка идентификаторов черновика целиком.
 *
 * Пустой черновик — три пустых списка, а не пропущенное поле: сервер сам решает, что по заявке без
 * единого урока снимать нечего (`normalizeDraftTree`), и молчание клиента ему для этого не нужно.
 */
data class ArenaDraftTreeDto(
    val sections: Set<String> = emptySet(),
    val themes: Set<String> = emptySet(),
    val lessons: Set<String> = emptySet(),
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
