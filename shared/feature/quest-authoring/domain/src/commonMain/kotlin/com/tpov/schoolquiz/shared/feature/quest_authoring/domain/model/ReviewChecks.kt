package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

data class ReviewChecks(
    val isTested: Boolean = false,
    val testingScore: Double? = null,
    val isLogicReviewed: Boolean = false,
    val logicScore: Double? = null,
    val isTranslationReviewed: Boolean = false,
    val translationScore: Int? = null,
    val translatedLanguages: Map<String, Int> = emptyMap(),
)
