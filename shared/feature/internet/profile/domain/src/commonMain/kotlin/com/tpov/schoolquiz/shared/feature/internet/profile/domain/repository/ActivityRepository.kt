package com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * How much the player has been playing lately.
 *
 * Counted from finished attempts already on the device, so the figure survives being offline —
 * which is the state the game is designed around. Nothing here is sent anywhere; it exists so the
 * profile can show a shape rather than a single lifetime total.
 */
interface ActivityRepository {
    /**
     * Attempts finished on each of the last [days] calendar days, oldest first.
     *
     * Always [days] long, zeros included. A chart that omitted quiet days would compress them out
     * of the shape and make a broken streak look like an unbroken one.
     */
    fun observeDailyActivity(days: Int): Flow<List<Int>>
}
