package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FakeClock(var nowMillis: Long = 1_000_000L) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(nowMillis)
}
