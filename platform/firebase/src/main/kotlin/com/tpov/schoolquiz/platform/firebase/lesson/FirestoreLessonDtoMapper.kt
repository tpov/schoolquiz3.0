package com.tpov.schoolquiz.platform.firebase.lesson

import com.google.firebase.firestore.DocumentSnapshot
import com.tpov.schoolquiz.platform.firebase.util.booleanField
import com.tpov.schoolquiz.platform.firebase.util.doubleField
import com.tpov.schoolquiz.platform.firebase.util.intField
import com.tpov.schoolquiz.platform.firebase.util.longField
import com.tpov.schoolquiz.platform.firebase.util.millisField
import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
import com.tpov.schoolquiz.shared.feature.lesson.data.dto.LessonDto

fun DocumentSnapshot.toLessonDto(): LessonDto =
    LessonDto(
        id = id,
        themeId = getString("themeId") ?: "",
        title = getString("title") ?: "",
        order = intField("order") ?: 0,
        version = longField("version") ?: 1L,
        lastModifiedAt = millisField("lastModifiedAt") ?: 0L,
        archived = booleanField("archived") ?: false,
        averageRating = doubleField("averageRating")?.toFloat(),
        ratingCount = intField("ratingCount"),
        top3 = parseTop3(this),
    )

@Suppress("UNCHECKED_CAST")
private fun parseTop3(doc: DocumentSnapshot): List<TopParticipant>? {
    val raw = doc.get("top3") as? List<*> ?: return null
    return raw.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val nickname = map["nickname"] as? String ?: return@mapNotNull null
        val avatarUrl = (map["avatarUrl"] as? String)?.takeIf { it.startsWith("https://") }
        val percent = (map["percent"] as? Number)?.toInt() ?: return@mapNotNull null
        TopParticipant(nickname = nickname, avatarUrl = avatarUrl, percent = percent)
    }
}
