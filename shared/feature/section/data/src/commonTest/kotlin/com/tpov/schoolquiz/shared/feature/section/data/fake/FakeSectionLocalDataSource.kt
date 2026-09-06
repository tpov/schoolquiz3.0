package com.tpov.schoolquiz.shared.feature.section.data.fake

import com.tpov.schoolquiz.shared.core.persistence.SectionEntity
import com.tpov.schoolquiz.shared.feature.section.data.SectionLocalDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSectionLocalDataSource : SectionLocalDataSource {

    private val store = mutableMapOf<String, SectionEntity>()
    private val _flow = MutableStateFlow<List<SectionEntity>>(emptyList())

    val upsertCallsFor = mutableMapOf<String, Int>()
    val deletedIds = mutableListOf<String>()

    override fun observeByQuest(questId: String): Flow<List<SectionEntity>> =
        // Как в SectionDao: `WHERE questId = :questId AND archived = 0` — скрытый узел из
        // выборки уходит, но строка остаётся.
        _flow.map { list -> list.filter { it.questId == questId && !it.archived } }

    override suspend fun upsertByIdIfNewerVersion(entity: SectionEntity) {
        upsertCallsFor[entity.id] = (upsertCallsFor[entity.id] ?: 0) + 1
        val current = store[entity.id]
        if (current == null || current.version < entity.version) {
            store[entity.id] = entity
            _flow.value = store.values.toList()
        }
    }


    /** Как в SectionDao.upsertFromSyncList: версия, время, а при их равенстве — отличие содержимого. */
    override suspend fun upsertFromSyncList(entity: SectionEntity) {
        upsertCallsFor[entity.id] = (upsertCallsFor[entity.id] ?: 0) + 1
        val current = store[entity.id]
        val replace =
            current == null ||
                current.version < entity.version ||
                current.lastModifiedAt < entity.lastModifiedAt ||
                (current.lastModifiedAt == entity.lastModifiedAt && current != entity)
        if (replace) {
            store[entity.id] = entity
            _flow.value = store.values.toList()
        }
    }

    override suspend fun deleteById(id: String) {
        deletedIds.add(id)
        store.remove(id)
        _flow.value = store.values.toList()
    }

    override suspend fun findById(id: String): SectionEntity? = store[id]

    fun seed(entities: List<SectionEntity>) {
        store.clear()
        entities.forEach { store[it.id] = it }
        _flow.value = store.values.toList()
    }
}
