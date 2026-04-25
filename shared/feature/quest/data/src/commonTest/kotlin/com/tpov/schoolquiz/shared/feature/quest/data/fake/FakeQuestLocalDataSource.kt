package com.tpov.schoolquiz.shared.feature.quest.data.fake

import com.tpov.schoolquiz.shared.core.persistence.QuestEntity
import com.tpov.schoolquiz.shared.feature.quest.data.QuestLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake QuestLocalDataSource for integration tests.
 * Signature Card: docs/features/home-and-my-quests/plan/phase-02/tests.md §2 FakeQuestLocalDataSource
 */
class FakeQuestLocalDataSource : QuestLocalDataSource {
    private val store = mutableMapOf<String, QuestEntity>()
    private val _flow = MutableStateFlow<List<QuestEntity>>(emptyList())

    val upsertCallsFor = mutableMapOf<String, Int>()
    val deletedIds = mutableListOf<String>()

    override fun observeMyQuests(authorUid: String, catalogId: String?): Flow<List<QuestEntity>> =
        _flow.map { list ->
            list.filter { entity ->
                entity.authorUid == authorUid &&
                    !entity.archived &&
                    (catalogId == null || entity.catalogId == catalogId)
            }
        }

    override fun observeByShelf(shelf: String): Flow<List<QuestEntity>> =
        _flow.map { list -> list.filter { shelf in it.visibleOn } }

    override suspend fun upsertByIdIfNewerVersion(entity: QuestEntity) {
        upsertCallsFor[entity.id] = (upsertCallsFor[entity.id] ?: 0) + 1
        val current = store[entity.id]
        if (current == null || current.version < entity.version) {
            store[entity.id] = entity
            _flow.value = store.values.toList()
        }
    }

    override suspend fun deleteById(id: String) {
        deletedIds.add(id)
        store.remove(id)
        _flow.value = store.values.toList()
    }

    override suspend fun findById(id: String): QuestEntity? = store[id]

    suspend fun getLocalContentsVersion(id: QuestId): Long? = store[id.value]?.contentsVersion

    fun seed(quests: List<QuestEntity>) {
        store.clear()
        quests.forEach { store[it.id] = it }
        _flow.value = store.values.toList()
    }
}
