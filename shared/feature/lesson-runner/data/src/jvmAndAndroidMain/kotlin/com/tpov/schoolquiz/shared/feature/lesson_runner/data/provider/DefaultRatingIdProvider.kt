package com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
import java.security.MessageDigest

class DefaultRatingIdProvider : RatingIdProvider {
    override fun provide(userId: String, lessonId: LessonId): RatingId {
        val input = "$userId:${lessonId.value}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return RatingId(hex)
    }
}
