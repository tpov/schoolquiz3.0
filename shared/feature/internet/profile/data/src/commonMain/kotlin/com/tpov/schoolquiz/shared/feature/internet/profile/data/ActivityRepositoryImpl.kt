package com.tpov.schoolquiz.shared.feature.internet.profile.data

import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptDao
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ActivityRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Counts finished attempts into calendar days.
 *
 * Days, not fixed 24-hour blocks. A player who finishes at 23:50 and again at 00:10 has played on
 * two days by every reckoning they would use themselves, and bucketing by elapsed hours would show
 * that as one. The zone is read per call rather than captured, so the chart follows the device
 * across a timezone change instead of keeping the old one until restart.
 */
class ActivityRepositoryImpl(
    private val attemptDao: LessonAttemptDao,
    private val currentUidFlow: () -> Flow<String?>,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ActivityRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDailyActivity(days: Int): Flow<List<Int>> {
        val window = days.coerceAtLeast(1)
        return currentUidFlow().flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(List(window) { 0 })
            } else {
                val zone = timeZone()
                val today = Instant.fromEpochMilliseconds(nowMs()).toLocalDateTime(zone).date
                // Midnight of the first day in the window, so the query drops everything older
                // before the rows reach us.
                val firstDay = today.minus(window - 1, DateTimeUnit.DAY)
                val sinceMs = firstDay.atStartOfDayIn(zone).toEpochMilliseconds()
                attemptDao.observeCompletionsSince(uid, sinceMs).map { completions ->
                    val buckets = MutableList(window) { 0 }
                    completions.forEach { completedAt ->
                        val date = Instant.fromEpochMilliseconds(completedAt).toLocalDateTime(zone).date
                        val index = firstDay.daysUntil(date)
                        // A clock moved backwards can leave an attempt dated after today; it is
                        // still real activity, so it lands on the last day rather than vanishing.
                        if (index >= 0) buckets[index.coerceAtMost(window - 1)]++
                    }
                    buckets
                }
            }
        }
    }
}
