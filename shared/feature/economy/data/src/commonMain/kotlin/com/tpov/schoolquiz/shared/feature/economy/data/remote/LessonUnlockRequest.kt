package com.tpov.schoolquiz.shared.feature.economy.data.remote

/** The price is not here on purpose: the server reads the lesson and decides what it costs. */
data class LessonUnlockRequest(
    val lessonId: String,
    val kind: String,
)
