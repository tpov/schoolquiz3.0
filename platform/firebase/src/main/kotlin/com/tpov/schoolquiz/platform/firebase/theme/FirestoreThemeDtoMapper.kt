package com.tpov.schoolquiz.platform.firebase.theme

import com.google.firebase.firestore.DocumentSnapshot
import com.tpov.schoolquiz.shared.feature.theme.data.dto.ThemeDto

fun DocumentSnapshot.toThemeDto(): ThemeDto = ThemeDto(
    id = id,
    sectionId = getString("sectionId") ?: "",
    title = getString("title") ?: "",
    order = getLong("order")?.toInt() ?: 0,
    version = getLong("version") ?: 1L,
    contentsVersion = getLong("contentsVersion") ?: 0L,
    lastModifiedAt = getTimestamp("lastModifiedAt")?.toDate()?.time ?: 0L,
    archived = getBoolean("archived") ?: false,
)
