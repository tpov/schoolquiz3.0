package com.tpov.schoolquiz.shared.feature.internet.profile.domain.model

/**
 * How much the account actually does, on the same six axes the qualifications sit on.
 *
 * Carried over from the legacy profile, where the radar drew these in green behind the roles in
 * red — the comparison is the point, since a role somebody holds but never uses looks nothing like
 * one they work at.
 *
 * Time in a quiz, time in chat and sms points have no source in this app yet and stay at zero.
 */
data class ProfileActivityRatings(
    val questionsAsked: Int = 0,
    val questionsRight: Int = 0,
    val timeInQuiz: Int = 0,
    val timeInChat: Int = 0,
    val smsPoints: Int = 0,
    val quizzes: Int = 0,
) {
    /**
     * The six figures in the order the radar draws them, each as a share of the largest.
     *
     * Relative rather than absolute, because they are counted in different units — questions,
     * seconds, points — and a shared axis of "how far along this one is compared to my best" is
     * the only reading that survives mixing them.
     */
    val axes: List<Float>
        get() {
            val raw = listOf(questionsAsked, questionsRight, timeInQuiz, timeInChat, smsPoints, quizzes)
            val peak = raw.maxOrNull() ?: 0
            return if (peak <= 0) List(raw.size) { 0f } else raw.map { it.toFloat() / peak }
        }
}
