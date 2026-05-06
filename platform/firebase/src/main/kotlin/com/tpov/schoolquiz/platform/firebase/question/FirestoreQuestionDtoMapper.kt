package com.tpov.schoolquiz.platform.firebase.question

import com.google.firebase.firestore.DocumentSnapshot
import com.tpov.schoolquiz.platform.firebase.util.booleanField
import com.tpov.schoolquiz.platform.firebase.util.intField
import com.tpov.schoolquiz.platform.firebase.util.longField
import com.tpov.schoolquiz.platform.firebase.util.millisField
import com.tpov.schoolquiz.shared.core.question_schema.QuestionLanguageLevel
import com.tpov.schoolquiz.shared.feature.question.data.dto.QuestionDto

fun DocumentSnapshot.toQuestionDto(): QuestionDto =
    QuestionDto(
        id = id,
        lessonId = getString("lessonId") ?: "",
        text = getString("text") ?: "",
        payload = getString("payload") ?: "",
        language = getString("language") ?: "",
        order = intField("order") ?: 0,
        version = longField("version") ?: 1L,
        lastModifiedAt = millisField("lastModifiedAt") ?: 0L,
        archived = booleanField("archived") ?: false,
        languageLevel =
            intField("languageLevel")
                ?.takeIf { it >= QuestionLanguageLevel.MIN }
                ?: QuestionLanguageLevel.DEFAULT,
    )
