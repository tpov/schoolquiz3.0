package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLessonRepository(
    private val lessons: MutableMap<String, Lesson> = mutableMapOf(),
) : LessonRepository {

    private val store = MutableStateFlow(lessons.toMap())

    fun addLesson(lesson: Lesson) {
        lessons[lesson.id.value] = lesson
        store.value = lessons.toMap()
    }

    override fun observeByTheme(themeId: ThemeId): Flow<List<Lesson>> =
        store.map { map -> map.values.filter { it.themeId == themeId }.sortedBy { it.order } }

    override suspend fun getById(id: LessonId): Lesson? = lessons[id.value]

    override suspend fun refreshByParents(themeIds: Set<ThemeId>, cursor: Long): Result<Set<LessonId>> =
        Result.success(emptySet())

    override suspend fun getLocalContentsVersion(id: LessonId): Long? = lessons[id.value]?.contentsVersion
}
