package com.tpov.schoolquiz.shared.feature.internet.profile.data

import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptDao
import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptEntity
import com.tpov.schoolquiz.shared.core.persistence.LessonResultAttemptOutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestionAnswerEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestionRepetitionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

private const val DAY_MS = 24L * 60 * 60 * 1000

/** 2026-08-24, 12:00 UTC — mid-afternoon, so a few hours either way stays inside the same day. */
private val NOW = Instant.parse("2026-08-24T12:00:00Z").toEpochMilliseconds()

class ActivityRepositoryImplTest {
    @Test
    fun observeDailyActivity_returnsOneEntryPerDayOldestFirst() = runTest {
        val dao = FakeAttemptDao()
        dao.add("uid-1", NOW)
        dao.add("uid-1", NOW - DAY_MS)
        dao.add("uid-1", NOW - DAY_MS)

        val activity = repository(dao).observeDailyActivity(days = 14).first()

        assertEquals(14, activity.size)
        assertEquals(1, activity.last())
        assertEquals(2, activity[12])
        assertEquals(0, activity.first())
    }

    @Test
    fun observeDailyActivity_countsQuietDaysAsZeroRatherThanDroppingThem() = runTest {
        val dao = FakeAttemptDao()
        dao.add("uid-1", NOW)
        dao.add("uid-1", NOW - 3 * DAY_MS)

        val activity = repository(dao).observeDailyActivity(days = 14).first()

        // Two days played, two gaps between them — a chart that dropped the gaps would draw the
        // same shape as playing four days running.
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1), activity)
    }

    @Test
    fun observeDailyActivity_ignoresAttemptsOlderThanTheWindow() = runTest {
        val dao = FakeAttemptDao()
        dao.add("uid-1", NOW - 20 * DAY_MS)

        val activity = repository(dao).observeDailyActivity(days = 14).first()

        assertEquals(List(14) { 0 }, activity)
    }

    @Test
    fun observeDailyActivity_countsOnlyTheSignedInPlayer() = runTest {
        val dao = FakeAttemptDao()
        dao.add("uid-1", NOW)
        dao.add("uid-2", NOW)
        dao.add("uid-2", NOW)

        val activity = repository(dao).observeDailyActivity(days = 14).first()

        assertEquals(1, activity.last())
    }

    @Test
    fun observeDailyActivity_returnsAllZerosWhenSignedOut() = runTest {
        val dao = FakeAttemptDao()
        dao.add("uid-1", NOW)

        val activity = repository(dao, uid = null).observeDailyActivity(days = 14).first()

        assertEquals(List(14) { 0 }, activity)
    }

    /**
     * Two attempts twenty minutes apart across midnight are two days, not one.
     *
     * This is the whole reason the buckets are calendar days: counting back in 24-hour blocks from
     * "now" would land both in the same bucket and quietly under-report the streak.
     */
    @Test
    fun observeDailyActivity_splitsAttemptsThatStraddleMidnight() = runTest {
        val dao = FakeAttemptDao()
        dao.add("uid-1", Instant.parse("2026-08-23T23:50:00Z").toEpochMilliseconds())
        dao.add("uid-1", Instant.parse("2026-08-24T00:10:00Z").toEpochMilliseconds())

        val activity = repository(dao).observeDailyActivity(days = 14).first()

        assertEquals(1, activity[12])
        assertEquals(1, activity[13])
    }

    /** A clock nudged backwards must not make finished work disappear from the chart. */
    @Test
    fun observeDailyActivity_keepsAttemptsDatedAfterToday() = runTest {
        val dao = FakeAttemptDao()
        dao.add("uid-1", NOW + 2 * DAY_MS)

        val activity = repository(dao).observeDailyActivity(days = 14).first()

        assertEquals(1, activity.last())
    }

    @Test
    fun observeDailyActivity_neverReturnsAnEmptyChart() = runTest {
        val activity = repository(FakeAttemptDao()).observeDailyActivity(days = 0).first()

        assertEquals(1, activity.size)
    }

    private fun repository(
        dao: LessonAttemptDao,
        uid: String? = "uid-1",
    ) = ActivityRepositoryImpl(
        attemptDao = dao,
        currentUidFlow = { flowOf(uid) },
        nowMs = { NOW },
        timeZone = { TimeZone.UTC },
    )
}

private class FakeAttemptDao : LessonAttemptDao {
    private val rows = MutableStateFlow<List<LessonAttemptEntity>>(emptyList())

    fun add(
        userId: String,
        completedAt: Long,
    ) {
        rows.value =
            rows.value +
                LessonAttemptEntity(
                    attemptId = "${userId}_$completedAt-${rows.value.size}",
                    userId = userId,
                    lessonId = "lesson",
                    lessonVersion = 1L,
                    isHard = 0,
                    codeAnswer = "1",
                    percentScore = 100,
                    completedAt = completedAt,
                )
    }

    override suspend fun upsert(entity: LessonAttemptEntity): Long = 0L

    override suspend fun upsertAnswers(entities: List<QuestionAnswerEntity>) = Unit

    override suspend fun upsertRepetitions(entities: List<QuestionRepetitionEntity>) = Unit

    override suspend fun upsertOutboxRow(entity: LessonResultAttemptOutboxEntity) = Unit

    override fun observeByLesson(
        userId: String,
        lessonId: String,
    ): Flow<List<LessonAttemptEntity>> = rows

    override fun observeAllByUser(userId: String): Flow<List<LessonAttemptEntity>> = rows

    override fun observeCompletionsSince(
        userId: String,
        sinceMs: Long,
    ): Flow<List<Long>> =
        rows.map { list ->
            list.filter { it.userId == userId && it.completedAt >= sinceMs }
                .map { it.completedAt }
                .sorted()
        }
}
