package com.tpov.schoolquiz.data.fierbase

import com.google.firebase.database.IgnoreExtraProperties
import com.tpov.schoolquiz.data.database.entities.TranslatorInfoEntity

@IgnoreExtraProperties
data class TranslatorInfo(
    val oldText: String,
    val newText: String,
    val idLastTranslator: Int,
) {
    constructor() : this(
        "", "",  0
    )
}

fun TranslatorInfo.toTranslatorInfoEntity(
    id: Int?,
    idQuiz: Int,
    numQuestion: Int,
    idThisTranslator: Int,
    language: String,
    lvlTranslator: Int,
    rating: Int,
): TranslatorInfoEntity {
    return TranslatorInfoEntity(
        id = id,
        oldText = this.oldText,
        newText = this.newText,
        idQuiz = idQuiz,
        numQuestion = numQuestion,
        idThisTranslator = idThisTranslator,
        idLastTranslator = this.idLastTranslator,
        language = language,
        lvlTranslator = lvlTranslator,
        rating = rating,
    )
}
