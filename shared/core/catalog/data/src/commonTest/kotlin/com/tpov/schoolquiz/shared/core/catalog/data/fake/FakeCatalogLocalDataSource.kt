package com.tpov.schoolquiz.shared.core.catalog.data.fake

import com.tpov.schoolquiz.shared.core.catalog.data.CatalogLocalDataSource
import com.tpov.schoolquiz.shared.core.persistence.CatalogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeCatalogLocalDataSource : CatalogLocalDataSource {
    private val _entities = MutableStateFlow<List<CatalogEntity>>(emptyList())
    var replaceAllCalls: Int = 0
    var lastReplacedEntities: List<CatalogEntity>? = null
    val upsertCallsFor = mutableMapOf<String, Int>()
    val deletedIds = mutableListOf<String>()

    override fun observeAll(): Flow<List<CatalogEntity>> = _entities

    override suspend fun findById(id: String): CatalogEntity? = _entities.value.find { it.id == id }

    override suspend fun replaceAll(entities: List<CatalogEntity>) {
        replaceAllCalls++
        lastReplacedEntities = entities
        _entities.value = entities
    }

    override suspend fun upsertByIdIfNewerVersion(entity: CatalogEntity) {
        upsertCallsFor[entity.id] = (upsertCallsFor[entity.id] ?: 0) + 1
        val current = _entities.value.find { it.id == entity.id }
        if (current == null || current.shouldBeReplacedBy(entity)) {
            _entities.value = _entities.value.filter { it.id != entity.id } + entity
        }
    }

    override suspend fun deleteById(id: String) {
        deletedIds.add(id)
        _entities.value = _entities.value.filter { it.id != id }
    }

    fun emit(entities: List<CatalogEntity>) {
        _entities.value = entities
    }

    fun seed(entities: List<CatalogEntity>) {
        _entities.value = entities
    }

    private fun CatalogEntity.shouldBeReplacedBy(incoming: CatalogEntity): Boolean =
        version < incoming.version ||
            (version == incoming.version && lastModifiedAt < incoming.lastModifiedAt) ||
            (version == incoming.version && lastModifiedAt == incoming.lastModifiedAt && this != incoming)
}
