package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * По-вопросная оценка TESTING-ревью (4.6): один независимый ревью на каждый вопрос ревизии.
 * Шкала та же, что у урока, 1..3; состав вопросов называет задание, а не провод.
 */
data class ReviewQuestionScore(
    val questionId: String,
    val score: Int,
) {
    init {
        require(questionId.isNotBlank()) { "ReviewQuestionScore.questionId must not be blank" }
        require(score in MIN_REVIEW_SCORE..MAX_REVIEW_SCORE) {
            "ReviewQuestionScore.score must be in $MIN_REVIEW_SCORE..$MAX_REVIEW_SCORE, got $score"
        }
    }

    companion object {
        const val MIN_REVIEW_SCORE: Int = 1
        const val MAX_REVIEW_SCORE: Int = 3
    }
}
