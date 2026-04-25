package com.tpov.schoolquiz.platform.firebase.lesson

import com.google.firebase.firestore.DocumentSnapshot
import com.tpov.schoolquiz.shared.feature.lesson.data.dto.LessonDto

fun DocumentSnapshot.toLessonDto(): LessonDto = LessonDto(
    id = id,
    themeId = getString("themeId") ?: "",
    title = getString("title") ?: "",
    order = getLong("order")?.toInt() ?: 0,
    version = getLong("version") ?: 1L,
    contentsVersion = getLong("contentsVersion") ?: 0L,
    lastModifiedAt = getTimestamp("lastModifiedAt")?.toDate()?.time ?: 0L,
    archived = getBoolean("archived") ?: false,
)
