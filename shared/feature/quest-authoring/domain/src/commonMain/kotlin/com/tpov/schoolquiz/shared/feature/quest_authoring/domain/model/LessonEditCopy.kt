package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * Редактируемая копия чужого урока (AD-33, история 4.23, UX-23): то, что сервер собирает
 * callable `buildLessonEditCopy` из опубликованных вопросов.
 *
 * Редактируемые строки ([LessonEditCopyRow.editable]) едут публичными половинами без ключа —
 * верный ответ в копии отмечает предложивший, «Факты» его проверяет. Запертые строки
 * (Survey, легаси-диалекты) приезжают байт-в-байт как опубликованы: править их копия не даёт,
 * и применения они не боятся — сверяются побайтово.
 */
data class LessonEditCopy(
    val lessonId: String,
    val lessonTitle: String,
    /** Версия урока, под которую собрана копия, — серверная правда для STALE-сверки решения. */
    val baseRevision: Int,
    val questions: List<LessonEditCopyRow>,
)

/** Строка копии — ровно то, что строгий парсер конверта `lesson-edit-copy-v1` читает. */
data class LessonEditCopyRow(
    val id: String,
    val lessonId: String,
    val type: String,
    val language: String,
    val languageLevel: Int,
    val difficulty: String,
    val order: Int,
    val text: String,
    val imagePath: String?,
    /** JSON-строка payload: публичная половина у редактируемых, опубликованные байты у запертых. */
    val payload: String,
    val editable: Boolean,
)
