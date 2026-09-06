package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestShelfChange
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestShelfSyncState
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeQuestRepository : QuestRepository {

    private val store = MutableStateFlow<List<Quest>>(emptyList())

    /** Намерения переноса, отданные в очередь, — в порядке поступления. */
    val queuedShelfChanges = mutableListOf<QuestShelfChange>()

    /** Сколько раз компонент просил перечитать квесты по id. */
    val refreshedIds = mutableListOf<Set<QuestId>>()

    /** Что «очередь» знает о переносе квеста — по владельцу и квесту. */
    private val shelfSync = MutableStateFlow<Map<Pair<String, QuestId>, QuestShelfSyncState>>(emptyMap())

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

    override suspend fun refreshByIds(ids: Set<QuestId>): Result<Unit> {
        refreshedIds += ids
        return Result.success(Unit)
    }

    override suspend fun refreshFromRemote(
        currentUserUid: String?,
        availableShelves: Set<String>,
        catalogIdsToSync: Set<CatalogId>,
        cursor: Long,
    ): Result<Set<QuestId>> = Result.success(emptySet())

    /** Только намерение: строка квеста не меняется (AD-25). Очередь отвечает «ждёт». */
    override suspend fun queueShelfChange(change: QuestShelfChange): Result<Unit> {
        queuedShelfChanges += change
        shelfSync.update { it + ((change.ownerUid to change.questId) to QuestShelfSyncState.PENDING) }
        return Result.success(Unit)
    }

    override fun observeShelfChangeState(
        ownerUid: String,
        questId: QuestId,
    ): Flow<QuestShelfSyncState> =
        shelfSync.map { it[ownerUid to questId] ?: QuestShelfSyncState.NONE }

    fun emit(quests: List<Quest>) { store.value = quests }

    /** Тест переводит запись очереди в нужное состояние — как это сделал бы движок. */
    fun setShelfChangeState(
        ownerUid: String,
        questId: QuestId,
        state: QuestShelfSyncState,
    ) {
        shelfSync.update { it + ((ownerUid to questId) to state) }
    }
}
