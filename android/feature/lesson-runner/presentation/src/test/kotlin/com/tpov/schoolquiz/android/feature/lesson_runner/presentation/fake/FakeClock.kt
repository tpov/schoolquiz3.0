package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FakeClock(var nowMillis: Long = 1_000_000L) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(nowMillis)
}
