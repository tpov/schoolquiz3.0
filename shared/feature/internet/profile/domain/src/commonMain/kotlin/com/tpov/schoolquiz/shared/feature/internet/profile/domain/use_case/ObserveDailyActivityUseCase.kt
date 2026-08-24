package com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow

/** Two weeks: long enough to show a habit, short enough that each day still gets drawable width. */
const val ACTIVITY_WINDOW_DAYS = 14

class ObserveDailyActivityUseCase(
    private val repository: ActivityRepository,
) {
    operator fun invoke(days: Int = ACTIVITY_WINDOW_DAYS): Flow<List<Int>> = repository.observeDailyActivity(days)
}
