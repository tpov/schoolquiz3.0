package com.tpov.schoolquiz.shared.feature.section.data.dto

data class SectionDto(
    val id: String,
    val questId: String,
    val title: String,
    val order: Int,
    val version: Long,
    val contentsVersion: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
)
