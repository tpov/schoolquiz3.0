package com.tpov.schoolquiz.shared.feature.internet.social.data.provider

import com.tpov.schoolquiz.shared.feature.internet.social.domain.provider.PollsIdProvider
import com.tpov.schoolquiz.shared.feature.internet.social.domain.provider.PollsTimestampProvider
import kotlinx.datetime.Clock
import kotlin.random.Random

/** Идентификатор намерения: префикс + случайный хвост, как у остальных фич. */
class DefaultPollsIdProvider : PollsIdProvider {
    override fun nextId(prefix: String): String {
        require(prefix.isNotBlank()) { "prefix must not be blank" }
        return "$prefix-${Random.nextLong(1, Long.MAX_VALUE).toString(36)}"
    }
}

/** Момент намерения — настенные часы устройства; серверное «сейчас» у приёмника своё. */
class DefaultPollsTimestampProvider : PollsTimestampProvider {
    override fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
}
