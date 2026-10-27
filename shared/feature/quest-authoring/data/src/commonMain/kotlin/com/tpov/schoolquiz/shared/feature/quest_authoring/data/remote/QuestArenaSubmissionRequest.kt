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
    /**
     * Настройки приватной ссылки (AD-29) — три авторских поля. `null` у всякого неприватного
     * квеста: поле на проводе отсутствует, и сервер настроек за него не пишет. Форма и дефолты
     * — `PrivateQuizSettings` домена; здесь только провод.
     */
    val privateQuiz: ArenaPrivateQuizSettingsDto? = null,
)

/**
 * Настройки приватной ссылки на проводе.
 *
 * Рантайм ссылки (`enabled`, `startedCount`) сюда не входит принципиально: их место — документ
 * на сервере, и присланные с устройства значения не имеют силы закрыть или приоткрыть ссылку.
 */
data class ArenaPrivateQuizSettingsDto(
    val attemptLimit: Int,
    val showResult: Boolean,
    val linkExpiresAtMs: Long?,
) {
    init {
        require(attemptLimit >= 1) { "ArenaPrivateQuizSettingsDto.attemptLimit must be >= 1, got $attemptLimit" }
        require(linkExpiresAtMs == null || linkExpiresAtMs >= 0) {
            "ArenaPrivateQuizSettingsDto.linkExpiresAtMs must be null or >= 0, got $linkExpiresAtMs"
        }
    }
}

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
