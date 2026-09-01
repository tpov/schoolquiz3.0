package com.tpov.schoolquiz.shared.core.persistence

/**
 * What stands between the current database and the one-off rebuild of its schema.
 *
 * The rebuild is allowed only from a clean state, and "clean" is not an opinion: it is four counts.
 * Three are the queues holding work the server has not been told about yet, and the fourth is the
 * drafts that exist on this device and nowhere else. A rebuild taken while any of them is non-zero
 * destroys work somebody performed and was told was saved.
 *
 * Pure on purpose — it holds no database handle, so the numbers can be read on a device and the
 * verdict can be tested without one.
 */
data class RebuildBoundaryReport(
    val lessonResultAttempts: Int,
    val questRatings: Int,
    val arenaSubmissions: Int,
    val undeliveredDrafts: Int,
) {
    /** Table name against count, in the order the record lists them. */
    val counts: List<Pair<String, Int>>
        get() = listOf(
            TABLE_LESSON_RESULT_ATTEMPT_OUTBOX to lessonResultAttempts,
            TABLE_QUEST_RATING_OUTBOX to questRatings,
            TABLE_QUEST_ARENA_SUBMISSION_OUTBOX to arenaSubmissions,
            TABLE_QUEST_DRAFTS_UNDELIVERED to undeliveredDrafts,
        )

    /** Nothing is pending anywhere: the rebuild costs nothing that was not already accepted. */
    val isClean: Boolean
        get() = counts.all { (_, count) -> count == 0 }

    /** The tables still holding something, named so the reader knows where to look. */
    val blockingTables: List<String>
        get() = counts.filter { (_, count) -> count > 0 }.map { (table, _) -> table }

    /**
     * The four numbers as one block of text, always all four, with the verdict last.
     *
     * Every line is printed even when it is zero: a record that lists only what is non-empty cannot
     * be told apart from a record taken before the check ran.
     */
    fun render(): String {
        val lines = counts.map { (table, count) -> "$table: $count" }
        val verdict = if (isClean) {
            "CLEAN — the database may be rebuilt"
        } else {
            "BLOCKED — still holding rows in: ${blockingTables.joinToString(", ")}"
        }
        return (lines + verdict).joinToString("\n")
    }

    companion object {
        const val TABLE_LESSON_RESULT_ATTEMPT_OUTBOX: String = "lesson_result_attempt_outbox"
        const val TABLE_QUEST_RATING_OUTBOX: String = "quest_rating_outbox"
        const val TABLE_QUEST_ARENA_SUBMISSION_OUTBOX: String = "quest_arena_submission_outbox"
        const val TABLE_QUEST_DRAFTS_UNDELIVERED: String = "quest_drafts (not yet on the server)"
    }
}
