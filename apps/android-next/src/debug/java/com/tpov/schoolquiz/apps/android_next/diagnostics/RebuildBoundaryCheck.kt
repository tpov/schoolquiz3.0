package com.tpov.schoolquiz.apps.android_next.diagnostics

import android.util.Log
import com.tpov.schoolquiz.shared.core.persistence.LessonResultSyncOutboxDao
import com.tpov.schoolquiz.shared.core.persistence.QuestArenaSubmissionDao
import com.tpov.schoolquiz.shared.core.persistence.QuestAuthoringDao
import com.tpov.schoolquiz.shared.core.persistence.RebuildBoundaryReport
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QUEST_DRAFT_STATUSES_REACHED_SERVER

/**
 * Reads the four numbers that decide whether the local database may be rebuilt.
 *
 * The rebuild throws the schema away once, and everything without a copy on the server goes with
 * it. This is the mechanical half of that decision: it reports what is still only here, so the
 * choice is made against counts rather than against a hope that the queues were empty.
 *
 * It lives in the debug source set rather than behind a runtime flag, because "not in the release
 * build" is a property of what is compiled, not of what happens to be skipped at startup.
 */
class RebuildBoundaryCheck(
    private val outboxDao: LessonResultSyncOutboxDao,
    private val arenaDao: QuestArenaSubmissionDao,
    private val authoringDao: QuestAuthoringDao,
) {
    suspend fun read(): RebuildBoundaryReport {
        val delivered = QUEST_DRAFT_STATUSES_REACHED_SERVER.map { it.name }
        val undeliveredDrafts = authoringDao.countDraftsNotDelivered(delivered)
        return RebuildBoundaryReport(
            lessonResultAttempts = outboxDao.countUnsentAttempts(),
            questRatings = outboxDao.countUnsentRatings(),
            arenaSubmissions = arenaDao.countPendingSubmissions(),
            undeliveredDrafts = undeliveredDrafts,
        )
    }

    /**
     * Prints all four counts and the verdict under one tag, so a single logcat filter finds it.
     *
     * A diagnostic that can take the app down with it is worse than no diagnostic: this is the
     * first thing to touch Room on a debug launch, so a database that fails to open is reported
     * here rather than thrown into the startup path.
     */
    suspend fun logReport() {
        val attempt = runCatching { read().render() }
        val rendered = attempt.getOrElse { failure -> "could not be read: $failure" }
        Log.i(TAG, "rebuild boundary\n" + rendered)
    }

    companion object {
        const val TAG: String = "RebuildBoundary"
    }
}
