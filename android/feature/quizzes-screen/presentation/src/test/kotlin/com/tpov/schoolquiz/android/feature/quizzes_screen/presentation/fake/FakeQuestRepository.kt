package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeQuestRepository : QuestRepository {

    private val store = MutableStateFlow<List<Quest>>(emptyList())
    val shelfSetRequests = mutableListOf<Pair<QuestId, String>>()

    override fun observeMyQuests(authorUid: String, catalogId: CatalogId?): Flow<List<Quest>> =
        store.map { list ->
            list.filter { q ->
                q.authorUid == authorUid &&
                    (catalogId == null || q.catalogId == catalogId)
            }
        }

    override fun observeByShelf(shelf: String): Flow<List<Quest>> =
        store.map { list -> list.filter { shelf in it.visibleOn } }

    override fun observeByCatalog(catalogId: CatalogId, shelf: String): Flow<List<Quest>> =
        store.map { list ->
            list.filter { q ->
                q.catalogId == catalogId && shelf in q.visibleOn
            }.sortedByDescending { it.lastModifiedAt }
        }

    override suspend fun getById(id: QuestId): Quest? = store.value.find { it.id == id }

    override suspend fun refreshFromRemote(
        currentUserUid: String?,
        availableShelves: Set<String>,
        catalogIdsToSync: Set<CatalogId>,
        cursor: Long,
    ): Result<Set<QuestId>> = Result.success(emptySet())

    override suspend fun setPublicShelf(
        questId: QuestId,
        targetShelf: String,
    ): Result<Unit> {
        shelfSetRequests += questId to targetShelf
        return Result.success(Unit)
    }

    fun emit(quests: List<Quest>) { store.value = quests }
}
