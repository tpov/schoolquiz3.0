package com.tpov.schoolquiz.platform.firebase.quest

import com.google.firebase.firestore.DocumentSnapshot
import com.tpov.schoolquiz.platform.firebase.util.isValidRelativePath
import com.tpov.schoolquiz.shared.feature.quest.data.dto.QuestDto

fun DocumentSnapshot.toQuestDto(): QuestDto {
    val rawPath = getString("picturePath")
    val picturePath = if (rawPath != null && isValidRelativePath(rawPath)) rawPath else null
    return QuestDto(
        id = id,
        catalogId = getString("catalogId") ?: "",
        authorUid = getString("authorUid") ?: "",
        title = getString("title") ?: "",
        picturePath = picturePath,
        visibleOn = (get("visibleOn") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        averageRating = getDouble("averageRating"),
        averageRatingCount = getLong("averageRatingCount")?.toInt() ?: 0,
        version = getLong("version") ?: 1L,
        contentsVersion = getLong("contentsVersion") ?: 0L,
        lastModifiedAt = getTimestamp("lastModifiedAt")?.toDate()?.time ?: 0L,
        archived = getBoolean("archived") ?: false,
    )
}
