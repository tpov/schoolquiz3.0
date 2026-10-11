package com.tpov.schoolquiz.shared.feature.internet.social.domain.provider

/** Источник идентификаторов намерений опросов (AD-2): назначается один раз, в момент нажатия. */
interface PollsIdProvider {
    fun nextId(prefix: String): String
}

/** Часы намерений опросов: момент постановки в очередь и фиксации набора. */
interface PollsTimestampProvider {
    fun nowMs(): Long
}
