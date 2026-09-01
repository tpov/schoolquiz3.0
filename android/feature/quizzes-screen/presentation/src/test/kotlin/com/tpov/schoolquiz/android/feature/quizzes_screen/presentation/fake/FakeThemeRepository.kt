package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeThemeRepository : ThemeRepository {
    private val store = MutableStateFlow<List<Theme>>(emptyList())

    fun emit(themes: List<Theme>) { store.value = themes }

    override fun observeBySection(sectionId: SectionId): Flow<List<Theme>> =
        store.map { list -> list.filter { it.sectionId == sectionId } }

    override suspend fun getById(id: ThemeId): Theme? = store.value.find { it.id == id }

    override suspend fun refreshByParents(sectionIds: Set<SectionId>, cursor: Long): Result<Set<ThemeId>> =
        Result.success(emptySet())
}
