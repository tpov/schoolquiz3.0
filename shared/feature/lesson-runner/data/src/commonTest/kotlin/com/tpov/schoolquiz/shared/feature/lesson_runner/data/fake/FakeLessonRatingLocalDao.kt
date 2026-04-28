package com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake

import com.tpov.schoolquiz.shared.core.persistence.LessonRatingLocalDao
import com.tpov.schoolquiz.shared.core.persistence.LessonRatingSubmittedLocalEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLessonRatingLocalDao : LessonRatingLocalDao {

    private val submitted = mutableSetOf<Pair<String, String>>()
    private val _trigger = MutableStateFlow(0)

    override suspend fun upsert(entity: LessonRatingSubmittedLocalEntity): Long {
        submitted.add(entity.userId to entity.lessonId)
        _trigger.value++
        return _trigger.value.toLong()
    }

    override fun hasSubmitted(userId: String, lessonId: String): Flow<Boolean> =
        _trigger.map { submitted.contains(userId to lessonId) }
}
