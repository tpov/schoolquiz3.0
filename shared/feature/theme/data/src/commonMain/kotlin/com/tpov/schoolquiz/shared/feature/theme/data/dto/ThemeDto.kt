package com.tpov.schoolquiz.shared.feature.theme.data.dto

data class ThemeDto(
    val id: String,
    val sectionId: String,
    val title: String,
    val order: Int,
    val version: Long,
    val contentsVersion: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
)
