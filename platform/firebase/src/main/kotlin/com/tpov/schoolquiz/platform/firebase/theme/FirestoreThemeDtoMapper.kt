package com.tpov.schoolquiz.platform.firebase.theme

import com.google.firebase.firestore.DocumentSnapshot
import com.tpov.schoolquiz.platform.firebase.util.booleanField
import com.tpov.schoolquiz.platform.firebase.util.intField
import com.tpov.schoolquiz.platform.firebase.util.longField
import com.tpov.schoolquiz.platform.firebase.util.millisField
import com.tpov.schoolquiz.shared.feature.theme.data.dto.ThemeDto

fun DocumentSnapshot.toThemeDto(): ThemeDto =
    ThemeDto(
        id = id,
        sectionId = getString("sectionId") ?: "",
        title = getString("title") ?: "",
        order = intField("order") ?: 0,
        version = longField("version") ?: 1L,
        contentsVersion = longField("contentsVersion") ?: 0L,
        lastModifiedAt = millisField("lastModifiedAt") ?: 0L,
        archived = booleanField("archived") ?: false,
    )
