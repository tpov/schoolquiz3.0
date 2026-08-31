package com.tpov.schoolquiz.shared.feature.lesson.domain.logic

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId

/**
 * Which lessons of a course are shut, and why.
 *
 * Inside a course the lessons open one at a time: the next is shut until the previous is passed.
 * Nolics open any shut lesson outright — not only the next one — so a player who wants lesson ten
 * can buy their way straight to it. Buying grants access and nothing else; the lesson it skipped
 * keeps its zero stars, which is what stops a whole theme being bought open and its test with it.
 *
 * The rule is stated over ids alone so it can be decided on the device, immediately, from what a
 * lesson attempt already knows. Nothing here waits on a server.
 */
enum class LessonAccess {
    /** Open because the previous lesson was passed, or because it is the first. */
    OPEN,

    /** Open because it was bought, not because it was earned. */
    PURCHASED,

    /** Shut: the lesson before it is unpassed and it has not been bought. */
    LOCKED,
}

/**
 * Resolves access for every lesson of one course, in teaching order.
 *
 * @param orderedLessonIds the course's lessons in the order they are taught.
 * @param passed lessons whose easy questions were all answered correctly.
 * @param purchased lessons opened with nolics.
 * @return access per lesson, keyed by id. Ids absent from [orderedLessonIds] are not returned.
 */
fun resolveLessonAccess(
    orderedLessonIds: List<LessonId>,
    passed: Set<LessonId>,
    purchased: Set<LessonId>,
): Map<LessonId, LessonAccess> {
    var previousPassed = true // the first lesson has nothing before it to clear
    return orderedLessonIds.associateWith { id ->
        val access = when {
            previousPassed -> LessonAccess.OPEN
            id in purchased -> LessonAccess.PURCHASED
            else -> LessonAccess.LOCKED
        }
        // Only actually passing opens the next one. A bought lesson left unplayed stops the chain
        // exactly where it was, so buying lesson five does not hand over lesson six.
        previousPassed = id in passed
        access
    }
}
