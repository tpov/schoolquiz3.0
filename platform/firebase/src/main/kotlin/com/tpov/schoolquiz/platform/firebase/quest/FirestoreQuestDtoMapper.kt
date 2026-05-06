package com.tpov.schoolquiz.platform.firebase.quest

import com.google.firebase.firestore.DocumentSnapshot
import com.tpov.schoolquiz.platform.firebase.util.booleanField
import com.tpov.schoolquiz.platform.firebase.util.doubleField
import com.tpov.schoolquiz.platform.firebase.util.isValidRelativePath
import com.tpov.schoolquiz.platform.firebase.util.longField
import com.tpov.schoolquiz.platform.firebase.util.millisField
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
        averageRating = doubleField("averageRating"),
        averageRatingCount = longField("averageRatingCount")?.toInt() ?: 0,
        version = longField("version") ?: 1L,
        contentsVersion = longField("contentsVersion") ?: 0L,
        lastModifiedAt = millisField("lastModifiedAt") ?: 0L,
        archived = booleanField("archived") ?: false,
    )
}
