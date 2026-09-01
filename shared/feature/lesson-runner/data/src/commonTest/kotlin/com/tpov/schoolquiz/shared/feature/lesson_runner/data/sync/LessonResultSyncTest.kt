package com.tpov.schoolquiz.shared.feature.lesson_runner.data.sync

import com.tpov.schoolquiz.shared.core.persistence.LessonResultAttemptOutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.LessonResultSyncOutboxDao
import com.tpov.schoolquiz.shared.core.persistence.QuestRatingOutboxEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.LessonResultAttemptEvent
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.LessonResultRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.QuestRatingEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Живой дефект, ради которого эпик и начался: одна устойчиво отвергаемая попытка урока навсегда
 * останавливала отправку всех оценок квестов — исключение уходило наверх до того, как до них
 * доходило управление.
 */
class LessonResultSyncTest {

    private val now = 1_700_000_000_000L

    private fun attempt(id: String) =
        LessonResultAttemptOutboxEntity(
            attemptId = id,
            userId = "u",
            scope = "public",
            ownerUid = null,
            catalogId = "c",
            questId = "q",
            sectionId = "s",
            themeId = "t",
            lessonId = "l",
            lessonVersion = 1L,
            sourceShelf = "home",
            difficulty = "EASY",
            codeAnswer = "999",
            percentScore = 100,
            completedAtMs = now,
            createdAtMs = now,
        )

    private fun rating(id: String) =
        QuestRatingOutboxEntity(
            ratingId = id,
            userId = "u",
            scope = "public",
            ownerUid = null,
            catalogId = "c",
            questId = "q",
            sectionId = "s",
            themeId = "t",
            lessonId = "l",
            lessonVersion = 1L,
            sourceShelf = "home",
            rating = 5,
            ratedAtMs = now,
            createdAtMs = now,
        )

    private class FakeDao(
        var attempts: List<LessonResultAttemptOutboxEntity> = emptyList(),
        var ratings: List<QuestRatingOutboxEntity> = emptyList(),
    ) : LessonResultSyncOutboxDao {
        val attemptsSent = mutableListOf<String>()
        val ratingsSent = mutableListOf<String>()
        val attemptsFailed = mutableListOf<String>()
        val ratingsFailed = mutableListOf<String>()

        override suspend fun upsertAttempt(entity: LessonResultAttemptOutboxEntity) = Unit

        override suspend fun upsertRating(entity: QuestRatingOutboxEntity) = Unit

        override suspend fun pendingAttempts(limit: Int) = attempts

        override suspend fun pendingRatings(limit: Int) = ratings

        override suspend fun markAttemptsSent(
            ids: List<String>,
            sentAtMs: Long,
        ) {
            attemptsSent += ids
        }

        override suspend fun markRatingsSent(
            ids: List<String>,
            sentAtMs: Long,
        ) {
            ratingsSent += ids
        }

        override suspend fun markAttemptsFailed(
            ids: List<String>,
            error: String,
        ) {
            attemptsFailed += ids
        }

        override suspend fun markRatingsFailed(
            ids: List<String>,
            error: String,
        ) {
            ratingsFailed += ids
        }
    }

    private class FakeRemote(
        private val failAttempts: Boolean = false,
        private val failRatings: Boolean = false,
    ) : LessonResultRemoteDataSource {
        var attemptCalls = 0
        var ratingCalls = 0

        override suspend fun submitAttempts(attempts: List<LessonResultAttemptEvent>) {
            attemptCalls++
            if (failAttempts) throw IllegalStateException("server said no")
        }

        override suspend fun submitRatings(ratings: List<QuestRatingEvent>) {
            ratingCalls++
            if (failRatings) throw IllegalStateException("server said no")
        }
    }

    @Test
    fun `given attempts keep failing then ratings are still sent`() = runTest {
        // Ровно тот сценарий, который сегодня ломается.
        val dao = FakeDao(attempts = listOf(attempt("a1")), ratings = listOf(rating("r1")))
        val remote = FakeRemote(failAttempts = true)

        val result = LessonResultSync(dao, remote, nowMs = { now }).sync()

        assertEquals(1, remote.ratingCalls, "оценки обязаны уехать несмотря на отказ попытки")
        assertEquals(listOf("r1"), dao.ratingsSent)
        assertEquals(listOf("a1"), dao.attemptsFailed)
        assertTrue(result.isFailure, "но неудача всё равно видна наверху")
    }

    @Test
    fun `given ratings fail then attempts are still sent`() = runTest {
        val dao = FakeDao(attempts = listOf(attempt("a1")), ratings = listOf(rating("r1")))
        val remote = FakeRemote(failRatings = true)

        val result = LessonResultSync(dao, remote, nowMs = { now }).sync()

        assertEquals(listOf("a1"), dao.attemptsSent)
        assertEquals(listOf("r1"), dao.ratingsFailed)
        assertTrue(result.isFailure)
    }

    @Test
    fun `given both succeed then the run reports success`() = runTest {
        val dao = FakeDao(attempts = listOf(attempt("a1")), ratings = listOf(rating("r1")))
        val remote = FakeRemote()

        val result = LessonResultSync(dao, remote, nowMs = { now }).sync()

        assertTrue(result.isSuccess)
        assertEquals(listOf("a1"), dao.attemptsSent)
        assertEquals(listOf("r1"), dao.ratingsSent)
    }

    @Test
    fun `given both fail then the run reports the first failure and still tried both`() = runTest {
        val dao = FakeDao(attempts = listOf(attempt("a1")), ratings = listOf(rating("r1")))
        val remote = FakeRemote(failAttempts = true, failRatings = true)

        val result = LessonResultSync(dao, remote, nowMs = { now }).sync()

        assertEquals(1, remote.attemptCalls)
        assertEquals(1, remote.ratingCalls, "вторая очередь обязана быть опробована")
        assertTrue(result.isFailure)
    }
}
