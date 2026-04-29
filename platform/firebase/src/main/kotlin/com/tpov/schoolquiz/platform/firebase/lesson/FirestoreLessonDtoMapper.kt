package com.tpov.schoolquiz.platform.firebase.lesson

import com.google.firebase.firestore.DocumentSnapshot
import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
import com.tpov.schoolquiz.shared.feature.lesson.data.dto.LessonDto

fun DocumentSnapshot.toLessonDto(): LessonDto =
    LessonDto(
        id = id,
        themeId = getString("themeId") ?: "",
        title = getString("title") ?: "",
        order = getLong("order")?.toInt() ?: 0,
        version = getLong("version") ?: 1L,
        contentsVersion = getLong("contentsVersion") ?: 0L,
        lastModifiedAt = getTimestamp("lastModifiedAt")?.toDate()?.time ?: 0L,
        archived = getBoolean("archived") ?: false,
        averageRating = getDouble("averageRating")?.toFloat(),
        ratingCount = getLong("ratingCount")?.toInt(),
        top3 = parseTop3(this),
    )

@Suppress("UNCHECKED_CAST")
private fun parseTop3(doc: DocumentSnapshot): List<TopParticipant>? {
    val raw = doc.get("top3") as? List<*> ?: return null
    return raw.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val nickname = map["nickname"] as? String ?: return@mapNotNull null
        val avatarUrl = (map["avatarUrl"] as? String)?.takeIf { it.startsWith("https://") }
        val percent = (map["percent"] as? Long)?.toInt() ?: return@mapNotNull null
        TopParticipant(nickname = nickname, avatarUrl = avatarUrl, percent = percent)
    }
}
