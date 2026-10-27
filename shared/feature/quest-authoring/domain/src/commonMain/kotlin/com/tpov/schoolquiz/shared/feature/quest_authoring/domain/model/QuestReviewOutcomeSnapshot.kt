package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * Авторский исход модельной проверки одной заявки (AD-32, история 4.16, UX-6).
 *
 * Серверная правда о том, как прошла проверка: статусы — те же, что называет вью; заваленные
 * вопросы едут с уроком, этапом и причиной; язык источника — деталь-строка; исход готов, но
 * статус ещё не доведён — флаг для клиента «попроси сервер довести».
 */
data class QuestReviewOutcomeSnapshot(
    val submissionId: String,
    val draftId: String,
    val status: String,
    val rejectionReason: String?,
    val failedQuestions: List<QuestionReviewFailure>,
    val detectedLanguage: String?,
    val outcomeReady: Boolean,
) {
    val isRejected: Boolean get() = status == STATUS_REJECTED
    val isPublished: Boolean get() = status == STATUS_PUBLISHED
    val isDelayed: Boolean get() = status == STATUS_REVIEW_DELAYED
    val isWaiting: Boolean get() = status == STATUS_UNDER_REVIEW || status == STATUS_PROCESSING

    companion object {
        const val STATUS_PROCESSING = "PROCESSING"
        const val STATUS_UNDER_REVIEW = "UNDER_REVIEW"
        const val STATUS_PUBLISHED = "PUBLISHED"
        const val STATUS_REJECTED = "REJECTED"
        const val STATUS_REVIEW_DELAYED = "REVIEW_DELAYED"
    }
}

/** Заваленный вопрос: урок, вопрос, этап («Слова»/«Факты»/«Перевод») и причина для автора. */
data class QuestionReviewFailure(
    val lessonId: String,
    val questionId: String,
    val stage: String,
    val reason: String,
)
