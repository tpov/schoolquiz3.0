package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLessonRepository : LessonRepository {
    private val store = MutableStateFlow<List<Lesson>>(emptyList())

    fun emit(lessons: List<Lesson>) { store.value = lessons }

    override fun observeByTheme(themeId: ThemeId): Flow<List<Lesson>> =
        store.map { list -> list.filter { it.themeId == themeId } }

    override suspend fun getById(id: LessonId): Lesson? = store.value.find { it.id == id }

    override suspend fun refreshByParents(themeIds: Set<ThemeId>, cursor: Long): Result<Set<LessonId>> =
        Result.success(emptySet())
}
