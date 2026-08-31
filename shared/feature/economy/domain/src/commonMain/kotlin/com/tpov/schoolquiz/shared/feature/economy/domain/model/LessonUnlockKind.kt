package com.tpov.schoolquiz.shared.feature.economy.domain.model

/**
 * What a nolic purchase opens on a lesson.
 *
 * Two things, priced apart, because one unlock would otherwise hand over both difficulties at once.
 * The wire names are the server's own, so a rename here is a protocol change, not a refactor.
 */
enum class LessonUnlockKind(val wireName: String) {
    /** Buys past the sequential gate: the lesson opens without the one before it being passed. */
    LESSON("lesson"),

    /** Buys the lesson's hard mode, otherwise earned by clearing every easy question. */
    HARD_MODE("hardMode"),

    ;

    /** How an unlock is keyed in [EconomyResourceBalance.lessonUnlocks]. */
    fun keyFor(lessonId: String): String = "$wireName:$lessonId"
}
