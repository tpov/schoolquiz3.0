package com.tpov.common.data.model.remote

import com.tpov.common.data.model.entity.QuestionEntity

data class TranslateRequest(
    val question: QuestionEntity,
    val usePaidTranslation: Boolean,
    val toLangCode: String,
        )
