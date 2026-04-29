package com.tpov.schoolquiz.platform.firebase.section

import com.google.firebase.firestore.DocumentSnapshot
import com.tpov.schoolquiz.shared.feature.section.data.dto.SectionDto

fun DocumentSnapshot.toSectionDto(): SectionDto =
    SectionDto(
        id = id,
        questId = getString("questId") ?: "",
        title = getString("title") ?: "",
        order = getLong("order")?.toInt() ?: 0,
        version = getLong("version") ?: 1L,
        contentsVersion = getLong("contentsVersion") ?: 0L,
        lastModifiedAt = getTimestamp("lastModifiedAt")?.toDate()?.time ?: 0L,
        archived = getBoolean("archived") ?: false,
    )
