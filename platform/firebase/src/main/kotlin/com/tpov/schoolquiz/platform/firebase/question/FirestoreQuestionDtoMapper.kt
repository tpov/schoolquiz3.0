package com.tpov.schoolquiz.platform.firebase.question

import com.google.firebase.firestore.DocumentSnapshot
import com.tpov.schoolquiz.shared.feature.question.data.dto.QuestionDto

fun DocumentSnapshot.toQuestionDto(): QuestionDto =
    QuestionDto(
        id = id,
        lessonId = getString("lessonId") ?: "",
        text = getString("text") ?: "",
        payload = getString("payload") ?: "",
        language = getString("language") ?: "",
        order = getLong("order")?.toInt() ?: 0,
        version = getLong("version") ?: 1L,
        lastModifiedAt = getTimestamp("lastModifiedAt")?.toDate()?.time ?: 0L,
        archived = getBoolean("archived") ?: false,
    )
