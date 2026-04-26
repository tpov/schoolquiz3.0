package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.section.domain.model.Section
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSectionRepository : SectionRepository {
    private val store = MutableStateFlow<List<Section>>(emptyList())

    fun emit(sections: List<Section>) { store.value = sections }

    override fun observeByQuest(questId: QuestId): Flow<List<Section>> =
        store.map { list -> list.filter { it.questId == questId } }

    override suspend fun getById(id: SectionId): Section? = store.value.find { it.id == id }

    override suspend fun refreshByParents(questIds: Set<QuestId>, cursor: Long): Result<Set<SectionId>> =
        Result.success(emptySet())

    override suspend fun getLocalContentsVersion(id: SectionId): Long? = null
}
