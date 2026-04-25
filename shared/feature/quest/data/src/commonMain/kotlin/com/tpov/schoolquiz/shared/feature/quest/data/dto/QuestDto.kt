package com.tpov.schoolquiz.shared.feature.quest.data.dto

data class QuestDto(
    val id: String,
    val catalogId: String,
    val authorUid: String,
    val title: String,
    val picturePath: String?,
    val visibleOn: List<String>,
    val averageRating: Double?,
    val averageRatingCount: Int,
    val version: Long,
    val contentsVersion: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
)
